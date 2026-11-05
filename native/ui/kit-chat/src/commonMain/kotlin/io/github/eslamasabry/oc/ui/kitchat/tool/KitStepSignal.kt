package io.github.eslamasabry.oc.ui.kitchat.tool

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.min

/**
 * The step timeline under Signal (Dart `kit_step_signal.dart`): the rail is the track, each node a signal, and the
 * folded work line a stretch of track between two dashed rules. The signal instruments themselves
 * (`style/kit_signal.dart`) are another part; this file holds the step-specific rules and the rules' paint.
 */
internal object KitStepSignal {
    /** Dart `_DashedRules.paint`: dash 3, gap 3, weight 1.5. */
    const val DASH: Float = 3f // F: lib/ui/kit/chat/kit_step_signal.dart:144
    const val GAP: Float = 3f // F: lib/ui/kit/chat/kit_step_signal.dart:144
    const val WEIGHT: Float = 1.5f // F: lib/ui/kit/chat/kit_step_signal.dart:144

    /**
     * Whether a step keeps its timeline's track live (solid): a running step. The native tool status has no
     * background or waiting-for-you state, so those two of Dart `_signalLive` do not occur here.
     */
    fun live(status: KitToolStatus): Boolean = status == KitToolStatus.Running // F: lib/ui/kit/chat/kit_step_signal.dart:10-13

    /** The x ranges `[from, to)` of the dashes on a rule [width] dp wide, the words starting [start] in (mirrored in RTL). */
    fun dashes(width: Float, start: Float, rtl: Boolean): List<ClosedFloatingPointRange<Float>> {
        val from = if (rtl) 0f else start
        val to = if (rtl) width - start else width
        val out = ArrayList<ClosedFloatingPointRange<Float>>()
        var x = from
        while (x < to) {
            out += x..min(x + DASH, to)
            x += DASH + GAP
        }
        return out
    }
}

/** The dashed rules above and below the folded work line, from where its words start to its end (mirrored in RTL). */
internal fun Modifier.kitDashedRules(color: Color, start: Dp): Modifier =
    drawBehind {
        val rtl = layoutDirection == LayoutDirection.Rtl
        val weight = KitStepSignal.WEIGHT.dp.toPx()
        val dashes = KitStepSignal.dashes(size.width, start.toPx(), rtl)
        for (y in listOf(0f, size.height - weight)) {
            for (d in dashes) drawRect(color, Offset(d.start, y), Size(d.endInclusive - d.start, weight))
        }
    }
