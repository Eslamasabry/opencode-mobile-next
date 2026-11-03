package io.github.eslamasabry.oc.feature.review.presentation

import io.github.eslamasabry.oc.core.common.Failure
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

    /** "Review the undo". */
    public fun openStaged()
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
)

/** One file's diff page. [diff] is null with [noDiff] when the tools reported no lines for it. */
public data class FileDiffUi(
    val path: String,
    val load: ReviewLoad = ReviewLoad.Loading,
    val diff: FileDiff? = null,
    /** The file is not in this change set any more (it moved on, or the undo changed). */
    val missing: Boolean = false,
    val noDiff: Boolean = false,
) {
    val name: String get() = path.split('/', '\\').lastOrNull { it.isNotEmpty() } ?: path
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
)
