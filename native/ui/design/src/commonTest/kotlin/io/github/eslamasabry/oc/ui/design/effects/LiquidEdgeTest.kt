package io.github.eslamasabry.oc.ui.design.effects

import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.color.ThemeResolver
import io.github.eslamasabry.oc.ui.design.color.over
import io.github.eslamasabry.oc.ui.design.color.wcagContrast
import io.github.eslamasabry.oc.ui.design.style.GlassFinish
import io.github.eslamasabry.oc.ui.design.style.Style
import kotlin.math.abs
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
    fun `B-KIT-003 the lens bends only in a rim of 12 dp or a fifth of the shorter side`() {
        assertEquals(12f * 3f, LiquidEdge.rimPx(56f * 3f * 4f, 3f))
        assertEquals(.2f * 40f * 3f, LiquidEdge.rimPx(40f * 3f, 3f))
        for (role in LiquidRole.entries) for (dark in listOf(true, false)) {
            val r = LiquidEdge.recipe(role, dark)
            assertTrue(r.refractionDp in 6f..20f, "$role refraction ${r.refractionDp} stays moderate")
        }
    }

    @Test
    fun `B-KIT-003 text1, text2 and text3 read on every pane over any content, in every pack, light and dark`() {
        for (pack in PackId.entries) for (dark in listOf(true, false)) {
            val roles = ThemeResolver.roles(pack, null, dark, saver = false, dynamic = null)
            for (role in LiquidRole.entries) {
                val face = LiquidEdge.face(roles, role)
                val (darkEnd, brightEnd) = LiquidEdge.backdropEnds(LiquidEdge.recipe(role, dark))
                val solid = LiquidEdge.solidOf(roles, role)
                val inks =
                    if (role == LiquidRole.Action) listOf(roles.onAccent to minOf(3.0, wcagContrast(roles.onAccent, solid)))
                    else listOf(roles.text1, roles.text2, roles.text3).map { it to minOf(4.5, wcagContrast(it, solid)) }
                for (fill in listOfNotNull(face.fill, face.fillStart)) for (under in listOf(roles.ground.solid(), darkEnd, brightEnd)) {
                    for ((ink, target) in inks) {
                        val c = wcagContrast(ink, fill.over(under))
                        assertTrue(c >= target - 1e-3, "$pack dark=$dark $role: $ink on ${fill.over(under)} is $c < $target")
                    }
                }
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
    fun `B-KIT-003 sheets keep their tint floor and Graphite's bars stay glass`() {
        for (dark in listOf(true, false)) {
            val roles = ThemeResolver.roles(PackId.Opencode, null, dark, saver = false, dynamic = null)
            val sheet = LiquidEdge.face(roles, LiquidRole.Sheet)
            assertTrue(sheet.fill!!.alpha / 255f >= LiquidEdge.tintFloor(LiquidRole.Sheet, dark) - .01f, "dark=$dark sheet")
            for (role in listOf(LiquidRole.Bar, LiquidRole.Composer, LiquidRole.Pill)) {
                val a = LiquidEdge.face(roles, role).fill!!.alpha / 255f
                assertTrue(a < .9f, "dark=$dark $role tint $a: still glass, not a solid face")
            }
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

    @Test
    fun `B-KIT-003 the rim light is the accent leaning cool in dark and warm in light, never white`() {
        for (pack in PackId.entries) {
            val dark = RimLight.of(ThemeResolver.roles(pack, null, true, saver = false, dynamic = null))
            val light = RimLight.of(ThemeResolver.roles(pack, null, false, saver = false, dynamic = null))
            assertNotEquals(dark, light, "$pack")
            for ((c, anchor) in listOf(dark to RimLight.NIGHT_HUE, light to RimLight.DAY_HUE)) {
                val (_, chroma, hue) = RimLight.oklch(c)
                assertTrue(chroma >= .03, "$pack $c has colour (chroma $chroma)")
                assertTrue(hueGap(hue, anchor) <= 95.0, "$pack $c hue $hue leans to $anchor")
            }
        }
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

    private fun Argb.solid(): Argb = Argb(v or (0xFF shl 24))

    private fun hueGap(a: Double, b: Double): Double {
        val d = abs(a - b) % 360.0
        return if (d > 180.0) 360.0 - d else d
    }
}
