package io.github.eslamasabry.oc.ui.kit.rb.o33

import io.github.eslamasabry.oc.ui.kit.live.KitLiveClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The shared clock's rules (lib/ui/kit/live_background/kit_live_clock.dart): rate, catch-up, rest and wake. */
class KitLiveClockRulesTest {
    private val ms = 1_000_000L

    @Test fun constants() {
        assertEquals(12, KitLiveClock.FRAMES_PER_SECOND)
        assertEquals(83_333L, KitLiveClock.INTERVAL_MICROS)
        assertEquals(30_000L, KitLiveClock.IDLE_AFTER_MS)
    }

    @Test fun neverFasterThanTwelveTicksASecond() {
        val c = KitLiveClock()
        c.attach()
        c.onFrame(0)
        // A frame on every vsync for a second (60 Hz) still gives 12 ticks.
        for (i in 1..60) c.onFrame(i * 1_000_000_000L / 60)
        assertEquals(12, c.ticks)
        assertEquals(12 * 83_333 / 1e6, c.seconds, 1e-9)
    }

    @Test fun aLateFrameCatchesUpAtMostThreeTicks() {
        val c = KitLiveClock()
        c.attach()
        c.onFrame(0)
        c.onFrame(2_000 * ms)
        assertEquals(3, c.ticks)
    }

    @Test fun restsAfterThirtySecondsWithoutATouchAndWakesWhereItStopped() {
        val c = KitLiveClock()
        c.attach()
        c.onFrame(0)
        var t = 0L
        while (c.awake) {
            t += 84 * ms
            c.onFrame(t)
        }
        val stopped = c.seconds
        assertTrue(stopped in 29.9..30.1, "rested at $stopped s")
        assertEquals(-1L, c.onFrame(t + 10_000 * ms))
        assertEquals(stopped, c.seconds)
        c.input()
        assertTrue(c.awake)
        c.onFrame(t + 20_000 * ms)
        c.onFrame(t + 20_000 * ms + 84 * ms)
        assertEquals(stopped + 83_333 / 1e6, c.seconds, 1e-9)
    }

    @Test fun aSlowLookMovesOnEveryFewTicks() {
        val c = KitLiveClock()
        c.attach()
        c.onFrame(0)
        var moved = 0
        for (i in 1..12) {
            c.onFrame(i * 84 * ms)
            if (c.due(4)) moved++
        }
        assertEquals(4, moved)
    }

    @Test fun theLastDetachStopsIt() {
        val c = KitLiveClock()
        c.attach()
        c.attach()
        c.detach()
        assertEquals(1, c.users)
        c.detach()
        assertEquals(0, c.users)
        assertEquals(-1L, c.onFrame(5_000 * ms))
        assertFalse(c.users > 0)
    }
}
