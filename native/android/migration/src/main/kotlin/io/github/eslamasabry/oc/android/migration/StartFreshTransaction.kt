package io.github.eslamasabry.oc.android.migration

import io.github.eslamasabry.oc.android.migration.readers.migration.FaultPoints
import io.github.eslamasabry.oc.android.migration.readers.migration.Fs
import java.io.File

/**
 * Start fresh (plan 05 §9.4), two phases, nothing deleted:
 *
 * 1. [request] (this process, while the gate is Blocked and has never opened): journal
 *    `pendingSwap = {kind: startFresh, at, phase: requested}`. Nothing moves; the app restarts.
 * 2. [recoverBeforeOpen] (next process, before any `getSharedPreferences`): move the live Flutter prefs file **and its
 *    `.bak` twin**, and the native secret file (`oc_secrets_v1.xml` and twin), into `files/migration/replaced-<at>/`
 *    (journal `phase: moved`), then one atomic journal write: not blocked, `pendingSwap = null`,
 *    `source.kind = startFresh`, v1 complete, notices acknowledged. The gate then opens on an empty app.
 *
 * The copy is the moved files themselves, plus the S0 backup when it exists. The Flutter secure-storage file and every
 * other file stay where they are. A kill at any point resumes from the recorded phase and ends in the same state.
 */
internal class StartFreshTransaction(
    private val paths: MigrationPaths,
    private val now: () -> Long,
    private val faults: FaultPoints = FaultPoints.None,
) {
    private val store = JournalStore(paths.journal)

    fun request(): Boolean {
        val j = (store.load() as? JournalStore.Loaded.Ok)?.journal ?: return false
        if (j.schema > MigrationJournal.SCHEMA || j.firstOpenAt != null || j.blocked == null) return false
        if (j.pendingSwap == null) {
            j.pendingSwap = PendingSwap(PendingSwap.START_FRESH, now(), PendingSwap.REQUESTED)
            store.save(j)
        }
        return true
    }

    /** True when a Start fresh finished now. Call before anything opens a prefs file (StartupFence step 5a). */
    fun recoverBeforeOpen(): Boolean {
        val j = (store.load() as? JournalStore.Loaded.Ok)?.journal ?: return false
        val swap = j.pendingSwap?.takeIf { it.kind == PendingSwap.START_FRESH } ?: return false
        val aside = File(paths.backupRoot, "replaced-${swap.at}")
        if (swap.phase == PendingSwap.REQUESTED) {
            aside.mkdirs()
            for (name in MOVED) {
                val from = File(paths.sharedPrefs, name)
                if (!from.exists()) continue
                faults.reached("fresh:move:$name")
                Fs.move(from, File(aside, name))
            }
            Fs.fsyncDir(paths.sharedPrefs)
            Fs.fsyncDir(aside)
            faults.reached("fresh:moved")
            j.pendingSwap = swap.copy(phase = PendingSwap.MOVED)
            store.save(j)
        }
        faults.reached("fresh:resetting")
        val at = now()
        j.blocked = null
        j.pendingSwap = null
        j.sourceKind = "startFresh"
        j.putSource("startFreshAt", at)
        j.secretRetriesRemaining = 0
        j.deferredFeatures = emptySet()
        j.noticesAcknowledgedAt = at
        j.markV1CompleteAt(at)
        store.save(j)
        return true
    }

    companion object {
        /** The adopted prefs file and the native secret file, each with its `.bak` twin (a `.bak` left behind would load). */
        val MOVED: List<String> = listOf(
            "FlutterSharedPreferences.xml",
            "FlutterSharedPreferences.xml.bak",
            "oc_secrets_v1.xml",
            "oc_secrets_v1.xml.bak",
        )
    }
}
