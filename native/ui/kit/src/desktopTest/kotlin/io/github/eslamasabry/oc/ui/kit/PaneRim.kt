package io.github.eslamasabry.oc.ui.kit

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PixelMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * How strong a glass pane's rim is all the way round its outline, in a picture of it (owner 2026-11-13: a pane whose
 * edge disappears at a corner reads as a broken shape). No stored picture and no radius: rays leave the pane from
 * inside it, square to each straight side every [STEP_DP] and fanned across each corner (5° to 85°, from a point
 * [OutlineCorners.INNER_DP] in from the corner), and each ray's rim strength is how far the brightest-standing pixel it
 * crosses near the edge lies from the blend of the pane's inside and the ground beyond it (0-255 per channel,
 * Euclidean; the rim stands out from both, a soft clip edge does not). [weak] names every stretch below a share of the
 * pane's own peak.
 */
internal object PaneRim {
    /** How often a straight side is sampled, in dp. */
    const val STEP_DP: Float = 8f

    /** One ray: where on the outline it leaves the pane, and the rim's strength there. */
    data class Sample(val where: String, val strength: Float)

    /**
     * The rim's strength round [box]'s outline ([only] keeps the rays leaving a part of it: a joined pane's free side).
     */
    fun around(image: PixelMap, box: Rect, density: Float, only: (x: Float, y: Float) -> Boolean = { _, _ -> true }): List<Sample> {
        val inner = minOf(OutlineCorners.INNER_DP * density, box.width / 2f, box.height / 2f)
        val band = OutlineCorners.BAND_DP * density
        val outside = OutlineCorners.OUTSIDE_DP * density
        val out = mutableListOf<Sample>()
        fun ray(where: String, ox: Float, oy: Float, dx: Float, dy: Float) {
            if (!only(ox + dx * inner, oy + dy * inner)) return
            out += Sample(where, strength(image, box, ox, oy, dx, dy, band, outside, density))
        }
        for ((name, sx, sy) in listOf(Triple("top-start", -1, -1), Triple("top-end", 1, -1), Triple("bottom-start", -1, 1), Triple("bottom-end", 1, 1))) {
            val ox = if (sx < 0) box.left + inner else box.right - inner
            val oy = if (sy < 0) box.top + inner else box.bottom - inner
            for (deg in 5..85 step 5) {
                val a = deg * PI / 180.0
                ray("$name corner at $deg°", ox, oy, (sx * cos(a)).toFloat(), (sy * sin(a)).toFloat())
            }
        }
        val step = STEP_DP * density
        var x = box.left + inner + step
        while (x < box.right - inner - step / 2f) {
            ray("top side at x ${x.roundToInt()}", x, box.top + inner, 0f, -1f)
            ray("bottom side at x ${x.roundToInt()}", x, box.bottom - inner, 0f, 1f)
            x += step
        }
        var y = box.top + inner + step
        while (y < box.bottom - inner - step / 2f) {
            ray("start side at y ${y.roundToInt()}", box.left + inner, y, -1f, 0f)
            ray("end side at y ${y.roundToInt()}", box.right - inner, y, 1f, 0f)
            y += step
        }
        return out
    }

    /** The samples below [share] of the strongest one, or below [least] at all (no rim there). */
    fun weak(samples: List<Sample>, share: Float, least: Float): List<String> {
        val peak = samples.maxOfOrNull { it.strength } ?: return listOf("no outline sampled")
        return samples.filter { it.strength < share * peak || it.strength < least }.map {
            "${it.where}: ${it.strength.roundToInt()} (${(it.strength / peak * 100f).roundToInt()} % of the peak ${peak.roundToInt()})"
        }
    }

    private fun strength(image: PixelMap, box: Rect, ox: Float, oy: Float, dx: Float, dy: Float, band: Float, outside: Float, density: Float): Float {
        val candidates = mutableListOf<FloatArray>()
        var inside: FloatArray? = null
        var beyond: FloatArray? = null
        var t = 0f
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
            t += .5f
        }
        val i = inside ?: return 0f
        val e = beyond ?: candidates.lastOrNull() ?: return 0f
        return candidates.maxOfOrNull { distanceToSegment(it, i, e) } ?: 0f
    }

    private fun rgb(image: PixelMap, x: Int, y: Int): FloatArray {
        val c = image[x, y]
        return floatArrayOf(c.red * c.alpha * 255f, c.green * c.alpha * 255f, c.blue * c.alpha * 255f)
    }

    private fun distanceToSegment(p: FloatArray, a: FloatArray, b: FloatArray): Float {
        val ab = FloatArray(3) { b[it] - a[it] }
        val len = ab.sumOf { (it * it).toDouble() }.toFloat()
        val t = if (len < 1e-3f) 0f else ((0 until 3).sumOf { ((p[it] - a[it]) * ab[it]).toDouble() }.toFloat() / len).coerceIn(0f, 1f)
        return sqrt((0 until 3).sumOf { val d = p[it] - (a[it] + t * ab[it]); (d * d).toDouble() }.toFloat())
    }
}
