package io.github.eslamasabry.oc.core.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Input → expected blocks, ported from test/kit/kit_markdown_test.dart, test/markdown_reading_test.dart and
 * test/markdown_agent_blocks_test.dart (their widget assertions turned into block facts). CHAT-GENUI-16 is the map's
 * Markdown renderer feature; the catalog has no B- row for the block rules themselves.
 */
class BlockParseTest {
    @Test
    fun `CHAT-GENUI-16 blocks of the role table`() {
        val blocks =
            parse(
                "# Title\n\n### Small\n\nSome **bold** and `code` here.\n\n" +
                    "- one\n- two\n\n> quoted\n\n---\n\n[site](https://example.com)",
            )
        assertEquals(
            listOf(
                "H1 \"Title\"",
                "H3 \"Small\"",
                "P \"Some bold and code here.\"",
                "LI • d0 first \"one\"",
                "LI • d0 \"two\"",
                "Q \"quoted\"",
                "R",
                "P \"site\"",
            ),
            dump(blocks),
        )
        assertFalse(drawn(blocks).contains("**"))
        val prose = blocks[2] as Block.Prose
        val code = prose.inlines.spans.single { it.kind == SpanKind.Code }
        assertEquals("code", prose.inlines.text.substring(code.start, code.end))
        val link = (blocks.last() as Block.Prose).inlines.spans.single()
        assertEquals(SpanKind.Link("https://example.com"), link.kind)
    }

    @Test
    fun `CHAT-GENUI-16 an ordered list split by a code block keeps counting`() {
        val blocks =
            parse("1. Upgrade the router\n\n```\nnpm install router@6\n```\n\n2. Or keep v5\n3. Then test\n\n- a\n- b")
        assertEquals(
            listOf(
                "LI 1. d0 first \"Upgrade the router\"",
                "C[] \"npm install router@6\"",
                "LI 2. d0 first \"Or keep v5\"",
                "LI 3. d0 \"Then test\"",
                "LI • d0 first \"a\"",
                "LI • d0 \"b\"",
            ),
            dump(blocks),
        )
    }

    @Test
    fun `CHAT-GENUI-16 lists nest by 2 or 4 spaces and nested ordered runs restart`() {
        assertEquals(
            listOf(
                "LI 1. d0 first \"a\"",
                "LI 1. d1 \"a1\"",
                "LI 2. d1 \"a2\"",
                "LI 2. d0 \"b\"",
            ),
            dump(parse("1. a\n    1. a1\n    2. a2\n2. b")),
        )
        assertEquals(
            listOf("LI • d0 first \"x\"", "LI • d1 \"y\"", "LI • d2 \"z\""),
            dump(parse("- x\n  - y\n    - z")),
        )
    }

    @Test
    fun `CHAT-GENUI-16 a line indented under a list item stays in that item`() {
        val blocks =
            parse(
                "1. Collect the diagnostics.\n" +
                    "2. Ask only for details needed to reproduce or understand\n" +
                    "   impact.\n" +
                    "3. Draft the issue.\n" +
                    "   ```sh\n" +
                    "   gh issue create\n" +
                    "   ```\n",
            )
        assertEquals(
            listOf(
                "LI 1. d0 first \"Collect the diagnostics.\"",
                "LI 2. d0 \"Ask only for details needed to reproduce or understand\\nimpact.\"",
                "LI 3. d0 \"Draft the issue.\"",
                "C[sh] \"gh issue create\"",
            ),
            dump(blocks),
        )
    }

    @Test
    fun `CHAT-GENUI-16 an HTML comment block is hidden, one in a fence or inside a line is not`() {
        val blocks =
            parse(
                "<!-- Built-in skill. Registered in code. -->\n# Report\n\n" +
                    "Add `<!-- TODO -->` to the page, then <!-- open\n\n" +
                    "<!--\nmulti line\n-->\n\nAfter the block.\n\n" +
                    "```html\n<!-- keep me -->\n```\n",
            )
        val shown = drawn(blocks)
        assertFalse(shown.contains("Built-in skill"))
        assertFalse(shown.contains("multi line"))
        assertTrue(shown.contains("Report"))
        assertTrue(shown.contains("<!-- TODO -->"))
        assertTrue(shown.contains("After the block."))
        assertTrue(shown.contains("keep me"))
        // Line breaks stay, so the lines after a comment do not move.
        assertEquals("a\n\n\n\nb", blankHtmlComments("a\n<!-- x\ny -->\n\nb"))
    }

    @Test
    fun `CHAT-GENUI-17 fence delimiters keep the body and the streaming state`() {
        val cases =
            listOf(
                Triple("````markdown\n```dart\nclass A {}\n```\n````", "```dart\nclass A {}\n```", true),
                Triple("~~~dart\nclass A {}\n~~~~", "class A {}", true),
                Triple("````dart\nfirst\n```\n~~~\n```` extra\nlast\n````", "first\n```\n~~~\n```` extra\nlast", true),
                Triple("~~~dart\nclass A {}\n```", "class A {}\n```", false),
                Triple("   ~~~dart\n   one\n two\n   ~~~", "one\ntwo", true),
                Triple("```dart\nclass A {", "class A {", false),
                Triple("~~~text | hint\n--- | ---\nvalue\n~~~", "--- | ---\nvalue", true),
            )
        for ((input, body, closed) in cases) {
            val code = parse(input).single() as Block.Code
            assertEquals(body, code.text, input)
            assertEquals(!closed, code.open, input)
        }
    }

    @Test
    fun `CHAT-GENUI-17 invalid backtick info and a four-space opening are not fences`() {
        assertTrue(parse("```bad`info\ntext\n\n    ~~~dart\ncode").none { it is Block.Code })
    }

    @Test
    fun `CHAT-GENUI-17 copy keeps the original CRLF, indent and trailing line break`() {
        val line = "  final a = 1; // value "
        val code = parse("  ~~~dart\r\n$line\r\n  \r\n  ~~~").single() as Block.Code
        assertEquals("$line\r\n  \r\n", code.copyText)
        assertEquals("final a = 1; // value \n", code.text)
        assertEquals("dart", code.info)
        assertEquals(CodeKind.Fence, code.kind)
    }

    @Test
    fun `CHAT-GENUI-17 an unclosed fence is open until it closes`() {
        val open = parse("```dart\nfinal a = 1;").single() as Block.Code
        assertTrue(open.open)
        val closed = parse("```dart\nfinal a = 1;\n```").single() as Block.Code
        assertFalse(closed.open)
        assertEquals("dart", closed.info)
        assertEquals("final a = 1;\n", closed.copyText)
    }

    @Test
    fun `CHAT-GENUI-16 a part that is all code is one closed block`() {
        val code = MarkdownBlocks.wholeCode("p", "a = 1\n# not a heading", "python")
        assertEquals(CodeKind.WholePart, code.kind)
        assertFalse(code.open)
        assertEquals("a = 1\n# not a heading", code.text)
    }

    @Test
    fun `CHAT-GENUI-16 tables keep cells, alignment and escaped pipes`() {
        val blocks = parse("| a | b | c |\n| :--- | :---: | ---: |\n| 1 | `x\\|y` | **3** |\n| 4 | 5 | 6 |\n\nafter")
        assertEquals(listOf("T a|b|c / Start,Center,End / 1|x|y|3;4|5|6", "P \"after\""), dump(blocks))
        val table = blocks.first() as Block.Table
        assertEquals(SpanKind.Code, table.rows[0][1].spans.single().kind)
    }

    @Test
    fun `CHAT-GENUI-16 a header without its delimiter row is a paragraph`() {
        assertEquals(listOf("P \"| a | b |\""), dump(parse("| a | b |")))
        assertEquals(listOf("P \"| a | b |\\n| - | - |\""), dump(parse("| a | b |\n| - | - |")))
    }

    @Test
    fun `CHAT-GENUI-16 links and code inside headings and bold are drawn, not shown`() {
        val blocks = parse("## See [docs](https://a.dev) and `lib/a.dart`\n\n**Bold [x](https://b.dev) `y`**")
        assertEquals(listOf("H2 \"See docs and lib/a.dart\"", "P \"Bold x y\""), dump(blocks))
        val bold = (blocks[1] as Block.Prose).inlines.spans
        assertTrue(bold.any { it.kind == SpanKind.Bold && it.start == 0 && it.end == 8 })
        assertTrue(bold.any { it.kind == SpanKind.Link("https://b.dev") })
        assertTrue(bold.any { it.kind == SpanKind.Code })
    }

    @Test
    fun `CHAT-GENUI-20 closed agent fences are agent blocks, open and unknown fences stay code`() {
        assertEquals(
            listOf("A checklist x:Parse the fence,o:Render the block,x:Ship"),
            dump(parse("```checklist\n[x] Parse the fence\n[ ] Render the block\n- [X] Ship\n```")),
        )
        assertEquals(
            listOf("A command flutter pub get,flutter test"),
            dump(parse("```command\nflutter pub get\nflutter test\n```")),
        )
        assertEquals(listOf("A choices A,B,C"), dump(parse("```Choices\n- A\n2. B\n\nC\n```")))
        // Still streaming: never an agent block.
        assertEquals(listOf("C[choices] open \"A\\nB\""), dump(parse("```choices\nA\nB")))
        assertEquals(listOf("C[dart] \"void main() {}\""), dump(parse("```dart\nvoid main() {}\n```")))
        assertEquals(null, AgentBlockType.of("dart"))
        assertEquals(AgentBlockType.Checklist, AgentBlockType.of("Checklist"))
    }

    @Test
    fun `CHAT-GENUI-16 keys are the part and the start offset, unique and ordered`() {
        val blocks = parse("same\n\nsame\n\n- a\n- a")
        assertEquals(listOf("p:0", "p:6", "p:12", "p:16"), blocks.map { it.key })
        assertEquals(listOf(0 to 4, 6 to 10, 12 to 15, 16 to 19), blocks.map { it.start to it.end })
    }

    @Test
    fun `B-CHAT-060 the caret rides on words, never after code, a table, pictures or a rule`() {
        assertTrue(parse("First.\n\nStill writing", streaming = true).last().carriesCaret)
        assertTrue(parse("Steps:\n\n- one\n- two", streaming = true).last().carriesCaret)
        assertFalse(parse("Here:\n\n```dart\nfinal a = 1;", streaming = true).last().carriesCaret)
        assertFalse(parse("| a | b |\n| --- | --- |\n| 1 | 2 |", streaming = true).last().carriesCaret)
        assertFalse(parse("![a](/tmp/a.png)", streaming = true).last().carriesCaret)
        assertFalse(parse("---", streaming = true).last().carriesCaret)
    }

    @Test
    fun `CHAT-GENUI-16 blank or empty text has no blocks`() {
        assertEquals(emptyList(), parse("  \n "))
        assertEquals(emptyList(), parse(""))
    }
}
