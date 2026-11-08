package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.settings.presentation.SendReportUi
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsActions
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.reportCodeCopy
import io.github.eslamasabry.oc.ui.words.res.reportCodeLabel
import io.github.eslamasabry.oc.ui.words.res.sendReportCollecting
import io.github.eslamasabry.oc.ui.words.res.sendReportDone
import io.github.eslamasabry.oc.ui.words.res.sendReportFailed
import io.github.eslamasabry.oc.ui.words.res.sendReportSend
import io.github.eslamasabry.oc.ui.words.res.sendReportSentBody
import io.github.eslamasabry.oc.ui.words.res.sendReportSentTitle
import io.github.eslamasabry.oc.ui.words.res.sendReportSubtitle
import org.jetbrains.compose.resources.stringResource

/**
 * Settings › Help › Send a problem report (B-190; Flutter 2.1.5 `send_problem_report.dart`). Asked every time, whatever
 * the two switches say: the page builds the report (the last log lines, the custom keys, the recent process ends, the
 * device context), redacts it and shows all of it. Only Send sends it, as one report to the developer; the page then
 * shows the report code to quote in a GitHub issue. Back sends nothing.
 */
@Composable
internal fun ColumnScope.SendProblemReportBody(report: SendReportUi?, actions: SettingsActions, onDone: () -> Unit) {
    val gutter = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)
    when (report) {
        null,
        SendReportUi.Collecting ->
            KitStateView(
                kind = KitStateKind.Loading,
                title = stringResource(Res.string.sendReportCollecting),
                modifier = Modifier.testTag("send-problem-report-collecting"),
            )
        is SendReportUi.Sent -> {
            KitNotice(
                stringResource(Res.string.sendReportSentBody),
                gutter.testTag("send-problem-report-sent"),
                title = stringResource(Res.string.sendReportSentTitle),
                tone = StatusTone.Ok,
            )
            KitRowGroup(leadingTiles = true) {
                KitRow(
                    title = stringResource(Res.string.reportCodeLabel),
                    leading = KitRowLeading.Glyph(KitIcons.bug),
                    supporting = report.code,
                    trailing = KitRowTrailing.Copy(report.code, stringResource(Res.string.reportCodeCopy)),
                    modifier = Modifier.testTag("send-problem-report-code"),
                )
            }
            Box(gutter) {
                KitActionBlock(primary = KitAction(stringResource(Res.string.sendReportDone), onDone))
            }
        }
        is SendReportUi.Preview,
        is SendReportUi.Sending,
        is SendReportUi.Failed -> {
            val text =
                when (report) {
                    is SendReportUi.Preview -> report.text
                    is SendReportUi.Sending -> report.text
                    is SendReportUi.Failed -> report.text
                }
            if (report is SendReportUi.Failed) {
                KitNotice(
                    stringResource(Res.string.sendReportFailed),
                    gutter.testTag("send-problem-report-failed"),
                    tone = StatusTone.Failure,
                    icon = KitIcons.warning,
                )
            }
            KitGroupNote(stringResource(Res.string.sendReportSubtitle))
            Box(gutter) {
                KitActionBlock(
                    primary =
                        KitAction(
                            stringResource(Res.string.sendReportSend),
                            actions::onSendProblemReport,
                            icon = KitIcons.upload,
                            working = report is SendReportUi.Sending,
                        )
                )
            }
            // Exactly the text that is sent.
            KitText(
                text,
                gutter.testTag("send-problem-report-text"),
                role = TypeRole.Mono,
                kind = KitTextKind.Reading,
            )
        }
    }
}
