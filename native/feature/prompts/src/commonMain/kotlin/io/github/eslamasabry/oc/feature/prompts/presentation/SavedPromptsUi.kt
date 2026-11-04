package io.github.eslamasabry.oc.feature.prompts.presentation

import io.github.eslamasabry.oc.core.common.Failure
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** What went wrong, in the screen's own words (never the failure's text). */
public enum class SavedPromptsIssue {
    ReadFailed,
    SaveFailed,
    DeleteFailed,
    Full,
    UndoFailed,

    /**
     * The prompt has files or review notes the composer cannot take back yet; only its words can go
     * (Codex #2).
     */
    FilesStay,
    /** A kept record or the selected destination changed before insertion. */
    RestoreFailed,
}

/** A short confirmation line after an action. */
public enum class SavedPromptsNote {
    Saved,
    Edited,
}

/**
 * The open editor: [id] null writes a new prompt; [generation] resets the field once per opening.
 */
public data class PromptEditor(val id: String?, val initial: String, val generation: Long) {
    override fun toString(): String = "PromptEditor(id=$id, generation=$generation)"
}

/** The one Undo the page offers ("Saved prompt deleted · Undo"). */
public data class SavedPromptsUndo(val id: String)

public data class SavedPromptsUi(
    val loading: Boolean = true,
    val prompts: ImmutableList<SavedPromptItem> = persistentListOf(),
    val query: String = "",
    val issue: SavedPromptsIssue? = null,
    val failure: Failure? = null,
    val note: SavedPromptsNote? = null,
    val editor: PromptEditor? = null,
    val busy: Boolean = false,
    val undo: SavedPromptsUndo? = null,
    /** With [SavedPromptsIssue.FilesStay]: the prompt whose words alone may be added. */
    val wordsOnly: String? = null,
    val migrationPending: Boolean = false,
) {
    /** The rows the search keeps, newest first (Flutter `_matches`: text, case-insensitive). */
    val visible: List<SavedPromptItem>
        get() = SavedPromptsRules.filter(prompts, query)

    val full: Boolean
        get() = prompts.count { !it.kept } >= SavedPromptsFull

    override fun toString(): String =
        "SavedPromptsUi(loading=$loading, prompts=${prompts.size}, issue=$issue, editor=$editor, busy=$busy)"
}

public object SavedPromptsRules {
    public fun filter(prompts: List<SavedPromptItem>, query: String): List<SavedPromptItem> {
        val q = query.trim().lowercase()
        return prompts
            .filter { q.isEmpty() || it.text.lowercase().contains(q) }
            .sortedByDescending { it.createdAt }
    }

    /**
     * Where a saved prompt goes in a composer that may already hold words: replace an empty field,
     * else append.
     */
    public fun merged(current: String, insert: String): String =
        if (current.isBlank()) insert else current.trimEnd() + "\n" + insert
}

public interface SavedPromptsActions {
    public fun back()

    public fun retry()

    public fun search(query: String)

    public fun newPrompt()

    public fun editPrompt(id: String)

    public fun cancelEdit()

    public fun submitEdit(text: String)

    /**
     * Puts the prompt's words into the conversation's composer and goes back there. The saved
     * prompt stays.
     */
    public fun insert(id: String)

    /**
     * After [SavedPromptsIssue.FilesStay]: adds only the prompt's words; its files and notes stay
     * saved here.
     */
    public fun insertWordsOnly(id: String) {}

    /** The open editor's text changed: kept so a recreated page shows it again. */
    public fun editorChanged(text: String) {}

    public fun delete(id: String)

    public fun undo()

    public fun undoTimedOut()

    public fun openCommands()

    public fun openTasks()
}
