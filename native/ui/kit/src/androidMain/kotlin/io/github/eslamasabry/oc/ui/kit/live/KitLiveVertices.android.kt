package io.github.eslamasabry.oc.ui.kit.live

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import java.util.WeakHashMap

private val meshPaint = Paint()
private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
private val fallbacks = WeakHashMap<IntArray, Bitmap>()
private val dst = RectF()

/**
 * Android: Skia's `drawVertices` with per-vertex colours, as Flutter draws the field. A hardware canvas draws vertices
 * from API 29; below that the same grid of colours is drawn as a bitmap of one pixel per grid point, filtered, which
 * interpolates between the same points.
 */
internal actual fun drawLiveVertices(
    scope: DrawScope,
    cols: Int,
    positions: FloatArray,
    colors: IntArray,
    indices: ShortArray,
) {
    scope.drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || !native.isHardwareAccelerated) {
            native.drawVertices(
                Canvas.VertexMode.TRIANGLES,
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
        } else {
            drawAsBitmap(native, cols, positions, colors)
        }
    }
}

private fun drawAsBitmap(native: Canvas, cols: Int, positions: FloatArray, colors: IntArray) {
    val rows = colors.size / cols
    val bitmap =
        fallbacks.getOrPut(colors) { Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888) }
    bitmap.setPixels(colors, 0, cols, 0, 0, cols, rows)
    // Texel centres sit on the grid points: the bitmap reaches half a cell past each edge.
    val w = positions[positions.size - 1 - 1]
    val h = positions[positions.size - 1]
    val cw = w / (cols - 1)
    val ch = h / (rows - 1)
    dst.set(-cw / 2, -ch / 2, w + cw / 2, h + ch / 2) // NV: half a cell past each edge
    native.save()
    native.clipRect(0f, 0f, w, h)
    native.drawBitmap(bitmap, null, dst, bitmapPaint)
    native.restore()
}
