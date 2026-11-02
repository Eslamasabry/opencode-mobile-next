package io.github.eslamasabry.oc.feature.chat.cards

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.core.model.SessionStatus
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.engine.cards.AgentCard
import io.github.eslamasabry.oc.engine.cards.CardAgent
import io.github.eslamasabry.oc.engine.cards.CardAnswer
import io.github.eslamasabry.oc.engine.cards.CardAnswerSender
import io.github.eslamasabry.oc.engine.cards.CardBoard
import io.github.eslamasabry.oc.engine.cards.CardBoardState
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

    @Multibinds(allowEmpty = true) public val cardLinkOpeners: Set<CardLinkOpener>
}

/** Opens a card's link after the tap, only through the app's ExternalLinkPolicy (AGENTS.md rule 8). */
public fun interface CardLinkOpener {
    public fun open(url: String)
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
        if (graph == null) return Outcome.Ok(CardsVerdict(emptyList(), CardsProblem.NotQualified, here))
        return when (val servers = (graph as ChatGraphAccessors).chatOperations.mcpServers()) {
            is Outcome.Failed -> Outcome.Ok(CardsVerdict(emptyList(), CardsProblem.NotQualified, here))
            is Outcome.Ok ->
                if (servers.value.any { it.name == HELPER && it.status.equals("connected", ignoreCase = true) }) {
                    Outcome.Ok(CardsVerdict(here))
                } else {
                    Outcome.Ok(CardsVerdict(emptyList(), CardsProblem.NotQualified, here))
                }
        }
    }

    private companion object {
        const val HELPER = "oc-ui"
    }
}

/** One per app: the availability state and the per-conversation models. */
@SingleIn(AppScope::class)
@Inject
public class ChatCards(
    private val registry: ConnectionRegistry,
    prefs: Set<CardsPrefs> = emptySet(),
    links: Set<CardLinkOpener> = emptySet(),
) {
    public val setup: CardsSetup = CardsSetup(prefs.firstOrNull() ?: MemoryCardsPrefs(), GraphCardsQualifier(registry))
    internal val linkOpener: CardLinkOpener? = links.firstOrNull()

    public fun open(ref: ConversationRef): ChatCardsModel = ChatCardsModel(ref, registry, setup).also { it.attach() }

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
) {
    private val mutable = MutableStateFlow(CardBoardState())
    public val state: StateFlow<CardBoardState> = mutable.asStateFlow()

    private var board: CardBoard? = null
    private var bound: ConnectionGraph? = null
    private val stops = ArrayList<() -> Unit>()
    private var stopWatching: (() -> Unit)? = null
    private val messages = LinkedHashMap<String, MessageWithParts>()
    private var complete = false
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
        messages.clear()
        complete = false
        mutable.value = CardBoardState()
    }

    private fun bind(graph: ConnectionGraph?) {
        unbind()
        bound = graph
        if (graph == null) return
        val services = graph as ChatGraphAccessors
        val gateway = services.chatGateway
        val sender = CardAnswerSender { _, text, photos ->
            when (val sent = services.chatDelivery.send(ref, text, photos.toImmutableList())) {
                is Outcome.Ok -> Outcome.Ok(Unit)
                is Outcome.Failed -> sent
            }
        }
        val b = CardBoard(graph.scope, sender, photosAllowed = { gateway.capabilities.value.promptAttachments })
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

    private suspend fun read(gateway: ServerGateway) {
        val source = CardHistorySource { session, cursor, limit ->
            when (val page = gateway.messagePage(SessionId(session), cursor = cursor, limit = limit)) {
                is Outcome.Ok -> Outcome.Ok(CardHistoryPage(page.value.items, page.value.nextCursor, page.value.nextCursor != null))
                is Outcome.Failed -> page
            }
        }
        val tail = CardHistory.readTail(source, sid.raw)
        if (tail !is Outcome.Ok) return // a failed read leaves the cards as they were (read-only stays read-only)
        // A newer view never loses messages the events already brought.
        val live = messages.values.toList()
        messages.clear()
        tail.value.messages.forEach { messages[it.info.id] = it }
        live.filter { it.info.id !in messages }.forEach { messages[it.info.id] = it }
        complete = tail.value.complete
        publish()
    }

    private fun upsert(info: MessageInfo) {
        val old = messages[info.id]
        messages[info.id] = MessageWithParts(info, old?.parts ?: kotlinx.collections.immutable.persistentListOf())
        publish()
    }

    private fun upsert(part: Part) {
        val old = messages[part.messageId] ?: return // its message arrives first; a later read reconciles
        val parts = old.parts.toMutableList()
        val at = parts.indexOfFirst { it.id == part.id }
        if (at >= 0) parts[at] = part else parts += part
        messages[part.messageId] = old.copy(parts = parts.toImmutableList())
        publish()
    }

    private fun publish() {
        val scope = cardScope ?: return
        board?.observe(scope, sid.raw, messages.values.toList(), complete)
    }

    private companion object {
        /** Flutter `_openCodeSourceId`. */
        const val SOURCE = "opencode"
        const val GLOBAL = "global"
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
