package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import kotlin.math.sin

/**
 * Wide ribbons of satin light fold and unfold slowly across the screen; they ripple and pick up the green glow while a
 * reply runs (Dart `KitLiveSilk`, `lib/ui/kit/live_background/styles/kit_live_silk.dart`).
 *
 * Each ribbon keeps its path and its paint in the frame's memory and rewrites the path in place; its gradient is
 * rebuilt only when the ribbon's band or colour changes (never while idle), so an idle frame allocates nothing.
 */
public object KitLiveSilk : KitLiveStyle() {
    override val id: String = "silk" // F: lib/ui/kit/live_background/styles/kit_live_silk.dart:16

    internal const val RUN_RATE: Double = .7 // F: lib/ui/kit/live_background/styles/kit_live_silk.dart:27
    internal const val STEPS: Int = 48 // F: lib/ui/kit/live_background/styles/kit_live_silk.dart:28
    internal const val RIBBONS: Int = 5 // F: lib/ui/kit/live_background/styles/kit_live_silk.dart:29
    internal const val TOP: Double = .15 // F: lib/ui/kit/live_background/styles/kit_live_silk.dart:30
    internal const val GAP: Double = .18 // F: lib/ui/kit/live_background/styles/kit_live_silk.dart:30
    internal const val AMP: Double = .06 // F: lib/ui/kit/live_background/styles/kit_live_silk.dart:31
    internal const val AMP_RUN: Double = .5 // F: lib/ui/kit/live_background/styles/kit_live_silk.dart:31
    internal const val SPEED: Double = .18 // F: lib/ui/kit/live_background/styles/kit_live_silk.dart:32
    internal const val SPEED_STEP: Double = .03 // F: lib/ui/kit/live_background/styles/kit_live_silk.dart:32
    internal const val GLOW_SPREAD: Double = 4.0 // F: lib/ui/kit/live_background/styles/kit_live_silk.dart:33
    internal const val WAVE: Double = 3.0 // F: lib/ui/kit/live_background/styles/kit_live_silk.dart:37,42
    internal const val RIPPLE: Double = 7.0 // F: lib/ui/kit/live_background/styles/kit_live_silk.dart:38,43
    internal const val RIPPLE_SPEED: Double = .6 // F: lib/ui/kit/live_background/styles/kit_live_silk.dart:38,43
    internal const val RIPPLE_AMP: Double = .3 // F: lib/ui/kit/live_background/styles/kit_live_silk.dart:38,43
    internal const val FOLD: Double = .8 // F: lib/ui/kit/live_background/styles/kit_live_silk.dart:42
    internal const val BAND: Double = 2.0 // F: lib/ui/kit/live_background/styles/kit_live_silk.dart:57,58
    internal const val ALPHA: Double = .95 // F: lib/ui/kit/live_background/styles/kit_live_silk.dart:61
    internal val STOPS: List<Float> = listOf(0f, .5f, 1f) // F: lib/ui/kit/live_background/styles/kit_live_silk.dart:64

    /** The ribbon's upper edge at [x] (kit_live_silk.dart:32-36). */
    internal fun top(x: Double, w: Double, base: Double, amp: Double, ph: Double): Double =
        base + sin(x / w * WAVE + ph) * amp + sin(x / w * RIPPLE - ph * RIPPLE_SPEED) * amp * RIPPLE_AMP - amp

    /** The ribbon's lower edge at [x] (kit_live_silk.dart:37-41). */
    internal fun bottom(x: Double, w: Double, base: Double, amp: Double, ph: Double): Double =
        base + sin(x / w * WAVE + ph + FOLD) * amp + sin(x / w * RIPPLE - ph * RIPPLE_SPEED) * amp * RIPPLE_AMP + amp

    private class Ribbon {
        val path = Path()
        val paint = Paint()
        var size = Size.Unspecified
        var base = Double.NaN
        var amp = Double.NaN
        var col = Color.Unspecified
        var peak = Double.NaN

        /** Whether the gradient was built for another band or colour. */
        fun stale(size: Size, base: Double, amp: Double, col: Color, peak: Double): Boolean =
            this.size != size || this.base != base || this.amp != amp || this.col != col || this.peak != peak
    }

    private class Ribbons {
        val all = Array(RIBBONS) { Ribbon() }
    }

    override fun paint(canvas: Canvas, size: Size, frame: KitLiveFrame) {
        val p = frame.palette
        val peak = livePeak(frame)
        if (peak <= 0) return
        val run = frame.running
        val clock = liveClock(frame, "silk", 1 + run * RUN_RATE)
        val w = size.width.toDouble()
        val step = w / STEPS
        val ribbons = frame.memory.of("silk.ribbons", ::Ribbons).all
        for (k in 0 until RIBBONS) {
            val base = size.height * (TOP + k * GAP)
            val amp = size.height * AMP * (1 + run * AMP_RUN)
            val ph = clock * (SPEED + k * SPEED_STEP) + k
            val col = liveMix(if (k and 1 == 1) p.teal else p.blue, p.glow, run * (k / GLOW_SPREAD))
            val ribbon = ribbons[k]
            val path = ribbon.path
            path.rewind()
            path.moveTo(0f, top(0.0, w, base, amp, ph).toFloat())
            var x = step
            while (x <= w + step / 2) { // F: lib/ui/kit/live_background/styles/kit_live_silk.dart:46
                path.lineTo(x.toFloat(), top(x, w, base, amp, ph).toFloat())
                x += step
            }
            x = w
            while (x >= -step / 2) { // F: lib/ui/kit/live_background/styles/kit_live_silk.dart:49
                path.lineTo(x.toFloat(), bottom(x, w, base, amp, ph).toFloat())
                x -= step
            }
            path.close()
            if (ribbon.stale(size, base, amp, col, peak)) {
                ribbon.size = size
                ribbon.base = base
                ribbon.amp = amp
                ribbon.col = col
                ribbon.peak = peak
                ribbon.paint.shader = LinearGradientShader(
                    from = Offset(0f, (base - amp * BAND).toFloat()),
                    to = Offset(0f, (base + amp * BAND).toFloat()),
                    colors = listOf(liveAlpha(col, 0.0), liveAlpha(col, peak * ALPHA), liveAlpha(col, 0.0)),
                    colorStops = STOPS,
                )
            }
            canvas.drawPath(path, ribbon.paint)
        }
    }
}
