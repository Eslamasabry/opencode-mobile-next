package io.github.eslamasabry.oc.feature.review.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.FileDiff
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

// The four review pages' state (no copy, no Compose: the screens choose the words). Flutter sources:
// `chat_files.dart` (turn files), `review_workspace.dart` (Looks good / Undo all), `staged_revert_screen.dart`
// (Undo from this prompt?, Review the undo).

/** Where the review pages go (the ViewModels map these to `AppNavigator` and `ReviewRoute`). */
public interface ReviewNavigation {
    public fun back()

    /** One file's diff: from the page's change set, or from the staged undo's preview. */
    public fun openFile(path: String, staged: Boolean)

    /** One file of the conversation folder's uncommitted changes. */
    public fun openWorkingTreeFile(path: String): Unit = openFile(path, staged = false)

    /** One file of the whole branch's changes, compared with the main branch. */
    public fun openBranchFile(path: String): Unit = openFile(path, staged = false)

    /** "Review the undo". */
    public fun openStaged()
}

/**
 * "Send back with a note" (Flutter `review_workspace.dart` `_sendBack`): the formatted review reference goes into
 * this conversation's message, then the person is back in the chat to send it. Nothing is sent from here.
 */
public fun interface ReviewSendBack {
    public suspend fun send(text: String): Outcome<Unit>
}

/** What "Add <file> to the prompt" did (Flutter `readerUiReferenceAdded` / `readerUiReferenceDuplicate`). */
public enum class AddedFile {
    Added,
    AlreadyThere,
}

/**
 * "Add <file> to the prompt" (Flutter `review_workspace.dart` `_stageFile`): the file goes into this conversation's
 * message as an `@path` mention and the person stays on the page. Nothing is sent.
 */
public fun interface ReviewAddFile {
    public suspend fun add(path: String): Outcome<AddedFile>
}

/**
 * The whole branch's changes, compared with the main branch's merge base (Flutter `loadBranchDiffs`, OpenCode 2
 * `GET /vcs/diff?mode=branch`). A [Failure.Unavailable] answer means the server cannot show it, so the scope is not
 * offered.
 */
public fun interface ReviewBranchRead {
    public suspend fun read(): Outcome<ImmutableList<FileDiff>>
}

/** Which changes the Changes page shows (Flutter `ReviewDiffScope`). */
public enum class ReviewScope {
    /** What this conversation (or turn) changed, from the tools' records. */
    Conversation,

    /** The folder's uncommitted changes, whoever made them. */
    WorkingTree,

    /** Everything on this branch, compared with the main branch. */
    Branch,
}

/** A page's read: loading, failed (a plain line + Details) or ready. */
public sealed interface ReviewLoad {
    public data object Loading : ReviewLoad

    public data class Failed(val failure: Failure) : ReviewLoad

    public data object Ready : ReviewLoad
}

/** One changed file row: the name first, the folder beneath, the tools' line counts (null = not reported). */
public data class ChangeRowUi(
    val path: String,
    val name: String,
    val folder: String?,
    val added: Int?,
    val removed: Int?,
    /** The tools reported lines for it, so the row opens a diff. */
    val hasDiff: Boolean,
    /** Its diff was opened in this visit and has not changed since (Flutter's "Viewed", session-local only). */
    val viewed: Boolean = false,
)

/** What "Undo all" can do here. Hidden where nothing would happen (Flutter `onUndoAll == null`). */
public enum class UndoAllUi {
    /** Offered: stages an undo from the first prompt of these changes, with files back. */
    Offered,

    /** The server has no staged undo. */
    Unavailable,

    /** The prompt these changes start from is not known (older history could not be read). */
    Unknown,

    /** An undo is already waiting for review: open it instead. */
    Waiting,
}

/** "Changes" (a turn's, or the whole conversation's) with "Looks good" and "Undo all". */
public data class ChangesUi(
    /** True for one turn, false for the whole conversation. */
    val turn: Boolean,
    val load: ReviewLoad = ReviewLoad.Loading,
    val files: ImmutableList<ChangeRowUi> = persistentListOf(),
    /** False when older messages could not be read: the list may miss files. */
    val complete: Boolean = true,
    /** Sum of the rows' counts; null when any row has none. */
    val added: Int? = null,
    val removed: Int? = null,
    val undoAll: UndoAllUi = UndoAllUi.Unavailable,
    /** The conversation is running or an undo action runs: Undo all waits (states why). */
    val busy: Boolean = false,
    val working: Boolean = false,
    /** "Undo N changed files?" is open. */
    val confirming: Boolean = false,
    /** The last Undo all did not finish. */
    val actionFailure: Failure? = null,
    /**
     * The tools reported no changes, so the page shows the conversation folder's uncommitted changes instead (Flutter:
     * "review opens the view that has changes", said once).
     */
    val workingTree: Boolean = false,
    /** The server can show the folder's uncommitted changes too, so the change-scope picker is offered. */
    val canPickScope: Boolean = false,
    /** The scope on screen; [workingTree] is true exactly when it is [ReviewScope.WorkingTree]. */
    val scope: ReviewScope = ReviewScope.Conversation,
    /** The scopes this server can show, in Flutter's order; the picker shows when there are two or more. */
    val scopes: ImmutableList<ReviewScope> = persistentListOf(ReviewScope.Conversation),
    /** "Send back with a note" is offered (a conversation's message can take it). */
    val canSendBack: Boolean = false,
    /**
     * False for a project's own uncommitted changes opened from Files (no conversation behind them): the page says
     * nothing about undoing from a prompt or which view it picked.
     */
    val conversation: Boolean = true,
    /** The note field is open. */
    val noting: Boolean = false,
    /** The note could not be put into the message. */
    val sendBackFailure: Failure? = null,
    /** Line comments written and not sent yet, in this conversation (B-156: "Send N comments"). */
    val drafts: Int = 0,
    /** Sent comments still waiting for the agent's edit. */
    val waiting: Int = 0,
    val sendingComments: Boolean = false,
    /** The last "Send N comments" did not reach the agent; the comments stay as drafts. */
    val commentsFailure: Failure? = null,
    /** "Guided" can be turned on here (B-155): the conversation's agent can be asked. */
    val canGuide: Boolean = false,
    val guide: GuideUi = GuideUi.Off,
    /** Cache-first: these are the last-seen rows and the live read is on its way (a quiet bar, no skeleton). */
    val updating: Boolean = false,
) {
    /** Rows whose diff was opened and is unchanged. */
    val viewedCount: Int get() = files.count { it.viewed }

    /** Every file was opened: with comments written, the page says so and offers the way back. */
    val allViewed: Boolean get() = files.isNotEmpty() && files.all { it.viewed }
}

/** The Guided view of the Changes page (B-155). */
public sealed interface GuideUi {
    /** The plain file list. */
    public data object Off : GuideUi

    /** The agent is putting the guide together. */
    public data object Making : GuideUi

    public data class Ready(val guide: ReviewGuide) : GuideUi

    /** No guide this time ([failure] says why): the plain file list is shown under a plain line. */
    public data class Fallback(val failure: Failure) : GuideUi
}

/** One file's diff page. [diff] is null with [noDiff] when the tools reported no lines for it. */
public data class FileDiffUi(
    val path: String,
    val load: ReviewLoad = ReviewLoad.Loading,
    val diff: FileDiff? = null,
    /** The file is not in this change set any more (it moved on, or the undo changed). */
    val missing: Boolean = false,
    val noDiff: Boolean = false,
    /** Lines can be commented for the agent here (B-156; not on the undo's preview). */
    val canComment: Boolean = false,
    /** "Add <file> to the prompt" is offered (the conversation's message can take it). */
    val canAddFile: Boolean = false,
    /** What the last "Add to the prompt" did; null before it was used or after it was read. */
    val fileNotice: FileNotice? = null,
    /** This file's comments, in the order written. */
    val comments: ImmutableList<LineComment> = persistentListOf(),
    /** The comment field is open for these lines. */
    val commenting: CommentAnchor? = null,
    /** Drafts in the whole conversation: "Send N comments" sends them all in one prompt. */
    val drafts: Int = 0,
    val sending: Boolean = false,
    val sendFailure: Failure? = null,
    /** Cache-first: this is the last-seen diff and the live read is on its way. */
    val updating: Boolean = false,
) {
    val name: String get() = path.split('/', '\\').lastOrNull { it.isNotEmpty() } ?: path
}

/** The one line under the diff's bar after "Add <file> to the prompt". */
public sealed interface FileNotice {
    public data object Added : FileNotice

    public data object AlreadyThere : FileNotice

    /** The message could not take it; [failure] is for Details only. */
    public data class Failed(val failure: Failure) : FileNotice
}

/** Why the undo's primary action is off right now; null when it is on. */
public enum class UndoBlock {
    /** The conversation is running a turn, or another undo action runs. */
    Busy,

    /** The undo on the server changed since this page read it. */
    Stale,
}

/** "Undo from this prompt?" in the shape the server allows. */
public data class UndoFromHereUi(
    val load: ReviewLoad = ReviewLoad.Loading,
    /** True: stage and review (with the files choice). False: one step, files listed. */
    val staged: Boolean = true,
    /** False when the server cannot undo from a prompt at all. */
    val available: Boolean = true,
    /** The prompt's words; "" for an attachment-only prompt; null while loading or [promptUnavailable]. */
    val prompt: String? = null,
    val promptUnavailable: Boolean = false,
    /** Messages after the prompt; null = unknown, never shown as zero (B-CHAT-103). */
    val messagesAfter: Int? = null,
    /** One step only: the paths the agent reported editing after the prompt. */
    val edited: ImmutableList<String> = persistentListOf(),
    val applyFiles: Boolean = true,
    val working: Boolean = false,
    val block: UndoBlock? = null,
    val failure: Failure? = null,
)

public enum class StagedPhase {
    Loading,

    /** Nothing is staged in this conversation. */
    None,

    /** The staged undo, to keep or put back. */
    Review,

    /** The undo changed on the server since this page read it. */
    Stale,

    /** "Undo kept": the hidden messages are deleted. */
    Kept,

    /** "Everything is back". */
    Restored,
}

public enum class StagedConfirm { Keep, PutBack }

/** "Review the undo". */
public data class StagedUi(
    val phase: StagedPhase = StagedPhase.Loading,
    /** The conversation's title, to name it in the questions; null = untitled. */
    val title: String? = null,
    val prompt: String? = null,
    val promptUnavailable: Boolean = false,
    val messagesAfter: Int? = null,
    /** The server's preview; null when it gave none (that says nothing about whether files changed). */
    val files: ImmutableList<FileDiff>? = null,
    val busy: Boolean = false,
    val working: Boolean = false,
    val confirm: StagedConfirm? = null,
    val failure: Failure? = null,
    /** The first read failed: Try again. */
    val loadFailure: Failure? = null,
    /** Cache-first: the last-seen files; Keep and Put back wait for the live read. */
    val updating: Boolean = false,
)
