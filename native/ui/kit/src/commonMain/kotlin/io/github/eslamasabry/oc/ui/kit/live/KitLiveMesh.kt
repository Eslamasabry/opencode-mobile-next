package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.liveStyleMesh
import org.jetbrains.compose.resources.StringResource
import kotlin.math.cos
import kotlin.math.sin

/**
 * Breathing mesh (Dart `KitLiveMesh`, `styles/kit_live_mesh.dart`): four colour points (blue, teal, a teal-to-glow mix
 * and the glow) drift slowly and blend by inverse distance into one soft gradient that breathes; while a reply runs
 * the glow point rises. A colour field on an 8 dp grid, recomputed at most every 30 ms and drawn as one vertex mesh.
 */
public class KitLiveMesh : KitLiveStyle() {
    override val id: String = ID
    override val label: StringResource = Res.string.liveStyleMesh

    override fun paint(scope: DrawScope, size: Size, frame: KitLiveFrame) {
        val p = frame.palette
        val a = strengthOf(frame)
        val run = frame.running
        val tt = frame.t * .12 // F: lib/ui/kit/live_background/styles/kit_live_mesh.dart:23
        val blue = rgb(p.blue)
        val teal = rgb(p.teal)
        val glow = rgb(p.glow)
        val third = mixRgb(teal, glow, .4 + run * .6) // F: lib/ui/kit/live_background/styles/kit_live_mesh.dart:25
        val m = frame.memory.of("points") { Points() }
        var i = 0
        m.set(i++, .2 + .15 * sin(tt * .7), .2 + .1 * cos(tt * .5), blue) // F: lib/ui/kit/live_background/styles/kit_live_mesh.dart:27
        m.set(i++, .85 + .1 * cos(tt * .6), .3 + .15 * sin(tt * .4), teal) // F: lib/ui/kit/live_background/styles/kit_live_mesh.dart:28
        m.set(i++, .3 + .2 * sin(tt * .3), .9 + .1 * cos(tt * .8), third) // F: lib/ui/kit/live_background/styles/kit_live_mesh.dart:29
        m.set(i, .7 + .15 * sin(tt * .5 + 1), 1.5 - .25 * run + .1 * cos(tt * .9), glow) // F: lib/ui/kit/live_background/styles/kit_live_mesh.dart:31-33
        val alpha = .13 * a * (.8 + .2 * sin(tt)) // F: lib/ui/kit/live_background/styles/kit_live_mesh.dart:36
        paintField(scope, size, frame, "f", DIV, every = EVERY) { u, v ->
            var w = 0.0
            var r = 0.0
            var g = 0.0
            var b = 0.0
            for (q in 0 until POINTS) {
                val dx = u - m.x[q]
                val dy = v - m.y[q] * 1.9 // F: lib/ui/kit/live_background/styles/kit_live_mesh.dart:40
                val d = 1 / (dx * dx + dy * dy + .02) // F: lib/ui/kit/live_background/styles/kit_live_mesh.dart:41
                w += d
                r += m.red[q] * d
                g += m.green[q] * d
                b += m.blue[q] * d
            }
            packArgb(r / w, g / w, b / w, alpha)
        }
    }

    /** The points of this frame, kept so a frame allocates nothing: place and colour (0..255). */
    internal class Points {
        val x = DoubleArray(POINTS)
        val y = DoubleArray(POINTS)
        val red = DoubleArray(POINTS)
        val green = DoubleArray(POINTS)
        val blue = DoubleArray(POINTS)

        fun set(i: Int, px: Double, py: Double, c: DoubleArray) {
            x[i] = px
            y[i] = py
            red[i] = c[0]
            green[i] = c[1]
            blue[i] = c[2] // F: lib/ui/kit/live_background/styles/kit_live_mesh.dart:45
        }
    }

    public companion object {
        public const val ID: String = "mesh"
        internal const val DIV: Int = 8 // F: lib/ui/kit/live_background/styles/kit_live_mesh.dart:37
        internal const val EVERY: Double = .03 // F: lib/ui/kit/live_background/styles/kit_live_mesh.dart:48
        internal const val POINTS: Int = 4 // NV: the Dart list holds four points
    }
}
