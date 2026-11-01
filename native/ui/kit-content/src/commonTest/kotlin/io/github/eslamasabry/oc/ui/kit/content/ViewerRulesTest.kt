package io.github.eslamasabry.oc.ui.kit.content

import io.github.eslamasabry.oc.ui.kit.content.viewer.ViewerPaths
import io.github.eslamasabry.oc.ui.kit.content.viewer.ViewerText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The viewer's pure rules: header (B-NAV-028) and text (B-MEDIA-001; plan 10 §11.2, §11.3). */
class ViewerRulesTest {
    @Test
    fun `B-NAV-028 the header shows the parent folder only, with its slash`() {
        assertEquals("docs/", ViewerPaths.folderOf("docs/README.md", "README.md"))
        assertEquals("/repo/docs/", ViewerPaths.folderOf("/repo/docs/a.md", "a.md"))
        assertEquals("C:\\repo\\", ViewerPaths.folderOf("C:\\repo\\a.md", "a.md"))
    }

    @Test
    fun `B-NAV-028 a root file has no folder line, so the name never shows twice`() {
        assertNull(ViewerPaths.folderOf("README.md", "README.md"))
        assertNull(ViewerPaths.folderOf("", "README.md"))
    }

    @Test
    fun `B-NAV-028 what follows the name stays`() {
        assertEquals("src/ · Line 12", ViewerPaths.folderOf("src/a.dart · Line 12", "a.dart"))
        assertEquals("Line 12", ViewerPaths.folderOf("a.dart · Line 12", "a.dart"))
    }

    @Test
    fun `B-NAV-028 a caption that is not the path shows as it is`() {
        assertEquals("MIT License", ViewerPaths.folderOf("MIT License", "Vosk"))
    }

    @Test
    fun `B-MEDIA-001 invalid UTF-8 gives no text, never replacement characters`() {
        val invalid = byteArrayOf(0x68, 0x69, 0xC3.toByte(), 0x28) // "hi" + a broken two-byte sequence
        assertNull(ViewerText.decodeStrict(invalid))
        assertNull(ViewerText.decodeStrict(byteArrayOf(0x61, 0x00, 0x62))) // a NUL means binary
        assertEquals("héllo ✓", ViewerText.decodeStrict("héllo ✓".encodeToByteArray()))
        assertEquals("", ViewerText.decodeStrict(ByteArray(0)))
    }

    @Test
    fun `B-MEDIA-001 a long text is cut for display without splitting a surrogate pair`() {
        val text = "a".repeat(9) + "🙂" + "b".repeat(10) // the emoji's high surrogate sits at index 9
        val cut = ViewerText.cut(text, max = 10)
        assertTrue(cut.truncated)
        assertEquals("a".repeat(9), cut.text)
        val whole = ViewerText.cut("short")
        assertEquals("short", whole.text)
        assertEquals(false, whole.truncated)
    }

    @Test
    fun `MEDIA-VOICE-37 lines split on LF, drop CR, and a trailing terminator adds no line`() {
        assertEquals(listOf("a", "b", ""), ViewerText.lines("a\r\nb\n\n"))
        assertEquals(listOf("a"), ViewerText.lines("a"))
        assertEquals(emptyList(), ViewerText.lines(""))
    }

    @Test
    fun `MEDIA-VOICE-37 sizes read in words`() {
        assertEquals("512 B", ViewerText.sizeInWords(512))
        assertEquals("2.4 MB", ViewerText.sizeInWords((2.4 * 1024 * 1024).toLong()))
        assertEquals("10 KB", ViewerText.sizeInWords(10 * 1024))
        assertEquals("1 KB", ViewerText.sizeInWords(1024))
        assertEquals("1.5 GB", ViewerText.sizeInWords(1024L * 1024 * 1024 * 3 / 2))
        assertNull(ViewerText.sizeInWords(null))
        assertNull(ViewerText.sizeInWords(-1))
    }
}
