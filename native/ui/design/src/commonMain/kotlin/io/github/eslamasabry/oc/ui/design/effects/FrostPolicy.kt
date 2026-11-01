package io.github.eslamasabry.oc.ui.design.effects

/** Exactly two places frost (owner rule R3, D10): the composer and the chat header. No other blur exists. */
public enum class FrostRole { Composer, ChatHeader }

/**
 * The frost strength (`oc.glass`, Dart `KitGlassChoice` / `KitFrostStrength`, `lib/ui/kit/kit_frost.dart`). Stored by
 * [storageName]; a missing or unknown value reads [Balanced].
 */
public enum class FrostStrength(
    public val storageName: String,
    /** Blur sigma in dp (a CSS blur radius is about twice this). */
    public val sigmaDp: Float,
    public val saturation: Float,
    public val tint: Float,
    public val headerTint: Float,
) {
    Off("off", 0f, 1f, 1f, 1f),
    Weak("weak", 6f, 1.2f, 0.62f, 0.72f),
    Balanced("balanced", 10f, 1.4f, 0.70f, 0.80f),
    Strong("strong", 16f, 1.6f, 0.76f, 0.85f),
    ;

    public companion object {
        public fun fromStorage(name: String?): FrostStrength = entries.firstOrNull { it.storageName == name } ?: Balanced
    }
}

/** What holds frost solid regardless of the choice (Settings says why). Dart `KitFrostHold`. */
public enum class FrostHold { Saver, Motion, Contrast, Style }

/** The conditions frost depends on. `styleAllows` is false for every Style except Solid. */
public data class FrostConditions(
    val saver: Boolean = false,
    val reducedMotion: Boolean = false,
    val highContrast: Boolean = false,
    val styleAllows: Boolean = true,
)

/** A blur to draw: sigma in dp, saturation multiplier, and the tint alpha laid over it. */
public data class FrostSpec(val sigmaDp: Float, val saturation: Float, val tintAlpha: Float)

public object FrostPolicy {
    /** The composer's top hairline: white at this alpha. */
    public const val HIGHLIGHT_ALPHA: Float = 0.20f
    /** The composer's edge all round, ink at this alpha. */
    public const val EDGE_ALPHA: Float = 0.10f
    /** The header's bottom line, ink at this alpha. */
    public const val HEADER_EDGE_ALPHA: Float = 0.12f
    /** The seeded noise over the tint. */
    public const val NOISE_OPACITY: Float = 0.03f
    /** How much less tint dark mode lays over the blur than light (coordinator review 2026-10-30). */
    public const val DARK_TINT_RELIEF: Float = 0.10f

    /** Dart `KitFrost.heldBy`: the first hold that applies, in Dart's order; null when the choice applies. */
    public fun heldBy(c: FrostConditions): FrostHold? =
        when {
            c.saver -> FrostHold.Saver
            c.reducedMotion -> FrostHold.Motion
            c.highContrast -> FrostHold.Contrast
            !c.styleAllows -> FrostHold.Style
            else -> null
        }

    /** The spec to draw at full strength, or null for a solid surface (Dart `strengthFor` + `_backdrop` at t = 1). */
    public fun spec(role: FrostRole, strength: FrostStrength, dark: Boolean, c: FrostConditions): FrostSpec? {
        if (heldBy(c) != null || strength == FrostStrength.Off) return null
        val relief = if (dark) DARK_TINT_RELIEF else 0f
        val tint = if (role == FrostRole.ChatHeader) strength.headerTint else strength.tint
        return FrostSpec(strength.sigmaDp, strength.saturation, tint - relief)
    }

    /**
     * The radius in pixels to pass to a blur that takes a radius (Android `RenderEffect.createBlurEffect`, Skia
     * `convertRadiusToSigma`: sigma = 0.57735 × radius + 0.5) so it draws Flutter's sigma ([sigmaDp] × [density]).
     */
    public fun blurRadiusPx(sigmaDp: Float, density: Float): Float =
        maxOf(0f, (sigmaDp * density - 0.5f) / BLUR_SIGMA_SCALE)

    private const val BLUR_SIGMA_SCALE: Float = 0.57735f
}
