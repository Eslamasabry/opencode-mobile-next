package io.github.eslamasabry.oc.ui.kitchat.markdown

import io.github.eslamasabry.oc.core.markdown.Align
import io.github.eslamasabry.oc.core.markdown.Block
import io.github.eslamasabry.oc.core.markdown.Inlines
import io.github.eslamasabry.oc.core.markdown.MarkdownBlocks
import io.github.eslamasabry.oc.core.markdown.MarkdownPicture
import io.github.eslamasabry.oc.core.markdown.PictureKind
import io.github.eslamasabry.oc.core.markdown.SpanKind
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * P12-CHAT-FEEL: one transcript row's Markdown source → the kit's render model. `:feature:chat` may not see
 * `:core:markdown` (ModuleRules), so the parse and the field copy (KitMdModel's promise) live here. The row's text is
 * one top-level block (or one ≤ 16-line chunk of one, STREAM-FIX), so a parse is O(one chunk). [key] prefixes every
 * block key, so blocks of different rows never share a reveal state.
 */
public object KitMdSource {
    public fun blocks(key: String, text: String, streaming: Boolean): ImmutableList<KitMdBlock> {
        if (text.isEmpty()) return persistentListOf()
        // Keys by position, not by offset: a streaming row re-parses as it grows (a paragraph that becomes a list
        // item, an unfinished mark), and its reveal state must stay the same one, never orphaned mid-reveal.
        return MarkdownBlocks.parse(key, text, streaming)
            .mapNotNull(::toKit)
            .mapIndexed { i, b -> rekey(b, "$key#$i") }
            .toImmutableList()
    }

    private fun rekey(b: KitMdBlock, key: String): KitMdBlock =
        when (b) {
            is KitMdBlock.Prose -> b.copy(key = key)
            is KitMdBlock.Heading -> b.copy(key = key)
            is KitMdBlock.Quote -> b.copy(key = key, children = b.children.map { rekey(it, "$key:q") }.toImmutableList())
            is KitMdBlock.ListItem -> b.copy(key = key)
            is KitMdBlock.Code -> b.copy(key = key)
            is KitMdBlock.Table -> b.copy(key = key)
            is KitMdBlock.Rule -> b.copy(key = key)
            is KitMdBlock.Pictures -> b.copy(key = key)
        }

    private fun toKit(b: Block): KitMdBlock? =
        when (b) {
            is Block.Prose -> KitMdBlock.Prose(b.key, inlines(b.inlines))
            is Block.Heading -> KitMdBlock.Heading(b.key, b.level, inlines(b.inlines))
            is Block.Quote -> KitMdBlock.Quote(b.key, persistentListOf(KitMdBlock.Prose("${b.key}:q", inlines(b.inlines))))
            is Block.ListItem -> KitMdBlock.ListItem(b.key, b.ordered, b.number, b.depth, inlines(b.inlines))
            is Block.Code -> KitMdBlock.Code(b.key, b.info, b.text, b.open)
            is Block.Table ->
                KitMdBlock.Table(
                    b.key,
                    b.header.map(::inlines).toImmutableList(),
                    b.aligns.map(::align).toImmutableList(),
                    b.rows.map { r -> r.map(::inlines).toImmutableList() }.toImmutableList(),
                )
            is Block.Rule -> KitMdBlock.Rule(b.key)
            is Block.PictureGroup -> KitMdBlock.Pictures(b.key, b.pictures.mapNotNull(::picture).toImmutableList())
            // A closed ```choices/```checklist/```command fence: its cards belong to the cards unit; until then its
            // lines read as a plain block (never lost, never interactive).
            is Block.AgentBlock ->
                KitMdBlock.Code(b.key, b.type.info, b.items.joinToString("\n") { it.text }, open = false)
        }

    /**
     * Pictures inside a sentence keep their place (U+FFFC, drawn as a frame glyph); a web one becomes a link to its
     * address. A server file one (B-CHAT-059) keeps its words after the glyph and becomes a [KitSpanKind.Picture]
     * the caller opens in its viewer, so `See ![Screenshot](/tmp/result.png) here` reads "See ▣ Screenshot here".
     */
    private fun inlines(i: Inlines): KitInlines {
        // Words added after a file picture's glyph: (offset in the source text, words).
        val inserts = ArrayList<Pair<Int, String>>()
        for (s in i.spans) {
            val k = s.kind as? SpanKind.Picture ?: continue
            if (k.picture.kind != PictureKind.File || k.picture.path == null) continue
            val words = label(k.picture) ?: continue
            inserts += s.end to " $words"
        }
        inserts.sortBy { it.first }
        fun shift(x: Int): Int {
            var out = x
            for ((at, words) in inserts) if (at <= x) out += words.length else break
            return out
        }
        val spans =
            i.spans.mapNotNull { s ->
                val kind =
                    when (val k = s.kind) {
                        SpanKind.Bold -> KitSpanKind.Bold
                        SpanKind.Italic -> KitSpanKind.Italic
                        SpanKind.Strike -> KitSpanKind.Strike
                        SpanKind.Code -> KitSpanKind.Code
                        is SpanKind.Link -> KitSpanKind.Link(k.url)
                        is SpanKind.PathCandidate -> KitSpanKind.Path(k.path, k.line)
                        is SpanKind.Picture -> {
                            val pic = k.picture
                            val path = pic.path
                            if (pic.kind == PictureKind.File && path != null) KitSpanKind.Picture(path, label(pic))
                            else pic.url?.let { KitSpanKind.Link(it) }
                        }
                    } ?: return@mapNotNull null
                KitSpan(shift(s.start), shift(s.end), kind)
            }
        // The placeholder is drawn as a small frame glyph, so a sentence never shows an empty box.
        val text = i.text.replace(PLACEHOLDER, FRAME)
        if (inserts.isEmpty()) return KitInlines(text, spans.toImmutableList())
        val out = StringBuilder(text.length + inserts.sumOf { it.second.length })
        var next = 0
        for ((at, words) in inserts) {
            val cut = at.coerceIn(next, text.length)
            out.append(text, next, cut).append(words)
            next = cut
        }
        out.append(text, next, text.length)
        return KitInlines(out.toString(), spans.toImmutableList())
    }

    private fun picture(p: MarkdownPicture): KitPicture? =
        when (p.kind) {
            PictureKind.Web -> KitPicture(label(p), url = p.url, host = p.host)
            PictureKind.File -> KitPicture(label(p), path = p.path)
            PictureKind.Inert -> if (p.hasDescription) KitPicture(p.alt) else null
        }

    private fun label(p: MarkdownPicture): String? =
        when {
            p.hasDescription -> p.alt
            p.hasMeaningfulName -> p.fileName
            else -> null
        }

    private fun align(a: Align): KitAlign =
        when (a) {
            Align.Center -> KitAlign.Center
            Align.End -> KitAlign.End
            else -> KitAlign.Start
        }

    private const val PLACEHOLDER = '￼'
    private const val FRAME = '▣'
}
