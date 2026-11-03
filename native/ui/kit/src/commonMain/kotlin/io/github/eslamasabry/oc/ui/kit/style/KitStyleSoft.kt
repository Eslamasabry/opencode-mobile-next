package io.github.eslamasabry.oc.ui.kit.style

import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.RoleDerivation
import io.github.eslamasabry.oc.ui.design.color.Roles
import io.github.eslamasabry.oc.ui.design.color.over
import io.github.eslamasabry.oc.ui.design.effects.KitDepth
import io.github.eslamasabry.oc.ui.design.effects.KitEdges
import io.github.eslamasabry.oc.ui.design.effects.KitKnobs
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel

/** How far a soft surface stands out (the shadow offset) and how soft its shadows are. */
public data class KitSoftDepth(val distance: Float, val blur: Float)

/**
 * Soft (Dart `KitStyleSoft`, neumorphism): the surface takes the ground's colour and is shaped by paired soft shadows,
 * raised at rest and pressed in when chosen.
 */
public object KitStyleSoft : KitStyleSpec {
    override val tokens: KitStyleTokens =
        KitStyleTokens(
            defaults = KitKnobs(depth = KitDepth.Raised, edges = KitEdges.None),
            dependsOnDepth = true,
            live = KitLivePolicy(maxStrength = .7f),
            needsYou = KitNeedsYouLook(tint = false, ring = false, startEdge = true),
            fieldHalo = true,
            type = KitStyleType(firmWeights = true),
        )

    public fun depthOf(role: KitSurfaceRole): KitSoftDepth =
        when (role) {
            KitSurfaceRole.Content, KitSurfaceRole.Floating -> KitSoftDepth(6f, 16f)
            KitSurfaceRole.Control -> KitSoftDepth(3f, 8f)
            KitSurfaceRole.Track, KitSurfaceRole.Field, KitSurfaceRole.Toggle, KitSurfaceRole.StateLayer, KitSurfaceRole.Code ->
                KitSoftDepth(2.5f, 6f)
            KitSurfaceRole.Sheet, KitSurfaceRole.Ground, KitSurfaceRole.Divider -> KitSoftDepth(0f, 0f)
        }

    /** The near-white washed over a raised face in dark (its lit top). */
    private const val LIFT = .05f

    /** The shade pressed into a dark well. */
    private const val WELL = .18f

    /**
     * The ground moved off the theme's black or near-white so both shadows of the pair can show: dark lifted, light one
     * step down, hue kept; it stops short where the quieter words would drop under AA.
     */
    override fun adjustRoles(roles: Roles): Roles {
        val ink = roles.text1.withAlpha(1f)
        val dark = roles.dark
        val step = if (dark) .035 else .03
        val words = listOf(roles.text2, roles.text3)
        var t = if (dark) .07 else .045
        while (t > 0) {
            val ground = mix(roles.ground, ink, t)
            val faces = listOf(ground, kitStyleLight(roles).withAlpha(if (dark) LIFT else 0f).over(ground), mix(ground, ink, step))
            if (words.all { w -> faces.all { f -> contrast(w, f) >= 4.5 } }) {
                return roles.copy(
                    ground = ground,
                    surface1 = ground,
                    surface2 = if (dark) mix(roles.surface2, ink, t) else roles.surface2,
                    surface3 = faces.last(),
                )
            }
            t -= .005
        }
        return roles
    }

    /** Words on a chosen control with no fill of its own: the accent, moved until it reads at 4.6:1 on the well. */
    override fun onColor(r: KitSurfaceRequest): Argb? {
        if (!r.selected || r.role != KitSurfaceRole.Control) return null
        if (r.color != null) return null
        val roles = r.roles
        val under = roles.ground.withAlpha(1f)
        val well = shaped(r).fill.over(under)
        return RoleDerivation.readableOn(roles.accent, listOf(well, under), 4.6, roles.text1.withAlpha(1f))
    }

    override fun resolve(r: KitSurfaceRequest): KitSurfaceLook {
        part(r)?.let { return it }
        val shaped = shaped(r)
        if (r.knobs.edges != KitEdges.Hairline) return shaped
        return withEdge(shaped, r.roles.text3.withAlpha(.45f), r.edgeWidth)
    }

    private fun withEdge(shaped: KitSurfaceLook, edge: Argb, width: Float) = shaped.copy(edgeStart = edge, edgeEnd = edge, edgeWidth = width)

    private fun part(r: KitSurfaceRequest): KitSurfaceLook? {
        val roles = r.roles
        return when (r.role) {
            KitSurfaceRole.Ground -> kitStyleGround(r)
            KitSurfaceRole.Divider ->
                if (r.knobs.edges == KitEdges.Hairline) r.solid else KitSurfaceLook(fill = roles.ground.withAlpha(0f))
            KitSurfaceRole.StateLayer -> {
                val dark = roles.dark
                if (!r.pressed) {
                    KitSurfaceLook(
                        radius = r.radius, circle = r.circle,
                        fill = if (dark) kitStyleLight(roles).withAlpha(.04f) else kitStyleShade(roles).withAlpha(.03f),
                    )
                } else {
                    val well = shaped(r)
                    KitSurfaceLook(
                        radius = well.radius, circle = well.circle,
                        fill = kitStyleShade(roles).withAlpha(if (dark) .28f else .03f),
                        lightShadow = well.lightShadow, darkShadow = well.darkShadow,
                        distance = well.distance, blur = well.blur, inset = 1f,
                    )
                }
            }
            KitSurfaceRole.Code -> {
                val well = shaped(r)
                if (r.knobs.edges == KitEdges.Hairline) withEdge(well, roles.text3.withAlpha(.45f), r.edgeWidth) else well
            }
            KitSurfaceRole.Field, KitSurfaceRole.Toggle -> {
                val on = r.selected && r.role == KitSurfaceRole.Toggle
                val edge = if (r.selected) (if (on) r.color ?: roles.accent else roles.accent) else kitStyleBoundary(roles)
                withEdge(shaped(r), edge, r.edgeWidth)
            }
            else -> null
        }
    }

    private fun shaped(r: KitSurfaceRequest): KitSurfaceLook {
        val roles = r.roles
        val role = r.role
        val radius = r.radius
        val circle = r.circle
        val color = r.color
        val edgeWidth = r.edgeWidth
        val dark = roles.dark
        val white = kitStyleLight(roles)
        val black = kitStyleShade(roles)
        val ground = color ?: roles.ground
        val filledChoice = role == KitSurfaceRole.Control && color != null && r.selected
        val pressedIn =
            when (role) {
                KitSurfaceRole.Track, KitSurfaceRole.Field, KitSurfaceRole.Toggle, KitSurfaceRole.Code, KitSurfaceRole.StateLayer -> true
                else -> r.pressed || (r.selected && !filledChoice)
            }
        val prompt = role == KitSurfaceRole.Content && r.level == SurfaceLevel.Surface2 && color == null && !pressedIn
        if (r.disabled && (role == KitSurfaceRole.Control || role == KitSurfaceRole.Content)) {
            return KitSurfaceLook(radius = radius, circle = circle, fill = ground)
        }
        val flat = r.knobs.depth == KitDepth.Flat
        val depth = if (flat) KitSoftDepth(0f, 0f) else depthOf(role)

        if (role == KitSurfaceRole.Sheet) {
            return KitSurfaceLook(
                radius = radius,
                circle = circle,
                fill = if (dark) white.withAlpha(.03f).over(ground) else ground,
                edgeStart = white.withAlpha(if (dark) .10f else .9f),
                edgeEnd = white.withAlpha(0f),
                edgeWidth = edgeWidth,
                shadows = listOf(KitShadow(black.withAlpha(if (dark) .55f else .14f), dy = -4f, blur = 28f)),
            )
        }

        val inks = listOf(roles.text1, roles.text2)
        val face =
            if (prompt) {
                tinted(ground, white, most = if (dark) .10 else .6, least = if (dark) LIFT + .01 else 0.0, inks = inks)
                    ?: tinted(ground, roles.partner, most = .10, inks = inks)
                    ?: ground
            } else {
                ground
            }

        if (dark) {
            val lit = white.withAlpha(if (pressedIn || prompt || color != null) 0f else LIFT).over(face)
            return KitSurfaceLook(
                radius = radius,
                circle = circle,
                fill = if (pressedIn) black.withAlpha(WELL).over(ground) else lit,
                edgeStart = if (pressedIn) white.withAlpha(0f) else white.withAlpha(.16f),
                edgeEnd = if (pressedIn) white.withAlpha(.06f) else white.withAlpha(0f),
                edgeWidth = edgeWidth,
                lightShadow = white.withAlpha(if (color != null) .07f else .10f),
                darkShadow = black.withAlpha(.85f),
                distance = depth.distance,
                blur = depth.blur,
                inset = if (pressedIn) 1f else 0f,
            )
        }

        return KitSurfaceLook(
            radius = radius,
            circle = circle,
            fill =
                if (pressedIn) {
                    if (role == KitSurfaceRole.Code) lightWell(roles, ground) else black.withAlpha(.025f).over(ground)
                } else {
                    face
                },
            edgeStart = if (pressedIn) white.withAlpha(0f) else white.withAlpha(if (filledChoice) .35f else .7f),
            edgeEnd = white.withAlpha(0f),
            edgeWidth = edgeWidth,
            lightShadow = white.withAlpha(.7f),
            darkShadow = if (filledChoice) mix(color!!, roles.text1, .55).withAlpha(.34f) else roles.text1.withAlpha(.26f),
            distance = depth.distance,
            blur = depth.blur,
            inset = if (pressedIn) 1f else 0f,
        )
    }

    /** [base] washed with up to [most] of [tint], backed off until every ink reads at AA; null below [least]. */
    private fun tinted(base: Argb, tint: Argb, most: Double, least: Double = 0.0, inks: List<Argb>): Argb? {
        var a = most
        while (a > least) {
            val face = tint.withAlpha(a.toFloat()).over(base)
            if (inks.all { contrast(it, face) >= 4.5 }) return face
            a -= .01
        }
        return null
    }

    /** The light code well: lightened toward the near-white only as far as every code colour needs to read at AA. */
    private fun lightWell(roles: Roles, fill: Argb): Argb {
        val light = kitStyleLight(roles)
        val inks = listOf(roles.text1, roles.text2, roles.codeKeyword, roles.codeString, roles.codeType, roles.codeAdded)
        var t = 0.0
        while (t < 1) {
            val well = mix(fill, light, t)
            if (inks.all { contrast(it, well) >= 4.6 }) return well
            t += .1
        }
        return light
    }
}
