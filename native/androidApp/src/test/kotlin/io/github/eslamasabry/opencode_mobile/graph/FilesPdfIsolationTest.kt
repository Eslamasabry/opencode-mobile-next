package io.github.eslamasabry.opencode_mobile.graph

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Codex files #3 (plan 10 §11.6 S4, B-MEDIA-004): untrusted PDFs are never parsed in the app's own process. */
class FilesPdfIsolationTest {
    private fun source(path: String): String = File(path).readText()

    @Test
    fun `B-MEDIA-004 the app process never opens a PdfRenderer, the isolated service does`() {
        val wiring = source("src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/FilesWiring.kt")
        assertFalse(Regex("""\bPdfRenderer\s*\(""").containsMatchIn(wiring), "FilesWiring parses PDFs in-process")
        assertTrue("bindIsolatedService" in wiring)
        val manifest = source("src/main/AndroidManifest.xml")
        val service = Regex("""<service\s+android:name="\.LocalPdfService"[^>]*>""").find(manifest)?.value
        assertTrue(service != null, "LocalPdfService is not declared")
        assertTrue("""android:isolatedProcess="true"""" in service)
        assertTrue("""android:exported="false"""" in service)
        assertTrue("""android:process=":local_pdf"""" in service)
    }

    @Test
    fun `B-MEDIA-004 a reply is accepted only as a PNG of exactly the reported size`() {
        fun png(w: Int, h: Int): ByteArray {
            val b = ByteArray(33)
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A).copyInto(b)
            "IHDR".encodeToByteArray().copyInto(b, 12)
            for (i in 0..3) {
                b[16 + i] = (w shr (24 - 8 * i)).toByte()
                b[20 + i] = (h shr (24 - 8 * i)).toByte()
            }
            return b
        }
        assertTrue(PdfReply.validPng(png(612, 792), 612, 792))
        assertFalse(PdfReply.validPng(png(612, 792), 612, 793))
        assertFalse(PdfReply.validPng("GIF89a-not-a-png-at-all-xx".encodeToByteArray(), 612, 792))
        assertTrue(PdfReply.validSize(3, 1086, 1536))
        assertFalse(PdfReply.validSize(0, 100, 100))
        assertFalse(PdfReply.validSize(1, 1537, 10))
        assertFalse(PdfReply.validSize(1, 1536, 1536)) // over two million pixels
    }
}
