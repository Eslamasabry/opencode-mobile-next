package io.github.eslamasabry.oc.ui.design.style

import io.github.eslamasabry.oc.ui.design.color.Roles
import io.github.eslamasabry.oc.ui.design.color.withAccent

/** Reading the style and the knobs in force (Dart `KitStyles`; plan 06 §4.3). */
public object StyleResolver {
    public fun spec(style: Style): StyleSpec =
        when (style) {
            Style.Solid -> SolidSpec
            Style.Soft -> SoftSpec
            Style.Brutal -> BrutalSpec
            Style.Paper -> PaperSpec
            Style.Signal -> SignalSpec
        }

    /** The style in force: Solid under Saver, and Solid under platform high contrast unless the style keeps itself. */
    public fun effectiveStyle(style: Style, highContrast: Boolean, saver: Boolean = false): Style =
        when {
            saver -> Style.Solid
            highContrast && !spec(style).tokens.keepsUnderHighContrast -> Style.Solid
            else -> style
        }

    /**
     * The Fine-tune knobs in force: the person's choices over the effective style's defaults; high contrast turns edges
     * on and depth off; a style shaped by its shadows keeps its edges when flat. Saver makes the knobs none.
     */
    public fun knobsFor(style: Style, fineTune: FineTuneChoice = FineTuneChoice.None, highContrast: Boolean = false, saver: Boolean = false): Knobs {
        val effective = effectiveStyle(style, highContrast, saver)
        val tokens = spec(effective).tokens
        var knobs = (if (saver) FineTuneChoice.None else fineTune).over(tokens.defaults)
        if (highContrast) knobs = knobs.copy(depth = Depth.Flat, edges = Edges.Hairline)
        if (tokens.dependsOnDepth && knobs.depth == Depth.Flat) knobs = knobs.copy(edges = Edges.Hairline)
        return knobs
    }

    /** [radiusDp] under Corners [corners]: Round keeps it, Soft takes about 60 %, Sharp takes [sharp]. Stadiums stay stadiums. */
    public fun corner(radiusDp: Float, corners: Corners, sharp: Float = 2f): Float {
        if (radiusDp <= 0f) return radiusDp
        return when (corners) {
            Corners.Round -> radiusDp
            Corners.Soft -> if (radiusDp >= 999f) radiusDp else kotlin.math.round(radiusDp * .6f)
            Corners.Sharp -> if (radiusDp < sharp) radiusDp else sharp
        }
    }

    /** The theme's roles under [style] (hook H6); the accent moves only as far as it must to read on the new ground. */
    public fun adjust(roles: Roles, style: Style): Roles {
        val spec = spec(style)
        val adjusted = spec.adjustRoles(roles)
        return if (adjusted == roles) roles else adjusted.withAccent(adjusted.accent)
    }

    /** Whether choosing [style] turns the live background off (Paper, Signal; Dart `KitStyles.choose`). */
    public fun offWhenChosen(style: Style): Boolean = spec(style).tokens.live.offWhenChosen
}
