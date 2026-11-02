package io.github.eslamasabry.oc.feature.linux.presentation

import io.github.eslamasabry.oc.feature.linux.manager.LinuxAnswer
import io.github.eslamasabry.oc.feature.linux.manager.LinuxJobKind
import io.github.eslamasabry.oc.feature.linux.manager.LinuxJobState
import io.github.eslamasabry.oc.feature.linux.manager.LinuxJobStatus
import io.github.eslamasabry.oc.feature.linux.manager.LinuxManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The one package job as every Linux page sees it (port of `LinuxJobController`): one object for the app, so a job
 * started on one page is seen on every other and after coming back. It owns no timer: a page that shows the job calls
 * [refresh] on [LinuxTimers.jobPoll] while it is shown and the job runs.
 */
public class LinuxJobWatch(
    private val manager: LinuxManager,
    private val notices: LinuxJobNotices,
) {
    private val state = MutableStateFlow(LinuxJobState.None)
    public val job: StateFlow<LinuxJobState> = state.asStateFlow()

    private val lock = Mutex()
    private var dismissed = false

    /**
     * Bumped when a job starts: a read that began before it describes the job before (or none) and must not overwrite
     * the running state (a stale "not running" would otherwise look like the job ended).
     */
    private var epoch = 0

    /** Reads the job again (after the app restarted, or on a tick). A failed read keeps the last state. */
    public suspend fun refresh() {
        val began = lock.withLock { epoch }
        val read = manager.readJob()
        lock.withLock { if (began == epoch) set(read) }
    }

    public suspend fun start(kind: LinuxJobKind, names: List<String>): LinuxAnswer<Unit> {
        val started = manager.startJob(kind, names, notices.notice(kind))
        if (started is LinuxAnswer.Ok) {
            lock.withLock {
                epoch++
                dismissed = false
                set(LinuxJobState(kind = kind, names = names, status = LinuxJobStatus.Running))
            }
        }
        return started
    }

    public suspend fun stop(): LinuxAnswer<Unit> {
        val stopped = manager.stopJob()
        refresh()
        return stopped
    }

    /** The person has seen how the last job ended: it is not shown again. */
    public suspend fun dismiss() {
        lock.withLock {
            if (state.value.running) return
            dismissed = true
            state.value = LinuxJobState.None
        }
    }

    private fun set(next: LinuxJobState) {
        state.value = if (dismissed && !next.running) LinuxJobState.None else next
        if (next.running) dismissed = false
    }
}
