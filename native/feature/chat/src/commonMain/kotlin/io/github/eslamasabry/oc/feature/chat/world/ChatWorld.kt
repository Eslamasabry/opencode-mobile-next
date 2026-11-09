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
    /** A helper conversation reports to its parent and cannot accept independent replies. */
    public val readOnly: StateFlow<ReadOnlyChat?>
        get() = NoReadOnly

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

    /**
     * S24-CHAT (Flutter `snapshot.nextAttemptAt`): when the link tries again by itself, in epoch
     * ms; null when no try is scheduled (the first connection, or a stop retrying cannot fix).
     */
    public val linkRetryAtMs: StateFlow<Long?>
        get() = NoRetry

    /**
     * Why the link stopped for good, when retrying cannot fix it (a rejected or missing password or
     * token): the connection line then offers Update password / Update token instead of Try now.
     */
    public val linkStopped: StateFlow<Failure?>
        get() = NoStop

    /** The conversation that delegated this one (a subagent's child session); null for a top-level one. */
    public val parent: StateFlow<ConversationRef?>
        get() = NoParent

    /** Flutter "Try now": cancel the wait and try the link at once. Never resends anything. */
    public fun tryLinkNow() {}

    /** The project folder the server reports for this conversation; null until known. */
    public val directory: String?
        get() = null

    /** True when this conversation's server is the one this phone runs (Restart is offered). */
    public val serverOnThisPhone: Boolean
        get() = false

    /** Flutter "Restart OpenCode" for this phone's server, after the person confirmed. */
    public suspend fun restartPhoneServer(): Outcome<Unit> =
        Outcome.Failed(Failure.Unavailable(FeatureId("restartPhoneServer")))

    /** Optimistic prompt bubbles not yet in the transcript, oldest first (plan 09 §4.6). */
    public val pending: StateFlow<ImmutableList<PendingSend>>

    /** The composer's words against the last saved draft (07 §3.4 Back guard). */
    public val draft: StateFlow<DraftFacts>

    /** A confirmed send could not be added to local prompt history. Never a send failure. */
    public val historyFailure: StateFlow<Failure?>
        get() = kotlinx.coroutines.flow.MutableStateFlow(null)

    /** Looks up the original admission receipt only; never dispatches the prompt again. */
    public suspend fun checkReceipt(localId: Long): Outcome<Boolean> =
        Outcome.Failed(Failure.Unavailable(FeatureId("commandReceipts")))

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

    /**
     * Opaque page identity, used only to stop a non-advancing history read. Never displayed or
     * logged.
     */
    public val olderPageKey: String?
        get() = transcript.value.revision.toString()

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
    public suspend fun cycleModel(reverse: Boolean = false): Outcome<Unit> =
        Outcome.Failed(Failure.Unavailable(FeatureId("sessionModelProviderSwitching")))

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
     * B-169: the prompt that started [turnId] as the transcript holds it (words, files, agent, the model its reply ran
     * on and the tool steps it ran), or null when this conversation cannot read it back.
     */
    public fun stoppedPrompt(turnId: String): StoppedPrompt? = null

    /**
     * B-169 §4: [prompt] sent again with the conversation's selected model, carrying its words, its files and its
     * agent (the agent is selected first when the conversation moved on to another one). The draft stays as it is.
     */
    public suspend fun sendAgainWith(prompt: StoppedPrompt): Outcome<Unit> = sendAgain(prompt.text)

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

    /** Whether [id] names a conversation whose parent is this one (server-checked). */
    public suspend fun isChildSession(id: String): Outcome<Boolean> = Outcome.Ok(false)

    public suspend fun compact(): Outcome<Unit> =
        Outcome.Failed(Failure.Unavailable(FeatureId("sessionCompact")))

    /** S30 story 3: what running work this server can move to the background (Flutter `backgroundWorkSupport`). */
    public suspend fun backgroundWorkSupport(): Outcome<io.github.eslamasabry.oc.core.model.BackgroundWorkSupport> =
        Outcome.Ok(io.github.eslamasabry.oc.core.model.BackgroundWorkSupport.Unavailable)

    /** S30 story 3: Prompt tools › Background (Flutter `backgroundSession`): the running work goes on by itself. */
    public suspend fun backgroundWork(): Outcome<io.github.eslamasabry.oc.core.model.BackgroundWorkResult> =
        Outcome.Failed(Failure.Unavailable(FeatureId("experimentalCapabilities")))

    /**
     * Runs one shell command in this conversation's project, by its agent and model (Flutter
     * `_runShellCommand`). Gate on `ServerCapabilities.conversationShellOn`.
     */
    public suspend fun runShell(command: String): Outcome<Unit> =
        Outcome.Failed(Failure.Unavailable(FeatureId("conversationShell")))

    /** The server's background commands (all of them; [ManagedShell.sessionId] says whose). */
    public suspend fun runningShells(): Outcome<io.github.eslamasabry.oc.core.model.ManagedShellList> =
        Outcome.Failed(Failure.Unavailable(FeatureId("developmentServices")))

    public suspend fun shellOutput(
        id: String,
        cursor: Long,
    ): Outcome<io.github.eslamasabry.oc.core.model.ManagedShellOutput> =
        Outcome.Failed(Failure.Unavailable(FeatureId("developmentServices")))

    public suspend fun stopShell(id: String): Outcome<Unit> =
        Outcome.Failed(Failure.Unavailable(FeatureId("developmentServices")))

    /** null [timeout] = no timeout. */
    public suspend fun setShellTimeout(
        id: String,
        timeout: kotlin.time.Duration?,
    ): Outcome<io.github.eslamasabry.oc.core.model.ManagedShell> =
        Outcome.Failed(Failure.Unavailable(FeatureId("developmentServices")))

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
     * S28 story 3 (Flutter `_stagedRevertStatus` / `_undoneStatus`): whether turns of this conversation are undone
     * now, and how they come back (Review the staged undo, or Put back).
     */
    public val undone: StateFlow<ChatUndone>
        get() = NoUndone

    /**
     * S28 story 3 (Flutter `_restore`, `/redo`): brings the undone turns back on a server that undoes at once, then
     * reads the conversation again. A staged undo is reviewed on its own page instead.
     */
    public suspend fun putBack(): Outcome<Unit> =
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

    /** Recheck the originating display after asynchronous work, before acknowledging it. */
    public fun markReadIfVisible(stillVisible: () -> Boolean) {
        if (stillVisible()) markRead()
    }

    /** Read on view, rechecking that this exact conversation display is still current. */
    public fun observeRead(stillCurrent: () -> Boolean): Unit = markReadIfVisible(stillCurrent)
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
    /** The exact dispatched queue record has a receipt on this connection. */
    val canCheckReceipt: Boolean = false,
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
    /**
     * P-003: [sendBlocked] only because the same words are still on their way (a normal send in
     * progress). Send stays off, but nothing is uncertain yet, so no "may already have been sent"
     * error.
     */
    val sendInFlight: Boolean = false,
    val editRevision: Long = 0,
    val repeatAllowed: Boolean = false,
    val attachments: List<PromptAttachment> = emptyList(),
    val savedAttachments: List<PromptAttachment> = emptyList(),
    /**
     * B-144: saved files not shown yet (kept in the saved draft); sending waits for them. Empty
     * while they only wait for the conversation's place.
     */
    val heldBackAttachments: List<String> = emptyList(),
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

public data class ReadOnlyChat(val parent: ConversationRef? = null)

private val NoParent: StateFlow<ConversationRef?> = kotlinx.coroutines.flow.MutableStateFlow(null)
private val NoStop: StateFlow<Failure?> = kotlinx.coroutines.flow.MutableStateFlow(null)
private val NoRetry: StateFlow<Long?> = kotlinx.coroutines.flow.MutableStateFlow(null)

private val NoReadOnly = kotlinx.coroutines.flow.MutableStateFlow<ReadOnlyChat?>(null)

/** S28 story 3: undone turns of a conversation and how they come back. */
public enum class ChatUndone {
    /** Nothing is undone. */
    None,

    /** A staged undo waits for review (OpenCode 2): its page restores or applies it. */
    Staged,

    /** The server undid the turns at once; Put back restores them. */
    Applied;

    public companion object {
        /** Flutter `chat_status_line.dart:89-98`: staged where the server stages, applied elsewhere. */
        public fun of(
            sessionRevert: Boolean,
            stages: Boolean,
            session: io.github.eslamasabry.oc.core.model.Session,
        ): ChatUndone {
            val undone = session.reverted || session.stagedRevert != null
            return when {
                !undone || !sessionRevert -> None
                stages -> Staged
                else -> Applied
            }
        }
    }
}

private val NoUndone: StateFlow<ChatUndone> = kotlinx.coroutines.flow.MutableStateFlow(ChatUndone.None)
