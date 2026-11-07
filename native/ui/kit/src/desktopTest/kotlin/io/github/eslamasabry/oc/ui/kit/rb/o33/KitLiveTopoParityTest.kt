package io.github.eslamasabry.oc.ui.kit.rb.o33

import io.github.eslamasabry.oc.ui.kit.live.KitLiveTopo as T
import kotlin.test.Test
import kotlin.test.assertEquals

/** Every constant and rule of lib/ui/kit/live_background/styles/kit_live_topo.dart at 8302de537. */
class KitLiveTopoParityTest {
    @Test fun idAndCeiling() {
        assertEquals("topo", T.id)
        assertEquals(1.0, T.ceilingFactor)
    }

    @Test fun constants() {
        assertEquals(.54, T.CALIBRATED)
        assertEquals(.02, T.DRIFT)
        assertEquals(3, T.DIV)
        assertEquals(1.4, T.SCALE)
        assertEquals(.6, T.DRIFT_V)
        assertEquals(8.0, T.LINES)
        assertEquals(4.0, T.LINES_RUN)
        assertEquals(.03, T.EDGE_IN)
        assertEquals(.09, T.EDGE_OUT)
        assertEquals(1.3, T.GLOW_FROM)
        assertEquals(1.9, T.GLOW_TO)
        assertEquals(.09, T.LINE_ALPHA)
        assertEquals(.2, T.GLOW_ALPHA)
        assertEquals(.06, T.EVERY)
    }

    @Test fun roundingTiesGoAwayFromZeroAsInDart() {
        assertEquals(3.0, T.dartRound(2.5))
        assertEquals(-3.0, T.dartRound(-2.5))
        assertEquals(2.0, T.dartRound(2.4999))
    }

    @Test fun aContourIsInkOnTheLineAndClearBetween() {
        val f = T.Field()
        f.setColours(darkPalette.ink, darkPalette.glow)
        f.a = 1.0
        // Away from any line the field is clear; the alpha is .09 * a at most on a line, idle.
        var most = 0
        var clear = 0
        for (i in 0..400) {
            val px = f.at(i / 400.0, .7)
            val a = (px ushr 24) and 0xFF
            most = maxOf(most, a)
            if (a == 0) clear++
        }
        assertEquals(23, most) // round(.09 * 255)
        assert(clear > 200)
    }

    /** Idle only: while running, the ambient looks are held by the contrast test (kit_live_background_test.dart). */
    @Test fun visibleUnderItsCeilingDeterministicAndAlive() = checkLook(T, runs = listOf(0.0))

    @Test fun thePreparedPassGivesTheSameColoursAsPointByPoint() {
        val f = T.Field()
        f.setColours(darkPalette.ink, darkPalette.glow)
        f.a = .54
        f.run = .4
        f.tt = 7.3 * .02
        val lw = 40
        val lh = 90
        val aspect = 915.0 / 412
        f.prepare(lw, lh, aspect)
        for (y in 0..lh) for (x in 0..lw) {
            val u = x.toDouble() / lw
            val v = y.toDouble() / lh * aspect
            assertEquals(f.at(u, v), f.atCell(x, y, u, v), "($x, $y)")
        }
    }
}
