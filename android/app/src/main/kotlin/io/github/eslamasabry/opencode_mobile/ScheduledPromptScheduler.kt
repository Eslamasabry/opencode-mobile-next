package io.github.eslamasabry.opencode_mobile

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

internal class ScheduledPromptScheduler(private val context: Context) {
    private val store = ScheduledPromptStore(context)
    private val alarms = context.getSystemService(AlarmManager::class.java)
    fun exactAllowed() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
    fun replace(profile: String) {
        val previous = store.entries(profile)
        check(store.replace(profile))
        val current = store.entries(profile)
        previous.filter { it !in current }.forEach { cancel(it) }
        current.forEach { arm(it) }
    }
    fun cancelProfile(profile: String) {
        val previous = store.entries(profile)
        store.clear(profile)
        previous.forEach { cancel(it) }
        WorkManager.getInstance(context).cancelAllWorkByTag(tag(profile))
    }
    fun restore(rebaseCalendar: Boolean = false) {
        // A crash can occur between CAS save and index replacement. The
        // envelope is authoritative even when no index has ever been written.
        store.profiles().forEach { profile ->
            if (store.profileExists(profile)) runCatching {
                if (rebaseCalendar) store.rebaseCalendar(profile)
                replace(profile)
            }
        }
        store.all().filter { !store.profileExists(it.profileId) }.forEach { cancel(it) }
    }
    fun arm(entry: ScheduledPromptStore.Entry, replacePending: Boolean = false) {
        enqueue(entry, (entry.due - System.currentTimeMillis()).coerceAtLeast(0), replacePending)
        if (entry.due > System.currentTimeMillis() && exactAllowed()) {
            try { alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, entry.due, pending(entry)) }
            catch (_: SecurityException) { /* WorkManager remains the durable fallback. */ }
        }
    }
    fun enqueue(
        entry: ScheduledPromptStore.Entry,
        delay: Long = 0,
        replacePending: Boolean = false,
        fromAlarm: Boolean = false,
    ) {
        val builder = OneTimeWorkRequestBuilder<ScheduledPromptWorker>()
            .setInputData(Data.Builder().putString("profileId", entry.profileId).putString("scheduleId", entry.id)
                .putLong("revision", entry.revision).putLong("scheduledAtMs", entry.due).build())
            .setInitialDelay(delay, TimeUnit.MILLISECONDS).addTag(tag(entry.profileId))
        if (ScheduledPromptPolicy.expediteAlarm(Build.VERSION.SDK_INT, fromAlarm, delay, entry.id == "__digest")) {
            builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        }
        val request = builder.build()
        // The alarm has a separate immediate work name; the durable claim makes the
        // alarm and fallback mutually exclusive without cancelling a running worker.
        // A zero-delay restore is still fallback work. It must not occupy the
        // alarm name and suppress a later expedited request through KEEP.
        val name = entry.workName + if (fromAlarm) ".alarm" else ".fallback"
        val policy = if (replacePending) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
        WorkManager.getInstance(context).enqueueUniqueWork(name, policy, request)
    }
    private fun cancel(entry: ScheduledPromptStore.Entry) {
        alarms.cancel(pending(entry))
        // Updating the next date from inside an executing worker must not stop it.
        if (!ScheduledPromptWorker.isActive(entry.workName)) {
            WorkManager.getInstance(context).cancelUniqueWork(entry.workName + ".fallback")
            WorkManager.getInstance(context).cancelUniqueWork(entry.workName + ".alarm")
        }
    }
    private fun pending(entry: ScheduledPromptStore.Entry): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, ScheduledPromptReceiver::class.java).apply {
            action = FIRE
            data = Uri.Builder().scheme("oc-schedule").authority(entry.profileId).appendPath(entry.occurrence).build()
            putExtra("profileId", entry.profileId); putExtra("scheduleId", entry.id)
            putExtra("revision", entry.revision); putExtra("scheduledAtMs", entry.due)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    companion object {
        const val FIRE = "io.github.eslamasabry.opencode_mobile.SCHEDULED_PROMPT"
        fun tag(profile: String) = "oc.schedule.$profile"
    }
}

class ScheduledPromptReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                dispatch(context.applicationContext, intent)
            } finally {
                pending.finish()
            }
        }.start()
    }

    private fun dispatch(context: Context, intent: Intent) {
        val scheduler = ScheduledPromptScheduler(context)
        if (intent.action != ScheduledPromptScheduler.FIRE) {
            val clockChanged = intent.action == Intent.ACTION_TIMEZONE_CHANGED ||
                intent.action == Intent.ACTION_TIME_CHANGED
            scheduler.restore(clockChanged)
            return
        }
        val entry = ScheduledPromptStore.Entry.orNull(
            intent.getStringExtra("profileId").orEmpty(),
            intent.getStringExtra("scheduleId").orEmpty(),
            intent.getLongExtra("revision", -1),
            intent.getLongExtra("scheduledAtMs", -1),
        )
        if (entry != null) scheduler.enqueue(entry, fromAlarm = true)
    }
}
