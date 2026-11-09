package io.github.eslamasabry.oc.ui.design.effects

import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.FineTuneChoice
import io.github.eslamasabry.oc.ui.design.style.GlassLookId
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
 * How lively the Background draws (vibrance spike, 2026-11-09; proposed key `oc.effectsLiveVibrance`, default Calm).
 * Calm is today's look, bit for bit. Every number scales one thing the look already has, so the contrast rules keep
 * holding: [capScale] widens how far a pixel may stray from the ground (the shader's `liveOut` cap, the glass spots),
 * [chroma] how much colour the tints keep, [plateChroma] the same for the painted aurora, [blobs] how large the colour
 * fields are and how many hues they use (1 = today's accent and partner), [speed] how fast they drift.
 */
public enum class LiveVibrance(
    public val wire: String,
    public val chroma: Float,
    public val capLight: Float,
    public val capDark: Float,
    public val amount: Float,
    public val blobs: Float,
    public val speed: Float,
    public val plateChroma: Float,
    public val plateSecond: Float,
) {
    Calm("calm", 1f, 1f, 1f, 1f, 1f, 1f, 1f, 0f),
    Vivid("vivid", 1.8f, 1.3f, 1f, 1.7f, 1f, 1f, 4.2f, 0f),
    Playful("playful", 1.6f, 1.35f, 1f, 1.8f, 1.6f, 1.35f, 4.6f, .7f),
    ;

    /** How far past Calm's cap a pixel may stray from the ground: dark grounds keep Calm's cap (their text is already at the limit). */
    public fun capFor(dark: Boolean): Float = if (dark) capDark else capLight

    public companion object {
        public fun of(wire: String?): LiveVibrance = entries.firstOrNull { it.wire == wire } ?: Calm
    }
}

/**
 * Stored style identities (port-kit name). One enum serves both names: [Style] carries the
 * storage names and the resolver specs; this alias keeps the kit's `KitSurfaceStyle` callers.
 */
public typealias KitSurfaceStyle = Style

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
    /** `oc.glassLook` (the Glass look picker): Glossy by default (glass looks D4). */
    val glassLook: GlassLookId = GlassLookId.Glossy,
    val activityGlow: Boolean = true,
    val softGlow: Boolean = false,
    val celebrations: Boolean = true,
    val liveBackground: Boolean = false,
    val liveHeldByStyle: Boolean = false,
    val liveStyle: String = "aurora",
    /**
     * `oc.effectsLiveIntensity` as Flutter's `KitLiveIntensity.strength`: Subtle .7, Normal 1, Bold 1.6 (FG:
     * lib/state/effects.dart:45-47 at GLASS_REF). A style may cap it (`LivePolicy.cap`).
     */
    val liveStrength: Float = 1f,
    /** `oc.effectsLiveVibrance` (proposed): how lively the Background draws; Calm = today. */
    val liveVibrance: LiveVibrance = LiveVibrance.Calm,
) {
    public val saver: Boolean
        get() = mode == AppearanceMode.Saver

    /** Flutter KitEffects.withSurfaceStyle resets the person's Fine-tune choices. */
    public fun withSurfaceStyle(style: Style): KitAppearance = copy(style = style, fineTune = FineTuneChoice.None)

    /** Render-only Saver override; the stored appearance is immutable. */
    public val underSaver: KitAppearance
        get() = copy(mode = AppearanceMode.Saver, motion = MotionLevel.Off, celebrations = false,
            liveBackground = false, style = Style.Solid, fineTune = FineTuneChoice.None)

    /** A person's background switch releases a style hold only when its value changes. */
    public fun withLiveBackground(enabled: Boolean): KitAppearance = copy(
        liveBackground = enabled,
        liveHeldByStyle = if (enabled == liveBackground) liveHeldByStyle else false,
    )

    public companion object {
        public val Default: KitAppearance = KitAppearance()
    }
}
