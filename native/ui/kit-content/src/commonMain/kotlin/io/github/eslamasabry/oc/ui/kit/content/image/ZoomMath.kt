package io.github.eslamasabry.oc.ui.kit.content.image

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

/**
 * Zoom arithmetic of `KitZoom` (plan 10 §9.3, Dart `KitZoomMode.fit`). Pure. The content is laid out fitted to the
 * viewport, so scale 1 is "fit to screen" and the floor; the ceiling is 5 × fit. The picture is scaled about the
 * viewport's centre and then moved by an offset that never shows past its edges.
 */
public object ZoomMath {
    public const val MIN_SCALE: Float = 1f
    public const val MAX_SCALE: Float = 5f

    /** One press of Zoom in or Zoom out. */
    public const val STEP: Float = 1.5f

    /** A double tap on a fitted picture. */
    public const val DOUBLE_TAP_SCALE: Float = 2.5f

    /** Below this the picture counts as fitted (a pager may swipe; plan 10 §9.3 uses 0.01 of zoom fraction). */
    public const val FITTED_EPSILON: Float = 0.01f

    public fun clampScale(scale: Float, max: Float = MAX_SCALE): Float = scale.coerceIn(MIN_SCALE, max)

    /** The furthest the picture may move each way at [scale] before an edge comes into view. */
    public fun maxOffset(viewport: Size, scale: Float): Offset =
        Offset(
            (viewport.width * (scale - 1f) / 2f).coerceAtLeast(0f),
            (viewport.height * (scale - 1f) / 2f).coerceAtLeast(0f),
        )

    public fun clampOffset(offset: Offset, viewport: Size, scale: Float): Offset {
        val m = maxOffset(viewport, scale)
        return Offset(offset.x.coerceIn(-m.x, m.x), offset.y.coerceIn(-m.y, m.y))
    }

    /**
     * The offset that keeps the point under [focus] (viewport coordinates) still while the scale goes from [from] to
     * [to], clamped to the edges.
     */
    public fun offsetAfterZoom(offset: Offset, focus: Offset, viewport: Size, from: Float, to: Float): Offset {
        val centre = Offset(viewport.width / 2f, viewport.height / 2f)
        val rel = focus - centre
        val moved = rel - (rel - offset) * (to / from)
        return clampOffset(moved, viewport, to)
    }

    public fun isFitted(scale: Float): Boolean = scale - MIN_SCALE < FITTED_EPSILON
}
