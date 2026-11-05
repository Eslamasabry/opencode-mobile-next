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
        )

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

    override fun resolve(r: SurfaceRequest): SurfaceDecoration? {
        part(r)?.let { return it }
        val roles = r.roles
        val role = r.role
        val dark = roles.dark
        val white = styleLight(roles)
        val shade = styleShade(roles)
        val fill: Argb
        val fillStart: Argb
        val own = r.color
        if (own != null) {
            fill = own
            fillStart = own
        } else {
            val lift = (if (r.selected) .12f else 0f) + (if (r.pressed) .08f else 0f) // FG: lib/ui/kit/style/kit_style_glass.dart:180
            val alpha = minOf(1f, opacity(role, dark) + lift)
            val base = tintOf(roles, role, r.level)
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
        val rimless = r.knobs.edges == Edges.None && (role == SurfaceRole.Content || role == SurfaceRole.Control)
        val ringed = r.selected && r.color == null && (role == SurfaceRole.Content || role == SurfaceRole.Control)
        val (top, bottom) = rims(roles)
        val clear = Argb(0)
        return SurfaceDecoration(
            radii = r.radii,
            circle = r.circle,
            fill = fill,
            fillStart = fillStart,
            edgeStart = if (ringed) roles.accent else if (rimless) clear else top,
            edgeEnd = if (ringed) roles.accent else if (rimless) clear else bottom,
            edgeWidthDp = if (ringed) CHOSEN_RING else r.edgeWidthDp,
            shadows = shadows,
            wordsPlate = if (r.color == null) plateFor(roles, role) else clear,
            frost = if (own == null || own.withAlpha(1f) != own) frostSigma(role) else 0f,
            innerGlow = if (r.color == null) innerGlowOf(roles) else clear,
        )
    }

    private fun part(r: SurfaceRequest): SurfaceDecoration? {
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
                SurfaceDecoration(
                    radii = r.radii,
                    circle = r.circle,
                    fill = own ?: tintOf(roles, r.role, r.level).withAlpha(opacity(r.role, dark)),
                    edgeStart = own ?: edge,
                    edgeEnd = own ?: edge,
                    edgeWidthDp = r.edgeWidthDp,
                    wordsPlate = if (own == null) plateFor(roles, r.role) else clear,
                    frost = frostSigma(r.role),
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
