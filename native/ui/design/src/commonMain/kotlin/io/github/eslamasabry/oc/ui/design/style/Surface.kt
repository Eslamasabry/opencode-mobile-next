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
)
