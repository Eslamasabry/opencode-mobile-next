// One Kotlin file per Dart file (kit_live_toolkit.dart): its helpers stay together.
@file:Suppress("TooManyFunctions")

package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.relativeLuminance
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * Shared drawing helpers of the looks (Dart `kit_live_toolkit.dart`): value noise, colour mixing and a low-resolution
 * colour field drawn as one smooth vertex mesh (the GPU interpolates between the grid points, so a coarse field
 * upscales softly with nothing blurred and no per-frame image decoding).
 */

private const val MASK32 = 0xFFFFFFFFL // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:13

private fun imul(a: Long, b: Long): Long = (a * b) and MASK32

/** Dart `hash2` (kit_live_toolkit.dart:15-19): 32-bit integer hash of a lattice point, 0..1. */
internal fun hash2(x: Int, y: Int): Double {
    var h = imul(x.toLong() and MASK32, 374761393) xor imul(y.toLong() and MASK32, 668265263) // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:16
    h = imul(h xor (h ushr 13), 1274126177) // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:17
    return ((h xor (h ushr 16)) and MASK32) / 4294967295.0 // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:18
}

/** Dart `valueNoise` (kit_live_toolkit.dart:21-30). */
internal fun valueNoise(x: Double, y: Double): Double {
    val xi = floor(x).toInt()
    val yi = floor(y).toInt()
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

/** Dart `fbm` (kit_live_toolkit.dart:32-35). */
internal fun fbm(x: Double, y: Double): Double =
    valueNoise(x, y) * .6 + // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:33
        valueNoise(x * 2.03 + 5.2, y * 2.03 + 1.3) * .3 + // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:34
        valueNoise(x * 4.1 + 9.1, y * 4.1 + 3.7) * .1 // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:35

/**
 * [fbm] over a grid whose columns share x and whose rows share y: the lattice hashes and each column's and row's
 * floor and fade are worked out once per pass, then every point costs three bilinear mixes. Same arithmetic in the
 * same order as [fbm], so the values are bit for bit the same; the arrays are kept and only grow.
 */
internal class LiveFbmGrid {
    private class Octave {
        var xi = IntArray(0)
        var xu = DoubleArray(0)
        var yi = IntArray(0)
        var yv = DoubleArray(0)
        var x0 = 0
        var y0 = 0
        var w = 0
        var table = DoubleArray(0)
    }

    private val o0 = Octave()
    private val o1 = Octave()
    private val o2 = Octave()
    private val octaves = arrayOf(o0, o1, o2)

    /** The [nx] column inputs [xs] and [ny] row inputs [ys] of this pass. */
    fun prepare(xs: DoubleArray, nx: Int, ys: DoubleArray, ny: Int) {
        for (o in octaves.indices) {
            val q = octaves[o]
            if (q.xi.size < nx) {
                q.xi = IntArray(nx)
                q.xu = DoubleArray(nx)
            }
            if (q.yi.size < ny) {
                q.yi = IntArray(ny)
                q.yv = DoubleArray(ny)
            }
            var minX = Int.MAX_VALUE
            var maxX = Int.MIN_VALUE
            for (i in 0 until nx) {
                val x = octaveOf(o, xs[i], true)
                val xi = floor(x).toInt()
                val xf = x - xi
                q.xi[i] = xi
                q.xu[i] = xf * xf * (3 - 2 * xf) // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:24
                if (xi < minX) minX = xi
                if (xi > maxX) maxX = xi
            }
            var minY = Int.MAX_VALUE
            var maxY = Int.MIN_VALUE
            for (j in 0 until ny) {
                val y = octaveOf(o, ys[j], false)
                val yi = floor(y).toInt()
                val yf = y - yi
                q.yi[j] = yi
                q.yv[j] = yf * yf * (3 - 2 * yf) // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:24
                if (yi < minY) minY = yi
                if (yi > maxY) maxY = yi
            }
            q.x0 = minX
            q.y0 = minY
            q.w = span(minX, maxX)
            val h = span(minY, maxY)
            if (q.table.size < q.w * h) q.table = DoubleArray(q.w * h)
            for (j in 0 until h) for (i in 0 until q.w) q.table[j * q.w + i] = hash2(minX + i, minY + j)
        }
    }

    /** Lattice points from [lo] to [hi] + 1: each cell needs its far corner too. */
    private fun span(lo: Int, hi: Int): Int = hi + 1 - lo + 1

    private fun octaveOf(o: Int, v: Double, isX: Boolean): Double = when (o) {
        0 -> v
        1 -> if (isX) v * 2.03 + 5.2 else v * 2.03 + 1.3 // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:34
        else -> if (isX) v * 4.1 + 9.1 else v * 4.1 + 3.7 // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:35
    }

    private fun noise(q: Octave, i: Int, j: Int): Double {
        val k = (q.yi[j] - q.y0) * q.w + (q.xi[i] - q.x0)
        val a = q.table[k]
        val b = q.table[k + 1]
        val c = q.table[k + q.w]
        val d = q.table[k + q.w + 1]
        val u = q.xu[i]
        val v = q.yv[j]
        return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v
    }

    /** [fbm] of column [i]'s x and row [j]'s y. */
    fun at(i: Int, j: Int): Double =
        noise(o0, i, j) * .6 + noise(o1, i, j) * .3 + noise(o2, i, j) * .1 // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:33-35
}

/** Dart `smooth` (kit_live_toolkit.dart:37-40). */
internal fun smooth(e0: Double, e1: Double, x: Double): Double {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0.0, 1.0)
    return t * t * (3 - 2 * t) // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:39
}

/** Red, green, blue of [c] as 0..255 (kit_live_toolkit.dart:43). */
internal fun rgb(c: Color): DoubleArray = doubleArrayOf(c.red * 255.0, c.green * 255.0, c.blue * 255.0) // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:43

/** Dart `mixRgb` (kit_live_toolkit.dart:45-49). */
internal fun mixRgb(a: DoubleArray, b: DoubleArray, t: Double): DoubleArray =
    doubleArrayOf(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t) // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:46-48

/** Dart `mixColor` (kit_live_toolkit.dart:51). */
internal fun mixColor(a: Color, b: Color, t: Double): Color = liveLerp(a, b, t.coerceIn(0.0, 1.0))

/** Dart `withA` (kit_live_toolkit.dart:53). */
internal fun withA(c: Color, a: Double): Color = liveAlpha(c, a.coerceIn(0.0, 1.0))

/** Dart `packArgb` (kit_live_toolkit.dart:55-59): 0..255 channels and a 0..1 alpha into 0xAARRGGBB. */
internal fun packArgb(r: Double, g: Double, b: Double, a: Double): Int =
    ((a * 255).roundToInt().coerceIn(0, 255) shl 24) or // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:56
        (r.roundToInt().coerceIn(0, 255) shl 16) or // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:57
        (g.roundToInt().coerceIn(0, 255) shl 8) or // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:58
        b.roundToInt().coerceIn(0, 255) // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:59

/** Strength of a look: 1 in dark, 0.7 in light, times the frame's strength (kit_live_toolkit.dart:63). */
internal fun strengthOf(f: KitLiveFrame): Double = (if (f.palette.dark) 1.0 else 0.7) * f.strength // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:63

/** The colour at (u, v) of a field, packed ARGB (the Dart field callback). */
internal fun interface LiveField {
    fun at(u: Double, v: Double): Int

    /** Called once before a pass over a grid of [lw] x [lh] cells: a field may precompute its rows and columns. */
    fun prepare(lw: Int, lh: Int, aspect: Double) {}

    /** The colour at grid point ([ix], [iy]), which is (u, v); a prepared field may answer from its tables. */
    fun atCell(ix: Int, iy: Int, u: Double, v: Double): Int = at(u, v)
}

/** The grid of one field (Dart `_FieldCache`), its arrays reused from frame to frame. */
internal class LiveFieldCache {
    var lw = 0
    var lh = 0
    var w = 0f
    var h = 0f
    var positions = FloatArray(0)
    var indices = ShortArray(0)
    var colors = IntArray(0)
    var lastCompute = -1e9 // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:71

    /** Whether the grid was built for [lw] x [lh] cells over [size]. */
    fun fits(lw: Int, lh: Int, size: Size): Boolean = this.lw == lw && this.lh == lh && w == size.width && h == size.height

    /** Builds the points and the strips of a grid of [lw] x [lh] cells over [size] (kit_live_toolkit.dart:93-124). */
    fun build(lw: Int, lh: Int, size: Size) {
        this.lw = lw
        this.lh = lh
        w = size.width
        h = size.height
        lastCompute = -1e9 // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:99
        val n = (lw + 1) * (lh + 1)
        positions = FloatArray(n * 2) // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:101
        colors = IntArray(n)
        var k = 0
        for (y in 0..lh) {
            for (x in 0..lw) {
                positions[k++] = x.toFloat() / lw * size.width
                positions[k++] = y.toFloat() / lh * size.height
            }
        }
        // Flutter's two triangles a cell, (a, b, d) and (b, e, d), as one strip a row: a0 d0 a1 d1 … gives the same
        // triangles on the same diagonal with a third of the indices; two repeated points join the rows.
        val idx = ShortArray(lh * (lw + 1) * 2 + (lh - 1) * 2) // NV: a strip of two points a column a row, two joins
        k = 0
        for (y in 0 until lh) {
            val row = y * (lw + 1)
            if (y > 0) idx[k++] = row.toShort()
            for (x in 0..lw) {
                idx[k++] = (row + x).toShort()
                idx[k++] = (row + lw + 1 + x).toShort()
            }
            if (y < lh - 1) idx[k++] = (row + lw + 1 + lw).toShort()
        }
        indices = idx
    }
}

/**
 * Draws a colour field (kit_live_toolkit.dart:77-144): [fn] gives the packed ARGB at (u, v), where u runs 0..1 across
 * and v 0..height/width down. The field is sampled on a grid of about [div] logical pixels and recomputed at most every
 * [every] seconds. The grid, its indices and its colours live in [KitLiveFrame.memory] and are rewritten in place:
 * nothing is allocated on a frame once the grid exists.
 */
@Suppress("LongParameterList") // Dart paintField's own parameters
internal fun paintField(
    canvas: Canvas,
    size: Size,
    frame: KitLiveFrame,
    key: String,
    div: Int,
    fn: LiveField,
    every: Double = 0.0,
) {
    val c = frame.memory.of(key, ::LiveFieldCache)
    var lw = max(8, (size.width / div).roundToInt()) // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:87
    var lh = max(8, (size.height / div).roundToInt()) // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:88
    while ((lw + 1) * (lh + 1) > 65000) { // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:89
        lw = (lw * .8).roundToInt() // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:90
        lh = (lh * .8).roundToInt() // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:91
    }
    if (!c.fits(lw, lh, size)) c.build(lw, lh, size)
    if (frame.t - c.lastCompute >= every || frame.t < c.lastCompute) {
        c.lastCompute = frame.t
        val aspect = size.height.toDouble() / size.width
        fn.prepare(lw, lh, aspect)
        var k = 0
        for (y in 0..lh) {
            val v = y.toDouble() / lh * aspect
            for (x in 0..lw) {
                c.colors[k++] = fn.atCell(x, y, x.toDouble() / lw, v)
            }
        }
    }
    canvas.liveDrawField(c)
}

/**
 * Draws the mesh of a field, a triangle strip a row, with its vertex colours only (Flutter
 * `drawVertices(…, BlendMode.dst, Paint())`).
 * Each platform hands the arrays to its own canvas without copying them.
 */
internal expect fun Canvas.liveDrawField(field: LiveFieldCache)

/** Dart `Color.computeLuminance`: the design module's, on the 8-bit colour Compose keeps. */
private fun luminance(c: Color): Double = relativeLuminance(Argb(c.toArgb()))

/** Dart `contrastRatio` (`lib/ui/theme_roles.dart:496-502`). */
internal fun liveContrast(a: Color, b: Color): Double {
    val la = luminance(a)
    val lb = luminance(b)
    val hi = if (la > lb) la else lb
    val lo = if (la > lb) lb else la
    return (hi + .05) / (lo + .05) // F: lib/ui/theme_roles.dart:501
}

/**
 * Whether [p]'s text reads on [paint] mixed [a] into [base] (kit_live_toolkit.dart:151-173): text1 bare and text2 and
 * text3 through [halo] of the ground, each at its floor (AA, or within .2 of the theme's own contrast where that is
 * lower) plus [spare]; with a halo in a dark theme the accent (the glow) is held to its floor too.
 */
@Suppress("LongParameterList") // Dart liveTextReads's own parameters
internal fun liveTextReads(
    p: KitLivePalette,
    base: Color,
    paint: Color,
    a: Double,
    halo: Double = 0.0,
    spare: Double = 0.0,
): Boolean {
    fun floor(text: Color): Double {
        val before = liveContrast(text, p.ground)
        return (if (before >= 4.5) 4.5 else before - .2) + .04 + spare // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:161
    }
    val under = liveLerp(base, paint, a)
    for (text in if (halo > 0 && p.dark) listOf(p.ink, p.glow) else listOf(p.ink)) {
        if (liveContrast(text, under) < floor(text)) return false
    }
    val behind = liveLerp(under, p.ground, halo)
    for (text in listOf(p.muted, p.faint)) {
        if (liveContrast(text, behind) < floor(text)) return false
    }
    return true
}

/** The most of [paint] (0..1) mixed into [base] while [p]'s text reads, less a sliver (kit_live_toolkit.dart:177-198). */
internal fun liveMost(
    p: KitLivePalette,
    base: Color,
    paint: Color,
    halo: Double = 0.0,
    spare: Double = 0.0,
): Double {
    fun reads(a: Double) = liveTextReads(p, base, paint, a, halo = halo, spare = spare)
    if (!reads(0.0)) return 0.0
    if (reads(1.0)) return 1.0
    var lo = 0.0
    var hi = 1.0
    repeat(LIVE_MOST_STEPS) {
        val mid = (lo + hi) / 2 // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:190
        if (reads(mid)) lo = mid else hi = mid
    }
    return lo
}

internal const val LIVE_MOST_STEPS: Int = 18 // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:189
