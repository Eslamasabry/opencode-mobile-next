package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.liveStyleCaustics
import org.jetbrains.compose.resources.StringResource
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The shimmering net of light on the floor of a pool, moving gently; the water gets livelier and greener while a reply
 * runs (Dart `KitLiveCaustics`, `styles/kit_live_caustics.dart`).
 *
 * A small grid of values (a cell of 8 logical pixels or a 56th of the width) is computed each frame into one reused
 * bitmap ([LiveField]) and drawn scaled with bilinear smoothing, as the Dart mesh is smoothed by the GPU.
 */
public object KitLiveCaustics : KitLiveStyle() {
    override val id: String = "caustics"
    override val label: StringResource = Res.string.liveStyleCaustics

    /** The grid's cell, in logical pixels, for a surface [width] wide. */
    internal fun cellFor(width: Double): Double = max(8.0, width / 56) // F: lib/ui/kit/live_background/styles/kit_live_caustics.dart:32

    /** The water's time at clock [clock] (the clock runs 1 + 1.4 × running). */
    internal fun waterTime(clock: Double): Double = clock * .25 // F: lib/ui/kit/live_background/styles/kit_live_caustics.dart:31

    /** The light at (u, v) at water time [tt], 0..1 (Dart lines 68-73). */
    internal fun lightAt(u: Double, v: Double, tt: Double): Double = lightOf(u, v, tt, liveFbm(warpX(u, tt), warpY(v, tt)))

    private fun warpX(u: Double, tt: Double) = u * 2 + tt * .3 // F: lib/ui/kit/live_background/styles/kit_live_caustics.dart:68

    private fun warpY(v: Double, tt: Double) = v * 2 - tt * .2 // F: lib/ui/kit/live_background/styles/kit_live_caustics.dart:68

    /** The light given the warp noise [noise] = liveFbm(warpX, warpY). */
    private fun lightOf(u: Double, v: Double, tt: Double, noise: Double): Double {
        val w = noise * 5 // F: lib/ui/kit/live_background/styles/kit_live_caustics.dart:68
        val a = sin(u * 13 + w + tt) // F: lib/ui/kit/live_background/styles/kit_live_caustics.dart:69
        val bb = sin(v * 11 - w * .8 - tt * .7) // F: lib/ui/kit/live_background/styles/kit_live_caustics.dart:70
        val d = sin((u + v) * 9 + w * .6 + tt * .5) // F: lib/ui/kit/live_background/styles/kit_live_caustics.dart:71
        val value = abs(a + bb + d) / 3 // F: lib/ui/kit/live_background/styles/kit_live_caustics.dart:72
        return liveSmooth(.62, .95, 1 - value) // F: lib/ui/kit/live_background/styles/kit_live_caustics.dart:73
    }

    private class Mesh(val field: LiveField) {
        val xs = DoubleArray(field.cols + 1)
        val ys = DoubleArray(field.rows + 1)
        val noise = LiveFbmGrid(sinHash = true)
    }

    override fun paint(canvas: DrawScope, size: Size, frame: KitLiveFrame) {
        val p = frame.palette
        val peak = livePeak(frame)
        if (peak <= 0) return
        val run = frame.running
        val tt = waterTime(liveClock(frame, "caustics", 1 + run * 1.4)) // F: lib/ui/kit/live_background/styles/kit_live_caustics.dart:31
        val cell = cellFor(size.width.toDouble())
        val mesh =
            liveCached(frame, "caustics.mesh", size) {
                Mesh(LiveField(ceil(size.width / cell).toInt(), ceil(size.height / cell).toInt()))
            }
        val field = mesh.field
        val c = liveMix(p.teal, p.glow, run)
        val rgb = ((c.red * 255).roundToInt() shl 16) or ((c.green * 255).roundToInt() shl 8) or (c.blue * 255).roundToInt() // F: lib/ui/kit/live_background/styles/kit_live_caustics.dart:61,62,75
        val inv = size.width / cell // columns across the width
        // The warp noise is separable (x by column, y by row), so each lattice point is hashed once a frame.
        for (x in 0..field.cols) mesh.xs[x] = warpX(x / inv, tt)
        for (y in 0..field.rows) mesh.ys[y] = warpY(y / inv, tt)
        mesh.noise.columns(mesh.xs, field.cols + 1)
        mesh.noise.rows(mesh.ys, field.rows + 1)
        var n = 0
        for (y in 0..field.rows) {
            val v = y / inv
            for (x in 0..field.cols) {
                val u = x / inv
                val alpha = (lightOf(u, v, tt, mesh.noise.at(x, y)) * peak * 255).roundToInt() // F: lib/ui/kit/live_background/styles/kit_live_caustics.dart:74
                field.colors[n++] = (alpha shl 24) or rgb // F: lib/ui/kit/live_background/styles/kit_live_caustics.dart:75
            }
        }
        field.upload()
        field.draw(canvas, cell.toFloat(), cell.toFloat())
    }
}
