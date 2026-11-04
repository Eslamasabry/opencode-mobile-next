package io.github.eslamasabry.oc.feature.linux.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.runtime.backup.LinuxBackupPhase
import io.github.eslamasabry.oc.engine.runtime.backup.LinuxBackupProblem
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxBackupChoice
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxBackupsActions
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxBackupsUi
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.choice.KitChoice
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceList
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitProgress
import io.github.eslamasabry.oc.ui.kit.state.KitProgressView
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * Setup and project backup, save and restore are real state of the app-owned LinuxBackups engine.
 */
@Composable
public fun LinuxBackupsScreen(state: LinuxBackupsUi, actions: LinuxBackupsActions) {
    val backup = state.backup
    val busyReason = stringResource(Res.string.linuxBackupProblemBusy)
    val enabled = if (backup.busy) KitEnabled.Disabled(busyReason) else KitEnabled.Enabled
    val dismiss = KitLineAction(stringResource(Res.string.linuxMgrJobDismiss), actions::dismiss)
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.linuxBackupRow),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        width = KitScreenWidth.Reading,
    ) {
        Column(
            Modifier.fillMaxWidth().testTag("linux-backups").verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            if (backup.busy && backup.problem != null)
                KitNotice(message = busyReason, tone = StatusTone.Attention)
            when (backup.phase) {
                LinuxBackupPhase.Preparing,
                LinuxBackupPhase.Restoring -> {
                    val restoring = backup.phase == LinuxBackupPhase.Restoring
                    val title =
                        stringResource(
                            if (restoring) Res.string.linuxMgrJobRestoring
                            else Res.string.linuxMgrJobBackingUp
                        )
                    KitNotice(
                        title = title,
                        message = stringResource(Res.string.linuxMgrJobKeepsGoing),
                        actions =
                            listOf(
                                KitLineAction(
                                    stringResource(
                                        if (restoring) Res.string.linuxMgrJobStopRestore
                                        else Res.string.linuxMgrJobStopBackup
                                    ),
                                    actions::askStop,
                                )
                            ),
                        liveRegion = false,
                    )
                    KitProgressView(
                        backup.percent?.let { KitProgress.Known(it / 100f) }
                            ?: KitProgress.Waiting()
                    )
                }
                LinuxBackupPhase.Checking ->
                    KitProgressView(
                        KitProgress.Waiting(stringResource(Res.string.linuxRestoreChecking))
                    )
                LinuxBackupPhase.Saving ->
                    KitProgressView(
                        if (backup.total > 0)
                            KitProgress.Known(
                                (backup.bytes.toFloat() / backup.total).coerceIn(0f, 1f),
                                stringResource(
                                    Res.string.linuxBackupSaving,
                                    size(backup.bytes),
                                    size(backup.total),
                                ),
                            )
                        else KitProgress.Waiting(stringResource(Res.string.linuxMgrJobBackingUp))
                    )
                LinuxBackupPhase.Saved ->
                    KitNotice(
                        message =
                            stringResource(
                                Res.string.linuxBackupSaved,
                                size(backup.bytes),
                                backup.name,
                            ),
                        tone = StatusTone.Ok,
                        dismiss = dismiss,
                    )
                LinuxBackupPhase.Restored -> {
                    val result = backup.summary
                    val counts = result?.counts.orEmpty()
                    KitNotice(
                        title = stringResource(Res.string.linuxRestoreDone),
                        message =
                            stringResource(
                                Res.string.linuxRestoreCounts,
                                "${counts["projects"] ?: 0}",
                                "${counts["services"] ?: 0}",
                                "${counts["settings"] ?: 0}",
                                "${counts["packages"] ?: 0}",
                            ),
                        tone = StatusTone.Ok,
                        dismiss = dismiss,
                    )
                    KitNotice(message = stringResource(Res.string.linuxBackupServiceAccountsNative))
                    if (!result?.skipped.isNullOrEmpty())
                        KitNotice(
                            message =
                                stringResource(
                                    Res.string.linuxRestoreSkipped,
                                    result.skipped.joinToString(", "),
                                )
                        )
                }
                LinuxBackupPhase.Failed ->
                    KitErrorNotice(
                        title =
                            stringResource(
                                if (
                                    backup.problem == LinuxBackupProblem.Restore ||
                                        backup.manifest != null
                                )
                                    Res.string.linuxRestoreFailedTitle
                                else Res.string.linuxBackupFailedTitle
                            ),
                        message =
                            stringResource(
                                when (backup.problem) {
                                    LinuxBackupProblem.Busy -> Res.string.linuxBackupProblemBusy
                                    LinuxBackupProblem.Missing ->
                                        Res.string.linuxBackupProblemMissing
                                    LinuxBackupProblem.Save -> Res.string.linuxBackupProblemSave
                                    LinuxBackupProblem.NotBackup ->
                                        Res.string.linuxBackupProblemNotBackup
                                    LinuxBackupProblem.Newer -> Res.string.linuxBackupProblemNewer
                                    LinuxBackupProblem.Restore ->
                                        Res.string.linuxBackupProblemRestore
                                    LinuxBackupProblem.Job,
                                    null -> Res.string.linuxBackupProblemJob
                                }
                            ),
                        failure = backup.failure,
                        detailsLabel = stringResource(Res.string.kitDetails),
                        copyLabel = stringResource(Res.string.kitCopyDetails),
                        retry = dismiss,
                    )
                LinuxBackupPhase.Idle,
                LinuxBackupPhase.Ready -> Unit
            }
            KitRowGroup(label = stringResource(Res.string.linuxBackupWhat)) {
                KitChoiceList(
                    choices =
                        listOf(
                            KitChoice(
                                LinuxBackupChoice.Setup,
                                stringResource(Res.string.linuxBackupSetup),
                                stringResource(Res.string.linuxBackupSetupDetail),
                                enabled = enabled,
                            ),
                            KitChoice(
                                LinuxBackupChoice.SetupAndProjects,
                                stringResource(Res.string.linuxBackupSetupProjects),
                                stringResource(Res.string.linuxBackupSetupProjectsDetail),
                                enabled = enabled,
                            ),
                            KitChoice(
                                LinuxBackupChoice.ProjectsOnly,
                                stringResource(Res.string.linuxBackupProjectsOnly),
                                stringResource(Res.string.linuxBackupProjectsOnlyDetail),
                                enabled = enabled,
                            ),
                        ),
                    selected = state.choice,
                    semanticsLabel = stringResource(Res.string.linuxBackupWhat),
                    onSelect = actions::choose,
                )
            }
            KitButton(
                stringResource(
                    when (state.choice) {
                        LinuxBackupChoice.Setup -> Res.string.linuxBackupNowSetup
                        LinuxBackupChoice.SetupAndProjects -> Res.string.linuxBackupNowSetupProjects
                        LinuxBackupChoice.ProjectsOnly -> Res.string.linuxBackupNowProjects
                    }
                ),
                actions::go,
                enabled = enabled,
                working =
                    backup.phase == LinuxBackupPhase.Preparing ||
                        backup.phase == LinuxBackupPhase.Saving,
                modifier = Modifier.testTag("linux-backup-go"),
            )
            KitRowGroup {
                KitRow(
                    title = stringResource(Res.string.linuxRestoreRow),
                    supporting = stringResource(Res.string.linuxRestoreRowDetail),
                    trailing = KitRowTrailing.Chevron,
                    enabled = !backup.busy,
                    disabledReason = busyReason,
                    onClick = actions::chooseBackup,
                    modifier = Modifier.testTag("linux-restore-row"),
                )
            }
            KitText(stringResource(Res.string.linuxBackupPrivacyNative))
        }
    }
    val manifest = backup.manifest
    val confirm =
        when {
            state.askingStop -> {
                val restoring = backup.phase == LinuxBackupPhase.Restoring
                KitConfirmSpec(
                    title =
                        stringResource(
                            if (restoring) Res.string.linuxMgrJobStopRestoreTitle
                            else Res.string.linuxMgrJobStopBackupTitle
                        ),
                    confirmLabel =
                        stringResource(
                            if (restoring) Res.string.linuxMgrJobStopRestore
                            else Res.string.linuxMgrJobStopBackup
                        ),
                    kind = KitConfirmKind.Stop,
                    body = stringResource(Res.string.linuxMgrJobStopBody),
                )
            }
            backup.phase == LinuxBackupPhase.Ready && manifest != null ->
                KitConfirmSpec(
                    title = stringResource(Res.string.linuxRestoreTitle),
                    confirmLabel = stringResource(Res.string.linuxRestoreConfirm),
                    body =
                        stringResource(
                            Res.string.linuxRestoreBody,
                            stringResource(
                                if (manifest.hasProjects) Res.string.linuxBackupSetupProjects
                                else Res.string.linuxBackupSetup
                            ),
                            manifest.created,
                        ),
                    consequences =
                        listOfNotNull(
                            stringResource(Res.string.linuxRestoreKeptProjects).takeIf {
                                manifest.hasProjects
                            },
                            stringResource(Res.string.linuxRestoreReplacedSettings),
                            stringResource(Res.string.linuxRestoreAddedTools),
                            stringResource(Res.string.linuxBackupServiceAccountsNative),
                        ),
                )
            else -> null
        }
    KitConfirm(confirm, actions::confirm, actions::dismiss)
}
