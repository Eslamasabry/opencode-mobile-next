package io.github.eslamasabry.oc.feature.prompts.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.composer.stash.PromptShelf
import io.github.eslamasabry.oc.engine.composer.stash.StashedPrompt
import kotlin.concurrent.atomics.AtomicLong
import kotlin.time.Clock

/** Makes the stores the shelf reads ready (preference warm-up, profile restore) before the first read. */
public fun interface ShelfReady {
    public suspend fun prepare(): Outcome<Unit>
}

/**
 * [SavedPromptsStore] over the composer engine's [PromptShelf]: the same `oc.promptStash.<profile>.<id>` records and
 * stash vault the Flutter app writes, so a migrated shelf reads unchanged. Edit keeps the record's date, files and
 * references; it deletes the old record first (so a full shelf can still be edited) and puts it back if the new one
 * cannot be saved.
 */
public class ShelfSavedPrompts(
    private val shelf: PromptShelf,
    private val ready: ShelfReady,
    private val clock: Clock,
) : SavedPromptsStore {
    private val lastId = AtomicLong(0L)

    /** Flutter ids are microseconds since the epoch; keep that shape and never repeat one. */
    private fun nextId(): String {
        val now = clock.now().toEpochMilliseconds() * 1000
        while (true) {
            val before = lastId.load()
            val next = if (now > before) now else before + 1
            if (lastId.compareAndSet(before, next)) return next.toString()
        }
    }

    private suspend fun <T> whenReady(block: suspend () -> Outcome<T>): Outcome<T> =
        when (val prepared = ready.prepare()) {
            is Outcome.Failed -> prepared
            is Outcome.Ok -> block()
        }

    override suspend fun list(profile: String): Outcome<List<SavedPromptItem>> = whenReady {
        when (val all = shelf.stashes(profile)) {
            is Outcome.Failed -> all
            is Outcome.Ok -> all.value.map { it.toItem() }.ok()
        }
    }

    override suspend fun save(profile: String, text: String): Outcome<Unit> = whenReady {
        if (text.isBlank()) return@whenReady Failure.StorageRefused().failed()
        shelf.stash(
            profile,
            StashedPrompt(id = nextId(), text = text, createdAt = clock.now().toEpochMilliseconds()),
        )
    }

    override suspend fun edit(profile: String, id: String, text: String): Outcome<Unit> = whenReady {
        if (text.isBlank()) return@whenReady Failure.StorageRefused().failed()
        val original =
            when (val all = shelf.stashes(profile)) {
                is Outcome.Failed -> return@whenReady all
                is Outcome.Ok ->
                    all.value.firstOrNull { it.id == id } ?: return@whenReady Failure.StorageRefused().failed()
            }
        if (original.text == text) return@whenReady Unit.ok()
        // Read the files back first: a prompt whose files cannot all be read is not rewritten.
        val files =
            when (val r = shelf.restoreAttachments(profile, id, sameLocation = true)) {
                is Outcome.Failed -> return@whenReady r
                is Outcome.Ok -> r.value
            }
        if (files.unavailable.isNotEmpty()) return@whenReady Failure.StorageRefused().failed()
        val undo =
            when (val removed = shelf.deleteWithUndo(profile, id)) {
                is Outcome.Failed -> return@whenReady removed
                is Outcome.Ok -> removed.value
            }
        val replacement =
            original.copy(
                id = nextId(),
                text = text,
                attachments = original.attachments.ifEmpty { files.attachments },
                attachmentRefs = emptyList(),
            )
        when (val saved = shelf.stash(profile, replacement)) {
            is Outcome.Ok -> saved
            is Outcome.Failed -> {
                undo.undo()
                saved
            }
        }
    }

    override suspend fun delete(profile: String, id: String): Outcome<SavedPromptRestore> = whenReady {
        when (val removed = shelf.deleteWithUndo(profile, id)) {
            is Outcome.Failed -> removed
            is Outcome.Ok -> {
                val undo = removed.value
                SavedPromptRestore { whenReady { undo.undo() } }.ok()
            }
        }
    }
}

internal fun StashedPrompt.toItem(): SavedPromptItem =
    SavedPromptItem(
        id = id,
        text = text,
        createdAt = createdAt,
        attachments = attachmentCount,
        references = references.size,
    )
