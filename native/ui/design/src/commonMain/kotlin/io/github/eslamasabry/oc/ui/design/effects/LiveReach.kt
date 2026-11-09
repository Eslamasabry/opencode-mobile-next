package io.github.eslamasabry.oc.ui.design.effects

/**
 * How far each shader look really moves the page's ground once a Glass pane has blurred it, as a share of `liveOut`'s
 * cap ([LiveSpots.CAP_DARK], [LiveSpots.CAP_LIGHT]) — measured, not guessed (glass-live 2026-11-09; owner: "why is glass
 * not interacting with the background?"). Before this, every pane over every look was thickened for the whole cap, the
 * worst any look could draw unblurred, so a pane over a look of thin glyphs (matrix rain) was as thick as one over a
 * wash of colour (aurora), and the motion behind it was mostly hidden.
 *
 * How the numbers were made (`LiveReachMeasureTest` in `:ui:kit` desktop tests renders them again and fails when a
 * look reaches further than its row): every look rendered through the host's own shader code at Bold (1.6, the
 * strongest a person can pick), on the light and dark themes of all 31 packs, at eight moments over two minutes; then
 * blurred as the lightest liquid lens blurs it ([LiquidEdge]'s pill recipe, 2 dp: `lens`, which the clear panes' lighter
 * frost uses too, see [FROST_SIGMA_DP]). The farthest blurred pixel from the ground,
 * over all of them, as a share of the cap, with 15 % room on top, rounded up to a twentieth (a look measured within
 * 8-bit dither of the cap, up to 1.05, is held at the cap itself).
 *
 * What the measure found besides: the dot, line and instrument looks do not end in the prelude's distance cap (the tint
 * set mixes the ground towards its tint by a share, the layer set caps luma), so under the lens's light blur on a light
 * theme several reach past it (constellation 1.47 of it, radar 1.39, oscilloscope 1.34). Their rows say so, and a face
 * that does not know its picture now measures the farthest of all of them ([most]) instead of the cap.
 *
 * A pane is then thickened only as far as its words need over that look ([GlassFinish.thickened] with
 * [LiveSpots.forLook]); text1/2/3 keep 4.5:1 (or what Solid reaches) over every pixel the look can put behind it.
 */
public object LiveReach {
    /** One look's reach: dark and light themes, under the clear frost and under the liquid lens. */
    public data class Row(val frostDark: Float, val frostLight: Float, val lensDark: Float, val lensLight: Float)

    /** The look [id]'s reach on a [dark] or light theme, under the lens or the frost; the farthest of any look when not measured. */
    public fun of(id: String?, dark: Boolean, lens: Boolean): Float {
        val row = rows[id] ?: return most(dark, lens)
        return row.at(dark, lens)
    }

    /** The farthest any look reaches on a [dark] or light theme, under the lens or the frost: the guess for an unknown picture. */
    public fun most(dark: Boolean, lens: Boolean): Float = rows.values.maxOf { it.at(dark, lens) }

    private fun Row.at(dark: Boolean, lens: Boolean): Float =
        when {
            lens && dark -> lensDark
            lens -> lensLight
            dark -> frostDark
            else -> frostLight
        }

    /** The measured rows, by stored look id. */
    public val rows: Map<String, Row> =
        mapOf(
            "aurora" to Row(1.0f, 1.0f, 1.0f, 1.0f), // measured frost@10 dp 1.014 1.025, lens 1.025 1.042
            "mesh" to Row(1.0f, 1.0f, 1.0f, 1.0f), // measured frost@10 dp 1.013 1.024, lens 1.025 1.040
            "dotRipple" to Row(1.0f, 1.45f, 1.0f, 1.45f), // measured frost@10 dp 0.109 0.153, lens 0.895 1.251
            "topo" to Row(0.85f, 1.0f, 0.85f, 1.0f), // measured frost@10 dp 0.358 0.511, lens 0.724 1.017
            "flowThreads" to Row(0.9f, 1.3f, 0.9f, 1.3f), // measured frost@10 dp 0.421 0.608, lens 0.750 1.107
            "glowSpill" to Row(1.0f, 1.0f, 1.0f, 1.0f), // measured frost@10 dp 1.016 1.018, lens 1.021 1.030
            "lightRays" to Row(0.9f, 1.25f, 0.9f, 1.25f), // measured frost@10 dp 0.354 0.494, lens 0.760 1.056
            "constellation" to Row(1.0f, 1.7f, 1.0f, 1.7f), // measured frost@10 dp 0.106 0.159, lens 1.021 1.474
            "halftone" to Row(0.7f, 1.0f, 0.7f, 1.0f), // measured frost@10 dp 0.344 0.485, lens 0.593 0.846
            "liquidBlobs" to Row(1.0f, 1.0f, 1.0f, 1.0f), // measured frost@10 dp 1.013 1.018, lens 1.025 1.038
            "matrixRain" to Row(0.55f, 0.75f, 0.55f, 0.75f), // measured frost@10 dp 0.092 0.121, lens 0.457 0.635
            "blueprint" to Row(1.0f, 1.35f, 1.0f, 1.35f), // measured frost@10 dp 0.442 0.536, lens 0.850 1.162
            "circuit" to Row(0.8f, 1.0f, 0.8f, 1.0f), // measured frost@10 dp 0.123 0.159, lens 0.656 0.865
            "oscilloscope" to Row(1.0f, 1.55f, 1.0f, 1.55f), // measured frost@10 dp 0.588 0.739, lens 0.969 1.340
            "radar" to Row(1.0f, 1.6f, 1.0f, 1.6f), // measured frost@10 dp 0.471 0.567, lens 1.008 1.387
            "honeycomb" to Row(0.95f, 1.35f, 0.95f, 1.35f), // measured frost@10 dp 0.559 0.760, lens 0.825 1.142
            "brushedMetal" to Row(1.0f, 1.4f, 1.0f, 1.4f), // measured frost@10 dp 0.824 1.146, lens 0.854 1.195
            "silk" to Row(1.0f, 1.0f, 1.0f, 1.0f), // measured frost@10 dp 1.020 1.023, lens 1.025 1.041
            "caustics" to Row(1.0f, 1.0f, 1.0f, 1.0f), // measured frost@10 dp 0.888 0.958, lens 1.021 1.030
            "bokeh" to Row(0.9f, 1.25f, 0.9f, 1.25f), // measured frost@10 dp 0.440 0.631, lens 0.754 1.083
        )

    /**
     * The lightest blur a clear in-flow pane may use, in dp ([GlassLook.contentFrostDp], [GlassLook.paneFrostDp]). The
     * `frost` columns hold the `lens` columns' numbers, measured under the lighter lens blur: a Gaussian blur only ever
     * pulls a pixel towards its neighbours' mean, so a look reaches no further under 6 dp than under 1.2 dp, and the
     * rows hold for any frost from here up. (Measured at 10 dp the frost reaches were smaller still: matrix rain .09,
     * aurora 1.01 of the cap; they are kept in each row's comment.)
     */
    public const val FROST_SIGMA_DP: Float = 6f

    /** The blur the `lens` columns were measured under, in dp: the lightest a liquid face uses (the pill's). */
    public const val LENS_BLUR_DP: Float = 2f

    /** Room on top of the farthest measured pixel. */
    public const val MARGIN: Float = 1.15f
}
