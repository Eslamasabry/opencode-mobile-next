package io.github.eslamasabry.oc.engine.transcript

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.ServerPage
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.engine.transcript.internal.RowBuilder
import io.github.eslamasabry.oc.engine.transcript.internal.TranscriptModel
import io.github.eslamasabry.oc.engine.transcript.internal.VersionFence
import io.github.eslamasabry.oc.engine.transcript.internal.mergeHydrated
import io.github.eslamasabry.oc.engine.transcript.state.OlderState
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptPhase
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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

/** Why a recent-history re-read was asked for (plan 09 §3.4 step 4). Counted, never shown. */
public enum class RefreshReason {
    Resume, InboxDelivered, SkillChanged, Stale, Reconnected, RunEndedAfterDrop, LateEdit, ShellChanged, HistoryReset,
    DeferredOverflow,
}

/** One page of history, oldest first within the page (`ServerGateway.messagePage`). A seam so tests script reads. */
public fun interface HistorySource {
    public suspend fun page(sessionId: String, cursor: String?, limit: Int): Outcome<ServerPage<MessageWithParts>>
}

/** Unit-level counters (plan 09 §3.11 / §14.2). Plain increments on the lane, per handle so tests never share them. */
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
    public var rowsReplaced: Int = 0
        internal set
    public var skippedWhileHidden: Int = 0
        internal set

    /** Recent-history reads asked for (stale, reconnect after a drop, late edit, deferred overflow …). */
    public var refreshRequests: Int = 0
        internal set

    /** History pages actually read through the gateway (newest page and older pages). */
    public var historyReads: Int = 0
        internal set
}

/**
 * One open transcript (plan 09 §3.1). Every event, history result and command is applied on [lane] in arrival order;
 * a history read suspends off the lane and is fenced by load generation and the event versions at its start.
 */
public class TranscriptHandle internal constructor(
    public val sessionId: String,
    private val lane: CoroutineDispatcher,
    parent: CoroutineScope,
    private val nowMs: () -> Long,
    private val history: HistorySource,
    events: Flow<GatewayEvent>? = null,
    private val release: () -> Unit = {},
    autoLoad: Boolean = true,
) : AutoCloseable {
    private val scope = CoroutineScope(parent.coroutineContext + SupervisorJob(parent.coroutineContext[Job]) + lane)
    public val counters: TranscriptCounters = TranscriptCounters()
    private val model = TranscriptModel(sessionId, counters, ::refreshWanted)
    private val rows = RowBuilder(model, counters)
    private val publisher = Publisher(scope, nowMs, ::publishNow)
    private val _state = MutableStateFlow(TranscriptState.Empty)
    private var hiddenDirty = false

    private var phase: TranscriptPhase = TranscriptPhase.Loading
    private var older: OlderState = OlderState.None
    private var olderCursor: String? = null
    private val usedOlderCursors = HashSet<String>()
    private var loadGeneration = 0L
    private var loading = false
    private var loadingOlder = false
    private var resetOnLoad = false
    private var refreshJob: Job? = null
    private var refreshPending = false
    private val withdrawn = HashSet<String>()

    /** Newest-first rows. Derivation runs on the lane, never on the main thread. */
    public val state: StateFlow<TranscriptState> = _state.asStateFlow()

    init {
        // Nobody watching [new, §3.5]: keep applying, skip derivation; the first subscriber gets one full derive.
        scope.launch {
            _state.subscriptionCount.collect { n ->
                if (n > 0 && hiddenDirty) {
                    hiddenDirty = false
                    publishNow()
                }
            }
        }
        if (events != null) scope.launch { events.collect { applyOne(it) } }
        if (autoLoad) scope.launch { load(reset = false) }
    }

    /** Queues one event on the lane, in arrival order (tests and fixture replays; production reads the gateway). */
    public fun submit(event: GatewayEvent) {
        scope.launch { applyOne(event) }
    }

    /** Applies every event of [events] on the lane, in order, until the flow ends. */
    public suspend fun consume(events: Flow<GatewayEvent>) {
        withContext(lane) { events.collect { applyOne(it) } }
    }

    /** Re-reads the newest page soon (debounced 150 ms, map CHAT-GENUI-03). Never a skeleton or an error over rows. */
    public fun refreshRecent(reason: RefreshReason) {
        scope.launch { refreshWanted(reason) }
    }

    /** Reads the newest page now. [reset] drops older pages, cursors and deferred maps first (§3.3 history resets). */
    public suspend fun reload(reset: Boolean): Outcome<Unit> = withContext(lane) { load(reset) }

    /** Reads the page before the oldest loaded one (§3.4 step 3). A reused cursor or HTTP 400/410 → "Reload". */
    public suspend fun loadOlder(): Outcome<Unit> =
        withContext(lane) {
            if (older == OlderState.NeedsReload || resetOnLoad) return@withContext load(reset = true)
            val cursor = olderCursor ?: return@withContext Outcome.Ok(Unit)
            if (loading || loadingOlder) return@withContext Outcome.Ok(Unit)
            val generation = ++loadGeneration
            val versionAtStart = model.eventVersion
            loadingOlder = true
            older = OlderState.Loading
            publisher.changed(ChangeKind.UserAction)
            counters.historyReads++
            val result = history.page(sessionId, cursor, PAGE)
            if (generation != loadGeneration) return@withContext Outcome.Ok(Unit) // fenced: a newer load owns the state
            loadingOlder = false
            when (result) {
                is Outcome.Ok -> {
                    val next = result.value.nextCursor
                    if (next != null && (next == cursor || next in usedOlderCursors)) {
                        older = OlderState.NeedsReload // an expired cursor never loops (chat_history.dart:262)
                    } else {
                        adopt(result.value.items, versionAtStart, prefix = emptyList(), preserveUnseen = true)
                        usedOlderCursors += cursor
                        olderCursor = next
                        older = if (next != null) OlderState.Available else OlderState.Exhausted
                        model.olderCursorKnown = next != null
                    }
                }
                is Outcome.Failed -> {
                    val f = result.failure
                    older =
                        if (f is Failure.Net.Rejected && (f.status == 400 || f.status == 410)) {
                            OlderState.NeedsReload
                        } else {
                            OlderState.Available
                        }
                }
            }
            publisher.changed(ChangeKind.UserAction)
            afterLoad()
            when (result) {
                is Outcome.Ok -> Outcome.Ok(Unit)
                is Outcome.Failed -> result
            }
        }

    /** Local removal after a confirmed server delete (§6.6). */
    public fun removeMessage(id: String) {
        scope.launch {
            withdrawn += id
            if (model.removeMessage(id)) publisher.changed(ChangeKind.UserAction)
        }
    }

    /** Waits until the lane has applied and published everything queued so far. Test and benchmark helper. */
    public suspend fun awaitIdle() {
        withContext(lane) { }
        publisher.awaitIdle()
    }

    private fun applyOne(event: GatewayEvent) {
        if (!model.apply(event)) return
        if (model.deleted) phase = TranscriptPhase.Failed(Failure.Net.NotFound())
        publisher.changed(ChangeKind.Stream)
    }

    // ------------------------------------------------------------------------------------------------ history

    private fun refreshWanted(reason: RefreshReason) {
        counters.refreshRequests++
        if (reason == RefreshReason.HistoryReset) resetOnLoad = true
        if (loading || loadingOlder) {
            refreshPending = true
            return
        }
        refreshJob?.cancel()
        refreshJob =
            scope.launch {
                // Map CHAT-GENUI-03 (`_scheduleRecentHistoryRefresh`): bursts of reasons share one read after 150 ms.
                delay(REFRESH_DEBOUNCE_MS)
                refreshJob = null
                load(reset = false)
            }
    }

    private fun afterLoad() {
        if (refreshPending) {
            refreshPending = false
            refreshWanted(RefreshReason.Stale)
            counters.refreshRequests-- // the re-armed read is the same request, counted once
        }
    }

    private suspend fun load(reset: Boolean): Outcome<Unit> {
        val generation = ++loadGeneration
        val versionAtStart = model.eventVersion
        if (reset) resetOnLoad = true
        loading = true
        loadingOlder = false
        counters.historyReads++
        val result = history.page(sessionId, null, PAGE)
        if (generation != loadGeneration) return Outcome.Ok(Unit) // a newer load owns the state; this one is dropped
        loading = false
        when (result) {
            is Outcome.Ok -> {
                val page = result.value
                val current = model.snapshots()
                val incoming = page.items.mapTo(HashSet()) { it.info.id }
                val overlap = current.indexOfFirst { it.info.id in incoming }
                val hasMore = page.nextCursor != null
                val retainPrefix = !resetOnLoad && hasMore && overlap >= 0
                if (resetOnLoad) {
                    model.clearDeferred()
                    rows.reset()
                }
                val prefix = if (retainPrefix) current.subList(0, overlap) else emptyList()
                adopt(page.items, versionAtStart, prefix, preserveUnseen = false, base = current)
                if (!retainPrefix) {
                    olderCursor = page.nextCursor
                    usedOlderCursors.clear()
                    older = if (page.nextCursor != null) OlderState.Available else OlderState.None
                }
                model.olderCursorKnown = olderCursor != null
                resetOnLoad = false
                phase = if (model.deleted) phase else TranscriptPhase.Live
            }
            is Outcome.Failed -> {
                // A refresh never shows an error over a transcript already on screen (map CHAT-GENUI-54).
                if (!model.historyLoaded && model.messages.isEmpty()) phase = TranscriptPhase.Failed(result.failure)
            }
        }
        publisher.changed(ChangeKind.UserAction)
        afterLoad()
        return when (result) {
            is Outcome.Ok -> Outcome.Ok(Unit)
            is Outcome.Failed -> result
        }
    }

    private fun adopt(
        items: List<MessageWithParts>,
        versionAtStart: Long,
        prefix: List<MessageWithParts>,
        preserveUnseen: Boolean,
        base: List<MessageWithParts> = model.snapshots(),
    ) {
        val fence =
            object : VersionFence {
                override fun message(id: String) = model.messageVersion(id)

                override fun part(messageId: String, partId: String) = model.partVersion(messageId, partId)

                override fun deferredParts(messageId: String): Map<String, Part>? = model.takeDeferredParts(messageId)

                override fun deferredMessage(messageId: String): MessageInfo? = model.takeDeferredMessage(messageId)
            }
        val merged = mergeHydrated(base, items, versionAtStart, fence, prefix, preserveUnseen, withdrawn)
        model.adoptHydrated(merged, items.mapTo(HashSet()) { it.info.id }, versionAtStart)
    }

    // ------------------------------------------------------------------------------------------------ publish

    private fun publishNow() {
        if (_state.subscriptionCount.value == 0 && _state.value.revision > 0) {
            counters.skippedWhileHidden++
            hiddenDirty = true
            return
        }
        counters.publishes++
        _state.value = rows.derive(_state.value.revision + 1, phase, older)
    }

    /** Lets go of this handle (ref-counted by the engine; the last holder starts the 60 s close). */
    override fun close() {
        release()
    }

    /** Ends the handle's work. Called by the engine, or by a test that built the handle directly. */
    internal fun dispose() {
        scope.cancel()
    }

    internal companion object {
        const val PAGE = 100
        const val REFRESH_DEBOUNCE_MS = 150L
    }
}

/** What caused a change: user actions publish at once; stream changes share the 50 ms window (§3.5). */
internal enum class ChangeKind { Stream, UserAction }

/**
 * Plan 09 §3.5 / ADR §9.3, matching Flutter `_scheduleStreamFlush` (lib/ui/screens/chat/chat_stream.dart):
 * - leading edge: the first change after ≥ 50 ms of quiet publishes on the lane's next turn, after the events already
 *   queued (Flutter's microtask flush), so a synchronous burst costs one publish and the first delta is never late
 *   (slice-0 P3 D9, plan 09 §14.2);
 * - trailing edge: changes inside the 50 ms window publish once at its end;
 * - user actions (loads, removals) publish at once.
 * Result: ≤ 1 stream publish per 50 ms per handle (B-CHAT-096, PublishCountTest).
 */
internal class Publisher(
    private val scope: CoroutineScope,
    private val nowMs: () -> Long,
    private val publish: () -> Unit,
) {
    /** Null = never published; a sentinel number would overflow `now - last`. */
    private var lastPublishMs: Long? = null
    private var pending: Job? = null
    private val idle = MutableStateFlow(true)

    fun changed(kind: ChangeKind) {
        val now = nowMs()
        if (kind == ChangeKind.UserAction) {
            pending?.cancel()
            pending = null
            publishAt(now)
            idle.value = true
            return
        }
        if (pending != null) return
        val last = lastPublishMs
        val wait = if (last == null) 0L else (WINDOW_MS - (now - last)).coerceIn(0L, WINDOW_MS)
        idle.value = false
        pending =
            scope.launch {
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
