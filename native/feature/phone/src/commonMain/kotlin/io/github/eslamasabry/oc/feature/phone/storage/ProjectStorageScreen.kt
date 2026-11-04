package io.github.eslamasabry.oc.feature.phone.storage

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.feature.phone.ui.PhoneSizes
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.kit.KitAction
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
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitProgress
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.manageSpaceCacheCleared
import io.github.eslamasabry.oc.ui.words.res.manageSpaceCacheFailed
import io.github.eslamasabry.oc.ui.words.res.manageSpaceClearCache
import io.github.eslamasabry.oc.ui.words.res.manageSpaceClearCacheDetail
import io.github.eslamasabry.oc.ui.words.res.manageSpaceClearCacheKeeps
import io.github.eslamasabry.oc.ui.words.res.manageSpaceDeleteAll
import io.github.eslamasabry.oc.ui.words.res.manageSpaceDeleteAllDetail
import io.github.eslamasabry.oc.ui.words.res.manageSpaceDeleteBody
import io.github.eslamasabry.oc.ui.words.res.manageSpaceDeleteTitle
import io.github.eslamasabry.oc.ui.words.res.manageSpaceDeletedLabel
import io.github.eslamasabry.oc.ui.words.res.manageSpaceExportFirst
import io.github.eslamasabry.oc.ui.words.res.manageSpaceIntro
import io.github.eslamasabry.oc.ui.words.res.manageSpaceIntroNothingToExport
import io.github.eslamasabry.oc.ui.words.res.manageSpaceKeptAll
import io.github.eslamasabry.oc.ui.words.res.manageSpaceKeptComputers
import io.github.eslamasabry.oc.ui.words.res.manageSpaceKeptGit
import io.github.eslamasabry.oc.ui.words.res.manageSpaceKeptLabel
import io.github.eslamasabry.oc.ui.words.res.manageSpaceKeptTermux
import io.github.eslamasabry.oc.ui.words.res.manageSpaceLostProjects
import io.github.eslamasabry.oc.ui.words.res.manageSpaceLostServer
import io.github.eslamasabry.oc.ui.words.res.manageSpaceLostSettings
import io.github.eslamasabry.oc.ui.words.res.manageSpaceLostSettings__eq0
import io.github.eslamasabry.oc.ui.words.res.manageSpaceMeasuring
import io.github.eslamasabry.oc.ui.words.res.manageSpaceSavedServers
import io.github.eslamasabry.oc.ui.words.res.manageSpaceSavedServers__eq0
import io.github.eslamasabry.oc.ui.words.res.manageSpaceServer
import io.github.eslamasabry.oc.ui.words.res.manageSpaceServerDetail
import io.github.eslamasabry.oc.ui.words.res.manageSpaceSettings
import io.github.eslamasabry.oc.ui.words.res.manageSpaceTitle
import io.github.eslamasabry.oc.ui.words.res.manageSpaceTryAgain
import io.github.eslamasabry.oc.ui.words.res.manageSpaceWaitForExport
import io.github.eslamasabry.oc.ui.words.res.projectExportDetail
import io.github.eslamasabry.oc.ui.words.res.projectExportDone
import io.github.eslamasabry.oc.ui.words.res.projectExportDoneLeftOut
import io.github.eslamasabry.oc.ui.words.res.projectExportDonePrivate
import io.github.eslamasabry.oc.ui.words.res.projectExportFailed
import io.github.eslamasabry.oc.ui.words.res.projectExportFailedDestination
import io.github.eslamasabry.oc.ui.words.res.projectExportFailedSource
import io.github.eslamasabry.oc.ui.words.res.projectExportFailedSpace
import io.github.eslamasabry.oc.ui.words.res.projectExportNoProjects
import io.github.eslamasabry.oc.ui.words.res.projectExportPreparing
import io.github.eslamasabry.oc.ui.words.res.projectExportPrivate
import io.github.eslamasabry.oc.ui.words.res.projectExportPrivateDetail
import io.github.eslamasabry.oc.ui.words.res.projectExportProgress
import io.github.eslamasabry.oc.ui.words.res.projectExportProjectsLabel
import io.github.eslamasabry.oc.ui.words.res.projectExportRunning
import io.github.eslamasabry.oc.ui.words.res.projectExportSave
import io.github.eslamasabry.oc.ui.words.res.projectExportStop
import io.github.eslamasabry.oc.ui.words.res.projectExportStopDetail
import io.github.eslamasabry.oc.ui.words.res.projectExportStopped
import io.github.eslamasabry.oc.ui.words.res.projectStorageDeleteFailed
import io.github.eslamasabry.oc.ui.words.res.projectStorageMeasureFailed
import io.github.eslamasabry.oc.ui.words.res.thisPhoneExportProjects
import io.github.eslamasabry.oc.ui.words.res.workspaceDismissNotice
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Both doors share measured facts and foreground export; only Manage space offers cache/all-data
 * deletion.
 */
@Composable
public fun ProjectStorageScreen(
    state: ProjectStorageUi,
    actions: ProjectStorageActions,
    manageSpace: Boolean,
) {
    DisposableEffect(actions) { onDispose { actions.stopExport() } }
    val retry = stringResource(Res.string.manageSpaceTryAgain)
    val busy = stringResource(Res.string.manageSpaceWaitForExport)
    val facts = state.facts
    KitScreen(
        topBar = {
            KitTopBar(
                title =
                    stringResource(
                        if (manageSpace) Res.string.manageSpaceTitle
                        else Res.string.thisPhoneExportProjects
                    ),
                exit = if (manageSpace) KitTopBarExit.Close else KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        width = KitScreenWidth.Reading,
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            if (state.measuring) {
                KitStateView(
                    KitStateKind.Working,
                    stringResource(Res.string.manageSpaceMeasuring),
                    progress = KitProgress.Waiting(),
                )
            }
            if (state.notice == StorageNotice.MeasureFailed) {
                KitStateView(
                    KitStateKind.Error,
                    stringResource(Res.string.projectStorageMeasureFailed),
                    primary = KitAction(retry, onClick = actions::load),
                )
            }
            if (facts != null) {
                if (manageSpace)
                    KitNotice(
                        stringResource(
                            if (facts.canExport) Res.string.manageSpaceIntro
                            else Res.string.manageSpaceIntroNothingToExport
                        )
                    )
                state.receipt?.let { receipt ->
                    KitNotice(
                        message =
                            stringResource(
                                Res.string.projectExportDone,
                                PhoneSizes.text(receipt.bytes),
                                receipt.files,
                            ),
                        tone = StatusTone.Ok,
                        notes =
                            when {
                                state.exportedPrivate ->
                                    listOf(stringResource(Res.string.projectExportDonePrivate))
                                facts.privateFiles > 0 ->
                                    listOf(
                                        pluralStringResource(
                                            Res.plurals.projectExportDoneLeftOut,
                                            facts.privateFiles,
                                            facts.privateFiles,
                                        )
                                    )
                                else -> emptyList()
                            },
                    )
                }
                state.cacheFreed?.let {
                    KitNotice(
                        stringResource(Res.string.manageSpaceCacheCleared, PhoneSizes.text(it)),
                        tone = StatusTone.Ok,
                    )
                }
                state.notice
                    ?.takeUnless { it == StorageNotice.MeasureFailed }
                    ?.let { notice ->
                        KitNotice(
                            message =
                                stringResource(
                                    when (notice) {
                                        StorageNotice.CacheFailed ->
                                            Res.string.manageSpaceCacheFailed
                                        StorageNotice.DeleteFailed ->
                                            Res.string.projectStorageDeleteFailed
                                        StorageNotice.ExportFailed ->
                                            when (state.exportProblem) {
                                                ProjectExportProblem.Destination ->
                                                    Res.string.projectExportFailedDestination
                                                ProjectExportProblem.Space ->
                                                    Res.string.projectExportFailedSpace
                                                ProjectExportProblem.Source ->
                                                    Res.string.projectExportFailedSource
                                                else -> Res.string.projectExportFailed
                                            }
                                        StorageNotice.ExportStopped ->
                                            Res.string.projectExportStopped
                                        StorageNotice.MeasureFailed ->
                                            Res.string.projectStorageMeasureFailed
                                    }
                                ),
                            tone =
                                if (notice == StorageNotice.ExportStopped) StatusTone.Neutral
                                else StatusTone.Failure,
                            actions =
                                when (notice) {
                                    StorageNotice.CacheFailed ->
                                        listOf(KitLineAction(retry, actions::clearCache))
                                    StorageNotice.ExportFailed ->
                                        listOf(KitLineAction(retry, actions::export))
                                    else -> emptyList()
                                },
                            dismiss =
                                KitLineAction(
                                    stringResource(Res.string.workspaceDismissNotice),
                                    actions::dismiss,
                                ),
                        )
                    }
                if (state.exporting) {
                    val progress = state.progress
                    KitStateView(
                        KitStateKind.Working,
                        stringResource(Res.string.projectExportRunning),
                        progress =
                            if (progress != null && progress.total > 0)
                                KitProgress.Known(
                                    (progress.done.toFloat() / progress.total).coerceIn(0f, 1f),
                                    stringResource(
                                        Res.string.projectExportProgress,
                                        PhoneSizes.text(progress.done),
                                        PhoneSizes.text(progress.total),
                                    ),
                                )
                            else
                                KitProgress.Waiting(
                                    stringResource(Res.string.projectExportPreparing)
                                ),
                        secondary =
                            KitAction(
                                stringResource(Res.string.projectExportStop),
                                onClick = actions::stopExport,
                            ),
                        body = stringResource(Res.string.projectExportStopDetail),
                    )
                }
                KitRowGroup {
                    if (facts.canExport) {
                        if (!state.exporting)
                            KitRow(
                                title =
                                    stringResource(
                                        if (manageSpace) Res.string.manageSpaceExportFirst
                                        else Res.string.projectExportSave
                                    ),
                                supporting =
                                    if (facts.projects.isEmpty())
                                        stringResource(Res.string.projectExportNoProjects)
                                    else
                                        pluralStringResource(
                                            Res.plurals.projectExportDetail,
                                            facts.projects.size,
                                            facts.projects.size,
                                            PhoneSizes.text(facts.projectBytes),
                                        ),
                                enabled = !state.busy,
                                disabledReason = busy,
                                onClick = actions::export,
                            )
                        KitSwitchRow(
                            title = stringResource(Res.string.projectExportPrivate),
                            supporting = stringResource(Res.string.projectExportPrivateDetail),
                            checked = state.includePrivate,
                            enabled =
                                if (state.busy) KitEnabled.Disabled(busy) else KitEnabled.Enabled,
                            onCheckedChange = actions::setIncludePrivate,
                        )
                    } else if (!manageSpace)
                        KitRow(title = stringResource(Res.string.projectExportNoProjects))
                    if (manageSpace) {
                        KitRow(
                            title = stringResource(Res.string.manageSpaceClearCache),
                            supporting =
                                if (facts.cacheBytes > 0)
                                    stringResource(
                                        Res.string.manageSpaceClearCacheDetail,
                                        PhoneSizes.text(facts.cacheBytes),
                                    )
                                else stringResource(Res.string.manageSpaceClearCacheKeeps),
                            enabled = !state.busy,
                            disabledReason = busy,
                            onClick = actions::clearCache,
                        )
                        KitRow(
                            title = stringResource(Res.string.manageSpaceDeleteAll),
                            supporting = stringResource(Res.string.manageSpaceDeleteAllDetail),
                            enabled = !state.busy && !state.measuring,
                            disabledReason = busy,
                            onClick = actions::askDelete,
                        )
                    }
                }
                if (manageSpace || facts.projects.isNotEmpty())
                    KitRowGroup(
                        label =
                            stringResource(
                                if (manageSpace) Res.string.manageSpaceDeletedLabel
                                else Res.string.projectExportProjectsLabel
                            )
                    ) {
                        if (manageSpace && facts.serverInstalled)
                            KitRow(
                                title = stringResource(Res.string.manageSpaceServer),
                                supporting =
                                    stringResource(Res.string.manageSpaceServerDetail) +
                                        " · " +
                                        PhoneSizes.text(facts.serverBytes),
                            )
                        facts.projects.forEach { project ->
                            KitRow(
                                title = project.name,
                                supporting = PhoneSizes.text(project.bytes),
                            )
                        }
                        if (manageSpace)
                            KitRow(
                                title = stringResource(Res.string.manageSpaceSettings),
                                supporting =
                                    if (facts.savedServers == 0)
                                        stringResource(Res.string.manageSpaceSavedServers__eq0)
                                    else
                                        pluralStringResource(
                                            Res.plurals.manageSpaceSavedServers,
                                            facts.savedServers,
                                            facts.savedServers,
                                        ),
                            )
                    }
                if (manageSpace)
                    KitRowGroup(label = stringResource(Res.string.manageSpaceKeptLabel)) {
                        KitRow(title = stringResource(Res.string.manageSpaceKeptTermux))
                        KitRow(title = stringResource(Res.string.manageSpaceKeptComputers))
                        KitRow(title = stringResource(Res.string.manageSpaceKeptGit))
                    }
            }
        }
    }
    KitConfirm(
        spec =
            if (state.confirmDelete && facts != null)
                KitConfirmSpec(
                    title = stringResource(Res.string.manageSpaceDeleteTitle),
                    body = stringResource(Res.string.manageSpaceDeleteBody),
                    confirmLabel = stringResource(Res.string.manageSpaceDeleteAll),
                    kind = KitConfirmKind.Destructive,
                    consequences =
                        buildList {
                            if (facts.serverInstalled)
                                add(stringResource(Res.string.manageSpaceLostServer))
                            if (facts.projects.isNotEmpty())
                                add(
                                    pluralStringResource(
                                        Res.plurals.manageSpaceLostProjects,
                                        facts.projects.size,
                                        facts.projects.size,
                                        PhoneSizes.text(facts.projectBytes),
                                    )
                                )
                            add(
                                if (facts.savedServers == 0)
                                    stringResource(Res.string.manageSpaceLostSettings__eq0)
                                else
                                    pluralStringResource(
                                        Res.plurals.manageSpaceLostSettings,
                                        facts.savedServers,
                                        facts.savedServers,
                                    )
                            )
                            add(stringResource(Res.string.manageSpaceKeptAll))
                        },
                )
            else null,
        onConfirm = actions::confirmDelete,
        onCancel = actions::dismiss,
    )
}
