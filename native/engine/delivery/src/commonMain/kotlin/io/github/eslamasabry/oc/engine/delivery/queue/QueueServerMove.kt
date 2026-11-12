package io.github.eslamasabry.oc.engine.delivery.queue

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** Why one waiting prompt cannot move to another server (Flutter `QueuedPromptMoveBlock`). */
public enum class QueueMoveBlock {
    /** A send left the device without a confirmed outcome: the person reviews it first. */
    Uncertain,
    /** An attachment is a file on the old server. */
    ServerFile,
    /** Moving would mask a password or key that agent mentions point into. */
    SecretMentions,
    /** The prompt is on the wire right now. */
    Sending,
}

/**
 * What a server-wide move did, and what its Undo needs (Flutter `QueuedPromptMoveResult`). [originals] are the
 * moved prompts exactly as they were queued for the source.
 */
public class QueueMoveResult(
    public val source: ProfileId,
    public val destination: ProfileId,
    public val session: SessionId,
    originals: List<QueuedPrompt>,
    /** Picked prompts that were no longer waiting (sent, removed) or could not move when the move ran. */
    public val skipped: Int,
    /** Moved prompts whose model or agent the destination does not offer (they send with the defaults). */
    public val selectionsReset: Int,
    public val newConversation: Boolean,
) {
    public val originals: ImmutableList<QueuedPrompt> = originals.toImmutableList()
    public val moved: Int
        get() = originals.size
}

/** The pure rules of a server-wide move (port of Flutter `QueuedPromptMove`); [OfflineQueue.moveMany] applies them. */
public object QueueServerMove {
    private val portableSchemes = setOf("data", "https", "http")

    /** Why [prompt] cannot move, or null when it can. [sending] is the live "on the wire" fact. */
    public fun blockFor(prompt: QueuedPrompt, redactor: Redactor, sending: Boolean = false): QueueMoveBlock? =
        when {
            prompt.dispatched -> QueueMoveBlock.Uncertain
            sending -> QueueMoveBlock.Sending
            prompt.attachments.any { it.url.substringBefore(':', "").lowercase() !in portableSchemes } ->
                QueueMoveBlock.ServerFile
            prompt.mentions.isNotEmpty() && redactor.redact(prompt.text) != prompt.text ->
                QueueMoveBlock.SecretMentions
            else -> null
        }

    /**
     * The prompt as the destination keeps it: same id, text redacted like every kept queued prompt, same
     * attachments, mentions and creation time (so the order is kept), no error. When the destination does not
     * offer the model or the agent, model, agent and variant are all dropped together (Flutter's rule); a null
     * list means the destination's list is unknown and nothing is dropped on a guess.
     */
    public fun relocate(
        prompt: QueuedPrompt,
        destination: ConversationRef,
        models: Set<ModelRef>?,
        agents: Set<String>?,
        redactedText: String,
    ): Pair<QueuedPrompt, Boolean> {
        val modelGone = prompt.model?.let { models != null && it !in models } == true
        val agentGone =
            prompt.agent?.let { it.isNotEmpty() && !agents.isNullOrEmpty() && it !in agents } == true
        val reset = modelGone || agentGone
        val moved =
            QueuedPrompt(
                id = prompt.id,
                profileId = destination.profileId,
                sessionId = destination.sessionId,
                text = redactedText,
                attachments = prompt.attachments,
                mentions = prompt.mentions,
                modelProviderId = if (reset) null else prompt.modelProviderId,
                modelId = if (reset) null else prompt.modelId,
                agent = if (reset) null else prompt.agent,
                variant = if (reset) null else prompt.variant,
                createdAt = prompt.createdAt,
            )
        return moved to (reset && (prompt.model != null || !prompt.agent.isNullOrEmpty()))
    }

    /**
     * The queue after Undo: each moved prompt that is still waiting, unsent, in the same destination conversation
     * returns to the source exactly as it was, in the same place. A prompt already sending or sent stays.
     */
    public fun undo(
        queue: List<QueuedPrompt>,
        result: QueueMoveResult,
        sending: (String) -> Boolean,
    ): Pair<List<QueuedPrompt>, Int> {
        val originals = result.originals.associateBy { it.id }
        var restored = 0
        val next =
            queue.map { entry ->
                val original = originals[entry.id]
                if (
                    original != null &&
                        entry.profileId == result.destination &&
                        entry.sessionId == result.session &&
                        !entry.dispatched &&
                        !sending(entry.id)
                ) {
                    restored++
                    original
                } else entry
            }
        return next to restored
    }
}
