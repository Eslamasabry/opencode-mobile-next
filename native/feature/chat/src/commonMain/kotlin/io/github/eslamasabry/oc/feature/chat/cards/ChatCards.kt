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
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.facet
import io.github.eslamasabry.oc.core.domain.BoundedHistoryGateway
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.core.model.SessionStatus
import io.github.eslamasabry.oc.core.model.StreamStatus
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.engine.cards.AgentCard
import io.github.eslamasabry.oc.engine.cards.CardAgent
import io.github.eslamasabry.oc.engine.cards.CardAnswer
import io.github.eslamasabry.oc.engine.cards.CardAnswerSender
import io.github.eslamasabry.oc.engine.cards.CardBoard
import io.github.eslamasabry.oc.engine.cards.CardBoardState
import io.github.eslamasabry.oc.engine.cards.CardDispatchCheck
import io.github.eslamasabry.oc.engine.cards.CardJournal
import io.github.eslamasabry.oc.engine.cards.CardJournalStore
import io.github.eslamasabry.oc.engine.cards.CardRefusal
import io.github.eslamasabry.oc.engine.cards.MemoryCardJournal
import io.github.eslamasabry.oc.engine.cards.StoredCardJournal
import io.github.eslamasabry.oc.engine.cards.CardHistory
import io.github.eslamasabry.oc.engine.cards.CardHistoryPage
import io.github.eslamasabry.oc.engine.cards.CardHistorySource
import io.github.eslamasabry.oc.engine.cards.CardScope
import io.github.eslamasabry.oc.engine.cards.CardsPrefs
import io.github.eslamasabry.oc.engine.cards.CardsProblem
import io.github.eslamasabry.oc.engine.cards.CardsQualifier
import io.github.eslamasabry.oc.engine.cards.CardsSetup
import io.github.eslamasabry.oc.engine.cards.CardsStatus
import io.github.eslamasabry.oc.engine.cards.CardsVerdict
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.delivery.SendRoute
import io.github.eslamasabry.oc.feature.chat.world.ChatGraphAccessors
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/*
 * The card slot's state (P12-CHAT-CARDS; plan 09 §13). Cards are untrusted conversation content: an answer is an
 * ordinary prompt through the chat's own delivery pipeline (never steered, never queued, never while the agent works).
 * The model reads its own bounded history (CardHistory: 2 pages of 50) and the live events of the open conversation,
 * as the Flutter GenUiStateController does, and hands the authoritative transcript to the engine's CardBoard.
 */

/** Where the person's "Cards from agents" choice is stored; the app binds one over its preferences. */
@ContributesTo(AppScope::class)
public interface ChatCardsBindings {
    @Multibinds(allowEmpty = true) public val cardsPrefs: Set<CardsPrefs>

    /** Where answers that may have left are remembered (`oc.genui.journal.<profileId>`, B-CHAT-051). */
    @Multibinds(allowEmpty = true) public val cardJournalStores: Set<CardJournalStore>
}

/** In-memory prefs where the app binds none (desktop preview, tests): cards stay off after a restart. */
public class MemoryCardsPrefs : CardsPrefs {
    private val values = HashMap<String, Boolean>()

    override fun enabled(profileId: String): Boolean? = values[profileId]

    override suspend fun setEnabled(profileId: String, on: Boolean): Outcome<Unit> {
        values[profileId] = on
        return Outcome.Ok(Unit)
    }
}

/**
 * Qualification for a connected server: the server must report the app's `oc-ui` MCP server as connected (the MCP
 * `initialize` self-check). The phone cannot install anything on a server it does not manage, so setup never runs an
 * installer here (B-CHAT-036); the agent named is OpenCode 2 (B-CHAT-035).
 */
public class GraphCardsQualifier(private val registry: ConnectionRegistry) : CardsQualifier {
    override fun managed(profileId: String): Boolean = false

    override fun agentsHere(profileId: String): List<CardAgent> = listOf(CardAgent.OpenCode2)

    override suspend fun verify(profileId: String): Outcome<CardsVerdict> {
        val graph = registry.live.value.entries.firstOrNull { it.key.raw == profileId }?.value
        val here = agentsHere(profileId)
        if (graph == null || !CardsQualification.OPEN_CODE_2_QUALIFIED) {
            return Outcome.Ok(CardsVerdict(emptyList(), CardsProblem.NotQualified, here))
        }
        return Outcome.Ok(CardsQualification.verdict((graph as ChatGraphAccessors).chatOperations.mcpServers(), here))
    }
}

/** One per app: the availability state and the per-conversation models. */
@SingleIn(AppScope::class)
@Inject
public class ChatCards(
    private val registry: ConnectionRegistry,
    prefs: Set<CardsPrefs> = emptySet(),
    journals: Set<CardJournalStore> = emptySet(),
) {
    public val setup: CardsSetup = CardsSetup(prefs.firstOrNull() ?: MemoryCardsPrefs(), GraphCardsQualifier(registry))
    private val journal: CardJournal = journals.firstOrNull()?.let(::StoredCardJournal) ?: MemoryCardJournal()

    public fun open(ref: ConversationRef): ChatCardsModel = ChatCardsModel(ref, registry, setup, journal).also { it.attach() }

    /** The Cards row: what is true now. */
    public fun status(profileId: String): CardsStatus = setup.status(profileId)

    public suspend fun setEnabled(profileId: String, on: Boolean): Outcome<Unit> = setup.setEnabled(profileId, on)
}

/**
 * The cards of one open conversation. Lives as long as the chat entry; [close] drops held answers (nothing is sent
 * late) and stops reading.
 */
public class ChatCardsModel internal constructor(
    public val ref: ConversationRef,
    private val registry: ConnectionRegistry,
    private val setup: CardsSetup,
    private val journal: CardJournal = MemoryCardJournal(),
) {
    private val mutable = MutableStateFlow(CardBoardState())
    public val state: StateFlow<CardBoardState> = mutable.asStateFlow()

    private var board: CardBoard? = null
    private var bound: ConnectionGraph? = null
    private val stops = ArrayList<() -> Unit>()
    private var stopWatching: (() -> Unit)? = null
    private val transcript = CardTranscript()
    private var cardScope: CardScope? = null
    private val sid: SessionId get() = ref.sessionId

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

    public fun answer(card: AgentCard, answer: CardAnswer, photos: List<PromptAttachment> = emptyList()) {
        board?.answer(card, answer, photos)
    }

    public fun undo(card: AgentCard) {
        board?.undo(card)
    }

    public fun close() {
        stopWatching?.invoke()
        unbind()
    }

    private fun unbind() {
        stops.forEach { it() }
        stops.clear()
        board?.close()
        board = null
        bound = null
        transcript.clear()
        mutable.value = CardBoardState()
    }

    private fun bind(graph: ConnectionGraph?) {
        unbind()
        bound = graph
        if (graph == null) return
        val services = graph as ChatGraphAccessors
        val gateway = services.chatGateway
        val delivery = services.chatDelivery
        val sender = CardAnswerSender { _, text, photos ->
            when (val sent = delivery.send(ref, text, photos.toImmutableList())) {
                // The id comes back before the request leaves: wait for this send's own result (B-CHAT-050).
                is Outcome.Ok -> {
                    val engine = delivery.engine
                    if (engine == null) Outcome.Failed(Failure.Refused(RefusalReason.DispatchUncertain))
                    else awaitCardSend(sent.value, engine.outcomes, engine.pending)
                }
                is Outcome.Failed -> sent
            }
        }
        // B-CHAT-039/050/052 (Flutter `_send`): a fresh read, then an explicit idle check, right before sending. A
        // card answer is never queued for later and never steered into a running turn.
        val check = CardDispatchCheck { card ->
            if (bound !== graph || card.sessionId != sid.raw) return@CardDispatchCheck CardRefusal.Changed
            if (gateway.events.status.value != StreamStatus.Connected) return@CardDispatchCheck CardRefusal.Changed
            if (!read(gateway)) return@CardDispatchCheck CardRefusal.Changed
            val idle =
                when (val statuses = gateway.sessionStatuses()) {
                    is Outcome.Failed -> false
                    is Outcome.Ok -> statuses.value[sid].let { it == null || it == SessionStatus.Idle }
                }
            if (bound !== graph) return@CardDispatchCheck CardRefusal.Changed
            board?.setBusy(!idle)
            if (!idle || delivery.engine?.routeFor(ref) != SendRoute.Plain) CardRefusal.Busy else null
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
        cardScope = CardScope(ref.profileId.raw, SOURCE, location.directory ?: GLOBAL, location.workspace)
        val profile = ref.profileId.raw
        graph.run { b.state.collect { mutable.value = it } }
        graph.run {
            setup.changes.collect { b.setAvailable(setup.effective(profile)) }
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
                            read(gateway) // reconcile by refetch, not replay
                        }
                    is GatewayEvent.MessageUpserted -> if (e.message.sessionId == sid.raw) upsert(e.message)
                    is GatewayEvent.PartUpserted -> if (e.part.sessionId == sid.raw) upsert(e.part)
                    is GatewayEvent.TranscriptStale -> if (e.sessionId == sid) read(gateway)
                    GatewayEvent.Connected -> read(gateway)
                    // Pure deltas never change a card (B-CHAT-037): input arrives whole when the call completes.
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

    /** One bounded read; true when it was applied (a failed or overtaken read leaves the cards as they were). */
    private suspend fun read(gateway: ServerGateway): Boolean {
        val bounded = gateway.facet<BoundedHistoryGateway>() ?: return false
        val source = CardHistorySource { session, cursor, limit ->
            when (val page = bounded.boundedHistoryPage(SessionId(session), cursor = cursor, limit = limit)) {
                is Outcome.Ok -> Outcome.Ok(CardHistoryPage(page.value.items, page.value.nextCursor, page.value.hasMore))
                is Outcome.Failed -> page
            }
        }
        // A live event during the read means the read is older than what is shown: read again, a few times at most.
        repeat(READ_ATTEMPTS) {
            val mark = transcript.mark()
            val tail = CardHistory.readTail(source, sid.raw)
            if (tail !is Outcome.Ok) return false // read-only stays read-only
            if (transcript.applyRead(mark, tail.value)) {
                publish()
                return true
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
    }

    private companion object {
        /** Flutter `_openCodeSourceId`. */
        const val SOURCE = "opencode"
        const val GLOBAL = "global"
        const val READ_ATTEMPTS = 3
    }
}

/**
 * What a prompt bubble shows for a card answer: only the summary line of a fully valid receipt (plan 09 §13.4: the
 * JSON line is hidden only for a validated receipt); any other text unchanged.
 */
public object CardAnswerDisplay {
    public fun text(raw: String): String {
        if (!raw.startsWith("[oc-ui answer ")) return raw
        val message =
            MessageWithParts(
                MessageInfo("display", "display", io.github.eslamasabry.oc.core.model.transcript.Role.User),
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
        return io.github.eslamasabry.oc.engine.cards.CardCodec.answerEnvelope(message)?.second ?: raw
    }
}
