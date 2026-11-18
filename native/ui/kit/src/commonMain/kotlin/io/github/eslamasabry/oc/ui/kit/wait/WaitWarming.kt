package io.github.eslamasabry.oc.ui.kit.wait

import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sin

/*
 * WARMING: a cup with steam (Astra's drawing). Two wisps of steam take turns: each grows up out of the cup, sways
 * as it climbs, lets go of the cup and rises away off the top, while the other is already on its way. The words beside
 * it say "warming up": something is being made ready, and it is not ready yet. Drawn on a 24-unit grid.
 * Colour: the steam in the warm role (heat); the cup and its saucer in ink.
 */

private const val WISPS = 2
private const val FIRST_WISP_X = 8f
private const val WISP_PITCH = 5f

/** Where a wisp leaves the cup, and how far above that it can climb before it is gone. */
private const val ROOT_Y = 9.2f
private const val CLIMB = 7.4f

/** The longest a wisp is (it never fills the whole climb, so it reads as a wisp and not a pole). */
private const val LONGEST = 4.8f
private const val SWAY = 1.05f
private const val WAVE = 5.2f

/** The sway grows from nothing at the root to its full width over this height, so the root stays put. */
private const val ROOT_CALM = 1.8f
private const val SAMPLE = 0.5f
private const val SHORTEST = 0.35f

/** A wisp's life: it grows to its length, climbs with it, and its tail catches up with its head at the top. */
private const val GROWN_BY = 0.34f
private const val CLIMBS_TO = 0.86f
private const val LETS_GO_AT = 0.30f

/** Each wisp starts half a loop after the other. */
private const val TURN_TAKING = 0.5f

/** The still: one wisp tall, the other shorter, as the cup is drawn. */
private const val STILL_LIFE_FIRST = 0.36f
private const val STILL_LIFE_SECOND = 0.14f

/** One whole turn, in radians. */
private const val FULL_TURN = 6.2831855f

internal fun WaitPen.cup(scope: DrawScope, phase: Float, still: Boolean) {
    val t = if (still) 0f else phase
    repeat(WISPS) { i ->
        val life =
            if (still) (if (i == 0) STILL_LIFE_FIRST else STILL_LIFE_SECOND) else turn(t - i * TURN_TAKING)
        wisp(scope, FIRST_WISP_X + i * WISP_PITCH, life, t)
    }
    with(scope) {
        // The handle, then the bowl, then the saucer.
        paint = ink
        unitShape(CUP_HANDLE, fill = false)
        unitShape(CUP_BOWL)
        line(SAUCER_LEFT, SAUCER_Y, SAUCER_RIGHT, SAUCER_Y)
    }
}

/** The cup, in the 24-unit grid. */
private val CUP_HANDLE = waitShape("M16 12 C23.5 9.5 23.2 17.6 16 17.2")
private val CUP_BOWL = waitShape("M3.4 11 L18.1 11 C17.9 16.5 16.1 19.2 12.9 19.2 L8.8 19.2 C5.2 19.2 3.6 16 3.4 11 Z")
private const val SAUCER_LEFT = 4.8f
private const val SAUCER_RIGHT = 17.6f
private const val SAUCER_Y = 21.8f

/** One wisp rising from [x], [life] of the way through its own turn; its sway travels up once per loop ([t]). */
private fun WaitPen.wisp(scope: DrawScope, x: Float, life: Float, t: Float) {
    val grown = LONGEST * easeOut(seg(life, 0f, GROWN_BY))
    val head = min(CLIMB, grown + (CLIMB - LONGEST) * easeInOut(seg(life, LETS_GO_AT, CLIMBS_TO)))
    val tail = CLIMB * easeIn(seg(life, LETS_GO_AT, 1f))
    if (head - tail < SHORTEST) return
    val pieces = ceil((head - tail) / SAMPLE).toInt()
    for (k in 0..pieces) {
        val up = tail + (head - tail) * k / pieces
        val px = x + SWAY * min(1f, up / ROOT_CALM) * sin(FULL_TURN * (up / WAVE - t))
        if (k == 0) begin(px, ROOT_Y - up) else lineTo(px, ROOT_Y - up)
    }
    paint = colours.warm
    with(scope) { strokeShape() }
}
