package io.github.eslamasabry.oc.feature.schedules.ui

import io.github.eslamasabry.oc.engine.schedules.Recurrence
import io.github.eslamasabry.oc.engine.schedules.RecurrenceKind
import io.github.eslamasabry.oc.engine.schedules.RunStatus
import io.github.eslamasabry.oc.feature.schedules.presentation.LastRunUi
import io.github.eslamasabry.oc.feature.schedules.presentation.ScheduleRowUi
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

// Pure text for the list rows (no Compose): repeat words, next run, last run.

internal fun scheduleLine(row: ScheduleRowUi, nowMs: Long, words: ScheduleWords): String {
    val repeat = repeatWords(row.recurrence, nowMs, words)
    val next =
        when {
            !row.enabled && row.nextRunAtMs == null && row.recurrence.kind == RecurrenceKind.Once -> words.over
            !row.enabled -> words.paused
            row.nextRunAtMs != null -> words.next(moment(row.nextRunAtMs, nowMs, words))
            else -> words.over
        }
    return listOfNotNull(repeat, next, if (row.continuesConversation) words.continues else null).joinToString(" · ")
}

internal fun repeatWords(recurrence: Recurrence, nowMs: Long, words: ScheduleWords): String {
    val clock = "${recurrence.hour.toString().padStart(2, '0')}:${recurrence.minute.toString().padStart(2, '0')}"
    return when (recurrence.kind) {
        RecurrenceKind.Once -> words.once(moment(recurrence.atMs ?: nowMs, nowMs, words))
        RecurrenceKind.Daily -> words.daily(clock)
        RecurrenceKind.Weekdays -> words.weekdays(clock)
        RecurrenceKind.Interval -> words.everyHours(recurrence.hours)
    }
}

internal fun lastLine(last: LastRunUi, nowMs: Long, words: ScheduleWords): String {
    val at = moment(last.atMs, nowMs, words)
    val line =
        when (last.status) {
            RunStatus.Ran -> if (last.sessionId != null) words.lastRan(at) else words.lastStarted(at)
            RunStatus.Skipped -> words.lastSkipped(at)
            RunStatus.Failed -> words.lastFailed(at)
            RunStatus.Running -> words.lastRunning(at)
        }
    return if (last.late) "$line · ${words.late}" else line
}

/** "Today 09:00", "Tomorrow 07:30", "Fri 18:00" within a week, else the date. Device time zone. */
internal fun moment(ms: Long, nowMs: Long, words: ScheduleWords, zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val at = Instant.fromEpochMilliseconds(ms).toLocalDateTime(zone)
    val now = Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(zone)
    val clock = "${at.hour.toString().padStart(2, '0')}:${at.minute.toString().padStart(2, '0')}"
    val days = (at.date.toEpochDays() - now.date.toEpochDays()).toLong()
    val day =
        when {
            days == 0L -> words.today
            days == 1L -> words.tomorrow
            days in 2L..6L -> words.weekdayNames[at.dayOfWeek.ordinal]
            else -> at.date.toString()
        }
    return "$day $clock"
}
