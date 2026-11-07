package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.nativeCanvas
import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.VertexMode

private val fieldPaint = Paint()

/** Skia draws the mesh as Flutter does: vertex colours kept as they are (`BlendMode.dst`). */
internal actual fun Canvas.liveDrawField(
    positions: FloatArray,
    colors: IntArray,
    indices: ShortArray,
    lw: Int,
    lh: Int,
    width: Float,
    height: Float,
) {
    nativeCanvas.drawVertices(VertexMode.TRIANGLE_STRIP, positions, colors, null, indices, BlendMode.DST, fieldPaint)
}
