package io.github.eslamasabry.opencode_mobile.graph

import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.Instant

/**
 * P-015: when the app was closed, as the home notice says it. Earlier today reads as time ago ("12 minutes ago"),
 * never a numeric stamp like "11/7/26 12:59 PM"; an earlier day reads as that day's date only.
 */
internal sealed interface ExitNoticeTime {
    data class MinutesAgo(val minutes: Int) : ExitNoticeTime

    data class HoursAgo(val hours: Int) : ExitNoticeTime

    data class OnDay(val date: LocalDate) : ExitNoticeTime
}

private const val MINUTES_PER_HOUR = 60L

internal fun exitNoticeTime(at: Instant, now: Instant, zone: ZoneId): ExitNoticeTime {
    val day = java.time.Instant.ofEpochMilli(at.toEpochMilliseconds()).atZone(zone).toLocalDate()
    val today = java.time.Instant.ofEpochMilli(now.toEpochMilliseconds()).atZone(zone).toLocalDate()
    if (day.isBefore(today)) return ExitNoticeTime.OnDay(day)
    // A clock that moved back can put the exit "after" now: still today, so at least a minute ago.
    val minutes = (now - at).inWholeMinutes.coerceAtLeast(1)
    return if (minutes < MINUTES_PER_HOUR) ExitNoticeTime.MinutesAgo(minutes.toInt())
    else ExitNoticeTime.HoursAgo((minutes / MINUTES_PER_HOUR).toInt())
}
