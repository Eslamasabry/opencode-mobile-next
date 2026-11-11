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
    /**
     * A clear pane's 1 dp white rim (owner 2026-11-09, "crisper": a sharp, thin white rim), white at these alphas along
     * the rim's fade from the top-start corner to the bottom-end one: at its top start (the specular sits on it), in the
     * rest of the rim between the two lit ends, and at its bottom end. One light, two lit arcs, as on the lens panes
     * (owner 2026-11-11 on the home, "Is this supposed to be glass?"; docs/designs/glass-in-the-kit.md 1a). A null rest
     * draws the rim straight from start to end, as before 2026-11-11. Chosen rows keep their accent rim.
     */
    val paneRimStartDark: Float,
    val paneRimRestDark: Float?,
    val paneRimEndDark: Float,
    val paneRimStartLight: Float,
    val paneRimRestLight: Float?,
    val paneRimEndLight: Float,
) {
    /** The clear pane rim's alphas for this theme: start, rest (null: straight from start to end), end. */
    public fun paneRim(dark: Boolean): Triple<Float, Float?, Float> =
        if (dark) Triple(paneRimStartDark, paneRimRestDark, paneRimEndDark) else Triple(paneRimStartLight, paneRimRestLight, paneRimEndLight)

    /** This look with [other]'s clear pane rim. */
    public fun withPaneRimOf(other: GlassLook): GlassLook =
        copy(
            paneRimStartDark = other.paneRimStartDark,
            paneRimRestDark = other.paneRimRestDark,
            paneRimEndDark = other.paneRimEndDark,
            paneRimStartLight = other.paneRimStartLight,
            paneRimRestLight = other.paneRimRestLight,
            paneRimEndLight = other.paneRimEndLight,
        )

    public companion object {
        /** Glass as it ships: clear and glossy, a bright Apple-like rim and specular (owner 2026-11-09, "more bright like apple"). */
        public val Default: GlassLook =
            GlassLook(
                contentFrostDp = 6f,
                paneFrostDp = 6f,
                frostSaturation = 1.7f,
                sheenDark = .16f,
                sheenLight = .30f,
                litLiftDark = .04f,
                litLiftLight = .06f,
                specularDark = .80f,
                specularLight = .96f,
                glintShare = .75f,
                liquidRimDark = .72f,
                liquidRimLight = .90f,
                liquidSpecularDark = .95f,
                liquidSpecularLight = 1f,
                backdropLightGain = 3.2f,
                backdropLightMax = .85f,
                backdropLightBandDp = 1.5f,
                // Dark (glass-in-the-kit 1a, 2026-11-11): a pane that stands on its own face on a dark page. The top start
                // is unchanged (.24; the specular sits on it), the rest of the rim a little quieter than the old straight
                // fade's middle (.10 against .155), and the bottom end lit (.30, was .07): the second lit arc.
                paneRimStartDark = .24f,
                paneRimRestDark = .10f,
                paneRimEndDark = .30f,
                // Light: unchanged in step 1 (the light theme's edge waits for its own mock, step 2).
                paneRimStartLight = .60f,
                paneRimRestLight = null,
                paneRimEndLight = .16f,
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
                // The rim as it was before 2026-11-11 (one lit arc, straight to a faint bottom end): Frosted keeps it.
                paneRimStartDark = .24f,
                paneRimRestDark = null,
                paneRimEndDark = .07f,
                paneRimStartLight = .60f,
                paneRimRestLight = null,
                paneRimEndLight = .16f,
            )
    }
}
