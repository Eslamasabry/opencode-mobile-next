package io.github.eslamasabry.oc.ui.design.effects

import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.text.AppTextSize
import io.github.eslamasabry.oc.ui.design.tokens.Density

/** `oc.appearance`. The default is Dark (plan 06 §4.2). Saver: true black, motion off, Style Solid. */
public enum class AppearanceMode { System, Light, Dark, Saver }

/**
 * Everything `KitTheme` needs (plan 06 §5.2). Slice 0 carries the fields the P7 seams read; pack, accent, glow, Style,
 * corners and live background join with the 31 packs and the Style resolver (WP K1/K4), as defaulted fields.
 */
public data class KitAppearance(
    val mode: AppearanceMode = AppearanceMode.Dark,
    val textSize: AppTextSize = AppTextSize.Normal,
    val motion: MotionLevel = MotionLevel.Full,
    /** Always true at the switch: no stored key exists (plan 06 §4.9). */
    val haptics: Boolean = true,
    val density: Density = Density.Comfortable,
    val frost: FrostStrength = FrostStrength.Balanced,
) {
    public val saver: Boolean get() = mode == AppearanceMode.Saver

    public companion object {
        public val Default: KitAppearance = KitAppearance()
    }
}
