package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.liveStyleBrushedMetal
import org.jetbrains.compose.resources.StringResource
import kotlin.math.ceil

/**
 * A fine brushed-metal grain with a soft highlight gliding across it, like an aluminium case catching light; the
 * highlight warms to green and glides faster while a reply runs (Dart `KitLiveBrushedMetal`,
 * `styles/kit_live_brushed_metal.dart`).
 *
 * The grain (two strokes of random strength per one-pixel row, as the Dart picture records) is drawn once per size,
 * colour and strength into a bitmap at the page's own scale and redrawn whole; the highlight is one gradient built once
 * and moved, so a frame builds nothing.
 */
public object KitLiveBrushedMetal : KitLiveStyle() {
    override val id: String = "brushedMetal"
    override val label: StringResource = Res.string.liveStyleBrushedMetal

    /** The grain's strongest stroke against the peak. */
    internal const val GRAIN = .55 // F: lib/ui/kit/live_background/styles/kit_live_brushed_metal.dart:37

    /** The highlight's peak against the peak. */
    internal const val SHINE = .45 // F: lib/ui/kit/live_background/styles/kit_live_brushed_metal.dart:62

    /** The highlight's centre, in widths, at clock [clock] (Dart line 51). */
    internal fun shineAt(clock: Double): Double = (dmod(clock * .05, 1.6) - .3) * 2 // F: lib/ui/kit/live_background/styles/kit_live_brushed_metal.dart:51

    private class Grain {
        var ink = Color.Unspecified
        var peak = -1.0
        var size = Size.Zero
        var image: ImageBitmap? = null
    }

    private class Shine {
        var col = Color.Unspecified
        var peak = -1.0
        var size = Size.Zero
        var brush: Brush? = null
    }

    override fun paint(canvas: DrawScope, size: Size, frame: KitLiveFrame) {
        val p = frame.palette
        val peak = livePeak(frame)
        if (peak <= 0) return
        val g = frame.memory.of("brushed.grain") { Grain() }
        if (g.ink != p.ink || g.peak != peak || g.size != size || g.image == null) {
            g.ink = p.ink
            g.peak = peak
            g.size = size
            g.image = run {
                val w = ceil(size.width).toInt().coerceAtLeast(1)
                val h = ceil(size.height).toInt().coerceAtLeast(1)
                val image = ImageBitmap(w, h)
                val c = Canvas(image)
                val rnd = liveRandom(id)
                val paint = Paint()
                var y = 0f
                while (y < size.height) {
                    val split = (rnd.nextDouble() * size.width).toFloat()
                    paint.color = liveAlpha(p.ink, peak * GRAIN * rnd.nextDouble())
                    c.drawRect(0f, y, split, y + 1, paint) // F: lib/ui/kit/live_background/styles/kit_live_brushed_metal.dart:38
                    paint.color = liveAlpha(p.ink, peak * GRAIN * rnd.nextDouble())
                    c.drawRect(split, y, size.width, y + 1, paint) // F: lib/ui/kit/live_background/styles/kit_live_brushed_metal.dart:40
                    y++
                }
                liveSettle(image)
                image
            }
        }
        val image = g.image!!
        canvas.drawImage(
            image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstSize = IntSize(image.width, image.height),
            filterQuality = FilterQuality.None,
        )

        val run = frame.running
        val clock = liveClock(frame, "brushed", 1 + run * 2) // F: lib/ui/kit/live_background/styles/kit_live_brushed_metal.dart:50
        val x = (shineAt(clock) * size.width).toFloat()
        val col = liveMix(p.ink, p.glow, run * .8) // F: lib/ui/kit/live_background/styles/kit_live_brushed_metal.dart:52
        val s = frame.memory.of("brushed.shine") { Shine() }
        if (s.col != col || s.peak != peak || s.size != size || s.brush == null) {
            s.col = col
            s.peak = peak
            s.size = size
            // The gradient at x = 0; a frame moves it by x.
            s.brush =
                Brush.linearGradient(
                    0f to liveAlpha(col, 0.0),
                    .5f to liveAlpha(col, peak * SHINE), // F: lib/ui/kit/live_background/styles/kit_live_brushed_metal.dart:65
                    1f to liveAlpha(col, 0.0),
                    start = Offset(-size.width * .5f, 0f), // F: lib/ui/kit/live_background/styles/kit_live_brushed_metal.dart:58
                    end = Offset(size.width * .2f, size.height * .6f), // F: lib/ui/kit/live_background/styles/kit_live_brushed_metal.dart:59
                )
        }
        canvas.translate(x, 0f) { drawRect(s.brush!!, topLeft = Offset(-x, 0f), size = size) }
    }
}
