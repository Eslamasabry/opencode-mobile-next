package io.github.eslamasabry.oc.feature.usage.presentation

import io.github.eslamasabry.oc.engine.usage.UsageCalendar
import kotlin.time.Instant

/**
 * The ranges the Usage page offers: Flutter's `UsageRange` (Today, 30 days, Year, All time) plus the Cost
 * segment's 24 hours, 7 days and 90 days (B-160).
 */
public enum class UsageRange {
    Today,
    /** The last 24 hours, rolling (not a calendar day). */
    Last24Hours,
    SevenDays,
    ThirtyDays,
    NinetyDays,
    Year,
    AllTime;

    /**
     * Budgets are stored in Flutter's format, which knows only Flutter's four ranges; a budget saved for another
     * range would be refused (and flag the store as damaged) when it is read back, so the new ranges offer none.
     */
    public val hasBudgets: Boolean
        get() = this == Today || this == ThirtyDays || this == Year || this == AllTime

    /** Activity comes back per day; ranges shorter than two days have no useful chart. */
    public val charted: Boolean
        get() = this != Today && this != Last24Hours

    public companion object {
        /** The Cost segment, in order (B-160). */
        public val segment: List<UsageRange> = listOf(Last24Hours, SevenDays, ThirtyDays, NinetyDays)
    }
}

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
        private const val DAY_MS: Long = 86_400_000

        /**
         * Calendar arithmetic, not 24-hour subtraction: a local day can have 23 or 25 hours. Null when [timezone] is not
         * a time zone this device can read (the caller says so instead of falling back to UTC), or when a chosen
         * project has a blank id.
         */
        public fun forRange(range: UsageRange, now: Instant, timezone: String?, projectId: String?): UsageQuery? {
            val id = timezone?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            // A chosen project that has no id is refused, never widened to all projects.
            if (projectId != null && projectId.isBlank()) return null
            // Calendar arithmetic in the device's own zone; an unreadable zone is refused, never dated in UTC.
            if (!UsageCalendar.isReadable(id)) return null
            val fromMs =
                when (range) {
                    UsageRange.Today -> UsageCalendar.startOfDayMs(now, id) ?: return null
                    // Rolling: the same instant one day ago, so it always spans 24 hours of activity.
                    UsageRange.Last24Hours -> now.toEpochMilliseconds() + 1 - DAY_MS
                    UsageRange.SevenDays -> UsageCalendar.startOfDayMs(now, id, daysBack = 6) ?: return null
                    UsageRange.ThirtyDays -> UsageCalendar.startOfDayMs(now, id, daysBack = 29) ?: return null
                    UsageRange.NinetyDays -> UsageCalendar.startOfDayMs(now, id, daysBack = 89) ?: return null
                    UsageRange.Year -> UsageCalendar.startOfYearMs(now, id) ?: return null
                    UsageRange.AllTime -> null
                }
            return UsageQuery(
                fromMs = fromMs,
                toMs = now.toEpochMilliseconds() + 1,
                timezone = id,
                projectId = projectId,
            )
        }
    }
}
