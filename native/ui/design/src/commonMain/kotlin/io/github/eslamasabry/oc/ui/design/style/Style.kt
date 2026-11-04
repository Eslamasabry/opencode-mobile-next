package io.github.eslamasabry.oc.ui.design.style

import io.github.eslamasabry.oc.ui.design.tokens.Density

/** The five visual styles (Dart `KitSurfaceStyle`; B-KIT-037). Glass and Crystal are retired and Clay was dropped. */
public enum class Style(public val storageName: String) { Solid("solid"), Soft("soft"), Brutal("brutal"), Paper("paper"), Signal("signal") }

public enum class Corners { Round, Soft, Sharp }

public enum class Depth { Flat, Raised }

public enum class Edges { Hairline, None }

/** The Fine-tune knobs in force (Dart `KitKnobs`); each style starts at its own [StyleTokens.defaults]. */
public data class Knobs(
    val corners: Corners = Corners.Round,
    val depth: Depth = Depth.Flat,
    val density: Density = Density.Comfortable,
    val edges: Edges = Edges.Hairline,
)

/** The person's Fine-tune choices (null = the style's own). Only Corners and Spacing exist (owner 2026-10-22). */
public data class FineTuneChoice(val corners: Corners? = null, val density: Density? = null) {
    public val isNone: Boolean get() = corners == null && density == null

    /** The choices laid over a style's [defaults]; a choice equal to the style's own does not differ. */
    public fun over(defaults: Knobs): Knobs = defaults.copy(corners = corners ?: defaults.corners, density = density ?: defaults.density)

    public companion object {
        public val None: FineTuneChoice = FineTuneChoice()
    }
}

/** How the words "Needs you" are set on a request card. */
public enum class NeedsYouTag { Caption, Pill }

/** The needs-you look (contract F2). Every null follows the glow's halo. */
public data class NeedsYouLook(
    val tag: NeedsYouTag = NeedsYouTag.Caption,
    val tint: Boolean? = null,
    val ring: Boolean? = null,
    val startEdge: Boolean? = null,
) {
    public data class Decision(val tint: Boolean, val line: Boolean, val ring: Boolean, val startEdge: Boolean, val pill: Boolean)

    public fun decide(halo: Boolean, question: Boolean = false): Decision {
        val line = ring ?: halo
        val ringed = !question && line
        return Decision(
            tint = !question && (tint ?: halo),
            line = line,
            ring = ringed,
            startEdge = startEdge ?: !ringed,
            pill = tag == NeedsYouTag.Pill,
        )
    }
}

/** How the live background shows under a style (contract §3.11). */
public data class LivePolicy(val maxStrength: Float? = null, val offWhenChosen: Boolean = false) {
    public fun cap(strength: Float): Float = maxStrength?.let { if (strength <= it) strength else it } ?: strength
}

/** How the message box's running glow is drawn (contract §3.10). */
public data class StyleGlow(val halo: Boolean = true, val lineWidthDp: Float? = null)

/** A style's type tweaks over the type scale (contract §3.6); null keeps the role's own value. */
public data class StyleType(
    val displayWeight: Int? = null,
    val displayTracking: Float? = null,
    val largeTitleSize: Int? = null,
    val largeTitleWeight: Int? = null,
    val titleSize: Int? = null,
    val bodyWeight: Int? = null,
    val monoLabels: Boolean = false,
    val engravedLabels: Boolean = false,
    val firmWeights: Boolean = false,
) {
    public val isNone: Boolean
        get() = displayWeight == null && displayTracking == null && largeTitleSize == null && largeTitleWeight == null &&
            titleSize == null && bodyWeight == null && !monoLabels && !engravedLabels && !firmWeights
}

/** What a style fixes for every part: its Fine-tune defaults and the tokens the resolver reads (Dart `KitStyleTokens`). */
public data class StyleTokens(
    val defaults: Knobs = Knobs(),
    val sharpRadius: Float = 2f,
    val keepsUnderHighContrast: Boolean = false,
    val dependsOnDepth: Boolean = false,
    val type: StyleType = StyleType(),
    val live: LivePolicy = LivePolicy(),
    val glow: StyleGlow = StyleGlow(),
    val flattensNested: Boolean = false,
    val keepsNestedSurfaces: Boolean = false,
    val dividerWidthDp: Float? = null,
    val needsYou: NeedsYouLook = NeedsYouLook(),
    val linkUnderline: Float? = null,
    val fieldHalo: Boolean? = null,
    val flatLists: Boolean = false,
    val track: Boolean = false,
) {
    public val fieldHaloShown: Boolean get() = fieldHalo ?: glow.halo
}
