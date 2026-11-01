package io.github.eslamasabry.oc.ui.design.color

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * A colour with float channels in 0..1, the way Flutter 3.27+ holds `Color` (`a`, `r`, `g`, `b` are doubles and
 * `Color.lerp` / `Color.alphaBlend` never round to 8 bits). The role derivation runs on these, as Dart does, and rounds
 * once at the end (Flutter `toARGB32`: `(x * 255).round()`), so a derived role lands on the colour Flutter paints.
 */
internal data class DColor(val a: Double, val r: Double, val g: Double, val b: Double) {
    fun toArgb(): Argb {
        fun q(x: Double) = (x * 255.0).roundToInt().coerceIn(0, 255)
        return Argb((q(a) shl 24) or (q(r) shl 16) or (q(g) shl 8) or q(b))
    }

    /** Flutter `withValues(alpha:)`. */
    fun withAlpha(alpha: Double): DColor = copy(a = alpha)

    companion object {
        val White = DColor(1.0, 1.0, 1.0, 1.0)
        val Black = DColor(1.0, 0.0, 0.0, 0.0)
    }
}

internal fun Argb.toD(): DColor = DColor(alpha / 255.0, red / 255.0, green / 255.0, blue / 255.0)

/** dart:ui `lerpDouble`. */
private fun lerpD(a: Double, b: Double, t: Double): Double = a * (1.0 - t) + b * t

/** Flutter `Color.lerp` (float channels, clamped). The Dart `_mix`. */
internal fun mix(x: DColor, y: DColor, t: Double): DColor =
    DColor(
        lerpD(x.a, y.a, t).coerceIn(0.0, 1.0),
        lerpD(x.r, y.r, t).coerceIn(0.0, 1.0),
        lerpD(x.g, y.g, t).coerceIn(0.0, 1.0),
        lerpD(x.b, y.b, t).coerceIn(0.0, 1.0),
    )

/** Flutter `Color.alphaBlend(foreground, background)` (float channels). */
internal fun alphaBlend(fg: DColor, bg: DColor): DColor {
    val alpha = fg.a
    if (alpha == 0.0) return bg
    val inv = 1.0 - alpha
    val backAlpha = bg.a
    return if (backAlpha == 1.0) {
        DColor(1.0, fg.r * alpha + bg.r * inv, fg.g * alpha + bg.g * inv, fg.b * alpha + bg.b * inv)
    } else {
        val ba = backAlpha * inv
        val out = alpha + ba
        DColor(out, (fg.r * alpha + bg.r * ba) / out, (fg.g * alpha + bg.g * ba) / out, (fg.b * alpha + bg.b * ba) / out)
    }
}

/** Flutter `Color.computeLuminance`. */
internal fun luminance(c: DColor): Double {
    fun lin(s: Double) = if (s <= 0.03928) s / 12.92 else powD((s + 0.055) / 1.055, 2.4)
    return 0.2126 * lin(c.r) + 0.7152 * lin(c.g) + 0.0722 * lin(c.b)
}

/** Dart `contrastRatio` over float colours. */
internal fun contrast(a: DColor, b: DColor): Double {
    val la = luminance(a)
    val lb = luminance(b)
    val hi = if (la > lb) la else lb
    val lo = if (la > lb) lb else la
    return (hi + 0.05) / (lo + 0.05)
}

private fun powD(x: Double, y: Double): Double = x.pow(y)

/** Flutter `HSVColor.fromColor(c).hue` (Dart `%` is Euclidean, so a negative remainder wraps). */
internal fun hue(c: DColor): Double {
    val max = maxOf(c.r, c.g, c.b)
    val min = minOf(c.r, c.g, c.b)
    val delta = max - min
    val h =
        when {
            max == 0.0 || delta == 0.0 -> 0.0
            max == c.r -> 60.0 * ((c.g - c.b) / delta).mod(6.0)
            max == c.g -> 60.0 * (((c.b - c.r) / delta) + 2)
            else -> 60.0 * (((c.r - c.g) / delta) + 4)
        }
    return if (h.isNaN()) 0.0 else h
}

internal fun hueDistance(a: DColor, b: DColor): Double {
    val d = kotlin.math.abs(hue(a) - hue(b))
    return if (d > 180) 360 - d else d
}

/** CIE L*a*b* (D65) of an opaque sRGB colour (Dart `_lab`). */
internal fun lab(c: DColor): DoubleArray {
    fun lin(v: Double) = if (v <= 0.04045) v / 12.92 else powD((v + 0.055) / 1.055, 2.4)
    val r = lin(c.r)
    val g = lin(c.g)
    val b = lin(c.b)
    val x = (r * .4124564 + g * .3575761 + b * .1804375) / .95047
    val y = r * .2126729 + g * .7151522 + b * .0721750
    val z = (r * .0193339 + g * .1191920 + b * .9503041) / 1.08883
    fun f(t: Double) = if (t > 0.008856) powD(t, 1.0 / 3) else 7.787 * t + 16.0 / 116
    val fx = f(x)
    val fy = f(y)
    val fz = f(z)
    return doubleArrayOf(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
}

/** CIEDE2000 of two L*a*b* colours (Sharma, Wu and Dalal 2005), line for line from Dart `deltaE2000Lab`. */
public fun deltaE2000Lab(lab1: DoubleArray, lab2: DoubleArray): Double {
    fun rad(deg: Double) = deg * kotlin.math.PI / 180
    fun deg(rad: Double) = rad * 180 / kotlin.math.PI
    fun pow(x: Double, y: Double) = powD(x, y)
    val (l1, a1, b1) = Triple(lab1[0], lab1[1], lab1[2])
    val (l2, a2, b2) = Triple(lab2[0], lab2[1], lab2[2])
    val cBar = (kotlin.math.sqrt(a1 * a1 + b1 * b1) + kotlin.math.sqrt(a2 * a2 + b2 * b2)) / 2
    val cBar7 = pow(cBar, 7.0)
    val g = .5 * (1 - kotlin.math.sqrt(cBar7 / (cBar7 + pow(25.0, 7.0))))
    val a1p = (1 + g) * a1
    val a2p = (1 + g) * a2
    val c1p = kotlin.math.sqrt(a1p * a1p + b1 * b1)
    val c2p = kotlin.math.sqrt(a2p * a2p + b2 * b2)
    val h1p = (deg(kotlin.math.atan2(b1, a1p)) + 360).mod(360.0)
    val h2p = (deg(kotlin.math.atan2(b2, a2p)) + 360).mod(360.0)
    val dLp = l2 - l1
    val dCp = c2p - c1p
    var dh = h2p - h1p
    if (c1p * c2p == 0.0) dh = 0.0 else if (dh > 180) dh -= 360 else if (dh < -180) dh += 360
    val dHp = 2 * kotlin.math.sqrt(c1p * c2p) * kotlin.math.sin(rad(dh / 2))
    val lBarp = (l1 + l2) / 2
    val cBarp = (c1p + c2p) / 2
    val hBarp =
        when {
            c1p * c2p == 0.0 -> h1p + h2p
            kotlin.math.abs(h1p - h2p) <= 180 -> (h1p + h2p) / 2
            h1p + h2p < 360 -> (h1p + h2p + 360) / 2
            else -> (h1p + h2p - 360) / 2
        }
    val t = 1 - .17 * kotlin.math.cos(rad(hBarp - 30)) + .24 * kotlin.math.cos(rad(2 * hBarp)) +
        .32 * kotlin.math.cos(rad(3 * hBarp + 6)) - .20 * kotlin.math.cos(rad(4 * hBarp - 63))
    val dTheta = 30 * kotlin.math.exp(-pow((hBarp - 275) / 25, 2.0))
    val cBarp7 = pow(cBarp, 7.0)
    val rc = 2 * kotlin.math.sqrt(cBarp7 / (cBarp7 + pow(25.0, 7.0)))
    val sl = 1 + .015 * pow(lBarp - 50, 2.0) / kotlin.math.sqrt(20 + pow(lBarp - 50, 2.0))
    val sc = 1 + .045 * cBarp
    val sh = 1 + .015 * cBarp * t
    val rt = -kotlin.math.sin(rad(2 * dTheta)) * rc
    return kotlin.math.sqrt(
        pow(dLp / sl, 2.0) + pow(dCp / sc, 2.0) + pow(dHp / sh, 2.0) + rt * (dCp / sc) * (dHp / sh),
    )
}

internal fun deltaE2000(a: DColor, b: DColor): Double = deltaE2000Lab(lab(a), lab(b))
