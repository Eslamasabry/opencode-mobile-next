package io.github.eslamasabry.oc.feature.chat.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptRow
import io.github.eslamasabry.oc.feature.chat.world.PendingSend
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf

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
    /**
     * The Back guard is enabled (07 §3.4): the system's predictive preview is off only while this
     * is true.
     */
    val guardBack: Boolean,
    /** The last copy the chat made (E15-S05): the screen announces it once, saying when a key was hidden. */
    val copied: CopiedUi? = null,
    /** P12-CHAT-FEEL: the "Select text" sheet over one message's words; null = closed. */
    val select: SelectTextUi? = null,
    /**
     * P12-CHAT-FEEL: words "Edit and resend" puts in the composer once, then acknowledged
     * ([ChatActions.insertDone]).
     */
    val insert: ClipUi? = null,
    /** P12-CHAT-FEEL: the picture viewer over the chat; null = closed. */
    val picture: PictureUi? = null,
    /**
     * A reply the person asked to hear (P12-VOICE): the screen hands it to the reader once, then
     * acknowledges.
     */
    val readAloud: ReadAloudUi? = null,
    /** B-CHAT-064: file paths in replies the server confirmed exist; only these become tappable. */
    val confirmedPaths: ImmutableSet<String> = persistentSetOf(),
    val timeline: TimelineUi? = null,
    val reasoningExpanded: Boolean = false,
    /** Lookup-only receipt checks, kept for this visit; never authorizes another send. */
    val receiptChecks: ImmutableMap<Long, ReceiptCheckUi> = persistentMapOf(),
    /** S28 story 3: undone turns can come back (the launcher's `/redo`). */
    val canPutBack: Boolean = false,
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

/**
 * The one status line (plan 09 §6.5, 07 §10.1). [id] is stable per condition (tag
 * `chat-status-<id>`).
 */
public data class ChatStatusUi(
    val id: String,
    val kind: ChatStatusKind,
    val failure: Failure? = null,
    val localId: Long? = null,
    val receiptResult: ReceiptCheckResult? = null,
    /**
     * S24-CHAT (Flutter `countdownLine`): whole seconds until the link tries again by itself; 0 =
     * "trying again now"; null = no try is scheduled. Only on the connection lines.
     */
    val retryInSeconds: Int? = null,
    /** This conversation's server runs on this phone: the line offers Restart (after a confirm). */
    val canRestart: Boolean = false,
)

/** A receipt check is only a read. Its timestamp is intentionally not persisted. */
public data class ReceiptCheckUi(
    val checking: Boolean = false,
    val checkedAtMs: Long? = null,
    val result: ReceiptCheckResult? = null,
)

public enum class ReceiptCheckResult {
    Confirmed,
    StillUnknown,
    StorageProblem,
}

/**
 * Lower ordinal = more urgent. Connection first (07 §10.1), then the chat's own lines in plan 09
 * §6.5's order.
 */
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

    /** P12-CHAT-FEEL: a link in a reply the app's link policy will not open. */
    LinkRefused,

    /** The result of the person's lookup; dismissing it never changes the receipt. */
    ReceiptChecked,

    /** S28 story 3: a staged undo waits for review ("Review"). */
    StagedUndo,

    /** S28 story 3: turns were undone at once; "Put back" restores them. */
    Undone,
}

/**
 * The live line while a turn runs (plan 09 §4.1, trimmed: no step count until S2-DLV's
 * `ConversationActivity`).
 */
public data class LiveTurnUi(
    val stopping: Boolean,
    /** S24-CHAT (Flutter `_stopAfterStepPending`): the stop note went; the line says it waits for the step. */
    val stoppingAfterStep: Boolean = false,
    /** Hold Stop offers "Stop after this step": the server steers a running turn and no note went yet. */
    val canStopAfterStep: Boolean = false,
)

/**
 * Find (plan 09 §6.6, Flutter `transcript_find.dart`): hits in reading order, "3 of 12". [current]
 * is 0-based (-1 with no hits); [currentIndex] is the newest-first row index of the current hit's
 * row in [ChatUi.rows] (-1 if none).
 */
public data class FindUi(
    val query: String,
    val count: Int,
    val current: Int,
    val currentKey: String?,
    val currentIndex: Int,
    /** Some history is not loaded, so not all of it was searched. */
    val partial: Boolean,
    val searchingAll: Boolean = false,
    val loadingOlder: Boolean = false,
    val olderFailure: Failure? = null,
    val excerpt: String? = null,
)

public data class TimelineEntryUi(
    val messageId: String,
    val rowKey: String,
    val prompt: Boolean,
    val preview: String,
    val createdMs: Long?,
    val forkable: Boolean,
)

public data class TimelineUi(
    val query: String,
    val entries: ImmutableList<TimelineEntryUi>,
    val partial: Boolean,
    val loadingOlder: Boolean,
    val olderFailure: Failure?,
)

/**
 * A message a menu acts on. [rowKey] is the row it was opened from; [messageId] null = not
 * addressable (no delete).
 */
public data class MessageTarget(
    val turnId: String,
    val rowKey: String,
    val kind: TargetKind,
    val messageId: String?,
)

public enum class TargetKind {
    Prompt,
    Reply,
}

/**
 * One action list per message for every way in (plan 09 §6.6). Order is the menu's order;
 * destructive last.
 */
public enum class MessageAction {
    Copy,
    Share,
    SelectText,
    /** P12-VOICE: hear a reply (shown only where the app has a reply reader). */
    ReadAloud,
    /**
     * S28 story 7 (Flutter `message-menu-read-aloud-voice`): the same reply, after choosing a voice. The menu offers
     * it beside [ReadAloud]; the action list never carries it on its own.
     */
    ReadAloudOtherVoice,
    EditAndResend,
    SendAgain,
    Fork,
    /** P12-REVIEW: the files the reply's turn changed (ReviewRoute.Changes). */
    Changes,
    /**
     * "Undo from here" on a prompt: P12-REVIEW's page (ReviewRoute.UndoFromHere), staged or
     * one-step.
     */
    UndoFromHere,
    Delete,
}

public data class MessageMenuUi(
    val target: MessageTarget,
    val actions: ImmutableList<MessageAction>,
)

public sealed interface ChatConfirm {
    /**
     * "Delete this message?" (plan 09 §6.6): no Undo bar afterwards; the message leaving is the
     * confirmation.
     */
    public data class DeleteMessage(val target: MessageTarget) : ChatConfirm

    /** The draft could not be saved on Back (07 §3.4): leave without saving, or keep editing. */
    public data object LeaveUnsaved : ChatConfirm

    /**
     * "Undo from here?" (Flutter `revertFromHere`, B-CHAT-120): the prompt and everything after it
     * go.
     */
    public data class UndoFrom(val target: MessageTarget) : ChatConfirm

    /**
     * A link from a reply asks first, naming where it goes (R17: policy → opener, never straight to
     * the platform).
     */
    public data class OpenLink(val url: String, val host: String, val insecure: Boolean = false) :
        ChatConfirm

    /** Flutter offerPhoneFile: "Open {name}?" / "Install {name}?" before the file is handed to Android. */
    public data class PhoneFile(val path: String, val install: Boolean) : ChatConfirm

    /** Android asked for file access; the person comes back and tries again (visible Retry). */
    public data class PhoneFileAccess(val path: String) : ChatConfirm

    /** Flutter `compact` in the session menu: "Shorten this conversation?" before it runs. */
    public data object Compact : ChatConfirm

    /**
     * Flutter `confirmPhoneServerRestart`: "Restart OpenCode on this phone?" (a running turn stops;
     * conversations are kept).
     */
    public data object RestartPhoneServer : ChatConfirm
}

/**
 * A reply's server picture in the viewer. [bytes] null while loading; [failed] when it could not be
 * read.
 */
public class PictureUi(
    public val id: Long,
    public val name: String,
    public val bytes: ByteArray?,
    public val failed: Boolean,
    /** S24-CHAT story 3: every server picture of the conversation, to swipe between; [index] is this one. */
    public val gallery: List<GalleryPicture> = emptyList(),
    public val index: Int = 0,
)

/**
 * One message's words in a sheet where any part of them can be selected (Flutter's selectable
 * message).
 */
public data class SelectTextUi(val id: Long, val text: String)

public data class ClipUi(val id: Long, val text: String)

/** One copy made ([id] grows with each); [masked] = the redactor hid something that looked like a password or key. */
public data class CopiedUi(val id: Long, val masked: Boolean)

public data class ReadAloudUi(
    val id: Long,
    val rowKey: String,
    val text: String,
    /** S28 story 7: the reader asks which voice first ("Read aloud with another voice"). */
    val chooseVoice: Boolean = false,
)

/**
 * One-time details the chat was opened with, acknowledged through [ChatActions.arrivalShown] (07
 * §2.5). [landOnIndex] is the newest-first row index to bring into view once (the newest failed
 * turn), or null.
 */
public data class ChatArrivalUi(val id: Long, val focusComposer: Boolean, val landOnIndex: Int?)

/** The flags the screen reads, never the backend kind (ADR §9.1). */
public data class ChatCapabilitiesUi(
    val fork: Boolean = false,
    val deleteMessage: Boolean = false,
    /** `sessionRevert`: Undo from here (P12-REVIEW). */
    val undo: Boolean = false,
    /** A reply's turn can list its changed files (P12-REVIEW). */
    val changes: Boolean = false,
    val compact: Boolean = false,
    /** `conversationShellOn`: Run shell command. */
    val shell: Boolean = false,
    val files: Boolean = false,
    val slashCommands: Boolean = false,
    /** S24-CHAT launcher gates (Flutter `_builtinChatCommands`). */
    val terminal: Boolean = false,
    val serverCatalog: Boolean = false,
    val agents: Boolean = false,
    val approvals: Boolean = false,
    /** Language services or formatters report a status (Project health has something to show). */
    val health: Boolean = false,
)

/**
 * S24-CHAT story 2: the places the commands launcher opens, named as Flutter's commands name them.
 * The presenter turns each into this conversation's route (same server, same project).
 */
public enum class ChatPlace {
    Workspaces,
    Health,
    Terminal,
    McpServers,
    Providers,
    Skills,
    References,
    ServerStatus,
    Diagnostics,
    Appearance,
}

/** Intents, named after what the person wants (ADR §9.1), never after widgets. */
public interface ChatActions {
    /** Back (button, gesture or Esc): pops when nothing is lost (07 §3.4). */
    public fun leaveRequested()

    /** Opens the Git page of this conversation's project (07 D09). */
    public fun openGit() {}

    public fun confirmLeave()

    public fun dismissConfirm()

    public fun retryLoad()

    /** "Report a problem" from the could-not-load page (Flutter chat_states.dart:85, `product-error-report-bug`). */
    public fun reportProblem() {}

    /**
     * S24-CHAT story 6 (Flutter `_stopAfterThisStep`): [note] ("Please stop after the step you are on
     * now…") steers the running turn; the draft stays as it is. A tap on Stop still stops at once.
     */
    public fun stopAfterStep(note: String) {}

    /** S24-CHAT: a commands-launcher place for this conversation (Flutter `_ChatCommandAction`). */
    public fun openPlace(place: ChatPlace) {}

    /** Opens Project Files for this conversation (P12-FILES). */
    public fun openFiles() {}

    public fun openTools() {}

    public fun stop()

    /** Opens find with [query]; null closes it. */
    public fun find(query: String?)

    public fun findNext()

    public fun findPrevious()

    /** The earlier-messages pill (plan 09 §7.3 rule 6). */
    public fun loadOlder() {}

    public fun openTimeline()

    public fun closeTimeline() {}

    public fun timelineQuery(query: String) {}

    public fun timelineSelect(messageId: String, fork: Boolean = false) {}

    public fun searchAllHistory() {}

    public fun cancelSearchAll() {}

    public fun toggleReasoning() {}

    public fun compactAgain() {}

    /** The menu's "Shorten conversation": asks first (nothing runs until [confirmCompact]). */
    public fun compactRequested() {}

    /** The person said yes to "Shorten this conversation?". */
    public fun confirmCompact() {}

    /** Flutter "Try now" on the connection line: try the link at once, and read again if needed. */
    public fun tryLinkNow() {}

    /** Flutter "Restart OpenCode" on the connection line: asks first ([ChatConfirm.RestartPhoneServer]). */
    public fun restartPhoneServer() {}

    public fun confirmRestartPhoneServer() {}

    /**
     * From a prompt (tap or long-press) or a reply's footer; the ViewModel resolves the message.
     */
    public fun openMessageMenu(rowKey: String)

    /** The conversation's changed files (P12-REVIEW, ReviewRoute.Changes). */
    public fun openChanges() {}

    public fun closeMessageMenu()

    public fun messageAction(target: MessageTarget, action: MessageAction)

    public fun confirmDelete()

    public fun clipDone(id: Long)

    /** The reader took the reply ([ChatUi.readAloud]). */
    public fun readAloudDone(id: Long) {}

    public fun arrivalShown(id: Long)

    /** The list reached or left the live edge (read acknowledgement, plan 09 §6.7). */
    public fun liveEdgeChanged(atLiveEdge: Boolean)

    /** Looks up an unconfirmed send. Never sends the prompt again. */
    public fun checkReceipt(localId: Long) {}

    public fun retrySend(localId: Long)

    /**
     * Only a line whose dismissal changes nothing real (a finished action's failure) can be
     * dismissed.
     */
    public fun dismissStatus(id: String)

    // ---- P12-CHAT-FEEL (defaults keep other implementers compiling) ----

    /**
     * A link tapped in a reply (or a picture that lives on the web): checked by the app's link
     * policy first.
     */
    public fun openLink(url: String) {}

    /** The person said yes to the "Open link?" confirm. */
    public fun confirmOpenLink() {}

    /** Copy from inside a reply (a code block's Copy): the given words, verbatim. */
    public fun copyText(text: String) {}

    public fun closeSelection() {}

    /** The composer took the "Edit and resend" words. */
    public fun insertDone(id: Long) {}

    /** The person confirmed "Undo from here". */
    public fun confirmUndo() {}

    /** A picture tile that names a file on the server: read it and show it in the viewer. */
    public fun openPicture(path: String, name: String) {}

    /** A server image thumbnail uses the same bounded, identity-fenced read as the viewer. */
    public suspend fun readPicture(path: String): ByteArray? = null

    /**
     * B-CHAT-064 / B-CHAT-114: a file a reply names (a confirmed path) or a step produced: a
     * picture opens in the viewer, any other file in the file viewer (which offers Open with and
     * Share).
     */
    public fun openPath(path: String, line: Int?) {}

    /** The person said yes to "Open {name}?"/"Install {name}?", or tries again after allowing file access. */
    public fun confirmPhoneFile() {}

    /**
     * B-CHAT-064: the path-like text a reply shows; the ones that exist on the server become
     * tappable.
     */
    public fun checkPaths(paths: List<String>) {}

    public fun closePicture() {}

    /**
     * B-AGENT-024: a reply that stopped on a lost connection to the model's provider offers
     * Continue: [words] (the localized "Continue") are sent alone, never the draft or its files
     * (Flutter `_continueTruncated`).
     */
    public fun continueReply(words: String) {}

    /** Recovery for a missing/rejected provider credential; opens the visible settings path. */
    public fun openSignInSettings() {}

    /** Hold Send / "Send later or repeat": opens the schedule editor for this conversation. */
    public fun sendLater(words: String) {}

    /** N-44: set (a new id each time) once a Send later schedule was saved from this chat; the composer clears its draft. */
    public val laterScheduled: kotlinx.coroutines.flow.StateFlow<Long?>
        get() = kotlinx.coroutines.flow.MutableStateFlow(null)

    /** The composer showed the Send later confirmation for [id]. */
    public fun laterShown(id: Long) {}

    /** Commands launcher: Retry last prompt (the newest prompt's words alone, once more). */
    public fun retryLastPrompt() {}

    /** Commands launcher: Revert last prompt (opens the Undo from here page for the newest prompt). */
    public fun undoLastPrompt() {}

    /**
     * S28 story 3 (Flutter `_restore`, `/redo`): undone turns come back: a staged undo opens its review page, an
     * undo the server applied at once is put back.
     */
    public fun putBack() {}

    /** Commands launcher: /new and /sessions, the app's own commands. */
    public fun openNewConversation() {}

    public fun openConversations() {}

    /** Error card: "Open providers" (the provider sign-ins of this conversation's server). */
    public fun openProviders() {}

    /**
     * Error card: "Use {model} and resend". Switches this conversation to the model, then sends
     * the failed turn's prompt again as it was ([turnId] names the turn).
     */
    public fun useModelAndResend(turnId: String, providerId: String, modelId: String) {}
}
