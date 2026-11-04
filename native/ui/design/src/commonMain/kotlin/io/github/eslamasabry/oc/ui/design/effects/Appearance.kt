package io.github.eslamasabry.oc.ui.design.effects

import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.FineTuneChoice
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.text.AppTextSize
import io.github.eslamasabry.oc.ui.design.tokens.Density

/**
 * `oc.appearance`. The default is Dark (plan 06 §4.2). Saver: true black, motion off, Style Solid.
 */
public enum class AppearanceMode {
    System,
    Light,
    Dark,
    Saver,
}

/**
 * Stored style identities (port-kit name). One enum serves both names: [Style] carries the
 * storage names and the resolver specs; this alias keeps the kit's `KitSurfaceStyle` callers.
 */
public typealias KitSurfaceStyle = Style

/**
 * The unit's effects-resolver names for the same identities: one enum each serves both callers
 * ([Style] stores the style, [io.github.eslamasabry.oc.ui.design.style.Corners] the corner knob).
 */
public typealias SurfaceStyle = Style

public typealias Corners = io.github.eslamasabry.oc.ui.design.style.Corners

public enum class GlowStyle { Classic, SoftRing }

/**
 * Everything `KitTheme` needs (plan 06 §5.2), from acknowledged preferences. Pack, Accent, Style, Fine-tune and the
 * activity glow live here; deferred live backgrounds retain their stored choices outside the render contract.
 */
public data class KitAppearance(
    val mode: AppearanceMode = AppearanceMode.Dark,
    /**
     * `oc.themePack`; Graphite by default. Material You falls back to Graphite without the
     * platform's tones.
     */
    val pack: PackId = PackId.Opencode,
    /** `oc.appearanceAccent` swatch id; null = Match theme. An unknown id reads Match theme. */
    val accent: String? = null,
    val textSize: AppTextSize = AppTextSize.Normal,
    val motion: MotionLevel = MotionLevel.Full,
    /** Always true at the switch: no stored key exists (plan 06 §4.9). */
    val haptics: Boolean = true,
    val density: Density = Density.Comfortable,
    val frost: FrostStrength = FrostStrength.Balanced,
    /** `oc.effectsSurfaceStyle`; Saver and platform high contrast resolve the style actually drawn (B-KIT-039). */
    val style: Style = Style.Solid,
    /** `oc.effectsCorners` / `oc.effectsDensity`: null = the style's own (B-KIT-017). */
    val fineTune: FineTuneChoice = FineTuneChoice.None,
    val activityGlow: Boolean = true,
    val softGlow: Boolean = false,
) {
    public val saver: Boolean
        get() = mode == AppearanceMode.Saver

    /** The corner fine-tune; null = the style's own (effects resolver name for [fineTune]). */
    public val corners: Corners?
        get() = fineTune.corners

    /** The glow look (effects resolver name for [softGlow]). */
    public val glowStyle: GlowStyle
        get() = if (softGlow) GlowStyle.SoftRing else GlowStyle.Classic

    public companion object {
        public val Default: KitAppearance = KitAppearance()
    }
}
