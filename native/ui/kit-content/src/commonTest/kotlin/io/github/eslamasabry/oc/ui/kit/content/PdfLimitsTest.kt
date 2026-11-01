package io.github.eslamasabry.oc.ui.kit.content

import io.github.eslamasabry.oc.ui.kit.content.pdf.PdfCheck
import io.github.eslamasabry.oc.ui.kit.content.pdf.PdfLimits
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** B-MEDIA-004: the PDF input and output budgets reject invalid transport results (plan 10 §11.6). */
class PdfLimitsTest {
    /** The Dart test's 1 × 1 PNG. */
    private val png =
        Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl2ZKgAAAAASUVORK5CYII=")

    @Test
    fun `B-MEDIA-004 input over 10 MiB or a page past 200 is refused before the renderer`() {
        assertEquals(PdfCheck.TooLarge, PdfLimits.checkRequest(PdfLimits.MAX_INPUT_BYTES + 1, 0))
        assertEquals(PdfCheck.Ok, PdfLimits.checkRequest(PdfLimits.MAX_INPUT_BYTES, 0))
        assertEquals(PdfCheck.TooLarge, PdfLimits.checkRequest(100, PdfLimits.MAX_PAGES))
        assertEquals(PdfCheck.Invalid, PdfLimits.checkRequest(0, 0))
    }

    @Test
    fun `B-MEDIA-004 a well-formed reply passes`() {
        assertEquals(PdfCheck.Ok, PdfLimits.checkReply(png, 1, 1, page = 0, askedPage = 0, pageCount = 3))
    }

    @Test
    fun `B-MEDIA-004 a reported size that disagrees with the PNG or the budget is rejected`() {
        assertEquals(PdfCheck.Invalid, PdfLimits.checkReply(png, 2000, 1, page = 0, askedPage = 0, pageCount = 3))
        assertEquals(PdfCheck.Invalid, PdfLimits.checkReply(png, 2, 1, page = 0, askedPage = 0, pageCount = 3))
    }

    @Test
    fun `B-MEDIA-004 a forged IHDR is rejected before decoding`() {
        val forged = png.copyOf()
        // width 100,000 big-endian at offset 16
        forged[16] = 0; forged[17] = 0x01; forged[18] = 0x86.toByte(); forged[19] = 0xA0.toByte()
        assertEquals(PdfCheck.Invalid, PdfLimits.checkReply(forged, 1, 1, page = 0, askedPage = 0, pageCount = 3))
        val notPng = png.copyOf().also { it[0] = 0 }
        assertEquals(PdfCheck.Invalid, PdfLimits.checkReply(notPng, 1, 1, page = 0, askedPage = 0, pageCount = 3))
    }

    @Test
    fun `B-MEDIA-004 a reply for another page or an oversized PNG is rejected`() {
        assertEquals(PdfCheck.Invalid, PdfLimits.checkReply(png, 1, 1, page = 1, askedPage = 0, pageCount = 3))
        assertEquals(PdfCheck.Invalid, PdfLimits.checkReply(png, 1, 1, page = 3, askedPage = 3, pageCount = 3))
        val huge = png.copyOf(PdfLimits.MAX_OUTPUT_BYTES + 1)
        assertEquals(PdfCheck.TooLarge, PdfLimits.checkReply(huge, 1, 1, page = 0, askedPage = 0, pageCount = 3))
    }

    @Test
    fun `B-MEDIA-004 the viewer offers at most 200 pages`() {
        assertEquals(200, PdfLimits.shownPages(201))
        assertEquals(3, PdfLimits.shownPages(3))
    }

    @Test
    fun `MEDIA-VOICE-40 the render scale keeps the long side and the area in budget`() {
        val letter = PdfLimits.scaleFor(612, 792)
        assertTrue(792 * letter <= PdfLimits.MAX_SIDE_PX + 0.5f)
        assertTrue(612 * letter * 792 * letter <= PdfLimits.MAX_PIXELS + 1f)
        val square = PdfLimits.scaleFor(1000, 1000) // area wins: sqrt(2) ≈ 1.414 < 1.536
        assertEquals(1.4142135f, square, 1e-4f)
    }
}
