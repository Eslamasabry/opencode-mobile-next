package io.github.eslamasabry.oc.feature.chat.world

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.StateFlow
import kotlin.coroutines.CoroutineContext

/**
 * What one open conversation reads and does: the frozen surface the chat's ViewModels code against (plan 09 §6.2).
 *
 * It is a seam, not an engine. Every flow is the engine's truth passed through: [transcript] is `:engine:transcript`'s
 * handle, [pending] and [send] are `:engine:delivery`'s (S2-DLV), [draft] is `:engine:composer`'s drafts (S3). Until
 * those land, [GraphChatWorld] binds them to the gateway with the plan's rules (bubble first, never resend on its
 * own); each binding names the unit that replaces it. The screen and its tests never see the difference.
 *
 * One instance per open conversation, shared by the chat and its composer through [ChatWorlds].
 */
public interface ChatWorld {
    public val ref: ConversationRef

    /** Newest-first rows, published at most every 50 ms by the engine (plan 09 §3.5). */
    public val transcript: StateFlow<TranscriptState>

    /** First load and reloads (plan 09 §6.5). */
    public val load: StateFlow<ChatLoad>

    /** The conversation's title as the server names it; null until read. Server text, shown as is. */
    public val title: StateFlow<String?>

    /** The conversation's own backend's flags (ADR §9.1: gate on capabilities, never on the backend kind). */
    public val capabilities: StateFlow<ServerCapabilities>

    /** This conversation's backend link (07 §10.1: a conversation page shows its own backend's condition). */
    public val link: StateFlow<ChatLink>

    /** Optimistic prompt bubbles not yet in the transcript, oldest first (plan 09 §4.6). */
    public val pending: StateFlow<ImmutableList<PendingSend>>

    /** The composer's words against the last saved draft (07 §3.4 Back guard). */
    public val draft: StateFlow<DraftFacts>

    /**
     * Where the chat's pure work (find) runs: the connection's default lane, never the main thread (ADR §9.5). A
     * feature cannot see `AppDispatchers` (K1), so the world hands its own context over.
     */
    public val work: CoroutineContext

    /** The composer reports every change of its text (ADR §9.2 `onDraftChanged`). */
    public fun draftChanged(text: String)

    /** Local-only save of the current draft. Refused while drafts are not stored (slice 2) or storage refuses. */
    public suspend fun saveDraft(): Outcome<Unit>

    /** Bubble first, then one dispatch; a lost answer stays unconfirmed and is never resent on its own. */
    public suspend fun send(text: String): Outcome<Unit>

    /** The person's own Try again on a failed bubble: one new dispatch of the same words. */
    public suspend fun retrySend(localId: Long): Outcome<Unit>

    /** Asks the server to stop the running reply. */
    public suspend fun stop(): Outcome<Unit>

    /** Reads the conversation again (Try again on a failed load; after a delete). */
    public suspend fun reload(): Outcome<Unit>

    /** A new conversation that starts from [from]; its id. */
    public suspend fun fork(from: MessageId): Outcome<SessionId>

    /** Deletes one message on the server; on success the transcript drops it and reloads (plan 09 §6.6). */
    public suspend fun deleteMessage(id: MessageId): Outcome<Unit>

    /** The visible transcript was read at the live edge (plan 09 §6.7). */
    public fun markRead()
}

/** The conversation's load phase (plan 09 §3.1 `TranscriptPhase`, without the excerpt until S2-TX adds it). */
public sealed interface ChatLoad {
    public data object Loading : ChatLoad

    public data object Live : ChatLoad

    public data class Failed(val failure: Failure) : ChatLoad
}

/** This conversation's backend, as one fact (07 §10.1). */
public enum class ChatLink { Live, Connecting, Offline }

/** A prompt the person sent that the transcript does not show yet. */
public data class PendingSend(val localId: Long, val text: String, val state: PendingState, val failure: Failure? = null)

public enum class PendingState {
    /** On its way; the bubble shows at once (bubble first). */
    Sending,

    /** The server did not confirm it; it may already be in the conversation. Never resent on its own. */
    Unconfirmed,

    /** It did not leave; Try again sends it once more. */
    Failed,
}

/** [text] is the composer's; [saved] is the last stored draft ("" when none). */
public data class DraftFacts(val text: String = "", val saved: String = "") {
    /** The Back guard is on only while there is something to lose (07 §3.4). Whitespace alone is nothing. */
    public val unsaved: Boolean get() = text.trim() != saved.trim()
}

/** Shares one [ChatWorld] per conversation between the chat entry and its composer (ref-counted). */
public interface ChatWorlds {
    public fun open(ref: ConversationRef): ChatWorld

    public fun release(world: ChatWorld)
}
