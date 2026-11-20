package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.eslamasabry.oc.feature.settings.presentation.AppLanguage
import io.github.eslamasabry.oc.ui.kit.choice.KitChoice
import io.github.eslamasabry.oc.ui.kit.choice.KitLanguageMark
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitLocaleTag
import io.github.eslamasabry.oc.ui.words.LanguageCoverage
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.languageNameArabic
import io.github.eslamasabry.oc.ui.words.res.languageNameChinese
import io.github.eslamasabry.oc.ui.words.res.languageNameEnglish
import io.github.eslamasabry.oc.ui.words.res.languageNameFrench
import io.github.eslamasabry.oc.ui.words.res.languageNameIndonesian
import io.github.eslamasabry.oc.ui.words.res.languageNameItalian
import io.github.eslamasabry.oc.ui.words.res.languageNameJapanese
import io.github.eslamasabry.oc.ui.words.res.languageNamePortuguese
import io.github.eslamasabry.oc.ui.words.res.languageNameRussian
import io.github.eslamasabry.oc.ui.words.res.languageNameSpanish
import io.github.eslamasabry.oc.ui.words.res.languageNameVietnamese
import io.github.eslamasabry.oc.ui.words.res.languagePickerPartlyTranslated
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The country whose flag stands for a language, and its two letters (the owner can change either). */
private data class LanguageFacts(val country: String, val letters: String, val inCurrent: StringResource)

private val FACTS: Map<AppLanguage, LanguageFacts> =
    mapOf(
        AppLanguage.English to LanguageFacts("US", "Aa", Res.string.languageNameEnglish),
        AppLanguage.Arabic to LanguageFacts("EG", "أب", Res.string.languageNameArabic),
        AppLanguage.Spanish to LanguageFacts("ES", "Es", Res.string.languageNameSpanish),
        AppLanguage.Japanese to LanguageFacts("JP", "あ", Res.string.languageNameJapanese),
        AppLanguage.Portuguese to LanguageFacts("BR", "Pt", Res.string.languageNamePortuguese),
        AppLanguage.Russian to LanguageFacts("RU", "Я", Res.string.languageNameRussian),
        AppLanguage.Vietnamese to LanguageFacts("VN", "Vi", Res.string.languageNameVietnamese),
        AppLanguage.Indonesian to LanguageFacts("ID", "Id", Res.string.languageNameIndonesian),
        AppLanguage.French to LanguageFacts("FR", "Fr", Res.string.languageNameFrench),
        AppLanguage.Chinese to LanguageFacts("CN", "文", Res.string.languageNameChinese),
        AppLanguage.Italian to LanguageFacts("IT", "It", Res.string.languageNameItalian),
    )

/** The language the app resolves "System default" to right now: the one it is drawn in while the choice is System. */
private fun resolvedSystem(current: AppLanguage, uiTag: String): AppLanguage? =
    if (current != AppLanguage.System) null
    else AppLanguage.entries.firstOrNull { it.tag != null && it.tag == uiTag.substringBefore('-').lowercase() }

/**
 * The rows of the Language list: "System default" first, then the chosen language and the phone's own, then the rest
 * by their own names. Each row: a round mark, the language in its own name, under it its name in the app's language
 * (and its share where it is only partly translated), the chosen one in the kit's chosen state.
 */
@Composable
internal fun languageChoices(current: AppLanguage, marks: LanguageMarkStyle): List<KitChoice<AppLanguage>> {
    val uiTag = LocalKitLocaleTag.current
    val system = resolvedSystem(current, uiTag)
    val offered = AppLanguage.entries.filter { it.tag == null || it.tag in LanguageCoverage.offered }
    val ownNames = offered.associateWith { languageWords(it) }
    val order =
        remember(offered, current, system, ownNames) {
            val rest = offered.filter { it != AppLanguage.System }
            val first = listOfNotNull(current.takeIf { it != AppLanguage.System }, system).distinct()
            val others = rest.filter { it !in first }.sortedBy { ownNames.getValue(it).lowercase() }
            listOf(AppLanguage.System) + first + others
        }
    val partly = order.associateWith { item -> item.tag?.let { LanguageCoverage.percent[it] }?.takeIf { it < FULL } }
    return order.map { item ->
        val facts = FACTS[item]
        val own = ownNames.getValue(item)
        val named = (facts ?: FACTS[system])?.inCurrent?.let { stringResource(it) }?.takeIf { it != own }
        val share = partly.getValue(item)?.let { stringResource(Res.string.languagePickerPartlyTranslated, it) }
        KitChoice(
            item,
            own,
            supporting = listOfNotNull(named, share).joinToString(" · ").ifEmpty { null },
            leading = { KitLanguageMark(markOf(item, facts, marks), selected = item == current) },
        )
    }
}

private fun markOf(item: AppLanguage, facts: LanguageFacts?, style: LanguageMarkStyle): KitLanguageMark =
    when {
        item == AppLanguage.System || facts == null -> KitLanguageMark.Glyph(KitIcons.phone)
        style == LanguageMarkStyle.Flags -> KitLanguageMark.Flag(facts.country, facts.letters)
        else -> KitLanguageMark.Letters(facts.letters)
    }

private const val FULL = 100
