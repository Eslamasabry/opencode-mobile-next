package io.github.eslamasabry.oc.ui.kit.live.looks

import kotlin.math.sin

/** One slow swing: `mid + swing·sin(speed·t + phase)`. */
internal class LiveWave(
    private val mid: Float,
    private val swing: Float,
    private val speed: Float,
    private val phase: Float = 0f,
) {
    fun at(t: Float): Float = mid + swing * sin(speed * t + phase)
}

/**
 * The slow path of a point a look moves on the CPU once a frame (mesh points, blob centres), in shares of the page,
 * with an optional size [r] (a blob's radius). Built once; reading it allocates nothing.
 */
internal class LiveOrbit(private val x: LiveWave, private val y: LiveWave, val r: Float = 0f) {
    fun x(t: Float): Float = x.at(t)

    fun y(t: Float): Float = y.at(t)
}

/** A quarter turn: shifts a sine into a cosine. */
internal const val QUARTER_TURN: Float = 1.5707964f
