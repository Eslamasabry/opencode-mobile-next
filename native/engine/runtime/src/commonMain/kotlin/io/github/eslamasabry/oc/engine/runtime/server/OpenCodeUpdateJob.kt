package io.github.eslamasabry.oc.engine.runtime.server

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Which setup job is the OpenCode update, for This phone's Update row.
 *
 * A setup job always carries every required component (Linux, the essentials, Node, OpenCode, the start step), so
 * the update cannot be told from Repair by what it lists. It is the job the update itself started: [starting] is
 * called with the job shown before the update asks, [started] with the job shown once the ask returned.
 */
public class OpenCodeUpdateJob {
    private data class State(val before: String?, val starting: Boolean, val job: String?)

    private val state = MutableStateFlow(State(before = null, starting = false, job = null))

    public fun starting(shownJob: String?) {
        state.update { it.copy(before = shownJob, starting = true) }
    }

    /** A job that was already there (the update was refused, or another job is still running) is not the update. */
    public fun started(shownJob: String?) {
        state.update { it.copy(starting = false, job = if (shownJob != it.before) shownJob else it.job) }
    }

    public fun owns(jobId: String?): Boolean =
        state.value.let { jobId != null && (jobId == it.job || (it.starting && jobId != it.before)) }
}
