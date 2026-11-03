package io.github.eslamasabry.oc.ui.kit.style

import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.Roles
import io.github.eslamasabry.oc.ui.design.color.withAccent
import io.github.eslamasabry.oc.ui.design.effects.KitCorners
import io.github.eslamasabry.oc.ui.design.effects.KitDepth
import io.github.eslamasabry.oc.ui.design.effects.KitEdges
import io.github.eslamasabry.oc.ui.design.effects.KitFineTune
import io.github.eslamasabry.oc.ui.design.effects.KitKnobs
import io.github.eslamasabry.oc.ui.design.effects.KitSurfaceStyle
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import kotlin.math.roundToInt

/** What Fine-tune shows for the stored choices (Dart `KitFineTunePanel.read`). */
public data class KitFineTuneRead(
    /** The style in force (high contrast may turn it to Solid). */
    val style: KitSurfaceStyle,
    /** That style's own knobs. */
    val defaults: KitKnobs,
    /** The knobs in force under the rules of [KitStyles.knobsFor]. */
    val knobs: KitKnobs,
    val heldByContrast: Boolean,
    /** Flat Soft keeps its edges on. */
    val edgesHeldByDepth: Boolean,
    /** Whether any choice differs from the style's own (the reset is offered). */
    val differs: Boolean,
)

/**
 * The design schools (Dart `kitStyleSpec`, `KitStyles` and `KitSurfaceLook.resolve`): one [KitStyleSpec] per
 * [KitSurfaceStyle], and the rules every style keeps whatever its spec says. Kit parts ask here; screens never know a
 * style exists.
 */
public object KitStyles {
    public fun spec(style: KitSurfaceStyle): KitStyleSpec =
        when (style) {
            KitSurfaceStyle.Solid -> KitStyleSolid
            KitSurfaceStyle.Soft -> KitStyleSoft
            KitSurfaceStyle.Brutal -> KitStyleBrutal
            KitSurfaceStyle.Paper -> KitStylePaper
            KitSurfaceStyle.Signal -> KitStyleSignal
        }

    /** The style in force: Solid under high contrast unless the style keeps itself (Brutal, Paper, Signal). */
    public fun effectiveStyle(style: KitSurfaceStyle, highContrast: Boolean): KitSurfaceStyle =
        if (highContrast && !spec(style).tokens.keepsUnderHighContrast) KitSurfaceStyle.Solid else style

    /**
     * The knobs in force: the person's choices over the effective style's defaults; high contrast turns edges on and
     * depth off; a style shaped by its shadows keeps its edges when flat.
     */
    public fun knobsFor(style: KitSurfaceStyle, fineTune: KitFineTune = KitFineTune.None, highContrast: Boolean = false): KitKnobs {
        val tokens = spec(effectiveStyle(style, highContrast)).tokens
        var knobs = fineTune.over(tokens.defaults)
        if (highContrast) knobs = knobs.copy(depth = KitDepth.Flat, edges = KitEdges.Hairline)
        if (tokens.dependsOnDepth && knobs.depth == KitDepth.Flat) knobs = knobs.copy(edges = KitEdges.Hairline)
        return knobs
    }

    /** [radius] under Corners: Round keeps it, Soft takes about 60 % (stadiums stay), Sharp takes [sharp]. */
    public fun corner(radius: Float, corners: KitCorners, sharp: Float = 2f): Float {
        if (radius <= 0f) return radius
        return when (corners) {
            KitCorners.Round -> radius
            KitCorners.Soft -> if (radius >= 999f) radius else (radius * .6f).roundToInt().toFloat()
            KitCorners.Sharp -> if (radius < sharp) radius else sharp
        }
    }

    /** What Fine-tune shows for the stored choices. */
    public fun fineTuneRead(style: KitSurfaceStyle, fineTune: KitFineTune, highContrast: Boolean = false): KitFineTuneRead {
        val effective = effectiveStyle(style, highContrast)
        val tokens = spec(effective).tokens
        val knobs = knobsFor(style, fineTune, highContrast)
        return KitFineTuneRead(
            style = effective,
            defaults = tokens.defaults,
            knobs = knobs,
            heldByContrast = highContrast,
            edgesHeldByDepth = tokens.dependsOnDepth && knobs.depth == KitDepth.Flat,
            differs = fineTune.over(tokens.defaults) != tokens.defaults,
        )
    }

    /** [roles] under [style] (hook H6): the theme itself when the style keeps it; else the accent guard re-runs. */
    public fun restyled(roles: Roles, style: KitSurfaceStyle): Roles {
        val adjusted = spec(style).adjustRoles(roles)
        if (adjusted == roles) return roles
        return adjusted.withAccent(adjusted.accent)
    }

    /**
     * The look for a surface without a context (Dart `KitSurfaceLook.resolve`). Solid at its defaults returns [solid]
     * itself. On top of a spec's answer: the page is its fill alone, code stays opaque, and content hosts the surfaces
     * nested in it. A highlight asked with [color] is the part's own unless the style answers it.
     */
    public fun resolve(
        roles: Roles,
        style: KitSurfaceStyle,
        role: KitSurfaceRole,
        solid: KitSurfaceLook,
        knobs: KitKnobs? = null,
        level: SurfaceLevel = SurfaceLevel.Surface1,
        radius: KitRadii? = null,
        circle: Boolean = false,
        color: Argb? = null,
        selected: Boolean = false,
        pressed: Boolean = false,
        hovered: Boolean = false,
        disabled: Boolean = false,
        withinGroup: Boolean = false,
        edgeWidth: Float = 1f,
        action: Boolean = false,
    ): KitSurfaceLook {
        val spec = spec(style)
        val request = KitSurfaceRequest(
            roles = roles, role = role, solid = solid, knobs = knobs ?: spec.tokens.defaults, level = level,
            radius = radius ?: solid.radius, circle = circle || solid.circle, color = color, selected = selected,
            pressed = pressed, hovered = hovered, disabled = disabled, withinGroup = withinGroup, edgeWidth = edgeWidth,
            action = action,
        )
        if (role == KitSurfaceRole.StateLayer && color != null) return spec.resolveFilledLayer(request) ?: solid
        val look = spec.resolve(request)
        // A part's own look handed back (Solid, Brutal's page, Soft's ruled divider) is kept exactly.
        if (style == KitSurfaceStyle.Solid || look === solid) return look
        return when (role) {
            KitSurfaceRole.Ground -> look.fillOnly
            KitSurfaceRole.Code -> look.opaqueOver(roles.ground)
            KitSurfaceRole.Content -> look.hosting
            else -> look
        }
    }

    /** The words' colour on a surface (hook H3): the style's, or [fallback]. Solid always keeps [fallback]. */
    public fun wordsOn(
        roles: Roles,
        style: KitSurfaceStyle,
        knobs: KitKnobs,
        fallback: Argb,
        role: KitSurfaceRole = KitSurfaceRole.Control,
        level: SurfaceLevel = SurfaceLevel.Surface3,
        color: Argb? = null,
        selected: Boolean = false,
        pressed: Boolean = false,
        action: Boolean = false,
    ): Argb =
        spec(style).onColor(
            KitSurfaceRequest(
                roles = roles, role = role, solid = KitSurfaceLook.Bare, knobs = knobs, level = level, color = color,
                selected = selected, pressed = pressed, action = action,
            ),
        ) ?: fallback

    /** A divider line's colour and width in a style. */
    public data class Line(val color: Argb, val width: Float)

    /**
     * A divider line here (the `divider` role): transparent means the style draws no line (keep its space). Solid at
     * its defaults returns both unchanged. [withinGroup]: a line between the rows of one group (Brutal parts it by
     * space alone).
     */
    public fun divider(
        roles: Roles,
        style: KitSurfaceStyle,
        knobs: KitKnobs,
        color: Argb,
        width: Float,
        withinGroup: Boolean = false,
    ): Line {
        val look = resolve(roles, style, KitSurfaceRole.Divider, KitSurfaceLook(fill = color), knobs, withinGroup = withinGroup)
        val styled = spec(style).tokens.dividerWidth
        return Line(look.fill, if (look.fill.alpha > 0) styled ?: width else width)
    }

    /** The rule above a section's label, or null when the style draws none (only Paper does). */
    public fun sectionRule(roles: Roles, style: KitSurfaceStyle, knobs: KitKnobs, hairline: Float): Line? {
        val none = KitSurfaceLook(fill = Clear)
        val row = resolve(roles, style, KitSurfaceRole.Divider, none, knobs)
        val section = resolve(roles, style, KitSurfaceRole.Divider, none, knobs, selected = true)
        if (section == row || section.fill.alpha == 0) return null
        return Line(section.fill, if (section.edgeWidth > 0f) section.edgeWidth else hairline)
    }

    /** The underline under a link's words, as a multiple of the words' stroke, or null for none. */
    public fun linkUnderline(roles: Roles, style: KitSurfaceStyle, knobs: KitKnobs): Float? {
        spec(style).tokens.linkUnderline?.let { return if (it > 0f) it else null }
        val line = divider(roles, style, knobs, Clear, 1f)
        return if (line.color.alpha > 0) line.width else null
    }
}
