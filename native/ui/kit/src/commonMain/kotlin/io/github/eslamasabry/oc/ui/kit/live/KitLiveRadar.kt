package io.github.eslamasabry.oc.ui.kit.live

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SweepGradientShader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Range rings and a slow sweeping beam; small contacts light up as it passes and fade. The sweep turns faster, with
 * more contacts, while a reply runs (Dart `KitLiveRadar`, `lib/ui/kit/live_background/styles/kit_live_radar.dart`).
 *
 * The beam's sweep gradient is built once per size and colour and turned with the canvas (Flutter turns the gradient
 * with `GradientRotation`; a circle turned about its centre is the same circle), so a frame allocates nothing.
 */
public object KitLiveRadar : KitLiveStyle() {
    override val id: String = "radar" // F: lib/ui/kit/live_background/styles/kit_live_radar.dart:19

    override val ceilingFactor: Double get() = CEILING_FACTOR

    internal const val CEILING_FACTOR: Double = 2.0 // F: lib/ui/kit/live_background/styles/kit_live_radar.dart:16
    internal const val CALIBRATED: Double = 0.72 // F: lib/ui/kit/live_background/styles/kit_live_radar.dart:28
    internal const val CENTRE_Y: Float = .62f // F: lib/ui/kit/live_background/styles/kit_live_radar.dart:30
    internal const val RADIUS: Float = .95f // F: lib/ui/kit/live_background/styles/kit_live_radar.dart:31
    internal const val RINGS: Int = 4 // F: lib/ui/kit/live_background/styles/kit_live_radar.dart:34
    internal const val RING_ALPHA: Double = .3 // F: lib/ui/kit/live_background/styles/kit_live_radar.dart:44
    internal const val TAU: Double = PI * 2 // F: lib/ui/kit/live_background/styles/kit_live_radar.dart:46
    internal const val RUN_RATE: Double = 2.0 // F: lib/ui/kit/live_background/styles/kit_live_radar.dart:48
    internal const val TURN: Double = .35 // F: lib/ui/kit/live_background/styles/kit_live_radar.dart:48
    internal const val BEAM_ALPHA: Double = .5 // F: lib/ui/kit/live_background/styles/kit_live_radar.dart:56
    internal val BEAM_STOPS: List<Float> = listOf(0f, .14f, .143f, 1f) // F: lib/ui/kit/live_background/styles/kit_live_radar.dart:60
    internal const val BEAM_LEAD: Double = .9 // F: lib/ui/kit/live_background/styles/kit_live_radar.dart:61
    internal const val BLIPS: Int = 14 // F: lib/ui/kit/live_background/styles/kit_live_radar.dart:67
    internal const val BLIP_REACH: Double = .9 // F: lib/ui/kit/live_background/styles/kit_live_radar.dart:68
    internal const val BLIP_INSET: Double = .08 // F: lib/ui/kit/live_background/styles/kit_live_radar.dart:68
    internal const val IDLE_BLIPS: Int = 6 // F: lib/ui/kit/live_background/styles/kit_live_radar.dart:73
    internal const val MIN_SHOWN: Double = .02 // F: lib/ui/kit/live_background/styles/kit_live_radar.dart:74,78
    internal const val FADE: Double = 2.6 // F: lib/ui/kit/live_background/styles/kit_live_radar.dart:77
    internal const val HALO_R: Float = 6f // F: lib/ui/kit/live_background/styles/kit_live_radar.dart:82
    internal const val HALO_ALPHA: Double = .15 // F: lib/ui/kit/live_background/styles/kit_live_radar.dart:83
    internal const val DOT_R: Float = 2f // F: lib/ui/kit/live_background/styles/kit_live_radar.dart:87
    internal const val DOT_ALPHA: Double = .55 // F: lib/ui/kit/live_background/styles/kit_live_radar.dart:88

    /** The contacts: angle and distance from the centre for a sweep of radius [r] (kit_live_radar.dart:64-70). */
    internal fun blipsFor(r: Double): DoubleArray {
        val rnd = liveRandom("radar")
        val out = DoubleArray(BLIPS * 2) // NV: angle and distance of each contact
        for (i in 0 until BLIPS) {
            val angle = rnd.nextDouble() * TAU
            val dist = rnd.nextDouble() * r * BLIP_REACH + r * BLIP_INSET
            out[i shl 1] = angle
            out[(i shl 1) + 1] = dist
        }
        return out
    }

    /** How lit a contact at angle [blip] is with the beam at [a] (kit_live_radar.dart:75-77). */
    internal fun lit(a: Double, blip: Double, k: Double): Double {
        var d = (a - blip) % TAU
        if (d < 0) d += TAU
        return max(0.0, 1 - d / FADE) * k
    }

    private class Kit {
        val rings = Paint().apply {
            style = PaintingStyle.Stroke
            strokeWidth = 1f
        }
        val beam = Paint()
        val dot = Paint()
        val halo = Paint()

        // The beam's gradient and what it was built for: rebuilt only when one of them changes.
        var beamSize = Size.Unspecified
        var beamGlow = Color.Unspecified
        var beamPeak = Double.NaN
    }

    override fun paint(canvas: Canvas, size: Size, frame: KitLiveFrame) {
        val p = frame.palette
        val peak = livePeak(frame) * ceilingFactor * CALIBRATED // calibrated: text3 keeps 4.5:1
        if (peak <= 0) return
        val c = Offset(size.width / 2, size.height * CENTRE_Y) // F: lib/ui/kit/live_background/styles/kit_live_radar.dart:30
        val r = size.width * RADIUS
        val rings = liveCached(frame, "radar.rings", size) {
            val path = Path()
            for (q in 1..RINGS) path.addOval(Rect(c, r * q / RINGS))
            path
        }
        val kit = frame.memory.of("radar.paints", ::Kit)
        kit.rings.color = liveAlpha(p.ink, peak * RING_ALPHA)
        canvas.drawPath(rings, kit.rings)
        val run = frame.running
        val a = liveClock(frame, "radar", 1 + run * RUN_RATE) * TURN % TAU
        val glow = p.glow
        if (kit.beamSize != size || kit.beamGlow != glow || kit.beamPeak != peak) {
            kit.beamSize = size
            kit.beamGlow = glow
            kit.beamPeak = peak
            kit.beam.shader = SweepGradientShader(
                center = c,
                colors = listOf(liveAlpha(glow, 0.0), liveAlpha(glow, peak * BEAM_ALPHA), liveAlpha(glow, 0.0), liveAlpha(glow, 0.0)),
                colorStops = BEAM_STOPS,
            )
        }
        canvas.save()
        canvas.translate(c.x, c.y)
        canvas.rotate(((a - BEAM_LEAD) * DEGREES).toFloat())
        canvas.translate(-c.x, -c.y)
        canvas.drawCircle(c, r, kit.beam)
        canvas.restore()
        val blips = liveCached(frame, "radar.blips", size) { blipsFor(r.toDouble()) }
        for (i in 0 until BLIPS) {
            val k = if (i < IDLE_BLIPS) 1.0 else run
            if (k < MIN_SHOWN) continue
            val angle = blips[i shl 1]
            val v = lit(a, angle, k)
            if (v < MIN_SHOWN) continue
            val dist = blips[(i shl 1) + 1]
            val at = Offset((c.x + cos(angle) * dist).toFloat(), (c.y + sin(angle) * dist).toFloat())
            kit.halo.color = liveAlpha(glow, peak * HALO_ALPHA * v)
            canvas.drawCircle(at, HALO_R, kit.halo)
            kit.dot.color = liveAlpha(glow, peak * DOT_ALPHA * v)
            canvas.drawCircle(at, DOT_R, kit.dot)
        }
    }

    private const val DEGREES: Double = 180 / PI // NV: radians to degrees for Canvas.rotate
}
