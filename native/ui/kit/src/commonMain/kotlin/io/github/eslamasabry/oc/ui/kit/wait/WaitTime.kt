package io.github.eslamasabry.oc.ui.kit.wait

import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

// ---- time: every mark is a function of one number, so phase 1 draws what phase 0 draws ----

private const val HALF = 0.5f
private const val BACK = 1.7f

/** [x] wrapped into 0..1: one whole turn is no turn. */
internal fun turn(x: Float): Float = x - floor(x)

/** How far [t] is through the stretch [from]..[to], 0 before it and 1 after it. */
internal fun seg(t: Float, from: Float, to: Float): Float = ((t - from) / (to - from)).coerceIn(0f, 1f)

/** Fast, then settling. */
internal fun easeOut(x: Float): Float {
    val d = 1f - x
    return 1f - d * d * d
}

/** Slow, then falling: a thing let go. */
internal fun easeIn(x: Float): Float = x * x * x

/** Eased at both ends. */
internal fun easeInOut(x: Float): Float {
    val d = if (x < HALF) 2f * x else 2f - 2f * x
    val half = d * d * d / 2f
    return if (x < HALF) half else 1f - half
}

/** [easeOut] that passes 1 by about a tenth and comes back. */
internal fun overshoot(x: Float): Float {
    val d = x - 1f
    return 1f + (BACK + 1f) * d * d * d + BACK * d * d
}

private const val GLIDE_RAMP = 0.12f

/** An even pace with short soft ends (a hand writing): speeds up over the first [GLIDE_RAMP], slows over the last. */
internal fun glide(x: Float): Float {
    val top = 1f / (1f - GLIDE_RAMP)
    return when {
        x <= 0f -> 0f
        x >= 1f -> 1f
        x < GLIDE_RAMP -> top * x * x / (2f * GLIDE_RAMP)
        x < 1f - GLIDE_RAMP -> top * (x - GLIDE_RAMP / 2f)
        else -> 1f - top * (1f - x) * (1f - x) / (2f * GLIDE_RAMP)
    }
}

/** One soft beat: 0, up to 1 in the middle, back to 0. */
internal fun bump(x: Float): Float {
    val s = sin(PI.toFloat() * x)
    return s * s
}
