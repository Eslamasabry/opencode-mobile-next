package io.github.eslamasabry.opencode_mobile

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/** Synchronous private receipts survive stopping without a Flutter engine. */
class BackgroundPauseStore(context: Context) {
    private val app = context.applicationContext
    private val preferences = app.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun confirmedStarted(): Boolean = synchronized(lock) {
        write(BackgroundPauseReceipt("running", at = System.currentTimeMillis())) && setEnabled(true)
    }

    fun timeout(): Boolean = synchronized(lock) {
        val saved = write(
            BackgroundPauseReceipt("paused", BackgroundPausePolicy.TIME_LIMIT, System.currentTimeMillis()),
        )
        // Always turn off restore, even if storing the richer receipt failed.
        val disabled = setEnabled(false)
        saved && disabled
    }

    fun clear(): Boolean = synchronized(lock) {
        val cleared = write(BackgroundPauseReceipt())
        val disabled = setEnabled(false)
        cleared && disabled
    }

    fun recordTaskRemoved() {
        // A hint only. Never replace a running receipt or classify a swipe.
        preferences.edit().putLong(KEY_TASK_REMOVAL, System.currentTimeMillis()).commit()
    }

    fun status(active: Boolean): Map<String, Any?> = synchronized(lock) {
        val previous = read()
        val restricted = backgroundRestricted()
        val exit = if (!active && previous.phase == "running") latestExit() else null
        val receipt = BackgroundPausePolicy.reconcile(
            previous, active, restricted, exit, System.currentTimeMillis(),
        )
        if (receipt != previous) check(write(receipt)) { STORAGE_ERROR }
        val paused = !active && receipt.phase == "paused"
        if (paused) check(setEnabled(false)) { STORAGE_ERROR }
        mapOf(
            "supported" to true,
            "active" to active,
            "paused" to paused,
            "reason" to if (paused) receipt.reason else "none",
            "at" to if (paused) receipt.at else null,
            "canResume" to paused,
        )
    }

    private fun read(): BackgroundPauseReceipt {
        val phase = preferences.getString(KEY_PHASE, "none") ?: "none"
        val reason = preferences.getString(KEY_REASON, "none") ?: "none"
        val at = preferences.getLong(KEY_AT, 0L)
        check(phase in setOf("none", "running", "paused")) { STORAGE_ERROR }
        val reasons = setOf("none", "timeLimit", "batteryRestricted", "userStopped", "interrupted")
        check(reason in reasons) { STORAGE_ERROR }
        check(phase == "none" || at > 0L) { STORAGE_ERROR }
        check(phase != "paused" || reason != "none") { STORAGE_ERROR }
        return BackgroundPauseReceipt(phase, reason, at)
    }

    private fun write(receipt: BackgroundPauseReceipt): Boolean = preferences.edit()
        .putString(KEY_PHASE, receipt.phase)
        .putString(KEY_REASON, receipt.reason)
        .putLong(KEY_AT, receipt.at)
        .commit()

    private fun setEnabled(value: Boolean): Boolean = app
        .getSharedPreferences(LivePauseReceiver.FLUTTER_PREFERENCES, Context.MODE_PRIVATE)
        .edit().putBoolean(LivePauseReceiver.FLUTTER_PREFERENCE_KEEP_LIVE, value).commit()

    private fun backgroundRestricted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        return runCatching {
            app.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted == true
        }.getOrDefault(false)
    }

    private fun latestExit(): BackgroundPauseExit? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return runCatching {
            app.getSystemService(ActivityManager::class.java)
                ?.getHistoricalProcessExitReasons(app.packageName, 0, EXIT_RECORD_LIMIT)
                ?.filter { it.processName == app.packageName }
                ?.maxByOrNull { it.timestamp }
                ?.let { BackgroundPauseExit(it.reason, it.timestamp) }
        }.getOrNull()
    }

    private companion object {
        val lock = Any()
        const val PREFERENCES = "oc_background_pause"
        const val KEY_PHASE = "phase"
        const val KEY_REASON = "reason"
        const val KEY_AT = "at"
        const val KEY_TASK_REMOVAL = "taskRemovalHintAt"
        const val EXIT_RECORD_LIMIT = 16
        const val STORAGE_ERROR = "Background state could not be saved or read."
    }
}
