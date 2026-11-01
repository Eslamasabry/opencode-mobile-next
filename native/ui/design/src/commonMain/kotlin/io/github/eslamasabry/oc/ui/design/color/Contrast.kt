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

/** WCAG contrast ratio (Dart `contrastRatio`, `lib/ui/theme_roles.dart`). */
public fun wcagContrast(a: Argb, b: Argb): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    val hi = maxOf(la, lb)
    val lo = minOf(la, lb)
    return (hi + 0.05) / (lo + 0.05)
}
