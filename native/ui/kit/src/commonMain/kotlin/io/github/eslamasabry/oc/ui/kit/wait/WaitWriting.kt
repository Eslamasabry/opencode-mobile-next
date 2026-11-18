package io.github.eslamasabry.oc.ui.kit.wait

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sin

/*
 * WRITING, the alternate: a page being written (Fable's drawing). Two lines are already there; the third is written behind a caret, a word, a
 * breath, the rest of the line. Then the page moves up one line, the oldest line leaves, and the caret is back at the
 * start of an empty line: exactly where the loop began, so it can go on for as long as the agent writes.
 * Colour: the caret and the line it is writing in the accent; once the page moves on, that line settles into ink.
 */

private const val LEFT = 3f
private const val RIGHT = 17f
private const val TOP_Y = 4.6f
private const val PITCH = 5f

/** The caret stands just inside the line's two ends. */
private const val INSET = 0.2f

/** Air between the words and the caret. */
private const val CARET_GAP = 2.4f
private const val CARET_HALF = 1.9f
private const val CARET_SHORTEST = 0.25f
private const val HARD_CARET = 1.25f
private const val SHORTEST = 0.3f

private const val FIRST_WORD = 0.45f
private const val FIRST_WORD_BY = 0.20f
private const val SECOND_WORD_FROM = 0.29f
private const val SECOND_WORD_BY = 0.50f
private const val MOVE_FROM = 0.62f
private const val MOVE_BY = 0.88f

/** How far up the leaving line goes (of one line) while it is taken back from its end. */
private const val LEAVING_RISE = 0.5f
private const val LEFT_AT = 0.97f

/** Phase 0 is here in the loop: the first word half written. */
private const val LEAD = 0.10f

/** The still: one word written, the caret after it. */
private const val STILL_AT = 0.24f

internal fun WaitPen.lines(scope: DrawScope, phase: Float, still: Boolean) {
    val t = if (still) STILL_AT else turn(phase + LEAD)
    val wrote =
        FIRST_WORD * easeInOut(seg(t, 0f, FIRST_WORD_BY)) +
            (1f - FIRST_WORD) * easeInOut(seg(t, SECOND_WORD_FROM, SECOND_WORD_BY))
    val moved = easeInOut(seg(t, MOVE_FROM, MOVE_BY))
    val from = LEFT + INSET
    val reach = RIGHT - INSET - from
    with(scope) {
        // The oldest line leaves: up a little, taken back from its end.
        val leavingY = TOP_Y - PITCH * LEAVING_RISE * moved
        if (moved < LEFT_AT) line(LEFT, leavingY, RIGHT - (RIGHT - LEFT) * moved, leavingY)
        val secondY = TOP_Y + PITCH - PITCH * moved
        line(LEFT, secondY, RIGHT, secondY)
        // The written line: behind the caret while it is written, whole once the page has moved.
        val written = from + reach * wrote - CARET_GAP
        val end = written + (RIGHT - written) * moved
        val writtenY = TOP_Y + 2f * PITCH - PITCH * moved
        if (end > LEFT + SHORTEST) line(LEFT, writtenY, end, writtenY, color = lerp(colours.live, ink, moved))
        caret(this, from + reach * wrote * (1f - moved), moved)
    }
}

/** The caret stays on the bottom line; on its way back to the line's start it closes and opens again. */
private fun WaitPen.caret(scope: DrawScope, x: Float, moved: Float) {
    val half = CARET_HALF * easeInOut(abs(1f - 2f * moved))
    if (half < CARET_SHORTEST) return
    val y = TOP_Y + 2f * PITCH
    with(scope) { line(x, y - half, x, y + half, if (square) HARD_CARET * stroke else stroke, colours.live) }
}

/*
 * WRITING, the default: a pen nib writing (Astra's drawing). The nib sets down and writes a line of handwriting from
 * left to right, the line appearing behind its point; it lifts off the page, the line is taken away from its start as
 * the nib floats back, and it sets down again where it began. Drawn on a 24-unit grid.
 * Colour: the written line in the accent (what the agent is making); the nib in ink.
 */

private const val STROKE_FROM = 1.6f
private const val STROKE_TO = 11.4f
private const val STROKE_BASE = 19.4f
private const val STROKE_CLIMB = 0.2f
private const val STROKE_WAVE = 1.15f
private const val STROKE_WAVE_LENGTH = 6.5f
private const val STROKE_SAMPLE = 0.4f
private const val NIB_LIFT = 2.2f

private const val WRITE_FROM = 0.06f
private const val WRITE_BY = 0.56f
private const val LIFT_BY = 0.64f
private const val TAKEN_FROM = 0.62f
private const val TAKEN_BY = 0.84f
private const val BACK_BY = 0.90f
private const val SET_DOWN_FROM = 0.88f
private const val SET_DOWN_BY = 0.98f

/** Phase 0 is here in the loop: the line half written. The still: most of the line written, the nib on it. */
private const val NIB_LEAD = 0.36f
private const val NIB_STILL = 0.48f

private const val TWO_PI = 6.2831855f

/** The handwriting: a gentle wave that climbs a little to the right. */
private fun strokeY(x: Float): Float {
    val along = x - STROKE_FROM
    return STROKE_BASE - STROKE_CLIMB * along - STROKE_WAVE * sin(TWO_PI * along / STROKE_WAVE_LENGTH)
}

internal fun WaitPen.nib(scope: DrawScope, phase: Float, still: Boolean) {
    val t = if (still) NIB_STILL else turn(phase + NIB_LEAD)
    val reach = STROKE_TO - STROKE_FROM
    val end = STROKE_FROM + reach * easeInOut(seg(t, WRITE_FROM, WRITE_BY))
    val start = STROKE_FROM + reach * easeInOut(seg(t, TAKEN_FROM, TAKEN_BY))
    val back = easeInOut(seg(t, LIFT_BY, BACK_BY))
    val tipX = end - (end - STROKE_FROM) * back
    val lift = NIB_LIFT * (easeOut(seg(t, WRITE_BY, LIFT_BY)) - easeIn(seg(t, SET_DOWN_FROM, SET_DOWN_BY)))
    val tipY = strokeY(tipX) - lift
    if (end - start > STROKE_SAMPLE / 2f) {
        val pieces = ceil((end - start) / STROKE_SAMPLE).toInt()
        for (k in 0..pieces) {
            val x = start + (end - start) * k / pieces
            if (k == 0) begin(x, strokeY(x)) else lineTo(x, strokeY(x))
        }
        with(scope) { strokeShape(colours.live) }
    }
    scope.translate(tipX * u, tipY * u) {
        // The breather hole and the slit cut all the way through to the page.
        begin(0f, 0f)
        lineTo(1.1f, -8.3f)
        lineTo(5.5f, -12.6f)
        lineTo(11f, -7.1f)
        lineTo(7.3f, -2.7f)
        lineTo(1.3f, -0.2f)
        lineTo(4.9f, -4.8f)
        cubicTo(7f, -4.7f, 7f, -7.9f, 5f, -7.9f)
        cubicTo(3.5f, -7.9f, 3.1f, -6.1f, 4f, -5.3f)
        fillShape()
        line(7.3f, -13.1f, 11.5f, -8.9f)
    }
}
