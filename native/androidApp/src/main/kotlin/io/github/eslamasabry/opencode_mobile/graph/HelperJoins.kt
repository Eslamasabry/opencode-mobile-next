package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    /** A person asked (pull to refresh): a loop pausing between tries tries again now. */
    fun wake(id: ProfileId) {
        synchronized(loops) { wakes[id] }?.complete(Unit)
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
        val since = now()
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
                val wake = CompletableDeferred<Unit>().also { synchronized(loops) { wakes[id] = it } }
                withTimeoutOrNull(if (late) pauseAfter(lateTries) else quickRetryMs) {
                    wake.await()
                }
            }
        } finally {
            mutableStarting.update { it - id }
        }
    }

    private class Answer(val failure: Failure?)

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
