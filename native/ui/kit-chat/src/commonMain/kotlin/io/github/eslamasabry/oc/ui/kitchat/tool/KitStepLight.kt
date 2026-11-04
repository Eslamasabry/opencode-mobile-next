package io.github.eslamasabry.oc.ui.kitchat.tool

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/** What a step node shows: no light ([Idle]: done, failed, stopped, waiting) or a running lap. */
public enum class KitStepLightState { Idle, Working, Background }

/**
 * The step light's lap (B-KIT-036). Port of Dart `kit_step_light.dart`: one lap of [LAP_MS] (a background process runs a
 * calmer [CALM_LAP_MS] lap). In one lap a light comes down the rail from above to the node (0-28 %), the node's outline
 * splits at the top centre and both halves fill at once, each with a bright head, meeting at the bottom centre
 * (26-66 %), then one light goes on down the rail while the outline dims (64-95 %); the last 5 % is quiet and the lap
 * starts again. Pure numbers here so the timeline and the geometry are testable; [KitStepLight] draws them.
 */
public object KitStepLightLap {
    public const val LAP_MS: Int = 2000
    public const val CALM_LAP_MS: Int = 4000
    public const val FILL_STROKE: Float = 1.8f
    public const val HEAD_STROKE: Float = 2.6f
    private const val LIGHT_LEN = 14f
    private const val HEAD_LEN = 8f

    /** The lap length for [state]; null when the node is idle (no light at all). */
    public fun lapMs(state: KitStepLightState): Int? =
        when (state) {
            KitStepLightState.Idle -> null
            KitStepLightState.Working -> LAP_MS
            KitStepLightState.Background -> CALM_LAP_MS
        }

    /** One frame of the lap at [p] (0..1), with [above] and [below] the rail lengths to the neighbouring nodes. */
    public data class Frame(
        /** The light on the rail above the node, as a (from, to) span measured down from the rail's top; or null. */
        val railAbove: ClosedFloatingPointRange<Float>?,
        /** How much of each outline half is filled, 0..1. */
        val fill: Float,
        /** The outline's opacity (it dims as the light leaves). */
        val outlineAlpha: Float,
        /** Whether each half carries its bright head. */
        val heads: Boolean,
        /** The light on the rail below the node, measured down from the node's bottom edge; or null. */
        val railBelow: ClosedFloatingPointRange<Float>?,
    ) {
        /** How many lines and paths this frame strokes (the Dart test counts them on the canvas). */
        val lines: Int get() = (if (railAbove != null) 1 else 0) + (if (railBelow != null) 1 else 0)
        val paths: Int get() = (if (fill > 0f) 2 else 0) + (if (heads) 2 else 0)
    }

    public fun frameAt(p: Float, above: Float = 24f, below: Float = 24f): Frame {
        var railAbove: ClosedFloatingPointRange<Float>? = null
        if (above > 0f && p < .3f) {
            val head = easeInOut(seg(p, 0f, .28f)) * (above + LIGHT_LEN)
            val a = (head - LIGHT_LEN).coerceIn(0f, above)
            val b = head.coerceIn(0f, above)
            if (b > a) railAbove = a..b
        }
        val fill = easeInOut(seg(p, .26f, .66f))
        val dim = .75f * (1f - seg(p, .7f, 1f))
        val heads = fill > 0f && p > .26f && p < .68f
        var railBelow: ClosedFloatingPointRange<Float>? = null
        if (below > 0f && p > .64f && p < .97f) {
            val head = easeOut(seg(p, .64f, .95f)) * (below + LIGHT_LEN)
            val a = (head - LIGHT_LEN).coerceIn(0f, below)
            val b = head.coerceIn(0f, below)
            if (b > a) railBelow = a..b
        }
        return Frame(railAbove, fill, dim, heads, railBelow)
    }

    /** The phase (0..1) of a lap of [lapMs] at [elapsedMs] since it started; the lap repeats. */
    public fun phase(elapsedMs: Long, lapMs: Int): Float = (elapsedMs % lapMs).toFloat() / lapMs

    /**
     * One half of a node [size] wide with corner radius `0.3 × size`, from the top centre round to the bottom centre,
     * the [stroke] inside the tile's own edge ([left] or its mirror), as a polyline. Both halves have the same length
     * and meet exactly at the bottom centre.
     */
    public fun outlineHalf(size: Float, stroke: Float, left: Boolean): List<Offset> {
        val inset = stroke / 2
        val r = max(0f, size * .3f - inset)
        val c = size / 2
        val t = inset
        val b = size - inset
        val side = if (left) inset else size - inset
        val cornerX = if (left) side + r else side - r // the corner arcs' centre x
        val sweep = if (left) -PI / 2 else PI / 2 // round towards the side, then back to the centre
        val points = mutableListOf(Offset(c, t), Offset(cornerX, t))
        arc(points, cornerX, t + r, r, from = -PI / 2, sweep = sweep)
        points += Offset(side, b - r)
        arc(points, cornerX, b - r, r, from = if (left) PI else 0.0, sweep = sweep)
        points += Offset(c, b)
        return points
    }

    /** The drawn length of a polyline. */
    public fun length(points: List<Offset>): Float =
        points.zipWithNext().fold(0f) { acc, (a, b) -> acc + hypot(b.x - a.x, b.y - a.y) }

    private fun arc(out: MutableList<Offset>, cx: Float, cy: Float, r: Float, from: Double, sweep: Double) {
        val steps = 16
        for (i in 1..steps) {
            val angle = from + sweep * i / steps
            out += Offset(cx + r * cos(angle).toFloat(), cy + r * sin(angle).toFloat())
        }
    }

    private fun seg(p: Float, a: Float, b: Float) = ((p - a) / (b - a)).coerceIn(0f, 1f)

    private fun easeInOut(t: Float) = if (t < .5f) 2 * t * t else 1 - (-2 * t + 2) * (-2 * t + 2) / 2

    private fun easeOut(t: Float) = 1 - (1 - t) * (1 - t)

    internal const val HEAD_LENGTH: Float = HEAD_LEN
}

/**
 * The step node's light (B-KIT-036): nothing when [state] is idle; otherwise the lap of [KitStepLightLap] drawn in
 * [color] around a node [size] square. With reduced motion ([animate] false) the outline is drawn still.
 */
@Composable
public fun KitStepLight(state: KitStepLightState, color: Color, size: Dp, animate: Boolean, modifier: Modifier = Modifier) {
    val lap = KitStepLightLap.lapMs(state) ?: return
    val p =
        if (animate) {
            rememberInfiniteTransition(label = "stepLight")
                .animateFloat(0f, 1f, infiniteRepeatable(tween(lap, easing = LinearEasing), RepeatMode.Restart), "lap")
                .value
        } else {
            .66f
        }
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val frame = KitStepLightLap.frameAt(p, above = 0f, below = 0f)
        if (frame.fill <= 0f) return@Canvas
        for (left in listOf(true, false)) {
            val pts = KitStepLightLap.outlineHalf(s, KitStepLightLap.FILL_STROKE, left)
            val path = Path().apply { pts.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } }
            drawPath(
                path,
                color.copy(alpha = color.alpha * frame.outlineAlpha.coerceAtLeast(.25f)),
                style = Stroke(KitStepLightLap.FILL_STROKE, cap = StrokeCap.Round),
            )
        }
    }
}
