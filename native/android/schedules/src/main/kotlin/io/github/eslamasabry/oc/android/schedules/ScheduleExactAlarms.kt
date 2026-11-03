package io.github.eslamasabry.oc.android.schedules

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import io.github.eslamasabry.oc.engine.schedules.ArmedEntry

/**
 * The exact-alarm half of timed runs (plan 01; Flutter `ScheduledPromptScheduler.arm`, Codex schedules #8): where the
 * phone lets the app set exact alarms, each armed occurrence also gets `setExactAndAllowWhileIdle`, so Doze does not
 * hold a run past its time (a skip-missed schedule would otherwise advance without sending). The alarm only starts the
 * same worker at once; the durable claim makes alarm and WorkManager fallback mutually exclusive. Without the
 * permission WorkManager alone remains, as in Flutter.
 */
public class ScheduleExactAlarms(context: Context, private val receiver: Class<out BroadcastReceiver>) {
    private val app = context.applicationContext
    private val alarms: AlarmManager? = app.getSystemService(AlarmManager::class.java)

    public fun allowed(): Boolean = exactAllowed(Build.VERSION.SDK_INT) { alarms?.canScheduleExactAlarms() == true }

    /** Sets the alarm of every future [entries] occurrence (a past one is already due through WorkManager). */
    public fun arm(entries: List<ArmedEntry>, nowMs: Long) {
        val manager = alarms ?: return
        if (!allowed()) return
        for (entry in entries) {
            if (entry.dueAtMs <= nowMs) continue
            try {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, entry.dueAtMs, pending(entry.workName, entry))
            } catch (_: SecurityException) {
                return // the permission went away meanwhile: WorkManager remains the durable fallback
            }
        }
    }

    /** Cancels the alarms of these work names (an alarm is identified by its name, not by its extras). */
    public fun cancel(workNames: Collection<String>) {
        val manager = alarms ?: return
        workNames.forEach { manager.cancel(pending(it, null)) }
    }

    private fun pending(workName: String, entry: ArmedEntry?): PendingIntent {
        val intent =
            Intent(app, receiver).apply {
                action = FIRE
                data = Uri.Builder().scheme(SCHEME).opaquePart(workName).build()
                if (entry != null) {
                    putExtra(EXTRA_PROFILE, entry.profileId)
                    putExtra(EXTRA_SCHEDULE, entry.scheduleId)
                    putExtra(EXTRA_REVISION, entry.revision)
                    putExtra(EXTRA_DUE, entry.dueAtMs)
                }
            }
        return PendingIntent.getBroadcast(app, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    public companion object {
        /** The native alarm action; Flutter's `…SCHEDULED_PROMPT` alarms only re-arm (they predate the migration). */
        public const val FIRE: String = "io.github.eslamasabry.opencode_mobile.NATIVE_SCHEDULED_PROMPT"
        private const val SCHEME = "oc-nschedule"
        private const val EXTRA_PROFILE = "profileId"
        private const val EXTRA_SCHEDULE = "scheduleId"
        private const val EXTRA_REVISION = "revision"
        private const val EXTRA_DUE = "scheduledAtMs"

        /** Below Android 12 exact alarms need no grant; from 12 the person (or the install-over grant) decides. */
        public fun exactAllowed(sdkInt: Int, canSchedule: () -> Boolean): Boolean = sdkInt < Build.VERSION_CODES.S || canSchedule()

        /** The occurrence a native alarm names, or null for anything else (Flutter alarms, system broadcasts). */
        public fun entry(intent: Intent): ArmedEntry? {
            if (intent.action != FIRE) return null
            return ArmedEntry.orNull(
                intent.getStringExtra(EXTRA_PROFILE).orEmpty(),
                intent.getStringExtra(EXTRA_SCHEDULE).orEmpty(),
                intent.getIntExtra(EXTRA_REVISION, -1),
                intent.getLongExtra(EXTRA_DUE, -1),
            )
        }
    }
}
