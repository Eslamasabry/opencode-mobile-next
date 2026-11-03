package io.github.eslamasabry.oc.ui.words

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.intl.Locale
import io.github.eslamasabry.oc.core.common.text.NumberSymbols
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.e7ModelUiCost
import org.jetbrains.compose.resources.stringResource

/**
 * Prices and counts as the app language writes them (Flutter `lib/ui/locale_format.dart`, B-L10N-012). Where the
 * dollar sign goes follows the app language (Arabic, Spanish and Russian put it after the amount; English, Japanese
 * and Chinese before it). The digits and separators are the phone's own ([Numbers], owner decision 2026-11-03), so a
 * price reads with the same digits as every other number on screen.
 */
public object LocaleFormat {
    private const val RLM = '‏'
    private const val NBSP = ' '

    /** A US-dollar amount with [decimals] places for the app language [languageTag]: `$0.043`, `‏0.043 $`. */
    public fun usd(
        languageTag: String,
        value: Double,
        decimals: Int = 2,
        symbols: NumberSymbols = Numbers.symbols(),
    ): String {
        val amount = Numbers.decimal(value, decimals, symbols)
        return when (AppLanguages.languageOf(languageTag)) {
            "ar" -> "$RLM$amount$NBSP$"
            "es", "ru" -> "$amount$NBSP$"
            "pt" -> "$$NBSP$amount"
            else -> if (amount.startsWith(symbols.minus)) "${symbols.minus}$${amount.drop(1)}" else "$$amount"
        }
    }

    /** A whole count with the phone's grouping: `1,234,567`. */
    public fun count(value: Long, symbols: NumberSymbols = Numbers.symbols()): String = Numbers.format(value, symbols)

    /** `980`, `12.4k`, `1.2M`: a count in compact form. */
    public fun compactCount(value: Long, symbols: NumberSymbols = Numbers.symbols()): String = when {
        value >= 1_000_000 -> Numbers.decimal(value / 1_000_000.0, 1, symbols) + "M"
        value >= 1_000 -> Numbers.decimal(value / 1_000.0, 1, symbols) + "k"
        else -> Numbers.format(value, symbols)
    }

    /**
     * The two prices of a model's label (per million tokens in and out), each with two places from a dollar and three
     * below it; null when the model states no price (Flutter `modelCostLabel`). The label itself is [modelCostLabel].
     */
    public fun modelPrices(
        languageTag: String,
        inputPerMillion: Double,
        outputPerMillion: Double,
        symbols: NumberSymbols = Numbers.symbols(),
    ): Pair<String, String>? {
        if (inputPerMillion <= 0 && outputPerMillion <= 0) return null
        fun money(v: Double) = usd(languageTag, v, if (v >= 1) 2 else 3, symbols)
        return money(inputPerMillion) to money(outputPerMillion)
    }
}

/** "$3.00 in · $15.00 out /1M" in the app language, or null when the model states no price (B-L10N-012). */
@Suppress("DEPRECATION") // the app language is the process locale (AppLocale recreates on a change), as KitTheme reads it
@Composable
public fun modelCostLabel(inputPerMillion: Double, outputPerMillion: Double): String? {
    val (input, output) = LocaleFormat.modelPrices(Locale.current.toLanguageTag(), inputPerMillion, outputPerMillion)
        ?: return null
    return stringResource(Res.string.e7ModelUiCost, input, output)
}
