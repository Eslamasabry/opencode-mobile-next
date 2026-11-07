package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/*
 * Shared drawing helpers of the looks (Dart `styles/kit_live_toolkit.dart`): value noise, colour mixing and a
 * low-resolution colour field drawn as one smooth vertex mesh (the GPU interpolates between the grid points, so a
 * coarse field upscales softly with nothing blurred and no per-frame image decoding).
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

/** Red, green, blue of [c] as 0..255 (Dart `rgb`). */
public fun rgb(c: Color): DoubleArray = doubleArrayOf(c.red * 255.0, c.green * 255.0, c.blue * 255.0) // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:41-43

/** Dart `mixRgb`. */
public fun mixRgb(a: DoubleArray, b: DoubleArray, t: Double): DoubleArray =
    doubleArrayOf(
        a[0] + (b[0] - a[0]) * t,
        a[1] + (b[1] - a[1]) * t,
        a[2] + (b[2] - a[2]) * t, // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:48
    )

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

/** Dart `packArgb`: 0..255 channels and a 0..1 alpha into one ARGB int. */
public fun packArgb(r: Double, g: Double, b: Double, a: Double): Int =
    ((a * 255).roundToLong().coerceIn(0, 255).toInt() shl 24) or // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:56
        (r.roundToLong().coerceIn(0, 255).toInt() shl 16) or // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:57
        (g.roundToLong().coerceIn(0, 255).toInt() shl 8) or // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:58
        b.roundToLong().coerceIn(0, 255).toInt() // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:43

/** Strength of a look: the demo's "A": 1 in dark, 0.7 in light, times the frame's strength (Dart `strengthOf`). */
public fun strengthOf(f: KitLiveFrame): Double = (if (f.palette.dark) 1.0 else .7) * f.strength // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:63

/** Dart's `%` on doubles: never negative for a positive divisor. */
internal fun dmod(a: Double, b: Double): Double {
    val r = a % b
    return if (r < 0) r + kotlin.math.abs(b) else r
}

/** The grid of a colour field, kept in a look's memory so a frame allocates nothing (Dart `_FieldCache`). */
internal class LiveFieldCache {
    var lw = 0
    var lh = 0
    var w = 0f
    var h = 0f
    var positions = FloatArray(0)
    var indices = ShortArray(0)
    var colors = IntArray(0)
    var lastCompute = -1e9
}

/** Gives the packed ARGB of a field at (u, v). */
public fun interface LiveFieldFn {
    public fun at(u: Double, v: Double): Int
}

/**
 * Dart `paintField`: draws a colour field where [fn] gives the packed ARGB at (u, v), u running 0..1 across and v
 * 0..height/width down. Sampled on a grid of about [div] logical pixels and recomputed at most every [every] seconds;
 * drawn as one triangle mesh with per-vertex colours (Skia's `drawVertices` on both platforms).
 */
public fun paintField(
    scope: DrawScope,
    size: Size,
    frame: KitLiveFrame,
    key: String,
    div: Int,
    every: Double = 0.0,
    fn: LiveFieldFn,
) {
    val c = frame.memory.of(key) { LiveFieldCache() }
    var lw = max(8, (size.width / div).roundToInt()) // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:87
    var lh = max(8, (size.height / div).roundToInt()) // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:88
    while ((lw + 1) * (lh + 1) > 65000) { // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:89
        lw = (lw * .8).roundToInt() // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:90
        lh = (lh * .8).roundToInt() // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:91
    }
    if (c.lw != lw || c.lh != lh || c.w != size.width || c.h != size.height) {
        c.lw = lw
        c.lh = lh
        c.w = size.width
        c.h = size.height
        c.lastCompute = -1e9
        val n = (lw + 1) * (lh + 1)
        c.positions = FloatArray(n * 2) // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:101
        c.colors = IntArray(n)
        var k = 0
        for (y in 0..lh) {
            for (x in 0..lw) {
                c.positions[k++] = x.toFloat() / lw * size.width
                c.positions[k++] = y.toFloat() / lh * size.height
            }
        }
        val idx = ShortArray(lw * lh * 6) // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:110
        k = 0
        for (y in 0 until lh) {
            for (x in 0 until lw) {
                val a = y * (lw + 1) + x
                val b = a + 1
                val d = a + lw + 1
                val e = d + 1
                idx[k++] = a.toShort()
                idx[k++] = b.toShort()
                idx[k++] = d.toShort()
                idx[k++] = b.toShort()
                idx[k++] = e.toShort()
                idx[k++] = d.toShort()
            }
        }
        c.indices = idx
    }
    if (frame.t - c.lastCompute >= every || frame.t < c.lastCompute) {
        c.lastCompute = frame.t
        val aspect = size.height.toDouble() / size.width
        var k = 0
        for (y in 0..lh) {
            val v = y.toDouble() / lh * aspect
            for (x in 0..lw) {
                c.colors[k++] = fn.at(x.toDouble() / lw, v)
            }
        }
    }
    drawLiveVertices(scope, lw + 1, lh + 1, c.positions, c.colors, c.indices)
}

/** Draws a triangle mesh whose vertex colours are interpolated across each triangle (Flutter `drawVertices`). */
internal expect fun drawLiveVertices(
    scope: DrawScope,
    cols: Int,
    rows: Int,
    positions: FloatArray,
    colors: IntArray,
    indices: ShortArray,
)
