package io.github.eslamasabry.oc.ui.kitchat.tool

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

/**
 * The kind glyph of a step: line drawings on a 24-unit grid, stroked in [color]. Decorative (the title says what the
 * step did), so it carries no semantics. Glyphs are not mirrored in RTL: none of them points a reading direction.
 */
@Composable
internal fun KitToolGlyph(kind: KitToolKind, color: Color, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 24f
        val stroke = Stroke(width = 1.75f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val g = Glyph(this, u, color, stroke)
        when (kind) {
            KitToolKind.Shell -> g.shell()
            KitToolKind.Read -> g.page(lines = true)
            KitToolKind.Edit -> g.pencil()
            KitToolKind.Write -> g.pageWithPlus()
            KitToolKind.Patch -> g.plusMinus()
            KitToolKind.Search -> g.magnifier()
            KitToolKind.WebFetch -> g.globe()
            KitToolKind.SubAgent -> g.person()
            KitToolKind.Todo -> g.checkbox()
            KitToolKind.Question -> g.question()
            KitToolKind.Card -> g.card()
            KitToolKind.Other -> g.dots()
        }
    }
}

private class Glyph(private val d: DrawScope, private val u: Float, private val c: Color, private val s: Stroke) {
    private fun p(x: Float, y: Float) = Offset(x * u, y * u)

    private fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
        d.drawLine(c, p(x1, y1), p(x2, y2), strokeWidth = s.width, cap = StrokeCap.Round)

    private fun box(x: Float, y: Float, w: Float, h: Float, r: Float) =
        d.drawRoundRect(c, p(x, y), Size(w * u, h * u), CornerRadius(r * u), style = s)

    private fun path(vararg pts: Float) {
        val path = Path()
        path.moveTo(pts[0] * u, pts[1] * u)
        var i = 2
        while (i < pts.size) {
            path.lineTo(pts[i] * u, pts[i + 1] * u)
            i += 2
        }
        d.drawPath(path, c, style = s)
    }

    /** A prompt: `>_`. */
    fun shell() {
        box(3f, 4f, 18f, 16f, 2.5f)
        path(7f, 9f, 10f, 12f, 7f, 15f)
        line(12f, 15f, 16f, 15f)
    }

    /** A page with its corner folded; with text lines. */
    fun page(lines: Boolean) {
        path(6f, 3f, 14f, 3f, 18f, 7f, 18f, 21f, 6f, 21f, 6f, 3f)
        path(14f, 3f, 14f, 7f, 18f, 7f)
        if (lines) {
            line(9f, 12f, 15f, 12f)
            line(9f, 16f, 15f, 16f)
        }
    }

    fun pageWithPlus() {
        page(lines = false)
        line(12f, 11f, 12f, 17f)
        line(9f, 14f, 15f, 14f)
    }

    fun pencil() {
        path(4f, 20f, 5f, 15f, 15f, 5f, 19f, 9f, 9f, 19f, 4f, 20f)
        line(13f, 7f, 17f, 11f)
    }

    /** Lines added and removed: a plus over a minus. */
    fun plusMinus() {
        line(12f, 4f, 12f, 12f)
        line(8f, 8f, 16f, 8f)
        line(8f, 18f, 16f, 18f)
    }

    fun magnifier() {
        d.drawCircle(c, radius = 6f * u, center = p(10.5f, 10.5f), style = s)
        line(15f, 15f, 20f, 20f)
    }

    fun globe() {
        d.drawCircle(c, radius = 9f * u, center = p(12f, 12f), style = s)
        d.drawOval(c, p(8f, 3f), Size(8f * u, 18f * u), style = s)
        line(3f, 12f, 21f, 12f)
    }

    fun person() {
        d.drawCircle(c, radius = 3.5f * u, center = p(12f, 8f), style = s)
        d.drawArc(c, 180f, 180f, useCenter = false, topLeft = p(5f, 14f), size = Size(14f * u, 12f * u), style = s)
    }

    fun checkbox() {
        box(4f, 4f, 16f, 16f, 3f)
        path(8f, 12f, 11f, 15f, 16f, 9f)
    }

    fun question() {
        d.drawCircle(c, radius = 9f * u, center = p(12f, 12f), style = s)
        d.drawArc(c, 180f, 225f, useCenter = false, topLeft = p(9.5f, 6.5f), size = Size(5f * u, 5f * u), style = s)
        line(12f, 11.5f, 12f, 13.5f)
        d.drawCircle(c, radius = 1.1f * u, center = p(12f, 17f))
    }

    fun card() {
        box(3f, 5f, 18f, 14f, 2.5f)
        line(3f, 10f, 21f, 10f)
        line(7f, 15f, 12f, 15f)
    }

    fun dots() {
        for (x in listOf(6f, 12f, 18f)) d.drawCircle(c, radius = 1.6f * u, center = p(x, 12f))
    }
}

/** The fold chevron: points down when folded, up when open (vertical, so it never mirrors). */
@Composable
internal fun KitFoldChevron(opened: Boolean, color: Color, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 24f
        val stroke = Stroke(width = 1.75f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path = Path()
        if (opened) {
            path.moveTo(7f * u, 14f * u)
            path.lineTo(12f * u, 9f * u)
            path.lineTo(17f * u, 14f * u)
        } else {
            path.moveTo(7f * u, 10f * u)
            path.lineTo(12f * u, 15f * u)
            path.lineTo(17f * u, 10f * u)
        }
        drawPath(path, color, style = stroke)
    }
}
