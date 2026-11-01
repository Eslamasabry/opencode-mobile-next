package io.github.eslamasabry.oc.android.camera.scan

/** A rectangle in frame (sensor buffer) pixels; [right] and [bottom] are exclusive, like android.graphics.Rect. */
public data class CropRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * Decode only what the person aims at (plan 10 §14.2): the scanner window is a centred square, so the crop is the
 * same square in frame pixels whatever the sensor rotation.
 */
public object ScanWindow {
    /** Below this a 240 dp window on a tablet would crop the code itself away. */
    public const val MIN_FRACTION: Float = 0.3f

    /** A centred square of `min(width, height) × fraction`, the fraction clamped to [MIN_FRACTION]…1. */
    public fun cropFor(frameWidth: Int, frameHeight: Int, fraction: Float): CropRect {
        require(frameWidth > 0 && frameHeight > 0) { "empty frame" }
        val f = if (fraction.isNaN()) MIN_FRACTION else fraction.coerceIn(MIN_FRACTION, 1f)
        val side = (minOf(frameWidth, frameHeight) * f).toInt().coerceAtLeast(1)
        val left = (frameWidth - side) / 2
        val top = (frameHeight - side) / 2
        return CropRect(left, top, left + side, top + side)
    }

    /**
     * The window's share of the preview's short side. The preview fills its view (FILL_CENTER), so the frame's short
     * side spans at least the view's short side; using the view's short side errs on decoding a little more.
     */
    public fun fractionFor(windowPx: Int, previewShortSidePx: Int): Float =
        if (previewShortSidePx <= 0) 1f else (windowPx.toFloat() / previewShortSidePx).coerceIn(MIN_FRACTION, 1f)
}
