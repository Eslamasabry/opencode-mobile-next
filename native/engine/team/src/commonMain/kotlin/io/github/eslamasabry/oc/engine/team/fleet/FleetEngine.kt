package io.github.eslamasabry.oc.engine.team.fleet

import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.engine.team.TeamConfig
import kotlin.concurrent.atomics.AtomicReference
import kotlin.time.Clock
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Dart `OrchestrationPhase` as a page sees it. */
public enum class FleetPhase {
    /** Nothing asked yet (no page is watching). */
    Idle,

    /** Looking for the team at its address (Dart `probing` / `connecting`). */
    Probing,

    /** The team answered; [FleetState.snapshot] is its last read. */
    Ready,

    /** The probe found no team to show: [FleetState.failure] says why. */
    Failed,
}

/** Dart `OrchestrationStreamStatus`. */
public enum class FleetStream { Closed, Connecting, Live, Reconnecting }

/** A computer's team as the pages read it (Dart `OrchestrationController`'s getters). */
public data class FleetState(
    val phase: FleetPhase = FleetPhase.Idle,
    val failure: FleetFailure? = null,
    val host: FleetHost? = null,
    val capabilities: FleetCapabilities = FleetCapabilities.NONE,
    val snapshot: FleetSnapshot = FleetSnapshot(),
    /** A read has come back at least once (Dart `snapshot.hasData`). */
    val hasData: Boolean = false,
    val stream: FleetStream = FleetStream.Closed,
    /** When the last read that succeeded came back. */
    val refreshedAtMs: Long? = null,
    /** The last read failed; [snapshot] is the one before it. */
    val readFailed: Boolean = false,
    /** The host's scheduled jobs once a page asked ([FleetEngine.loadJobs]); null before. */
    val jobs: List<FleetJob>? = null,
    /** Every write's receipt this run, by request id. */
    val receipts: Map<String, FleetReceipt> = emptyMap(),
    /** Writes on their way, by request id. */
    val sending: Set<String> = emptySet(),
) {
    /** Dart `isStale`: ready, and the stream is not live or the data is older than a minute. */
    public fun stale(nowMs: Long): Boolean {
        if (phase != FleetPhase.Ready) return false
        if (stream != FleetStream.Live) return true
        val at = refreshedAtMs ?: return true
        return nowMs - at > FleetEngine.STALE_AFTER_MS
    }
}

/**
 * The AI Team on a computer (Dart `OrchestrationController` for Gas City): finds the team at the saved address, reads
 * every scope, follows the host's event stream and reads again only what an event made stale (400 ms debounce). After
 * a drop it reconnects with back-off and reads everything again: reconcile by refetch, never replay. Writes go through
 * [control], one receipt each, never re-sent. Nothing is stored on the phone.
 *
 * The stream runs while a page collects [live]; a second collector joins the first.
 */
public class FleetEngine(
    private val transports: FleetTransports,
    private val clock: Clock,
    private val newRequestId: () -> String = Ids()::next,
    private val debounceMs: Long = 400,
    private val backoffBaseMs: Long = 500,
    private val backoffCapMs: Long = 30_000,
) {
    private val states = AtomicReference<PersistentMap<String, MutableStateFlow<FleetState>>>(persistentHashMapOf())
    private val runs = AtomicReference<PersistentMap<String, Run>>(persistentHashMapOf())
    private val log = AppLog.tagged("TeamFleet")

    public fun state(profileId: String): StateFlow<FleetState> = flow(profileId).asStateFlow()

    private fun flow(profileId: String): MutableStateFlow<FleetState> {
        while (true) {
            val current = states.load()
            current[profileId]?.let { return it }
            val created = MutableStateFlow(FleetState())
            if (states.compareAndSet(current, current.put(profileId, created))) return created
        }
    }

    /** One profile's live connection: its gateway, what is stale, the retry signal. */
    private inner class Run(val profileId: String, val config: TeamConfig) {
        val owner = Mutex()
        val reading = Mutex()
        var gateway: FleetGateway? = null
        val dirty = mutableSetOf<FleetScope>()
        val dirtyLock = Mutex()
        val retries = MutableStateFlow(0)
        val state: MutableStateFlow<FleetState>
            get() = flow(profileId)
    }

    private fun run(profileId: String, config: TeamConfig): Run {
        while (true) {
            val current = runs.load()
            current[profileId]?.takeIf { it.config == config }?.let { return it }
            val created = Run(profileId, config)
            if (runs.compareAndSet(current, current.put(profileId, created))) return created
        }
    }

    private fun now(): Long = clock.now().toEpochMilliseconds()

    /**
     * Keeps [profileId]'s team connected until the caller is cancelled: probe, read everything, follow the stream. A
     * failed probe waits for [refresh] to look again. A caller that finds one already running waits beside it and
     * takes over if it ends first.
     */
    public suspend fun live(profileId: String, config: TeamConfig) {
        val run = run(profileId, config)
        while (true) {
            if (run.owner.tryLock()) {
                try {
                    connect(run)
                } finally {
                    run.gateway?.close()
                    run.gateway = null
                    run.state.update { it.copy(stream = FleetStream.Closed) }
                    run.owner.unlock()
                }
                return
            }
            delay(JOIN_POLL_MS)
        }
    }

    private suspend fun connect(run: Run) {
        while (true) {
            val seenRetry = run.retries.value
            run.state.update { it.copy(phase = if (it.hasData) it.phase else FleetPhase.Probing, failure = null) }
            val verdict = FleetProbe.probe(transports, run.config.url, run.config.city.ifEmpty { null }, run.config.hostMode)
            when (verdict) {
                is FleetVerdict.Refused -> {
                    log.i { "team host not found: ${verdict.failure}" }
                    run.state.update { it.copy(phase = FleetPhase.Failed, failure = verdict.failure, stream = FleetStream.Closed) }
                    // Dart: a failed probe stays failed until the person asks again.
                    run.retries.first { it != seenRetry }
                }
                is FleetVerdict.Found -> {
                    val gateway = FleetGateway(transports.open(verdict.supervisorUrl), verdict.city, verdict.capabilities, verdict.host.front, ::now)
                    run.gateway = gateway
                    run.state.update {
                        it.copy(phase = FleetPhase.Ready, failure = null, host = verdict.host, capabilities = verdict.capabilities, stream = FleetStream.Connecting)
                    }
                    follow(run, gateway)
                    return
                }
            }
        }
    }

    /** Dart `_subscribe` + `OrchestrationSseClient`: read everything, then follow the stream until cancelled. */
    private suspend fun follow(run: Run, gateway: FleetGateway): Unit = coroutineScope {
        markAll(run)
        readDirty(run)
        // Dart `_scheduleRefetch`: events only mark scopes; one reader waits [debounceMs] after the first and reads them
        // all. A kick that comes while it reads is kept, so nothing marked is ever left unread.
        val kicks = Channel<Unit>(Channel.CONFLATED)
        launch {
            for (kick in kicks) {
                delay(debounceMs)
                readDirty(run)
            }
        }
        var attempt = 0
        var lastId: String? = null
        while (true) {
            var live = false
            val end =
                gateway.events(lastId) { frame ->
                    if (!live) {
                        live = true
                        attempt = 0
                        run.state.update { it.copy(stream = FleetStream.Live) }
                    }
                    val signal = FleetSignal.of(frame)
                    signal.seq?.let { lastId = it.toString() }
                    if (signal is FleetSignal.Changed) {
                        val scopes = signal.scopes.toMutableSet()
                        // A session bead (its id is the session id) is the only sign of a stopped pool session.
                        val bead = signal.beadId
                        if (bead != null && run.state.value.snapshot.agents.any { it.id == bead || it.sessionId == bead }) scopes += FleetScope.Agents
                        run.dirtyLock.withLock { run.dirty += scopes }
                        kicks.trySend(Unit)
                    }
                }
            log.i { "team stream ended: ${end::class.simpleName}" }
            run.state.update { it.copy(stream = FleetStream.Reconnecting) }
            delay(backoff(attempt++))
            // Reconcile by refetch: whatever happened while the stream was down is read again.
            markAll(run)
            readDirty(run)
        }
    }

    /** Dart `backoffDelay` without the jitter: base doubled per attempt, capped. */
    private fun backoff(attempt: Int): Long {
        val shift = attempt.coerceIn(0, 20)
        return (backoffBaseMs shl shift).coerceAtMost(backoffCapMs)
    }

    private suspend fun markAll(run: Run) = run.dirtyLock.withLock { run.dirty += FleetScope.entries }

    /**
     * Dart `_refetchNow`: reads only the stale scopes, one read at a time; each scope that fails keeps its last value
     * and marks the read failed. Runs, work and gates come from one read of the runs and the work.
     */
    private suspend fun readDirty(run: Run) {
        val gateway = run.gateway ?: return
        run.reading.withLock {
            val scopes = run.dirtyLock.withLock { run.dirty.toSet().also { run.dirty.clear() } }
            if (scopes.isEmpty()) return
            var next = run.state.value.snapshot
            var succeeded = false
            var failed = false

            suspend fun <T> scope(load: suspend () -> T, fold: (FleetSnapshot, T) -> FleetSnapshot) {
                try {
                    next = fold(next, load())
                    succeeded = true
                } catch (_: FleetHttpError) {
                    failed = true
                } catch (_: FleetUnreachableError) {
                    failed = true
                }
            }

            if (FleetScope.Projects in scopes) scope({ gateway.projects() }) { s, v -> s.copy(projects = v) }
            if (FleetScope.Runs in scopes || FleetScope.Work in scopes || FleetScope.Gates in scopes) {
                scope({
                    val read = gateway.runsAndWork()
                    read to FleetMap.gates(gateway.withQuestions(read.pending), read.beads, read.runs)
                }) { s, (read, gates) -> s.copy(runs = read.runs, work = read.work, gates = gates) }
            }
            if (FleetScope.Agents in scopes) scope({ gateway.agents() }) { s, v -> s.copy(agents = v) }
            // `/usage` is optional on older hosts: a refusal leaves it unknown, never zero.
            if (FleetScope.Usage in scopes) {
                try {
                    val usage = gateway.usage()
                    next = next.copy(usage = usage)
                } catch (_: FleetHttpError) {
                    Unit
                } catch (_: FleetUnreachableError) {
                    failed = true
                }
            }
            val snapshot = next
            run.state.update {
                it.copy(
                    snapshot = snapshot,
                    hasData = it.hasData || succeeded,
                    refreshedAtMs = if (succeeded) now() else it.refreshedAtMs,
                    readFailed = failed,
                )
            }
        }
    }

    /**
     * Reads every scope now (the Refresh action). After a failed probe it looks for the team again instead; with no
     * page watching nothing happens.
     */
    public suspend fun refresh(profileId: String) {
        val run = runs.load()[profileId] ?: return
        if (run.state.value.phase == FleetPhase.Failed || run.gateway == null) {
            run.retries.update { it + 1 }
            return
        }
        markAll(run)
        readDirty(run)
        if (run.state.value.jobs != null) loadJobs(profileId)
    }

    /** Dart `scheduledJobs()`: the host's scheduled jobs into [FleetState.jobs]; a refusal keeps the last list. */
    public suspend fun loadJobs(profileId: String) {
        val run = runs.load()[profileId] ?: return
        val gateway = run.gateway ?: return
        if (!gateway.capabilities.scheduledJobs) return
        val jobs = quiet { gateway.scheduledJobs() } ?: return
        run.state.update { it.copy(jobs = jobs) }
    }

    /**
     * Dart `OrchestrationPolicyGateway.policy`: the host front's read-only supervision policy for [projectId] (a rig), or
     * the host's default rig when null. Null when the host has no such route (no front), the read failed, or no team is
     * connected. Never throws.
     */
    public suspend fun policy(profileId: String, projectId: String? = null): FleetPolicy? {
        val gateway = runs.load()[profileId]?.gateway ?: return null
        return quiet { gateway.policy(projectId) }
    }

    /** What a merge readiness read found (Dart `mergeReadiness` + `mergeReadinessError`). */
    public sealed interface MergeRead {
        /** The host's document. */
        public data class Found(val readiness: MergeReadiness) : MergeRead

        /** The host has no merge roles for this run (no front, 404 or 422), or no team is connected. */
        public data object NoRoles : MergeRead

        /** The read failed: the host refused or did not answer. */
        public data object Failed : MergeRead
    }

    /** Dart `mergeReadiness`: reads the front's readiness document for [runId] now. Never throws. */
    public suspend fun mergeReadiness(profileId: String, runId: String): MergeRead {
        val gateway = runs.load()[profileId]?.gateway ?: return MergeRead.NoRoles
        if (!gateway.capabilities.mergeReadiness) return MergeRead.NoRoles
        return try {
            gateway.mergeReadiness(runId)?.let { MergeRead.Found(it) } ?: MergeRead.NoRoles
        } catch (_: FleetHttpError) {
            MergeRead.Failed
        } catch (_: FleetUnreachableError) {
            MergeRead.Failed
        }
    }

    /** Dart `approveMergeRequest`: approves the merge request [mergeRequestId]. Front only; one receipt, never re-sent. */
    public suspend fun approveMerge(profileId: String, mergeRequestId: String): FleetMergeResult = mergeWrite(profileId) { gateway, id -> gateway.approveMerge(mergeRequestId, id) }

    /** Dart `mergeRun`: merges [runId] into the rig's default branch. Only a person's confirmed tap calls this. Front only. */
    public suspend fun merge(profileId: String, runId: String): FleetMergeResult = mergeWrite(profileId) { gateway, id -> gateway.merge(runId, id) }

    private suspend fun mergeWrite(profileId: String, send: suspend (FleetGateway, String) -> FleetMergeResult): FleetMergeResult {
        val requestId = newRequestId()
        val run = runs.load()[profileId]
        val gateway = run?.gateway
        if (run == null || gateway == null) return FleetMergeResult(FleetReceipt.rejected(requestId, FleetReceipt.FRONT_REQUIRED))
        run.state.update { it.copy(sending = it.sending + requestId) }
        val result =
            try {
                send(gateway, requestId)
            } catch (e: CancellationException) {
                run.state.update { it.copy(sending = it.sending - requestId) }
                throw e
            }
        run.state.update { it.copy(sending = it.sending - requestId, receipts = it.receipts + (requestId to result.receipt)) }
        if (result.receipt.status != ReceiptStatus.Rejected) {
            markAll(run)
            readDirty(run)
        }
        return result
    }

    /**
     * Sends one write (Dart `mutate`): refused without a request when the capability is off or no team is connected,
     * else one call whose receipt lands in [FleetState.receipts]; the team is read again after it. Never re-sent.
     */
    public suspend fun control(profileId: String, control: FleetControl): FleetReceipt {
        val requestId = newRequestId()
        val run = runs.load()[profileId]
        val gateway = run?.gateway
        if (run == null || gateway == null) return FleetReceipt.rejected(requestId, FleetReceipt.FRONT_REQUIRED)
        run.state.update { it.copy(sending = it.sending + requestId) }
        val receipt =
            try {
                val snapshot = run.state.value.snapshot
                gateway.control(control, requestId, snapshot.agents, snapshot.runs, snapshot.gates)
            } catch (e: CancellationException) {
                run.state.update { it.copy(sending = it.sending - requestId) }
                throw e
            }
        run.state.update { it.copy(sending = it.sending - requestId, receipts = it.receipts + (requestId to receipt)) }
        if (receipt.status != ReceiptStatus.Rejected) {
            markAll(run)
            readDirty(run)
            if (control is FleetControl.Job) loadJobs(profileId)
        }
        return receipt
    }

    /** Forgets [profileId]'s team (turned off or deleted): the next page starts from nothing. */
    public fun forget(profileId: String) {
        flow(profileId).value = FleetState()
        while (true) {
            val current = runs.load()
            if (current[profileId] == null || runs.compareAndSet(current, current.remove(profileId))) return
        }
    }

    /** Unique per write, the `X-GC-Request` and idempotency key (Dart `mintMutationKey`). */
    private class Ids {
        private val prefix = kotlin.random.Random.nextLong().toULong().toString(36)
        private val serial = kotlin.concurrent.atomics.AtomicInt(0)

        fun next(): String = "app-$prefix-${serial.fetchAndAdd(1)}"
    }

    public companion object {
        /** Dart `staleAfter`. */
        public const val STALE_AFTER_MS: Long = 60_000

        /** How often a second watcher checks whether the first one left. */
        private const val JOIN_POLL_MS: Long = 1_000
    }
}
