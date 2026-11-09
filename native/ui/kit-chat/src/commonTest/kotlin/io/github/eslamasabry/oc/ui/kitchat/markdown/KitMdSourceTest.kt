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
        assertEquals("b:m/p:0#0", b.single().key, "keys are per row and position")
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
    fun `B-CHAT-059 a server picture inside a sentence keeps its words and opens the viewer`() {
        val b = KitMdSource.blocks("r", "See ![Screenshot](/tmp/result.png) and **this** here", false)
        val inl = assertIs<KitMdBlock.Prose>(b.single()).inlines
        assertEquals("See ▣ Screenshot and this here", inl.text)
        val pic = inl.spans.single { it.kind is KitSpanKind.Picture }
        assertEquals(KitSpanKind.Picture("/tmp/result.png", "Screenshot"), pic.kind)
        assertEquals("▣ Screenshot", inl.text.substring(pic.start, pic.end))
        val bold = inl.spans.single { it.kind == KitSpanKind.Bold }
        assertEquals("this", inl.text.substring(bold.start, bold.end), "spans after the picture move with its words")
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

    @Test
    fun `B-CHAT-101 closed choices, checklist and command fences become agent blocks, not plain code`() {
        val choices = KitMdSource.blocks("r", "```choices\n- Yes, do it\n2. Not now\n```", false)
        val c = assertIs<KitMdBlock.Agent>(choices.single())
        assertEquals(KitAgentKind.Choices, c.kind)
        assertEquals(listOf("Yes, do it", "Not now"), c.items.map { it.text })
        val list = assertIs<KitMdBlock.Agent>(KitMdSource.blocks("r", "```checklist\n[x] one\n[ ] two\n```", false).single())
        assertEquals(listOf(true, false), list.items.map { it.done })
        val cmd = assertIs<KitMdBlock.Agent>(KitMdSource.blocks("r", "```command\nls -la\n```", false).single())
        assertEquals(KitAgentKind.Command, cmd.kind)
        assertIs<KitMdBlock.Code>(KitMdSource.blocks("r", "```choices\n- unfinished", true).single(), "an open fence stays plain")
    }
}

class KitSentFilesTest {
    @Test
    fun `B-CHAT-130 a picture's data address gives its bytes and anything else gives none`() {
        val bytes = io.github.eslamasabry.oc.ui.kitchat.transcript.dataUrlBytes("data:image/png;base64,AQID")
        assertEquals(listOf<Byte>(1, 2, 3), bytes?.toList())
        assertNull(io.github.eslamasabry.oc.ui.kitchat.transcript.dataUrlBytes("file:///tmp/a.png"))
        assertNull(io.github.eslamasabry.oc.ui.kitchat.transcript.dataUrlBytes("data:image/png,raw"))
    }
}
