package io.github.eslamasabry.oc.ui.design.effects

import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.color.ThemeResolver
import io.github.eslamasabry.oc.ui.design.color.over
import io.github.eslamasabry.oc.ui.design.color.wcagContrast
import io.github.eslamasabry.oc.ui.design.style.GlassFinish
import io.github.eslamasabry.oc.ui.design.style.GlassLookId
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
        assertNull(FrostPolicy.edgesHeldBy(ready, balanced, GlassLookId.Glossy))
        assertTrue(FrostPolicy.liquidEdges(ready, balanced, GlassLookId.Glossy))
    }

    @Test
    fun `B-KIT-018 below Android 13 the edges keep today's frost or solid face`() {
        assertEquals(EdgeHold.Platform, FrostPolicy.edgesHeldBy(ready.copy(lensSupported = false), balanced, GlassLookId.Glossy))
    }

    @Test
    fun `B-KIT-018 Saver, reduced motion and high contrast hold the edges as they hold frost, in frost's order`() {
        assertEquals(EdgeHold.Saver, FrostPolicy.edgesHeldBy(ready.copy(saver = true, reducedMotion = true), balanced, GlassLookId.Glossy))
        assertEquals(EdgeHold.Motion, FrostPolicy.edgesHeldBy(ready.copy(reducedMotion = true, highContrast = true), balanced, GlassLookId.Glossy))
        assertEquals(EdgeHold.Contrast, FrostPolicy.edgesHeldBy(ready.copy(highContrast = true), balanced, GlassLookId.Glossy))
        // A hold of frost's own comes before the platform: Settings names the reason the person can change.
        assertEquals(EdgeHold.Motion, FrostPolicy.edgesHeldBy(ready.copy(reducedMotion = true, lensSupported = false), balanced, GlassLookId.Glossy))
    }

    @Test
    fun `B-KIT-018 frost Off and the Frosted look keep the edges frosted`() {
        assertEquals(EdgeHold.Off, FrostPolicy.edgesHeldBy(ready, FrostStrength.Off, GlassLookId.Glossy))
        assertEquals(EdgeHold.Frosted, FrostPolicy.edgesHeldBy(ready, balanced, GlassLookId.Frosted))
        // Every look with a lens keeps liquid edges (glass looks 2026-11-09).
        for (look in listOf(GlassLookId.Glossy, GlassLookId.Clear, GlassLookId.Smoked)) assertNull(FrostPolicy.edgesHeldBy(ready, balanced, look), "$look")
        assertEquals(GlassLookId.Glossy, GlassLookId.fromStorage(null))
        assertEquals(GlassLookId.Glossy, GlassLookId.fromStorage("melted"))
        assertEquals(GlassLookId.Frosted, GlassLookId.fromStorage("frosted"))
        assertEquals(GlassLookId.Smoked, GlassLookId.fromStorage("smoked"))
    }

    @Test
    fun `B-KIT-018 the edges are held wherever frost itself is`() {
        val flags = listOf(false, true)
        for (saver in flags) for (motion in flags) for (contrast in flags) for (style in flags) {
            val c = FrostConditions(saver, motion, contrast, style, lensSupported = true)
            val frost = FrostPolicy.heldBy(c)
            val edges = FrostPolicy.edgesHeldBy(c, balanced, GlassLookId.Glossy)
            assertEquals(frost?.name, edges?.name, "$c")
        }
    }

    @Test
    fun `B-KIT-037 only Glass ever has liquid edges`() {
        for (style in Style.entries) {
            val c = ready.copy(styleAllows = style == Style.Glass)
            assertEquals(style == Style.Glass, FrostPolicy.liquidEdges(c, balanced, GlassLookId.Glossy), "$style")
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
    fun `B-KIT-003 text1, text2 and text3 read on the bare face over the content their ink lies over, in every pack, light and dark`() {
        // Owner 2026-11-08: no plate behind words; the face's tint (and blur) carries them. A pane whose words flip to
        // the other theme's ink flips its face to the other theme's too, so both themes' faces cover every content.
        val tints = mutableListOf<String>()
        for (pack in PackId.entries) for (dark in listOf(true, false)) {
            val roles = ThemeResolver.roles(pack, null, dark, saver = false, dynamic = null)
            for (role in LiquidRole.entries) {
                val face = LiquidEdge.face(roles, role)
                assertEquals(0, face.wordsPlate.alpha, "$pack dark=$dark $role: no plate")
                val target = LiquidEdge.inks(roles, role).minOf { it.second }
                val worst = LiquidEdge.worstContrast(roles, role)
                assertTrue(worst >= target - 1e-3, "$pack dark=$dark $role: worst $worst < $target")
                val band = if (role == LiquidRole.Action) worst else LiquidEdge.bandContrast(roles, role)
                tints += "$pack ${if (dark) "dark" else "light"} $role ${face.fill!!.alpha * 100 / 255}% worst ${"%.2f".format(worst)} band ${"%.2f".format(band)}"
            }
        }
        println(tints.joinToString("\n"))
    }

    @Test
    fun `B-KIT-003 a face is measured over the ground, its surfaces, the frosted aurora, blurred words and an accent wash`() {
        val roles = ThemeResolver.roles(PackId.Opencode, null, true, saver = false, dynamic = null)
        val spots = LiquidEdge.themeSpots(roles, LiquidEdge.recipe(LiquidRole.Bar, true))
        assertTrue(spots.size >= 8)
        assertTrue(LiquidEdge.BLURRED_INK in .2f..0.5f)
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
    fun `B-KIT-003 the panes start as clear glass and thicken only as far as their words need`() {
        for (pack in PackId.entries) for (dark in listOf(true, false)) {
            val roles = ThemeResolver.roles(pack, null, dark, saver = false, dynamic = null)
            for (role in LiquidRole.entries.filter { it != LiquidRole.Action }) {
                val a = LiquidEdge.faceTint(roles, role).fill.alpha / 255f
                val thin = LiquidEdge.recipe(role, dark).tint
                assertTrue(a >= thin - 1e-2f, "$pack dark=$dark $role tint $a below the recipe's $thin")
            }
        }
    }

    @Test
    fun `B-KIT-003 a liquid face has no words plate, no white rim and no top glow, and a soft sheen`() {
        val roles = ThemeResolver.roles(PackId.Opencode, null, dark = true, saver = false, dynamic = null)
        val face = LiquidEdge.face(roles, LiquidRole.Composer)
        assertEquals(0, face.wordsPlate.alpha, "owner 2026-11-08: no haze behind the text")
        assertEquals(0f, face.edgeWidthDp)
        assertEquals(0, face.innerGlow.alpha)
        // A soft sheen, or none where it would cost the words more than a little tint (GlassFinish.GLOSS_ALLOWANCE).
        assertTrue(face.sheen.alpha in 0..40, "a soft sheen: ${face.sheen.alpha}")
        val light = LiquidEdge.face(ThemeResolver.roles(PackId.Opencode, null, dark = false, saver = false, dynamic = null), LiquidRole.Composer)
        assertTrue(light.sheen.alpha in 0..80, "a soft sheen in light: ${light.sheen.alpha}")
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
        // bar 1 + page 1, fab 1 + list 1: a budget of 4 is spent; the pill and the status pill drop to frost.
        assertEquals(setOf("bar", "fab"), LiquidEdge.grant(claims, budget = 4))
        assertEquals(setOf("bar", "fab", "pill"), LiquidEdge.grant(claims, budget = 5))
    }

    @Test
    fun `B-KIT-018 opening a menu or a sheet keeps the chat's header, composer and jump pill liquid`() {
        // Owner 2026-11-08: "opening a glass overlay in a sub menu kills all other glass surfaces".
        val page = Any()
        val chrome = listOf(
            LiquidClaim("composer", LiquidRole.Composer, null),
            LiquidClaim("bar", LiquidRole.Bar, null),
            LiquidClaim("pill", LiquidRole.Pill, null),
        )
        for (modal in listOf(LiquidClaim("menu", LiquidRole.Menu, page), LiquidClaim("sheet", LiquidRole.Sheet, page))) {
            val kinds = LiquidEdge.faceOf(chrome + modal)
            assertEquals(LiquidFaceKind.Liquid, kinds.getValue(modal.key))
            for (c in chrome) assertEquals(LiquidFaceKind.Liquid, kinds.getValue(c.key), "${modal.key} open: ${c.key}")
        }
        val both = LiquidEdge.faceOf(chrome + LiquidClaim("sheet", LiquidRole.Sheet, page) + LiquidClaim("menu", LiquidRole.Menu, page))
        assertEquals(chrome.size + 2, both.size, "every part keeps a glass face")
    }

    @Test
    fun `B-KIT-018 opening a reply's menu changes no other pane's face, however many chips the chat shows`() {
        // Smoothness 2026-11-09 (owner: the reply's menu janks in Glass): two of the chat's chips flipped to frost as
        // the menu opened and back as it closed, each rebuilding its glass.
        val page = Any()
        val chat =
            listOf(LiquidClaim("composer", LiquidRole.Composer, null), LiquidClaim("bar", LiquidRole.Bar, null)) +
                (0 until 3).map { LiquidClaim("chip$it", LiquidRole.Pill, null, order = it) }
        val before = LiquidEdge.faceOf(chat)
        for (modal in listOf(listOf(LiquidClaim("menu", LiquidRole.Menu, page)), listOf(LiquidClaim("sheet", LiquidRole.Sheet, page), LiquidClaim("menu", LiquidRole.Menu, page)))) {
            val open = LiquidEdge.faceOf(chat + modal)
            for (c in chat) assertEquals(before.getValue(c.key), open.getValue(c.key), "${modal.map { it.key }} open: ${c.key}")
            for (m in modal) assertEquals(LiquidFaceKind.Liquid, open.getValue(m.key), "${m.key} is liquid")
        }
    }

    @Test
    fun `B-KIT-018 beyond the budget a part is frost of the same shape, never solid`() {
        val claims = (0 until 9).map { LiquidClaim("chip$it", LiquidRole.Pill, null, order = it) }
        val kinds = LiquidEdge.faceOf(claims)
        assertEquals(claims.size, kinds.size, "every claim has a face")
        assertEquals(LiquidEdge.BUDGET, kinds.values.count { it == LiquidFaceKind.Liquid })
        assertTrue(kinds.values.all { it == LiquidFaceKind.Liquid || it == LiquidFaceKind.Frost })
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
