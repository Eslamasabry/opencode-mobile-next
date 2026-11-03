package io.github.eslamasabry.oc.feature.chat.world

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.BusyKind
import io.github.eslamasabry.oc.core.common.ConnectionScope
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.ServerOperationsGateway
import io.github.eslamasabry.oc.core.domain.SessionSelectionGateway
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent as CoreEvent
import io.github.eslamasabry.oc.core.domain.facet
import io.github.eslamasabry.oc.core.model.ChatDefaults
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.ModelSelection
import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.core.model.SessionStatus
import io.github.eslamasabry.oc.core.model.StreamStatus
import io.github.eslamasabry.oc.engine.composer.drafts.ConversationDrafts
import io.github.eslamasabry.oc.engine.composer.input.Mentions
import io.github.eslamasabry.oc.engine.composer.drafts.DraftSession
import io.github.eslamasabry.oc.engine.composer.drafts.DraftSubmission
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.delivery.ConnectionDelivery
import io.github.eslamasabry.oc.engine.delivery.LocalSendId
import io.github.eslamasabry.oc.engine.delivery.SendOutcome
import io.github.eslamasabry.oc.engine.delivery.SendPhase
import io.github.eslamasabry.oc.engine.delivery.StopAnswer
import io.github.eslamasabry.oc.engine.requests.ConnectionRequests
import io.github.eslamasabry.oc.engine.transcript.TranscriptHandle
import io.github.eslamasabry.oc.engine.transcript.state.GatewayEvent
import io.github.eslamasabry.oc.engine.transcript.state.PromptRow
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptState
import io.github.eslamasabry.oc.engine.transcript.transcript
import io.github.eslamasabry.oc.feature.chat.requests.presentation.ChatRequestsModel
import io.github.eslamasabry.oc.feature.chat.requests.presentation.RequestPlace
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The chat reads its conversation's gateways from that conversation's own graph (ADR §9.1). */
@ContributesTo(ConnectionScope::class)
public interface ChatGraphAccessors {
    public val chatDelivery: ConnectionDelivery
    public val chatRequests: ConnectionRequests
    public val chatGateway: ServerGateway
    public val chatOperations: ServerOperationsGateway
}

/**
 * Shares one world between the entry and composer. Pending recovery and unsaved drafts survive the
 * last entry; their transport work belongs to the connection, while display collectors stop when
 * the entry leaves.
 */
@ContributesTo(AppScope::class)
public interface ChatDraftBindings {
    @Multibinds(allowEmpty = true) public val conversationDrafts: Set<ConversationDrafts>

    @Multibinds(allowEmpty = true) public val recentModelsBackings: Set<RecentModelsBacking>
}

/** Flutter `ModelLibrary.recentLimit`. */
private const val SAVED_RECENT_MODELS = 8

@SingleIn(AppScope::class)
@Inject
@ContributesBinding(AppScope::class)
public class GraphChatWorlds(
    private val registry: ConnectionRegistry,
    private val draftStores: Set<ConversationDrafts> = emptySet(),
    private val recentBackings: Set<RecentModelsBacking> = emptySet(),
) : ChatWorlds {
    /** B3: shared by every conversation of the app run; B-STATE-032: saved per server (8 newest) when the app binds a store. */
    private val recentModels =
        recentBackings.singleOrNull()?.let { RecentModels(max = SAVED_RECENT_MODELS, backing = it) } ?: RecentModels()

    init {
        check(draftStores.size <= 1) { "multiple conversation draft stores" }
        check(recentBackings.size <= 1) { "multiple recent model stores" }
    }

    private val open = HashMap<ConversationRef, Pair<GraphChatWorld, Int>>()

    override fun open(ref: ConversationRef): ChatWorld {
        val (world, holders) =
            open[ref] ?: (GraphChatWorld(ref, registry, draftStores.singleOrNull(), recentModels) to 0)
        open[ref] = world to holders + 1
        if (holders == 0) world.attach()
        return world
    }

    override fun release(world: ChatWorld) {
        val (held, holders) = open[world.ref] ?: return
        if (held !== world) return
        if (holders <= 1) {
            held.detach()
            if (held.pending.value.isEmpty() && !held.draft.value.unsaved) {
                held.disposeDraftDisplay()
                open.remove(world.ref)
            } else open[world.ref] = held to 0
        } else {
            open[world.ref] = held to holders - 1
        }
    }
}

/**
 * The feature binding of connection-owned engines. Entry disposal stops display work, never
 * accepted delivery. History merges against the reducer version captured before I/O; every mutation
 * fences the registry generation. Draft saving remains unavailable until the adopted drafts store
 * is connected; send admission requires storage.
 */
internal class GraphChatWorld(
    override val ref: ConversationRef,
    private val registry: ConnectionRegistry,
    private val draftStore: ConversationDrafts? = null,
    private val recentModels: RecentModels = RecentModels(),
) : ChatWorld, TurnFeedSource {
    // CHAT-TRANSCRIPT: full messages, run and retry facts for the turn facts (steps, results, context).
    private val feedKeeper = TurnFeedKeeper(ref.sessionId.raw)
    override val turnFeed: StateFlow<TurnFeed> = feedKeeper.feed
    private var draftSession = draftStore?.open(ref)
    private val mutableTranscript = MutableStateFlow(TranscriptState.Empty)
    private val mutableLoad = MutableStateFlow<ChatLoad>(ChatLoad.Loading)
    private val mutableTitle = MutableStateFlow<String?>(null)
    private val mutableCaps = MutableStateFlow(ServerCapabilities())
    private val mutableLink = MutableStateFlow(ChatLink.Connecting)
    private val mutablePending = MutableStateFlow<ImmutableList<PendingSend>>(persistentListOf())
    private val mutableDraft = MutableStateFlow(DraftFacts(ready = draftSession == null))
    private var protectedDraftText: String? = null
    /** The files that were protected with [protectedDraftText]; null = any (history matches). */
    private var protectedFiles: List<PromptAttachment>? = null
    private var protectedByHistory = false
    private var checkedRestoreRevision: Long? = null
    private var historyReconciled = false
    private val knownUserPrompts = HashSet<String>()
    private val draftSubmissions = HashMap<Long, DraftSubmission>()
    private val draftAcknowledgements = HashMap<Long, () -> Unit>()
    private var stopDraftDisplay: (() -> Unit)? = null
    private val mutableModels = MutableStateFlow(ComposerModels())
    private var catalogDefaults: ChatDefaults? = null
    /** Bumped by every verified selection: a load that began earlier never publishes its choice. */
    private var selectionEpoch = 0L
    /** Bumped whenever the draft owner is replaced: a delayed pick for the old one writes nothing. */
    private var draftOwnerGeneration = 0L
    override val draftGeneration: Long
        get() = draftOwnerGeneration
    /** Server user messages of this conversation ("prompt:<id>"), with or without words. */
    private val userMessageKeys = LinkedHashSet<String>()

    /** The immutable files/model one bubble dispatched; a retry reuses exactly these. */
    private class SentSnapshot(
        val files: ImmutableList<PromptAttachment>,
        val model: ModelSelection?,
    )

    private val sentSnapshots = HashMap<Long, SentSnapshot>()

    /** Visible sub-agents of the loaded catalog: an `@name` of one rides the prompt as a mention (Codex composer #6). */
    private var subagentNames: List<String> = emptyList()

    override val transcript: StateFlow<TranscriptState> = mutableTranscript.asStateFlow()
    override val load: StateFlow<ChatLoad> = mutableLoad.asStateFlow()
    override val title: StateFlow<String?> = mutableTitle.asStateFlow()
    override val capabilities: StateFlow<ServerCapabilities> = mutableCaps.asStateFlow()
    override val link: StateFlow<ChatLink> = mutableLink.asStateFlow()
    override val pending: StateFlow<ImmutableList<PendingSend>> = mutablePending.asStateFlow()
    override val draft: StateFlow<DraftFacts> = mutableDraft.asStateFlow()
    override val models: StateFlow<ComposerModels> = mutableModels.asStateFlow()
    private val mutableCommands = MutableStateFlow<List<ComposerCommand>>(emptyList())
    override val commands: StateFlow<List<ComposerCommand>> = mutableCommands.asStateFlow()

    private var attached: Attached? = null
    private var stopWatching: (() -> Unit)? = null
    private val mutableOlder = MutableStateFlow(false)
    override val hasOlder: StateFlow<Boolean> = mutableOlder
    private var nextCursor: String? = null
    private val baselines = HashMap<Long, Set<String>>()
    private val canonical = HashMap<Long, String>()
    private val admitted = HashSet<Long>()
    /** Local ids the server accepted during one [syncDelivery] pass (B11). */
    private val delivered = LinkedHashSet<Long>()
    private val settled = HashSet<String>()
    private var nextLocalId = 1L
    private val deliveryIds = HashMap<Long, LocalSendId>()
    private val recoveryIds = HashMap<String, Long>()
    private val mutableRequests = MutableStateFlow<ChatRequestsModel?>(null)
    override val requests: StateFlow<ChatRequestsModel?> = mutableRequests

    /** The handle, its graph and the collectors feeding it. Replaced as a whole on reload. */
    private class Attached(
        val graph: ConnectionGraph,
        val gateway: ServerGateway,
        val handle: TranscriptHandle,
        val stop: () -> Unit,
    )

    override val work: CoroutineContext
        get() =
            attached?.graph?.scope?.coroutineContext?.get(ContinuationInterceptor)
                ?: EmptyCoroutineContext

    private val sid: SessionId
        get() = ref.sessionId

    init {
        draftSession?.let(::observeDraftSession)
    }

    private fun observeDraftSession(session: DraftSession) {
        stopDraftDisplay?.invoke()
        val draftDisplayJob =
            registry.appScope.launch(registry.mainContext) {
                session.state.collect { saved ->
                    mutableDraft.value =
                        DraftFacts(
                            saved.text,
                            saved.saved,
                            saved.ready,
                            saved.failure,
                            saved.restoreRevision,
                            editRevision = saved.editRevision,
                            attachments = saved.attachments,
                            savedAttachments = saved.savedAttachments,
                        )
                    refreshDraftProtection()
                }
            }
        stopDraftDisplay = { draftDisplayJob.cancel() }
    }

    internal fun disposeDraftDisplay() {
        draftSession?.close()
        stopDraftDisplay?.invoke()
        stopDraftDisplay = null
    }

    private fun refreshDraftProtection() {
        val current = mutableDraft.value
        if (
            current.ready && historyReconciled && checkedRestoreRevision != current.restoreRevision
        ) {
            checkedRestoreRevision = current.restoreRevision
            val restored = current.text.trim()
            // An unconfirmed send is the same send only with the same words AND the same files
            // (complete payload identity, not names).
            val samePending =
                mutablePending.value.any {
                    it.state == PendingState.Unconfirmed &&
                        it.text.trim() == restored &&
                        sentFiles(it.localId) == current.attachments
                }
            // History rows carry no file identity: an equal history prompt stays protected (an
            // explicit repeat remains possible).
            val inHistory = restored in knownUserPrompts
            if (restored.isNotEmpty() && current.text == current.saved && (inHistory || samePending)) {
                protectedDraftText = current.text
                protectedFiles = current.attachments
                protectedByHistory = inHistory && !samePending
            }
        }
        if (
            protectedDraftText?.trim()?.isNotEmpty() != true &&
                current.text.trim().isEmpty() &&
                current.text == current.saved &&
                current.attachments.isNotEmpty() &&
                unconfirmedFileOnly(current.attachments)
        ) {
            // A file-only draft equal to an unconfirmed dispatched send — here or after a restart —
            // is one accidental tap from a duplicate; it has no text token to compare, so the file
            // names decide. Removing or changing a file re-arms nothing.
            protectedDraftText = current.text
            protectedFiles = current.attachments
            protectedByHistory = false
        }
        // Codex composer #1 / QA wave 4 N5: while a bubble with exactly these words and files is on its way or
        // unconfirmed, a second Send would dispatch the same message again. (A not-sent twin is retired when the box
        // sends it again, so a Failed bubble never blocks.)
        val pendingTwin =
            (current.text.isNotBlank() || current.attachments.isNotEmpty()) &&
                mutablePending.value.any {
                    it.state != PendingState.Failed &&
                        it.text.trim() == current.text.trim() &&
                        sentFiles(it.localId) == current.attachments
                }
        val blocked =
            pendingTwin ||
                protectedDraftText?.let { it.trim() == current.text.trim() } == true &&
                protectedFiles.let { it == null || it == current.attachments }
        val repeat =
            blocked &&
                protectedByHistory &&
                current.ready &&
                current.failure == null &&
                mutablePending.value.none { it.state == PendingState.Unconfirmed }
        if (current.sendBlocked != blocked || current.repeatAllowed != repeat)
            mutableDraft.value = current.copy(sendBlocked = blocked, repeatAllowed = repeat)
    }

    /** A blank-text unconfirmed bubble with exactly [files] (every byte, not only names). */
    private fun unconfirmedFileOnly(files: List<PromptAttachment>): Boolean =
        mutablePending.value.any { bubble ->
            bubble.state == PendingState.Unconfirmed &&
                bubble.text.isBlank() &&
                sentFiles(bubble.localId) == files
        }

    private fun sentFiles(localId: Long): List<PromptAttachment> =
        sentSnapshots[localId]?.files.orEmpty()

    private fun acknowledgedDraft(localId: Long) {
        val submitted = draftSubmissions.remove(localId) ?: return
        val session = draftSession ?: return
        registry.appScope.launch(registry.mainContext) {
            if (session.clearAfterSend(submitted) is Outcome.Failed) {
                protectedDraftText = submitted.text
                protectedFiles = submitted.attachments
                protectedByHistory = false
                refreshDraftProtection()
            }
        }
    }

    /** Acknowledgement lives beyond an entry, but every terminal outcome ends this collector. */
    private fun watchDraftAcknowledgement(
        localId: Long,
        id: LocalSendId,
        delivery: ConnectionDelivery,
    ) {
        if (localId !in draftSubmissions) return
        val engine = delivery.engine ?: return
        draftAcknowledgements.remove(localId)?.invoke()
        val expectedGraph = registry.graphFor(ref)
        var finished = false
        var stop: (() -> Unit)? = null
        val acknowledgement =
            registry.appScope.launch(registry.mainContext) {
                try {
                    val accepted =
                        combine(engine.pending, engine.outcomes, registry.live) {
                                pending,
                                outcomes,
                                live ->
                                when {
                                    pending.any {
                                        it.localId == id && it.phase == SendPhase.Accepted
                                    } ||
                                        outcomes.any {
                                            it is SendOutcome.Accepted &&
                                                it.ref == ref &&
                                                it.localId == id
                                        } -> true
                                    outcomes.any {
                                        it is SendOutcome.Unconfirmed &&
                                            it.ref == ref &&
                                            it.localId == id
                                    } ||
                                        outcomes.any {
                                            it is SendOutcome.PutBack &&
                                                it.ref == ref &&
                                                it.localId == id
                                        } -> false
                                    live[ref.profileId] !== expectedGraph -> false
                                    else -> null
                                }
                            }
                            .first { it != null }
                    if (accepted == true) acknowledgedDraft(localId)
                } finally {
                    finished = true
                    if (draftAcknowledgements[localId] === stop)
                        draftAcknowledgements.remove(localId)
                }
            }
        val cancel = { acknowledgement.cancel() }
        stop = cancel
        if (!finished) draftAcknowledgements[localId] = cancel
    }

    /**
     * Registry observation lives in app scope: a closed graph cannot cancel its own replacement
     * observer.
     */
    fun attach() {
        if (stopWatching != null) return
        draftStore?.open(ref)?.let { reopened ->
            // Empty closed sessions can retire while this world remains for pending chat work.
            if (reopened !== draftSession) {
                draftSession = reopened
                draftOwnerGeneration++
                checkedRestoreRevision = null
                observeDraftSession(reopened)
            }
        }
        val watcher =
            registry.appScope.launch(registry.mainContext) {
                registry.live.collect {
                    val graph = registry.graphFor(ref)
                    if (
                        graph !== attached?.graph ||
                            (graph == null && mutableLink.value != ChatLink.Offline)
                    )
                        bind(graph)
                }
            }
        stopWatching = { watcher.cancel() }
    }

    private fun bind(graph: ConnectionGraph?) {
        unbind()
        mutableCaps.value = ServerCapabilities()
        // Another server owns another catalog and selection; a stale graph publishes nothing.
        mutableModels.value = ComposerModels()
        mutableCommands.value = emptyList()
        catalogDefaults = null
        mutableTranscript.value = TranscriptState.Empty
        feedKeeper.reset()
        nextCursor = null
        mutableOlder.value = false
        historyReconciled = false
        knownUserPrompts.clear()
        if (graph == null) {
            mutableLink.value = ChatLink.Offline
            mutableLoad.value = ChatLoad.Failed(Failure.Network(NetworkWhy.Offline))
            return
        }
        val gateway = (graph as ChatGraphAccessors).chatGateway
        userMessageKeys.clear()
        // The draft records (and restores `file:` references only at) this conversation's place.
        gateway.location.value.let {
            draftSession?.bindLocation(
                io.github.eslamasabry.oc.engine.composer.drafts.DraftLocation(it.directory, it.workspace)
            )
        }
        val handle = graph.transcript.open(sid.raw)
        val bridge = P3Bridge(sid.raw)
        val scope = graph.scope
        val services = graph as ChatGraphAccessors
        val delivery = services.chatDelivery
        syncDelivery(delivery)
        services.chatRequests.watching(true)
        mutableRequests.value =
            ChatRequestsModel(
                services.chatRequests.engine,
                sid,
                gateway.capabilities,
                MutableStateFlow(RequestPlace()),
                scope,
            )
        // A restarted process shows durable possibly-sent markers, never replays them.
        for (entry in
            delivery.queue?.entries().orEmpty().filter {
                it.profileId == ref.profileId && it.sessionId == sid && it.dispatchedAt != null
            }) {
            val active =
                delivery.engine?.pending?.value.orEmpty().any {
                    it.reviewId == entry.id && it.localId in deliveryIds.values
                }
            if (!active && entry.id !in recoveryIds) {
                val id = nextLocalId++
                recoveryIds[entry.id] = id
                mutablePending.update {
                    (it + PendingSend(id, entry.text, PendingState.Unconfirmed)).toImmutableList()
                }
                // The marker keeps its dispatched files and model, so an equal draft never
                // duplicates and a settled retry needs nothing from the current composer.
                sentSnapshots[id] =
                    SentSnapshot(
                        entry.attachments,
                        entry.model?.let { ModelSelection(it, entry.variant) },
                    )
            }
        }
        var refreshes = handle.refreshes.value
        var connects = gateway.events.connects.value
        mutableLoad.value = ChatLoad.Loading
        if (connects > 0) handle.submit(GatewayEvent.Connected)
        val stops = ArrayList<() -> Unit>()
        attached = Attached(graph, gateway, handle) { stops.forEach { it() } }
        val jobs =
            listOf(
                scope.launch(registry.mainContext) {
                    services.chatRequests.engine.refresh()
                },
                scope.launch(registry.mainContext) {
                    delivery.engine?.let { engine ->
                        combine(engine.pending, engine.outcomes) { _, _ -> Unit }
                            .collect { syncDelivery(delivery) }
                    }
                },
                scope.launch(registry.mainContext) {
                    handle.state.collect { s ->
                        mutableTranscript.value = s
                        knownUserPrompts +=
                            s.rows.filterIsInstance<PromptRow>().map { it.text.trim() }
                        refreshDraftProtection()
                        delivery.running(ref, s.run.busy)
                        syncDelivery(delivery)
                        settlePending(s)
                    }
                },
                scope.launch(registry.mainContext) {
                    handle.refreshes.collect { n ->
                        if (n != refreshes) {
                            refreshes = n
                            readHistory(gateway, handle, bridge)
                        }
                    }
                },
                scope.launch(registry.mainContext) {
                    gateway.events.all.collect { envelope ->
                        val event = envelope.event
                        if (
                            event is CoreEvent.SessionChanged &&
                                event.sessionId == sid &&
                                event.title != null
                        )
                            mutableTitle.value = event.title
                        if (
                            event is CoreEvent.MessageUpserted &&
                                event.message.sessionId == sid.raw &&
                                event.message.role ==
                                    io.github.eslamasabry.oc.core.model.transcript.Role.User
                        )
                            userMessageKeys += "prompt:${event.message.id}"
                        if (event is CoreEvent.SessionSelection && event.sessionId == sid) adoptRemoteSelection(event)
                        feedKeeper.event(event, envelope.receivedAtMs)
                        bridge.event(event)?.let(handle::submit)
                    }
                },
                scope.launch(registry.mainContext) {
                    gateway.events.status.collect {
                        mutableLink.value = linkOf(it)
                        feedKeeper.link(mutableLink.value)
                    }
                },
                scope.launch(registry.mainContext) {
                    gateway.events.connects.collect { n ->
                        if (n > connects) handle.submit(GatewayEvent.Connected)
                        connects = n
                    }
                },
                scope.launch(registry.mainContext) {
                    gateway.capabilities.collect { mutableCaps.value = it }
                },
                // B13/B4 (QA wave 1): the chip names the conversation's real model from the start (not "Server
                // default" after a restart), and the picker's first open never races capability negotiation.
                scope.launch(registry.mainContext) {
                    // Unknown = not negotiated yet (its defaults read as "switching"): wait for the real flags.
                    gateway.capabilities.first {
                        it != ServerCapabilities.Unknown && it.sessionModelProviderSwitching
                    }
                    if (mutableModels.value.choices.isEmpty() && !mutableModels.value.loading) loadModels()
                },
                scope.launch(registry.mainContext) {
                    (gateway.session(sid) as? Outcome.Ok)?.value?.title?.let {
                        mutableTitle.value = it
                    }
                    val statuses = gateway.sessionStatuses()
                    if (statuses is Outcome.Ok && statuses.value[sid] == SessionStatus.Busy) {
                        handle.submit(GatewayEvent.RunStarted(sid.raw))
                        feedKeeper.running(null)
                    }
                    readHistory(gateway, handle, bridge)
                    feedKeeper.loadWindows { services.chatOperations.catalog() }
                },
            )
        jobs.forEach { job -> stops += { job.cancel() } }
    }

    fun detach() {
        stopWatching?.invoke()
        stopWatching = null
        unbind(flushHeld = true)
        draftSession?.close()
    }

    private fun currentAttached(): Attached? = attached?.takeIf {
        registry.graphFor(ref) === it.graph
    }

    private fun unbind(flushHeld: Boolean = false) {
        val a = attached ?: return
        attached = null
        if (registry.graphFor(ref) !== a.graph) {
            mutablePending.update { list ->
                list
                    .map {
                        if (it.state == PendingState.Sending)
                            it.copy(state = PendingState.Unconfirmed)
                        else it
                    }
                    .toImmutableList()
            }
            deliveryIds.clear()
            admitted.clear()
            canonical.clear()
        }
        val services = a.graph as ChatGraphAccessors
        val model = mutableRequests.value
        mutableRequests.value = null
        services.chatRequests.watching(false)
        if (flushHeld && model != null) a.graph.scope.launch(registry.mainContext) { model.leave() }
        a.stop()
        a.handle.close()
    }

    private suspend fun readHistory(
        gateway: ServerGateway,
        handle: TranscriptHandle,
        bridge: P3Bridge,
        cursor: String? = null,
    ): Outcome<Unit> {
        val version = handle.historyVersion()
        val feedVersion = feedKeeper.historyVersion()
        return when (val page = gateway.messagePage(sid, cursor = cursor, limit = HISTORY_PAGE)) {
            is Outcome.Ok -> {
                if (currentAttached()?.handle !== handle)
                    return Outcome.Failed(Failure.Network(NetworkWhy.Offline))
                handle.loadHistory(page.value.items.map(bridge::message), version)
                feedKeeper.history(page.value.items, feedVersion)
                nextCursor = page.value.nextCursor
                mutableOlder.value = nextCursor != null
                page.value.items
                    .filter { it.info.role == io.github.eslamasabry.oc.core.model.transcript.Role.User }
                    .forEach { userMessageKeys += "prompt:${it.info.id}" }
                knownUserPrompts +=
                    page.value.items
                        .filter {
                            it.info.role == io.github.eslamasabry.oc.core.model.transcript.Role.User
                        }
                        .map { message ->
                            message.parts
                                .filter {
                                    it.type ==
                                        io.github.eslamasabry.oc.core.model.transcript.PartType.Text
                                }
                                .joinToString("") { it.text.orEmpty() }
                                .trim()
                        }
                historyReconciled = true
                refreshDraftProtection()
                mutableLoad.value = ChatLoad.Live
                Outcome.Ok(Unit)
            }
            is Outcome.Failed -> {
                if (currentAttached()?.handle === handle && mutableLoad.value !is ChatLoad.Live)
                    mutableLoad.value = ChatLoad.Failed(page.failure)
                page
            }
        }
    }

    override suspend fun loadOlder(): Outcome<Unit> {
        val a = currentAttached() ?: return Outcome.Failed(Failure.Network(NetworkWhy.Offline))
        val cursor = nextCursor ?: return Outcome.Ok(Unit)
        return readHistory(a.gateway, a.handle, P3Bridge(sid.raw), cursor)
    }

    // ---- Drafts: one canonical, app-scoped text session per conversation ----

    override fun draftChanged(text: String) {
        if (protectedDraftText?.trim() != text.trim()) {
            protectedDraftText = null
            protectedFiles = null
            protectedByHistory = false
        }
        val session = draftSession
        if (session == null)
            mutableDraft.update { it.copy(text = text, editRevision = it.editRevision + 1) }
        else {
            session.changed(text)
            // The input transaction reports synchronously, even if StateFlow coalesces B -> A
            // edits.
            val current = session.state.value
            mutableDraft.value =
                DraftFacts(
                    current.text,
                    current.saved,
                    current.ready,
                    current.failure,
                    current.restoreRevision,
                    editRevision = current.editRevision,
                    attachments = current.attachments,
                    savedAttachments = current.savedAttachments,
                )
        }
        refreshDraftProtection()
    }

    override suspend fun saveDraft(): Outcome<Unit> =
        draftSession?.flush() ?: Outcome.Failed(Failure.Unavailable(FeatureId("drafts")))

    override suspend fun replaceAttachments(files: List<PromptAttachment>): Outcome<Unit> {
        val session = draftSession
            ?: return Outcome.Failed(Failure.Unavailable(FeatureId("draftAttachments")))
        val previous = mutableDraft.value.attachments
        val result = session.replaceAttachments(files)
        // Publish only the acknowledged snapshot; failures and revisions pass through untouched.
        val current = session.state.value
        if (previous != current.attachments) {
            // A different file set is a different send: the ambiguity guard re-arms.
            protectedDraftText = null
            protectedFiles = null
            protectedByHistory = false
        }
        mutableDraft.value =
            DraftFacts(
                current.text,
                current.saved,
                current.ready,
                current.failure,
                current.restoreRevision,
                editRevision = current.editRevision,
                attachments = current.attachments,
                savedAttachments = current.savedAttachments,
            )
        refreshDraftProtection()
        return result
    }

    // ---- Models: the server session is the only place a choice lives ----

    override suspend fun loadModels(): Outcome<Unit> {
        val a = currentAttached() ?: return Outcome.Failed(Failure.Network(NetworkWhy.Offline))
        if (!a.gateway.capabilities.value.sessionModelProviderSwitching)
            return Outcome.Failed(Failure.Unavailable(FeatureId("sessionModelProviderSwitching")))
        val epoch = selectionEpoch
        mutableModels.value = mutableModels.value.copy(loading = true, failure = null)
        val operations = (a.graph as ChatGraphAccessors).chatOperations
        val catalog = operations.catalog()
        if (currentAttached() !== a) return staleModels()
        if (catalog is Outcome.Failed) {
            mutableModels.value = mutableModels.value.copy(loading = false, failure = catalog.failure)
            return catalog
        }
        val defaults = operations.chatDefaults()
        if (currentAttached() !== a) return staleModels()
        val readback = a.gateway.session(sid)
        if (currentAttached() !== a) return staleModels()
        val snapshot = (catalog as Outcome.Ok).value
        val enabledProviders =
            snapshot.providers.filter { it.enabled }.mapTo(HashSet()) { it.id }
        val providerNames = snapshot.providers.associate { it.id to it.name.ifBlank { it.id } }
        val choices = ArrayList<ComposerModel>()
        val seen = HashSet<Pair<String, String>>()
        for (m in snapshot.models) {
            if (!m.enabled || m.providerId !in enabledProviders) continue
            if (!seen.add(m.providerId to m.id)) continue
            // Safe display metadata only; raw provider config never leaves the catalog.
            choices +=
                ComposerModel(
                    m.providerId,
                    m.id,
                    m.name.ifBlank { m.id },
                    m.attachments,
                    variants = m.variants.filterNot { it.disabled }.map { it.id },
                    providerName = providerNames[m.providerId] ?: m.providerId,
                )
        }
        catalogDefaults = (defaults as? Outcome.Ok)?.value
        val agents =
            snapshot.agents
                .filter { !it.hidden && it.mode != "subagent" }
                .map { ComposerAgent(it.id, it.description) }
        subagentNames = snapshot.agents.filter { !it.hidden && it.mode == "subagent" }.map { it.id }
        // What the session holds; an absent model means the server default. A failed read (of the
        // session, or of the default it falls back on) leaves the choice unknown, never guessed.
        val session = (readback as? Outcome.Ok)?.value?.selection
        val failure =
            (readback as? Outcome.Failed)?.failure
                ?: if (session?.model == null) (defaults as? Outcome.Failed)?.failure else null
        val effective = if (failure != null) null else session?.model ?: catalogDefaults?.model
        val resolved =
            effective?.let { ref ->
                choices.firstOrNull { it.providerId == ref.providerId && it.modelId == ref.modelId }
            }
        val previous = mutableModels.value
        // A choice verified while this load ran is newer than anything this load read.
        val newer = selectionEpoch != epoch
        mutableModels.value =
            ComposerModels(
                choices,
                if (newer) previous.selected else resolved,
                failure = failure,
                variant = if (newer) previous.variant else session?.variant.orEmpty(),
                agents = agents,
                agent = if (newer) previous.agent else session?.agent ?: catalogDefaults?.agent,
                usesDefault = if (newer) previous.usesDefault else session?.model == null && failure == null,
                recent = recentOf(choices),
            )
        return if (failure != null) Outcome.Failed(failure) else Outcome.Ok(Unit)
    }

    private fun recentOf(choices: List<ComposerModel>): List<ComposerModel> =
        recentModels.of(ref.profileId.raw).mapNotNull { (provider, model) ->
            choices.firstOrNull { it.providerId == provider && it.modelId == model }
        }

    /** A graph swapped mid-load owns the next load; this one publishes nothing new. */
    private fun staleModels(): Outcome<Unit> {
        mutableModels.value = mutableModels.value.copy(loading = false)
        return Outcome.Failed(STALE)
    }

    /**
     * Codex composer #4 (B-STATE-118): another client changed this session's model or agent; the chip follows the
     * server, so the next send never re-applies a stale choice. A pick of our own in flight wins (its read-back
     * publishes the server's answer).
     */
    private fun adoptRemoteSelection(event: CoreEvent.SessionSelection) {
        if (localSelections > 0) return
        val now = mutableModels.value
        val model = event.model?.let { m -> now.choices.firstOrNull { it.providerId == m.providerId && it.modelId == m.modelId } }
        val modelChanged = model != null && model != now.selected
        val agentChanged = event.agent != null && event.agent != now.agent
        if (!modelChanged && !agentChanged) return
        selectionEpoch++
        mutableModels.value =
            now.copy(
                selected = if (modelChanged) model else now.selected,
                usesDefault = if (modelChanged) false else now.usesDefault,
                agent = if (agentChanged) event.agent else now.agent,
            )
    }

    private var localSelections = 0

    override suspend fun selectModel(choice: ComposerModel?, variant: String): Outcome<Unit> {
        localSelections++
        val result =
            try {
                val delivery = (currentAttached()?.graph as? ChatGraphAccessors)?.chatDelivery
                if (delivery == null) applyModel(choice, variant)
                else delivery.withSelection(ref) { applyModel(choice, variant) }
            } finally {
                localSelections--
            }
        if (result is Outcome.Failed && result.failure != STALE)
            // The previous choice stays; the person is told this one did not take.
            mutableModels.value = mutableModels.value.copy(failure = result.failure)
        return result
    }

    private suspend fun applyModel(choice: ComposerModel?, variant: String): Outcome<Unit> {
        val a = currentAttached() ?: return Outcome.Failed(Failure.Network(NetworkWhy.Offline))
        if (!a.gateway.capabilities.value.sessionModelProviderSwitching)
            return Outcome.Failed(Failure.Unavailable(FeatureId("sessionModelProviderSwitching")))
        val selection =
            a.gateway.facet<SessionSelectionGateway>()
                ?: return Outcome.Failed(
                    Failure.Unavailable(FeatureId("sessionModelProviderSwitching"))
                )
        val catalog = mutableModels.value.choices
        val target =
            when (choice) {
                // Null asks for the default; only a concrete catalog model may be set, there is no
                // reset route.
                null -> {
                    val default = catalogDefaults?.model
                        ?: return Outcome.Failed(Failure.Unavailable(FeatureId("sessionModelDefault")))
                    catalog.firstOrNull {
                        it.providerId == default.providerId && it.modelId == default.modelId
                    } ?: return Outcome.Failed(Failure.Unavailable(FeatureId("sessionModelDefault")))
                }
                else ->
                    catalog.firstOrNull {
                        it.providerId == choice.providerId && it.modelId == choice.modelId
                    } ?: return Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
            }
        // A thinking level the model does not offer is refused (B-STATE-112); "default" is none.
        val level = variant.trim().let { if (it == "default") "" else it }
        if (level.isNotEmpty() && level !in target.variants)
            return Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
        val targetRef = ModelRef(target.providerId, target.modelId)
        return when (val set = selection.setSessionModel(sid, targetRef, level)) {
            is Outcome.Failed -> set
            is Outcome.Ok ->
                if (currentAttached() !== a) Outcome.Failed(STALE)
                else
                    when (val back = a.gateway.session(sid)) {
                        is Outcome.Failed -> back
                        is Outcome.Ok -> {
                            val held = back.value.selection
                            if (currentAttached() !== a) Outcome.Failed(STALE)
                            else if (held?.model != targetRef || held.variant.trim() != level)
                                // Never acknowledged: the previous choice stays published.
                                Outcome.Failed(Failure.Refused(RefusalReason.DispatchUncertain))
                            else {
                                selectionEpoch++
                                recentModels.record(ref.profileId.raw, target.providerId, target.modelId)
                                mutableModels.value =
                                    mutableModels.value.copy(
                                        selected = target,
                                        variant = level,
                                        failure = null,
                                        usesDefault = false,
                                        recent = recentOf(mutableModels.value.choices),
                                    )
                                Outcome.Ok(Unit)
                            }
                        }
                    }
        }
    }

    override suspend fun selectAgent(agent: String): Outcome<Unit> {
        val a = currentAttached() ?: return Outcome.Failed(Failure.Network(NetworkWhy.Offline))
        val selection =
            a.gateway.facet<SessionSelectionGateway>()
                ?.takeIf { a.gateway.capabilities.value.agentSelection }
                ?: return Outcome.Failed(Failure.Unavailable(FeatureId("agentSelection")))
        if (mutableModels.value.agents.none { it.id == agent })
            return Outcome.Failed(Failure.Refused(RefusalReason.StaleScope))
        val result =
            when (val set = selection.setSessionAgent(sid, agent)) {
                is Outcome.Failed -> set
                is Outcome.Ok ->
                    when (val back = a.gateway.session(sid)) {
                        is Outcome.Failed -> back
                        is Outcome.Ok ->
                            if (currentAttached() !== a) Outcome.Failed(STALE)
                            else if (back.value.selection?.agent != agent)
                                Outcome.Failed(Failure.Refused(RefusalReason.DispatchUncertain))
                            else {
                                selectionEpoch++
                                mutableModels.value =
                                    mutableModels.value.copy(agent = agent, failure = null)
                                Outcome.Ok(Unit)
                            }
                    }
            }
        if (result is Outcome.Failed && result.failure != STALE)
            mutableModels.value = mutableModels.value.copy(failure = result.failure)
        return result
    }

    // ---- Slash commands and @ files ----

    override suspend fun loadCommands(): Outcome<Unit> {
        val a = currentAttached() ?: return Outcome.Failed(Failure.Network(NetworkWhy.Offline))
        if (!a.gateway.capabilities.value.slashCommands)
            return Outcome.Failed(Failure.Unavailable(FeatureId("slashCommands")))
        return when (val listed = (a.graph as ChatGraphAccessors).chatOperations.commands()) {
            is Outcome.Failed -> listed
            is Outcome.Ok -> {
                if (currentAttached() !== a) return Outcome.Failed(STALE)
                mutableCommands.value = listed.value.map { ComposerCommand(it.name, it.description) }
                Outcome.Ok(Unit)
            }
        }
    }

    override suspend fun findFiles(query: String): Outcome<List<String>> {
        val a = currentAttached() ?: return Outcome.Failed(Failure.Network(NetworkWhy.Offline))
        return when (val found = a.gateway.findFile(query)) {
            is Outcome.Failed -> found
            is Outcome.Ok -> if (currentAttached() !== a) Outcome.Failed(STALE) else Outcome.Ok(found.value)
        }
    }

    override suspend fun mentionFile(path: String): Outcome<Unit> {
        val a = currentAttached() ?: return Outcome.Failed(Failure.Network(NetworkWhy.Offline))
        val directory =
            a.gateway.location.value.directory
                ?: return Outcome.Failed(Failure.Unavailable(FeatureId("fileBrowsing")))
        val file = Mentions.projectFile(directory, path)
        val current = mutableDraft.value.attachments
        if (current.any { it.mentionPath == path }) return Outcome.Ok(Unit)
        return replaceAttachments(current + file)
    }

    // ---- Sends (bubble first, never twice) ----

    override suspend fun send(text: String): Outcome<Unit> = send(text, draftFiles = true)

    override suspend fun sendAgain(text: String): Outcome<Unit> = send(text, draftFiles = false)

    private suspend fun send(text: String, draftFiles: Boolean): Outcome<Unit> {
        val id = nextLocalId++
        if (mutableLoad.value !is ChatLoad.Live)
            return Outcome.Failed(Failure.Busy(BusyKind.Starting))
        val draft = mutableDraft.value
        if (!draft.ready) return Outcome.Failed(Failure.Busy(BusyKind.Starting))
        if (draft.failure != null) return Outcome.Failed(draft.failure)
        if (draft.sendBlocked)
            return Outcome.Failed(Failure.Refused(RefusalReason.DispatchUncertain))
        // The session captures text and files as one revision; the dispatch carries that snapshot.
        val submission =
            if (!draftFiles) null
            else
                draftSession
                    ?.captureSubmission()
                    ?.takeIf { it.text.trim() == text.trim() }
        val files = if (!draftFiles) emptyList() else submission?.attachments ?: draft.attachments
        if (text.isBlank() && files.isEmpty())
            return Outcome.Failed(Failure.Refused(RefusalReason.DispatchUncertain))
        submission?.let { draftSubmissions[id] = it }
        val selection =
            models.value.selected?.let {
                ModelSelection(
                    ModelRef(it.providerId, it.modelId),
                    models.value.variant.takeIf { v -> v.isNotEmpty() },
                )
            }
        val snapshot = SentSnapshot(files.toImmutableList(), selection)
        sentSnapshots[id] = snapshot
        val expected = currentAttached()
        baselines[id] = expected?.handle?.messageIds()?.mapTo(HashSet()) { "prompt:$it" }.orEmpty()
        mutablePending.update {
            (it + PendingSend(id, text, PendingState.Sending)).toImmutableList()
        }
        // QA wave 4 N5: the words left the box again, so their not-sent bubble (and its Try again) goes now; one copy
        // of the message stays on screen and only one can be sent.
        retireFailedTwins(id)
        refreshDraftProtection()
        return dispatch(id, text, snapshot, expected)
    }

    override suspend fun sendRepeated(text: String, expectedRevision: Long): Outcome<Unit> {
        val session =
            draftSession ?: return Outcome.Failed(Failure.Unavailable(FeatureId("draftRepeat")))
        val captured = session.state.value
        if (
            !captured.ready ||
                !protectedByHistory ||
                !mutableDraft.value.repeatAllowed ||
                captured.editRevision != expectedRevision ||
                captured.text != text ||
                mutableDraft.value.failure != null ||
                mutablePending.value.any { it.state == PendingState.Unconfirmed }
        )
            return Outcome.Failed(Failure.Refused(RefusalReason.DispatchUncertain))
        protectedDraftText = null
        protectedFiles = null
        protectedByHistory = false
        refreshDraftProtection()
        return try {
            send(text.trim())
        } finally {
            // The confirmation admits one attempt, never an automatic retry or permanent bypass.
            if (
                session.state.value.editRevision == captured.editRevision &&
                    session.state.value.text == captured.text
            ) {
                protectedDraftText = captured.text
                protectedFiles = captured.attachments
                protectedByHistory = true
                refreshDraftProtection()
            }
        }
    }

    override suspend fun retrySend(localId: Long): Outcome<Unit> {
        val bubble =
            mutablePending.value.firstOrNull {
                it.localId == localId && it.state == PendingState.Failed
            } ?: return Outcome.Ok(Unit)
        val expected = currentAttached()
        baselines[localId] =
            expected?.handle?.messageIds()?.mapTo(HashSet()) { "prompt:$it" }.orEmpty()
        setPending(localId) { it.copy(state = PendingState.Sending, failure = null) }
        // Try again sends the same words, files and model; the live composer is irrelevant.
        val original = sentSnapshots[localId] ?: SentSnapshot(persistentListOf(), null)
        return dispatch(localId, bubble.text, original, expected)
    }

    private suspend fun dispatch(
        localId: Long,
        text: String,
        sent: SentSnapshot,
        expected: Attached?,
    ): Outcome<Unit> {
        val a = currentAttached() ?: return fail(localId, Failure.Network(NetworkWhy.Offline))
        if (a !== expected) return fail(localId, Failure.Refused(RefusalReason.StaleScope))
        val delivery = (a.graph as ChatGraphAccessors).chatDelivery
        // A file picked from the `@` list rides only while its `@path` is still in the words, with
        // its range (B-CHAT-087); other files pass through untouched.
        val files = Mentions.resolveFileMentions(text, sent.files).toImmutableList()
        val mentions = Mentions.agentMentions(text, subagentNames).toImmutableList()
        val result = delivery.send(ref, text, files, sent.model, mentions)
        return when (result) {
            is Outcome.Failed -> fail(localId, result.failure)
            is Outcome.Ok -> {
                if (result.value.raw.startsWith("cmd-")) {
                    mutablePending.update { list ->
                        list.filterNot { it.localId == localId }.toImmutableList()
                    }
                    sentSnapshots.remove(localId)
                    // Codex composer #2: a command that ran is done; its words leave the box (Flutter clears them).
                    acknowledgedDraft(localId)
                    refreshDraftProtection()
                } else {
                    deliveryIds[localId] = result.value
                    watchDraftAcknowledgement(localId, result.value, delivery)
                }
                syncDelivery(delivery)
                Outcome.Ok(Unit)
            }
        }
    }

    private fun syncDelivery(delivery: ConnectionDelivery) {
        val engine = delivery.engine ?: return
        for ((localId, id) in deliveryIds) {
            val bubble = engine.pending.value.firstOrNull { it.localId == id }
            bubble?.reviewId?.let { recoveryIds[it] = localId }
            bubble?.dispatchedMessageId?.let { canonical[localId] = "prompt:${it.raw}" }
            if (bubble?.phase == SendPhase.Accepted) {
                admitted += localId
                acknowledgedDraft(localId)
                delivered += localId
            }
            for (outcome in engine.outcomes.value.filter { it.ref == ref }) when (outcome) {
                is SendOutcome.Accepted ->
                    if (outcome.localId == id) {
                        acknowledgedDraft(localId)
                        delivered += localId
                    }
                is SendOutcome.Unconfirmed ->
                    if (outcome.localId == id) {
                        outcome.reviewId?.let { recoveryIds[it] = localId }
                        setPending(localId) {
                            it.copy(state = PendingState.Unconfirmed, failure = outcome.reason)
                        }
                    }
                is SendOutcome.PutBack ->
                    if (outcome.localId == id)
                        setPending(localId) {
                            it.copy(state = PendingState.Failed, failure = outcome.reason)
                        }
                else -> Unit
            }
        }
        delivered.forEach(::retireFailedTwins)
        delivered.clear()
        settlePending(mutableTranscript.value)
    }

    /**
     * B11 (QA wave 1): the words of a failed bubble went out on a later tap (the person sent them again), so the old
     * bubble is no longer "not sent": its Try again would send them twice. Same words and same files only.
     */
    private fun retireFailedTwins(localId: Long) {
        val sent = mutablePending.value.firstOrNull { it.localId == localId } ?: return
        val files = sentFiles(localId)
        val twins =
            mutablePending.value
                .filter {
                    it.localId != localId &&
                        it.state == PendingState.Failed &&
                        it.text.trim() == sent.text.trim() &&
                        sentFiles(it.localId) == files
                }
                .mapTo(HashSet()) { it.localId }
        if (twins.isEmpty()) return
        for (twin in twins) {
            sentSnapshots.remove(twin)
            baselines.remove(twin)
            deliveryIds.remove(twin)
            draftSubmissions.remove(twin)
        }
        mutablePending.update { list -> list.filterNot { it.localId in twins }.toImmutableList() }
    }

    private fun fail(localId: Long, failure: Failure): Outcome<Unit> {
        setPending(localId) { it.copy(state = PendingState.Failed, failure = failure) }
        return Outcome.Failed(failure)
    }

    private fun setPending(localId: Long, change: (PendingSend) -> PendingSend) {
        mutablePending.update { list ->
            list.map { if (it.localId == localId) change(it) else it }.toImmutableList()
        }
        // A bubble turning unconfirmed can make the still-attached files a duplicate risk.
        refreshDraftProtection()
    }

    /** Canonical ids win; the fallback is post-dispatch, admitted and one-to-one. */
    private fun settlePending(s: TranscriptState) {
        val prompts = s.rows.filterIsInstance<PromptRow>()
        // A file-only user message has no prompt row (no words): it is matched by its server id.
        val wordless = userMessageKeys.filterTo(LinkedHashSet()) { key -> prompts.none { it.key == key } }
        for (bubble in mutablePending.value) {
            val id = canonical[bubble.localId]
            if (bubble.state == PendingState.Failed) continue
            val fileOnly = bubble.text.isBlank()
            val present = id != null && (prompts.any { it.key == id } || (fileOnly && id in wordless))
            if (bubble.localId !in admitted && !present) continue
            val matchKey =
                if (fileOnly)
                    if (id != null) id.takeIf { it in wordless && it !in settled }
                    else
                        wordless.firstOrNull {
                            it !in settled && it !in baselines[bubble.localId].orEmpty()
                        }
                else
                    prompts
                        .firstOrNull { row ->
                            row.key !in settled &&
                                if (id != null) row.key == id
                                else
                                    row.key !in baselines[bubble.localId].orEmpty() &&
                                        row.text.trim() == bubble.text.trim()
                        }
                        ?.key
            if (matchKey == null) continue
            settled += matchKey
            retireFailedTwins(bubble.localId)
            acknowledgedDraft(bubble.localId)
            admitted -= bubble.localId
            canonical.remove(bubble.localId)
            baselines.remove(bubble.localId)
            sentSnapshots.remove(bubble.localId)
            deliveryIds.remove(bubble.localId)?.let {
                (currentAttached()?.graph as? ChatGraphAccessors)
                    ?.chatDelivery
                    ?.engine
                    ?.reconciled(it)
            }
            mutablePending.update { list ->
                list.filterNot { it.localId == bubble.localId }.toImmutableList()
            }
        }
    }

    // ---- Commands ----

    override suspend fun stop(): Outcome<Unit> =
        when (val r = stopTurn()) {
            is Outcome.Ok -> Outcome.Ok(Unit)
            is Outcome.Failed -> r
        }

    override suspend fun stopTurn(): Outcome<Boolean> {
        val a = currentAttached() ?: return Outcome.Failed(Failure.Network(NetworkWhy.Offline))
        (a.graph as ChatGraphAccessors).chatDelivery.engine?.let {
            // The engine calls off waiting sends and records the person's Stop, then interrupts.
            val stopped = it.stopTurn(ref)
            // Codex chat-bugs 2: a tap inside the guard after a send asked nothing; Stop stays usable.
            when (stopped) {
                is Outcome.Ok -> return Outcome.Ok(stopped.value == StopAnswer.Asked)
                // B10: a stale fence there asked the server nothing; the running turn is still owed its interrupt.
                is Outcome.Failed -> if (stopped.failure != STALE || currentAttached() !== a) return stopped
            }
        }
        return when (val r = a.gateway.stop(sid)) {
            is Outcome.Ok -> Outcome.Ok(true)
            is Outcome.Failed -> r
        }
    }

    override suspend fun reload(): Outcome<Unit> {
        bind(registry.graphFor(ref))
        return when (val l = mutableLoad.value) {
            is ChatLoad.Failed -> Outcome.Failed(l.failure)
            else -> Outcome.Ok(Unit)
        }
    }

    override suspend fun fork(from: MessageId): Outcome<SessionId> {
        val graph =
            currentAttached()?.graph ?: return Outcome.Failed(Failure.Network(NetworkWhy.Offline))
        return (graph as ChatGraphAccessors).chatOperations.forkSession(sid, from)
    }

    /**
     * Plan 09 §6.6: delete on the server, then read the conversation again from scratch (P3 has no
     * removeMessage).
     */
    override suspend fun deleteMessage(id: MessageId): Outcome<Unit> {
        val graph =
            currentAttached()?.graph ?: return Outcome.Failed(Failure.Network(NetworkWhy.Offline))
        val r = (graph as ChatGraphAccessors).chatOperations.deleteMessage(sid, id)
        if (r is Outcome.Ok && registry.graphFor(ref) === graph) bind(graph)
        return r
    }

    override suspend fun revertFrom(from: MessageId): Outcome<Unit> {
        val graph =
            currentAttached()?.graph ?: return Outcome.Failed(Failure.Network(NetworkWhy.Offline))
        val r = (graph as ChatGraphAccessors).chatOperations.revertSession(sid, from)
        if (r is Outcome.Ok && registry.graphFor(ref) === graph) bind(graph)
        return r
    }

    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    override suspend fun readPicture(path: String): Outcome<ByteArray> {
        val a = currentAttached() ?: return Outcome.Failed(Failure.Network(NetworkWhy.Offline))
        val read = a.gateway.fileContent(path)
        if (currentAttached() !== a) return Outcome.Failed(STALE)
        val file = (read as? Outcome.Ok)?.value ?: return read as Outcome.Failed
        if (file.encoding?.lowercase() != "base64" || file.content.length > MAX_PICTURE_BASE64) {
            return Outcome.Failed(Failure.Unavailable(FeatureId("fileBrowsing")))
        }
        return try {
            Outcome.Ok(kotlin.io.encoding.Base64.Default.decode(file.content.filterNot { it.isWhitespace() }))
        } catch (_: IllegalArgumentException) {
            Outcome.Failed(Failure.Unexpected())
        }
    }

    override suspend fun fileExists(path: String): Outcome<Boolean> {
        val slash = path.lastIndexOf('/')
        val name = if (slash >= 0) path.substring(slash + 1) else ""
        if (name.isEmpty()) return Outcome.Ok(false)
        val dir = if (slash == 0) "/" else path.substring(0, slash)
        val a = currentAttached() ?: return Outcome.Failed(Failure.Network(NetworkWhy.Offline))
        if (!a.gateway.capabilities.value.fileBrowsing) return Outcome.Ok(false)
        val listed = a.gateway.listFiles(dir)
        if (currentAttached() !== a) return Outcome.Failed(STALE)
        val nodes = (listed as? Outcome.Ok)?.value ?: return listed as Outcome.Failed
        return Outcome.Ok(nodes.any { !it.isDir && it.name == name })
    }

    override fun markRead() {
        val a = currentAttached() ?: return
        a.graph.scope.launch(registry.mainContext) {
            val session = (a.gateway.session(sid) as? Outcome.Ok)?.value ?: return@launch
            if (currentAttached() !== a) return@launch
            val owner = a.graph.session.readState
            owner.observe(session)
            session.time?.idleMs?.let { owner.markRead(sid, it) }
        }
    }

    private fun linkOf(s: StreamStatus): ChatLink =
        when (s) {
            StreamStatus.Connected -> ChatLink.Live
            StreamStatus.Connecting,
            is StreamStatus.Reconnecting,
            StreamStatus.Suspended -> ChatLink.Connecting
            is StreamStatus.Disconnected,
            StreamStatus.WaitingForNetwork,
            is StreamStatus.Stopped -> ChatLink.Offline
        }

    private companion object {
        const val HISTORY_PAGE = 100
        val STALE = Failure.Refused(RefusalReason.StaleScope)
    }
}

/** 20 MB of picture as base64 (Flutter's attachment cap); a bigger file is not opened in the chat. */
private const val MAX_PICTURE_BASE64: Int = 28_000_000
