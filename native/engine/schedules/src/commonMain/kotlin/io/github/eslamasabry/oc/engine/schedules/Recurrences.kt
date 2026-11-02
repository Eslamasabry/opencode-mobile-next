package io.github.eslamasabry.oc.engine.schedules

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Port of lib/scheduling/recurrence.dart. */
public object Recurrences {
    /** A delayed run is late only after the operating system's 15-minute grace. */
    public const val LATE_TOLERANCE_MS: Long = 15 * 60_000L

    private const val HOUR_MS = 3_600_000L
    private const val FRIDAY = 5

    /**
     * One occurrence strictly after [afterMs]; null when a once schedule is over. Intervals keep their absolute
     * anchor, so delayed work does not drift. Daily and weekday schedules follow the local calendar of [zone],
     * including zone and daylight-saving changes (calendar construction, never "add 24 hours").
     */
    public fun next(recurrence: Recurrence, afterMs: Long, zone: TimeZone): Long? {
        require(recurrence.valid) { "invalid_recurrence" }
        return when (recurrence.kind) {
            RecurrenceKind.Once -> recurrence.atMs!!.takeIf { it > afterMs }
            RecurrenceKind.Interval -> {
                val anchor = recurrence.atMs!!
                if (anchor > afterMs) {
                    anchor
                } else {
                    val period = recurrence.hours * HOUR_MS
                    anchor + ((afterMs - anchor) / period + 1) * period
                }
            }
            RecurrenceKind.Daily, RecurrenceKind.Weekdays -> {
                val weekdaysOnly = recurrence.kind == RecurrenceKind.Weekdays
                val today = Instant.fromEpochMilliseconds(afterMs).toLocalDateTime(zone).date
                for (offset in 0..7) {
                    val day = today.plus(DatePeriod(days = offset))
                    val candidate =
                        LocalDateTime(day.year, day.month, day.day, recurrence.hour, recurrence.minute)
                            .toInstant(zone)
                            .toEpochMilliseconds()
                    if ((!weekdaysOnly || day.dayOfWeek.isoDayNumber <= FRIDAY) && candidate > afterMs) {
                        return candidate
                    }
                }
                error("no_next_weekday")
            }
        }
    }

    /** The next local [hour]:[minute] strictly after [nowMs] (the morning digest). */
    public fun nextDigest(digest: ScheduleDigest, nowMs: Long, zone: TimeZone): Long =
        next(Recurrence.daily(digest.hour, digest.minute), nowMs, zone)!!
}

/** What one due check decided (Flutter `ScheduledPromptDue`). */
public data class Due(val scheduledAtMs: Long, val late: Boolean, val shouldRun: Boolean, val nextRunAtMs: Long?)

/**
 * Collapses every missed occurrence into one catch-up or one skipped record. The caller durably claims its identity
 * before any network mutation. Null when the schedule is off or not yet due.
 */
public fun ScheduledPrompt.due(nowMs: Long, zone: TimeZone): Due? {
    val due = nextRunAtMs
    if (!enabled || due == null || due > nowMs) return null
    val late = nowMs - due > Recurrences.LATE_TOLERANCE_MS
    return Due(
        scheduledAtMs = due,
        late = late,
        shouldRun = !late || missedPolicy == MissedRunPolicy.CatchUp,
        nextRunAtMs = Recurrences.next(recurrence, nowMs, zone),
    )
}
