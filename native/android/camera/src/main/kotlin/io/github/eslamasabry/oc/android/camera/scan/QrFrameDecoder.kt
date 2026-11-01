package io.github.eslamasabry.oc.android.camera.scan

/**
 * Reads one QR code from a camera frame. On Android the frame is a CameraX `ImageProxy` and the decoder is zxing-cpp
 * (`ZxingCppQrDecoder`, src/camerax); host tests use ZXing core on PNG fixtures. Returns null when no code is found;
 * never throws (a decoder error is "no code this frame").
 */
public fun interface QrFrameDecoder<in F> {
    public fun decode(frame: F): String?
}

/** The decoder options for pairing codes (plan 10 §14.2). QR only in every case. */
public data class QrScanOptions(
    /** Dark terminals print light modules on a dark background. */
    val tryInvert: Boolean,
    val tryRotate: Boolean,
    /** Off for the 30 ms budget; P-M3 turns it on only if the first lock misses 1 s. */
    val tryHarder: Boolean,
) {
    public companion object {
        public val Pairing: QrScanOptions = QrScanOptions(tryInvert = true, tryRotate = true, tryHarder = false)
    }
}
