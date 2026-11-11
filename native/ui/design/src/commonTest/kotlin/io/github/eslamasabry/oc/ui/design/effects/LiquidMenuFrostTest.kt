package io.github.eslamasabry.oc.ui.design.effects

import io.github.eslamasabry.oc.ui.design.style.GlassLooks
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Words on glass 3 (owner 2026-11-11, device sweep), item 1: a menu frosts as a sheet does, in both themes, and the glass
 * that derives from the Menu recipe (the edge dock's swell) keeps what it was.
 */
class LiquidMenuFrostTest {
    @Test
    fun `a menu frosts as a sheet does in both themes and the refraction, rim and tint are unchanged`() {
        for (dark in listOf(true, false)) {
            val menu = LiquidEdge.recipe(LiquidRole.Menu, dark)
            val sheet = LiquidEdge.recipe(LiquidRole.Sheet, dark)
            assertEquals(12f, menu.blurDp, "menu blur dark=$dark")
            assertEquals(sheet.blurDp, menu.blurDp)
            assertEquals(48f, menu.refractionDp, "menu bend dark=$dark")
            assertEquals(26f, menu.rimMaxDp, "menu rim dark=$dark")
            assertEquals(if (dark) .20f else .10f, menu.tint, "menu tint dark=$dark")
            assertEquals(1.6f, menu.saturation, "menu saturation dark=$dark")
        }
    }

    @Test
    fun `the dock's strip, swell and list keep their numbers - the swell is the menu's old glass`() {
        for (dark in listOf(true, false)) {
            assertEquals(3f, LiquidEdge.dockRecipe(dark, 0f).blurDp, "strip (dark=$dark)")
            assertEquals(if (dark) 6f else 3f, LiquidEdge.dockRecipe(dark, 1f).blurDp, "swell (dark=$dark)")
            assertEquals(12f, LiquidEdge.dockRecipe(dark, 1f, GlassLooks.Glossy, list = 1f).blurDp, "list (dark=$dark)")
            assertEquals(3f + if (dark) 3f else 0f, LiquidEdge.swellRecipe(dark).blurDp)
            assertEquals(LiquidEdge.recipe(LiquidRole.Menu, dark).refractionDp, LiquidEdge.swellRecipe(dark).refractionDp)
        }
    }

    @Test
    fun `only the menu's blur moved - every other role keeps its frost`() {
        for (dark in listOf(true, false)) {
            assertEquals(3f, LiquidEdge.recipe(LiquidRole.Bar, dark).blurDp)
            assertEquals(3f, LiquidEdge.recipe(LiquidRole.Dock, dark).blurDp)
            assertEquals(2f, LiquidEdge.recipe(LiquidRole.Composer, dark).blurDp)
            assertEquals(1f, LiquidEdge.recipe(LiquidRole.Pill, dark).blurDp)
            assertEquals(1f, LiquidEdge.recipe(LiquidRole.Status, dark).blurDp)
            assertEquals(1f, LiquidEdge.recipe(LiquidRole.Action, dark).blurDp)
            assertEquals(12f, LiquidEdge.recipe(LiquidRole.Sheet, dark).blurDp)
        }
    }
}
