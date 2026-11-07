package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.liveStyleHoneycomb
import org.jetbrains.compose.resources.StringResource
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Honeycomb (Dart `KitLiveHoneycomb`, `styles/kit_live_honeycomb.dart`): a fine hexagon lattice, like a carbon panel;
 * a band of light slides across it (green and more often while a reply runs). The lattice is one cached path; a lit
 * cell redraws one cached hexagon moved to its centre.
 */
public class KitLiveHoneycomb : KitLiveStyle() {
    override val id: String = ID
    override val label: StringResource = Res.string.liveStyleHoneycomb
    override val ceilingFactor: Double = CEILING_FACTOR

    override fun paint(scope: DrawScope, size: Size, frame: KitLiveFrame) {
        val p = frame.palette
        val peak = livePeak(frame) * ceilingFactor
        if (peak <= 0) return
        val r = size.width / 16.0 // F: lib/ui/kit/live_background/styles/kit_live_honeycomb.dart:38
        val geo = liveCached(frame, "honeycomb.geo", size) { Geo.of(size, r) }
        scope.drawPath(geo.all, withA(p.ink, peak * .24), style = STROKE) // F: lib/ui/kit/live_background/styles/kit_live_honeycomb.dart:58-60
        val run = frame.running
        val clock = liveClock(frame, "honeycomb", 1 + run * 1.7) // F: lib/ui/kit/live_background/styles/kit_live_honeycomb.dart:63
        val band = bandAt(clock, size)
        val sigma = r * 3 // F: lib/ui/kit/live_background/styles/kit_live_honeycomb.dart:65
        for (k in geo.xs.indices) {
            val cx = geo.xs[k]
            val cy = geo.ys[k]
            val d = abs(cx + cy - band)
            if (d > sigma * 3) continue // F: lib/ui/kit/live_background/styles/kit_live_honeycomb.dart:68
            val lit = exp(-(d * d) / (2 * sigma * sigma)) // F: lib/ui/kit/live_background/styles/kit_live_honeycomb.dart:69
            if (lit < .03) continue // F: lib/ui/kit/live_background/styles/kit_live_honeycomb.dart:70
            val colour = withA(liveMix(p.ink, p.glow, lit * run), peak * .12 * lit) // F: lib/ui/kit/live_background/styles/kit_live_honeycomb.dart:74-78
            scope.translate(cx.toFloat(), cy.toFloat()) { drawPath(geo.hex, colour, style = STROKE) }
        }
    }

    /** The lattice: every cell centre, every hexagon in one path, and one hexagon at the origin. */
    internal class Geo(val xs: DoubleArray, val ys: DoubleArray, val all: Path, val hex: Path) {
        companion object {
            fun of(size: Size, r: Double): Geo {
                val h = r * sqrt(3.0) // F: lib/ui/kit/live_background/styles/kit_live_honeycomb.dart:40
                val xs = ArrayList<Double>()
                val ys = ArrayList<Double>()
                val all = Path()
                var row = -1
                while (row * h * .5 < size.height + h) { // F: lib/ui/kit/live_background/styles/kit_live_honeycomb.dart:43
                    var col = -1
                    while (col * r * 3 < size.width + r * 3) { // F: lib/ui/kit/live_background/styles/kit_live_honeycomb.dart:44
                        val x = col * r * 3 + (if ((row and 1) != 0) r * 1.5 else 0.0) // F: lib/ui/kit/live_background/styles/kit_live_honeycomb.dart:46
                        val y = row * h * .5 // F: lib/ui/kit/live_background/styles/kit_live_honeycomb.dart:43
                        xs.add(x)
                        ys.add(y)
                        all.addPath(hexAt(x, y, r))
                        col++
                    }
                    row++
                }
                return Geo(xs.toDoubleArray(), ys.toDoubleArray(), all, hexAt(0.0, 0.0, r))
            }
        }
    }

    public companion object {
        public const val ID: String = "honeycomb"
        internal const val CEILING_FACTOR: Double = 2.0 // F: lib/ui/kit/live_background/styles/kit_live_honeycomb.dart:15
        internal val STROKE: Stroke = Stroke(width = 1f)

        /** Dart `_hex`: a hexagon of radius [r] around ([x], [y]), its first corner to the right. */
        internal fun hexAt(x: Double, y: Double, r: Double): Path {
            val path = Path()
            for (k in 0 until SIDES) {
                val an = PI / 3 * k // F: lib/ui/kit/live_background/styles/kit_live_honeycomb.dart:26
                val px = (x + r * cos(an)).toFloat()
                val py = (y + r * sin(an)).toFloat()
                if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()
            return path
        }

        /** Where the band of light crosses x + y at [clock] seconds of the look's own clock. */
        internal fun bandAt(clock: Double, size: Size): Double =
            (dmod(clock * .07, 1.4) - .2) * (size.width + size.height) // F: lib/ui/kit/live_background/styles/kit_live_honeycomb.dart:64

        private const val SIDES = 6 // F: lib/ui/kit/live_background/styles/kit_live_honeycomb.dart:25
    }
}
