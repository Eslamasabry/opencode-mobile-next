package io.github.eslamasabry.oc.feature.chat.header.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.engine.agents.resume.SessionResumeCommand
import io.github.eslamasabry.oc.engine.transcript.turnfacts.ContextLevel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/*
 * The conversation header's state and intents (P12 CHAT-HEADER; Flutter chat_top_bar.dart, chat_session_menu.dart,
 * widgets/session_menu.dart). No Compose, no copy: ids, enums, Failure and server text.
 */

public data class ChatHeaderUi(
    /** The conversation's title as the server names it; null = "Untitled conversation". */
    val title: String? = null,
    /** "server · project" parts; either may be absent. */
    val serverName: String? = null,
    val projectName: String? = null,
    /**
     * The ⋯ menu's entries in order (Go to, then Do; destructive last), built from capabilities.
     */
    val menu: ImmutableList<HeaderAction> = persistentListOf(),
    val menuOpen: Boolean = false,
    /** Compact and Fork wait for the first prompt (Flutter `sessionMenuNeedsPrompt`). */
    val hasPrompt: Boolean = false,
    val sheet: HeaderSheet? = null,
    val confirm: HeaderConfirm? = null,
    /** An act is running (the sheet or confirm shows it working). */
    val working: HeaderAction? = null,
    /** The last act's failure, shown as one plain line with Details; dismissible. */
    val failure: HeaderFailure? = null,
    /** Text the screen puts on the clipboard once, then acknowledges. */
    val clip: HeaderClip? = null,
    /** "Free model, slower": once per conversation until dismissed (B-CHAT-015). */
    val freeModelNote: Boolean = false,
    /** The rename dialog is open with this text. */
    val renaming: String? = null,
)

/** What the ⋯ menu offers (Flutter `SessionMenuAction`, the native subset with a real path). */
public enum class HeaderAction {
    Find,
    Details,
    Share,
    Unshare,
    Fork,
    Rename,
    ContinueWith,
    AgentOptions,
    CardsSettings,

    /**
     * "Continue on computer" (Flutter `continueOnComputer`, B-AGENT-227): the copyable resume
     * command.
     */
    ContinueOnComputer,
    CopyTranscript,
    Archive,
    Delete,
}

public sealed interface HeaderSheet {
    /** "Details": server, project, model, agent, context use. */
    public data class Details(val facts: DetailsUi) : HeaderSheet

    /** "Continue with…": this server's other models; picking one moves the conversation to it. */
    public data class ContinueWith(
        val choices: ImmutableList<ContinueChoice>,
        val loading: Boolean,
        val failure: Failure?,
    ) : HeaderSheet

    /**
     * "Continue on computer": the terminal command that resumes this conversation, or why there is
     * none.
     */
    public data class ContinueOnComputer(val command: SessionResumeCommand) : HeaderSheet
}

public data class DetailsUi(
    val serverName: String?,
    val projectName: String?,
    val model: String?,
    val agent: String?,
    /**
     * Whole percent 0–100, or null when the server reported no tokens or no window (B-CHAT-019).
     */
    val contextPercent: Int?,
    val contextLevel: ContextLevel?,
    val totalTokens: Long?,
)

public data class ContinueChoice(
    val providerId: String,
    val modelId: String,
    val label: String,
    val current: Boolean,
)

public sealed interface HeaderConfirm {
    public data class Archive(val title: String?) : HeaderConfirm

    public data class Delete(val title: String?) : HeaderConfirm
}

public data class HeaderFailure(val id: Long, val action: HeaderAction, val failure: Failure)

public enum class ClipKind {
    Transcript,
    ShareLink,
    ResumeCommand,
}

public data class HeaderClip(val id: Long, val text: String, val kind: ClipKind)

/** Intents, named after what the person wants. */
public interface ChatHeaderActions {
    public fun openMenu()

    public fun closeMenu()

    public fun choose(action: HeaderAction)

    /** Copy transcript, headed in the person's own language. */
    public fun copyTranscript(words: TranscriptWords, mask: (String) -> String)

    /** The title was tapped (the visible rename path; the menu has it too). */
    public fun renameRequested()

    /** The rename dialog answered: the new title, or null for cancel/unchanged. */
    public fun renameDone(title: String?)

    public fun closeSheet()

    public fun continueWith(choice: ContinueChoice)

    public fun retryContinueWith()

    public fun confirm()

    public fun dismissConfirm()

    public fun dismissFailure()

    public fun clipDone(id: Long)

    public fun dismissFreeNote() {}

    /** Copy the "Continue on computer" command (only writes the clipboard; nothing is sent). */
    public fun copyResumeCommand() {}
}
