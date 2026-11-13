package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PixelMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Finds a style shadow that a clip cut into a box (device pass 2026-11-13, Soft: "a darker box ends in a straight
 * vertical edge"). A whole shadow fades smoothly (a Gaussian changes by a level or two from one pixel to the next); a
 * clipped one steps by many levels along a straight line. Around each face (the visible part, never its taller tap
 * target) it looks for a step of [STEP] levels or more between neighbouring pixels that both lie outside every face,
 * running straight for [RUN_DP] or longer: a vertical one (the same column over many rows) or a horizontal one.
 * No stored picture: the rays read the render.
 */
internal object ShadowEdges {
    private const val STEP = 6
    private const val RUN_DP = 3f

    /** Each straight edge found around [faces] (in px) out to [reachDp], named for a failure message. */
    fun cuts(image: PixelMap, faces: List<Rect>, density: Float, reachDp: Float = 16f): List<String> {
        val reach = reachDp * density
        val run = (RUN_DP * density).roundToInt()
        val out = mutableListOf<String>()
        // A face's own rim (a 1 dp edge stroke centred on its outline, anti-aliased) lies just outside its box.
        val rim = density + 1.5f
        fun outside(x: Int, y: Int) = x in 0 until image.width && y in 0 until image.height && faces.none { it.inflate(rim).contains(androidx.compose.ui.geometry.Offset(x + .5f, y + .5f)) }
        fun step(x0: Int, y0: Int, x1: Int, y1: Int): Boolean {
            if (!outside(x0, y0) || !outside(x1, y1)) return false
            val a = image[x0, y0]
            val b = image[x1, y1]
            val d = max(abs(a.red - b.red), max(abs(a.green - b.green), abs(a.blue - b.blue))) * 255f
            return d >= STEP
        }
        faces.forEachIndexed { i, f ->
            val left = (f.left - reach).toInt().coerceAtLeast(0)
            val right = (f.right + reach).toInt().coerceAtMost(image.width - 2)
            val top = (f.top - reach).toInt().coerceAtLeast(0)
            val bottom = (f.bottom + reach).toInt().coerceAtMost(image.height - 2)
            // Vertical edges: the same column steps over [run] rows in a row.
            for (x in left..right) {
                var n = 0
                for (y in top..bottom) {
                    n = if (step(x, y, x + 1, y)) n + 1 else 0
                    if (n == run) {
                        out += "face $i: a straight vertical edge at x=$x around y=$y (${(x - f.right) / density} dp past its end, ${(f.left - x) / density} dp before its start)"
                        break
                    }
                }
            }
            // Horizontal edges: the same row steps over [run] columns in a row.
            for (y in top..bottom) {
                var n = 0
                for (x in left..right) {
                    n = if (step(x, y, x, y + 1)) n + 1 else 0
                    if (n == run) {
                        out += "face $i: a straight horizontal edge at y=$y around x=$x (${(y - f.bottom) / density} dp below it, ${(f.top - y) / density} dp above it)"
                        break
                    }
                }
            }
        }
        return out.distinct().take(MAX_REPORTED)
    }

    /** How far [x],[y] differs from [ground], the largest channel in levels. */
    fun differs(image: PixelMap, x: Int, y: Int, ground: androidx.compose.ui.graphics.Color): Float {
        val a = image[x, y]
        return max(abs(a.red - ground.red), max(abs(a.green - ground.green), abs(a.blue - ground.blue))) * 255f
    }

    private const val MAX_REPORTED = 12
}
