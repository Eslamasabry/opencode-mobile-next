package io.github.eslamasabry.oc.engine.schedules

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScheduleModelTest {
    private val utc = TimeZone.UTC

    @Test
    fun `B-STATE-092 a schedule round-trips its private target and explicit agent and model`() {
        val value =
            prompt().copy(sessionId = "ses_1", workspace = "wrk", agent = "build", modelProviderId = "anthropic", modelId = "claude")
        val back = ScheduleCodec.decodeSchedule(ScheduleCodec.schedule(value))
        assertEquals(value, back)
    }

    @Test
    fun `B-STATE-092 invalid recurrence and malformed stored schedules are rejected`() {
        assertFalse(Recurrence.daily(24, 0).valid)
        assertFalse(Recurrence(RecurrenceKind.Once).valid)
        assertFalse(Recurrence.interval(0, T0).valid)
        assertFalse(prompt().copy(modelProviderId = "a").valid) // provider without model
        assertFalse(prompt().copy(prompt = "  ").valid)
        val json = ScheduleCodec.schedule(prompt()).toMutableMap().apply { put("revision", "1") }
        assertFailsWith<ScheduleFormatException> { ScheduleCodec.decodeSchedule(json) }
        val unknownKind = ScheduleCodec.schedule(prompt()).toMutableMap().apply { put("missedPolicy", "later") }
        assertFailsWith<ScheduleFormatException> { ScheduleCodec.decodeSchedule(unknownKind) }
    }

    @Test
    fun `B-STATE-092 occurrence identity includes revision and instant`() {
        assertEquals("a1:3:1793610000000", ScheduleIds.occurrence(prompt(revision = 3), T0 + HOUR))
        assertTrue(ScheduleIds.occurrence(prompt(revision = 1), T0) != ScheduleIds.occurrence(prompt(revision = 2), T0))
    }

    @Test
    fun `B-MIGR-007 the Flutter JSON form is read and written byte for byte`() {
        val flutter =
            """{"schemaVersion":1,"schedules":[{"id":"a1","profileId":"p1","profileFingerprint":"${"ab".repeat(32)}",""" +
                """"prompt":"Check the nightly build","projectPath":"/work/app","workspace":null,"sessionId":null,"agent":null,""" +
                """"modelProviderId":null,"modelId":null,"recurrence":{"kind":"daily","at":null,"hour":9,"minute":0,"hours":1},""" +
                """"missedPolicy":"catchUp","enabled":true,"nextRunAt":"2026-11-02T09:00:00.000Z","createdAt":"2026-11-02T08:00:00.000Z",""" +
                """"revision":1}],"runs":[{"id":"a1:1:1793610000000","scheduleId":"a1","scheduledAt":"2026-11-02T09:00:00.000Z",""" +
                """"startedAt":"2026-11-02T09:00:01.000Z","status":"ran","late":false,"finishedAt":"2026-11-02T09:00:05.000Z",""" +
                """"sessionId":"ses_1","errorCode":null}],"digest":{"enabled":false,"hour":8,"minute":0,"lastDeliveredAt":null}}"""
        val envelope = ScheduleCodec.decodeEnvelope(flutter, P, 100, 500, 1 shl 22)
        assertEquals(T0 + HOUR, envelope.schedules.single().nextRunAtMs)
        assertEquals(RunStatus.Ran, envelope.runs.single().status)
        assertEquals(flutter, ScheduleCodec.encodeEnvelope(envelope))
    }

    @Test
    fun `B-STATE-095 local timestamps survive serialization`() {
        assertEquals("2026-11-02T08:00:00.000Z", ScheduleCodec.isoUtc(T0))
        assertEquals(T0, ScheduleCodec.parseIso("2026-11-02T08:00:00.000Z"))
        assertEquals(T0 + 123, ScheduleCodec.parseIso("2026-11-02T08:00:00.123456Z")) // Dart microseconds
        assertEquals(T0, ScheduleCodec.parseIso("2026-11-02T10:00:00.000+02:00"))
    }

    @Test
    fun `B-STATE-093 once has no future occurrence after its instant`() {
        val once = Recurrence.once(T0 + HOUR)
        assertEquals(T0 + HOUR, Recurrences.next(once, T0, utc))
        assertNull(Recurrences.next(once, T0 + HOUR, utc))
    }

    @Test
    fun `B-STATE-093 intervals stay anchored after days missed`() {
        val every6 = Recurrence.interval(6, T0)
        assertEquals(T0 + 6 * HOUR, Recurrences.next(every6, T0, utc))
        // Three days late: still on the anchor's 6-hour grid, not drifted to "now + 6h".
        assertEquals(T0 + 78 * HOUR, Recurrences.next(every6, T0 + 72 * HOUR + 5 * MIN, utc))
    }

    @Test
    fun `B-STATE-093 weekdays skip the weekend and the current exact occurrence`() {
        val friday9 = T0 + 4 * 24 * HOUR + HOUR // Friday 09:00
        val weekdays = Recurrence.weekdays(9, 0)
        assertEquals(friday9 + 3 * 24 * HOUR, Recurrences.next(weekdays, friday9, utc)) // Monday, not Saturday
        assertEquals(T0 + HOUR, Recurrences.next(weekdays, T0, utc))
    }

    @Test
    fun `B-STATE-093 every day includes weekends`() {
        val friday9 = T0 + 4 * 24 * HOUR + HOUR
        assertEquals(friday9 + 24 * HOUR, Recurrences.next(Recurrence.daily(9, 0), friday9, utc))
    }

    @Test
    fun `B-STATE-093 daily keeps the wall-clock time across a daylight-saving change`() {
        val berlin = TimeZone.of("Europe/Berlin")
        // 2026-10-24 09:00 CEST (UTC+2) then 2026-10-25 09:00 CET (UTC+1): 25 hours apart.
        val saturday = ScheduleCodec.parseIso("2026-10-24T07:00:00.000Z")
        assertEquals(ScheduleCodec.parseIso("2026-10-25T08:00:00.000Z"), Recurrences.next(Recurrence.daily(9, 0), saturday, berlin))
    }

    @Test
    fun `B-STATE-094 catch-up collapses missed runs and marks late only beyond grace`() {
        val schedule = prompt(next = T0)
        val within = schedule.due(T0 + 10 * MIN, utc)!!
        assertFalse(within.late)
        assertTrue(within.shouldRun)
        val days = schedule.due(T0 + 50 * HOUR, utc)!!
        assertTrue(days.late)
        assertTrue(days.shouldRun) // one catch-up run, not fifty
        assertEquals(T0, days.scheduledAtMs)
        assertTrue(days.nextRunAtMs!! > T0 + 50 * HOUR)
    }

    @Test
    fun `B-STATE-094 skip-missed keeps on-time and within-grace runs`() {
        val schedule = prompt(next = T0).copy(missedPolicy = MissedRunPolicy.Skip)
        assertTrue(schedule.due(T0, utc)!!.shouldRun)
        assertTrue(schedule.due(T0 + 15 * MIN, utc)!!.shouldRun)
        assertFalse(schedule.due(T0 + 16 * MIN, utc)!!.shouldRun)
        assertNull(schedule.due(T0 - 1, utc))
    }

    @Test
    fun `B-STATE-094 a run error accepts a fixed code only and keeps the late marker`() {
        val run = ScheduledRun("a1:1:1", "a1", T0, T0, RunStatus.Failed, late = true, errorCode = "timeout")
        assertTrue(run.valid)
        assertEquals(run, ScheduleCodec.decodeRun(ScheduleCodec.run(run)))
        assertFalse(run.copy(errorCode = "Connection refused by the host").valid)
    }
}
