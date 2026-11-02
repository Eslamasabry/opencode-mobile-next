package io.github.eslamasabry.oc.feature.usage

import io.github.eslamasabry.oc.feature.usage.presentation.UsageFigures
import io.github.eslamasabry.oc.feature.usage.presentation.UsageModel
import io.github.eslamasabry.oc.feature.usage.presentation.UsageQuery
import io.github.eslamasabry.oc.feature.usage.presentation.UsageRange
import io.github.eslamasabry.oc.feature.usage.presentation.UsageTokens
import io.github.eslamasabry.oc.feature.usage.presentation.UsageTools
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class UsageQueryTest {
    // 2026-03-29 12:00 in Berlin: the night before had the spring-forward (a 23-hour local day).
    private val now = Instant.parse("2026-03-29T10:00:00Z")

    @Test
    fun `B-PROTO-122 requests are global unless a project is chosen and always send the native time zone`() {
        val all = UsageQuery.forRange(UsageRange.ThirtyDays, now, "Europe/Berlin", null)!!.toWire()
        assertFalse("project" in all)
        assertEquals("Europe/Berlin", all["timezone"])
        assertEquals("summary", all["tools"])
        val one = UsageQuery.forRange(UsageRange.ThirtyDays, now, "Europe/Berlin", "prj_1")!!.toWire()
        assertEquals("prj_1", one["project"])
    }

    @Test
    fun `B-PROTO-122 calendar ranges start at local midnight, include today, and all time omits from`() {
        val today = UsageQuery.forRange(UsageRange.Today, now, "Europe/Berlin", null)!!
        // Local midnight of 2026-03-29 in Berlin is 2026-03-28T23:00Z (still +01:00 at midnight).
        assertEquals(Instant.parse("2026-03-28T23:00:00Z").toEpochMilliseconds(), today.fromMs)
        assertEquals(now.toEpochMilliseconds() + 1, today.toMs)
        val month = UsageQuery.forRange(UsageRange.ThirtyDays, now, "Europe/Berlin", null)!!
        assertEquals(Instant.parse("2026-02-27T23:00:00Z").toEpochMilliseconds(), month.fromMs)
        val year = UsageQuery.forRange(UsageRange.Year, now, "Europe/Berlin", null)!!
        assertEquals(Instant.parse("2025-12-31T23:00:00Z").toEpochMilliseconds(), year.fromMs)
        val ever = UsageQuery.forRange(UsageRange.AllTime, now, "Europe/Berlin", null)!!
        assertNull(ever.fromMs)
        assertFalse("from" in ever.toWire())
    }

    @Test
    fun `B-PROTO-122 an unreadable time zone never silently becomes UTC and a blank project never becomes all projects`() {
        assertNull(UsageQuery.forRange(UsageRange.Today, now, null, null))
        assertNull(UsageQuery.forRange(UsageRange.Today, now, "  ", null))
        assertNull(UsageQuery.forRange(UsageRange.Today, now, "Not/AZone", null))
        assertNull(UsageQuery.forRange(UsageRange.Today, now, "Europe/Berlin", " "))
        assertFailsWith<IllegalArgumentException> { UsageQuery(null, 10, "", null).toWire() }
        assertFailsWith<IllegalArgumentException> { UsageQuery(20, 10, "UTC", null).toWire() }
        assertFailsWith<IllegalArgumentException> { UsageQuery(null, 10, "UTC", "").toWire() }
    }

    private fun model(provider: String, id: String, variant: String? = null, cost: Double = 1.0, steps: Long = 1) =
        UsageModel(provider, id, variant, steps, UsageTokens(input = 10), cost)

    @Test
    fun `B-PROTO-121 provider rollups use returned ids and variants are not new models`() {
        val providers =
            UsageFigures.providers(
                listOf(
                    model("anthropic", "claude-x", cost = 2.0),
                    model("anthropic", "claude-x", variant = "high", cost = 1.0),
                    model("openai", "gpt-y", cost = 0.5),
                )
            )
        assertEquals(listOf("anthropic", "openai"), providers.map { it.providerId })
        assertEquals(1, providers.first().models)
        assertEquals(3.0, providers.first().cost)
        assertEquals(20, providers.first().tokens)
    }

    @Test
    fun `B-PROTO-121 a cost overflow is unavailable and empty data invents no providers`() {
        assertTrue(UsageFigures.providers(emptyList()).isEmpty())
        val big = UsageFigures.providers(listOf(model("p", "a", cost = Double.MAX_VALUE), model("p", "b", cost = Double.MAX_VALUE)))
        assertNull(big.single().cost)
    }

    @Test
    fun `B-AGENT-310 costs read as estimates in dollars, tokens compactly, and a tiny cost is not shown as zero`() {
        assertEquals("$0.00", UsageFigures.money(0.0))
        assertEquals("$1,234.50", UsageFigures.money(1234.5))
        assertEquals("$0.00345", UsageFigures.money(0.00345))
        assertTrue(UsageFigures.isTiny(0.0000001))
        assertFalse(UsageFigures.isTiny(0.0))
        assertEquals("950", UsageFigures.compact(950))
        assertEquals("12.3K", UsageFigures.compact(12_345))
        assertEquals("4M", UsageFigures.compact(4_000_000))
        assertEquals("1,234,567", UsageFigures.count(1_234_567))
    }

    @Test
    fun `B-NAV-078 the success rate leaves out unfinished calls and says nothing when none finished`() {
        assertEquals(0.75, UsageTools(calls = 10, succeeded = 3, failed = 1, unfinished = 6).successRate)
        assertNull(UsageTools(calls = 2, succeeded = 0, failed = 0, unfinished = 2).successRate)
        assertEquals("75%", UsageFigures.percent(0.75))
    }
}
