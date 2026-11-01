package io.github.eslamasabry.oc.core.markdown

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Inline marks (Flutter `_spansOf`) and the totality the renderer relies on (plan 09 §9.1, ADR §9.6). */
class InlineParserTest {
    @Test
    fun `CHAT-GENUI-16 each mark becomes a span over its words`() {
        val inlines = InlineParser.parse("***both*** **b** *i* __u__ ~~s~~ `c` [l](https://x.dev) https://y.dev/a.")
        assertEquals("both b i u s c l https://y.dev/a.", inlines.text)
        fun range(kind: SpanKind) = inlines.spans.filter { it.kind == kind }.map { inlines.text.substring(it.start, it.end) }
        assertEquals(listOf("both", "b", "u"), range(SpanKind.Bold))
        assertEquals(listOf("both", "i"), range(SpanKind.Italic))
        assertEquals(listOf("s"), range(SpanKind.Strike))
        assertEquals(listOf("c"), range(SpanKind.Code))
        assertEquals(listOf("l"), range(SpanKind.Link("https://x.dev")))
        // A bare address leaves its trailing full stop out.
        assertEquals(listOf("https://y.dev/a"), range(SpanKind.Link("https://y.dev/a")))
    }

    @Test
    fun `CHAT-GENUI-16 marks never cross a line break`() {
        val inlines = InlineParser.parse("**open\nclose**")
        assertEquals("**open\nclose**", inlines.text)
        assertTrue(inlines.spans.isEmpty())
    }

    @Test
    fun `CHAT-GENUI-16 any input parses without throwing and every span stays inside the text`() {
        val alphabet = "ab *_~`[]()!<>|\\#:/.-\nاאhttps"
        val random = Random(2026_11_01)
        repeat(10_000) {
            val length = random.nextInt(0, 40)
            val text = buildString { repeat(length) { append(alphabet[random.nextInt(alphabet.length)]) } }
            val inlines = InlineParser.parse(text)
            for (span in inlines.spans) {
                assertTrue(span.start in 0..span.end && span.end <= inlines.text.length, "\"$text\" → $span")
            }
            // Block parses and the other scanners are total too.
            MarkdownBlocks.parse("p", text, streaming = it % 2 == 0)
            markdownDisplay(text)
            UnfinishedTail.close(text)
            proseForSpeech(text)
        }
    }
}
