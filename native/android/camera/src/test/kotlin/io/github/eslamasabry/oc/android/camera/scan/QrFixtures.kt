package io.github.eslamasabry.oc.android.camera.scan

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import javax.imageio.ImageIO

/** A grey camera frame (the Y plane of a YUV_420_888 image, tightly packed) read from a PNG fixture. */
class LumaFrame(val width: Int, val height: Int, val luma: ByteArray, val crop: CropRect = CropRect(0, 0, width, height)) {
    fun withCrop(rect: CropRect) = LumaFrame(width, height, luma, rect)
}

object QrFixtures {
    const val PAIRING =
        """{"urls":["http://127.0.0.1:49374"],"username":"opencode","password":"fixture-not-a-live-serve-password-000000000"}"""
    const val PAIRING_TWO_ADDRESSES =
        """{"urls":["http://127.0.0.1:4097","http://192.0.2.20:4097"],"username":"opencode","password":"fixture-not-a-live-serve-password-000000000"}"""
    const val URL = "https://example.com"
    const val WIFI = "WIFI:S:home;T:WPA;P:hunter2;;"

    fun frame(name: String): LumaFrame {
        val stream = checkNotNull(QrFixtures::class.java.getResourceAsStream("/qr/$name")) { "missing fixture $name" }
        val image = stream.use { ImageIO.read(it) }
        val luma = ByteArray(image.width * image.height)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val rgb = image.getRGB(x, y)
                val r = rgb shr 16 and 0xFF
                val g = rgb shr 8 and 0xFF
                val b = rgb and 0xFF
                luma[y * image.width + x] = ((r * 299 + g * 587 + b * 114) / 1000).toByte()
            }
        }
        return LumaFrame(image.width, image.height, luma)
    }
}

/**
 * The host-JVM stand-in for zxing-cpp: ZXing core with the same [QrScanOptions] meaning (QR only; tryInvert →
 * ALSO_INVERTED; tryHarder → TRY_HARDER). It decodes only the frame's crop, as zxing-cpp does with ImageProxy.cropRect.
 */
class ZxingCoreDecoder(private val options: QrScanOptions = QrScanOptions.Pairing) : QrFrameDecoder<LumaFrame> {
    var calls = 0
        private set

    override fun decode(frame: LumaFrame): String? {
        calls++
        val c = frame.crop
        val source = PlanarYUVLuminanceSource(frame.luma, frame.width, frame.height, c.left, c.top, c.width, c.height, false)
        val hints = buildMap<DecodeHintType, Any> {
            put(DecodeHintType.POSSIBLE_FORMATS, listOf(BarcodeFormat.QR_CODE))
            put(DecodeHintType.CHARACTER_SET, "UTF-8")
            if (options.tryHarder) put(DecodeHintType.TRY_HARDER, true)
        }
        val reader = QRCodeReader()
        fun read(src: com.google.zxing.LuminanceSource): String? =
            try {
                reader.decode(BinaryBitmap(HybridBinarizer(src)), hints).text
            } catch (_: NotFoundException) {
                null
            } catch (_: ReaderException) {
                null
            }
        return read(source) ?: if (options.tryInvert) read(source.invert()) else null
    }
}
