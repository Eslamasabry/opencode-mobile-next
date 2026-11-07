package io.github.eslamasabry.oc.feature.usage

import io.github.eslamasabry.oc.feature.usage.presentation.UsageDay
import io.github.eslamasabry.oc.feature.usage.presentation.UsageFigures
import io.github.eslamasabry.oc.feature.usage.presentation.UsageQuery
import io.github.eslamasabry.oc.feature.usage.presentation.UsageRange
import io.github.eslamasabry.oc.feature.usage.presentation.UsageReport
import io.github.eslamasabry.oc.feature.usage.presentation.UsageTokens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf

/** Usage › Cost (B-160): the 24h · 7d · 30d · 90d segment, the provider split and the daily series. */
class CostRangesTest {
    private val now = Instant.parse("2026-11-07T10:00:00Z")

    @Test
    fun `B-160 the segment is 24 hours, 7, 30 and 90 days, in that order`() {
        assertEquals(
            listOf(UsageRange.Last24Hours, UsageRange.SevenDays, UsageRange.ThirtyDays, UsageRange.NinetyDays),
            UsageRange.segment,
        )
    }

    @Test
    fun `B-160 24 hours is rolling, the day ranges start at local midnight and include today`() {
        val day = UsageQuery.forRange(UsageRange.Last24Hours, now, "Africa/Cairo", null)!!
        assertEquals(86_400_000L, day.toMs - day.fromMs!!)
        assertEquals(now.toEpochMilliseconds() + 1, day.toMs)
        // Cairo is UTC+2 in November: local midnight of 1 November is 31 October 22:00 UTC.
        val week = UsageQuery.forRange(UsageRange.SevenDays, now, "Africa/Cairo", null)!!
        assertEquals(Instant.parse("2026-10-31T22:00:00Z").toEpochMilliseconds(), week.fromMs)
        val quarter = UsageQuery.forRange(UsageRange.NinetyDays, now, "Africa/Cairo", null)!!
        assertEquals(Instant.parse("2026-08-09T21:00:00Z").toEpochMilliseconds(), quarter.fromMs)
        // An unreadable zone still sends nothing.
        assertNull(UsageQuery.forRange(UsageRange.SevenDays, now, "Not/AZone", null))
    }

    @Test
    fun `B-160 budgets stay on Flutter's four ranges, so a stored budget is never written in a shape it refuses`() {
        assertEquals(
            setOf(UsageRange.Today, UsageRange.ThirtyDays, UsageRange.Year, UsageRange.AllTime),
            UsageRange.entries.filter { it.hasBudgets }.toSet(),
        )
        assertFalse(UsageRange.Last24Hours.charted)
        assertFalse(UsageRange.Today.charted)
        assertTrue(UsageRange.SevenDays.charted)
    }

    @Test
    fun `B-160 a provider's share of cost is stated only when there is a cost to share`() {
        assertEquals(0.25, UsageFigures.share(1.0, 4.0)!!, 1e-9)
        assertNull(UsageFigures.share(null, 4.0))
        assertNull(UsageFigures.share(1.0, 0.0))
        assertNull(UsageFigures.share(Double.NaN, 4.0))
    }

    @Test
    fun `B-160 the daily series has every day of the range, with the days the server left out as zero`() {
        val report =
            UsageReport(
                fromMs = Instant.parse("2026-11-01T00:00:00Z").toEpochMilliseconds(),
                toMs = Instant.parse("2026-11-04T09:00:00Z").toEpochMilliseconds(),
                sessions = 1,
                subagents = 0,
                prompts = 1,
                steps = 9,
                activeDays = 2,
                streak = 1,
                cost = 1.0,
                tokens = UsageTokens(input = 1),
                tools = null,
                models = persistentListOf(),
                activity = persistentListOf(UsageDay("2026-11-04", 4), UsageDay("2026-11-02", 5)),
            )
        assertEquals(
            listOf(
                UsageDay("2026-11-01", 0),
                UsageDay("2026-11-02", 5),
                UsageDay("2026-11-03", 0),
                UsageDay("2026-11-04", 4),
            ),
            UsageFigures.dailySteps(report, "UTC"),
        )
        assertEquals(emptyList(), UsageFigures.dailySteps(report, "Not/AZone"))
    }
}
