package io.github.eslamasabry.oc.feature.settings.presentation

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * One searchable row: its title and its acceptance words, already in the person's language (the screen resolves them
 * from `:ui:words`; this layer holds no copy). Aliases may mix languages (the freeze tip's bilingual alias lines).
 */
public data class SettingsSearchEntry(val row: SettingsRowId, val title: String, val aliases: String = "")

/**
 * Settings search (B-NAV-071, B-NAV-072). Literal matches (every query word is a prefix of, or inside, a word of the
 * title or aliases) rank above fuzzy ones (one edit, a swap counts as one, for words of four letters or more); ties
 * keep input order. Only entries for rows that exist are passed in, so rows the phone lacks never appear.
 *
 * A port of the rules the freeze tip's `SearchMatcher` tests pin; the shared `SearchMatcher` (plan 07 §10.4, S5-SEARCH)
 * replaces it when it lands.
 */
public object SettingsSearch {
    private const val FUZZY_FROM = 4

    public fun find(query: String, entries: List<SettingsSearchEntry>): ImmutableList<SettingsRowId> {
        val words = tokens(query)
        if (words.isEmpty()) return emptyList<SettingsRowId>().toImmutableList()
        val literal = mutableListOf<SettingsRowId>()
        val fuzzy = mutableListOf<SettingsRowId>()
        for (entry in entries) {
            val text = (entry.title + " " + entry.aliases).lowercase()
            val hay = tokens(text)
            when {
                words.all { w -> text.contains(w) || hay.any { it.startsWith(w) } } -> literal += entry.row
                words.all { w -> hay.any { it.startsWith(w) } || (w.length >= FUZZY_FROM && hay.any { near(w, it) }) } ->
                    fuzzy += entry.row
            }
        }
        return (literal + fuzzy).distinct().toImmutableList()
    }

    private fun tokens(text: String): List<String> =
        text.lowercase().split(Regex("[\\s,·/›\\-]+")).filter { it.isNotBlank() }

    /** One edit apart, or one swap of neighbours (optimal string alignment ≤ 1); or one edit from a prefix of [word]. */
    private fun near(query: String, word: String): Boolean {
        if (osa(query, word) <= 1) return true
        if (word.length > query.length) return osa(query, word.take(query.length)) <= 1
        return false
    }

    private fun osa(a: String, b: String): Int {
        if (kotlin.math.abs(a.length - b.length) > 1) return 2
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
            }
        }
        return d[a.length][b.length]
    }
}
