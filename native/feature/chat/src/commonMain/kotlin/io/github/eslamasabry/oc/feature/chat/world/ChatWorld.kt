package io.github.eslamasabry.oc.feature.chat.world

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.PromptAttachment
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptState
import kotlin.coroutines.CoroutineContext
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.StateFlow

/**
 * What one open conversation reads and does: the frozen surface the chat's ViewModels code against
 * (plan 09 §6.2).
 *
 * It is a seam, not an engine. Every flow is the engine's truth passed through: [transcript] is
 * `:engine:transcript`'s handle, [pending] and [send] are `:engine:delivery`'s (S2-DLV), [draft] is
 * `:engine:composer`'s drafts (S3). Until those land, [GraphChatWorld] binds them to the gateway
 * with the plan's rules (bubble first, never resend on its own); each binding names the unit that
 * replaces it. The screen and its tests never see the difference.
 *
 * One instance per open conversation, shared by the chat and its composer through [ChatWorlds].
 */
public interface ChatWorld {
    public val requests:
        StateFlow<io.github.eslamasabry.oc.feature.chat.requests.presentation.ChatRequestsModel?>
        get() = NoRequests

    public val ref: ConversationRef

    /** Newest-first rows, published at most every 50 ms by the engine (plan 09 §3.5). */
    public val transcript: StateFlow<TranscriptState>

    /** First load and reloads (plan 09 §6.5). */
    public val load: StateFlow<ChatLoad>

    /**
     * The conversation's title as the server names it; null until read. Server text, shown as is.
     */
    public val title: StateFlow<String?>

    /**
     * The conversation's own backend's flags (ADR §9.1: gate on capabilities, never on the backend
     * kind).
     */
    public val capabilities: StateFlow<ServerCapabilities>

    /**
     * This conversation's backend link (07 §10.1: a conversation page shows its own backend's
     * condition).
     */
    public val link: StateFlow<ChatLink>

    /** Optimistic prompt bubbles not yet in the transcript, oldest first (plan 09 §4.6). */
    public val pending: StateFlow<ImmutableList<PendingSend>>

    /** The composer's words against the last saved draft (07 §3.4 Back guard). */
    public val draft: StateFlow<DraftFacts>

    /**
     * The models this conversation's server offers, safe for display; empty until [loadModels]
     * succeeds. A selection lives in the server session, never in a local preference.
     */
    public val models: StateFlow<ComposerModels>
        get() = NoModels

    /** The server's own slash commands; empty until [loadCommands] succeeds. */
    public val commands: StateFlow<List<ComposerCommand>>
        get() = NoCommands

    public suspend fun loadCommands(): Outcome<Unit> =
        Outcome.Failed(Failure.Unavailable(FeatureId("slashCommands")))

    /** Project files whose path matches [query] (the `@` list); paths relative to the project. */
    public suspend fun findFiles(query: String): Outcome<List<String>> =
        Outcome.Failed(Failure.Unavailable(FeatureId("fileBrowsing")))

    /**
     * Stages the project file [path] the person picked from the `@` list; it is sent only while its
     * `@path` stays in the words.
     */
    public suspend fun mentionFile(path: String): Outcome<Unit> =
        Outcome.Failed(Failure.Unavailable(FeatureId("fileBrowsing")))

    /** True while the server offers an older history page. */
    public val hasOlder: StateFlow<Boolean>
        get() = NoOlder

    public suspend fun loadOlder(): Outcome<Unit> = Outcome.Ok(Unit)

    /** The connection's work context for pure search work off the main thread. */
    public val work: CoroutineContext

    /** The composer reports every change of its text (ADR §9.2 `onDraftChanged`). */
    public fun draftChanged(text: String)

    /**
     * Local-only save of the current draft. Refused while drafts are not stored (slice 2) or
     * storage refuses.
     */
    public suspend fun saveDraft(): Outcome<Unit>

    /**
     * Replaces the draft's attached files. Only the storage-acknowledged snapshot is published; a
     * refusal keeps the previous chips and failure.
     */
    public suspend fun replaceAttachments(files: List<PromptAttachment>): Outcome<Unit> =
        Outcome.Failed(Failure.Unavailable(FeatureId("draftAttachments")))

    /** First read of the model catalog and the conversation's current selection. */
    public suspend fun loadModels(): Outcome<Unit> =
        Outcome.Failed(Failure.Unavailable(FeatureId("serverCatalog")))

    /**
     * Moves the conversation to [choice] in the server session itself; null asks for the catalog
     * default. A choice is published only after the server acknowledged it, and a failed selection
     * never replaces the previous one.
     */
    public suspend fun selectModel(choice: ComposerModel?, variant: String = ""): Outcome<Unit> =
        Outcome.Failed(Failure.Unavailable(FeatureId("sessionModelProviderSwitching")))

    /** Changes only the personal shortcut; never the conversation's model. */
    public suspend fun toggleModelFavorite(choice: ComposerModel): Outcome<Unit> =
        Outcome.Failed(Failure.Unavailable(FeatureId("modelFavorites")))

    /**
     * Moves the conversation to the primary [agent] in the server session, verified by readback.
     */
    public suspend fun selectAgent(agent: String): Outcome<Unit> =
        Outcome.Failed(Failure.Unavailable(FeatureId("agentSelection")))

    /**
     * Changes whenever the draft's owner is replaced; a file pick that began under another
     * generation writes nothing.
     */
    public val draftGeneration: Long
        get() = 0

    /**
     * Bubble first, then one dispatch; a lost answer stays unconfirmed and is never resent on its
     * own.
     */
    public suspend fun send(text: String): Outcome<Unit>

    /**
     * Send again (plan 09 §6.6): an earlier prompt's [text] alone. The draft's files never ride
     * along and the draft stays as it is (Flutter `_sendInsteadOfDraft` sets the draft's
     * attachments aside).
     */
    public suspend fun sendAgain(text: String): Outcome<Unit> = send(text)

    /**
     * Explicit repeat of this exact draft revision, permitted only for canonical-history ambiguity.
     */
    public suspend fun sendRepeated(text: String, expectedRevision: Long): Outcome<Unit> =
        Outcome.Failed(
            Failure.Unavailable(io.github.eslamasabry.oc.core.common.FeatureId("draftRepeat"))
        )

    /** The person's own Try again on a failed bubble: one new dispatch of the same words. */
    public suspend fun retrySend(localId: Long): Outcome<Unit>

    /** Asks the server to stop the running reply. */
    public suspend fun stop(): Outcome<Unit>

    /**
     * [stop], saying whether the server was asked: `Ok(false)` is a tap the guard after a send
     * ignored (it asked nothing), so Stop stays usable (Codex chat-bugs 2, Flutter's ignored-tap
     * guard).
     */
    public suspend fun stopTurn(): Outcome<Boolean> =
        when (val r = stop()) {
            is Outcome.Ok -> Outcome.Ok(true)
            is Outcome.Failed -> r
        }

    /** Reads the conversation again (Try again on a failed load; after a delete). */
    public suspend fun reload(): Outcome<Unit>

    /** A new conversation that starts from [from]; its id. */
    public suspend fun fork(from: MessageId): Outcome<SessionId>

    /**
     * Deletes one message on the server; on success the transcript drops it and reloads (plan 09
     * §6.6).
     */
    public suspend fun deleteMessage(id: MessageId): Outcome<Unit>

    /**
     * P12-CHAT-FEEL "Undo from here" (B-CHAT-120, servers that undo directly): the prompt [from]
     * and everything after it are undone on the server, then the conversation is read again.
     */
    public suspend fun revertFrom(from: MessageId): Outcome<Unit> =
        Outcome.Failed(Failure.Unavailable(FeatureId("sessionRevert")))

    /**
     * P12-CHAT-FEEL: the bytes of a picture a reply names by its server path (B-CHAT-059), for the
     * in-chat viewer. Read through the conversation's own file transport; never a web fetch.
     */
    public suspend fun readPicture(path: String): Outcome<ByteArray> =
        Outcome.Failed(Failure.Unavailable(FeatureId("fileBrowsing")))

    /**
     * B-CHAT-064: whether [path] names a file on the server (Flutter `_checkPathLink`: its folder
     * is listed and the name found). A bare name without a folder is never checked.
     */
    public suspend fun fileExists(path: String): Outcome<Boolean> =
        Outcome.Failed(Failure.Unavailable(FeatureId("fileBrowsing")))

    /** The visible transcript was read at the live edge (plan 09 §6.7). */
    public fun markRead()
}

/**
 * The conversation's load phase (plan 09 §3.1 `TranscriptPhase`, without the excerpt until S2-TX
 * adds it).
 */
public sealed interface ChatLoad {
    public data object Loading : ChatLoad

    public data object Live : ChatLoad

    public data class Failed(val failure: Failure) : ChatLoad
}

/** This conversation's backend, as one fact (07 §10.1). */
public enum class ChatLink {
    Live,
    Connecting,
    Offline,
}

/** A prompt the person sent that the transcript does not show yet. */
public data class PendingSend(
    val localId: Long,
    val text: String,
    val state: PendingState,
    val failure: Failure? = null,
)

public enum class PendingState {
    /** On its way; the bubble shows at once (bubble first). */
    Sending,

    /**
     * The server did not confirm it; it may already be in the conversation. Never resent on its
     * own.
     */
    Unconfirmed,

    /** It did not leave; Try again sends it once more. */
    Failed,
}

/** [text] is the composer's; [saved] is the last stored draft ("" when none). */
public data class DraftFacts(
    val text: String = "",
    val saved: String = "",
    val ready: Boolean = true,
    val failure: Failure? = null,
    val restoreRevision: Long = 0,
    val sendBlocked: Boolean = false,
    val editRevision: Long = 0,
    val repeatAllowed: Boolean = false,
    val attachments: List<PromptAttachment> = emptyList(),
    val savedAttachments: List<PromptAttachment> = emptyList(),
) {
    /**
     * The Back guard is on only while there is something to lose (07 §3.4). Whitespace without
     * files is nothing; files alone are unsent work.
     */
    public val unsaved: Boolean
        get() =
            if (
                text.isBlank() &&
                    saved.isBlank() &&
                    attachments.isEmpty() &&
                    savedAttachments.isEmpty()
            )
                false
            else text != saved || attachments != savedAttachments

    override fun toString(): String =
        "DraftFacts(ready=$ready, failed=${failure != null}, restoreRevision=$restoreRevision)"
}

/**
 * Shares one [ChatWorld] per conversation between the chat entry and its composer (ref-counted).
 */
public interface ChatWorlds {
    public fun open(ref: ConversationRef): ChatWorld

    public fun release(world: ChatWorld)
}

private val NoRequests =
    kotlinx.coroutines.flow.MutableStateFlow<
        io.github.eslamasabry.oc.feature.chat.requests.presentation.ChatRequestsModel?
    >(
        null
    )

private val NoOlder = kotlinx.coroutines.flow.MutableStateFlow(false)

private val NoModels = kotlinx.coroutines.flow.MutableStateFlow(ComposerModels())

private val NoCommands =
    kotlinx.coroutines.flow.MutableStateFlow<List<ComposerCommand>>(emptyList())
