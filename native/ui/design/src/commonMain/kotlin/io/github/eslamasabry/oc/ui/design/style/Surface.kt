package io.github.eslamasabry.oc.ui.design.style

import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.Roles
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel

/** What a surface is for (Dart `KitSurfaceRole`). */
public enum class SurfaceRole { Content, Control, Track, Floating, Sheet, Ground, Field, Code, Divider, StateLayer, Toggle }

/** Corner radii in dp, in layout-direction order; [circle] on the request overrides them. */
public data class Radii(val topStart: Float, val topEnd: Float, val bottomStart: Float, val bottomEnd: Float) {
    public val uniform: Boolean get() = topStart == topEnd && topStart == bottomStart && topStart == bottomEnd

    public fun map(f: (Float) -> Float): Radii = Radii(f(topStart), f(topEnd), f(bottomStart), f(bottomEnd))

    public companion object {
        public val Zero: Radii = Radii(0f, 0f, 0f, 0f)
        public fun all(dp: Float): Radii = Radii(dp, dp, dp, dp)
    }
}

/** One surface to resolve: the part's request, no look of its own (Dart `KitSurfaceRequest` without the `solid` decoration). */
public data class SurfaceRequest(
    val roles: Roles,
    val role: SurfaceRole,
    val knobs: Knobs,
    val level: SurfaceLevel = SurfaceLevel.Surface1,
    val radii: Radii = Radii.Zero,
    val circle: Boolean = false,
    /** A part's own fill (an accent button, a selected chip), kept as it is. */
    val color: Argb? = null,
    val selected: Boolean = false,
    val pressed: Boolean = false,
    val hovered: Boolean = false,
    val disabled: Boolean = false,
    val withinGroup: Boolean = false,
    val edgeWidthDp: Float = 1f,
    /** The control is an action the person presses (a button, Send), not a mark or a choice. */
    val action: Boolean = false,
    /**
     * The surface lies inside another outlined surface (a chip or a disc in the message box, a disc in its tray): it takes
     * the style's edge but casts no shadow of its own, so shadows never stack inside a pane, and it reads quieter than
     * the pane it sits in.
     */
    val nested: Boolean = false,
    /** The glass look's numbers (Glass only): its clear panes' blur, sheen, lit corner, specular and glint. */
    val look: GlassLook = GlassLook.Default,
) {
    public val levelFill: Argb
        get() = when (level) {
            SurfaceLevel.Ground -> roles.ground
            SurfaceLevel.Surface1 -> roles.surface1
            SurfaceLevel.Surface2 -> roles.surface2
            SurfaceLevel.Surface3 -> roles.surface3
        }
}

/** A drop shadow as pure data (dp). */
public data class Shadow(val dxDp: Float, val dyDp: Float, val blurDp: Float, val color: Argb, val spreadDp: Float = 0f)

/** Soft's paired light and dark shadows; [inset] > 0 presses the face in. */
public data class PairedShadow(val light: Argb, val dark: Argb, val distanceDp: Float, val blurDp: Float, val inset: Float)

/**
 * A style's answer for one surface (Dart `KitSurfaceDecoration`): fill, edge (a start colour fading to an end colour
 * for a lit rim), shadows and the face shift. A null decoration from the resolver means the part keeps its own look.
 * [hardOffsetDp] is Brutal's no-blur shadow toward the bottom end (mirrored in RTL by the painter).
 */
public data class SurfaceDecoration(
    val radii: Radii = Radii.Zero,
    val circle: Boolean = false,
    val fill: Argb? = null,
    val edgeStart: Argb? = null,
    val edgeEnd: Argb? = null,
    val edgeWidthDp: Float = 0f,
    val shadows: List<Shadow> = emptyList(),
    val paired: PairedShadow? = null,
    val hardShadow: Argb? = null,
    val hardOffsetDp: Float = 0f,
    /** Hook H2: the face and its content sink into the shadow while pressed. */
    val faceShiftDp: Float = 0f,
    /** The fill at the top-start corner; it fades to [fill] at the bottom end. Null: [fill] (GLASS_REF `fillStart`). */
    val fillStart: Argb? = null,
    /** A soft plate of this colour behind the words on this surface (hook H10; Glass). Transparent: none. */
    val wordsPlate: Argb = Argb(0),
    /** The sigma of the background blur behind this surface's face (Glass); 0 asks for none. Never lerps. */
    val frost: Float = 0f,
    /** A glow inside the face along its top edge, [INNER_GLOW_DEPTH_DP] down (Glass); transparent draws none. */
    val innerGlow: Argb = Argb(0),
    /**
     * Glass's top sheen (owner 2026-11-08, "more glossy"): this colour at the face's top edge, fading out over the first
     * [SHEEN_SHARE] of its height. Transparent draws none.
     */
    val sheen: Argb = Argb(0),
    /**
     * Glass's specular light (owner 2026-11-09, "crisper glossy glass"): this colour on a bright segment of the rim at the
     * top-start corner, fading along the edge, and, at [GLINT_SHARE] of it, a thin line just inside the top edge, fading
     * at both ends. Crisp (no blur). Transparent draws none.
     */
    val specular: Argb = Argb(0),
    /** The specular's glint line, as a share of [specular] (the glass look's [GlassLook.glintShare]). */
    val glint: Float = GLINT_SHARE,
    /**
     * The rim's colour between its two lit ends, halfway along its fade from [edgeStart] at the top start to [edgeEnd]
     * at the bottom end (Glass's clear panes, docs/designs/glass-in-the-kit.md 1a: one light, two lit arcs). Null: the
     * rim fades straight from [edgeStart] to [edgeEnd].
     */
    val edgeRest: Argb? = null,
) {
    public companion object {
        /** The most the [sheen] reaches down a face, in dp: a tall pane keeps its gloss at its top, not over its rows. */
        public const val SHEEN_MAX_DP: Float = 40f

        /** The [specular] line's strength next to the rim's bright segment. */
        public val GLINT_SHARE: Float get() = GlassLook.Default.glintShare

        /** How far down from the top edge the specular line lies, in dp (inside the rim). */
        public const val GLINT_DROP_DP: Float = 2.5f

        /** How far down the face [innerGlow] reaches, in dp (at most half the face). */
        public const val INNER_GLOW_DEPTH_DP: Float = 22f // FG: lib/ui/kit/kit_surface_look.dart:608

        /** The share of the face's height [sheen] fades over (at most [SHEEN_MAX_DP]). */
        public const val SHEEN_SHARE: Float = .30f
    }
}
