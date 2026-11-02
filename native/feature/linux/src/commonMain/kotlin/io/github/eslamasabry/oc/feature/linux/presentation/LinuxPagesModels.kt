package io.github.eslamasabry.oc.feature.linux.presentation

import io.github.eslamasabry.oc.core.common.pace.Pacer
import io.github.eslamasabry.oc.feature.linux.manager.LinuxAnswer
import io.github.eslamasabry.oc.feature.linux.manager.LinuxCleanupId
import io.github.eslamasabry.oc.feature.linux.manager.LinuxJobKind
import io.github.eslamasabry.oc.feature.linux.manager.LinuxJobState
import io.github.eslamasabry.oc.feature.linux.manager.LinuxManager
import io.github.eslamasabry.oc.feature.linux.manager.LinuxPackage
import io.github.eslamasabry.oc.feature.linux.manager.LinuxPackages
import io.github.eslamasabry.oc.feature.linux.manager.LinuxPort
import io.github.eslamasabry.oc.feature.linux.manager.LinuxProblem
import io.github.eslamasabry.oc.feature.linux.manager.LinuxStorage
import io.github.eslamasabry.oc.feature.linux.manager.LinuxStorageReport
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- Ports (Flutter LinuxPortsPage)

public data class LinuxPortsUi(
    val loading: Boolean = true,
    /** The first read failed: said with Try again. Null when it worked. */
    val problem: LinuxProblem? = null,
    val ports: ImmutableList<LinuxPort> = persistentListOf(),
    /** The browser did not open for the preview. */
    val previewFailed: Boolean = false,
)

public interface LinuxPortsActions {
    public fun shown(visible: Boolean)

    public fun retry()

    public fun preview(port: Int)

    public fun dismiss()

    public fun back()
}

public class LinuxPortsModel(
    private val manager: LinuxManager,
    private val opener: PortPreviewOpener,
    private val nav: LinuxNavigator,
    private val scope: CoroutineScope,
) : LinuxPortsActions {
    private val ui = MutableStateFlow(LinuxPortsUi())
    public val state: StateFlow<LinuxPortsUi> = ui.asStateFlow()

    override fun shown(visible: Boolean) {
        if (visible) retry()
    }

    override fun retry() {
        scope.launch {
            ui.update { it.copy(loading = true) }
            when (val read = manager.scanPorts()) {
                is LinuxAnswer.Ok -> ui.update { it.copy(loading = false, problem = null, ports = read.value.toImmutableList()) }
                is LinuxAnswer.Problem -> ui.update { it.copy(loading = false, problem = read.problem) }
            }
        }
    }

    /** Only a port the probe reached and that serves pages; the address is app-authored loopback. */
    override fun preview(port: Int) {
        val target = ui.value.ports.firstOrNull { it.port == port && it.canPreview } ?: return
        ui.update { it.copy(previewFailed = !opener.open(target.previewUrl)) }
    }

    override fun dismiss() {
        ui.update { it.copy(previewFailed = false) }
    }

    override fun back(): Unit = nav.back()
}

// ---------------------------------------------------------------- Storage (Flutter LinuxStoragePage)

/** The cleanup a confirm asks about, with the bytes it would give back now. */
public data class CleanupAsk(val id: LinuxCleanupId, val bytes: Long)

public sealed interface CleanupResult {
    public data class Freed(val bytes: Long) : CleanupResult

    public data object Nothing : CleanupResult
}

public data class LinuxStorageUi(
    val loading: Boolean = true,
    val problem: LinuxProblem? = null,
    val report: LinuxStorageReport? = null,
    /** Bytes each cleanup would give back now; absent while measuring. */
    val cleanable: ImmutableMap<LinuxCleanupId, Long> = persistentMapOf(),
    val confirm: CleanupAsk? = null,
    val cleaning: LinuxCleanupId? = null,
    val result: CleanupResult? = null,
    /** A cleanup that could not run, said in place. */
    val actProblem: LinuxProblem? = null,
)

public interface LinuxStorageActions {
    public fun shown(visible: Boolean)

    public fun retry()

    public fun askCleanup(id: LinuxCleanupId)

    public fun confirm()

    public fun dismiss()

    public fun back()
}

public class LinuxStorageModel(
    private val manager: LinuxManager,
    private val nav: LinuxNavigator,
    private val scope: CoroutineScope,
) : LinuxStorageActions {
    private val ui = MutableStateFlow(LinuxStorageUi())
    public val state: StateFlow<LinuxStorageUi> = ui.asStateFlow()

    override fun shown(visible: Boolean) {
        if (visible && ui.value.report == null) retry()
    }

    override fun retry() {
        scope.launch { load() }
    }

    private suspend fun load() {
        ui.update { it.copy(loading = true) }
        when (val read = manager.measureStorage()) {
            is LinuxAnswer.Problem -> {
                ui.update { it.copy(loading = false, problem = read.problem) }
                return
            }
            is LinuxAnswer.Ok -> ui.update { it.copy(loading = false, problem = null, report = read.value) }
        }
        val measured = mutableMapOf<LinuxCleanupId, Long>()
        for (action in LinuxStorage.cleanups) {
            val bytes = manager.cleanableBytes(action)
            if (bytes is LinuxAnswer.Ok) measured[action.id] = bytes.value
            ui.update { it.copy(cleanable = measured.toImmutableMap()) }
        }
    }

    override fun askCleanup(id: LinuxCleanupId) {
        if (ui.value.cleaning != null) return
        val bytes = ui.value.cleanable[id] ?: return
        ui.update { it.copy(confirm = CleanupAsk(id, bytes), result = null, actProblem = null) }
    }

    override fun confirm() {
        val ask = ui.value.confirm ?: return
        val action = LinuxStorage.cleanups.first { it.id == ask.id }
        ui.update { it.copy(confirm = null, cleaning = ask.id) }
        scope.launch {
            when (val done = manager.cleanup(action)) {
                is LinuxAnswer.Ok ->
                    ui.update { it.copy(cleaning = null, result = if (done.value > 0) CleanupResult.Freed(done.value) else CleanupResult.Nothing) }
                is LinuxAnswer.Problem -> ui.update { it.copy(cleaning = null, actProblem = done.problem) }
            }
            load()
        }
    }

    override fun dismiss() {
        ui.update { it.copy(confirm = null, result = null, actProblem = null) }
    }

    override fun back(): Unit = nav.back()
}

// ---------------------------------------------------------------- Packages (Flutter LinuxPackagesPage)

/** What the packages page asks before acting. */
public sealed interface PackagesConfirm {
    public data class Install(val names: List<String>) : PackagesConfirm

    /** [alsoRemoved] are the other packages apt would take with it (read before asking). */
    public data class Remove(val name: String, val alsoRemoved: List<String>) : PackagesConfirm

    public data object Update : PackagesConfirm

    public data class StopJob(val kind: LinuxJobKind, val names: List<String>) : PackagesConfirm
}

/** One line said in place under the install field or the list. */
public sealed interface PackagesNotice {
    public data object InvalidName : PackagesNotice

    public data class AlreadyInstalled(val name: String) : PackagesNotice

    public data class Unknown(val name: String) : PackagesNotice

    /** Removing it would also remove [needed], which Linux needs. */
    public data class BlockedPlan(val needed: String) : PackagesNotice

    public data class Problem(val problem: LinuxProblem) : PackagesNotice
}

public data class LinuxPackagesUi(
    val loading: Boolean = true,
    val problem: LinuxProblem? = null,
    val packages: ImmutableList<LinuxPackage> = persistentListOf(),
    val query: String = "",
    val job: LinuxJobState = LinuxJobState.None,
    val checking: Boolean = false,
    val confirm: PackagesConfirm? = null,
    val notice: PackagesNotice? = null,
) {
    /** The installed list filtered by the search text (name contains it). */
    val shown: List<LinuxPackage>
        get() = if (query.isBlank()) packages else packages.filter { it.name.contains(query.trim().lowercase()) }

    /** One job at a time: the acts that start one wait while it runs. */
    val canStartJob: Boolean get() = !job.running && !checking
}

public interface LinuxPackagesActions : LinuxJobActions {
    public fun shown(visible: Boolean)

    public fun retry()

    public fun search(text: String)

    /** "Check" for the typed names: valid names only, then what apt knows, then the install confirm. */
    public fun checkInstall(typed: String)

    public fun askRemove(name: String)

    public fun askUpdate()

    public fun confirm()

    public fun dismiss()

    public fun back()
}

public class LinuxPackagesModel(
    private val manager: LinuxManager,
    private val watch: LinuxJobWatch,
    private val nav: LinuxNavigator,
    private val scope: CoroutineScope,
    private val pacer: Pacer,
) : LinuxPackagesActions {
    private val ui = MutableStateFlow(LinuxPackagesUi())
    public val state: StateFlow<LinuxPackagesUi> = ui.asStateFlow()
    private val visible = MutableStateFlow(false)

    init {
        scope.launch {
            var wasRunning = false
            watch.job.collect { job ->
                ui.update { it.copy(job = job) }
                // A job that just ended changed what is installed.
                if (wasRunning && !job.running) load()
                wasRunning = job.running
            }
        }
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
                watch.refresh()
                if (ui.value.packages.isEmpty()) load()
            }
        }
    }

    override fun retry() {
        scope.launch { load() }
    }

    private suspend fun load() {
        ui.update { it.copy(loading = true) }
        when (val read = manager.installedPackages()) {
            is LinuxAnswer.Ok -> ui.update { it.copy(loading = false, problem = null, packages = read.value.toImmutableList()) }
            is LinuxAnswer.Problem -> ui.update { it.copy(loading = false, problem = read.problem) }
        }
    }

    override fun search(text: String) {
        ui.update { it.copy(query = text) }
    }

    override fun checkInstall(typed: String) {
        if (!ui.value.canStartJob) return
        val names = LinuxPackages.parseTyped(typed)
        if (names == null) {
            ui.update { it.copy(notice = PackagesNotice.InvalidName) }
            return
        }
        ui.update { it.copy(checking = true, notice = null) }
        scope.launch {
            val notice: PackagesNotice? =
                when (val read = manager.checkPackages(names)) {
                    is LinuxAnswer.Problem -> PackagesNotice.Problem(read.problem)
                    is LinuxAnswer.Ok -> {
                        val byName = read.value.associateBy { it.name }
                        val unknown = names.firstOrNull { byName[it]?.available != true }
                        val installed = names.firstOrNull { byName[it]?.installed == true }
                        when {
                            unknown != null -> PackagesNotice.Unknown(unknown)
                            installed != null && names.size == 1 -> PackagesNotice.AlreadyInstalled(installed)
                            else -> null
                        }
                    }
                }
            val wanted = names.filterNot { name -> ui.value.packages.any { it.name == name } }.ifEmpty { names }
            ui.update {
                it.copy(checking = false, notice = notice, confirm = if (notice == null) PackagesConfirm.Install(wanted) else null)
            }
        }
    }

    override fun askRemove(name: String) {
        val pkg = ui.value.packages.firstOrNull { it.name == name } ?: return
        if (pkg.locked || !ui.value.canStartJob) return
        ui.update { it.copy(checking = true, notice = null) }
        scope.launch {
            when (val plan = manager.planRemoval(listOf(name))) {
                is LinuxAnswer.Problem -> ui.update { it.copy(checking = false, notice = PackagesNotice.Problem(plan.problem)) }
                is LinuxAnswer.Ok -> {
                    val others = plan.value.filter { it != name }
                    val needed = others.firstOrNull { other -> ui.value.packages.firstOrNull { it.name == other }?.locked == true || other in LinuxPackages.kept }
                    ui.update {
                        if (needed != null) {
                            it.copy(checking = false, notice = PackagesNotice.BlockedPlan(needed))
                        } else {
                            it.copy(checking = false, confirm = PackagesConfirm.Remove(name, others))
                        }
                    }
                }
            }
        }
    }

    override fun askUpdate() {
        if (ui.value.canStartJob) ui.update { it.copy(confirm = PackagesConfirm.Update, notice = null) }
    }

    override fun askStopJob() {
        val job = ui.value.job
        val kind = job.kind ?: return
        if (job.running) ui.update { it.copy(confirm = PackagesConfirm.StopJob(kind, job.names)) }
    }

    override fun dismissJob() {
        scope.launch { watch.dismiss() }
    }

    override fun confirm() {
        val asked = ui.value.confirm ?: return
        ui.update { it.copy(confirm = null) }
        scope.launch {
            val answer =
                when (asked) {
                    is PackagesConfirm.Install -> watch.start(LinuxJobKind.Install, asked.names)
                    is PackagesConfirm.Remove -> watch.start(LinuxJobKind.Remove, listOf(asked.name))
                    PackagesConfirm.Update -> watch.start(LinuxJobKind.Update, emptyList())
                    is PackagesConfirm.StopJob -> watch.stop()
                }
            if (answer is LinuxAnswer.Problem) ui.update { it.copy(notice = PackagesNotice.Problem(answer.problem)) }
        }
    }

    override fun dismiss() {
        ui.update { it.copy(confirm = null, notice = null) }
    }

    override fun back(): Unit = nav.back()
}
