package io.github.eslamasabry.oc.engine.analytics.health

import io.github.eslamasabry.oc.engine.analytics.product.ProductScreen
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * c2-performance (2026-11-15): the `jank` event judged a frame slow when its whole duration passed one refresh period,
 * which Android's pipelined frames do while still showing on time (a Galaxy S22 hour: 60 % by that rule, 8.3 % by
 * Android's own count). `late_pct_range` judges each frame against its own deadline, leaves a window's first frame out,
 * and `seen_hz` says the rate the frames were given, not the display mode at report time.
 */
class JankLateTest {
    private val period120 = 8_333_333L
    private val period60 = 16_666_667L

    @Test
    fun `JANK-LATE-1 a frame is late only past its own deadline, and a first draw is not judged`() {
        // 12 ms on a 120 Hz screen whose frame was given two periods: slow by the old rule, on time by its deadline.
        assertEquals(FrameJudge.Frame(slow = 1, frozen = 0, judged = 1, late = 0, hz = 1e9 / period120), FrameJudge.of(12_000_000, 2 * period120, period120, firstDraw = false))
        assertEquals(1, FrameJudge.of(20_000_000, 2 * period120, period120, firstDraw = false).late)
        // Below Android 12 there is no deadline: one period, as before.
        assertEquals(1, FrameJudge.of(20_000_000, null, period60, firstDraw = false).late)
        assertEquals(0, FrameJudge.of(15_000_000, null, period60, firstDraw = false).late)
        // A window's first frame (a page opening) counts as drawn, never as late.
        val first = FrameJudge.of(900_000_000, period60, period60, firstDraw = true)
        assertEquals(listOf(1, 1, 0, 0), listOf(first.slow, first.frozen, first.judged, first.late))
    }

    @Test
    fun `JANK-LATE-2 the event carries the late share and the rate the frames were given next to the old share`() {
        val sent = mutableListOf<Pair<ProductScreen, JankReport>>()
        val meter = JankMeter(report = { s, r -> sent += s to r }, refreshRate = { 60.0 })
        meter.start()
        meter.screen(ProductScreen.Chat)
        repeat(100) { i ->
            val f = FrameJudge.of(if (i < 60) 12_000_000 else 4_000_000, 2 * period120, period120, firstDraw = i == 0)
            meter.onFrames(1, f.slow, f.frozen, f.judged, f.late, f.hz)
        }
        meter.endSession()
        val (screen, report) = sent.single()
        assertEquals(ProductScreen.Chat, screen)
        assertEquals(">30", report.pctRange, "the old rule: 60 of 100 frames past one period")
        assertEquals("<1", report.latePctRange, "none past its deadline")
        assertEquals("120", report.seenHz, "the frames were given 120 Hz")
        assertEquals("60", report.hz, "the display mode when the report was made, as before")
    }
}
