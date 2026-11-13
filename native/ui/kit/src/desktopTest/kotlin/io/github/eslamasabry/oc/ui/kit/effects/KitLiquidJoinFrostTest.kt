package io.github.eslamasabry.oc.ui.kit.effects

import io.github.eslamasabry.oc.ui.design.effects.LiquidEdge
import io.github.eslamasabry.oc.ui.design.effects.LiquidRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Words on glass 3, item 1: a joined menu's frost (one frost for the body) moves to the menu's as its rows come in. */
class KitLiquidJoinFrostTest {
    @Test
    fun `a joined menu's frost moves from the bar's to the menu's as its rows come in, with no step`() {
        for (dark in listOf(true, false)) {
            val bar = LiquidEdge.recipe(LiquidRole.Bar, dark)
            val menu = LiquidEdge.recipe(LiquidRole.Menu, dark)
            assertEquals(bar, joinedFrost(bar, menu, 0f), "no rows yet: the bar's frost (dark=$dark)")
            assertEquals(bar.copy(blurDp = LiquidEdge.DECISION_BLUR_DP), joinedFrost(bar, menu, 1f), "rows in: the menu's blur, nothing else of the bar's moves (dark=$dark)")
            assertEquals(joinedFrost(bar, menu, 1f), joinedFrost(bar, menu, 1.7f), "clamped")
            var last = joinedFrost(bar, menu, 0f).blurDp
            for (i in 1..100) {
                val now = joinedFrost(bar, menu, i / 100f).blurDp
                assertTrue(now >= last && now - last <= .4f, "a step at ${i / 100f}: $last to $now")
                last = now
            }
        }
    }
}
