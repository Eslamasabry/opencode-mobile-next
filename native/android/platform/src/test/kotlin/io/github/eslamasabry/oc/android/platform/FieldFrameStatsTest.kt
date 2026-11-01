package io.github.eslamasabry.oc.android.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** S2-FIELD frame counting (plan 13 §3.6, §7.1, WP-13.4). No catalog row covers it; names carry the work package. */
class FieldFrameStatsTest {
    private val minute = 60_000_000_000L
    private val deadline = 8_333_333L // 120 Hz

    @Test
    fun `WP-13_4 FIELD a frame is late only when it took more than twice its deadline (JankStats rule)`() {
        val c = FrameCounter()
        c.record(0, deadline, deadline, isStreaming = false) // on time
        c.record(1, deadline * 2, deadline, isStreaming = false) // exactly 2x: on time
        c.record(2, deadline * 2 + 1, deadline, isStreaming = false) // late
        c.record(3, 50_000_000, 0, isStreaming = false) // unknown deadline: never counted late
        val s = c.lastHour(10)
        assertEquals(4, s.frames)
        assertEquals(3, s.onTime)
        assertEquals(75, s.onTimePerHundred)
        assertEquals(1, s.minutes)
    }

    @Test
    fun `WP-13_4 FIELD streaming frames are counted on their own as well`() {
        val c = FrameCounter()
        c.record(0, deadline, deadline, isStreaming = true)
        c.record(1, deadline * 3, deadline, isStreaming = true)
        c.record(2, deadline, deadline, isStreaming = false)
        val s = c.lastHour(10)
        assertEquals(3, s.frames)
        assertEquals(2, s.streamingFrames)
        assertEquals(1, s.streamingOnTime)
    }

    @Test
    fun `WP-13_4 FIELD only the last hour counts, in 60 one-minute buckets`() {
        val c = FrameCounter()
        for (m in 0L until 90L) c.record(m * minute, deadline, deadline, isStreaming = false)
        val now = 89 * minute + 1
        val s = c.lastHour(now)
        assertEquals(60, s.frames, "minutes 30..89")
        assertEquals(60, s.minutes)
        // An hour later with no frames, nothing is left.
        assertEquals(FrameSummary.Empty, c.lastHour(now + 60 * minute))
        // A late frame from a minute already overwritten is ignored.
        c.record(10 * minute, deadline, deadline, isStreaming = false)
        assertEquals(60, c.lastHour(now).frames)
    }

    @Test
    fun `WP-13_4 FIELD no frames means no smoothness number, and clear forgets everything`() {
        val c = FrameCounter()
        assertNull(c.lastHour(0).onTimePerHundred)
        c.record(0, deadline, deadline, isStreaming = false)
        c.clear()
        assertEquals(FrameSummary.Empty, c.lastHour(1))
        c.record(-1, deadline, deadline, isStreaming = false)
        c.record(0, -1, deadline, isStreaming = false)
        assertEquals(0, c.lastHour(1).frames, "malformed readings are dropped")
    }

    /**
     * The counting step's own cost, far under the 0.1 ms per-frame budget (it runs off the UI thread; the UI thread
     * pays only the platform's FrameMetrics copy, measured on the emulator in PJ-05 with and without the listener).
     */
    @Test
    fun `WP-13_4 FIELD counting one frame costs well under 0_1 ms`() {
        val c = FrameCounter()
        val n = 200_000
        repeat(20_000) { c.record(it.toLong() * 8_000_000, deadline, deadline, isStreaming = it % 3 == 0) } // warm-up
        val t0 = System.nanoTime()
        for (i in 0 until n) c.record(i.toLong() * 8_000_000, deadline + (i % 7) * deadline, deadline, isStreaming = i % 2 == 0)
        val perFrameNs = (System.nanoTime() - t0) / n
        assertTrue(perFrameNs < 100_000, "per frame: $perFrameNs ns")
        assertTrue(perFrameNs < 10_000, "per frame: $perFrameNs ns (a tenth of the budget on a loaded CI machine)")
    }
}
