package io.github.eslamasabry.oc.ui.design.color

import kotlin.math.pow

/** Flutter `Color.computeLuminance` (sRGB → linear, Rec. 709 weights). Alpha is ignored, as in Dart. */
public fun relativeLuminance(c: Argb): Double {
    fun lin(channel: Int): Double {
        val s = channel / 255.0
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * lin(c.red) + 0.7152 * lin(c.green) + 0.0722 * lin(c.blue)
}

/** The distance between two colours' hues on the colour wheel, 0–180° (Dart `hueDistance`). */
public fun hueDistance(a: Argb, b: Argb): Double = hueDistance(a.toD(), b.toD())

/** CIEDE2000 colour difference of two opaque sRGB colours (Dart `deltaE2000`). */
public fun deltaE2000(a: Argb, b: Argb): Double = deltaE2000(a.toD(), b.toD())

/** WCAG contrast ratio (Dart `contrastRatio`, `lib/ui/theme_roles.dart`). */
public fun wcagContrast(a: Argb, b: Argb): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    val hi = maxOf(la, lb)
    val lo = minOf(la, lb)
    return (hi + 0.05) / (lo + 0.05)
}

/**
 * The one "is this red" test (Dart `test/support/no_red.dart`): hue within 12° of red (345–360 or 0–12), saturation
 * and value both at least .45. Orange (Monokai's accent, hue 25°) is not red; no role may be red (B-KIT-056).
 */
public fun isRedHue(c: Argb): Boolean {
    val d = c.toD()
    val max = maxOf(d.r, d.g, d.b)
    val min = minOf(d.r, d.g, d.b)
    val saturation = if (max == 0.0) 0.0 else (max - min) / max
    val h = hue(d)
    return (h <= 12 || h >= 345) && saturation >= .45 && max >= .45
}
