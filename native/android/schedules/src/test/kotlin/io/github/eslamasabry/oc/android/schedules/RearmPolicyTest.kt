package io.github.eslamasabry.oc.android.schedules

import androidx.work.ExistingWorkPolicy
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * B-148: after the Flutter → native update the scheduled prompt did not fire on the clock jump. A re-arm (boot, app
 * update, first launch, `TIME_SET`) kept the pending work of an occurrence that was already due, so it waited out its
 * old delay (WorkManager counts elapsed time, not the wall clock). A due occurrence is replaced by work that runs now.
 */
class RearmPolicyTest {
    private val now = 1_730_457_600_000L

    @Test
    fun `B-148 an occurrence already due is replaced so it runs now`() {
        assertEquals(ExistingWorkPolicy.REPLACE, ScheduleWorkPolicy.rearmPolicy(running = false, dueAtMs = now - 60_000, nowMs = now))
        assertEquals(ExistingWorkPolicy.REPLACE, ScheduleWorkPolicy.rearmPolicy(running = false, dueAtMs = now, nowMs = now))
    }

    @Test
    fun `B-148 a future occurrence keeps its armed work`() {
        assertEquals(ExistingWorkPolicy.KEEP, ScheduleWorkPolicy.rearmPolicy(running = false, dueAtMs = now + 60_000, nowMs = now))
    }

    @Test
    fun `B-148 a running occurrence is never cancelled`() {
        assertEquals(ExistingWorkPolicy.APPEND_OR_REPLACE, ScheduleWorkPolicy.rearmPolicy(running = true, dueAtMs = now - 1, nowMs = now))
        assertEquals(ExistingWorkPolicy.APPEND_OR_REPLACE, ScheduleWorkPolicy.rearmPolicy(running = true, dueAtMs = now + 1, nowMs = now))
    }
}
