package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.liveStyleConstellation
import org.jetbrains.compose.resources.StringResource
import kotlin.math.sqrt

/**
 * Constellation (Dart `KitLiveConstellation`, `styles/kit_live_constellation.dart`): a few dozen faint stars drift and
 * join with thin lines when near; they move faster and reach further, in the glow's colour, while a reply runs.
 */
public class KitLiveConstellation : KitLiveStyle() {
    override val id: String = ID
    override val label: StringResource = Res.string.liveStyleConstellation

    override fun paint(scope: DrawScope, size: Size, frame: KitLiveFrame) {
        val p = frame.palette
        val a = strengthOf(frame)
        val run = frame.running
        val t = frame.t
        val w = size.width.toDouble()
        val h = size.height.toDouble()
        val st = frame.memory.of("s") { Stars(w, h) }
        if (st.w != w || st.h != h) {
            st.reset(w, h)
            st.last = t
        }
        var dt = t - st.last
        st.last = t
        if (dt < 0 || dt > .2) dt = 1.0 / 24 // F: lib/ui/kit/live_background/styles/kit_live_constellation.dart:32
        val steps = dt * 30 * (w / 390) // F: lib/ui/kit/live_background/styles/kit_live_constellation.dart:33
        val reach = w * (.22 + run * .12) // F: lib/ui/kit/live_background/styles/kit_live_constellation.dart:34
        val speed = 1 + run * 2 // F: lib/ui/kit/live_background/styles/kit_live_constellation.dart:36
        for (i in 0 until COUNT) {
            st.x[i] = dmod(st.x[i] + st.vx[i] * speed * steps + w, w)
            st.y[i] = dmod(st.y[i] + st.vy[i] * speed * steps + h, h)
        }
        val linkColour = mixColor(p.ink, p.glow, run)
        for (i in 0 until COUNT) {
            for (j in i + 1 until COUNT) {
                val dx = st.x[i] - st.x[j]
                val dy = st.y[i] - st.y[j]
                val d = sqrt(dx * dx + dy * dy)
                if (d < reach) {
                    scope.drawLine(
                        withA(linkColour, (1 - d / reach) * .16 * a), // F: lib/ui/kit/live_background/styles/kit_live_constellation.dart:48
                        Offset(st.x[i].toFloat(), st.y[i].toFloat()),
                        Offset(st.x[j].toFloat(), st.y[j].toFloat()),
                        strokeWidth = LINE,
                    )
                }
            }
        }
        val dot = withA(mixColor(p.ink, p.glow, run * .7), .35 * a) // F: lib/ui/kit/live_background/styles/kit_live_constellation.dart:53-54
        for (i in 0 until COUNT) {
            scope.drawCircle(dot, DOT, Offset(st.x[i].toFloat(), st.y[i].toFloat()))
        }
    }

    /** Dart `_Stars`: the stars' places and speeds, reseeded (seed 5) whenever the surface changes size. */
    internal class Stars(w: Double, h: Double) {
        var w = 0.0
        var h = 0.0
        var last = 0.0
        val x = DoubleArray(COUNT)
        val y = DoubleArray(COUNT)
        val vx = DoubleArray(COUNT)
        val vy = DoubleArray(COUNT)

        init {
            reset(w, h)
        }

        fun reset(nw: Double, nh: Double) {
            w = nw
            h = nh
            val rnd = DartRandom(SEED)
            for (i in 0 until COUNT) {
                x[i] = rnd.nextDouble() * nw
                y[i] = rnd.nextDouble() * nh
                vx[i] = (rnd.nextDouble() - .5) * .12 // F: lib/ui/kit/live_background/styles/kit_live_constellation.dart:82
                vy[i] = (rnd.nextDouble() - .5) * .12 // F: lib/ui/kit/live_background/styles/kit_live_constellation.dart:82
            }
        }
    }

    public companion object {
        public const val ID: String = "constellation"
        internal const val COUNT: Int = 38 // F: lib/ui/kit/live_background/styles/kit_live_constellation.dart:78
        internal const val SEED: Int = 5 // F: lib/ui/kit/live_background/styles/kit_live_constellation.dart:76
        internal const val LINE: Float = .6f // F: lib/ui/kit/live_background/styles/kit_live_constellation.dart:39
        internal const val DOT: Float = 1.1f // F: lib/ui/kit/live_background/styles/kit_live_constellation.dart:56
    }
}
