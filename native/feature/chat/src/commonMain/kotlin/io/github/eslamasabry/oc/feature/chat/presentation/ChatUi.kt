package io.github.eslamasabry.oc.feature.chat.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptRow
import io.github.eslamasabry.oc.feature.chat.world.PendingSend
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/*
 * The chat screen's state and intents (plan 09 §6.1). No Compose, no copy: ids, enums, Failure and the person's or
 * server's own text. The rows are the engine's list passed through untouched; every other per-row fact is a side table
 * keyed by row key, so a change elsewhere never copies the transcript.
 */

public data class ChatUi(
    /** +1 per change; first so StateFlow's equality stops here (ADR §9.3). */
    val revision: Long,
    val ref: ConversationRef,
    val title: String?,
    /** The one status line, most urgent first (plan 09 §6.5); null = none. */
    val status: ChatStatusUi?,
    val phase: ChatPhase,
    /** NEWEST FIRST, identity-stable (plan 09 §7.1). */
    val rows: ImmutableList<TranscriptRow>,
    /** Key of the newest engine row (the list follows when it changes). */
    val newestRowKey: String?,
    /** The live line: a real item only while a turn runs (plan 09 §7.1). */
    val live: LiveTurnUi?,
    /** Optimistic bubbles, NEWEST FIRST to match the list (plan 09 §4.6). */
    val pending: ImmutableList<PendingSend>,
    val find: FindUi?,
    val menu: MessageMenuUi?,
    val confirm: ChatConfirm?,
    /** Text the screen puts on the clipboard once, then acknowledges (no channel; ADR §9.1). */
    val clip: ClipUi?,
    val arrival: ChatArrivalUi?,
    val capabilities: ChatCapabilitiesUi,
    /** The Back guard is enabled (07 §3.4): the system's predictive preview is off only while this is true. */
    val guardBack: Boolean,
) {
    public companion object {
        public fun loading(ref: ConversationRef): ChatUi =
            ChatUi(
                revision = 0,
                ref = ref,
                title = null,
                status = null,
                phase = ChatPhase.Loading,
                rows = persistentListOf(),
                newestRowKey = null,
                live = null,
                pending = persistentListOf(),
                find = null,
                menu = null,
                confirm = null,
                clip = null,
                arrival = null,
                capabilities = ChatCapabilitiesUi(),
                guardBack = false,
            )
    }
}

public sealed interface ChatPhase {
    /** Loading bar + skeleton, never an empty-state text (plan 09 §6.5). */
    public data object Loading : ChatPhase

    public data object Live : ChatPhase

    /** "Could not load": which step failed, Try again (plan 09 §6.5). */
    public data class Failed(val failure: Failure) : ChatPhase
}

/** The one status line (plan 09 §6.5, 07 §10.1). [id] is stable per condition (tag `chat-status-<id>`). */
public data class ChatStatusUi(val id: String, val kind: ChatStatusKind, val failure: Failure? = null, val localId: Long? = null)

/** Lower ordinal = more urgent. Connection first (07 §10.1), then the chat's own lines in plan 09 §6.5's order. */
public enum class ChatStatusKind {
    /** This conversation's backend is unreachable. */
    Offline,

    /** Reconnecting to this conversation's backend. */
    Connecting,

    /** A send failed (Try again on the line). */
    SendFailed,

    /** A send's answer was lost; it may already be in the conversation. */
    SendUnconfirmed,

    /** The newest prompt's reply failed and no message says so already. */
    RunFailed,

    /** A message action (fork, delete, stop) did not finish. */
    ActionFailed,
}

/** The live line while a turn runs (plan 09 §4.1, trimmed: no step count until S2-DLV's `ConversationActivity`). */
public data class LiveTurnUi(val stopping: Boolean)

/**
 * Find (plan 09 §6.6, Flutter `transcript_find.dart`): hits in reading order, "3 of 12". [current] is 0-based (-1 with
 * no hits); [currentIndex] is the newest-first row index of the current hit's row in [ChatUi.rows] (-1 if none).
 */
public data class FindUi(
    val query: String,
    val count: Int,
    val current: Int,
    val currentKey: String?,
    val currentIndex: Int,
    /** Some history is not loaded, so not all of it was searched. */
    val partial: Boolean,
)

/** A message a menu acts on. [rowKey] is the row it was opened from; [messageId] null = not addressable (no delete). */
public data class MessageTarget(val turnId: String, val rowKey: String, val kind: TargetKind, val messageId: String?)

public enum class TargetKind { Prompt, Reply }

/** One action list per message for every way in (plan 09 §6.6). Order is the menu's order; destructive last. */
public enum class MessageAction { Copy, SendAgain, Fork, Delete }

public data class MessageMenuUi(val target: MessageTarget, val actions: ImmutableList<MessageAction>)

public sealed interface ChatConfirm {
    /** "Delete this message?" (plan 09 §6.6): no Undo bar afterwards; the message leaving is the confirmation. */
    public data class DeleteMessage(val target: MessageTarget) : ChatConfirm

    /** The draft could not be saved on Back (07 §3.4): leave without saving, or keep editing. */
    public data object LeaveUnsaved : ChatConfirm
}

public data class ClipUi(val id: Long, val text: String)

/**
 * One-time details the chat was opened with, acknowledged through [ChatActions.arrivalShown] (07 §2.5).
 * [landOnIndex] is the newest-first row index to bring into view once (the newest failed turn), or null.
 */
public data class ChatArrivalUi(val id: Long, val focusComposer: Boolean, val landOnIndex: Int?)

/** The flags the screen reads, never the backend kind (ADR §9.1). */
public data class ChatCapabilitiesUi(val fork: Boolean = false, val deleteMessage: Boolean = false)

/** Intents, named after what the person wants (ADR §9.1), never after widgets. */
public interface ChatActions {
    /** Back (button, gesture or Esc): pops when nothing is lost (07 §3.4). */
    public fun leaveRequested()

    public fun confirmLeave()

    public fun dismissConfirm()

    public fun retryLoad()

    public fun stop()

    /** Opens find with [query]; null closes it. */
    public fun find(query: String?)

    public fun findNext()

    public fun findPrevious()

    /** The earlier-messages pill (plan 09 §7.3 rule 6). */
    public fun openTimeline()

    /** From a prompt (tap or long-press) or a reply's footer; the ViewModel resolves the message. */
    public fun openMessageMenu(rowKey: String)

    public fun closeMessageMenu()

    public fun messageAction(target: MessageTarget, action: MessageAction)

    public fun confirmDelete()

    public fun clipDone(id: Long)

    public fun arrivalShown(id: Long)

    /** The list reached or left the live edge (read acknowledgement, plan 09 §6.7). */
    public fun liveEdgeChanged(atLiveEdge: Boolean)

    public fun retrySend(localId: Long)

    /** Only a line whose dismissal changes nothing real (a finished action's failure) can be dismissed. */
    public fun dismissStatus(id: String)
}
