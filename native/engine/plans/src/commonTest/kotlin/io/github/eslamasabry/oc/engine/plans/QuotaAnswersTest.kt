package io.github.eslamasabry.oc.engine.plans

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class QuotaAnswersTest {
    private fun stats(from: Long, to: Long) = UsageStatisticsRange(from, to)

    @Test
    fun `B-CHAT-090 weekly 60 percent used means 40 percent remaining`() {
        val window = QuotaAnswerWindow(id = "weekly", usedPercent = 60.0, durationMinutes = 10080)
        assertEquals(40.0, window.remainingPercent)
        assertTrue(window.isWeekly)
        assertFalse(QuotaAnswerWindow(id = "short", usedPercent = 60.0).isWeekly)
        assertEquals(0.0, QuotaAnswerWindow(id = "exceeded", usedPercent = 120.0).remainingPercent)
    }

    @Test
    fun `B-CHAT-090 offline reads keep observation time and increase age`() {
        val observed = utc(2026, 9, 27, 10)
        val snapshot = QuotaAnswerSnapshot(QuotaAnswerSource.CodexAccount, observed, emptyList())
        assertEquals(5.minutes, snapshot.age(observed + 5 * MIN))
        assertEquals(2.hours, snapshot.age(observed + 2 * HOUR))
        assertEquals(observed, snapshot.observedAt)
        assertEquals(Duration.ZERO, snapshot.age(observed - SEC))
    }

    @Test
    fun `B-CHAT-090 a window expires at its reset while an unknown reset stays unknown`() {
        val reset = utc(2026, 9, 29)
        val snapshot =
            QuotaAnswerSnapshot(
                QuotaAnswerSource.CodexAccount,
                reset - DAY,
                listOf(
                    QuotaAnswerWindow(id = "unknown", usedPercent = 20.0),
                    QuotaAnswerWindow(id = "weekly", usedPercent = 60.0, resetsAt = reset),
                ),
            )
        assertFalse(snapshot.isExpired(reset - 1))
        assertTrue(snapshot.isExpired(reset))
        assertTrue(snapshot.isExpired(reset + DAY))
        val unknown =
            QuotaAnswerSnapshot(
                QuotaAnswerSource.Collector,
                reset,
                listOf(QuotaAnswerWindow(id = "unknown", usedPercent = 20.0)),
            )
        assertFalse(unknown.isExpired(reset + 365 * DAY))
    }

    @Test
    fun `B-CHAT-090 snapshot copies its windows and does not allow mutation`() {
        val input = mutableListOf(QuotaAnswerWindow(id = "weekly", usedPercent = 60.0))
        val snapshot = QuotaAnswerSnapshot(QuotaAnswerSource.CodexAccount, utc(2026, 9, 27), input)
        input.clear()
        assertEquals("weekly", snapshot.windows.single().id)
        assertFalse(snapshot.windows is MutableList<*>)
    }

    @Test
    fun `B-CHAT-091 Spent preserves Sep 2-6 bounds when thirty-day response is narrower`() {
        val actualFrom = utc(2026, 9, 2)
        val actualTo = utc(2026, 9, 7)
        val period =
            SpentPeriod.fromUsage(
                query = UsageQuery(from = utc(2026, 8, 9), to = utc(2026, 9, 8), timezone = "UTC"),
                statistics = stats(actualFrom, actualTo),
                requestedRange = UsageRange.ThirtyDays,
            )
        assertEquals(actualFrom, period.from)
        assertEquals(actualTo, period.to)
        assertEquals(UsageRange.ThirtyDays, period.requestedRange)
        assertFalse(period.matchesRequestedRange)
        // The last included instant is still September 6.
        assertEquals(utc(2026, 9, 6), (period.to - 1) / DAY * DAY)
    }

    @Test
    fun `B-CHAT-091 Spent reports matching submitted interval without extending it`() {
        val from = utc(2026, 9, 1)
        val to = utc(2026, 9, 27, 10)
        val period =
            SpentPeriod.fromUsage(
                UsageQuery(from = from, to = to, timezone = "UTC"),
                stats(from, to),
                UsageRange.ThirtyDays,
            )
        assertTrue(period.matchesRequestedRange)
        assertEquals(from, period.from)
        assertEquals(to, period.to)
    }

    @Test
    fun `B-CHAT-091 Spent notices a changed lower bound even with matching upper bound`() {
        val from = utc(2026, 8, 29)
        val to = utc(2026, 9, 27, 10)
        val period =
            SpentPeriod.fromUsage(
                UsageQuery(from = from, to = to, timezone = "UTC"),
                stats(from + DAY, to),
                UsageRange.ThirtyDays,
            )
        assertFalse(period.matchesRequestedRange)
    }

    @Test
    fun `B-CHAT-091 all-time accepts first available record but still checks upper bound`() {
        val from = utc(2020, 1, 1)
        val to = utc(2026, 9, 27)
        val query = UsageQuery(to = to, timezone = "UTC")
        val period = SpentPeriod.fromUsage(query, stats(from, to), UsageRange.AllTime)
        assertTrue(period.matchesRequestedRange)
        assertEquals(from, period.from)
        assertFalse(SpentPeriod.fromUsage(query, stats(from, to - DAY), UsageRange.AllTime).matchesRequestedRange)
        assertFalse(SpentPeriod.fromUsage(query, stats(from, to), UsageRange.ThirtyDays).matchesRequestedRange)
    }
}
