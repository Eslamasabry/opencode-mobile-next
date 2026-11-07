package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.kit.row.kitRowGutter
import io.github.eslamasabry.oc.ui.words.res.e7LocaleUiDescription
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.settings.presentation.AppLanguage
import io.github.eslamasabry.oc.feature.settings.presentation.ReportUi
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsActions
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsOutside
import io.github.eslamasabry.oc.feature.settings.presentation.reportHeadline
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.choice.KitChoice
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceList
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.row.KitExpandRow
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.LanguageCoverage
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.commonRetry
import io.github.eslamasabry.oc.ui.words.res.diagnosticsReportShare
import io.github.eslamasabry.oc.ui.words.res.diagnosticsUnavailable
import io.github.eslamasabry.oc.ui.words.res.e7LocaleUiArabic
import io.github.eslamasabry.oc.ui.words.res.e7LocaleUiChinese
import io.github.eslamasabry.oc.ui.words.res.e7LocaleUiEnglish
import io.github.eslamasabry.oc.ui.words.res.e7LocaleUiJapanese
import io.github.eslamasabry.oc.ui.words.res.e7LocaleUiLanguage
import io.github.eslamasabry.oc.ui.words.res.e7LocaleUiPortuguese
import io.github.eslamasabry.oc.ui.words.res.e7LocaleUiRussian
import io.github.eslamasabry.oc.ui.words.res.e7LocaleUiSpanish
import io.github.eslamasabry.oc.ui.words.res.e7LocaleUiSystem
import io.github.eslamasabry.oc.ui.words.res.kitDetails
import io.github.eslamasabry.oc.ui.words.res.kitReportProblem
import io.github.eslamasabry.oc.ui.words.res.languagePickerPartlyTranslated
import io.github.eslamasabry.oc.ui.words.res.reportProblemCopyShort
import io.github.eslamasabry.oc.ui.words.res.reportProblemDescribeHint
import io.github.eslamasabry.oc.ui.words.res.reportProblemDescribeLabel
import io.github.eslamasabry.oc.ui.words.res.reportProblemKeptLink
import io.github.eslamasabry.oc.ui.words.res.reportProblemPrivacy
import io.github.eslamasabry.oc.ui.words.res.reportProblemSendGitHub
import io.github.eslamasabry.oc.ui.words.res.reportProblemSendHint
import org.jetbrains.compose.resources.stringResource

/**
 * Settings › This app › Language (Flutter `language_picker.dart`): one check-mark choice per
 * language, each in its own name so a person who cannot read the current one still finds theirs. A
 * tap applies at once; the page redraws in the chosen language and direction (B-L10N-001).
 */
@Composable
internal fun ColumnScope.LanguageBody(language: AppLanguage, actions: SettingsActions) {
    // Flutter's picker says what the choice changes before the list (lib/ui/widgets/language_picker.dart:158-159).
    KitText(
        stringResource(Res.string.e7LocaleUiDescription),
        Modifier.padding(horizontal = kitRowGutter()).testTag("language-description"),
        role = TypeRole.Secondary,
        tone = TextTone.Secondary,
    )
    Spacer(Modifier.height(Space.S3.dp)) // F: lib/ui/widgets/language_picker.dart:159
    KitChoiceList(
        choices =
            AppLanguage.entries.map { item ->
                val share = item.tag?.let { LanguageCoverage.percent[it] }
                KitChoice(
                    item,
                    languageWords(item),
                    supporting =
                        share
                            ?.takeIf { it < 100 }
                            ?.let { stringResource(Res.string.languagePickerPartlyTranslated, it) },
                )
            },
        selected = language,
        semanticsLabel = stringResource(Res.string.e7LocaleUiLanguage),
        modifier = Modifier.testTag("language-choices"),
        onSelect = actions::onLanguage,
    )
}

@Composable
internal fun languageWords(language: AppLanguage): String =
    stringResource(
        when (language) {
            AppLanguage.System -> Res.string.e7LocaleUiSystem
            AppLanguage.English -> Res.string.e7LocaleUiEnglish
            AppLanguage.Arabic -> Res.string.e7LocaleUiArabic
            AppLanguage.Spanish -> Res.string.e7LocaleUiSpanish
            AppLanguage.Japanese -> Res.string.e7LocaleUiJapanese
            AppLanguage.Portuguese -> Res.string.e7LocaleUiPortuguese
            AppLanguage.Russian -> Res.string.e7LocaleUiRussian
            AppLanguage.Chinese -> Res.string.e7LocaleUiChinese
        }
    )

/**
 * Settings › Help › Report a problem: what is taken out, the exact report as it will leave the
 * phone (B-SEC-060), then Copy and Share. Nothing is sent by opening the page. A report that cannot
 * be built says so plainly with Try again.
 */
@Composable
internal fun ColumnScope.ReportBody(
    report: ReportUi?,
    actions: SettingsActions,
    description: String = "",
) {
    val gutter = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)
    val field = rememberTextFieldState(description)
    KitField(
        state = field,
        label = stringResource(Res.string.reportProblemDescribeLabel),
        // P-022: the hint is a helper line that wraps; a one-line placeholder cut it with "…".
        helper = stringResource(Res.string.reportProblemDescribeHint),
        kind = KitFieldKind.Multiline,
        modifier = gutter.testTag("report-description"),
        onTextChanged = actions::onReportDescription,
    )
    when (report) {
        null,
        ReportUi.Loading ->
            KitStateView(
                kind = KitStateKind.Loading,
                title = stringResource(Res.string.kitReportProblem),
            )
        ReportUi.Failed ->
            KitStateView(
                kind = KitStateKind.Error,
                title = stringResource(Res.string.diagnosticsUnavailable),
                primary = KitAction(stringResource(Res.string.commonRetry), actions::onRetryReport),
            )
        is ReportUi.Ready -> {
            KitGroupNote(stringResource(Res.string.reportProblemPrivacy))
            Box(gutter) {
                KitActionBlock(
                    primary =
                        KitAction(
                            stringResource(Res.string.diagnosticsReportShare),
                            actions::onShareReport,
                        ),
                    secondary =
                        KitAction(
                            stringResource(Res.string.reportProblemCopyShort),
                            actions::onCopyReport,
                        ),
                )
            }
            KitRowGroup {
                KitRow(
                    title = stringResource(Res.string.reportProblemSendGitHub),
                    supporting = stringResource(Res.string.reportProblemSendHint),
                    supportingMaxLines = 2,
                    onClick = actions::onSendReport,
                )
                KitRow(
                    title = stringResource(Res.string.reportProblemKeptLink),
                    onClick = { actions.onOutside(SettingsOutside.KeptDiagnostics) },
                )
            }
            // P-022: the exact report stays one tap away in a Details fold that starts closed, last on the page;
            // its line is the report's headline, so the page still says what will be sent.
            var open by rememberSaveable { mutableStateOf(false) }
            KitExpandRow(
                title = stringResource(Res.string.kitDetails),
                expanded = open,
                supporting = reportHeadline(report.text),
                onExpandedChange = { open = it },
                modifier = Modifier.testTag("report-details"),
            ) {
                KitText(
                    report.text,
                    Modifier.testTag("report-text"),
                    role = TypeRole.Mono,
                    kind = KitTextKind.Reading,
                )
            }
        }
    }
}
