package io.github.eslamasabry.oc.ui.design.effects

import io.github.eslamasabry.oc.ui.design.tokens.Density

/**
 * How the app's surfaces are made (Settings › Appearance › Style; Dart `KitSurfaceStyle`, `lib/state/effects.dart`).
 * Solid is the default and what Saver always uses. Stored by [storageName] (`oc.effectsSurfaceStyle`): a name no longer
 * here (the retired Glass and Crystal, the dropped Clay) reads as Solid.
 */
public enum class KitSurfaceStyle(public val storageName: String) {
    /** Matte panels with a hairline edge. */
    Solid("solid"),

    /** Soft UI: the ground's own colour, shaped by paired soft shadows. */
    Soft("soft"),

    /** Neo-brutalism: bold ink edges and hard offset shadows with no blur. */
    Brutal("brutal"),

    /** Flat print, Swiss style: ink rules on paper, square, no shadows. */
    Paper("paper"),

    /** A railway signal box with a console's instruments: cream enamel and ink, raised keys for actions. */
    Signal("signal"),
    ;

    public companion object {
        public fun fromStorage(name: String?): KitSurfaceStyle = entries.firstOrNull { it.storageName == name } ?: Solid
    }
}

/** Fine-tune: how round the corners are. */
public enum class KitCorners { Round, Soft, Sharp }

/** Fine-tune: whether content surfaces stand out with the style's depth. */
public enum class KitDepth { Flat, Raised }

/** Fine-tune: whether surfaces draw the style's own edge (a hairline, an ink border, a rule). Shown as On and Off. */
public enum class KitEdges { Hairline, None }

/** The four Fine-tune knobs, all decided: a style's defaults, or the person's choices laid over them. */
public data class KitKnobs(
    val corners: KitCorners = KitCorners.Round,
    val depth: KitDepth = KitDepth.Flat,
    val density: Density = Density.Comfortable,
    val edges: KitEdges = KitEdges.Hairline,
)

/** The person's Fine-tune choices: null means "the style's default". A new style starts from [None]. */
public data class KitFineTune(
    val corners: KitCorners? = null,
    val depth: KitDepth? = null,
    val density: Density? = null,
    val edges: KitEdges? = null,
) {
    val isNone: Boolean get() = corners == null && depth == null && density == null && edges == null

    /** These choices over a style's [defaults]. */
    public fun over(defaults: KitKnobs): KitKnobs =
        KitKnobs(
            corners = corners ?: defaults.corners,
            depth = depth ?: defaults.depth,
            density = density ?: defaults.density,
            edges = edges ?: defaults.edges,
        )

    public companion object {
        public val None: KitFineTune = KitFineTune()
    }
}
