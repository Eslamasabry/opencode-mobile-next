package io.github.eslamasabry.oc.android.schedules

import android.content.Context
import android.os.Build
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import io.github.eslamasabry.oc.engine.schedules.ArmedEntry
import io.github.eslamasabry.oc.engine.schedules.RunReport
import io.github.eslamasabry.oc.engine.schedules.ScheduleAlarms
import io.github.eslamasabry.oc.engine.schedules.ScheduleEngine
import io.github.eslamasabry.oc.engine.schedules.SchedulePlatform
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** The work request's routing data (only identifiers: never the prompt, a URL or a credential). */
public object ScheduleWorkInput {
    public const val PROFILE: String = "profileId"
    public const val SCHEDULE: String = "scheduleId"
    public const val REVISION: String = "revision"
    public const val DUE: String = "scheduledAtMs"
    public const val MANUAL: String = "manual"

    public fun of(entry: ArmedEntry, manual: Boolean = false): Data =
        workDataOf(
            PROFILE to entry.profileId,
            SCHEDULE to entry.scheduleId,
            REVISION to entry.revision,
            DUE to entry.dueAtMs,
            MANUAL to manual,
        )

    public fun entry(data: Data): ArmedEntry? =
        ArmedEntry.orNull(
            data.getString(PROFILE).orEmpty(),
            data.getString(SCHEDULE).orEmpty(),
            data.getInt(REVISION, -1),
            data.getLong(DUE, -1),
        )
}

/**
 * The bounded worker policy (Flutter `ScheduledPromptPolicy`): WorkManager's own 10-minute window, of which a run
 * may use [RUN_BUDGET_MS]. Nothing here holds a dataSync foreground service (Android 15's 6-hour cap never applies).
 */
public object ScheduleWorkPolicy {
    public const val RUN_BUDGET_MS: Long = 420_000L

    /**
     * The body every scheduled-prompt worker runs. It never asks WorkManager to retry: a transport failure can follow
     * an accepted prompt. A worker killed mid-run is restarted by WorkManager; the durable claim then records
     * "interrupted" and never sends again.
     */
    public suspend fun run(engine: ScheduleEngine, data: Data, nowMs: Long): RunReport? {
        val entry = ScheduleWorkInput.entry(data) ?: return null
        val deadline = nowMs + RUN_BUDGET_MS
        if (data.getBoolean(ScheduleWorkInput.MANUAL, false)) {
            return engine.runManual(entry.profileId, entry.scheduleId, entry.revision, deadline)
        }
        // The run re-arms the schedule when it ends; that must not cancel this very work while it still finishes.
        running.add(entry.workName)
        try {
            return engine.runOccurrence(entry, deadline)
        } finally {
            running.remove(entry.workName)
        }
    }

    /** Unique work names running in this process; [WorkScheduleAlarms.replace] never cancels them. */
    internal val running: MutableSet<String> = ConcurrentHashMap.newKeySet()
}

/**
 * WorkManager as the OS scheduler: one unique work per armed occurrence ([ArmedEntry.workName]), delayed to its due
 * time. `replace` is total per profile: names armed before and absent now are cancelled. The armed names are kept in
 * a small private preference file so replacing never blocks on a WorkManager query.
 */
public class WorkScheduleAlarms(
    context: Context,
    private val worker: Class<out ListenableWorker>,
    private val nowMs: () -> Long,
) : ScheduleAlarms {
    private val app = context.applicationContext
    private val armed = app.getSharedPreferences(ARMED_FILE, Context.MODE_PRIVATE)
    private val work get() = WorkManager.getInstance(app)

    override fun status(): SchedulePlatform =
        SchedulePlatform(supported = true, notificationsAllowed = androidx.core.app.NotificationManagerCompat.from(app).areNotificationsEnabled())

    @Synchronized
    override fun replace(profileId: String, entries: List<ArmedEntry>) {
        val names = entries.map { it.workName }.toSet()
        val before = armed.getStringSet(profileId, emptySet()).orEmpty()
        (before - names).filterNot { it in ScheduleWorkPolicy.running }.forEach { work.cancelUniqueWork(it) }
        for (entry in entries) {
            // A Flutter-era job of the same occurrence names a worker class that no longer exists: drop it.
            work.cancelUniqueWork("oc.schedule.${entry.profileId}.${entry.occurrence}")
            val request =
                OneTimeWorkRequest.Builder(worker)
                    .setInitialDelay((entry.dueAtMs - nowMs()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
                    .setInputData(ScheduleWorkInput.of(entry))
                    .addTag(TAG)
                    .addTag(profileTag(profileId))
                    .build()
            // The occurrence's own worker is still running (the clock moved back before it was due): queue the
            // re-armed attempt after it instead of being swallowed by KEEP.
            val policy = if (entry.workName in ScheduleWorkPolicy.running) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP
            work.enqueueUniqueWork(entry.workName, policy, request)
        }
        armed.edit().putStringSet(profileId, names).apply()
    }

    override fun runNow(profileId: String, scheduleId: String, revision: Int) {
        val entry = ArmedEntry(profileId, scheduleId, revision, nowMs())
        val builder =
            OneTimeWorkRequest.Builder(worker)
                .setInputData(ScheduleWorkInput.of(entry, manual = true))
                .addTag(TAG)
                .addTag(profileTag(profileId))
        // Below API 31 WorkManager emulates expedited work with a foreground service; this bounded worker has none.
        if (Build.VERSION.SDK_INT >= EXPEDITED_MIN_SDK) builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        val request = builder.build()
        // One "Run now" at a time per schedule; a second tap while it runs does nothing (never twice).
        work.enqueueUniqueWork("oc.nschedule.manual.$profileId.$scheduleId", ExistingWorkPolicy.KEEP, request)
    }

    @Synchronized
    override fun cancelProfile(profileId: String) {
        work.cancelAllWorkByTag(profileTag(profileId))
        armed.edit().remove(profileId).apply()
    }

    private fun profileTag(profileId: String) = "$TAG.$profileId"

    private companion object {
        const val TAG = "oc.nschedule"
        const val ARMED_FILE = "oc_schedules_armed"
        const val EXPEDITED_MIN_SDK = 31
    }
}
