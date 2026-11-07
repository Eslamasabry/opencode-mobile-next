package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import kotlin.math.sin

/**
 * A faint scope grid with a slow, steady trace across the middle; the trace comes alive while a reply runs (Dart
 * `KitLiveOscilloscope`, `lib/ui/kit/live_background/styles/kit_live_oscilloscope.dart`).
 *
 * The grid is one cached path; the trace path and the pens are kept in the frame's memory and rewritten in place, so a
 * frame allocates nothing.
 */
public object KitLiveOscilloscope : KitLiveStyle() {
    override val id: String = "oscilloscope" // F: lib/ui/kit/live_background/styles/kit_live_oscilloscope.dart:18

    override val ceilingFactor: Double get() = CEILING_FACTOR

    internal const val CEILING_FACTOR: Double = 2.0 // F: lib/ui/kit/live_background/styles/kit_live_oscilloscope.dart:15
    internal const val COLUMNS: Int = 8 // F: lib/ui/kit/live_background/styles/kit_live_oscilloscope.dart:28
    internal const val GRID_ALPHA: Double = .15 // F: lib/ui/kit/live_background/styles/kit_live_oscilloscope.dart:48
    internal const val MID: Double = .55 // F: lib/ui/kit/live_background/styles/kit_live_oscilloscope.dart:52
    internal const val AMP_IDLE: Double = .35 // F: lib/ui/kit/live_background/styles/kit_live_oscilloscope.dart:53
    internal const val AMP_RUN: Double = .9 // F: lib/ui/kit/live_background/styles/kit_live_oscilloscope.dart:53
    internal const val STEP: Double = 3.0 // F: lib/ui/kit/live_background/styles/kit_live_oscilloscope.dart:55
    internal const val GLOW_WIDTH: Float = 6f // F: lib/ui/kit/live_background/styles/kit_live_oscilloscope.dart:72
    internal const val GLOW_ALPHA: Double = .2 // F: lib/ui/kit/live_background/styles/kit_live_oscilloscope.dart:73
    internal const val LINE_WIDTH: Float = 1.3f // F: lib/ui/kit/live_background/styles/kit_live_oscilloscope.dart:78
    internal const val LINE_ALPHA: Double = .65 // F: lib/ui/kit/live_background/styles/kit_live_oscilloscope.dart:79

    /** The trace's height at [u] (0..1 across) at [t] seconds with a reply [run]ning (kit_live_oscilloscope.dart:57-61). */
    internal fun traceY(u: Double, t: Double, run: Double, mid: Double, amp: Double): Double =
        mid +
            sin(u * 9 - t * 1.6) * amp * .6 + // F: lib/ui/kit/live_background/styles/kit_live_oscilloscope.dart:59
            sin(u * 23 + t * 2.3) * amp * .25 * run + // F: lib/ui/kit/live_background/styles/kit_live_oscilloscope.dart:60
            (liveFbm(u * 6 + t * .8, t * .3) - .5) * amp * (.4 + run) // F: lib/ui/kit/live_background/styles/kit_live_oscilloscope.dart:61

    private class Pens {
        val grid = Paint().apply {
            style = PaintingStyle.Stroke
            strokeWidth = 1f
        }
        val pen = Paint().apply {
            style = PaintingStyle.Stroke
            strokeJoin = StrokeJoin.Round
            strokeCap = StrokeCap.Round
        }
        val trace = Path()
    }

    override fun paint(canvas: Canvas, size: Size, frame: KitLiveFrame) {
        val p = frame.palette
        val peak = livePeak(frame) * ceilingFactor
        if (peak <= 0) return
        val dx = size.width.toDouble() / COLUMNS
        val grid = liveCached(frame, "scope.grid", size) {
            val path = Path()
            var x = 0.0
            while (x <= size.width) {
                path.moveTo(x.toFloat(), 0f)
                path.lineTo(x.toFloat(), size.height)
                x += dx
            }
            var y = size.height % dx / 2 // F: lib/ui/kit/live_background/styles/kit_live_oscilloscope.dart:36
            while (y <= size.height) {
                path.moveTo(0f, y.toFloat())
                path.lineTo(size.width, y.toFloat())
                y += dx
            }
            path
        }
        val pens = frame.memory.of("scope.pens", ::Pens)
        pens.grid.color = liveAlpha(p.ink, peak * GRID_ALPHA)
        canvas.drawPath(grid, pens.grid)

        val run = frame.running
        val t = frame.t
        val mid = size.height * MID
        val amp = dx * (AMP_IDLE + run * AMP_RUN)
        val trace = pens.trace
        trace.rewind()
        var x = 0.0
        while (x <= size.width + STEP) {
            val y = traceY(x / size.width, t, run, mid, amp)
            if (x == 0.0) trace.moveTo(x.toFloat(), y.toFloat()) else trace.lineTo(x.toFloat(), y.toFloat())
            x += STEP
        }
        val col = liveMix(p.teal, p.glow, run)
        val pen = pens.pen
        pen.strokeWidth = GLOW_WIDTH
        pen.color = liveAlpha(col, peak * GLOW_ALPHA)
        canvas.drawPath(trace, pen)
        pen.strokeWidth = LINE_WIDTH
        pen.color = liveAlpha(col, peak * LINE_ALPHA)
        canvas.drawPath(trace, pen)
    }
}
