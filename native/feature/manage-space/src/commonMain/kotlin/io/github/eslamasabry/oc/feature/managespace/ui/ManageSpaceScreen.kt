package io.github.eslamasabry.oc.feature.managespace.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.engine.lifecycle.ManageSpaceState
import io.github.eslamasabry.oc.engine.lifecycle.StorageNotice
import io.github.eslamasabry.oc.feature.managespace.presentation.ManageSpaceActions
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.*
import kotlin.math.roundToLong
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Android's Manage space surface: independent of connections and the main application graph. */
@Composable
public fun ManageSpaceScreen(state: ManageSpaceState, actions: ManageSpaceActions) {
    var delete by rememberSaveable { mutableStateOf(false) }
    var cache by rememberSaveable { mutableStateOf(false) }
    val wait = stringResource(Res.string.manageSpaceWaitForExport)
    val enabled = if (state.busy) KitEnabled.Disabled(wait) else KitEnabled.Enabled
    KitSheetHost {
        KitScreen(
            topBar = {
                KitTopBar(
                    title = stringResource(Res.string.manageSpaceTitle),
                    exit = KitTopBarExit.Close,
                    exitLabel = stringResource(Res.string.kitTopBarBack),
                    onExit = actions::back,
                )
            },
            width = KitScreenWidth.Reading,
            loading = state.loading,
            loadingLabel = stringResource(Res.string.manageSpaceMeasuring),
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                KitText(
                    stringResource(
                        if (state.facts?.projects?.isNotEmpty() == true) Res.string.manageSpaceIntro
                        else Res.string.manageSpaceIntroNothingToExport
                    )
                )
                if (state.failed)
                    KitNotice(
                        message = stringResource(Res.string.projectExportFailedSource),
                        tone = StatusTone.Failure,
                        actions =
                            listOf(
                                KitLineAction(
                                    stringResource(Res.string.manageSpaceTryAgain),
                                    actions::refresh,
                                )
                            ),
                    )
                state.notice?.let { notice ->
                    val words =
                        when (notice) {
                            StorageNotice.CacheCleared ->
                                stringResource(
                                    Res.string.manageSpaceCacheCleared,
                                    bytes(state.cacheFreed),
                                )
                            StorageNotice.CacheFailed ->
                                stringResource(Res.string.manageSpaceCacheFailed)
                            StorageNotice.Exported ->
                                stringResource(
                                    Res.string.projectExportDone,
                                    bytes(state.export?.bytes ?: 0),
                                    state.export?.files ?: 0,
                                )
                            StorageNotice.ExportFailed ->
                                stringResource(Res.string.projectExportFailed)
                            StorageNotice.ExportStopped ->
                                stringResource(Res.string.projectExportStopped)
                            StorageNotice.DeleteFailed ->
                                stringResource(Res.string.manageSpaceCacheFailed)
                        }
                    KitNotice(
                        message = words,
                        dismiss =
                            KitLineAction(
                                stringResource(Res.string.workspaceDismissNotice),
                                actions::dismiss,
                            ),
                    )
                    if (notice == StorageNotice.Exported && state.export?.private == true)
                        KitText(stringResource(Res.string.projectExportDonePrivate))
                    if (
                        notice == StorageNotice.Exported &&
                            state.export?.private == false &&
                            (state.facts?.privateFiles ?: 0) > 0
                    ) {
                        val count = state.facts?.privateFiles ?: 0
                        KitText(
                            pluralStringResource(Res.plurals.projectExportDoneLeftOut, count, count)
                        )
                    }
                }
                state.facts?.let { facts ->
                    KitRowGroup(label = stringResource(Res.string.manageSpaceDeletedLabel)) {
                        if (facts.serverInstalled || facts.serverBytes > 0)
                            KitRow(
                                title = stringResource(Res.string.manageSpaceServer),
                                supporting = stringResource(Res.string.manageSpaceServerDetail),
                                supportingMaxLines = 3,
                                trailing =
                                    KitRowTrailing.Value(bytes(facts.serverBytes), chevron = false),
                            )
                        facts.projects.forEach { project ->
                            KitRow(
                                title = project.name,
                                titleIsFileName = true,
                                trailing =
                                    KitRowTrailing.Value(bytes(project.bytes), chevron = false),
                            )
                        }
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
                    if (facts.projects.isNotEmpty() || facts.privateDataBytes > 0) {
                        KitRowGroup(label = stringResource(Res.string.manageSpaceExportFirst)) {
                            KitSwitchRow(
                                title = stringResource(Res.string.projectExportPrivate),
                                supporting = stringResource(Res.string.projectExportPrivateDetail),
                                risk = stringResource(Res.string.projectExportPrivateDetail),
                                checked = state.includePrivate,
                                enabled = enabled,
                                onCheckedChange = actions::includePrivate,
                            )
                            if (state.exporting) {
                                KitRow(
                                    title = stringResource(Res.string.projectExportRunning),
                                    supporting = bytes(state.bytesDone),
                                )
                                KitRow(
                                    title = stringResource(Res.string.projectExportStop),
                                    supporting = stringResource(Res.string.projectExportStopDetail),
                                    onClick = actions::stopExport,
                                )
                            } else
                                KitRow(
                                    title = stringResource(Res.string.projectExportSave),
                                    enabled =
                                        !state.busy &&
                                            (facts.projects.isNotEmpty() ||
                                                (state.includePrivate &&
                                                    facts.privateDataBytes > 0)),
                                    disabledReason =
                                        if (state.busy) wait
                                        else stringResource(Res.string.projectExportNoProjects),
                                    onClick = actions::export,
                                )
                        }
                    }
                }
                KitRowGroup {
                    KitRow(
                        title = stringResource(Res.string.manageSpaceClearCache),
                        supporting =
                            state.cacheBytes?.let {
                                stringResource(Res.string.manageSpaceClearCacheDetail, bytes(it))
                            } ?: stringResource(Res.string.manageSpaceClearCacheKeeps),
                        supportingMaxLines = 3,
                        enabled = !state.busy,
                        disabledReason = wait,
                        onClick = { cache = true },
                    )
                    KitRow(
                        title = stringResource(Res.string.manageSpaceDeleteAll),
                        supporting = stringResource(Res.string.manageSpaceDeleteAllDetail),
                        supportingMaxLines = 3,
                        enabled = !state.busy && !state.loading && state.facts != null,
                        disabledReason =
                            if (state.busy) wait
                            else stringResource(Res.string.manageSpaceMeasuring),
                        onClick = { delete = true },
                    )
                }
                KitRowGroup(label = stringResource(Res.string.manageSpaceKeptLabel)) {
                    KitRow(title = stringResource(Res.string.manageSpaceKeptTermux))
                    KitRow(title = stringResource(Res.string.manageSpaceKeptComputers))
                    KitRow(title = stringResource(Res.string.manageSpaceKeptGit))
                }
            }
        }
        KitConfirm(
            spec =
                if (delete && !state.busy && !state.loading && state.facts != null)
                    KitConfirmSpec(
                        title = stringResource(Res.string.manageSpaceDeleteTitle),
                        confirmLabel = stringResource(Res.string.manageSpaceDeleteAll),
                        kind = KitConfirmKind.Destructive,
                        body = stringResource(Res.string.manageSpaceDeleteBody),
                        consequences =
                            listOf(
                                stringResource(Res.string.manageSpaceLostServer),
                                stringResource(Res.string.manageSpaceSettings),
                            ) + state.facts?.projects.orEmpty().map { it.name },
                    )
                else null,
            onConfirm = {
                delete = false
                actions.deleteAll()
            },
            onCancel = { delete = false },
        )
        KitConfirm(
            spec =
                if (cache && !state.busy)
                    KitConfirmSpec(
                        title = stringResource(Res.string.manageSpaceClearCacheQuestion),
                        confirmLabel = stringResource(Res.string.manageSpaceClearCache),
                        body = stringResource(Res.string.manageSpaceClearCacheKeeps),
                    )
                else null,
            onConfirm = {
                cache = false
                actions.clearCache()
            },
            onCancel = { cache = false },
        )
    }
}

/** Flutter `formatPhoneStorage`: binary steps, one decimal; Latin digits in an LTR isolate. */
private fun bytes(value: Long): String {
    if (value < 1024) return "\u2066${value.coerceAtLeast(0)} B\u2069"
    val units = listOf("KB", "MB", "GB", "TB")
    var size = value / 1024.0
    var unit = 0
    while (size >= 1024 && unit < units.lastIndex) {
        size /= 1024
        unit++
    }
    val tenths = (size * 10).roundToLong()
    return "\u2066${tenths / 10}.${tenths % 10} ${units[unit]}\u2069"
}
