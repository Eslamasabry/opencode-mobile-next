package io.github.eslamasabry.oc.engine.media.preview

import io.github.eslamasabry.oc.core.common.MediaProblem
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Port of `test/common_file_preview_test.dart:74,102`. */
class FilePreviewTest {
    @Test
    fun `B-MEDIA-001 eligible byte-only text decodes strictly and Save keeps the original bytes`() {
        val bytes = "a,b\r\n1,2\r\n".encodeToByteArray()
        val data = PreviewData("data.csv", null, bytes = bytes)
        assertEquals("a,b\r\n1,2\r\n", data.text)
        assertSame(bytes, data.exportBytes)
        assertNull(PreviewData("data.csv", null, bytes = byteArrayOf(0xFF.toByte())).text)
        assertNull(PreviewData("data.csv", null, bytes = byteArrayOf(0)).text)
        assertNull(PreviewData("data.csv", "application/pdf", bytes = bytes).text)
    }

    @Test
    fun `B-MEDIA-001 invalid UTF-8 data URL keeps Save bytes without replacement text`() {
        val data = PreviewData.fromDataUrl("data.csv", "text/csv", "data:text/csv;base64,/w==")
        assertNull(data.text)
        assertContentEquals(byteArrayOf(0xFF.toByte()), data.exportBytes)
        assertNull(data.problem)
    }

    @Test
    fun `B-MEDIA-001 strict decoding refuses overlong forms, surrogates and truncated sequences`() {
        val bad =
            listOf(
                byteArrayOf(0xC0.toByte(), 0xAF.toByte()), // overlong '/'
                byteArrayOf(0xED.toByte(), 0xA0.toByte(), 0x80.toByte()), // UTF-16 surrogate
                byteArrayOf(0xE2.toByte(), 0x82.toByte()), // cut short
                byteArrayOf(0xF4.toByte(), 0x90.toByte(), 0x80.toByte(), 0x80.toByte()), // above U+10FFFF
            )
        for (b in bad) assertFalse(StrictUtf8.isValid(b))
        assertEquals("مرحبا 👋", StrictUtf8.decode("مرحبا 👋".encodeToByteArray()))
        assertEquals(4L + 10 + 1, StrictUtf8.encodedLength("👋مرحبا "))
    }

    @Test
    fun `B-MEDIA-001 missing and remote attachments say so instead of showing nothing`() {
        assertEquals(MediaProblem.AttachmentContentMissing, PreviewData.fromDataUrl("a.txt", "text/plain", " ").problem)
        assertEquals(MediaProblem.RemoteAttachmentPreview, PreviewData.fromDataUrl("a.txt", null, "https://example.org/a").problem)
        assertEquals(MediaProblem.FileNotShowable, PreviewData.fromDataUrl("a.txt", null, "data:text/plain;base64,%%%").problem)
        val plain = PreviewData.fromDataUrl("a.txt", null, "data:text/plain,a%20b")
        assertEquals("a b", plain.text)
        assertEquals("text/plain", plain.mime)
    }

    @Test
    fun `B-MEDIA-001 the default preview keeps the bytes and long text is capped without splitting a pair`() =
        runTest {
            val bytes = "x".encodeToByteArray()
            val data = DefaultFilePreview.prepare("a.txt", "text/plain; charset=utf-8", bytes, null)
            assertEquals("text/plain", data.mime)
            assertSame(bytes, data.exportBytes)
            val text = "a".repeat(Truncate.MAX_SOURCE_CHARS - 1) + "👋"
            val capped = Truncate.cap(text)
            assertTrue(capped.cut)
            assertEquals(Truncate.MAX_SOURCE_CHARS - 1, capped.text.length)
            assertFalse(Truncate.cap("short").cut)
        }
}
