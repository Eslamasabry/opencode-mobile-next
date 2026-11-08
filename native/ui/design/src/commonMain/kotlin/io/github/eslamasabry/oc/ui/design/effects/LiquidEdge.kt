package io.github.eslamasabry.oc.ui.design.effects

import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.Roles
import io.github.eslamasabry.oc.ui.design.color.over
import io.github.eslamasabry.oc.ui.design.color.wcagContrast
import io.github.eslamasabry.oc.ui.design.style.GlassFinish
import io.github.eslamasabry.oc.ui.design.style.GlassSpec
import io.github.eslamasabry.oc.ui.design.style.Knobs
import io.github.eslamasabry.oc.ui.design.style.Radii
import io.github.eslamasabry.oc.ui.design.style.SurfaceDecoration
import io.github.eslamasabry.oc.ui.design.style.SurfaceRequest
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Glass's liquid edges (docs/designs/liquid-glass.md v2 + owner decision 2026-11-08: "go full blown glass"). Under the
 * Glass style the floating parts are glass over the content behind them: the content, pressed together in contrast and
 * blurred a little, is bent only in a rim at the pane's edge (no depth effect, no dispersion), under today's Glass tint
 * and words plate (thickened by GlassFinish until text1/2/3 read over any content), with a soft rim light tinted from the
 * accent. When it is drawn is FrostPolicy's (`edgesHeldBy`). This file holds the numbers; the kit paints them.
 */

/**
 * The floating parts that become liquid glass, highest precedence first: when a window has more of them than
 * [LiquidEdge.BUDGET] allows, the lowest drop to frost first (a menu over a sheet over the page; the composer above the
 * bars; the bars above the floating + and the pills).
 */
public enum class LiquidRole(public val rank: Int) {
    Menu(7),
    Sheet(6),
    Composer(5),
    Bar(4),
    Action(3),
    Pill(2),
    Status(1),
    ;

    /** A sheet or a menu: it covers the page, so the page's own glass under it drops to frost while it shows. */
    public val modal: Boolean get() = this == Menu || this == Sheet
}

/**
 * One role's numbers. The backdrop is pressed together first ([saturation], [contrast], [brightness]: the library's
 * `colorControls` matrix, as [LiquidEdge.filtered] applies it), blurred by [blurDp], then bent by [refractionDp] inside
 * the rim ([LiquidEdge.rimPx]); [shadowAlpha] is the soft shadow under the pane.
 */
public data class LiquidRecipe(
    val saturation: Float,
    val contrast: Float,
    val brightness: Float,
    val blurDp: Float,
    val refractionDp: Float,
    val shadowAlpha: Float,
)

public object LiquidEdge {
    /** The rim the lens bends in: at most this many dp ... */
    public const val RIM_MAX_DP: Float = 12f

    /** ... and at most this share of the pane's shorter side, so the middle, where words and the caret sit, stays calm. */
    public const val RIM_SHARE: Float = .20f

    /**
     * The liquid surfaces one window may hold at once. A pane costs 1; a page the kit records for panes over it costs 1,
     * once however many panes share it (a feature's list or transcript recording costs nothing here: it records in
     * every style). See [grant].
     */
    public const val BUDGET: Int = 4

    /** How much colour the glass lifts from what lies behind it (the library's vibrancy is 1.5). */
    public const val SATURATION: Float = 1.3f

    /** How far the glass presses the backdrop's contrast together (1 keeps it). */
    public const val PRESS: Float = .28f

    /**
     * How far the pressed backdrop moves towards the theme: darker in a dark theme, lighter in a light one. Dark goes
     * further: text3 must keep 4.5:1 over a white page behind a dark pane, and a darker backdrop needs less tint.
     */
    public const val PRESS_LIFT_DARK: Float = .48f
    public const val PRESS_LIFT_LIGHT: Float = .40f

    /** The rim width the lens bends in, for a pane whose shorter side is [shortSidePx], at [density] px per dp. */
    public fun rimPx(shortSidePx: Float, density: Float): Float = minOf(RIM_MAX_DP * density, RIM_SHARE * shortSidePx)

    /** The recipe for [role] in a [dark] or light theme: thin bars bend gently; sheets and menus blur more (more words). */
    public fun recipe(role: LiquidRole, dark: Boolean): LiquidRecipe {
        val lift = if (dark) -PRESS_LIFT_DARK else PRESS_LIFT_LIGHT
        return when (role) {
            LiquidRole.Bar -> LiquidRecipe(SATURATION, PRESS, lift, 4f, 14f, .10f)
            LiquidRole.Composer -> LiquidRecipe(SATURATION, PRESS, lift, 4f, 14f, .12f)
            LiquidRole.Sheet -> LiquidRecipe(SATURATION, PRESS, lift, if (dark) 10f else 14f, 18f, 0f)
            LiquidRole.Menu -> LiquidRecipe(SATURATION, PRESS, lift, if (dark) 8f else 12f, 16f, .16f)
            LiquidRole.Pill, LiquidRole.Status -> LiquidRecipe(SATURATION, PRESS, lift, 2f, 10f, .14f)
            LiquidRole.Action -> LiquidRecipe(1.2f, .40f, if (dark) -.10f else .10f, 2f, 12f, .20f)
        }
    }

    /** The Glass surface role and level whose tint and words plate a liquid [role] pane keeps (hook H10). */
    public fun glassRole(role: LiquidRole): Pair<SurfaceRole, SurfaceLevel> =
        when (role) {
            LiquidRole.Sheet, LiquidRole.Menu -> SurfaceRole.Sheet to SurfaceLevel.Surface2
            LiquidRole.Composer, LiquidRole.Bar -> SurfaceRole.Floating to SurfaceLevel.Surface2
            LiquidRole.Pill, LiquidRole.Status, LiquidRole.Action -> SurfaceRole.Floating to SurfaceLevel.Surface3
        }

    /** The opaque Solid surface a [role] pane stands for: Glass is never asked to read better than Solid does. */
    public fun solidOf(roles: Roles, role: LiquidRole): Argb {
        val ground = roles.ground.opaqueArgb()
        return when (role) {
            LiquidRole.Bar -> ground
            LiquidRole.Composer, LiquidRole.Sheet, LiquidRole.Menu -> roles.surface2.over(ground)
            LiquidRole.Pill, LiquidRole.Status -> roles.surface3.over(ground)
            LiquidRole.Action -> roles.accent.over(ground)
        }
    }

    /**
     * The least tint a [role] pane has, whatever GlassFinish needs: sheets and menus hold the most words (lists, long
     * prose), so their glass stays a little thicker than a bar's.
     */
    public fun tintFloor(role: LiquidRole, dark: Boolean): Float =
        when (role) {
            LiquidRole.Sheet -> if (dark) .62f else .76f
            LiquidRole.Menu -> if (dark) .56f else .70f
            LiquidRole.Action -> .78f
            else -> 0f
        }

    /**
     * [c] through the recipe's colour controls, as the library's `colorControls` matrix applies them (sRGB channels;
     * saturation about luma 0.213/0.715/0.072, then contrast about mid grey, then brightness).
     */
    public fun filtered(c: Argb, recipe: LiquidRecipe): Argb {
        val r = c.red / 255f
        val g = c.green / 255f
        val b = c.blue / 255f
        val inv = 1f - recipe.saturation
        val luma = .213f * inv * r + .715f * inv * g + .072f * inv * b
        val shift = .5f - recipe.contrast * .5f + recipe.brightness
        fun ch(x: Float): Int = ((recipe.contrast * (luma + recipe.saturation * x) + shift).coerceIn(0f, 1f) * 255f).roundToInt()
        return Argb((0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b))
    }

    /**
     * The darkest and the brightest colour any content can show through the glass: the controls are affine per channel,
     * so each channel stays between its values at the RGB cube's corners; the blur and the lens only mix colours, so
     * every colour under the pane lies in that box, and its luminance between the box's two ends.
     */
    public fun backdropEnds(recipe: LiquidRecipe): Pair<Argb, Argb> {
        val corners =
            listOf(0x000000, 0xFFFFFF, 0xFF0000, 0x00FF00, 0x0000FF, 0x00FFFF, 0xFF00FF, 0xFFFF00)
                .map { filtered(Argb(it or (0xFF shl 24)), recipe) }
        fun box(pick: (List<Int>) -> Int): Argb =
            Argb((0xFF shl 24) or (pick(corners.map { it.red }) shl 16) or (pick(corners.map { it.green }) shl 8) or pick(corners.map { it.blue }))
        return box { it.min() } to box { it.max() }
    }

    /**
     * The face a liquid [role] pane lays over its backdrop: today's Glass tint and words plate for the role
     * ([GlassSpec]), thickened by [GlassFinish] until text1, text2 and text3 read over the ground and over both ends of
     * any backdrop ([backdropEnds]), at least the role's [tintFloor]; no rims and no top glow (the rim light replaces
     * them). The + is the accent at its floor, thickened until its glyph keeps 3:1.
     */
    public fun face(roles: Roles, role: LiquidRole, radii: Radii = Radii.Zero): SurfaceDecoration {
        val recipe = recipe(role, roles.dark)
        val (darkEnd, brightEnd) = backdropEnds(recipe)
        val ground = roles.ground.opaqueArgb()
        if (role == LiquidRole.Action) {
            var a = tintFloor(role, roles.dark)
            val accent = roles.accent.opaqueArgb()
            val target = minOf(GlassFinish.FLOOR - 1.5, wcagContrast(roles.onAccent, accent))
            fun reads(alpha: Float) = listOf(darkEnd, brightEnd).all { wcagContrast(roles.onAccent, accent.withAlpha(alpha).over(it)) >= target - 1e-6 }
            while (a < 1f && !reads(a)) a = minOf(1f, a + .02f)
            return SurfaceDecoration(radii = radii, circle = true, fill = accent.withAlpha(a), wordsPlate = Argb(0))
        }
        val (glass, level) = glassRole(role)
        val request = SurfaceRequest(roles = roles, role = glass, knobs = Knobs(), level = level, radii = radii)
        val resolved = GlassSpec.resolve(request) ?: SurfaceDecoration(radii = radii, fill = solidOf(roles, role))
        val floor = tintFloor(role, roles.dark)
        val floored =
            if (floor <= 0f) resolved
            else resolved.copy(
                fill = resolved.fill?.let { if (it.alpha / 255f < floor) it.withAlpha(floor) else it },
                fillStart = resolved.fillStart?.let { if (it.alpha / 255f < floor) it.withAlpha(floor) else it },
            )
        val thick = GlassFinish.thickened(floored, roles, solidOf(roles, role), listOf(ground, darkEnd, brightEnd))
        return thick.copy(edgeWidthDp = 0f, edgeStart = null, edgeEnd = null, innerGlow = Argb(0), shadows = emptyList(), frost = 0f)
    }

    /**
     * The lowest contrast any of text1/2/3 has on [role]'s face over the ground and both backdrop ends (for tests and the
     * QA note); for the + it is its glyph's.
     */
    public fun worstContrast(roles: Roles, role: LiquidRole): Double {
        val d = face(roles, role)
        val (darkEnd, brightEnd) = backdropEnds(recipe(role, roles.dark))
        val unders = listOf(roles.ground.opaqueArgb(), darkEnd, brightEnd)
        val inks = if (role == LiquidRole.Action) listOf(roles.onAccent) else listOf(roles.text1, roles.text2, roles.text3)
        val faces = listOfNotNull(d.fill, d.fillStart)
        return faces.minOf { f -> unders.minOf { u -> inks.minOf { wcagContrast(it, f.over(u)) } } }
    }

    /**
     * Which of the window's liquid [claims] are drawn as liquid glass (the rest drop to frost), within [budget]:
     * highest [LiquidRole.rank] first; a claim fits when its pane (1) and its source (1, if no granted claim already
     * pays for that source; null = a feature recording, free) fit what is left. While a modal claim (a sheet, a menu)
     * is granted, every non-modal claim below it drops: the page under the modal is covered by its scrim, and the page
     * is then recorded once, for the modal, rather than again for its own glass.
     */
    public fun <K> grant(claims: List<LiquidClaim<K>>, budget: Int = BUDGET): Set<K> {
        val granted = LinkedHashSet<K>()
        val paid = HashSet<Any>()
        var left = budget
        var modal = false
        for (c in claims.sortedWith(compareByDescending<LiquidClaim<K>> { it.role.rank }.thenBy { it.order })) {
            if (modal && !c.role.modal) continue
            val sourceCost = if (c.source == null || c.source in paid) 0 else 1
            val cost = 1 + sourceCost
            if (cost > left) continue
            left -= cost
            c.source?.let { paid += it }
            granted += c.key
            if (c.role.modal) modal = true
        }
        return granted
    }

    private fun Argb.opaqueArgb(): Argb = Argb(v or (0xFF shl 24))
}

/** One floating part asking to be liquid glass: [order] breaks ties between equal roles (earlier first). */
public data class LiquidClaim<K>(val key: K, val role: LiquidRole, val source: Any?, val order: Int = 0)

/**
 * The rim light's colour: the theme accent with its hue pulled towards a cool night anchor in a dark theme and a warm day
 * anchor in a light one, at a fixed lightness and a clamped chroma (docs/designs/theme-switch-premium.md §4, the s33 rim
 * light). The perceptual space is OKLCH, standing in for HCT until s33's shared anchor lands; then this function reads it.
 */
public object RimLight {
    /** The cool anchor hue (OKLCH degrees) a dark theme's rim leans to. */
    public const val NIGHT_HUE: Double = 250.0

    /** The warm anchor hue a light theme's rim leans to. */
    public const val DAY_HUE: Double = 75.0

    /** How far the accent's hue moves towards the anchor (0 keeps the accent's hue, 1 is the anchor's). */
    public const val PULL: Double = .5

    /** The rim's lightness (OKLCH L) in a dark and a light theme. */
    public const val DARK_L: Double = .88
    public const val LIGHT_L: Double = .72

    /** The rim's chroma stays within this range: a tint, never a neon line, and never plain white. */
    public const val MIN_C: Double = .05
    public const val MAX_C: Double = .12

    public fun of(roles: Roles): Argb {
        val (_, c, h) = oklch(roles.accent)
        val anchor = if (roles.dark) NIGHT_HUE else DAY_HUE
        val hue = if (c < 1e-4) anchor else mixHue(h, anchor, PULL)
        var chroma = c.coerceIn(MIN_C, MAX_C)
        val l = if (roles.dark) DARK_L else LIGHT_L
        // Out of the sRGB gamut: give up chroma, never lightness.
        while (chroma > 0.0 && !inGamut(l, chroma, hue)) chroma -= .005
        return fromOklch(l, chroma.coerceAtLeast(0.0), hue)
    }

    private fun mixHue(from: Double, to: Double, t: Double): Double {
        var d = (to - from) % 360.0
        if (d > 180.0) d -= 360.0
        if (d < -180.0) d += 360.0
        return ((from + d * t) % 360.0 + 360.0) % 360.0
    }

    private fun lin(ch: Int): Double {
        val s = ch / 255.0
        return if (s <= .04045) s / 12.92 else ((s + .055) / 1.055).pow(2.4)
    }

    private fun enc(x: Double): Double = if (x <= .0031308) 12.92 * x else 1.055 * x.pow(1 / 2.4) - .055

    internal fun oklch(c: Argb): Triple<Double, Double, Double> {
        val r = lin(c.red)
        val g = lin(c.green)
        val b = lin(c.blue)
        val l = cbrt(.4122214708 * r + .5363325363 * g + .0514459929 * b)
        val m = cbrt(.2119034982 * r + .6806995451 * g + .1073969566 * b)
        val s = cbrt(.0883024619 * r + .2817188376 * g + .6299787005 * b)
        val L = .2104542553 * l + .7936177850 * m - .0040720468 * s
        val A = 1.9779984951 * l - 2.4285922050 * m + .4505937099 * s
        val B = .0259040371 * l + .7827717662 * m - .8086757660 * s
        val h = (atan2(B, A) * 180.0 / PI + 360.0) % 360.0
        return Triple(L, sqrt(A * A + B * B), h)
    }

    private fun linear(L: Double, C: Double, hDeg: Double): DoubleArray {
        val h = hDeg * PI / 180.0
        val a = C * cos(h)
        val b = C * sin(h)
        val l = (L + .3963377774 * a + .2158037573 * b).pow(3)
        val m = (L - .1055613458 * a - .0638541728 * b).pow(3)
        val s = (L - .0894841775 * a - 1.2914855480 * b).pow(3)
        return doubleArrayOf(
            4.0767416621 * l - 3.3077115913 * m + .2309699292 * s,
            -1.2684380046 * l + 2.6097574011 * m - .3413193965 * s,
            -.0041960863 * l - .7034186147 * m + 1.7076147010 * s,
        )
    }

    private fun inGamut(L: Double, C: Double, h: Double): Boolean = linear(L, C, h).all { it in -1e-4..1.0001 }

    private fun fromOklch(L: Double, C: Double, h: Double): Argb {
        val rgb = linear(L, C, h).map { (enc(it.coerceIn(0.0, 1.0)) * 255.0).roundToInt().coerceIn(0, 255) }
        return Argb((0xFF shl 24) or (rgb[0] shl 16) or (rgb[1] shl 8) or rgb[2])
    }
}
