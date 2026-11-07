package io.github.eslamasabry.oc.ui.kit.rb.o33

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import io.github.eslamasabry.oc.ui.kit.live.KitLiveFrame
import io.github.eslamasabry.oc.ui.kit.live.KitLiveMemory
import io.github.eslamasabry.oc.ui.kit.live.LIVE_MOST_STEPS
import io.github.eslamasabry.oc.ui.kit.live.LiveFbmGrid
import io.github.eslamasabry.oc.ui.kit.live.LiveField
import io.github.eslamasabry.oc.ui.kit.live.LiveFieldCache
import io.github.eslamasabry.oc.ui.kit.live.fbm
import io.github.eslamasabry.oc.ui.kit.live.hash2
import io.github.eslamasabry.oc.ui.kit.live.liveContrast
import io.github.eslamasabry.oc.ui.kit.live.liveMost
import io.github.eslamasabry.oc.ui.kit.live.liveTextReads
import io.github.eslamasabry.oc.ui.kit.live.mixRgb
import io.github.eslamasabry.oc.ui.kit.live.packArgb
import io.github.eslamasabry.oc.ui.kit.live.paintField
import io.github.eslamasabry.oc.ui.kit.live.rgb
import io.github.eslamasabry.oc.ui.kit.live.smooth
import io.github.eslamasabry.oc.ui.kit.live.strengthOf
import io.github.eslamasabry.oc.ui.kit.live.valueNoise
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Every constant and rule of lib/ui/kit/live_background/styles/kit_live_toolkit.dart at 8302de537. */
class KitLiveToolkitParityTest {
    @Test fun hash2IsTheDart32BitHash() {
        assertEquals(0.0, hash2(0, 0))
        assertEquals(0.4025184426928215, hash2(3, 7), 1e-15)
        assertEquals(0.40432957429539634, hash2(-5, 12), 1e-15)
    }

    @Test fun fbmIsThreeOctavesOfTheIntegerNoise() {
        assertEquals(0.5902410731934987, fbm(0.37, 1.91), 1e-12)
        assertEquals(0.5867668150600431, fbm(2.5, -3.25), 1e-12)
        assertEquals(hash2(3, 4), valueNoise(3.0, 4.0), 1e-15)
    }

    @Test fun smoothAndColourHelpers() {
        assertEquals(.5, smooth(.03, .09, .06), 1e-12)
        rgb(Color(0xFFFF0033)).zip(doubleArrayOf(255.0, 0.0, 51.0)).forEach { (a, b) -> assertEquals(b, a, 1e-5) }
        assertContentEquals(doubleArrayOf(5.0, 10.0, 15.0), mixRgb(doubleArrayOf(0.0, 0.0, 0.0), doubleArrayOf(10.0, 20.0, 30.0), .5))
    }

    @Test fun packArgbRoundsAndClamps() {
        assertEquals(0x80FF0A00.toInt(), packArgb(300.0, 10.4, -3.0, .5))
        assertEquals(0x00000000, packArgb(0.0, 0.0, 0.0, -1.0))
        assertEquals(0xFF010203.toInt(), packArgb(1.0, 2.0, 3.0, 2.0))
    }

    @Test fun strengthIsOneInDarkAndSevenTenthsInLight() {
        val m = KitLiveMemory()
        assertEquals(1.6, strengthOf(KitLiveFrame(0.0, 0.0, 1.6, darkPalette, m)), 1e-12)
        assertEquals(.7, strengthOf(KitLiveFrame(0.0, 0.0, 1.0, lightPalette, m)), 1e-12)
    }

    private fun fieldAt(size: Size, div: Int, t: Double, m: KitLiveMemory, every: Double, calls: IntArray): LiveFieldCache {
        val canvas = skiaCanvas(size)
        paintField(canvas, size, KitLiveFrame(t, 0.0, 1.0, darkPalette, m), "f", div, LiveField { _, _ -> calls[0]++; 0 }, every)
        return m.of("f") { LiveFieldCache() }
    }

    @Test fun paintFieldGridAndRecomputeRules() {
        val m = KitLiveMemory()
        val calls = intArrayOf(0)
        val c = fieldAt(Size(412f, 915f), 3, 1.0, m, .06, calls)
        // About div logical pixels a cell, at least 8 a side.
        assertEquals(137, c.lw)
        assertEquals(305, c.lh)
        assertEquals((c.lw + 1) * (c.lh + 1), calls[0])
        // Flutter's two triangles a cell, sent as one strip a row: the same triangles, a third of the indices.
        assertEquals(c.lh * (c.lw + 1) * 2 + (c.lh - 1) * 2, c.indices.size)
        assertEquals(listOf(0, c.lw + 1, 1, c.lw + 2), c.indices.take(4).map { it.toInt() and 0xFFFF })
        // Within `every` seconds the colours are kept.
        fieldAt(Size(412f, 915f), 3, 1.05, m, .06, calls)
        assertEquals((c.lw + 1) * (c.lh + 1), calls[0])
        fieldAt(Size(412f, 915f), 3, 1.07, m, .06, calls)
        assertEquals(2 * (c.lw + 1) * (c.lh + 1), calls[0])
        // Time going back recomputes.
        fieldAt(Size(412f, 915f), 3, .5, m, .06, calls)
        assertEquals(3 * (c.lw + 1) * (c.lh + 1), calls[0])
        // Tiny surfaces keep eight cells a side; huge ones shrink by .8 until under 65000 points.
        val small = fieldAt(Size(10f, 10f), 3, 0.0, KitLiveMemory(), 0.0, intArrayOf(0))
        assertEquals(8, small.lw)
        assertEquals(8, small.lh)
        val big = fieldAt(Size(1200f, 1200f), 3, 0.0, KitLiveMemory(), 0.0, intArrayOf(0))
        assertTrue((big.lw + 1) * (big.lh + 1) <= 65000)
        assertEquals(205, big.lw) // 400 -> 320 -> 256 -> 205
    }

    @Test fun textReadsAndMost() {
        val p = darkPalette
        assertTrue(liveContrast(Color.White, Color.Black) > 20.9)
        assertTrue(liveTextReads(p, p.ground, p.green, 0.0))
        assertFalse(liveTextReads(p, p.ground, p.ink, 1.0))
        val most = liveMost(p, p.ground, p.ink)
        assertTrue(most > 0 && most < 1)
        assertTrue(liveTextReads(p, p.ground, p.ink, most))
        assertFalse(liveTextReads(p, p.ground, p.ink, most + 1.0 / (1 shl (LIVE_MOST_STEPS - 1))))
        assertEquals(18, LIVE_MOST_STEPS)
    }

    @Test fun theSeparableFbmIsBitForBitTheSame() {
        val xs = DoubleArray(57) { it / 56.0 * 1.4 + 3.21 }
        val ys = DoubleArray(131) { it / 130.0 * 2.2 * 1.4 - 1.7 }
        val grid = LiveFbmGrid()
        grid.prepare(xs, xs.size, ys, ys.size)
        for (j in ys.indices) for (i in xs.indices) assertEquals(fbm(xs[i], ys[j]), grid.at(i, j), "($i, $j)")
    }
}
