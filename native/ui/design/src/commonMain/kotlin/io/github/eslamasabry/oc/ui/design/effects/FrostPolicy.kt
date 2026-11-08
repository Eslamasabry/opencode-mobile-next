package io.github.eslamasabry.oc.ui.design.effects

/**
 * Exactly two places frost under the strength choice (owner rule R3, D10): the composer and the chat header. The Glass
 * style (owner, 2026-11-05) blurs its own panes through the same platform effect (`KitSurfaceGlass`, order 34).
 */
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

/**
 * The conditions frost depends on. `styleAllows` is false for every Style except Glass (N-57). [lensSupported] is the
 * platform's runtime shaders (Android 13+; desktop not yet), which Glass's liquid edges need ([FrostPolicy.edgesHeldBy]).
 */
public data class FrostConditions(
    val saver: Boolean = false,
    val reducedMotion: Boolean = false,
    val highContrast: Boolean = false,
    val styleAllows: Boolean = true,
    val lensSupported: Boolean = false,
)

/**
 * Glass's floating edges (Fine-tune under Glass, "Glass edges", `oc.glassEdges`): liquid glass that bends the content at
 * its rim (the default), or today's frost. Stored by [storageName]; a missing or unknown value reads [Liquid].
 */
public enum class GlassEdges(public val storageName: String) {
    Liquid("liquid"),
    Frosted("frosted"),
    ;

    public companion object {
        public fun fromStorage(name: String?): GlassEdges = entries.firstOrNull { it.storageName == name } ?: Liquid
    }
}

/**
 * Why Glass's floating parts are frosted rather than liquid (Settings says why). The first four are frost's own holds
 * ([FrostHold]); [Off] is frost strength Off, [Frosted] the person's "Glass edges" choice, [Platform] no runtime shaders
 * ("Needs Android 13").
 */
public enum class EdgeHold { Saver, Motion, Contrast, Style, Off, Frosted, Platform }

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

    /**
     * The first reason Glass's floating parts are not liquid glass, or null when they are: frost's own holds first (in
     * [heldBy]'s order), then frost strength Off, then the person's Frosted choice, then the platform.
     */
    public fun edgesHeldBy(c: FrostConditions, strength: FrostStrength, edges: GlassEdges): EdgeHold? =
        when (heldBy(c)) {
            FrostHold.Saver -> EdgeHold.Saver
            FrostHold.Motion -> EdgeHold.Motion
            FrostHold.Contrast -> EdgeHold.Contrast
            FrostHold.Style -> EdgeHold.Style
            null ->
                when {
                    strength == FrostStrength.Off -> EdgeHold.Off
                    edges == GlassEdges.Frosted -> EdgeHold.Frosted
                    !c.lensSupported -> EdgeHold.Platform
                    else -> null
                }
        }

    /** Whether Glass's floating parts are liquid glass under [c]; every hold keeps today's frost, solid or own face. */
    public fun liquidEdges(c: FrostConditions, strength: FrostStrength, edges: GlassEdges): Boolean =
        edgesHeldBy(c, strength, edges) == null

    /**
     * Whether Glass's in-flow panes (cards, row groups, chips, fields) are clear frosted glass (owner 2026-11-08: much
     * lighter, the background's colour showing through) rather than today's thick panes. They need only the blur, not
     * the lens, so the platform's missing lens does not hold them; every other hold keeps today's panes (Saver, reduced
     * motion, high contrast, another style, frost Off and "Glass edges: Frosted"). The kit also needs the pane to blur a
     * picture there.
     */
    public fun clearPanes(c: FrostConditions, strength: FrostStrength, edges: GlassEdges): Boolean =
        edgesHeldBy(c, strength, edges).let { it == null || it == EdgeHold.Platform }

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
