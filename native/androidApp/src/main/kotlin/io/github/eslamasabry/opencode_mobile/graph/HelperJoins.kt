package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The home's starts of the phone agents' helper as a list source (issue 147, owner's phone on build 10045: the
 * connections chip read "Starting Claude Code…" for minutes while no helper ran and nothing was starting it).
 *
 * "Starting" is only ever true while a start runs and within [limitMs]: a start that outlives its limit stops saying
 * so and [start]'s `onLimit` names the failure, even when the start itself is stuck in a call that cannot be cut
 * (it keeps running in the background; no second start is piled on it). A source that should be listed but has no
 * start may be started again once [retryAfterMs] passed since the last one ([due]).
 */
internal class HelperJoins(
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val limitMs: Long = LIMIT_MS,
    private val retryAfterMs: Long = RETRY_AFTER_MS,
) {
    private val jobs = HashMap<ProfileId, Job>()
    private val lastStart = HashMap<ProfileId, Long>()
    private val mutableStarting = MutableStateFlow<Set<ProfileId>>(emptySet())

    /** The sources whose start runs now, within its limit: the only ones that may read "Starting". */
    val starting: StateFlow<Set<ProfileId>> = mutableStarting.asStateFlow()

    /**
     * Runs [work] for [id] unless a start of it still runs (also one past its limit). [onLimit] runs when the start
     * outlives [limitMs]. False when nothing was started.
     */
    fun start(id: ProfileId, onLimit: () -> Unit, work: suspend () -> Unit): Boolean {
        val job =
            synchronized(jobs) {
                if (jobs[id]?.isActive == true) return false
                lastStart[id] = now()
                scope.launch(start = CoroutineStart.LAZY) { work() }.also { jobs[id] = it }
            }
        mutableStarting.update { it + id }
        scope.launch {
            job.start()
            val ended = withTimeoutOrNull(limitMs) { job.join() } != null
            mutableStarting.update { it - id }
            if (!ended) onLimit()
        }
        return true
    }

    /** Whether [id] may be started again now: no start of it runs and the last one began [retryAfterMs] ago or more. */
    fun due(id: ProfileId): Boolean =
        synchronized(jobs) {
            jobs[id]?.isActive != true && lastStart[id]?.let { now() - it >= retryAfterMs } != false
        }

    companion object {
        /** The helper's own start waits up to 30 s for its answer; its connection and first list read come on top. */
        const val LIMIT_MS: Long = 90_000L

        /** A source that fell out of the list without a failure is started again at most this often. */
        const val RETRY_AFTER_MS: Long = 30_000L
    }
}
