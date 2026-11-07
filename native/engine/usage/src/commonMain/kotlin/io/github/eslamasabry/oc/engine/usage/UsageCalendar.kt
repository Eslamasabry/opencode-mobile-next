package io.github.eslamasabry.oc.engine.usage

import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.isoDayNumber
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

    /**
     * [epochMs] on the local clock: the calendar day and the 24-hour time, for an absolute "resets at" line (B-159).
     * [weekday] is ISO, 1 = Monday … 7 = Sunday.
     */
    public fun stamp(epochMs: Long, zoneId: String): UsageStamp? {
        val zone = zoneOf(zoneId) ?: return null
        val local = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(zone)
        return UsageStamp(local.date.toString(), local.hour, local.minute, local.date.dayOfWeek.isoDayNumber)
    }

    /**
     * Every local calendar day (`YYYY-MM-DD`) from the one holding [fromMs] to the one holding [toMs] − 1, oldest
     * first, at most [limit] (the newest are kept). Empty when the range is inverted or the zone unreadable.
     */
    public fun daysBetween(fromMs: Long, toMs: Long, zoneId: String, limit: Int = 400): List<String> {
        val zone = zoneOf(zoneId) ?: return emptyList()
        if (toMs <= fromMs || limit <= 0) return emptyList()
        val first = Instant.fromEpochMilliseconds(fromMs).toLocalDateTime(zone).date
        val last = Instant.fromEpochMilliseconds(toMs - 1).toLocalDateTime(zone).date
        val out = ArrayDeque<String>()
        var day = last
        while (day >= first && out.size < limit) {
            out.addFirst(day.toString())
            day = day.minus(1, DateTimeUnit.DAY)
        }
        return out.toList()
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

/** A moment on the local clock (see [UsageCalendar.stamp]). [day] is `YYYY-MM-DD`. */
public data class UsageStamp(val day: String, val hour: Int, val minute: Int, val weekday: Int) {
    /** `HH:MM`, 24-hour, the way the Plans page writes reset times. */
    public val clock: String
        get() = hour.toString().padStart(2, '0') + ":" + minute.toString().padStart(2, '0')
}
