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
import org.jetbrains.compose.resources.stringResource

/** The country whose flag stands for a language, and its two letters (the owner can change either). */
private data class LanguageFacts(val country: String, val letters: String)

private val FACTS: Map<AppLanguage, LanguageFacts> =
    mapOf(
        AppLanguage.English to LanguageFacts("US", "Aa"),
        AppLanguage.Arabic to LanguageFacts("EG", "أب"),
        AppLanguage.Spanish to LanguageFacts("ES", "Es"),
        AppLanguage.Japanese to LanguageFacts("JP", "あ"),
        AppLanguage.Portuguese to LanguageFacts("BR", "Pt"),
        AppLanguage.Russian to LanguageFacts("RU", "Я"),
        AppLanguage.Vietnamese to LanguageFacts("VN", "Vi"),
        AppLanguage.Indonesian to LanguageFacts("ID", "Id"),
        AppLanguage.French to LanguageFacts("FR", "Fr"),
        AppLanguage.Chinese to LanguageFacts("CN", "文"),
        AppLanguage.Italian to LanguageFacts("IT", "It"),
    )

/** The language the app resolves "System default" to right now: the one it is drawn in while the choice is the system's. */
private fun resolvedSystem(current: AppLanguage, uiTag: String): AppLanguage? =
    if (current != AppLanguage.System) null
    else AppLanguage.entries.firstOrNull { it.tag != null && it.tag == uiTag.substringBefore('-').lowercase() }

/**
 * The rows of the Language list: "System default" first, then the chosen language and the phone's own, then the rest
 * by their own names. Each row: a round mark, the language in its own name, under it its name in the app's language
 * (and its share where it is only partly translated), the chosen one in the kit's chosen state.
 */
@Composable
internal fun languageChoices(current: AppLanguage): List<KitChoice<AppLanguage>> {
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
        val named = (if (facts != null) item else system)?.let { nameInCurrent(it) }?.takeIf { it != own }
        val share = partly.getValue(item)?.let { stringResource(Res.string.languagePickerPartlyTranslated, it) }
        KitChoice(
            item,
            own,
            supporting = listOfNotNull(named, share).joinToString(" · ").ifEmpty { null },
            leading = { KitLanguageMark(markOf(item, facts), selected = item == current) },
        )
    }
}

/** The language's name in the language the app is drawn in; System has none (its row names the phone's own language). */
@Composable
private fun nameInCurrent(language: AppLanguage): String? =
    when (language) {
        AppLanguage.System -> null
        AppLanguage.English -> stringResource(Res.string.languageNameEnglish)
        AppLanguage.Arabic -> stringResource(Res.string.languageNameArabic)
        AppLanguage.Spanish -> stringResource(Res.string.languageNameSpanish)
        AppLanguage.Japanese -> stringResource(Res.string.languageNameJapanese)
        AppLanguage.Portuguese -> stringResource(Res.string.languageNamePortuguese)
        AppLanguage.Russian -> stringResource(Res.string.languageNameRussian)
        AppLanguage.Vietnamese -> stringResource(Res.string.languageNameVietnamese)
        AppLanguage.Indonesian -> stringResource(Res.string.languageNameIndonesian)
        AppLanguage.French -> stringResource(Res.string.languageNameFrench)
        AppLanguage.Chinese -> stringResource(Res.string.languageNameChinese)
        AppLanguage.Italian -> stringResource(Res.string.languageNameItalian)
    }

private fun markOf(item: AppLanguage, facts: LanguageFacts?): KitLanguageMark =
    when {
        item == AppLanguage.System || facts == null -> KitLanguageMark.Glyph(KitIcons.phone)
        else -> KitLanguageMark.Flag(facts.country, facts.letters)
    }

private const val FULL = 100
