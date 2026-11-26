package io.github.eslamasabry.oc.feature.chat.demo.presentation

import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.PermissionRequest
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

public enum class DemoStage {
    Ready,
    Review,
    Finished,
}

/** One atomic demo transition paired with [newestRowKey] so UI state never lands out of sync with the transcript. */
public data class DemoStep(
    val newestRowKey: String? = null,
    val stage: DemoStage = DemoStage.Ready,
    val request: PermissionRequest? = null,
    val ownReplyKey: String? = null,
)

/**
 * Localized sample content is supplied by the UI, never copied into presentation code. [reply] is what the agent says
 * before it asks; [before] and [after] the sample file's line as it is and as the agent would make it; [allowed] and
 * [denied] what it says after the answer; [more] its answer to anything asked once the sample is over.
 */
public data class DemoWords(
    val title: String,
    val prompt: String,
    val reply: String,
    val allowed: String,
    val denied: String,
    val before: String = "",
    val after: String = "",
    val more: String = "",
    /** The sample model's name on the message box's model chip. */
    val model: String = "",
    /** The honest line for a question of the person's own. */
    val own: String = "",
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

    /**
     * The edit the sample agent asks to make, while it waits for an answer: the same request a server sends, so the
     * demo shows the app's own request card (owner 2026-11-10: two "simulated edit" buttons in place of the message
     * box "feels slop"). Nothing answers it but [decide].
     */
    public val request: MutableStateFlow<PermissionRequest?> = MutableStateFlow(null)

    /** The sample model's name, once the words are known. */
    public val model: MutableStateFlow<String> = MutableStateFlow("")

    /** The row key of the newest off-script reply so the UI can attach one-tap sample and exit actions to it. */
    public val ownReplyKey: MutableStateFlow<String?> = MutableStateFlow(null)

    /** Atomic snapshot of stage, request, and ownReplyKey keyed to [transcript]'s newest row. */
    public val step: MutableStateFlow<DemoStep> = MutableStateFlow(DemoStep())

    public fun configure(content: DemoWords) {
        words = content
        model.value = content.model
        title.value = content.title
        if (stage.value == DemoStage.Ready && transcript.value.rows.isEmpty() && draft.value.text.isEmpty()) {
            draft.value = DraftFacts(restoreRevision = draft.value.restoreRevision + 1)
            draftChanged(content.prompt)
        }
    }

    override fun draftChanged(text: String) {
        if (closed || stage.value == DemoStage.Review) return
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
        if (closed || text.isBlank() || stage.value == DemoStage.Review) {
            return@withLock unavailable()
        }
        sendLocked(text, content)
    }

    /** Sends the suggested sample question in one tap without manual retyping (B-623). */
    public suspend fun runSample(): Outcome<Unit> = lock.withLock {
        val content = words ?: return@withLock unavailable()
        if (closed || stage.value != DemoStage.Ready || content.prompt.isBlank()) {
            return@withLock unavailable()
        }
        sendLocked(content.prompt, content)
    }

    private data class ReplyStep(
        val text: String,
        val stage: DemoStage,
        val request: PermissionRequest?,
        val own: Boolean,
    )

    private fun sendLocked(text: String, content: DemoWords): Outcome<Unit> {
        val next =
            when {
                stage.value == DemoStage.Finished ->
                    ReplyStep(content.more, DemoStage.Finished, null, own = false)
                isSample(text, content) ->
                    ReplyStep(content.reply, DemoStage.Review, editRequest(content), own = false)
                else -> ReplyStep(content.own, DemoStage.Ready, null, own = true)
            }
        val replyKey = appendPair(text, next.text)
        val nextOwn = if (next.own) replyKey else null
        ownReplyKey.value = nextOwn
        request.value = next.request
        stage.value = next.stage
        step.value = DemoStep(replyKey, next.stage, next.request, nextOwn)
        draft.value = DraftFacts(restoreRevision = draft.value.restoreRevision + 1)
        return Outcome.Ok(Unit)
    }

    private fun isSample(text: String, content: DemoWords): Boolean =
        text.trim().equals(content.prompt.trim(), ignoreCase = true)

    private fun editRequest(content: DemoWords): PermissionRequest =
        PermissionRequest(
            id = RequestId("demo-request"),
            sessionId = ref.sessionId,
            permission = "edit",
            patterns = persistentListOf(SAMPLE_FILE),
            metadata =
                buildJsonObject {
                    put("filePath", SAMPLE_FILE)
                    put("diff", "--- a/$SAMPLE_FILE\n+++ b/$SAMPLE_FILE\n@@ -1 +1 @@\n-${content.before}\n+${content.after}\n")
                },
            always = persistentListOf(),
            tool = null,
            canAlwaysAllow = false,
            message = null,
        )

    public suspend fun decide(allow: Boolean): Outcome<Unit> = lock.withLock {
        val content = words ?: return@withLock unavailable()
        if (closed || stage.value != DemoStage.Review) return@withLock unavailable()
        owner.prefs.setBoolean(DECISION, allow)
        val replyKey =
            append(
                if (owner.prefs.boolean(DECISION) == true) content.allowed else content.denied,
                prompt = false,
            )
        request.value = null
        stage.value = DemoStage.Finished
        step.value = DemoStep(replyKey, DemoStage.Finished, null, ownReplyKey.value)
        Outcome.Ok(Unit)
    }

    private fun append(text: String, prompt: Boolean): String {
        val saved = owner.prefs.stringList(HISTORY).orEmpty().toMutableList()
        saved += (if (prompt) "p:" else "r:") + text
        owner.prefs.setStringList(HISTORY, saved)
        publishHistory(saved)
        return "demo-${saved.lastIndex}"
    }

    private fun appendPair(prompt: String, reply: String): String {
        val saved = owner.prefs.stringList(HISTORY).orEmpty().toMutableList()
        saved += "p:$prompt"
        saved += "r:$reply"
        owner.prefs.setStringList(HISTORY, saved)
        publishHistory(saved)
        return "demo-${saved.lastIndex}"
    }

    private fun publishHistory(saved: List<String>) {
        val rows: List<TranscriptRow> =
            saved.mapIndexed { index, stored ->
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
        request.value = null
        ownReplyKey.value = null
        stage.value = DemoStage.Ready
        transcript.value = TranscriptState.Empty.copy(revision = ++sequence)
        step.value = DemoStep(null, DemoStage.Ready, null, null)
        draft.value = DraftFacts(restoreRevision = draft.value.restoreRevision + 1)
        words?.let { draftChanged(it.prompt) }
    }

    public fun close() {
        closed = true
        request.value = null
        ownReplyKey.value = null
        step.value = step.value.copy(request = null, ownReplyKey = null)
        owner.prefs.clear()
    }

    // These capabilities are absent in the demo. Refuse at the world boundary as well as hiding
    // their UI.
    override suspend fun saveDraft(): Outcome<Unit> = unavailable()

    override suspend fun retrySend(localId: Long): Outcome<Unit> = unavailable()

    /** Stop while the sample agent waits for an answer: it stops, and the file stays as it is. */
    override suspend fun stop(): Outcome<Unit> = decide(false)

    override suspend fun reload(): Outcome<Unit> = Outcome.Ok(Unit)

    override suspend fun fork(from: MessageId): Outcome<SessionId> = unavailable()

    override suspend fun deleteMessage(id: MessageId): Outcome<Unit> = unavailable()

    override fun markRead() {
        /* The simulated conversation has no read-state persistence. */
    }

    private fun unavailable(): Outcome.Failed =
        Outcome.Failed(Failure.Unavailable(FeatureId("offlineDemo")))

    private companion object {
        /** The sample project's one file. A file name, not copy: it reads the same in every language. */
        const val SAMPLE_FILE = "welcome.txt"
        const val DRAFT = "oc.demoDraft.offline-demo"
        const val HISTORY = "oc.demoHistory.offline-demo"
        const val DECISION = "oc.demoDecision.offline-demo"
    }
}
