package io.github.eslamasabry.oc.ui.kit.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors

/**
 * The words a search matched, shown in place (Search's results): each word of the query, wherever it
 * occurs, case ignored. The match is marked in the accent ink at semibold weight only: no fill behind it,
 * so a row stays calm and the words keep their place. Pure rules here; [kitHighlighted] dresses them.
 */
public object KitHighlight {
    private val WORDS = Regex("\\s+")

    /** Where each word of [query] occurs in [text], case ignored: sorted, overlaps merged. Blank query = none. */
    public fun ranges(text: String, query: String?): List<IntRange> {
        val words = query.orEmpty().trim().split(WORDS).filter { it.isNotEmpty() }.distinct()
        val found = mutableListOf<IntRange>()
        for (word in words) {
            var from = 0
            while (from <= text.length - word.length) {
                val at = text.indexOf(word, from, ignoreCase = true)
                if (at < 0) break
                found += at until at + word.length
                from = at + word.length
            }
        }
        val merged = mutableListOf<IntRange>()
        for (r in found.sortedBy { it.first }) {
            val last = merged.lastOrNull()
            if (last != null && r.first <= last.last + 1) merged[merged.lastIndex] = last.first..maxOf(last.last, r.last)
            else merged += r
        }
        return merged
    }
}

/** [text] with [query]'s words in the accent ink ([KitHighlight.ranges]); plain when nothing matches. */
@Composable
public fun kitHighlighted(text: String, query: String?, ink: Color = LocalKitColors.current.accent): AnnotatedString =
    remember(text, query, ink) {
        val ranges = KitHighlight.ranges(text, query)
        if (ranges.isEmpty()) AnnotatedString(text)
        else
            AnnotatedString.Builder(text).apply {
                val style = SpanStyle(color = ink, fontWeight = FontWeight.SemiBold)
                for (r in ranges) addStyle(style, r.first, r.last + 1)
            }.toAnnotatedString()
    }
