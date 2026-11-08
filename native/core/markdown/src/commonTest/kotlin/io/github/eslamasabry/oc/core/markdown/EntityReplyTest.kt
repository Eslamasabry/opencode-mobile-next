package io.github.eslamasabry.oc.core.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** B-108: a character reference such as `&#10;` never reaches the screen literally, in any block, whole or streamed. */
class EntityReplyTest {
    private fun texts(block: Block): List<String> = when (block) {
        is Block.Prose -> listOf(block.inlines.text)
        is Block.Heading -> listOf(block.inlines.text)
        is Block.Quote -> block.children.flatMap(::texts)
        is Block.ListItem -> listOf(block.inlines.text) + block.children.flatMap(::texts)
        is Block.Table -> (block.header + block.rows.flatten()).map { it.text }
        else -> emptyList()
    }

    private val sources = listOf(
        "first&#10;second",
        "# Head&#10;er",
        "- item&#10;two\n- b",
        "> quoted&#10;line",
        "| a | b |\n| --- | --- |\n| x&#10;y | **z&#10;** |",
        "**bold&#10;text** and *it&#10;alic*",
        "[label&#10;](https://example.com)",
        "line one &#10; line two &#x0A; three",
    )

    @Test
    fun `B-108 numeric newline references decode in every block of a finished reply`() {
        for (source in sources) {
            val all = MarkdownBlocks.parse("p", source).flatMap(::texts)
            assertTrue(all.isNotEmpty(), source)
            for (t in all) assertFalse("&#" in t, "$source -> $t")
        }
    }

    @Test
    fun `B-108 and while the reply streams in, at every delta`() {
        for (source in sources) {
            val md = StreamingMarkdown("p")
            for (chunk in source.chunked(1)) {
                md.append(chunk)
                val seen = md.blocks.flatMap(::texts)
                // A reference still arriving ("&#1") may show its start; a complete one never does.
                for (t in seen) assertFalse("&#10;" in t || "&#x0A;" in t, "${md.text} -> $t")
            }
            md.finish()
            assertEquals(MarkdownBlocks.parse("p", source), md.blocks)
        }
    }
}
