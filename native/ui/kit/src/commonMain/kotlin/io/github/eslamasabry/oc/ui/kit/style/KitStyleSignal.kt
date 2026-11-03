package io.github.eslamasabry.oc.ui.kit.style

import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.RoleDerivation
import io.github.eslamasabry.oc.ui.design.color.Roles
import io.github.eslamasabry.oc.ui.design.color.argb
import io.github.eslamasabry.oc.ui.design.effects.KitCorners
import io.github.eslamasabry.oc.ui.design.effects.KitDepth
import io.github.eslamasabry.oc.ui.design.effects.KitEdges
import io.github.eslamasabry.oc.ui.design.effects.KitKnobs
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel

/** The Signal enamel the style's surfaces read (Dart `SignalPalette`, `lib/ui/theme_packs_signal.dart`). */
public data class KitSignalEnamel(
    val dark: Boolean,
    val ground: Argb,
    val slip: Argb,
    val rule: Argb,
    /** A quiet raised key (Reject on a slip) and its edge. */
    val key: Argb,
    val keyEdge: Argb,
) {
    public companion object {
        public val Day: KitSignalEnamel =
            KitSignalEnamel(false, argb(0xFFEFE9DA), argb(0xFFFBF9F2), argb(0xFFC9BFA6), argb(0xFFF7F3E8), argb(0xFF8F887A))
        public val Night: KitSignalEnamel =
            KitSignalEnamel(true, argb(0xFF12110E), argb(0xFF221F19), argb(0xFF2E2B24), argb(0xFF26241F), argb(0xFF0A0908))

        public fun of(roles: Roles): KitSignalEnamel = if (roles.dark) Night else Day
    }
}

/** A raised hardware key: its face, a 1 dp edge and a blur-free lip under it (3 dp, 1 dp while held). */
public fun kitSignalKey(fill: Argb, edge: Argb, radius: KitRadii, circle: Boolean = false, pressed: Boolean = false): KitSurfaceLook =
    KitSurfaceLook(
        radius = radius, circle = circle, fill = fill, edgeStart = edge, edgeEnd = edge, edgeWidth = 1f,
        shadows = listOf(KitShadow(edge, dy = if (pressed) 1f else 3f)),
    )

/** The colour a key's edge and lip take under [fill]: the fill gone most of the way to black. */
public fun kitSignalKeyEdge(fill: Argb): Argb {
    val hsl = Hsl.of(fill.withAlpha(1f))
    return hsl.copy(lightness = hsl.lightness * .5).toArgb()
}

/**
 * Signal (Dart `KitStyleSignal`): a railway signal box crossed with a hardware console. Cream enamel and ink in light,
 * a night signal box in dark. Surfaces are flat print on the enamel; what you act on is a flat ink outline or, when it
 * is an action with a fill of its own, a raised key. A chosen control is set in reverse.
 */
public object KitStyleSignal : KitStyleSpec {
    /** A flat control's ink outline, in dp. */
    public const val OUTLINE: Float = 1.5f

    public fun invertedInk(roles: Roles): Argb = roles.text1.withAlpha(1f)

    private fun inverted(r: KitSurfaceRequest) = r.role == KitSurfaceRole.Control && r.selected && r.color == null && !r.circle

    override val tokens: KitStyleTokens =
        KitStyleTokens(
            defaults = KitKnobs(corners = KitCorners.Sharp),
            sharpRadius = 4f,
            keepsUnderHighContrast = true,
            live = KitLivePolicy(offWhenChosen = true),
            needsYou = KitNeedsYouLook(tint = false, ring = false, startEdge = false),
            flatLists = true,
            track = true,
            type = KitStyleType(
                displayWeight = 700, largeTitleSize = 32f, largeTitleWeight = 760, displayTracking = -.02f, titleSize = 22f,
                engravedLabels = true, firmWeights = true,
            ),
        )

    override fun adjustRoles(roles: Roles): Roles {
        val palette = KitSignalEnamel.of(roles)
        val ink = roles.text1.withAlpha(1f)
        for (share in listOf(1.0, .7, .4)) {
            val ground = mix(roles.ground, palette.ground, share)
            val slip = mix(roles.surface2, palette.slip, share)
            val pressed = mix(ground, ink, if (roles.dark) .09 else .07)
            val steps = listOf(ground, slip, pressed)
            val next = roles.copy(
                ground = ground,
                surface1 = ground,
                surface2 = slip,
                surface3 = pressed,
                hairline = mix(roles.hairline, palette.rule, share),
                text3 = RoleDerivation.readableOn(roles.text3, steps, 4.5, ink),
                attention = RoleDerivation.readableOn(roles.attention, steps, 4.5, ink),
            )
            if (KitStyleBrutal.readable(next)) return next
        }
        return roles
    }

    private fun radiusOf(r: KitSurfaceRequest): KitRadii =
        if (r.knobs.corners != KitCorners.Sharp) r.radius else r.radius.map { if (it > tokens.sharpRadius) tokens.sharpRadius else it }

    override fun resolve(r: KitSurfaceRequest): KitSurfaceLook {
        val roles = r.roles
        val radius = radiusOf(r)
        val edges = r.knobs.edges == KitEdges.Hairline
        val ink = roles.text2.withAlpha(1f)
        val down = r.selected || r.pressed
        val clear = roles.ground.withAlpha(0f)
        fun box(fill: Argb, edge: Argb? = null, width: Float = 1f, shadows: List<KitShadow> = emptyList()) =
            KitSurfaceLook(
                radius = radius, circle = r.circle, fill = fill,
                edgeStart = edge ?: Clear, edgeEnd = edge ?: Clear, edgeWidth = if (edge == null) 0f else width, shadows = shadows,
            )
        val lift = if (r.knobs.depth == KitDepth.Raised) listOf(KitStyleSolid.lift(roles)) else emptyList()
        val own = r.color
        return when (r.role) {
            KitSurfaceRole.Ground -> kitStyleGround(r)
            KitSurfaceRole.Field -> box(clear, ink, if (r.selected) 2f else OUTLINE)
            KitSurfaceRole.Toggle -> box((if (r.selected) r.color else null) ?: r.levelFill, ink, OUTLINE)
            KitSurfaceRole.Code -> box(r.color ?: r.levelFill, if (edges) roles.hairline else null)
            KitSurfaceRole.Divider -> KitSurfaceLook(fill = if (edges && !r.selected) roles.hairline else Clear)
            KitSurfaceRole.StateLayer -> {
                val face = roles.text1.withAlpha(1f)
                box(if (r.pressed) mix(roles.ground, face, if (roles.dark) .12 else .08) else face.withAlpha(.04f))
            }
            KitSurfaceRole.Content -> {
                val step = r.level == SurfaceLevel.Surface2 || r.level == SurfaceLevel.Surface3
                box(
                    fill = r.color ?: (if (down) roles.surface3 else if (step) roles.surface2 else clear),
                    edge = if (edges && (step || r.selected)) roles.hairline else null,
                    shadows = if (radius.uniform) lift else emptyList(),
                )
            }
            KitSurfaceRole.Control -> {
                val opaque = own != null && own.alpha >= 255
                when {
                    r.action && opaque && !r.selected && !r.disabled && radius.uniform -> {
                        val palette = KitSignalEnamel.of(roles)
                        val face = keyFace(roles, own!!)
                        kitSignalKey(face, if (own == palette.key) palette.keyEdge else kitSignalKeyEdge(face), radius, r.circle, r.pressed)
                    }
                    inverted(r) -> box(invertedInk(roles))
                    own != null -> box(if (opaque) own else clear)
                    else -> box(if (down) roles.surface3 else clear, ink, OUTLINE)
                }
            }
            KitSurfaceRole.Track -> box(clear, ink, OUTLINE)
            KitSurfaceRole.Floating ->
                if (r.action && own != null && own.alpha >= 255 && !r.disabled && radius.uniform) {
                    val face = keyFace(roles, own)
                    kitSignalKey(face, kitSignalKeyEdge(face), radius, r.circle, r.pressed)
                } else {
                    box(own ?: roles.ground, ink, OUTLINE, if (radius.uniform) lift else emptyList())
                }
            KitSurfaceRole.Sheet ->
                box(r.color ?: r.levelFill, if (edges) roles.hairline else null, shadows = if (radius.uniform) lift else emptyList())
        }
    }

    /** A key's face for an action's own [fill]: as it is by day; by night eased toward the ground. */
    public fun keyFace(roles: Roles, fill: Argb): Argb {
        if (!roles.dark || fill == KitSignalEnamel.Night.key) return fill
        return mix(fill, roles.ground.withAlpha(1f), .3)
    }

    override fun onColor(r: KitSurfaceRequest): Argb? {
        if (inverted(r)) return r.roles.ground.withAlpha(1f)
        val own = r.color
        if ((r.role != KitSurfaceRole.Control && r.role != KitSurfaceRole.Floating) || !r.action || own == null ||
            own.alpha < 255 || r.selected || r.disabled || !r.roles.dark || own == KitSignalEnamel.Night.key
        ) {
            return null
        }
        val face = keyFace(r.roles, own)
        val light = r.roles.text1.withAlpha(1f)
        val dark = r.roles.ground.withAlpha(1f)
        return if (contrast(light, face) >= contrast(dark, face)) light else dark
    }
}
