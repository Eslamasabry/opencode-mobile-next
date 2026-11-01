package io.github.eslamasabry.oc.ui.kitchat.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontFamily
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Plan 09 §9.1 `InlineAnnotatedTotalityTest` and the inline rules of B-CHAT-064 and R15/R17. */
class InlineAnnotatedTest {
    private val style = KitMdInlineStyle(Color.Blue, Color.Gray, Color.LightGray, FontFamily.Monospace)
    private val opened = mutableListOf<KitSpanKind>()
    private val sink = KitMdLinkSink { opened += it }

    private fun links(s: androidx.compose.ui.text.AnnotatedString) = s.getLinkAnnotations(0, s.length).map { it.item }

    @Test
    fun `B-CHAT-064 a path chip is a link only once the file is confirmed, with the line kept apart`() {
        val inl = TestStreamingMarkdown.parseInlines("see `lib/main.dart:42` and `not a path`")
        val path = inl.spans.single { it.kind is KitSpanKind.Path }.kind as KitSpanKind.Path
        assertEquals("lib/main.dart", path.path)
        assertEquals(42, path.line)
        assertTrue(inl.spans.any { it.kind == KitSpanKind.Code })
        assertEquals(0, links(inlineAnnotated(inl, style, sink)).size, "unconfirmed: a chip, not a link")
        val confirmed = inlineAnnotated(inl, style, sink) { it == "lib/main.dart" }
        val link = links(confirmed).single() as LinkAnnotation.Clickable
        link.linkInteractionListener!!.onClick(link)
        assertEquals(listOf<KitSpanKind>(path), opened)
    }

    @Test
    fun `B-SEC-064 a link is handed to the caller, never opened by the kit`() {
        val inl = TestStreamingMarkdown.parseInlines("read [the docs](https://example.com/a?b=c) now")
        val s = inlineAnnotated(inl, style, sink)
        assertEquals("read the docs now", s.text)
        val link = links(s).single() as LinkAnnotation.Clickable
        assertEquals("https://example.com/a?b=c", link.tag)
        link.linkInteractionListener!!.onClick(link)
        assertEquals(listOf<KitSpanKind>(KitSpanKind.Link("https://example.com/a?b=c")), opened)
    }

    @Test
    fun `inline code and paths are isolated so they never reorder inside right-to-left prose`() {
        val inl = KitInlines("شغّل npm test الآن", persistentListOf(KitSpan(5, 13, KitSpanKind.Code)))
        val s = inlineAnnotated(inl, style, sink)
        assertEquals("شغّل ${FSI}npm test$PDI الآن", s.text)
        val code = s.spanStyles.single { it.item.fontFamily == FontFamily.Monospace }
        assertEquals("${FSI}npm test$PDI", s.text.substring(code.start, code.end))
    }

    @Test
    fun `nested spans keep their ranges`() {
        val inl = TestStreamingMarkdown.parseInlines("a **bold _and italic_** ~~gone~~")
        val s = inlineAnnotated(inl, style, sink)
        assertEquals("a bold and italic gone", s.text)
        assertEquals(3, s.spanStyles.size)
    }

    @Test
    fun `the inline builder is total over random spans and texts`() {
        val rnd = Random(9_1)
        val alphabet = "ab ال `*_[]()~\n😀"
        val kinds =
            listOf(
                KitSpanKind.Bold, KitSpanKind.Italic, KitSpanKind.Strike, KitSpanKind.Code, KitSpanKind.Link("https://x.y"),
                KitSpanKind.Path("a/b.kt", 3),
            )
        repeat(10_000) {
            val text = String(CharArray(rnd.nextInt(0, 40)) { alphabet[rnd.nextInt(alphabet.length)] })
            val spans =
                List(rnd.nextInt(0, 6)) {
                    KitSpan(rnd.nextInt(-5, text.length + 5), rnd.nextInt(-5, text.length + 5), kinds[rnd.nextInt(kinds.size)])
                }.toImmutableList()
            val s = inlineAnnotated(KitInlines(text, spans), style, sink) { rnd.nextBoolean() }
            assertEquals(text, s.text.replace(FSI.toString(), "").replace(PDI.toString(), ""))
        }
        // And the fixture parser's output, through the same builder.
        val reply = P3Fixtures.reply()
        reply.lines().forEach { inlineAnnotated(TestStreamingMarkdown.parseInlines(it), style, sink) }
    }
}
