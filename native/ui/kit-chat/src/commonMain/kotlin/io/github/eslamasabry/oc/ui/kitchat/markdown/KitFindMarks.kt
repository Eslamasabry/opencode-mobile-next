package io.github.eslamasabry.oc.ui.kitchat.markdown

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle

/** The kit's find tint: every match in `accent` at 18 % (Dart `_paintMarks`, as the viewer's code body). */
public const val KIT_FIND_TINT_ALPHA: Float = 0.18f

/** The current match, stronger: `accent` at 38 %. */
public const val KIT_FIND_CURRENT_ALPHA: Float = 0.38f

/**
 * What Find is looking for, read by every text part under it (markdown prose, headings, lists, quotes, table cells,
 * code lines, the person's own bubble). A part marks each match of [query] in the text it draws and the [current]-th
 * match of its own text with the stronger tint; it never decides what matches beyond "the query, literally, ignoring
 * case, not overlapping" (the same rule the search itself uses over the loaded messages).
 *
 * [current] counts matches in the part's own text, from 0; -1 marks none strongly (the part is not the current hit's
 * row, or has more than one text and no single current). A screen provides one per row and nothing when Find is closed
 * (the default is no marks at all).
 */
@Immutable
public class KitFindMarks(public val query: String, public val current: Int = -1) {
    private val needle: String = query.trim()

    /** Whether there is anything to mark. */
    public val active: Boolean
        get() = needle.isNotEmpty()

    /** The matches in [text], in reading order: literal, case-insensitive, non-overlapping; end exclusive. */
    public fun ranges(text: CharSequence): List<IntRange> {
        if (needle.isEmpty() || text.length < needle.length) return emptyList()
        val s = text.toString()
        val out = ArrayList<IntRange>()
        var at = s.indexOf(needle, 0, ignoreCase = true)
        while (at >= 0) {
            out += at until at + needle.length
            at = s.indexOf(needle, at + needle.length, ignoreCase = true)
        }
        return out
    }

    override fun equals(other: Any?): Boolean =
        other is KitFindMarks && other.needle == needle && other.current == current

    override fun hashCode(): Int = needle.hashCode() * 31 + current
}

/** The find marks of the row being drawn; null when Find is closed. */
public val LocalKitFindMarks: ProvidableCompositionLocal<KitFindMarks?> = compositionLocalOf { null }

/**
 * [this] with the matches of [marks] painted: the tint behind each, the stronger tint behind the one whose number
 * (counted from [firstOrdinal], the matches that came before this text in the same part) is [KitFindMarks.current].
 * Returns [this] when there is nothing to mark. Matches are found in the displayed characters, so a table cell or a
 * bold word is marked as it reads, not as it was written.
 */
public fun AnnotatedString.withFindMarks(marks: KitFindMarks?, accent: Color, firstOrdinal: Int = 0): AnnotatedString {
    if (marks == null || !marks.active) return this
    val found = marks.ranges(text)
    if (found.isEmpty()) return this
    val b = AnnotatedString.Builder(this)
    found.forEachIndexed { i, r ->
        val strong = firstOrdinal + i == marks.current
        b.addStyle(
            SpanStyle(background = accent.copy(alpha = if (strong) KIT_FIND_CURRENT_ALPHA else KIT_FIND_TINT_ALPHA)),
            r.first,
            r.last + 1,
        )
    }
    return b.toAnnotatedString()
}
