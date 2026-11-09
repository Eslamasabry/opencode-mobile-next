package io.github.eslamasabry.oc.ui.design.style

/**
 * Every tint, blur and gloss number of Glass's clear panes and liquid faces in one place (owner 2026-11-09: "why is
 * glass so frosty and not glossy?"; a design for person-pickable glass looks is starting, and its presets reuse this).
 * [GlassSpec], [io.github.eslamasabry.oc.ui.design.effects.LiquidEdge] and the kit read [Default]; nothing else holds
 * these numbers. A pane's tint itself is not here: it starts at [GlassSpec.clearOpacity] and is thickened only as far
 * as its words need over the look behind it ([GlassFinish], [io.github.eslamasabry.oc.ui.design.effects.LiveReach]).
 *
 * Default (glass-live 2026-11-09): clearer and glossier than the first clear panes. Less milk (a lighter light-theme
 * sheen and lit corner, a light 6 dp blur for every clear pane, so thin marks such as matrix rain stay visible), more gloss (a brighter specular and glint, a brighter liquid
 * rim, and a rim that catches the live picture's light).
 */
public data class GlassLook(
    /** Blur behind a clear content pane (a card, a row group), in dp. */
    val contentFrostDp: Float,
    /** Blur behind a clear chip, control, track or field, in dp. */
    val paneFrostDp: Float,
    /** How much the frost lifts the saturation of what it blurs (vibrancy; 1 keeps it). */
    val frostSaturation: Float,
    /** A clear pane's top sheen, white at this alpha: dark and light themes. */
    val sheenDark: Float,
    val sheenLight: Float,
    /** How much more a clear pane's lit top-start corner holds than its tint, and how far it leans to white. */
    val litLiftDark: Float,
    val litLiftLight: Float,
    /** The specular light on every glass face (the bright rim segment at the top start), white at this alpha. */
    val specularDark: Float,
    val specularLight: Float,
    /** The glint line inside the top edge, as a share of [specularDark] / [specularLight]. */
    val glintShare: Float,
    /** A liquid face's rim light (added light over a dark theme, laid over a light one) and its specular. */
    val liquidRimDark: Float,
    val liquidRimLight: Float,
    val liquidSpecularDark: Float,
    val liquidSpecularLight: Float,
    /** The rim catching the live picture's light: alpha per unit of luma above the ground, its most, its band in dp. */
    val backdropLightGain: Float,
    val backdropLightMax: Float,
    val backdropLightBandDp: Float,
) {
    public companion object {
        /** Glass as it ships: clear and glossy. */
        public val Default: GlassLook =
            GlassLook(
                contentFrostDp = 6f,
                paneFrostDp = 6f,
                frostSaturation = 1.7f,
                sheenDark = .12f,
                sheenLight = .24f,
                litLiftDark = .04f,
                litLiftLight = .06f,
                specularDark = .80f,
                specularLight = .96f,
                glintShare = .75f,
                liquidRimDark = .46f,
                liquidRimLight = .62f,
                liquidSpecularDark = .80f,
                liquidSpecularLight = .95f,
                backdropLightGain = 3.2f,
                backdropLightMax = .85f,
                backdropLightBandDp = 1.5f,
            )

        /** The first clear panes (glass-v3, 2026-11-09 morning), kept for comparison and as a quieter preset. */
        public val Frosted: GlassLook =
            GlassLook(
                contentFrostDp = 12f,
                paneFrostDp = 10f,
                frostSaturation = 1.7f,
                sheenDark = .10f,
                sheenLight = .34f,
                litLiftDark = .04f,
                litLiftLight = .12f,
                specularDark = .55f,
                specularLight = .90f,
                glintShare = .60f,
                liquidRimDark = .36f,
                liquidRimLight = .55f,
                liquidSpecularDark = .60f,
                liquidSpecularLight = .85f,
                backdropLightGain = 0f,
                backdropLightMax = 0f,
                backdropLightBandDp = 1.5f,
            )
    }
}
