package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.analytics.health.ProblemReportContent
import io.github.eslamasabry.oc.engine.analytics.health.ReportClose
import io.github.eslamasabry.oc.feature.settings.presentation.ReportStage
import io.github.eslamasabry.oc.feature.settings.presentation.ReportUi
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsActions
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.foundation.KitDivider
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.motion.KitSwap
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.text.kitLocalTime
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kitchat.KitRedactedCopyIconButton
import io.github.eslamasabry.oc.ui.kitchat.rememberKitRedactedCopyAction
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitCopied
import io.github.eslamasabry.oc.ui.words.res.kitReportProblem
import io.github.eslamasabry.oc.ui.words.res.appDiagnosticsClearBody
import io.github.eslamasabry.oc.ui.words.res.appDiagnosticsClearConfirm
import io.github.eslamasabry.oc.ui.words.res.appDiagnosticsClearTitle
import io.github.eslamasabry.oc.ui.words.res.perfTraceClearTimings
import io.github.eslamasabry.oc.ui.words.res.perfTraceCopied
import io.github.eslamasabry.oc.ui.words.res.perfTraceCopy
import io.github.eslamasabry.oc.ui.words.res.perfTraceTitle
import io.github.eslamasabry.oc.ui.words.res.reportProblemClearFailed
import io.github.eslamasabry.oc.ui.words.res.reportKeptClearErrors
import io.github.eslamasabry.oc.ui.words.res.reportKeptCopyErrors
import io.github.eslamasabry.oc.ui.words.res.reportProblemErrorsLabel
import io.github.eslamasabry.oc.ui.words.res.reportCodeCopy
import io.github.eslamasabry.oc.ui.words.res.reportCodeLabel
import io.github.eslamasabry.oc.ui.words.res.reportProblemDescribeHint
import io.github.eslamasabry.oc.ui.words.res.reportProblemDescribeLabel
import io.github.eslamasabry.oc.ui.words.res.reportR1AddressOut
import io.github.eslamasabry.oc.ui.words.res.reportR1Android
import io.github.eslamasabry.oc.ui.words.res.reportR1App
import io.github.eslamasabry.oc.ui.words.res.reportR1AppValue
import io.github.eslamasabry.oc.ui.words.res.reportR1ClosedForMemory
import io.github.eslamasabry.oc.ui.words.res.reportR1Connection
import io.github.eslamasabry.oc.ui.words.res.reportR1CopyText
import io.github.eslamasabry.oc.ui.words.res.reportR1Crashed
import io.github.eslamasabry.oc.ui.words.res.reportR1Froze
import io.github.eslamasabry.oc.ui.words.res.reportR1Included
import io.github.eslamasabry.oc.ui.words.res.reportR1IncludedClose
import io.github.eslamasabry.oc.ui.words.res.reportR1IncludedCrash
import io.github.eslamasabry.oc.ui.words.res.reportR1IncludedFreeze
import io.github.eslamasabry.oc.ui.words.res.reportR1IncludedLine
import io.github.eslamasabry.oc.ui.words.res.reportR1IncludedOpen
import io.github.eslamasabry.oc.ui.words.res.reportR1KindAuth
import io.github.eslamasabry.oc.ui.words.res.reportR1KindDns
import io.github.eslamasabry.oc.ui.words.res.reportR1KindOffline
import io.github.eslamasabry.oc.ui.words.res.reportR1KindOther
import io.github.eslamasabry.oc.ui.words.res.reportR1KindProtocol
import io.github.eslamasabry.oc.ui.words.res.reportR1KindRefused
import io.github.eslamasabry.oc.ui.words.res.reportR1KindRejected
import io.github.eslamasabry.oc.ui.words.res.reportR1KindServer
import io.github.eslamasabry.oc.ui.words.res.reportR1KindTimeout
import io.github.eslamasabry.oc.ui.words.res.reportR1KindTls
import io.github.eslamasabry.oc.ui.words.res.reportR1KindTooLarge
import io.github.eslamasabry.oc.ui.words.res.reportR1LastClose
import io.github.eslamasabry.oc.ui.words.res.reportR1Memory
import io.github.eslamasabry.oc.ui.words.res.reportR1NeverSent
import io.github.eslamasabry.oc.ui.words.res.reportR1NoProblems
import io.github.eslamasabry.oc.ui.words.res.reportR1NoSend
import io.github.eslamasabry.oc.ui.words.res.reportR1NoWords
import io.github.eslamasabry.oc.ui.words.res.reportR1NotConnected
import io.github.eslamasabry.oc.ui.words.res.reportR1Optional
import io.github.eslamasabry.oc.ui.words.res.reportR1Phone
import io.github.eslamasabry.oc.ui.words.res.reportR1PostGitHub
import io.github.eslamasabry.oc.ui.words.res.reportR1ProblemCount
import io.github.eslamasabry.oc.ui.words.res.reportR1Problems
import io.github.eslamasabry.oc.ui.words.res.reportR1PublicBody
import io.github.eslamasabry.oc.ui.words.res.reportR1PublicConfirm
import io.github.eslamasabry.oc.ui.words.res.reportR1PublicTitle
import io.github.eslamasabry.oc.ui.words.res.reportR1Send
import io.github.eslamasabry.oc.ui.words.res.reportR1SentBody
import io.github.eslamasabry.oc.ui.words.res.reportR1SheetSubtitle
import io.github.eslamasabry.oc.ui.words.res.reportR1Words
import io.github.eslamasabry.oc.ui.words.res.sendReportDone
import io.github.eslamasabry.oc.ui.words.res.sendReportFailed
import io.github.eslamasabry.oc.ui.words.res.sendReportSentTitle
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Settings › Help › Report a problem (R1, the owner's pick of 2026-11-09). One question with its field, one line that
 * says what goes with it ("What's included ›" opens the full list in a sheet), one primary Send report, and a quiet
 * line under it: Copy text · Post publicly on GitHub. In a build that cannot send, Copy text is the primary and one
 * plain line says why. After a send the page says so and shows the report code with its copy, then Done.
 *
 * Nothing is sent by opening the page, typing or leaving: only Send report sends, and only the text the sheet lists.
 * Copy text copies that same text through the redacting copy; GitHub asks first, because an issue is public.
 */
@Composable
internal fun ReportProblemPage(
    state: SettingsUi,
    actions: SettingsActions,
    back: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val report = state.report ?: ReportUi()
    val sent = report.stage as? ReportStage.Sent
    val copy =
        rememberKitRedactedCopyAction(
            label = stringResource(Res.string.reportR1CopyText),
            copiedLabel = stringResource(Res.string.kitCopied),
            text = { report.content?.text.orEmpty() },
        )
    SettingsFrame(
        stringResource(Res.string.kitReportProblem),
        back,
        state,
        actions,
        modifier,
        footer = { ReportFooter(report, copy, actions, back) },
    ) {
        KitSwap(sent?.code, alignment = Alignment.TopStart) { code ->
            Column(verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                if (code == null) ReportForm(state.reportDescription, report, actions) else ReportSent(code)
            }
        }
        IncludedSheet(report, actions)
        KitConfirm(
            spec =
                if (report.confirmClearErrors)
                    KitConfirmSpec(
                        title =
                            pluralStringResource(
                                Res.plurals.appDiagnosticsClearTitle,
                                report.errorsKept,
                                report.errorsKept,
                            ),
                        confirmLabel =
                            pluralStringResource(
                                Res.plurals.appDiagnosticsClearConfirm,
                                report.errorsKept,
                                report.errorsKept,
                            ),
                        kind = KitConfirmKind.Neutral,
                        body =
                            pluralStringResource(
                                Res.plurals.appDiagnosticsClearBody,
                                report.errorsKept,
                                report.errorsKept,
                            ),
                    )
                else null,
            onConfirm = { actions.onConfirmClearErrors(true) },
            onCancel = { actions.onConfirmClearErrors(false) },
        )
        KitConfirm(
            spec =
                if (report.confirmPublic)
                    KitConfirmSpec(
                        title = stringResource(Res.string.reportR1PublicTitle),
                        confirmLabel = stringResource(Res.string.reportR1PublicConfirm),
                        kind = KitConfirmKind.Neutral,
                        body = stringResource(Res.string.reportR1PublicBody),
                    )
                else null,
            onConfirm = { actions.onPostPublicly(true) },
            onCancel = { actions.onPostPublicly(false) },
        )
    }
}

/** What happened (optional), then the one line that says what goes with it. */
@Composable
private fun ColumnScope.ReportForm(description: String, report: ReportUi, actions: SettingsActions) {
    val gutter = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)
    val field = rememberTextFieldState(description)
    val label = stringResource(Res.string.reportProblemDescribeLabel)
    Row(gutter.fillMaxWidth().padding(top = Space.S2.dp), verticalAlignment = Alignment.Bottom) {
        KitText(
            label,
            Modifier.weight(1f).semantics { heading() },
            role = TypeRole.RowTitle,
            kind = KitTextKind.Frame,
        )
        KitText(
            stringResource(Res.string.reportR1Optional),
            role = TypeRole.Secondary,
            tone = TextTone.Tertiary,
            kind = KitTextKind.Frame,
        )
    }
    KitField(
        state = field,
        label = label,
        placeholder = stringResource(Res.string.reportProblemDescribeHint),
        kind = KitFieldKind.Multiline,
        multilineLines = 6..12,
        labelShown = false,
        modifier = gutter.testTag("report-description"),
        onTextChanged = actions::onReportDescription,
    )
    Spacer(Modifier.height(Space.S3.dp))
    KitRow(
        title = stringResource(Res.string.reportR1Included),
        supporting = includedLine(report.content?.lastClose, report.content?.madeMs ?: 0),
        supportingMaxLines = 4,
        trailing = KitRowTrailing.Chevron,
        onClickLabel = stringResource(Res.string.reportR1IncludedOpen),
        modifier = Modifier.testTag("report-included"),
        onClick = { actions.onShowIncluded(true) },
    )
    KeptOnPhone(report, actions)
}

/**
 * What this phone kept, with the two local things a person can do about it: copy the error log or the timing report
 * (both redacted, nothing is sent) and clear them. Shown only when there is something; it replaces the retired
 * "Everything this phone kept" page's errors and performance lines, inside this page.
 */
@Composable
private fun KeptOnPhone(report: ReportUi, actions: SettingsActions) {
    val copyErrors =
        rememberKitRedactedCopyAction(
            label = stringResource(Res.string.reportKeptCopyErrors),
            copiedLabel = stringResource(Res.string.kitCopied),
            text = { report.errorLog },
        )
    val copyTimings =
        rememberKitRedactedCopyAction(
            label = stringResource(Res.string.perfTraceCopy),
            copiedLabel = stringResource(Res.string.perfTraceCopied),
            text = { report.timingReport },
        )
    if (report.errorsKept > 0) {
        KitSectionLabel(
            pluralStringResource(Res.plurals.reportProblemErrorsLabel, report.errorsKept, report.errorsKept)
        )
        KitRow(
            title = copyErrors.label,
            leading = KitRowLeading.Glyph(KitIcons.copy),
            modifier = Modifier.testTag("report-copy-errors"),
            onClick = copyErrors.onClick,
        )
        KitRow(
            title = stringResource(Res.string.reportKeptClearErrors),
            leading = KitRowLeading.Glyph(KitIcons.delete),
            modifier = Modifier.testTag("report-clear-errors"),
            onClick = { actions.onClearErrors(true) },
        )
        if (report.clearFailed) {
            KitText(
                stringResource(Res.string.reportProblemClearFailed),
                Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp).testTag("report-clear-failed"),
                role = TypeRole.Secondary,
                tone = TextTone.Attention,
                kind = KitTextKind.Frame,
            )
        }
    }
    if (report.timingReport.isNotBlank()) {
        KitSectionLabel(stringResource(Res.string.perfTraceTitle))
        KitRow(
            title = copyTimings.label,
            leading = KitRowLeading.Glyph(KitIcons.copy),
            modifier = Modifier.testTag("report-copy-timings"),
            onClick = copyTimings.onClick,
        )
        KitRow(
            title = stringResource(Res.string.perfTraceClearTimings),
            leading = KitRowLeading.Glyph(KitIcons.delete),
            modifier = Modifier.testTag("report-clear-timings"),
            onClick = actions::onClearTimings,
        )
    }
}

/** "App and phone details[, and the crash at 23:12]. Passwords, keys and server addresses are taken out." */
@Composable
private fun includedLine(close: ReportClose?, nowMs: Long): String {
    close ?: return stringResource(Res.string.reportR1IncludedLine)
    val at = kitLocalTime(close.atMs, nowMs).orEmpty()
    return stringResource(
        when (close.kind) {
            "crash", "crash_native" -> Res.string.reportR1IncludedCrash
            "anr" -> Res.string.reportR1IncludedFreeze
            else -> Res.string.reportR1IncludedClose
        },
        at,
    )
}

/** Report sent, the code with its copy; Done is in the footer. */
@Composable
private fun ColumnScope.ReportSent(code: String) {
    val gutter = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)
    Column(gutter.fillMaxWidth().padding(top = Space.S6.dp).testTag("report-sent")) {
        KitIcon(KitIcons.check, size = KitIconSize.Fab, tone = TextTone.Success)
        Spacer(Modifier.height(Space.S5.dp))
        KitText(
            stringResource(Res.string.sendReportSentTitle),
            Modifier.semantics { heading() },
            role = TypeRole.Title,
            kind = KitTextKind.Frame,
        )
        Spacer(Modifier.height(Space.S2.dp))
        KitText(stringResource(Res.string.reportR1SentBody), role = TypeRole.Body, tone = TextTone.Secondary)
        Spacer(Modifier.height(Space.S6.dp))
        KitText(
            stringResource(Res.string.reportCodeLabel),
            role = TypeRole.Secondary,
            tone = TextTone.Secondary,
            kind = KitTextKind.Frame,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            KitText(
                code,
                Modifier.weight(1f).testTag("report-code"),
                role = TypeRole.LargeTitle,
                kind = KitTextKind.Frame,
                tabular = true,
            )
            KitRedactedCopyIconButton(text = { code }, label = stringResource(Res.string.reportCodeCopy))
        }
        Spacer(Modifier.height(Space.S3.dp))
        KitDivider()
    }
}

/**
 * The foot of the page: one primary (Send report, or Copy text where this build cannot send) and the quiet line under
 * it. A failed send says so in one plain line above the button, and Send report tries again.
 */
@Composable
private fun ReportFooter(report: ReportUi, copy: KitAction, actions: SettingsActions, done: () -> Unit) {
    val tokens = LocalKitTokens.current
    Column(
        Modifier.fillMaxWidth().padding(start = tokens.gutter.dp, end = tokens.gutter.dp, bottom = Space.S3.dp),
        verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
    ) {
        val github =
            KitAction(stringResource(Res.string.reportR1PostGitHub), actions::onAskPostPublicly, icon = KitIcons.github)
        when {
            report.stage is ReportStage.Sent ->
                KitActionBlock(primary = KitAction(stringResource(Res.string.sendReportDone), done))
            report.canSend -> {
                if (report.stage == ReportStage.Failed) {
                    KitText(
                        stringResource(Res.string.sendReportFailed),
                        Modifier.testTag("report-send-failed"),
                        role = TypeRole.Secondary,
                        tone = TextTone.Attention,
                        kind = KitTextKind.Frame,
                    )
                }
                KitActionBlock(
                    primary =
                        KitAction(
                            stringResource(Res.string.reportR1Send),
                            actions::onSendProblemReport,
                            working = report.stage == ReportStage.Sending,
                        ),
                    tertiary = listOf(copy, github),
                )
            }
            else -> {
                KitText(
                    stringResource(Res.string.reportR1NoSend),
                    Modifier.testTag("report-no-send"),
                    role = TypeRole.Secondary,
                    tone = TextTone.Secondary,
                    kind = KitTextKind.Frame,
                )
                KitActionBlock(primary = copy.copy(icon = null), tertiary = listOf(github))
            }
        }
    }
}

/** "What's included": every field the report sends, in the person's language, then what is never sent. */
@Composable
private fun IncludedSheet(report: ReportUi, actions: SettingsActions) {
    val content = report.content ?: ProblemReportContent()
    KitSheet(
        visible = report.showIncluded,
        onDismissRequest = { actions.onShowIncluded(false) },
        title = stringResource(Res.string.reportR1Included),
        subtitle = stringResource(Res.string.reportR1SheetSubtitle),
    ) {
        Column(Modifier.fillMaxWidth().testTag("report-included-sheet")) {
            Fact(
                Res.string.reportR1Words,
                content.words.ifBlank { stringResource(Res.string.reportR1NoWords) },
                tone = if (content.words.isBlank()) TextTone.Tertiary else TextTone.Primary,
            )
            content.lastClose?.let { close ->
                KitDivider()
                val at = kitLocalTime(close.atMs, content.madeMs).orEmpty()
                Fact(
                    Res.string.reportR1LastClose,
                    stringResource(
                        when (close.kind) {
                            "crash", "crash_native" -> Res.string.reportR1Crashed
                            "anr" -> Res.string.reportR1Froze
                            else -> Res.string.reportR1ClosedForMemory
                        },
                        at,
                    ),
                    tone = TextTone.Attention,
                )
            }
            KitDivider()
            Fact(Res.string.reportR1App, stringResource(Res.string.reportR1AppValue, content.appVersion, content.appBuild))
            KitDivider()
            Fact(
                Res.string.reportR1Phone,
                listOfNotNull(
                        stringResource(Res.string.reportR1Android, content.android),
                        content.abi,
                        content.ramRange?.let { stringResource(Res.string.reportR1Memory, it) },
                    )
                    .joinToString(" · "),
            )
            KitDivider()
            Fact(
                Res.string.reportR1Connection,
                content.connection?.let {
                    stringResource(Res.string.reportR1AddressOut, listOfNotNull(it.kind, it.version).joinToString(" "))
                } ?: stringResource(Res.string.reportR1NotConnected),
            )
            KitDivider()
            Fact(
                Res.string.reportR1Problems,
                content.problems
                    .map { stringResource(Res.string.reportR1ProblemCount, problemWords(it.kind), it.count.toString()) }
                    .joinToString(" · ")
                    .ifEmpty { stringResource(Res.string.reportR1NoProblems) },
            )
            KitDivider()
            Spacer(Modifier.height(Space.S3.dp))
            KitText(
                stringResource(Res.string.reportR1NeverSent),
                Modifier.testTag("report-never-sent"),
                role = TypeRole.Secondary,
                tone = TextTone.Secondary,
            )
            Spacer(Modifier.height(Space.S3.dp))
        }
    }
}

/** One fact of the report: its name over its value. */
@Composable
private fun Fact(label: StringResource, value: String, tone: TextTone = TextTone.Primary) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = Space.S3.dp).semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(Space.S1.dp),
    ) {
        KitText(stringResource(label), role = TypeRole.Secondary, tone = TextTone.Secondary, kind = KitTextKind.Frame)
        KitText(value, role = TypeRole.Body, tone = tone)
    }
}

@Composable
private fun problemWords(kind: String): String =
    stringResource(
        when (kind) {
            "timeout" -> Res.string.reportR1KindTimeout
            "auth" -> Res.string.reportR1KindAuth
            "tls" -> Res.string.reportR1KindTls
            "server_5xx" -> Res.string.reportR1KindServer
            "protocol" -> Res.string.reportR1KindProtocol
            "dns" -> Res.string.reportR1KindDns
            "refused" -> Res.string.reportR1KindRefused
            "offline" -> Res.string.reportR1KindOffline
            "too_large" -> Res.string.reportR1KindTooLarge
            "rejected" -> Res.string.reportR1KindRejected
            else -> Res.string.reportR1KindOther
        }
    )
