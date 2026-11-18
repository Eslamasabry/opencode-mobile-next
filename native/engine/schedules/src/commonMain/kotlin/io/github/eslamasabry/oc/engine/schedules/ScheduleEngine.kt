package io.github.eslamasabry.oc.engine.schedules

import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.TimeZone
import kotlin.concurrent.atomics.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

/** One profile's list and editor state (Flutter `ScheduledPromptsController` fields). */
public data class SchedulesState(
    val loading: Boolean = true,
    val platform: SchedulePlatform = SchedulePlatform(),
    val schedules: ImmutableList<ScheduledPrompt> = persistentListOf(),
    val runs: ImmutableList<ScheduledRun> = persistentListOf(),
    val digest: ScheduleDigest = ScheduleDigest(),
    /** The last action's fixed code; null after a success. */
    val error: ScheduleCode? = null,
)

/** What a person typed in the editor; the engine turns it into a stored schedule. */
public data class ScheduleDraft(
    val prompt: String,
    val projectPath: String,
    val recurrence: Recurrence,
    val workspace: String? = null,
    val sessionId: String? = null,
    val agent: String? = null,
    val modelProviderId: String? = null,
    val modelId: String? = null,
    val missedPolicy: MissedRunPolicy = MissedRunPolicy.CatchUp,
    /** B-440: see [ScheduledPrompt.webReads]. */
    val webReads: Boolean = false,
)

/**
 * The schedules engine (plan 07 §5 K, Flutter controller + entrypoint + worker policy). One store is the authority
 * across the UI and background runs; every change re-arms the OS scheduler. Runs of one profile are serialized (they
 * can share a conversation and the phone's own runtime), runs of different profiles are not; they are claimed durably
 * before any network mutation and are never retried: an occurrence is sent at most once. A run's time budget starts
 * when its run starts, never while it waits for its profile's lane.
 */
public class ScheduleEngine(
    private val backend: ScheduleBackend,
    private val profiles: ScheduleProfiles,
    private val alarms: ScheduleAlarms,
    private val notices: ScheduleNotices,
    private val nowMs: () -> Long,
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
    private val newId: () -> String = { Random.nextBytes(12).joinToString("") { b -> (b.toInt() and 0xff).toString(16).padStart(2, '0') } },
    pollMs: Long = 2_000,
    /**
     * True while the Flutter build can still be restored (`oc.native.flutterReadable`): Flutter-era fingerprints are
     * then kept as they are in the shared store (E00-S04).
     */
    private val flutterReadable: suspend () -> Boolean = { false },
    /** The longest a run waits for its profile's lane before it gives up without sending (WorkManager's window is 10 minutes). */
    private val laneWaitMs: Long = LANE_WAIT_MS,
) {
    public val store: ScheduleStore = ScheduleStore(backend, nowMs, zone)
    public val claims: ScheduleClaims = ScheduleClaims(backend, nowMs)
    private val fingerprints = ScheduleFingerprints(backend)
    private val receipts = ScheduleReceipts(backend, store, nowMs, zone)
    private val runner =
        ScheduleRunner(profiles, nowMs, pollMs) { schedule ->
            fingerprints.accepts(schedule.profileId, schedule.profileFingerprint, profiles.fingerprint(schedule.profileId)) ||
                legacyNamesThisServer(schedule.profileId, schedule.profileFingerprint, remember = true) ||
                schedule.profileFingerprint in profiles.earlierFingerprints(schedule.profileId)
        }
    private val unmatched = AtomicReference<PersistentMap<String, Boolean>>(persistentHashMapOf())

    /**
     * One lane per profile. What really conflicts is what runs of one profile share: a conversation (a schedule that
     * continues one, or two prompts into the same one: the busy check and the send are not atomic) and the phone's own
     * runtime (only the profile that owns it uses it). Runs on different servers share nothing, so one server's slow
     * reply never holds up another server's run.
     */
    private val runLanes = AtomicReference<PersistentMap<String, Mutex>>(persistentHashMapOf())

    /** B-STATE-089/096: one sync at a time, each reading the store inside the lane, so an older read never wins. */
    private val syncLane = Mutex()
    private val states = AtomicReference<PersistentMap<String, MutableStateFlow<SchedulesState>>>(persistentHashMapOf())
    private val log = AppLog.tagged("Schedules")

    private fun runLane(profileId: String): Mutex {
        while (true) {
            val current = runLanes.load()
            current[profileId]?.let { return it }
            val created = Mutex()
            if (runLanes.compareAndSet(current, current.putting(profileId, created))) return created
        }
    }

    /** Runs [block] holding [profileId]'s lane; [busy] when the lane stays taken for [laneWaitMs] (nothing was sent). */
    private suspend fun <T> inRunLane(profileId: String, busy: suspend () -> T, block: suspend () -> T): T {
        val lane = runLane(profileId)
        var held = false
        withTimeoutOrNull(laneWaitMs) {
            lane.lock()
            held = true
        }
        if (!held) return busy()
        try {
            return block()
        } finally {
            lane.unlock()
        }
    }

    public fun state(profileId: String): StateFlow<SchedulesState> = flow(profileId).asStateFlow()

    private fun flow(profileId: String): MutableStateFlow<SchedulesState> {
        while (true) {
            val current = states.load()
            current[profileId]?.let { return it }
            val created = MutableStateFlow(SchedulesState())
            if (states.compareAndSet(current, current.putting(profileId, created))) return created
        }
    }

    // ---- List and editor

    /** Reads the store (migrating Flutter-era schedules once), refreshes the OS scheduler and publishes state. */
    public suspend fun load(profileId: String): Outcome<Unit> {
        flow(profileId).update { it.copy(loading = true, error = null) }
        val platform = alarms.status()
        if (!platform.supported) {
            flow(profileId).update { it.copy(loading = false, platform = platform, error = ScheduleCode.Unsupported) }
            return ScheduleCode.Unsupported.asFailure().failed()
        }
        val result =
            guarded(profileId, platform) {
                migrate(profileId)
                // E00-S05: failed runs the Flutter worker could only leave as receipts show in the history.
                receipts.merge(profileId)
                sync(profileId)
            }
        // The list is already shown; runs whose reply could not be followed are settled from their conversation now.
        if (result is Outcome.Ok && settleUnconfirmed(profileId) > 0) safely { publish(profileId, platform) }
        return result
    }

    /**
     * Runs the server accepted but whose reply could not be followed ([ScheduleRunner.UNCONFIRMED]) are settled from
     * the conversation itself once the server answers: replied, failed, or still unknown. Nothing is sent. Returns how
     * many records changed. Best effort and bounded; a server that is still unreachable leaves them as they are.
     */
    public suspend fun settleUnconfirmed(profileId: String): Int {
        if (!ScheduleIds.valid(profileId)) return 0
        val pending =
            safely { store.history(profileId) }
                ?.filter { it.status == RunStatus.Ran && it.errorCode == ScheduleRunner.UNCONFIRMED && it.sessionId != null }
                ?.sortedByDescending { it.scheduledAtMs }
                ?.take(SETTLE_MAX)
                .orEmpty()
        if (pending.isEmpty()) return 0
        val schedules = safely { store.load(profileId) }.orEmpty().associateBy { it.id }
        var changed = 0
        val deadline = nowMs() + SETTLE_BUDGET_MS
        for ((scheduleId, runs) in pending.groupBy { it.scheduleId }) {
            val schedule = schedules[scheduleId] ?: continue
            if (profiles.fingerprint(profileId) == null) return changed
            if (!fingerprints.accepts(profileId, schedule.profileFingerprint, profiles.fingerprint(profileId))) continue
            if (deadline - nowMs() <= 0) break
            val opened =
                withTimeoutOrNull(deadline - nowMs()) { profiles.openUntil(profileId, schedule.projectPath, schedule.workspace, deadline) }
            if (opened !is Outcome.Ok) continue
            val connection = opened.value
            try {
                // A phone runtime this check would have to start is not worth starting just to look.
                if (connection.startedPhoneServer) continue
                for (run in runs) {
                    val settled =
                        try {
                            withTimeoutOrNull(deadline - nowMs()) { runner.settle(connection.gateway, run, nowMs()) }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            null
                        } ?: continue
                    if (safely { store.recordRun(profileId, settled); true } == true) changed++
                }
            } finally {
                withContext(NonCancellable) { withTimeoutOrNull(CLOSE_WAIT_MS) { connection.close() } }
            }
        }
        return changed
    }

    /** A new schedule from [draft], or an edit of [editing] (same id, next revision). */
    public suspend fun save(profileId: String, draft: ScheduleDraft, editing: ScheduledPrompt? = null): Outcome<ScheduledPrompt> {
        var saved: ScheduledPrompt? = null
        val result =
            guarded(profileId) {
                if (!alarms.status().supported) throw ScheduleStoreException(ScheduleCode.Unsupported)
                if (editing != null && editing.profileId != profileId) throw ScheduleStoreException(ScheduleCode.Unsupported)
                val fingerprint = profiles.fingerprint(profileId) ?: throw ScheduleStoreException(ScheduleCode.Unsupported)
                if (!draft.recurrence.valid) throw ScheduleStoreException(ScheduleCode.InvalidSchedule)
                val next = Recurrences.next(draft.recurrence, nowMs(), zone()) ?: throw ScheduleStoreException(ScheduleCode.ScheduleExpired)
                val schedule =
                    ScheduledPrompt(
                        id = editing?.id ?: newId(),
                        profileId = profileId,
                        profileFingerprint = stampFor(profileId, fingerprint),
                        prompt = draft.prompt.trim(),
                        projectPath = draft.projectPath.trim(),
                        recurrence = draft.recurrence,
                        createdAtMs = editing?.createdAtMs ?: nowMs(),
                        workspace = draft.workspace,
                        sessionId = draft.sessionId,
                        agent = draft.agent,
                        modelProviderId = draft.modelProviderId,
                        modelId = draft.modelId.takeIf { draft.modelProviderId != null },
                        missedPolicy = draft.missedPolicy,
                        enabled = true,
                        nextRunAtMs = next,
                        revision = (editing?.revision ?: 0) + 1,
                        webReads = draft.webReads,
                    )
                store.save(schedule)
                saved = schedule
                sync(profileId)
            }
        return when (result) {
            is Outcome.Failed -> result
            is Outcome.Ok -> saved!!.ok()
        }
    }

    public suspend fun setEnabled(profileId: String, id: String, enabled: Boolean): Outcome<Unit> =
        guarded(profileId) {
            store.setEnabled(profileId, id, enabled)
            sync(profileId)
        }

    public suspend fun delete(profileId: String, id: String): Outcome<Unit> =
        guarded(profileId) {
            store.delete(profileId, id)
            sync(profileId)
        }

    public suspend fun setDigest(profileId: String, digest: ScheduleDigest): Outcome<Unit> =
        guarded(profileId) {
            store.setDigest(profileId, digest)
            sync(profileId)
        }

    /** B-STATE-024: the editor's unsaved prompt for [scheduleId] (null = a new schedule); kept until it is saved. */
    public suspend fun promptDraft(profileId: String, scheduleId: String?): String? =
        if (!ScheduleIds.valid(profileId)) null else safely { backend.read(ScheduleKeys.draft(profileId, scheduleId)) }

    /** Saves (or, when empty, forgets) the editor's typed prompt. Best effort: the editor keeps working either way. */
    public suspend fun setPromptDraft(profileId: String, scheduleId: String?, text: String) {
        if (!ScheduleIds.valid(profileId)) return
        val key = ScheduleKeys.draft(profileId, scheduleId)
        val value = text.ifEmpty { null }
        repeat(DRAFT_ATTEMPTS) {
            val raw = safely { backend.read(key) }
            if (raw == value) return
            if (safely { backend.compareAndSet(key, raw, value) } == true) return
        }
    }

    /** Runs the saved schedule once, now, in the background. The next scheduled time is kept. */
    public suspend fun runNow(profileId: String, id: String): Outcome<Unit> =
        guarded(profileId) {
            val schedule = store.load(profileId).firstOrNull { it.id == id } ?: throw ScheduleStoreException(ScheduleCode.ScheduleMissing)
            if (!alarms.status().supported) throw ScheduleStoreException(ScheduleCode.Unsupported)
            alarms.runNow(profileId, id, schedule.revision)
        }

    // ---- OS scheduler

    /** Re-arms every saved profile's schedules (boot, app update, first open, Flutter migration). */
    public suspend fun restoreAll() {
        for (profileId in profiles.ids()) {
            if (!ScheduleIds.valid(profileId)) continue
            try {
                migrate(profileId)
                advanceAllSettled(profileId)
                sync(profileId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ScheduleStoreException) {
                log.w { "restore refused: ${e.code.wire}" }
            }
        }
    }

    /** A removed profile: cancel what is armed. Its keys are swept by the profile deletion (`oc.<what>.<id>`). */
    public fun forgetProfile(profileId: String) {
        alarms.cancelProfile(profileId)
        while (true) {
            val current = states.load()
            if (states.compareAndSet(current, current.removing(profileId))) return
        }
    }

    private suspend fun sync(profileId: String): Unit = syncLane.withLock { syncNow(profileId) }

    private suspend fun syncNow(profileId: String) {
        val schedules = store.load(profileId)
        val digest = store.digest(profileId)
        val entries =
            schedules.filter { it.enabled && it.nextRunAtMs != null }.map { ArmedEntry(profileId, it.id, it.revision, it.nextRunAtMs!!) } +
                listOfNotNull(
                    if (digest.enabled) ArmedEntry(profileId, ScheduleIds.DIGEST, 0, Recurrences.nextDigest(digest, nowMs(), zone())) else null,
                )
        claims.replaceIndex(profileId, entries)
        alarms.replace(profileId, entries)
    }

    /**
     * Flutter-era schedules carry the Flutter fingerprint (a bare SHA-256 of the Dart profile JSON). A schedule whose
     * legacy fingerprint still names the same saved server keeps firing; one made for a server the profile no longer
     * names (an edited address or account) stays refused, as in Flutter (B-AGENT-218); a profile that is gone keeps
     * its schedules refused.
     *
     * E00-S04: while the Flutter build can still be restored, the shared store is left exactly as Flutter wrote it and
     * the native fingerprint goes to a native-only key ([ScheduleFingerprints]). Once it cannot, the schedules are
     * re-stamped once with the native fingerprint, keeping id, revision and claims, and that key is removed.
     */
    private suspend fun migrate(profileId: String) {
        val fingerprint = profiles.fingerprint(profileId) ?: return
        val legacy = profiles.legacyFingerprint(profileId)?.takeIf { ScheduleFingerprints.LEGACY.matches(it) }
        // Stamps made under the same server's earlier version (the version is part of Flutter's hash and moves when
        // the phone's own server is refreshed) name this server too.
        val earlier =
            store.load(profileId).map { it.profileFingerprint }.filter {
                ScheduleFingerprints.LEGACY.matches(it) && it != legacy && legacyNamesThisServer(profileId, it, remember = false)
            }.toSet()
        if (flutterReadable()) {
            for (stamp in setOfNotNull(legacy.takeIf { l -> store.load(profileId).any { it.profileFingerprint == l } }) + earlier) {
                fingerprints.remember(profileId, fingerprint, stamp)
            }
            return
        }
        // A native stamp from before this phone's own server moved to this app's port: the same server.
        val moved = profiles.earlierFingerprints(profileId)
        if (moved.isNotEmpty() && store.load(profileId).any { it.profileFingerprint in moved }) {
            store.restamp(profileId, { it in moved }, fingerprint)
        }
        val alias = fingerprints.alias(profileId)
        val accepted = setOfNotNull(legacy) + earlier + alias?.takeIf { it.native == fingerprint }?.legacy.orEmpty()
        if (accepted.isNotEmpty()) {
            val changed = store.restamp(profileId, { it in accepted }, fingerprint)
            if (changed > 0) log.i { "migrated $changed scheduled prompts" }
        }
        if (alias != null) fingerprints.forget(profileId)
    }

    /**
     * True when the Flutter-era [stamp] is this profile's own row under another server version (and nothing else
     * changed). A positive is written to the alias when [remember] so later checks need no search; a negative is
     * remembered in memory until the row changes.
     */
    private suspend fun legacyNamesThisServer(profileId: String, stamp: String, remember: Boolean): Boolean {
        if (!ScheduleFingerprints.LEGACY.matches(stamp)) return false
        val native = profiles.fingerprint(profileId) ?: return false
        val row = profiles.legacyRow(profileId) ?: return false
        val key = "$profileId|$stamp|${LegacyFingerprints.stamp(row)}"
        if (unmatched.load()[key] == true) return false
        if (!LegacyFingerprints.matches(row, stamp, profiles.earlierAddresses(profileId))) {
            while (true) {
                val current = unmatched.load()
                if (unmatched.compareAndSet(current, current.putting(key, true))) break
            }
            return false
        }
        if (remember) safely { fingerprints.remember(profileId, native, stamp) }
        return true
    }

    /**
     * The stamp a saved schedule gets. While the Flutter build can still be restored it is the Flutter fingerprint
     * (when known), so a schedule made on native also runs after a rollback; native accepts it through the alias.
     */
    private suspend fun stampFor(profileId: String, fingerprint: String): String {
        if (!flutterReadable()) return fingerprint
        val legacy = profiles.legacyFingerprint(profileId)?.takeIf { ScheduleFingerprints.LEGACY.matches(it) } ?: return fingerprint
        fingerprints.remember(profileId, fingerprint, legacy)
        return legacy
    }

    /** B-NAV-054: the destination of a tapped scheduled-run notification, or null to open nothing. */
    public suspend fun resolveOpen(profileId: String, scheduleId: String, sessionId: String?): ScheduledOpen? {
        if (!ScheduleIds.valid(profileId)) return null
        return safely {
            val current = profiles.fingerprint(profileId)
            val alias = fingerprints.alias(profileId)?.takeIf { it.native == current }
            ScheduledOpen.resolve(
                profileId, scheduleId, sessionId, current, store.load(profileId), store.history(profileId),
                alsoAccepted = alias?.legacy.orEmpty(),
            )
        }
    }

    // ---- Background runs (the OS worker calls these)

    /**
     * One timed occurrence. Null when nothing ran and nothing should be shown. The occurrence is claimed before any
     * network mutation; a recovered claim (the last attempt died) records "interrupted" and never sends again.
     */
    public suspend fun runOccurrence(entry: ArmedEntry, budgetMs: Long): RunReport? =
        inRunLane(
            entry.profileId,
            busy = {
                // Still queued behind another run of this server: nothing was claimed or sent. Re-arm the occurrence
                // (it is already due, so the OS runs it again as soon as this worker ends) instead of spending it.
                safely { sync(entry.profileId) }
                null
            },
        ) {
            // The budget starts here, when this run starts, not when the OS worker did.
            val deadlineMs = nowMs() + budgetMs
            if (profiles.fingerprint(entry.profileId) == null) return@inRunLane null
            if (entry.dueAtMs > nowMs()) {
                // The clock moved back: keep the occurrence unsent and re-arm it.
                safely { sync(entry.profileId) }
                return@inRunLane null
            }
            val claim = safely { claims.claim(entry) } ?: return@inRunLane null
            if (claim == Claim.Duplicate) {
                // B-STATE-090: the last attempt settled this occurrence but may have died (or failed a write) before
                // it advanced the schedule or re-armed the next run. Finish that, never run it again.
                if (!entry.digest) safely { advanceSettled(entry) }
                safely { sync(entry.profileId) }
                return@inRunLane null
            }
            if (claim == Claim.Stale) return@inRunLane null
            if (claim == Claim.New && !entry.digest) notices.started(entry)
            val report =
                try {
                    if (entry.digest) digestRun(entry) else occurrence(entry, recoveryOnly = claim == Claim.Recover, deadlineMs)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ScheduleStoreException) {
                    RunReport("failed", e.code.wire)
                }
            safely { claims.settle(entry) }
            safely { sync(entry.profileId) }
            publishAfterRun(entry.profileId)
            report.also { if (it.quiet) notices.clear(entry) else notices.finished(entry, it) }
        }

    /**
     * Moves a settled occurrence's schedule past it when the attempt that settled it could not. The settled run covered
     * the occurrences missed up to the moment it ran (its claim's `at`), not up to now: an occurrence that fell due
     * after that run and before now is still owed, and arming it makes it the one catch-up run (B-148: Flutter 2.1.4
     * ran a late occurrence without moving `nextRunAt`; moving past *now* dropped the day the phone was off after the
     * update).
     */
    private suspend fun advanceSettled(entry: ArmedEntry) {
        val schedule = store.load(entry.profileId).firstOrNull { it.id == entry.scheduleId } ?: return
        if (!schedule.enabled || schedule.revision != entry.revision || schedule.nextRunAtMs != entry.dueAtMs) return
        val now = nowMs()
        schedule.due(now, zone()) ?: return
        val ranAt = (claims.claimedAtMs(entry) ?: now).coerceIn(entry.dueAtMs, now)
        store.advanceOccurrence(schedule, Recurrences.next(schedule.recurrence, ranAt, zone()))
    }

    /**
     * Before a restore arms anything: a stored occurrence that is already past and already settled (Flutter 2.1.4 ran
     * it late and left `nextRunAt` on it) moves to its next time now. Arming it as stored gives the OS only an
     * already-due run and no exact alarm, so after the update the phone held no alarm for the schedule until that
     * catch-up worker had run (upgrade gate run 6, schedule-armed `1 -> 0`). An unsettled past occurrence is still
     * owed and stays as it is, armed due.
     */
    private suspend fun advanceAllSettled(profileId: String) {
        val now = nowMs()
        for (schedule in store.load(profileId)) {
            val due = schedule.nextRunAtMs ?: continue
            if (!schedule.enabled || due > now) continue
            val entry = ArmedEntry(profileId, schedule.id, schedule.revision, due)
            if (claims.finished(entry)) advanceSettled(entry)
        }
    }

    /**
     * A person's "Run now": no occurrence claim and no settlement, so no scheduled time moves. [requestedAtMs] is the
     * tap's time carried in the work request, so a worker the OS restarts after process death is the same run: its
     * in-flight record (written before the prompt) is then closed as "interrupted" and never sent again (B-AGENT-215).
     */
    public suspend fun runManual(
        profileId: String,
        scheduleId: String,
        revision: Int,
        budgetMs: Long,
        requestedAtMs: Long = nowMs(),
    ): RunReport? =
        inRunLane(
            profileId,
            busy = { notSentBusy(profileId, scheduleId, revision, requestedAtMs) },
        ) {
            if (profiles.fingerprint(profileId) == null) return@inRunLane null
            val schedule =
                safely { store.load(profileId) }?.firstOrNull { it.id == scheduleId }?.takeIf { it.revision == revision }
                    ?: return@inRunLane null
            val admittedAt = nowMs()
            // The budget starts here, when this run starts, not when the OS worker did.
            val deadlineMs = admittedAt + budgetMs
            val entry = ArmedEntry(profileId, scheduleId, revision, requestedAtMs)
            val runId = ScheduleIds.occurrence(schedule, requestedAtMs)
            val earlier = safely { store.history(profileId) }?.firstOrNull { it.id == runId }
            if (earlier != null) {
                if (earlier.status != RunStatus.Running) return@inRunLane null // this request already ended
                val interrupted = earlier.copy(finishedAtMs = admittedAt, status = RunStatus.Failed, errorCode = "interrupted")
                safely { store.recordRun(profileId, interrupted) }
                publishAfterRun(profileId)
                return@inRunLane report(interrupted).also { notices.finished(entry, it) }
            }
            val running = ScheduledRun(runId, schedule.id, admittedAt, admittedAt, RunStatus.Running)
            val report =
                try {
                    store.recordRun(profileId, running)
                    notices.started(entry)
                    val result =
                        runner.execute(
                            schedule,
                            deadlineMs,
                            manual = true,
                            onSessionCreated = { store.recordRun(profileId, running.copy(sessionId = it)) },
                            isCurrent = { store.load(profileId).firstOrNull { it.id == schedule.id }?.revision == schedule.revision },
                        )
                    store.recordRun(profileId, result.copy(id = runId, scheduledAtMs = admittedAt, startedAtMs = admittedAt, late = false))
                    report(result)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ScheduleStoreException) {
                    RunReport("failed", e.code.wire)
                }
            publishAfterRun(profileId)
            report.also { if (it.quiet) notices.clear(entry) else notices.finished(entry, it) }
        }

    /**
     * A "Run now" whose profile lane stayed taken for the whole wait: it was never sent. It is recorded as skipped
     * ("busy": another run on this server was still using it) so the person sees it and can tap again.
     */
    private suspend fun notSentBusy(profileId: String, scheduleId: String, revision: Int, requestedAtMs: Long): RunReport? {
        val schedule =
            safely { store.load(profileId) }?.firstOrNull { it.id == scheduleId }?.takeIf { it.revision == revision }
                ?: return null
        val runId = ScheduleIds.occurrence(schedule, requestedAtMs)
        if (safely { store.history(profileId) }?.any { it.id == runId } == true) return null
        val at = nowMs()
        val skipped = ScheduledRun(runId, schedule.id, at, at, RunStatus.Skipped, finishedAtMs = at, errorCode = "busy")
        safely { store.recordRun(profileId, skipped) }
        publishAfterRun(profileId)
        return report(skipped).also { notices.finished(ArmedEntry(profileId, scheduleId, revision, requestedAtMs), it) }
    }

    private suspend fun occurrence(entry: ArmedEntry, recoveryOnly: Boolean, deadlineMs: Long): RunReport {
        val profileId = entry.profileId
        val schedule = store.load(profileId).firstOrNull { it.id == entry.scheduleId }
        if (schedule == null || !schedule.enabled || schedule.revision != entry.revision || schedule.nextRunAtMs != entry.dueAtMs) {
            return RunReport("skipped", "stale_target")
        }
        val due = schedule.due(nowMs(), zone()) ?: return RunReport("skipped", "not_due")
        val runId = ScheduleIds.occurrence(schedule, due.scheduledAtMs)
        val existing = store.history(profileId).firstOrNull { it.id == runId }
        val run: ScheduledRun
        var title: String? = null
        var summary: String? = null
        if (existing != null && existing.status != RunStatus.Running) {
            run = existing
        } else if (existing != null || recoveryOnly || !due.shouldRun) {
            val interrupted = existing != null || recoveryOnly
            run =
                ScheduledRun(
                    id = runId,
                    scheduleId = schedule.id,
                    scheduledAtMs = due.scheduledAtMs,
                    startedAtMs = existing?.startedAtMs ?: nowMs(),
                    finishedAtMs = nowMs(),
                    status = if (interrupted) RunStatus.Failed else RunStatus.Skipped,
                    late = due.late,
                    sessionId = existing?.sessionId,
                    errorCode = if (interrupted) "interrupted" else "missed",
                )
        } else {
            // Claimed above. Keep an in-flight record before crossing the network boundary, so an interrupted
            // attempt is recovered as "interrupted" and never resent.
            val admittedAt = nowMs()
            val running = ScheduledRun(runId, schedule.id, due.scheduledAtMs, admittedAt, RunStatus.Running, late = due.late)
            store.recordRun(profileId, running)
            val result =
                runner.execute(
                    schedule,
                    deadlineMs,
                    onSessionCreated = { store.recordRun(profileId, running.copy(sessionId = it)) },
                    isCurrent = {
                        val current = store.load(profileId).firstOrNull { it.id == schedule.id }
                        current != null && current.enabled && current.revision == schedule.revision &&
                            current.nextRunAtMs == schedule.nextRunAtMs
                    },
                )
            title = result.title
            summary = result.summary
            run = result.copy(startedAtMs = admittedAt)
        }
        if (existing == null || existing.status == RunStatus.Running) store.recordRun(profileId, run)
        store.advanceOccurrence(schedule, due.nextRunAtMs)
        return report(run.copy(title = title, summary = summary))
    }

    private suspend fun digestRun(entry: ArmedEntry): RunReport {
        val profileId = entry.profileId
        val digest = store.digest(profileId)
        val cutoff = nowMs()
        if (!digest.enabled) return RunReport("skipped", "disabled")
        val since = digest.lastDeliveredAtMs ?: (cutoff - DAY_MS)
        val runs =
            store.history(profileId).filter {
                val at = it.finishedAtMs ?: it.startedAtMs
                it.status != RunStatus.Running && at > since && at <= cutoff
            }
        if (!store.claimDigest(profileId, digest, cutoff)) return RunReport("skipped", "disabled")
        return RunReport(
            status = "digest",
            ran = runs.count { it.status == RunStatus.Ran },
            failed = runs.count { it.status == RunStatus.Failed },
            skipped = runs.count { it.status == RunStatus.Skipped },
        )
    }

    private fun report(run: ScheduledRun): RunReport =
        RunReport(run.status.wire, run.errorCode, run.sessionId, run.late, run.title, run.summary)

    // ---- Plumbing

    private suspend fun publishAfterRun(profileId: String) {
        if (!states.load().containsKey(profileId)) return
        safely { publish(profileId, flow(profileId).value.platform) }
    }

    private suspend fun publish(profileId: String, platform: SchedulePlatform) {
        val envelopeSchedules = store.load(profileId)
        val runs = store.history(profileId)
        val digest = store.digest(profileId)
        flow(profileId).update {
            it.copy(
                loading = false,
                platform = platform,
                schedules = envelopeSchedules.sortedBy { s -> s.nextRunAtMs ?: Long.MAX_VALUE }.toImmutableList(),
                runs = runs.toImmutableList(),
                digest = digest,
            )
        }
    }

    private suspend fun guarded(
        profileId: String,
        platform: SchedulePlatform = flow(profileId).value.platform,
        block: suspend () -> Unit,
    ): Outcome<Unit> {
        flow(profileId).update { it.copy(error = null) }
        return try {
            block()
            publish(profileId, platform)
            Unit.ok()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ScheduleStoreException) {
            flow(profileId).update { it.copy(loading = false, platform = platform, error = e.code) }
            safely { publish(profileId, platform) }
            flow(profileId).update { it.copy(error = e.code) }
            e.code.asFailure().failed()
        }
    }

    private suspend fun <T> safely(block: suspend () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ScheduleStoreException) {
            log.w { "schedule store: ${e.code.wire}" }
            null
        }

    public companion object {
        /** Native fingerprints carry this prefix; a bare 64-hex value is a Flutter-era one. */
        public const val FINGERPRINT_PREFIX: String = "n1:"
        private const val DAY_MS = 86_400_000L
        private const val DRAFT_ATTEMPTS = 5
        private const val SETTLE_MAX = 10
        private const val SETTLE_BUDGET_MS = 20_000L
        private const val CLOSE_WAIT_MS = 3_000L

        /** 2.5 minutes: a 7-minute budget plus this still ends inside WorkManager's 10-minute window. */
        public const val LANE_WAIT_MS: Long = 150_000L

        /** The native profile fingerprint over the server identity fields, without the display name. */
        public fun fingerprint(identity: List<String?>): String = FINGERPRINT_PREFIX + DartJson.sha256Hex(DartJson.encode(identity))
    }
}
