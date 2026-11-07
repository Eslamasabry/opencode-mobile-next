package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.liveStyleCircuit
import org.jetbrains.compose.resources.StringResource
import kotlin.math.roundToLong

/**
 * Faint circuit-board traces cover the ground; small pulses of light travel along them, more of them (and greener)
 * while a reply runs (Dart `KitLiveCircuit`, `styles/kit_live_circuit.dart`).
 *
 * The traces, their end pads and their lengths are built once per size from the Dart seed; a frame only places the
 * pulses.
 */
public object KitLiveCircuit : KitLiveStyle() {
    override val id: String = "circuit"
    override val label: StringResource = Res.string.liveStyleCircuit
    override val ceilingFactor: Double = 2.0 // F: lib/ui/kit/live_background/styles/kit_live_circuit.dart:15

    /** The share of the ceiling it uses: calibrated so text3 keeps 4.5:1. */
    internal const val CALIBRATED = .66 // F: lib/ui/kit/live_background/styles/kit_live_circuit.dart:27

    /** Grid steps across the width. */
    internal const val STEPS = 14 // F: lib/ui/kit/live_background/styles/kit_live_circuit.dart:71

    /** How many traces. */
    internal const val TRACES = 16 // F: lib/ui/kit/live_background/styles/kit_live_circuit.dart:76

    /** Segments per trace. */
    internal const val SEGMENTS = 5 // F: lib/ui/kit/live_background/styles/kit_live_circuit.dart:80

    /** The directions a segment may take. */
    internal val DIRS = arrayOf(1 to 0, 0 to 1, 1 to 1, -1 to 1, 0 to -1) // F: lib/ui/kit/live_background/styles/kit_live_circuit.dart:72

    private val TRACE = Stroke(width = 1.2f) // F: lib/ui/kit/live_background/styles/kit_live_circuit.dart:34

    /** One trace: its corners and lengths, and where along it its pulse starts. */
    internal class Trace(val pts: List<Offset>, val offset: Double) {
        private val lens = DoubleArray(pts.size - 1) { j -> (pts[j + 1] - pts[j]).getDistance().toDouble() }
        val total = lens.sum()

        /** The point a share [f] of the way along the trace (Dart `_Trace.pointAt`). */
        fun pointAt(f: Double): Offset {
            var d = f * total
            for (j in lens.indices) {
                if (d <= lens[j]) return lerp(pts[j], pts[j + 1], if (lens[j] == 0.0) 0f else (d / lens[j]).toFloat())
                d -= lens[j]
            }
            return pts.last()
        }
    }

    internal class Geo(val path: Path, val ends: List<Offset>, val traces: List<Trace>)

    /** The traces of a surface [size], as the Dart seed lays them out (Dart `_build`). */
    internal fun build(size: Size): Geo {
        val rnd = liveRandom(id)
        val g = size.width.toDouble() / STEPS
        val path = Path()
        val ends = ArrayList<Offset>()
        val traces = ArrayList<Trace>()
        repeat(TRACES) {
            var x = roundHalfUp(rnd.nextDouble() * STEPS) * g // F: lib/ui/kit/live_background/styles/kit_live_circuit.dart:77
            var y = roundHalfUp(rnd.nextDouble() * size.height / g) * g
            val pts = ArrayList<Offset>()
            pts += Offset(x.toFloat(), y.toFloat())
            repeat(SEGMENTS) {
                val (dx, dy) = DIRS[rnd.nextInt(DIRS.size)]
                val n = 1 + rnd.nextInt(3) // F: lib/ui/kit/live_background/styles/kit_live_circuit.dart:82
                x += dx * g * n
                y += dy * g * n
                pts += Offset(x.toFloat(), y.toFloat())
            }
            path.moveTo(pts.first().x, pts.first().y)
            for (q in pts.drop(1)) path.lineTo(q.x, q.y)
            ends += pts.first()
            ends += pts.last()
            traces += Trace(pts, rnd.nextDouble())
        }
        return Geo(path, ends, traces)
    }

    /** Dart `roundToDouble` for the positive values here: halves round up, not to even (as `Math.round`). */
    private fun roundHalfUp(x: Double): Double = x.roundToLong().toDouble()

    override fun paint(canvas: DrawScope, size: Size, frame: KitLiveFrame) {
        val p = frame.palette
        val peak = livePeak(frame) * ceilingFactor * CALIBRATED
        if (peak <= 0) return
        val geo = liveCached(frame, "circuit.geo", size) { build(size) }
        canvas.drawPath(geo.path, liveAlpha(p.ink, peak * .25), style = TRACE) // F: lib/ui/kit/live_background/styles/kit_live_circuit.dart:35
        canvas.drawPoints(
            geo.ends,
            PointMode.Points,
            liveAlpha(p.ink, peak * .45), // F: lib/ui/kit/live_background/styles/kit_live_circuit.dart:43
            strokeWidth = 4f, // F: lib/ui/kit/live_background/styles/kit_live_circuit.dart:42
            cap = StrokeCap.Round,
        )
        val run = frame.running
        val clock = liveClock(frame, "circuit", 1 + run * 2.1) // F: lib/ui/kit/live_background/styles/kit_live_circuit.dart:46
        val core = liveMix(p.teal, p.glow, run)
        for (i in geo.traces.indices) {
            val tr = geo.traces[i]
            for (q in 0 until 2) { // F: lib/ui/kit/live_background/styles/kit_live_circuit.dart:51
                val k = if (q == 0) 1.0 else run
                if (k < .02) continue // F: lib/ui/kit/live_background/styles/kit_live_circuit.dart:53
                val at = tr.pointAt(dmod(clock * .12 + tr.offset + q * .5, 1.0)) // F: lib/ui/kit/live_background/styles/kit_live_circuit.dart:54
                canvas.drawCircle(liveAlpha(core, peak * .12 * k), radius = 5f, center = at) // F: lib/ui/kit/live_background/styles/kit_live_circuit.dart:57,58
                canvas.drawCircle(liveAlpha(core, peak * .45 * k), radius = 1.8f, center = at) // F: lib/ui/kit/live_background/styles/kit_live_circuit.dart:62,63
            }
        }
    }
}
