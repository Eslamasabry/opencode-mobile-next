package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.liveStyleHalftone
import org.jetbrains.compose.resources.StringResource
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sin

/**
 * Halftone (Dart `KitLiveHalftone`, `styles/kit_live_halftone.dart`): a grid of print dots whose size follows a slow
 * diagonal wave bent by still noise; while a reply runs the lower page warms towards the glow.
 */
public class KitLiveHalftone : KitLiveStyle() {
    override val id: String = ID
    override val label: StringResource = Res.string.liveStyleHalftone

    override fun paint(scope: DrawScope, size: Size, frame: KitLiveFrame) {
        val p = frame.palette
        // Calibrated so text keeps its contrast at the strongest moment (test/kit/kit_live_background_test.dart).
        val a = strengthOf(frame) * .65 // F: lib/ui/kit/live_background/styles/kit_live_halftone.dart:22
        val run = frame.running
        val t = frame.t
        val w = size.width.toDouble()
        val h = size.height.toDouble()
        val sp = max(9.0, w / 26) // F: lib/ui/kit/live_background/styles/kit_live_halftone.dart:26
        val cols = ceil(w / sp).toInt()
        val rows = ceil(h / sp).toInt()
        val warp =
            frame.memory.of("w${size.width}${size.height}") {
                DoubleArray(rows * cols) { k ->
                    val r = k / cols
                    val c = k % cols
                    fbm((c * sp + sp / 2) * .01, (r * sp + sp / 2) * .01) * 3 // F: lib/ui/kit/live_background/styles/kit_live_halftone.dart:32
                }
            }
        val drift = t * (.5 + run * .9) // F: lib/ui/kit/live_background/styles/kit_live_halftone.dart:48
        for (r in 0 until rows) {
            val y = r * sp + sp / 2 // F: lib/ui/kit/live_background/styles/kit_live_halftone.dart:39
            val g = run * smooth(.4, 1.0, y / h) // F: lib/ui/kit/live_background/styles/kit_live_halftone.dart:52
            val colour = mixColor(p.ink, p.glow, g)
            val alpha = (.12 + g * .15) * a // F: lib/ui/kit/live_background/styles/kit_live_halftone.dart:53
            for (c in 0 until cols) {
                val x = c * sp + sp / 2 // F: lib/ui/kit/live_background/styles/kit_live_halftone.dart:41
                val wv = .5 + .5 * sin(x * .025 + y * .018 - drift + warp[r * cols + c]) // F: lib/ui/kit/live_background/styles/kit_live_halftone.dart:45-49
                val rad = wv * wv * sp * .28 // F: lib/ui/kit/live_background/styles/kit_live_halftone.dart:51
                scope.drawCircle(withA(colour, alpha), rad.toFloat(), Offset(x.toFloat(), y.toFloat()))
            }
        }
    }

    public companion object {
        public const val ID: String = "halftone"
    }
}
