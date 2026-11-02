package io.github.eslamasabry.oc.engine.transcript

import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.ConnectionCoroutineScope
import io.github.eslamasabry.oc.core.common.ConnectionScope
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.transcript.internal.TranscriptModel
import io.github.eslamasabry.oc.engine.transcript.state.GatewayEvent
import io.github.eslamasabry.oc.engine.transcript.state.MessageWithParts
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptState
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The connection-scoped transcript engine (plan 09 §3). P3 form: it reduces gateway events into an
 * immutable, newest-first row list on one serial lane per handle, and publishes at most once per 50
 * ms. History reads through the gateway, the LRU of warm handles, passes beyond turn grouping,
 * search and the stall tracker are slice 2 (S2-TX).
 */
@SingleIn(ConnectionScope::class)
@Inject
public class TranscriptEngine(
    private val gateway: ServerGateway,
    private val dispatchers: AppDispatchers,
    private val clock: Clock,
    private val scope: ConnectionCoroutineScope,
) {
    /** P1 seam kept: the S4 accessor proof reads a session through the engine. */
    public suspend fun session(id: SessionId): Outcome<Session> = gateway.session(id)

    /**
     * Opens a transcript for one session. Events for other sessions are dropped without a publish.
     */
    public fun open(sessionId: String): TranscriptHandle =
        TranscriptHandle(
            sessionId = sessionId,
            lane = dispatchers.serial("transcript:$sessionId"),
            parent = scope,
            nowMs = { clock.now().toEpochMilliseconds() },
        )
}

/** Unit-level counters (plan 09 §3.11 / §14.2). Per handle, so tests never share them. */
public class TranscriptCounters {
    public var publishes: Int = 0
        internal set

    public var derives: Int = 0
        internal set

    public var displayPassMessages: Int = 0
        internal set

    public var markdownParses: Int = 0
        internal set

    public var tailReparses: Int = 0
        internal set

    public var skippedWhileHidden: Int = 0
        internal set

    /**
     * Characters copied into tail block rows (STREAM-FIX: one chunk per publish at most, whatever
     * the reply length).
     */
    public var tailChars: Long = 0
        internal set

    /**
     * Rows written into the published list: removed plus added (STREAM-FIX: the changed suffix of
     * the newest turn).
     */
    public var rowWrites: Long = 0
        internal set
}

public class TranscriptHandle
internal constructor(
    sessionId: String,
    private val lane: CoroutineDispatcher,
    parent: CoroutineScope,
    private val nowMs: () -> Long,
) : AutoCloseable {
    private val scope =
        CoroutineScope(parent.coroutineContext + Job(parent.coroutineContext[Job]) + lane)
    public val counters: TranscriptCounters = TranscriptCounters()
    private val model = TranscriptModel(sessionId, counters)
    private val publisher = Publisher(scope, nowMs, ::publishNow)
    private val _state = MutableStateFlow(TranscriptState.Empty)
    private var hiddenDirty = false

    /** Newest-first rows. Derivation runs on the lane, never on the main thread. */
    public val state: StateFlow<TranscriptState> = _state.asStateFlow()

    /**
     * Recent-history reads the model asked for (stale, reconnect after a drop, deferred overflow).
     */
    public val refreshRequests: Int
        get() = model.refreshRequests

    private val refreshSignal = MutableStateFlow(0)
    public val refreshes: StateFlow<Int> = refreshSignal.asStateFlow()

    public suspend fun historyVersion(): Long = withContext(lane) { model.version }

    /** Admission snapshots the reducer, including events not yet flushed to the display. */
    public suspend fun messageIds(): Set<String> = withContext(lane) { model.messageIds }

    init {
        // Nobody watching [new, §3.5]: keep applying, skip derivation; the first subscriber gets
        // one full derive.
        scope.launch {
            _state.subscriptionCount.collect { n ->
                if (n > 0 && hiddenDirty) {
                    hiddenDirty = false
                    publishNow()
                }
            }
        }
    }

    /** Queues one event on the lane, in arrival order. */
    public fun submit(event: GatewayEvent) {
        scope.launch { applyOne(event) }
    }

    /** Applies every event of [events] on the lane, in order, until the flow ends. */
    public suspend fun consume(events: Flow<GatewayEvent>) {
        withContext(lane) { events.collect { applyOne(it) } }
    }

    /**
     * A history page, oldest first: loaded rows never animate. Publishes at once (a user-visible
     * load).
     */
    public suspend fun loadHistory(
        page: List<MessageWithParts>,
        beforeVersion: Long = Long.MAX_VALUE,
    ) {
        withContext(lane) {
            model.loadHistory(page, beforeVersion)
            publisher.changed(userAction = true)
        }
    }

    /**
     * Waits until the lane has applied and published everything queued so far. Test and benchmark
     * helper.
     */
    public suspend fun awaitIdle() {
        withContext(lane) {}
        publisher.awaitIdle()
    }

    private fun applyOne(event: GatewayEvent) {
        val changed = model.apply(event)
        refreshSignal.value = model.refreshRequests
        if (changed) publisher.changed(userAction = false)
    }

    private fun publishNow() {
        if (_state.subscriptionCount.value == 0 && _state.value.revision > 0) {
            counters.skippedWhileHidden++
            hiddenDirty = true
            return
        }
        counters.publishes++
        _state.value = model.derive(_state.value.revision + 1)
    }

    override fun close() {
        scope.cancel()
    }
}

/**
 * Plan 09 §3.5 / ADR §9.3, matching Flutter `_scheduleStreamFlush`
 * (lib/ui/screens/chat/chat_stream.dart):
 * - leading edge: the first change after ≥ 50 ms of quiet publishes on the lane's next turn, after
 *   the events already queued (Flutter's microtask flush), so a synchronous burst costs one publish
 *   and the first delta is never late;
 * - trailing edge: changes inside the 50 ms window publish once at its end. Result: ≤ 1 publish per
 *   50 ms per handle (B-CHAT-096, PublishCountTest).
 */
internal class Publisher(
    private val scope: CoroutineScope,
    private val nowMs: () -> Long,
    private val publish: () -> Unit,
) {
    private var lastPublishMs = Long.MIN_VALUE
    private var pending: Job? = null
    private val idle = MutableStateFlow(true)

    fun changed(userAction: Boolean) {
        val now = nowMs()
        if (userAction) {
            pending?.cancel()
            pending = null
            publishAt(now)
            return
        }
        if (pending != null) return
        val wait =
            if (lastPublishMs == Long.MIN_VALUE) 0L
            else (lastPublishMs + WINDOW_MS - now).coerceAtLeast(0L)
        idle.value = false
        pending = scope.launch {
            // B-CHAT-096: the 50 ms coalescing window of the Flutter stream flush (plan 09 §3.5).
            if (wait > 0) delay(wait)
            pending = null
            publishAt(nowMs())
            idle.value = pending == null
        }
    }

    private fun publishAt(now: Long) {
        lastPublishMs = now
        publish()
    }

    suspend fun awaitIdle() {
        idle.filter { it }.first()
    }

    companion object {
        const val WINDOW_MS = 50L
    }
}

@ContributesTo(ConnectionScope::class)
public interface TranscriptAccessors {
    public val transcriptEngine: TranscriptEngine
}

public val ConnectionGraph.transcript: TranscriptEngine
    get() = (this as TranscriptAccessors).transcriptEngine
