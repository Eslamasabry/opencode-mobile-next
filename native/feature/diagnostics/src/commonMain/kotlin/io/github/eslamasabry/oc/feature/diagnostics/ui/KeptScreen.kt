package io.github.eslamasabry.oc.feature.diagnostics.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.engine.diagnostics.crash.CrashSource
import io.github.eslamasabry.oc.engine.diagnostics.exit.ExitKind
import io.github.eslamasabry.oc.feature.diagnostics.presentation.KeptActions
import io.github.eslamasabry.oc.feature.diagnostics.presentation.KeptCrashRow
import io.github.eslamasabry.oc.feature.diagnostics.presentation.KeptExits
import io.github.eslamasabry.oc.feature.diagnostics.presentation.KeptNotice
import io.github.eslamasabry.oc.feature.diagnostics.presentation.KeptUi
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.log.KitSelectableText
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHeight
import io.github.eslamasabry.oc.ui.kit.row.KitExpandRow
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
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
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private const val REPORT_KIB = 1024 // F: lib/ui/screens/crash_report_share.dart:164

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
                title = stringResource(Res.string.e7SettingsUi88),
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
                .padding(top = Space.S2.dp, bottom = Space.S6.dp),
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
            PerformanceSection(state, actions, gutter)
            CrashSection(state, actions, gutter)
            ExitsSection(state.exits, actions, gutter)
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
                        consequences =
                            if (state.crashRows.isNotEmpty())
                                listOf(stringResource(Res.string.crashReportsClearedToo))
                            else emptyList(),
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
}

@Composable
private fun ErrorsSection(state: KeptUi, actions: KeptActions) {
    if (state.errorsKept <= 0) return
    var copied by remember { mutableStateOf(false) }
    @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current
    KitSectionLabel(
        pluralStringResource(
            Res.plurals.reportProblemErrorsLabel,
            state.errorsKept,
            state.errorsKept,
        ),
        trailing = {
            Row {
            KitIconButton(
                KitIcons.copy,
                stringResource(Res.string.reportProblemCopyShort),
                {
                    clipboard.setText(AnnotatedString(actions.errorLogText()))
                    copied = true
                },
                modifier = Modifier.testTag("kept-copy-errors"),
            )
            KitIconButton(
                KitIcons.delete,
                pluralStringResource(
                    Res.plurals.appDiagnosticsClearConfirm,
                    state.errorsKept,
                    state.errorsKept,
                ),
                actions::onClearErrors,
                modifier = Modifier.testTag("kept-clear"),
            )
            }
        },
    )
    if (copied) KitNotice(stringResource(Res.string.kitCopied))
    // The kept errors, expandable in place: the host's text is already redacted.
    val lines = remember(state.errorsKept) { actions.errorLogText().lines().filter { it.isNotBlank() } }
    KitDetailsFold(
        stringResource(Res.string.kitDetails),
        stringResource(Res.string.kitCopyDetails),
        notes = lines,
    )
}

/** Where time went in this run (Flutter `PerfTraceSection`): steps by name, Copy and Clear. */
@Composable
private fun PerformanceSection(state: KeptUi, actions: KeptActions, gutter: Modifier) {
    var copied by remember { mutableStateOf(false) }
    @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current
    KitSectionLabel(stringResource(Res.string.perfTraceTitle))
    KitText(stringResource(Res.string.perfTraceBody), gutter, role = TypeRole.Secondary, tone = TextTone.Secondary)
    if (state.perf.isEmpty()) {
        KitText(stringResource(Res.string.perfTraceEmpty), gutter, role = TypeRole.Secondary, tone = TextTone.Secondary)
        return
    }
    KitRowGroup(label = stringResource(Res.string.perfTraceSlowest)) {
        for (step in state.perf.take(12)) {
            KitRow(
                title = step.name,
                titleMaxLines = 2,
                supporting =
                    stringResource(
                        Res.string.perfTraceStatLine,
                        step.count,
                        step.typical,
                        step.slow,
                        step.longest,
                    ) +
                        if (step.errors > 0)
                            " · " + pluralStringResource(Res.plurals.perfTraceFailed, step.errors, step.errors)
                        else "",
                supportingMaxLines = 3,
            )
        }
    }
    KitRow(
        title = stringResource(Res.string.perfTraceCopy),
        leading = KitRowLeading.Glyph(KitIcons.copy),
        onClick = {
            clipboard.setText(AnnotatedString(actions.performanceText()))
            copied = true
        },
    )
    KitRow(
        title = stringResource(Res.string.perfTraceClearTimings),
        leading = KitRowLeading.Glyph(KitIcons.delete),
        onClick = {
            copied = false
            actions.onClearPerformance()
            }
        },
    )
    if (copied) KitNotice(stringResource(Res.string.perfTraceCopied), gutter)
}

@Composable
private fun CrashSection(state: KeptUi, actions: KeptActions, gutter: Modifier) {
    var selected by remember { mutableStateOf<KeptCrashRow?>(null) }
    val unavailable = stringResource(Res.string.crashReportsUnavailable)
    KitRowGroup(label = stringResource(Res.string.crashReportsLabel)) {
        KitSwitchRow(
            title = stringResource(Res.string.crashReportsSwitch),
            checked = state.crashOn,
            supporting = stringResource(Res.string.crashReportsSwitchBody),
            enabled =
                if (state.crashAvailable) KitEnabled.Enabled else KitEnabled.Disabled(unavailable),
            onCheckedChange = actions::onCrashCapture,
        )
    }
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
    KitGroupNote(stringResource(Res.string.crashReportsNote))
    if (state.crashOn) {
        Spacer(Modifier.height(Space.S3.dp))
        KitRowGroup(leadingTiles = true) {
            if (state.crashRows.isEmpty())
                KitRow(
                    stringResource(Res.string.crashReportsNone),
                    leading = KitRowLeading.Glyph(KitIcons.info),
                    supporting = stringResource(Res.string.crashReportsNoneBody),
                    supportingMaxLines = 2, // F: lib/ui/screens/crash_reports_section.dart:246
                )
            state.crashRows.forEach { row ->
                KitRow(
                    crashKind(row.source),
                    supporting = row.time,
                    leading =
                        KitRowLeading.Glyph(
                            if (row.source == CrashSource.Anr) KitIcons.timer else KitIcons.error
                        ),
                    onClick = { selected = row },
                )
            }
            if (state.crashRows.isNotEmpty()) {
                KitRow(
                    stringResource(Res.string.diagnosticsReportOpen),
                    modifier = Modifier.testTag("kept-preview"),
                    leading = KitRowLeading.Glyph(KitIcons.upload),
                    onClick = actions::onPreviewCrashReport,
                )
                KitRow(
                    pluralStringResource(
                        Res.plurals.crashReportsDelete,
                        state.crashRows.size,
                        state.crashRows.size,
                    ),
                    leading = KitRowLeading.Glyph(KitIcons.delete),
                    onClick = actions::onDeleteCrashReports,
                )
            }
        }
    }
    val record = selected?.takeIf { state.crashOn && it in state.crashRows }
    KitSheet(
        visible = record != null,
        onDismissRequest = { selected = null },
        title =
            record?.let { crashKind(it.source) } ?: stringResource(Res.string.crashReportsLabel),
        subtitle = record?.time,
        icon = if (record?.source == CrashSource.Anr) KitIcons.timer else KitIcons.error,
        closeLabel = stringResource(Res.string.kitSheetClose),
    ) {
        record?.let { row ->
            KitText(
                stringResource(Res.string.crashReportPreviewBody),
                role = TypeRole.Secondary,
                tone = TextTone.Secondary,
            )
            Spacer(Modifier.height(Space.S3.dp))
            KitDetailsFold(
                stringResource(Res.string.kitDetails),
                stringResource(Res.string.kitCopyDetails),
                notes =
                    listOf(
                        "${stringResource(Res.string.crashReportDetailSource)}: crash.${row.source.wire}",
                        "${stringResource(Res.string.crashReportDetailCategory)}: ${row.category}",
                    ),
            )
        }
    }
    SharePreview(state, actions)
}

@Composable
private fun SharePreview(state: KeptUi, actions: KeptActions) {
    val preview = state.preview
    KitSheet(
        visible = preview != null,
        onDismissRequest = actions::onClosePreview,
        title = stringResource(Res.string.diagnosticsReportTitle),
        subtitle = stringResource(Res.string.diagnosticsReportPrivacy),
        icon = KitIcons.bug,
        height = KitSheetHeight.Full,
        primary =
            KitAction(
                stringResource(Res.string.diagnosticsReportShare),
                actions::onShareCrashReport,
                icon = KitIcons.upload,
                working = state.sharing,
            ),
    ) {
        if (preview != null) {
            state.notice?.let { KitNotice(noticeWords(it)) }
            val size =
                if (preview.bytes < REPORT_KIB) "${preview.bytes} B"
                else "${Numbers.decimal(preview.bytes / REPORT_KIB.toDouble(), 1)} KB"
            KitText(
                pluralStringResource(
                    Res.plurals.diagnosticsReportSize,
                    preview.records,
                    preview.records,
                    size,
                ),
                role = TypeRole.Secondary,
                tone = TextTone.Secondary,
            )
            Spacer(Modifier.height(Space.S2.dp))
            KitSelectableText(
                preview.text,
                Modifier.testTag("kept-preview-text"),
                role = TypeRole.Mono,
            )
        }
    }
}

@Composable
private fun ExitsSection(exits: KeptExits, actions: KeptActions, gutter: Modifier) {
    if (exits == KeptExits.Loading) return
    var showAll by remember { mutableStateOf(false) }
    var routineOpen by remember { mutableStateOf(false) }
    KitSectionLabel(stringResource(Res.string.diagnosticsExitHistoryTitle))
    when (exits) {
        KeptExits.Loading -> Unit
        KeptExits.Unsupported ->
            KitRowGroup(leadingTiles = true) {
                KitRow(
                    stringResource(Res.string.diagnosticsExitHistoryUnsupported),
                    leading = KitRowLeading.Glyph(KitIcons.info),
                    supporting = stringResource(Res.string.diagnosticsExitHistoryUnsupportedBody),
                    supportingMaxLines = 2, // F: lib/ui/screens/exit_history_section.dart:72
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
            // Flutter considers force-stop routine here, independently of the runtime attention
            // policy.
            val problems =
                exits.rows.filter {
                    it.kind in
                        setOf(
                            ExitKind.Crash,
                            ExitKind.LowMemory,
                            ExitKind.MemoryLimit,
                            ExitKind.Killed,
                        )
                }
            val routine = exits.rows.filterNot { it in problems }
            if (problems.isEmpty())
                KitGroupNote(stringResource(Res.string.diagnosticsExitNoProblems))
            KitRowGroup(leadingTiles = true) {
                val shown =
                    if (showAll) problems
                    else problems.take(5) // F: lib/ui/screens/exit_history_section.dart:43
                shown.forEach { row ->
                    KitRow(
                        exitWords(row.kind),
                        supporting = row.time,
                        leading =
                            KitRowLeading.Glyph(
                                if (row.kind == ExitKind.Crash) KitIcons.error else KitIcons.warning
                            ),
                    )
                }
                if (shown.size < problems.size)
                    KitRow(
                        pluralStringResource(
                            Res.plurals.diagnosticsExitShowAll,
                            problems.size,
                            problems.size,
                        ),
                        leading = KitRowLeading.Glyph(KitIcons.info),
                        onClick = { showAll = true },
                    )
                if (routine.isNotEmpty())
                    KitExpandRow(
                        pluralStringResource(
                            Res.plurals.diagnosticsExitRoutine,
                            routine.size,
                            routine.size,
                        ),
                        routineOpen,
                        modifier = Modifier.testTag("exit-history-routine"),
                        onExpandedChange = { routineOpen = it },
                    ) {
                        routine.forEach { row ->
                            KitRow(exitWords(row.kind), supporting = row.time)
                        }
                    }
            }
        }
    }
    KitGroupNote(stringResource(Res.string.diagnosticsExitHistoryNote))
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
