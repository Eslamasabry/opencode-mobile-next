package io.github.eslamasabry.oc.android.schedules

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.eslamasabry.oc.engine.schedules.ArmedEntry
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlarmTarget : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScheduleExactAlarmsTest {
    private val app = RuntimeEnvironment.getApplication()
    private val manager = app.getSystemService(AlarmManager::class.java)
    private val now = 1_793_606_400_000L
    private val entry = ArmedEntry("p1", "a1", 2, now + 3_600_000L)

    @Test
    fun `B-STATE-094 an armed occurrence gets an exact alarm that wakes through Doze and names its run`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val exact = ScheduleExactAlarms(app, AlarmTarget::class.java)
        exact.arm(listOf(entry, entry.copy(scheduleId = "past", dueAtMs = now - 1)), now)
        val scheduled = shadowOf(manager).scheduledAlarms
        assertEquals(1, scheduled.size) // a past occurrence is already due through WorkManager
        val alarm = scheduled.single()
        assertEquals(AlarmManager.RTC_WAKEUP, alarm.type)
        assertEquals(entry.dueAtMs, alarm.triggerAtMs)
        assertTrue(alarm.isAllowWhileIdle)
        val fired = shadowOf(alarm.operation).savedIntent
        assertEquals(entry, ScheduleExactAlarms.entry(fired))
        exact.cancel(listOf(entry.workName))
        assertTrue(shadowOf(manager).scheduledAlarms.isEmpty())
    }

    @Test
    fun `B-STATE-094 without the exact-alarm grant only WorkManager times the run`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val exact = ScheduleExactAlarms(app, AlarmTarget::class.java)
        assertFalse(exact.allowed())
        exact.arm(listOf(entry), now)
        assertTrue(shadowOf(manager).scheduledAlarms.isEmpty())
        assertTrue(ScheduleExactAlarms.exactAllowed(30) { false }) // below Android 12 no grant is needed
    }

    @Test
    fun `a system broadcast or a Flutter-era alarm names no run (it only re-arms)`() {
        assertNull(ScheduleExactAlarms.entry(Intent(Intent.ACTION_BOOT_COMPLETED)))
        assertNull(ScheduleExactAlarms.entry(Intent("io.github.eslamasabry.opencode_mobile.SCHEDULED_PROMPT")))
    }
}
