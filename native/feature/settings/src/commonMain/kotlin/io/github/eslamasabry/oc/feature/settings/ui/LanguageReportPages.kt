package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.kit.row.kitRowGutter
import io.github.eslamasabry.oc.ui.words.res.e7LocaleUiDescription
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.settings.presentation.AppLanguage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsActions
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.choice.KitChoice
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceList
import io.github.eslamasabry.oc.ui.words.LanguageCoverage
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.e7LocaleUiArabic
import io.github.eslamasabry.oc.ui.words.res.e7LocaleUiChinese
import io.github.eslamasabry.oc.ui.words.res.e7LocaleUiEnglish
import io.github.eslamasabry.oc.ui.words.res.e7LocaleUiJapanese
import io.github.eslamasabry.oc.ui.words.res.e7LocaleUiLanguage
import io.github.eslamasabry.oc.ui.words.res.e7LocaleUiPortuguese
import io.github.eslamasabry.oc.ui.words.res.e7LocaleUiRussian
import io.github.eslamasabry.oc.ui.words.res.e7LocaleUiSpanish
import io.github.eslamasabry.oc.ui.words.res.e7LocaleUiSystem
import io.github.eslamasabry.oc.ui.words.res.languagePickerPartlyTranslated
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
            AppLanguage.entries.filter { it.tag == null || it.tag in LanguageCoverage.offered }.map { item ->
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
