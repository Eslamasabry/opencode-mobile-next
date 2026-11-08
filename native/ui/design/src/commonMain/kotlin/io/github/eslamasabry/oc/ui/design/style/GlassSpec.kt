package io.github.eslamasabry.oc.ui.design.style

import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.Roles
import io.github.eslamasabry.oc.ui.design.color.lerp
import io.github.eslamasabry.oc.ui.design.color.over
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel

/**
 * Glass (glassmorphism): frosted panes over moving colour, restored from GLASS_REF `1bbdc3f9c`. Every pane blurs what
 * lies behind it (the decoration's [SurfaceDecoration.frost], painted by order 34), with a smoky (dark) or milky (light)
 * tint, a top-edge glow, a soft light rim and a soft long shadow. Port of Dart `KitStyleGlass`.
 */
public object GlassSpec : StyleSpec {
    override val tokens: StyleTokens =
        StyleTokens(
            defaults = Knobs(),
            blursBehind = true,
            type = StyleType(weightBoost = WEIGHT_BOOST),
            live = LivePolicy(fillsMiddle = true, lookWhenChosen = "auroraImage"),
            // Owner 2026-11-09, "good inside padding to let glass effects show": the content sits clear of the rim, the
            // sheen and the lens, with corners grown to stay concentric.
            paneInsetDp = PANE_INSET,
            paneCornerBoostDp = PANE_CORNER_BOOST,
        )

    /** The extra inner inset of a Glass pane beyond its own padding, in dp ([StyleTokens.paneInsetDp]). */
    public const val PANE_INSET: Float = 6f

    /** How much a Glass pane's corners grow with [PANE_INSET] ([StyleTokens.paneCornerBoostDp]). */
    public const val PANE_CORNER_BOOST: Float = 4f

    /** The accent ring around a chosen pane, in dp. */
    public const val CHOSEN_RING: Float = 2f // FG: lib/ui/kit/style/kit_style_glass.dart:46

    /** What every word gains in weight under Glass (hook H1). */
    public const val WEIGHT_BOOST: Float = 50f // FG: lib/ui/kit/style/kit_style_glass.dart:49

    /** How much more of the near-white the frost catches at a pane's top start. */
    public const val SHEEN: Float = .08f // FG: lib/ui/kit/style/kit_style_glass.dart:91

    /** The background blur behind a card, a row group, a chip or a pill (sigma, dp). */
    public const val PANE_FROST: Float = 20f // FG: lib/ui/kit/style/kit_style_glass.dart:96

    /** The blur behind a floating piece or a sheet. */
    public const val FLOAT_FROST: Float = 26f // FG: lib/ui/kit/style/kit_style_glass.dart:100

    /**
     * Clear panes (owner 2026-11-08, option 1: "keep liquid for floating pieces only, and make the in-flow Glass panes
     * much lighter frosted glass"): the background blur behind a card or a row group when the panes are clear. A big
     * pane carries a little more frost than a chip or a field ([PANE_FROST]).
     */
    public const val CLEAR_CONTENT_FROST: Float = 12f

    /**
     * The blur behind a clear chip, control or field (owner 2026-11-09, "crisper": moderate blur, so colour and shapes
     * behind stay visible; the frost's saturation carries the vibrancy).
     */
    public const val CLEAR_PANE_FROST: Float = 10f

    /**
     * The accent a chosen pane's tint takes (dark, light): accent-tinted frost, never a solid slab. A dark chosen pane
     * is the page's own smoke with the accent in it (a lifted grey with accent would wash out light words), a light one
     * the milk with a little accent (more would grey out dark words).
     */
    public const val CHOSEN_TINT_DARK: Float = .20f
    public const val CHOSEN_TINT_LIGHT: Float = .12f

    /**
     * A chosen pane's opaque tint: the accent in the ground's smoke (dark) or in the theme's milk (light; never a grey
     * surface step, under which dark words fell back to an opaque face). [plain] is the pane's own tint, kept for the
     * callers that lean a tint they already have.
     */
    public fun chosenTintOf(roles: Roles, @Suppress("UNUSED_PARAMETER") plain: Argb): Argb {
        val base = if (roles.dark) roles.ground.opaque() else styleLight(roles)
        val most = if (roles.dark) CHOSEN_TINT_DARK else CHOSEN_TINT_LIGHT
        // As much accent as the words allow on the tint itself (an accent close to the words' own lightness, as in
        // Solarized's light theme, gets less): text1/2/3 keep their targets on the opaque tint, so the pane can stay
        // translucent over the page.
        val inks = GlassFinish.textInks(roles, roles.surface1.over(roles.ground.opaque()))
        for (step in CHOSEN_STEPS downTo 0) {
            val tint = lerp(base, roles.accent.opaque(), most * step / CHOSEN_STEPS).opaque()
            if (inks.all { (ink, target) -> contrast(ink, tint) >= target + CHOSEN_MARGIN }) return tint
        }
        return base
    }

    /** The accent steps [chosenTintOf] tries, from the most down to none. */
    private const val CHOSEN_STEPS: Int = 4

    /** The room the chosen tint leaves over its target, so the pane's own translucency can still read. */
    private const val CHOSEN_MARGIN: Double = .25

    /** How much thicker a chosen pane's tint is than its resting one. */
    public const val CHOSEN_LIFT: Float = .10f

    /** The accent rim light round a chosen pane, in dp (the kit lights it from the accent): soft, never a thick border. */
    public const val CHOSEN_RIM: Float = 1.5f

    /** The in-flow roles that become clear panes; floating parts and sheets are liquid glass or keep their frost. */
    public fun clears(role: SurfaceRole): Boolean =
        role == SurfaceRole.Content || role == SurfaceRole.Control || role == SurfaceRole.Track || role == SurfaceRole.Field

    /**
     * How opaque a clear in-flow pane's tint is (owner 2026-11-08: much lighter, so the background's colour clearly
     * shows through, as in Apple's materials). These are where the tint starts: the kit thickens it only as far as
     * text1/2/3 need to read over the ground and the frosted picture ([GlassFinish.thickened]); no plate behind words
     * (owner 2026-11-08: "get rid of the haze behind the text").
     */
    public fun clearOpacity(role: SurfaceRole, dark: Boolean): Float =
        when (role) {
            SurfaceRole.Content -> if (dark) .16f else .22f
            SurfaceRole.Control -> if (dark) .20f else .24f
            SurfaceRole.Track -> if (dark) .14f else .20f
            SurfaceRole.Field -> if (dark) .18f else .24f
            else -> opacity(role, dark)
        }

    /** The blur behind a clear [role] pane. */
    public fun clearFrostSigma(role: SurfaceRole): Float =
        when (role) {
            SurfaceRole.Content -> CLEAR_CONTENT_FROST
            SurfaceRole.Control, SurfaceRole.Track, SurfaceRole.Field -> CLEAR_PANE_FROST
            else -> frostSigma(role)
        }

    /**
     * A clear pane's top sheen (owner 2026-11-09, "glossy"): the theme's near-white, fading out over the first 30 % of
     * the pane and at most [SurfaceDecoration.SHEEN_MAX_DP].
     */
    public fun sheenOf(roles: Roles): Argb = styleLight(roles).withAlpha(if (roles.dark) .10f else .34f)

    /**
     * A clear pane's specular light ([SurfaceDecoration.specular]): white, a bright segment of the 1 dp rim at the top
     * start and a thin line just inside the top edge. Restrained: strong enough to catch the eye, never a white frame.
     */
    public fun specularOf(roles: Roles): Argb = styleLight(roles).withAlpha(if (roles.dark) SPECULAR_DARK else SPECULAR_LIGHT)

    public const val SPECULAR_DARK: Float = .55f
    public const val SPECULAR_LIGHT: Float = .90f

    /**
     * Whether [r] is a chosen pane drawn as accent-tinted glass: a chosen content or control surface with no fill of its
     * own, or with a surface step as its own (Solid's way to show chosen: a raised step; under Glass that step was an
     * opaque slab). An accent or other fill of a part's own stays as it is.
     */
    public fun chosen(r: SurfaceRequest): Boolean {
        if (!r.selected || (r.role != SurfaceRole.Content && r.role != SurfaceRole.Control)) return false
        val own = r.color ?: return true
        if (own.alpha != 255) return false
        val roles = r.roles
        return listOf(roles.ground, roles.surface1, roles.surface2, roles.surface3).any { it.opaque() == own }
    }

    /** How opaque a glass pane's tint is, per role and brightness. */
    public fun opacity(role: SurfaceRole, dark: Boolean): Float =
        when (role) {
            SurfaceRole.Content -> if (dark) .42f else .50f // FG: lib/ui/kit/style/kit_style_glass.dart:58
            SurfaceRole.Control -> if (dark) .48f else .56f // FG: lib/ui/kit/style/kit_style_glass.dart:59
            SurfaceRole.Track -> if (dark) .38f else .46f // FG: lib/ui/kit/style/kit_style_glass.dart:60
            SurfaceRole.Floating -> if (dark) .50f else .62f // FG: lib/ui/kit/style/kit_style_glass.dart:61
            SurfaceRole.Sheet -> if (dark) .58f else .72f // FG: lib/ui/kit/style/kit_style_glass.dart:62
            SurfaceRole.Field -> .5f // FG: lib/ui/kit/style/kit_style_glass.dart:64
            SurfaceRole.Toggle -> if (dark) .28f else .38f // FG: lib/ui/kit/style/kit_style_glass.dart:65
            SurfaceRole.Ground, SurfaceRole.Code, SurfaceRole.Divider, SurfaceRole.StateLayer -> 1f
        }

    /** How much of the theme's near-white a dark pane's tint takes. */
    public fun frostOf(role: SurfaceRole): Float =
        when (role) {
            SurfaceRole.Content, SurfaceRole.Track -> .07f // FG: lib/ui/kit/style/kit_style_glass.dart:78
            SurfaceRole.Control, SurfaceRole.Field, SurfaceRole.Toggle -> .08f // FG: lib/ui/kit/style/kit_style_glass.dart:81
            SurfaceRole.Floating, SurfaceRole.Sheet -> .06f // FG: lib/ui/kit/style/kit_style_glass.dart:82
            SurfaceRole.Ground, SurfaceRole.Code, SurfaceRole.Divider, SurfaceRole.StateLayer -> 0f
        }

    /** The blur [role]'s pane asks for; 0 for a surface that is not a pane. */
    public fun frostSigma(role: SurfaceRole): Float =
        when (role) {
            SurfaceRole.Content, SurfaceRole.Control, SurfaceRole.Track, SurfaceRole.Field -> PANE_FROST
            SurfaceRole.Floating, SurfaceRole.Sheet -> FLOAT_FROST
            SurfaceRole.Toggle, SurfaceRole.Ground, SurfaceRole.Code, SurfaceRole.Divider, SurfaceRole.StateLayer -> 0f
        }

    /** The glow along a pane's top edge, inside it. */
    public fun innerGlowOf(roles: Roles): Argb =
        styleLight(roles).withAlpha(if (roles.dark) .10f else .55f) // FG: lib/ui/kit/style/kit_style_glass.dart:120

    /** How far the plate leans from the pane towards the theme's shade (dark) or white (light). */
    public fun plateShadeOf(role: SurfaceRole): Float =
        when (role) {
            SurfaceRole.Content, SurfaceRole.Track -> .38f // FG: lib/ui/kit/style/kit_style_glass.dart:130
            SurfaceRole.Control, SurfaceRole.Field, SurfaceRole.Toggle -> .6f // FG: lib/ui/kit/style/kit_style_glass.dart:133
            SurfaceRole.Floating, SurfaceRole.Sheet -> .52f // FG: lib/ui/kit/style/kit_style_glass.dart:134
            SurfaceRole.Ground, SurfaceRole.Code, SurfaceRole.Divider, SurfaceRole.StateLayer -> .4f // FG: lib/ui/kit/style/kit_style_glass.dart:138
        }

    /** The plate behind the words on a [role] pane (hook H10). */
    public fun plateFor(roles: Roles, role: SurfaceRole): Argb {
        val dark = roles.dark
        val level =
            when (role) {
                SurfaceRole.Control, SurfaceRole.Field, SurfaceRole.Toggle -> SurfaceLevel.Surface3
                SurfaceRole.Floating, SurfaceRole.Sheet -> SurfaceLevel.Surface2
                else -> SurfaceLevel.Surface1
            }
        val pane = tintOf(roles, role, level).withAlpha(opacity(role, dark)).over(roles.ground.opaque())
        val towards = if (dark) styleShade(roles) else styleLight(roles)
        return lerp(pane, towards, plateShadeOf(role))
    }

    override fun resolve(r: SurfaceRequest): SurfaceDecoration? = build(r, clear = false)

    /**
     * [r] as a clear in-flow pane (owner 2026-11-08): [clearOpacity]'s thin tint, [clearFrostSigma]'s blur and a top
     * [sheenOf]; the kit lights its rim from the accent and thickens the tint until the words read
     * ([GlassFinish.thickened]). Floating parts, sheets and every role that is not a pane resolve as [resolve] does.
     */
    public fun resolveClear(r: SurfaceRequest): SurfaceDecoration? = build(r, clear = clears(r.role))

    private fun build(r: SurfaceRequest, clear: Boolean): SurfaceDecoration? {
        part(r, clear)?.let { return it }
        val roles = r.roles
        val role = r.role
        val dark = roles.dark
        val white = styleLight(roles)
        val shade = styleShade(roles)
        val fill: Argb
        val fillStart: Argb
        val chosen = chosen(r)
        val own = if (chosen) null else r.color
        if (own != null) {
            fill = own
            fillStart = own
        } else {
            // A chosen pane is accent-tinted frost a little thicker than its neighbours (owner 2026-11-08), not Flutter's
            // .12 lift of the plain tint inside a thick accent ring.
            val lift = (if (chosen) CHOSEN_LIFT else if (r.selected) .12f else 0f) + (if (r.pressed) .08f else 0f) // FG: lib/ui/kit/style/kit_style_glass.dart:180
            val alpha = minOf(1f, (if (clear) clearOpacity(role, dark) else opacity(role, dark)) + lift)
            val plain = tintOf(roles, role, r.level)
            val base = if (chosen) chosenTintOf(roles, plain) else plain
            fill = base.withAlpha(alpha)
            fillStart = lerp(base, white, if (dark) SHEEN else SHEEN * 2).withAlpha(minOf(1f, alpha + (if (dark) .04f else .12f))) // FG: lib/ui/kit/style/kit_style_glass.dart:188,189
        }
        val raised = r.knobs.depth == Depth.Raised
        val shadows =
            when (role) {
                SurfaceRole.Floating, SurfaceRole.Sheet ->
                    listOf(Shadow(0f, 12f, 32f, if (dark) roles.elevationShadow else shade.withAlpha(.14f), spreadDp = -8f)) // FG: lib/ui/kit/style/kit_style_glass.dart:196,197,198,199
                SurfaceRole.Content ->
                    listOf(
                        Shadow(
                            0f,
                            if (raised) 16f else 12f, // FG: lib/ui/kit/style/kit_style_glass.dart:209
                            if (raised) 40f else 32f, // FG: lib/ui/kit/style/kit_style_glass.dart:207
                            shade.withAlpha(if (dark) (if (raised) .40f else .28f) else (if (raised) .16f else .11f)), // FG: lib/ui/kit/style/kit_style_glass.dart:205
                            spreadDp = -8f, // FG: lib/ui/kit/style/kit_style_glass.dart:208
                        ),
                    )
                else -> emptyList()
            }
        // A clear pane is crisp (owner 2026-11-09): no soft shadow halo round it, no soft glow inside its top; the sheen
        // and the specular carry its gloss.
        val crisp = clear && own == null
        val rimless = r.knobs.edges == Edges.None && (role == SurfaceRole.Content || role == SurfaceRole.Control)
        val (top, bottom) = rims(roles)
        val none = Argb(0)
        val plain = own == null
        return SurfaceDecoration(
            radii = r.radii,
            circle = r.circle,
            fill = fill,
            fillStart = fillStart,
            edgeStart = if (chosen) roles.accent else if (rimless) none else top,
            edgeEnd = if (chosen) roles.accent else if (rimless) none else bottom,
            edgeWidthDp = if (chosen) CHOSEN_RIM else r.edgeWidthDp,
            shadows = if (clear) emptyList() else shadows,
            wordsPlate = if (plain) plateFor(roles, role) else none,
            frost = if (own == null || own.withAlpha(1f) != own) (if (clear) clearFrostSigma(role) else frostSigma(role)) else 0f,
            innerGlow = if (plain && !crisp) innerGlowOf(roles) else none,
            sheen = if (clear && plain) sheenOf(roles) else none,
            specular = if (crisp) specularOf(roles) else none,
        )
    }

    private fun part(r: SurfaceRequest, clearPane: Boolean): SurfaceDecoration? {
        val roles = r.roles
        val dark = roles.dark
        val clear = Argb(0)
        return when (r.role) {
            SurfaceRole.Ground -> styleGround(r)
            SurfaceRole.Divider -> SurfaceDecoration(fill = roles.hairline)
            SurfaceRole.StateLayer -> {
                val lift = if (dark) (if (r.pressed) .10f else .06f) else (if (r.pressed) .42f else .28f) // FG: lib/ui/kit/style/kit_style_glass.dart:269
                SurfaceDecoration(radii = r.radii, circle = r.circle, fill = styleLight(roles).withAlpha(lift))
            }
            SurfaceRole.Code -> {
                val (top, bottom) = rims(roles)
                val fill = r.color ?: roles.surface1
                SurfaceDecoration(
                    radii = r.radii,
                    circle = r.circle,
                    fill = fill.over(roles.ground.opaque()),
                    edgeStart = top,
                    edgeEnd = bottom,
                    edgeWidthDp = r.edgeWidthDp,
                )
            }
            SurfaceRole.Field, SurfaceRole.Toggle -> {
                val on = r.selected && r.role == SurfaceRole.Toggle
                val own = if (on) r.color else null
                val edge = if (r.selected) roles.accent else styleBoundary(roles)
                val thin = clearPane && r.role == SurfaceRole.Field
                SurfaceDecoration(
                    radii = r.radii,
                    circle = r.circle,
                    fill = own ?: tintOf(roles, r.role, r.level).withAlpha(if (thin) clearOpacity(r.role, dark) else opacity(r.role, dark)),
                    edgeStart = own ?: edge,
                    edgeEnd = own ?: edge,
                    edgeWidthDp = r.edgeWidthDp,
                    wordsPlate = if (own == null) plateFor(roles, r.role) else clear,
                    frost = if (thin) clearFrostSigma(r.role) else frostSigma(r.role),
                )
            }
            SurfaceRole.Content, SurfaceRole.Control, SurfaceRole.Track, SurfaceRole.Floating, SurfaceRole.Sheet -> null
        }
    }

    /** The pane's 1 px soft light rim: brightest along the top start, fading towards the bottom end. */
    public fun rims(roles: Roles): Pair<Argb, Argb> {
        val white = styleLight(roles)
        val shade = styleShade(roles)
        return if (roles.dark) {
            white.withAlpha(.30f) to white.withAlpha(.08f) // FG: lib/ui/kit/style/kit_style_glass.dart:324
        } else {
            white.withAlpha(.95f) to shade.withAlpha(.10f) // FG: lib/ui/kit/style/kit_style_glass.dart:325
        }
    }

    /** Words on a chosen control or a secondary button (hook H3): the primary ink; a part's own fill keeps its own words. */
    override fun onColor(r: SurfaceRequest): Argb? = if (r.color == null) r.roles.text1 else null

    /** Under a glass sheet the page dims less (hook H6). */
    override fun adjustRoles(roles: Roles): Roles =
        roles.copy(scrim = styleShade(roles).withAlpha(if (roles.dark) .24f else .10f)) // FG: lib/ui/kit/style/kit_style_glass.dart:338

    /** The colour a pane is tinted with, opaque. */
    public fun tintOf(roles: Roles, role: SurfaceRole, level: SurfaceLevel): Argb {
        if (!roles.dark) {
            return when (role) {
                SurfaceRole.Control, SurfaceRole.Track -> step(roles, level)
                else -> styleLight(roles)
            }
        }
        val lifted =
            when (level) {
                SurfaceLevel.Ground -> roles.surface1
                SurfaceLevel.Surface1 -> roles.surface2
                SurfaceLevel.Surface2, SurfaceLevel.Surface3 -> roles.surface3
            }
        return lerp(lifted.opaque(), styleLight(roles), frostOf(role)).opaque()
    }

    private fun step(roles: Roles, level: SurfaceLevel): Argb =
        when (level) {
            SurfaceLevel.Ground -> roles.ground
            SurfaceLevel.Surface1 -> roles.surface1
            SurfaceLevel.Surface2 -> roles.surface2
            SurfaceLevel.Surface3 -> roles.surface3
        }.opaque()
}
