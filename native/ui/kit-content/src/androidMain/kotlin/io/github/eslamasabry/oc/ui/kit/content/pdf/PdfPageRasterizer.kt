package io.github.eslamasabry.oc.ui.kit.content.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.IOException
import java.io.OutputStream

/**
 * Renders one PDF page to PNG with the platform `PdfRenderer` (plan 10 §11.6, the service half). It is meant to run
 * inside the isolated `:local_pdf` process: the caller (the `LocalPdfService` in `:androidApp`, S5-VIEWER) owns binding,
 * the descriptors, the 10 s watchdog and killing the process. This class owns only the rendering rules: the scale of
 * [PdfLimits.scaleFor], a white background, `RENDER_MODE_FOR_DISPLAY`, and a PNG that stops at 10 MiB.
 *
 * Never throws for a bad document: every expected failure is a [PdfRaster.Failed] reason.
 */
public object PdfPageRasterizer {
    public fun render(input: ParcelFileDescriptor, page: Int, output: OutputStream): PdfRaster {
        val renderer =
            try {
                PdfRenderer(input)
            } catch (_: SecurityException) {
                return PdfRaster.Failed(PdfRasterFailure.Encrypted)
            } catch (_: IOException) {
                return PdfRaster.Failed(PdfRasterFailure.Invalid)
            } catch (_: IllegalArgumentException) {
                return PdfRaster.Failed(PdfRasterFailure.Invalid)
            }
        return renderer.use { doc ->
            val count = doc.pageCount
            if (page !in 0 until minOf(count, PdfLimits.MAX_PAGES)) return@use PdfRaster.Failed(PdfRasterFailure.PageOutOfRange)
            doc.openPage(page).use { p ->
                if (p.width <= 0 || p.height <= 0) return@use PdfRaster.Failed(PdfRasterFailure.Invalid)
                val scale = PdfLimits.scaleFor(p.width, p.height)
                val w = (p.width * scale).toInt().coerceIn(1, PdfLimits.MAX_SIDE_PX)
                val h = (p.height * scale).toInt().coerceIn(1, PdfLimits.MAX_SIDE_PX)
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                try {
                    bitmap.eraseColor(Color.WHITE)
                    val matrix = Matrix().apply { setScale(w.toFloat() / p.width, h.toFloat() / p.height) }
                    p.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val capped = CappedOutputStream(output, PdfLimits.MAX_OUTPUT_BYTES)
                    // Android's compress catches stream errors and returns false, so the cap is read from the stream.
                    val ok =
                        try {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, capped).also { capped.flush() }
                        } catch (_: IOException) {
                            false
                        }
                    if (capped.overflowed) return@use PdfRaster.Failed(PdfRasterFailure.TooLarge)
                    if (!ok) return@use PdfRaster.Failed(PdfRasterFailure.Invalid)
                    PdfRaster.Page(page = page, pageCount = count, width = w, height = h)
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }
}

/** What one render produced. The PNG bytes went to the output stream; only these numbers travel back. */
public sealed interface PdfRaster {
    public data class Page(val page: Int, val pageCount: Int, val width: Int, val height: Int) : PdfRaster

    public data class Failed(val reason: PdfRasterFailure) : PdfRaster
}

public enum class PdfRasterFailure { Encrypted, Invalid, PageOutOfRange, TooLarge }

/** Stops a PNG at [limit] bytes instead of writing an unbounded image (plan 10 §11.6). */
internal class CappedOutputStream(private val out: OutputStream, private val limit: Int) : OutputStream() {
    class Overflow : IOException("output budget reached")

    var written: Int = 0
        private set
    var overflowed: Boolean = false
        private set

    override fun write(b: Int) {
        if (written + 1 > limit) overflow()
        out.write(b)
        written++
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        if (written + len > limit) overflow()
        out.write(b, off, len)
        written += len
    }

    override fun flush(): Unit = out.flush()

    private fun overflow(): Nothing {
        overflowed = true
        throw Overflow()
    }
}
