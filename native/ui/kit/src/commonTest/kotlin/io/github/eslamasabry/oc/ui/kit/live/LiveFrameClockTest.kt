package io.github.eslamasabry.oc.ui.kit.live

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** E20-S28: the one live clock follows the display's refresh, rests after 30 s without a touch and wakes on one. */
class LiveFrameClockTest {
    private val ms = 1_000_000L

    /** Feeds frames every [frameMs] for [forMs] from [from]; returns the steps and the next frame time. */
    private fun LiveFrameClock.run(from: Long, frameMs: Double, forMs: Long): Pair<Int, Long> {
        var steps = 0
        var t = from
        val end = from + forMs * ms
        while (t < end) {
            if (frame(t)) steps++
            t += (frameMs * ms).toLong()
        }
        return steps to t
    }

    @Test
    fun `B-KIT-LIVE-001 the clock steps on every display frame at 60, 90 and 120 Hz, with no cap of its own`() {
        for (hz in listOf(60, 90, 120)) {
            val clock = LiveFrameClock().apply { acquire() }
            val (steps, _) = clock.run(0, 1000.0 / hz, 1000)
            assertTrue(steps in hz - 2..hz, "$hz Hz: $steps steps")
            assertEquals(1f, clock.seconds, .05f)
        }
    }

    @Test
    fun `B-KIT-LIVE-002 with no one using it the clock asks for no frames and keeps its time`() {
        val clock = LiveFrameClock()
        assertFalse(clock.wantsFrames)
        assertFalse(clock.frame(0))
        assertFalse(clock.frame(100 * ms))
        assertEquals(0f, clock.seconds)
        clock.acquire()
        assertTrue(clock.wantsFrames)
        clock.release()
        assertFalse(clock.wantsFrames)
    }

    @Test
    fun `B-KIT-LIVE-003 after 30 seconds without a touch the clock rests`() {
        val clock = LiveFrameClock().apply { acquire() }
        val (_, t) = clock.run(0, 1000.0 / 60, 30_000)
        clock.frame(t)
        assertTrue(clock.resting)
        assertFalse(clock.wantsFrames)
        val frozen = clock.seconds
        assertFalse(clock.frame(t + 500 * ms))
        assertEquals(frozen, clock.seconds)
        assertTrue(frozen in 29f..30.1f, "ran $frozen s before resting")
    }

    @Test
    fun `B-KIT-LIVE-004 a touch wakes a resting clock and time carries on without a jump`() {
        val clock = LiveFrameClock().apply { acquire() }
        val (_, t) = clock.run(0, 1000.0 / 60, 31_000)
        assertTrue(clock.resting)
        val before = clock.seconds
        clock.touch()
        assertFalse(clock.resting)
        assertTrue(clock.wantsFrames)
        // Two minutes later the person touches; the first frames after it step by one frame, not by two minutes (and
        // the first second gathers speed: half the ease-in is lost to it).
        val (steps, _) = clock.run(t + 120_000 * ms, 1000.0 / 60, 1000)
        assertTrue(steps >= 58)
        val easeIn = LiveFrameClock.EASE_IN_NANOS / 1e9f
        assertEquals(before + 1f - easeIn / 2f, clock.seconds, .05f)
    }

    @Test
    fun `B-KIT-LIVE-040 a woken clock gathers speed smoothly instead of jumping to full speed`() {
        val clock = LiveFrameClock().apply { acquire() }
        val (_, t) = clock.run(0, 1000.0 / 60, 31_000)
        assertTrue(clock.resting)
        clock.touch()
        var at = t + 60_000 * ms
        clock.frame(at) // the first frame after the wake only starts the count
        var last = clock.seconds
        val steps = mutableListOf<Float>()
        repeat(90) {
            at += 16_666_667L
            clock.frame(at)
            steps += clock.seconds - last
            last = clock.seconds
        }
        val frame = 1f / 60f
        assertTrue(steps.first() < frame * .05f, "the first step is a sliver of a frame: ${steps.first()}")
        assertTrue(steps.zipWithNext().all { (a, b) -> b >= a - 1e-6f }, "speed only rises: $steps")
        assertTrue(steps.zipWithNext().all { (a, b) -> b - a <= frame * .06f }, "no step jumps the speed")
        assertEquals(frame, steps.last(), frame * .01f)
        assertEquals(1f, clock.speed, .001f)
    }

    @Test
    fun `B-KIT-LIVE-041 the background slows to a stop before the rest, still resting at 30 seconds`() {
        val clock = LiveFrameClock().apply { acquire() }
        val easeOut = LiveFrameClock.EASE_OUT_NANOS / 1_000_000L
        val (_, t) = clock.run(0, 1000.0 / 60, 30_000 - easeOut)
        assertEquals(1f, clock.speed, .01f)
        var at = t
        var last = clock.seconds
        val steps = mutableListOf<Float>()
        while (!clock.resting) {
            clock.frame(at)
            steps += clock.seconds - last
            last = clock.seconds
            at += 16_666_667L
        }
        assertTrue(at <= 30_000 * ms + 50 * ms, "it still rests at 30 s: ${at / ms} ms")
        assertTrue(steps.zipWithNext().all { (a, b) -> b <= a + 1e-6f }, "speed only falls")
        assertTrue(steps.dropLast(1).last() < (1f / 60f) * .05f, "the last steps are slivers: ${steps.takeLast(3)}")
        // A touch while it slows picks the speed up from where it is, without a jump.
        val again = LiveFrameClock().apply { acquire() }
        val (_, t2) = again.run(0, 1000.0 / 60, 30_000 - easeOut / 2)
        val slow = again.speed
        assertTrue(slow in .2f..0.8f, "half way into the ease: $slow")
        again.touch()
        again.frame(t2)
        assertTrue(again.speed - slow < .05f, "no jump on the touch: $slow -> ${again.speed}")
    }

    @Test
    fun `B-KIT-LIVE-005 touches keep it running past 30 seconds`() {
        val clock = LiveFrameClock().apply { acquire() }
        var t = 0L
        repeat(4) {
            val (_, next) = clock.run(t, 1000.0 / 60, 20_000)
            clock.touch()
            t = next
        }
        assertFalse(clock.resting)
        assertTrue(clock.seconds > 75f)
    }

    @Test
    fun `B-KIT-LIVE-006 a held clock (app hidden, Saver, heat) stops stepping and resumes smoothly`() {
        val clock = LiveFrameClock().apply { acquire() }
        val (_, t) = clock.run(0, 1000.0 / 60, 1000)
        clock.held = true
        assertFalse(clock.wantsFrames)
        val held = clock.seconds
        val (none, t2) = clock.run(t, 1000.0 / 60, 5000)
        assertEquals(0, none)
        assertEquals(held, clock.seconds)
        clock.held = false
        clock.run(t2, 1000.0 / 60, 1000)
        assertEquals(held + 1f, clock.seconds, .1f)
    }

    @Test
    fun `B-KIT-LIVE-007 a long stall steps a quarter second at most`() {
        val clock = LiveFrameClock().apply { acquire() }
        clock.frame(0)
        clock.frame(40 * ms)
        val before = clock.seconds
        clock.frame(5_040 * ms)
        assertEquals(before + .25f, clock.seconds, .001f)
    }

    @Test
    fun `B-KIT-LIVE-008 a look that appears after a long quiet spell starts moving, not resting`() {
        val clock = LiveFrameClock()
        clock.frame(0)
        clock.acquire()
        val (steps, _) = clock.run(60_000 * ms, 1000.0 / 60, 1000)
        assertFalse(clock.resting)
        assertTrue(steps >= 58)
        clock.held = true
        clock.frame(200_000 * ms)
        clock.held = false
        val (after, _) = clock.run(200_000 * ms, 1000.0 / 60, 1000)
        assertFalse(clock.resting, "coming back to the app counts as activity")
        assertTrue(after >= 58)
    }
}
