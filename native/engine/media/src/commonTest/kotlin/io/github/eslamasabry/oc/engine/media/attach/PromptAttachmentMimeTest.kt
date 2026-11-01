package io.github.eslamasabry.oc.engine.media.attach

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Port of `test/chat_attachment_reader_test.dart` (MIME gate and bounded reads). */
class PromptAttachmentMimeTest {
    private val text = "hello".encodeToByteArray()
    private val binary = byteArrayOf(0, 1, 2, 0xFF.toByte())

    @Test
    fun `B-CHAT-009 Markdown and HTML are sent as text-plain`() {
        assertEquals("text/plain", PromptAttachmentMime.of("text/markdown", "notes.md", text))
        assertEquals("text/plain", PromptAttachmentMime.of("application/octet-stream", "report.html", text))
        assertEquals("text/plain", PromptAttachmentMime.of("application/vnd.api+json; charset=utf-8", "a", text))
    }

    @Test
    fun `B-CHAT-009 Unicode source and configuration stay readable, corrupt text is refused`() {
        val bytes = "title: \"مرحبا 👋\"\n".encodeToByteArray()
        for (name in listOf("settings.yaml", "app.kt", "Dockerfile")) {
            assertEquals("text/plain", PromptAttachmentMime.of(null, name, bytes), name)
        }
        assertNull(PromptAttachmentMime.of(null, "corrupt.txt", byteArrayOf(0xC3.toByte(), 0x28)))
    }

    @Test
    fun `B-CHAT-009 generic textual bytes are detected and unknown binary is rejected`() {
        assertEquals("text/plain", PromptAttachmentMime.of("application/octet-stream", "README", text))
        assertNull(PromptAttachmentMime.of("application/octet-stream", "archive.bin", binary))
        assertNull(PromptAttachmentMime.of("application/octet-stream", "disguised.txt", binary))
        assertNull(PromptAttachmentMime.of("text/plain", "declared.txt", binary))
        assertNull(PromptAttachmentMime.of("application/json", "declared.json", binary))
        // Control characters other than tab, LF, VT, FF and CR are not text.
        assertNull(PromptAttachmentMime.of(null, "bell.txt", byteArrayOf(0x41, 0x07)))
        assertEquals("text/plain", PromptAttachmentMime.of(null, "ff.txt", byteArrayOf(0x41, 0x09, 0x0A, 0x0B, 0x0C, 0x0D)))
    }

    @Test
    fun `B-CHAT-009 supported images and PDFs keep their types, other images are refused`() {
        assertEquals("image/jpeg", PromptAttachmentMime.of(null, "photo.jpg", byteArrayOf(0, 1)))
        assertEquals("image/jpeg", PromptAttachmentMime.of("image/jpg", "photo", byteArrayOf(0, 1)))
        assertEquals("application/pdf", PromptAttachmentMime.of(null, "document.pdf", byteArrayOf(0, 1)))
        assertNull(PromptAttachmentMime.of("image/bmp", "legacy.bmp", byteArrayOf(0x42, 0x4D, 0, 1)))
        assertNull(PromptAttachmentMime.of("image/bmp", "misnamed.png", byteArrayOf(0x42, 0x4D, 0, 1)))
    }

    @Test
    fun `B-CHAT-009 file names keep only a clean base name`() {
        assertEquals("report.csv", AttachmentName.clean("/storage/emulated/0/report.csv"))
        assertEquals("a_b_.txt", AttachmentName.clean("a\u0001b\".txt"))
        assertEquals("file", AttachmentName.clean(""))
        assertEquals("file", AttachmentName.clean(null))
    }

    private class Chunks(private val chunks: List<ByteArray>) : ByteSource {
        var index = 0
        var requested = 0L

        override suspend fun read(buffer: ByteArray): Int {
            requested += buffer.size
            val chunk = chunks.getOrNull(index++) ?: return -1
            val n = minOf(chunk.size, buffer.size)
            chunk.copyInto(buffer, 0, 0, n)
            return n
        }
    }

    @Test
    fun `B-CHAT-010 a streamed attachment at the exact byte limit is read`() =
        runTest {
            val bytes = BoundedRead.read(Chunks(listOf(byteArrayOf(1, 2), byteArrayOf(3, 4, 5))), limit = 5)
            assertContentEquals(byteArrayOf(1, 2, 3, 4, 5), bytes)
        }

    @Test
    fun `B-CHAT-010 a growing stream stops after the overflow detection byte`() =
        runTest {
            val source = Chunks(listOf(byteArrayOf(1, 2, 3), byteArrayOf(4, 5, 6), ByteArray(1024 * 1024) { 7 }))
            assertNull(BoundedRead.read(source, limit = 5))
            assertEquals(2, source.index) // the megabyte chunk was never requested
        }

    @Test
    fun `B-CHAT-010 stale metadata is not trusted and oversized picker data is rejected without copying`() =
        runTest {
            // The picker said 3 bytes; the file now has 6.
            assertNull(BoundedRead.read(Chunks(listOf(byteArrayOf(1, 2, 3, 4, 5, 6))), limit = 5))
            assertNull(BoundedRead.check(byteArrayOf(1, 2, 3), limit = 2))
            val ok = byteArrayOf(1, 2)
            kotlin.test.assertSame(ok, BoundedRead.check(ok, limit = 2))
        }

    @Test
    fun `B-CHAT-010 data URL byte length comes from the base64 length and padding`() {
        assertEquals(5, DataUrl.decodedLength(DataUrl.encode("text/plain", "hello".encodeToByteArray())))
        assertEquals(4, DataUrl.decodedLength(DataUrl.encode("text/plain", "abcd".encodeToByteArray())))
        assertEquals(3, DataUrl.decodedLength("data:text/plain,abc"))
        assertEquals(0, DataUrl.decodedLength("https://example.org/a"))
    }
}
