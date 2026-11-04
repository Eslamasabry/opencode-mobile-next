package io.github.eslamasabry.oc.feature.prompts.presentation

import io.github.eslamasabry.oc.core.common.Outcome

/** One saved prompt as the list shows it. No attachment bytes or paths reach the screen. */
public data class SavedPromptItem(
    val id: String,
    val text: String,
    val createdAt: Long,
    val attachments: Int = 0,
    val references: Int = 0,
    val unavailable: List<String> = emptyList(),
) {
    override fun toString(): String =
        "SavedPromptItem(id=$id, chars=${text.length}, attachments=$attachments)"
}

/** Puts a deleted saved prompt back exactly once. */
public fun interface SavedPromptRestore {
    public suspend fun undo(): Outcome<Unit>
}

/**
 * The saved prompts of one server (plan 09 §10.8; Flutter `prompt_stash.dart`). Every call names
 * its profile; a removed profile refuses. Implemented over the composer engine's PromptShelf
 * ([ShelfSavedPrompts]).
 */
public interface SavedPromptsStore {
    public fun migrationPending(profile: String): Boolean = false

    /** Newest first. A shelf that cannot be read fails; it never reads as empty. */
    public suspend fun list(profile: String): Outcome<List<SavedPromptItem>>

    /** Saves [text] as a new prompt. Refused when the shelf holds 50 ([SavedPromptsFull]). */
    public suspend fun save(profile: String, text: String): Outcome<Unit>

    /** Replaces the words of one prompt, keeping its date, files and references. */
    public suspend fun edit(profile: String, id: String, text: String): Outcome<Unit>

    /** Deletes at once; the result puts it back. */
    public suspend fun delete(profile: String, id: String): Outcome<SavedPromptRestore>
}

/** The shelf's capacity (Flutter `PromptShelfStore.capacity`); a saved prompt is never evicted. */
public const val SavedPromptsFull: Int = 50
