package io.github.eslamasabry.oc.ui.kit.rb.o31

import io.github.eslamasabry.oc.ui.kit.live.KitAuroraBands
import io.github.eslamasabry.oc.ui.kit.live.KitLiveAurora
import io.github.eslamasabry.oc.ui.kit.live.liveTextReads
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Every constant and rule of lib/ui/kit/live_background/styles/kit_live_aurora.dart at 8302de537. */
class KitLiveAuroraParityTest {
    @Test fun constants() {
        assertEquals("aurora", KitLiveAurora.id)
        assertEquals(.012, KitAuroraBands.HAZE)
        assertEquals(72, KitAuroraBands.ROWS)
        assertEquals(1.45, KitAuroraBands.LONG)
        assertEquals(.55, KitAuroraBands.HALO_COVER)
        assertEquals(
            listOf(0.0, .005, .011, .018, .027, .039, .054, .072, .094, .12, .15, .19, .24),
            KitAuroraBands.ACROSS.toList(),
        )
        assertEquals(listOf(-1.0, -.5, 0.0, .5, 1.0), KitAuroraBands.RIDGE_AT.toList())
        assertEquals(listOf(0.0, .55, 1.0, .55, 0.0), KitAuroraBands.RIDGE.toList())
    }

    @Test fun fourRibbonsThatNeverCross() {
        val r = KitAuroraBands.RIBBONS
        assertEquals(4, r.size)
        assertEquals(listOf(-.16 to .25, .02 to .14, .2 to .065, .42 to .0, .62 to -.08), r[0].spine)
        assertEquals(listOf(1.0, -1.0, -1.0, 1.0), r.map { it.side })
        assertEquals(listOf(0.0, 1.9, 3.7, 5.3), r.map { it.phase })
        assertEquals(listOf(.85, 1.0, .7, .6), r.map { it.width })
        assertEquals(listOf(1.0, 1.0, .85, .6), r.map { it.peak })
        assertEquals(9, r[1].spine.size)
        assertEquals(.02 to 1.08, r[1].spine.first())
        assertEquals(1.14 to .36, r[1].spine.last())
        val s = KitAuroraBands.STRANDS
        assertEquals(listOf(.03, -.05, -.1), s.map { it.at })
        assertEquals(listOf(.9, .8, .7), s.map { it.alpha })
        assertEquals(listOf(0.0, .08, .2), s.map { it.from })
        assertEquals(listOf(.9, 1.0, .85), s.map { it.to })
    }

    /** The values Dart 3.9 prints for `_profile`. */
    @Test fun theProfileIsDarts() {
        assertEquals(1.0, KitAuroraBands.profile(0.0, 0.0, true), 1e-12)
        assertEquals(0.5177273351018356, KitAuroraBands.profile(.02, 0.0, true), 1e-12)
        assertEquals(0.8153285274959245, KitAuroraBands.profile(.05, 1.0, false), 1e-12)
        assertEquals(0.07279548012219483, KitAuroraBands.profile(.2, .5, true), 1e-12)
    }

    @Test fun catmullRomPassesThroughItsPoints() {
        val px = doubleArrayOf(0.0, 10.0, 30.0)
        val py = doubleArrayOf(0.0, 5.0, 0.0)
        val out = DoubleArray(2)
        KitAuroraBands.catmullRom(px, py, 0.0, out)
        assertEquals(listOf(0.0, 0.0), out.toList())
        KitAuroraBands.catmullRom(px, py, .5, out)
        assertEquals(10.0, out[0], 1e-9)
        assertEquals(5.0, out[1], 1e-9)
    }

    @Test fun theBrightestSpotKeepsTheTextReadable() {
        for (dark in listOf(true, false)) {
            val p = livePalette(dark)
            val (hue, peak) = KitAuroraBands.peakOf(p, 1.0)
            assertTrue(peak > 0, "the aurora shows (dark=$dark)")
            assertTrue(liveTextReads(p, p.ground, hue, peak, halo = if (dark) KitAuroraBands.HALO_COVER else 0.0), "dark=$dark")
        }
        assertTrue(KitLiveAurora.wantsTextHalo(livePalette(true)))
        assertTrue(!KitLiveAurora.wantsTextHalo(livePalette(false)))
    }

    @Test fun theGridIsTwoTrianglesPerCell() {
        assertEquals(listOf(0, 1, 2, 1, 3, 2), KitAuroraBands.grid(1, 2))
        assertEquals(72 * 4 * 6, KitAuroraBands.grid(72, 5).size)
    }

    @Test fun deterministicAndAlive() {
        for (dark in listOf(true, false)) {
            val a = renderLook(KitLiveAurora, PHONE, liveFrame(dark))
            val b = renderLook(KitLiveAurora, PHONE, liveFrame(dark))
            assertTrue(a.contentEquals(b))
            val c = renderLook(KitLiveAurora, PHONE, liveFrame(dark, t = GOLDEN_T + 60))
            assertTrue(!a.contentEquals(c))
            val f = liveFrame(dark)
            assertTrue(maxDelta(a, f) > 0, "visible (dark=$dark)")
        }
    }
}
