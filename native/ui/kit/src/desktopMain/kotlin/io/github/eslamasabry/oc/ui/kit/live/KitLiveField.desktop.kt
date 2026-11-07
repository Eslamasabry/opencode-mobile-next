package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.nativeCanvas
import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.VertexMode

private val fieldPaint = Paint()

/** Skia draws the mesh as Flutter does: vertex colours kept as they are (`BlendMode.dst`). */
internal actual fun Canvas.liveDrawField(field: LiveFieldCache) {
    val f = field
    val mode = VertexMode.TRIANGLE_STRIP
    nativeCanvas.drawVertices(mode, f.positions, f.colors, null, f.indices, BlendMode.DST, fieldPaint)
}
