package io.github.eslamasabry.oc.ui.design.effects

import io.github.eslamasabry.oc.ui.design.style.GlassLooks
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The edge dock's glass (owner 2026-11-11, "Why isn't it glass?"): one body, the bar's recipe at rest and the menu's
 * fully grown, mixed by the growth with no step; its claim on the window's lenses ranks between the + and the bars.
 */
class LiquidEdgeDockTest {
    private fun close(a: Float, b: Float, what: String) = assertTrue(abs(a - b) < 1e-4f, "$what: $a, wanted $b")

    @Test
    fun `dock glass - at rest the bar's recipe, fully grown the menu's, halfway every number halfway`() {
        for (dark in listOf(true, false)) for (look in listOf(GlassLooks.Glossy, GlassLooks.of(io.github.eslamasabry.oc.ui.design.style.GlassLookId.Clear))) {
            val bar = LiquidEdge.recipe(LiquidRole.Bar, dark, look)
            val menu = LiquidEdge.recipe(LiquidRole.Menu, dark, look)
            assertEquals(bar, LiquidEdge.recipe(LiquidRole.Dock, dark, look), "the dock's own recipe is the bar's (dark=$dark)")
            assertEquals(bar, LiquidEdge.dockRecipe(dark, 0f, look), "at rest (dark=$dark)")
            assertEquals(menu, LiquidEdge.dockRecipe(dark, 1f, look), "fully grown (dark=$dark)")
            assertEquals(menu, LiquidEdge.dockRecipe(dark, 1.15f, look), "the growth's overshoot is clamped (dark=$dark)")
            val half = LiquidEdge.dockRecipe(dark, .5f, look)
            close(half.blurDp, (bar.blurDp + menu.blurDp) / 2f, "blur dark=$dark")
            close(half.refractionDp, (bar.refractionDp + menu.refractionDp) / 2f, "bend dark=$dark")
            close(half.rimMaxDp, (bar.rimMaxDp + menu.rimMaxDp) / 2f, "rim width dark=$dark")
            close(half.rimShare, (bar.rimShare + menu.rimShare) / 2f, "rim share dark=$dark")
            close(half.tint, (bar.tint + menu.tint) / 2f, "tint dark=$dark")
            close(half.saturation, bar.saturation, "saturation dark=$dark")
        }
    }

    @Test
    fun `dock glass - the mix has no step as the body grows`() {
        for (dark in listOf(true, false)) {
            var last = LiquidEdge.dockRecipe(dark, 0f)
            for (i in 1..100) {
                val now = LiquidEdge.dockRecipe(dark, i / 100f)
                assertTrue(abs(now.blurDp - last.blurDp) <= .05f && abs(now.refractionDp - last.refractionDp) <= .2f, "a step at ${i / 100f}")
                assertTrue(abs(now.rimMaxDp - last.rimMaxDp) <= .05f && abs(now.tint - last.tint) <= .01f, "a step at ${i / 100f}")
                last = now
            }
        }
    }

    @Test
    fun `dock glass - its rank lies between the + and the bars`() {
        assertTrue(LiquidRole.Dock.rank > LiquidRole.Action.rank)
        assertTrue(LiquidRole.Dock.rank < LiquidRole.Bar.rank)
        assertTrue(!LiquidRole.Dock.modal, "the dock takes a slot of the page's budget, not the modal allowance")
        // The order of the roles as ranks stays: menu, sheet, message box, bar, dock, +, pill, status.
        assertEquals(LiquidRole.entries.sortedByDescending { it.rank }, LiquidRole.entries)
    }

    @Test
    fun `dock glass - a dock claim yields to a bar and the message box and beats a pill and a status pill`() {
        // A budget of one pane: the higher rank takes it, whatever came first.
        for (higher in listOf(LiquidRole.Bar, LiquidRole.Composer)) {
            val claims = listOf(LiquidClaim("dock", LiquidRole.Dock, null, order = 0), LiquidClaim("other", higher, null, order = 1))
            assertEquals(setOf("other"), LiquidEdge.grant(claims, budget = 1), "$higher over the dock")
        }
        for (lower in listOf(LiquidRole.Pill, LiquidRole.Status, LiquidRole.Action)) {
            val claims = listOf(LiquidClaim("other", lower, null, order = 0), LiquidClaim("dock", LiquidRole.Dock, null, order = 1))
            assertEquals(setOf("dock"), LiquidEdge.grant(claims, budget = 1), "the dock over $lower")
        }
        // Sheets and menus outrank it and keep their own allowance: they never take its slot, nor it theirs.
        assertTrue(LiquidRole.Sheet.rank > LiquidRole.Dock.rank && LiquidRole.Menu.rank > LiquidRole.Dock.rank)
        val page = Any()
        val claims =
            listOf(LiquidClaim("dock", LiquidRole.Dock, null), LiquidClaim("sheet", LiquidRole.Sheet, page), LiquidClaim("menu", LiquidRole.Menu, page))
        assertEquals(setOf("dock", "sheet", "menu"), LiquidEdge.grant(claims, budget = 1))
    }

    /**
     * What loses its lens when the dock appears. A chat: the bar, the message box, the jump pill and the chips over the
     * message box claim; every one reads the transcript's recording (a feature's recording: free). Before the dock
     * the budget of 5 holds the message box, the bar and three pills; with it, two pills: the third pill or chip (the
     * last to claim) is drawn as frost of the same shape. Home: the floating bar over the page the kit records (costs
     * 2), the + over the list's recording: the dock fits beside them, nothing loses its lens.
     */
    @Test
    fun `dock glass - in a chat the last pill or chip loses its lens to the dock, at home nothing does`() {
        val chat =
            listOf(LiquidClaim("composer", LiquidRole.Composer, null), LiquidClaim("bar", LiquidRole.Bar, null)) +
                listOf("jump", "chip0", "chip1").mapIndexed { i, k -> LiquidClaim(k, LiquidRole.Pill, null, order = i) }
        assertEquals(setOf("composer", "bar", "jump", "chip0", "chip1"), LiquidEdge.grant(chat))
        val withDock = chat + LiquidClaim("dock", LiquidRole.Dock, null, order = 10)
        assertEquals(setOf("composer", "bar", "dock", "jump", "chip0"), LiquidEdge.grant(withDock))
        // With only the jump pill out (no chips) nothing loses its lens.
        val quiet = chat.take(3) + LiquidClaim("dock", LiquidRole.Dock, null, order = 10)
        assertEquals(setOf("composer", "bar", "jump", "dock"), LiquidEdge.grant(quiet))
        val page = Any()
        val home =
            listOf(LiquidClaim("bar", LiquidRole.Bar, page), LiquidClaim("plus", LiquidRole.Action, null), LiquidClaim("dock", LiquidRole.Dock, null))
        assertEquals(setOf("bar", "plus", "dock"), LiquidEdge.grant(home))
    }
}
