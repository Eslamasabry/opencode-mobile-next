package io.github.eslamasabry.oc.android.migration.ledger

import io.github.eslamasabry.oc.android.migration.FlutterPrefsFile
import io.github.eslamasabry.oc.android.migration.MigrationSecretSink
import io.github.eslamasabry.oc.android.migration.readers.prefs.FlutterPrefsCodec
import io.github.eslamasabry.oc.android.migration.readers.prefs.PrefValue
import io.github.eslamasabry.oc.android.migration.steps.LogicalPrefs
import io.github.eslamasabry.oc.android.migration.steps.PrefsEdit

/**
 * Every NATIVE change or deletion of a secret after the migration goes through here (plan 05 §4.4 rule 1), so the R1
 * Flutter rollback build can never pick up an outdated FSS copy:
 *
 * 1. ledger first (`rev+1`, state, `legacy = null`, `staleQueued`),
 * 2. then the name joins `oc.native.staleLegacySecrets` in the prefs file (only while FSS still holds that name),
 * 3. and only then the secret store changes.
 *
 * A failure at step 1 or 2 leaves the secret store untouched and answers false.
 */
internal class LedgeredSecretWriter(
    private val ledger: CredentialLedger,
    private val prefs: FlutterPrefsFile,
    private val sink: MigrationSecretSink,
    /** Names `FlutterSecureStorage.xml` still holds (entry prefix stripped). */
    private val fssNames: () -> Set<String>,
) {
    suspend fun write(name: String, value: String): Boolean = invalidateThen(name, deleted = false) { sink.write(name, value) }

    suspend fun delete(name: String): Boolean = invalidateThen(name, deleted = true) { sink.delete(name) }

    private suspend fun invalidateThen(name: String, deleted: Boolean, change: suspend () -> Boolean): Boolean {
        val queued = name in fssNames()
        ledger.invalidate(name, deleted, queued)
        if (queued && !queueStale(name)) return false
        return change()
    }

    private fun queueStale(name: String): Boolean {
        val raw = LogicalPrefs(prefs.live()).map[LogicalPrefs.STALE_LEGACY_SECRETS]
        val current = (raw?.let { FlutterPrefsCodec.decode(it).value } as? PrefValue.StrList)?.value.orEmpty()
        if (name in current) return true
        val edit = PrefsEdit().apply { putStringList(LogicalPrefs.STALE_LEGACY_SECRETS, (current + name).sorted()) }
        return prefs.commit(edit.changes)
    }
}
