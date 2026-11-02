package io.github.eslamasabry.oc.core.markdown

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Inline Markdown (Flutter `_KitMdTextState._spansOf`): `***bold italic***`, `**bold**`, `*italic*`, `__bold__`,
 * `~~strike~~`, `` `code` ``, `[label](url)`, a bare http(s) address, and pictures inside a sentence. Each line parses
 * on its own; marks never span lines. Total: any input gives a value, nothing throws.
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

    /** Rendering adds 9 = a bare https/http URL (trailing punctuation left out). */
    internal val renderPattern =
        Regex(INLINE_SOURCE + """|(https?://[^\s<>()\[\]`]*[^\s<>()\[\]`.,;:!?'"*_~])""")

    private const val PLACEHOLDER = '￼'
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
        // A picture inside a sentence is a run of its own, never `!` and a link.
        if (src.contains("![")) {
            val parts = PictureScanner.scan(src)
            if (parts.any { it.picture != null }) {
                for (part in parts) {
                    val picture = part.picture
                    if (picture != null) {
                        spans += InlineSpan(out.length, out.length + 1, SpanKind.Picture(picture))
                        out.append(PLACEHOLDER)
                    } else {
                        plain(part.text!!, out, spans)
                    }
                }
                return
            }
        }
        plain(src, out, spans)
    }

    private fun plain(src: String, out: StringBuilder, spans: MutableList<InlineSpan>) {
        var pos = 0
        for (m in renderPattern.findAll(src)) {
            if (m.range.first > pos) out.append(src, pos, m.range.first)
            pos = m.range.last + 1
            val g = m.groups
            val label = g[7]?.value
            val bare = g[9]?.value
            when {
                label != null || bare != null -> {
                    val start = out.length
                    out.append(label ?: bare)
                    spans += InlineSpan(start, out.length, SpanKind.Link(g[8]?.value ?: bare!!))
                }
                g[6] != null -> {
                    val code = g[6]!!.value
                    val start = out.length
                    out.append(code)
                    spans += InlineSpan(start, out.length, SpanKind.Code)
                    if (PathChipScanner.looksLikeFilePath(code)) {
                        val (path, number) = PathChipScanner.stripLineSuffix(code)
                        spans += InlineSpan(start, out.length, SpanKind.PathCandidate(path, number))
                    }
                }
                g[5] != null -> nested(g[5]!!.value, out, spans, SpanKind.Strike)
                g[1] != null -> nested(g[1]!!.value, out, spans, SpanKind.Bold, SpanKind.Italic)
                g[4] != null -> nested(g[4]!!.value, out, spans, SpanKind.Bold)
                g[2] != null -> nested(g[2]!!.value, out, spans, SpanKind.Bold)
                g[3] != null -> nested(g[3]!!.value, out, spans, SpanKind.Italic)
                else -> out.append(m.value)
            }
        }
        if (pos < src.length) out.append(src, pos, src.length)
    }

    private fun nested(inner: String, out: StringBuilder, spans: MutableList<InlineSpan>, vararg kinds: SpanKind) {
        val start = out.length
        // Emphasis content may itself hold a picture (Flutter recurses through _spansOf, pictures first).
        line(inner, out, spans)
        for (kind in kinds) spans += InlineSpan(start, out.length, kind)
    }
}

/** What an inline run lays out as (Flutter `kitMarkdownDisplay`): the words without marks, and its path chips. */
public data class DisplayText(val text: String, val pathChips: Int)

/** A prompt bubble sizes itself from this, not from the raw source (B-CHAT-061). */
public fun markdownDisplay(source: String): DisplayText {
    var chips = 0
    val text =
        InlineParser.renderPattern.replace(source) { m ->
            val g = m.groups
            g[7]?.let { return@replace it.value }
            g[6]?.let {
                if (PathChipScanner.looksLikeFilePath(it.value)) chips++
                return@replace it.value
            }
            for (group in 1..5) g[group]?.let { return@replace it.value }
            m.value
        }
    return DisplayText(text, chips)
}
