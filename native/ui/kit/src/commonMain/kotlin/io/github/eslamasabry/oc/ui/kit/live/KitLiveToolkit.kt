package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/*
 * Shared drawing helpers of the looks (Dart `lib/ui/kit/live_background/styles/kit_live_toolkit.dart`): value noise,
 * colour mixing and a low-resolution colour field. Flutter draws the field as one vertex mesh the GPU smooths; here the
 * same grid of colours is written into one small bitmap (reused, never reallocated) and drawn scaled with bilinear
 * filtering, which smooths it the same way and costs one image draw per frame.
 */

private fun imul(a: Long, b: Long): Long = (a * b) and 0xFFFFFFFFL // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:13

/** Dart `hash2`: a 32-bit integer hash of a lattice point into 0..1. */
public fun hash2(x: Long, y: Long): Double {
    var h = imul(x and 0xFFFFFFFFL, 374761393L) xor imul(y and 0xFFFFFFFFL, 668265263L) // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:16
    h = imul(h xor (h ushr 13), 1274126177L) // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:17
    return ((h xor (h ushr 16)) and 0xFFFFFFFFL) / 4294967295.0 // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:18
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

/** Dart `fbm`: three octaves of [valueNoise]. */
public fun fbm(x: Double, y: Double): Double =
    valueNoise(x, y) * .6 + // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:33
        valueNoise(x * 2.03 + 5.2, y * 2.03 + 1.3) * .3 + // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:34
        valueNoise(x * 4.1 + 9.1, y * 4.1 + 3.7) * .1 // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:35

/** Dart `smooth`: smoothstep. */
public fun smooth(e0: Double, e1: Double, x: Double): Double = liveSmooth(e0, e1, x)

/** Red, green, blue of [c] as 0..255 (Dart `rgb`), into [out]. */
public fun rgb(c: Color, out: DoubleArray = doubleArrayOf(0.0, 0.0, 0.0)): DoubleArray {
    out[0] = c.red * 255.0 // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:43
    out[1] = c.green * 255.0 // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:43
    out[2] = c.blue * 255.0 // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:43,48
    return out
}

/** Dart `mixRgb`, into [out] (which may be [a]). */
public fun mixRgb(a: DoubleArray, b: DoubleArray, t: Double, out: DoubleArray = doubleArrayOf(0.0, 0.0, 0.0)): DoubleArray {
    out[0] = a[0] + (b[0] - a[0]) * t
    out[1] = a[1] + (b[1] - a[1]) * t
    out[2] = a[2] + (b[2] - a[2]) * t // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:48
    return out
}

/** Dart `mixColor`. */
public fun mixColor(a: Color, b: Color, t: Double): Color = liveMix(a, b, t)

/** Dart `withA`. */
public fun withA(c: Color, a: Double): Color = liveAlpha(c, a)

/** Dart `packArgb`: 0..255 channels and a 0..1 alpha as one ARGB int. */
public fun packArgb(r: Double, g: Double, b: Double, a: Double): Int =
    ((a * 255).roundToInt().coerceIn(0, 255) shl 24) or // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:56
        (r.roundToInt().coerceIn(0, 255) shl 16) or // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:57
        (g.roundToInt().coerceIn(0, 255) shl 8) or // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:58
        b.roundToInt().coerceIn(0, 255) // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:59

/** Strength of a look (Dart `strengthOf`): 1 in dark, 0.7 in light, times the frame's strength. */
public fun strengthOf(f: KitLiveFrame): Double = (if (f.palette.dark) 1.0 else 0.7) * f.strength // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:63

/**
 * A grid of colours, one per vertex of a ([cols]+1) × ([rows]+1) lattice, kept in one small bitmap. [draw] scales it
 * so each texel's centre sits on its vertex and bilinear filtering smooths between them, as Flutter's coloured vertex
 * mesh does. Built once per size; a frame only rewrites [colors] (unpremultiplied ARGB) and calls [upload].
 */
public class LiveField(public val cols: Int, public val rows: Int) {
    public val colors: IntArray = IntArray((cols + 1) * (rows + 1))
    public val image: ImageBitmap = ImageBitmap(cols + 1, rows + 1)
    private val scratch: Any? = liveFieldScratch(cols + 1, rows + 1)

    /** Copies [colors] into [image]. */
    public fun upload() {
        liveWritePixels(image, colors, scratch)
    }

    /** Draws the field with vertex (x, y) at (x × [stepX], y × [stepY]). */
    public fun draw(scope: DrawScope, stepX: Float, stepY: Float) {
        scope.withTransform({
            scale(stepX, stepY, pivot = Offset.Zero)
            translate(-.5f, -.5f) // NV: a texel's centre sits on its vertex
        }) {
            drawImage(
                image,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(cols + 1, rows + 1),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(cols + 1, rows + 1),
                filterQuality = FilterQuality.Low,
            )
        }
    }
}

/** Platform scratch space for [liveWritePixels] (a byte buffer on desktop), made once per field. */
internal expect fun liveFieldScratch(w: Int, h: Int): Any?

/** Writes unpremultiplied ARGB [argb] (row by row) into [image], whose size it matches. */
internal expect fun liveWritePixels(image: ImageBitmap, argb: IntArray, scratch: Any?)

private class FieldCache {
    var field: LiveField? = null
    var w = 0f
    var h = 0f
    var lastCompute = -1e9 // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:71
}

/**
 * Draws a colour field (Dart `paintField`): [fn] gives the packed ARGB at (u, v) where u runs 0..1 across and v
 * 0..height/width down. The field is sampled on a grid of about [div] logical pixels and recomputed at most every
 * [every] seconds.
 */
public fun paintField(
    canvas: DrawScope,
    size: Size,
    frame: KitLiveFrame,
    key: String,
    div: Int,
    every: Double = 0.0,
    fn: (u: Double, v: Double) -> Int,
) {
    val c = frame.memory.of(key) { FieldCache() }
    var lw = max(8, (size.width / div).roundToInt()) // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:87
    var lh = max(8, (size.height / div).roundToInt()) // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:88
    while ((lw + 1) * (lh + 1) > 65000) { // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:89
        lw = (lw * .8).roundToInt() // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:90
        lh = (lh * .8).roundToInt() // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:91
    }
    var field = c.field
    if (field == null || field.cols != lw || field.rows != lh || c.w != size.width || c.h != size.height) {
        field = LiveField(lw, lh)
        c.field = field
        c.w = size.width
        c.h = size.height
        c.lastCompute = -1e9 // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:99
    }
    if (frame.t - c.lastCompute >= every || frame.t < c.lastCompute) {
        c.lastCompute = frame.t
        val aspect = size.height.toDouble() / size.width
        var k = 0
        for (y in 0..lh) {
            val v = y.toDouble() / lh * aspect
            for (x in 0..lw) field.colors[k++] = fn(x.toDouble() / lw, v)
        }
        field.upload()
    }
    field.draw(canvas, size.width / lw, size.height / lh)
}

/* Text contrast, as `lib/ui/theme_roles.dart` measures it (Flutter `Color.computeLuminance` on double channels). */

private fun lin(s: Double): Double = if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4) // NV: dart:ui Color.computeLuminance

private fun luminance(r: Double, g: Double, b: Double): Double = 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b) // NV: dart:ui Color.computeLuminance

private fun contrast(a: DoubleArray, b: DoubleArray): Double {
    val (ar, ag, ab) = a
    val (br, bg, bb) = b
    val la = luminance(ar, ag, ab)
    val lb = luminance(br, bg, bb)
    return (max(la, lb) + .05) / (min(la, lb) + .05) // F: lib/ui/theme_roles.dart:501
}

private fun ch(c: Color) = doubleArrayOf(c.red.toDouble(), c.green.toDouble(), c.blue.toDouble())

private fun lerp3(a: DoubleArray, b: DoubleArray, t: Double) = mixRgb(a, b, t)

/**
 * Whether [p]'s text reads on [paint] mixed [a] into [base] (Dart `liveTextReads`): text1 bare and text2 and text3
 * through [halo] of the ground, each at its floor (AA, or within .2 of the theme's own contrast where that is lower)
 * plus [spare]; with a halo in a dark theme the accent (the glow) is held to its floor too, as it reads bare.
 */
public fun liveTextReads(p: KitLivePalette, base: Color, paint: Color, a: Double, halo: Double = 0.0, spare: Double = 0.0): Boolean {
    val ground = ch(p.ground)
    fun floor(text: DoubleArray): Double {
        val before = contrast(text, ground)
        return (if (before >= 4.5) 4.5 else before - .2) + .04 + spare // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:161
    }
    val under = lerp3(ch(base), ch(paint), a)
    val bare = if (halo > 0 && p.dark) listOf(p.ink, p.glow) else listOf(p.ink)
    for (text in bare) {
        val t = ch(text)
        if (contrast(t, under) < floor(t)) return false
    }
    val behind = lerp3(under, ground, halo)
    for (text in listOf(p.muted, p.faint)) {
        val t = ch(text)
        if (contrast(t, behind) < floor(t)) return false
    }
    return true
}

/** The most of [paint] (0..1) that can be mixed into [base] while [p]'s text reads, less a sliver (Dart `liveMost`). */
public fun liveMost(p: KitLivePalette, base: Color, paint: Color, halo: Double = 0.0, spare: Double = 0.0): Double {
    fun reads(a: Double) = liveTextReads(p, base, paint, a, halo, spare)
    if (!reads(0.0)) return 0.0
    if (reads(1.0)) return 1.0
    var lo = 0.0
    var hi = 1.0
    repeat(18) { // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:189
        val mid = (lo + hi) / 2 // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:190
        if (reads(mid)) lo = mid else hi = mid
    }
    return lo
}

/** Flutter's `Color.computeLuminance`. */
public fun liveLuminance(c: Color): Double = luminance(c.red.toDouble(), c.green.toDouble(), c.blue.toDouble())

/** Flutter `HSLColor` (hue in degrees, saturation and lightness 0..1), for the aurora's hue. */
public class LiveHsl(public val alpha: Double, public val hue: Double, public val saturation: Double, public val lightness: Double) {
    public fun withSaturation(s: Double): LiveHsl = LiveHsl(alpha, hue, s, lightness)

    public fun withLightness(l: Double): LiveHsl = LiveHsl(alpha, hue, saturation, l)

    /** Flutter `HSLColor.toColor` (painting/colors.dart `_colorFromHue`), each channel rounded to 8 bits. */
    public fun toColor(): Color {
        val chroma = (1.0 - abs(2.0 * lightness - 1.0)) * saturation; val secondary = chroma * (1.0 - abs(dmod(hue / 60.0, 2.0) - 1.0)); val match = lightness - chroma / 2.0 // NV: Flutter HSLColor.toColor
        val (r, g, b) = if (hue < 60.0) Triple(chroma, secondary, 0.0) else if (hue < 120.0) Triple(secondary, chroma, 0.0) else if (hue < 180.0) Triple(0.0, chroma, secondary) else if (hue < 240.0) Triple(0.0, secondary, chroma) else if (hue < 300.0) Triple(secondary, 0.0, chroma) else Triple(chroma, 0.0, secondary) // NV: Flutter HSLColor's six hue sextants
        fun q(x: Double) = (x * 255).roundToInt() / 255f // NV: Color.fromARGB's 8-bit channels
        return Color(q(r + match), q(g + match), q(b + match), q(alpha))
    }

    public companion object {
        /** Flutter `HSLColor.fromColor`. */
        public fun of(c: Color): LiveHsl {
            val r = c.red.toDouble()
            val g = c.green.toDouble()
            val b = c.blue.toDouble()
            val mx = max(r, max(g, b))
            val mn = min(r, min(g, b))
            val delta = mx - mn
            var hue = if (mx == 0.0) 0.0 else if (mx == r) 60.0 * dmod((g - b) / delta, 6.0) else if (mx == g) 60.0 * ((b - r) / delta + 2) else 60.0 * ((r - g) / delta + 4) // NV: Flutter HSLColor._getHue
            if (hue.isNaN()) hue = 0.0
            val lightness = (mx + mn) / 2.0; val saturation = if (lightness == 1.0) 0.0 else (delta / (1.0 - abs(2.0 * lightness - 1.0))).coerceIn(0.0, 1.0) // NV: Flutter HSLColor.fromColor
            return LiveHsl(c.alpha.toDouble(), hue, saturation, lightness)
        }
    }
}

/** Dart's `%`: never negative for a positive divisor. */
internal fun dmod(a: Double, b: Double): Double {
    val r = a % b
    return if (r < 0) r + b else r
}

/**
 * Fractal noise over a grid whose x depends only on the column and y only on the row (the aurora's haze, the caustics'
 * warp), with the same three octaves as [fbm] ([sinHash] false) or [liveFbm] ([sinHash] true) and bit for bit the same
 * values. Each lattice point is hashed once per frame instead of four times per octave per vertex, which is most of a
 * field's cost: set the columns' and rows' coordinates, then read [at]. Its arrays grow once and are reused.
 */
public class LiveFbmGrid(private val sinHash: Boolean) {
    private val o0 = Octave()
    private val o1 = Octave()
    private val o2 = Octave()
    private val octaves = arrayOf(o0, o1, o2)
    private var cols = 0

    /** One octave: per column its lattice offset and smoothed weight, per row the same, and the lattice's hashes. */
    private class Octave {
        var xi = LongArray(0)
        var xw = DoubleArray(0)
        var xo = IntArray(0)
        var yi = LongArray(0)
        var yw = DoubleArray(0)
        var yo = IntArray(0)
        var tw = 0
        var table = DoubleArray(0)
    }

    /** The noise's x for each of [n] columns. */
    public fun columns(xs: DoubleArray, n: Int) {
        for (o in octaves.indices) {
            val q = octaves[o]
            if (q.xi.size < n) {
                q.xi = LongArray(n)
                q.xw = DoubleArray(n)
                q.xo = IntArray(n)
            }
            for (i in 0 until n) {
                val x = octX(o, xs[i])
                val f = floor(x)
                val xf = x - f
                q.xi[i] = f.toLong()
                q.xw[i] = xf * xf * (3 - 2 * xf) // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:24
            }
        }
        cols = n
    }

    /** The noise's y for each of [n] rows; call after [columns], then read [at]. */
    public fun rows(ys: DoubleArray, n: Int) {
        for (o in octaves.indices) {
            val q = octaves[o]
            if (q.yi.size < n) {
                q.yi = LongArray(n)
                q.yw = DoubleArray(n)
                q.yo = IntArray(n)
            }
            for (j in 0 until n) {
                val y = octY(o, ys[j])
                val f = floor(y)
                val yf = y - f
                q.yi[j] = f.toLong()
                q.yw[j] = yf * yf * (3 - 2 * yf) // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:24
            }
            // The lattice this frame touches, hashed once.
            var xMin = Long.MAX_VALUE
            var xMax = Long.MIN_VALUE
            for (i in 0 until cols) {
                xMin = min(xMin, q.xi[i]); xMax = max(xMax, q.xi[i])
            }
            var yMin = Long.MAX_VALUE
            var yMax = Long.MIN_VALUE
            for (j in 0 until n) {
                yMin = min(yMin, q.yi[j]); yMax = max(yMax, q.yi[j])
            }
            // The lattice spans every point the columns and rows fall between, and the point after the last.
            q.tw = (xMax - xMin + 1).toInt() + 1
            val th = (yMax - yMin + 1).toInt() + 1
            if (q.table.size < q.tw * th) q.table = DoubleArray(q.tw * th)
            for (b in 0 until th) {
                for (a in 0 until q.tw) q.table[b * q.tw + a] = hashAt(xMin + a, yMin + b)
            }
            for (i in 0 until cols) q.xo[i] = (q.xi[i] - xMin).toInt()
            for (j in 0 until n) q.yo[j] = (q.yi[j] - yMin).toInt() * q.tw
        }
    }

    /** The noise at column [i], row [j]: exactly [fbm] or [liveFbm] at that column's x and that row's y. */
    public fun at(i: Int, j: Int): Double = noise(o0, i, j) * .6 + noise(o1, i, j) * .3 + noise(o2, i, j) * .1 // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:33-35

    private fun noise(q: Octave, i: Int, j: Int): Double {
        val t = q.table
        val k = q.yo[j] + q.xo[i]
        val a = t[k]
        val b = t[k + 1]
        val c = t[k + q.tw]
        val d = t[k + q.tw + 1]
        val u = q.xw[i]
        val v = q.yw[j]
        return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v
    }

    private fun hashAt(x: Long, y: Long): Double = if (sinHash) liveHash(x.toDouble(), y.toDouble()) else hash2(x, y)

    private companion object {
        fun octX(o: Int, x: Double) = when (o) {
            0 -> x
            1 -> x * 2.03 + 5.2 // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:34
            else -> x * 4.1 + 9.1 // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:35
        }

        fun octY(o: Int, y: Double) = when (o) {
            0 -> y
            1 -> y * 2.03 + 1.3 // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:34
            else -> y * 4.1 + 3.7 // F: lib/ui/kit/live_background/styles/kit_live_toolkit.dart:35
        }
    }
}

/**
 * Marks a bitmap a look has finished drawing and will only draw from now on (the aurora's plates, the brushed grain),
 * so a frame draws it without copying it: desktop Skia shares an immutable bitmap's pixels instead of copying them on
 * every draw; Android uploads it to the GPU once ahead of the first frame.
 */
internal expect fun liveSettle(image: ImageBitmap)
