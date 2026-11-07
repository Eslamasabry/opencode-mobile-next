package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.liveStyleBokeh
import org.jetbrains.compose.resources.StringResource
import kotlin.math.sin

/**
 * Out-of-focus lights drift slowly upward, like city lights through a lens; green lights join and drift a little
 * faster while a reply runs (Dart `KitLiveBokeh`, `styles/kit_live_bokeh.dart`).
 *
 * Each light's soft disc is one radial brush built once (per size, colour and strength) around the origin and moved to
 * its centre; a running light fades in through the draw's alpha, so a frame builds nothing.
 */
public object KitLiveBokeh : KitLiveStyle() {
    override val id: String = "bokeh"
    override val label: StringResource = Res.string.liveStyleBokeh

    /** How many lights; the last ones are the running (green) lights. */
    internal const val COUNT = 20 // F: lib/ui/kit/live_background/styles/kit_live_bokeh.dart:31

    /** Lights from this index on show only while a reply runs. */
    internal const val FIRST_GREEN = 12 // F: lib/ui/kit/live_background/styles/kit_live_bokeh.dart:38

    /** One light: its place, size (of the width), rise speed (heights a second) and colour index. */
    internal class Light(val x: Double, val y: Double, val r: Double, val v: Double, val c: Int, val green: Boolean)

    /** The lights, as the Dart seed draws them. */
    internal fun lights(): List<Light> {
        val rnd = liveRandom(id)
        return List(COUNT) { i ->
            val x = rnd.nextDouble()
            val y = rnd.nextDouble() * 1.4 - .2 // F: lib/ui/kit/live_background/styles/kit_live_bokeh.dart:34
            val r = .04 + rnd.nextDouble() * .1 // F: lib/ui/kit/live_background/styles/kit_live_bokeh.dart:35
            val v = .004 + rnd.nextDouble() * .01 // F: lib/ui/kit/live_background/styles/kit_live_bokeh.dart:36
            Light(x, y, r, v, i % 3, i >= FIRST_GREEN) // F: lib/ui/kit/live_background/styles/kit_live_bokeh.dart:37
        }
    }

    /** Where light [b] is, as a height share (-.2 .. 1.2), at clock [clock]. */
    internal fun yAt(b: Light, clock: Double): Double = dmod(dmod(b.y + .2 - b.v * clock, 1.4) + 1.4, 1.4) - .2 // F: lib/ui/kit/live_background/styles/kit_live_bokeh.dart:46

    private class Discs(val lights: List<Light>) {
        val brushes = arrayOfNulls<Brush>(lights.size)
        var w = -1f
        var peak = -1.0
        var teal = Color.Unspecified
        var blue = Color.Unspecified
        var ink = Color.Unspecified
        var glow = Color.Unspecified
    }

    override fun paint(canvas: DrawScope, size: Size, frame: KitLiveFrame) {
        val p = frame.palette
        val peak = livePeak(frame)
        if (peak <= 0) return
        val run = frame.running
        val clock = liveClock(frame, "bokeh", 1 + run)
        val d = frame.memory.of("bokeh.lights") { Discs(lights()) }
        if (d.w != size.width || d.peak != peak || d.teal != p.teal || d.blue != p.blue || d.ink != p.ink || d.glow != p.glow) {
            d.w = size.width
            d.peak = peak
            d.teal = p.teal
            d.blue = p.blue
            d.ink = p.ink
            d.glow = p.glow
            for ((i, b) in d.lights.withIndex()) {
                val col =
                    if (b.green) p.glow
                    else when (b.c) { 0 -> p.teal; 1 -> p.blue; else -> p.ink }
                val al = peak * .4 // F: lib/ui/kit/live_background/styles/kit_live_bokeh.dart:53
                d.brushes[i] =
                    Brush.radialGradient(
                        0f to liveAlpha(col, al),
                        .75f to liveAlpha(col, al * .8), // F: lib/ui/kit/live_background/styles/kit_live_bokeh.dart:63,66
                        1f to liveAlpha(col, 0.0),
                        center = Offset.Zero,
                        radius = (b.r * size.width).toFloat(),
                    )
            }
        }
        for (i in d.lights.indices) {
            val b = d.lights[i]
            val k = if (b.green) run else 1.0
            if (k < .02) continue // F: lib/ui/kit/live_background/styles/kit_live_bokeh.dart:45
            val y = yAt(b, clock)
            val x = b.x * size.width + sin(frame.t * .2 + b.r * 40) * size.width * .03 // F: lib/ui/kit/live_background/styles/kit_live_bokeh.dart:49
            val radius = (b.r * size.width).toFloat()
            canvas.translate(x.toFloat(), (y * size.height).toFloat()) {
                drawCircle(d.brushes[i]!!, radius = radius, center = Offset.Zero, alpha = k.toFloat())
            }
        }
    }
}
