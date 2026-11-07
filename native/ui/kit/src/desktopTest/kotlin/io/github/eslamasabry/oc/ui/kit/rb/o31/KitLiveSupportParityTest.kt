package io.github.eslamasabry.oc.ui.kit.rb.o31

import io.github.eslamasabry.oc.ui.kit.live.KitLiveMemory
import io.github.eslamasabry.oc.ui.kit.live.LiveFbmGrid
import io.github.eslamasabry.oc.ui.kit.live.fbm
import io.github.eslamasabry.oc.ui.kit.live.hash2
import io.github.eslamasabry.oc.ui.kit.live.liveClock
import io.github.eslamasabry.oc.ui.kit.live.liveFbm
import io.github.eslamasabry.oc.ui.kit.live.liveHash
import io.github.eslamasabry.oc.ui.kit.live.liveRandom
import io.github.eslamasabry.oc.ui.kit.live.liveSmooth
import io.github.eslamasabry.oc.ui.kit.live.packArgb
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The shared helpers (styles/kit_live_support.dart, styles/kit_live_toolkit.dart) against the values Dart 3.9 prints
 * for the same calls: the looks' geometry is only Flutter's when these match.
 */
class KitLiveSupportParityTest {
    @Test fun dartRandomGivesDartsSequence() {
        val bokeh = liveRandom("bokeh")
        assertEquals(
            listOf(0.29964286686324304, 0.2507481861562334, 0.3215761165809118, 0.2685845867428497),
            List(4) { bokeh.nextDouble() },
        )
        assertEquals(listOf(4, 2, 3, 1, 1, 0), List(6) { bokeh.nextInt(5) })
        assertEquals(listOf(2, 2, 0, 2), List(4) { bokeh.nextInt(3) })
        val metal = liveRandom("brushedMetal")
        assertEquals(
            listOf(0.8476376277652765, 0.03745530422003451, 0.15085065358408356, 0.259108755848519),
            List(4) { metal.nextDouble() },
        )
        assertEquals(listOf(0, 2, 4, 3, 3, 0), List(6) { metal.nextInt(5) })
        val circuit = liveRandom("circuit")
        assertEquals(0.3338482920337149, circuit.nextDouble())
    }

    @Test fun hashesAndNoiseAreDarts() {
        assertEquals(0.07724846998462453, liveHash(1.5, 2.25, 3.0), 1e-9)
        assertEquals(0.5829638271752628, liveHash(-7.3), 1e-9)
        assertEquals(0.1734798267056118, liveFbm(.37, 1.91), 1e-9)
        assertEquals(0.5326189429995267, liveFbm(-3.2, 12.5), 1e-9)
        assertEquals(0.8468459488001759, hash2(3, -4), 1e-15)
        assertEquals(0.47597644791844684, hash2(123456, 789), 1e-15)
        assertEquals(0.5902410731934987, fbm(.37, 1.91), 1e-15)
        assertEquals(0.40545187303762015, fbm(-3.2, 12.5), 1e-15)
    }

    @Test fun theGridNoiseIsFbmBitForBit() {
        for (sin in listOf(false, true)) {
            val g = LiveFbmGrid(sinHash = sin)
            val xs = DoubleArray(57) { it * .037 + 3.31 }
            val ys = DoubleArray(130) { it * .041 - 7.9 }
            g.columns(xs, xs.size)
            g.rows(ys, ys.size)
            for (j in ys.indices) {
                for (i in xs.indices) {
                    val want = if (sin) liveFbm(xs[i], ys[j]) else fbm(xs[i], ys[j])
                    assertEquals(want, g.at(i, j), "sin=$sin ($i, $j)")
                }
            }
        }
    }

    @Test fun smoothAndPack() {
        assertEquals(0.0, liveSmooth(.2, .4, .1))
        assertEquals(.5, liveSmooth(.2, .4, .3), 1e-12)
        assertEquals(1.0, liveSmooth(.2, .4, .9))
        assertEquals(0x80FF0A00.toInt(), packArgb(300.0, 10.2, -4.0, .5))
    }

    @Test fun aLiveClockStartsAtTTimesRateAndNeverJumps() {
        val m = KitLiveMemory()
        assertEquals(14.6, liveClock(liveFrame(true, t = 7.3, memory = m), "k", 2.0), 1e-12)
        assertEquals(14.6 + .1 * 3, liveClock(liveFrame(true, t = 7.4, memory = m), "k", 3.0), 1e-12)
        assertEquals(14.9 + .25, liveClock(liveFrame(true, t = 100.0, memory = m), "k", 1.0), 1e-12, "a quarter second at most")
        assertEquals(1.0, liveClock(liveFrame(true, t = 1.0, memory = m), "k", 1.0), 1e-12, "a clock that went back starts again")
    }
}
