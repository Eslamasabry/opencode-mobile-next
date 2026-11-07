package io.github.eslamasabry.oc.ui.kit.live

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.nativeCanvas

private val meshPaint = Paint().apply { color = android.graphics.Color.WHITE }
private val platePaint = Paint(Paint.FILTER_BITMAP_FLAG)
private var plate: Bitmap? = null
private val plateDst = RectF()

/**
 * API 29+: the mesh straight to the canvas (the platform modulates the vertex colours by an opaque white paint, so
 * they stay as they are). Below 29 a hardware canvas does not draw vertices, so the same grid of colours goes into one
 * small bitmap, reused, drawn stretched and filtered so its pixel centres sit on the grid points.
 */
internal actual fun Canvas.liveDrawField(
    positions: FloatArray,
    colors: IntArray,
    indices: ShortArray,
    lw: Int,
    lh: Int,
    width: Float,
    height: Float,
) {
    val canvas = nativeCanvas
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        canvas.drawVertices(
            android.graphics.Canvas.VertexMode.TRIANGLE_STRIP,
            positions.size,
            positions,
            0,
            null,
            0,
            colors,
            0,
            indices,
            0,
            indices.size,
            meshPaint,
        )
        return
    }
    val w = lw + 1
    val h = lh + 1
    val bitmap = plate?.takeIf { it.width == w && it.height == h } ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { plate = it }
    bitmap.setPixels(colors, 0, w, 0, 0, w, h)
    val cellW = width / lw
    val cellH = height / lh
    plateDst.set(-cellW / 2, -cellH / 2, width + cellW / 2, height + cellH / 2) // NV: half a cell past each edge
    canvas.drawBitmap(bitmap, null, plateDst, platePaint)
}
