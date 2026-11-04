package io.github.eslamasabry.oc.feature.linux.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.pace.Pacer
import io.github.eslamasabry.oc.engine.runtime.components.ComponentRemovalBlock
import io.github.eslamasabry.oc.engine.runtime.components.ComponentRemovalEntry
import io.github.eslamasabry.oc.engine.runtime.components.RemovalResult
import io.github.eslamasabry.oc.engine.runtime.setup.SetupProgress
import io.github.eslamasabry.oc.engine.runtime.storage.LinuxFolder
import io.github.eslamasabry.oc.engine.runtime.storage.LinuxLocalStorage
import io.github.eslamasabry.oc.engine.runtime.storage.LinuxRemovalSize
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** What a Linux page asks before acting (KitConfirm). */
public sealed interface LinuxConfirm {
    public data object Repair : LinuxConfirm

    /**
     * Guarded by the typed word "Reset" (owner rule LOOK-5: never red, the typed question guards
     * it).
     */
    public data object Reset : LinuxConfirm

    public data class Remove(val projects: Boolean, val bytes: Long) : LinuxConfirm

    public data object StopSetup : LinuxConfirm

    public data class RemoveComponent(val id: String) : LinuxConfirm

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

    public data class SetupRefused(val failure: io.github.eslamasabry.oc.core.common.Failure) :
        LinuxHomeNotice

    public data object StorageFailed : LinuxHomeNotice

    public data object Removed : LinuxHomeNotice
}

/** LinuxRoute.Home's state (Flutter LinuxManagerScreen). */
public data class LinuxHomeUi(
    /** Null until the first read answered. */
    val presence: LinuxPresence? = null,
    val job: LinuxJobState = LinuxJobState.None,
    val confirm: LinuxConfirm? = null,
    val notice: LinuxHomeNotice? = null,
    val working: Boolean = false,
    val folderPath: String? = null,
    val folders: List<LinuxFolder> = emptyList(),
    val folderLoading: Boolean = false,
    val folderFailed: Boolean = false,
    val removalSize: LinuxRemovalSize? = null,
    val setup: SetupProgress = SetupProgress.Idle,
    val idle: LinuxIdleUi = LinuxIdleUi(),
    val idleWorking: Boolean = false,
    val askingIdleMinutes: Boolean = false,
    val lifetimes: LinuxLifetimesUi = LinuxLifetimesUi(),
    val askingCreateProject: Boolean = false,
    val vitals: LinuxVitalsUi? = null,
    val components: List<ComponentRemovalEntry> = emptyList(),
    val componentsLoading: Boolean = false,
    val componentBlock: ComponentRemovalBlock? = null,
    val migrationOffers: List<LinuxMigrationOffer> = emptyList(),
) {
    /** The rows that act on Linux are offered only when it answers. */
    val ready: Boolean
        get() = presence == LinuxPresence.Ready
}

public interface LinuxHomeActions : LinuxJobActions {
    public fun shown(visible: Boolean)

    public fun open(page: LinuxPage)

    public fun askRepair()

    public fun askReset()

    public fun startSetup()

    public fun addPython()

    public fun continueSetup()

    public fun refreshComponents()

    public fun askRemoveComponent(id: String)

    public fun openOwnershipSettings()

    public fun askStopSetup()

    public fun setIdleEnabled(enabled: Boolean)

    public fun askIdleMinutes()

    public fun finishIdleMinutes(value: String?)

    public fun resumeIdle()

    public fun refreshLifetimes()

    public fun askCreateProject()

    public fun finishCreateProject(path: String?)

    public fun browse(path: String)

    public fun closeFolders()

    public fun measureRemoval()

    public fun askRemove(projects: Boolean)

    public fun confirm()

    public fun dismiss()

    public fun back()
}

/** Optional native journeys; existing layout-only consumers need only the base actions. */
public interface LinuxHomeNativeActions : LinuxHomeActions {
    public fun addVoice()

    public fun openMigration(profileId: String)
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
    private val storage: LinuxLocalStorage? = null,
    private val setupSource: LinuxSetupSource? = null,
    private val idleSource: LinuxIdleSource? = null,
    lifetimesSource: LinuxLifetimesSource? = null,
    vitalsSource: LinuxVitalsSource? = null,
    private val migrationOffers: LinuxMigrationOffers? = null,
) : LinuxHomeNativeActions {
    private val ui = MutableStateFlow(LinuxHomeUi())
    public val state: StateFlow<LinuxHomeUi> = ui.asStateFlow()
    private val visible = MutableStateFlow(false)

    private val lifetimes = lifetimesSource?.let { LinuxLifetimesModel(it, scope) }

    init {
        migrationOffers?.let { offers ->
            scope.launch {
                offers.offers.collect { values -> ui.update { it.copy(migrationOffers = values) } }
            }
        }
        vitalsSource?.let { source ->
            scope.launch {
                visible.collectLatest { shown ->
                    if (shown) {
                        suspend fun read() {
                            when (val result = source.read()) {
                                is Outcome.Ok -> ui.update { it.copy(vitals = result.value) }
                                is Outcome.Failed -> Unit
                            }
                        }
                        read()
                        pacer.ticks(LinuxVitalsTimer).collect { read() }
                    }
                }
            }
        }
        lifetimes?.let { model ->
            scope.launch {
                model.state.collect { state -> ui.update { it.copy(lifetimes = state) } }
            }
        }
        idleSource?.let { idle ->
            scope.launch { idle.state.collect { status -> ui.update { it.copy(idle = status) } } }
        }
        setupSource?.let { setup ->
            scope.launch {
                setup.progress.collect { progress -> ui.update { it.copy(setup = progress) } }
            }
            scope.launch {
                setup.toolsChanged.collect { revision ->
                    if (revision > 0) {
                        val presence = manager.presence()
                        ui.update { it.copy(presence = presence) }
                        watch.refresh()
                        loadComponents()
                    }
                }
            }
        }
        scope.launch { watch.job.collect { job -> ui.update { it.copy(job = job) } } }
        scope.launch {
            combine(visible, watch.job) { shown, job -> shown && job.running }
                .collectLatest { polling ->
                    if (polling) pacer.ticks(LinuxTimers.jobPoll).collect { watch.refresh() }
                }
        }
    }

    override fun shown(visible: Boolean) {
        this.visible.value = visible
        if (visible) {
            scope.launch { migrationOffers?.refresh() }
            scope.launch {
                ui.update { it.copy(presence = manager.presence()) }
                watch.refresh()
                setupSource?.refresh()
                loadComponents()
                idleSource?.refresh()
                lifetimes?.refresh()
            }
        }
    }

    override fun open(page: LinuxPage): Unit = nav.open(page)

    override fun openMigration(profileId: String) {
        if (ui.value.migrationOffers.any { it.profileId == profileId })
            (nav as? LinuxMigrationNavigator)?.migrate(profileId)
    }

    override fun askRepair() {
        guarded { ui.update { it.copy(confirm = LinuxConfirm.Repair) } }
    }

    override fun askReset() {
        guarded { ui.update { it.copy(confirm = LinuxConfirm.Reset) } }
    }

    override fun setIdleEnabled(enabled: Boolean) {
        val minutes = ui.value.idle.minutes ?: return
        idleCommand { it.configure(enabled, minutes) }
    }

    override fun askIdleMinutes() {
        if (ui.value.idle.supported && !ui.value.idleWorking)
            ui.update { it.copy(askingIdleMinutes = true) }
    }

    override fun finishIdleMinutes(value: String?) {
        ui.update { it.copy(askingIdleMinutes = false) }
        val minutes = value?.toIntOrNull()?.takeIf { it in 1..60 } ?: return
        val enabled = ui.value.idle.enabled
        idleCommand { it.configure(enabled, minutes) }
    }

    override fun resumeIdle() = idleCommand { it.resume() }

    private fun idleCommand(command: suspend (LinuxIdleSource) -> Outcome<Unit>) {
        val idle = idleSource ?: return
        if (!ui.value.idle.supported || ui.value.idleWorking) return
        ui.update { it.copy(idleWorking = true) }
        scope.launch {
            command(idle)
            ui.update { it.copy(idleWorking = false) }
        }
    }

    private suspend fun loadComponents() {
        val setup = setupSource ?: return
        ui.update { it.copy(componentsLoading = true) }
        val result = setup.components()
        ui.update {
            it.copy(
                componentsLoading = false,
                components = (result as? Outcome.Ok)?.value ?: it.components,
                componentBlock =
                    if (result is Outcome.Failed) ComponentRemovalBlock.InventoryUnavailable
                    else null,
            )
        }
    }

    override fun refreshComponents() {
        if (ui.value.componentsLoading || ui.value.working) return
        scope.launch { loadComponents() }
    }

    override fun askRemoveComponent(id: String) {
        if (
            ui.value.working ||
                ui.value.job.running ||
                ui.value.setup.state ==
                    io.github.eslamasabry.oc.engine.runtime.setup.SetupState.Running
        )
            return
        val entry = ui.value.components.firstOrNull { it.component.id == id } ?: return
        if (entry.canRemove)
            ui.update { it.copy(confirm = LinuxConfirm.RemoveComponent(id), componentBlock = null) }
    }

    override fun openOwnershipSettings() {
        val setup = setupSource ?: return
        scope.launch {
            if (setup.openOwnershipSettings() is Outcome.Failed)
                ui.update { it.copy(notice = LinuxHomeNotice.SetupUnavailable) }
        }
    }

    override fun startSetup() = setupCommand { it.start() }

    override fun addPython() = setupCommand { it.addPython() }

    override fun addVoice() = setupCommand { it.addVoice() }

    override fun continueSetup() = setupCommand { it.resume() }

    override fun askStopSetup() {
        if (
            ui.value.setup.state == io.github.eslamasabry.oc.engine.runtime.setup.SetupState.Running
        )
            ui.update { it.copy(confirm = LinuxConfirm.StopSetup) }
    }

    private fun setupCommand(command: suspend (LinuxSetupSource) -> Outcome<Unit>) {
        val setup = setupSource ?: return
        if (
            ui.value.working ||
                ui.value.job.running ||
                ui.value.setup.state ==
                    io.github.eslamasabry.oc.engine.runtime.setup.SetupState.Running
        )
            return
        ui.update { it.copy(working = true, notice = null) }
        scope.launch {
            val result = command(setup)
            ui.update {
                it.copy(
                    working = false,
                    notice =
                        if (result is Outcome.Failed) LinuxHomeNotice.SetupRefused(result.failure)
                        else null,
                )
            }
        }
    }

    override fun refreshLifetimes() {
        lifetimes?.refresh()
    }

    override fun askCreateProject() {
        if (ui.value.ready && !ui.value.working && !ui.value.job.running)
            ui.update { it.copy(askingCreateProject = true) }
    }

    override fun finishCreateProject(path: String?) {
        ui.update { it.copy(askingCreateProject = false) }
        val files = storage ?: return
        if (path == null || ui.value.working || !ui.value.ready) return
        val normalized = LinuxLocalStorage.normalize(path) ?: return
        if (normalized == "/" || normalized.contains('\n') || normalized.contains('\r')) return
        ui.update { it.copy(working = true, notice = null) }
        scope.launch {
            val result = files.createProject(normalized)
            ui.update {
                it.copy(
                    working = false,
                    notice = if (result is Outcome.Failed) LinuxHomeNotice.StorageFailed else null,
                )
            }
            if (result is Outcome.Ok)
                browse(LinuxLocalStorage.parent(normalized) ?: LinuxLocalStorage.PROJECTS)
        }
    }

    private var folderGeneration = 0L

    override fun browse(path: String) {
        val files = storage ?: return
        val normalized = LinuxLocalStorage.normalize(path) ?: return
        val generation = ++folderGeneration
        ui.update {
            it.copy(
                folderPath = normalized,
                folderLoading = true,
                folderFailed = false,
                folders = emptyList(),
            )
        }
        scope.launch {
            val result = files.folders(normalized)
            if (generation != folderGeneration) return@launch
            ui.update {
                it.copy(
                    folderLoading = false,
                    folderFailed = result is Outcome.Failed,
                    folders = (result as? Outcome.Ok)?.value.orEmpty(),
                )
            }
        }
    }

    override fun closeFolders() {
        folderGeneration++
        ui.update {
            it.copy(
                folderPath = null,
                folderLoading = false,
                folderFailed = false,
                folders = emptyList(),
            )
        }
    }

    override fun measureRemoval() {
        val files = storage ?: return
        if (
            ui.value.working ||
                ui.value.job.running ||
                ui.value.setup.state ==
                    io.github.eslamasabry.oc.engine.runtime.setup.SetupState.Running
        )
            return
        ui.update { it.copy(working = true, notice = null, removalSize = null) }
        scope.launch {
            val result = files.measure()
            ui.update {
                it.copy(
                    working = false,
                    removalSize = (result as? Outcome.Ok)?.value,
                    notice = if (result is Outcome.Failed) LinuxHomeNotice.StorageFailed else null,
                )
            }
        }
    }

    override fun askRemove(projects: Boolean) {
        if (
            ui.value.working ||
                ui.value.job.running ||
                ui.value.setup.state ==
                    io.github.eslamasabry.oc.engine.runtime.setup.SetupState.Running
        )
            return
        val size = ui.value.removalSize ?: return
        ui.update { it.copy(confirm = LinuxConfirm.Remove(projects, size.freed(projects))) }
    }

    override fun askStopJob() {
        val job = ui.value.job
        val kind = job.kind ?: return
        if (job.running)
            ui.update { it.copy(confirm = LinuxConfirm.StopJob(kind, job.names), notice = null) }
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
                    if (lifecycle.repair() is Outcome.Failed)
                        ui.update { it.copy(notice = LinuxHomeNotice.SetupUnavailable) }
                    else nav.open(LinuxPage.ThisPhone)
                LinuxConfirm.Reset ->
                    when (val result = reset.run()) {
                        is LinuxResetResult.NotErased ->
                            ui.update { it.copy(notice = LinuxHomeNotice.ResetNotErased) }
                        is LinuxResetResult.Reinstalling ->
                            if (result.setupStarted) nav.open(LinuxPage.ThisPhone)
                            else ui.update { it.copy(notice = LinuxHomeNotice.ResetSetupPending) }
                    }
                LinuxConfirm.StopSetup -> setupSource?.cancel()
                is LinuxConfirm.RemoveComponent -> {
                    val result = setupSource?.removeComponent(asked.id)
                    loadComponents()
                    ui.update { it.copy(componentBlock = (result as? RemovalResult.Blocked)?.code) }
                }
                is LinuxConfirm.Remove -> {
                    val files = storage
                    val result =
                        files?.remove(
                            asked.projects,
                            LinuxLocalStorage.DELETION_NAME.takeIf { asked.projects },
                        )
                    if (result is Outcome.Ok) {
                        closeFolders()
                        val presence = manager.presence()
                        ui.update {
                            it.copy(
                                presence = presence,
                                removalSize = null,
                                notice = LinuxHomeNotice.Removed,
                            )
                        }
                    } else
                        ui.update {
                            it.copy(notice = LinuxHomeNotice.StorageFailed, removalSize = null)
                        }
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

    /**
     * Repair and Reset first make sure setup or a package job is not running (said in place,
     * plainly).
     */
    private fun guarded(ask: () -> Unit) {
        if (ui.value.working) return
        scope.launch {
            when (val quiet = manager.requireQuiet()) {
                is LinuxAnswer.Problem ->
                    ui.update { it.copy(notice = LinuxHomeNotice.Problem(quiet.problem)) }
                is LinuxAnswer.Ok -> {
                    ui.update { it.copy(notice = null) }
                    ask()
                }
            }
        }
    }
}
