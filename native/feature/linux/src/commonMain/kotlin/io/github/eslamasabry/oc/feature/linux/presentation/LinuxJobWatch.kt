package io.github.eslamasabry.oc.feature.linux.presentation

import io.github.eslamasabry.oc.feature.linux.manager.LinuxAnswer
import io.github.eslamasabry.oc.feature.linux.manager.LinuxJobKind
import io.github.eslamasabry.oc.feature.linux.manager.LinuxJobState
import io.github.eslamasabry.oc.feature.linux.manager.LinuxJobStatus
import io.github.eslamasabry.oc.feature.linux.manager.LinuxManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * The one package job as every Linux page sees it (port of `LinuxJobController`): one object for the app, so a job
 * started on one page is seen on every other and after coming back. It owns no timer: a page that shows the job calls
 * [refresh] on [LinuxTimers.jobPoll] while it is shown and the job runs.
 */
public class LinuxJobWatch(
    private val manager: LinuxManager,
    private val notices: LinuxJobNotices,
) {
    /**
     * Everything the watch keeps, in one value so each change is one atomic [update] (no lock: K1 keeps
     * kotlinx.coroutines.sync out of features). [epoch] is bumped when a job starts: a read that began before it
     * describes the job before (or none) and must not overwrite the running state (a stale "not running" would
     * otherwise look like the job ended).
     */
    private data class Book(val shown: LinuxJobState, val epoch: Int, val dismissed: Boolean) {
        fun with(next: LinuxJobState): Book =
            Book(
                shown = if (dismissed && !next.running) LinuxJobState.None else next,
                epoch = epoch,
                dismissed = dismissed && !next.running,
            )
    }

    private val book = MutableStateFlow(Book(LinuxJobState.None, epoch = 0, dismissed = false))
    public val job: Flow<LinuxJobState> = book.map { it.shown }.distinctUntilChanged()

    /** Reads the job again (after the app restarted, or on a tick). A failed read keeps the last state. */
    public suspend fun refresh() {
        val began = book.value.epoch
        val read = manager.readJob()
        book.update { if (it.epoch == began) it.with(read) else it }
    }

    public suspend fun start(kind: LinuxJobKind, names: List<String>): LinuxAnswer<Unit> {
        val started = manager.startJob(kind, names, notices.notice(kind))
        if (started is LinuxAnswer.Ok) {
            book.update {
                Book(LinuxJobState(kind = kind, names = names, status = LinuxJobStatus.Running), epoch = it.epoch + 1, dismissed = false)
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
    public fun dismiss() {
        book.update { if (it.shown.running) it else it.copy(shown = LinuxJobState.None, dismissed = true) }
    }
}
