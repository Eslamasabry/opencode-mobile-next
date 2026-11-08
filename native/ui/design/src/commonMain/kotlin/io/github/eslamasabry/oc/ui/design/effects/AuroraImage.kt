package io.github.eslamasabry.oc.ui.design.effects

import io.github.eslamasabry.oc.ui.design.color.Argb
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The painted aurora, the Glass background (Dart `KitLiveAuroraImage`,
 * `lib/ui/kit/live_background/styles/kit_live_aurora_image.dart` at GLASS_REF 1bbdc3f9c; id `auroraImage`). Two
 * plates, one per brightness (`aurora_dark.webp`, `aurora_light.webp`, byte-identical copies of
 * `assets/backgrounds/aurora-*.webp`), shown as a tint: every pixel keeps the page ground's brightness and adds only
 * [chromaFor] of the picture's colour (owner, 2026-10-26: the aurora toned down), turned to the accent's hue. It drifts
 * very slowly (a 1.06-1.10x zoom and a pan on long sine periods) and holds one still frame under reduced motion.
 *
 * Pure maths: the kit loads the plate and draws it with [matrixFor] as a colour matrix.
 */
public object AuroraImage {
    public const val ID: String = "auroraImage" // FG: lib/ui/kit/live_background/styles/kit_live_aurora_image.dart:44

    /** The plates' own hue (both are painted round it). */
    public const val PLATE_HUE: Double = 165.0 // FG: lib/ui/kit/live_background/styles/kit_live_aurora_image.dart:54

    /** An accent within this many degrees of [PLATE_HUE] keeps the plate as painted. */
    public const val KEEP_WITHIN: Double = 25.0 // FG: lib/ui/kit/live_background/styles/kit_live_aurora_image.dart:58

    /** The drift moves under a pixel a second: six frames a second are smooth to the eye. */
    public const val FRAMES_PER_SECOND: Int = 6 // FG: lib/ui/kit/live_background/styles/kit_live_aurora_image.dart:94

    public const val CHROMA_LIGHT: Float = .12f // FG: lib/ui/kit/live_background/styles/kit_live_aurora_image.dart:104
    public const val CHROMA_DARK: Float = .14f // FG: lib/ui/kit/live_background/styles/kit_live_aurora_image.dart:105

    /** After this long with no touch the look rests on its frame (Dart `KitLiveClock.idleAfter`). */
    public const val IDLE_AFTER_MS: Long = 30_000 // FG: lib/ui/kit/live_background/kit_live_clock.dart:44

    /** How much of the picture's colour shows. */
    public fun chromaFor(dark: Boolean): Float = if (dark) CHROMA_DARK else CHROMA_LIGHT

    /**
     * The person's Intensity (Dart `KitLiveIntensity.strength`): Subtle .7, Normal 1, Bold 1.6
     * (FG: lib/state/effects.dart:45-47). The picture is drawn at `strength` clamped to 1, so Normal and Bold show it whole and
     * Subtle lets the ground through (`kit_live_aurora_image.dart:305-312`).
     */
    public fun alphaFor(strength: Float): Float = strength.coerceIn(0f, 1f)

    // The plates' darkest and brightest spots (8 px blocks of the 1080 px plates), as painted: measured data of the
    // pictures, not colours of the theme. Frosted: under a 40 px Gaussian, a little less than Glass's frost spans.
    private val darkPlateBrightest = Rgb(225, 254, 218) // FG: lib/ui/kit/live_background/styles/kit_live_aurora_image.dart:110
    private val lightPlateDarkest = Rgb(1, 45, 48) // FG: lib/ui/kit/live_background/styles/kit_live_aurora_image.dart:111
    private val lightPlateBrightest = Rgb(226, 254, 228) // FG: lib/ui/kit/live_background/styles/kit_live_aurora_image.dart:112
    private val darkPlateFrostedDarkest = Rgb(0, 15, 16) // FG: lib/ui/kit/live_background/styles/kit_live_aurora_image.dart:124
    private val darkPlateFrostedBrightest = Rgb(76, 232, 160) // FG: lib/ui/kit/live_background/styles/kit_live_aurora_image.dart:125
    private val lightPlateFrostedDarkest = Rgb(1, 53, 54) // FG: lib/ui/kit/live_background/styles/kit_live_aurora_image.dart:126
    private val lightPlateFrostedBrightest = Rgb(122, 252, 200) // FG: lib/ui/kit/live_background/styles/kit_live_aurora_image.dart:127
    private val darkPlateDarkest = Rgb(0, 13, 15) // FG: lib/ui/kit/live_background/styles/kit_live_aurora_image.dart:128

    private data class Rgb(val r: Int, val g: Int, val b: Int)

    // Rec. 709 luma weights, as Dart writes them.
    private const val LR: Float = .2126f // FG: lib/ui/kit/live_background/styles/kit_live_aurora_image.dart:150
    private const val LG: Float = .7152f // FG: lib/ui/kit/live_background/styles/kit_live_aurora_image.dart:150
    private const val LB: Float = .0722f // FG: lib/ui/kit/live_background/styles/kit_live_aurora_image.dart:150

    /**
     * The 4x5 colour matrix (row-major, offsets 0..255, as Android's and Compose's `ColorMatrix`) that turns the plate to
     * [accent]'s hue and tints it onto [ground] (Dart `_matrixFor`, `kit_live_aurora_image.dart:141-145`).
     */
    public fun matrixFor(ground: Argb, accent: Argb, dark: Boolean): FloatArray {
        val tint = tintOn(ground, chromaFor(dark))
        val hue = hueFor(accent)
        return if (hue == null) tint else compose(hue, tint)
    }

    /**
     * The picture's darkest and brightest spots as shown on [ground] ([frosted]: under a Glass pane's blur), each moved
     * 3 % past itself towards black or white, so a pane over the picture never thickens too little
     * (`kit_live_aurora_image.dart:174-198`).
     */
    public fun spotsOf(ground: Argb, accent: Argb, dark: Boolean, frosted: Boolean): Pair<Argb, Argb> {
        val shown = matrixFor(ground, accent, dark)
        val (darkest, brightest) =
            when {
                dark && frosted -> darkPlateFrostedDarkest to darkPlateFrostedBrightest
                dark -> darkPlateDarkest to darkPlateBrightest
                frosted -> lightPlateFrostedDarkest to lightPlateFrostedBrightest
                else -> lightPlateDarkest to lightPlateBrightest
            }
        return toward(apply(shown, darkest), 0f) to toward(apply(shown, brightest), 1f)
    }

    /**
     * The spots a clear Glass pane measures its words over (owner 2026-11-09, "crisper": the panes blur less, so the
     * picture behind them keeps more of its extremes): the frosted spots ([spotsOf]) moved [LIGHT_FROST_SHARE] of the way
     * to the picture's raw darkest and brightest. Conservative: Flutter measured the frost under a 40 px Gaussian, and
     * Glass's clear panes still blur 10-12 dp.
     */
    public fun clearSpotsOf(ground: Argb, accent: Argb, dark: Boolean): List<Argb> {
        val (fd, fb) = spotsOf(ground, accent, dark, frosted = true)
        val (rd, rb) = spotsOf(ground, accent, dark, frosted = false)
        return listOf(
            io.github.eslamasabry.oc.ui.design.color.lerp(fd, rd, LIGHT_FROST_SHARE),
            io.github.eslamasabry.oc.ui.design.color.lerp(fb, rb, LIGHT_FROST_SHARE),
        )
    }

    /** How far [clearSpotsOf] moves the frosted spots towards the raw ones. */
    public const val LIGHT_FROST_SHARE: Float = .25f

$a
    public data class Drift(val zoom: Float, val panX: Float, val panY: Float)

    public fun driftAt(t: Double): Drift =
        Drift(
            zoom = (1.08 + .02 * sin(t * .021)).toFloat(), // FG: lib/ui/kit/live_background/styles/kit_live_aurora_image.dart:292
            panX = (.85 * sin(t * .013 + .7)).toFloat(), // FG: lib/ui/kit/live_background/styles/kit_live_aurora_image.dart:295
            panY = (.85 * sin(t * .009 + 2.1)).toFloat(), // FG: lib/ui/kit/live_background/styles/kit_live_aurora_image.dart:296
        )

    /**
     * The part of an [imageWidth] x [imageHeight] plate that covers a [width] x [height] page at [drift]
     * (`kit_live_aurora_image.dart:289-301`): left, top, width, height in plate pixels.
     */
    public fun sourceRect(imageWidth: Float, imageHeight: Float, width: Float, height: Float, drift: Drift): FloatArray {
        val cover = max(width / imageWidth, height / imageHeight)
        val sw = width / (cover * drift.zoom)
        val sh = height / (cover * drift.zoom)
        val slackX = (imageWidth - sw) / 2f
        val slackY = (imageHeight - sh) / 2f
        val cx = imageWidth / 2f + slackX * drift.panX
        val cy = imageHeight / 2f + slackY * drift.panY
        return floatArrayOf(cx - sw / 2f, cy - sh / 2f, sw, sh)
    }

    /** Keeps [s] of a colour's chroma and sets it on [ground]: out = ground + s * (c - luma(c)) (`_tintOn`, :149-157). */
    private fun tintOn(ground: Argb, s: Float): FloatArray =
        floatArrayOf(
            s * (1 - LR), -s * LG, -s * LB, 0f, ground.red.toFloat(),
            -s * LR, s * (1 - LG), -s * LB, 0f, ground.green.toFloat(),
            -s * LR, -s * LG, s * (1 - LB), 0f, ground.blue.toFloat(),
            0f, 0f, 0f, 1f, 0f,
        )

    /** [first] then [then], as one 4x5 matrix (`_compose`, :160-172). */
    private fun compose(first: FloatArray, then: FloatArray): FloatArray {
        val out = FloatArray(20)
        for (row in 0 until 4) {
            for (col in 0 until 5) {
                var v = if (col == 4) then[row * 5 + 4] else 0f
                for (k in 0 until 4) v += then[row * 5 + k] * first[k * 5 + col]
                out[row * 5 + col] = v
            }
        }
        return out
    }

    /** The hue rotation (or the greying) that turns the plate to [accent]; null near the plate's hue (`_hueFor`, :202-210). */
    private fun hueFor(accent: Argb): FloatArray? {
        val (hue, saturation) = hsl(accent)
        if (saturation < .15) return saturationMatrix(.12f) // FG: lib/ui/kit/live_background/styles/kit_live_aurora_image.dart:204
        var turn = hue - PLATE_HUE
        if (turn > 180) turn -= 360
        if (turn < -180) turn += 360
        if (abs(turn) <= KEEP_WITHIN) return null
        return hueRotation(turn * PI / 180)
    }

    /** A luminance-keeping hue rotation by [a] radians (feColorMatrix hueRotate; `_hueRotation`, :226-250). */
    private fun hueRotation(a: Double): FloatArray {
        val c = cos(a)
        val s = sin(a)
        return doubleArrayOf(
            .213 + c * .787 - s * .213, .715 - c * .715 - s * .715, .072 - c * .072 + s * .928, 0.0, 0.0,
            .213 - c * .213 + s * .143, .715 + c * .285 + s * .140, .072 - c * .072 - s * .283, 0.0, 0.0,
            .213 - c * .213 - s * .787, .715 - c * .715 + s * .715, .072 + c * .928 + s * .072, 0.0, 0.0,
            0.0, 0.0, 0.0, 1.0, 0.0,
        ).map { it.toFloat() }.toFloatArray()
    }

    /** [s] of the colour kept, 0 grey .. 1 as painted (`_saturation`, :253-277). */
    private fun saturationMatrix(s: Float): FloatArray =
        floatArrayOf(
            LR + (1 - LR) * s, LG - LG * s, LB - LB * s, 0f, 0f,
            LR - LR * s, LG + (1 - LG) * s, LB - LB * s, 0f, 0f,
            LR - LR * s, LG - LG * s, LB + (1 - LB) * s, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )

    /** [m] applied to [c] (`_apply`, :213-222). */
    private fun apply(m: FloatArray, c: Rgb): FloatArray {
        val r = c.r / 255f
        val g = c.g / 255f
        val b = c.b / 255f
        fun ch(row: Int) = (m[row * 5] * r + m[row * 5 + 1] * g + m[row * 5 + 2] * b + m[row * 5 + 3] + m[row * 5 + 4] / 255f).coerceIn(0f, 1f)
        return floatArrayOf(ch(0), ch(1), ch(2))
    }

    /** [c] moved 3 % of the way to the channel value [end] (`_toward`, :195-198). */
    private fun toward(c: FloatArray, end: Float): Argb {
        fun m(v: Float) = ((v + (end - v) * .03f) * 255f).roundToInt().coerceIn(0, 255) // FG: lib/ui/kit/live_background/styles/kit_live_aurora_image.dart:196
        return Argb((0xFF shl 24) or (m(c[0]) shl 16) or (m(c[1]) shl 8) or m(c[2]))
    }

    /** Flutter `HSLColor.fromColor`: hue in degrees and HSL saturation. */
    private fun hsl(c: Argb): Pair<Double, Double> {
        val r = c.red / 255.0
        val g = c.green / 255.0
        val b = c.blue / 255.0
        val maxC = max(r, max(g, b))
        val minC = min(r, min(g, b))
        val delta = maxC - minC
        val hue =
            when {
                delta == 0.0 -> 0.0
                maxC == r -> 60.0 * (((g - b) / delta) % 6)
                maxC == g -> 60.0 * (((b - r) / delta) + 2)
                else -> 60.0 * (((r - g) / delta) + 4)
            }.let { if (it.isNaN()) 0.0 else if (it < 0) it + 360.0 else it }
        val lightness = (maxC + minC) / 2.0
        val saturation = if (lightness == 1.0 || lightness == 0.0) 0.0 else (delta / (1.0 - abs(2.0 * lightness - 1.0))).coerceIn(0.0, 1.0)
        return hue to saturation
    }
}
