package io.github.eslamasabry.oc.engine.analytics

import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.attempt
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.time.Clock

/** One payload a Remote sink sent, for "What was sent" (plan 16 §5.2). [json] is exactly the body sent. */
public data class SentPayload(val atMs: Long, val sinkId: SinkId, val json: String)

/** What one upload run did. */
public data class UploadReport(val sentEvents: Int, val batches: Int, val held: HoldReason?)

/** The analytics API emitters see (plan 16 §5.2). */
public interface Analytics {
    /** Never throws, never blocks, never suspends. Drops the event when its tier is not permitted. */
    public fun track(event: AnalyticsEvent)

    /** Adds one to today's BASIC `daily_use` count. Same guarantees as [track]. */
    public fun count(counter: UsageCounter)

    public val consent: StateFlow<ConsentState>

    /** Granting turns DETAILED on; declining also drops every queued DETAILED event at once. */
    public suspend fun setDetailedConsent(granted: Boolean): Outcome<Unit>

    /** Newest first, at most 50. Only bodies that left the phone (a Remote sink). */
    public val sent: StateFlow<ImmutableList<SentPayload>>

    public val whatWeCollect: StateFlow<WhatWeCollect>
}

/** Tunables (plan 16 §5.1); tests shrink them. */
public data class AnalyticsConfig(
    val foregroundUploadEveryMs: Long = 30 * 60_000L,
    val maxBatchesPerRun: Int = 4,
    val sentLogSize: Int = 50,
    val inboxLimit: Int = 1_024,
)

/**
 * The analytics core. One serial lane (an inbox consumed by a single coroutine): every command, event and upload
 * runs there in order, so the gate, consent and queue are never raced. `track` and `count` only enqueue.
 *
 * Lifecycle: [start] after the first frame; [openGate] once the migration gate opens (events before it are dropped,
 * plan 16 §5.6 rule 1); [updateConditions] whenever connectivity or foreground state changes (moving to the
 * background triggers an upload); a foreground timer uploads every 30 minutes.
 * TODO(app-wiring): Metro binding (`@SingleIn(AppScope::class)`) and the `AppBuildInfo` → [SystemProps] mapping
 * are wired by `:androidApp` / `:desktopApp`.
 */
public class AnalyticsEngine(
    private val scope: CoroutineScope,
    dispatchers: AppDispatchers,
    private val clock: Clock,
    private val store: AnalyticsStore,
    private val sink: AnalyticsSink,
    private val system: SystemProps,
    private val ids: IdSource = SecureIdSource,
    private val config: AnalyticsConfig = AnalyticsConfig(),
) : Analytics {
    private val lane = dispatchers.serial("analytics")
    private val inbox = Channel<Command>(Channel.UNLIMITED)
    private val pending = AtomicInt(0)
    private val started = AtomicBoolean(false)
    private val log = AppLog.tagged("analytics")

    // Lane-confined state.
    private val queue = EventQueue()
    private val sessions = SessionTracker(ids)
    private var restoredSession: String? = null
    private var gateOpen = false
    private var install: InstallIdRecord? = null
    private var nextSeq = 0L
    private var backoff = Backoff()
    private var ledger: DailyLedger? = null
    private var conditions = DeviceConditions.Unknown

    private val _consent = MutableStateFlow(ConsentState())
    override val consent: StateFlow<ConsentState> = _consent.asStateFlow()

    private val _sent = MutableStateFlow<ImmutableList<SentPayload>>(persistentListOf())
    override val sent: StateFlow<ImmutableList<SentPayload>> = _sent.asStateFlow()

    private val _queued = MutableStateFlow(0)

    /** Events waiting in the queue. */
    public val queued: StateFlow<Int> = _queued.asStateFlow()

    private val _lastHold = MutableStateFlow<HoldReason?>(null)

    /** Why the last upload attempt waited, or null if it ran. For diagnostics only. */
    public val lastHold: StateFlow<HoldReason?> = _lastHold.asStateFlow()

    private val _dropped = MutableStateFlow(DropCounts())

    /** Counts of events dropped and why: for diagnostics and tests, never sent. */
    public val dropped: StateFlow<DropCounts> = _dropped.asStateFlow()

    private val collect = MutableStateFlow(WhatWeCollectSource.build(ConsentState(), sink))
    override val whatWeCollect: StateFlow<WhatWeCollect> = collect.asStateFlow()

    // ------------------------------------------------------------------ emitter API (non-blocking)

    override fun track(event: AnalyticsEvent) {
        offer(Command.Track(event, nowMs()))
    }

    override fun count(counter: UsageCounter) {
        offer(Command.Count(counter, nowMs()))
    }

    /** The latest number of servers and agents per kind; summarised once per day as BASIC counts. */
    public fun noteInventory(servers: Map<ServerKind, Int>, agents: Map<AgentKind, Int>) {
        offer(Command.Inventory(servers.toMap(), agents.toMap()))
    }

    public fun updateConditions(next: DeviceConditions) {
        offer(Command.Conditions(next))
    }

    // ------------------------------------------------------------------ commands

    /** Loads stored state and starts the lane and the foreground timer. Idempotent. */
    public fun start() {
        if (!started.compareAndSet(expectedValue = false, newValue = true)) return
        scope.launch(lane) {
            load()
            for (command in inbox) {
                if (command is Command.Track || command is Command.Count) pending.decrementAndFetch()
                handle(command)
            }
        }
        scope.launch(lane) {
            while (true) {
                delay(config.foregroundUploadEveryMs)
                if (conditions.foreground) offer(Command.Upload(null))
            }
        }
    }

    public suspend fun openGate(): Outcome<Unit> = ask { Command.OpenGate(it) }

    override suspend fun setDetailedConsent(granted: Boolean): Outcome<Unit> = ask { Command.SetConsent(granted, it) }

    public suspend fun uploadNow(): Outcome<UploadReport> = ask { Command.Upload(it) }

    /** Manage space › Delete everything: the queue, the sent log, the ledger, the install id and the consent answer. */
    public suspend fun deleteEverything(): Outcome<Unit> = ask { Command.DeleteEverything(it) }

    // ------------------------------------------------------------------ lane

    private fun offer(command: Command) {
        if (command is Command.Track || command is Command.Count) {
            if (pending.incrementAndFetch() > config.inboxLimit) {
                pending.decrementAndFetch()
                bumpDrop { it.copy(overflow = it.overflow + 1) }
                return
            }
        }
        inbox.trySend(command)
    }

    private suspend fun <T> ask(build: (CompletableDeferred<Outcome<T>>) -> Command): Outcome<T> {
        val reply = CompletableDeferred<Outcome<T>>()
        inbox.trySend(build(reply))
        return reply.await()
    }

    private suspend fun handle(command: Command) {
        val outcome = attempt(::unexpected) { dispatch(command) }
        if (outcome is Outcome.Failed) {
            log.w { "command ${command::class.simpleName} failed: ${outcome.failure::class.simpleName}" }
            command.reply?.complete(outcome)
        }
    }

    private suspend fun dispatch(command: Command) {
        val now = nowMs()
        when (command) {
            is Command.Track -> accept(command.event, command.atMs)
            is Command.Count -> {
                if (!gateOpen) return bumpDrop { it.copy(beforeGate = it.beforeGate + 1) }
                roll(command.atMs)
                ledger = ledger?.count(command.counter)
                persist()
            }
            is Command.Inventory -> {
                roll(now)
                ledger = ledger?.copy(servers = command.servers, agents = command.agents)
                persist()
            }
            is Command.Conditions -> {
                val wasForeground = conditions.foreground
                conditions = command.conditions
                if (wasForeground && !conditions.foreground) upload()
            }
            is Command.OpenGate -> {
                gateOpen = true
                roll(now)
                command.done.complete(Outcome.Ok(Unit))
            }
            is Command.SetConsent -> {
                val next = ConsentState(if (command.granted) DetailedConsent.Granted else DetailedConsent.Declined)
                _consent.value = next
                if (!next.detailedOn) queue.dropTier(AnalyticsTier.Detailed)
                persist()
                publish()
                command.done.complete(Outcome.Ok(Unit))
            }
            is Command.Upload -> {
                val report = upload()
                command.done?.complete(Outcome.Ok(report))
            }
            is Command.DeleteEverything -> {
                queue.clear()
                _sent.value = persistentListOf()
                ledger = DailyLedger(DailyLedger.dayOf(now))
                install = null
                backoff = Backoff()
                sessions.forget()
                _consent.value = ConsentState()
                store.delete()
                publish()
                command.done.complete(Outcome.Ok(Unit))
            }
        }
    }

    /** The privacy gate: migration gate, tier vs consent, typed schema check, dedupe, full wire scrub, then queue. */
    private suspend fun accept(event: AnalyticsEvent, atMs: Long) {
        if (!gateOpen) return bumpDrop { it.copy(beforeGate = it.beforeGate + 1) }
        if (!_consent.value.permits(event.spec.tier)) return bumpDrop { it.copy(notConsented = it.notConsented + 1) }
        val typed = PrivacyScrubber.checkTyped(event)
        if (typed is ScrubVerdict.Rejected) return reject(typed)
        roll(atMs)
        if (event is BackendConnected) {
            val today = ledger ?: return
            if (event.kind in today.connectedKinds) return bumpDrop { it.copy(duplicate = it.duplicate + 1) }
            ledger = today.copy(connectedKinds = today.connectedKinds + event.kind)
        }
        enqueue(event, atMs)
        persist()
    }

    private fun enqueue(event: AnalyticsEvent, atMs: Long) {
        val record = InstallIdPolicy.current(install, atMs, ids).also { install = it }
        val values = event.props()
        val queued =
            QueuedEvent(
                seq = nextSeq++,
                tier = event.spec.tier,
                atMs = atMs,
                name = event.spec.name,
                hourUtc = truncateToHour(atMs),
                install = record.id,
                system = system,
                props = event.spec.props.associate { it.key to values.getValue(it.key).wire },
            )
        val verdict = PrivacyScrubber.check(queued.toWire(sessions.sessionAt(atMs)))
        if (verdict is ScrubVerdict.Rejected) return reject(verdict)
        queue.add(queued)
        publish()
    }

    /** Closes the previous UTC day into its summary events (BASIC, so always permitted once the gate is open). */
    private fun roll(atMs: Long) {
        val day = DailyLedger.dayOf(atMs)
        val current = ledger
        if (current == null) {
            ledger = DailyLedger(day)
            return
        }
        if (day <= current.day) return
        ledger = current.next(day)
        if (gateOpen) current.summary().forEach { enqueue(it, atMs) }
    }

    private suspend fun upload(): UploadReport {
        val now = nowMs()
        queue.expire(now)
        var sentEvents = 0
        var batches = 0
        var held: HoldReason? = null
        while (batches < config.maxBatchesPerRun) {
            val decision = UploadPolicy.decide(conditions, sink.kind, gateOpen, queue.size, backoff, now)
            if (decision is UploadDecision.Hold) {
                held = decision.reason.takeUnless { it == HoldReason.NothingQueued && batches > 0 }
                break
            }
            val batch = queue.batch(EventQueue.MAX_BATCH)
            val clean =
                batch
                    .map { it to it.toWire(sessionFor(it)) }
                    .filter { (_, wire) -> PrivacyScrubber.check(wire) == ScrubVerdict.Clean }
            val rejected = batch.size - clean.size
            if (rejected > 0) bumpDrop { it.copy(rejected = it.rejected + rejected) }
            if (clean.isEmpty()) {
                queue.remove(batch.map { it.seq }.toSet())
                continue
            }
            val result = attempt(::unexpected) { sink.send(WireBatch(clean.map { it.second })) }.let { it.flatten() }
            when (result) {
                is Outcome.Ok -> {
                    queue.remove(batch.map { it.seq }.toSet())
                    backoff = Backoff()
                    sentEvents += clean.size
                    batches++
                    if (sink.kind == SinkKind.Remote) {
                        val payload = SentPayload(now, sink.id, result.value.body)
                        _sent.value = (listOf(payload) + _sent.value).take(config.sentLogSize).toImmutableList()
                    }
                }
                is Outcome.Failed -> {
                    backoff = backoff.failed(now)
                    // Category only (plan 16 §5.6 rule 6): never the failure's text.
                    log.w { "upload failed: ${result.failure::class.simpleName}, attempt ${backoff.failures}" }
                    held = HoldReason.BackingOff
                    break
                }
            }
        }
        _lastHold.value = held
        persist()
        publish()
        return UploadReport(sentEvents, batches, held)
    }

    private fun sessionFor(event: QueuedEvent): String =
        if (event.seq < firstLiveSeq) {
            restoredSession ?: ids.newId().also { restoredSession = it }
        } else {
            sessions.sessionAt(event.atMs.coerceAtLeast(0))
        }

    private var firstLiveSeq = 0L

    private suspend fun load() {
        val text = attempt(::unexpected) { store.read() }
        val state =
            (text as? Outcome.Ok)?.value?.let { raw ->
                val decoded = attempt(::unexpected) { WireCodec.json.decodeFromString(PersistedState.serializer(), raw) }
                (decoded as? Outcome.Ok)?.value?.takeIf { it.version == PersistedState.VERSION }
            }
        if (state == null && text is Outcome.Ok && text.value != null) {
            log.w { "stored analytics state unreadable; starting empty" }
            attempt(::unexpected) { store.delete() }
        }
        val loaded = state ?: PersistedState()
        _consent.value = loaded.consent
        install = loaded.install
        nextSeq = loaded.nextSeq
        firstLiveSeq = loaded.nextSeq
        backoff = loaded.backoff
        ledger = loaded.ledger
        // Stored events are re-checked: a store edited on disk cannot smuggle content into a batch.
        queue.restore(loaded.queue.filter { PrivacyScrubber.check(it.toWire(PLACEHOLDER_ID)) == ScrubVerdict.Clean })
        publish()
    }

    private suspend fun persist() {
        val state =
            PersistedState(
                consent = _consent.value,
                install = install,
                nextSeq = nextSeq,
                // With no destination nothing piles up on disk: the queue lives in memory only (plan 16 §5.5 None).
                queue = if (sink.kind == SinkKind.None) emptyList() else queue.snapshot(),
                backoff = backoff,
                ledger = ledger,
            )
        val text = WireCodec.json.encodeToString(PersistedState.serializer(), state)
        val written = attempt(::unexpected) { store.write(text) }
        if (written is Outcome.Failed) log.w { "analytics state not saved: ${written.failure::class.simpleName}" }
    }

    private fun publish() {
        _queued.value = queue.size
        collect.value = WhatWeCollectSource.build(_consent.value, sink, queue.size)
    }

    private fun reject(verdict: ScrubVerdict.Rejected) {
        bumpDrop { it.copy(rejected = it.rejected + 1) }
        log.w { "event rejected: ${verdict.reason} in ${verdict.field}${verdict.leak?.let { " ($it)" }.orEmpty()}" }
    }

    private fun bumpDrop(change: (DropCounts) -> DropCounts) {
        while (true) {
            val current = _dropped.value
            if (_dropped.compareAndSet(current, change(current))) return
        }
    }

    private fun nowMs(): Long = clock.now().toEpochMilliseconds()

    private fun unexpected(error: Throwable): Failure = Failure.Unexpected(Redacted.shape(error))

    private sealed interface Command {
        val reply: CompletableDeferred<Outcome<Nothing>>? get() = null

        data class Track(val event: AnalyticsEvent, val atMs: Long) : Command

        data class Count(val counter: UsageCounter, val atMs: Long) : Command

        data class Inventory(val servers: Map<ServerKind, Int>, val agents: Map<AgentKind, Int>) : Command

        data class Conditions(val conditions: DeviceConditions) : Command

        class OpenGate(val done: CompletableDeferred<Outcome<Unit>>) : Command {
            override val reply: CompletableDeferred<Outcome<Nothing>> get() = cast(done)
        }

        class SetConsent(val granted: Boolean, val done: CompletableDeferred<Outcome<Unit>>) : Command {
            override val reply: CompletableDeferred<Outcome<Nothing>> get() = cast(done)
        }

        class Upload(val done: CompletableDeferred<Outcome<UploadReport>>?) : Command {
            override val reply: CompletableDeferred<Outcome<Nothing>>? get() = done?.let(::cast)
        }

        class DeleteEverything(val done: CompletableDeferred<Outcome<Unit>>) : Command {
            override val reply: CompletableDeferred<Outcome<Nothing>> get() = cast(done)
        }
    }

    private companion object {
        const val PLACEHOLDER_ID = "00000000000000000000000000000000"

        /** A failed reply carries only [Outcome.Failed], which is an Outcome of any T. */
        @Suppress("UNCHECKED_CAST")
        fun cast(d: CompletableDeferred<*>): CompletableDeferred<Outcome<Nothing>> = d as CompletableDeferred<Outcome<Nothing>>

        fun Outcome<Outcome<Delivery>>.flatten(): Outcome<Delivery> =
            when (this) {
                is Outcome.Ok -> value
                is Outcome.Failed -> this
            }
    }
}

/** Why events were dropped (diagnostics and tests). */
public data class DropCounts(
    val beforeGate: Int = 0,
    val notConsented: Int = 0,
    val rejected: Int = 0,
    val duplicate: Int = 0,
    val overflow: Int = 0,
)
