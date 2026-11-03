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
)

/**
 * The schedules engine (plan 07 §5 K, Flutter controller + entrypoint + worker policy). One store is the authority
 * across the UI and background runs; every change re-arms the OS scheduler. Runs are serialized (one at a time), are
 * claimed durably before any network mutation and are never retried: an occurrence is sent at most once.
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
) {
    public val store: ScheduleStore = ScheduleStore(backend, nowMs, zone)
    public val claims: ScheduleClaims = ScheduleClaims(backend, nowMs)
    private val runner = ScheduleRunner(profiles, nowMs, pollMs)
    private val runLane = Mutex()

    /** B-STATE-089/096: one sync at a time, each reading the store inside the lane, so an older read never wins. */
    private val syncLane = Mutex()
    private val states = AtomicReference<PersistentMap<String, MutableStateFlow<SchedulesState>>>(persistentHashMapOf())
    private val log = AppLog.tagged("Schedules")

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
        return guarded(profileId, platform) {
            migrate(profileId)
            sync(profileId)
        }
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
                        profileFingerprint = fingerprint,
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
     * Flutter-era schedules carry the Flutter fingerprint (a bare SHA-256 of the Dart profile JSON). After the update
     * a schedule whose legacy fingerprint still names the same saved server is re-stamped once with the native
     * fingerprint, keeping id, revision and claims, so it keeps firing. One made for a server the profile no longer
     * names (an edited address or account) keeps its legacy stamp and stays refused, as in Flutter (B-AGENT-218); a
     * profile that is gone keeps its schedules refused.
     */
    private suspend fun migrate(profileId: String) {
        val fingerprint = profiles.fingerprint(profileId) ?: return
        val legacy = profiles.legacyFingerprint(profileId)?.takeIf { LEGACY_FINGERPRINT.matches(it) } ?: return
        val changed = store.restamp(profileId, { it == legacy }, fingerprint)
        if (changed > 0) log.i { "migrated $changed scheduled prompts" }
    }

    /** B-NAV-054: the destination of a tapped scheduled-run notification, or null to open nothing. */
    public suspend fun resolveOpen(profileId: String, scheduleId: String, sessionId: String?): ScheduledOpen? {
        if (!ScheduleIds.valid(profileId)) return null
        return safely {
            ScheduledOpen.resolve(
                profileId, scheduleId, sessionId, profiles.fingerprint(profileId), store.load(profileId), store.history(profileId),
            )
        }
    }

    // ---- Background runs (the OS worker calls these)

    /**
     * One timed occurrence. Null when nothing ran and nothing should be shown. The occurrence is claimed before any
     * network mutation; a recovered claim (the last attempt died) records "interrupted" and never sends again.
     */
    public suspend fun runOccurrence(entry: ArmedEntry, deadlineMs: Long): RunReport? =
        runLane.withLock {
            if (profiles.fingerprint(entry.profileId) == null) return@withLock null
            if (entry.dueAtMs > nowMs()) {
                // The clock moved back: keep the occurrence unsent and re-arm it.
                safely { sync(entry.profileId) }
                return@withLock null
            }
            val claim = safely { claims.claim(entry) } ?: return@withLock null
            if (claim == Claim.Duplicate) {
                // B-STATE-090: the last attempt settled this occurrence but may have died (or failed a write) before
                // it advanced the schedule or re-armed the next run. Finish that, never run it again.
                if (!entry.digest) safely { advanceSettled(entry) }
                safely { sync(entry.profileId) }
                return@withLock null
            }
            if (claim == Claim.Stale) return@withLock null
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

    /** Moves a settled occurrence's schedule past it when the attempt that settled it could not. */
    private suspend fun advanceSettled(entry: ArmedEntry) {
        val schedule = store.load(entry.profileId).firstOrNull { it.id == entry.scheduleId } ?: return
        if (!schedule.enabled || schedule.revision != entry.revision || schedule.nextRunAtMs != entry.dueAtMs) return
        val due = schedule.due(nowMs(), zone()) ?: return
        store.advanceOccurrence(schedule, due.nextRunAtMs)
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
        deadlineMs: Long,
        requestedAtMs: Long = nowMs(),
    ): RunReport? =
        runLane.withLock {
            if (profiles.fingerprint(profileId) == null) return@withLock null
            val schedule =
                safely { store.load(profileId) }?.firstOrNull { it.id == scheduleId }?.takeIf { it.revision == revision }
                    ?: return@withLock null
            val admittedAt = nowMs()
            val entry = ArmedEntry(profileId, scheduleId, revision, requestedAtMs)
            val runId = ScheduleIds.occurrence(schedule, requestedAtMs)
            val earlier = safely { store.history(profileId) }?.firstOrNull { it.id == runId }
            if (earlier != null) {
                if (earlier.status != RunStatus.Running) return@withLock null // this request already ended
                val interrupted = earlier.copy(finishedAtMs = admittedAt, status = RunStatus.Failed, errorCode = "interrupted")
                safely { store.recordRun(profileId, interrupted) }
                publishAfterRun(profileId)
                return@withLock report(interrupted).also { notices.finished(entry, it) }
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
        private val LEGACY_FINGERPRINT = Regex("^[0-9a-f]{64}$")
        private const val DAY_MS = 86_400_000L
        private const val DRAFT_ATTEMPTS = 5

        /** The native profile fingerprint over the server identity fields, without the display name. */
        public fun fingerprint(identity: List<String?>): String = FINGERPRINT_PREFIX + DartJson.sha256Hex(DartJson.encode(identity))
    }
}
