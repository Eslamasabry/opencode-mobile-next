package io.github.eslamasabry.oc.feature.settings.presentation

/**
 * How much of the app reads in each language, in whole per cent (Flutter `languageTranslatedPercent`, B-L10N-017):
 * the language's `:ui:words` entries over the English ones (the plural-select helper keys `name__form` aside, which
 * only English carries). English is the catalogue itself and is not listed; the phone's language is not a language.
 * `LanguageCoverageTest` recomputes these from the word files and fails when one drifts by more than 3 points:
 * update the number it prints then.
 */
public object LanguageCoverage {
    public val percent: Map<AppLanguage, Int> =
        mapOf(
            AppLanguage.Arabic to 100,
            AppLanguage.Spanish to 26,
            AppLanguage.Japanese to 6,
            AppLanguage.Portuguese to 6,
            AppLanguage.Russian to 6,
            AppLanguage.Chinese to 83,
        )

    /** The share a partly translated language shows ("Partly translated (N %)"); null when complete or English. */
    public fun partly(language: AppLanguage): Int? = percent[language]?.takeIf { it < 100 }
}
