package io.github.eslamasabry.oc.ui.kitchat.tool

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kitchat.composer.rememberLoopPhase
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

    /** The light on the rail: 2 dp wide, centred on the rail (F: kit_step_light.dart:403). */
    internal const val LIGHT_WIDTH: Float = 2f
}

/**
 * Where a step's node sits on its rail, in px of the node's own box: how far the rail runs [above] it to the node (or
 * the line) before, and [below] it to the next node (or the last node's short tail). [lone]: off any rail, the light
 * goes round the node only. Read at draw time (the neighbours are placed after this node composes).
 */
internal class StepLightRail(val above: () -> Float, val below: () -> Float, val last: () -> Boolean)

/**
 * The step node's light (B-KIT-036; Dart `_StepLight` + `_LightPainter`, lib/ui/kit/chat/kit_step_light.dart): nothing
 * when [state] is idle; otherwise one lap of [KitStepLightLap] in [color] round a node [size] square: on a rail
 * ([rail]) the light comes down the rail from the node before, splits at the node's top centre and fills both sides
 * with a bright head each until they meet at the bottom centre, then one light runs on down the rail to the next node
 * and the outline dims. The rail parts fade in above and out below. Calm runs the lap at half speed; Off and reduced
 * motion ([animate] false) draw the whole outline still. Draw phase only: the lap never recomposes.
 */
@Composable
public fun KitStepLight(state: KitStepLightState, color: Color, size: Dp, animate: Boolean, modifier: Modifier = Modifier) {
    KitStepLight(state, color, size, animate, modifier, rail = null)
}

@Composable
internal fun KitStepLight(
    state: KitStepLightState,
    color: Color,
    size: Dp,
    animate: Boolean,
    modifier: Modifier = Modifier,
    rail: StepLightRail?,
    railColor: Color = color,
) {
    val lap = KitStepLightLap.lapMs(state) ?: return
    val motion = KitMotion.state
    // F: kit_step_light.dart:78-82 Calm runs the lap at half speed
    val speed = if (motion.level == MotionLevel.Calm) .5f else 1f
    val moving = animate && !motion.reduced && !motion.stillFrame
    val phase = rememberLoopPhase(moving, lap, speed, still = STILL_PHASE)
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val dp = density
        if (!moving) {
            // F: kit_step_light.dart:281-287 still: the whole outline in the light's colour
            drawOutline(s, color, 1f, KitStepLightLap.FILL_STROKE * dp)
            return@Canvas
        }
        val above = rail?.above?.invoke()?.coerceAtLeast(0f) ?: 0f
        val below = rail?.below?.invoke()?.coerceAtLeast(0f) ?: 0f
        val p = phase.floatValue
        val frame = KitStepLightLap.frameAt(p, above = above / dp, below = below / dp)
        val railW = 2f // F: kit_step_light.dart:463 two physical pixels
        val x = s / 2f
        val lightW = KitStepLightLap.LIGHT_WIDTH * dp
        // The last step has no rail of its own below its node: the light's tail draws one (F: kit_step_light.dart:470-476).
        if (rail != null && rail.last() && below > 0f)
            drawLine(railColor.copy(alpha = railColor.alpha * .5f), Offset(x, s), Offset(x, s + below), railW)
        // 1) From above, down the rail to the node's top edge, fading in from the node before.
        frame.railAbove?.let { r ->
            val brush =
                Brush.verticalGradient(0f to color.copy(alpha = 0f), .4f to color, 1f to color, startY = -above, endY = 0f)
            drawLine(brush, Offset(x, -above + r.start * dp), Offset(x, -above + r.endInclusive * dp), lightW, StrokeCap.Round)
        }
        // 2) Split at the top centre, fill both sides, meet at the bottom centre; each half carries a bright head.
        if (frame.fill > 0f) {
            drawOutline(s, color.copy(alpha = color.alpha * frame.outlineAlpha), frame.fill, KitStepLightLap.FILL_STROKE * dp)
            if (frame.heads) drawHeads(s, color, frame.fill, dp)
        }
        // 3) One light on down the rail to the next node, fading out.
        frame.railBelow?.let { r ->
            val brush =
                Brush.verticalGradient(0f to color, .5f to color, 1f to color.copy(alpha = 0f), startY = s, endY = s + below)
            drawLine(brush, Offset(x, s + r.start * dp), Offset(x, s + r.endInclusive * dp), lightW, StrokeCap.Round)
        }
    }
}

/** The lap phase drawn when nothing moves: the outline whole (the frame is not used then). */
private const val STILL_PHASE = .66f

/** One half of the outline as a path, in px (the polyline of [KitStepLightLap.outlineHalf]). */
private fun halfPath(size: Float, stroke: Float, left: Boolean): Path =
    Path().apply {
        KitStepLightLap.outlineHalf(size, stroke, left).forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
    }

/** Both halves of the outline from the top centre to [fraction] of their length (F: kit_step_light.dart:421-434). */
private fun DrawScope.drawOutline(size: Float, color: Color, fraction: Float, stroke: Float) {
    val measure = PathMeasure()
    for (left in listOf(true, false)) {
        measure.setPath(halfPath(size, stroke, left), false)
        val end = measure.length * fraction.coerceIn(0f, 1f)
        if (end <= 0f) continue
        val part = Path()
        measure.getSegment(0f, end, part, true)
        drawPath(part, color, style = Stroke(stroke, cap = StrokeCap.Butt, join = StrokeJoin.Round))
    }
}

/** The two bright heads at the filling ends (F: kit_step_light.dart:505-523): 8 dp long, 2.6 dp wide, round. */
private fun DrawScope.drawHeads(size: Float, color: Color, fraction: Float, dp: Float) {
    val stroke = KitStepLightLap.HEAD_STROKE * dp
    val measure = PathMeasure()
    for (left in listOf(true, false)) {
        measure.setPath(halfPath(size, stroke, left), false)
        val end = measure.length * fraction.coerceIn(0f, 1f)
        val start = maxOf(0f, end - KitStepLightLap.HEAD_LENGTH * dp)
        if (end <= start) continue
        val part = Path()
        measure.getSegment(start, end, part, true)
        drawPath(part, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
