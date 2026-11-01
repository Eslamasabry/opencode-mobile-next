package io.github.eslamasabry.oc.tools.strings

/**
 * CLDR plural rules for non-negative integers (CMP `pluralStringResource` takes an `Int`), for the shipped
 * languages. Checked rule by rule against CMP 1.12.2 `CLDRPluralRuleLists.kt` (en, ar, es, pt, ru, ja, zh), the
 * table `pluralStringResource` uses at runtime.
 */
object PluralRules {
    const val ZERO = "zero"; const val ONE = "one"; const val TWO = "two"
    const val FEW = "few"; const val MANY = "many"; const val OTHER = "other"
    val ALL = listOf(ZERO, ONE, TWO, FEW, MANY, OTHER)

    private fun lang(locale: String) = locale.substringBefore('_').substringBefore('-').lowercase()

    fun isKnown(locale: String) = lang(locale) in setOf("en", "ar", "es", "pt", "ru", "ja", "zh")

    /** The categories an integer can take in [locale], in CLDR order. */
    fun categories(locale: String): List<String> = when (lang(locale)) {
        "en" -> listOf(ONE, OTHER)
        "ar" -> ALL
        "es", "pt" -> listOf(ONE, MANY, OTHER)
        "ru" -> listOf(ONE, FEW, MANY, OTHER) // `other` is only reachable with fractions; kept for safety.
        "ja", "zh" -> listOf(OTHER)
        else -> throw IllegalArgumentException("No plural rules for locale \"$locale\"; add them to PluralRules")
    }

    fun category(locale: String, n: Int): String {
        require(n >= 0) { "negative count $n" }
        return when (lang(locale)) {
            "en" -> if (n == 1) ONE else OTHER
            "ar" -> {
                val m = n % 100
                when {
                    n == 0 -> ZERO
                    n == 1 -> ONE
                    n == 2 -> TWO
                    m in 3..10 -> FEW
                    m in 11..99 -> MANY
                    else -> OTHER
                }
            }
            "es" -> when { n == 1 -> ONE; n != 0 && n % 1_000_000 == 0 -> MANY; else -> OTHER }
            "pt" -> when { n == 0 || n == 1 -> ONE; n % 1_000_000 == 0 -> MANY; else -> OTHER }
            "ru" -> {
                val m10 = n % 10; val m100 = n % 100
                when {
                    m10 == 1 && m100 != 11 -> ONE
                    m10 in 2..4 && m100 !in 12..14 -> FEW
                    else -> MANY
                }
            }
            "ja", "zh" -> OTHER
            else -> throw IllegalArgumentException("No plural rules for locale \"$locale\"")
        }
    }

    /**
     * The single integer that [category] matches in [locale], or null when it matches several. An ICU `=N` branch
     * can stand in for a missing category only when the category is exactly {N} (en/es `one` = {1}; ar `zero`,
     * `one`, `two` = {0}, {1}, {2}; pt `one` is {0, 1} and ru `one` is {1, 21, …}, so not there).
     */
    fun soleMember(locale: String, category: String): Int? {
        val hits = (0..1_000).filter { category(locale, it) == category }
        return hits.singleOrNull()
    }
}
