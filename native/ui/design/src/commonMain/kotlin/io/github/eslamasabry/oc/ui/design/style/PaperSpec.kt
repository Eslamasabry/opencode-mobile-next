package io.github.eslamasabry.oc.ui.design.style

import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.Roles
import io.github.eslamasabry.oc.ui.design.color.lerp
import io.github.eslamasabry.oc.ui.design.color.over
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel

/**
 * Paper: warm paper, no shadows on content, rows sit on the page between fine ink rules, controls are ink boxes, chosen
 * controls invert to ink with paper words. Port of Dart `KitStylePaper`.
 */
public object PaperSpec : StyleSpec {
    public fun boxAlpha(roles: Roles): Float = if (roles.dark) .6f else .9f
    public const val RULE_ALPHA: Float = .2f
    public const val CHOSEN_RULE: Float = 2f
    public const val SECTION_ALPHA: Float = .9f
    public const val HOVER_ALPHA: Float = .03f
    public fun pressStep(roles: Roles): Float = if (roles.dark) .10f else .07f
    public fun invertedInk(roles: Roles): Argb = roles.text1.opaque()

    private fun inverted(r: SurfaceRequest) = r.role == SurfaceRole.Control && r.selected && !r.circle

    private fun squared(r: SurfaceRequest) =
        r.role == SurfaceRole.Control && r.circle && r.knobs.corners == Corners.Sharp &&
            (r.level == SurfaceLevel.Surface2 || r.color == r.roles.accent)

    override val tokens: StyleTokens =
        StyleTokens(
            defaults = Knobs(corners = Corners.Sharp),
            sharpRadius = 0f,
            keepsUnderHighContrast = true,
            live = LivePolicy(offWhenChosen = true),
            glow = StyleGlow(halo = false, lineWidthDp = 1f),
            type = StyleType(displayWeight = 750, displayTracking = -.03f, largeTitleSize = 34, titleSize = 24, monoLabels = true, firmWeights = true),
        )

    override fun adjustRoles(roles: Roles): Roles {
        val ink = roles.text1.opaque()
        val ground =
            if (roles.dark) {
                lerp(roles.ground, ink, .02f)
            } else {
                roles.attentionFill.withAlpha(.04f).over(lerp(roles.surface2, ink, .04f))
            }
        return roles.copy(
            ground = ground,
            surface1 = ground,
            surface3 = lerp(ground, ink, if (roles.dark) .08f else .05f),
            hairline = ink.withAlpha(RULE_ALPHA),
        )
    }

    override fun onColor(r: SurfaceRequest): Argb? = if (inverted(r)) r.roles.ground.opaque() else null

    override fun resolve(r: SurfaceRequest): SurfaceDecoration {
        part(r)?.let { return it }
        val roles = r.roles
        val ink = roles.text1.withAlpha(boxAlpha(roles))
        val down = r.selected || r.pressed
        val edges = r.knobs.edges == Edges.Hairline
        val fill: Argb
        val boxed: Boolean
        when (r.role) {
            SurfaceRole.Content -> {
                val step = r.level.isStep
                fill = r.color ?: if (down || (step && !edges)) roles.surface3 else if (step) roles.ground else roles.ground.withAlpha(0f)
                boxed = edges && (step || r.selected)
            }
            SurfaceRole.Control, SurfaceRole.Track -> {
                fill = if (inverted(r)) invertedInk(roles) else r.color ?: if (down) roles.surface3 else roles.ground
                boxed = r.color == null || r.selected
            }
            SurfaceRole.Floating -> {
                fill = r.color ?: roles.ground
                boxed = true
            }
            SurfaceRole.Sheet -> {
                fill = r.color ?: r.levelFill
                boxed = edges
            }
            else -> return part(r)!!
        }
        val shadows =
            if (r.knobs.depth == Depth.Raised && r.role == SurfaceRole.Floating) listOf(floatingLift(roles)) else emptyList()
        val chosen = r.selected && r.role != SurfaceRole.Floating
        val edge = if (inverted(r)) fill else ink
        return SurfaceDecoration(
            radii = if (r.knobs.corners == Corners.Sharp) Radii.Zero else r.radii,
            circle = r.circle && !squared(r),
            fill = fill,
            edgeStart = if (boxed) edge else null,
            edgeEnd = if (boxed) edge else null,
            edgeWidthDp = if (boxed) (if (chosen && !inverted(r)) CHOSEN_RULE else 1f) else 0f,
            shadows = shadows,
        )
    }

    private fun part(r: SurfaceRequest): SurfaceDecoration? {
        val roles = r.roles
        val ink = roles.text1
        val edged = r.knobs.edges == Edges.Hairline
        val radii = if (r.knobs.corners == Corners.Sharp) Radii.Zero else r.radii
        val rule = if (edged) ink.withAlpha(RULE_ALPHA) else roles.clear()
        val box = ink.withAlpha(boxAlpha(roles))
        return when (r.role) {
            SurfaceRole.Ground -> styleGround(r)
            SurfaceRole.Field, SurfaceRole.Toggle ->
                SurfaceDecoration(
                    radii = radii,
                    circle = r.circle,
                    fill = if (r.role == SurfaceRole.Field) roles.ground.withAlpha(0f) else (if (r.selected) r.color else null) ?: r.levelFill,
                    edgeStart = box,
                    edgeEnd = box,
                    edgeWidthDp = if (r.selected) CHOSEN_RULE else 1f,
                )
            SurfaceRole.Code ->
                SurfaceDecoration(
                    radii = radii,
                    circle = r.circle,
                    fill = r.color ?: r.levelFill,
                    edgeStart = if (edged) box else null,
                    edgeEnd = if (edged) box else null,
                    edgeWidthDp = if (edged) 1f else 0f,
                )
            SurfaceRole.Divider ->
                if (r.selected && edged) SurfaceDecoration(fill = ink.withAlpha(SECTION_ALPHA), edgeWidthDp = 1f) else SurfaceDecoration(fill = rule)
            SurfaceRole.StateLayer ->
                SurfaceDecoration(
                    radii = radii,
                    circle = r.circle,
                    fill = if (r.pressed) lerp(roles.ground, ink, pressStep(roles)) else ink.withAlpha(HOVER_ALPHA),
                )
            else -> null
        }
    }
}
