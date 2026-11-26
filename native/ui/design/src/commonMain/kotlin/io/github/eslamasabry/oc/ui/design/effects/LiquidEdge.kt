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
import kotlin.math.exp
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
 * bars; the bars above the edge dock; the dock above the floating + and the pills).
 */
public enum class LiquidRole(public val rank: Int) {
    Menu(8),
    Sheet(7),
    Composer(6),
    Bar(5),

    /**
     * The edge dock's one body of glass (the strip and the swell, the peek or the list out of it): the bar's recipe at
     * rest, the menu's fully grown ([LiquidEdge.dockRecipe]); its words' ink as the bar's.
     */
    Dock(4),
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
    /** What the glass holds back of the light and the colour behind a line of words ([LiquidHold]). */
    val hold: LiquidHold = LiquidHold.Off,
)

/**
 * The light and the colour a pane holds back inside its lens so the words on it read over a bright or saturated thing
 * behind it (docs/designs/glass-in-the-kit.md "words on glass"). [knee]: the luminance knee, [LiquidJoin.held]
 * (past [LiquidJoin.KNEE] the glass keeps [LiquidJoin.KNEE_SLOPE] of the light). [chroma]: the most colour the glass
 * lets through, 0 = no limit: a colour's chroma (largest channel minus smallest, 0-1) is left alone up to
 * [CHROMA_FREE] of this, then eases to this as the colour gets stronger ([LiquidEdge.heldRgb]), the light unchanged,
 * so a pure green held to .61 of its light by the knee is also a muted green, and a disc of it has no ring. The rim,
 * where content must show, keeps its colour.
 */
public data class LiquidHold(
    val knee: Boolean,
    val chroma: Float,
    /**
     * The local-contrast hold (owner 2026-11-13, "half glass half frosting" and the black slab): detail is compressed
     * round the LOCAL mean of what is behind (its reach [localDp]); the mean light and colour pass, then the knee and
     * the colour limit hold what is left. 0 = off. Menus and sheets wear it ([LiquidEdge.MODAL_HOLD]), outside the rim.
     */
    val localDp: Float = 0f,
    /** The detail (RGB distance from the local mean, 0-1) that passes whole, and the share kept beyond it. */
    val localFree: Float = 0f,
    val localSlope: Float = 1f,
    /**
     * The ink-side hold (round two, 2026-11-14: the grey bars of text behind a light menu and the ghost words between a
     * sheet's rows): the share kept of what behind the pane looks like the pane's own ink, past the page's local ground
     * within [localDp]. Under dark words a stroke darker than the ground round it is lifted to it, under light words a
     * stroke lighter than it is pressed down to it; only the grey part moves (the same amount from every channel), so a
     * colour keeps its chroma: no white film, no black slab. Where most of what is round a point is ink-like (a dark
     * picture under dark words) it lets go, so a dark thing is never lifted to fog. 1 = off. Outside the rim, as the rest.
     */
    val inkKeep: Float = 1f,
    /**
     * How much of the ink-side hold works in the rim's outer half, where the lens bends the page (0: none, the bent
     * strokes whole; 1: as in the middle, the bend shows only colour). Between, the bend still shows the page's strokes.
     */
    val inkRim: Float = 1f,
) {
    /** Whether this hold is the local-contrast one. */
    public val local: Boolean get() = localDp > 0f

    public companion object {
        public val Off: LiquidHold = LiquidHold(knee = false, chroma = 0f)

        /** The share of the chroma limit up to which colour is left alone. */
        public const val CHROMA_FREE: Float = .4f
    }
}

/**
 * The band of the page a modal pane (a sheet, a dialog, a menu, the dock's list) shows along each edge it shares with the
 * visible page: whole for [keepDp] in from that edge, fading out over [fadeDp] more ([LiquidEdge.modalPageKept]). Past it
 * the pane shows only the page's own picture (its ground and live background), never the page's controls or words.
 */
public data class ModalBand(val keepDp: Float, val fadeDp: Float) {
    /** Where the band is gone, dp in from the edge. */
    public val endDp: Float get() = keepDp + fadeDp
}

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

    /**
     * The most colour a menu or a sheet lets through under its words ([LiquidHold.chroma]): .26 of the full range.
     * Measured in `GlassWordsMenuSheetTest` on a pure green and a pure orange circle: the colour under the words is
     * .05 (green) and .26 (orange) of today's at the circle's core (the knee alone leaves .60 and .77; saturation 1.0
     * with it .59 and .70).
     */
    public const val HOLD_CHROMA: Float = .26f

    /**
     * The frost of a menu and a sheet, dp (B-609 / scale-dependent weight): larger modal surfaces carry visibly deeper
     * optical thickness than slim bars/pills (1 dp / 0.5 dp) while still visibly bending content at the curved rim.
     */
    public const val MODAL_BLUR_DP: Float = 6f

    /**
     * What a menu and a sheet hold back behind their words: the knee and the colour ([HOLD_CHROMA]) as before, the fine
     * detail (a line of text, a table's rules) pressed toward its local mean within [LiquidHold.localDp], and what looks
     * like the pane's own ink lifted (or pressed) to the page's local ground ([LiquidHold.inkKeep]), so the words do not
     * sit on words or on grey bars of them while the colour and the light of the page come through. Not in the rim.
     * Round two (2026-11-14): the mean is an even disc and reaches past a line's pitch (about 24 dp), so a line of text
     * no longer survives in it as a bar.
     */
    public val MODAL_HOLD: LiquidHold = LiquidHold(knee = true, chroma = HOLD_CHROMA, localDp = 28f, localFree = .02f, localSlope = .10f, inkKeep = .10f, inkRim = .8f)

    /**
     * What the plain bar and the plain message box hold back behind their words (B-608/B-609): the luminance knee plus
     * center-only optical frost & ink softening (`localDp = 22f`, `inkKeep = 0.14f`, `inkRim = 0f`). Because `inkRim`
     * is 0 and `deep` stays 0 across the outer half of the curved lens rim, passing text refracts crisply as light
     * bending around the outer edge while dissolving into calm ambient bokeh behind header titles and composer hints.
     */
    public val WORDS_HOLD: LiquidHold =
        LiquidHold(
            knee = true,
            chroma = 0f,
            localDp = 22f,
            localFree = .02f,
            localSlope = .12f,
            inkKeep = .14f,
            inkRim = 0f,
        )

    /** A sheet's hold: a sheet is modal and holds a list, so the page's words between its rows may be held harder. */
    public val SHEET_HOLD: LiquidHold = MODAL_HOLD

    /**
     * What a modal sheet (and a dialog) shows of the page it covers (cut audit 1, 2026-11-14: the green "Allow once"
     * button read as a big green ghost under the Details sheet's field, the send button as a green smear by a row's star
     * in the model sheet, the permission card's code box as a dark plate behind a row; no hold can tell a control from a
     * field of colour). The page under a sheet is not live content, so the sheet's middle shows the page's own picture
     * (the live background) and its ground, through the same glass: the colour and the light of the background pass, no
     * control, word or card of the page. The page itself shows only along an edge the sheet shares with the visible page
     * (a bottom sheet's top, every edge of a centred dialog): whole for [MODAL_PAGE_KEEP_DP] in from that edge (the rim
     * and the furthest its bend reads, [modalPageKept]) and fading out over [MODAL_PAGE_FADE_DP] more, held there as
     * before ([SHEET_HOLD]). An edge on the window's own edge shows none of the page. A small pane (a menu, a dialog, the
     * dock's list) keeps a narrower band, its rim ([smallPaneBand]).
     */
    public const val MODAL_PAGE_KEEP_DP: Float = 48f

    /** How far, past [MODAL_PAGE_KEEP_DP], the page fades out of a modal sheet, dp. */
    public const val MODAL_PAGE_FADE_DP: Float = 32f

    /** How far, dp, a sheet's edge lies from the window's edge before it shows the page whole (a predictive-back shrink). */
    public const val MODAL_PAGE_OPEN_DP: Float = 8f

    /**
     * The share of the page a modal sheet keeps at [depthDp] in from one of its edges, that edge lying [gapDp] from the
     * window's edge: 1 within [MODAL_PAGE_KEEP_DP], easing linearly to 0 over [MODAL_PAGE_FADE_DP] more; scaled by how
     * open the edge is (0 on the window's edge, 1 from [MODAL_PAGE_OPEN_DP] in). Outside the sheet (negative depth) as at
     * its edge.
     */
    public fun modalPageKept(depthDp: Float, gapDp: Float, band: ModalBand = SHEET_BAND): Float {
        val open = (gapDp / MODAL_PAGE_OPEN_DP).coerceIn(0f, 1f)
        val fade = if (band.fadeDp <= 0f) (if (depthDp <= band.keepDp) 1f else 0f) else 1f - ((depthDp - band.keepDp) / band.fadeDp).coerceIn(0f, 1f)
        return open * fade
    }

    /** A bottom sheet's band of the page along its open top edge (wave one): its handle and title lie there. */
    public val SHEET_BAND: ModalBand = ModalBand(MODAL_PAGE_KEEP_DP, MODAL_PAGE_FADE_DP)

    /**
     * How far, dp, the page fades out at the inner end of a small pane's rim (a menu, a dialog, the dock's list): the page
     * is whole up to this much short of the rim's end and gone at the rim's end ([smallPaneBand]).
     */
    public const val SMALL_PANE_FADE_DP: Float = 6f

    /**
     * The band of the page a small modal pane keeps along each open edge (cut audit 2, 2026-11-14, c-rowmenu-top,
     * c-rowmenu-bot, c-dock: the home row's menu and the dock's list showed the chips, the row icons and a button behind them
     * as blobs and dark blocks). A menu or a centred dialog borders the visible page on every side, so a sheet's band (48 dp
     * whole, 32 dp fading) would cover most of it: a 220 dp menu kept 80 dp of page on each side. Its band is its rim:
     * whole to [SMALL_PANE_FADE_DP] short of [rimMaxDp] and gone at the rim's end, so the bend still reads the page
     * (strongest a few dp in from the edge) and nothing past the rim does: no control, icon, chip or word behind the pane
     * reaches its calm middle, where its rows lie. A menu of 26 dp rim keeps 10-15 % of its width on each side.
     */
    public fun smallPaneBand(rimMaxDp: Float): ModalBand = ModalBand((rimMaxDp - SMALL_PANE_FADE_DP).coerceAtLeast(0f), SMALL_PANE_FADE_DP)

    /** A menu's band of the page (its rim, [smallPaneBand]). */
    public val MENU_BAND: ModalBand get() = smallPaneBand(baseRecipe(LiquidRole.Menu, dark = true).rimMaxDp)

    /** A centred dialog's band of the page (its rim: it borders the page on every side, as a menu does). */
    public val DIALOG_BAND: ModalBand get() = smallPaneBand(baseRecipe(LiquidRole.Sheet, dark = true).rimMaxDp)

    /** The band of the page [kind]'s pane keeps along its open edges; null for a pane that is not modal (it sees the page). */
    public fun modalBand(kind: GlassPaneKind): ModalBand? =
        when (kind) {
            GlassPaneKind.Menu -> MENU_BAND
            GlassPaneKind.Sheet -> SHEET_BAND
            GlassPaneKind.Dialog -> DIALOG_BAND
            else -> null
        }

    /** How far the glass presses the backdrop's contrast together (1 keeps it): lightly, so the content shows. */
    public const val PRESS: Float = 1f

    /** How far the backdrop moves towards the theme: a little darker in a dark theme, a little lighter in a light one. */
    public const val PRESS_LIFT_DARK: Float = .03f
    public const val PRESS_LIFT_LIGHT: Float = 0f

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

    /**
     * The menu's glass as the edge dock's swell wears it: the dock grows out of its strip towards this (blur 6 dp dark,
     * 3 light, no hold), then towards the menu's own glass when its list is out ([dockRecipe]). Menus themselves frost at
     * [MODAL_BLUR_DP] ([recipe]); the dock's strip and swell keep what they were.
     */
    public fun swellRecipe(dark: Boolean, look: GlassLookPreset = GlassLooks.Glossy): LiquidRecipe =
        recipe(LiquidRole.Menu, dark, look).copy(blurDp = if (dark) 6f else 3f, hold = LiquidHold.Off)

    private const val BAR_BLUR_DP: Float = 2f
    private const val COMPOSER_BLUR_DP: Float = 1.5f
    private const val PILL_BLUR_DP: Float = .5f
    private const val BAR_REFRACTION_DP: Float = 18f
    private const val SHEET_REFRACTION_DP: Float = 38f
    private const val MENU_REFRACTION_DP: Float = 36f
    private const val PILL_REFRACTION_DP: Float = 14f
    private const val ACTION_REFRACTION_DP: Float = 24f
    private const val BAR_RIM_MAX_DP: Float = 14f
    private const val MODAL_RIM_MAX_DP: Float = 26f
    private const val PILL_RIM_MAX_DP: Float = 16f
    private const val ACTION_RIM_MAX_DP: Float = 36f
    private const val BAR_RIM_SHARE: Float = .28f
    private const val WIDE_RIM_SHARE: Float = .42f
    private const val ACTION_RIM_SHARE: Float = .5f
    private const val BAR_SHADOW: Float = .30f
    private const val COMPOSER_SHADOW: Float = .34f
    private const val SHEET_SHADOW: Float = .38f
    private const val MENU_SHADOW: Float = .40f
    private const val PILL_SHADOW: Float = .26f
    private const val ACTION_SHADOW: Float = .34f
    private const val BAR_TINT_DARK: Float = .14f
    private const val BAR_TINT_LIGHT: Float = .10f
    private const val COMPOSER_TINT_DARK: Float = .16f
    private const val COMPOSER_TINT_LIGHT: Float = .12f
    private const val SHEET_TINT_DARK: Float = .22f
    private const val SHEET_TINT_LIGHT: Float = .16f
    private const val MENU_TINT_DARK: Float = .20f
    private const val MENU_TINT_LIGHT: Float = .14f

    private fun baseRecipe(role: LiquidRole, dark: Boolean): LiquidRecipe {
        val lift = if (dark) -PRESS_LIFT_DARK else PRESS_LIFT_LIGHT
        // Apple Liquid Glass (B-609, B-612, B-613): scale-dependent optical weight, clear bar/pill centers,
        // deeper modal glass, luminous low-tint light mode, and content-aware adaptive elevation shadow.
        fun r(
            blur: Float,
            refraction: Float,
            shadow: Float,
            tintDark: Float,
            tintLight: Float,
            rimMax: Float = RIM_MAX_DP,
            share: Float = RIM_SHARE,
            depth: Boolean = false,
            hold: LiquidHold = LiquidHold.Off,
        ) = LiquidRecipe(
            SATURATION, PRESS, lift, blur, refraction, shadow, rimMax, share, depth,
            if (dark) tintDark else tintLight, hold,
        )
        return when (role) {
            LiquidRole.Bar ->
                r(
                    BAR_BLUR_DP, BAR_REFRACTION_DP, BAR_SHADOW, BAR_TINT_DARK, BAR_TINT_LIGHT,
                    rimMax = BAR_RIM_MAX_DP, share = BAR_RIM_SHARE, hold = WORDS_HOLD,
                )
            LiquidRole.Dock ->
                r(
                    BAR_BLUR_DP, BAR_REFRACTION_DP, BAR_SHADOW, BAR_TINT_DARK, BAR_TINT_LIGHT,
                    rimMax = BAR_RIM_MAX_DP, share = BAR_RIM_SHARE,
                )
            LiquidRole.Composer ->
                r(
                    COMPOSER_BLUR_DP, BAR_REFRACTION_DP, COMPOSER_SHADOW, COMPOSER_TINT_DARK, COMPOSER_TINT_LIGHT,
                    rimMax = BAR_RIM_MAX_DP, share = BAR_RIM_SHARE, hold = WORDS_HOLD,
                )
            LiquidRole.Sheet ->
                r(
                    MODAL_BLUR_DP, SHEET_REFRACTION_DP, SHEET_SHADOW, SHEET_TINT_DARK, SHEET_TINT_LIGHT,
                    rimMax = MODAL_RIM_MAX_DP, share = WIDE_RIM_SHARE, hold = SHEET_HOLD,
                )
            LiquidRole.Menu ->
                r(
                    MODAL_BLUR_DP, MENU_REFRACTION_DP, MENU_SHADOW, MENU_TINT_DARK, MENU_TINT_LIGHT,
                    rimMax = MODAL_RIM_MAX_DP, share = WIDE_RIM_SHARE, hold = MODAL_HOLD,
                )
            LiquidRole.Pill, LiquidRole.Status ->
                r(
                    PILL_BLUR_DP, PILL_REFRACTION_DP, PILL_SHADOW, BAR_TINT_DARK, BAR_TINT_LIGHT,
                    rimMax = PILL_RIM_MAX_DP, share = WIDE_RIM_SHARE,
                )
            // Pure-glass + FAB (owner preference respected): jewel-like dome lensing, crisp specular, subtle shadow.
            LiquidRole.Action ->
                r(
                    PILL_BLUR_DP, ACTION_REFRACTION_DP, ACTION_SHADOW, BAR_TINT_DARK, BAR_TINT_LIGHT,
                    rimMax = ACTION_RIM_MAX_DP, share = ACTION_RIM_SHARE, depth = true,
                )
        }
    }

    /**
     * [rgb] (0-1 each, the backdrop after the colour controls) as the glass's [hold] leaves it: the light past the knee
     * held ([LiquidJoin.held]; under dark ink the dark end lifted), then the colour that is left limited: a chroma
     * above `CHROMA_FREE * cap` eases to [LiquidHold.chroma] (`t + (cap - t) * (1 - exp(-(c - t) / (cap - t)))`), as far
     * as [inside] says (0 in the rim's outer half, 1 from its inner end: the rim keeps its colour). The light is the
     * knee's whatever the colour. The shaders do this per pixel with the same formula; this is the reference they are
     * tested against.
     */
    public fun heldRgb(rgb: FloatArray, lightInk: Boolean, hold: LiquidHold, inside: Float = 1f): FloatArray {
        if (!hold.knee) return rgb.copyOf()
        val lum = 0.213f * rgb[0] + 0.715f * rgb[1] + 0.072f * rgb[2]
        val light = if (lightInk) lum else 1f - lum
        val held = LiquidJoin.held(lum, lightInk)
        val heldLight = if (lightInk) held else 1f - held
        val scale = heldLight / maxOf(light, 0.0001f)
        val scaled = FloatArray(3) { i -> if (lightInk) rgb[i] * scale else 1f - (1f - rgb[i]) * scale }
        val c = scaled.max() - scaled.min()
        val cap = hold.chroma
        val t = LiquidHold.CHROMA_FREE * cap
        val limited = if (cap > 0f && c > t) (t + (cap - t) * (1f - exp(-(c - t) / (cap - t)))) / c else 1f
        val f = 1f + (limited - 1f) * inside.coerceIn(0f, 1f)
        return FloatArray(3) { i -> (held + (scaled[i] - held) * f).coerceIn(0f, 1f) }
    }

    /**
     * The dock's recipe at [growth] (0 at rest: the strip alone; 1 fully grown: the swell, the peek or the list out):
     * the bar's at rest and the menu's fully grown, every number mixed by the growth with no step ([mix]): one frost for
     * the whole body, its blur, bend, rim and tint moving together as it grows.
     */
    public fun dockRecipe(dark: Boolean, growth: Float, look: GlassLookPreset = GlassLooks.Glossy, list: Float = 0f): LiquidRecipe =
        toward(mix(recipe(LiquidRole.Dock, dark, look), swellRecipe(dark, look), growth), recipe(DOCK_LIST_ROLE, dark, look), list)

    /**
     * The glass the dock's pulled-out list wears (cut audit 2, c-dock: it kept a sheet's old 12 dp frost, and the row icons
     * behind it read through as dark blocks): a menu's, the family of every small modal pane: its blur, its bend, its
     * holds ([LiquidHold]) and its band of the page ([MENU_BAND]).
     */
    public val DOCK_LIST_ROLE: LiquidRole = LiquidRole.Menu

    /**
     * [from] with its blur and its bend moved towards [sheet]'s by [t] (0 keeps [from], 1 has the other's): the pulled-out
     * dock list reads rows of words over a page of words, so its glass frosts as a menu's does ([DOCK_LIST_ROLE]) while
     * its tint, rim and saturation stay the dock's. Linear in [t], so the change from the swell's glass to the list's has no step.
     */
    public fun toward(from: LiquidRecipe, sheet: LiquidRecipe, t: Float): LiquidRecipe {
        val f = t.coerceIn(0f, 1f)
        if (f == 0f) return from
        return from.copy(
            blurDp = from.blurDp + (sheet.blurDp - from.blurDp) * f,
            refractionDp = from.refractionDp + (sheet.refractionDp - from.refractionDp) * f,
        )
    }

    /**
     * [from] to [to] at [t] (clamped to 0..1): the blur, the bend, the rim's width and share, the shadow and the tint
     * mixed linearly; the colour controls too (equal in every recipe today). The depth flag is [from]'s until halfway.
     */
    public fun mix(from: LiquidRecipe, to: LiquidRecipe, t: Float): LiquidRecipe {
        val f = t.coerceIn(0f, 1f)
        fun m(a: Float, b: Float) = a + (b - a) * f
        return LiquidRecipe(
            saturation = m(from.saturation, to.saturation),
            contrast = m(from.contrast, to.contrast),
            brightness = m(from.brightness, to.brightness),
            blurDp = m(from.blurDp, to.blurDp),
            refractionDp = m(from.refractionDp, to.refractionDp),
            shadowAlpha = m(from.shadowAlpha, to.shadowAlpha),
            rimMaxDp = m(from.rimMaxDp, to.rimMaxDp),
            rimShare = m(from.rimShare, to.rimShare),
            depth = if (f < .5f) from.depth else to.depth,
            tint = m(from.tint, to.tint),
            hold = if (f < .5f) from.hold else to.hold,
        )
    }

    /**
     * The edge dock's place in the window's budget (owner 2026-11-11): tucked or at rest it ranks below every pill (the
     * chips over the message box and the jump pill keep their lens; a resting dock short of one is frost of the same
     * shape); grown (the swell, the peek or the list out) it takes its own rank, between the + and the bars.
     */
    public fun dockRank(grown: Boolean): Int = if (grown) LiquidRole.Dock.rank else DOCK_REST_RANK

    /** The resting dock's rank: below the status pill, the lowest page role. */
    public const val DOCK_REST_RANK: Int = 0

    /** The Glass surface role and level whose tint a liquid [role] pane keeps. */
    public fun glassRole(role: LiquidRole): Pair<SurfaceRole, SurfaceLevel> =
        when (role) {
            LiquidRole.Sheet, LiquidRole.Menu -> SurfaceRole.Sheet to SurfaceLevel.Surface2
            LiquidRole.Composer, LiquidRole.Bar, LiquidRole.Dock -> SurfaceRole.Floating to SurfaceLevel.Surface2
            LiquidRole.Pill, LiquidRole.Status, LiquidRole.Action -> SurfaceRole.Floating to SurfaceLevel.Surface3
        }

    /** The opaque Solid surface a [role] pane stands for: Glass is never asked to read better than Solid does. */
    public fun solidOf(roles: Roles, role: LiquidRole): Argb {
        val ground = roles.ground.opaqueArgb()
        return when (role) {
            LiquidRole.Bar, LiquidRole.Dock -> ground
            LiquidRole.Composer, LiquidRole.Sheet, LiquidRole.Menu -> roles.surface2.over(ground)
            LiquidRole.Pill, LiquidRole.Status, LiquidRole.Action -> roles.surface3.over(ground)
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
            // The + is clear glass with the pills' face (their tint, lit corner and sheen), round; its glyph is the
            // page's ink, flipped by what lies behind it as on every pane.
            val t = faceTint(roles, LiquidRole.Pill, picture, look, page)
            return SurfaceDecoration(radii = radii, circle = true, fill = t.fill, fillStart = t.fillStart, sheen = t.sheen, wordsPlate = Argb(0))
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
            LiquidRole.Bar, LiquidRole.Composer, LiquidRole.Dock -> if (dark) .58f else .62f
            LiquidRole.Pill, LiquidRole.Status -> .55f
            LiquidRole.Action -> 1f
        }

    /** A liquid face's tint, its lit top-start corner and its sheen. */
    public data class FaceTint(val fill: Argb, val fillStart: Argb, val sheen: Argb)

    /** How much more the lit top-start corner of a liquid face holds than the rest. */
    private const val LIT: Float = .03f


    /** Share of a clear pane's sheen a liquid face takes: subtle glossy top catch preserving WCAG contrast. */
    private const val SHEEN_SHARE: Float = .375f

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
        // Restore physical glass top sheen (B-609) while keeping fillStart equal to fill so dark-mode text2/text3
        // contrast over surface3 stays above 4.5:1 across every theme pack.
        val d = thin.copy(fillStart = thin.fill)
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
        return if (role == LiquidRole.Action) listOf(roles.text1 to minOf(GLYPH_FLOOR, wcagContrast(roles.text1, solid)))
        else listOf(roles.text1, roles.text2, roles.text3).map { it to minOf(GlassFinish.FLOOR, wcagContrast(it, solid)) }
    }

    /** Non-text contrast (3:1, with GlassFinish's margin) for a glyph on the +. */
    public const val GLYPH_FLOOR: Double = 3.0 + .04

    /**
     * The lowest contrast any ink of [role] has where it sits: on the face (with and without its sheen) over what it
     * floats over ([themeSpots]; the +: its glyph on its clear face). No
     * plate. For tests and the QA note.
     */
    public fun worstContrast(roles: Roles, role: LiquidRole): Double {
        val face = face(roles, role)
        val recipe = recipe(role, roles.dark)
        // The + is clear glass like every pane (owner 2026-11-14), so it is measured where every pane is.
        val spots = themeSpots(roles, recipe)
        return listOfNotNull(face.fill, face.fillStart).minOf { f ->
            spots.minOf { e ->
                val bare = f.over(e)
                listOf(bare, face.sheen.over(bare)).minOf { seen -> inks(roles, role).minOf { (ink, _) -> wcagContrast(ink, seen) } }
            }
        }
    }

    /**
     * Each ink of [role] on the face over the page at rest (its ground and surface steps, nothing scrolling behind):
     * the ink, its target and the lowest contrast it has there. Clear glass holds every ink to its target here.
     */
    public fun restContrasts(roles: Roles, role: LiquidRole): List<Triple<Argb, Double, Double>> {
        val face = face(roles, role)
        val recipe = recipe(role, roles.dark)
        val ground = roles.ground.opaqueArgb()
        val rest = listOf(ground, roles.surface1.over(ground), roles.surface2.over(ground), roles.surface3.over(ground)).map { filtered(it, recipe) }
        return inks(roles, role).map { (ink, target) ->
            Triple(ink, target, listOfNotNull(face.fill, face.fillStart).minOf { f -> rest.minOf { e -> wcagContrast(ink, f.over(e)) } })
        }
    }

    /**
     * The lowest contrast [role]'s primary words (text1; the +: its glyph) have on the face over everything it floats
     * over ([themeSpots]: blurred words scrolling behind, an accent wash, the live pictures). Clear glass holds them to
     * [MOVING_FLOOR] there.
     */
    public fun movingContrast(roles: Roles, role: LiquidRole): Double {
        val face = face(roles, role)
        val recipe = recipe(role, roles.dark)
        // The + is clear glass like every pane (owner 2026-11-14), so it is measured where every pane is.
        val spots = themeSpots(roles, recipe)
        val ink = inks(roles, role).first().first
        return listOfNotNull(face.fill, face.fillStart).minOf { f -> spots.minOf { e -> wcagContrast(ink, f.over(e)) } }
    }

    /**
     * Clear glass (owner 2026-11-09 "is it that difficult to have the real glass feel", 2026-11-10 "the menu glass still
     * has the milky feeling"): a liquid face is one thin tint and is never thickened into a plate for its words. So the
     * text floor (4.5:1) is kept where the page is at rest ([restContrasts]); over content moving behind the glass the
     * primary words keep at least this, WCAG's 3:1 for large text and controls, and the pane's ink flip
     * ([LiquidInkRule]) turns them to the other theme's ink over content of the other brightness.
     */
    public const val MOVING_FLOOR: Double = 3.0

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
        for (c in claims.sortedWith(compareByDescending<LiquidClaim<K>> { it.rank }.thenBy { it.order })) {
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

/**
 * One floating part asking to be liquid glass: [order] breaks ties between equal roles (earlier first); [rank] is its
 * role's unless the part says otherwise (the edge dock at rest: [LiquidEdge.dockRank]).
 */
public data class LiquidClaim<K>(val key: K, val role: LiquidRole, val source: Any?, val order: Int = 0, val rank: Int = role.rank)

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

    /**
     * Mean luminance and its variance of [argb] pixels (Rec. 709 on sRGB channels, cheap: no linearisation), each pixel
     * counted by its [weights] (glass-truth 2026-11-16: the pane weights where its words lie); null, a size that does not
     * match, or no weight at all counts every pixel alike.
     */
    public fun measure(argb: IntArray, weights: FloatArray? = null): Pair<Float, Float> {
        if (argb.isEmpty()) return 0f to 0f
        val w = weights?.takeIf { it.size == argb.size && it.any { v -> v > 0f } }
        var sum = 0.0
        var sq = 0.0
        var total = 0.0
        for (i in argb.indices) {
            val k = w?.get(i)?.toDouble() ?: 1.0
            if (k <= 0.0) continue
            val p = argb[i]
            val l = (.2126 * ((p shr 16) and 0xFF) + .7152 * ((p shr 8) and 0xFF) + .0722 * (p and 0xFF)) / 255.0
            sum += l * k
            sq += l * l * k
            total += k
        }
        val mean = sum / total
        return mean.toFloat() to (sq / total - mean * mean).coerceAtLeast(0.0).toFloat()
    }
}
