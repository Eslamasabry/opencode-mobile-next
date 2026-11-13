package io.github.eslamasabry.oc.ui.kit

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PixelMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Whether a part's state outline (a focus ring, a chosen edge, an error edge) goes all the way round its four corners
 * in a picture of it. Needs no stored picture and no radius: from a point [INNER_DP] in from each corner, rays fan out
 * towards the corner (5° to 85°); every ray must cross a pixel of an outline where it leaves the part. An outline pixel
 * is one that is neither the part's inside, nor the outside, nor a blend of the two (the clip's own soft edge): its
 * colour lies at least [MIN_DISTANCE] away from the line between the inside and the outside samples of that ray. A clip
 * of another radius than the outline cuts the outline off at the corners: the rays across that run find only the
 * fill, the soft clip edge and the outside, and the check names the corner and the angles. The outside is sampled on
 * each ray past the part, or, with `outsideAt`, at one point of the picture's own ground (when words or marks lie just
 * outside the part, as a field's error line does).
 */
internal object OutlineCorners {
    /** How far in from the corner the rays start, in dp (at most half the part's width or height). */
    const val INNER_DP: Float = 36f

    /** The band inside the part's edge where an outline is looked for, in dp (a 30 dp corner's arc lies 9 dp in). */
    const val BAND_DP: Float = 10f

    /** How far outside the part's bounds an outline may lie (a ring drawn outside the face), in dp. */
    const val OUTSIDE_DP: Float = 4f

    /** Colour distance (0-255 per channel, Euclidean) an outline pixel keeps from the inside-outside blend line. */
    const val MIN_DISTANCE: Float = 28f

    private val corners = listOf("top-start" to (-1 to -1), "top-end" to (1 to -1), "bottom-start" to (-1 to 1), "bottom-end" to (1 to 1))

    /** The corners, with the angles of their rays that found no outline; empty when the outline is whole. */
    fun gaps(image: PixelMap, box: Rect, density: Float, outsideAt: Pair<Int, Int>? = null): List<String> {
        val ground = outsideAt?.let { (x, y) -> rgb(image, x, y) }
        val inner = minOf(INNER_DP * density, box.width / 2f, box.height / 2f)
        val band = BAND_DP * density
        val outside = OUTSIDE_DP * density
        val out = mutableListOf<String>()
        for ((name, signs) in corners) {
            val (sx, sy) = signs
            val ox = if (sx < 0) box.left + inner else box.right - inner
            val oy = if (sy < 0) box.top + inner else box.bottom - inner
            val missing = mutableListOf<Int>()
            for (deg in 5..85 step 5) {
                val a = deg * PI / 180.0
                val dx = (sx * cos(a)).toFloat()
                val dy = (sy * sin(a)).toFloat()
                if (!rayCrossesOutline(image, box, ox, oy, dx, dy, band, outside, density, ground)) missing += deg
            }
            if (missing.isNotEmpty()) out += "$name corner has no outline at ${missing.joinToString("°, ")}°"
        }
        return out
    }

    private fun rayCrossesOutline(
        image: PixelMap,
        box: Rect,
        ox: Float,
        oy: Float,
        dx: Float,
        dy: Float,
        band: Float,
        outside: Float,
        density: Float,
        ground: FloatArray?,
    ): Boolean {
        val candidates = mutableListOf<FloatArray>()
        var inside: FloatArray? = null
        var beyond: FloatArray? = null
        var t = 0f
        val step = .5f
        while (true) {
            val x = ox + dx * t
            val y = oy + dy * t
            val depth = minOf(x - box.left, box.right - x, y - box.top, box.bottom - y)
            val px = x.roundToInt()
            val py = y.roundToInt()
            if (px < 0 || py < 0 || px >= image.width || py >= image.height) break
            val c = rgb(image, px, py)
            if (depth > band) inside = c
            else if (depth > -outside) candidates += c
            else {
                beyond = c
                if (depth < -outside - 2f * density) break
            }
            t += step
        }
        val i = inside ?: return false
        val e = ground ?: beyond ?: candidates.lastOrNull() ?: return false
        return candidates.any { distanceToSegment(it, i, e) >= MIN_DISTANCE }
    }

    private fun rgb(image: PixelMap, x: Int, y: Int): FloatArray {
        val c = image[x, y]
        // Over black where the picture is see-through, as a screen would show it over its ground.
        return floatArrayOf(c.red * c.alpha * 255f, c.green * c.alpha * 255f, c.blue * c.alpha * 255f)
    }

    private fun distanceToSegment(p: FloatArray, a: FloatArray, b: FloatArray): Float {
        val ab = FloatArray(3) { b[it] - a[it] }
        val len = ab.sumOf { (it * it).toDouble() }.toFloat()
        val t = if (len < 1e-3f) 0f else ((0 until 3).sumOf { ((p[it] - a[it]) * ab[it]).toDouble() }.toFloat() / len).coerceIn(0f, 1f)
        return sqrt((0 until 3).sumOf { val d = p[it] - (a[it] + t * ab[it]); (d * d).toDouble() }.toFloat())
    }
}
