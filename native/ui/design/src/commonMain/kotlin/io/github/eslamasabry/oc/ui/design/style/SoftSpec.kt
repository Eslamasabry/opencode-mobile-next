package io.github.eslamasabry.oc.ui.design.style

import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.RoleDerivation
import io.github.eslamasabry.oc.ui.design.color.Roles
import io.github.eslamasabry.oc.ui.design.color.lerp
import io.github.eslamasabry.oc.ui.design.color.over
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel

/**
 * Soft: surfaces take the ground colour and are shaped by paired soft shadows (raised at rest, pressed in when chosen).
 * Port of Dart `KitStyleSoft`.
 */
public object SoftSpec : StyleSpec {
    override val tokens: StyleTokens =
        StyleTokens(
            defaults = Knobs(depth = Depth.Raised, edges = Edges.None),
            dependsOnDepth = true,
            live = LivePolicy(maxStrength = .7f),
            needsYou = NeedsYouLook(tint = false, ring = false, startEdge = true),
            fieldHalo = true,
            type = StyleType(firmWeights = true),
        )

    private const val LIFT = .05f
    private const val WELL = .18f

    private fun depthOf(role: SurfaceRole): Pair<Float, Float> =
        when (role) {
            SurfaceRole.Content, SurfaceRole.Floating -> 6f to 16f
            SurfaceRole.Control -> 3f to 8f
            SurfaceRole.Track -> 2.5f to 6f
            SurfaceRole.Sheet, SurfaceRole.Ground, SurfaceRole.Divider -> 0f to 0f
            SurfaceRole.Field, SurfaceRole.Toggle, SurfaceRole.StateLayer, SurfaceRole.Code -> 2.5f to 6f
        }

    override fun adjustRoles(roles: Roles): Roles {
        val ink = roles.text1.opaque()
        val dark = roles.dark
        val step = if (dark) .035f else .03f
        val words = listOf(roles.text2, roles.text3)
        var t = if (dark) .07f else .045f
        while (t > 0f) {
            val ground = lerp(roles.ground, ink, t)
            val faces =
                listOf(
                    ground,
                    styleLight(roles).withAlpha(if (dark) LIFT else 0f).over(ground),
                    lerp(ground, ink, step),
                )
            if (words.all { w -> faces.all { f -> contrast(w, f) >= 4.5 } }) {
                return roles.copy(
                    ground = ground,
                    surface1 = ground,
                    surface2 = if (dark) lerp(roles.surface2, ink, t) else roles.surface2,
                    surface3 = faces.last(),
                )
            }
            t -= .005f
        }
        return roles
    }

    override fun onColor(r: SurfaceRequest): Argb? {
        if (!r.selected || r.role != SurfaceRole.Control || r.color != null) return null
        val roles = r.roles
        val under = roles.ground.opaque()
        val well = (shaped(r).fill ?: under).over(under)
        return RoleDerivation.readableOn(roles.accent, listOf(well, under), 4.6, roles.text1.opaque())
    }

    override fun resolve(r: SurfaceRequest): SurfaceDecoration? {
        part(r)?.let { return it }
        val shaped = shaped(r)
        if (r.knobs.edges != Edges.Hairline) return shaped
        return withEdge(shaped, r.roles.text3.withAlpha(.45f), r.edgeWidthDp)
    }

    private fun withEdge(d: SurfaceDecoration, edge: Argb, width: Float): SurfaceDecoration =
        d.copy(edgeStart = edge, edgeEnd = edge, edgeWidthDp = width)

    private fun part(r: SurfaceRequest): SurfaceDecoration? {
        val roles = r.roles
        return when (r.role) {
            SurfaceRole.Ground -> styleGround(r)
            SurfaceRole.Divider ->
                if (r.knobs.edges == Edges.Hairline) SurfaceDecoration(fill = roles.hairline) else SurfaceDecoration(fill = roles.clear())
            SurfaceRole.StateLayer -> {
                val dark = roles.dark
                if (!r.pressed) {
                    SurfaceDecoration(
                        radii = r.radii,
                        circle = r.circle,
                        fill = if (dark) styleLight(roles).withAlpha(.04f) else styleShade(roles).withAlpha(.03f),
                    )
                } else {
                    val well = shaped(r)
                    SurfaceDecoration(
                        radii = well.radii,
                        circle = well.circle,
                        fill = styleShade(roles).withAlpha(if (dark) .28f else .03f),
                        paired = well.paired?.copy(inset = 1f),
                    )
                }
            }
            SurfaceRole.Code -> {
                val well = shaped(r)
                if (r.knobs.edges == Edges.Hairline) withEdge(well, roles.text3.withAlpha(.45f), r.edgeWidthDp) else well
            }
            SurfaceRole.Field, SurfaceRole.Toggle -> {
                val on = r.selected && r.role == SurfaceRole.Toggle
                val edge = if (r.selected) (if (on) r.color ?: roles.accent else roles.accent) else styleBoundary(roles)
                withEdge(shaped(r), edge, r.edgeWidthDp)
            }
            SurfaceRole.Content, SurfaceRole.Control, SurfaceRole.Track, SurfaceRole.Floating, SurfaceRole.Sheet -> null
        }
    }

    private fun shaped(r: SurfaceRequest): SurfaceDecoration {
        val roles = r.roles
        val role = r.role
        val dark = roles.dark
        val white = styleLight(roles)
        val black = styleShade(roles)
        val ground = r.color ?: roles.ground
        val filledChoice = role == SurfaceRole.Control && r.color != null && r.selected
        val pressedIn =
            when (role) {
                SurfaceRole.Track, SurfaceRole.Field, SurfaceRole.Toggle, SurfaceRole.Code, SurfaceRole.StateLayer -> true
                else -> r.pressed || (r.selected && !filledChoice)
            }
        val prompt = role == SurfaceRole.Content && r.level == SurfaceLevel.Surface2 && r.color == null && !pressedIn
        if (r.disabled && (role == SurfaceRole.Control || role == SurfaceRole.Content)) {
            return SurfaceDecoration(radii = r.radii, circle = r.circle, fill = ground)
        }
        val flat = r.knobs.depth == Depth.Flat
        val (distance, blur) = if (flat) 0f to 0f else depthOf(role)

        if (role == SurfaceRole.Sheet) {
            return SurfaceDecoration(
                radii = r.radii,
                circle = r.circle,
                fill = if (dark) white.withAlpha(.03f).over(ground) else ground,
                edgeStart = white.withAlpha(if (dark) .10f else .9f),
                edgeEnd = white.withAlpha(0f),
                edgeWidthDp = r.edgeWidthDp,
                shadows = listOf(Shadow(0f, -4f, 28f, black.withAlpha(if (dark) .55f else .14f))),
            )
        }

        val face =
            if (prompt) {
                tinted(ground, white, most = if (dark) .10f else .6f, least = if (dark) LIFT + .01f else 0f, inks = listOf(roles.text1, roles.text2))
                    ?: tinted(ground, roles.partner, most = .10f, least = 0f, inks = listOf(roles.text1, roles.text2))
                    ?: ground
            } else {
                ground
            }

        if (dark) {
            val lit = white.withAlpha(if (pressedIn || prompt || r.color != null) 0f else LIFT).over(face)
            return SurfaceDecoration(
                radii = r.radii,
                circle = r.circle,
                fill = if (pressedIn) black.withAlpha(WELL).over(ground) else lit,
                edgeStart = if (pressedIn) white.withAlpha(0f) else white.withAlpha(.16f),
                edgeEnd = if (pressedIn) white.withAlpha(.06f) else white.withAlpha(0f),
                edgeWidthDp = r.edgeWidthDp,
                paired = PairedShadow(white.withAlpha(if (r.color != null) .07f else .10f), black.withAlpha(.85f), distance, blur, if (pressedIn) 1f else 0f),
            )
        }
        return SurfaceDecoration(
            radii = r.radii,
            circle = r.circle,
            fill =
                if (pressedIn) {
                    if (role == SurfaceRole.Code) lightWell(roles, ground) else black.withAlpha(.025f).over(ground)
                } else {
                    face
                },
            edgeStart = if (pressedIn) white.withAlpha(0f) else white.withAlpha(if (filledChoice) .35f else .7f),
            edgeEnd = white.withAlpha(0f),
            edgeWidthDp = r.edgeWidthDp,
            paired =
                PairedShadow(
                    white.withAlpha(.7f),
                    if (filledChoice) lerp(r.color!!, roles.text1, .55f).withAlpha(.34f) else roles.text1.withAlpha(.26f),
                    distance,
                    blur,
                    if (pressedIn) 1f else 0f,
                ),
        )
    }

    private fun tinted(base: Argb, tint: Argb, most: Float, least: Float, inks: List<Argb>): Argb? {
        var a = most
        while (a > least) {
            val face = tint.withAlpha(a).over(base)
            if (inks.all { contrast(it, face) >= 4.5 }) return face
            a -= .01f
        }
        return null
    }

    private fun lightWell(roles: Roles, fill: Argb): Argb {
        val light = styleLight(roles)
        val inks = listOf(roles.text1, roles.text2, roles.codeKeyword, roles.codeString, roles.codeType, roles.codeAdded)
        var t = 0f
        while (t < 1f) {
            val well = lerp(fill, light, t)
            if (inks.all { contrast(it, well) >= 4.6 }) return well
            t += .1f
        }
        return light
    }
}
