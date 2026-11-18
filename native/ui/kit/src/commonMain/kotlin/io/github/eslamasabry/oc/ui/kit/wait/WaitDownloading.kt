package io.github.eslamasabry.oc.ui.kit.wait

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect

/*
 * DOWNLOADING: the arrow-over-a-line everyone knows, with the line doing a real job: it is the download. The thin
 * line is the whole of it, the thick part is what has arrived. Above it an arrow grows down, rests, then sinks into
 * the line, which gives a little under it; then the next arrow. At 0 the line is all thin; at 1 it is all thick and
 * the arrow has landed on it and stays: nothing more is coming.
 */

private const val LINE_LEFT = 3f
private const val LINE_RIGHT = 17f
private const val LINE_Y = 16.4f
private const val ARRIVED_WIDTH = 1.5f
private const val WHOLE_WIDTH = 0.5f

/** Air between the arrow's point and the line. */
private const val CLEARANCE = 0.9f

private const val CENTRE_X = 10f
private const val STEM_TOP = 2.6f
private const val POINT_REST = 12.4f
private const val HEAD = 3.7f

/** How far past the line's edge the arrow's point goes, so the whole arrow is under it. */
private const val SINK_DEPTH = 13.6f
private const val SHORTEST = 0.2f

private const val GROWS_BY = 0.32f

/** The next arrow starts this long before the loop comes round, so the air above the line is not empty for long. */
private const val NEXT_EARLY = 0.08f
private const val SINK_FROM = 0.52f
private const val SINK_TO = 0.80f
private const val GIVE_FROM = 0.74f
private const val GIVE_TO = 0.92f
private const val GIVE = 0.8f

/** Phase 0 (and the still) is here in the loop: the arrow whole, resting over the line. */
private const val LEAD = 0.40f

internal fun WaitPen.downloading(scope: DrawScope, phase: Float, fraction: Float, still: Boolean) {
    val arrived = fraction.coerceIn(0f, 1f)
    val landed = arrived >= 1f
    val t = if (still) LEAD else turn(phase + LEAD)
    val y = LINE_Y + if (landed) 0f else GIVE * bump(seg(t, GIVE_FROM, GIVE_TO))
    val floor = LINE_Y - ARRIVED_WIDTH * stroke / 2f - CLEARANCE
    val grown = if (landed) 1f else spring(seg(turn(t + NEXT_EARLY), 0f, GROWS_BY))
    val sunk = if (landed || t >= 1f - NEXT_EARLY) 0f else SINK_DEPTH * easeIn(seg(t, SINK_FROM, SINK_TO))
    val point = if (landed) floor else STEM_TOP + (POINT_REST - STEM_TOP) * grown + sunk
    val stemTop = if (landed) floor - (POINT_REST - STEM_TOP) else STEM_TOP + sunk
    with(scope) {
        line(LINE_LEFT, y, LINE_RIGHT, y, WHOLE_WIDTH * stroke)
        // Colour: what has arrived is in the accent; what is still to come, and the arrow bringing it, in ink.
        if (arrived > 0f) {
            line(LINE_LEFT, y, LINE_LEFT + (LINE_RIGHT - LINE_LEFT) * arrived, y, ARRIVED_WIDTH * stroke, colours.live)
        }
        // The line is the ground: what goes under its edge is gone, and the head folds in as its point goes under.
        val head = (HEAD * grown.coerceIn(0f, 1f) - (point - floor).coerceAtLeast(0f)).coerceAtLeast(0f)
        clipRect(bottom = floor * u) { arrow(this, stemTop, point, head) }
    }
}

private fun WaitPen.arrow(scope: DrawScope, stemTop: Float, point: Float, head: Float) {
    if (point - stemTop < SHORTEST) return
    begin(CENTRE_X - head, point - head)
    lineTo(CENTRE_X, point)
    lineTo(CENTRE_X + head, point - head)
    with(scope) {
        line(CENTRE_X, stemTop, CENTRE_X, point)
        strokeShape()
    }
}
