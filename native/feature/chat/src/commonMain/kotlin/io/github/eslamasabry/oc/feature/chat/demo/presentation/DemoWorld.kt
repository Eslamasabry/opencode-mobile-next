package io.github.eslamasabry.oc.feature.chat.demo.presentation

import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.engine.connection.demo.DemoProfileStore
import io.github.eslamasabry.oc.engine.transcript.state.BlockRow
import io.github.eslamasabry.oc.engine.transcript.state.NewestFirst
import io.github.eslamasabry.oc.engine.transcript.state.Origin
import io.github.eslamasabry.oc.engine.transcript.state.PromptRow
import io.github.eslamasabry.oc.engine.transcript.state.RowKind
import io.github.eslamasabry.oc.engine.transcript.state.RunFacts
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptRow
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptState
import io.github.eslamasabry.oc.feature.chat.world.ChatLink
import io.github.eslamasabry.oc.feature.chat.world.ChatLoad
import io.github.eslamasabry.oc.feature.chat.world.ChatWorld
import io.github.eslamasabry.oc.feature.chat.world.DraftFacts
import io.github.eslamasabry.oc.feature.chat.world.PendingSend
import kotlin.coroutines.CoroutineContext
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

public enum class DemoStage {
    Ready,
    Review,
    Finished,
}

/** Localized sample content is supplied by the UI, never copied into presentation code. */
public data class DemoWords(
    val title: String,
    val prompt: String,
    val reply: String,
    val allowed: String,
    val denied: String,
)

/**
 * A deliberately simulated, route-owned world consumed by the real chat presenter/screen. No
 * application graph, real profile store, file service or transport can enter this constructor. All
 * state dies when the route leaves.
 */
public class DemoWorld(dispatchers: AppDispatchers) : ChatWorld {
    private val owner = DemoProfileStore()
    private val lock = Mutex()
    private var words: DemoWords? = null
    private var closed = false
    private var sequence = 0L
    public val stage: MutableStateFlow<DemoStage> = MutableStateFlow(DemoStage.Ready)
    override val ref: ConversationRef = ConversationRef(owner.profileId, SessionId("demo-session"))
    override val work: CoroutineContext = dispatchers.default
    override val transcript: MutableStateFlow<TranscriptState> =
        MutableStateFlow(TranscriptState.Empty)
    override val load: MutableStateFlow<ChatLoad> = MutableStateFlow(ChatLoad.Live)
    override val title: MutableStateFlow<String?> = MutableStateFlow(null)
    override val capabilities: MutableStateFlow<ServerCapabilities> =
        MutableStateFlow(
            ServerCapabilities(
                promptAttachments = false,
                offlinePromptQueue = false,
                fileBrowsing = false,
                terminal = false,
                sessionFork = false,
                messageDelete = false,
                sessionRevert = false,
                sessionDiff = false,
                sessionShare = false,
                sessionNotes = false,
                sessionModelProviderSwitching = false,
                agentSelection = false,
                serverCatalog = false,
                slashCommands = false,
                profileAttentionPolling = false,
                permissionRequests = false,
            )
        )
    override val link: MutableStateFlow<ChatLink> = MutableStateFlow(ChatLink.Live)
    override val pending: MutableStateFlow<ImmutableList<PendingSend>> =
        MutableStateFlow(persistentListOf())
    override val draft: MutableStateFlow<DraftFacts> = MutableStateFlow(DraftFacts())

    public fun configure(content: DemoWords) {
        words = content
        title.value = content.title
        if (stage.value == DemoStage.Ready && draft.value.text.isEmpty()) {
            draft.value = DraftFacts(restoreRevision = draft.value.restoreRevision + 1)
            draftChanged(content.prompt)
        }
    }

    override fun draftChanged(text: String) {
        if (closed || stage.value != DemoStage.Ready) return
        owner.prefs.setString(DRAFT, text)
        // Demo drafts never autosave to the application's draft store and are never guarded as
        // durable work.
        draft.value =
            DraftFacts(
                text = owner.prefs.string(DRAFT).orEmpty(),
                saved = text,
                restoreRevision = draft.value.restoreRevision,
            )
    }

    override suspend fun send(text: String): Outcome<Unit> = lock.withLock {
        val content = words ?: return@withLock unavailable()
        if (closed || text.isBlank() || stage.value != DemoStage.Ready)
            return@withLock unavailable()
        append(text, prompt = true)
        append(content.reply, prompt = false)
        stage.value = DemoStage.Review
        draft.value = DraftFacts(restoreRevision = draft.value.restoreRevision + 1)
        Outcome.Ok(Unit)
    }

    public suspend fun decide(allow: Boolean): Outcome<Unit> = lock.withLock {
        val content = words ?: return@withLock unavailable()
        if (closed || stage.value != DemoStage.Review) return@withLock unavailable()
        owner.prefs.setBoolean(DECISION, allow)
        append(
            if (owner.prefs.boolean(DECISION) == true) content.allowed else content.denied,
            prompt = false,
        )
        stage.value = DemoStage.Finished
        Outcome.Ok(Unit)
    }

    private fun append(text: String, prompt: Boolean) {
        val saved = owner.prefs.stringList(HISTORY).orEmpty().toMutableList()
        saved += (if (prompt) "p:" else "r:") + text
        owner.prefs.setStringList(HISTORY, saved)
        val rows: List<TranscriptRow> =
            owner.prefs.stringList(HISTORY).orEmpty().mapIndexed { index, stored ->
                val key = "demo-$index"
                if (stored.startsWith("p:")) PromptRow(key, "demo-turn", stored.substring(2))
                else
                    BlockRow(
                        key,
                        "demo-turn",
                        RowKind.Prose,
                        key,
                        0,
                        stored.substring(2),
                        false,
                        false,
                        Origin.Loaded,
                    )
            }
        transcript.value =
            TranscriptState(
                ++sequence,
                NewestFirst(rows.toPersistentList()),
                rows.mapIndexed { index, row -> row.key to index }.toMap().toPersistentMap(),
                RunFacts(),
            )
    }

    public suspend fun reset() = lock.withLock {
        if (closed) return@withLock
        owner.prefs.clear()
        stage.value = DemoStage.Ready
        transcript.value = TranscriptState.Empty.copy(revision = ++sequence)
        draft.value = DraftFacts(restoreRevision = draft.value.restoreRevision + 1)
        words?.let { draftChanged(it.prompt) }
    }

    public fun close() {
        closed = true
        owner.prefs.clear()
    }

    // These capabilities are absent in the demo. Refuse at the world boundary as well as hiding
    // their UI.
    override suspend fun saveDraft(): Outcome<Unit> = unavailable()

    override suspend fun retrySend(localId: Long): Outcome<Unit> = unavailable()

    override suspend fun stop(): Outcome<Unit> = unavailable()

    override suspend fun reload(): Outcome<Unit> = Outcome.Ok(Unit)

    override suspend fun fork(from: MessageId): Outcome<SessionId> = unavailable()

    override suspend fun deleteMessage(id: MessageId): Outcome<Unit> = unavailable()

    override fun markRead() {
        /* The simulated conversation has no read-state persistence. */
    }

    private fun unavailable(): Outcome.Failed =
        Outcome.Failed(Failure.Unavailable(FeatureId("offlineDemo")))

    private companion object {
        const val DRAFT = "oc.demoDraft.offline-demo"
        const val HISTORY = "oc.demoHistory.offline-demo"
        const val DECISION = "oc.demoDecision.offline-demo"
    }
}
