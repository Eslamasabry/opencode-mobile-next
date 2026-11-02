package io.github.eslamasabry.oc.feature.linux.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.feature.linux.manager.LinuxCleanupId
import io.github.eslamasabry.oc.feature.linux.manager.LinuxJobKind
import io.github.eslamasabry.oc.feature.linux.manager.LinuxJobState
import io.github.eslamasabry.oc.feature.linux.manager.LinuxJobStatus
import io.github.eslamasabry.oc.feature.linux.manager.LinuxProblem
import io.github.eslamasabry.oc.feature.linux.manager.LinuxProgramKind
import io.github.eslamasabry.oc.feature.linux.manager.LinuxStorageCategory
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxSize
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.linuxMgrCatAgents
import io.github.eslamasabry.oc.ui.words.res.linuxMgrCatAgentsDetail
import io.github.eslamasabry.oc.ui.words.res.linuxMgrCatBase
import io.github.eslamasabry.oc.ui.words.res.linuxMgrCatBaseDetail
import io.github.eslamasabry.oc.ui.words.res.linuxMgrCatPackages
import io.github.eslamasabry.oc.ui.words.res.linuxMgrCatPackagesDetail
import io.github.eslamasabry.oc.ui.words.res.linuxMgrCatProjects
import io.github.eslamasabry.oc.ui.words.res.linuxMgrCatProjectsDetail
import io.github.eslamasabry.oc.ui.words.res.linuxMgrCatTemp
import io.github.eslamasabry.oc.ui.words.res.linuxMgrCatTempDetail
import io.github.eslamasabry.oc.ui.words.res.linuxMgrCatTools
import io.github.eslamasabry.oc.ui.words.res.linuxMgrCatToolsDetail
import io.github.eslamasabry.oc.ui.words.res.linuxMgrCleanAgents
import io.github.eslamasabry.oc.ui.words.res.linuxMgrCleanAgentsAsk
import io.github.eslamasabry.oc.ui.words.res.linuxMgrCleanAgentsDetail
import io.github.eslamasabry.oc.ui.words.res.linuxMgrCleanApt
import io.github.eslamasabry.oc.ui.words.res.linuxMgrCleanAptAsk
import io.github.eslamasabry.oc.ui.words.res.linuxMgrCleanAptDetail
import io.github.eslamasabry.oc.ui.words.res.linuxMgrCleanTemp
import io.github.eslamasabry.oc.ui.words.res.linuxMgrCleanTempAsk
import io.github.eslamasabry.oc.ui.words.res.linuxMgrCleanTempDetail
import io.github.eslamasabry.oc.ui.words.res.linuxMgrCleanTools
import io.github.eslamasabry.oc.ui.words.res.linuxMgrCleanToolsAsk
import io.github.eslamasabry.oc.ui.words.res.linuxMgrCleanToolsDetail
import io.github.eslamasabry.oc.ui.words.res.linuxMgrJobBackingUp
import io.github.eslamasabry.oc.ui.words.res.linuxMgrJobDismiss
import io.github.eslamasabry.oc.ui.words.res.linuxMgrJobDoneBackup
import io.github.eslamasabry.oc.ui.words.res.linuxMgrJobDoneInstall
import io.github.eslamasabry.oc.ui.words.res.linuxMgrJobDoneRemove
import io.github.eslamasabry.oc.ui.words.res.linuxMgrJobDoneRestore
import io.github.eslamasabry.oc.ui.words.res.linuxMgrJobDoneUpdate
import io.github.eslamasabry.oc.ui.words.res.linuxMgrJobFailed
import io.github.eslamasabry.oc.ui.words.res.linuxMgrJobInstalling
import io.github.eslamasabry.oc.ui.words.res.linuxMgrJobInterrupted
import io.github.eslamasabry.oc.ui.words.res.linuxMgrJobKeepsGoing
import io.github.eslamasabry.oc.ui.words.res.linuxMgrJobRemoving
import io.github.eslamasabry.oc.ui.words.res.linuxMgrJobRestoring
import io.github.eslamasabry.oc.ui.words.res.linuxMgrJobStopBackup
import io.github.eslamasabry.oc.ui.words.res.linuxMgrJobStopBackupTitle
import io.github.eslamasabry.oc.ui.words.res.linuxMgrJobStopInstall
import io.github.eslamasabry.oc.ui.words.res.linuxMgrJobStopInstallTitle
import io.github.eslamasabry.oc.ui.words.res.linuxMgrJobStopRemove
import io.github.eslamasabry.oc.ui.words.res.linuxMgrJobStopRemoveTitle
import io.github.eslamasabry.oc.ui.words.res.linuxMgrJobStopRestore
import io.github.eslamasabry.oc.ui.words.res.linuxMgrJobStopRestoreTitle
import io.github.eslamasabry.oc.ui.words.res.linuxMgrJobStopUpdate
import io.github.eslamasabry.oc.ui.words.res.linuxMgrJobStopUpdateTitle
import io.github.eslamasabry.oc.ui.words.res.linuxMgrJobTimedOut
import io.github.eslamasabry.oc.ui.words.res.linuxMgrJobUpdating
import io.github.eslamasabry.oc.ui.words.res.linuxMgrKindAgent
import io.github.eslamasabry.oc.ui.words.res.linuxMgrKindDev
import io.github.eslamasabry.oc.ui.words.res.linuxMgrKindOther
import io.github.eslamasabry.oc.ui.words.res.linuxMgrKindPaseo
import io.github.eslamasabry.oc.ui.words.res.linuxMgrKindServer
import io.github.eslamasabry.oc.ui.words.res.linuxMgrProblemBusy
import io.github.eslamasabry.oc.ui.words.res.linuxMgrProblemFailed
import io.github.eslamasabry.oc.ui.words.res.linuxMgrProblemJob
import io.github.eslamasabry.oc.ui.words.res.linuxMgrProblemSetup
import io.github.eslamasabry.oc.ui.words.res.linuxMgrProblemUnavailable
import org.jetbrains.compose.resources.stringResource

/* The words of the Linux pages. Every sentence is a :ui:words string ported from the Flutter ARB (en + ar). */

@Composable
internal fun LinuxProblem.words(): String =
    stringResource(
        when (this) {
            LinuxProblem.Busy -> Res.string.linuxMgrProblemBusy
            LinuxProblem.SetupRunning -> Res.string.linuxMgrProblemSetup
            LinuxProblem.JobRunning -> Res.string.linuxMgrProblemJob
            LinuxProblem.Unavailable -> Res.string.linuxMgrProblemUnavailable
            LinuxProblem.Failed -> Res.string.linuxMgrProblemFailed
        },
    )

@Composable
internal fun LinuxProgramKind.words(): String =
    stringResource(
        when (this) {
            LinuxProgramKind.OpenCodeServer -> Res.string.linuxMgrKindServer
            LinuxProgramKind.Paseo -> Res.string.linuxMgrKindPaseo
            LinuxProgramKind.Agent -> Res.string.linuxMgrKindAgent
            LinuxProgramKind.DevServer -> Res.string.linuxMgrKindDev
            LinuxProgramKind.Other -> Res.string.linuxMgrKindOther
        },
    )

@Composable
internal fun LinuxStorageCategory.title(): String =
    stringResource(
        when (this) {
            LinuxStorageCategory.Base -> Res.string.linuxMgrCatBase
            LinuxStorageCategory.Packages -> Res.string.linuxMgrCatPackages
            LinuxStorageCategory.Tools -> Res.string.linuxMgrCatTools
            LinuxStorageCategory.Agents -> Res.string.linuxMgrCatAgents
            LinuxStorageCategory.Projects -> Res.string.linuxMgrCatProjects
            LinuxStorageCategory.Temp -> Res.string.linuxMgrCatTemp
        },
    )

@Composable
internal fun LinuxStorageCategory.detail(): String =
    stringResource(
        when (this) {
            LinuxStorageCategory.Base -> Res.string.linuxMgrCatBaseDetail
            LinuxStorageCategory.Packages -> Res.string.linuxMgrCatPackagesDetail
            LinuxStorageCategory.Tools -> Res.string.linuxMgrCatToolsDetail
            LinuxStorageCategory.Agents -> Res.string.linuxMgrCatAgentsDetail
            LinuxStorageCategory.Projects -> Res.string.linuxMgrCatProjectsDetail
            LinuxStorageCategory.Temp -> Res.string.linuxMgrCatTempDetail
        },
    )

@Composable
internal fun LinuxCleanupId.title(): String =
    stringResource(
        when (this) {
            LinuxCleanupId.AptCache -> Res.string.linuxMgrCleanApt
            LinuxCleanupId.ToolCaches -> Res.string.linuxMgrCleanTools
            LinuxCleanupId.TempFiles -> Res.string.linuxMgrCleanTemp
            LinuxCleanupId.OldAgentVersions -> Res.string.linuxMgrCleanAgents
        },
    )

@Composable
internal fun LinuxCleanupId.detail(): String =
    stringResource(
        when (this) {
            LinuxCleanupId.AptCache -> Res.string.linuxMgrCleanAptDetail
            LinuxCleanupId.ToolCaches -> Res.string.linuxMgrCleanToolsDetail
            LinuxCleanupId.TempFiles -> Res.string.linuxMgrCleanTempDetail
            LinuxCleanupId.OldAgentVersions -> Res.string.linuxMgrCleanAgentsDetail
        },
    )

@Composable
internal fun LinuxCleanupId.ask(): String =
    stringResource(
        when (this) {
            LinuxCleanupId.AptCache -> Res.string.linuxMgrCleanAptAsk
            LinuxCleanupId.ToolCaches -> Res.string.linuxMgrCleanToolsAsk
            LinuxCleanupId.TempFiles -> Res.string.linuxMgrCleanTempAsk
            LinuxCleanupId.OldAgentVersions -> Res.string.linuxMgrCleanAgentsAsk
        },
    )

/** A size in an LTR isolate, so Arabic text around it keeps its order. */
internal fun size(bytes: Long): String = "⁦${LinuxSize.format(bytes)}⁩"

/** Package names in an LTR isolate, joined plainly. */
internal fun names(names: List<String>): String = "⁦${names.joinToString(", ")}⁩"

/** The stop question and its button for the running job (the button names its object). */
@Composable
internal fun stopJobWords(kind: LinuxJobKind, list: List<String>): Pair<String, String> {
    val n = names(list)
    return when (kind) {
        LinuxJobKind.Install -> stringResource(Res.string.linuxMgrJobStopInstallTitle, n) to stringResource(Res.string.linuxMgrJobStopInstall, n)
        LinuxJobKind.Remove -> stringResource(Res.string.linuxMgrJobStopRemoveTitle, n) to stringResource(Res.string.linuxMgrJobStopRemove, n)
        LinuxJobKind.Update -> stringResource(Res.string.linuxMgrJobStopUpdateTitle) to stringResource(Res.string.linuxMgrJobStopUpdate)
        LinuxJobKind.Backup -> stringResource(Res.string.linuxMgrJobStopBackupTitle) to stringResource(Res.string.linuxMgrJobStopBackup)
        LinuxJobKind.Restore -> stringResource(Res.string.linuxMgrJobStopRestoreTitle) to stringResource(Res.string.linuxMgrJobStopRestore)
    }
}

/**
 * The one long job, said in place (Flutter LinuxJobPanel): what runs with its percent and that it keeps going when the
 * page is left, with a Stop that names it; how it ended, with Done. Nothing when there is no job.
 */
@Composable
internal fun LinuxJobPanel(job: LinuxJobState, onStop: () -> Unit, onDismiss: () -> Unit) {
    val kind = job.kind ?: return
    val n = names(job.names)
    when (job.status) {
        LinuxJobStatus.None -> Unit
        LinuxJobStatus.Running -> {
            val title =
                when (kind) {
                    LinuxJobKind.Install -> stringResource(Res.string.linuxMgrJobInstalling, n)
                    LinuxJobKind.Remove -> stringResource(Res.string.linuxMgrJobRemoving, n)
                    LinuxJobKind.Update -> stringResource(Res.string.linuxMgrJobUpdating)
                    LinuxJobKind.Backup -> stringResource(Res.string.linuxMgrJobBackingUp)
                    LinuxJobKind.Restore -> stringResource(Res.string.linuxMgrJobRestoring)
                }
            KitNotice(
                title = job.percent?.let { "$title · ⁦$it %⁩" } ?: title,
                message = stringResource(Res.string.linuxMgrJobKeepsGoing),
                actions = listOf(KitLineAction(stopJobWords(kind, job.names).second, onStop, testTag = "linux-job-stop")),
                liveRegion = false,
            )
        }
        LinuxJobStatus.Done -> {
            val done =
                when (kind) {
                    LinuxJobKind.Install -> stringResource(Res.string.linuxMgrJobDoneInstall, n)
                    LinuxJobKind.Remove -> stringResource(Res.string.linuxMgrJobDoneRemove, n)
                    LinuxJobKind.Update -> stringResource(Res.string.linuxMgrJobDoneUpdate)
                    LinuxJobKind.Backup -> stringResource(Res.string.linuxMgrJobDoneBackup)
                    LinuxJobKind.Restore -> stringResource(Res.string.linuxMgrJobDoneRestore)
                }
            KitNotice(
                message = done,
                tone = StatusTone.Ok,
                dismiss = KitLineAction(stringResource(Res.string.linuxMgrJobDismiss), onDismiss, testTag = "linux-job-dismiss"),
            )
        }
        LinuxJobStatus.Failed, LinuxJobStatus.TimedOut, LinuxJobStatus.Interrupted -> {
            val why =
                when {
                    job.wasBusy -> Res.string.linuxMgrProblemBusy
                    job.status == LinuxJobStatus.TimedOut -> Res.string.linuxMgrJobTimedOut
                    job.status == LinuxJobStatus.Interrupted -> Res.string.linuxMgrJobInterrupted
                    else -> Res.string.linuxMgrJobFailed
                }
            KitNotice(
                message = stringResource(why),
                tone = StatusTone.Failure,
                dismiss = KitLineAction(stringResource(Res.string.linuxMgrJobDismiss), onDismiss, testTag = "linux-job-dismiss"),
            )
        }
    }
}
