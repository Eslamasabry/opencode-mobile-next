package io.github.eslamasabry.oc.ui.design.effects

import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Cut audit 2 (2026-11-14, c-rowmenu-top, c-rowmenu-bot, c-dock): the home row's menu and the dock's list showed the chips,
 * the row icons and a button behind them as blobs and dark blocks. A menu is modal while it is open, so it shows the page
 * only along its open edges, as a sheet does; but a menu borders the page on every side and is small, so its band is its
 * rim, not a sheet's 80 dp.
 */
class ModalBandTest {
    /** Where the lens reads the page for a pixel [d] dp in from the edge (the join shader's and the library's circle profile). */
    private fun sampled(d: Float, rim: Float, bend: Float): Float {
        if (d >= rim) return d
        val x = 1f - d / rim
        val dome = 1f - sqrt(maxOf(1f - x * x, 0f))
        return d + dome * bend
    }

    @Test
    fun `B-GLASS-MENU-001 a menu and a dialog keep the page only in their rim, a sheet keeps wave one's band`() {
        for (dark in listOf(true, false)) {
            val menu = LiquidEdge.recipe(LiquidRole.Menu, dark)
            val sheet = LiquidEdge.recipe(LiquidRole.Sheet, dark)
            assertEquals(menu.rimMaxDp, LiquidEdge.MENU_BAND.endDp, "a menu's band ends where its rim does (dark=$dark)")
            assertEquals(sheet.rimMaxDp, LiquidEdge.DIALOG_BAND.endDp, "a dialog's band ends where its rim does (dark=$dark)")
        }
        assertEquals(LiquidEdge.MENU_BAND, LiquidEdge.modalBand(GlassPaneKind.Menu))
        assertEquals(LiquidEdge.DIALOG_BAND, LiquidEdge.modalBand(GlassPaneKind.Dialog))
        assertEquals(ModalBand(48f, 32f), LiquidEdge.modalBand(GlassPaneKind.Sheet), "a bottom sheet's band is wave one's")
        for (kind in listOf(GlassPaneKind.Bar, GlassPaneKind.MessageBox, GlassPaneKind.Chip, GlassPaneKind.Button)) {
            assertNull(LiquidEdge.modalBand(kind), "$kind is not modal: it sees the page whole")
        }
        // Past the rim a menu shows none of the page; in its rim it shows it whole up to the last few dp.
        val band = LiquidEdge.MENU_BAND
        assertEquals(1f, LiquidEdge.modalPageKept(band.keepDp, 100f, band))
        assertEquals(0f, LiquidEdge.modalPageKept(band.endDp, 100f, band))
        assertEquals(0f, LiquidEdge.modalPageKept(60f, 100f, band), "the middle of a menu shows none of the page")
        // A 220 dp wide menu keeps its whole middle clear: before, a sheet's band left it 60 dp of 220.
        val width = 220f
        assertTrue((width - 2 * band.endDp) / width >= .75f, "a menu is not all page: ${width - 2 * band.endDp} of $width dp clear")
        assertTrue((width - 2 * LiquidEdge.SHEET_BAND.endDp) / width < .3f, "a sheet's band on a menu would cover most of it")
    }

    @Test
    fun `B-GLASS-MENU-002 the menu's rim still bends the page - most of its rim reads kept page`() {
        val menu = LiquidEdge.recipe(LiquidRole.Menu, dark = true)
        val band = LiquidEdge.MENU_BAND
        var bent = 0
        var d = 0f
        while (d < menu.rimMaxDp) {
            val at = sampled(d, menu.rimMaxDp, menu.refractionDp)
            if (LiquidEdge.modalPageKept(at, 100f, band) >= .5f) bent++
            d += .5f
        }
        // Half-dp steps across a 26 dp rim: at least 30 of 52 read the page at half strength or more.
        assertTrue(bent >= 30, "the bend reads kept page over only ${bent / 2f} dp of the rim")
        // And what lies past the rim reads none: a pixel there reads the page where it is.
        assertEquals(0f, LiquidEdge.modalPageKept(sampled(menu.rimMaxDp + 1f, menu.rimMaxDp, menu.refractionDp), 100f, band))
    }

    @Test
    fun `B-GLASS-MENU-003 the Glass lab tunes the band of menus, sheets and dialogs, and only theirs`() {
        for (kind in GlassPaneKind.entries) for (dark in listOf(true, false)) {
            val k = GlassTuning.shipped(kind, dark)
            assertEquals(LiquidEdge.modalBand(kind), GlassTuning.bandOf(kind, k), "$kind shipped band")
            val modal = LiquidEdge.modalBand(kind) != null
            assertEquals(modal, GlassKnob.PageKeep.appliesTo(kind), "$kind page keep offered")
            assertEquals(modal, GlassKnob.PageFade.appliesTo(kind), "$kind page fade offered")
        }
        val moved = GlassTuning.Shipped.with(GlassPaneKind.Menu, true, GlassKnob.PageKeep, 10f).with(GlassPaneKind.Menu, true, GlassKnob.PageFade, 4f)
        assertEquals(ModalBand(10f, 4f), GlassTuning.bandOf(GlassPaneKind.Menu, moved.knobs(GlassPaneKind.Menu, true)))
        assertFalse(moved.isShipped)
        // The block the lab copies carries the band and reads it back.
        val back = GlassTuning.decode(GlassTuning.encode(moved))
        assertEquals(moved.knobs(GlassPaneKind.Menu, true), back?.knobs(GlassPaneKind.Menu, true))
        // "All linked" shows the knobs every kind has.
        assertTrue(GlassKnob.PageKeep.appliesTo(null))
    }
}
