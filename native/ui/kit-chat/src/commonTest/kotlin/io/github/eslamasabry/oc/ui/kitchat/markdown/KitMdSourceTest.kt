package io.github.eslamasabry.oc.ui.kitchat.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** P12-CHAT-FEEL: a transcript row's source becomes the kit's Markdown model (B-CHAT-059, B-CHAT-064). */
class KitMdSourceTest {
    @Test
    fun `B-CHAT-064 prose loses its marks and keeps links and path candidates as spans`() {
        val b = KitMdSource.blocks("b:m/p:0", "Read **this** at [docs](https://example.com) or `lib/main.dart:12`", false)
        val prose = assertIs<KitMdBlock.Prose>(b.single())
        assertEquals("Read this at docs or lib/main.dart:12", prose.inlines.text)
        assertTrue(prose.inlines.spans.any { it.kind == KitSpanKind.Link("https://example.com") })
        assertTrue(prose.inlines.spans.any { it.kind == KitSpanKind.Path("lib/main.dart", 12) })
        assertTrue(prose.inlines.spans.any { it.kind == KitSpanKind.Bold })
        assertTrue(b.single().key.startsWith("b:m/p:0"), "keys stay unique per row")
    }

    @Test
    fun `B-CHAT-059 a line of pictures becomes tiles that tell web from server files and never show an id`() {
        val b = KitMdSource.blocks("r", "![chart](https://example.com/c.png) ![](out/plot.png) ![image](079f63375d36aabbccddeeff00112233.png)", false)
        val pics = assertIs<KitMdBlock.Pictures>(b.single()).pictures
        assertEquals("chart", pics[0].label)
        assertEquals("https://example.com/c.png", pics[0].url)
        assertEquals("example.com", pics[0].host)
        assertEquals("plot.png", pics[1].label)
        assertEquals("./out/plot.png".removePrefix("./"), pics[1].path?.removePrefix("./"))
        assertNull(pics.getOrNull(2)?.label, "a content hash names nothing")
    }

    @Test
    fun `B-CHAT-060 headings, list items, quotes and code keep their kinds`() {
        assertIs<KitMdBlock.Heading>(KitMdSource.blocks("r", "## Title", false).single())
        assertIs<KitMdBlock.ListItem>(KitMdSource.blocks("r", "- item", false).single())
        assertIs<KitMdBlock.Quote>(KitMdSource.blocks("r", "> said", false).single())
        val code = assertIs<KitMdBlock.Code>(KitMdSource.blocks("r", "```kotlin\nval x = 1\n```", false).single())
        assertEquals("kotlin", code.info)
        assertEquals("val x = 1", code.text.trimEnd())
        assertTrue(KitMdSource.blocks("r", "", false).isEmpty())
    }
}
