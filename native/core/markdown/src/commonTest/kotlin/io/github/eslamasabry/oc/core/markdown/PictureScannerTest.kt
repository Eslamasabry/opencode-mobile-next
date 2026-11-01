package io.github.eslamasabry.oc.core.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ported from the scanner group of test/kit/kit_markdown_image_test.dart. */
class PictureScannerTest {
    private fun one(source: String): MarkdownPicture {
        val pictures = PictureScanner.scan(source).mapNotNull { it.picture }
        assertEquals(1, pictures.size, "pictures in \"$source\"")
        return pictures.single()
    }

    @Test
    fun `B-CHAT-059 a picture is a picture, a link is still a link`() {
        val picture = one("![Image](/tmp/shot.png)")
        assertEquals("Image", picture.alt)
        assertEquals("/tmp/shot.png", picture.target)
        assertEquals(PictureKind.File, picture.kind)
        assertEquals("/tmp/shot.png", picture.path)
        assertFalse(picture.hasDescription)
        assertEquals(listOf(PicturePart("[Image](/tmp/shot.png)", null)), PictureScanner.scan("[Image](/tmp/shot.png)"))
    }

    @Test
    fun `B-CHAT-059 a stored attachment id is never shown as a name`() {
        val id = "78879b2a53e0a38ed447ab2a7416b15cb2b7a0d1e5f6a7b8c9d0e1f2a3b4c5d6"
        val picture = one("![Image]($id)")
        assertEquals(PictureKind.Inert, picture.kind)
        assertNull(picture.path)
        assertNull(picture.fileName)
        assertEquals(PictureKind.File, one("![Image](abc123.png)").kind)
        assertFalse(one("![a](/tmp/079f63375d36a1b2c3d4e5f60718293a.png)").hasMeaningfulName)
        assertTrue(one("![a](/tmp/home.png)").hasMeaningfulName)
    }

    @Test
    fun `B-CHAT-059 a bang before something that is not a picture stays text`() {
        for (text in listOf(
            "Wow! [link](https://example.com)",
            "Done!",
            "!important",
            "a ! [b](c)",
            "! [b](c)",
            "![ not closed",
            "![alt] (/tmp/a.png)",
            "![alt]",
            "![alt](/tmp/a.png",
            "Use ![ as the prefix",
        )) {
            val parts = PictureScanner.scan(text)
            assertTrue(parts.all { it.picture == null }, "\"$text\" is text")
            assertEquals(text, parts.joinToString("") { it.text!! })
        }
    }

    @Test
    fun `B-CHAT-059 brackets inside the description do not end it early`() {
        val picture = one("![a [b] c](/tmp/x.png)")
        assertEquals("a [b] c", picture.alt)
        assertEquals("/tmp/x.png", picture.target)
        assertEquals("/tmp/shot_(1).png", one("![x](/tmp/shot_(1).png)").target)
        assertEquals("""a \] b""", one("""![a \] b](/tmp/x.png)""").alt)
    }

    @Test
    fun `B-CHAT-059 several pictures on a line, with the words between them`() {
        val parts = PictureScanner.scan("One ![a](/tmp/a.png) two ![b](https://example.com/b.png) three")
        assertEquals(listOf("One ", "<img>", " two ", "<img>", " three"), parts.map { it.text ?: "<img>" })
        assertEquals("a", parts[1].picture!!.alt)
        assertEquals(PictureKind.Web, parts[3].picture!!.kind)
    }

    @Test
    fun `B-CHAT-059 the description is trimmed and empty or Image says nothing`() {
        assertEquals("Home screen", one("![  Home screen  ](/tmp/a.png)").alt)
        assertTrue(one("![  Home screen  ](/tmp/a.png)").hasDescription)
        assertFalse(one("![](/tmp/a.png)").hasDescription)
        assertFalse(one("![Image](/tmp/a.png)").hasDescription)
        assertFalse(one("![image](/tmp/a.png)").hasDescription)
        assertTrue(one("![Images of it](/tmp/a.png)").hasDescription)
    }

    @Test
    fun `B-CHAT-059 a path on the server, a web address, or neither`() {
        assertEquals("/tmp/shots/a.png", one("![a](/tmp/shots/a.png)").path)
        assertEquals("shots/a.png", one("![a](shots/a.png)").path)
        assertEquals("./a.png", one("![a](a.png)").path)
        assertEquals("/tmp/a b.png", one("![a](file:///tmp/a%20b.png)").path)
        assertEquals("/tmp/my shot.png", one("![a](</tmp/my shot.png>)").path)
        assertEquals("/tmp/my shot.png", one("![a](/tmp/my%20shot.png)").path)
        assertEquals("/tmp/a.png", one("![a](/tmp/a.png \"A title\")").path)
        assertEquals("a.PNG", one("![a](/tmp/a.PNG)").fileName)

        val https = one("![a](https://cdn.example.com:8443/a.png)")
        assertEquals(PictureKind.Web, https.kind)
        assertEquals("https://cdn.example.com:8443/a.png", https.url)
        assertEquals("cdn.example.com:8443", https.host)
        assertNull(https.path)
        assertEquals(PictureKind.Web, one("![a](http://example.com/a.png)").kind)
        assertEquals("example.com", one("![a](https://example.com:443/a.png)").host)

        for (target in listOf(
            "data:image/png;base64,iVBORw0KGgo=",
            "javascript:alert(1)",
            "intent://scan/#Intent;scheme=zxing;end",
            "content://media/external/images/1",
            "file://evil.example.com/share/a.png",
            "//cdn.example.com/a.png",
            """C:\Users\me\a.png""",
            "/tmp/a.png?raw=1",
            "notes/readme",
            "",
        )) {
            val picture = one("![alt]($target)")
            assertEquals(PictureKind.Inert, picture.kind, target)
            assertNull(picture.path, target)
            assertNull(picture.url, target)
        }
    }

    @Test
    fun `B-CHAT-059 a picture inside a link opens the link, never the file twice`() {
        val badge = one("[![build](https://img.example.com/b.svg)](https://example.com/ci)")
        assertEquals("build", badge.alt)
        assertEquals(PictureKind.Web, badge.kind)
        assertEquals("https://example.com/ci", badge.url)
        assertEquals("example.com", badge.host)
        assertEquals(PictureKind.File, one("[![shot](/tmp/s.png)](https://example.com/pr/1)").kind)
    }

    @Test
    fun `B-CHAT-059 inline code is code, not a picture`() {
        assertNull(PictureScanner.scan("Write `![alt](/tmp/a.png)` to embed it.").single().picture)
    }

    @Test
    fun `B-CHAT-059 only - a line that holds nothing but pictures`() {
        assertEquals(2, PictureScanner.only("  ![a](/tmp/a.png)  ![b](/tmp/b.png) ")!!.size)
        assertNull(PictureScanner.only("see ![a](/tmp/a.png)"))
        assertNull(PictureScanner.only("![a](/tmp/a.png) see"))
        assertNull(PictureScanner.only("plain"))
        assertNull(PictureScanner.only("[a](/tmp/a.png)"))
    }

    @Test
    fun `B-CHAT-059 speech, measuring and copy read the description, not the target`() {
        assertEquals("Look Chart here", PictureScanner.withAltText("Look ![Chart](/tmp/c.png) here"))
        assertEquals(
            "The chart:\nRevenue by month\nDone.",
            proseForSpeech("The chart:\n![Revenue by month](https://example.com/r.png)\nDone."),
        )
        val three = proseForSpeech("![Image](/tmp/a.png)\n![Image](/tmp/b.png)\n![](/tmp/c.png)")
        assertEquals("Image\nImage", three)
        assertFalse(three.contains("/tmp"))
        assertFalse(three.contains("!"))
        assertFalse(three.contains("]("))
        assertEquals("See the docs.", proseForSpeech("See [the docs](https://example.com)."))
    }

    @Test
    fun `B-CHAT-059 a picture still being written is held back`() {
        for (partial in listOf(
            "Here:\n![",
            "![Ima",
            "![Image]",
            "![Image](",
            "![Image](/tmp/sho",
            "![Image](/tmp/a.png \"Ti",
        )) {
            val last = partial.split('\n').last()
            assertEquals("", PictureScanner.withoutUnfinished(last), "\"$last\" is unfinished")
        }
        assertEquals("![a](/tmp/a.png) and ", PictureScanner.withoutUnfinished("![a](/tmp/a.png) and ![b](/tm"))
        for (done in listOf("![a](/tmp/a.png)", "Great!", "x ![a](/tmp/a.png) y", "no picture")) {
            assertEquals(done, PictureScanner.withoutUnfinished(done))
        }
    }

    @Test
    fun `B-CHAT-059 consecutive picture lines are one group, deduplicated, never raw syntax`() {
        val blocks =
            parse("Shots:\n\n![Home](/tmp/home.png)\n\n![Home again](/tmp/home.png)\n![Settings](/tmp/settings.png)\n\nDone.")
        assertEquals(listOf("P \"Shots:\"", "IMG /tmp/home.png,/tmp/settings.png", "P \"Done.\""), dump(blocks))
        assertFalse(drawn(blocks).contains("!["))
    }

    @Test
    fun `B-CHAT-059 a picture inside a sentence is one placeholder run, the words around it parse`() {
        val inlines = InlineParser.parse("See **this** ![Chart](/tmp/c.png) now")
        assertEquals("See this \uFFFC now", inlines.text)
        val picture = inlines.spans.single { it.kind is SpanKind.Picture }
        assertEquals("\uFFFC", inlines.text.substring(picture.start, picture.end))
        assertEquals("Chart", (picture.kind as SpanKind.Picture).picture.alt)
        assertNotNull(inlines.spans.singleOrNull { it.kind == SpanKind.Bold })
    }

    @Test
    fun `B-CHAT-059 a half-written picture on the last line shows nothing`() {
        assertEquals(listOf("P \"Here:\""), dump(parse("Here:\n![Image](/tmp/sho", streaming = true)))
        assertEquals(listOf("P \"Here:\""), dump(parse("Here:\n![Image](/tmp/sho")))
    }

    @Test
    fun `B-CHAT-059 only drawable formats are thumbnails`() {
        assertTrue(one("![a](/tmp/a.png)").drawable)
        assertTrue(one("![a](/tmp/a.JPG)").drawable)
        assertFalse(one("![a](/tmp/a.svg)").drawable)
        assertFalse(one("![a](/tmp/a.heic)").drawable)
    }
}
