package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.liveStyleGlowSpill
import org.jetbrains.compose.resources.StringResource
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Glow spill (Dart `KitLiveGlowSpill`, `styles/kit_live_glow_spill.dart`): the accent's glow rises softly from below
 * the bottom edge and breathes; a fine still grain lies over the page. The glow is one cached unit-radius gradient
 * scaled to the frame's radius, so breathing allocates no shader.
 */
public class KitLiveGlowSpill : KitLiveStyle() {
    override val id: String = ID
    override val label: StringResource = Res.string.liveStyleGlowSpill

    override fun paint(scope: DrawScope, size: Size, frame: KitLiveFrame) {
        val p = frame.palette
        // Calibrated so text keeps its contrast at the strongest moment (test/kit/kit_live_background_test.dart).
        val a = strengthOf(frame) * .54 // F: lib/ui/kit/live_background/styles/kit_live_glow_spill.dart:23
        val run = frame.running
        val t = frame.t
        val w = size.width.toDouble()
        val h = size.height.toDouble()
        val br = .5 + .5 * sin(t * (1.2 + run * 1.6)) // F: lib/ui/kit/live_background/styles/kit_live_glow_spill.dart:27
        val r = (h * (.45 + run * .3 + br * .05)).toFloat() // F: lib/ui/kit/live_background/styles/kit_live_glow_spill.dart:28
        val cx = (w / 2).toFloat() // F: lib/ui/kit/live_background/styles/kit_live_glow_spill.dart:29
        val cy = (h * 1.02).toFloat() // F: lib/ui/kit/live_background/styles/kit_live_glow_spill.dart:29
        val inner = withA(p.glow, (.16 + run * .12) * a) // F: lib/ui/kit/live_background/styles/kit_live_glow_spill.dart:34
        val glow = frame.memory.of("glow") { GlowPaint() }
        glow.ensure(inner, withA(p.glow, 0.0))
        val grain = frame.memory.of("g") { Grain(w, h) }
        if (grain.w != w || grain.h != h) grain.reset(w, h)
        grain.paint.color = withA(p.ink, .05 * a) // F: lib/ui/kit/live_background/styles/kit_live_glow_spill.dart:44
        scope.drawIntoCanvas { canvas ->
            if (r > 0f) {
                canvas.save()
                canvas.translate(cx, cy)
                canvas.scale(r, r)
                canvas.drawRect(-cx / r, -cy / r, (size.width - cx) / r, (size.height - cy) / r, glow.paint)
                canvas.restore()
            }
            canvas.drawRawPoints(PointMode.Points, grain.points, grain.paint)
        }
    }

    /** A radial gradient of radius 1 at the origin, rebuilt only when its colours change. */
    internal class GlowPaint {
        val paint = Paint()
        private var inner: Color? = null
        private var outer: Color? = null

        fun ensure(i: Color, o: Color) {
            if (i == inner && o == outer) return
            inner = i
            outer = o
            paint.shader = RadialGradientShader(Offset.Zero, 1f, listOf(i, o))
        }
    }

    /** Dart `_Grain`: one still point per 90 square dp, seeded (11). */
    internal class Grain(w: Double, h: Double) {
        var w = 0.0
        var h = 0.0
        var points = FloatArray(0)
        val paint = Paint().apply {
            strokeWidth = 1f
            strokeCap = StrokeCap.Butt
        }

        init {
            reset(w, h)
        }

        fun reset(nw: Double, nh: Double) {
            w = nw
            h = nh
            val rnd = DartRandom(SEED)
            val n = (nw * nh / AREA).roundToInt()
            points = FloatArray(n * 2) // F: lib/ui/kit/live_background/styles/kit_live_glow_spill.dart:62
            for (i in 0 until n) {
                points[i * 2] = (rnd.nextDouble() * nw).toFloat() // F: lib/ui/kit/live_background/styles/kit_live_glow_spill.dart:64
                points[i * 2 + 1] = (rnd.nextDouble() * nh).toFloat() // F: lib/ui/kit/live_background/styles/kit_live_glow_spill.dart:65
            }
        }
    }

    public companion object {
        public const val ID: String = "glowSpill"
        internal const val SEED: Int = 11 // F: lib/ui/kit/live_background/styles/kit_live_glow_spill.dart:60
        internal const val AREA: Double = 90.0 // F: lib/ui/kit/live_background/styles/kit_live_glow_spill.dart:61
    }
}
