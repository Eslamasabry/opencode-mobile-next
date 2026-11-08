package io.github.eslamasabry.oc.ui.kit.choice

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The points a regular icon breaks into (s33 rework, owner 2026-11-08: "Regular icons yes"): the [ImageVector] is
 * rasterised to a small alpha mask and [IconParticles.COUNT] points are picked inside its ink, spread evenly (a greedy
 * minimum-distance pass in a fixed shuffled order) and moved off the mask's pixel centres by a fixed jitter, so the
 * cloud never reads as a grid. Points are in the icon's unit square (0 … 1, top-left origin), the same for every run.
 */
internal object IconParticles {
    /** Particles per icon: many and small, so the moving icon reads as fine dust, not as blocks. */
    const val COUNT: Int = 110

    /** The mask's side in pixels; Phosphor's 16/256 stroke is 3 px wide at this size. */
    const val MASK_PX: Int = 48

    private const val INK_ALPHA = 96
    private const val ALPHA_SHIFT = 24
    private const val BYTE = 0xff
    private const val HALF = 0.5f
    private const val JITTER = 0.7f
    private const val SPACING = 0.85f
    private const val SEED_ORDER = 7919
    private const val SEED_X = 104_729
    private const val SEED_Y = 1_299_709

    private val cache = HashMap<String, FloatArray>()

    /** [COUNT] points of [icon] as x0, y0, x1, y1, … in its unit square; cached by the vector's name. */
    fun of(icon: ImageVector): FloatArray = cache.getOrPut(icon.name) { sample(icon, COUNT, MASK_PX) }

    /** The alpha mask of [icon] at [size] × [size] pixels, row by row (0 … 255). */
    fun mask(icon: ImageVector, size: Int): IntArray {
        val bitmap = ImageBitmap(size, size, ImageBitmapConfig.Argb8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { color = Color.Black }
        canvas.save()
        canvas.scale(size / icon.viewportWidth, size / icon.viewportHeight)
        drawGroup(canvas, icon.root, paint)
        canvas.restore()
        val pixels = IntArray(size * size)
        bitmap.readPixels(pixels)
        return IntArray(pixels.size) { (pixels[it] ushr ALPHA_SHIFT) and BYTE }
    }

    private fun drawGroup(canvas: Canvas, group: VectorGroup, paint: Paint) {
        for (node in group) {
            when (node) {
                is VectorGroup -> drawGroup(canvas, node, paint)
                is VectorPath -> {
                    val path: Path = node.pathData.toPath()
                    path.fillType = node.pathFillType
                    canvas.drawPath(path, paint)
                }
            }
        }
    }

    /** [count] points inside [icon]'s ink, from a [maskPx] mask. */
    fun sample(icon: ImageVector, count: Int, maskPx: Int): FloatArray {
        val alpha = mask(icon, maskPx)
        val ink = alpha.indices.filter { alpha[it] >= INK_ALPHA }
        val out = FloatArray(count * 2)
        if (ink.isEmpty()) return out.also { it.fill(HALF) }
        val order = ink.sortedBy { hash(it * SEED_ORDER) }
        // Evenly spread: a candidate is taken when no taken point is nearer than the spacing the ink's area allows.
        val minGap = SPACING * sqrt(ink.size.toFloat() / count)
        val gap2 = minGap * minGap
        val taken = ArrayList<Int>(count)
        for (c in order) {
            if (taken.size == count) break
            val cx = c % maskPx
            val cy = c / maskPx
            if (taken.none { t -> sq(t % maskPx - cx) + sq(t / maskPx - cy) < gap2 }) taken += c
        }
        // Too little ink for the spacing: fill up from the rest, then repeat (a repeat gets its own jitter).
        var k = 0
        while (taken.size < count) {
            val c = order[k % order.size]
            if (k >= order.size || c !in taken) taken += c
            k++
        }
        taken.forEachIndexed { n, c ->
            val jx = hash(n * SEED_X + c) * JITTER * HALF
            val jy = hash(n * SEED_Y + c) * JITTER * HALF
            out[2 * n] = (c % maskPx + HALF + jx) / maskPx
            out[2 * n + 1] = (c / maskPx + HALF + jy) / maskPx
        }
        return out
    }

    private fun sq(v: Int): Float = (v * v).toFloat()

    /** A fixed pseudo-random value in -1 … 1 for [i]. */
    @Suppress("MagicNumber") // An integer hash's mixing constants.
    fun hash(i: Int): Float {
        var h = i * -0x61c88647 + 0x3c6ef372
        h = h xor (h ushr 15)
        h *= 0x2c1b3c6d
        h = h xor (h ushr 12)
        return ((h and 0xffff) / 32767.5f) - 1f
    }
}

/**
 * The tap's particle morph (s33 rework): the old icon breaks into fine particles that drift and re-form into the new
 * icon over [MORPH_MS]. The cloud rides from the old icon's place to the new one's (with the sliding block); on the
 * way each particle moves from its point on the old shape to its point on the new one (paired by angle round each
 * shape's centre, so the shape turns into the other rather than crossing over), swells out from the centre and back
 * (a soft burst that peaks mid-flight), shrinks a little, gains a faint halo (the blur) and dims a little. Particles
 * leave staggered, the side facing the travel first. In the last fifth they fade out while the crisp vector icon
 * fades in at its slot.
 *
 * Positions are the caller's pixels: [from] holds x, y, alpha per particle (the old icon at rest, or the particles in
 * flight when a tap interrupts), [to] holds x, y per particle on the new icon. [iconPx] scales the burst.
 */
internal class ParticleMorph(from: FloatArray, to: FloatArray, iconPx: Float) {
    val count: Int = minOf(from.size / FROM_FLOATS, to.size / 2)
    private val fcx: Float
    private val fcy: Float
    private val tcx: Float
    private val tcy: Float
    private val fx = FloatArray(count)
    private val fy = FloatArray(count)
    private val fa = FloatArray(count)
    private val gx = FloatArray(count)
    private val gy = FloatArray(count)
    private val burstX = FloatArray(count)
    private val burstY = FloatArray(count)
    private val stagger = FloatArray(count)

    init {
        fcx = centre(from, FROM_FLOATS, 0)
        fcy = centre(from, FROM_FLOATS, 1)
        tcx = centre(to, 2, 0)
        tcy = centre(to, 2, 1)
        val src = byAngle(from, FROM_FLOATS, fcx, fcy)
        val dst = byAngle(to, 2, tcx, tcy)
        val dir = if (tcx >= fcx) 1f else -1f
        for (n in 0 until count) {
            val s = src[n]
            val d = dst[n]
            fx[n] = from[s * FROM_FLOATS] - fcx
            fy[n] = from[s * FROM_FLOATS + 1] - fcy
            fa[n] = from[s * FROM_FLOATS + 2]
            gx[n] = to[d * 2] - tcx
            gy[n] = to[d * 2 + 1] - tcy
            // The burst: out from the centre along the particle's own bearing, turned a little (a soft swirl).
            val r = sqrt(fx[n] * fx[n] + fy[n] * fy[n])
            val base = if (r > MIN_RADIUS) atan2(fy[n], fx[n]) else IconParticles.hash(n) * PI.toFloat()
            val angle = base + IconParticles.hash(n + SWIRL_SEED) * SWIRL
            val reach = iconPx * (BURST_MIN + BURST_SPAN * (IconParticles.hash(n + REACH_SEED) + 1f) * HALF)
            burstX[n] = cos(angle) * reach
            burstY[n] = sin(angle) * reach
            // The side facing the travel leaves first; a fixed jitter keeps the front from reading as a line.
            val lead = ((-dir * fx[n]) / (iconPx * HALF) + 1f) * HALF
            stagger[n] = (LEAD_WEIGHT * lead.coerceIn(0f, 1f) +
                (1f - LEAD_WEIGHT) * (IconParticles.hash(n + STAGGER_SEED) + 1f) * HALF).coerceIn(0f, 1f)
        }
    }

    /** The values per particle in [frame]'s output. */
    val floats: Int get() = count * FLOATS

    /**
     * Fills [out] with the particles [elapsedMs] into the morph (x, y, alpha, size, halo per particle) and returns how
     * many. [carried] is how far the chosen block has slid (0 … 1): the cloud keeps up with it, so the dust stays in
     * the block as in the reference, and never lags behind its own clock. No allocation: the draw loop calls it.
     */
    fun frame(elapsedMs: Float, out: FloatArray, carried: Float = 0f): Int {
        val t = (elapsedMs / MORPH_MS).coerceIn(0f, 1f)
        val c = maxOf(easeInOut((t / TRAVEL_END).coerceIn(0f, 1f)), carried.coerceIn(0f, 1f))
        val cx = fcx + (tcx - fcx) * c
        val cy = fcy + (tcy - fcy) * c
        val fadeIn = smooth(t, 0f, FADE_IN_END)
        val fadeOut = 1f - smooth(t, FADE_OUT_START, 1f)
        var k = 0
        for (n in 0 until count) {
            val u = ((t - stagger[n] * STAGGER) / FLIGHT).coerceIn(0f, 1f)
            val e = easeInOut(u)
            val bump = sin(PI.toFloat() * u)
            out[k++] = cx + fx[n] + (gx[n] - fx[n]) * e + burstX[n] * bump
            out[k++] = cy + fy[n] + (gy[n] - fy[n]) * e + burstY[n] * bump
            out[k++] = (fa[n] + (1f - fa[n]) * fadeIn) * (1f - DIM * bump) * fadeOut
            out[k++] = 1f - SHRINK * bump
            out[k++] = bump
        }
        return count
    }

    /** The particles now as x, y, alpha (a new morph's [from] when a tap interrupts this one). */
    fun sample(elapsedMs: Float): FloatArray {
        val buf = FloatArray(floats)
        frame(elapsedMs, buf)
        return FloatArray(count * FROM_FLOATS) { i ->
            val n = i / FROM_FLOATS
            buf[n * FLOATS + i % FROM_FLOATS]
        }
    }

    companion object {
        /** The morph's length: break, drift and re-form (owner: "over about 300 ms"). */
        const val MORPH_MS: Float = 300f

        /** Floats per particle in a [frame]: x, y, alpha, size (1 = at rest), halo (0 … 1), at these offsets. */
        const val FLOATS: Int = 5
        const val ALPHA_AT: Int = 2
        const val SIZE_AT: Int = 3
        const val HALO_AT: Int = 4

        /** Floats per particle in a morph's start: x, y, alpha. */
        const val FROM_FLOATS: Int = 3

        /** Where in the morph (0 … 1) the particles start to give way to the vector icon. */
        const val FADE_OUT_START: Float = 0.8f

        private const val HALF = 0.5f
        private const val FADE_IN_END = 0.12f
        private const val TRAVEL_END = 0.78f
        private const val STAGGER = 0.22f
        private const val FLIGHT = 0.56f
        private const val DIM = 0.3f
        private const val SHRINK = 0.3f
        private const val BURST_MIN = 0.16f
        private const val BURST_SPAN = 0.3f
        private const val SWIRL = 0.45f
        private const val LEAD_WEIGHT = 0.55f
        private const val MIN_RADIUS = 0.01f
        private const val SWIRL_SEED = 4099
        private const val REACH_SEED = 8191
        private const val STAGGER_SEED = 16_381

        /** Ease-in-out cubic: a soft start and a soft landing. */
        @Suppress("MagicNumber") // The cubic's own coefficients.
        fun easeInOut(t: Float): Float =
            if (t < HALF) 4f * t * t * t else 1f - (-2f * t + 2f).let { it * it * it } / 2f

        /** 0 before [a], 1 after [b], smoothstep between. */
        @Suppress("MagicNumber") // Smoothstep's own coefficients.
        fun smooth(t: Float, a: Float, b: Float): Float {
            val x = ((t - a) / (b - a)).coerceIn(0f, 1f)
            return x * x * (3f - 2f * x)
        }

        private fun centre(points: FloatArray, stride: Int, axis: Int): Float {
            val n = points.size / stride
            if (n == 0) return 0f
            var sum = 0f
            for (i in 0 until n) sum += points[i * stride + axis]
            return sum / n
        }

        private fun byAngle(points: FloatArray, stride: Int, cx: Float, cy: Float): IntArray {
            val n = points.size / stride
            return (0 until n)
                .sortedBy { i -> atan2(points[i * stride + 1] - cy, points[i * stride] - cx) }
                .toIntArray()
        }
    }
}
