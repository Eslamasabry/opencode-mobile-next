package io.github.eslamasabry.oc.ui.words

/**
 * The languages the app ships words for (one `composeResources/values*` folder each), by the code a choice is saved
 * under (`oc.appLocale`), and how the phone's language list picks one (Flutter `AppLocaleStore`, `resolveAppLocales`).
 *
 * Brazilian Portuguese is `pt` and Simplified Chinese is `zh`: one catalogue per language, matched by language only,
 * so a Portuguese or a Traditional-Chinese reader gets that catalogue rather than English.
 */
public object AppLanguages {
    /** The language every message exists in, and the answer when the phone speaks nothing the app ships. */
    public const val ENGLISH: String = "en"

    /** The Language page's order after "Use system language": English first, then by code (B-L10N-008). */
    public val picker: List<String> = listOf("en", "ar", "es", "ja", "pt", "ru", "zh")

    /** Every shipped language; a saved choice outside it reads as "follow the phone". */
    public val shipped: Set<String> = picker.toSet()

    /** The language part of a BCP 47 or Java tag (`pt-BR`, `zh_Hant_TW`, `zh-Hans-CN`), lower case. */
    public fun languageOf(tag: String): String = tag.trim().substringBefore('-').substringBefore('_').lowercase()

    /** True when [code] is a language the app ships and so may be saved. */
    public fun isShipped(code: String?): Boolean = code != null && code in shipped

    /**
     * The app language for the phone's ordered language list [device] (B-L10N-009): the first entry whose language the
     * app ships, whatever its region or script; an entry the app does not ship is skipped for the next one; English
     * when none matches or the list is empty. Never Arabic by accident (Flutter's own rule would take the first
     * catalogue, which sorts first).
     */
    public fun resolve(device: List<String>): String =
        device.asSequence().map(::languageOf).firstOrNull { it in shipped } ?: ENGLISH
}
