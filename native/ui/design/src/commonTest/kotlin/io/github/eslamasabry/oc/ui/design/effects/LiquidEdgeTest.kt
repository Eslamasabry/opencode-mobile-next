package io.github.eslamasabry.oc.ui.design.effects

import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.color.ThemeResolver
import io.github.eslamasabry.oc.ui.design.color.over
import io.github.eslamasabry.oc.ui.design.color.wcagContrast
import io.github.eslamasabry.oc.ui.design.style.GlassFinish
import io.github.eslamasabry.oc.ui.design.style.Style
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Glass's liquid edges (docs/designs/liquid-glass.md v2, owner 2026-11-08). Extends the rows of B-KIT-018 (frost's
 * holds), B-KIT-037 (other styles untouched) and B-KIT-003 (contrast floors); the test-writer lead assigns the liquid
 * glass rows.
 */
class LiquidEdgeTest {
    private val ready = FrostConditions(styleAllows = true, lensSupported = true)
    private val balanced = FrostStrength.Balanced

    // ---- when (FrostPolicy)

    @Test
    fun `B-KIT-018 Glass on Android 13 or later with nothing held has liquid edges`() {
        assertNull(FrostPolicy.edgesHeldBy(ready, balanced, GlassEdges.Liquid))
        assertTrue(FrostPolicy.liquidEdges(ready, balanced, GlassEdges.Liquid))
    }

    @Test
    fun `B-KIT-018 below Android 13 the edges keep today's frost or solid face`() {
        assertEquals(EdgeHold.Platform, FrostPolicy.edgesHeldBy(ready.copy(lensSupported = false), balanced, GlassEdges.Liquid))
    }

    @Test
    fun `B-KIT-018 Saver, reduced motion and high contrast hold the edges as they hold frost, in frost's order`() {
        assertEquals(EdgeHold.Saver, FrostPolicy.edgesHeldBy(ready.copy(saver = true, reducedMotion = true), balanced, GlassEdges.Liquid))
        assertEquals(EdgeHold.Motion, FrostPolicy.edgesHeldBy(ready.copy(reducedMotion = true, highContrast = true), balanced, GlassEdges.Liquid))
        assertEquals(EdgeHold.Contrast, FrostPolicy.edgesHeldBy(ready.copy(highContrast = true), balanced, GlassEdges.Liquid))
        // A hold of frost's own comes before the platform: Settings names the reason the person can change.
        assertEquals(EdgeHold.Motion, FrostPolicy.edgesHeldBy(ready.copy(reducedMotion = true, lensSupported = false), balanced, GlassEdges.Liquid))
    }

    @Test
    fun `B-KIT-018 frost Off and the Frosted choice keep the edges frosted`() {
        assertEquals(EdgeHold.Off, FrostPolicy.edgesHeldBy(ready, FrostStrength.Off, GlassEdges.Liquid))
        assertEquals(EdgeHold.Frosted, FrostPolicy.edgesHeldBy(ready, balanced, GlassEdges.Frosted))
        assertEquals(GlassEdges.Liquid, GlassEdges.fromStorage(null))
        assertEquals(GlassEdges.Liquid, GlassEdges.fromStorage("melted"))
        assertEquals(GlassEdges.Frosted, GlassEdges.fromStorage("frosted"))
    }

    @Test
    fun `B-KIT-018 the edges are held wherever frost itself is`() {
        val flags = listOf(false, true)
        for (saver in flags) for (motion in flags) for (contrast in flags) for (style in flags) {
            val c = FrostConditions(saver, motion, contrast, style, lensSupported = true)
            val frost = FrostPolicy.heldBy(c)
            val edges = FrostPolicy.edgesHeldBy(c, balanced, GlassEdges.Liquid)
            assertEquals(frost?.name, edges?.name, "$c")
        }
    }

    @Test
    fun `B-KIT-037 only Glass ever has liquid edges`() {
        for (style in Style.entries) {
            val c = ready.copy(styleAllows = style == Style.Glass)
            assertEquals(style == Style.Glass, FrostPolicy.liquidEdges(c, balanced, GlassEdges.Liquid), "$style")
        }
    }

    // ---- look

    @Test
    fun `B-KIT-003 the lens bends in a rim of at most its share of the shorter side, the middle stays calm`() {
        val bar = LiquidEdge.recipe(LiquidRole.Bar, dark = true)
        assertEquals(bar.rimMaxDp * 3f, LiquidEdge.rimPx(400f * 3f, 3f, bar))
        assertEquals(bar.rimShare * 40f * 3f, LiquidEdge.rimPx(40f * 3f, 3f, bar))
        assertTrue(bar.rimShare < .5f, "a bar keeps a calm middle")
        assertTrue(LiquidEdge.recipe(LiquidRole.Action, true).depth, "the + is a drop of glass")
        assertTrue(!bar.depth, "no depth effect on panes that carry words")
    }

    @Test
    fun `B-KIT-003 text1, text2 and text3 read on their words plate over any content, in every pack, light and dark`() {
        for (pack in PackId.entries) for (dark in listOf(true, false)) {
            val roles = ThemeResolver.roles(pack, null, dark, saver = false, dynamic = null)
            for (role in LiquidRole.entries) {
                val target = LiquidEdge.inks(roles, role).minOf { it.second }
                val worst = LiquidEdge.worstContrast(roles, role)
                assertTrue(worst >= target - 1e-3, "$pack dark=$dark $role: worst $worst < $target")
            }
        }
    }

    @Test
    fun `B-KIT-003 every colour a page can show lies between the backdrop's two ends`() {
        val recipe = LiquidEdge.recipe(LiquidRole.Sheet, dark = true)
        val (lo, hi) = LiquidEdge.backdropEnds(recipe)
        val steps = listOf(0, 51, 102, 153, 204, 255)
        for (r in steps) for (g in steps) for (b in steps) {
            val f = LiquidEdge.filtered(Argb((0xFF shl 24) or (r shl 16) or (g shl 8) or b), recipe)
            assertTrue(f.red in lo.red..hi.red && f.green in lo.green..hi.green && f.blue in lo.blue..hi.blue, "$f")
        }
    }

    @Test
    fun `B-KIT-003 the panes are clear glass and the words get the plate`() {
        for (dark in listOf(true, false)) {
            val roles = ThemeResolver.roles(PackId.Opencode, null, dark, saver = false, dynamic = null)
            for (role in LiquidRole.entries.filter { it != LiquidRole.Action }) {
                val a = LiquidEdge.face(roles, role).fill!!.alpha / 255f
                assertTrue(a in .1f..0.42f, "dark=$dark $role tint $a: clear glass")
            }
            assertTrue(LiquidEdge.wordsPlate(roles, LiquidRole.Bar).alpha > 0, "dark=$dark: the bar's words need their plate")
        }
    }

    @Test
    fun `B-KIT-003 a liquid face keeps today's words plate but draws no white rim or top glow`() {
        val roles = ThemeResolver.roles(PackId.Opencode, null, dark = true, saver = false, dynamic = null)
        val face = LiquidEdge.face(roles, LiquidRole.Composer)
        assertNotEquals(0, face.wordsPlate.alpha, "hook H10: the words plate stays")
        assertEquals(0f, face.edgeWidthDp)
        assertEquals(0, face.innerGlow.alpha)
        assertTrue(GlassFinish.FLOOR > 4.5)
    }

    // ---- the cap

    @Test
    fun `B-KIT-018 a chat holds its composer, header and pill liquid`() {
        val claims = listOf(
            LiquidClaim("composer", LiquidRole.Composer, null),
            LiquidClaim("bar", LiquidRole.Bar, null),
            LiquidClaim("pill", LiquidRole.Pill, null),
        )
        assertEquals(setOf("composer", "bar", "pill"), LiquidEdge.grant(claims))
    }

    @Test
    fun `B-KIT-018 a page recording counts once, and the lowest surfaces drop first`() {
        val page = Any()
        val list = Any()
        val claims = listOf(
            LiquidClaim("status", LiquidRole.Status, null),
            LiquidClaim("fab", LiquidRole.Action, list),
            LiquidClaim("bar", LiquidRole.Bar, page),
            LiquidClaim("pill", LiquidRole.Pill, list),
        )
        // bar 1 + page 1, fab 1 + list 1: the budget of 4 is spent; the pill and the status pill drop to frost.
        assertEquals(setOf("bar", "fab"), LiquidEdge.grant(claims, budget = 4))
        assertEquals(setOf("bar", "fab", "pill"), LiquidEdge.grant(claims, budget = 5))
    }

    @Test
    fun `B-KIT-018 a sheet over a chat records the page once and the chrome under it drops to frost`() {
        val page = Any()
        val claims = listOf(
            LiquidClaim("composer", LiquidRole.Composer, null),
            LiquidClaim("bar", LiquidRole.Bar, null),
            LiquidClaim("sheet", LiquidRole.Sheet, page),
        )
        assertEquals(setOf("sheet"), LiquidEdge.grant(claims))
        val menu = LiquidClaim("menu", LiquidRole.Menu, page)
        assertEquals(setOf("menu", "sheet"), LiquidEdge.grant(claims + menu))
    }

    // ---- adaptive ink

    @Test
    fun `B-KIT-003 a pane flips to dark ink over bright content and back to light over dark, with hysteresis`() {
        assertEquals(LiquidInk.Dark, LiquidInkRule.next(LiquidInk.Light, .80f))
        assertEquals(LiquidInk.Light, LiquidInkRule.next(LiquidInk.Dark, .20f))
        // Mid-grey content keeps whatever ink the pane has: no flicker as it scrolls past.
        assertEquals(LiquidInk.Light, LiquidInkRule.next(LiquidInk.Light, .55f))
        assertEquals(LiquidInk.Dark, LiquidInkRule.next(LiquidInk.Dark, .50f))
        assertTrue(LiquidInkRule.TO_DARK - LiquidInkRule.TO_LIGHT >= .15f)
        assertEquals(LiquidInk.Light, LiquidInkRule.of(dark = true))
        assertEquals(LiquidInk.Dark, LiquidInkRule.of(dark = false))
        assertTrue(LiquidInkRule.FLIP_MS in 150..200)
    }

    @Test
    fun `B-KIT-003 the sample's mean luminance and variance tell bright, dark and busy content apart`() {
        val white = IntArray(64) { 0xFFFFFFFF.toInt() }
        val black = IntArray(64) { 0xFF000000.toInt() }
        val stripes = IntArray(64) { if (it % 2 == 0) 0xFFFFFFFF.toInt() else 0xFF000000.toInt() }
        assertEquals(1f, LiquidInkRule.measure(white).first, 1e-3f)
        assertEquals(0f, LiquidInkRule.measure(black).first, 1e-3f)
        assertTrue(LiquidInkRule.measure(white).second < LiquidInkRule.BUSY)
        assertTrue(LiquidInkRule.measure(stripes).second > LiquidInkRule.BUSY, "black-and-white stripes are busy")
    }

    private fun Argb.solid(): Argb = Argb(v or (0xFF shl 24))
}
