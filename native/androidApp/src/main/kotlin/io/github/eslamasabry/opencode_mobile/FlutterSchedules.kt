package io.github.eslamasabry.opencode_mobile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.eslamasabry.oc.android.schedules.ScheduleExactAlarms
import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.opencode_mobile.graph.SchedulesGraphAccess
import io.github.eslamasabry.opencode_mobile.graph.appGraph
import kotlinx.coroutines.launch

/**
 * P12-FIX (Codex schedules #6, B-MIGR-013): the Flutter app armed its scheduled prompts as WorkManager work naming this
 * class. After an install-over update that work still fires, perhaps before the app is ever opened. It never sends
 * anything itself (Flutter's input shape is not the native one): it re-arms every saved schedule under native work
 * (`ScheduleEngine.restoreAll`, which also migrates the Flutter fingerprints and cancels the Flutter-era jobs), and the
 * native worker then runs the due occurrence once under the durable claim.
 */
class ScheduledPromptWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = applicationContext.appGraph
        // In the app's scope: re-arming cancels this very (Flutter-era) work, which must not cut the re-arm short.
        graph.appScope.launch { (graph as SchedulesGraphAccess).schedules.restoreAll() }.join()
        AppLog.tagged("Schedules").i { "Flutter-era scheduled work re-armed as native work" }
        return Result.success()
    }
}

/**
 * The schedule receiver under the Flutter class name (frozen: Flutter's pending exact alarms and its manifest entry
 * name it). A native exact alarm starts its occurrence's worker now; every other broadcast — boot, an app update
 * (including the Flutter → native one, before the app is opened), a clock or time-zone change, the exact-alarm grant
 * changing, or a Flutter-era alarm — re-arms every saved schedule (Codex schedules #6/#8; B-MIGR-013, B-STATE-094).
 */
class ScheduledPromptReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val graph = context.applicationContext.appGraph
        val access = graph as SchedulesGraphAccess
        val alarm = ScheduleExactAlarms.entry(intent)
        if (alarm != null) {
            access.scheduleAlarms.enqueueFromAlarm(alarm)
            return
        }
        val pending = goAsync()
        graph.appScope.launch {
            try {
                access.schedules.restoreAll()
            } finally {
                pending.finish()
            }
        }
    }
}
