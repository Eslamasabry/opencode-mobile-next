package io.github.eslamasabry.oc.android.camera.camerax

import androidx.camera.core.ImageProxy
import io.github.eslamasabry.oc.android.camera.scan.QrFrameDecoder
import io.github.eslamasabry.oc.android.camera.scan.QrScanOptions
import zxingcpp.BarcodeReader

/**
 * zxing-cpp 3.1.1 on the frame's Y plane, limited to the proxy's crop rect (set by [CameraXScanner] from
 * `ScanWindow`). Google-free: no ML Kit, no Play services (plan 02 G8). A native error is "no code this frame".
 */
public class ZxingCppQrDecoder(options: QrScanOptions = QrScanOptions.Pairing) : QrFrameDecoder<ImageProxy> {
    private val reader =
        BarcodeReader(
            BarcodeReader.Options(
                formats = setOf(BarcodeReader.Format.QR_CODE),
                tryHarder = options.tryHarder,
                tryRotate = options.tryRotate,
                tryInvert = options.tryInvert,
            ),
        )

    /** Milliseconds the last read took (P-M3 budget: p95 < 30 ms per 720p frame). */
    public val lastReadTimeMs: Int get() = reader.lastReadTime

    override fun decode(frame: ImageProxy): String? =
        try {
            reader.read(frame).firstOrNull { it.text != null }?.text
        } catch (_: RuntimeException) {
            null
        }
}
