package io.github.eslamasabry.oc.android.migration

import io.github.eslamasabry.oc.android.migration.ledger.CredentialLedger
import io.github.eslamasabry.oc.android.migration.ledger.LedgeredSecretWriter
import io.github.eslamasabry.oc.android.migration.readers.json.MiniJson
import io.github.eslamasabry.oc.android.migration.readers.migration.Fs
import io.github.eslamasabry.oc.android.migration.readers.secure.FssFormat
import io.github.eslamasabry.oc.android.migration.readers.secure.FssKeyUnwrapper
import io.github.eslamasabry.oc.android.migration.readers.xml.RawPref
import io.github.eslamasabry.oc.android.migration.readers.xml.SharedPrefsFile
import io.github.eslamasabry.oc.android.migration.steps.BackupFile
import io.github.eslamasabry.oc.android.migration.steps.LogicalPrefs
import io.github.eslamasabry.oc.android.migration.steps.M1Census
import io.github.eslamasabry.oc.android.migration.steps.M2Secrets
import io.github.eslamasabry.oc.android.migration.steps.M3LegacyTransforms
import io.github.eslamasabry.oc.android.migration.steps.M4RestoreAuthority
import io.github.eslamasabry.oc.android.migration.steps.M5Audit
import io.github.eslamasabry.oc.android.migration.steps.PrefsBaseline
import io.github.eslamasabry.oc.android.migration.steps.PrefsEdit
import io.github.eslamasabry.oc.android.migration.steps.S0Backup
import io.github.eslamasabry.oc.android.migration.steps.S0Backup.Companion.toMap
import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Native changes of a secret after the migration: ledger first, then the R1 stale marker, then the store (05 §4.4). */
public interface NativeSecretChanges {
    public suspend fun write(name: String, value: String): Boolean

    public suspend fun delete(name: String): Boolean
}

/**
 * `native.migration.v1` (plan 05 §7): S0 byte backup → M1 census → barrier → M2 secrets → M3 prefs adoption → M4
 * restore-authority check → M5 audit → M6 complete, then the gate opens.
 *
 * Every step follows plan 05 §7.11: journal `attempts+1, running` BEFORE the step's first write (a crash counts), the
 * idempotent body, then `done` with the step's counts. Three attempts without `done` → `failed` →
 * `Blocked(StepCrashed)` instead of a crash loop. Expected failures (`LowSpace`, `PrefsUnreadable`, `WriteRefused`,
 * `AuthorityChanged`, `BackupInvalid`) block at once and keep everything: nothing is deleted, the source files are
 * only read, and the S0 backup is never overwritten.
 *
 * [run] is single-flight; [retry] while a run is in flight waits on that same run (never a second runner).
 */
public class MigrationRunner internal constructor(
    private val paths: MigrationPaths,
    private val prefs: FlutterPrefsFile,
    private val kotlinPrefs: KotlinPrefsSource,
    private val unwrapper: FssKeyUnwrapper,
    private val sink: MigrationSecretSink,
    private val redactor: Redactor,
    private val signals: MigrationSignals,
    private val interrupter: StepInterrupter,
    private val freeBytes: () -> Long,
    private val packageName: String,
    private val nativeVersionCode: Long,
    private val now: () -> Long,
    private val io: CoroutineDispatcher,
) {
    private val mutableState = MutableStateFlow<RunnerState>(RunnerState.Closed)
    public val state: StateFlow<RunnerState> = mutableState

    private val store = JournalStore(paths.journal)
    private val ledger = CredentialLedger(paths.credentialLedger)
    private val flight = Mutex()
    private var inFlight: CompletableDeferred<RunnerState>? = null

    /** Runs (or resumes) the migration, or opens at once when it already completed. Single-flight. */
    public suspend fun run(): RunnerState {
        val (deferred, owner) = flight.withLock {
            inFlight?.let { it to false } ?: CompletableDeferred<RunnerState>().also { inFlight = it }.let { it to true }
        }
        if (!owner) return deferred.await()
        try {
            val result = withContext(io) { execute() }
            deferred.complete(result)
            return result
        } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
            deferred.completeExceptionally(t)
            throw t
        } finally {
            withContext(NonCancellable) { flight.withLock { inFlight = null } }
        }
    }

    /**
     * "Try moving your data again" (plan 05 §8.2). While a run is in flight it waits on that run. From `Blocked` it
     * clears the block, resets the attempts of the steps that did not finish, and runs again.
     */
    public suspend fun retry(): RunnerState {
        flight.withLock { inFlight }?.let { return it.await() }
        withContext(io) {
            val loaded = store.load() as? JournalStore.Loaded.Ok ?: return@withContext
            val j = loaded.journal
            if (j.blocked != null) {
                j.blocked = null
                j.resetUnfinishedSteps()
                store.save(j)
            }
        }
        return run()
    }

    /** The one-time sheet and the deferred-feature notice were shown: never again (plan 05 §8.2, 02 §3.7 item 4). */
    public suspend fun acknowledgeNotices() {
        withContext(io) {
            val j = (store.load() as? JournalStore.Loaded.Ok)?.journal ?: return@withContext
            if (j.noticesAcknowledgedAt == null) {
                j.noticesAcknowledgedAt = now()
                store.save(j)
            }
        }
        (mutableState.value as? RunnerState.Open)?.let { mutableState.value = it.copy(notices = MigrationNoticeSet()) }
    }

    /**
     * Start fresh, phase 1 (plan 05 §9.4): journals the request and moves nothing in this process, because it holds a
     * loaded `SharedPreferencesImpl` that would write the old map back. The caller then restarts the app; phase 2 runs
     * in [StartFreshTransaction.recoverBeforeOpen] before anything opens the prefs file. False when not allowed now.
     */
    public suspend fun requestStartFresh(): Boolean = withContext(io) { StartFreshTransaction(paths, now).request() }

    /** The writer engines use for every native secret change after the gate opens (plan 05 §4.4 rule 1). */
    public fun secretChanges(): NativeSecretChanges {
        val writer = LedgeredSecretWriter(ledger, prefs, sink) { fssNames() }
        return object : NativeSecretChanges {
            override suspend fun write(name: String, value: String): Boolean = withContext(io) { writer.write(name, value) }
            override suspend fun delete(name: String): Boolean = withContext(io) { writer.delete(name) }
        }
    }

    // ------------------------------------------------------------------------------------------------------------

    private sealed interface StepOutcome {
        data class Ok(val result: Map<String, Any?>) : StepOutcome
        data class Block(val reason: BlockReason, val neededBytes: Long? = null) : StepOutcome
    }

    private suspend fun execute(): RunnerState {
        val j = when (val loaded = store.load()) {
            JournalStore.Loaded.Unreadable -> return publish(blockedState(null, BlockReason.StepCrashed, "journal", null))
            is JournalStore.Loaded.Ok -> loaded.journal
        }
        if (j.schema > MigrationJournal.SCHEMA) {
            // Only reachable through a downgrade, which Android refuses: honour what is recorded, write nothing.
            return publish(if (j.v1Complete) open(j, write = false) else blockedState(j, BlockReason.StepCrashed, "journal", null))
        }
        j.blocked?.let { b ->
            val reason = BlockReason.entries.firstOrNull { it.name == b.reason } ?: BlockReason.StepCrashed
            return publish(blockedState(j, reason, b.step, b.neededBytes))
        }
        if (!j.v1Complete) {
            val blocked = runV1(j)
            if (blocked != null) return publish(blocked)
        }
        return publish(open(j, write = true))
    }

    /** Null when v1 completed; else the Blocked state (already journaled). */
    private suspend fun runV1(j: MigrationJournal): RunnerState.Blocked? {
        if (j.foundAt == null) j.putSource("foundAt", now())
        val s0 = S0Backup(paths, interrupter, freeBytes, nativeVersionCode, now)
        step(j, "S0") { runS0(j, s0) }?.let { return it }
        if (j.sourceKind != "flutter") {
            // Fresh install, or a device restore with no data: a clean first run, never a failure.
            j.markV1CompleteAt(now())
            store.save(j)
            return null
        }
        val backupFiles = backupFiles(j)
        step(j, "M1") { runM1(j, backupFiles) }?.let { return it }
        initBarrier(j)?.let { return it }
        step(j, "M2") { runM2(j) }?.let { return it }
        step(j, "M3") {
            when (val r = M3LegacyTransforms(paths, prefs).run()) {
                is M3LegacyTransforms.Result.Done -> StepOutcome.Ok(r.counts)
                is M3LegacyTransforms.Result.WriteRefused -> StepOutcome.Block(BlockReason.WriteRefused)
            }
        }?.let { return it }
        step(j, "M4") {
            when (val r = M4RestoreAuthority(kotlinPrefs, nativeVersionCode).run(prefs.live(), PrefsBaseline.load(paths.baseline))) {
                is M4RestoreAuthority.Result.Done -> StepOutcome.Ok(r.counts)
                M4RestoreAuthority.Result.AuthorityChanged -> StepOutcome.Block(BlockReason.AuthorityChanged)
            }
        }?.let { return it }
        step(j, "M5") {
            val r = M5Audit(paths).run(
                prefs.live(), PrefsBaseline.load(paths.baseline),
                j.stepResult("M1"), j.stepResult("M2"), j.stepResult("M3"), j.stepResult("M4"),
            )
            when (r) {
                is M5Audit.Result.Done -> StepOutcome.Ok(linkedMapOf("line" to r.diagnosticsLine))
                M5Audit.Result.AuthorityChanged -> StepOutcome.Block(BlockReason.AuthorityChanged)
            }
        }?.let { return it }
        step(j, "M6") { runM6(j) }?.let { return it }
        return null
    }

    private suspend fun runS0(j: MigrationJournal, s0: S0Backup): StepOutcome {
        val wait = interrupter.delayMs()
        if (wait > 0) delay(wait)
        return when (val r = s0.run()) {
            is S0Backup.Result.Published -> {
                j.sourceKind = "flutter"
                j.backupState = "done"
                j.putBackup("dir", "files/migration/${MigrationPaths.BACKUP_NAME}")
                j.putBackup("at", now())
                j.putBackup("files", r.files.map { it.toMap() })
                j.putSource("secureEntries", SharedPrefsFile(paths.sharedPrefs, FssFormat.DATA_FILE).read()?.androidMap?.size?.toLong() ?: 0L)
                StepOutcome.Ok(linkedMapOf("files" to r.files.size.toLong(), "adopted" to r.adopted))
            }
            S0Backup.Result.NoSource -> {
                j.sourceKind = "none"
                StepOutcome.Ok(linkedMapOf("files" to 0L))
            }
            is S0Backup.Result.LowSpace -> StepOutcome.Block(BlockReason.LowSpace, r.neededBytes)
            S0Backup.Result.BackupInvalid -> StepOutcome.Block(BlockReason.BackupInvalid)
        }
    }

    private fun runM1(j: MigrationJournal, backupFiles: List<BackupFile>): StepOutcome {
        val copy = S0Backup(paths, interrupter, freeBytes, nativeVersionCode, now).authoritativeCopy(FLUTTER_PREFS, backupFiles)
        return when (val r = M1Census(paths, packageName).run(prefs.live(), copy)) {
            M1Census.Result.PrefsUnreadable -> StepOutcome.Block(BlockReason.PrefsUnreadable)
            is M1Census.Result.Done -> {
                j.deferredFeatures = r.deferred
                j.putSource("prefsBytes", r.counts["prefsBytes"])
                j.putSource("prefsKeys", r.counts["prefsKeys"])
                StepOutcome.Ok(r.counts)
            }
        }
    }

    private suspend fun runM2(j: MigrationJournal): StepOutcome {
        val m2 = M2Secrets(paths, prefs, unwrapper, sink, redactor, ledger, interrupter, now)
        return when (val r = m2.run()) {
            M2Secrets.Result.WriteRefused -> StepOutcome.Block(BlockReason.WriteRefused)
            is M2Secrets.Result.Done -> {
                if (r.flagged > 0) j.secretRetriesRemaining = SECRET_RETRIES
                StepOutcome.Ok(r.counts)
            }
        }
    }

    /** M6 (plan 05 §7.4): FSS snapshot → journal complete → barrier pref → `gate.firstOpenAt` (written in [open]). */
    private fun runM6(j: MigrationJournal): StepOutcome {
        val data = SharedPrefsFile(paths.sharedPrefs, FssFormat.DATA_FILE).read()?.androidMap.orEmpty()
        val prefix = FssFormat.ENTRY_PREFIX + "_"
        val snapshot = data.entries.filter { it.key.startsWith(prefix) && !it.key.endsWith(FssFormat.BACKUP_SUFFIX) }
            .mapNotNull { (k, v) -> (v as? RawPref.Str)?.let { k.removePrefix(prefix) to CredentialLedger.ciphertextDigest(it.value) } }
            .toMap().toSortedMap()
        Fs.writeAtomically(paths.fssSnapshot, MiniJson.encode(linkedMapOf("v" to 1L, "names" to snapshot)).toByteArray(Charsets.UTF_8))
        j.markV1CompleteAt(now())
        store.save(j)
        // `true` unless a step lowered it: the barrier never goes from false back to true (plan 05 §7.11 rule 3).
        if (!writeBarrier(j.flutterReadable != false)) return StepOutcome.Block(BlockReason.WriteRefused)
        return StepOutcome.Ok(linkedMapOf("snapshotNames" to snapshot.size.toLong()))
    }

    /**
     * The one compatibility barrier (plan 05 §7.11): written `true` to the journal, then to the prefs file (read back),
     * right after M1 and before any write. When the two disagree, `false` wins and is written to both.
     */
    private fun initBarrier(j: MigrationJournal): RunnerState.Blocked? {
        val pref = LogicalPrefs(prefs.live()).bool(LogicalPrefs.BARRIER)
        val target = if (j.flutterReadable == false || (j.flutterReadable != null && pref == false)) false else true
        if (j.flutterReadable != target) {
            j.flutterReadable = target
            store.save(j)
        }
        interrupter.reached("barrier:journal")
        if (pref != target && !writeBarrier(target)) return block(j, BlockReason.WriteRefused, "barrier", null)
        return null
    }

    private fun writeBarrier(value: Boolean): Boolean {
        if (!prefs.commit(PrefsEdit().apply { putBool(LogicalPrefs.BARRIER, value) }.changes)) return false
        return LogicalPrefs(prefs.live()).bool(LogicalPrefs.BARRIER) == value
    }

    private suspend fun step(j: MigrationJournal, id: String, body: suspend () -> StepOutcome): RunnerState.Blocked? {
        if (j.stepState(id) == StepState.Done) return null
        val attempts = j.attempts(id)
        if (attempts >= MAX_ATTEMPTS) {
            j.setStep(id, StepState.Failed, attempts, now())
            return block(j, BlockReason.StepCrashed, id, null)
        }
        mutableState.value = RunnerState.Running(id)
        j.setStep(id, StepState.Running, attempts + 1, now())
        store.save(j)
        return when (val outcome = body()) {
            is StepOutcome.Ok -> {
                j.setStep(id, StepState.Done, attempts + 1, now(), outcome.result)
                store.save(j)
                interrupter.reached(id)
                null
            }
            is StepOutcome.Block -> {
                j.setStep(id, StepState.Failed, attempts + 1, now())
                block(j, outcome.reason, id, outcome.neededBytes)
            }
        }
    }

    private fun block(j: MigrationJournal, reason: BlockReason, step: String?, neededBytes: Long?): RunnerState.Blocked {
        j.blocked = JournalBlock(reason.name, step, neededBytes)
        store.save(j)
        return blockedState(j, reason, step, neededBytes)
    }

    private fun blockedState(j: MigrationJournal?, reason: BlockReason, step: String?, neededBytes: Long?): RunnerState.Blocked {
        if (j != null && !j.migrationDoneSent) {
            signals.migrationDone(
                MigrationDoneEvent(MigrationOutcome.Failed, MigrationStepId.of(step), CountBucket.Zero, duration(j)),
            )
            j.migrationDoneSent = true
            store.save(j)
        }
        // R2 is offered only before any engine started and while the backup verifies (plan 05 §9.4). R2 is S6's.
        val restore = j != null && j.firstOpenAt == null &&
            S0Backup(paths, interrupter, freeBytes, nativeVersionCode, now).verifyPublished() != null
        val fresh = j != null && j.firstOpenAt == null && j.schema <= MigrationJournal.SCHEMA
        return RunnerState.Blocked(reason, step, neededBytes, restoreAvailable = restore, startFreshAvailable = fresh)
    }

    private suspend fun open(j: MigrationJournal, write: Boolean): RunnerState.Open {
        val firstOpen = j.firstOpenAt == null
        val live = LogicalPrefs(prefs.live())
        if (write) {
            if (j.secretRetriesRemaining > 0 && !firstOpen) {
                j.secretRetriesRemaining -= 1 // counted before the retry runs, so a crash counts too
                store.save(j)
                M2Secrets(paths, prefs, unwrapper, sink, redactor, ledger, interrupter, now).retryFlagged()
            }
            if (!j.migrationDoneSent && j.sourceKind == "flutter") {
                val flagged = M2Secrets.notices(LogicalPrefs(prefs.live())).size
                signals.diagnosticsLine(j.stepResult("M5")["line"] as? String ?: "")
                signals.migrationDone(
                    MigrationDoneEvent(
                        if (flagged > 0) MigrationOutcome.ReentryNeeded else MigrationOutcome.Done,
                        MigrationStepId.None, CountBucket.of(flagged), duration(j),
                    ),
                )
                j.migrationDoneSent = true
            }
            if (j.lastVersionCode != nativeVersionCode) {
                val last = j.lastVersionCode
                if (last != null || j.sourceKind == "flutter") signals.appUpdated(last)
                j.lastVersionCode = nativeVersionCode
            }
            if (firstOpen) j.firstOpenAt = now()
            store.save(j)
            if (paths.baseline.isFile) paths.baseline.delete() // deleted when v1 completes (plan 05 §7.4 M1)
        }
        val notices = if (j.noticesAcknowledgedAt != null) {
            MigrationNoticeSet()
        } else {
            MigrationNoticeSet(M2Secrets.notices(if (write) LogicalPrefs(prefs.live()) else live), j.deferredFeatures)
        }
        return RunnerState.Open(firstNativeLaunch = firstOpen && j.sourceKind == "flutter", notices = notices)
    }

    private fun duration(j: MigrationJournal): DurationBucket {
        val start = j.foundAt
        return DurationBucket.of(if (start == null) 0L else (now() - start).coerceAtLeast(0L))
    }

    private fun backupFiles(@Suppress("UNUSED_PARAMETER") j: MigrationJournal): List<BackupFile> =
        S0Backup(paths, interrupter, freeBytes, nativeVersionCode, now).verifyPublished().orEmpty()

    private fun fssNames(): Set<String> {
        val prefix = FssFormat.ENTRY_PREFIX + "_"
        return SharedPrefsFile(paths.sharedPrefs, FssFormat.DATA_FILE).read()?.androidMap.orEmpty().keys
            .filter { it.startsWith(prefix) && !it.endsWith(FssFormat.BACKUP_SUFFIX) }.map { it.removePrefix(prefix) }.toSet()
    }

    private fun publish(state: RunnerState): RunnerState {
        mutableState.value = state
        return state
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
        const val SECRET_RETRIES = 3
        const val FLUTTER_PREFS = "FlutterSharedPreferences"
    }
}
