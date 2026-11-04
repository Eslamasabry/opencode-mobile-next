package io.github.eslamasabry.oc.feature.linux.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleStartEffect
import io.github.eslamasabry.oc.feature.linux.host.LinuxPresence
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxConfirm
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxHomeActions
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxHomeNotice
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxHomeUi
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxPage
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.kit.KitAction
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
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.keptSvcKeepsRunning
import io.github.eslamasabry.oc.ui.words.res.keptSvcTitle
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.linuxBackupRow
import io.github.eslamasabry.oc.ui.words.res.linuxBackupSetupProjectsDetail
import io.github.eslamasabry.oc.ui.words.res.linuxComponentsRemove
import io.github.eslamasabry.oc.ui.words.res.linuxComponentsRemoveBody
import io.github.eslamasabry.oc.ui.words.res.linuxComponentsRemoveQuestion
import io.github.eslamasabry.oc.ui.words.res.linuxLocalRemoveAll
import io.github.eslamasabry.oc.ui.words.res.linuxLocalRemoveAllBody
import io.github.eslamasabry.oc.ui.words.res.linuxLocalRemoveAllQuestion
import io.github.eslamasabry.oc.ui.words.res.linuxLocalRemoveKeep
import io.github.eslamasabry.oc.ui.words.res.linuxLocalRemoveKeepBody
import io.github.eslamasabry.oc.ui.words.res.linuxLocalRemoveKeepQuestion
import io.github.eslamasabry.oc.ui.words.res.linuxLocalRemoved
import io.github.eslamasabry.oc.ui.words.res.linuxLocalStorageFailed
import io.github.eslamasabry.oc.ui.words.res.linuxMgrJobStopBody
import io.github.eslamasabry.oc.ui.words.res.linuxMgrLoadFailedTitle
import io.github.eslamasabry.oc.ui.words.res.linuxMgrPackages
import io.github.eslamasabry.oc.ui.words.res.linuxMgrPackagesDetail
import io.github.eslamasabry.oc.ui.words.res.linuxMgrPorts
import io.github.eslamasabry.oc.ui.words.res.linuxMgrPortsDetail
import io.github.eslamasabry.oc.ui.words.res.linuxMgrProblemUnavailable
import io.github.eslamasabry.oc.ui.words.res.linuxMgrRepair
import io.github.eslamasabry.oc.ui.words.res.linuxMgrRepairBody
import io.github.eslamasabry.oc.ui.words.res.linuxMgrRepairDetail
import io.github.eslamasabry.oc.ui.words.res.linuxMgrRepairKept
import io.github.eslamasabry.oc.ui.words.res.linuxMgrRepairTitle
import io.github.eslamasabry.oc.ui.words.res.linuxMgrReset
import io.github.eslamasabry.oc.ui.words.res.linuxMgrResetBackUpFirst
import io.github.eslamasabry.oc.ui.words.res.linuxMgrResetBody
import io.github.eslamasabry.oc.ui.words.res.linuxMgrResetCleanupTitle
import io.github.eslamasabry.oc.ui.words.res.linuxMgrResetDetail
import io.github.eslamasabry.oc.ui.words.res.linuxMgrResetKept
import io.github.eslamasabry.oc.ui.words.res.linuxMgrResetLostChats
import io.github.eslamasabry.oc.ui.words.res.linuxMgrResetLostSignIns
import io.github.eslamasabry.oc.ui.words.res.linuxMgrResetLostTools
import io.github.eslamasabry.oc.ui.words.res.linuxMgrResetNotErasedBody
import io.github.eslamasabry.oc.ui.words.res.linuxMgrResetNotErasedTitle
import io.github.eslamasabry.oc.ui.words.res.linuxMgrResetTitle
import io.github.eslamasabry.oc.ui.words.res.linuxMgrResetWord
import io.github.eslamasabry.oc.ui.words.res.linuxMgrRunning
import io.github.eslamasabry.oc.ui.words.res.linuxMgrRunningDetail
import io.github.eslamasabry.oc.ui.words.res.linuxMgrStorage
import io.github.eslamasabry.oc.ui.words.res.linuxMgrStorageDetail
import io.github.eslamasabry.oc.ui.words.res.linuxMgrTitle
import io.github.eslamasabry.oc.ui.words.res.linuxSetupStop
import io.github.eslamasabry.oc.ui.words.res.linuxSetupStopBody
import io.github.eslamasabry.oc.ui.words.res.linuxSetupStopQuestion
import io.github.eslamasabry.oc.ui.words.res.linuxSetupSwitchBusy
import io.github.eslamasabry.oc.ui.words.res.linuxSetupSwitchFailed
import io.github.eslamasabry.oc.ui.words.res.openCode2MoveFailedSeparateHistory
import io.github.eslamasabry.oc.ui.words.res.phoneSetupProfileName
import io.github.eslamasabry.oc.ui.words.res.workspaceDismissNotice
import org.jetbrains.compose.resources.stringResource

/**
 * LinuxRoute.Home (Flutter LinuxManagerScreen): the long job in place, then one row per page
 * (Running, Ports, Storage, Packages), then Repair and, last and never red, Reset guarded by its
 * typed word (owner rule LOOK-5). When Linux does not answer the page says so plainly and offers
 * This phone instead of rows that cannot work. Kit parts only.
 */
@Composable
public fun LinuxHomeScreen(
    state: LinuxHomeUi,
    actions: LinuxHomeActions,
    tools: io.github.eslamasabry.oc.feature.linux.presentation.LinuxToolsPanelUi? = null,
    toolsActions: io.github.eslamasabry.oc.feature.linux.presentation.LinuxToolsActions? = null,
) {
    LifecycleStartEffect(actions) {
        actions.shown(true)
        onStopOrDispose { actions.shown(false) }
    }
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.linuxMgrTitle),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        width = KitScreenWidth.Reading,
        loading = state.presence == null || state.working,
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            state.vitals?.let { LinuxVitalsPanel(it) { actions.open(LinuxPage.Running) } }
            val dismiss =
                KitLineAction(stringResource(Res.string.workspaceDismissNotice), actions::dismiss)
            when (val notice = state.notice) {
                null -> Unit
                is LinuxHomeNotice.Problem ->
                    KitNotice(
                        message = notice.problem.words(),
                        tone = StatusTone.Attention,
                        dismiss = dismiss,
                    )
                LinuxHomeNotice.ResetNotErased ->
                    KitNotice(
                        title = stringResource(Res.string.linuxMgrResetNotErasedTitle),
                        message = stringResource(Res.string.linuxMgrResetNotErasedBody),
                        tone = StatusTone.Failure,
                        dismiss = dismiss,
                    )
                LinuxHomeNotice.ResetSetupPending ->
                    KitNotice(
                        title = stringResource(Res.string.linuxMgrResetCleanupTitle),
                        message = stringResource(Res.string.linuxMgrProblemUnavailable),
                        tone = StatusTone.Attention,
                        dismiss = dismiss,
                    )
                LinuxHomeNotice.StorageFailed ->
                    KitNotice(
                        message = stringResource(Res.string.linuxLocalStorageFailed),
                        tone = StatusTone.Attention,
                        dismiss = dismiss,
                    )
                LinuxHomeNotice.Removed ->
                    KitNotice(
                        message = stringResource(Res.string.linuxLocalRemoved),
                        dismiss = dismiss,
                    )
                is LinuxHomeNotice.SetupRefused ->
                    KitNotice(
                        message =
                            stringResource(
                                if (
                                    (notice.failure
                                            as?
                                            io.github.eslamasabry.oc.core.common.Failure.Refused)
                                        ?.reason ==
                                        io.github.eslamasabry.oc.core.common.RefusalReason
                                            .MigrationPending
                                )
                                    Res.string.openCode2MoveFailedSeparateHistory
                                else if (
                                    (notice.failure
                                            as?
                                            io.github.eslamasabry.oc.core.common.Failure.Runtime)
                                        ?.problem ==
                                        io.github.eslamasabry.oc.core.common.RuntimeProblem.SlotBusy
                                )
                                    Res.string.linuxSetupSwitchBusy
                                else Res.string.linuxSetupSwitchFailed
                            ),
                        tone = StatusTone.Attention,
                        dismiss = dismiss,
                    )
                LinuxHomeNotice.SetupUnavailable ->
                    KitNotice(
                        message = stringResource(Res.string.linuxMgrProblemUnavailable),
                        tone = StatusTone.Attention,
                        dismiss = dismiss,
                    )
            }
            val presence = state.presence
            if (presence != null && presence != LinuxPresence.Ready) {
                KitNotice(
                    title = stringResource(Res.string.linuxMgrLoadFailedTitle),
                    message = stringResource(Res.string.linuxMgrProblemUnavailable),
                    actions =
                        if (presence == LinuxPresence.NotInstalled) {
                            listOf(
                                KitLineAction(
                                    stringResource(Res.string.phoneSetupProfileName),
                                    { actions.open(LinuxPage.ThisPhone) },
                                )
                            )
                        } else {
                            emptyList()
                        },
                    liveRegion = false,
                )
            }
            LinuxJobPanel(state.job, actions::askStopJob, actions::dismissJob)
            if (presence != null && presence != LinuxPresence.NotOnThisBuild) {
                if (state.ready) LinuxIdlePanel(state, actions)
                LinuxSetupPanel(state, actions, toolsActions?.let { it::openAppSettings })
                if (tools != null && toolsActions != null)
                    LinuxToolsPanel(
                        tools,
                        toolsActions,
                        busy =
                            state.working ||
                                state.job.running ||
                                state.setup.state ==
                                    io.github.eslamasabry.oc.engine.runtime.setup.SetupState
                                        .Running,
                    )
                if (state.ready) LinuxComponentsPanel(state, actions)
                LinuxLocalStoragePanel(state, actions)
                LinuxLifetimesPanel(state.lifetimes, actions::refreshLifetimes)
            }
            if (state.ready) {
                KitRowGroup {
                    PageRow(
                        stringResource(Res.string.keptSvcTitle),
                        stringResource(Res.string.keptSvcKeepsRunning),
                    ) {
                        actions.open(LinuxPage.KeptServices)
                    }
                    PageRow(
                        stringResource(Res.string.linuxBackupRow),
                        stringResource(Res.string.linuxBackupSetupProjectsDetail),
                    ) {
                        actions.open(LinuxPage.Backups)
                    }
                    PageRow(
                        stringResource(Res.string.linuxMgrRunning),
                        stringResource(Res.string.linuxMgrRunningDetail),
                    ) {
                        actions.open(LinuxPage.Running)
                    }
                    PageRow(
                        stringResource(Res.string.linuxMgrPorts),
                        stringResource(Res.string.linuxMgrPortsDetail),
                    ) {
                        actions.open(LinuxPage.Ports)
                    }
                    PageRow(
                        stringResource(Res.string.linuxMgrStorage),
                        stringResource(Res.string.linuxMgrStorageDetail),
                    ) {
                        actions.open(LinuxPage.Storage)
                    }
                    PageRow(
                        stringResource(Res.string.linuxMgrPackages),
                        stringResource(Res.string.linuxMgrPackagesDetail),
                    ) {
                        actions.open(LinuxPage.Packages)
                    }
                    KitRow(
                        title = stringResource(Res.string.linuxMgrRepair),
                        supporting = stringResource(Res.string.linuxMgrRepairDetail),
                        supportingMaxLines = 3,
                        trailing = KitRowTrailing.Chevron,
                        onClick = actions::askRepair,
                    )
                    KitRow(
                        title = stringResource(Res.string.linuxMgrReset),
                        supporting = stringResource(Res.string.linuxMgrResetDetail),
                        supportingMaxLines = 2,
                        onClick = actions::askReset,
                    )
                }
            }
        }
    }
    val spec =
        when (val asked = state.confirm) {
            null -> null
            LinuxConfirm.Repair ->
                KitConfirmSpec(
                    title = stringResource(Res.string.linuxMgrRepairTitle),
                    confirmLabel = stringResource(Res.string.linuxMgrRepair),
                    body = stringResource(Res.string.linuxMgrRepairBody),
                    consequences = listOf(stringResource(Res.string.linuxMgrRepairKept)),
                )
            LinuxConfirm.Reset ->
                KitConfirmSpec(
                    title = stringResource(Res.string.linuxMgrResetTitle),
                    confirmLabel = stringResource(Res.string.linuxMgrReset),
                    kind = KitConfirmKind.Destructive,
                    body = stringResource(Res.string.linuxMgrResetBody),
                    consequences =
                        listOf(
                            stringResource(Res.string.linuxMgrResetLostTools),
                            stringResource(Res.string.linuxMgrResetLostSignIns),
                            stringResource(Res.string.linuxMgrResetLostChats),
                            stringResource(Res.string.linuxMgrResetKept),
                        ),
                    typedName = stringResource(Res.string.linuxMgrResetWord),
                )
            LinuxConfirm.StopSetup ->
                KitConfirmSpec(
                    title = stringResource(Res.string.linuxSetupStopQuestion),
                    confirmLabel = stringResource(Res.string.linuxSetupStop),
                    kind = KitConfirmKind.Stop,
                    body = stringResource(Res.string.linuxSetupStopBody),
                )
            is LinuxConfirm.RemoveComponent ->
                KitConfirmSpec(
                    title =
                        stringResource(
                            io.github.eslamasabry.oc.ui.words.res.Res.string
                                .linuxComponentsRemoveQuestion,
                            componentName(asked.id),
                        ),
                    confirmLabel =
                        stringResource(
                            io.github.eslamasabry.oc.ui.words.res.Res.string.linuxComponentsRemove,
                            componentName(asked.id),
                        ),
                    kind = KitConfirmKind.Destructive,
                    body =
                        stringResource(
                            io.github.eslamasabry.oc.ui.words.res.Res.string
                                .linuxComponentsRemoveBody
                        ),
                )
            is LinuxConfirm.Remove ->
                KitConfirmSpec(
                    title =
                        stringResource(
                            if (asked.projects) Res.string.linuxLocalRemoveAllQuestion
                            else Res.string.linuxLocalRemoveKeepQuestion
                        ),
                    confirmLabel =
                        stringResource(
                            if (asked.projects) Res.string.linuxLocalRemoveAll
                            else Res.string.linuxLocalRemoveKeep
                        ),
                    kind = KitConfirmKind.Destructive,
                    body =
                        stringResource(
                            if (asked.projects) Res.string.linuxLocalRemoveAllBody
                            else Res.string.linuxLocalRemoveKeepBody,
                            size(asked.bytes),
                        ),
                    typedName =
                        if (asked.projects)
                            io.github.eslamasabry.oc.engine.runtime.storage.LinuxLocalStorage
                                .DELETION_NAME
                        else null,
                )
            is LinuxConfirm.StopJob -> {
                val (title, label) = stopJobWords(asked.kind, asked.names)
                KitConfirmSpec(
                    title = title,
                    confirmLabel = label,
                    kind = KitConfirmKind.Stop,
                    body = stringResource(Res.string.linuxMgrJobStopBody),
                )
            }
        }
    KitConfirm(
        spec = spec,
        onConfirm = actions::confirm,
        onCancel = actions::dismiss,
        alternative =
            if (state.confirm == LinuxConfirm.Reset)
                KitAction(
                    stringResource(Res.string.linuxMgrResetBackUpFirst),
                    { actions.open(LinuxPage.Backups) },
                )
            else null,
    )
}

@Composable
private fun PageRow(
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    KitRow(
        title = title,
        supporting = detail,
        supportingMaxLines = 2,
        trailing = KitRowTrailing.Chevron,
        onClick = onClick,
    )
}
