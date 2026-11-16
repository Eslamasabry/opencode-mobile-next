package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The home's starts of the phone agents' helper as a list source (issue 147).
 *
 * Owner's phone, build 10045: the connections chip read "Starting Claude Code…" for minutes with no helper and nothing
 * starting it. Galaxy S22, build 10046, right after an update: the first try failed at once (nothing listened yet),
 * the home said "isn't answering · Try again" within 4 s and nothing started the helper until the tap.
 *
 * Now one start loop per source ([ensure]): it tries, and while the first [limitMs] of the loop last it reads
 * "starting" ([starting]) whatever the tries answer, trying again every [quickRetryMs]; a failure is named
 * ([ensure]'s `onFailed`) only after that window, or at once when it is one waiting cannot change ([keepTrying]
 * false). After the window it keeps trying with a growing pause ([pauseAfter]). A try that never returns stops the
 * "starting" at the window's end and is waited for, never piled on. The loop ends when a try lists the source.
 *
 * Owner's phone, build 10057 (updated from 10045): the update started the app in the background, where Android's
 * Linux admits no helper start (`BuiltinLinux.captureAgentHostStart`: only while the app is in front). Every try of the
 * first window was refused, the loop went into its long pause, and coming to the front or opening a Claude Code
 * conversation woke nothing: "Connecting" for minutes. [wake] now gives a loop a new first window (tries every
 * [quickRetryMs], reads "starting"), also when it comes while a try runs.
 */
internal class HelperJoins(
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val limitMs: Long = LIMIT_MS,
    private val quickRetryMs: Long = QUICK_RETRY_MS,
) {
    private val loops = HashMap<ProfileId, Job>()
    private val wakes = HashMap<ProfileId, CompletableDeferred<Unit>>()
    private val mutableStarting = MutableStateFlow<Set<ProfileId>>(emptySet())

    /** The sources whose start loop is in its first window: the only ones that read "Starting". */
    val starting: StateFlow<Set<ProfileId>> = mutableStarting.asStateFlow()

    /** The loops woken while a try ran: their next pause is skipped and a new window starts. */
    private val woken = HashSet<ProfileId>()

    /**
     * Someone needs the helper now (a pull to refresh, the app coming to the front, a phone-agent conversation
     * opening): a running loop of [id] tries again at once with a new first window. Nothing when no loop runs.
     */
    fun wake(id: ProfileId) {
        synchronized(loops) {
            if (loops[id]?.isActive != true) return
            woken += id
            wakes[id]
        }?.complete(Unit)
    }

    /** Whether a start loop of [id] runs now (also past its window). */
    fun running(id: ProfileId): Boolean = synchronized(loops) { loops[id]?.isActive == true }

    /**
     * Makes sure a start loop of [id] runs. [attempt] is one try: null when it listed the source, else why not.
     * [keepTrying] false for a failure waiting cannot change (it is named at once and the loop ends). False when a
     * loop already ran.
     */
    fun ensure(
        id: ProfileId,
        keepTrying: (Failure) -> Boolean,
        onFailed: (Failure) -> Unit,
        attempt: suspend () -> Failure?,
    ): Boolean {
        synchronized(loops) {
            if (loops[id]?.isActive == true) return false
            mutableStarting.update { it + id }
            loops[id] = scope.launch { loop(id, keepTrying, onFailed, attempt) }
        }
        return true
    }

    private suspend fun loop(
        id: ProfileId,
        keepTrying: (Failure) -> Boolean,
        onFailed: (Failure) -> Unit,
        attempt: suspend () -> Failure?,
    ) {
        var since = now()
        var lateTries = 0
        try {
            while (true) {
                val work = scope.async { attempt() }
                val inWindow = now() - since < limitMs
                val wait = if (inWindow) (limitMs - (now() - since)).coerceAtLeast(1) else limitMs
                val answer = withTimeoutOrNull(wait) { Answer(work.await()) }
                if (answer != null && answer.failure == null) return
                val failure = answer?.failure ?: Failure.Runtime(RuntimeProblem.ServerStartUnavailable)
                if (answer != null && !keepTrying(failure)) {
                    onFailed(failure)
                    return
                }
                if (now() - since >= limitMs) {
                    mutableStarting.update { it - id }
                    onFailed(failure)
                }
                // A try that never returned is waited for, never piled on.
                if (answer == null) work.join()
                val late = now() - since >= limitMs
                if (late) lateTries++
                val wake =
                    CompletableDeferred<Unit>().also {
                        synchronized(loops) {
                            wakes[id] = it
                            if (id in woken) it.complete(Unit)
                        }
                    }
                val woke = withTimeoutOrNull(if (late) pauseAfter(lateTries) else quickRetryMs) { wake.await() } != null
                if (woke) {
                    // Someone needs the helper now: a new first window, read as starting again.
                    synchronized(loops) { woken -= id }
                    since = now()
                    lateTries = 0
                    mutableStarting.update { it + id }
                }
            }
        } finally {
            synchronized(loops) { woken -= id }
            mutableStarting.update { it - id }
        }
    }

    private class Answer(val failure: Failure?)

    /**
     * Each time the app comes to the front ([front] turns true), [want] runs for every source [sources] lists: a start
     * refused while the app was in the background (an update or a boot started it) is tried again at once.
     */
    suspend fun followFront(front: Flow<Boolean>, sources: () -> Collection<ProfileId>, want: (ProfileId) -> Unit) {
        front.distinctUntilChanged().collect { inFront -> if (inFront) sources().forEach(want) }
    }

    companion object {
        /** The helper's own start waits up to 30 s for its answer; its connection and first list read come on top. */
        const val LIMIT_MS: Long = 90_000L

        /** Inside the first window a refused try is tried again this soon (the phone may just be busy starting). */
        const val QUICK_RETRY_MS: Long = 5_000L

        /** After the window ([lateTries] failed tries past it): 30 s, 60 s, 2 min, 4 min, then every 5 min. */
        fun pauseAfter(lateTries: Int): Long =
            (30_000L shl (lateTries - 1).coerceIn(0, 4)).coerceAtMost(300_000L)
    }
}
