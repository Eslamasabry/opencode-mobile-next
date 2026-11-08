package io.github.eslamasabry.oc.ui.design.effects

import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.Roles
import io.github.eslamasabry.oc.ui.design.color.over
import io.github.eslamasabry.oc.ui.design.color.relativeLuminance
import io.github.eslamasabry.oc.ui.design.color.wcagContrast
import io.github.eslamasabry.oc.ui.design.style.Style
import kotlin.math.roundToInt

/*
 * Liquid glass (owner, 2026-11-08: "Build it now for native"): under the Glass style the floating parts are real glass
 * over the content behind them, through Kyant0/AndroidLiquidGlass (Apache-2.0): the content is captured, its contrast
 * pressed together, blurred a little and bent at the rim (refraction, with a little dispersion), then laid under a
 * tint of the theme and a light rim. Everything here is pure: what is drawn, and when today's frost or solid face is
 * drawn instead. The kit (`KitLiquidGlass`) paints it.
 */

/** The floating parts that become liquid glass. Every other surface keeps its Glass pane. */
public enum class LiquidRole { Bar, Sheet, Menu, Composer, Floating, Action }

/** Why a floating part keeps today's frost or solid face instead of liquid glass; null in [LiquidGlass.holdOf] = liquid. */
public enum class LiquidHold {
    /** Battery saver: the style in force is Solid. */
    Saver,

    /** Reduced motion or motion off: frost is held solid (FrostPolicy's Motion hold). */
    Motion,

    /** Platform high contrast: the style in force is Solid. */
    Contrast,

    /** A style other than Glass: nothing changes. */
    Style,

    /** The platform has no runtime shaders (Android below 13): the refraction cannot run. */
    Platform,

    /** The person turned frost off. */
    Off,
}

/** What liquid glass depends on. [runtimeShaders] is Android 13+ (and desktop): the library's lens needs it. */
public data class LiquidConditions(
    val style: Style,
    val runtimeShaders: Boolean,
    val saver: Boolean = false,
    val reducedMotion: Boolean = false,
    val highContrast: Boolean = false,
    val frostOff: Boolean = false,
)

/**
 * One liquid pane's recipe, in dp. The backdrop is first pressed together ([saturation], [contrast], [brightness]:
 * the library's `colorControls`, the same matrix as [LiquidGlass.filtered]), then blurred by [blurDp], then bent by the
 * lens: [refractionHeightDp] is how far in from the rim the glass curves, [refractionAmountDp] how far the light bends
 * there, [dispersion] splits it a little into colour at the rim, [depth] makes the middle read thicker. [rimAlpha] is
 * the light along the rim (a 45° highlight, brighter where the light falls).
 */
public data class LiquidRecipe(
    val saturation: Float,
    val contrast: Float,
    val brightness: Float,
    val blurDp: Float,
    val refractionHeightDp: Float,
    val refractionAmountDp: Float,
    val dispersion: Boolean,
    val depth: Boolean,
    val rimAlpha: Float,
    val rimWidthDp: Float,
    /** The soft shadow under the pane, at this alpha of the theme's elevation shadow (0: none). */
    val shadowAlpha: Float,
)

public object LiquidGlass {
    /** AA (4.5:1) with GlassFinish's small margin, for the words on a pane. */
    public const val TEXT_FLOOR: Double = 4.5 + .04

    /** Non-text contrast (3:1) for a glyph on an action's face (the floating +). */
    public const val GLYPH_FLOOR: Double = 3.0 + .04

    /** The thinnest tint a pane ever has, so glass always reads as a surface, never as a hole. */
    public const val MIN_TINT: Float = .22f

    private const val STEPS: Int = 14

    /**
     * The first hold that applies: [FrostPolicy.heldBy]'s own (Saver, Motion, Contrast, Style, in its order, so liquid
     * glass is held exactly where frost is), then Platform and Off.
     */
    public fun holdOf(c: LiquidConditions): LiquidHold? {
        val frost = FrostConditions(c.saver, c.reducedMotion, c.highContrast, styleAllows = c.style == Style.Glass)
        return when (FrostPolicy.heldBy(frost)) {
            FrostHold.Saver -> LiquidHold.Saver
            FrostHold.Motion -> LiquidHold.Motion
            FrostHold.Contrast -> LiquidHold.Contrast
            FrostHold.Style -> LiquidHold.Style
            null ->
                when {
                    !c.runtimeShaders -> LiquidHold.Platform
                    c.frostOff -> LiquidHold.Off
                    else -> null
                }
        }
    }

    /** Whether a floating part is liquid glass under [c]. Every hold draws today's face instead. */
    public fun isLiquid(c: LiquidConditions): Boolean = holdOf(c) == null

    /**
     * The recipe for [role] in a [dark] or light theme. Bars and the composer are thin and bend a lot at their rounded
     * ends; sheets and menus are thicker panes (more blur, a deeper lens) that carry many words; a floating pill or the
     * + is a small drop of glass. A light theme lifts its backdrop towards white, a dark one presses it towards black.
     */
    public fun recipe(role: LiquidRole, dark: Boolean): LiquidRecipe {
        val lift = if (dark) -PRESS_LIFT else PRESS_LIFT
        val actionLift = if (dark) -.10f else .10f
        return when (role) {
            LiquidRole.Bar -> LiquidRecipe(SATURATION, PRESS, lift, 4f, 18f, 30f, true, false, .55f, 1f, .10f)
            LiquidRole.Composer -> LiquidRecipe(SATURATION, PRESS, lift, 4f, 16f, 28f, true, false, .55f, 1f, .12f)
            LiquidRole.Sheet -> LiquidRecipe(SATURATION, PRESS, lift, if (dark) 10f else 14f, 24f, 44f, true, true, .45f, 1f, 0f)
            LiquidRole.Menu -> LiquidRecipe(SATURATION, PRESS, lift, if (dark) 8f else 12f, 20f, 36f, true, true, .50f, 1f, .16f)
            LiquidRole.Floating -> LiquidRecipe(SATURATION, PRESS, lift, 2f, 12f, 22f, true, false, .60f, 1f, .14f)
            LiquidRole.Action -> LiquidRecipe(1.2f, .40f, actionLift, 2f, 14f, 24f, true, false, .55f, 1f, .20f)
        }
    }

    /** How much colour the glass lifts from what lies behind it (the library's vibrancy is 1.5). */
    public const val SATURATION: Float = 1.3f

    /** How far the glass presses the backdrop's contrast together (1 keeps it). */
    public const val PRESS: Float = .36f

    /** How far the pressed backdrop moves towards the theme: lighter in a light theme, darker in a dark one. */
    public const val PRESS_LIFT: Float = .33f

    /** The colour a [role] pane is tinted with: the theme's own ground (dark) or its lightest surface (light); the + its accent. */
    public fun tintOf(roles: Roles, role: LiquidRole): Argb =
        when {
            role == LiquidRole.Action -> roles.accent
            roles.dark -> roles.ground
            else -> roles.surface2
        }.let { Argb(it.v or (0xFF shl 24)) }

    /** The opaque Solid surface a [role] pane stands for: Glass is never asked to read better than Solid does. */
    public fun solidOf(roles: Roles, role: LiquidRole): Argb =
        when (role) {
            LiquidRole.Action -> roles.accent
            LiquidRole.Bar, LiquidRole.Composer -> roles.ground
            LiquidRole.Sheet, LiquidRole.Menu, LiquidRole.Floating -> roles.surface2
        }.let { Argb(it.v or (0xFF shl 24)) }.let { it.over(Argb(roles.ground.v or (0xFF shl 24))) }

    /** The words (or the +'s glyph) that must read on a [role] pane, with the contrast each must reach. */
    public fun inks(roles: Roles, role: LiquidRole): List<Pair<Argb, Double>> {
        val solid = solidOf(roles, role)
        return if (role == LiquidRole.Action) {
            listOf(roles.onAccent to minOf(GLYPH_FLOOR, wcagContrast(roles.onAccent, solid)))
        } else {
            listOf(roles.text1, roles.text2).map { it to minOf(TEXT_FLOOR, wcagContrast(it, solid)) }
        }
    }

    /**
     * [c] through the recipe's colour controls, as the library's `colorControls` matrix applies them (sRGB channels,
     * saturation about Rec. 709-ish luma 0.213/0.715/0.072, then contrast about mid grey, then brightness).
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
     * The darkest and the brightest colour the backdrop can reach after the colour controls, per channel: the
     * controls are affine per channel, so over every colour a page can hold (a white page, a photo, a code block) each
     * channel stays between its values at the RGB cube's corners. The blur and the lens only mix such colours and the
     * dispersion only moves channels apart, so every colour seen through the glass lies in this box, and (luminance
     * rising with each channel) its luminance lies between the box's two ends.
     */
    public fun extremes(recipe: LiquidRecipe): Pair<Argb, Argb> {
        val corners =
            listOf(0x000000, 0xFFFFFF, 0xFF0000, 0x00FF00, 0x0000FF, 0x00FFFF, 0xFF00FF, 0xFFFF00)
                .map { filtered(Argb(it or (0xFF shl 24)), recipe) }
        fun box(pick: (List<Int>) -> Int): Argb =
            Argb((0xFF shl 24) or (pick(corners.map { it.red }) shl 16) or (pick(corners.map { it.green }) shl 8) or pick(corners.map { it.blue }))
        return box { it.min() } to box { it.max() }
    }

    /**
     * Whether every ink of [role] reads on [face] over every backdrop colour: the faces over the box's two ends lie on
     * the same side of the ink (never a backdrop where the words vanish in between) and each reaches the ink's target.
     */
    private fun reads(roles: Roles, role: LiquidRole, face: Argb, ends: Pair<Argb, Argb>): Boolean {
        val dark = face.over(ends.first)
        val bright = face.over(ends.second)
        for ((ink, target) in inks(roles, role)) {
            val l = relativeLuminance(ink)
            val oneSide = (relativeLuminance(bright) < l) || (relativeLuminance(dark) > l)
            if (!oneSide || wcagContrast(ink, dark) < target - 1e-6 || wcagContrast(ink, bright) < target - 1e-6) return false
        }
        return true
    }

    /**
     * The thinnest tint (alpha of [tintOf]) at which every ink of [role] reaches its contrast over any backdrop, never
     * below [MIN_TINT]; 1 (an opaque tint, the Solid look) when nothing thinner reads.
     */
    public fun tintAlpha(roles: Roles, role: LiquidRole, recipe: LiquidRecipe = recipe(role, roles.dark)): Float {
        val tint = tintOf(roles, role)
        val ends = extremes(recipe)
        if (reads(roles, role, tint.withAlpha(MIN_TINT), ends)) return MIN_TINT
        if (!reads(roles, role, tint, ends)) return 1f
        var lo = MIN_TINT
        var hi = 1f
        repeat(STEPS) {
            val mid = (lo + hi) / 2f
            if (reads(roles, role, tint.withAlpha(mid), ends)) hi = mid else lo = mid
        }
        return hi
    }

    /** The lowest contrast any ink of [role] has over the backdrop's two ends at tint [alpha] (tests, the QA note). */
    public fun worstContrast(roles: Roles, role: LiquidRole, alpha: Float, recipe: LiquidRecipe = recipe(role, roles.dark)): Double {
        val face = tintOf(roles, role).withAlpha(alpha)
        val (dark, bright) = extremes(recipe)
        return inks(roles, role).minOf { (ink, _) -> minOf(wcagContrast(ink, face.over(dark)), wcagContrast(ink, face.over(bright))) }
    }
}
