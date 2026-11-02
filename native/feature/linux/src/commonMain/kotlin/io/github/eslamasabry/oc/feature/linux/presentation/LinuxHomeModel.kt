package io.github.eslamasabry.oc.feature.linux.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.pace.Pacer
import io.github.eslamasabry.oc.feature.linux.host.LinuxPresence
import io.github.eslamasabry.oc.feature.linux.manager.LinuxAnswer
import io.github.eslamasabry.oc.feature.linux.manager.LinuxJobKind
import io.github.eslamasabry.oc.feature.linux.manager.LinuxJobState
import io.github.eslamasabry.oc.feature.linux.manager.LinuxManager
import io.github.eslamasabry.oc.feature.linux.manager.LinuxProblem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What a Linux page asks before acting (KitConfirm). */
public sealed interface LinuxConfirm {
    public data object Repair : LinuxConfirm

    /** Guarded by the typed word "Reset" (owner rule LOOK-5: never red, the typed question guards it). */
    public data object Reset : LinuxConfirm

    public data class StopJob(val kind: LinuxJobKind, val names: List<String>) : LinuxConfirm
}

/** One line said in place on the manager home, never a snackbar. */
public sealed interface LinuxHomeNotice {
    public data class Problem(val problem: LinuxProblem) : LinuxHomeNotice

    /** The phone refused before erasing anything (B-LINUX-093). */
    public data object ResetNotErased : LinuxHomeNotice

    /** Erased, but setup could not start now; the next app start tries again. */
    public data object ResetSetupPending : LinuxHomeNotice

    /** Repair (or setting Linux up again) is not available in this build. */
    public data object SetupUnavailable : LinuxHomeNotice
}

/** LinuxRoute.Home's state (Flutter LinuxManagerScreen). */
public data class LinuxHomeUi(
    /** Null until the first read answered. */
    val presence: LinuxPresence? = null,
    val job: LinuxJobState = LinuxJobState.None,
    val confirm: LinuxConfirm? = null,
    val notice: LinuxHomeNotice? = null,
    val working: Boolean = false,
) {
    /** The rows that act on Linux are offered only when it answers. */
    val ready: Boolean get() = presence == LinuxPresence.Ready
}

public interface LinuxHomeActions : LinuxJobActions {
    public fun shown(visible: Boolean)

    public fun open(page: LinuxPage)

    public fun askRepair()

    public fun askReset()

    public fun confirm()

    public fun dismiss()

    public fun back()
}

/** The job panel's intents, shared by every page that shows the job. */
public interface LinuxJobActions {
    public fun askStopJob()

    public fun dismissJob()
}

public class LinuxHomeModel(
    private val manager: LinuxManager,
    private val watch: LinuxJobWatch,
    private val lifecycle: LinuxLifecycle,
    private val reset: LinuxResetFlow,
    private val nav: LinuxNavigator,
    private val scope: CoroutineScope,
    private val pacer: Pacer,
) : LinuxHomeActions {
    private val ui = MutableStateFlow(LinuxHomeUi())
    public val state: StateFlow<LinuxHomeUi> = ui.asStateFlow()
    private val visible = MutableStateFlow(false)

    init {
        scope.launch { watch.job.collect { job -> ui.update { it.copy(job = job) } } }
        scope.launch {
            combine(visible, watch.job) { shown, job -> shown && job.running }.collectLatest { polling ->
                if (polling) pacer.ticks(LinuxTimers.jobPoll).collect { watch.refresh() }
            }
        }
    }

    override fun shown(visible: Boolean) {
        this.visible.value = visible
        if (visible) {
            scope.launch {
                ui.update { it.copy(presence = manager.presence()) }
                watch.refresh()
            }
        }
    }

    override fun open(page: LinuxPage): Unit = nav.open(page)

    override fun askRepair() {
        guarded { ui.update { it.copy(confirm = LinuxConfirm.Repair) } }
    }

    override fun askReset() {
        guarded { ui.update { it.copy(confirm = LinuxConfirm.Reset) } }
    }

    override fun askStopJob() {
        val job = ui.value.job
        val kind = job.kind ?: return
        if (job.running) ui.update { it.copy(confirm = LinuxConfirm.StopJob(kind, job.names), notice = null) }
    }

    override fun dismissJob() {
        scope.launch { watch.dismiss() }
    }

    override fun confirm() {
        val asked = ui.value.confirm ?: return
        ui.update { it.copy(confirm = null, working = true) }
        scope.launch {
            when (asked) {
                LinuxConfirm.Repair ->
                    if (lifecycle.repair() is Outcome.Failed) ui.update { it.copy(notice = LinuxHomeNotice.SetupUnavailable) } else nav.open(LinuxPage.ThisPhone)
                LinuxConfirm.Reset ->
                    when (val result = reset.run()) {
                        is LinuxResetResult.NotErased -> ui.update { it.copy(notice = LinuxHomeNotice.ResetNotErased) }
                        is LinuxResetResult.Reinstalling ->
                            if (result.setupStarted) nav.open(LinuxPage.ThisPhone) else ui.update { it.copy(notice = LinuxHomeNotice.ResetSetupPending) }
                    }
                is LinuxConfirm.StopJob -> watch.stop()
            }
            ui.update { it.copy(working = false) }
        }
    }

    override fun dismiss() {
        ui.update { it.copy(confirm = null, notice = null) }
    }

    override fun back(): Unit = nav.back()

    /** Repair and Reset first make sure setup or a package job is not running (said in place, plainly). */
    private fun guarded(ask: () -> Unit) {
        if (ui.value.working) return
        scope.launch {
            when (val quiet = manager.requireQuiet()) {
                is LinuxAnswer.Problem -> ui.update { it.copy(notice = LinuxHomeNotice.Problem(quiet.problem)) }
                is LinuxAnswer.Ok -> {
                    ui.update { it.copy(notice = null) }
                    ask()
                }
            }
        }
    }
}
