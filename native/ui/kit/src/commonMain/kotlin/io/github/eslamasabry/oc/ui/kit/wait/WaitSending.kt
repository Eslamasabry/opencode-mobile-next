package io.github.eslamasabry.oc.ui.kit.wait

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.util.lerp

/*
 * SENDING, the alternate: the person's message as a paper plane (Fable's drawing). It leans back, flies off along its own nose and grows small with
 * distance, leaving two strokes of air; a breath later the next one glides in from behind and settles. It never
 * arrives in the picture: the message is on its way, not there.
 * Colour: the plane in the accent (the app's colour for its own things on the move); its air in ink.
 */

// The plane at rest, nose up and forward: nose, wing tip, the fold (one point on each side of it), keel, middle.
private const val NOSE_X = 17.6f
private const val NOSE_Y = 2.6f
private const val WING_X = 2.2f
private const val WING_Y = 8.4f
private const val FOLD_WING_X = 7.82f
private const val FOLD_WING_Y = 10.99f
private const val FOLD_KEEL_X = 8.38f
private const val FOLD_KEEL_Y = 11.61f
private const val KEEL_X = 11.4f
private const val KEEL_Y = 17.6f
private const val MID_X = 10f
private const val MID_Y = 9.6f

// The way it flies (middle to nose, length 1) and the way across it.
private const val FLY_X = 0.7355f
private const val FLY_Y = -0.6775f
private const val ACROSS_X = 0.6775f
private const val ACROSS_Y = 0.7355f

private const val LEAN_FROM = 0.14f
private const val LEAN_BY = 0.28f
private const val LEAN_BACK = 1.5f
private const val GONE_BY = 0.58f
private const val FLIGHT = 10.5f
private const val LEFT_AT = 0.60f
private const val SMALLEST = 0.04f

private const val NEXT_FROM = 0.62f
private const val NEXT_BY = 0.92f
private const val NEXT_BEHIND = 8.5f
private const val NEXT_SMALL = 0.3f

// Two strokes of air behind the plane: across the way it flies, and from where to where along it.
private const val AIR_FROM = 0.34f
private const val AIR_MADE_BY = 0.50f
private const val AIR_GONE_BY = 0.70f
private const val AIR_WIDTH = 0.8f
private const val AIR_SHORTEST = 0.02f
private const val AIR_ONE_ACROSS = 1.7f
private const val AIR_ONE_BACK = -9.4f
private const val AIR_ONE_FRONT = -5.6f
private const val AIR_TWO_ACROSS = -1.5f
private const val AIR_TWO_BACK = -7.6f
private const val AIR_TWO_FRONT = -4.4f

internal fun WaitPen.plane(scope: DrawScope, phase: Float, still: Boolean) {
    val t = if (still) 0f else phase
    val gone = easeIn(seg(t, LEAN_BY, GONE_BY))
    if (t < LEFT_AT && 1f - gone > SMALLEST) {
        val lean = easeInOut(seg(t, LEAN_FROM, LEAN_BY))
        paperPlane(scope, FLIGHT * gone - LEAN_BACK * lean * (1f - gone), 1f - gone)
    }
    if (t > NEXT_FROM) {
        val come = spring(seg(t, NEXT_FROM, NEXT_BY))
        paperPlane(scope, NEXT_BEHIND * (come - 1f), NEXT_SMALL + (1f - NEXT_SMALL) * come.coerceAtMost(1f))
    }
    // In the still the air is whole: a plane with air behind it is a plane in flight.
    val made = if (still) 1f else easeOut(seg(t, AIR_FROM, AIR_MADE_BY))
    val faded = if (still) 0f else easeInOut(seg(t, AIR_MADE_BY, AIR_GONE_BY))
    if (made - faded > AIR_SHORTEST) {
        air(scope, AIR_ONE_ACROSS, lerp(AIR_ONE_BACK, AIR_ONE_FRONT, faded), lerp(AIR_ONE_BACK, AIR_ONE_FRONT, made))
        air(scope, AIR_TWO_ACROSS, lerp(AIR_TWO_BACK, AIR_TWO_FRONT, faded), lerp(AIR_TWO_BACK, AIR_TWO_FRONT, made))
    }
}

/** The plane moved [along] its flight and drawn at [scale] about its middle: the wing, then the keel under the fold. */
private fun WaitPen.paperPlane(scope: DrawScope, along: Float, scale: Float) {
    val dx = MID_X + FLY_X * along
    val dy = MID_Y + FLY_Y * along
    begin(dx + (NOSE_X - MID_X) * scale, dy + (NOSE_Y - MID_Y) * scale)
    lineTo(dx + (WING_X - MID_X) * scale, dy + (WING_Y - MID_Y) * scale)
    lineTo(dx + (FOLD_WING_X - MID_X) * scale, dy + (FOLD_WING_Y - MID_Y) * scale)
    with(scope) { fillShape(colours.live) }
    begin(dx + (NOSE_X - MID_X) * scale, dy + (NOSE_Y - MID_Y) * scale)
    lineTo(dx + (FOLD_KEEL_X - MID_X) * scale, dy + (FOLD_KEEL_Y - MID_Y) * scale)
    lineTo(dx + (KEEL_X - MID_X) * scale, dy + (KEEL_Y - MID_Y) * scale)
    with(scope) { fillShape(colours.live) }
}

/** One stroke of air, [across] the flight, from [back] to [front] along it. */
private fun WaitPen.air(scope: DrawScope, across: Float, back: Float, front: Float) {
    val x = MID_X + ACROSS_X * across
    val y = MID_Y + ACROSS_Y * across
    with(scope) { line(x + FLY_X * back, y + FLY_Y * back, x + FLY_X * front, y + FLY_Y * front, AIR_WIDTH * stroke) }
}

/*
 * SENDING, the default: a paper dart with two lines of speed behind it (Astra's drawing). It rests; it draws back a
 * little; it is thrown and flies off along its nose, growing small with distance while its speed lines are pulled
 * away after it; a breath later the next dart glides in from behind, settles, and its speed lines come back.
 * Drawn on a 24-unit grid. Colour: the dart in the accent (the app's colour for its own things on the move); the
 * speed lines in ink.
 */

/** The dart's middle, and the way it flies (middle to nose, length 1). */
private const val DART_MID_X = 12.1f
private const val DART_MID_Y = 11.8f
private const val DART_FLY_X = 0.765f
private const val DART_FLY_Y = -0.643f

private const val DART_DRAW_BACK_FROM = 0.08f
private const val DART_DRAW_BACK_BY = 0.22f
private const val DART_DRAW_BACK = 1.6f
private const val DART_THROWN_BY = 0.54f
private const val DART_FLIGHT = 9.5f
private const val DART_SMALLEST = 0.05f
private const val DART_NEXT_FROM = 0.56f
private const val DART_NEXT_BY = 0.90f
private const val DART_NEXT_BEHIND = 8f
private const val DART_NEXT_SMALL = 0.3f

/** The speed lines, each from (x0, y0) to (x1, y1). */
private val SPEED = arrayOf(floatArrayOf(1.5f, 13.7f, 3.4f, 12.9f), floatArrayOf(3.3f, 18.6f, 5.8f, 17.5f))
private const val PULL_FROM = 0.26f
private const val PULLED_BY = 0.44f
private const val BACK_FROM = 0.80f
private const val BACK_BY = 0.94f
private const val SPEED_SHORTEST = 0.04f

internal fun WaitPen.dart(scope: DrawScope, phase: Float, still: Boolean) {
    val t = if (still) 0f else phase
    val gone = easeIn(seg(t, DART_DRAW_BACK_BY, DART_THROWN_BY))
    val drawnBack = easeInOut(seg(t, DART_DRAW_BACK_FROM, DART_DRAW_BACK_BY)) * (1f - gone)
    if (t < DART_NEXT_FROM && 1f - gone > DART_SMALLEST) {
        paperDart(scope, DART_FLIGHT * gone - DART_DRAW_BACK * drawnBack, 1f - gone)
    }
    if (t > DART_NEXT_FROM) {
        val come = spring(seg(t, DART_NEXT_FROM, DART_NEXT_BY))
        paperDart(scope, DART_NEXT_BEHIND * (come - 1f), DART_NEXT_SMALL + (1f - DART_NEXT_SMALL) * come.coerceAtMost(1f))
    }
    // The speed lines: pulled away after the dart as it is thrown, grown back behind the next one. Each is drawn
    // between two places along it, 0 its back end and 1 its front end.
    val back = easeInOut(seg(t, PULL_FROM, PULLED_BY)) * (1f - step(t, BACK_FROM))
    val front = if (t < BACK_FROM) 1f else easeOut(seg(t, BACK_FROM, BACK_BY))
    if (front - back > SPEED_SHORTEST) {
        for (s in SPEED) {
            val dx = s[2] - s[0]
            val dy = s[3] - s[1]
            with(scope) { line(s[0] + dx * back, s[1] + dy * back, s[0] + dx * front, s[1] + dy * front) }
        }
    }
}

private fun step(t: Float, at: Float): Float = if (t < at) 0f else 1f

/** The dart moved [along] its flight and drawn at [scale] about its middle: two folds and a tapered cut. */
private fun WaitPen.paperDart(scope: DrawScope, along: Float, scale: Float) {
    scope.withTransform({
        translate(DART_FLY_X * along * u, DART_FLY_Y * along * u)
        scale(scale, scale, Offset(DART_MID_X * u, DART_MID_Y * u))
    }) {
        begin(3.3f, 9.6f)
        lineTo(20.9f, 4.4f)
        lineTo(15.7f, 19.2f)
        lineTo(10.2f, 14f)
        lineTo(17.4f, 7.2f)
        lineTo(8.3f, 12.4f)
        close()
        moveTo(8.4f, 14.1f)
        lineTo(11.1f, 16.8f)
        lineTo(7.9f, 18.8f)
        fillShape(colours.live)
    }
}
