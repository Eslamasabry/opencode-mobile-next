package io.github.eslamasabry.oc.ui.kit.content.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

/**
 * The zoom of one picture (Dart `KitZoomController`). Hosts read [isFitted] (a pager swipes only then) and drive it
 * from their own buttons or keys; gestures and the visible Zoom out / Fit to screen / Zoom in buttons share it.
 */
@Stable
public class KitZoomState(public val maxScale: Float = ZoomMath.MAX_SCALE) {
    public var scale: Float by mutableFloatStateOf(ZoomMath.MIN_SCALE)
        private set
    public var offset: Offset by mutableStateOf(Offset.Zero)
        private set
    internal var viewport: Size by mutableStateOf(Size.Zero)

    public val isFitted: Boolean get() = ZoomMath.isFitted(scale)
    public val canZoomIn: Boolean get() = scale < maxScale - ZoomMath.FITTED_EPSILON
    public val canZoomOut: Boolean get() = !isFitted

    /** Scales by [factor] about [focus] (viewport coordinates; the centre when null). */
    public fun zoomBy(factor: Float, focus: Offset? = null) {
        val to = ZoomMath.clampScale(scale * factor, maxScale)
        val at = focus ?: Offset(viewport.width / 2f, viewport.height / 2f)
        offset = ZoomMath.offsetAfterZoom(offset, at, viewport, scale, to)
        scale = to
    }

    public fun panBy(delta: Offset) {
        offset = ZoomMath.clampOffset(offset + delta, viewport, scale)
    }

    public fun zoomIn(): Unit = zoomBy(ZoomMath.STEP)

    public fun zoomOut(): Unit = zoomBy(1f / ZoomMath.STEP)

    /** Back to the whole picture. */
    public fun fit() {
        scale = ZoomMath.MIN_SCALE
        offset = Offset.Zero
    }

    /** One gesture frame: pinch about [centroid], then move by [pan]. */
    internal fun transform(centroid: Offset, pan: Offset, zoom: Float) {
        zoomBy(zoom, centroid)
        panBy(pan)
    }

    /** Double tap: zoom in where tapped, or back to fit when already zoomed. */
    internal fun toggle(at: Offset) {
        if (isFitted) zoomBy(ZoomMath.DOUBLE_TAP_SCALE, at) else fit()
    }
}

@Composable
public fun rememberKitZoomState(maxScale: Float = ZoomMath.MAX_SCALE): KitZoomState =
    remember(maxScale) { KitZoomState(maxScale) }
