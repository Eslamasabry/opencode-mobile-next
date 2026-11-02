package io.github.eslamasabry.oc.engine.usage

import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/**
 * Calendar days on the person's own clock (B-PROTO-122). Calendar arithmetic, not 24-hour subtraction: a local day can
 * have 23 or 25 hours. Every function returns null when [zoneId] is not a time zone this device can read, so the
 * caller says so instead of falling back to UTC.
 */
public object UsageCalendar {
    /** The start, in epoch ms, of the local day [daysBack] calendar days before the day that holds [now]. */
    public fun startOfDayMs(now: Instant, zoneId: String, daysBack: Int = 0): Long? {
        val zone = zoneOf(zoneId) ?: return null
        val today = now.toLocalDateTime(zone).date
        return today.minus(daysBack, DateTimeUnit.DAY).atStartOfDayIn(zone).toEpochMilliseconds()
    }

    /** The start, in epoch ms, of 1 January of the local year that holds [now]. */
    public fun startOfYearMs(now: Instant, zoneId: String): Long? {
        val zone = zoneOf(zoneId) ?: return null
        return LocalDate(now.toLocalDateTime(zone).date.year, 1, 1).atStartOfDayIn(zone).toEpochMilliseconds()
    }

    /** The local calendar day (`YYYY-MM-DD`) that holds [epochMs]. */
    public fun dayLabel(epochMs: Long, zoneId: String): String? {
        val zone = zoneOf(zoneId) ?: return null
        return Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(zone).date.toString()
    }

    /** True when [zoneId] names a time zone this device can read. */
    public fun isReadable(zoneId: String): Boolean = zoneOf(zoneId) != null

    private fun zoneOf(id: String): TimeZone? =
        try {
            TimeZone.of(id)
        } catch (_: IllegalArgumentException) {
            null
        }
}
