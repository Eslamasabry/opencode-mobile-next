package io.github.eslamasabry.oc.feature.prompts.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.model.PromptAttachment
import kotlinx.collections.immutable.ImmutableList

/** Only embedded bytes can travel from a removed server. Other references remain on the shelf. */
public class KeptPromptDraft(
    public val text: String,
    public val attachments: ImmutableList<PromptAttachment>,
    public val unavailable: ImmutableList<String>,
    public val stillCurrent: () -> Boolean,
) {
    override fun toString(): String =
        "KeptPromptDraft(chars=${text.length},files=${attachments.size},unavailable=${unavailable.size})"
}

public data class KeptPromptDestination(
    val ref: ConversationRef,
    val title: String,
    val server: String,
)

/** App-owned records survive removal of their original server; none are ever automatically sent. */
public interface KeptQueuedPrompts {
    public suspend fun list(): Outcome<List<SavedPromptItem>>

    public suspend fun delete(id: String): Outcome<SavedPromptRestore>

    public suspend fun destinations(): Outcome<List<KeptPromptDestination>>

    public suspend fun restore(id: String, destination: ConversationRef): Outcome<KeptPromptDraft>
}

/**
 * Optional extra surface of the production Saved prompts store; old ordinary shelf stores remain
 * valid.
 */
public interface KeptPromptRestorer {
    public suspend fun restoreKept(
        id: String,
        destination: ConversationRef,
    ): Outcome<KeptPromptDraft>
}

/** Every conversation sees the global kept records alongside its own saved prompts. */
public class KeptAwareSavedPrompts(
    private val shelf: SavedPromptsStore,
    private val kept: KeptQueuedPrompts,
) : SavedPromptsStore, KeptPromptRestorer {
    override suspend fun list(profile: String): Outcome<List<SavedPromptItem>> {
        val local = shelf.list(profile)
        if (local is Outcome.Failed) return local
        val global = kept.list()
        if (global is Outcome.Failed) return global
        return ((local as Outcome.Ok).value + (global as Outcome.Ok).value)
            .sortedByDescending { it.createdAt }
            .ok()
    }

    override suspend fun save(profile: String, text: String): Outcome<Unit> =
        shelf.save(profile, text)

    override suspend fun edit(profile: String, id: String, text: String): Outcome<Unit> =
        if (id.startsWith("kept-")) Failure.Refused(RefusalReason.StaleScope).failed()
        else shelf.edit(profile, id, text)

    override suspend fun delete(profile: String, id: String): Outcome<SavedPromptRestore> =
        if (id.startsWith("kept-")) kept.delete(id) else shelf.delete(profile, id)

    override suspend fun restoreKept(
        id: String,
        destination: ConversationRef,
    ): Outcome<KeptPromptDraft> = kept.restore(id, destination)
}
