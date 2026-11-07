package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Contour lines of a slowly drifting height field (Dart `KitLiveTopo`, `lib/ui/kit/live_background/styles/kit_live_topo.dart`):
 * a field sampled every 3 logical pixels and drawn as one smooth vertex mesh ([paintField]); while a reply runs the lines
 * tighten and the lower part glows.
 *
 * The field's maths lives in one object kept in the frame's memory, its inputs rewritten each frame, so computing the
 * colours allocates nothing.
 */
public object KitLiveTopo : KitLiveStyle() {
    override val id: String = "topo" // F: lib/ui/kit/live_background/styles/kit_live_topo.dart:11

    internal const val CALIBRATED: Double = .54 // F: lib/ui/kit/live_background/styles/kit_live_topo.dart:21
    internal const val DRIFT: Double = .02 // F: lib/ui/kit/live_background/styles/kit_live_topo.dart:24
    internal const val DIV: Int = 3 // F: lib/ui/kit/live_background/styles/kit_live_topo.dart:26
    internal const val SCALE: Double = 1.4 // F: lib/ui/kit/live_background/styles/kit_live_topo.dart:27
    internal const val DRIFT_V: Double = .6 // F: lib/ui/kit/live_background/styles/kit_live_topo.dart:27
    internal const val LINES: Double = 8.0 // F: lib/ui/kit/live_background/styles/kit_live_topo.dart:28
    internal const val LINES_RUN: Double = 4.0 // F: lib/ui/kit/live_background/styles/kit_live_topo.dart:28
    internal const val EDGE_IN: Double = .03 // F: lib/ui/kit/live_background/styles/kit_live_topo.dart:31
    internal const val EDGE_OUT: Double = .09 // F: lib/ui/kit/live_background/styles/kit_live_topo.dart:31
    internal const val GLOW_FROM: Double = 1.3 // F: lib/ui/kit/live_background/styles/kit_live_topo.dart:32
    internal const val GLOW_TO: Double = 1.9 // F: lib/ui/kit/live_background/styles/kit_live_topo.dart:32
    internal const val LINE_ALPHA: Double = .09 // F: lib/ui/kit/live_background/styles/kit_live_topo.dart:34
    internal const val GLOW_ALPHA: Double = .2 // F: lib/ui/kit/live_background/styles/kit_live_topo.dart:34
    internal const val EVERY: Double = .06 // F: lib/ui/kit/live_background/styles/kit_live_topo.dart:35

    /** The field of one frame (the Dart closure at kit_live_topo.dart:26-35), its inputs set before each pass. */
    internal class Field : LiveField {
        var tt = 0.0
        var run = 0.0
        var a = 0.0
        var inkR = 0.0
        var inkG = 0.0
        var inkB = 0.0
        var glowR = 0.0
        var glowG = 0.0
        var glowB = 0.0
        var ink = androidx.compose.ui.graphics.Color.Unspecified
        var glow = androidx.compose.ui.graphics.Color.Unspecified

        /** Dart `rgb(p.ink)` and `rgb(p.glow)` (kit_live_topo.dart:25): channels as 0..255, worked out again only when a colour changes. */
        fun setColours(ink: androidx.compose.ui.graphics.Color, glow: androidx.compose.ui.graphics.Color) {
            if (ink == this.ink && glow == this.glow) return
            this.ink = ink
            this.glow = glow
            val (ir, ig, ib) = rgb(ink)
            val (gr, gg, gb) = rgb(glow)
            inkR = ir
            inkG = ig
            inkB = ib
            glowR = gr
            glowG = gg
            glowB = gb
        }

        private val grid = LiveFbmGrid()
        private var xs = DoubleArray(0)
        private var ys = DoubleArray(0)

        /** The columns' and rows' noise inputs of this pass, as [at] would compute them point by point. */
        override fun prepare(lw: Int, lh: Int, aspect: Double) {
            if (xs.size < lw + 1) xs = DoubleArray(lw + 1)
            if (ys.size < lh + 1) ys = DoubleArray(lh + 1)
            for (x in 0..lw) xs[x] = x.toDouble() / lw * SCALE + tt
            for (y in 0..lh) ys[y] = y.toDouble() / lh * aspect * SCALE + tt * DRIFT_V
            grid.prepare(xs, lw + 1, ys, lh + 1)
            clearRow = -1
        }

        private var clearRow = -1
        private var clear = 0

        /**
         * Between the lines (most of the field) the colour is clear, and its red, green and blue depend on the row
         * only: worked out once a row and reused, the same value [colourOf] gives there.
         */
        override fun atCell(ix: Int, iy: Int, u: Double, v: Double): Int {
            val n = grid.at(ix, iy)
            val x = n * (LINES + run * LINES_RUN)
            if (abs(x - dartRound(x)) < EDGE_OUT) return colourOf(n, v)
            if (iy != clearRow) {
                clearRow = iy
                clear = colourOf(n, v)
            }
            return clear
        }

        override fun at(u: Double, v: Double): Int = colourOf(fbm(u * SCALE + tt, v * SCALE + tt * DRIFT_V), v)

        /** The contour colour for height [n] at row [v] (kit_live_topo.dart:28-34). */
        private fun colourOf(n: Double, v: Double): Int {
            val k = LINES + run * LINES_RUN
            val x = n * k
            val b = abs(x - dartRound(x))
            val line = 1 - smooth(EDGE_IN, EDGE_OUT, b)
            val g = run * smooth(GLOW_FROM, GLOW_TO, v)
            return packArgb(
                inkR + (glowR - inkR) * g,
                inkG + (glowG - inkG) * g,
                inkB + (glowB - inkB) * g,
                line * (LINE_ALPHA + g * GLOW_ALPHA) * a,
            )
        }
    }

    /** Dart `roundToDouble`: ties away from zero (Kotlin's `round` ties to even). */
    internal fun dartRound(x: Double): Double = if (x >= 0) floor(x + .5) else ceil(x - .5) // NV: half up, as Dart rounds

    override fun paint(canvas: Canvas, size: Size, frame: KitLiveFrame) {
        val p = frame.palette
        // Calibrated so text keeps its contrast at the strongest moment (test/kit/kit_live_background_test.dart).
        val field = frame.memory.of("topo.field", ::Field)
        field.a = strengthOf(frame) * CALIBRATED
        field.run = frame.running
        field.tt = frame.t * DRIFT
        field.setColours(p.ink, p.glow)
        paintField(canvas, size, frame, "f", DIV, field, every = EVERY)
    }
}
