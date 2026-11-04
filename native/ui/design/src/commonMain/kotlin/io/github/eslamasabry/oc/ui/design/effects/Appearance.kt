package io.github.eslamasabry.oc.ui.design.effects

import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
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

/** Stored style identities; only implemented styles are selectable. */
public enum class KitSurfaceStyle {
    Solid,
    Soft,
    Brutal,
    Paper,
    Signal,
}

/**
 * Everything `KitTheme` needs (plan 06 §5.2), from acknowledged preferences. Deferred live
 * backgrounds retain their stored choices outside the render contract.
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
    val style: KitSurfaceStyle = KitSurfaceStyle.Solid,
    val activityGlow: Boolean = true,
    val softGlow: Boolean = false,
) {
    public val saver: Boolean
        get() = mode == AppearanceMode.Saver

    public companion object {
        public val Default: KitAppearance = KitAppearance()
    }
}
