package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.liveStyleDotRipple
import org.jetbrains.compose.resources.StringResource
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Dot ripple (Dart `KitLiveDotRipple`, `styles/kit_live_dot_ripple.dart`): a grid of faint dots; a ring of brighter,
 * larger dots spreads from the bottom middle, every seven seconds (every 2.2 while a reply runs).
 */
public class KitLiveDotRipple : KitLiveStyle() {
    override val id: String = ID
    override val label: StringResource = Res.string.liveStyleDotRipple

    override fun paint(scope: DrawScope, size: Size, frame: KitLiveFrame) {
        val p = frame.palette
        // Calibrated so text keeps its contrast at the strongest moment (test/kit/kit_live_background_test.dart).
        val a = strengthOf(frame) * .28 // F: lib/ui/kit/live_background/styles/kit_live_dot_ripple.dart:22
        val run = frame.running
        val t = frame.t
        val w = size.width.toDouble()
        val h = size.height.toDouble()
        val sp = max(12.0, w / 18) // F: lib/ui/kit/live_background/styles/kit_live_dot_ripple.dart:26
        val ox = w / 2 // F: lib/ui/kit/live_background/styles/kit_live_dot_ripple.dart:26
        val oy = h * .93 // F: lib/ui/kit/live_background/styles/kit_live_dot_ripple.dart:26
        val period = periodFor(run)
        val ph = dmod(t, period) / period
        val radius = ph * h * 1.15 // F: lib/ui/kit/live_background/styles/kit_live_dot_ripple.dart:29
        val spread = sp * 1.6 // F: lib/ui/kit/live_background/styles/kit_live_dot_ripple.dart:35
        val twoS2 = 2 * spread * spread // F: lib/ui/kit/live_background/styles/kit_live_dot_ripple.dart:26
        val lift = if (run > .5) 1.0 else .6 // F: lib/ui/kit/live_background/styles/kit_live_dot_ripple.dart:37
        var y = sp / 2 // F: lib/ui/kit/live_background/styles/kit_live_dot_ripple.dart:31
        while (y < h) {
            var x = sp / 2 // F: lib/ui/kit/live_background/styles/kit_live_dot_ripple.dart:32
            while (x < w) {
                val d = sqrt((x - ox) * (x - ox) + (y - oy) * (y - oy))
                val ring = exp(-(d - radius) * (d - radius) / twoS2) * (1 - ph)
                val al = (.10 + ring * .55 * lift) * a // F: lib/ui/kit/live_background/styles/kit_live_dot_ripple.dart:37
                scope.drawCircle(
                    withA(mixColor(p.ink, p.glow, ring * 1.5), al), // F: lib/ui/kit/live_background/styles/kit_live_dot_ripple.dart:38
                    (1 + ring * 1.3).toFloat(), // F: lib/ui/kit/live_background/styles/kit_live_dot_ripple.dart:39
                    Offset(x.toFloat(), y.toFloat()),
                )
                x += sp
            }
            y += sp
        }
    }

    public companion object {
        public const val ID: String = "dotRipple"

        /** Seconds between two rings: 7 idle, 2.2 while a reply runs. */
        internal fun periodFor(run: Double): Double = if (run > .5) 2.2 else 7.0 // F: lib/ui/kit/live_background/styles/kit_live_dot_ripple.dart:27
    }
}
