package io.github.eslamasabry.oc.ui.kit.wait

import androidx.compose.ui.graphics.drawscope.DrawScope

/*
 * NEEDS YOU: a person, and beside them a small dot that knocks twice, softly, a heartbeat apart. After the second
 * knock the person looks toward it, holds, and looks away again; then a long quiet. It is a tap on the shoulder, not a
 * bell: the dot never flashes and nothing else moves (Fable's drawing). The dot is amber in both modes, the colour
 * the app already gives "Needs you": it is the only wait that is the person's to end. The person is in ink.
 */

private const val HEAD_X = 9f
private const val HEAD_Y = 6.4f
private const val HEAD_R = 3f
private const val LOOK_X = 1.1f
private const val LOOK_Y = -0.3f

// The shoulders: a dome standing on a short foot.
private const val SHOULDER_LEFT = 3.2f
private const val SHOULDER_RIGHT = 14.8f
private const val SHOULDER_TOP = 11.2f
private const val SHOULDER_Y = 17f
private const val SHOULDER_FOOT = 17.4f

private const val DOT_X = 16.4f
private const val DOT_Y = 4.4f
private const val DOT_R = 1.9f
private const val KNOCK = 0.4f

/** The width of the dot's edge on a light page, in units. */
private const val DOT_EDGE = 0.55f

private const val FIRST_KNOCK_FROM = 0.08f
private const val FIRST_KNOCK_BY = 0.26f
private const val SECOND_KNOCK_FROM = 0.32f
private const val SECOND_KNOCK_BY = 0.50f
private const val LOOKS_BY = 0.62f
private const val LOOKS_AWAY_FROM = 0.84f
private const val LOOKS_AWAY_BY = 0.96f

internal fun WaitPen.needsYou(scope: DrawScope, phase: Float, still: Boolean) {
    val t = if (still) 0f else phase
    val knock = bump(seg(t, FIRST_KNOCK_FROM, FIRST_KNOCK_BY)) + bump(seg(t, SECOND_KNOCK_FROM, SECOND_KNOCK_BY))
    val look = easeInOut(seg(t, SECOND_KNOCK_BY, LOOKS_BY)) - easeInOut(seg(t, LOOKS_AWAY_FROM, LOOKS_AWAY_BY))
    begin(SHOULDER_LEFT, SHOULDER_FOOT)
    lineTo(SHOULDER_LEFT, SHOULDER_Y)
    arch(SHOULDER_LEFT, SHOULDER_TOP, SHOULDER_RIGHT, SHOULDER_Y)
    lineTo(SHOULDER_RIGHT, SHOULDER_FOOT)
    with(scope) {
        fillShape()
        dot(HEAD_X + LOOK_X * look, HEAD_Y + LOOK_Y * look, HEAD_R)
        val r = DOT_R * (1f + KNOCK * knock)
        val edge = colours.callEdge
        if (edge != null) {
            paint = edge
            dot(DOT_X, DOT_Y, r)
        }
        paint = colours.call
        dot(DOT_X, DOT_Y, if (edge != null) r - DOT_EDGE else r)
    }
}
