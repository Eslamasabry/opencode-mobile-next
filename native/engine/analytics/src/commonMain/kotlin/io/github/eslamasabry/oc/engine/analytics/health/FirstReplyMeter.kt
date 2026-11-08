package io.github.eslamasabry.oc.engine.analytics.health

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * B-190: `send_result` ok with `first_reply_range`, and the `send_first_reply` Performance trace, for an accepted send
 * (Flutter `HealthFlow.sendOk`). Fed by the prompt timeline's signals (ids only): the first assistant token after the
 * send is the first reply; a turn that ends without one counts as `none`. An idle left over from the previous turn does
 * not end the new one. A refused send is counted where it is refused, with its failure kind.
 */
public class FirstReplyMeter(private val now: () -> Long, private val flow: HealthFlow = HealthFlow()) {
    private class Pending(val startMs: Long, val onPhone: Boolean) {
        val assistant = HashSet<String>()
        var busy = false
    }

    private val pending = MutableStateFlow<Map<String, Pending>>(emptyMap())

    public fun sent(sessionId: String, onPhone: Boolean) {
        pending.update { current ->
            val base = if (current.size >= MAX_TRACKED && sessionId !in current) current - current.keys.first() else current
            base + (sessionId to Pending(now(), onPhone))
        }
    }

    public fun accepted(sessionId: String, error: Boolean) {
        if (error) forget(sessionId)
    }

    public fun message(sessionId: String, messageId: String, assistant: Boolean) {
        if (assistant) pending.value[sessionId]?.assistant?.add(messageId)
    }

    public fun part(sessionId: String, messageId: String, kind: String) {
        val p = pending.value[sessionId] ?: return
        if (messageId in p.assistant || kind in ASSISTANT_ONLY_KINDS) firstReply(sessionId, p)
    }

    public fun delta(sessionId: String) {
        pending.value[sessionId]?.let { firstReply(sessionId, it) }
    }

    public fun busy(sessionId: String) {
        pending.value[sessionId]?.busy = true
    }

    /** The turn ended (idle or error) before any reply. */
    public fun ended(sessionId: String) {
        val p = pending.value[sessionId] ?: return
        if (!p.busy) return
        forget(sessionId)
        flow.sendResult("ok", firstReplyMs = null)
    }

    private fun firstReply(sessionId: String, p: Pending) {
        forget(sessionId)
        val elapsed = (now() - p.startMs).coerceAtLeast(0)
        flow.sendResult("ok", firstReplyMs = elapsed, serverKind = if (p.onPhone) "phone" else "computer")
    }

    private fun forget(sessionId: String) {
        pending.update { it - sessionId }
    }

    private companion object {
        const val MAX_TRACKED = 16
        val ASSISTANT_ONLY_KINDS = setOf("step-start", "reasoning", "tool")
    }
}
