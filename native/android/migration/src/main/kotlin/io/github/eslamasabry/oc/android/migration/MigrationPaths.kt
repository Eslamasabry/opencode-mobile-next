package io.github.eslamasabry.oc.android.migration

import java.io.File

/**
 * Every path the runner reads or writes, under the app's data directory (`/data/data/<package>/`, plan 05 §1 and
 * Appendix C). Nothing outside `files/native/`, `files/migration/` and the adopted prefs file is ever written.
 */
public class MigrationPaths(
    /** `context.filesDir.parentFile`: holds `shared_prefs/`, `files/`, `cache/`, … */
    public val dataDir: File,
    /** `getExternalFilesDir(null)`: inventoried only (FactsBook's `factsbook/` lives there). */
    public val externalFilesDir: File? = null,
    /** `externalCacheDir`: inventoried only (project exports). */
    public val externalCacheDir: File? = null,
) {
    public val sharedPrefs: File get() = File(dataDir, "shared_prefs")
    public val files: File get() = File(dataDir, "files")
    public val nativeDir: File get() = File(files, "native")
    public val journal: File get() = File(nativeDir, "migration.json")
    public val audit: File get() = File(nativeDir, "migration-audit.json")
    public val baseline: File get() = File(nativeDir, "m1-baseline.bin")
    public val inventory: File get() = File(nativeDir, "m1-inventory.json")
    public val credentialLedger: File get() = File(nativeDir, "credential-ledger.json")
    public val fssSnapshot: File get() = File(nativeDir, "fss-snapshot.json")
    public val backupRoot: File get() = File(files, "migration")
    public val backup: File get() = File(backupRoot, BACKUP_NAME)

    /** `qa` build type only (plan 05 §7.4 S0 step 3, §10.5 J2): read by `QaStepInterrupter`, never by release code. */
    public val qaKillAfter: File get() = File(nativeDir, "qa-kill-after")
    public val qaDelayMs: File get() = File(nativeDir, "qa-delay-ms")
    public val qaFreeBytes: File get() = File(nativeDir, "qa-free-bytes")

    public companion object {
        /** Fixed by ADR §9.7 even when the source is a local 4xxx build (plan 05 §7.4 S0). */
        public const val BACKUP_NAME: String = "flutter-v57"
    }
}
