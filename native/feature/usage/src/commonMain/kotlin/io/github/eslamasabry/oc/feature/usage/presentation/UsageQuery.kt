package io.github.eslamasabry.oc.feature.usage.presentation

import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/** The calendar ranges the Usage page offers (Flutter `UsageRange`). */
public enum class UsageRange { Today, ThirtyDays, Year, AllTime }

/**
 * One statistics request (Flutter `UsageQuery`): a half-open `[from, to)` interval in epoch ms that includes the
 * current millisecond, the device's own time zone, and a project id only when a project is chosen (B-PROTO-122).
 */
public data class UsageQuery(val fromMs: Long?, val toMs: Long, val timezone: String, val projectId: String?) {
    /** The exact wire query. Never sends `from` for all time, never a blank project, always the time zone. */
    public fun toWire(): Map<String, String?> {
        require(timezone.isNotBlank()) { "usage query without a time zone" }
        require(fromMs == null || fromMs < toMs) { "usage query with an inverted range" }
        require(projectId == null || projectId.isNotBlank()) { "usage query with a blank project" }
        return buildMap {
            if (fromMs != null) put("from", fromMs.toString())
            put("to", toMs.toString())
            put("timezone", timezone)
            if (projectId != null) put("project", projectId)
            put("tools", "summary")
        }
    }

    public companion object {
        /**
         * Calendar arithmetic, not 24-hour subtraction: a local day can have 23 or 25 hours. Null when [timezone] is not
         * a time zone this device can read (the caller says so instead of falling back to UTC), or when a chosen
         * project has a blank id.
         */
        public fun forRange(range: UsageRange, now: Instant, timezone: String?, projectId: String?): UsageQuery? {
            val id = timezone?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            // A chosen project that has no id is refused, never widened to all projects.
            if (projectId != null && projectId.isBlank()) return null
            val zone =
                try {
                    TimeZone.of(id)
                } catch (_: IllegalArgumentException) {
                    return null
                }
            val today = now.toLocalDateTime(zone).date
            val start =
                when (range) {
                    UsageRange.Today -> today
                    UsageRange.ThirtyDays -> today.minus(29, DateTimeUnit.DAY)
                    UsageRange.Year -> LocalDate(today.year, 1, 1)
                    UsageRange.AllTime -> null
                }
            return UsageQuery(
                fromMs = start?.atStartOfDayIn(zone)?.toEpochMilliseconds(),
                toMs = now.toEpochMilliseconds() + 1,
                timezone = id,
                projectId = projectId,
            )
        }
    }
}
