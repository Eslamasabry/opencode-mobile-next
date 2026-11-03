package io.github.eslamasabry.oc.ui.kit.style

import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.Roles
import io.github.eslamasabry.oc.ui.design.color.over
import io.github.eslamasabry.oc.ui.design.effects.KitCorners
import io.github.eslamasabry.oc.ui.design.effects.KitDepth
import io.github.eslamasabry.oc.ui.design.effects.KitEdges
import io.github.eslamasabry.oc.ui.design.effects.KitKnobs
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel

/**
 * Paper (Dart `KitStylePaper`, flat print, Swiss style): ink on newsprint. No shadows, no blur, square corners; rows
 * are set on the page and parted by fine ink rules, and only what you act on is a box, ruled in ink on the paper.
 */
public object KitStylePaper : KitStyleSpec {
    /** The ink box around what you act on: 0.9 on light paper, 0.6 on dark. */
    public fun boxAlpha(roles: Roles): Float = if (roles.dark) .6f else .9f

    /** The row divider: ink at this alpha. */
    public const val RULE_ALPHA: Float = .2f

    /** A chosen control's rule, in dp: twice the box. */
    public const val CHOSEN_RULE: Float = 2f

    /** The section rule above a section's label. */
    public const val SECTION_ALPHA: Float = .9f

    /** The highlight under a fine pointer, in ink over the face. */
    public const val HOVER_ALPHA: Float = .03f

    /** How far toward the ink a held row or control goes. */
    public fun pressStep(roles: Roles): Double = if (roles.dark) .10 else .07

    /** The ink a chosen control is filled with (inverted selection). */
    public fun invertedInk(roles: Roles): Argb = roles.text1.withAlpha(1f)

    private fun inverted(r: KitSurfaceRequest) = r.role == KitSurfaceRole.Control && r.selected && !r.circle

    /** A round control Corners › Sharp squares: a switch's thumb and the main action in its accent. */
    private fun squared(r: KitSurfaceRequest) =
        r.role == KitSurfaceRole.Control && r.circle && r.knobs.corners == KitCorners.Sharp &&
            (r.level == SurfaceLevel.Surface2 || r.color == r.roles.accent)

    override val tokens: KitStyleTokens =
        KitStyleTokens(
            defaults = KitKnobs(corners = KitCorners.Sharp),
            sharpRadius = 0f,
            keepsUnderHighContrast = true,
            live = KitLivePolicy(offWhenChosen = true),
            glow = KitStyleGlow(halo = false, lineWidth = 1f),
            type = KitStyleType(
                displayWeight = 750, displayTracking = -.03f, largeTitleSize = 34f, titleSize = 24f,
                monoLabels = true, firmWeights = true,
            ),
        )

    /** Newsprint: light paper a shade under white and a touch warm, dark paper a step off the theme's black. */
    override fun adjustRoles(roles: Roles): Roles {
        val ink = roles.text1.withAlpha(1f)
        val ground =
            if (roles.dark) mix(roles.ground, ink, .02)
            else roles.attentionFill.withAlpha(.04f).over(mix(roles.surface2, ink, .04))
        return roles.copy(
            ground = ground,
            surface1 = ground,
            surface3 = mix(ground, ink, if (roles.dark) .08 else .05),
            hairline = ink.withAlpha(RULE_ALPHA),
        )
    }

    override fun resolve(r: KitSurfaceRequest): KitSurfaceLook {
        part(r)?.let { return it }
        val roles = r.roles
        val ink = roles.text1.withAlpha(boxAlpha(roles))
        val down = r.selected || r.pressed
        val edges = r.knobs.edges == KitEdges.Hairline
        val fill: Argb
        val boxed: Boolean
        when (r.role) {
            KitSurfaceRole.Content -> {
                val step = r.level == SurfaceLevel.Surface2 || r.level == SurfaceLevel.Surface3
                fill = r.color ?: when {
                    down || (step && !edges) -> roles.surface3
                    step -> roles.ground
                    else -> roles.ground.withAlpha(0f)
                }
                boxed = edges && (step || r.selected)
            }
            KitSurfaceRole.Control, KitSurfaceRole.Track -> {
                fill = if (inverted(r)) invertedInk(roles) else r.color ?: (if (down) roles.surface3 else roles.ground)
                boxed = r.color == null || r.selected
            }
            KitSurfaceRole.Floating -> {
                fill = r.color ?: roles.ground
                boxed = true
            }
            KitSurfaceRole.Sheet -> {
                fill = r.color ?: r.levelFill
                boxed = edges
            }
            else -> return part(r)!!
        }
        val shadows =
            if (r.knobs.depth == KitDepth.Raised && r.role == KitSurfaceRole.Floating) listOf(KitStyleSolid.lift(roles)) else emptyList()
        val chosen = r.selected && r.role != KitSurfaceRole.Floating
        val edge = if (inverted(r)) fill else ink
        return KitSurfaceLook(
            radius = if (r.knobs.corners == KitCorners.Sharp) KitRadii.Zero else r.radius,
            circle = r.circle && !squared(r),
            fill = fill,
            edgeStart = if (boxed) edge else Clear,
            edgeEnd = if (boxed) edge else Clear,
            edgeWidth = if (boxed) (if (chosen && !inverted(r)) CHOSEN_RULE else 1f) else 0f,
            shadows = shadows,
        )
    }

    /** The words on a chosen control are the paper, on its ink. */
    override fun onColor(r: KitSurfaceRequest): Argb? = if (inverted(r)) r.roles.ground.withAlpha(1f) else null

    private fun part(r: KitSurfaceRequest): KitSurfaceLook? {
        val roles = r.roles
        val ink = roles.text1
        val edged = r.knobs.edges == KitEdges.Hairline
        val radius = if (r.knobs.corners == KitCorners.Sharp) KitRadii.Zero else r.radius
        val rule = if (edged) ink.withAlpha(RULE_ALPHA) else Clear
        val box = ink.withAlpha(boxAlpha(roles))
        return when (r.role) {
            KitSurfaceRole.Ground -> kitStyleGround(r)
            KitSurfaceRole.Field, KitSurfaceRole.Toggle ->
                KitSurfaceLook(
                    radius = radius,
                    circle = r.circle,
                    fill = if (r.role == KitSurfaceRole.Field) roles.ground.withAlpha(0f) else (if (r.selected) r.color else null) ?: r.levelFill,
                    edgeStart = box,
                    edgeEnd = box,
                    edgeWidth = if (r.selected) CHOSEN_RULE else 1f,
                )
            KitSurfaceRole.Code ->
                KitSurfaceLook(
                    radius = radius, circle = r.circle, fill = r.color ?: r.levelFill,
                    edgeStart = if (edged) box else Clear, edgeEnd = if (edged) box else Clear, edgeWidth = if (edged) 1f else 0f,
                )
            KitSurfaceRole.Divider ->
                if (r.selected && edged) KitSurfaceLook(fill = ink.withAlpha(SECTION_ALPHA), edgeWidth = 1f) else KitSurfaceLook(fill = rule)
            KitSurfaceRole.StateLayer ->
                KitSurfaceLook(
                    radius = radius, circle = r.circle,
                    fill = if (r.pressed) mix(roles.ground, ink, pressStep(roles)) else ink.withAlpha(HOVER_ALPHA),
                )
            else -> null
        }
    }
}
