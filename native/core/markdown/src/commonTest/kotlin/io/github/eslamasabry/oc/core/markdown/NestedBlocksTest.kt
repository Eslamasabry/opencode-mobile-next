package io.github.eslamasabry.oc.core.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Lists and quotes hold blocks. Cases marked `cm-NNN` are CommonMark 0.31.2 spec examples (list items, block quotes)
 * adapted to the block model; the rest are chat replies from native/docs/parity/MARKDOWN-EVAL.md (probes p05, p06, p15).
 */
class NestedBlocksTest {
    @Test
    fun `sub-bullets under numbered steps sit one level deeper and the numbering goes on`() {
        // p05
        assertEquals(
            listOf(
                "LI 1. d0 first \"Install\"",
                "LI • d1 \"npm\"",
                "LI • d1 \"yarn\"",
                "LI 2. d0 \"Run\"",
                "LI • d1 \"dev\"",
                "LI 3. d0 \"Ship\"",
            ),
            dump(parse("1. Install\n   - npm\n   - yarn\n2. Run\n   - dev\n3. Ship")),
        )
    }

    @Test
    fun `cm-318 style, a code fence stays in its list item`() {
        val blocks = parse("1. Run:\n\n   ```sh\n   npm test\n   ```\n\n2. Done")
        assertEquals(
            listOf("LI 1. d0 first \"Run:\" {C[sh] \"npm test\"}", "LI 2. d0 \"Done\""),
            dump(blocks),
        )
        val code = (blocks[0] as Block.ListItem).children.single() as Block.Code
        assertEquals("npm test\n", code.copyText)
    }

    @Test
    fun `a fence in a nested item belongs to the nested item`() {
        assertEquals(
            listOf("LI • d0 first \"a\"", "LI • d1 \"b\" {C[] \"x\"}", "LI • d0 \"c\""),
            dump(parse("- a\n  - b\n    ```\n    x\n    ```\n- c")),
        )
    }

    @Test
    fun `cm-306 style, a second paragraph and a blank line between items keep one list`() {
        val blocks = parse("- one\n\n  more of one\n\n- two")
        assertEquals(
            listOf("LI • d0 first \"one\" {P \"more of one\"}", "LI • d0 \"two\""),
            dump(blocks),
        )
    }

    @Test
    fun `loose ordered list keeps counting`() {
        assertEquals(
            listOf("LI 1. d0 first \"a\"", "LI 2. d0 \"b\"", "LI 3. d0 \"c\""),
            dump(parse("1. a\n\n2. b\n\n3. c")),
        )
    }

    @Test
    fun `a list does not swallow a paragraph after a blank line`() {
        assertEquals(
            listOf("LI • d0 first \"a\"", "LI • d0 \"b\"", "P \"after\""),
            dump(parse("- a\n- b\n\nafter")),
        )
    }

    @Test
    fun `an ordered list after a bullet list is a list of its own`() {
        assertEquals(
            listOf("LI • d0 first \"a\"", "LI 1. d0 first \"b\""),
            dump(parse("- a\n1. b")),
        )
    }

    @Test
    fun `nested list restarts its own numbers and the parent goes on`() {
        assertEquals(
            listOf("LI 3. d0 first \"c\"", "LI 1. d1 \"x\"", "LI 4. d0 \"d\""),
            dump(parse("3. c\n   1. x\n4. d")),
        )
    }

    @Test
    fun `cm-238 style, block quote with a paragraph and a heading inside`() {
        assertEquals(
            listOf("Q[H1 \"Foo\" ; P \"bar\\nbaz\"]"),
            dump(parse("> # Foo\n> bar\n> baz")),
        )
    }

    @Test
    fun `p15, lists and fences inside a quote render`() {
        assertEquals(
            listOf("Q[P \"Note:\" ; LI • d0 first \"a\" ; LI • d0 \"b\" ; C[js] \"let x\"]"),
            dump(parse("> Note:\n> - a\n> - b\n>\n> ```js\n> let x\n> ```")),
        )
    }

    @Test
    fun `cm-250 style, quotes nest`() {
        assertEquals(listOf("Q[Q \"x\"]"), dump(parse("> > x")))
        assertEquals(listOf("Q[P \"a\" ; Q \"b\"]"), dump(parse("> a\n>\n> > b")))
    }

    @Test
    fun `a quote inside a list item`() {
        assertEquals(
            listOf("LI • d0 first \"item\" {Q \"said\"}"),
            dump(parse("- item\n  > said")),
        )
    }

    @Test
    fun `a table in a quote`() {
        assertEquals(listOf("Q[T a|b / None,None / 1|2]"), dump(parse("> | a | b |\n> |---|---|\n> | 1 | 2 |")))
    }

    @Test
    fun `nested block offsets are absolute in the raw text`() {
        val text = "> - a\n> - b"
        val quote = parse(text).single() as Block.Quote
        val b = quote.children[1] as Block.ListItem
        assertEquals(text.indexOf("- b"), b.start)
        assertEquals(text.length, b.end)
    }

    @Test
    fun `an open fence in a list item while streaming is open and not highlighted`() {
        val blocks = parse("1. Run\n   ```sh\n   npm te", streaming = true)
        val code = (blocks.single() as Block.ListItem).children.single() as Block.Code
        assertTrue(code.open)
        assertEquals("npm te", code.text)
    }

    @Test
    fun `a wrapped item line is one paragraph`() {
        assertEquals(
            listOf("LI • d0 first \"a\\nwrapped\"", "LI • d0 \"b\""),
            dump(parse("- a\n wrapped\n- b")),
        )
    }
}
