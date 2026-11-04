package io.github.eslamasabry.oc.ui.design.style

import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.RoleDerivation
import io.github.eslamasabry.oc.ui.design.color.Roles
import io.github.eslamasabry.oc.ui.design.color.argb
import io.github.eslamasabry.oc.ui.design.color.lerp

/**
 * The colours of a railway signal box, the same whatever the theme pack (Dart `SignalPalette`, owner 2026-10-30). The
 * lamps' red is the one red the app shows, approved for this style only.
 */
public data class SignalPalette(
    val dark: Boolean,
    val ground: Argb,
    val slip: Argb,
    val slipEdge: Argb,
    val rule: Argb,
    val track: Argb,
    val siding: Argb,
    val head: Argb,
    val lampOff: Argb,
    val lcd: Argb,
    val lcdGhost: Argb,
    val lcdBezel: Argb,
    val key: Argb,
    val keyEdge: Argb,
    val stopKey: Argb,
    val stopEdge: Argb,
    val alertInk: Argb,
    val inkKey: Argb,
    val inkKeyEdge: Argb,
    val inkKeyWords: Argb,
) {
    public companion object {
        public val Light: SignalPalette =
            SignalPalette(
                false, argb(0xFFEFE9DA), argb(0xFFFBF9F2), argb(0xFFCDC3A9), argb(0xFFC9BFA6), argb(0xFF191815), argb(0xFF6F6858),
                argb(0xFF15140F), argb(0xFF4A4740), argb(0xFF1B1D1A), argb(0xFF2C302A), argb(0x00000000), argb(0xFFF7F3E8), argb(0xFF8F887A),
                argb(0xFFB3261E), argb(0xFF6E130E), argb(0xFFA8231C), argb(0xFF22211D), argb(0xFF000000), argb(0xFFF4EFE2),
            )
        public val Night: SignalPalette =
            SignalPalette(
                true, argb(0xFF12110E), argb(0xFF221F19), argb(0xFF3F3A30), argb(0xFF2E2B24), argb(0xFF8E8673), argb(0xFF6A6353),
                argb(0xFF000000), argb(0xFF3A372F), argb(0xFF070806), argb(0xFF1A1D18), argb(0xFF34302A), argb(0xFF26241F), argb(0xFF0A0908),
                argb(0xFFC0332A), argb(0xFF5A0F0A), argb(0xFFFF8A7F), argb(0xFF3A372F), argb(0xFF000000), argb(0xFFF4EFE2),
            )
        public val LampRed: Argb = argb(0xFFFF3B2F)
        public val LampGreen: Argb = argb(0xFF3BE070)
        public val LcdOn: Argb = argb(0xFF9FE870)

        public fun forRoles(roles: Roles): SignalPalette = if (roles.dark) Night else Light
    }
}

/**
 * Signal: a track down the start edge, lamps, raised hardware keys, a printed slip, engraved labels; cream enamel by
 * day, a night signal box by dark. An action with its own fill is a raised key; the rest are flat. Port of Dart
 * `KitStyleSignal` (the lamps and slip are drawn by the parts that read [StyleTokens.track]).
 */
public object SignalSpec : StyleSpec {
    public const val OUTLINE: Float = 1.5f

    override val tokens: StyleTokens =
        StyleTokens(
            defaults = Knobs(corners = Corners.Sharp),
            sharpRadius = 4f,
            keepsUnderHighContrast = true,
            live = LivePolicy(offWhenChosen = true),
            needsYou = NeedsYouLook(tint = false, ring = false, startEdge = false),
            flatLists = true,
            track = true,
            type = StyleType(displayWeight = 700, largeTitleSize = 32, largeTitleWeight = 760, displayTracking = -.02f, titleSize = 22, engravedLabels = true, firmWeights = true),
        )

    public fun invertedInk(roles: Roles): Argb = roles.text1.opaque()

    private fun inverted(r: SurfaceRequest) = r.role == SurfaceRole.Control && r.selected && r.color == null && !r.circle

    override fun adjustRoles(roles: Roles): Roles {
        val palette = SignalPalette.forRoles(roles)
        val ink = roles.text1.opaque()
        for (share in floatArrayOf(1f, .7f, .4f)) {
            val ground = lerp(roles.ground, palette.ground, share)
            val slip = lerp(roles.surface2, palette.slip, share)
            val pressed = lerp(ground, ink, if (roles.dark) .09f else .07f)
            val steps = listOf(ground, slip, pressed)
            val next =
                roles.copy(
                    ground = ground,
                    surface1 = ground,
                    surface2 = slip,
                    surface3 = pressed,
                    hairline = lerp(roles.hairline, palette.rule, share),
                    text3 = RoleDerivation.readableOn(roles.text3, steps, 4.5, ink),
                    attention = RoleDerivation.readableOn(roles.attention, steps, 4.5, ink),
                )
            if (readable(next)) return next
        }
        return roles
    }

    private fun readable(r: Roles): Boolean =
        listOf(r.ground, r.surface1, r.surface2, r.surface3).all { fill ->
            listOf(r.text1, r.text2, r.text3, r.attention).all { ink -> contrast(ink, fill) >= 4.5 }
        }

    private fun radiiOf(r: SurfaceRequest): Radii =
        if (r.knobs.corners != Corners.Sharp) r.radii else r.radii.map { if (it > tokens.sharpRadius) tokens.sharpRadius else it }

    /** The edge of a key face: the face's HSL lightness halved. */
    public fun keyEdgeOf(fill: Argb): Argb = withLightness(fill.opaque(), hslLightness(fill.opaque()) * .5f)

    private fun key(fill: Argb, edge: Argb, r: SurfaceRequest, radii: Radii): SurfaceDecoration =
        SurfaceDecoration(
            radii = radii,
            circle = r.circle,
            fill = fill,
            edgeStart = edge,
            edgeEnd = edge,
            edgeWidthDp = 1f,
            shadows = listOf(Shadow(0f, if (r.pressed) 1f else 3f, 0f, edge)),
        )

    public fun keyFace(roles: Roles, fill: Argb): Argb =
        if (!roles.dark || fill == SignalPalette.Night.key) fill else lerp(fill, roles.ground.opaque(), .3f)

    override fun resolve(r: SurfaceRequest): SurfaceDecoration {
        val roles = r.roles
        val radii = radiiOf(r)
        val edges = r.knobs.edges == Edges.Hairline
        val ink = roles.text2.opaque()
        val down = r.selected || r.pressed
        val clear = roles.clear()
        fun box(fill: Argb, edge: Argb? = null, width: Float = 1f, shadows: List<Shadow> = emptyList()) =
            SurfaceDecoration(radii, r.circle, fill, edge, edge, if (edge == null) 0f else width, shadows)
        val lift = if (r.knobs.depth == Depth.Raised) listOf(floatingLift(roles)) else emptyList()
        return when (r.role) {
            SurfaceRole.Ground -> styleGround(r)
            SurfaceRole.Field -> box(clear, ink, if (r.selected) 2f else OUTLINE)
            SurfaceRole.Toggle -> box((if (r.selected) r.color else null) ?: r.levelFill, ink, OUTLINE)
            SurfaceRole.Code -> box(r.color ?: r.levelFill, if (edges) roles.hairline else null)
            SurfaceRole.Divider -> SurfaceDecoration(fill = if (edges && !r.selected) roles.hairline else clear)
            SurfaceRole.StateLayer -> {
                val face = roles.text1.opaque()
                box(if (r.pressed) lerp(roles.ground, face, if (roles.dark) .12f else .08f) else face.withAlpha(.04f))
            }
            SurfaceRole.Content -> {
                val step = r.level.isStep
                box(
                    fill = r.color ?: (if (down) roles.surface3 else if (step) roles.surface2 else clear),
                    edge = if (edges && (step || r.selected)) roles.hairline else null,
                    shadows = if (radii.uniform) lift else emptyList(),
                )
            }
            SurfaceRole.Control -> {
                val own = r.color
                val opaque = own != null && own.alpha == 255
                if (r.action && opaque && !r.selected && !r.disabled && radii.uniform) {
                    val palette = SignalPalette.forRoles(roles)
                    val quiet = own == palette.key
                    val face = keyFace(roles, own!!)
                    key(face, if (quiet) palette.keyEdge else keyEdgeOf(face), r, radii)
                } else if (inverted(r)) {
                    box(invertedInk(roles))
                } else if (own != null) {
                    box(if (opaque) own else clear)
                } else {
                    box(if (down) roles.surface3 else clear, ink, OUTLINE)
                }
            }
            SurfaceRole.Track -> box(clear, ink, OUTLINE)
            SurfaceRole.Floating ->
                if (r.action && r.color != null && r.color.alpha == 255 && !r.disabled && radii.uniform) {
                    val face = keyFace(roles, r.color)
                    key(face, keyEdgeOf(face), r, radii)
                } else {
                    box(r.color ?: roles.ground, ink, OUTLINE, if (radii.uniform) lift else emptyList())
                }
            SurfaceRole.Sheet ->
                box(r.color ?: r.levelFill, if (edges) roles.hairline else null, 1f, if (radii.uniform) lift else emptyList())
        }
    }

    override fun onColor(r: SurfaceRequest): Argb? {
        if (inverted(r)) return r.roles.ground.opaque()
        val own = r.color
        if ((r.role != SurfaceRole.Control && r.role != SurfaceRole.Floating) || !r.action || own == null || own.alpha < 255 ||
            r.selected || r.disabled || !r.roles.dark || own == SignalPalette.Night.key
        ) {
            return null
        }
        val face = keyFace(r.roles, own)
        val light = r.roles.text1.opaque()
        val darkInk = r.roles.ground.opaque()
        return if (contrast(light, face) >= contrast(darkInk, face)) light else darkInk
    }
}
