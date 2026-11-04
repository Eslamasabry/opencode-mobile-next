package io.github.eslamasabry.oc.engine.voice.conversation

import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.flow.StateFlow

/**
 * Only server identity and final prose participate in automatic playback; never text/time matching.
 */
public data class VoiceReplyMessage(
    val id: String,
    val user: Boolean,
    val parentId: String?,
    val settled: Boolean,
    val text: String,
)

/**
 * Proof retained independently of optimistic bubbles, which may disappear before a fast send
 * returns.
 */
public data class VoiceDispatchProof(
    val localId: Long,
    val messageId: String? = null,
    val accepted: Boolean = false,
    val uncertain: Boolean = false,
)

public data class VoiceConversationSnapshot(
    val generation: Long = 0,
    val connected: Boolean = false,
    val ready: Boolean = false,
    val busy: Boolean = false,
    val requestWaiting: Boolean = false,
    val hasAttachments: Boolean = false,
    val busyCount: Long = 0,
    val idleCount: Long = 0,
    val liveUserIds: Set<String> = emptySet(),
    val messages: List<VoiceReplyMessage> = emptyList(),
    val dispatch: VoiceDispatchProof? = null,
) {
    override fun toString(): String =
        "VoiceConversationSnapshot(generation=$generation, connected=$connected, busy=$busy)"
}

/**
 * The existing conversation's send pipeline and event truth. A new graph invalidates every watch.
 */
public interface VoiceConversationSession {
    public val voiceConversation: StateFlow<VoiceConversationSnapshot>

    public suspend fun sendVoice(text: String): Outcome<Unit>

    /**
     * Bounded fresh read after a settled identity check: facts ledgers need not retain every text
     * delta.
     */
    public suspend fun readVoiceReply(id: String, automatic: Boolean): Outcome<String>
}

public sealed interface VoiceReplyDecision {
    public data object Wait : VoiceReplyDecision

    public data object Review : VoiceReplyDecision

    public data object Interrupted : VoiceReplyDecision

    public data object NoProse : VoiceReplyDecision

    public data class Read(val id: String, val markdown: String) : VoiceReplyDecision
}

/**
 * One opt-in turn, armed before dispatch, and consumed once. Busy then idle must both be live
 * observations.
 */
public class VoiceReplyWatch(private val before: VoiceConversationSnapshot) {
    private val oldIds = before.messages.mapTo(HashSet()) { it.id }
    private val oldLiveIds = before.liveUserIds

    public fun decide(now: VoiceConversationSnapshot): VoiceReplyDecision {
        if (now.generation != before.generation || !now.connected) return VoiceReplyDecision.Review
        if (now.requestWaiting) return VoiceReplyDecision.Interrupted
        val proof = now.dispatch ?: return VoiceReplyDecision.Wait
        if (proof.localId == before.dispatch?.localId) return VoiceReplyDecision.Wait
        if (proof.uncertain) return VoiceReplyDecision.Review
        if (
            !proof.accepted ||
                now.busy ||
                now.busyCount <= before.busyCount ||
                now.idleCount <= before.idleCount
        ) {
            return VoiceReplyDecision.Wait
        }
        val id = proof.messageId ?: return VoiceReplyDecision.Review
        val newUsers = now.liveUserIds - oldLiveIds
        if (id in oldIds || newUsers != setOf(id)) return VoiceReplyDecision.Review
        val start = now.messages.indexOfFirst { it.user && it.id == id }
        if (start < 0) return VoiceReplyDecision.Review
        val replies = now.messages.drop(start + 1).takeWhile { !it.user }
        if (replies.isEmpty() || replies.any { it.parentId != id }) return VoiceReplyDecision.Review
        if (replies.any { !it.settled }) return VoiceReplyDecision.Wait
        val markdown = replies.joinToString("\n\n") { it.text }
        return if (markdown.isBlank()) VoiceReplyDecision.NoProse
        else VoiceReplyDecision.Read(id, markdown)
    }
}
