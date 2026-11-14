package io.github.eslamasabry.oc.ui.design.effects

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/*
 * The outline of a joined body (a menu on the bar, the jump pill on the message box) as points, for what is drawn along
 * the body rather than through its shader: the message box's running glow (owner 2026-11-14, c26: the box's green rim
 * ran straight across the bottom of the joined "Jump to latest" pill, cutting it from the box). It is the same field as
 * [LiquidJoin.sample] (the cover's `dist`), so the line lies on the edge the glass shows, in every state of the join.
 */
public object JoinOutline {
    /** About one point per this many px of the traced box's perimeter. */
    public const val STEP_PX: Float = 3f

    /** The fewest and the most points an outline has. */
    public const val MIN_POINTS: Int = 64
    public const val MAX_POINTS: Int = 1024

    /** How many points the outline of [a] and [b] gets: one per [STEP_PX] of their bounding box's perimeter. */
    public fun pointsFor(a: JoinBox, b: JoinBox, marginPx: Float): Int {
        val w = max(a.right, b.right) - min(a.left, b.left) + 2f * marginPx
        val h = max(a.bottom, b.bottom) - min(a.top, b.top) + 2f * marginPx
        return ceil(2f * (w + h) / STEP_PX).toInt().coerceIn(MIN_POINTS, MAX_POINTS)
    }

    /**
     * The joined body of [a] (the body) and [b] (its drop) with reach [reachPx], as the closed line [offsetPx] inside
     * its outline (negative: outside it), written to [out] as x, y pairs ([count] of them, walking the outline in one
     * direction). Each point is where a line from a point of a box around both panes towards the drop's foot inside
     * the body first meets that line, so the points follow the outline evenly and never cross. With reach 0 the panes
     * are not joined: the line runs round both, as two panes that touch.
     */
    public fun trace(
        a: JoinBox,
        b: JoinBox,
        reachPx: Float,
        offsetPx: Float,
        count: Int = pointsFor(a, b, abs(offsetPx) + reachPx / 6f + MARGIN_PX),
        out: FloatArray = FloatArray(count * 2),
    ): FloatArray {
        val margin = abs(offsetPx) + reachPx / 6f + MARGIN_PX
        val left = min(a.left, b.left) - margin
        val top = min(a.top, b.top) - margin
        val right = max(a.right, b.right) + margin
        val bottom = max(a.bottom, b.bottom) + margin
        // The drop's foot inside the body: the centre every line heads for. A drop beside or inside the body is always
        // reached from it through the body (the drop stands on the body's edge, the join fills between them).
        val cx = b.cx.coerceIn(a.left + 1f, a.right - 1f)
        val cy = (a.cy + b.cy.coerceIn(a.top, a.bottom)) / 2f
        val w = right - left
        val h = bottom - top
        val perimeter = 2f * (w + h)
        val field = Field(a, b, reachPx)
        for (i in 0 until count) {
            // Clockwise round the box from its top-left corner.
            var s = perimeter * i / count
            val px: Float
            val py: Float
            when {
                s < w -> { px = left + s; py = top }
                s < w + h -> { s -= w; px = right; py = top + s }
                s < 2f * w + h -> { s -= w + h; px = right - s; py = bottom }
                else -> { s -= 2f * w + h; px = left; py = bottom - s }
            }
            val dx = cx - px
            val dy = cy - py
            val length = sqrt(dx * dx + dy * dy)
            val ux = dx / max(length, 1e-6f)
            val uy = dy / max(length, 1e-6f)
            // March in from outside: the field's distance never overshoots the outline, so the first point inside
            // is the outline's (to half a pixel by halving the last step).
            var t = 0f
            var outside = 0f
            var hit = length
            var steps = 0
            while (t < length && steps < MAX_STEPS) {
                val f = field.dist(px + ux * t, py + uy * t) + offsetPx
                if (f <= 0f) {
                    var lo = outside
                    var hi = t
                    repeat(BISECT) {
                        val mid = (lo + hi) / 2f
                        if (field.dist(px + ux * mid, py + uy * mid) + offsetPx <= 0f) hi = mid else lo = mid
                    }
                    hit = hi
                    break
                }
                outside = t
                // Step by the smaller of the two measures: where the mixed direction is short, `dist` runs ahead of the
                // real distance, the plain field never does.
                t += max((min(field.lastD, f - offsetPx) + offsetPx) * STEP_SHARE, MIN_STEP_PX)
                steps++
            }
            out[2 * i] = px + ux * hit
            out[2 * i + 1] = py + uy * hit
        }
        return out
    }

    /** Room around both panes the tracing starts from, px. */
    private const val MARGIN_PX = 4f
    private const val MAX_STEPS = 96
    private const val BISECT = 6
    private const val STEP_SHARE = .9f
    private const val MIN_STEP_PX = .5f

    /** The joined field's distance as the cover measures it (`dist`), without allocating per point. */
    private class Field(val a: JoinBox, val b: JoinBox, val reach: Float) {
        private val ga = FloatArray(2)
        private val gb = FloatArray(2)

        /** The plain field's value at the last point measured. */
        var lastD: Float = 0f

        fun dist(px: Float, py: Float): Float {
            val da = LiquidJoin.sdBox(px, py, a)
            val db = LiquidJoin.sdBox(px, py, b)
            LiquidJoin.direction(px, py, a, 1f, ga)
            LiquidJoin.direction(px, py, b, 1f, gb)
            val facing = (0.5f - 0.5f * (ga[0] * gb[0] + ga[1] * gb[1])).coerceIn(0f, 1f)
            val inside =
                (1f - LiquidJoin.smoothstep(0.5f * LiquidJoin.depthOf(a), LiquidJoin.depthOf(a), -da)) *
                    (1f - LiquidJoin.smoothstep(0.5f * LiquidJoin.depthOf(b), LiquidJoin.depthOf(b), -db))
            val k = max(reach * facing * inside, 0.001f)
            val hh = max(k - abs(da - db), 0f) / k
            val m = hh * hh * 0.5f
            val w = if (da <= db) m else 1f - m
            val d = min(da, db) - hh * hh * hh * k / 6f
            lastD = d
            val gx = ga[0] + (gb[0] - ga[0]) * w
            val gy = ga[1] + (gb[1] - ga[1]) * w
            return d / max(sqrt(gx * gx + gy * gy), LiquidJoin.MIN_DIRECTION)
        }
    }
}
