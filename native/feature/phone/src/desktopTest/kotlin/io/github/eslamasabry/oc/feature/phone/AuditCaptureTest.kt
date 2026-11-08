package io.github.eslamasabry.oc.feature.phone

import androidx.compose.ui.test.ExperimentalTestApi
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.engine.runtime.procs.CpuLevel
import io.github.eslamasabry.oc.engine.runtime.procs.CpuWords
import io.github.eslamasabry.oc.engine.runtime.procs.RunningGroupKind
import io.github.eslamasabry.oc.feature.phone.presentation.AboutWait
import io.github.eslamasabry.oc.feature.phone.presentation.AnswerHeadline
import io.github.eslamasabry.oc.feature.phone.presentation.AnswerLine
import io.github.eslamasabry.oc.feature.phone.presentation.AnswerTone
import io.github.eslamasabry.oc.feature.phone.presentation.ChildLimitCardKind
import io.github.eslamasabry.oc.feature.phone.presentation.KeepAliveSetting
import io.github.eslamasabry.oc.feature.phone.presentation.KeepAliveStepKind
import io.github.eslamasabry.oc.feature.phone.presentation.KeepAliveStepUi
import io.github.eslamasabry.oc.feature.phone.presentation.KeepRunningUi
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneAct
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneBudgetActions
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneDetailsUi
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneMaker
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneOp
import io.github.eslamasabry.oc.feature.phone.presentation.ProcessKind
import io.github.eslamasabry.oc.feature.phone.presentation.ProcessOrphanReason
import io.github.eslamasabry.oc.feature.phone.presentation.ProcessRole
import io.github.eslamasabry.oc.feature.phone.presentation.ProcessRowUi
import io.github.eslamasabry.oc.feature.phone.presentation.RunningActions
import io.github.eslamasabry.oc.feature.phone.presentation.RunningRowUi
import io.github.eslamasabry.oc.feature.phone.presentation.RunningUi
import io.github.eslamasabry.oc.feature.phone.presentation.ServerRow
import io.github.eslamasabry.oc.feature.phone.presentation.ThermalKeepRunningActions
import io.github.eslamasabry.oc.feature.phone.presentation.ThisPhoneActions
import io.github.eslamasabry.oc.feature.phone.presentation.ThisPhoneAnswer
import io.github.eslamasabry.oc.feature.phone.presentation.ThisPhoneConfirm
import io.github.eslamasabry.oc.feature.phone.presentation.ThisPhoneNotice
import io.github.eslamasabry.oc.feature.phone.presentation.ThisPhoneUi
import io.github.eslamasabry.oc.feature.phone.presentation.UpdateRow
import io.github.eslamasabry.oc.feature.phone.presentation.VersionRowUi
import io.github.eslamasabry.oc.feature.phone.storage.ProjectExportProblem
import io.github.eslamasabry.oc.feature.phone.storage.ProjectExportProgress
import io.github.eslamasabry.oc.feature.phone.storage.ProjectExportReceipt
import io.github.eslamasabry.oc.feature.phone.storage.ProjectStorageActions
import io.github.eslamasabry.oc.feature.phone.storage.ProjectStorageFacts
import io.github.eslamasabry.oc.feature.phone.storage.ProjectStorageUi
import io.github.eslamasabry.oc.feature.phone.storage.SharedFolderState
import io.github.eslamasabry.oc.feature.phone.storage.SharedFoldersActions
import io.github.eslamasabry.oc.feature.phone.storage.SharedFoldersScreen
import io.github.eslamasabry.oc.feature.phone.storage.SharedFoldersUi
import io.github.eslamasabry.oc.feature.phone.storage.StorageNotice
import io.github.eslamasabry.oc.feature.phone.storage.StoredProject
import io.github.eslamasabry.oc.feature.phone.storage.ProjectStorageScreen
import io.github.eslamasabry.oc.feature.phone.ui.KeepRunningScreen
import io.github.eslamasabry.oc.feature.phone.ui.RunningScreen
import io.github.eslamasabry.oc.feature.phone.ui.ThisPhoneScreen
import kotlin.test.Test
import kotlinx.collections.immutable.persistentListOf

/** Audit capture only: This phone, Running, Keep running and the storage pages in every state their models allow. */
@OptIn(ExperimentalTestApi::class)
class AuditCaptureTest {
    private val m = "phone"

    private object PhoneCalls : ThisPhoneActions {
        override fun act(act: PhoneAct) {}
        override fun dismissRecovery() {}
        override fun refreshStorage() {}
        override fun stopServer() {}
        override fun askRestart() {}
        override fun askUpdate() {}
        override fun confirm() {}
        override fun dismiss() {}
        override fun measureStorage() {}
        override fun openRunning() {}
        override fun openKeepRunning() {}
        override fun back() {}
        override fun shown(visible: Boolean) {}
    }

    private object RunningCalls : RunningActions {
        override fun shown(visible: Boolean) {}
        override fun retry() {}
        override fun details(pid: Int) {}
        override fun askStop(pid: Int) {}
        override fun confirmStop() {}
        override fun dismiss() {}
        override fun openChildLimitSettings() {}
        override fun hideChildLimit() {}
        override fun back() {}
    }

    private object KeepCalls : ThermalKeepRunningActions, PhoneBudgetActions {
        override fun resumed() {}
        override fun open(step: KeepAliveStepKind) {}
        override fun back() {}
        override fun setThermalEnabled(value: Boolean) {}
        override fun setFullSpeed(enabled: Boolean) {}
    }

    private object ProjectStorageCalls : ProjectStorageActions {
        override fun load() {}
        override fun setIncludePrivate(value: Boolean) {}
        override fun export() {}
        override fun stopExport() {}
        override fun clearCache() {}
        override fun askDelete() {}
        override fun confirmDelete() {}
        override fun dismiss() {}
        override fun back() {}
    }

    private object SharedCalls : SharedFoldersActions {
        override fun refresh() {}
        override fun allowAndroidAccess() {}
        override fun allowAllFolders(value: Boolean) {}
        override fun openThisPhone() {}
        override fun back() {}
    }

    // ---- This phone

    private val details = PhoneDetailsUi(installed = true, installedNames = persistentListOf("Linux base", "Git and SSH", "Node.js", "OpenCode"))

    private val running =
        ThisPhoneUi(
            answer = ThisPhoneAnswer(AnswerTone.Ok, AnswerHeadline.Running, AnswerLine.Storage(700L * 1024 * 1024)),
            details = details,
            version = VersionRowUi("1.18.29", UpdateRow.Available("2.1.1")),
            serverRows = persistentListOf(ServerRow.Restart, ServerRow.Stop),
            running = RunningRowUi(count = 7),
        )

    private fun answer(tone: AnswerTone, headline: AnswerHeadline, line: AnswerLine? = null, primary: PhoneAct? = null) =
        ThisPhoneUi(answer = ThisPhoneAnswer(tone, headline, line, primary))

    @Test
    fun thisPhoneAnswers() = Audit.guard("${m}__this-phone__build") {
        Audit.shoot(m, "this-phone", "checking") { ThisPhoneScreen(ThisPhoneUi(), PhoneCalls) }
        Audit.shoot(m, "this-phone", "not-set-up") { ThisPhoneScreen(answer(AnswerTone.Off, AnswerHeadline.NotSetUp, primary = PhoneAct.SetUp), PhoneCalls) }
        Audit.shoot(m, "this-phone", "setting-up") { ThisPhoneScreen(answer(AnswerTone.Working, AnswerHeadline.SettingUp, primary = PhoneAct.ShowProgress), PhoneCalls) }
        Audit.shoot(m, "this-phone", "install-failed") { ThisPhoneScreen(answer(AnswerTone.NeedsYou, AnswerHeadline.InstallFailed, primary = PhoneAct.SetUpAgain), PhoneCalls) }
        Audit.shoot(m, "this-phone", "starting") { ThisPhoneScreen(answer(AnswerTone.Working, AnswerHeadline.Starting), PhoneCalls) }
        Audit.shoot(m, "this-phone", "stopping") { ThisPhoneScreen(answer(AnswerTone.Working, AnswerHeadline.Stopping), PhoneCalls) }
        Audit.shoot(m, "this-phone", "running") { ThisPhoneScreen(running, PhoneCalls) }
        Audit.shoot(m, "this-phone", "running-memory") {
            ThisPhoneScreen(running.copy(answer = ThisPhoneAnswer(AnswerTone.Ok, AnswerHeadline.Running, AnswerLine.Memory(420L * 1024 * 1024))), PhoneCalls)
        }
        Audit.shoot(m, "this-phone", "not-answering") { ThisPhoneScreen(answer(AnswerTone.NeedsYou, AnswerHeadline.NotAnswering, primary = PhoneAct.RestartNow), PhoneCalls) }
        Audit.shoot(m, "this-phone", "off-by-you") {
            ThisPhoneScreen(answer(AnswerTone.Off, AnswerHeadline.Off, AnswerLine.StaysOff, PhoneAct.Start), PhoneCalls)
        }
        Audit.shoot(m, "this-phone", "android-closed") {
            ThisPhoneScreen(answer(AnswerTone.NeedsYou, AnswerHeadline.AndroidClosed, AnswerLine.RestartsSoon, PhoneAct.StartAgain), PhoneCalls)
        }
        Audit.shoot(m, "this-phone", "stopped-itself") {
            ThisPhoneScreen(answer(AnswerTone.NeedsYou, AnswerHeadline.StoppedItself, AnswerLine.GaveUp, PhoneAct.StartAgain), PhoneCalls)
        }
        Audit.shoot(m, "this-phone", "restarting-in-seconds") {
            ThisPhoneScreen(answer(AnswerTone.Working, AnswerHeadline.Restarting, AnswerLine.RestartsIn(AboutWait.Seconds(40))), PhoneCalls)
        }
        Audit.shoot(m, "this-phone", "restarting-in-minutes") {
            ThisPhoneScreen(answer(AnswerTone.Working, AnswerHeadline.Restarting, AnswerLine.RestartsIn(AboutWait.Minutes(2))), PhoneCalls)
        }
        Audit.shoot(m, "this-phone", "restarting-because-android") {
            ThisPhoneScreen(answer(AnswerTone.Working, AnswerHeadline.Restarting, AnswerLine.Because(AnswerHeadline.AndroidClosed)), PhoneCalls)
        }
        Audit.shoot(m, "this-phone", "start-failed-port-busy") {
            ThisPhoneScreen(answer(AnswerTone.NeedsYou, AnswerHeadline.StartFailed, AnswerLine.PortBusy(4096), PhoneAct.StartAgain), PhoneCalls)
        }
        Audit.shoot(m, "this-phone", "start-failed") {
            ThisPhoneScreen(answer(AnswerTone.NeedsYou, AnswerHeadline.StartFailed, AnswerLine.StartFailedLine, PhoneAct.StartAgain), PhoneCalls)
        }
        Audit.shoot(m, "this-phone", "recovery") {
            ThisPhoneScreen(answer(AnswerTone.Paused, AnswerHeadline.Recovery, AnswerLine.RecoveryBody, PhoneAct.OpenAppSettings), PhoneCalls)
        }
        Audit.shoot(m, "this-phone", "needs-opencode-2") {
            ThisPhoneScreen(answer(AnswerTone.NeedsYou, AnswerHeadline.NeedsOpenCode2, AnswerLine.OpenCode2Offer, PhoneAct.SwitchToOpenCode2), PhoneCalls)
        }
    }

    @Test
    fun thisPhoneParts() = Audit.guard("${m}__this-phone-parts__build") {
        Audit.shoot(m, "this-phone", "update-up-to-date") { ThisPhoneScreen(running.copy(version = VersionRowUi("2.1.1", UpdateRow.UpToDate)), PhoneCalls) }
        Audit.shoot(m, "this-phone", "update-failed") { ThisPhoneScreen(running.copy(version = VersionRowUi("1.18.29", UpdateRow.Failed("2.1.1"))), PhoneCalls) }
        Audit.shoot(m, "this-phone", "updating") { ThisPhoneScreen(running.copy(version = VersionRowUi("1.18.29", UpdateRow.Updating), working = PhoneOp.Update), PhoneCalls) }
        Audit.shoot(m, "this-phone", "working-stop") { ThisPhoneScreen(running.copy(working = PhoneOp.Stop), PhoneCalls) }
        Audit.shoot(m, "this-phone", "running-near-limit") { ThisPhoneScreen(running.copy(running = RunningRowUi(count = 30, nearLimit = true)), PhoneCalls) }
        Audit.shoot(m, "this-phone", "details-open") {
            ThisPhoneScreen(running.copy(detailsOpen = true, details = details.copy(canConnect = true, memoryBytes = 700L * 1024 * 1024)), PhoneCalls)
        }
        Audit.shoot(m, "this-phone", "installed-open") { ThisPhoneScreen(running.copy(installedOpen = true), PhoneCalls) }
        Audit.shoot(m, "this-phone", "details-loading") { ThisPhoneScreen(running.copy(detailsOpen = true, details = details.copy(loading = true)), PhoneCalls) }
        Audit.shoot(m, "this-phone", "free-model") { ThisPhoneScreen(running.copy(freeModel = true), PhoneCalls) }
        Audit.shoot(m, "this-phone", "storage-failed") { ThisPhoneScreen(running.copy(storageSupported = true, storageFailed = true), PhoneCalls) }
        Audit.shoot(m, "this-phone", "storage-measuring") { ThisPhoneScreen(running.copy(storageSupported = true, measuringStorage = true), PhoneCalls) }
        Audit.shoot(m, "this-phone", "confirm-restart") { ThisPhoneScreen(running.copy(confirm = ThisPhoneConfirm.Restart(false)), PhoneCalls) }
        Audit.shoot(m, "this-phone", "confirm-restart-reply-running") { ThisPhoneScreen(running.copy(confirm = ThisPhoneConfirm.Restart(true)), PhoneCalls) }
        Audit.shoot(m, "this-phone", "confirm-update") { ThisPhoneScreen(running.copy(confirm = ThisPhoneConfirm.Update("2.1.1")), PhoneCalls) }
        Audit.shoot(m, "this-phone", "notice-failed-start") { ThisPhoneScreen(running.copy(notice = ThisPhoneNotice.Failed(PhoneOp.Start)), PhoneCalls) }
        Audit.shoot(m, "this-phone", "notice-failed-update") { ThisPhoneScreen(running.copy(notice = ThisPhoneNotice.Failed(PhoneOp.Update)), PhoneCalls) }
        Audit.shoot(m, "this-phone", "notice-update-busy") { ThisPhoneScreen(running.copy(notice = ThisPhoneNotice.UpdateBusy), PhoneCalls) }
        Audit.shoot(m, "this-phone", "notice-settings-missing") { ThisPhoneScreen(running.copy(notice = ThisPhoneNotice.SettingsMissing), PhoneCalls) }
    }

    // ---- Running on this phone

    private fun row(
        pid: Int,
        name: String,
        kind: ProcessKind = ProcessKind.DevService,
        cpu: Int? = 3,
        left: Boolean = false,
        role: ProcessRole? = null,
        words: CpuWords? = CpuWords(CpuLevel.Idle, "3.0"),
        heavy: Boolean = false,
        group: RunningGroupKind = RunningGroupKind.Other,
    ) = ProcessRowUi(
        pid, name, kind, cpu, left, role,
        cpuWords = words, memoryBytes = (40L + pid) * 1048576L, elapsedSeconds = 720, heavy = heavy, group = group,
    )

    private val rows =
        persistentListOf(
            row(1, "OpenCode", ProcessKind.OpenCode, role = ProcessRole.ServerRoot, group = RunningGroupKind.OpenCode),
            row(2, "node", cpu = 92, words = CpuWords(CpuLevel.VeryBusy, "91.8"), heavy = true),
            row(3, "a-process-with-a-really-long-name-that-should-wrap-or-truncate-on-a-phone", ProcessKind.Other),
            row(4, "python", ProcessKind.Other, left = true, group = RunningGroupKind.LeftBehind),
            row(5, "claude", ProcessKind.ClaudeCode, group = RunningGroupKind.ClaudeCode),
        )

    @Test
    fun running() = Audit.guard("${m}__running__build") {
        Audit.shoot(m, "running", "loading") { RunningScreen(RunningUi(), RunningCalls) }
        Audit.shoot(m, "running", "load-failed") { RunningScreen(RunningUi(loading = false, loadFailed = true), RunningCalls) }
        Audit.shoot(m, "running", "empty") { RunningScreen(RunningUi(loading = false), RunningCalls) }
        Audit.shoot(m, "running", "grouped-rows") { RunningScreen(RunningUi(loading = false, rows = rows, count = 5), RunningCalls) }
        Audit.shoot(m, "running", "refresh-failed") { RunningScreen(RunningUi(loading = false, rows = rows, count = 5, refreshFailed = true), RunningCalls) }
        Audit.shoot(m, "running", "near-limit") { RunningScreen(RunningUi(loading = false, rows = rows, count = 30, nearLimit = true), RunningCalls) }
        Audit.shoot(m, "running", "child-limit-open-settings") {
            RunningScreen(RunningUi(loading = false, rows = rows, count = 5, childLimit = ChildLimitCardKind.OpenSettings), RunningCalls)
        }
        Audit.shoot(m, "running", "child-limit-enable-dev-options") {
            RunningScreen(RunningUi(loading = false, rows = rows, count = 5, childLimit = ChildLimitCardKind.EnableDeveloperOptions), RunningCalls)
        }
        Audit.shoot(m, "running", "child-limit-instructions") {
            RunningScreen(RunningUi(loading = false, rows = rows, count = 5, childLimit = ChildLimitCardKind.InstructionsOnly), RunningCalls)
        }
        Audit.shoot(m, "running", "confirm-stop") { RunningScreen(RunningUi(loading = false, rows = rows, confirmStop = rows[1]), RunningCalls) }
        Audit.shoot(m, "running", "stopping") { RunningScreen(RunningUi(loading = false, rows = rows, stopping = 2), RunningCalls) }
        Audit.shoot(m, "running", "stop-failed") { RunningScreen(RunningUi(loading = false, rows = rows, stopFailed = true), RunningCalls) }
        Audit.shoot(m, "running", "confirm-stop-left-behind") {
            RunningScreen(RunningUi(loading = false, rows = rows, confirmStopLeftBehind = persistentListOf(rows[3])), RunningCalls)
        }
        Audit.shoot(m, "running", "stopped-count") { RunningScreen(RunningUi(loading = false, rows = rows, stoppedCount = 2), RunningCalls) }
        Audit.shoot(m, "running", "detail-normal") { RunningScreen(RunningUi(loading = false, selected = rows[1]), RunningCalls) }
        Audit.shoot(m, "running", "detail-protected") { RunningScreen(RunningUi(loading = false, selected = rows[0]), RunningCalls) }
        Audit.shoot(m, "running", "detail-orphan") {
            RunningScreen(RunningUi(loading = false, selected = rows[3].copy(orphanReason = ProcessOrphanReason.ParentGone)), RunningCalls)
        }
    }

    // ---- Keep running

    private val steps =
        persistentListOf(
            KeepAliveStepUi(KeepAliveStepKind.Battery, done = true, opens = null),
            KeepAliveStepUi(KeepAliveStepKind.Background, done = false, opens = KeepAliveSetting.AppDetails),
            KeepAliveStepUi(KeepAliveStepKind.Autostart, done = false, opens = KeepAliveSetting.Autostart),
            KeepAliveStepUi(KeepAliveStepKind.LockInRecents, done = false, opens = null),
        )

    @Test
    fun keepRunning() = Audit.guard("${m}__keep-running__build") {
        val base = KeepRunningUi(loaded = true, maker = PhoneMaker.Samsung, steps = steps, swipeWarning = true)
        Audit.shoot(m, "keep-running", "loading") { KeepRunningScreen(KeepRunningUi(), KeepCalls) }
        Audit.shoot(m, "keep-running", "steps") { KeepRunningScreen(base, KeepCalls) }
        Audit.shoot(m, "keep-running", "all-set") { KeepRunningScreen(base.copy(allSet = true, swipeWarning = false, steps = persistentListOf(KeepAliveStepUi(KeepAliveStepKind.Battery, true, KeepAliveSetting.Battery))), KeepCalls) }
        Audit.shoot(m, "keep-running", "stock-android") { KeepRunningScreen(base.copy(maker = PhoneMaker.Other, swipeWarning = false, steps = steps.subList(0, 2)), KeepCalls) }
        Audit.shoot(m, "keep-running", "nubia") { KeepRunningScreen(base.copy(maker = PhoneMaker.Nubia), KeepCalls) }
        Audit.shoot(m, "keep-running", "open-failed") { KeepRunningScreen(base.copy(openFailed = true), KeepCalls) }
        Audit.shoot(m, "keep-running", "no-daily-limit-note") { KeepRunningScreen(base.copy(dailyLimit = false), KeepCalls) }
        Audit.shoot(m, "keep-running", "thermal-on") { KeepRunningScreen(base.copy(thermalSupported = true, thermalEnabled = true), KeepCalls) }
        Audit.shoot(m, "keep-running", "thermal-off") { KeepRunningScreen(base.copy(thermalSupported = true, thermalEnabled = false), KeepCalls) }
        Audit.shoot(m, "keep-running", "thermal-saving") { KeepRunningScreen(base.copy(thermalSupported = true, thermalSaving = true), KeepCalls) }
        Audit.shoot(m, "keep-running", "thermal-failed") { KeepRunningScreen(base.copy(thermalSupported = true, thermalFailed = true), KeepCalls) }
        Audit.shoot(m, "keep-running", "full-speed") { KeepRunningScreen(base.copy(speedAvailable = true, fullSpeed = true), KeepCalls) }
        Audit.shoot(m, "keep-running", "speed-save-failed") { KeepRunningScreen(base.copy(speedAvailable = true, speedSaveFailed = true), KeepCalls) }
    }

    // ---- Storage pages

    private val facts =
        ProjectStorageFacts(
            projects = persistentListOf(
                StoredProject("my-app", 120L * 1024 * 1024, 2_400, 3),
                StoredProject("a-project-folder-with-a-really-long-name-for-wrapping", 4L * 1024 * 1024, 40, 0),
            ),
            serverInstalled = true,
            serverBytes = 900L * 1024 * 1024,
            privateDataBytes = 12L * 1024 * 1024,
            savedServers = 2,
            cacheBytes = 85L * 1024 * 1024,
        )

    @Test
    fun projectStorage() = Audit.guard("${m}__project-storage__build") {
        val ui = ProjectStorageUi(facts = facts, measuring = false)
        for (manage in listOf(false, true)) {
            val door = if (manage) "manage-space" else "export"
            Audit.shoot(m, "project-storage-$door", "measuring") { ProjectStorageScreen(ProjectStorageUi(), ProjectStorageCalls, manage) }
            Audit.shoot(m, "project-storage-$door", "ready") { ProjectStorageScreen(ui, ProjectStorageCalls, manage) }
            Audit.shoot(m, "project-storage-$door", "nothing-to-export") {
                ProjectStorageScreen(ProjectStorageUi(facts = ProjectStorageFacts(), measuring = false), ProjectStorageCalls, manage)
            }
            Audit.shoot(m, "project-storage-$door", "private-included") { ProjectStorageScreen(ui.copy(includePrivate = true), ProjectStorageCalls, manage) }
            Audit.shoot(m, "project-storage-$door", "exporting") {
                ProjectStorageScreen(ui.copy(exporting = true, progress = ProjectExportProgress(40L * 1024 * 1024, 120L * 1024 * 1024)), ProjectStorageCalls, manage)
            }
            Audit.shoot(m, "project-storage-$door", "export-receipt") {
                ProjectStorageScreen(ui.copy(receipt = ProjectExportReceipt(120L * 1024 * 1024, 2_400)), ProjectStorageCalls, manage)
            }
            Audit.shoot(m, "project-storage-$door", "export-problem-space") {
                ProjectStorageScreen(ui.copy(exportProblem = ProjectExportProblem.Space), ProjectStorageCalls, manage)
            }
            Audit.shoot(m, "project-storage-$door", "export-problem-destination") {
                ProjectStorageScreen(ui.copy(exportProblem = ProjectExportProblem.Destination), ProjectStorageCalls, manage)
            }
            Audit.shoot(m, "project-storage-$door", "measure-failed") {
                ProjectStorageScreen(ProjectStorageUi(measuring = false, notice = StorageNotice.MeasureFailed, failure = Failure.Device()), ProjectStorageCalls, manage)
            }
            Audit.shoot(m, "project-storage-$door", "cache-clearing") { ProjectStorageScreen(ui.copy(cacheClearing = true), ProjectStorageCalls, manage) }
            Audit.shoot(m, "project-storage-$door", "cache-freed") { ProjectStorageScreen(ui.copy(cacheFreed = 85L * 1024 * 1024), ProjectStorageCalls, manage) }
            Audit.shoot(m, "project-storage-$door", "cache-failed") { ProjectStorageScreen(ui.copy(notice = StorageNotice.CacheFailed), ProjectStorageCalls, manage) }
            Audit.shoot(m, "project-storage-$door", "confirm-delete") { ProjectStorageScreen(ui.copy(confirmDelete = true), ProjectStorageCalls, manage) }
            Audit.shoot(m, "project-storage-$door", "delete-failed") { ProjectStorageScreen(ui.copy(notice = StorageNotice.DeleteFailed), ProjectStorageCalls, manage) }
        }
    }

    @Test
    fun sharedFolders() = Audit.guard("${m}__shared-folders__build") {
        fun state(granted: Boolean?, all: Boolean = false, canAll: Boolean = true, restart: Boolean = false) =
            SharedFoldersUi(state = SharedFolderState(granted, all, canAll, restart))
        Audit.shoot(m, "shared-folders", "loading") { SharedFoldersScreen(SharedFoldersUi(), SharedCalls) }
        Audit.shoot(m, "shared-folders", "not-granted") { SharedFoldersScreen(state(false), SharedCalls) }
        Audit.shoot(m, "shared-folders", "granted") { SharedFoldersScreen(state(true), SharedCalls) }
        Audit.shoot(m, "shared-folders", "granted-all") { SharedFoldersScreen(state(true, all = true), SharedCalls) }
        Audit.shoot(m, "shared-folders", "all-unavailable") { SharedFoldersScreen(state(true, canAll = false), SharedCalls) }
        Audit.shoot(m, "shared-folders", "restart-required") { SharedFoldersScreen(state(true, all = true, restart = true), SharedCalls) }
        Audit.shoot(m, "shared-folders", "permission-unknown") { SharedFoldersScreen(state(null), SharedCalls) }
        Audit.shoot(m, "shared-folders", "busy") { SharedFoldersScreen(state(true).copy(busy = true), SharedCalls) }
        Audit.shoot(m, "shared-folders", "failed") { SharedFoldersScreen(state(false).copy(failed = true), SharedCalls) }
        Audit.shoot(m, "shared-folders", "declined") { SharedFoldersScreen(state(false).copy(declined = true), SharedCalls) }
    }
}
