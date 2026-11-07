package io.github.eslamasabry.oc.ui.kit.content.usage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors

/**
 * A minimal line chart (Usage › Cost, B-160): one series, oldest first, drawn as a single accent line over a hairline
 * baseline with a dot on the newest point. No axes, no gradient, no fill; the host states the numbers in words
 * ([semantics] and the rows around it). Time runs with the reading direction (mirrored in RTL).
 *
 * States: one point (a dot), several points (a line), all zero (a flat line on the baseline)
 * Text: none (the host passes [semantics])
 * Cap: Fill
 * Motion: the line draws in STANDARD (about 200 ms) when the series changes; Off = drawn at once
 * Hidden: none
 */
@Composable
public fun KitTrendLine(values: List<Double>, semantics: String, modifier: Modifier = Modifier) {
    val colors = LocalKitColors.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val points = remember(values) { TrendLineRules.normalise(values) }
    // Each new series draws in from the oldest point (STANDARD on Enter); reduced motion snaps it in at once.
    val specs = KitMotion.specs
    val reveal = remember(points) { Animatable(if (specs.state.reduced) 1f else 0f) }
    LaunchedEffect(reveal) { reveal.animateTo(1f, specs.enter()) }
    Canvas(modifier.fillMaxWidth().height(64.dp).clearAndSetSemantics { contentDescription = semantics }) {
        val stroke = 2.dp.toPx()
        val inset = stroke * 2
        val w = size.width - inset * 2
        val h = size.height - inset * 2
        drawLine(colors.hairline, Offset(0f, size.height - inset), Offset(size.width, size.height - inset), 1.dp.toPx())
        if (points.isEmpty()) return@Canvas
        fun at(i: Int): Offset {
            val step = if (points.size == 1) 0f else w * i / (points.size - 1)
            val x = if (rtl) size.width - inset - step else inset + step
            return Offset(x, inset + h * (1f - points[i]))
        }
        val path = Path().apply {
            moveTo(at(0).x, at(0).y)
            for (i in 1 until points.size) lineTo(at(i).x, at(i).y)
        }
        val shown = reveal.value.coerceIn(0f, 1f)
        val left = if (rtl) size.width * (1f - shown) else 0f
        val right = if (rtl) size.width else size.width * shown
        clipRect(left, 0f, right, size.height) {
            drawPath(path, colors.accent, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        if (shown >= 1f) drawCircle(colors.accent, stroke * 1.5f, at(points.lastIndex))
    }
}

/** Pure rules of [KitTrendLine]. */
public object TrendLineRules {
    /**
     * Each value as a share of the largest, 0..1. Negative or non-finite values count as zero; an all-zero series is
     * a flat line on the baseline, never a division by zero.
     */
    public fun normalise(values: List<Double>): List<Float> {
        val clean = values.map { if (it.isFinite() && it > 0) it else 0.0 }
        val top = clean.maxOrNull() ?: return emptyList()
        return if (top <= 0.0) clean.map { 0f } else clean.map { (it / top).toFloat() }
    }
}
