package io.github.eslamasabry.oc.ui.design.style

import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.Roles
import io.github.eslamasabry.oc.ui.design.color.lerp

/**
 * Solid glass (design draft, B-427): Solid's own opaque fills with a fine finish, as a design system reads as material
 * up close: a one-pixel top-edge light, a hairline border, and, on big panes only, a very slight top-to-bottom thickness
 * and a calm shade; plus a pressed-in answer ([KitPressedInIndication][io.github.eslamasabry.oc.ui.design.style.SolidGlassSpec.PRESSED_SHADE_DARK]).
 * No blur, no backdrop, nothing time-driven: every number is a colour or a shadow drawn once per change.
 *
 * Big or small: a pane is BIG when it is a top-level card, a floating pane (bar, message box) or a sheet, not nested in
 * another outlined pane, not a part that draws its own face (bubbles, menus, code), not a circle or a pill. Only big panes
 * get the gradient and the shade (review 2026-11-17: on small, nested and inline parts they read as grey plastic in light).
 * Small parts keep the hairline and the top-edge light. The shade needs Depth Raised (the style's default; Flat, which
 * high contrast forces, draws none).
 *
 * The fill is the part's own ([StyleTokens.keepsOwnFills]); a part with no fill of its own, the page ground, switches,
 * dividers and the state layer keep Solid's look (null).
 */
public object SolidGlassSpec : StyleSpec {
    // Depth defaults to Raised (the shade is the style's depth); Edges stay Hairline, so `dependsOnDepth` (a style whose
    // shape is its shadows) is not needed: a Flat Solid glass keeps its hairline and loses only the shade.
    override val tokens: StyleTokens =
        StyleTokens(defaults = Knobs(depth = Depth.Raised), keepsNestedSurfaces = true, flatLists = true, keepsOwnFills = true, dressesOwnLooks = true)

    /** The top-edge light: the theme's near-white at this alpha (dark, light). */
    public const val TOP_LIGHT_DARK: Float = .20f
    public const val TOP_LIGHT_LIGHT: Float = .90f

    /** The pressed face keeps this share of its top-edge light. */
    public const val TOP_LIGHT_PRESSED_SHARE: Float = .5f

    /** The hairline: the theme's near-white (dark) or ink (light) at this alpha. */
    public const val HAIRLINE_DARK: Float = .08f
    public const val HAIRLINE_LIGHT: Float = .10f

    /** A big pane's thickness: its bottom shaded toward the theme's shade (dark, light), its top lifted toward the light (dark). */
    public const val BOTTOM_DARK: Float = .10f
    public const val BOTTOM_LIGHT: Float = .02f
    public const val TOP_LIFT_DARK: Float = .025f

    /** The shade: contact and ambient alphas (dark, light); a card's ambient is [CARD_AMBIENT_SHARE] of a floating pane's. */
    public const val CONTACT_DARK: Float = .40f
    public const val CONTACT_LIGHT: Float = .08f
    public const val AMBIENT_DARK: Float = .30f
    public const val AMBIENT_LIGHT: Float = .09f
    public const val CARD_AMBIENT_SHARE: Float = .7f

    /** A pressed big pane's face is this far toward the shade. */
    public const val PRESSED_DARKEN: Float = .04f

    /** The pressed-in answer (indication): a shade along the inside of the top edge (alpha dark, light), in dp, and the face sink. */
    public const val PRESSED_SHADE_DARK: Float = .16f
    public const val PRESSED_SHADE_LIGHT: Float = .05f
    public const val PRESSED_BAND_DP: Float = 3f
    public const val PRESSED_SINK_DP: Float = 1f

    /** A radius at or above this is a pill or a circle: small. */
    private const val PILL_DP: Float = 100f

    private fun Roles.light(): Argb = styleLight(this)

    private fun contact(roles: Roles): Shadow = Shadow(0f, 1f, 2f, styleShade(roles).withAlpha(if (roles.dark) CONTACT_DARK else CONTACT_LIGHT))

    private fun ambient(roles: Roles, share: Float = 1f): Shadow =
        Shadow(0f, 8f, 20f, styleShade(roles).withAlpha((if (roles.dark) AMBIENT_DARK else AMBIENT_LIGHT) * share), spreadDp = -8f)

    /** Whether [r] is a big pane (see the class comment). */
    private fun big(r: SurfaceRequest): Boolean =
        !r.nested && !r.dressed && !r.circle && r.radii.topStart < PILL_DP &&
            // A card is a Content pane on the first surface step; a tonal step (a bubble, an inline fill) is small.
            ((r.role == SurfaceRole.Content && r.level == io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel.Surface1) || r.role == SurfaceRole.Floating || r.role == SurfaceRole.Sheet)

    override fun resolve(r: SurfaceRequest): SurfaceDecoration? {
        val roles = r.roles
        when (r.role) {
            SurfaceRole.Ground, SurfaceRole.Toggle, SurfaceRole.Divider, SurfaceRole.StateLayer -> return null
            else -> Unit
        }
        val own = r.color ?: (if (r.dressed) r.levelFill else return null)
        if (own.alpha == 0) return null
        val dark = roles.dark
        val accent = own.opaque() == roles.accent.opaque()
        val shade = styleShade(roles)
        val a = own.alpha / 255f
        val base = own.opaque()
        val down = r.pressed
        val big = big(r)
        val pressedFace: (Argb) -> Argb = { if (down) lerp(it, shade, PRESSED_DARKEN) else it }
        val top = pressedFace(if (big && dark && !accent) lerp(base, roles.light(), TOP_LIFT_DARK) else base)
        val bottom = if (big) pressedFace(lerp(base, shade, if (dark) BOTTOM_DARK else BOTTOM_LIGHT)) else null
        val edges = r.knobs.edges == Edges.Hairline
        val hairline = if (dark) roles.light().withAlpha(HAIRLINE_DARK) else roles.text1.opaque().withAlpha(HAIRLINE_LIGHT)
        val raised = r.knobs.depth == Depth.Raised
        val shadows =
            when {
                !raised || !big || down || r.disabled || r.role == SurfaceRole.Sheet -> emptyList()
                r.role == SurfaceRole.Floating -> listOf(contact(roles), ambient(roles))
                else -> listOf(contact(roles), ambient(roles, CARD_AMBIENT_SHARE))
            }
        // An accent fill takes the light only in dark: a white line on a light theme's accent edge reads jagged.
        val lit = !accent || dark
        val light = if (lit) roles.light().withAlpha((if (dark) TOP_LIGHT_DARK else TOP_LIGHT_LIGHT) * (if (down) TOP_LIGHT_PRESSED_SHARE else 1f)) else Argb(0)
        return SurfaceDecoration(
            radii = r.radii,
            circle = r.circle,
            fill = top.withAlpha(a),
            fillEnd = bottom?.withAlpha(a),
            edgeStart = if (edges && !accent) hairline else null,
            edgeEnd = if (edges && !accent) hairline else null,
            edgeWidthDp = if (edges && !accent) r.edgeWidthDp else 0f,
            shadows = shadows,
            faceShiftDp = if (down) PRESSED_SINK_DP else 0f,
            topLight = light,
        )
    }
}
