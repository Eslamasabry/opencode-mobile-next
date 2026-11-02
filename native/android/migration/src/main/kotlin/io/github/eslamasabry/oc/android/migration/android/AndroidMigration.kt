package io.github.eslamasabry.oc.android.migration.android

import android.content.Context
import android.os.StatFs
import io.github.eslamasabry.oc.android.migration.MigrationPaths
import io.github.eslamasabry.oc.android.migration.MigrationRunner
import io.github.eslamasabry.oc.android.migration.MigrationSecretSink
import io.github.eslamasabry.oc.android.migration.MigrationSignals
import io.github.eslamasabry.oc.android.migration.StartFreshTransaction
import io.github.eslamasabry.oc.android.migration.StepInterrupters
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
     * Returns true when a Start fresh finished now.
     */
    public fun recoverBeforeOpen(context: Context, clock: Clock): Boolean {
        val dataDir = context.applicationContext.filesDir.parentFile ?: return false
        return StartFreshTransaction(MigrationPaths(dataDir)) { clock.now().toEpochMilliseconds() }.recoverBeforeOpen()
    }

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
