package io.github.eslamasabry.oc.feature.usage

import io.github.eslamasabry.oc.feature.usage.presentation.LimitMath
import io.github.eslamasabry.oc.feature.usage.presentation.LimitPaceMath
import io.github.eslamasabry.oc.feature.usage.presentation.PaceForecast
import io.github.eslamasabry.oc.feature.usage.presentation.PaceMoment
import io.github.eslamasabry.oc.feature.usage.presentation.PaceRateUnit
import io.github.eslamasabry.oc.feature.usage.presentation.ResetIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Pure unit tests for no-history pace bend math (B-508). */
class LimitPaceForecastTest {
    // Saturday 2026-11-07 12:00 UTC.
    private val now = Instant.parse("2026-11-07T12:00:00Z").toEpochMilliseconds()
    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour

    @Test
    fun `B-508 no reset time, unknown length, or passed reset yields no forecast`() {
        assertNull(LimitPaceMath.forecast(0.5, now, resetsAtMs = null, lengthMs = LimitMath.WEEK_MS, zone = "UTC"))
        assertNull(LimitPaceMath.forecast(0.5, now, resetsAtMs = now + day, lengthMs = null, zone = "UTC"))
        assertNull(LimitPaceMath.forecast(0.5, now, resetsAtMs = now + day, lengthMs = 0L, zone = "UTC"))
        assertNull(LimitPaceMath.forecast(0.5, now, resetsAtMs = now, lengthMs = LimitMath.WEEK_MS, zone = "UTC"))
        assertNull(
            LimitPaceMath.forecast(0.5, now, resetsAtMs = now - hour, lengthMs = LimitMath.WEEK_MS, zone = "UTC"),
        )
    }

    @Test
    fun `B-508 under five percent elapsed or at most one percent used returns TooLittle`() {
        val early =
            LimitPaceMath.forecast(
                left = 0.85,
                nowMs = now,
                resetsAtMs = now + 7 * day - 3 * hour,
                lengthMs = LimitMath.WEEK_MS,
                zone = "UTC",
            )
        assertIs<PaceForecast.TooLittle>(early)

        val tinyUse =
            LimitPaceMath.forecast(
                left = 0.99,
                nowMs = now,
                resetsAtMs = now + 150 * minute,
                lengthMs = LimitMath.FIVE_HOURS_MS,
                zone = "UTC",
            )
        assertIs<PaceForecast.TooLittle>(tinyUse)
    }

    @Test
    fun `B-508 on course to last projects remaining share at reset`() {
        val forecast =
            LimitPaceMath.forecast(
                left = 0.70,
                nowMs = now,
                resetsAtMs = now + 84 * hour,
                lengthMs = LimitMath.WEEK_MS,
                zone = "UTC",
            )
        val lasts = assertIs<PaceForecast.Lasts>(forecast)
        assertEquals(40, lasts.atResetPercent)
        assertEquals(0.40, lasts.atReset, 1e-6)
        assertTrue(!lasts.frame.shortWindow)
    }

    @Test
    fun `B-508 weekly ahead of pace compares since-reset rate against needed-to-last rate`() {
        // 2d elapsed of 7d, 50% used (50% left), 5d to reset.
        // Since reset = 25%/day; needed to last = 50%/5d = 10%/day (NOT 100%/7d = 14%/day).
        val forecast =
            LimitPaceMath.forecast(
                left = 0.50,
                nowMs = now,
                resetsAtMs = now + 5 * day,
                lengthMs = LimitMath.WEEK_MS,
                zone = "UTC",
            )
        val runsOut = assertIs<PaceForecast.RunsOut>(forecast)
        assertEquals(PaceRateUnit.PerDay, runsOut.comparison.unit)
        assertEquals(25, runsOut.comparison.sinceResetPercent)
        assertEquals(10, runsOut.comparison.neededToLastPercent)
        assertEquals(ResetIn.Left(days = 3, hours = 0, minutes = 0), runsOut.gapBeforeReset)
        assertEquals(PaceMoment.Weekday(isoDay = 1, clock = "12:00"), runsOut.runsOutMoment)
        assertEquals(4.0 / 7.0, runsOut.runsOutElapsed, 1e-6)
    }

    @Test
    fun `B-508 five-hour ahead of pace computes hourly rates and same-day clock`() {
        // 2h elapsed of 5h, 85% used (15% left), 3h to reset.
        // Since reset = 42.5% -> 43%/h; needed to last = 15%/3h = 5%/h (NOT 20%/h).
        val forecast =
            LimitPaceMath.forecast(
                left = 0.15,
                nowMs = now,
                resetsAtMs = now + 3 * hour,
                lengthMs = LimitMath.FIVE_HOURS_MS,
                zone = "UTC",
            )
        val runsOut = assertIs<PaceForecast.RunsOut>(forecast)
        assertEquals(PaceRateUnit.PerHour, runsOut.comparison.unit)
        assertEquals(43, runsOut.comparison.sinceResetPercent)
        assertEquals(5, runsOut.comparison.neededToLastPercent)
        assertEquals(PaceMoment.Today("12:21"), runsOut.runsOutMoment)
        assertEquals(ResetIn.Left(days = 0, hours = 2, minutes = 39), runsOut.gapBeforeReset)
        assertTrue(runsOut.frame.shortWindow)
    }

    @Test
    fun `B-508 exhausted window returns Out and missing zone falls back to relative time`() {
        val out =
            LimitPaceMath.forecast(
                left = 0.0,
                nowMs = now,
                resetsAtMs = now + 2 * hour,
                lengthMs = LimitMath.FIVE_HOURS_MS,
                zone = null,
            )
        val exhausted = assertIs<PaceForecast.Out>(out)
        assertEquals(PaceMoment.Relative(days = 0, hours = 2, minutes = 0), exhausted.resetsMoment)
    }
}
