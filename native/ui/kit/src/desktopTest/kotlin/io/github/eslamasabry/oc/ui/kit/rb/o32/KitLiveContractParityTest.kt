package io.github.eslamasabry.oc.ui.kit.rb.o32

import io.github.eslamasabry.oc.ui.design.effects.AuroraImage
import io.github.eslamasabry.oc.ui.kit.live.DartRandom
import io.github.eslamasabry.oc.ui.kit.live.KitLiveClock
import io.github.eslamasabry.oc.ui.kit.live.hash2
import io.github.eslamasabry.oc.ui.kit.live.kitLiveLookIds
import io.github.eslamasabry.oc.ui.kit.live.kitLiveLookLabel
import io.github.eslamasabry.oc.ui.kit.live.kitLiveStyleById
import io.github.eslamasabry.oc.ui.kit.live.kitLiveStyles
import io.github.eslamasabry.oc.ui.kit.live.mixColor
import io.github.eslamasabry.oc.ui.kit.live.strengthOf
import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The shared contract the order-32 looks draw through (Dart kit_live_clock.dart, kit_live_style.dart,
 * kit_live_styles.dart, styles/kit_live_toolkit.dart, styles/kit_live_support.dart at 8302de537), interim until
 * order 31 lands its own tests.
 */
class KitLiveContractParityTest {
    @Test fun theClockTicksTwelveTimesASecondAndNeverLeaps() {
        assertEquals(12, KitLiveClock.FRAMES_PER_SECOND)
        assertEquals(83333L, KitLiveClock.INTERVAL_US)
        assertEquals(30_000L, KitLiveClock.IDLE_AFTER_MS)
        assertEquals(3, KitLiveClock.MAX_STEP)
        val clock = KitLiveClock.instance.also { it.reset() }
        clock.attach()
        val ms = 1_000_000L
        assertFalse(clock.advance(0)) // the first frame starts the timer
        assertFalse(clock.advance(80 * ms)) // not a whole interval yet: no tick, never faster than 12 a second
        assertTrue(clock.advance(84 * ms))
        assertEquals(1, clock.ticks)
        assertFalse(clock.advance(90 * ms))
        // A stall of a second moves three ticks' worth, not twelve.
        assertTrue(clock.advance(1_090 * ms))
        assertEquals(4, clock.ticks)
        assertEquals(4 * 83333 / 1e6, clock.seconds, 1e-9)
        clock.detach()
        clock.reset()
    }

    @Test fun theClockRestsAfterHalfAMinuteAndATouchWakesItWhereItStopped() {
        val clock = KitLiveClock.instance.also { it.reset() }
        clock.attach()
        clock.advance(0)
        var now = 0L
        val step = KitLiveClock.INTERVAL_US * 1000
        while (clock.awake) {
            now += step
            clock.advance(now)
        }
        val held = clock.seconds
        assertEquals(361, clock.ticks) // 30 s at 12 a second: 361 whole 83 333 µs ticks, as Dart
        assertFalse(clock.advance(now + 10 * step), "a resting clock does not tick")
        assertEquals(held, clock.seconds)
        clock.input()
        assertTrue(clock.awake)
        clock.advance(now + 20 * step) // restarts its timer here
        clock.advance(now + 21 * step)
        assertEquals(held + 83333 / 1e6, clock.seconds, 1e-9)
        clock.detach()
        clock.reset()
    }

    @Test fun aSlowerLookRepaintsOnEveryFewTicks() {
        assertEquals(1, KitLiveClock.everyFor(12))
        assertEquals(1, KitLiveClock.everyFor(40))
        assertEquals(2, KitLiveClock.everyFor(6))
        assertEquals(12, KitLiveClock.everyFor(0))
    }

    @Test fun theOrder32LooksAreRegisteredInDartOrderByTheirStoredIds() {
        val ids = kitLiveStyles.map { it.id }
        val dartOrder =
            listOf(
                "aurora", "mesh", "dotRipple", "topo", "flowThreads", "glowSpill", "lightRays", "constellation",
                "halftone", "liquidBlobs", "matrixRain", "blueprint", "circuit", "oscilloscope", "radar", "honeycomb",
                "brushedMetal", "silk", "caustics", "bokeh",
            )
        assertEquals(dartOrder.filter { it in ids }, ids, "the registry keeps the Dart picker order")
        for (id in listOf(
            "mesh", "dotRipple", "flowThreads", "glowSpill", "lightRays", "constellation", "halftone", "liquidBlobs",
            "matrixRain", "honeycomb",
        )) {
            assertEquals(id, kitLiveStyleById(id)?.id)
            assertNotNull(kitLiveLookLabel(id))
        }
    }

    @Test fun thePickerOffersThePaintedAuroraAndEveryDrawnLook() {
        val ids = kitLiveLookIds()
        assertTrue(AuroraImage.ID in ids)
        assertEquals(kitLiveStyles.size + 1, ids.size)
        assertNull(kitLiveLookLabel("not-a-look"))
        // An unknown id falls back to the default look, which the native app does not draw before order 31 lands.
        assertEquals(kitLiveStyleById("aurora"), kitLiveStyleById("not-a-look"))
    }

    @Test fun toolkitMathIsFlutters() {
        assertEquals(0.0, hash2(0, 0))
        val h = hash2(3, 7)
        assertTrue(h in 0.0..1.0)
        assertEquals(h, hash2(3, 7))
        // Negative cells hash through their 32-bit pattern.
        assertEquals(hash2(-1, 0), hash2(0xFFFFFFFFL, 0))
        // sRGB-linear mixing, never Oklab: half of black to white is 50 % grey.
        val grey = mixColor(Color.Black, Color.White, .5)
        assertEquals(.5f, grey.red, 1e-6f)
        assertEquals(.5f, grey.green, 1e-6f)
        val p = LiveRender.palette(false)
        assertEquals(.7, strengthOf(LiveRender.frame(p)), 1e-12)
        assertEquals(1.6, strengthOf(LiveRender.frame(LiveRender.palette(true), strength = 1.6)), 1e-12)
        assertEquals(.045, p.ceiling)
        assertEquals(.06, LiveRender.palette(true).ceiling)
    }

    @Test fun dartRandomIsSeededAndInRange() {
        val a = DartRandom(5)
        val b = DartRandom(5)
        val c = DartRandom(7)
        var differs = false
        repeat(1000) {
            val x = a.nextDouble()
            assertTrue(x >= 0.0 && x < 1.0)
            assertEquals(x, b.nextDouble())
            if (x != c.nextDouble()) differs = true
        }
        assertTrue(differs)
    }
}
