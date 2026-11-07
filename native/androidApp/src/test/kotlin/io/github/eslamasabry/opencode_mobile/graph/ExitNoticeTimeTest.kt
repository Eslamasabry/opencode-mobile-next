package io.github.eslamasabry.opencode_mobile.graph

import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** P-015: the app-exit notice says time ago for today and a date only for an earlier day, never "11/7/26 12:59 PM". */
class ExitNoticeTimeTest {
    private val zone = ZoneId.of("Africa/Cairo")
    // 2026-11-07 13:11 in Cairo (UTC+2).
    private val now = Instant.parse("2026-11-07T11:11:00Z")

    @Test
    fun `P-015 an exit twelve minutes ago reads as minutes ago`() {
        assertEquals(ExitNoticeTime.MinutesAgo(12), exitNoticeTime(now - 12.minutes, now, zone))
    }

    @Test
    fun `P-015 an exit seconds ago reads as one minute ago, never zero`() {
        assertEquals(ExitNoticeTime.MinutesAgo(1), exitNoticeTime(now - 20.seconds, now, zone))
        // A clock that moved back: still today, still at least a minute.
        assertEquals(ExitNoticeTime.MinutesAgo(1), exitNoticeTime(now + 3.minutes, now, zone))
    }

    @Test
    fun `P-015 an exit hours ago today reads as hours ago`() {
        assertEquals(ExitNoticeTime.HoursAgo(3), exitNoticeTime(now - 3.hours - 20.minutes, now, zone))
    }

    @Test
    fun `P-015 an exit on an earlier day reads as that day's date only`() {
        // 23:30 the evening before, in the phone's zone: yesterday, though less than 14 hours ago.
        val lastNight = Instant.parse("2026-11-06T21:30:00Z")
        assertEquals(ExitNoticeTime.OnDay(LocalDate.of(2026, 11, 6)), exitNoticeTime(lastNight, now, zone))
    }

    @Test
    fun `P-015 today is the phone's day, not UTC's`() {
        // 00:30 on 7 Nov in Cairo is still 6 Nov in UTC; it is today here.
        val earlyToday = Instant.parse("2026-11-06T22:30:00Z")
        assertEquals(ExitNoticeTime.HoursAgo(12), exitNoticeTime(earlyToday, now, zone))
    }
}
