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
import kotlin.math.roundToInt

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
    /** The rim the lens bends in, at most this many dp and this share of the shorter side ([LiquidEdge.rimPx]). */
    val rimMaxDp: Float = LiquidEdge.RIM_MAX_DP,
    val rimShare: Float = LiquidEdge.RIM_SHARE,
    /** A drop of glass (the +, a pill): the middle reads thicker too. */
    val depth: Boolean = false,
    /** How much of the pane's Glass tint lies over the backdrop (the words get their own plate). */
    val tint: Float = .2f,
)

public object LiquidEdge {
    /** The rim the lens bends in on a large pane: at most this many dp ... */
    public const val RIM_MAX_DP: Float = 22f

    /** ... and at most this share of the pane's shorter side; the middle, where the words sit, stays calm. */
    public const val RIM_SHARE: Float = .40f

    /**
     * The liquid surfaces one window may hold at once. A pane costs 1; a page the kit records for panes over it costs 1,
     * once however many panes share it (a feature's list or transcript recording costs nothing here: it records in
     * every style). See [grant].
     */
    public const val BUDGET: Int = 4

    /** How much colour the glass lifts from what lies behind it (vibrancy; the library's own is 1.5). */
    public const val SATURATION: Float = 1.45f

    /** How far the glass presses the backdrop's contrast together (1 keeps it): lightly, so the content shows. */
    public const val PRESS: Float = .86f

    /** How far the backdrop moves towards the theme: a little darker in a dark theme, a little lighter in a light one. */
    public const val PRESS_LIFT_DARK: Float = .06f
    public const val PRESS_LIFT_LIGHT: Float = .06f

    /** The rim width the lens bends in, for a pane whose shorter side is [shortSidePx], at [density] px per dp. */
    public fun rimPx(shortSidePx: Float, density: Float, recipe: LiquidRecipe? = null): Float =
        minOf((recipe?.rimMaxDp ?: RIM_MAX_DP) * density, (recipe?.rimShare ?: RIM_SHARE) * shortSidePx)

    /**
     * The recipe for [role] in a [dark] or light theme. The panes are clear glass (a thin tint: 0.18–0.34), bent hard in
     * a wide rim so the content visibly curves at the edges; the + and the pills are drops (depth, lens to the middle).
     * The words on a pane lie on their own plate ([wordsPlate]), so the pane itself never has to be thick.
     */
    public fun recipe(role: LiquidRole, dark: Boolean): LiquidRecipe {
        val lift = if (dark) -PRESS_LIFT_DARK else PRESS_LIFT_LIGHT
        fun r(blur: Float, refraction: Float, shadow: Float, tintDark: Float, tintLight: Float, rimMax: Float = RIM_MAX_DP, share: Float = RIM_SHARE, depth: Boolean = false) =
            LiquidRecipe(SATURATION, PRESS, lift, blur, refraction, shadow, rimMax, share, depth, if (dark) tintDark else tintLight)
        return when (role) {
            // Bars and the message box carry words over moving text: their glass frosts it to colour and shape
            // (never readable text behind words), while the rim still bends it.
            LiquidRole.Bar -> r(7f, 34f, .14f, .18f, .30f)
            LiquidRole.Composer -> r(7f, 34f, .16f, .20f, .32f)
            LiquidRole.Sheet -> r(if (dark) 6f else 8f, 44f, 0f, .30f, .40f, rimMax = 30f)
            LiquidRole.Menu -> r(if (dark) 4f else 6f, 36f, .18f, .28f, .38f, rimMax = 26f)
            LiquidRole.Pill, LiquidRole.Status -> r(1f, 22f, .16f, .18f, .28f, rimMax = 40f, share = .5f, depth = true)
            LiquidRole.Action -> r(1f, 26f, .22f, .55f, .62f, rimMax = 40f, share = .5f, depth = true)
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
     * The face a liquid [role] pane lays over its backdrop: today's Glass tint for the role ([GlassSpec], hook H10's
     * words plate kept) at the recipe's thin [LiquidRecipe.tint], lit a little at its top start; no rims and no top glow
     * (the accent rim light replaces them). The + is its accent at the recipe's tint.
     */
    public fun face(roles: Roles, role: LiquidRole, radii: Radii = Radii.Zero): SurfaceDecoration {
        val recipe = recipe(role, roles.dark)
        if (role == LiquidRole.Action) {
            // The + is accent glass, as thin as its glyph allows: 3:1 over either end of any backdrop.
            val accent = roles.accent.opaqueArgb()
            val (darkEnd, brightEnd) = backdropEnds(recipe)
            val (glyph, target) = inks(roles, role).single()
            fun reads(a: Float) = listOf(darkEnd, brightEnd).all { wcagContrast(glyph, accent.withAlpha(a).over(it)) >= target - 1e-6 }
            var a = recipe.tint
            while (a < 1f && !reads(a)) a = minOf(1f, a + .02f)
            return SurfaceDecoration(radii = radii, circle = true, fill = accent.withAlpha(a), wordsPlate = Argb(0))
        }
        val (glass, level) = glassRole(role)
        val request = SurfaceRequest(roles = roles, role = glass, knobs = Knobs(), level = level, radii = radii)
        val resolved = GlassSpec.resolve(request) ?: SurfaceDecoration(radii = radii, fill = solidOf(roles, role))
        val base = resolved.fill?.opaqueArgb() ?: solidOf(roles, role)
        val lit = resolved.fillStart?.opaqueArgb() ?: base
        return resolved.copy(
            fill = base.withAlpha(recipe.tint),
            fillStart = lit.withAlpha(minOf(1f, recipe.tint + .06f)),
            edgeWidthDp = 0f, edgeStart = null, edgeEnd = null, innerGlow = Argb(0), shadows = emptyList(), frost = 0f,
        )
    }

    /** What must read on a [role] pane: text1, text2 and text3 (the +: its glyph), each with its target contrast. */
    public fun inks(roles: Roles, role: LiquidRole): List<Pair<Argb, Double>> {
        val solid = solidOf(roles, role)
        return if (role == LiquidRole.Action) listOf(roles.onAccent to minOf(GLYPH_FLOOR, wcagContrast(roles.onAccent, solid)))
        else listOf(roles.text1, roles.text2, roles.text3).map { it to minOf(GlassFinish.FLOOR, wcagContrast(it, solid)) }
    }

    /** Non-text contrast (3:1, with GlassFinish's margin) for a glyph on the +. */
    public const val GLYPH_FLOOR: Double = 3.0 + .04

    /**
     * The plate behind the words on a [role] pane (GlassSpec.plateFor, hook H10), at the least alpha at which text1,
     * text2 and text3 reach their targets over the pane's face over either end of any backdrop ([backdropEnds]); the
     * pane stays clear around the words. Transparent for the + (its glyph reads on its accent).
     */
    public fun wordsPlate(roles: Roles, role: LiquidRole): Argb {
        if (role == LiquidRole.Action) return Argb(0)
        val (glass, _) = glassRole(role)
        val glassPlate = GlassSpec.plateFor(roles, glass).opaqueArgb()
        val face = face(roles, role)
        val (darkEnd, brightEnd) = backdropEnds(recipe(role, roles.dark))
        val unders = listOfNotNull(face.fill, face.fillStart).flatMap { f -> listOf(f.over(darkEnd), f.over(brightEnd)) }
        val inks = inks(roles, role)
        fun reads(plate: Argb, a: Float): Boolean = unders.all { u ->
            val seen = plate.withAlpha(a).over(u)
            inks.all { (ink, target) -> wcagContrast(ink, seen) >= target - 1e-6 }
        }
        // Glass's own plate where it can carry the words; else the Solid surface itself, which by the targets' own
        // definition always can.
        val plate = if (reads(glassPlate, 1f)) glassPlate else solidOf(roles, role)
        fun reads(a: Float): Boolean = reads(plate, a)
        if (reads(0f)) return plate.withAlpha(0f)
        var lo = 0f
        var hi = 1f
        repeat(14) {
            val mid = (lo + hi) / 2f
            if (reads(mid)) hi = mid else lo = mid
        }
        return plate.withAlpha(hi)
    }

    /**
     * The lowest contrast any ink of [role] has where it sits: on its words plate over the pane over either backdrop
     * end (the +: its glyph on the accent face). For tests and the QA note.
     */
    public fun worstContrast(roles: Roles, role: LiquidRole): Double {
        val face = face(roles, role)
        val plate = wordsPlate(roles, role)
        val (darkEnd, brightEnd) = backdropEnds(recipe(role, roles.dark))
        return listOfNotNull(face.fill, face.fillStart).minOf { f ->
            listOf(darkEnd, brightEnd).minOf { e ->
                val seen = plate.over(f.over(e))
                inks(roles, role).minOf { (ink, _) -> wcagContrast(ink, seen) }
            }
        }
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

/** The ink the words on one liquid pane are drawn in: light over dark content, dark over bright (Apple's adaptive glass). */
public enum class LiquidInk { Light, Dark }

/**
 * When a liquid pane flips its ink (per pane, whatever the app's own light or dark mode): it samples the mean luminance
 * of the content under it, flips to dark ink above [TO_DARK] and back to light ink below [TO_LIGHT] (the gap is the
 * hysteresis, so a pane over mid-grey content never flickers), cross-fading over [FLIP_MS]. Busy content (luminance
 * variance above [BUSY]) thickens the words plate by [BUSY_PLATE]; calm content lets it back.
 */
public object LiquidInkRule {
    public const val TO_DARK: Float = .62f
    public const val TO_LIGHT: Float = .42f
    public const val FLIP_MS: Int = 180
    public const val BUSY: Float = .035f
    public const val BUSY_PLATE: Float = 1.35f

    /** How often a pane samples at most while its content moves (10 Hz). */
    public const val SAMPLE_MS: Long = 100

    /** The ink after [current] given the content's mean [luminance] (0-1). */
    public fun next(current: LiquidInk, luminance: Float): LiquidInk =
        when {
            luminance >= TO_DARK -> LiquidInk.Dark
            luminance <= TO_LIGHT -> LiquidInk.Light
            else -> current
        }

    /** The ink a theme draws its words in. */
    public fun of(dark: Boolean): LiquidInk = if (dark) LiquidInk.Light else LiquidInk.Dark

    /** Mean luminance and its variance of [argb] pixels (Rec. 709 on sRGB channels, cheap: no linearisation). */
    public fun measure(argb: IntArray): Pair<Float, Float> {
        if (argb.isEmpty()) return 0f to 0f
        var sum = 0.0
        var sq = 0.0
        for (p in argb) {
            val l = (.2126 * ((p shr 16) and 0xFF) + .7152 * ((p shr 8) and 0xFF) + .0722 * (p and 0xFF)) / 255.0
            sum += l
            sq += l * l
        }
        val mean = sum / argb.size
        return mean.toFloat() to (sq / argb.size - mean * mean).coerceAtLeast(0.0).toFloat()
    }
}
