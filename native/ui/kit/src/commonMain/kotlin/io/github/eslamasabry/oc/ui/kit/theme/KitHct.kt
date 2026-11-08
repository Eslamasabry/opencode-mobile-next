// The CAM16 and sRGB constants are the published model's (material-color-utilities); naming each would hide them.
@file:Suppress("MagicNumber")

package io.github.eslamasabry.oc.ui.kit.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A colour as hue, chroma and tone (Material's HCT: CAM16 hue and chroma under the default viewing conditions, CIE L*
 * tone). Kit-internal: the theme switch's rim light derives its colour from the accent here (theme-switch-premium §4).
 * The forward and inverse CAM16 formulas follow material-color-utilities; the inverse finds the colour by searching
 * J for the tone, then lowering chroma until the colour fits sRGB.
 */
@Immutable
public data class KitHct(val hue: Double, val chroma: Double, val tone: Double) {
    public fun toColor(alpha: Float = 1f): Color = KitHctMath.solve(hue, chroma, tone).copy(alpha = alpha)

    public companion object {
        public fun of(color: Color): KitHct = KitHctMath.fromColor(color)
    }
}

internal object KitHctMath {
    // Default viewing conditions (material-color-utilities ViewingConditions.DEFAULT).
    private val whitePoint = doubleArrayOf(95.047, 100.0, 108.883)
    private val n: Double
    private val aw: Double
    private val nbb: Double
    private val ncb: Double
    private val c: Double
    private val nc: Double
    private val fl: Double
    private val z: Double
    private val rgbD: DoubleArray

    init {
        val adaptingLuminance = 200.0 / PI * yFromLstar(50.0) / 100.0
        val backgroundY = yFromLstar(50.0)
        val f = 1.0 // surround 2
        c = 0.69
        nc = f
        val rW = 0.401288 * whitePoint[0] + 0.650173 * whitePoint[1] - 0.051461 * whitePoint[2]
        val gW = -0.250268 * whitePoint[0] + 1.204414 * whitePoint[1] + 0.045854 * whitePoint[2]
        val bW = -0.002079 * whitePoint[0] + 0.048952 * whitePoint[1] + 0.953127 * whitePoint[2]
        val d = (f * (1.0 - (1.0 / 3.6) * exp((-adaptingLuminance - 42.0) / 92.0))).coerceIn(0.0, 1.0)
        rgbD = doubleArrayOf(d * (100.0 / rW) + 1.0 - d, d * (100.0 / gW) + 1.0 - d, d * (100.0 / bW) + 1.0 - d)
        val k = 1.0 / (5.0 * adaptingLuminance + 1.0)
        val k4 = k * k * k * k
        val k4F = 1.0 - k4
        fl = k4 * adaptingLuminance + 0.1 * k4F * k4F * cbrt(5.0 * adaptingLuminance)
        n = backgroundY / whitePoint[1]
        z = 1.48 + sqrt(n)
        nbb = 0.725 / n.pow(0.2)
        ncb = nbb
        fun adapt(x: Double): Double {
            val p = (fl * x / 100.0).pow(0.42)
            return 400.0 * p / (p + 27.13)
        }
        aw = (2.0 * adapt(rgbD[0] * rW) + adapt(rgbD[1] * gW) + 0.05 * adapt(rgbD[2] * bW)) * nbb
    }

    private const val E = 216.0 / 24389.0
    private const val KAPPA = 24389.0 / 27.0

    fun yFromLstar(l: Double): Double {
        val ft = (l + 16.0) / 116.0
        val ft3 = ft * ft * ft
        return 100.0 * (if (ft3 > E) ft3 else (116.0 * ft - 16.0) / KAPPA)
    }

    fun lstarFromY(y: Double): Double {
        val t = y / 100.0
        val f = if (t > E) cbrt(t) else (KAPPA * t + 16.0) / 116.0
        return 116.0 * f - 16.0
    }

    private fun linearized(channel: Float): Double {
        val v = channel.toDouble()
        return 100.0 * (if (v <= 0.040449936) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4))
    }

    private fun delinearized(linear: Double): Double {
        val v = linear / 100.0
        return if (v <= 0.0031308) v * 12.92 else 1.055 * v.pow(1.0 / 2.4) - 0.055
    }

    fun fromColor(color: Color): KitHct {
        val r = linearized(color.red)
        val g = linearized(color.green)
        val b = linearized(color.blue)
        val x = 0.41233895 * r + 0.35762064 * g + 0.18051042 * b
        val y = 0.2126 * r + 0.7152 * g + 0.0722 * b
        val zz = 0.01932141 * r + 0.11916382 * g + 0.95034478 * b
        val rC = 0.401288 * x + 0.650173 * y - 0.051461 * zz
        val gC = -0.250268 * x + 1.204414 * y + 0.045854 * zz
        val bC = -0.002079 * x + 0.048952 * y + 0.953127 * zz
        fun adapt(v: Double): Double {
            val p = (fl * abs(v) / 100.0).pow(0.42)
            return sign(v) * 400.0 * p / (p + 27.13)
        }
        val rA = adapt(rgbD[0] * rC)
        val gA = adapt(rgbD[1] * gC)
        val bA = adapt(rgbD[2] * bC)
        val a = (11.0 * rA - 12.0 * gA + bA) / 11.0
        val bb = (rA + gA - 2.0 * bA) / 9.0
        val u = (20.0 * rA + 20.0 * gA + 21.0 * bA) / 20.0
        val p2 = (40.0 * rA + 20.0 * gA + bA) / 20.0
        var hue = atan2(bb, a) * 180.0 / PI
        if (hue < 0) hue += 360.0
        if (hue >= 360.0) hue -= 360.0
        val ac = p2 * nbb
        val j = 100.0 * (ac / aw).coerceAtLeast(0.0).pow(c * z)
        val huePrime = if (hue < 20.14) hue + 360.0 else hue
        val eHue = 0.25 * (cos(huePrime * PI / 180.0 + 2.0) + 3.8)
        val p1 = 50000.0 / 13.0 * eHue * nc * ncb
        val t = p1 * hypot(a, bb) / (u + 0.305)
        val alpha = (1.64 - 0.29.pow(n)).pow(0.73) * t.pow(0.9)
        val chroma = alpha * sqrt(j / 100.0)
        return KitHct(hue, chroma, lstarFromY(y))
    }

    /** CAM16 (J, C, h) to linear RGB (0 … 100 per channel), unclamped. */
    private fun linearRgb(j: Double, chroma: Double, hue: Double): DoubleArray {
        val alpha = if (chroma == 0.0 || j == 0.0) 0.0 else chroma / sqrt(j / 100.0)
        val t = (alpha / (1.64 - 0.29.pow(n)).pow(0.73)).pow(1.0 / 0.9)
        val hRad = hue * PI / 180.0
        val eHue = 0.25 * (cos(hRad + 2.0) + 3.8)
        val ac = aw * (j / 100.0).pow(1.0 / c / z)
        val p1 = eHue * (50000.0 / 13.0) * nc * ncb
        val p2 = ac / nbb
        val hSin = sin(hRad)
        val hCos = cos(hRad)
        val gamma = 23.0 * (p2 + 0.305) * t / (23.0 * p1 + 11.0 * t * hCos + 108.0 * t * hSin)
        val a = gamma * hCos
        val b = gamma * hSin
        val rA = (460.0 * p2 + 451.0 * a + 288.0 * b) / 1403.0
        val gA = (460.0 * p2 - 891.0 * a - 261.0 * b) / 1403.0
        val bA = (460.0 * p2 - 220.0 * a - 6300.0 * b) / 1403.0
        fun unadapt(v: Double): Double {
            val base = (27.13 * abs(v) / (400.0 - abs(v))).coerceAtLeast(0.0)
            return sign(v) * (100.0 / fl) * base.pow(1.0 / 0.42)
        }
        val rF = unadapt(rA) / rgbD[0]
        val gF = unadapt(gA) / rgbD[1]
        val bF = unadapt(bA) / rgbD[2]
        val x = 1.86206786 * rF - 1.01125463 * gF + 0.14918677 * bF
        val y = 0.38752654 * rF + 0.62144744 * gF - 0.00897398 * bF
        val zz = -0.01584150 * rF - 0.03412294 * gF + 1.04996444 * bF
        return doubleArrayOf(
            3.2413774792388685 * x - 1.5376652402851851 * y - 0.49885366846268053 * zz,
            -0.9691452513005321 * x + 1.8758853451067872 * y + 0.04156585616912061 * zz,
            0.05562093689691305 * x - 0.20395524564742123 * y + 1.0571799111220335 * zz,
        )
    }

    private fun yOf(rgb: DoubleArray): Double = 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2]

    /** The J (0 … 100) whose colour at [chroma] and [hue] has luminance [y]; Y rises with J at a fixed C and h. */
    private fun jFor(y: Double, chroma: Double, hue: Double): Double {
        var lo = 0.0
        var hi = 100.0
        repeat(40) {
            val mid = (lo + hi) / 2.0
            if (yOf(linearRgb(mid, chroma, hue)) < y) lo = mid else hi = mid
        }
        return (lo + hi) / 2.0
    }

    /** The sRGB colour of ([hue], [chroma], [tone]); chroma drops to the most the gamut holds at that hue and tone. */
    fun solve(hue: Double, chroma: Double, tone: Double): Color {
        val t = tone.coerceIn(0.0, 100.0)
        val y = yFromLstar(t)
        if (t <= 0.0001 || t >= 99.9999 || chroma < 0.0001) return grey(t)
        val h = ((hue % 360.0) + 360.0) % 360.0
        fun inGamut(rgb: DoubleArray): Boolean = rgb.all { it >= -0.01 && it <= 100.01 }
        fun at(cc: Double): DoubleArray = linearRgb(jFor(y, cc, h), cc, h)
        val wanted = at(chroma)
        val rgb =
            if (inGamut(wanted)) {
                wanted
            } else {
                var lo = 0.0
                var hi = chroma
                repeat(24) {
                    val mid = (lo + hi) / 2.0
                    if (inGamut(at(mid))) lo = mid else hi = mid
                }
                at(lo)
            }
        fun ch(v: Double): Float = delinearized(v.coerceIn(0.0, 100.0)).coerceIn(0.0, 1.0).toFloat()
        return Color(ch(rgb[0]), ch(rgb[1]), ch(rgb[2]))
    }

    private fun grey(tone: Double): Color {
        val v = delinearized(yFromLstar(tone)).coerceIn(0.0, 1.0).toFloat()
        return Color(v, v, v)
    }
}
