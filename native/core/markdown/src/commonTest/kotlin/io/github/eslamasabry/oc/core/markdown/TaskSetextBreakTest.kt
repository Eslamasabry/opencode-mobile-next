package io.github.eslamasabry.oc.core.markdown

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Task list items (GFM spec examples 279, 280), setext headings (CommonMark 0.31.2 examples 50–70) and hard line
 * breaks (633–635, 642–644, 647) of the chat Markdown engine.
 */
class TaskSetextBreakTest {
    @Test
    fun `gfm-279 task list items are checks, not text`() {
        assertEquals(
            listOf("LI • d0 first [ ] \"foo\"", "LI • d0 [x] \"bar\""),
            dump(parse("- [ ] foo\n- [x] bar")),
        )
    }

    @Test
    fun `gfm-280 nested task items`() {
        assertEquals(
            listOf("LI • d0 first [x] \"foo\"", "LI • d1 [ ] \"bar\"", "LI • d1 [x] \"baz\"", "LI • d0 [ ] \"bim\""),
            dump(parse("- [x] foo\n  - [ ] bar\n  - [x] baz\n- [ ] bim")),
        )
    }

    @Test
    fun `task items in ordered lists, with a capital X, and a link that only looks like a box`() {
        assertEquals(
            listOf("LI 1. d0 first [x] \"one\"", "LI 2. d0 [ ] \"two\""),
            dump(parse("1. [X] one\n2. [ ] two")),
        )
        // `[x](u)` is a link and `[x]y` plain text, neither is a box.
        assertEquals(listOf("LI • d0 first \"x\""), dump(parse("- [x](u)")))
        assertEquals(listOf("LI • d0 first \"[x]y\""), dump(parse("- [x]y")))
        assertEquals(listOf("LI • d0 first [ ] \"\""), dump(parse("- [ ]")))
    }

    @Test
    fun `cm-50 and 51, a paragraph underlined by equals or dashes is a heading`() {
        assertEquals(listOf("H1 \"Foo bar\"", "H2 \"Foo bar\""), dump(parse("Foo *bar*\n=========\n\nFoo *bar*\n---------")))
        // Marks never cross a line, so the stars stay (see InlineParser); the heading still spans both lines.
        assertEquals(listOf("H1 \"Foo *bar\\nbaz*\""), dump(parse("Foo *bar\nbaz*\n====")))
    }

    @Test
    fun `cm-53 and 55, any length and indented text`() {
        assertEquals(listOf("H2 \"Foo\"", "H1 \"Foo\""), dump(parse("Foo\n-------------------------\n\nFoo\n=")))
        assertEquals(listOf("H2 \"Foo\"", "H2 \"Foo\"", "H1 \"Foo\""), dump(parse("   Foo\n---\n\n  Foo\n-----\n\n  Foo\n  ===")))
    }

    @Test
    fun `cm-57 to 59 and 66 to 68, which lines the underline takes`() {
        assertEquals(listOf("H2 \"Foo\\nBar\""), dump(parse("Foo\nBar\n---")))
        assertEquals(listOf("R", "H2 \"Foo\"", "H2 \"Bar\"", "P \"Baz\""), dump(parse("---\nFoo\n---\nBar\n---\nBaz")))
        assertEquals(listOf("P \"====\""), dump(parse("\n====")))
        assertEquals(listOf("H2 \"Foo\\nbar\"", "P \"baz\""), dump(parse("Foo\nbar\n---\nbaz")))
        assertEquals(listOf("P \"Foo\"", "H2 \"bar\"", "P \"baz\""), dump(parse("Foo\n\nbar\n---\nbaz")))
        assertEquals(listOf("P \"Foo\\nbar\"", "R", "P \"baz\""), dump(parse("Foo\nbar\n\n---\n\nbaz")))
        assertEquals(listOf("P \"Foo\\nbar\\n---\\nbaz\""), dump(parse("Foo\nbar\n\\---\nbaz")))
    }

    @Test
    fun `cm-60 and 61 rules next to lists stay rules`() {
        assertEquals(listOf("R", "R"), dump(parse("---\n---")))
        assertEquals(listOf("LI • d0 first \"foo\"", "R"), dump(parse("- foo\n-----")))
    }

    @Test
    fun `a lone dash under a paragraph is not an underline, so a list can start`() {
        assertEquals(listOf("P \"Steps:\\n-\""), dump(parse("Steps:\n-")))
        assertEquals(listOf("P \"Steps:\"", "LI • d0 first \"a\""), dump(parse("Steps:\n- a")))
    }

    @Test
    fun `setext headings in a quote and a list item`() {
        assertEquals(listOf("Q[H1 \"Title\"]"), dump(parse("> Title\n> ===")))
        assertEquals(listOf("LI • d0 first {H2 \"Sub\"}"), dump(parse("- \n  Sub\n  ---")).map { it.replace("\"\" ", "") })
    }

    @Test
    fun `cm-633 to 635 hard breaks leave a line break and no marks`() {
        assertEquals(listOf("P \"foo\\nbaz\""), dump(parse("foo  \nbaz")))
        assertEquals(listOf("P \"foo\\nbaz\""), dump(parse("foo\\\nbaz")))
        assertEquals(listOf("P \"foo\\nbaz\""), dump(parse("foo       \nbaz")))
        assertEquals(listOf("P \"foo\\\\nbaz\""), dump(parse("foo\\\\\nbaz")))
    }

    @Test
    fun `cm-642 to 647 a break mark on the last line is text, soft breaks stay line breaks`() {
        assertEquals(listOf("P \"foo\\\""), dump(parse("foo\\")))
        assertEquals(listOf("H3 \"foo\\\""), dump(parse("### foo\\")))
        assertEquals(listOf("P \"foo\\nbaz\""), dump(parse("foo\nbaz")))
        assertEquals(listOf("P \"foo\\nbaz\""), dump(parse("foo \nbaz")))
    }

    @Test
    fun `hard breaks in a list item and a quote`() {
        assertEquals(listOf("LI • d0 first \"a\\nb\""), dump(parse("- a  \n  b")))
        assertEquals(listOf("Q \"a\\nb\""), dump(parse("> a\\\n> b")))
    }

    @Test
    fun `the new constructs streamed one character at a time equal the one-shot parse`() {
        val texts =
            listOf(
                "Todo:\n- [ ] write\n- [x] test\n  - [ ] sub\n",
                "Title\n=====\n\nSub *title*\n---\nbody  \nnext\\\nline\n",
                "> Head\n> ---\n> text\n\n1. [x] done\n   ```sh\n   run\n   ```\n",
            )
        for (text in texts) {
            val md = StreamingMarkdown("p")
            var sent = ""
            for (c in text) {
                md.append(c.toString())
                sent += c
                assertEquals(MarkdownBlocks.parse("p", sent, streaming = true), md.blocks, "after \"$sent\"")
            }
            md.finish()
            assertEquals(MarkdownBlocks.parse("p", text), md.blocks, text)
        }
    }
}
