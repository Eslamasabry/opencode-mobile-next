package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.rotateRad
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.liveStyleLightRays
import org.jetbrains.compose.resources.StringResource
import kotlin.math.cos
import kotlin.math.sin

/**
 * Light rays (Dart `KitLiveLightRays`, `styles/kit_live_light_rays.dart`): five soft beams fall from the top-left
 * corner and sway slowly, fading along their length. Each beam is drawn turned to its angle, so its fade is one cached
 * gradient along x and a frame allocates no shader.
 */
public class KitLiveLightRays : KitLiveStyle() {
    override val id: String = ID
    override val label: StringResource = Res.string.liveStyleLightRays

    override fun paint(scope: DrawScope, size: Size, frame: KitLiveFrame) {
        val p = frame.palette
        // Calibrated so text keeps its contrast at the strongest moment (test/kit/kit_live_background_test.dart).
        val a = strengthOf(frame) * .7 // F: lib/ui/kit/live_background/styles/kit_live_light_rays.dart:22
        val run = frame.running
        val t = frame.t
        val w = size.width.toDouble()
        val h = size.height.toDouble()
        val col = mixColor(p.blue, p.glow, .3 + run * .6) // F: lib/ui/kit/live_background/styles/kit_live_light_rays.dart:26
        val len = h * 1.6 // F: lib/ui/kit/live_background/styles/kit_live_light_rays.dart:27
        val al = (if (p.dark) .09 else .06) * (1 + run * .6) * a // F: lib/ui/kit/live_background/styles/kit_live_light_rays.dart:31
        val beam = frame.memory.of("rays") { Beam() }
        beam.ensure(withA(col, al), withA(col, 0.0), len.toFloat())
        // The beams' shared corner, Dart (-w * .1, -h * .05).
        val ax = -w * .1 // F: lib/ui/kit/live_background/styles/kit_live_light_rays.dart:34
        val ay = -h * .05 // F: lib/ui/kit/live_background/styles/kit_live_light_rays.dart:34
        scope.drawIntoCanvas { canvas ->
            for (i in 0 until BEAMS) {
                val ang = .35 + i * .16 + .05 * sin(t * .25 + i * 1.7) // F: lib/ui/kit/live_background/styles/kit_live_light_rays.dart:29
                val wid = .05 + .03 * sin(t * .3 + i) // F: lib/ui/kit/live_background/styles/kit_live_light_rays.dart:30
                // The beam in its own turned frame: the fade runs along x from the corner of the canvas.
                val ca = cos(ang)
                val sa = sin(ang)
                beam.path.reset()
                beam.path.moveTo((ax * ca + ay * sa).toFloat(), (-ax * sa + ay * ca).toFloat())
                beam.path.lineTo((cos(-wid) * len).toFloat(), (sin(-wid) * len).toFloat())
                beam.path.lineTo((cos(wid) * len).toFloat(), (sin(wid) * len).toFloat())
                beam.path.close()
                canvas.save()
                canvas.rotateRad(ang.toFloat())
                canvas.drawPath(beam.path, beam.paint)
                canvas.restore()
            }
        }
    }

    /** One reused path and a fade from the corner to [len] along x, rebuilt only when its colours or length change. */
    internal class Beam {
        val path = Path()
        val paint = Paint()
        private var from: Color? = null
        private var to: Color? = null
        private var len = -1f

        fun ensure(f: Color, t: Color, l: Float) {
            if (f == from && t == to && l == len) return
            from = f
            to = t
            len = l
            paint.shader = LinearGradientShader(Offset.Zero, Offset(l, 0f), listOf(f, t))
        }
    }

    public companion object {
        public const val ID: String = "lightRays"
        internal const val BEAMS: Int = 5 // F: lib/ui/kit/live_background/styles/kit_live_light_rays.dart:28
    }
}
