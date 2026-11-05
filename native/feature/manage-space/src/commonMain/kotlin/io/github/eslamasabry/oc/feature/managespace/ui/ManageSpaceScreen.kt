package io.github.eslamasabry.oc.feature.managespace.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.lifecycle.ManageSpaceState
import io.github.eslamasabry.oc.engine.lifecycle.StorageNotice
import io.github.eslamasabry.oc.feature.managespace.presentation.ManageSpaceActions
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitConsequence
import io.github.eslamasabry.oc.ui.kit.modal.KitConsequenceMark
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitProgressRow
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import kotlin.math.roundToLong
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Android's Manage space surface: independent of connections and the main application graph. */
@Composable
public fun ManageSpaceScreen(state: ManageSpaceState, actions: ManageSpaceActions) {
    var delete by rememberSaveable { mutableStateOf(false) }
    val initialFacts = state.facts
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
                if (state.loading && state.facts == null) KitSkeletonRows()
                else if (initialFacts != null)
                    KitNotice(
                        message =
                            stringResource(
                                if (
                                    initialFacts.projects.isNotEmpty() || initialFacts.serverInstalled
                                )
                                    Res.string.manageSpaceIntro
                                else Res.string.manageSpaceIntroNothingToExport
                            ),
                        icon = KitIcons.warning,
                        modifier = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp),
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
                                stringResource(Res.string.kitConfirmFailed)
                        }
                    KitNotice(
                        message = words,
                        modifier = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp),
                        tone =
                            when (notice) {
                                StorageNotice.CacheCleared,
                                StorageNotice.Exported -> StatusTone.Ok
                                StorageNotice.CacheFailed,
                                StorageNotice.ExportFailed,
                                StorageNotice.DeleteFailed -> StatusTone.Failure
                                else -> StatusTone.Neutral
                            },
                        notes =
                            buildList {
                                if (
                                    notice == StorageNotice.Exported &&
                                        state.export?.private == true
                                )
                                    add(stringResource(Res.string.projectExportDonePrivate))
                                if (
                                    notice == StorageNotice.Exported &&
                                        state.export?.private == false &&
                                        (state.facts?.privateFiles ?: 0) > 0
                                ) {
                                    val count = state.facts?.privateFiles ?: 0
                                    add(
                                        if (count == 1)
                                            stringResource(Res.string.projectExportDoneLeftOut__eq1)
                                        else
                                            pluralStringResource(
                                                Res.plurals.projectExportDoneLeftOut,
                                                count,
                                                count,
                                            )
                                    )
                                }
                            },
                        actions =
                            when (notice) {
                                StorageNotice.CacheFailed ->
                                    listOf(
                                        KitLineAction(
                                            stringResource(Res.string.manageSpaceTryAgain),
                                            actions::clearCache,
                                        )
                                    )
                                StorageNotice.ExportFailed ->
                                    listOf(
                                        KitLineAction(
                                            stringResource(Res.string.manageSpaceTryAgain),
                                            actions::export,
                                        )
                                    )
                                else -> emptyList()
                            },
                        dismiss =
                            KitLineAction(
                                stringResource(Res.string.workspaceDismissNotice),
                                actions::dismiss,
                            ),
                    )
                }
                state.facts?.let { facts ->
                    Spacer(Modifier.height(LocalKitTokens.current.sectionGap.dp))
                    KitRowGroup(leadingTiles = true) {
                        if (facts.projects.isNotEmpty() || facts.serverInstalled) {
                            if (state.exporting) {
                                KitProgressRow(
                                    stringResource(Res.string.projectExportRunning),
                                    value = null,
                                    valueLabel = bytes(state.bytesDone),
                                )
                                KitRow(
                                    title = stringResource(Res.string.projectExportStop),
                                    leading = KitRowLeading.Glyph(KitIcons.stop),
                                    supporting = stringResource(Res.string.projectExportStopDetail),
                                    onClick = actions::stopExport,
                                )
                            } else
                                KitRow(
                                    title = stringResource(Res.string.manageSpaceExportFirst),
                                    leading = KitRowLeading.Glyph(KitIcons.zip),
                                    supporting =
                                        if (facts.projects.isEmpty())
                                            stringResource(Res.string.projectExportNoProjects)
                                        else if (facts.projects.size == 1)
                                            stringResource(
                                                Res.string.projectExportDetail__eq1,
                                                facts.projects.size,
                                                bytes(facts.projectBytes),
                                            )
                                        else
                                            pluralStringResource(
                                                Res.plurals.projectExportDetail,
                                                facts.projects.size,
                                                facts.projects.size,
                                                bytes(facts.projectBytes),
                                            ),
                                    supportingMaxLines =
                                        2, // F: lib/ui/screens/manage_space_screen.dart:331
                                    trailing = KitRowTrailing.Chevron,
                                    enabled = !state.busy,
                                    disabledReason = wait,
                                    onClick = actions::export,
                                )
                            KitSwitchRow(
                                title = stringResource(Res.string.projectExportPrivate),
                                checked = state.includePrivate,
                                leading = KitRowLeading.Glyph(KitIcons.locked),
                                supporting = stringResource(Res.string.projectExportPrivateDetail),
                                enabled = enabled,
                                onCheckedChange = actions::includePrivate,
                            )
                        }
                        KitRow(
                            title = stringResource(Res.string.manageSpaceClearCache),
                            leading = KitRowLeading.Glyph(KitIcons.clearAll),
                            supporting =
                                state.cacheBytes
                                    ?.takeIf { it > 0 }
                                    ?.let {
                                        stringResource(
                                            Res.string.manageSpaceClearCacheDetail,
                                            bytes(it),
                                        )
                                    } ?: stringResource(Res.string.manageSpaceClearCacheKeeps),
                            supportingMaxLines = 2,
                            enabled = !state.busy,
                            disabledReason = wait,
                            onClick = actions::clearCache,
                        ) // F: lib/ui/screens/manage_space_screen.dart:192
                        KitRow(
                            title = stringResource(Res.string.manageSpaceDeleteAll),
                            leading = KitRowLeading.Glyph(KitIcons.delete),
                            supporting = stringResource(Res.string.manageSpaceDeleteAllDetail),
                            supportingMaxLines = 2, // F: lib/ui/kit/kit_row.dart:451
                            enabled = !state.busy && !state.loading,
                            disabledReason = wait,
                            onClick = { delete = true },
                        )
                    }
                    Spacer(Modifier.height(LocalKitTokens.current.sectionGap.dp))
                    KitRowGroup(
                        label = stringResource(Res.string.manageSpaceDeletedLabel),
                        leadingTiles = true,
                    ) {
                        if (facts.serverInstalled)
                            KitRow(
                                title = stringResource(Res.string.manageSpaceServer),
                                leading = KitRowLeading.Glyph(KitIcons.server),
                                supporting = stringResource(Res.string.manageSpaceServerDetail),
                                supportingMaxLines =
                                    2, // F: lib/ui/screens/manage_space_screen.dart:232
                                trailing =
                                    if (facts.serverBytes > 0)
                                        KitRowTrailing.Value(
                                            bytes(facts.serverBytes),
                                            chevron = false,
                                        )
                                    else KitRowTrailing.None,
                            )
                        facts.projects.forEach { project ->
                            KitRow(
                                title = project.name,
                                leading = KitRowLeading.Glyph(KitIcons.projects),
                                titleIsFileName = true,
                                trailing =
                                    KitRowTrailing.Value(bytes(project.bytes), chevron = false),
                            )
                        }
                        KitRow(
                            title = stringResource(Res.string.manageSpaceSettings),
                            leading = KitRowLeading.Glyph(KitIcons.settings),
                            supporting =
                                if (facts.savedServers == 0)
                                    stringResource(Res.string.manageSpaceSavedServers__eq0)
                                else if (facts.savedServers == 1)
                                    stringResource(Res.string.manageSpaceSavedServers__eq1)
                                else
                                    pluralStringResource(
                                        Res.plurals.manageSpaceSavedServers,
                                        facts.savedServers,
                                        facts.savedServers,
                                    ),
                        )
                    }
                    Spacer(Modifier.height(LocalKitTokens.current.sectionGap.dp))
                    KitRowGroup(
                        label = stringResource(Res.string.manageSpaceKeptLabel),
                        leadingTiles = true,
                    ) {
                        KitRow(
                            title = stringResource(Res.string.manageSpaceKeptTermux),
                            leading = KitRowLeading.Glyph(KitIcons.terminal),
                        )
                        KitRow(
                            title = stringResource(Res.string.manageSpaceKeptComputers),
                            leading = KitRowLeading.Glyph(KitIcons.computer),
                        )
                        KitRow(
                            title = stringResource(Res.string.manageSpaceKeptGit),
                            leading = KitRowLeading.Glyph(KitIcons.branch),
                        )
                    }
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
                        consequenceItems =
                            buildList<KitConsequence> {
                                val facts = state.facts ?: return@buildList
                                if (facts.serverInstalled)
                                    add(
                                        KitConsequence(
                                            stringResource(Res.string.manageSpaceLostServer),
                                            KitConsequenceMark.Lost,
                                        )
                                    )
                                if (facts.projects.isNotEmpty())
                                    add(
                                        KitConsequence(
                                            if (facts.projects.size == 1)
                                                stringResource(
                                                    Res.string.manageSpaceLostProjects__eq1,
                                                    facts.projects.size,
                                                    bytes(facts.projectBytes),
                                                )
                                            else
                                                pluralStringResource(
                                                    Res.plurals.manageSpaceLostProjects,
                                                    facts.projects.size,
                                                    facts.projects.size,
                                                    bytes(facts.projectBytes),
                                                ),
                                            KitConsequenceMark.Lost,
                                        )
                                    )
                                add(
                                    KitConsequence(
                                        when (facts.savedServers) {
                                            0 ->
                                                stringResource(
                                                    Res.string.manageSpaceLostSettings__eq0
                                                )
                                            1 ->
                                                stringResource(
                                                    Res.string.manageSpaceLostSettings__eq1
                                                )
                                            else ->
                                                pluralStringResource(
                                                    Res.plurals.manageSpaceLostSettings,
                                                    facts.savedServers,
                                                    facts.savedServers,
                                                )
                                        },
                                        KitConsequenceMark.Lost,
                                    )
                                )
                                add(
                                    KitConsequence(
                                        stringResource(Res.string.manageSpaceKeptAll),
                                        KitConsequenceMark.Kept,
                                    )
                                )
                            },
                    )
                else null,
            onConfirm = {
                delete = false
                actions.deleteAll()
            },
            onCancel = { delete = false },
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
