package io.github.eslamasabry.oc.ui.design.effects

import io.github.eslamasabry.oc.ui.design.style.GlassLooks
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Words on glass 3 (owner 2026-11-11, device sweep): menus frost as sheets do and the sheet's and the menu's glass hold
 * back the light and the colour behind their words. Only those two roles; the bars, the message box, the pills, the +
 * and the edge dock keep what they were.
 */
class LiquidWordsHoldTest {
    private fun close(a: Float, b: Float, what: String, eps: Float = 1e-4f) = assertTrue(abs(a - b) < eps, "$what: $a, wanted $b")

    @Test
    fun `a menu frosts as a sheet does in both themes and the refraction is unchanged`() {
        for (dark in listOf(true, false)) {
            val menu = LiquidEdge.recipe(LiquidRole.Menu, dark)
            val sheet = LiquidEdge.recipe(LiquidRole.Sheet, dark)
            assertEquals(12f, menu.blurDp, "menu blur dark=$dark")
            assertEquals(sheet.blurDp, menu.blurDp)
            assertEquals(48f, menu.refractionDp, "menu bend dark=$dark")
            assertEquals(26f, menu.rimMaxDp, "menu rim dark=$dark")
            assertEquals(if (dark) .20f else .10f, menu.tint, "menu tint dark=$dark")
        }
    }

    @Test
    fun `only the menu and the sheet hold back light and colour`() {
        for (dark in listOf(true, false)) for (role in LiquidRole.entries) {
            val hold = LiquidEdge.recipe(role, dark).hold
            val wants = role == LiquidRole.Menu || role == LiquidRole.Sheet
            assertEquals(wants, hold.knee, "$role knee dark=$dark")
            assertEquals(if (wants) LiquidEdge.HOLD_CHROMA else 0f, hold.chroma, "$role colour hold dark=$dark")
        }
    }

    @Test
    fun `the dock's strip, swell and list never hold anything and keep the swell's old numbers`() {
        for (dark in listOf(true, false)) {
            for (g in listOf(0f, .3f, .5f, 1f)) for (list in listOf(0f, .5f, 1f)) {
                assertEquals(LiquidHold.Off, LiquidEdge.dockRecipe(dark, g, GlassLooks.Glossy, list).hold, "growth $g list $list dark=$dark")
            }
            val swell = LiquidEdge.dockRecipe(dark, 1f)
            assertEquals(if (dark) 6f else 3f, swell.blurDp, "the swell's blur (dark=$dark)")
        }
    }

    @Test
    fun `mixing carries the hold of the nearer end`() {
        val bar = LiquidEdge.recipe(LiquidRole.Bar, true)
        val menu = LiquidEdge.recipe(LiquidRole.Menu, true)
        assertEquals(LiquidHold.Off, LiquidEdge.mix(bar, menu, .4f).hold)
        assertEquals(menu.hold, LiquidEdge.mix(bar, menu, .6f).hold)
    }

    @Test
    fun `the held colour is mirrored under dark ink and never leaves 0 to 1`() {
        val hold = LiquidEdge.recipe(LiquidRole.Sheet, false).hold
        for (r in 0..4) for (g in 0..4) for (b in 0..4) for (ink in listOf(true, false)) {
            val c = floatArrayOf(r / 4f, g / 4f, b / 4f)
            val out = LiquidEdge.heldRgb(c, ink, hold)
            assertTrue(out.all { it in 0f..1f }, "range for ${c.toList()} ink $ink")
            // Less colour than the knee alone leaves, never more.
            val knee = LiquidEdge.heldRgb(c, ink, LiquidHold(true, 0f))
            assertTrue(out.max() - out.min() <= knee.max() - knee.min() + 1e-4f, "more colour than the knee for ${c.toList()} ink $ink")
            // The light is the knee's.
            val l = { x: FloatArray -> 0.213f * x[0] + 0.715f * x[1] + 0.072f * x[2] }
            if (out.all { it > 0.001f && it < 0.999f }) close(l(out), l(knee), "light for ${c.toList()}", 2e-3f)
        }
    }

    @Test
    fun `light words keep the order of brightness and colour held back is continuous across the knee`() {
        val hold = LiquidEdge.recipe(LiquidRole.Menu, true).hold
        var last = LiquidEdge.heldRgb(floatArrayOf(0f, .30f, 0f), true, hold)
        for (i in 31..100) {
            val now = LiquidEdge.heldRgb(floatArrayOf(0f, i / 100f, 0f), true, hold)
            assertTrue(abs(now[1] - last[1]) < .03f, "a step in the held green at ${i / 100f}: ${last[1]} to ${now[1]}")
            last = now
        }
    }
}
