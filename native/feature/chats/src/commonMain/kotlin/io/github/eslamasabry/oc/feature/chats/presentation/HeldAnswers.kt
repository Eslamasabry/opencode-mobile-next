package io.github.eslamasabry.oc.feature.chats.presentation

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The one undo window for permission answers: an answer waits [holdMs] with Undo, then [send] sends it once. The home
 * rows, the switcher and the edge light's list all hold through one instance, so a request answered in one place shows
 * its Undo in the others and leaves once. The wait runs in [scope], which must outlive any one screen (closing a panel
 * or leaving the home never cancels a hold). Confined to one dispatcher, like the screens that call it.
 */
public class HeldAnswers(
    private val scope: CoroutineScope,
    private val holdMs: Long = ANSWER_HOLD_MS,
    private val send: (ConversationRef, String, HomePermissionReply) -> Unit,
) {
    private val heldState = MutableStateFlow<Map<String, HomeHeldReply>>(emptyMap())
    private val rows = HashMap<String, Pair<ConversationRef, HomePermissionReply>>()
    private val jobs = HashMap<String, Job>()

    /** The replies waiting out the window, by request id, as the rows word them. */
    public val held: StateFlow<Map<String, HomeHeldReply>> = heldState.asStateFlow()

    public fun isHeld(requestId: String): Boolean = requestId in rows

    /** Holds [reply] for [requestId]; false (and nothing changes) when that request is already held. */
    public fun hold(ref: ConversationRef, requestId: String, reply: HomePermissionReply): Boolean {
        if (requestId in rows) return false
        rows[requestId] = ref to reply
        heldState.update {
            it + (requestId to
                when (reply) {
                    HomePermissionReply.Once -> HomeHeldReply.Allowed
                    HomePermissionReply.Reject -> HomeHeldReply.Rejected
                    HomePermissionReply.Always -> HomeHeldReply.AlwaysAllowed
                })
        }
        jobs[requestId] =
            scope.launch {
                delay(holdMs)
                release(requestId)
            }
        return true
    }

    /** The window ended: the answer leaves now (also what closing a screen that owns the store does). */
    public fun release(requestId: String) {
        val (ref, reply) = rows.remove(requestId) ?: return
        jobs.remove(requestId)
        send(ref, requestId, reply)
        heldState.update { it - requestId }
    }

    /** Undo: nothing is sent. False when the request was not held. */
    public fun undo(requestId: String): Boolean {
        if (rows.remove(requestId) == null) return false
        jobs.remove(requestId)?.cancel()
        heldState.update { it - requestId }
        return true
    }

    /** What is held now, for an owner that goes away with it (the home's own store sends these when it is cleared). */
    public fun pending(): List<Triple<ConversationRef, String, HomePermissionReply>> =
        rows.map { (id, held) -> Triple(held.first, id, held.second) }

    /** Forgets every hold without sending (after [pending] was sent another way). */
    public fun discard() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        rows.clear()
        heldState.value = emptyMap()
    }
}
