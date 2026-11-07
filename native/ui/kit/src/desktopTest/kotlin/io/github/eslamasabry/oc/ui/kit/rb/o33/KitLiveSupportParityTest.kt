package io.github.eslamasabry.oc.ui.kit.rb.o33

import androidx.compose.ui.geometry.Size
import io.github.eslamasabry.oc.ui.kit.live.KitLiveFrame
import io.github.eslamasabry.oc.ui.kit.live.KitLiveMemory
import io.github.eslamasabry.oc.ui.kit.live.liveCached
import io.github.eslamasabry.oc.ui.kit.live.liveClock
import io.github.eslamasabry.oc.ui.kit.live.liveFbm
import io.github.eslamasabry.oc.ui.kit.live.liveHash
import io.github.eslamasabry.oc.ui.kit.live.liveMix
import io.github.eslamasabry.oc.ui.kit.live.livePeak
import io.github.eslamasabry.oc.ui.kit.live.liveRandom
import io.github.eslamasabry.oc.ui.kit.live.liveSmooth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Every rule of lib/ui/kit/live_background/styles/kit_live_support.dart at 8302de537. The expected numbers are the
 * Dart expressions evaluated in IEEE doubles (the same sin and floor), and the seeded generator's first numbers are the
 * Dart VM's `math.Random(308674399)` ones; contact 4 of the radar golden sits where they put it.
 */
class KitLiveSupportParityTest {
    private fun frame(t: Double, memory: KitLiveMemory = KitLiveMemory(), strength: Double = 1.0, dark: Boolean = true) =
        KitLiveFrame(t, 0.0, strength, if (dark) darkPalette else lightPalette, memory)

    @Test fun liveHashIsTheFractionOfAScaledSine() {
        assertEquals(0.32561142705526436, liveHash(1.0), 1e-12)
        assertEquals(0.9592253042574157, liveHash(3.5, 2.25, -1.0), 1e-12)
    }

    @Test fun liveFbmIsThreeOctavesSixThreeOne() {
        assertEquals(0.1734798267056118, liveFbm(0.37, 1.91), 1e-12)
        assertEquals(0.4310317575540903, liveFbm(12.5, -3.25), 1e-12)
    }

    @Test fun liveSmoothIsAClampedSmoothstep() {
        assertEquals(0.0, liveSmooth(.2, .8, .1))
        assertEquals(1.0, liveSmooth(.2, .8, .9))
        assertEquals(.5, liveSmooth(.2, .8, .5), 1e-12)
        assertEquals(0.15625, liveSmooth(0.0, 1.0, .25), 1e-12)
    }

    @Test fun liveMixClampsT() {
        val p = darkPalette
        assertEquals(liveMix(p.teal, p.glow, 1.0), liveMix(p.teal, p.glow, 3.0))
        assertEquals(liveMix(p.teal, p.glow, 0.0), liveMix(p.teal, p.glow, -1.0))
        assertEquals(p.teal, liveMix(p.teal, p.glow, 0.0))
    }

    @Test fun livePeakIsTheCeilingTimesStrength() {
        assertEquals(.06 * 1.6, livePeak(frame(0.0, strength = 1.6)), 1e-12)
        assertEquals(.045, livePeak(frame(0.0, dark = false)), 1e-12)
    }

    @Test fun liveRandomIsDartsSeededRandom() {
        val r = liveRandom("radar")
        assertEquals(0.6364420707251958, r.nextDouble())
        assertEquals(0.8389828018322434, r.nextDouble())
        assertEquals(0.7756689565275138, r.nextDouble())
        assertEquals(0.5574789639218954, r.nextDouble())
    }

    @Test fun liveClockStartsAtTTimesRateCapsAStepAndRestartsWhenTimeGoesBack() {
        val m = KitLiveMemory()
        assertEquals(7.3 * 1.5, liveClock(frame(7.3, m), "k", 1.5), 1e-12)
        // A step adds the elapsed time times the rate: the rate changes without a jump.
        assertEquals(7.3 * 1.5 + .1 * 3, liveClock(frame(7.4, m), "k", 3.0), 1e-9)
        // A long stall moves a quarter second at most.
        assertEquals(7.3 * 1.5 + .1 * 3 + .25, liveClock(frame(20.0, m), "k", 1.0), 1e-9)
        // Time going back (a fresh page) starts again at t * rate.
        assertEquals(2.0, liveClock(frame(1.0, m), "k", 2.0), 1e-12)
        // Keys are separate clocks.
        assertEquals(4.0, liveClock(frame(4.0, m), "other", 1.0), 1e-12)
    }

    @Test fun liveCachedRebuildsOnlyForANewSizeOrSignature() {
        val m = KitLiveMemory()
        val f = frame(0.0, m)
        var made = 0
        val a = liveCached(f, "g", Size(10f, 20f)) { made++; Any() }
        assertSame(a, liveCached(f, "g", Size(10f, 20f)) { made++; Any() })
        assertEquals(1, made)
        liveCached(f, "g", Size(11f, 20f)) { made++; Any() }
        assertEquals(2, made)
        liveCached(f, "g", Size(11f, 20f), sig = "red") { made++; Any() }
        assertEquals(3, made)
        liveCached(f, "g", Size(11f, 20f), sig = "red") { made++; Any() }
        assertEquals(3, made)
    }
}
