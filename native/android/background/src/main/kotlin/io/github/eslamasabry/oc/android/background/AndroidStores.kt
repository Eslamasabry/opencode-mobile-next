package io.github.eslamasabry.oc.android.background

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.data.kv.KeyValueStore
import io.github.eslamasabry.oc.core.data.kv.bool
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.data.lane.WriterLane
import kotlin.time.Clock

/**
 * The app-wide preferences of this module in the adopted Flutter file (`stores.prefs`), under the Flutter keys so a
 * migrated install keeps its choices: `oc.keepLiveInBackground`, `oc.backgroundPauseDismissed`, `oc.firstRun.notifyAsk`.
 * All three are app-scoped (not per profile), so no ProfileScope shape is registered. Writes go through the app lane.
 */
public class KeyValueLiveOptIn(private val prefs: KeyValueStore, private val lane: WriterLane) : LiveOptInStore {
    override fun read(): Boolean? = prefs.bool(LiveOptInStore.KEY)

    override suspend fun write(value: Boolean): Boolean =
        prefs.commit(lane) { putBool(LiveOptInStore.KEY, value) } is Outcome.Ok
}

public class KeyValueDismissedPause(private val prefs: KeyValueStore, private val lane: WriterLane) : DismissedPauseStore {
    override fun read(): String? = prefs.string(DismissedPauseStore.KEY)

    override suspend fun write(id: String): Boolean =
        prefs.commit(lane) { putString(DismissedPauseStore.KEY, id) } is Outcome.Ok
}

public class KeyValueNotifyAsk(private val prefs: KeyValueStore, private val lane: WriterLane) : NotifyAskStore {
    override fun pending(): Boolean = prefs.string(NotifyAskStore.KEY) == NotifyAskStore.PENDING

    override suspend fun answer(): Boolean =
        prefs.commit(lane) { putString(NotifyAskStore.KEY, NotifyAskStore.ANSWERED) } is Outcome.Ok
}

/**
 * The receipt's own file, `shared_prefs/oc_background_pause.xml`, with the Flutter-era keys (`phase`, `reason`, `at`,
 * `taskRemovalHintAt`), so a receipt written by the Flutter app before the update still reads. `commit()`, never
 * `apply()`: the receipt must be on disk before the service reports a start.
 */
public class SharedPreferencesReceiptFile(context: Context) : PauseReceiptFile {
    private val preferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun phase(): String? = readOrNull { preferences.getString(KEY_PHASE, null) }

    override fun reason(): String? = readOrNull { preferences.getString(KEY_REASON, null) }

    override fun at(): Long = readOrNull { preferences.getLong(KEY_AT, 0L) } ?: -1L

    override fun commit(receipt: PauseReceipt): Boolean =
        preferences
            .edit()
            .putString(KEY_PHASE, receipt.phase)
            .putString(KEY_REASON, receipt.reason)
            .putLong(KEY_AT, receipt.at)
            .commit()

    override fun recordTaskRemoval(at: Long) {
        preferences.edit().putLong(KEY_TASK_REMOVAL, at).commit()
    }

    /** A value of the wrong type (a damaged file) reads as unreadable, never as a default. */
    private inline fun <T> readOrNull(read: () -> T): T? =
        try {
            read()
        } catch (_: ClassCastException) {
            null
        }

    public companion object {
        public const val FILE: String = "oc_background_pause"
        internal const val KEY_PHASE = "phase"
        internal const val KEY_REASON = "reason"
        internal const val KEY_AT = "at"
        internal const val KEY_TASK_REMOVAL = "taskRemovalHintAt"
    }
}

/** What Android says about this app now (port of `BackgroundPauseSystemFacts`). Read only. */
public class AndroidPauseFacts(context: Context, private val clock: Clock) : PauseSystemFacts {
    private val app = context.applicationContext

    override fun now(): Long = clock.now().toEpochMilliseconds()

    override fun bootTime(): Long = now() - SystemClock.elapsedRealtime()

    override fun backgroundRestricted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        return try {
            app.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted == true
        } catch (_: RuntimeException) {
            false
        }
    }

    override fun latestExit(): PauseExit? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return try {
            app
                .getSystemService(ActivityManager::class.java)
                ?.getHistoricalProcessExitReasons(app.packageName, 0, EXIT_RECORD_LIMIT)
                ?.filter { it.processName == app.packageName }
                ?.maxByOrNull { it.timestamp }
                ?.let { PauseExit(it.reason, it.timestamp) }
        } catch (_: RuntimeException) {
            null
        }
    }

    private companion object {
        const val EXIT_RECORD_LIMIT = 16
    }
}
