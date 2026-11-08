@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.feature.linux

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.engine.runtime.backup.LinuxBackupManifest
import io.github.eslamasabry.oc.engine.runtime.backup.LinuxBackupPhase
import io.github.eslamasabry.oc.engine.runtime.backup.LinuxBackupProblem
import io.github.eslamasabry.oc.engine.runtime.backup.LinuxBackupState
import io.github.eslamasabry.oc.engine.runtime.procs.LinuxStopOutcome
import io.github.eslamasabry.oc.engine.runtime.procs.LinuxStopResult
import io.github.eslamasabry.oc.engine.runtime.procs.LocalServerProcess
import io.github.eslamasabry.oc.engine.runtime.services.DevelopmentService
import io.github.eslamasabry.oc.engine.runtime.services.DevelopmentServiceStatus
import io.github.eslamasabry.oc.engine.runtime.services.ServiceRefusal
import io.github.eslamasabry.oc.engine.runtime.services.kept.KeptAct
import io.github.eslamasabry.oc.engine.runtime.services.kept.KeptField
import io.github.eslamasabry.oc.engine.runtime.services.kept.KeptPhase
import io.github.eslamasabry.oc.engine.runtime.services.kept.KeptService
import io.github.eslamasabry.oc.engine.runtime.services.kept.KeptServiceDef
import io.github.eslamasabry.oc.engine.runtime.setup.SetupProgress
import io.github.eslamasabry.oc.engine.runtime.setup.SetupState
import io.github.eslamasabry.oc.engine.runtime.storage.LinuxFolder
import io.github.eslamasabry.oc.engine.runtime.storage.LinuxRemovalSize
import io.github.eslamasabry.oc.engine.runtime.vitals.LinuxHeat
import io.github.eslamasabry.oc.feature.linux.host.LinuxPresence
import io.github.eslamasabry.oc.feature.linux.manager.LinuxCleanupId
import io.github.eslamasabry.oc.feature.linux.manager.LinuxJobKind
import io.github.eslamasabry.oc.feature.linux.manager.LinuxJobState
import io.github.eslamasabry.oc.feature.linux.manager.LinuxJobStatus
import io.github.eslamasabry.oc.feature.linux.manager.LinuxPackage
import io.github.eslamasabry.oc.feature.linux.manager.LinuxPort
import io.github.eslamasabry.oc.feature.linux.manager.LinuxProblem
import io.github.eslamasabry.oc.feature.linux.manager.LinuxStorageCategory
import io.github.eslamasabry.oc.feature.linux.manager.LinuxStorageReport
import io.github.eslamasabry.oc.feature.linux.manager.LinuxStorageScan
import io.github.eslamasabry.oc.feature.linux.manager.LinuxStorageScanState
import io.github.eslamasabry.oc.feature.linux.manager.PortOwner
import io.github.eslamasabry.oc.feature.linux.presentation.CleanupAsk
import io.github.eslamasabry.oc.feature.linux.presentation.CleanupResult
import io.github.eslamasabry.oc.feature.linux.presentation.KeptAsk
import io.github.eslamasabry.oc.feature.linux.presentation.KeptServicesActions
import io.github.eslamasabry.oc.feature.linux.presentation.KeptServicesUi
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxBackupChoice
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxBackupsActions
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxBackupsUi
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxConfirm
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxHomeActions
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxHomeNotice
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxHomeUi
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxIdleUi
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxPackagesActions
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxPackagesUi
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxPage
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxPortsActions
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxPortsUi
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxServiceAsk
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxServiceRow
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxServicesActions
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxServicesUi
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxStorageActions
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxStorageUi
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxVitalsUi
import io.github.eslamasabry.oc.feature.linux.presentation.PackagesConfirm
import io.github.eslamasabry.oc.feature.linux.presentation.PackagesNotice
import io.github.eslamasabry.oc.feature.linux.presentation.PortOwnerStop
import io.github.eslamasabry.oc.feature.linux.presentation.ServiceAskKind
import io.github.eslamasabry.oc.feature.linux.ui.KeptServicesScreen
import io.github.eslamasabry.oc.feature.linux.ui.LinuxBackupsScreen
import io.github.eslamasabry.oc.feature.linux.ui.LinuxHomeScreen
import io.github.eslamasabry.oc.feature.linux.ui.LinuxPackagesScreen
import io.github.eslamasabry.oc.feature.linux.ui.LinuxPortsScreen
import io.github.eslamasabry.oc.feature.linux.ui.LinuxServicesScreen
import io.github.eslamasabry.oc.feature.linux.ui.LinuxStorageScreen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlin.test.Test
import kotlin.time.Instant

/** Audit capture only: the Linux manager pages (home, ports, storage, packages, services, backups). */
class AuditCaptureTest {
    private object HomeA : LinuxHomeActions {
        override fun askStopJob() {}

        override fun dismissJob() {}

        override fun shown(visible: Boolean) {}

        override fun open(page: LinuxPage) {}

        override fun askRepair() {}

        override fun askReset() {}

        override fun startSetup() {}

        override fun addPython() {}

        override fun continueSetup() {}

        override fun refreshComponents() {}

        override fun askRemoveComponent(id: String) {}

        override fun openOwnershipSettings() {}

        override fun askStopSetup() {}

        override fun setIdleEnabled(enabled: Boolean) {}

        override fun askIdleMinutes() {}

        override fun finishIdleMinutes(value: String?) {}

        override fun resumeIdle() {}

        override fun refreshLifetimes() {}

        override fun askCreateProject() {}

        override fun finishCreateProject(path: String?) {}

        override fun browse(path: String) {}

        override fun closeFolders() {}

        override fun measureRemoval() {}

        override fun askRemove(projects: Boolean) {}

        override fun confirm() {}

        override fun dismiss() {}

        override fun back() {}
    }

    private object PortsA : LinuxPortsActions {
        override fun selectPort(port: Int) {}

        override fun copyAddress(port: Int) {}

        override fun askStopOwner(port: Int) {}

        override fun confirmStopOwner() {}

        override fun shown(visible: Boolean) {}

        override fun retry() {}

        override fun preview(port: Int) {}

        override fun readProgramLog(pid: Int) {}

        override fun askStopProgram(pid: Int) {}

        override fun confirmStopProgram() {}

        override fun dismiss() {}

        override fun back() {}
    }

    private object StorageA : LinuxStorageActions {
        override fun shown(visible: Boolean) {}

        override fun retry() {}

        override fun askCleanup(id: LinuxCleanupId) {}

        override fun confirm() {}

        override fun dismiss() {}

        override fun back() {}
    }

    private object PackagesA : LinuxPackagesActions {
        override fun shown(visible: Boolean) {}

        override fun retry() {}

        override fun search(text: String) {}

        override fun checkInstall(typed: String) {}

        override fun askRemove(name: String) {}

        override fun askUpdate() {}

        override fun confirm() {}

        override fun dismiss() {}

        override fun back() {}

        override fun askStopJob() {}

        override fun dismissJob() {}
    }

    private object ServicesA : LinuxServicesActions {
        override fun shown(visible: Boolean) {}

        override fun refresh() {}

        override fun add() {}

        override fun finishName(value: String?) {}

        override fun finishCommand(value: String?) {}

        override fun select(id: String) {}

        override fun start(id: String) {}

        override fun restart(id: String) {}

        override fun logs(id: String) {}

        override fun report(id: String) {}

        override fun ask(kind: ServiceAskKind, id: String) {}

        override fun confirm() {}

        override fun detectedLogs(id: String) {}

        override fun dismiss() {}

        override fun back() {}
    }

    private object KeptA : KeptServicesActions {
        override fun shown(visible: Boolean) {}

        override fun refresh() {}

        override fun select(name: String) {}

        override fun add() {}

        override fun save(name: String, command: String, directory: String, port: String, restart: Boolean) {}

        override fun act(name: String, action: KeptAct) {}

        override fun confirm() {}

        override fun searchLog(query: String) {}

        override fun copyLog() {}

        override fun preview(port: Int) {}

        override fun dismiss() {}

        override fun back() {}
    }

    private object BackupsA : LinuxBackupsActions {
        override fun choose(choice: LinuxBackupChoice) {}

        override fun go() {}

        override fun chooseBackup() {}

        override fun askStop() {}

        override fun confirm() {}

        override fun dismiss() {}

        override fun back() {}
    }

    // ---- Home

    private fun home(state: String, ui: LinuxHomeUi) {
        Audit.shoot("linux", "home", state) { LinuxHomeScreen(ui, HomeA) }
    }

    @Test
    fun homeStates() = Audit.guard("linux__home__build") {
        val ready = LinuxHomeUi(presence = LinuxPresence.Ready)
        home("checking", LinuxHomeUi(presence = null))
        home("ready", ready)
        home(
            "ready-vitals-idle",
            ready.copy(
                vitals = LinuxVitalsUi(
                    heat = LinuxHeat.Warm,
                    phoneFreeBytes = 12_000_000_000,
                    linuxMemoryBytes = 800_000_000,
                    linuxCpuPercent = 35,
                    heavyProgram = "node",
                    heavyPercent = 80,
                ),
                idle = LinuxIdleUi(supported = true, enabled = true, minutes = 10),
            ),
        )
        home("ready-too-hot", ready.copy(vitals = LinuxVitalsUi(heat = LinuxHeat.TooHot, phoneFreeBytes = 900_000_000)))
        home("ready-idle-stopped", ready.copy(idle = LinuxIdleUi(supported = true, enabled = true, minutes = 5, stopped = true)))
        home("ready-idle-failed", ready.copy(idle = LinuxIdleUi(supported = true, enabled = false, minutes = 5, failed = true)))
        home("ready-working", ready.copy(working = true))
        home("not-installed", LinuxHomeUi(presence = LinuxPresence.NotInstalled))
        home("not-on-this-build", LinuxHomeUi(presence = LinuxPresence.NotOnThisBuild))
        home(
            "ready-job-running",
            ready.copy(job = LinuxJobState(kind = LinuxJobKind.Install, names = listOf("htop"), status = LinuxJobStatus.Running, percent = 42, stage = "Unpacking")),
        )
        home(
            "ready-job-failed",
            ready.copy(job = LinuxJobState(kind = LinuxJobKind.Update, status = LinuxJobStatus.Failed, exitCode = 100)),
        )
        home("ready-setup-running", ready.copy(setup = SetupProgress(SetupState.Running, emptyList(), 0.4)))
        home("confirm-repair", ready.copy(confirm = LinuxConfirm.Repair))
        home("confirm-reset", ready.copy(confirm = LinuxConfirm.Reset))
        home("confirm-remove-projects", ready.copy(confirm = LinuxConfirm.Remove(projects = true, bytes = 1_500_000_000)))
        home("confirm-stop-setup", ready.copy(confirm = LinuxConfirm.StopSetup))
        home("confirm-stop-job", ready.copy(confirm = LinuxConfirm.StopJob(LinuxJobKind.Install, listOf("htop"))))
        home("notice-busy", ready.copy(notice = LinuxHomeNotice.Problem(LinuxProblem.Busy)))
        home("notice-reset-not-erased", ready.copy(notice = LinuxHomeNotice.ResetNotErased))
        home("notice-storage-failed", ready.copy(notice = LinuxHomeNotice.StorageFailed))
        home("notice-removed", ready.copy(notice = LinuxHomeNotice.Removed))
        home(
            "ready-removal-size",
            ready.copy(removalSize = LinuxRemovalSize(runtimeBytes = 1_200_000_000, projectsBytes = 300_000_000, measuredAtMilliseconds = 1L)),
        )
        home(
            "ready-folders",
            ready.copy(
                folderPath = "/root/projects",
                folders = listOf(
                    LinuxFolder("app", "/root/projects/app", true),
                    LinuxFolder("a-project-folder-with-a-very-long-name-that-goes-on-and-on", "/root/projects/long", false),
                ),
            ),
        )
        home("ready-folders-loading", ready.copy(folderPath = "/root/projects", folderLoading = true))
        home("ready-folders-failed", ready.copy(folderPath = "/root/projects", folderFailed = true))
        home("ready-ask-idle-minutes", ready.copy(idle = LinuxIdleUi(supported = true, enabled = true, minutes = 10), askingIdleMinutes = true))
        home("ready-ask-create-project", ready.copy(askingCreateProject = true))
    }

    // ---- Ports

    private fun ports(state: String, ui: LinuxPortsUi) {
        Audit.shoot("linux", "ports", state) { LinuxPortsScreen(ui, PortsA) }
    }

    @Test
    fun portsStates() = Audit.guard("linux__ports__build") {
        val list = persistentListOf(
            LinuxPort(4097, PortOwner(91, "opencode serve --port 4097"), allInterfaces = true),
            LinuxPort(5173, PortOwner(92, "node /project/vite.js --port 5173"), allInterfaces = false),
            LinuxPort(8080, null, null),
        )
        ports("loading", LinuxPortsUi(loading = true))
        ports("empty", LinuxPortsUi(loading = false))
        ports("list", LinuxPortsUi(loading = false, ports = list))
        ports("failed-first-read", LinuxPortsUi(loading = false, problem = LinuxProblem.Failed))
        ports("failed-with-list", LinuxPortsUi(loading = false, problem = LinuxProblem.Failed, ports = list))
        ports("busy", LinuxPortsUi(loading = false, problem = LinuxProblem.Busy))
        ports("preview-failed", LinuxPortsUi(loading = false, ports = list, previewFailed = true))
        ports("port-menu", LinuxPortsUi(loading = false, ports = list, selectedPort = 5173))
        ports("stop-owner-ask", LinuxPortsUi(loading = false, ports = list, selectedPort = 5173, ownerStopAsk = PortOwnerStop("vite", listOf("92:1"))))
        ports("stopping", LinuxPortsUi(loading = false, ports = list, ownerStopping = true))
        ports("stop-result", LinuxPortsUi(loading = false, ports = list, ownerStopResult = LinuxStopResult(mapOf("92:1" to LinuxStopOutcome.Stopped))))
        val program = LocalServerProcess(pid = 120, command = "python3 -m http.server 8000", ports = listOf(8000), cwd = "/root/projects/site", logPath = "/root/site.log")
        ports("programs", LinuxPortsUi(loading = false, ports = list, programs = persistentListOf(program), programsScanned = true))
        ports("programs-scanning", LinuxPortsUi(loading = false, ports = list, scanningPrograms = true))
        ports("programs-failed", LinuxPortsUi(loading = false, ports = list, programsFailed = true))
        ports("program-stop-ask", LinuxPortsUi(loading = false, ports = list, programs = persistentListOf(program), programsScanned = true, stopAsk = program))
        ports("program-log", LinuxPortsUi(loading = false, ports = list, programs = persistentListOf(program), programsScanned = true, logPid = 120, logText = "Serving HTTP on 0.0.0.0 port 8000\nGET / 200"))
        ports("program-log-failed", LinuxPortsUi(loading = false, ports = list, programs = persistentListOf(program), programsScanned = true, logPid = 120, logFailed = true))
    }

    // ---- Storage

    private fun storage(state: String, ui: LinuxStorageUi) {
        Audit.shoot("linux", "storage", state) { LinuxStorageScreen(ui, StorageA) }
    }

    @Test
    fun storageStates() = Audit.guard("linux__storage__build") {
        val report = LinuxStorageReport(
            mapOf(
                LinuxStorageCategory.Base to 1_200_000_000L,
                LinuxStorageCategory.Packages to 300_000_000L,
                LinuxStorageCategory.Tools to 900_000_000L,
                LinuxStorageCategory.Agents to 400_000_000L,
                LinuxStorageCategory.Projects to 2_500_000_000L,
                LinuxStorageCategory.Temp to 50_000_000L,
            ),
        )
        val cleanable = persistentMapOf(
            LinuxCleanupId.AptCache to 200_000_000L,
            LinuxCleanupId.ToolCaches to 120_000_000L,
            LinuxCleanupId.TempFiles to 50_000_000L,
            LinuxCleanupId.OldAgentVersions to 0L,
        )
        storage("loading", LinuxStorageUi(loading = true))
        storage("scanning", LinuxStorageUi(loading = true, scan = LinuxStorageScan(LinuxStorageScanState.Running, listOf(LinuxStorageCategory.Base, LinuxStorageCategory.Packages))))
        storage("report", LinuxStorageUi(loading = false, report = report, cleanable = cleanable))
        storage("nothing-to-clean", LinuxStorageUi(loading = false, report = report))
        storage("failed", LinuxStorageUi(loading = false, problem = LinuxProblem.Failed))
        storage("busy", LinuxStorageUi(loading = false, problem = LinuxProblem.Busy, report = report, cleanable = cleanable))
        storage("confirm-clean", LinuxStorageUi(loading = false, report = report, cleanable = cleanable, confirm = CleanupAsk(LinuxCleanupId.AptCache, 200_000_000)))
        storage("cleaning", LinuxStorageUi(loading = false, report = report, cleanable = cleanable, cleaning = LinuxCleanupId.AptCache))
        storage("cleaned", LinuxStorageUi(loading = false, report = report, cleanable = cleanable, result = CleanupResult.Freed(200_000_000)))
        storage("cleaned-nothing", LinuxStorageUi(loading = false, report = report, cleanable = cleanable, result = CleanupResult.Nothing))
        storage("clean-failed", LinuxStorageUi(loading = false, report = report, cleanable = cleanable, actProblem = LinuxProblem.Failed))
    }

    // ---- Packages

    private fun packages(state: String, ui: LinuxPackagesUi) {
        Audit.shoot("linux", "packages", state) { LinuxPackagesScreen(ui, PackagesA) }
    }

    @Test
    fun packagesStates() = Audit.guard("linux__packages__build") {
        val list = persistentListOf(
            LinuxPackage("git", "1:2.43.0-1ubuntu7", 18_000),
            LinuxPackage("htop", "3.3.0-4build1", 420),
            LinuxPackage("bash", "5.2.21-2ubuntu4", 1_800, essential = true),
            LinuxPackage("nodejs", "24.21.0", 190_000, required = true),
            LinuxPackage("a-package-with-a-really-long-name-that-keeps-going-and-going", "0.0.1~rc1+really.long.version.string", 12),
        )
        packages("loading", LinuxPackagesUi(loading = true))
        packages("empty", LinuxPackagesUi(loading = false))
        packages("list", LinuxPackagesUi(loading = false, packages = list))
        packages("search-no-match", LinuxPackagesUi(loading = false, packages = list, query = "zzzz"))
        packages("failed", LinuxPackagesUi(loading = false, problem = LinuxProblem.Failed))
        packages("busy-with-list", LinuxPackagesUi(loading = false, problem = LinuxProblem.Busy, packages = list))
        packages("checking", LinuxPackagesUi(loading = false, packages = list, checking = true))
        packages("job-running", LinuxPackagesUi(loading = false, packages = list, job = LinuxJobState(kind = LinuxJobKind.Install, names = listOf("htop"), status = LinuxJobStatus.Running, percent = 60, stage = "Unpacking")))
        packages("job-done", LinuxPackagesUi(loading = false, packages = list, job = LinuxJobState(kind = LinuxJobKind.Install, names = listOf("htop"), status = LinuxJobStatus.Done, exitCode = 0)))
        packages("job-failed", LinuxPackagesUi(loading = false, packages = list, job = LinuxJobState(kind = LinuxJobKind.Remove, names = listOf("htop"), status = LinuxJobStatus.Failed, exitCode = 100)))
        packages("confirm-install", LinuxPackagesUi(loading = false, packages = list, confirm = PackagesConfirm.Install(listOf("htop", "tmux"))))
        packages("confirm-remove", LinuxPackagesUi(loading = false, packages = list, confirm = PackagesConfirm.Remove("htop", emptyList())))
        packages("confirm-remove-also", LinuxPackagesUi(loading = false, packages = list, confirm = PackagesConfirm.Remove("git", listOf("git-man", "liberror-perl"))))
        packages("confirm-update", LinuxPackagesUi(loading = false, packages = list, confirm = PackagesConfirm.Update))
        packages("confirm-stop-job", LinuxPackagesUi(loading = false, packages = list, confirm = PackagesConfirm.StopJob(LinuxJobKind.Install, listOf("htop"))))
        packages("notice-already-installed", LinuxPackagesUi(loading = false, packages = list, notice = PackagesNotice.AlreadyInstalled("git")))
        packages("notice-unknown", LinuxPackagesUi(loading = false, packages = list, notice = PackagesNotice.Unknown("htopp")))
        packages("notice-blocked-plan", LinuxPackagesUi(loading = false, packages = list, notice = PackagesNotice.BlockedPlan("nodejs")))
        packages("notice-problem", LinuxPackagesUi(loading = false, packages = list, notice = PackagesNotice.Problem(LinuxProblem.Failed)))
    }

    // ---- Services (project commands)

    private fun services(state: String, ui: LinuxServicesUi) {
        Audit.shoot("linux", "services", state) { LinuxServicesScreen(ui, ServicesA) }
    }

    private fun row(id: String, name: String, command: String, status: DevelopmentServiceStatus) =
        LinuxServiceRow(
            service = DevelopmentService(id, name, command, "/root/projects/app", "http://127.0.0.1:5173/"),
            status = status,
            canStart = status != DevelopmentServiceStatus.Running,
            canStop = status == DevelopmentServiceStatus.Running,
            canReport = status == DevelopmentServiceStatus.Stopped,
        )

    @Test
    fun servicesStates() = Audit.guard("linux__services__build") {
        val dir = "/root/projects/app"
        val rows = persistentListOf(
            row("1", "Web", "npm run dev", DevelopmentServiceStatus.Running),
            row("2", "Tests", "npm test -- --watch", DevelopmentServiceStatus.Stopped),
            row("3", "API", "python3 -m http.server 8000", DevelopmentServiceStatus.NotStarted),
            row("4", "Unknown state", "sleep 1000", DevelopmentServiceStatus.Unknown),
            row("5", "A service with a very long name that should wrap or truncate", "npm run a-very-long-command-line --with --many --arguments --that --go --on", DevelopmentServiceStatus.Running),
        )
        services("loading", LinuxServicesUi(directory = dir, loading = true))
        services("unsupported", LinuxServicesUi(directory = dir, loading = false, supported = false))
        services("empty", LinuxServicesUi(directory = dir, loading = false, supported = true))
        services("list", LinuxServicesUi(directory = dir, loading = false, supported = true, services = rows))
        services("working", LinuxServicesUi(directory = dir, loading = false, supported = true, services = rows, working = true))
        services("corrupt", LinuxServicesUi(directory = dir, loading = false, supported = true, readable = false))
        services("failure", LinuxServicesUi(directory = dir, loading = false, supported = true, services = rows, failure = Failure.Unknown()))
        services("refusal", LinuxServicesUi(directory = dir, loading = false, supported = true, services = rows, refusal = ServiceRefusal.OwnershipNotConfirmed))
        services("selected-running", LinuxServicesUi(directory = dir, loading = false, supported = true, services = rows, selected = "1"))
        services("selected-stopped", LinuxServicesUi(directory = dir, loading = false, supported = true, services = rows, selected = "2"))
        services("selected-log", LinuxServicesUi(directory = dir, loading = false, supported = true, services = rows, selected = "1", log = "VITE ready in 312 ms\nLocal: http://127.0.0.1:5173/"))
        services("ask-stop", LinuxServicesUi(directory = dir, loading = false, supported = true, services = rows, selected = "1", ask = LinuxServiceAsk(ServiceAskKind.Stop, "1")))
        services("ask-remove", LinuxServicesUi(directory = dir, loading = false, supported = true, services = rows, selected = "2", ask = LinuxServiceAsk(ServiceAskKind.Remove, "2")))
    }

    // ---- Kept services

    private fun kept(state: String, ui: KeptServicesUi) {
        Audit.shoot("linux", "kept-services", state) { KeptServicesScreen(ui, KeptA) }
    }

    private fun keptService(name: String, phase: KeptPhase, port: Int? = 5173, command: String = "npm run dev", wanted: Boolean = true) =
        KeptService(
            KeptServiceDef(name, command, title = name.replaceFirstChar { it.uppercase() }, port = port),
            wanted = wanted,
            phase = phase,
            since = Instant.fromEpochMilliseconds(0),
        )

    @Test
    fun keptServicesStates() = Audit.guard("linux__kept-services__build") {
        val all = persistentListOf(
            keptService("web", KeptPhase.Running),
            keptService("api", KeptPhase.Backoff, port = 8080).copy(lastExit = 3, nextTry = Instant.fromEpochMilliseconds(250_000)),
            keptService("worker", KeptPhase.Failed, port = null, command = "node worker.js").copy(restarts = 5),
            keptService("db", KeptPhase.PortBusy, port = 5432),
            keptService("cron", KeptPhase.Exited, port = null).copy(lastExit = 0),
            keptService("docs", KeptPhase.Off, wanted = false),
            keptService("starting", KeptPhase.Starting),
            keptService("stopping", KeptPhase.Stopping),
        )
        kept("loading", KeptServicesUi(loading = true))
        kept("empty", KeptServicesUi(loading = false))
        kept("list", KeptServicesUi(loading = false, services = all, nowMs = 200_000))
        kept("working", KeptServicesUi(loading = false, services = all, nowMs = 200_000, working = true))
        kept("failure", KeptServicesUi(loading = false, services = all, nowMs = 200_000, failure = Failure.Device()))
        kept("selected-running", KeptServicesUi(loading = false, services = all, selected = "web", nowMs = 200_000, log = "VITE ready in 312 ms\nLocal: http://127.0.0.1:5173/"))
        kept("selected-failed", KeptServicesUi(loading = false, services = all, selected = "worker", nowMs = 200_000, log = "::svc exit 2\nError: cannot find module"))
        kept("selected-exited", KeptServicesUi(loading = false, services = all, selected = "cron", nowMs = 200_000, log = "::svc exit 0"))
        kept("log-search", KeptServicesUi(loading = false, services = all, selected = "web", nowMs = 200_000, log = "VITE ready\nLocal: http://127.0.0.1:5173/\nGET / 200", logQuery = "GET"))
        kept("ask-stop", KeptServicesUi(loading = false, services = all, selected = "web", nowMs = 200_000, ask = KeptAsk("web", KeptAct.Stop)))
        kept("ask-remove", KeptServicesUi(loading = false, services = all, selected = "docs", nowMs = 200_000, ask = KeptAsk("docs", KeptAct.Remove)))
        kept("adding", KeptServicesUi(loading = false, services = all, nowMs = 200_000, adding = true))
        kept("adding-invalid-name", KeptServicesUi(loading = false, services = all, nowMs = 200_000, adding = true, invalid = KeptField.Name))
        kept("adding-limit", KeptServicesUi(loading = false, services = all, nowMs = 200_000, adding = true, invalid = KeptField.Limit))
    }

    // ---- Backups

    private fun backups(state: String, ui: LinuxBackupsUi) {
        Audit.shoot("linux", "backups", state) { LinuxBackupsScreen(ui, BackupsA) }
    }

    @Test
    fun backupsStates() = Audit.guard("linux__backups__build") {
        backups("idle", LinuxBackupsUi())
        backups("choice-setup", LinuxBackupsUi(choice = LinuxBackupChoice.Setup))
        backups("choice-projects", LinuxBackupsUi(choice = LinuxBackupChoice.ProjectsOnly))
        backups("preparing", LinuxBackupsUi(backup = LinuxBackupState(phase = LinuxBackupPhase.Preparing)))
        backups("saving", LinuxBackupsUi(backup = LinuxBackupState(phase = LinuxBackupPhase.Saving, bytes = 300_000_000, total = 900_000_000, percent = 33)))
        backups("saving-ask-stop", LinuxBackupsUi(backup = LinuxBackupState(phase = LinuxBackupPhase.Saving, bytes = 300_000_000, total = 900_000_000, percent = 33), askingStop = true))
        backups("checking", LinuxBackupsUi(backup = LinuxBackupState(phase = LinuxBackupPhase.Checking)))
        backups("ready-to-restore", LinuxBackupsUi(backup = LinuxBackupState(phase = LinuxBackupPhase.Ready, manifest = LinuxBackupManifest("checked-backup", "Nov 6, 2026", true))))
        backups("restoring", LinuxBackupsUi(backup = LinuxBackupState(phase = LinuxBackupPhase.Restoring, percent = 50)))
        backups("saved", LinuxBackupsUi(backup = LinuxBackupState(phase = LinuxBackupPhase.Saved, name = "oc-backup-2026-11-06.tar.gz")))
        backups("restored", LinuxBackupsUi(backup = LinuxBackupState(phase = LinuxBackupPhase.Restored)))
        backups("failed-save", LinuxBackupsUi(backup = LinuxBackupState(phase = LinuxBackupPhase.Failed, problem = LinuxBackupProblem.Save)))
        backups("failed-not-backup", LinuxBackupsUi(backup = LinuxBackupState(phase = LinuxBackupPhase.Failed, problem = LinuxBackupProblem.NotBackup)))
        backups("failed-newer", LinuxBackupsUi(backup = LinuxBackupState(phase = LinuxBackupPhase.Failed, problem = LinuxBackupProblem.Newer)))
        backups("failed-busy", LinuxBackupsUi(backup = LinuxBackupState(phase = LinuxBackupPhase.Failed, problem = LinuxBackupProblem.Busy)))
        backups("failed-restore", LinuxBackupsUi(backup = LinuxBackupState(phase = LinuxBackupPhase.Failed, problem = LinuxBackupProblem.Restore, failure = Failure.Unknown())))
    }
}
