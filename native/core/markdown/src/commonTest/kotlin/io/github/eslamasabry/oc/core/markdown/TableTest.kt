package io.github.eslamasabry.oc.core.markdown

import kotlin.test.Test
import kotlin.test.assertEquals

/** GFM tables (spec 0.29 examples 198–205) and the chat probes of native/docs/parity/MARKDOWN-EVAL.md class 8. */
class TableTest {
    @Test
    fun `gfm-198 a plain table`() {
        assertEquals(
            listOf("T foo|bar / None,None / baz|bim"),
            dump(parse("| foo | bar |\n| --- | --- |\n| baz | bim |")),
        )
    }

    @Test
    fun `gfm-199 alignment without outer pipes`() {
        assertEquals(
            listOf("T abc|defghi / Center,End / bar|baz"),
            dump(parse("| abc | defghi |\n:-: | -----------:\nbar | baz")),
        )
    }

    @Test
    fun `gfm-200 a one-column table with escaped pipes`() {
        val blocks = parse("| f\\|oo  |\n| ------ |\n| b `\\|` az |\n| b **\\|** im |")
        assertEquals(listOf("T f|oo / None / b | az;b | im"), dump(blocks))
        val table = blocks.single() as Block.Table
        assertEquals(SpanKind.Code, table.rows[0][0].spans.single().kind)
        assertEquals(SpanKind.Bold, table.rows[1][0].spans.single().kind)
    }

    @Test
    fun `gfm-201 a quote ends the table`() {
        assertEquals(
            listOf("T abc|def / None,None / bar|baz", "Q \"bar\""),
            dump(parse("| abc | def |\n| --- | --- |\n| bar | baz |\n> bar")),
        )
    }

    @Test
    fun `gfm-202 dev, a line without a pipe is a paragraph, not a row`() {
        assertEquals(
            listOf("T abc|def / None,None / bar|baz", "P \"bar\"", "P \"bar\""),
            dump(parse("| abc | def |\n| --- | --- |\n| bar | baz |\nbar\n\nbar")),
        )
    }

    @Test
    fun `gfm-203 a delimiter row with another cell count is no table`() {
        assertEquals(listOf("P \"| abc | def |\\n| --- |\\n| bar |\""), dump(parse("| abc | def |\n| --- |\n| bar |")))
    }

    @Test
    fun `gfm-204 short rows are padded and extra cells ignored`() {
        val blocks = parse("| abc | def |\n| --- | --- |\n| bar |\n| bar | baz | boo |")
        assertEquals(listOf("T abc|def / None,None / bar|;bar|baz"), dump(blocks))
        assertEquals(listOf(2, 2), (blocks.single() as Block.Table).rows.map { it.size })
    }

    @Test
    fun `gfm-205 a header with a delimiter row and no body is a table`() {
        assertEquals(listOf("T abc|def / None,None / "), dump(parse("| abc | def |\n| --- | --- |")))
    }

    @Test
    fun `delimiter cells with one or two dashes make a table`() {
        assertEquals(listOf("T a|b / None,None / 1|2"), dump(parse("| a | b |\n|-|--|\n| 1 | 2 |")))
        assertEquals(listOf("T a|b / Center,End / 1|2"), dump(parse("a | b\n:-: | -:\n1 | 2")))
        assertEquals(listOf("T a|b / Start,None / 1|2"), dump(parse("a | b\n:- | -\n1 | 2")))
    }

    @Test
    fun `a row of one cell continues the table, an empty row line or a heading ends it`() {
        assertEquals(
            listOf("T a|b / None,None / 1|;2|3", "H2 \"Next\""),
            dump(parse("| a | b |\n|---|---|\n| 1\n| 2 | 3 |\n## Next")),
        )
        assertEquals(
            listOf("T a|b / None,None / 1|2", "C[] \"x\""),
            dump(parse("| a | b |\n|---|---|\n| 1 | 2 |\n```\nx\n```")),
        )
        assertEquals(
            listOf("T a|b / None,None / 1|2", "P \"|\""),
            dump(parse("| a | b |\n|---|---|\n| 1 | 2 |\n\n|")),
        )
    }

    @Test
    fun `a table streamed one character at a time equals the one-shot parse`() {
        for (text in listOf("Intro\n| a | b |\n|-|--|\n| 1 |\n| 2 | 3 | 4 |\nafter\n", "a | b\n:-: | -:\n1 | 2\n\nend")) {
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
