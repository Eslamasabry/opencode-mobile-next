package io.github.eslamasabry.oc.feature.review

import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.engine.composer.drafts.ConversationDrafts
import io.github.eslamasabry.oc.feature.review.presentation.AddedFile
import io.github.eslamasabry.oc.feature.review.presentation.ReviewAddFile
import io.github.eslamasabry.oc.feature.review.presentation.ReviewSendBack
import kotlinx.coroutines.flow.first

/**
 * "Send back with a note" into the conversation's own message (the draft owner persists it; the chat's composer
 * restores it on return). The note is appended after what is already typed, never replacing it.
 */
@Inject
public class ReviewDrafts(private val draftStores: Set<ConversationDrafts>) {
    public fun sendBack(ref: ConversationRef): ReviewSendBack? {
        val drafts = draftStores.singleOrNull() ?: return null
        return ReviewSendBack { text -> append(drafts, ref, text) }
    }

    /** "Add <file> to the prompt": the `@path ` mention, once, after what is typed (Flutter `_stageFile`). */
    public fun addFile(ref: ConversationRef): ReviewAddFile? {
        val drafts = draftStores.singleOrNull() ?: return null
        return ReviewAddFile { path -> mention(drafts, ref, path) }
    }

    private suspend fun mention(drafts: ConversationDrafts, ref: ConversationRef, path: String): Outcome<AddedFile> {
        val session = drafts.open(ref)
        val ready = session.state.first { it.ready || it.failure != null }
        if (!ready.ready) return Outcome.Failed(ready.failure ?: Failure.StorageRefused())
        val mention = "@$path "
        val text = ready.text
        if (text.contains(mention) || text.endsWith("@$path")) return Outcome.Ok(AddedFile.AlreadyThere)
        session.changed(
            when {
                text.isEmpty() || text.endsWith(' ') || text.endsWith('\n') -> text + mention
                else -> "$text $mention"
            }
        )
        return when (val flushed = session.flush()) {
            is Outcome.Failed -> flushed
            is Outcome.Ok -> Outcome.Ok(AddedFile.Added)
        }
    }

    private suspend fun append(drafts: ConversationDrafts, ref: ConversationRef, text: String): Outcome<Unit> {
        if (text.isBlank()) return Outcome.Failed(Failure.Unavailable(FeatureId("reviewNote")))
        val session = drafts.open(ref)
        val ready = session.state.first { it.ready || it.failure != null }
        if (!ready.ready) return Outcome.Failed(ready.failure ?: Failure.StorageRefused())
        val current = ready.text
        val next =
            when {
                current.isBlank() -> text
                current.endsWith("\n\n") -> current + text
                current.endsWith('\n') -> current + "\n" + text
                else -> current + "\n\n" + text
            }
        session.changed(next)
        return session.flush()
    }
}
