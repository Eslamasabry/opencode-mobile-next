package io.github.eslamasabry.oc.ui.design.effects

import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.Roles
import io.github.eslamasabry.oc.ui.design.color.lerp
import io.github.eslamasabry.oc.ui.design.color.over
import io.github.eslamasabry.oc.ui.design.color.wcagContrast
import io.github.eslamasabry.oc.ui.design.style.GlassFinish
import io.github.eslamasabry.oc.ui.design.style.GlassLookPreset
import io.github.eslamasabry.oc.ui.design.style.GlassLooks
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
 * (thickened until text1/2/3 read over the content their ink lies over; no plate behind words, owner 2026-11-08), with a
 * soft white rim light. When it is drawn is FrostPolicy's (`edgesHeldBy`). This file holds the numbers; the kit paints them.
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

    /** A sheet or a menu: it records the page under it (the page's own glass included) and bends that. */
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
    /** How much of the pane's Glass tint lies over the backdrop at least ([LiquidEdge.faceTint] may thicken it). */
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
     * every style). See [grant]. Five: a chat's header, composer and jump pill stay liquid while a menu or a sheet,
     * with the page it records, opens over them (owner 2026-11-08: "opening a glass overlay kills all other glass").
     */
    public const val BUDGET: Int = 5

    /** What sheets and menus may hold on top of [BUDGET]: a menu over a sheet, both over the one page they record. */
    public const val MODAL_BUDGET: Int = 3

    /** How much colour the glass lifts from what lies behind it (vibrancy; the library's own is 1.5). */
    public const val SATURATION: Float = 1.6f

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
     * a wide rim so the content visibly curves at the edges; the + is a drop (depth, lens to the middle), the pills calm.
     * The words read on the face itself: [face] thickens the tint only as far as they need ([faceTint]). A [look] scales
     * the starting tint ([GlassLookPreset.tintFactor]); Smoked starts at least at its smoke ([GlassLookPreset.smokeTint]).
     */
    public fun recipe(role: LiquidRole, dark: Boolean, look: GlassLookPreset = GlassLooks.Glossy): LiquidRecipe {
        val base = baseRecipe(role, dark)
        if (role == LiquidRole.Action) return base
        val tint = (base.tint * look.tintFactor).let { if (look.smoked) maxOf(it, look.smokeTint) else it }.coerceIn(0f, 1f)
        return if (tint == base.tint) base else base.copy(tint = tint)
    }

    private fun baseRecipe(role: LiquidRole, dark: Boolean): LiquidRecipe {
        val lift = if (dark) -PRESS_LIFT_DARK else PRESS_LIFT_LIGHT
        // Crisp edges (owner 2026-11-09, "crisper glossy glass"): no soft shadow halo under any pane; the 1 dp rim light
        // and the specular set it off what lies behind it.
        @Suppress("UNUSED_PARAMETER")
        fun r(blur: Float, refraction: Float, shadow: Float, tintDark: Float, tintLight: Float, rimMax: Float = RIM_MAX_DP, share: Float = RIM_SHARE, depth: Boolean = false) =
            LiquidRecipe(SATURATION, PRESS, lift, blur, refraction, NO_SHADOW, rimMax, share, depth, if (dark) tintDark else tintLight)
        return when (role) {
            // Bars and the message box carry words over moving text: their glass frosts it to colour and shape
            // (never readable text behind words), while the rim still bends it.
            LiquidRole.Bar -> r(7f, 34f, .14f, .18f, .30f)
            LiquidRole.Composer -> r(7f, 34f, .16f, .20f, .32f)
            LiquidRole.Sheet -> r(if (dark) 16f else 18f, 44f, 0f, .30f, .40f, rimMax = 30f)
            LiquidRole.Menu -> r(if (dark) 12f else 14f, 36f, .18f, .28f, .38f, rimMax = 26f)
            // Small pills (the jump pill, the chips over the message box) are calm glass: a narrow rim and a light bend,
            // no lens to the middle (owner 2026-11-08: the jump pill's lens read swirly over text).
            LiquidRole.Pill, LiquidRole.Status -> r(2f, 12f, .16f, .18f, .28f, rimMax = 12f, share = .28f)
            LiquidRole.Action -> r(1f, 26f, .22f, .55f, .62f, rimMax = 40f, share = .5f, depth = true)
        }
    }

    /** No liquid pane casts a shadow (owner 2026-11-09: crisp edges, no halo). */
    private const val NO_SHADOW: Float = 0f

    /** The Glass surface role and level whose tint a liquid [role] pane keeps. */
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
     * The face a liquid [role] pane lays over its backdrop: today's Glass tint for the role ([GlassSpec]) at the
     * recipe's thin [LiquidRecipe.tint], thickened only as far as its words need ([faceTint]: there is no plate behind
     * words, owner 2026-11-08 "get rid of the haze behind the text"), lit a little at its top start, with a soft top
     * [SurfaceDecoration.sheen]; no rims and no top glow (the rim light replaces them). The + is its accent at the
     * recipe's tint. A pane whose words flip to the other theme's ink draws the other theme's face with them.
     */
    public fun face(
        roles: Roles,
        role: LiquidRole,
        radii: Radii = Radii.Zero,
        picture: List<Argb>? = null,
        look: GlassLookPreset = GlassLooks.Glossy,
        /** The page the pane floats over, when its face is another theme's (Smoked's dark face over a light page). */
        page: Roles = roles,
    ): SurfaceDecoration {
        val recipe = recipe(role, roles.dark, look)
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
        val t = faceTint(roles, role, picture, look, page)
        return SurfaceDecoration(radii = radii, fill = t.fill, fillStart = t.fillStart, sheen = t.sheen)
    }

    /**
     * The most a liquid [role] face holds (owner 2026-11-09, "glass, not frosty plastic"): sheets and menus blur their
     * backdrop to soft colour (18/16 dp, 14/12 dp) and stay under .70/.66 and .72/.68; the bars and the message box,
     * over the transcript behind the edge fade, under .62/.58; the small pills under .55.
     */
    public fun faceCap(role: LiquidRole, dark: Boolean): Float =
        when (role) {
            LiquidRole.Sheet -> if (dark) .66f else .70f
            LiquidRole.Menu -> if (dark) .68f else .72f
            LiquidRole.Bar, LiquidRole.Composer -> if (dark) .58f else .62f
            LiquidRole.Pill, LiquidRole.Status -> .55f
            LiquidRole.Action -> 1f
        }

    /** A liquid face's tint, its lit top-start corner and its sheen. */
    public data class FaceTint(val fill: Argb, val fillStart: Argb, val sheen: Argb)

    /** How much more the lit top-start corner of a liquid face holds than the rest. */
    private const val LIT: Float = .06f


    /** The share of a clear in-flow pane's sheen a liquid face takes: the lens and rim light already carry the gloss. */
    private const val SHEEN_SHARE: Float = .85f

    /** A liquid face's top sheen (owner 2026-11-08, "more glossy"): the theme's near-white, fading over the top 30 %. */
    public fun sheenOf(roles: Roles, look: GlassLookPreset = GlassLooks.Glossy): Argb = GlassSpec.sheenOf(roles, look.spec).let { it.withAlpha(it.alpha / 255f * SHEEN_SHARE) }

    /**
     * A liquid [role] face's tint: the least alpha, from the recipe's thin tint up, at which text1, text2 and text3 reach
     * their targets ([inks]) on the face over what it floats over ([themeSpots]), with and without the sheen. As GlassFinish does for a pane: where Glass's own tint cannot carry them its lit corner goes, then the Solid
     * surface the pane stands for takes its place ([solidOf]), and last the sheen goes. Quieter, never less legible.
     */
    public fun faceTint(
        roles: Roles,
        role: LiquidRole,
        picture: List<Argb>? = null,
        look: GlassLookPreset = GlassLooks.Glossy,
        page: Roles = roles,
    ): FaceTint {
        val recipe = recipe(role, roles.dark, look)
        val (base, lit) = tintOf(roles, role)
        val thin =
            SurfaceDecoration(
                fill = base.withAlpha(recipe.tint),
                fillStart = lit.withAlpha(minOf(1f, recipe.tint + LIT)),
                sheen = sheenOf(roles, look),
                // Marks a face that carries its own words (GlassFinish thickens only such faces); never drawn.
                wordsPlate = base,
            )
        // The same measure as Glass's in-flow panes: a dark face deepens with the ground's smoke, a light one with its
        // own milk, the gloss goes first where it costs, then the Solid surface itself.
        val d = GlassFinish.thickened(thin, roles, solidOf(roles, role), themeSpots(page, recipe, picture), inks(roles, role))
        // Glass, never a plate (owner 2026-11-09 retune, Apple's regular material): the face may thicken for its words
        // only up to its role's cap; the backdrop's blur, not paint, does the rest.
        val cap = faceCap(role, roles.dark)
        fun Argb.capped(): Argb = if (alpha / 255f > cap) withAlpha(cap) else this
        val fill = (d.fill ?: base).capped()
        return FaceTint(fill, (d.fillStart ?: fill).capped().let { if (it.alpha / 255f > cap + LIT) it.withAlpha(cap + LIT) else it }, d.sheen)
    }

    /** Today's Glass tint for [role]'s pane and its lit top-start corner, opaque. */
    private fun tintOf(roles: Roles, role: LiquidRole): Pair<Argb, Argb> {
        val (glass, level) = glassRole(role)
        val request = SurfaceRequest(roles = roles, role = glass, knobs = Knobs(), level = level)
        val resolved = GlassSpec.resolve(request) ?: SurfaceDecoration(fill = solidOf(roles, role))
        val base = resolved.fill?.opaqueArgb() ?: solidOf(roles, role)
        return base to (resolved.fillStart?.opaqueArgb() ?: base)
    }

    /**
     * What a liquid pane floats over in this app, as the lens presses it: the page's ground and its surface steps, the
     * frosted aurora's darkest and brightest spots, words blurred into the ground ([BLURRED_INK] of text1: the bar's and
     * the composer's blur leaves no readable text behind words) and an accent wash. A picture of the other brightness
     * (a photo, a white page in a screenshot) flips the words and the face to the other theme's ([LiquidInkRule]), whose
     * face is measured the same way. Not every colour a picture can hold: that would need a plate, and the owner removed
     * the plate (2026-11-08); the strictly worst case is reported by [bandContrast].
     *
     * [picture] is what the page's live background can put behind the content, when the kit knows it (glass-live
     * 2026-11-09): nothing over the flat ground, the frosted aurora's spots, or a shader look's own spots under the lens
     * ([LiveSpots.forLook]). Null (not known) measures every picture the app draws: the frosted aurora and the farthest
     * any shader look reaches under the lens ([LiveReach.most]).
     */
    public fun themeSpots(roles: Roles, recipe: LiquidRecipe, picture: List<Argb>? = null): List<Argb> {
        val ground = roles.ground.opaqueArgb()
        val pictured =
            picture
                ?: (AuroraImage.spotsOf(roles.ground, roles.accent, roles.dark, frosted = true).toList() +
                    // A shader look behind the content (owner 2026-11-09: the glass now shows it): the farthest any
                    // look reaches under the lens (LiveReach, measured).
                    LiveSpots.of(roles, LiveReach.most(roles.dark, lens = true)))
        val content =
            listOf(ground, roles.surface1.over(ground), roles.surface2.over(ground), roles.surface3.over(ground)) +
                pictured +
                listOf(lerp(ground, roles.text1.opaqueArgb(), BLURRED_INK), lerp(ground, roles.accent.opaqueArgb(), ACCENT_WASH))
        return content.map { filtered(it, recipe) }
    }

    /**
     * How much of the ink a line of words keeps once the glass has blurred it. Measured (ChatEdgeATest, 2026-11-09):
     * a paragraph of the transcript's Body text under the bar's 7 dp blur keeps .28 at its densest; the transcript now
     * scrolls on under the bar (chat edge A), so the bar's words are held over that, with a little room.
     */
    public const val BLURRED_INK: Float = .30f

    /** An accent-filled part (a chip, a link run) behind the glass, blurred into the ground. */
    public const val ACCENT_WASH: Float = .25f

    /**
     * The lowest text contrast on [role]'s face over content of any colour whose luminance stays on the theme's side
     * of the ink flip (black to [LiquidInkRule.TO_DARK] in a dark theme, [LiquidInkRule.TO_LIGHT] to white in a light
     * one). For the QA note: what the removed plate used to guard.
     */
    public fun bandContrast(roles: Roles, role: LiquidRole): Double {
        val recipe = recipe(role, roles.dark)
        val face = face(roles, role)
        val band = if (roles.dark) listOf(grey(0f), grey(LiquidInkRule.TO_DARK)) else listOf(grey(LiquidInkRule.TO_LIGHT), grey(1f))
        return listOfNotNull(face.fill, face.fillStart).minOf { f ->
            band.minOf { e -> inks(roles, role).minOf { (ink, _) -> wcagContrast(ink, f.over(filtered(e, recipe))) } }
        }
    }

    private fun grey(l: Float): Argb {
        val v = (l.coerceIn(0f, 1f) * 255f).roundToInt()
        return Argb((0xFF shl 24) or (v shl 16) or (v shl 8) or v)
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
     * The lowest contrast any ink of [role] has where it sits: on the face (with and without its sheen) over what it
     * floats over ([themeSpots]; the +: its glyph on the accent face over either end of any backdrop). No
     * plate. For tests and the QA note.
     */
    public fun worstContrast(roles: Roles, role: LiquidRole): Double {
        val face = face(roles, role)
        val recipe = recipe(role, roles.dark)
        val spots = if (role == LiquidRole.Action) backdropEnds(recipe).toList() else themeSpots(roles, recipe)
        return listOfNotNull(face.fill, face.fillStart).minOf { f ->
            spots.minOf { e ->
                val bare = f.over(e)
                listOf(bare, face.sheen.over(bare)).minOf { seen -> inks(roles, role).minOf { (ink, _) -> wcagContrast(ink, seen) } }
            }
        }
    }

    /**
     * Which of the window's liquid [claims] are drawn as liquid glass. The page's parts (bars, composer, +, pills) share
     * [budget]: highest [LiquidRole.rank] first; a claim fits when its pane (1) and its source (1, if no granted claim
     * already pays for that source; null = a feature recording, free) fit what is left. A sheet or a menu has its own
     * allowance ([MODAL_BUDGET]) and never takes a slot from the page: opening one changes no face under it, so nothing
     * flips to frost and back, or rebuilds its glass, while it opens and closes (owner 2026-11-08: "opening a glass
     * overlay kills all other glass surfaces"; smoothness 2026-11-09: a reply's menu janked as two chips flipped). A
     * claim left out is not solid: it is drawn as frost of the same shape ([faceOf]).
     */
    public fun <K> grant(claims: List<LiquidClaim<K>>, budget: Int = BUDGET): Set<K> {
        val (modal, page) = claims.partition { it.role.modal }
        return fill(page, budget) + fill(modal, MODAL_BUDGET)
    }

    private fun <K> fill(claims: List<LiquidClaim<K>>, budget: Int): Set<K> {
        val granted = LinkedHashSet<K>()
        val paid = HashSet<Any>()
        var left = budget
        for (c in claims.sortedWith(compareByDescending<LiquidClaim<K>> { it.role.rank }.thenBy { it.order })) {
            val sourceCost = if (c.source == null || c.source in paid) 0 else 1
            val cost = 1 + sourceCost
            if (cost > left) continue
            left -= cost
            c.source?.let { paid += it }
            granted += c.key
        }
        return granted
    }

    /** How each of [claims] is drawn: liquid where [grant] grants it, else frost of the same shape. Never solid. */
    public fun <K> faceOf(claims: List<LiquidClaim<K>>, budget: Int = BUDGET): Map<K, LiquidFaceKind> {
        val granted = grant(claims, budget)
        return claims.associate { it.key to if (it.key in granted) LiquidFaceKind.Liquid else LiquidFaceKind.Frost }
    }

    private fun Argb.opaqueArgb(): Argb = Argb(v or (0xFF shl 24))
}

/** How a floating part asking for liquid glass is drawn: bent glass, or frost of the same shape through the same glass. */
public enum class LiquidFaceKind { Liquid, Frost }

/** One floating part asking to be liquid glass: [order] breaks ties between equal roles (earlier first). */
public data class LiquidClaim<K>(val key: K, val role: LiquidRole, val source: Any?, val order: Int = 0)

/** The ink the words on one liquid pane are drawn in: light over dark content, dark over bright (Apple's adaptive glass). */
public enum class LiquidInk { Light, Dark }

/**
 * When a liquid pane flips its ink (per pane, whatever the app's own light or dark mode): it samples the mean luminance
 * of the content under it, flips to dark ink above [TO_DARK] and back to light ink below [TO_LIGHT] (the gap is the
 * hysteresis, so a pane over mid-grey content never flickers), cross-fading over [FLIP_MS]. [BUSY] is the luminance
 * variance above which content counts as busy (measured, no longer acted on: there is no plate to thicken).
 */
public object LiquidInkRule {
    public const val TO_DARK: Float = .62f
    public const val TO_LIGHT: Float = .42f
    public const val FLIP_MS: Int = 180
    public const val BUSY: Float = .035f

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
