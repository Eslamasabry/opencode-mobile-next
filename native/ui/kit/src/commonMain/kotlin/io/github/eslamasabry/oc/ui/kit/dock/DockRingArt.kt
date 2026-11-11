package io.github.eslamasabry.oc.ui.kit.dock

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.LayoutDirection
import io.github.eslamasabry.oc.ui.kit.theme.KitColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** The colours the rings draw with, from the kit's roles (and the body's ink). */
@Immutable
internal class DockInks(
    val ink: Color,
    val ink2: Color,
    val track: Color,
    val accent: Color,
    val attention: Color,
    val failure: Color,
    val onMark: Color,
) {
    /** The ring's state colour. */
    fun of(state: KitDockRingState): Color =
        when (state) {
            KitDockRingState.NeedsYou -> attention
            KitDockRingState.Failed -> failure
            KitDockRingState.Idle -> ink2
            else -> accent
        }

    companion object {
        fun from(colors: KitColors, ink: Color, ink2: Color): DockInks =
            DockInks(ink, ink2, ink.copy(alpha = 0.14f), colors.accent, colors.attention, dockFailureInk(colors), colors.onAttentionFill)
    }
}

/** One ring's motion values (kept per ring key). */
internal class RingMotion {
    /** The task arc (done / total), springing forward with a small overshoot. */
    val arc = Animatable(0f)
    /** A scale kick: the request's double pop, a task's advance, a tap. */
    val pop = Animatable(0f)
    /** The failure's one sideways shake, dp. */
    val shake = Animatable(0f)
    /** The failure's 120 ms flash, 0..1. */
    val flash = Animatable(0f)
    /** The finished check drawing itself, 0..1. */
    val check = Animatable(0f)
    /** Finished settling into a dim ring, 0..1. */
    val settle = Animatable(0f)
    /** The ring's place in the column, as a fractional slot. */
    val slot = Animatable(-1f)
    /** Growing in when the ring arrives. */
    val presence = Animatable(0f)
    var lastPulse = -1
}

private const val COMET_SHARE = 0.36f

/**
 * Draws one ring centred at [c] with stroke radius [r] and width [w] (px). [arc], [check] and [settle] come from the
 * ring's [RingMotion]; [spin] (degrees) turns the comet, [breath] (0..1) breathes the thinking ring; [flash] (0..1)
 * brightens a fresh failure. No allocation: arcs and lines only.
 */
internal fun DrawScope.drawDockRing(
    c: Offset,
    r: Float,
    w: Float,
    state: KitDockRingState,
    inks: DockInks,
    arc: Float,
    check: Float,
    settle: Float,
    spin: Float,
    breath: Float,
    flash: Float,
    alpha: Float = 1f,
    mini: Boolean = false,
) {
    val tl = Offset(c.x - r, c.y - r)
    val sz = Size(2 * r, 2 * r)
    val sc = inks.of(state).let { if (flash > 0f) lerp(it, inks.ink, 0.45f * flash) else it }
    val round = Stroke(width = w, cap = StrokeCap.Round)
    val flat = Stroke(width = w)
    when (state) {
        is KitDockRingState.WorkingWithTasks -> {
            drawCircle(inks.track, r, c, alpha, flat)
            drawArc(sc, -90f, 360f * arc.coerceIn(0f, 1.04f), false, tl, sz, alpha, round)
        }
        KitDockRingState.Working -> {
            drawCircle(inks.track, r, c, alpha, flat)
            // A calm comet: three arcs ending at one head, each fainter and longer.
            val sweep = 360f * COMET_SHARE
            drawArc(sc, spin - sweep, sweep, false, tl, sz, alpha * 0.22f, round)
            drawArc(sc, spin - sweep * 0.62f, sweep * 0.62f, false, tl, sz, alpha * 0.5f, round)
            drawArc(sc, spin - sweep * 0.26f, sweep * 0.26f, false, tl, sz, alpha, round)
        }
        KitDockRingState.Thinking -> drawCircle(sc, r, c, alpha * (0.5f + 0.45f * breath), flat)
        KitDockRingState.NeedsYou -> {
            drawCircle(sc, r, c, alpha, flat)
            if (mini) drawCircle(sc, r * 0.33f, c, alpha) else drawMark(c, r, sc, inks.onMark, bang = false, alpha)
        }
        KitDockRingState.Finished -> {
            drawCircle(sc, r, c, alpha * (1f - 0.5f * settle), flat)
            drawCheck(c, r, sc, w * 0.87f, if (mini) 1f else check, alpha)
        }
        KitDockRingState.Failed -> {
            drawCircle(sc, r, c, alpha, flat)
            if (mini) drawBang(c, r, sc, w, alpha) else drawMark(c, r, sc, inks.onMark, bang = true, alpha)
        }
        KitDockRingState.Idle -> drawCircle(inks.ink2.copy(alpha = 0.5f), r, c, alpha, flat)
    }
}

/** The dot on the ring's upper end (needs you), with a "!" in it for a failure: a shape, never colour alone. */
private fun DrawScope.drawMark(c: Offset, r: Float, color: Color, on: Color, bang: Boolean, alpha: Float) {
    val k = r / DockDims.RING_R
    val m = Offset(c.x + 12.9f * k * (if (layoutDirection == LayoutDirection.Rtl) -1f else 1f), c.y - 12.9f * k)
    drawCircle(color, 5.6f * k, m, alpha)
    if (bang) {
        drawLine(on, Offset(m.x, m.y - 2.9f * k), Offset(m.x, m.y + 0.2f * k), 1.7f * k, StrokeCap.Round, alpha = alpha)
        drawCircle(on, 0.95f * k, Offset(m.x, m.y + 2.8f * k), alpha)
    }
}

/** A "!" in the middle of a mini ring (a failed sub-agent). */
private fun DrawScope.drawBang(c: Offset, r: Float, color: Color, w: Float, alpha: Float) {
    drawLine(color, Offset(c.x, c.y - r * 0.5f), Offset(c.x, c.y + r * 0.12f), w, StrokeCap.Round, alpha = alpha)
    drawCircle(color, w * 0.55f, Offset(c.x, c.y + r * 0.45f), alpha)
}

/** The check, drawn up to [t] of its length (two strokes, no path). */
private fun DrawScope.drawCheck(c: Offset, r: Float, color: Color, w: Float, t: Float, alpha: Float) {
    if (t <= 0f) return
    val k = r / DockDims.RING_R
    val ax = c.x - 7.4f * k
    val ay = c.y + 0.6f * k
    val bx = c.x - 2.4f * k
    val by = c.y + 5.6f * k
    val ex = c.x + 7.4f * k
    val ey = c.y - 5.3f * k
    val l1 = hypot(bx - ax, by - ay)
    val l2 = hypot(ex - bx, ey - by)
    val d = (l1 + l2) * t.coerceIn(0f, 1f)
    val f1 = (d / l1).coerceAtMost(1f)
    drawLine(color, Offset(ax, ay), Offset(ax + (bx - ax) * f1, ay + (by - ay) * f1), w, StrokeCap.Round, alpha = alpha)
    if (d > l1) {
        val f2 = ((d - l1) / l2).coerceAtMost(1f)
        drawLine(color, Offset(bx, by), Offset(bx + (ex - bx) * f2, by + (ey - by) * f2), w, StrokeCap.Round, alpha = alpha)
    }
}

/** The hold's charge arc around the ring, 0..1. */
internal fun DrawScope.drawCharge(c: Offset, r: Float, ink: Color, t: Float) {
    if (t <= 0.01f) return
    val rr = r * 1.245f
    drawArc(ink, -90f, 360f * t, false, Offset(c.x - rr, c.y - rr), Size(2 * rr, 2 * rr), (t * 4f).coerceAtMost(1f), Stroke(1.8f * density, cap = StrokeCap.Round))
}

/**
 * Up to four satellites on the rim facing the screen's middle ([towardStart]: the middle is at the layout start), in
 * their state colours, then "+n" ([more], measured once in composition). A working one pulses gently ([pulse] 0..1).
 */
internal fun DrawScope.drawSatellites(
    c: Offset,
    r: Float,
    children: List<KitDockChild>,
    order: List<Int>,
    inks: DockInks,
    towardLeft: Boolean,
    pulse: Float,
    more: TextLayoutResult?,
    alpha: Float,
) {
    if (children.isEmpty()) return
    val shown = minOf(4, children.size)
    val m = shown + (if (more != null) 1 else 0)
    val base = if (towardLeft) PI.toFloat() else 0f
    val dir = if (towardLeft) 1f else -1f
    val rr = r + 7.4f * (r / DockDims.RING_R)
    for (i in 0 until m) {
        val ang = base - dir * (i - (m - 1) / 2f) * 0.3f
        val p = Offset(c.x + rr * cos(ang), c.y + rr * sin(ang))
        if (i < shown) {
            val ch = children[order[i]]
            val col = inks.of(ch.state)
            val a = when {
                ch.state == KitDockRingState.Finished -> 0.45f
                ch.state.running -> 0.42f + 0.58f * pulse
                else -> 1f
            }
            drawCircle(col, 3.3f * (r / DockDims.RING_R), p, alpha * a)
        } else if (more != null) {
            drawText(more, topLeft = Offset(p.x - more.size.width / 2f, p.y - more.size.height / 2f), alpha = alpha)
        }
    }
}

/** Degrees per second of the comet (a lap in about 1.15 s; the mini rings turn slower). */
internal const val COMET_DEG_PER_S = 313f

/** Angle helper for tests: the comet's head after [ms] milliseconds. */
internal fun cometHead(ms: Long, slow: Boolean = false): Float = ((ms / 1000f) * COMET_DEG_PER_S * (if (slow) 0.45f else 1f)) % 360f
