package io.github.eslamasabry.oc.ui.kit.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance

/** The colours of the theme switch's motion (theme-switch-premium §4), derived once per theme. */
@Immutable
public data class ThemeSwitchColors(
    /** The rim light's head and glow: the accent's hue pulled toward the night (dark) or day (light) anchor. */
    val rim: Color,
    /** The chosen segment's icon: the accent, its tone moved only as far as 3:1 against the chosen step needs. */
    val selectedInk: Color,
)

/**
 * The rim light's colour rule (theme-switch-premium §4). In HCT the accent's hue is pulled [PULL] of the way toward
 * a fixed anchor, cool at night and warm by day; chroma stays in [MIN_CHROMA] … [MAX_CHROMA] so a grey accent still
 * glows; the tone is clamped into a band that reads on the ground, then moved further until the rim has [MIN_CONTRAST]
 * against both the track it runs along and the page ground.
 */
public object ThemeSwitchColorRules {
    /** A cool blue (HCT hue) the rim leans toward in dark themes. */
    public const val NIGHT_HUE: Double = 255.0

    /** A warm amber (HCT hue) the rim leans toward in light themes. */
    public const val DAY_HUE: Double = 65.0

    public const val PULL: Double = 0.4
    public const val MIN_CHROMA: Double = 36.0
    public const val MAX_CHROMA: Double = 72.0

    /** WCAG non-text contrast (1.4.11). */
    public const val MIN_CONTRAST: Double = 3.0

    private const val DARK_TONE_MIN = 70.0
    private const val DARK_TONE_MAX = 92.0
    private const val LIGHT_TONE_MIN = 30.0
    private const val LIGHT_TONE_MAX = 58.0
    private const val TONE_STEP = 2.0
    private const val MAX_TONE = 100.0
    private const val TURN = 360.0
    private const val HALF_TURN = 180.0

    /** WCAG's 0.05 flare term in the contrast ratio. */
    private const val FLARE = 0.05

    /** Below this luminance a ground is dark (the luminance whose contrast to black and white is equal). */
    private const val MID_LUMINANCE = 0.18

    /** [from] moved [t] of the way to [to] along the shorter way round the hue circle. */
    public fun pullHue(from: Double, to: Double, t: Double): Double {
        var d = (to - from) % TURN
        if (d > HALF_TURN) d -= TURN
        if (d < -HALF_TURN) d += TURN
        return ((from + d * t) % TURN + TURN) % TURN
    }

    /** WCAG contrast of two opaque colours. */
    public fun contrast(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (maxOf(la, lb) + FLARE) / (minOf(la, lb) + FLARE)
    }

    /**
     * The colours for a theme: [colors] gives the accent and ground; [track] and [step] are the control's track and
     * chosen step as drawn (translucent fills are laid on the ground first).
     */
    public fun of(colors: KitColors, track: Color, step: Color): ThemeSwitchColors {
        val ground = colors.ground.copy(alpha = 1f)
        val trackOpaque = track.compositeOver(ground)
        val stepOpaque = step.compositeOver(trackOpaque)
        return ThemeSwitchColors(
            rim = rim(colors.accent.compositeOver(ground), colors.dark, listOf(trackOpaque, ground)),
            selectedInk = readable(colors.accent.compositeOver(stepOpaque), colors.dark, listOf(stepOpaque)),
        )
    }

    /** The rim colour for [accent] in a [dark] or light theme, kept at 3:1 against every colour in [grounds]. */
    public fun rim(accent: Color, dark: Boolean, grounds: List<Color>): Color {
        val hct = KitHct.of(accent)
        val hue = pullHue(hct.hue, if (dark) NIGHT_HUE else DAY_HUE, PULL)
        val chroma = hct.chroma.coerceIn(MIN_CHROMA, MAX_CHROMA)
        val band = if (dark) DARK_TONE_MIN..DARK_TONE_MAX else LIGHT_TONE_MIN..LIGHT_TONE_MAX
        val tone = hct.tone.coerceIn(band)
        return toneUntilReadable(hue, chroma, tone, dark, grounds)
    }

    /** [ink] as is when it already has 3:1 against [grounds]; otherwise its tone moves away from them until it does. */
    public fun readable(ink: Color, dark: Boolean, grounds: List<Color>): Color {
        if (grounds.all { contrast(ink, it) >= MIN_CONTRAST }) return ink
        val hct = KitHct.of(ink)
        // Which way gives more room: away from the grounds' mean luminance.
        val lighter = if (grounds.isEmpty()) dark else grounds.map { it.luminance() }.average() < MID_LUMINANCE
        return toneUntilReadable(hct.hue, hct.chroma, hct.tone, lighter, grounds)
    }

    private fun toneUntilReadable(
        hue: Double,
        chroma: Double,
        start: Double,
        lighter: Boolean,
        grounds: List<Color>,
    ): Color {
        var tone = start
        var best = KitHct(hue, chroma, tone).toColor()
        while (tone in 0.0..MAX_TONE) {
            val c = KitHct(hue, chroma, tone).toColor()
            best = c
            if (grounds.all { contrast(c, it) >= MIN_CONTRAST }) return c
            tone += if (lighter) TONE_STEP else -TONE_STEP
        }
        return best
    }
}
