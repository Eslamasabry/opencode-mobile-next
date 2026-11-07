package io.github.eslamasabry.oc.ui.kit.text

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.icon.PhosphorFamily
import io.github.eslamasabry.oc.ui.kit.icon.glyphTable

/**
 * Where a trailing glyph's ink ends (Dart `KitOptical`, `lib/ui/kit/kit_optical.dart`; impeccable layout pass,
 * 2026-10-28: "the visible glyph of every trailing control ends on the rail").
 *
 * A glyph's ink stands in from its square by a margin that differs per glyph: Phosphor's caret by 28 % of its size,
 * its dots and cross by 19 %, its copy by 12.5 %, its check by 9 %. [endInset] is that margin, so a part can place a
 * trailing glyph by its ink: [endPad] is the room to leave after the glyph's box for its ink to end `rail` from the
 * edge. A glyph not listed, or not from the regular Phosphor family, is placed by its box (an inset of 0).
 */
public object KitOptical {
    /**
     * Code point → (ink start, ink end) as fractions of the glyph's size, in the glyph's own left-to-right drawing
     * (Dart `_ink`).
     */
    internal val ink: Map<Int, Pair<Float, Float>> =
        mapOf(
            0xe13a to (.34375f to .28125f), // F: lib/ui/kit/kit_optical.dart:26
            0xe136 to (.15625f to .15625f), // F: lib/ui/kit/kit_optical.dart:27
            0xe13c to (.15625f to .15625f), // F: lib/ui/kit/kit_optical.dart:28
            0xe1fe to (.1875f to .1875f), // F: lib/ui/kit/kit_optical.dart:29
            0xe4f6 to (.1875f to .1875f), // F: lib/ui/kit/kit_optical.dart:30
            0xe182 to (.125f to .09375f), // F: lib/ui/kit/kit_optical.dart:31
            0xe1cc to (.125f to .125f), // F: lib/ui/kit/kit_optical.dart:32
        )

    /**
     * How far [icon]'s ink stands in from the end of its [size] square in [direction]. A glyph that mirrors in a
     * right-to-left language (`matchTextDirection`, native `autoMirror`) keeps its end margin.
     */
    public fun endInset(icon: ImageVector, size: Dp, direction: LayoutDirection): Dp {
        val glyph = glyphTable[icon.name] ?: return 0.dp
        if (glyph.family != PhosphorFamily.Regular) return 0.dp
        val (start, end) = ink[glyph.codePoint] ?: return 0.dp
        val rtl = direction == LayoutDirection.Rtl
        return size * (if (rtl && !icon.autoMirror) start else end)
    }

    /** The room after [icon]'s [size] box so that its ink ends [rail] from the same edge. Never negative. */
    public fun endPad(icon: ImageVector, size: Dp, rail: Dp, direction: LayoutDirection): Dp =
        maxOf(0.dp, rail - endInset(icon, size, direction))
}
