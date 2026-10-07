package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class RestartBackoffTest {
    @Test
    fun repeatedShortExitsDoubleDelayUntilTheSixtySecondCap() {
        val backoff = RestartBackoff()
        val delays = List(10) { backoff.exited(uptimeMs = 100L, nowMs = 10_000L) }

        assertEquals(
            listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 32_000L,
                60_000L, 60_000L, 60_000L, 60_000L),
            delays,
        )
    }

    @Test
    fun remainingDelayCountsDownAndStaysZeroAfterTheDeadline() {
        val backoff = RestartBackoff()
        assertEquals(0L, backoff.remainingMs(10_000L))
        backoff.exited(uptimeMs = 0L, nowMs = 10_000L)

        assertEquals(1_000L, backoff.remainingMs(10_000L))
        assertEquals(1L, backoff.remainingMs(10_999L))
        assertEquals(0L, backoff.remainingMs(11_000L))
        assertEquals(0L, backoff.remainingMs(20_000L))
    }

    @Test
    fun thirtySecondsOfUptimeResetsDelayButOneMillisecondLessDoesNot() {
        val backoff = RestartBackoff()
        backoff.exited(uptimeMs = 0L, nowMs = 10_000L)
        backoff.exited(uptimeMs = 0L, nowMs = 20_000L)

        assertEquals(4_000L, backoff.exited(uptimeMs = 29_999L, nowMs = 30_000L))
        assertEquals(1_000L, backoff.exited(uptimeMs = 30_000L, nowMs = 40_000L))
        assertEquals(1_000L, backoff.remainingMs(40_000L))
        assertEquals(2_000L, backoff.exited(uptimeMs = 0L, nowMs = 50_000L))
    }

    @Test
    fun explicitResetClearsAnOutstandingDeadlineAndStartsAtOneSecond() {
        val backoff = RestartBackoff()
        repeat(8) { backoff.exited(uptimeMs = 0L, nowMs = 10_000L) }
        backoff.reset()

        assertEquals(0L, backoff.remainingMs(10_000L))
        assertEquals(1_000L, backoff.exited(uptimeMs = 0L, nowMs = 20_000L))
    }
}
