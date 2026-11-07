package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.ui.graphics.Color
import kotlin.math.floor

/*
 * Shared drawing helpers of the looks (Dart `styles/kit_live_toolkit.dart`): value noise and colour mixing; the
 * colour field is in KitLiveField.kt.
 *
 * Interim copy of order 31's toolkit (orders 31-33 run in parallel); every look of 32 draws through it.
 */

internal const val MASK32: Long = 0xFFFFFFFFL

private fun imul(a: Long, b: Long): Long = (a * b) and MASK32

/** Dart `hash2`: an integer hash of a grid point into 0..1. */
public fun hash2(x: Long, y: Long): Double {
    var h = imul(x and MASK32, 374761393) xor imul(y and MASK32, 668265263) // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:16
    h = imul(h xor (h ushr 13), 1274126177) // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:17
    return ((h xor (h ushr 16)) and MASK32).toDouble() / 4294967295.0 // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:18
}

/** Dart `valueNoise`. */
public fun valueNoise(x: Double, y: Double): Double {
    val xi = floor(x).toLong()
    val yi = floor(y).toLong()
    val xf = x - xi
    val yf = y - yi
    val u = xf * xf * (3 - 2 * xf) // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:24
    val v = yf * yf * (3 - 2 * yf) // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:24
    val a = hash2(xi, yi)
    val b = hash2(xi + 1, yi)
    val c = hash2(xi, yi + 1)
    val d = hash2(xi + 1, yi + 1)
    return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v
}

/** Dart `fbm`: three octaves of value noise, 0..1. */
public fun fbm(x: Double, y: Double): Double =
    valueNoise(x, y) * .6 + // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:33
        valueNoise(x * 2.03 + 5.2, y * 2.03 + 1.3) * .3 + // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:34
        valueNoise(x * 4.1 + 9.1, y * 4.1 + 3.7) * .1 // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:35

/** Dart `smooth`: smoothstep. */
public fun smooth(e0: Double, e1: Double, x: Double): Double {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0.0, 1.0)
    return t * t * (3 - 2 * t) // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:39
}

/**
 * Dart `Color.lerp` (each channel linear in sRGB), clamped as `mixColor`. Never Compose's `lerp`, which mixes in Oklab
 * and would change every look's colour.
 */
public fun mixColor(a: Color, b: Color, t: Double): Color {
    val k = t.coerceIn(0.0, 1.0).toFloat()
    return Color(
        red = (a.red + (b.red - a.red) * k).coerceIn(0f, 1f),
        green = (a.green + (b.green - a.green) * k).coerceIn(0f, 1f),
        blue = (a.blue + (b.blue - a.blue) * k).coerceIn(0f, 1f),
        alpha = (a.alpha + (b.alpha - a.alpha) * k).coerceIn(0f, 1f),
    )
}

/** Dart `withA`: [c] at alpha [a], clamped. */
public fun withA(c: Color, a: Double): Color = c.copy(alpha = a.coerceIn(0.0, 1.0).toFloat())

/** Strength of a look: the demo's "A": 1 in dark, 0.7 in light, times the frame's strength (Dart `strengthOf`). */
public fun strengthOf(f: KitLiveFrame): Double = (if (f.palette.dark) 1.0 else .7) * f.strength // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:63

