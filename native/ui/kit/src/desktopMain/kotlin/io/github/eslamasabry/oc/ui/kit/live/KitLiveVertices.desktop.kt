package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.VertexMode

private val meshPaint = Paint()

/** Desktop: Skia's `drawVertices` with per-vertex colours and `BlendMode.DST`, exactly Flutter's call. */
internal actual fun drawLiveVertices(
    scope: DrawScope,
    cols: Int,
    rows: Int,
    positions: FloatArray,
    colors: IntArray,
    indices: ShortArray,
) {
    scope.drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawVertices(VertexMode.TRIANGLES, positions, colors, null, indices, BlendMode.DST, meshPaint)
    }
}
