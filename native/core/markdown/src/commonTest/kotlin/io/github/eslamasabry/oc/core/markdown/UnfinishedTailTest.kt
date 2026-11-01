package io.github.eslamasabry.oc.core.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Ported from test/markdown_unfinished_syntax_test.dart. CHAT-GENUI-21 is the map's streaming-polish feature (the
 * owner's 2026-10-27 requests); the catalog has no B- row for it.
 */
class UnfinishedTailTest {
    // partial text, what is drawn while it streams.
    private val cases =
        listOf(
            "Make it **bold" to "Make it bold",
            "Make it __bold" to "Make it bold",
            "Make it *ita" to "Make it ita",
            "Run `cod" to "Run cod",
            "Make it ~~stri" to "Make it stri",
            "See [the docs" to "See the docs",
            "See [the docs]" to "See the docs",
            "See [the docs](htt" to "See the docs",
            "Make it **" to "Make it",
            "Make it **bold*" to "Make it bold",
            "- one\n- Make **bo" to "one\nMake bo",
        )

    @Test
    fun `CHAT-GENUI-21 streaming shows no raw marks`() {
        for ((partial, shown) in cases) {
            val blocks = parse(partial, streaming = true)
            assertEquals(shown, drawn(blocks).trimEnd(), partial)
        }
    }

    @Test
    fun `CHAT-GENUI-21 words arrive already bold, italic, in code, struck`() {
        fun kinds(text: String) = (parse(text, streaming = true).last() as Block.Prose).inlines.spans.map { it.kind }
        assertEquals(listOf(SpanKind.Bold), kinds("Make it **bold"))
        assertEquals(listOf(SpanKind.Italic), kinds("Make it *ita"))
        assertEquals(listOf(SpanKind.Code), kinds("Run `cod"))
        assertEquals(listOf(SpanKind.Strike), kinds("Make it ~~stri"))
    }

    @Test
    fun `CHAT-GENUI-21 a partial table row is held back until it has two cells`() {
        val table = "| a | b |\n| --- | --- |\n| 1 | 2 |\n"
        val first = drawn(parse("$table| **x", streaming = true))
        assertFalse(first.contains("|"))
        assertFalse(first.contains("*"))
        val second = drawn(parse("$table| **x** | **y", streaming = true))
        assertFalse(second.contains("*"))
        assertTrue(second.contains("y"))
    }

    @Test
    fun `CHAT-GENUI-21 an open fence is left as it is`() {
        val shown = drawn(parse("```dart\nfinal a = **b;\nx = `y", streaming = true))
        assertTrue(shown.contains("final a = **b;"))
        assertTrue(shown.contains("x = `y"))
    }

    @Test
    fun `CHAT-GENUI-21 a finished reply draws its exact text`() {
        assertEquals("Make it **bold", drawn(parse("Make it **bold")))
        assertEquals("Run `cod", drawn(parse("Run `cod")))
        assertEquals("Ends with a lone *", drawn(parse("Ends with a lone *")))
        assertEquals("It ends with", drawn(parse("It ends with *", streaming = true)).trimEnd())
    }

    @Test
    fun `CHAT-GENUI-21 only the last line changes`() {
        assertEquals("**one\n\nthen **two**", UnfinishedTail.close("**one\n\nthen **two"))
    }

    @Test
    fun `CHAT-GENUI-21 finished syntax is untouched`() {
        for (s in listOf(
            "a **b** c",
            "a `b` c",
            "see [a](https://b.c) now",
            "2 * 3",
            "arr[0",
            "- [ ] todo",
            "- [x] done",
            "snake_case_name",
            "![Image](/tmp/x.png)",
            "a \\*b",
            "",
        )) {
            assertEquals(s, UnfinishedTail.close(s))
        }
    }

    @Test
    fun `CHAT-GENUI-21 unfinished syntax closes`() {
        assertEquals("a **b *c***", UnfinishedTail.close("a **b *c"))
        assertEquals("a **b `c`**", UnfinishedTail.close("a **b `c"))
        assertEquals("a ~~b~~ ", UnfinishedTail.close("a ~~b "))
        assertEquals("a b", UnfinishedTail.close("a [b](c"))
        assertEquals("a ", UnfinishedTail.close("a `"))
    }
}
