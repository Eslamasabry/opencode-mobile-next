package io.github.eslamasabry.oc.ui.kit.wait

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import kotlin.math.cos
import kotlin.math.sin

/*
 * STARTING, the default: a sunrise (Astra's drawing). The sun comes up out of the horizon, slow at both ends like a
 * real one; its three rays fan out with a little weight; it stands a while; the rays draw in, it sinks back to a
 * sliver on the horizon and rests there a beat. Something is coming up, and it is not up yet. The edge of the earth is
 * a cut in the ink: the sun never shows through the horizon. Drawn on a 24-unit grid.
 * Colour: the sun and its rays in the warm role (light); the horizon in ink.
 */

private const val SUN_X = 12f
private const val SUN_R = 5.5f

/** The sun's middle when it is up, and when only a sliver shows over the horizon. */
private const val SUN_UP_Y = 12f
private const val SUN_DOWN_Y = 18.4f
private const val CUT = 14.5f
private const val HORIZON_Y = 17f
private const val HORIZON_LEFT = 2f
private const val HORIZON_RIGHT = 22f
private const val UNDER_Y = 20.5f
private const val UNDER_LEFT = 8f
private const val UNDER_RIGHT = 16f

/** The rays start this far from the sun's middle and grow to this length, at these angles (degrees, 0 = right). */
private const val RAY_FROM = 8f
private const val RAY_LENGTH = 2.4f
private const val RAY_SHORTEST = 0.15f
private const val RAYS = 3
private const val FIRST_RAY_DEGREES = -135f
private const val RAY_APART_DEGREES = 45f
private const val DEGREES = 0.017453292f

private const val RISE_FROM = 0.04f
private const val RISE_BY = 0.36f

/** The rays fan out one gesture: each a little after the one before it. */
private const val RAYS_FROM = 0.33f
private const val RAY_TAKES = 0.10f
private const val RAY_STAGGER = 0.03f
private const val RAYS_IN_FROM = 0.70f
private const val RAYS_IN_BY = 0.78f
private const val SET_FROM = 0.74f
private const val SET_BY = 0.95f

/** Phase 0 (and the still) is here in the loop: the sun up, its rays out. */
private const val LEAD = 0.58f

internal fun WaitPen.sunrise(scope: DrawScope, phase: Float, still: Boolean) {
    val t = if (still) LEAD else turn(phase + LEAD)
    val up = easeInOut(seg(t, RISE_FROM, RISE_BY)) - easeInOut(seg(t, SET_FROM, SET_BY))
    val y = SUN_DOWN_Y + (SUN_UP_Y - SUN_DOWN_Y) * up
    val raysIn = 1f - easeIn(seg(t, RAYS_IN_FROM, RAYS_IN_BY))
    with(scope) {
        paint = colours.warm
        clipRect(bottom = CUT * u) {
            dot(SUN_X, y, SUN_R)
            repeat(RAYS) { i ->
                val from = RAYS_FROM + i * RAY_STAGGER
                val length = RAY_LENGTH * settle(seg(t, from, from + RAY_TAKES)) * raysIn
                if (length >= RAY_SHORTEST) ray(this, y, (FIRST_RAY_DEGREES + i * RAY_APART_DEGREES) * DEGREES, length)
            }
        }
        paint = ink
        line(HORIZON_LEFT, HORIZON_Y, HORIZON_RIGHT, HORIZON_Y)
        line(UNDER_LEFT, UNDER_Y, UNDER_RIGHT, UNDER_Y)
    }
}

/** One ray of [length], starting [RAY_FROM] from the sun's middle (at height [y]) at [angle] (radians). */
private fun WaitPen.ray(scope: DrawScope, y: Float, angle: Float, length: Float) {
    val dx = cos(angle)
    val dy = sin(angle)
    val end = RAY_FROM + length
    with(scope) { line(SUN_X + dx * RAY_FROM, y + dy * RAY_FROM, SUN_X + dx * end, y + dy * end) }
}

/*
 * STARTING, the alternate: three columns come up from the ground one after another, each a little higher than the
 * last, the way a machine comes up (the phone's Linux, then its server, then what runs on it) (Fable's drawing). Each
 * lands with a little weight. They stand for a beat, sink together, rest as three seeds, and come up again.
 * Colour: the last and tallest column, what runs on top and the thing being waited for, in the accent; the two under
 * it in ink.
 */

private const val COLUMNS = 3
private const val GROUND = 17f
private const val WIDTH = 3.4f
private const val FIRST_X = 4.7f
private const val PITCH = 5.3f
private const val FIRST_HEIGHT = 7.4f
private const val HEIGHT_STEP = 3.8f

/** One column starts this far (of the loop) after the one before it, and takes [RISE] to come up. */
private const val STAGGER = 0.18f
private const val RISE = 0.16f
private const val SINK_FROM = 0.78f
private const val SINK_TO = 0.90f

/** Phase 0 (and the still) is here in the loop: two columns up, the third not yet. */
private const val COLUMNS_LEAD = 0.345f

internal fun WaitPen.columns(scope: DrawScope, phase: Float, still: Boolean) {
    val t = if (still) COLUMNS_LEAD else turn(phase + COLUMNS_LEAD)
    val sunk = 1f - easeInOut(seg(t, SINK_FROM, SINK_TO))
    with(scope) {
        repeat(COLUMNS) { i ->
            val up = settle(seg(t, i * STAGGER, i * STAGGER + RISE)) * sunk
            val height = WIDTH + (FIRST_HEIGHT + i * HEIGHT_STEP - WIDTH) * up
            val x = FIRST_X + i * PITCH
            // The last and tallest column is the thing being waited for.
            paint = if (i == COLUMNS - 1) colours.live else ink
            column(x - WIDTH / 2f, GROUND - height, x + WIDTH / 2f, GROUND)
        }
    }
}
