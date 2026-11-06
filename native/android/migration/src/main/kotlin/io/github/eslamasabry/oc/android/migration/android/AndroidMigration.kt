package io.github.eslamasabry.oc.android.migration.android

import android.content.Context
import android.os.StatFs
import io.github.eslamasabry.oc.android.migration.MigrationPaths
import io.github.eslamasabry.oc.android.migration.MigrationRunner
import io.github.eslamasabry.oc.android.migration.MigrationSecretSink
import io.github.eslamasabry.oc.android.migration.MigrationSignals
import io.github.eslamasabry.oc.android.migration.StartFreshTransaction
import io.github.eslamasabry.oc.android.migration.StepInterrupters
import io.github.eslamasabry.oc.android.migration.sweep.FlutterEraCopies
import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.time.Clock

/**
 * Builds the one runner for the app graph (`:androidApp` binds it behind the gate; plan 05 §7.3). Call [MigrationRunner.run]
 * from the StartupFence thread / IO, never from the main thread (ADR §9.7 hard rule).
 */
public object AndroidMigration {
    /**
     * Start fresh, phase 2 (plan 05 §9.4): finishes a requested Start fresh by moving the old prefs and native secret
     * files aside (kept, never deleted). Call once per process BEFORE anything opens a prefs file or the secret store.
     * Returns true when a Start fresh finished now. Never throws (Codex review p12/migration #4): a failure leaves the
     * request pending and the runner shows it as a failure screen with Try again.
     */
    public fun recoverBeforeOpen(context: Context, clock: Clock): Boolean {
        val dataDir = context.applicationContext.filesDir.parentFile ?: return false
        val fresh = StartFreshTransaction(MigrationPaths(dataDir), now = { clock.now().toEpochMilliseconds() })
        return fresh.recoverSafely() == StartFreshTransaction.Recovery.Finished
    }

    /**
     * The Flutter-era copies a server deletion must sweep (E00-S08): the live secure-storage file, the S0 backup and
     * every Start fresh copy. Listing and editing are blocking file work: call them on the IO dispatcher.
     */
    public fun flutterEraCopies(context: Context): FlutterEraCopies? =
        context.applicationContext.filesDir.parentFile?.let { FlutterEraCopies(MigrationPaths(it)) }

    /**
     * The counts-only files Report a problem compares (E00-S15): the M5 audit and the bridge census, each null when
     * absent or unreadable. Blocking file reads of two small files: call on the IO dispatcher.
     */
    public fun updateCheckFiles(context: Context): Pair<String?, String?> {
        val dataDir = context.applicationContext.filesDir.parentFile ?: return null to null
        val paths = MigrationPaths(dataDir)
        fun text(file: java.io.File): String? =
            try {
                file.takeIf { it.isFile && it.length() <= MAX_CHECK_FILE_BYTES }?.readText(Charsets.UTF_8)
            } catch (@Suppress("SwallowedException") e: java.io.IOException) {
                null
            }
        return text(paths.audit) to text(paths.bridgeCensus)
    }

    private const val MAX_CHECK_FILE_BYTES = 256L * 1024

    public fun create(
        context: Context,
        secrets: MigrationSecretSink,
        redactor: Redactor,
        signals: MigrationSignals,
        nativeVersionCode: Long,
        clock: Clock,
        io: CoroutineDispatcher,
    ): MigrationRunner {
        val app = context.applicationContext
        val paths = MigrationPaths(
            dataDir = requireNotNull(app.filesDir.parentFile),
            externalFilesDir = app.getExternalFilesDir(null),
            externalCacheDir = app.externalCacheDir,
        )
        return MigrationRunner(
            paths = paths,
            prefs = SharedPreferencesFlutterPrefs(app),
            kotlinPrefs = AndroidKotlinPrefs(app),
            unwrapper = AndroidKeystoreUnwrapper(app.packageName),
            sink = secrets,
            redactor = redactor,
            signals = signals,
            interrupter = StepInterrupters.forBuild(paths),
            freeBytes = { StatFs(app.filesDir.path).availableBytes },
            packageName = app.packageName,
            nativeVersionCode = nativeVersionCode,
            now = { clock.now().toEpochMilliseconds() },
            io = io,
        )
    }
}
