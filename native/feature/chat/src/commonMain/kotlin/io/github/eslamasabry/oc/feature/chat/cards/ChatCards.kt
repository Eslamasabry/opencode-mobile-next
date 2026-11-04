package io.github.eslamasabry.oc.feature.chat.cards

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.AgentFeaturesGateway
import io.github.eslamasabry.oc.core.domain.CachedAgentRecoveryGateway
import io.github.eslamasabry.oc.core.domain.McpGateway
import io.github.eslamasabry.oc.core.domain.BoundedHistoryGateway
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.domain.facet
import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.core.model.SessionStatus
import io.github.eslamasabry.oc.core.model.StreamStatus
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.cards.AgentCard
import io.github.eslamasabry.oc.engine.cards.CardAgent
import io.github.eslamasabry.oc.engine.cards.CardAnswer
import io.github.eslamasabry.oc.engine.cards.CardAnswerSender
import io.github.eslamasabry.oc.engine.cards.CardBoard
import io.github.eslamasabry.oc.engine.cards.CardBoardState
import io.github.eslamasabry.oc.engine.cards.CardCoverage
import io.github.eslamasabry.oc.engine.cards.CardDispatchCheck
import io.github.eslamasabry.oc.engine.cards.CardHistory
import io.github.eslamasabry.oc.engine.cards.CardHistoryPage
import io.github.eslamasabry.oc.engine.cards.CardHistorySource
import io.github.eslamasabry.oc.engine.cards.CardJournal
import io.github.eslamasabry.oc.engine.cards.CardJournalStore
import io.github.eslamasabry.oc.engine.cards.CardRefusal
import io.github.eslamasabry.oc.engine.cards.CardRecovery
import io.github.eslamasabry.oc.engine.cards.CardScope
import io.github.eslamasabry.oc.engine.cards.CardState
import io.github.eslamasabry.oc.engine.cards.CardsLifecycle
import io.github.eslamasabry.oc.engine.cards.CardsPrefs
import io.github.eslamasabry.oc.engine.cards.CardsProblem
import io.github.eslamasabry.oc.engine.cards.CardsQualifier
import io.github.eslamasabry.oc.engine.cards.CardsSetup
import io.github.eslamasabry.oc.engine.cards.CardsStatus
import io.github.eslamasabry.oc.engine.cards.CardsVerdict
import io.github.eslamasabry.oc.engine.cards.MemoryCardJournal
import io.github.eslamasabry.oc.engine.cards.StoredCardJournal
import io.github.eslamasabry.oc.engine.cards.CoverageStore
import io.github.eslamasabry.oc.engine.cards.RecoveryTarget
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.delivery.SendRoute
import io.github.eslamasabry.oc.engine.integrations.catalog.McpChatConnect
import io.github.eslamasabry.oc.engine.integrations.catalog.McpChatConnectors
import io.github.eslamasabry.oc.engine.integrations.catalog.catalogServerName
import io.github.eslamasabry.oc.feature.chat.world.ChatGraphAccessors
import io.github.eslamasabry.oc.ui.routes.McpCatalogRoute
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/*
 * The card slot's state (P12-CHAT-CARDS; plan 09 §13). Cards are untrusted conversation content: an answer is an
 * ordinary prompt through the chat's own delivery pipeline (never steered, never queued, never while the agent works).
 * The model reads its own bounded history (CardHistory: 2 pages of 50) and the live events of the open conversation,
 * as the Flutter GenUiStateController does, and hands the authoritative transcript to the engine's CardBoard.
 */

/**
 * Where the person's "Cards from agents" choice is stored; the app binds one over its preferences.
 */
@ContributesTo(AppScope::class)
public interface ChatCardsBindings {
    @Multibinds(allowEmpty = true) public val cardsPrefs: Set<CardsPrefs>
    @Multibinds(allowEmpty = true) public val cardsLifecycles: Set<CardsLifecycle>
    @Multibinds(allowEmpty = true) public val connectorFactories: Set<McpChatConnectors>

    /**
     * Where answers that may have left are remembered (`oc.genui.journal.<profileId>`, B-CHAT-051).
     */
    @Multibinds(allowEmpty = true) public val cardJournalStores: Set<CardJournalStore>

    /**
     * The hosts the app manages itself: the phone's built-in Linux server (B-CHAT-040), the phone's agent host
     * (LINUX-2) and the native phone helper (AGENT-2). One set; each claims only its own profiles.
     */
    @Multibinds(allowEmpty = true) public val managedCardsQualifiers: Set<CardsQualifier>
}

/**
 * In-memory prefs where the app binds none (desktop preview, tests): cards stay off after a
 * restart.
 */
public class MemoryCardsPrefs : CardsPrefs {
    private val values = HashMap<String, Boolean>()

    override fun enabled(profileId: String): Boolean? = values[profileId]

    override suspend fun setEnabled(profileId: String, on: Boolean): Outcome<Unit> {
        values[profileId] = on
        return Outcome.Ok(Unit)
    }
}

/**
 * Qualification for a connected server: the server must report the app's `oc-ui` MCP server as
 * connected (the MCP `initialize` self-check). The phone cannot install anything on a server it
 * does not manage, so setup never runs an installer here (B-CHAT-036); the agent named is OpenCode
 * 2 (B-CHAT-035).
 */
public class GraphCardsQualifier(
    private val registry: ConnectionRegistry,
    private val managedHosts: Set<CardsQualifier> = emptySet(),
) : CardsQualifier {
    private fun host(profileId: String): CardsQualifier? = managedHosts.singleOrNull {
        it.managed(profileId)
    }

    override fun managed(profileId: String): Boolean = host(profileId) != null

    // AGENT-2: helper aliases store settings under their saved owner and gate per session.
    override fun permitsSession(profileId: String, sessionId: String): Boolean =
        managedHosts.all { it.permitsSession(profileId, sessionId) }

    override fun settingsOwner(profileId: String): String? =
        managedHosts.firstNotNullOfOrNull { host ->
            host.settingsOwner(profileId)?.takeIf { it != profileId }
        } ?: profileId

    override fun activeSettingsOwner(profileId: String): Boolean =
        managedHosts.all { it.activeSettingsOwner(profileId) }

    override fun agentsHere(profileId: String): List<CardAgent> =
        host(profileId)?.agentsHere(profileId) ?: listOf(CardAgent.OpenCode2)

    override suspend fun install(profileId: String): Outcome<CardsVerdict> =
        host(profileId)?.install(profileId) ?: verify(profileId)

    override suspend fun remove(profileId: String): Outcome<Unit> =
        host(profileId)?.remove(profileId) ?: Outcome.Ok(Unit)

    override suspend fun verify(profileId: String): Outcome<CardsVerdict> {
        host(profileId)?.let {
            return it.verify(profileId)
        }
        val graph = registry.live.value.entries.firstOrNull { it.key.raw == profileId }?.value
        val here = agentsHere(profileId)
        if (graph == null || !CardsQualification.OPEN_CODE_2_QUALIFIED) {
            return Outcome.Ok(CardsVerdict(emptyList(), CardsProblem.NotQualified, here))
        }
        return Outcome.Ok(
            CardsQualification.verdict(
                (graph as ChatGraphAccessors).chatOperations.mcpServers(),
                here,
            )
        )
    }
}

/**
 * Routes by host: the phone's own server is managed (setup, verification and removal run in the built-in Linux through
 * [phone]); every other connection is verified over its connected server and never installed to (B-CHAT-036).
 */
internal class RoutedCardsQualifier(private val graph: CardsQualifier, private val phone: CardsQualifier?) : CardsQualifier {
    private fun pick(profileId: String): CardsQualifier = phone?.takeIf { it.managed(profileId) } ?: graph

    override fun managed(profileId: String): Boolean = pick(profileId).managed(profileId)

    override fun agentsHere(profileId: String): List<CardAgent> = pick(profileId).agentsHere(profileId)

    override suspend fun verify(profileId: String): Outcome<CardsVerdict> = pick(profileId).verify(profileId)

    override suspend fun install(profileId: String): Outcome<CardsVerdict> = pick(profileId).install(profileId)

    override suspend fun remove(profileId: String): Outcome<Unit> = pick(profileId).remove(profileId)
}

/** One per app: the availability state and the per-conversation models. */
@SingleIn(AppScope::class)
@Inject
public class ChatCards(
    private val registry: ConnectionRegistry,
    prefs: Set<CardsPrefs> = emptySet(),
    journals: Set<CardJournalStore> = emptySet(),
    qualifiers: Set<CardsQualifier> = emptySet(),
    lifecycles: Set<CardsLifecycle> = emptySet(),
    private val connectors: Set<McpChatConnectors> = emptySet(),
) {
    public val setup: CardsSetup =
        CardsSetup(
            prefs.firstOrNull() ?: MemoryCardsPrefs(),
            // Each managed host answers for its own profiles; every other connection is verified over the server.
            GraphCardsQualifier(registry, qualifiers),
        )

    init {
        lifecycles.forEach { it.attach(setup) }
    }

    private val journal: CardJournal =
        journals.firstOrNull()?.let(::StoredCardJournal) ?: MemoryCardJournal()

    public fun open(ref: ConversationRef): ChatCardsModel =
        ChatCardsModel(ref, registry, setup, journal, connectors.singleOrNull()).also {
            it.attach()
        }

    /** The Cards row: what is true now. */
    public fun status(profileId: String): CardsStatus = setup.status(profileId)

    public suspend fun setEnabled(profileId: String, on: Boolean): Outcome<Unit> =
        setup.setEnabled(profileId, on)
}

/**
 * The cards of one open conversation. Lives as long as the chat entry; [close] drops held answers
 * (nothing is sent late) and stops reading.
 */
public class ChatCardsModel
internal constructor(
    public val ref: ConversationRef,
    private val registry: ConnectionRegistry,
    private val setup: CardsSetup,
    private val journal: CardJournal = MemoryCardJournal(),
    private val connectors: McpChatConnectors? = null,
) {
    private val mutable = MutableStateFlow(CardBoardState())
    public val state: StateFlow<CardBoardState> = mutable.asStateFlow()

    private var coverage = graphCoverage()

    private fun graphCoverage(): CardCoverage =
        CardCoverage(
            object : CoverageStore {
                private val values = HashMap<String, String>()

                override fun read(profileId: String): String? = values[profileId]

                override suspend fun write(profileId: String, json: String?): Outcome<Unit> {
                    if (json == null) values.remove(profileId) else values[profileId] = json
                    return Outcome.Ok(Unit)
                }
            }
        )

    private var closed = false

    private data class ConnectorOrigin(
        val profile: String,
        val endpoint: String,
        val user: String?,
        val backend: String,
        val directory: String?,
        val workspace: String?,
        val owner: String,
    )

    private data class ConnectorAdmission(
        val card: AgentCard,
        val origin: ConnectorOrigin,
        val controller: McpChatConnect,
    )

    private data class PendingConnector(
        val card: AgentCard,
        val completion: CompletableDeferred<McpChatConnect?>,
    )

    private val connectorAdmissions =
        LinkedHashMap<Pair<ConnectorOrigin, String>, ConnectorAdmission>()
    private val resolvingConnectors = HashMap<Pair<ConnectorOrigin, String>, PendingConnector>()

    private fun connectorOrigin(graph: ConnectionGraph?): ConnectorOrigin? {
        graph ?: return null
        val owner = setup.settingsOwner(ref.profileId.raw) ?: return null
        val location = graph.session.server.location.value
        return ConnectorOrigin(
            graph.profile.id.raw,
            graph.profile.origin.toString(),
            graph.profile.username,
            graph.profile.backend.name,
            location.directory,
            location.workspace,
            owner,
        )
    }

    private fun connectorCurrent(card: AgentCard, origin: ConnectorOrigin): Boolean {
        if (closed || !setup.desired(ref.profileId.raw) || !setup.activeSettingsOwner(origin.owner))
            return false
        val graph = registry.graphFor(ref) ?: return false
        if (
            connectorOrigin(graph) != origin ||
                graph.session.server.events.status.value != StreamStatus.Connected
        )
            return false
        val observed = if (bound === graph) board?.observedState(card) else null
        if (
            !setup.permitsSession(ref.profileId.raw, sid.raw) && bound === graph && observed != null
        )
            return false
        return observed == null || observed == CardState.Report
    }

    private fun connectorAuthorized(card: AgentCard): Boolean =
        !closed &&
            bound === registry.graphFor(ref) &&
            state.value.available &&
            board?.observedCard(card)?.revision == card.revision &&
            board?.observedState(card) == CardState.Report

    private fun pruneConnectors() {
        val iterator = connectorAdmissions.entries.iterator()
        while (iterator.hasNext()) {
            val admitted = iterator.next().value
            val replacing =
                registry.graphFor(ref) == null &&
                    setup.settingsOwner(admitted.origin.owner) == admitted.origin.owner &&
                    setup.desired(admitted.origin.owner) &&
                    setup.activeSettingsOwner(admitted.origin.owner)
            if (
                admitted.controller.isDisposed ||
                    (!replacing && !connectorCurrent(admitted.card, admitted.origin))
            ) {
                admitted.controller.invalidate()
                iterator.remove()
            }
        }
    }

    /** Only an authoritative observed report may turn a catalogue id into a controller. */
    public suspend fun connector(card: AgentCard): McpChatConnect? {
        val factory = connectors ?: return null
        pruneConnectors()
        if (!connectorAuthorized(card)) return null
        val id = card.connector?.catalogId ?: return null
        val origin = connectorOrigin(bound) ?: return null
        val key = origin to catalogServerName(id)
        connectorAdmissions[key]?.let {
            return it.controller.takeIf { _ ->
                it.card.identity == card.identity && it.card.revision == card.revision
            }
        }
        resolvingConnectors[key]?.let {
            return if (it.card.identity == card.identity && it.card.revision == card.revision)
                it.completion.await()
            else null
        }
        if (connectorAdmissions.size + resolvingConnectors.size >= 32) return null
        val pending = PendingConnector(card, CompletableDeferred())
        resolvingConnectors[key] = pending
        var result: McpChatConnect? = null
        try {
            val item =
                factory.resolve(origin.owner, id) { connectorCurrent(card, origin) } ?: return null
            if (
                !connectorCurrent(card, origin) ||
                    !connectorAuthorized(card) ||
                    item.serverName != key.second
            )
                return null
            val controller =
                factory.create(
                    item,
                    gateway = {
                        if (!connectorCurrent(card, origin)) throw IllegalStateException()
                        val graph = registry.graphFor(ref) ?: throw IllegalStateException()
                        if (connectorOrigin(graph) != origin) throw IllegalStateException()
                        if (bound !== graph) bind(graph)
                        val gateway = (graph as ChatGraphAccessors).chatGateway
                        if (
                            !read(gateway) ||
                                !connectorCurrent(card, origin) ||
                                !connectorAuthorized(card) ||
                                registry.graphFor(ref) !== graph
                        )
                            throw IllegalStateException()
                        gateway.facet<McpGateway>() ?: throw IllegalStateException()
                    },
                    capabilities = {
                        registry.graphFor(ref)?.session?.capabilities?.value ?: ServerCapabilities()
                    },
                    current = { connectorCurrent(card, origin) },
                )
            connectorAdmissions[key] = ConnectorAdmission(card, origin, controller)
            result = controller
            return controller
        } finally {
            if (resolvingConnectors[key] === pending) resolvingConnectors.remove(key)
            pending.completion.complete(result)
        }
    }

    /**
     * Catalogue navigation always follows the trusted saved owner, never an agent-authored address.
     */
    public fun catalogueRoute(): McpCatalogRoute? {
        val owner = setup.settingsOwner(ref.profileId.raw)?.let(::ProfileId) ?: return null
        val location = registry.live.value[owner]?.session?.server?.location?.value
        return McpCatalogRoute(owner, location?.directory, location?.workspace)
    }

    /** Cached session runtime metadata only; an unknown runtime is named generically by the UI. */
    public fun agentDisplayName(): String? {
        val provider =
            registry
                .graphFor(ref)
                ?.session
                ?.server
                ?.facet<AgentFeaturesGateway>()
                ?.providerIdForSession(sid) ?: return null
        return AgentCatalog.builtIn.agents.firstOrNull { it.providerId == provider }?.name
    }

    private var board: CardBoard? = null
    private var bound: ConnectionGraph? = null
    private val stops = ArrayList<() -> Unit>()
    private var stopWatching: (() -> Unit)? = null
    private val transcript = CardTranscript()
    private var cardScope: CardScope? = null
    private val sid: SessionId
        get() = ref.sessionId

    internal fun attach() {
        val watcher =
            registry.appScope.launch(registry.mainContext) {
                registry.live.collect {
                    val graph = registry.graphFor(ref)
                    if (graph !== bound) bind(graph)
                }
            }
        stopWatching = { watcher.cancel() }
    }

    public fun answer(
        card: AgentCard,
        answer: CardAnswer,
        photos: List<PromptAttachment> = emptyList(),
    ) {
        val graph = bound
        board?.setAvailable(
            graph != null &&
                setup.effective(ref.profileId.raw) &&
                setup.permitsSession(ref.profileId.raw, sid.raw) &&
                graph.session.server.events.status.value == StreamStatus.Connected
        )
        board?.answer(card, answer, photos)
    }

    public fun undo(card: AgentCard) {
        board?.undo(card)
    }

    /**
     * Display continuity during suspension is separate from permission to start a connector
     * request.
     */
    public fun observedCard(card: AgentCard): AgentCard? = board?.observedCard(card)

    public fun observedState(card: AgentCard): CardState? = board?.observedState(card)

    public fun close() {
        closed = true
        connectorAdmissions.values.forEach { it.controller.dispose() }
        connectorAdmissions.clear()
        stopWatching?.invoke()
        unbind()
    }

    private fun unbind() {
        coverage.closeProfile(ref.profileId.raw)
        coverage = graphCoverage()
        stops.forEach { it() }
        stops.clear()
        board?.close()
        board = null
        bound = null
        transcript.clear()
        mutable.value = CardBoardState()
    }

    private fun bind(graph: ConnectionGraph?) {
        pruneConnectors()
        unbind()
        bound = graph
        if (graph == null) return
        val services = graph as ChatGraphAccessors
        val gateway = services.chatGateway
        val delivery = services.chatDelivery
        val sender = CardAnswerSender { _, text, photos ->
            when (val sent = delivery.send(ref, text, photos.toImmutableList())) {
                // The id comes back before the request leaves: wait for this send's own result
                // (B-CHAT-050).
                is Outcome.Ok -> {
                    val engine = delivery.engine
                    if (engine == null)
                        Outcome.Failed(Failure.Refused(RefusalReason.DispatchUncertain))
                    else awaitCardSend(sent.value, engine.outcomes, engine.pending)
                }
                is Outcome.Failed -> sent
            }
        }
        // B-CHAT-039/050/052 (Flutter `_send`): a fresh read, then an explicit idle check, right
        // before sending. A
        // card answer is never queued for later and never steered into a running turn.
        val check = CardDispatchCheck { card ->
            if (!setup.permitsSession(ref.profileId.raw, sid.raw))
                return@CardDispatchCheck CardRefusal.Changed
            if (bound !== graph || card.sessionId != sid.raw)
                return@CardDispatchCheck CardRefusal.Changed
            if (gateway.events.status.value != StreamStatus.Connected)
                return@CardDispatchCheck CardRefusal.Changed
            if (!read(gateway)) return@CardDispatchCheck CardRefusal.Changed
            val idle =
                when (val statuses = gateway.sessionStatuses()) {
                    is Outcome.Failed -> false
                    is Outcome.Ok ->
                        statuses.value[sid].let { it == null || it == SessionStatus.Idle }
                }
            if (bound !== graph) return@CardDispatchCheck CardRefusal.Changed
            board?.setBusy(!idle)
            if (!idle || delivery.engine?.routeFor(ref) != SendRoute.Plain) CardRefusal.Busy
            else null
        }
        val b =
            CardBoard(
                graph.scope,
                sender,
                photosAllowed = { gateway.capabilities.value.promptAttachments },
                check = check,
                journal = journal,
            )
        board = b
        val location = gateway.location.value
        cardScope =
            CardScope(ref.profileId.raw, SOURCE, location.directory ?: GLOBAL, location.workspace)
        val profile = ref.profileId.raw
        graph.run {
            b.state.collect {
                mutable.value = it
                pruneConnectors()
            }
        }
        graph.run {
            combine(setup.changes, gateway.events.status) { _, stream ->
                    setup.effective(profile) &&
                        setup.permitsSession(profile, sid.raw) &&
                        stream == StreamStatus.Connected
                }
                .collect { b.setAvailable(it) }
        }
        graph.run {
            gateway.location.collectLatest { place ->
                val nextScope =
                    CardScope(ref.profileId.raw, SOURCE, place.directory ?: GLOBAL, place.workspace)
                if (cardScope != nextScope) {
                    cardScope = nextScope
                    transcript.clear()
                    b.observe(nextScope, sid.raw, emptyList(), false)
                    pruneConnectors()
                    read(gateway)
                }
            }
        }
        graph.run {
            setup.recheck(profile) // B-CHAT-034: a reconnect or another server is checked again
            val statuses = gateway.sessionStatuses()
            if (statuses is Outcome.Ok) b.setBusy(statuses.value[sid] == SessionStatus.Busy)
            read(gateway)
        }
        graph.run {
            gateway.events.all.collect { envelope ->
                when (val e = envelope.event) {
                    is GatewayEvent.RunStarted -> if (e.sessionId == sid) b.setBusy(true)
                    is GatewayEvent.RunEnded ->
                        if (e.sessionId == sid) {
                            b.setBusy(false)
                            read(gateway, explicit = false) // reconcile by refetch, not replay
                        }
                    is GatewayEvent.MessageUpserted ->
                        if (e.message.sessionId == sid.raw) upsert(e.message)
                    is GatewayEvent.PartUpserted -> if (e.part.sessionId == sid.raw) upsert(e.part)
                    is GatewayEvent.TranscriptStale -> if (e.sessionId == sid) read(gateway)
                    GatewayEvent.Connected -> {
                        setup.recheck(profile)
                        read(gateway, explicit = false)
                    }
                    // Pure deltas never change a card (B-CHAT-037): input arrives whole when the
                    // call completes.
                    else -> Unit
                }
            }
        }
    }

    /** Work tied to the bound graph; [unbind] stops it. */
    private fun ConnectionGraph.run(block: suspend CoroutineScope.() -> Unit) {
        val job = scope.launch(registry.mainContext, block = block)
        stops += { job.cancel() }
    }

    /**
     * One bounded read; true when it was applied (a failed or overtaken read leaves the cards as
     * they were).
     */
    private suspend fun read(gateway: ServerGateway, explicit: Boolean = true): Boolean {
        val connection = bound ?: return false
        val boardOwner = board ?: return false
        val sourceScope = cardScope ?: return false
        val location = gateway.location.value
        val thisCoverage = coverage
        val snapshots = gateway.facet<CachedAgentRecoveryGateway>()
        fun current(): Boolean =
            !closed &&
                bound === connection &&
                board === boardOwner &&
                (connection as ChatGraphAccessors).chatGateway === gateway &&
                gateway.location.value == location &&
                cardScope == sourceScope &&
                registry.graphFor(ref) === connection &&
                setup.settingsOwner(ref.profileId.raw) != null
        fun target(): RecoveryTarget {
            val snapshot = snapshots?.cachedAgentRecovery(sid)
            val sent = journal.dispatched(sourceScope.profileId)
            return RecoveryTarget(
                sourceScope,
                sid.raw,
                connection.profile.origin.toString(),
                idle = snapshot?.idle,
                promptStamp = snapshot?.promptStamp,
                journaled = sent == null || sent.isNotEmpty(),
            )
        }
        if (!explicit && !CardRecovery.needsRead(target(), thisCoverage)) return current()
        // Agent-card recovery reads only through the bounded facet (1 MiB, 10 s): a backend without
        // it cannot supply authoritative recovery through an unbounded fallback.
        val bounded = gateway.facet<BoundedHistoryGateway>() ?: return false
        val source = CardHistorySource { session, cursor, limit ->
            when (
                val page = bounded.boundedHistoryPage(SessionId(session), cursor = cursor, limit = limit)
            ) {
                is Outcome.Ok ->
                    Outcome.Ok(
                        CardHistoryPage(page.value.items, page.value.nextCursor, page.value.hasMore)
                    )
                is Outcome.Failed -> page
            }
        }
        // A live event during the read means the read is older than what is shown: read again, a
        // few times at most.
        repeat(READ_ATTEMPTS) {
            if (!current()) return false
            val before = target()
            val mark = transcript.mark()
            val tail = CardHistory.readTail(source, sid.raw)
            if (tail !is Outcome.Ok || !current()) return false
            val after = target()
            if (transcript.applyRead(mark, tail.value)) {
                publish()
                if (current() && CardRecovery.mayCover(tail.value, before, after)) {
                    after.promptStamp?.let {
                        thisCoverage.remember(
                            sourceScope.profileId,
                            CardCoverage.identity(after.endpoint, sourceScope, sid.raw),
                            it,
                        )
                    }
                }
                return current()
            }
        }
        return false
    }

    private fun upsert(info: MessageInfo) {
        transcript.upsert(info)
        publish()
    }

    private fun upsert(part: Part) {
        if (transcript.upsert(part)) publish() // its message arrives first; a later read reconciles
    }

    private fun publish() {
        val scope = cardScope ?: return
        board?.observe(scope, sid.raw, transcript.list(), transcript.complete)
        pruneConnectors()
    }

    private companion object {
        /** Flutter `_openCodeSourceId`. */
        const val SOURCE = "opencode"
        const val GLOBAL = "global"
        const val READ_ATTEMPTS = 3
    }
}

/**
 * What a prompt bubble shows for a card answer: only the summary line of a fully valid receipt
 * (plan 09 §13.4: the JSON line is hidden only for a validated receipt); any other text unchanged.
 */
public object CardAnswerDisplay {
    public fun text(raw: String): String {
        if (!raw.startsWith("[oc-ui answer ")) return raw
        val message =
            MessageWithParts(
                MessageInfo(
                    "display",
                    "display",
                    io.github.eslamasabry.oc.core.model.transcript.Role.User,
                ),
                kotlinx.collections.immutable.persistentListOf(
                    Part(
                        id = "display",
                        messageId = "display",
                        sessionId = "display",
                        type = io.github.eslamasabry.oc.core.model.transcript.PartType.Text,
                        rawType = "text",
                        text = raw,
                    )
                ),
            )
        return io.github.eslamasabry.oc.engine.cards.CardCodec.answerEnvelope(message)?.second
            ?: raw
    }
}
