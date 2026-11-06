package io.github.eslamasabry.oc.core.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** Nested lists and quotes fed character by character: every delta equals a one-shot parse of the text so far. */
class NestedStreamingTest {
    private val corpus =
        listOf(
            "1. Install\n   - npm\n   - yarn\n2. Run\n   ```sh\n   npm test\n   ```\n   - dev\n3. Ship\n",
            "- a\n  - b\n    ```\n    x\n    ```\n- c\n\nafter",
            "> Note:\n> - a\n> - b\n>\n> ```js\n> let x\n> ```\n\nend",
            "> > deep **bold**\n> back\n\n1. one\n\n   more\n\n2. two",
            "- item\n  > said *this*\n- | a | b |\n  |---|---|\n  | 1 | 2 |\n",
        )

    @Test
    fun `a nested reply streamed one character at a time equals the one-shot parse at every delta`() {
        for (text in corpus) {
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

    @Test
    fun `settled blocks before a growing nested list stay the same instances`() {
        val md = StreamingMarkdown("p")
        md.append("# Title\n\nIntro text.\n\n1. Step\n   - sub")
        val heading = md.blocks[0]
        val prose = md.blocks[1]
        md.append(" more words\n   - second")
        md.append(" sub\n2. Next")
        assertSame(heading, md.blocks[0])
        assertSame(prose, md.blocks[1])
        assertEquals(
            listOf("LI 1. d0 first \"Step\"", "LI • d1 \"sub more words\"", "LI • d1 \"second sub\"", "LI 2. d0 \"Next\""),
            dump(md.blocks.drop(2)),
        )
    }
}
