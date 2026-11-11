package io.github.eslamasabry.oc.ui.design.style

/**
 * The glass looks a person picks under Glass (docs/designs/glass-looks-2026-11-09.md v2; owner 2026-11-09: "can we have
 * multiple glass options for users to decide?"). Stored by [storageName] in `oc.glassLook`; a missing or unknown value
 * reads [Glossy], the default (D4).
 */
public enum class GlassLookId(public val storageName: String) {
    /** Glass-live's own glass ([GlassLook.Default]): clear, crisp, a calm specular and glint. */
    Glossy("glossy"),

    /** Thinner and more see-through, less shine. Offered only while [GlassLooks.clearEarnsItsPlace] holds (D3). */
    Clear("clear"),

    /** Milky blur, the most readable; no lens (works without runtime shaders). */
    Frosted("frosted"),

    /** Dark tinted glass on the floating pieces only, in both modes (D6). */
    Smoked("smoked"),
    ;

    public companion object {
        /** A stored Smoked reads as Glossy: Smoked is no longer offered (owner 2026-11-09, "get rid of dark ... not needed"). */
        public fun fromStorage(name: String?): GlassLookId =
            entries.firstOrNull { it.storageName == name }?.takeIf { it != Smoked } ?: Glossy
    }
}

/**
 * One look: a [GlassLook] spec (every tint, blur and gloss number of the clear panes and the liquid faces) plus a few
 * liquid levers. Every number a look changes lives in [GlassLooks]: tune them there, and `GlassPanesTest` re-measures the
 * readability floor and the Clear gap for every pack.
 */
public data class GlassLookPreset(
    val id: GlassLookId,
    val spec: GlassLook,
    /** Whether floating pieces bend light at their rim (liquid glass). Off: they are frost of the same shape. */
    val lens: Boolean,
    /** A liquid face's starting tint is its role's tint times this; the floor then thickens it as its words need. */
    val tintFactor: Float,
    /**
     * Smoked: floating pieces take the dark theme's face and ink in both modes, starting at [smokeTint] (never below the
     * role's own tint); in-flow panes keep the page's own clear panes (D6).
     */
    val smoked: Boolean = false,
    val smokeTint: Float = 0f,
)

public object GlassLooks {
    /** The default (D4): exactly [GlassLook.Default], what glass-live ships. Nothing extra. */
    public val Glossy: GlassLookPreset = GlassLookPreset(GlassLookId.Glossy, GlassLook.Default, lens = true, tintFactor = 1f)

    /** [GlassLook.Default] with its speculars at [CLEAR_SPECULAR] and a thinner starting tint. */
    public val Clear: GlassLookPreset =
        GlassLookPreset(
            GlassLookId.Clear,
            GlassLook.Default.let {
                it.copy(
                    specularDark = it.specularDark * CLEAR_SPECULAR,
                    specularLight = it.specularLight * CLEAR_SPECULAR,
                    liquidSpecularDark = it.liquidSpecularDark * CLEAR_SPECULAR,
                    liquidSpecularLight = it.liquidSpecularLight * CLEAR_SPECULAR,
                )
            },
            lens = true,
            tintFactor = .7f,
        )

    /**
     * [GlassLook.Frosted] (glass-v3's lighter clear panes, D7: no longer the old thick Flutter panes) and no lens: the
     * floating pieces keep frost's face.
     */
    public val Frosted: GlassLookPreset = GlassLookPreset(GlassLookId.Frosted, GlassLook.Frosted, lens = false, tintFactor = 1.3f)

    /**
     * [GlassLook.Default] with dark smoke on the floating pieces, and the clear pane rim it had before 2026-11-11
     * (Frosted's): Smoked is no longer offered, so the step-1 lit bottom end does not change it
     * (docs/designs/glass-in-the-kit.md 1a).
     */
    public val Smoked: GlassLookPreset =
        GlassLookPreset(GlassLookId.Smoked, GlassLook.Default.withPaneRimOf(GlassLook.Frosted), lens = true, tintFactor = 1f, smoked = true, smokeTint = .40f)

    /** Clear's specular, as a share of Default's. */
    public const val CLEAR_SPECULAR: Float = .8f

    public fun of(id: GlassLookId): GlassLookPreset =
        when (id) {
            GlassLookId.Glossy -> Glossy
            GlassLookId.Clear -> Clear
            GlassLookId.Frosted -> Frosted
            GlassLookId.Smoked -> Smoked
        }

    /**
     * Gate 1 (D3), measured by `GlassPanesTest` over every pack, both modes, the painted aurora and the shader looks:
     * Clear ships only if, after the floor, its median liquid face is at least [CLEAR_TINT_GAP] thinner than Glossy's, or
     * its gloss-kept count differs by at least [CLEAR_GLOSS_GAP] of the faces. Measured 2026-11-09 (GitHub CI run
     * 42587811814): the median gap is 3.1-5.5 points in dark mode and 0 in light mode in every pack, over both
     * backdrops, and the gloss-kept count never differs, so Clear is cut and three looks ship. The test fails if a
     * retune opens the gap, so this flag is never stale.
     */
    public const val clearEarnsItsPlace: Boolean = false

    public const val CLEAR_TINT_GAP: Float = .08f
    public const val CLEAR_GLOSS_GAP: Float = .25f

    /** The looks the picker offers, in its order. A stored Clear still reads (and draws) as Clear. */
    public val offered: List<GlassLookId> =
        // Smoked was dropped (owner 2026-11-09): two looks, clear Glossy and milky Frosted.
        if (clearEarnsItsPlace) listOf(GlassLookId.Clear, GlassLookId.Glossy, GlassLookId.Frosted)
        else listOf(GlassLookId.Glossy, GlassLookId.Frosted)
}
