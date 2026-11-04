package io.github.eslamasabry.oc.feature.diagnostics.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.diagnostics.crash.CrashSource
import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitKind
import io.github.eslamasabry.oc.feature.diagnostics.presentation.KeptActions
import io.github.eslamasabry.oc.feature.diagnostics.presentation.KeptCrashRow
import io.github.eslamasabry.oc.feature.diagnostics.presentation.KeptExits
import io.github.eslamasabry.oc.feature.diagnostics.presentation.KeptNotice
import io.github.eslamasabry.oc.feature.diagnostics.presentation.KeptUi
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.content.log.KitLogPanel
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.log.KitSelectableText
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.appDiagnosticsClearBody
import io.github.eslamasabry.oc.ui.words.res.appDiagnosticsClearConfirm
import io.github.eslamasabry.oc.ui.words.res.appDiagnosticsClearTitle
import io.github.eslamasabry.oc.ui.words.res.commonRetry
import io.github.eslamasabry.oc.ui.words.res.crashReportDetailCategory
import io.github.eslamasabry.oc.ui.words.res.crashReportPreviewBody
import io.github.eslamasabry.oc.ui.words.res.crashReportsDelete
import io.github.eslamasabry.oc.ui.words.res.crashReportsDeleteBody
import io.github.eslamasabry.oc.ui.words.res.crashReportsDeleteConfirm
import io.github.eslamasabry.oc.ui.words.res.crashReportsDeleteTitle
import io.github.eslamasabry.oc.ui.words.res.crashReportsFailed
import io.github.eslamasabry.oc.ui.words.res.crashReportsLabel
import io.github.eslamasabry.oc.ui.words.res.crashReportsNone
import io.github.eslamasabry.oc.ui.words.res.crashReportsNoneBody
import io.github.eslamasabry.oc.ui.words.res.crashReportsNote
import io.github.eslamasabry.oc.ui.words.res.crashReportsOffDeletes
import io.github.eslamasabry.oc.ui.words.res.crashReportsSwitch
import io.github.eslamasabry.oc.ui.words.res.crashReportsSwitchBody
import io.github.eslamasabry.oc.ui.words.res.crashReportsUnavailable
import io.github.eslamasabry.oc.ui.words.res.diagnosticsExitCrash
import io.github.eslamasabry.oc.ui.words.res.diagnosticsExitForceStop
import io.github.eslamasabry.oc.ui.words.res.diagnosticsExitHistoryFailed
import io.github.eslamasabry.oc.ui.words.res.diagnosticsExitHistoryNote
import io.github.eslamasabry.oc.ui.words.res.diagnosticsExitHistoryTitle
import io.github.eslamasabry.oc.ui.words.res.diagnosticsExitHistoryUnsupported
import io.github.eslamasabry.oc.ui.words.res.diagnosticsExitHistoryUnsupportedBody
import io.github.eslamasabry.oc.ui.words.res.diagnosticsExitKilled
import io.github.eslamasabry.oc.ui.words.res.diagnosticsExitLowMemory
import io.github.eslamasabry.oc.ui.words.res.diagnosticsExitNoProblems
import io.github.eslamasabry.oc.ui.words.res.diagnosticsExitNormal
import io.github.eslamasabry.oc.ui.words.res.diagnosticsExitUpdate
import io.github.eslamasabry.oc.ui.words.res.diagnosticsReportCaptureOff
import io.github.eslamasabry.oc.ui.words.res.diagnosticsReportEmpty
import io.github.eslamasabry.oc.ui.words.res.diagnosticsReportPrivacy
import io.github.eslamasabry.oc.ui.words.res.diagnosticsReportShare
import io.github.eslamasabry.oc.ui.words.res.diagnosticsReportShareFailed
import io.github.eslamasabry.oc.ui.words.res.diagnosticsReportStale
import io.github.eslamasabry.oc.ui.words.res.diagnosticsReportTitle
import io.github.eslamasabry.oc.ui.words.res.diagnosticsUnavailable
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.reportProblemClearFailed
import io.github.eslamasabry.oc.ui.words.res.reportProblemErrorsLabel
import io.github.eslamasabry.oc.ui.words.res.reportProblemKeptLink
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Settings › Report a problem › Everything this phone kept (Flutter `AppDiagnosticsKeptScreen`):
 * the opt-in crash reports with their switch, the saved reports (time and category only), Preview →
 * the exact text → Share report; Android's record of each time the app closed; and the errors kept
 * on this phone with Clear. Nothing here sends anything by itself.
 */
@Composable
public fun KeptScreen(state: KeptUi, actions: KeptActions) {
    val gutter = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.reportProblemKeptLink),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::onBack,
            )
        },
        width = KitScreenWidth.Reading,
    ) {
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = Space.S3.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
        ) {
            state.notice?.let { notice ->
                KitNotice(
                    noticeWords(notice),
                    gutter.testTag("kept-notice"),
                    tone =
                        if (notice == KeptNotice.Stale || notice == KeptNotice.CaptureOff)
                            StatusTone.Neutral
                        else StatusTone.Failure,
                )
            }
            ErrorsSection(state, actions)
            CrashSection(state, actions, gutter)
            ExitsSection(state.exits, actions, gutter)
        }
    }
    KitConfirm(
        spec =
            if (state.confirmDelete) {
                KitConfirmSpec(
                    title = stringResource(Res.string.crashReportsDeleteTitle),
                    confirmLabel = stringResource(Res.string.crashReportsDeleteConfirm),
                    kind = KitConfirmKind.Destructive,
                    body =
                        if (state.errorsKept > 0)
                            pluralStringResource(
                                Res.plurals.crashReportsDeleteBodyWithErrors,
                                state.errorsKept,
                                state.errorsKept,
                            )
                        else stringResource(Res.string.crashReportsDeleteBody),
                )
            } else if (state.confirmClear) {
                KitConfirmSpec(
                    title =
                        pluralStringResource(
                            Res.plurals.appDiagnosticsClearTitle,
                            state.errorsKept,
                            state.errorsKept,
                        ),
                    confirmLabel =
                        pluralStringResource(
                            Res.plurals.appDiagnosticsClearConfirm,
                            state.errorsKept,
                            state.errorsKept,
                        ),
                    kind = KitConfirmKind.Destructive,
                    body =
                        pluralStringResource(
                            Res.plurals.appDiagnosticsClearBody,
                            state.errorsKept,
                            state.errorsKept,
                        ),
                )
            } else {
                null
            },
        onConfirm = {
            if (state.confirmDelete) actions.onConfirmDelete() else actions.onConfirmClear()
        },
        onCancel = actions::onCancelConfirm,
    )
}

@Composable
private fun ErrorsSection(state: KeptUi, actions: KeptActions) {
    KitSectionLabel(
        pluralStringResource(
            Res.plurals.reportProblemErrorsLabel,
            state.errorsKept,
            state.errorsKept,
        )
    )
    if (state.errorsKept > 0) {
        KitRowGroup {
            KitRow(
                title =
                    pluralStringResource(
                        Res.plurals.appDiagnosticsClearConfirm,
                        state.errorsKept,
                        state.errorsKept,
                    ),
                modifier = Modifier.testTag("kept-clear"),
                onClick = actions::onClearErrors,
            )
        }
    }
}

@Composable
private fun CrashSection(state: KeptUi, actions: KeptActions, gutter: Modifier) {
    var selected by remember { mutableStateOf<KeptCrashRow?>(null) }
    KitSectionLabel(stringResource(Res.string.crashReportsLabel))
    val unavailable = stringResource(Res.string.crashReportsUnavailable)
    KitRowGroup {
        KitSwitchRow(
            title = stringResource(Res.string.crashReportsSwitch),
            checked = state.crashOn,
            supporting = stringResource(Res.string.crashReportsSwitchBody),
            enabled =
                if (state.crashAvailable) KitEnabled.Enabled else KitEnabled.Disabled(unavailable),
            onCheckedChange = actions::onCrashCapture,
        )
        if (state.crashOn && state.crashRows.isEmpty()) {
            KitRow(
                title = stringResource(Res.string.crashReportsNone),
                supporting = stringResource(Res.string.crashReportsNoneBody),
                supportingMaxLines = 2,
            )
        }
        state.crashRows.forEach { row ->
            KitRow(
                title = crashKind(row.source),
                supporting = row.time,
                onClick = { selected = row },
            )
        }
        if (state.crashRows.isNotEmpty()) {
            KitRow(
                title = stringResource(Res.string.diagnosticsReportTitle),
                modifier = Modifier.testTag("kept-preview"),
                onClick = actions::onPreviewCrashReport,
            )
            KitRow(
                title =
                    pluralStringResource(
                        Res.plurals.crashReportsDelete,
                        state.crashRows.size,
                        state.crashRows.size,
                    ),
                onClick = actions::onDeleteCrashReports,
            )
        }
    }
    KitGroupNote(stringResource(Res.string.crashReportsNote))
    val effect =
        when {
            state.crashOn && state.crashRows.isNotEmpty() && state.errorsKept > 0 ->
                pluralStringResource(
                    Res.plurals.crashReportsOffDeletesAndClears,
                    state.errorsKept,
                    state.errorsKept,
                )
            state.crashOn && state.crashRows.isNotEmpty() ->
                stringResource(Res.string.crashReportsOffDeletes)
            state.crashOn && state.errorsKept > 0 ->
                pluralStringResource(
                    Res.plurals.crashReportsOffClears,
                    state.errorsKept,
                    state.errorsKept,
                )
            !state.crashOn && state.errorsKept > 0 ->
                pluralStringResource(
                    Res.plurals.crashReportsOnClears,
                    state.errorsKept,
                    state.errorsKept,
                )
            else -> null
        }
    effect?.let { KitGroupNote(it) }
    val record = selected?.takeIf { it in state.crashRows }
    KitSheet(
        visible = record != null,
        onDismissRequest = { selected = null },
        title =
            record?.let { crashKind(it.source) } ?: stringResource(Res.string.crashReportsLabel),
        subtitle = record?.time,
        closeLabel = stringResource(Res.string.kitSheetClose),
    ) {
        record?.let { row ->
            KitGroupNote(stringResource(Res.string.crashReportPreviewBody))
            KitDetailsFold(
                label = stringResource(Res.string.kitDetails),
                copyLabel = stringResource(Res.string.kitCopyDetails),
                notes =
                    listOf(
                        "${stringResource(Res.string.crashReportDetailSource)}: crash.${row.source.wire}",
                        "${stringResource(Res.string.crashReportDetailCategory)}: ${row.category}",
                    ),
            )
        }
    }
    state.preview?.let { preview ->
        KitGroupNote(stringResource(Res.string.diagnosticsReportPrivacy))
        KitGroupNote(
            pluralStringResource(
                Res.plurals.diagnosticsReportSize,
                preview.records,
                preview.records,
                "${preview.bytes} B",
            )
        )
        KitGroupNote(stringResource(Res.string.crashReportPreviewBody))
        Box(gutter.testTag("kept-preview-text")) { KitLogPanel(redactedText = preview.text, live = false) }
        Box(gutter) {
            KitActionBlock(
                primary =
                    KitAction(
                        stringResource(Res.string.diagnosticsReportShare),
                        actions::onShareCrashReport,
                        working = state.sharing,
                    )
            )
        }
    }
}

@Composable
private fun ExitsSection(exits: KeptExits, actions: KeptActions, gutter: Modifier) {
    KitSectionLabel(stringResource(Res.string.diagnosticsExitHistoryTitle))
    when (exits) {
        KeptExits.Loading -> Unit
        KeptExits.Unsupported ->
            KitRowGroup {
                KitRow(
                    title = stringResource(Res.string.diagnosticsExitHistoryUnsupported),
                    supporting = stringResource(Res.string.diagnosticsExitHistoryUnsupportedBody),
                )
            }
        KeptExits.Failed ->
            KitNotice(
                stringResource(Res.string.diagnosticsExitHistoryFailed),
                gutter,
                tone = StatusTone.Failure,
                actions =
                    listOf(
                        KitLineAction(stringResource(Res.string.commonRetry), actions::onRetryExits)
                    ),
            )
        is KeptExits.Ready -> {
            KitRowGroup {
                if (exits.rows.none { it.kind.notable })
                    KitRow(title = stringResource(Res.string.diagnosticsExitNoProblems))
                exits.rows.forEach { row ->
                    KitRow(title = exitWords(row.kind), supporting = row.time)
                }
            }
            KitGroupNote(stringResource(Res.string.diagnosticsExitHistoryNote))
        }
    }
}

@Composable
private fun exitWords(kind: ExitKind): String =
    stringResource(
        when (kind) {
            ExitKind.Normal -> Res.string.diagnosticsExitNormal
            ExitKind.Update -> Res.string.diagnosticsExitUpdate
            ExitKind.ForceStop -> Res.string.diagnosticsExitForceStop
            ExitKind.LowMemory,
            ExitKind.MemoryLimit -> Res.string.diagnosticsExitLowMemory
            ExitKind.Crash -> Res.string.diagnosticsExitCrash
            ExitKind.Killed -> Res.string.diagnosticsExitKilled
        }
    )

@Composable
private fun noticeWords(notice: KeptNotice): String =
    stringResource(
        when (notice) {
            KeptNotice.CrashUnavailable -> Res.string.crashReportsUnavailable
            KeptNotice.CrashUpdateFailed -> Res.string.crashReportsFailed
            KeptNotice.CaptureOff -> Res.string.diagnosticsReportCaptureOff
            KeptNotice.NothingToShare -> Res.string.diagnosticsReportEmpty
            KeptNotice.Stale -> Res.string.diagnosticsReportStale
            KeptNotice.ShareFailed -> Res.string.diagnosticsReportShareFailed
            KeptNotice.DetailsUnavailable -> Res.string.diagnosticsUnavailable
            KeptNotice.ClearFailed -> Res.string.reportProblemClearFailed
        }
    )

@Composable
private fun crashKind(source: CrashSource): String =
    stringResource(
        when (source) {
            CrashSource.Widget -> Res.string.crashKindScreen
            CrashSource.Native -> Res.string.crashKindClosed
            CrashSource.Anr -> Res.string.crashKindNotResponding
            else -> Res.string.crashKindError
        }
    )
