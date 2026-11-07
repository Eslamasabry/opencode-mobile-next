package io.github.eslamasabry.oc.ui.kit.rb.o31

import io.github.eslamasabry.oc.ui.kit.live.KitLiveClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Every constant and rule of lib/ui/kit/live_background/kit_live_clock.dart at 8302de537. */
class KitLiveClockParityTest {
    private val step = KitLiveClock.INTERVAL_NANOS

    @Test fun twelveTicksASecondRestingAfterHalfAMinute() {
        assertEquals(12, KitLiveClock.FRAMES_PER_SECOND)
        assertEquals(83_333L, KitLiveClock.INTERVAL_MICROS) // Duration(microseconds: 1000000 ~/ 12)
        assertEquals(30_000L, KitLiveClock.IDLE_AFTER_MS)
        assertEquals(30_000L, KitLiveClock().idleAfterMs)
    }

    @Test fun aDetachedClockNeverMoves() {
        val c = KitLiveClock()
        assertFalse(c.advance(1_000_000_000L))
        assertEquals(0.0, c.seconds)
        assertEquals(0, c.ticks)
    }

    @Test fun eachIntervalMovesOneTickHoweverManyPagesOfferIt() {
        val c = KitLiveClock()
        c.attach()
        c.attach()
        assertTrue(c.advance(0))
        assertFalse(c.advance(10), "the second page in the same interval does nothing")
        assertTrue(c.advance(step))
        assertEquals(2, c.ticks)
        assertEquals(2 * 83_333 / 1e6, c.seconds, 1e-12)
    }

    @Test fun aLateFrameMovesAtMostThreeTicks() {
        val c = KitLiveClock()
        c.attach()
        c.advance(0)
        c.advance(step * 10)
        assertEquals(1 + 3, c.ticks, "a long stall moves at most three ticks' worth")
        assertEquals(4 * 83_333 / 1e6, c.seconds, 1e-12)
    }

    @Test fun itRestsAfterIdleAndATouchWakesItWhereItStopped() {
        val c = KitLiveClock()
        c.attach()
        var now = 0L
        while (c.awake) {
            c.advance(now)
            now += step
        }
        // 30 s of 83 333 µs ticks: the 361st tick reaches 30 000 000 µs.
        assertEquals(361, c.ticks)
        val held = c.seconds
        assertFalse(c.advance(now + step), "a resting clock arms no tick")
        assertEquals(held, c.seconds)
        c.input()
        assertTrue(c.awake)
        assertTrue(c.advance(now + 100 * step))
        assertEquals(held + 83_333 / 1e6, c.seconds, 1e-12, "it carries on where it stopped, no jump")
    }

    @Test fun inputKeepsItAwake() {
        val c = KitLiveClock()
        c.attach()
        var now = 0L
        repeat(1000) {
            c.advance(now)
            now += step
            if (it % 100 == 0) c.input()
        }
        assertTrue(c.awake)
    }

    @Test fun theLastDetachStopsIt() {
        val c = KitLiveClock()
        c.attach()
        c.attach()
        c.detach()
        assertEquals(1, c.users)
        c.detach()
        assertEquals(0, c.users)
        assertFalse(c.advance(step))
        assertFailsWith<IllegalStateException> { c.detach() }
    }

    @Test fun aSlowerLookMovesOnEveryFewTicks() {
        val c = KitLiveClock()
        c.attach()
        c.advance(0) // tick 1
        assertTrue(c.due(12))
        assertFalse(c.due(6), "6 a second: every second tick")
        c.advance(step) // tick 2
        assertTrue(c.due(6))
        assertFalse(c.due(4), "4 a second: every third tick")
        assertTrue(c.due(24), "never faster than the clock")
    }
}
