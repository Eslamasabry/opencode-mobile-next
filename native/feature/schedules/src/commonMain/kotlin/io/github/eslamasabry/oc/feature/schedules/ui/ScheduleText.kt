package io.github.eslamasabry.oc.feature.schedules.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.eslamasabry.oc.engine.schedules.Recurrence
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.engine.schedules.RecurrenceKind
import io.github.eslamasabry.oc.engine.schedules.RunStatus
import io.github.eslamasabry.oc.feature.schedules.presentation.LastRunUi
import io.github.eslamasabry.oc.feature.schedules.presentation.ScheduleRowUi
import io.github.eslamasabry.oc.engine.schedules.ScheduleTime

// Pure text for the list rows: repeat words, next run, last run. A clock is written by [clockOf] when one is given: the
// phone's own 12 or 24 hour style, the same one the time wheel uses (B-440 look 2).

/** The clock writer of the time wheel (KitPlatform.messageTime on today at that time): the phone's own 12/24 hour setting. */
@Composable
internal fun rememberClockOf(nowMs: Long): (Int, Int) -> String {
    val platform = LocalKitPlatform.current
    return remember(platform, nowMs) {
        { hour: Int, minute: Int ->
            platform.messageTime(ScheduleTime.todayAt(nowMs, hour, minute), nowMs)
                ?: "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
        }
    }
}

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

internal fun repeatWords(
    recurrence: Recurrence,
    nowMs: Long,
    words: ScheduleWords,
    clockOf: ((Int, Int) -> String)? = null,
): String {
    val clock =
        clockOf?.invoke(recurrence.hour, recurrence.minute)
            ?: "${recurrence.hour.toString().padStart(2, '0')}:${recurrence.minute.toString().padStart(2, '0')}"
    return when (recurrence.kind) {
        RecurrenceKind.Once -> words.once(moment(recurrence.atMs ?: nowMs, nowMs, words, clockOf = clockOf))
        RecurrenceKind.Daily -> words.daily(clock)
        RecurrenceKind.Weekdays -> words.weekdays(clock)
        RecurrenceKind.Interval -> words.everyHours(recurrence.hours)
    }
}

internal fun lastLine(
    last: LastRunUi,
    nowMs: Long,
    words: ScheduleWords,
    clockOf: ((Int, Int) -> String)? = null,
): String {
    val at = moment(last.atMs, nowMs, words, clockOf = clockOf)
    val line =
        when (last.status) {
            // "replied" only for a run followed to its reply; one the server accepted but never confirmed was "started".
            RunStatus.Ran ->
                if (last.sessionId != null && last.errorCode == null) words.lastRan(at) else words.lastStarted(at)
            RunStatus.Skipped -> words.lastSkipped(at)
            RunStatus.Failed -> words.lastFailed(at)
            RunStatus.Running -> words.lastRunning(at)
        }
    return if (last.late) "$line · ${words.late}" else line
}

/** "Today 09:00", "Tomorrow 07:30", "Fri 18:00" within a week, else the date. Device time zone unless [zoneId]. */
internal fun moment(
    ms: Long,
    nowMs: Long,
    words: ScheduleWords,
    zoneId: String? = null,
    /** N-44: the clock as the screen's own time wheel writes it ("9:00 AM"), so the two never disagree. */
    clockWords: String? = null,
    /** The phone's clock style for a time of day; used when [clockWords] is not given. */
    clockOf: ((Int, Int) -> String)? = null,
): String {
    val at = ScheduleTime.moment(ms, nowMs, zoneId)
    val clock = clockWords ?: clockOf?.invoke(at.hour, at.minute) ?: "${at.hour.toString().padStart(2, '0')}:${at.minute.toString().padStart(2, '0')}"
    val day =
        when (at.dayOffset) {
            0L -> words.today
            1L -> words.tomorrow
            in 2L..6L -> words.weekdayNames[at.weekday]
            else -> at.date
        }
    return "$day $clock"
}
