package io.github.eslamasabry.oc.android.migration

import io.github.eslamasabry.oc.android.migration.readers.xml.RawPref

/**
 * The adopted Flutter prefs file (`shared_prefs/FlutterSharedPreferences.xml`), PHYSICAL keys (`flutter.<key>`), raw
 * values. Android: [io.github.eslamasabry.oc.android.migration.android.SharedPreferencesFlutterPrefs]; JVM tests: a
 * file-backed fake. Blocking; called on the runner's IO dispatcher only, and only after S0 (StartupFence rule, plan 05
 * §7.1: the first `getSharedPreferences` of a backed-up file happens after the byte backup).
 *
 * TODO(core:data): once `:core:data` compiles again, route [commit] through `PrefsKeyValueStore.migrationWriter()` so
 * the migrator shares the store's one file lock (plan 05 §2.2, audit 04+05 #8). See the S2-MIGRRUN report.
 */
internal interface FlutterPrefsFile {
    /** The map Android loaded, physical keys. */
    fun live(): Map<String, RawPref>

    /** One atomic `Editor.commit()`; a null value removes the key. True only when the platform acknowledged it. */
    fun commit(changes: Map<String, RawPref?>): Boolean
}

/** Read-only view of a Kotlin-owned prefs file (plan 05 Appendix D). M4 reads `builtin_server_recovery` through it. */
internal fun interface KotlinPrefsSource {
    /** The file's `.all`, or null when it does not exist. */
    fun read(fileName: String): Map<String, Any?>?
}

/**
 * The migrator's own, UNGATED handle on the native secret store (plan 05 §4.2: "binds the raw `KeystoreSecretStore`
 * only into `:android:migration`"). `:androidApp` adapts `:core:data`'s `SecretStore` to it. Values never reach logs.
 */
public interface MigrationSecretSink {
    /** The stored value, null when there is none. Throws only when the store itself is unavailable (Keystore error). */
    public suspend fun read(name: String): String?

    /** Writes, reads back and compares (`SecretStore.write` contract). False when it did not verify. */
    public suspend fun write(name: String, value: String): Boolean

    /** Removes the entry. False when the platform refused. */
    public suspend fun delete(name: String): Boolean
}

/**
 * The runner's outward events (plan 16 §5.3, plan 05 M5). `:android:migration` may not depend on engines, so
 * `:androidApp` implements this and hands the events to `:engine:analytics` / `:engine:diagnostics` once the gate is
 * Open. Enums and buckets only: no value, name, id or path (16 K-SEC-11).
 */
public interface MigrationSignals {
    /** First launch after the versionCode changed. [fromCode] is null when the previous build was Flutter (its code is
     *  not readable after `adb install -r`, plan 05 §7.4 S0). */
    public fun appUpdated(fromCode: Long?)

    /** Once, when `native.migration.v1` completes or the runner gives up (blocked). */
    public fun migrationDone(event: MigrationDoneEvent)

    /** The one diagnostics line of M5 (B-MIGR-011): counts only. */
    public fun diagnosticsLine(text: String)

    public companion object {
        public val None: MigrationSignals = object : MigrationSignals {
            override fun appUpdated(fromCode: Long?) = Unit
            override fun migrationDone(event: MigrationDoneEvent) = Unit
            override fun diagnosticsLine(text: String) = Unit
        }
    }
}

public enum class MigrationOutcome(public val wire: String) { Done("done"), ReentryNeeded("reentry_needed"), Failed("failed") }

/** `failed_step` of `migration_done`: None / S0 / M1…M6. */
public enum class MigrationStepId(public val wire: String) {
    None("none"), S0("s0"), M1("m1"), M2("m2"), M3("m3"), M4("m4"), M5("m5"), M6("m6");

    internal companion object {
        fun of(step: String?): MigrationStepId = entries.firstOrNull { it.name == step?.substringBefore(':') } ?: None
    }
}

public enum class CountBucket(public val wire: String) {
    Zero("0"), One("1"), Few("2-5"), Several("6-20"), Many("21+");

    public companion object {
        public fun of(n: Int): CountBucket = when {
            n <= 0 -> Zero
            n == 1 -> One
            n <= 5 -> Few
            n <= 20 -> Several
            else -> Many
        }
    }
}

public enum class DurationBucket(public val wire: String) {
    UnderOneSecond("lt_1s"), UnderFiveSeconds("lt_5s"), UnderThirtySeconds("lt_30s"), UnderFiveMinutes("lt_5m"), Longer("ge_5m");

    public companion object {
        public fun of(ms: Long): DurationBucket = when {
            ms < 1_000 -> UnderOneSecond
            ms < 5_000 -> UnderFiveSeconds
            ms < 30_000 -> UnderThirtySeconds
            ms < 300_000 -> UnderFiveMinutes
            else -> Longer
        }
    }
}

public data class MigrationDoneEvent(
    val outcome: MigrationOutcome,
    val failedStep: MigrationStepId,
    val secretsReentry: CountBucket,
    val duration: DurationBucket,
)
