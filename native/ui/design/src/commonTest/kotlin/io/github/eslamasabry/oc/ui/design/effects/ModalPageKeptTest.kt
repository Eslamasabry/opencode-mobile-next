package io.github.eslamasabry.oc.ui.design.effects

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Cut audit 1 (2026-11-14, c16, c22, c23): a modal sheet showed the page's controls through it (the green "Allow once"
 * button as a big green ghost, the send button as a green smear, the card's code box as a dark plate). The page shows in a
 * modal sheet only along an edge it shares with the visible page, as far in as the rim bends it, and fades out past it.
 */
class ModalPageKeptTest {
    @Test
    fun `B-GLASS-MODAL-001 the page is kept whole as far in as the sheet's rim bends it, then fades out`() {
        val sheet = LiquidEdge.recipe(LiquidRole.Sheet, dark = true)
        // The lens reads the page up to its bend in from the edge: all of that is kept.
        assertTrue(LiquidEdge.MODAL_PAGE_KEEP_DP >= sheet.refractionDp, "the rim's bend reads only kept page")
        assertTrue(LiquidEdge.MODAL_PAGE_KEEP_DP >= sheet.rimMaxDp)
        assertEquals(1f, LiquidEdge.modalPageKept(0f, 100f))
        assertEquals(1f, LiquidEdge.modalPageKept(-20f, 100f), "outside the sheet, as at its edge")
        assertEquals(1f, LiquidEdge.modalPageKept(LiquidEdge.MODAL_PAGE_KEEP_DP, 100f))
        assertEquals(.5f, LiquidEdge.modalPageKept(LiquidEdge.MODAL_PAGE_KEEP_DP + LiquidEdge.MODAL_PAGE_FADE_DP / 2f, 100f), 1e-4f)
        assertEquals(0f, LiquidEdge.modalPageKept(LiquidEdge.MODAL_PAGE_KEEP_DP + LiquidEdge.MODAL_PAGE_FADE_DP, 100f))
        assertEquals(0f, LiquidEdge.modalPageKept(400f, 100f), "the sheet's middle shows none of the page")
        // No step: the fade moves by at most what a 1 dp step of a linear 32 dp fade moves.
        var last = 1f
        var d = 0f
        while (d < 120f) {
            val now = LiquidEdge.modalPageKept(d, 100f)
            assertTrue(now <= last && last - now <= 1f / LiquidEdge.MODAL_PAGE_FADE_DP + 1e-4f, "a step at $d dp: $last to $now")
            last = now
            d += 1f
        }
    }

    @Test
    fun `B-GLASS-MODAL-002 an edge on the window's edge shows none of the page, and opens smoothly as the sheet shrinks away from it`() {
        assertEquals(0f, LiquidEdge.modalPageKept(0f, 0f), "a bottom sheet's sides and foot")
        assertEquals(.5f, LiquidEdge.modalPageKept(0f, LiquidEdge.MODAL_PAGE_OPEN_DP / 2f), 1e-4f)
        assertEquals(1f, LiquidEdge.modalPageKept(0f, LiquidEdge.MODAL_PAGE_OPEN_DP))
        assertEquals(0f, LiquidEdge.modalPageKept(0f, -5f), "an edge past the window's edge (a sheet sliding in)")
    }
}
