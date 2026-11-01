package io.github.eslamasabry.oc.migration.readers.migration

import io.github.eslamasabry.oc.migration.readers.json.MiniJson
import java.io.File

/**
 * R2 "Restore data from before the update" as a resumable transaction (audit 04+05 findings 2, 5, 6).
 *
 * Phases, each recorded in the journal *before* its first filesystem operation:
 * 1. `staged`      — copy the backup's authoritative file for each store into `restore-staging/<name>.xml`
 *                    and verify each against the manifest; choose and record the aside dir;
 * 2. `movingAside` — move every live `<name>.xml` AND `<name>.xml.bak` (Android loads `.bak` first) of the
 *                    restored stores and of the native secret store into `replaced-<ms>/`;
 * 3. `swapping`    — move the staged files into `shared_prefs/`;
 * 4. `resetting`   — reset the migration steps and the barrier, set the outbound hold, clear the restore.
 *
 * [recoverBeforeOpen] must run before ANY `getSharedPreferences` call in the process; it finishes an
 * interrupted restore. Every phase is idempotent, so a crash at any point resumes cleanly.
 *
 * Eligibility: only in storage generation [StorageGeneration.PrefsV1] (before Room is authoritative), only with
 * a backup that verifies, and only while no engine writes (the gate is Blocked, or safe mode).
 */
public class RestoreTransaction(
    private val sharedPrefsDir: File,
    private val migrationRoot: File,
    private val backup: BackupPublisher,
    private val journal: JournalStore,
    private val clockMs: () -> Long,
    private val faults: FaultPoints = FaultPoints.None,
    /** Stores restored from the backup. */
    private val restoredStores: List<String> = BackupPublisher.DEFAULT_FILES,
    /** Native-only stores moved aside but not restored (they did not exist before the update). */
    private val nativeStores: List<String> = listOf(NATIVE_SECRET_STORE),
) {
    public sealed interface Outcome {
        public data object Restored : Outcome
        public data object NothingToResume : Outcome
        public data class Refused(val reason: String) : Outcome
    }

    private val stagingDir: File get() = File(migrationRoot, "restore-staging")

    public fun eligibility(): Outcome.Refused? {
        val j = journal.load()
        if (j.generation != StorageGeneration.PrefsV1) return Outcome.Refused("GenerationNotRestorable")
        if (j.backupState != BackupState.Published) return Outcome.Refused("NoBackup")
        if (backup.verifyPublished() == null) return Outcome.Refused("BackupInvalid")
        return null
    }

    /** Start a restore (from the failure screen or safe mode). */
    public fun start(): Outcome {
        val j = journal.load()
        if (j.restoreState != RestoreState.Idle) return resume()
        eligibility()?.let { return it }
        return resume()
    }

    /** Call first thing in `Application.onCreate` (after the crash handler), before any store is opened. */
    public fun recoverBeforeOpen(): Outcome =
        if (journal.load().restoreState == RestoreState.Idle) Outcome.NothingToResume else resume()

    private fun resume(): Outcome {
        var j = journal.load()
        if (j.restoreState == RestoreState.Idle) {
            stage()
            j = journal.load()
        }
        if (j.restoreState == RestoreState.Staged) {
            journal.update { it.restoreState = RestoreState.MovingAside }
            faults.reached("R2:movingAside")
            j = journal.load()
        }
        if (j.restoreState == RestoreState.MovingAside) {
            moveAside(File(migrationRoot, j.restoreAsideDir!!))
            journal.update { it.restoreState = RestoreState.Swapping }
            faults.reached("R2:swapping")
            j = journal.load()
        }
        if (j.restoreState == RestoreState.Swapping) {
            swapIn()
            journal.update { it.restoreState = RestoreState.Resetting }
            faults.reached("R2:resetting")
        }
        journal.update { r ->
            r.steps.clear()
            // The restored prefs predate the barrier key; the next run writes it again (initBarrier). Until then
            // the R1 guard sees "journal exists, flag missing" and blocks.
            r.flutterReadable = null
            r.outboundHold = true
            r.blocked = null
            r.restoreState = RestoreState.Idle
            r.restoreAsideDir = null
        }
        faults.reached("R2:done")
        return Outcome.Restored
    }

    private fun stage() {
        val files = backup.verifyPublished() ?: throw IllegalStateException("backup no longer verifies")
        Fs.deleteRecursively(stagingDir)
        stagingDir.mkdirs()
        faults.reached("R2:stagingCreated")
        for (f in files.filter { it.authoritative }) {
            val logical = f.name.removeSuffix(".bak").removeSuffix(".xml")
            if (logical !in restoredStores) continue
            val staged = File(stagingDir, "$logical.xml")
            val sha = Fs.copyDurably(File(backup.publishedDir, f.name), staged)
            check(sha == f.sha256) { "staged copy differs from the manifest" }
            faults.reached("R2:staged:$logical")
        }
        Fs.fsyncDir(stagingDir)
        val aside = "replaced-${clockMs()}"
        journal.update {
            it.restoreState = RestoreState.Staged
            it.restoreAsideDir = aside
        }
        faults.reached("R2:staged")
    }

    private fun moveAside(aside: File) {
        aside.mkdirs()
        for (name in restoredStores + nativeStores) {
            for (suffix in listOf(".xml", ".xml.bak")) {
                val live = File(sharedPrefsDir, name + suffix)
                if (!live.exists()) continue
                Fs.move(live, File(aside, name + suffix))
                faults.reached("R2:aside:$name$suffix")
            }
        }
        Fs.fsyncDir(aside)
        Fs.fsyncDir(sharedPrefsDir)
    }

    private fun swapIn() {
        val staged = stagingDir.listFiles()?.sortedBy { it.name }.orEmpty()
        for (f in staged) {
            Fs.move(f, File(sharedPrefsDir, f.name))
            faults.reached("R2:swapped:${f.name}")
        }
        Fs.fsyncDir(sharedPrefsDir)
        Fs.deleteRecursively(stagingDir)
    }

    public companion object {
        /** plan/05 §4.1 `SecretStore` file. */
        public const val NATIVE_SECRET_STORE: String = "oc_secrets_v1"
    }
}

/**
 * Dispatch identities and schedule-occurrence tombstones, kept in `files/native/outbound-tombstones.json`,
 * OUTSIDE every restorable snapshot (audit finding 2). An R2 restore brings back old queue and schedule state;
 * this file is what tells the queue and the scheduler that an entry was already sent or an occurrence already
 * fired, so neither is repeated. Ids only (`queue:<entryId>`, `occ:<profileId>:<scheduleId>:<occurrence>`).
 */
public class OutboundTombstones(private val file: File, private val faults: FaultPoints = FaultPoints.None) {
    public fun load(): Set<String> {
        if (!file.exists()) return emptySet()
        val list = (MiniJson.parseOrNull(file.readText()) as? Map<*, *>)?.get("ids") as? List<*> ?: return emptySet()
        return list.filterIsInstance<String>().toSet()
    }

    /** Record before reporting success to the caller (send succeeded / occurrence claimed). */
    public fun record(id: String) {
        val ids = load().toSortedSet()
        if (!ids.add(id)) return
        Fs.writeAtomically(file, MiniJson.encode(linkedMapOf("v" to 1L, "ids" to ids.toList())).toByteArray(Charsets.UTF_8), faults, "tombstones")
    }

    /** Split restored outbound ids into (already done → never resend, still pending → hold for review). */
    public fun partition(restoredIds: Collection<String>): Pair<List<String>, List<String>> {
        val done = load()
        return restoredIds.partition { it in done }
    }
}
