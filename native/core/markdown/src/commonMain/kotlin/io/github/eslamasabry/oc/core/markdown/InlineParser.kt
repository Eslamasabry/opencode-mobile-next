package io.github.eslamasabry.oc.core.markdown

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Inline Markdown: emphasis by CommonMark's delimiter-run rules (`*a*`, `_a_`, `**a**`, `__a__`, `***a***`, nesting,
 * left/right flanking, so `2 * 3 * 4` and `a*b*c` stay as written), `~~strike~~`, code spans of any backtick count,
 * `[label](url)` with balanced parentheses and an optional title, `<https://x>`, bare http(s) addresses (balanced
 * parentheses, trailing punctuation left out), backslash escapes, common entities, and pictures inside a sentence.
 * Raw HTML stays text. Each line parses on its own; marks never span lines. Total: any input gives a value, nothing
 * throws. [speechPattern] keeps Flutter's regex for read-aloud only.
 */
public object InlineParser {
    // Groups: 1=***bold italic*** 2=**bold** 3=*italic* 4=__bold__ 5=~~strike~~ 6=`code` 7=[label](url) label 8=url
    // ("." in Dart's dotAll pattern is [\s\S] here: common Kotlin regex has no DOT_MATCHES_ALL option).
    internal const val INLINE_SOURCE =
        """\*\*\*([\s\S]+?)\*\*\*""" +
            """|\*\*([\s\S]+?)\*\*""" +
            """|\*([\s\S]+?)\*""" +
            """|__([\s\S]+?)__""" +
            """|~~([\s\S]+?)~~""" +
            """|`([^`\n]+)`""" +
            """|\[([^\]]+?)\]\(([^)\s]+?)\)"""

    /** Speech keeps Flutter's pattern: a bare URL is read as the agent wrote it. */
    internal val speechPattern = Regex(INLINE_SOURCE)

    private const val PLACEHOLDER = '\uFFFC'
    private val empty = Inlines("", persistentListOf())

    public fun parse(source: String): Inlines {
        if (source.isEmpty()) return empty
        val out = StringBuilder()
        val spans = ArrayList<InlineSpan>()
        val lines = source.split('\n')
        for ((i, line) in lines.withIndex()) {
            if (i > 0) out.append('\n')
            line(line, out, spans)
        }
        return Inlines(out.toString(), spans.sortedWith(compareBy({ it.start }, { -it.end })).toImmutableList())
    }

    private fun line(src: String, out: StringBuilder, spans: MutableList<InlineSpan>) {
        // A picture inside a sentence is a run of its own, never `!` and a link: it is masked as one character the
        // scanner treats as a word.
        var masked = src
        var pictures: Map<Int, MarkdownPicture> = emptyMap()
        if (src.contains("![")) {
            val parts = PictureScanner.scan(src)
            if (parts.any { it.picture != null }) {
                val text = StringBuilder()
                val at = HashMap<Int, MarkdownPicture>()
                for (part in parts) {
                    val picture = part.picture
                    if (picture != null) {
                        at[text.length] = picture
                        text.append(PLACEHOLDER)
                    } else {
                        text.append(part.text!!)
                    }
                }
                masked = text.toString()
                pictures = at
            }
        }
        val scan = InlineScan(masked, 0, masked.length, pictures, links = true)
        scan.run()
        scan.writeTo(out, spans)
    }

    /**
     * The delimiter runs of [line] that are not (fully) paired, in text order. [UnfinishedTail] closes a streaming
     * line with the ones that could open, and hides a mark that closes nothing at the end.
     */
    internal fun leftovers(line: String): List<Leftover> {
        val scan = InlineScan(line, 0, line.length, emptyMap(), links = true)
        scan.run()
        return scan.leftovers()
    }

    /** An unpaired run: [count] marks of [mark] left of [orig], starting at [offset]; [open] when it can still open. */
    internal class Leftover(val offset: Int, val mark: Char, val count: Int, val orig: Int, val open: Boolean)
}

/** What an inline run lays out as (Flutter `kitMarkdownDisplay`): the words without marks, and its path chips. */
public data class DisplayText(val text: String, val pathChips: Int)

/** A prompt bubble sizes itself from this, not from the raw source (B-CHAT-061). */
public fun markdownDisplay(source: String): DisplayText {
    val inlines = InlineParser.parse(source)
    val chips = inlines.spans.count { it.kind is SpanKind.PathCandidate }
    return DisplayText(inlines.text.replace("\uFFFC", ""), chips)
}
