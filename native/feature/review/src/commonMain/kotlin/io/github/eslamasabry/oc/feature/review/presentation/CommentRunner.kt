package io.github.eslamasabry.oc.feature.review.presentation

import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.review.ConversationReview
import io.github.eslamasabry.oc.engine.review.TurnChanges

/**
 * Sends a conversation's draft comments as one prompt and resolves sent ones against the conversation's changes
 * (B-156). Comments are resolved against the whole conversation's files, never one turn's: the agent's fix lands in a
 * later turn.
 */
internal class CommentRunner(
    private val review: ConversationReview,
    val book: ReviewCommentBook,
    private val sender: ReviewPromptSender,
) {
    /** Sends every draft in one prompt. The files' versions are read first, so the fix that follows is newer. */
    suspend fun sendDrafts(): Outcome<Unit> {
        val versions = versions()
        val taken = book.sending(book.drafts.map { it.id }.toSet())
        if (taken.isEmpty()) return Outcome.Ok(Unit)
        val text = ReviewCommentPrompt.format(taken)
        val result =
            if (text.isBlank()) Outcome.Failed(Failure.Unavailable(FeatureId("reviewComments"))) else sender.send(text)
        book.sent(taken.map { it.id }.toSet(), ok = result is Outcome.Ok) { versions[it] }
        return result
    }

    /** Reads the conversation's changes and resolves every waiting comment the agent's edits touched. */
    suspend fun reconcile() {
        val waiting = book.comments.value.filter { it.status == CommentStatus.Pending }.map { it.anchor.path }.toSet()
        if (waiting.isEmpty()) return
        val set = (review.changes(null) as? Outcome.Ok)?.value ?: return
        for (file in set.files.filter { it.path in waiting && it.hasDiff }) {
            val diff = TurnChanges.asFileDiff(file)
            book.reconcile(file.path, CommentResolve.version(diff), CommentResolve.diffFile(diff))
        }
    }

    private suspend fun versions(): Map<String, String> {
        val set = (review.changes(null) as? Outcome.Ok)?.value ?: return emptyMap()
        return set.files
            .filter { it.hasDiff }
            .associate { it.path to CommentResolve.version(TurnChanges.asFileDiff(it)) }
    }
}

/**
 * The review pages' way to the conversation's agent (B-155, B-156): its [comments], the [sender] that reaches the agent
 * (null: nothing can be sent, so comments and the guide are not offered), the [guideAsk] and the conversation's cached
 * [guides] by change set ([ReviewGuidePrompt.key]).
 */
public class ReviewAgentPorts(
    public val comments: ReviewCommentBook? = null,
    public val sender: ReviewPromptSender? = null,
    public val guideAsk: ReviewGuideAsk? = null,
    public val guides: MutableMap<String, ReviewGuide> = HashMap(),
) {
    internal fun runner(review: ConversationReview): CommentRunner? =
        if (comments != null && sender != null) CommentRunner(review, comments, sender) else null
}
