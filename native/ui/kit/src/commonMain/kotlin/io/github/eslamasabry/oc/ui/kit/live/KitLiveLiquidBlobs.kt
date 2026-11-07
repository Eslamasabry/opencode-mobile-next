package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.liveStyleLiquidBlobs
import org.jetbrains.compose.resources.StringResource
import kotlin.math.cos
import kotlin.math.sin

/**
 * Liquid blobs (Dart `KitLiveLiquidBlobs`, `styles/kit_live_liquid_blobs.dart`): three soft metaballs in blue and teal
 * drift and merge; while a reply runs a fourth, in the glow's colour, rises from below. A colour field on a 6 dp grid,
 * recomputed at most every 30 ms and drawn as one vertex mesh.
 */
public class KitLiveLiquidBlobs : KitLiveStyle() {
    override val id: String = ID
    override val label: StringResource = Res.string.liveStyleLiquidBlobs

    override fun paint(scope: DrawScope, size: Size, frame: KitLiveFrame) {
        val p = frame.palette
        val a = strengthOf(frame)
        val run = frame.running
        val tt = frame.t * .15 // F: lib/ui/kit/live_background/styles/kit_live_liquid_blobs.dart:23
        val blue = rgb(p.blue)
        val teal = rgb(p.teal)
        val glow = rgb(p.glow)
        val b = frame.memory.of("blobs") { Blobs() }
        var i = 0
        b.set(i++, .3 + .2 * sin(tt * .8), .4 + .25 * cos(tt * .6), .05, blue) // F: lib/ui/kit/live_background/styles/kit_live_liquid_blobs.dart:26
        b.set(i++, .7 + .2 * cos(tt * .7), .8 + .3 * sin(tt * .5), .06, teal) // F: lib/ui/kit/live_background/styles/kit_live_liquid_blobs.dart:27
        b.set(i++, .5 + .25 * sin(tt * .4 + 2), 1.3 + .2 * cos(tt * .9), .05, teal) // F: lib/ui/kit/live_background/styles/kit_live_liquid_blobs.dart:29-32
        b.set(i, .5, 2.0 - run * .45, .06 * run, glow) // F: lib/ui/kit/live_background/styles/kit_live_liquid_blobs.dart:34
        val alpha = .15 * a // F: lib/ui/kit/live_background/styles/kit_live_liquid_blobs.dart:47
        paintField(scope, size, frame, "f", DIV, every = EVERY) { u, v ->
            var s = 0.0
            var r = 0.0
            var g = 0.0
            var bl = 0.0
            for (q in 0 until BLOBS) {
                if (b.weight[q] <= 0) continue
                val d = (u - b.x[q]) * (u - b.x[q]) + (v - b.y[q]) * (v - b.y[q]) + .0001 // F: lib/ui/kit/live_background/styles/kit_live_liquid_blobs.dart:40
                val k = b.weight[q] / d
                s += k
                r += b.red[q] * k
                g += b.green[q] * k
                bl += b.blue[q] * k
            }
            packArgb(r / s, g / s, bl / s, smooth(.7, 1.4, s) * alpha) // F: lib/ui/kit/live_background/styles/kit_live_liquid_blobs.dart:47
        }
    }

    /** The blobs of this frame, kept so a frame allocates nothing: place, weight and colour (0..255). */
    internal class Blobs {
        val x = DoubleArray(BLOBS)
        val y = DoubleArray(BLOBS)
        val weight = DoubleArray(BLOBS)
        val red = DoubleArray(BLOBS)
        val green = DoubleArray(BLOBS)
        val blue = DoubleArray(BLOBS)

        fun set(i: Int, px: Double, py: Double, w: Double, c: DoubleArray) {
            x[i] = px
            y[i] = py
            weight[i] = w
            red[i] = c[0]
            green[i] = c[1]
            blue[i] = c[2] // F: lib/ui/kit/live_background/styles/kit_live_liquid_blobs.dart:29
        }
    }

    public companion object {
        public const val ID: String = "liquidBlobs"
        internal const val DIV: Int = 6 // F: lib/ui/kit/live_background/styles/kit_live_liquid_blobs.dart:36
        internal const val EVERY: Double = .03 // F: lib/ui/kit/live_background/styles/kit_live_liquid_blobs.dart:48
        internal const val BLOBS: Int = 4 // NV: the Dart list holds four blobs
    }
}
