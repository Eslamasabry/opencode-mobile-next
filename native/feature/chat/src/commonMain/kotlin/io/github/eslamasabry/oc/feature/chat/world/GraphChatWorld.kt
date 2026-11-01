package io.github.eslamasabry.oc.feature.chat.world

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.ConnectionScope
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.ServerOperationsGateway
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent as CoreEvent
import io.github.eslamasabry.oc.core.model.OutgoingPrompt
import io.github.eslamasabry.oc.core.model.PromptAdmission
import io.github.eslamasabry.oc.core.model.PromptDelivery
import io.github.eslamasabry.oc.core.model.SessionStatus
import io.github.eslamasabry.oc.core.model.StreamStatus
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.transcript.TranscriptHandle
import io.github.eslamasabry.oc.engine.transcript.state.GatewayEvent
import io.github.eslamasabry.oc.engine.transcript.state.PromptRow
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptState
import io.github.eslamasabry.oc.engine.transcript.transcript
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/** The chat reads its conversation's gateways from that conversation's own graph (ADR §9.1). */
@ContributesTo(ConnectionScope::class)
public interface ChatGraphAccessors {
    public val chatGateway: ServerGateway
    public val chatOperations: ServerOperationsGateway
}

/**
 * One [ChatWorld] per open conversation, shared by the chat entry and its composer, released when the last holder
 * leaves (plan 09 §6.3: the App-scoped composer slot reaches Connection-scoped engines through the registry).
 */
@SingleIn(AppScope::class)
@Inject
@ContributesBinding(AppScope::class)
public class GraphChatWorlds(private val registry: ConnectionRegistry) : ChatWorlds {
    private val open = HashMap<ConversationRef, Pair<GraphChatWorld, Int>>()

    override fun open(ref: ConversationRef): ChatWorld {
        val (world, holders) = open[ref] ?: (GraphChatWorld(ref, registry) to 0)
        open[ref] = world to holders + 1
        if (holders == 0) world.attach()
        return world
    }

    override fun release(world: ChatWorld) {
        val (held, holders) = open[world.ref] ?: return
        if (held !== world) return
        if (holders <= 1) {
            open.remove(world.ref)
            held.detach()
        } else {
            open[world.ref] = held to holders - 1
        }
    }
}

/**
 * The production [ChatWorld] for slice 2. The transcript is `:engine:transcript`'s handle; everything the plan gives
 * to engines that are still being built is bound here to the gateway with the plan's own rules, and each binding
 * names its replacement:
 * - history and events → the handle through [P3Bridge] (S2-TX: the handle reads them itself);
 * - [send], [pending], [retrySend] → bubble first, one dispatch, a lost answer is Unconfirmed and never resent on its
 *   own (S2-DLV `PromptDelivery`, with receipts, steering and the offline queue);
 * - [draft] / [saveDraft] → in memory, saving refused: slice 2 has no drafts (plan 09 §6.3; S3 `Drafts`);
 * - [markRead] → nothing to write yet (the connection engine's `ReadState`, B-CHAT-012).
 * Main-thread state; gateway calls suspend on the caller's dispatcher and the gateway moves its own I/O.
 */
internal class GraphChatWorld(override val ref: ConversationRef, private val registry: ConnectionRegistry) : ChatWorld {
    private val mutableTranscript = MutableStateFlow(TranscriptState.Empty)
    private val mutableLoad = MutableStateFlow<ChatLoad>(ChatLoad.Loading)
    private val mutableTitle = MutableStateFlow<String?>(null)
    private val mutableCaps = MutableStateFlow(ServerCapabilities())
    private val mutableLink = MutableStateFlow(ChatLink.Connecting)
    private val mutablePending = MutableStateFlow<ImmutableList<PendingSend>>(persistentListOf())
    private val mutableDraft = MutableStateFlow(DraftFacts())

    override val transcript: StateFlow<TranscriptState> = mutableTranscript.asStateFlow()
    override val load: StateFlow<ChatLoad> = mutableLoad.asStateFlow()
    override val title: StateFlow<String?> = mutableTitle.asStateFlow()
    override val capabilities: StateFlow<ServerCapabilities> = mutableCaps.asStateFlow()
    override val link: StateFlow<ChatLink> = mutableLink.asStateFlow()
    override val pending: StateFlow<ImmutableList<PendingSend>> = mutablePending.asStateFlow()
    override val draft: StateFlow<DraftFacts> = mutableDraft.asStateFlow()

    private var attached: Attached? = null
    private var nextLocalId = 1L

    /** The handle, its graph and the collectors feeding it. Replaced as a whole on reload. */
    private class Attached(val graph: ConnectionGraph, val gateway: ServerGateway, val handle: TranscriptHandle, val stop: () -> Unit)

    override val work: CoroutineContext
        get() = attached?.graph?.scope?.coroutineContext?.get(ContinuationInterceptor) ?: EmptyCoroutineContext

    private val sid: SessionId get() = ref.sessionId

    /** Opens the transcript on the conversation's live graph, or reports that the connection is not live. */
    fun attach() {
        detach()
        val graph = registry.graphFor(ref)
        if (graph == null) {
            mutableLink.value = ChatLink.Offline
            mutableLoad.value = ChatLoad.Failed(Failure.Network(NetworkWhy.Offline))
            return
        }
        val gateway = (graph as ChatGraphAccessors).chatGateway
        val handle = graph.transcript.open(sid.raw)
        val bridge = P3Bridge(sid.raw)
        val scope = graph.scope
        var refreshes = handle.refreshRequests
        var connects = gateway.events.connects.value
        mutableLoad.value = ChatLoad.Loading
        val jobs =
            listOf(
                scope.launch {
                    handle.state.collect { s ->
                        mutableTranscript.value = s
                        settlePending(s)
                        // The reducer asks for a re-read after a drop, a stale hint or an overflow (reconcile by refetch, R20).
                        if (handle.refreshRequests != refreshes) {
                            refreshes = handle.refreshRequests
                            readHistory(gateway, handle, bridge)
                        }
                    }
                },
                scope.launch {
                    gateway.events.all.collect { envelope ->
                        val event = envelope.event
                        if (event is CoreEvent.SessionChanged && event.sessionId == sid && event.title != null) mutableTitle.value = event.title
                        bridge.event(event)?.let(handle::submit)
                    }
                },
                scope.launch { gateway.events.status.collect { mutableLink.value = linkOf(it) } },
                scope.launch {
                    gateway.events.connects.collect { n ->
                        if (n > connects) handle.submit(GatewayEvent.Connected)
                        connects = n
                    }
                },
                scope.launch { gateway.capabilities.collect { mutableCaps.value = it } },
                scope.launch {
                    (gateway.session(sid) as? Outcome.Ok)?.value?.title?.let { mutableTitle.value = it }
                    readHistory(gateway, handle, bridge)
                    val statuses = gateway.sessionStatuses()
                    if (statuses is Outcome.Ok && statuses.value[sid] == SessionStatus.Busy) handle.submit(GatewayEvent.RunStarted(sid.raw))
                },
            )
        attached = Attached(graph, gateway, handle) { jobs.forEach { it.cancel() } }
    }

    fun detach() {
        val a = attached ?: return
        attached = null
        a.stop()
        a.handle.close()
    }

    private suspend fun readHistory(gateway: ServerGateway, handle: TranscriptHandle, bridge: P3Bridge): Outcome<Unit> =
        when (val page = gateway.messagePage(sid, limit = HISTORY_PAGE)) {
            is Outcome.Ok -> {
                handle.loadHistory(page.value.items.map(bridge::message))
                mutableLoad.value = ChatLoad.Live
                Outcome.Ok(Unit)
            }
            is Outcome.Failed -> {
                if (mutableLoad.value !is ChatLoad.Live) mutableLoad.value = ChatLoad.Failed(page.failure)
                page
            }
        }

    // ---- Drafts (slice 2: none stored) ----

    override fun draftChanged(text: String) {
        mutableDraft.update { it.copy(text = text) }
    }

    override suspend fun saveDraft(): Outcome<Unit> = Outcome.Failed(Failure.Unavailable(FeatureId("drafts")))

    // ---- Sends (bubble first, never twice) ----

    override suspend fun send(text: String): Outcome<Unit> {
        val id = nextLocalId++
        mutablePending.update { (it + PendingSend(id, text, PendingState.Sending)).toImmutableList() }
        return dispatch(id, text)
    }

    override suspend fun retrySend(localId: Long): Outcome<Unit> {
        val bubble = mutablePending.value.firstOrNull { it.localId == localId && it.state == PendingState.Failed } ?: return Outcome.Ok(Unit)
        setPending(localId) { it.copy(state = PendingState.Sending, failure = null) }
        return dispatch(localId, bubble.text)
    }

    private suspend fun dispatch(localId: Long, text: String): Outcome<Unit> {
        val gateway = attached?.gateway ?: return fail(localId, Failure.Network(NetworkWhy.Offline))
        // Steer, never queue, where steering exists (plan 09 §4.3): a send during a run joins it.
        val steer = mutableTranscript.value.run.busy && mutableCaps.value.promptSteering
        val prompt = OutgoingPrompt(text, delivery = if (steer) PromptDelivery.Steer else null)
        return when (val r = gateway.prompt(sid, prompt)) {
            is Outcome.Ok ->
                when (r.value) {
                    is PromptAdmission.Confirmed -> Outcome.Ok(Unit) // the bubble leaves when the prompt row arrives
                    PromptAdmission.Uncertain -> {
                        setPending(localId) { it.copy(state = PendingState.Unconfirmed) }
                        Outcome.Ok(Unit)
                    }
                }
            is Outcome.Failed -> fail(localId, r.failure)
        }
    }

    private fun fail(localId: Long, failure: Failure): Outcome<Unit> {
        setPending(localId) { it.copy(state = PendingState.Failed, failure = failure) }
        return Outcome.Failed(failure)
    }

    private fun setPending(localId: Long, change: (PendingSend) -> PendingSend) {
        mutablePending.update { list -> list.map { if (it.localId == localId) change(it) else it }.toImmutableList() }
    }

    /** A sending bubble is settled by the newest prompt row with its words (plan 09 §4.6, newest turn only). */
    private fun settlePending(s: TranscriptState) {
        val sending = mutablePending.value
        if (sending.isEmpty()) return
        val newest = s.rows.firstOrNull { it is PromptRow } as? PromptRow ?: return
        val match = sending.firstOrNull { it.state != PendingState.Failed && it.text.trim() == newest.text.trim() } ?: return
        mutablePending.update { list -> list.filterNot { it.localId == match.localId }.toImmutableList() }
    }

    // ---- Commands ----

    override suspend fun stop(): Outcome<Unit> {
        val gateway = attached?.gateway ?: return Outcome.Failed(Failure.Network(NetworkWhy.Offline))
        return when (val r = gateway.stop(sid)) {
            is Outcome.Ok -> Outcome.Ok(Unit)
            is Outcome.Failed -> r
        }
    }

    override suspend fun reload(): Outcome<Unit> {
        attach()
        return when (val l = mutableLoad.value) {
            is ChatLoad.Failed -> Outcome.Failed(l.failure)
            else -> Outcome.Ok(Unit)
        }
    }

    override suspend fun fork(from: MessageId): Outcome<SessionId> {
        val graph = attached?.graph ?: return Outcome.Failed(Failure.Network(NetworkWhy.Offline))
        return (graph as ChatGraphAccessors).chatOperations.forkSession(sid, from)
    }

    /** Plan 09 §6.6: delete on the server, then read the conversation again from scratch (P3 has no removeMessage). */
    override suspend fun deleteMessage(id: MessageId): Outcome<Unit> {
        val graph = attached?.graph ?: return Outcome.Failed(Failure.Network(NetworkWhy.Offline))
        val r = (graph as ChatGraphAccessors).chatOperations.deleteMessage(sid, id)
        if (r is Outcome.Ok) attach()
        return r
    }

    override fun markRead(): Unit = Unit

    private fun linkOf(s: StreamStatus): ChatLink =
        when (s) {
            StreamStatus.Connected -> ChatLink.Live
            StreamStatus.Connecting, is StreamStatus.Reconnecting, StreamStatus.Suspended -> ChatLink.Connecting
            is StreamStatus.Disconnected, StreamStatus.WaitingForNetwork, is StreamStatus.Stopped -> ChatLink.Offline
        }

    private companion object {
        const val HISTORY_PAGE = 100
    }
}
