package io.github.eslamasabry.oc.ui.kit.content

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.decoder.Decoder
import com.google.zxing.qrcode.QRCodeReader
import io.github.eslamasabry.oc.ui.kit.content.qr.QrMatrix
import io.github.eslamasabry.oc.ui.kit.content.qr.QrRules

/** ZXing core 3.5.4 as the independent judge of every code (plan 10 §14.1 readability test). */
object QrDecode {
    /**
     * PURE_BARCODE: the image is an exact, unrotated render, so ZXing reads the modules without its camera-style finder
     * search. (That search misses a few large synthetic renders — versions 22, 26 and 32 in a sweep — even though the
     * symbols decode; scanning with a camera is checked on the emulator, plan 10 §20.5.)
     */
    private val hints = mapOf(DecodeHintType.CHARACTER_SET to "UTF-8", DecodeHintType.PURE_BARCODE to true)

    /** Decodes the bare module grid, no image at all: the encoder's own correctness. */
    fun modules(m: QrMatrix): String {
        val bits = BitMatrix(m.size)
        for (r in 0 until m.size) for (c in 0 until m.size) if (m.isDark(r, c)) bits.set(c, r)
        return Decoder().decode(bits, hints).text
    }

    /** Renders [m] with its quiet zone at [modulePx] pixels per module and decodes it. */
    fun matrix(m: QrMatrix, modulePx: Int = 4): String {
        val total = m.size + QrRules.QUIET_MODULES * 2
        val side = total * modulePx
        val pixels = IntArray(side * side) { 0xFFFFFFFF.toInt() }
        for (row in 0 until m.size) for (col in 0 until m.size) {
            if (!m.isDark(row, col)) continue
            for (dy in 0 until modulePx) for (dx in 0 until modulePx) {
                val y = (row + QrRules.QUIET_MODULES) * modulePx + dy
                val x = (col + QrRules.QUIET_MODULES) * modulePx + dx
                pixels[y * side + x] = 0xFF000000.toInt()
            }
        }
        return decode(RGBLuminanceSource(side, side, pixels))
    }

    /** A screen capture: the camera-style search first (the card, its corners and border included), then pure. */
    fun argb(pixels: IntArray, width: Int, height: Int): String {
        val source = RGBLuminanceSource(width, height, pixels)
        return runCatching { decode(source, cameraHints) }.getOrElse { decode(source, hints) }
    }

    private val cameraHints = mapOf(DecodeHintType.CHARACTER_SET to "UTF-8", DecodeHintType.TRY_HARDER to true)

    private fun decode(source: LuminanceSource, with: Map<DecodeHintType, Any> = hints): String =
        QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), with).text
}
