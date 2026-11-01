package io.github.eslamasabry.oc.ui.kit.content.pdf

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The PDF budgets of plan 10 §11.6 (B-MEDIA-004; Dart `LocalPdf.maxBytes`/`maxPages`). Pure: the rasterizer applies
 * [scaleFor] and the client checks every reply with [checkReply] before a single byte is decoded.
 */
public object PdfLimits {
    public const val MAX_INPUT_BYTES: Long = 10L * 1024 * 1024
    public const val MAX_OUTPUT_BYTES: Int = 10 * 1024 * 1024
    public const val MAX_PAGES: Int = 200
    public const val MAX_SIDE_PX: Int = 1536
    public const val MAX_PIXELS: Int = 2_000_000

    private val PNG_SIGNATURE = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)

    /** Pixels per PDF point for a page of [widthPt] × [heightPt]: longest side ≤ 1,536 px and area ≤ 2 million px. */
    public fun scaleFor(widthPt: Int, heightPt: Int): Float {
        require(widthPt > 0 && heightPt > 0) { "page size must be positive" }
        return min(MAX_SIDE_PX.toFloat() / max(widthPt, heightPt), sqrt(MAX_PIXELS.toFloat() / (widthPt.toFloat() * heightPt)))
    }

    /** Pages the viewer offers: the document's count, never past [MAX_PAGES] ("Page 1 of 200" for 201 pages). */
    public fun shownPages(pageCount: Int): Int = pageCount.coerceIn(0, MAX_PAGES)

    /** Before anything is sent to the renderer. */
    public fun checkRequest(inputBytes: Long, page: Int): PdfCheck =
        when {
            inputBytes <= 0 -> PdfCheck.Invalid
            inputBytes > MAX_INPUT_BYTES -> PdfCheck.TooLarge
            page !in 0 until MAX_PAGES -> PdfCheck.TooLarge
            else -> PdfCheck.Ok
        }

    /**
     * A renderer reply is trusted only when the PNG is within budget, starts with the PNG signature, its IHDR width and
     * height equal the reported ones (and those are within budget), and it is the page that was asked for.
     */
    public fun checkReply(png: ByteArray, width: Int, height: Int, page: Int, askedPage: Int, pageCount: Int): PdfCheck {
        if (png.size > MAX_OUTPUT_BYTES) return PdfCheck.TooLarge
        if (page != askedPage || pageCount <= 0 || page !in 0 until pageCount) return PdfCheck.Invalid
        if (width <= 0 || height <= 0 || width > MAX_SIDE_PX || height > MAX_SIDE_PX) return PdfCheck.Invalid
        if (width.toLong() * height > MAX_PIXELS + MAX_SIDE_PX) return PdfCheck.Invalid // one row of rounding slack
        if (png.size < 24) return PdfCheck.Invalid
        for (i in PNG_SIGNATURE.indices) if (png[i] != PNG_SIGNATURE[i]) return PdfCheck.Invalid
        // IHDR is the first chunk: length (4) + "IHDR" (4), then width and height big-endian at offsets 16 and 20.
        if (png[12] != 'I'.code.toByte() || png[13] != 'H'.code.toByte() || png[14] != 'D'.code.toByte() ||
            png[15] != 'R'.code.toByte()
        ) return PdfCheck.Invalid
        if (uint32(png, 16) != width.toLong() || uint32(png, 20) != height.toLong()) return PdfCheck.Invalid
        return PdfCheck.Ok
    }

    private fun uint32(b: ByteArray, at: Int): Long =
        ((b[at].toLong() and 0xFF) shl 24) or ((b[at + 1].toLong() and 0xFF) shl 16) or
            ((b[at + 2].toLong() and 0xFF) shl 8) or (b[at + 3].toLong() and 0xFF)
}

/** The outcome of a budget check. Words for each live with the host (`filePdfLimit`, `filePdfFailed`). */
public enum class PdfCheck { Ok, TooLarge, Invalid }
