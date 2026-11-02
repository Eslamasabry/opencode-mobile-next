package io.github.eslamasabry.oc.engine.schedules

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** One instant on the device's wall clock, relative to "now" (no copy: the screens word it). */
public data class LocalMoment(
    /** Calendar days from today: 0 today, 1 tomorrow, negative in the past. */
    val dayOffset: Long,
    /** 0 = Monday … 6 = Sunday. */
    val weekday: Int,
    /** ISO date, `2026-11-04`. */
    val date: String,
    val hour: Int,
    val minute: Int,
)

/**
 * Wall-clock helpers for the schedule screens, so features never handle time zones themselves. [zoneId] null is the
 * device zone; tests pass a fixed id.
 */
public object ScheduleTime {
    public fun zone(zoneId: String?): TimeZone = zoneId?.let { TimeZone.of(it) } ?: TimeZone.currentSystemDefault()

    public fun moment(ms: Long, nowMs: Long, zoneId: String? = null): LocalMoment {
        val zone = zone(zoneId)
        val at = Instant.fromEpochMilliseconds(ms).toLocalDateTime(zone)
        val now = Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(zone)
        return LocalMoment(
            dayOffset = at.date.toEpochDays() - now.date.toEpochDays(),
            weekday = DayOfWeek.entries.indexOf(at.dayOfWeek),
            date = at.date.toString(),
            hour = at.hour,
            minute = at.minute,
        )
    }

    /** The instant of [date] (ISO `yyyy-MM-dd`) at [hour]:[minute] local; null when the date is not a real date. */
    public fun at(date: String, hour: Int, minute: Int, zoneId: String? = null): Long? {
        val day =
            try {
                LocalDate.parse(date.trim())
            } catch (e: IllegalArgumentException) {
                return null
            }
        return LocalDateTime(day.year, day.month, day.day, hour, minute).toInstant(zone(zoneId)).toEpochMilliseconds()
    }

    /** Today (of [nowMs]) at [hour]:[minute] local. */
    public fun todayAt(nowMs: Long, hour: Int, minute: Int, zoneId: String? = null): Long =
        at(today(nowMs, zoneId), hour, minute, zoneId)!!

    public fun today(nowMs: Long, zoneId: String? = null): String =
        Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(zone(zoneId)).date.toString()
}
