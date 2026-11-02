package io.github.eslamasabry.oc.engine.transcript.ledger

import io.github.eslamasabry.oc.core.domain.events.DeltaField
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.engine.transcript.turnfacts.settledToolStatus
import kotlinx.collections.immutable.toImmutableList

/**
 * One turn of the conversation in core's full types: the prompt message that starts it and every message after it
 * until the next prompt (plan 09 §3.6, the same grouping as the transcript reducer, so [turnId] matches the rows'
 * `turnId`). Messages before the first prompt belong to the turn [HEAD].
 */
public data class LedgerTurn(val turnId: String, val messages: List<MessageWithParts>)

/** What the ledger holds after a change: every known message oldest first, and the same messages as turns. */
public data class LedgerSnapshot(val version: Long, val messages: List<MessageWithParts>, val turns: List<LedgerTurn>) {
    public companion object {
        public val Empty: LedgerSnapshot = LedgerSnapshot(0, emptyList(), emptyList())
    }
}

/**
 * The open conversation's messages with their full tool state, tokens and times (CHAT-TRANSCRIPT). The transcript
 * reducer keeps only what its rows draw; the turn facts (steps, durations, results, context, background work) need
 * core's whole [Part], so this keeps the core values the gateway delivered, keyed by id, and nothing else.
 *
 * Text deltas are not appended (the facts only ask whether a reply has words), so a long stream costs O(1) here.
 * Single-threaded: the chat world calls it from its one collector context.
 */
public class TurnLedger(private val sessionId: String) {
    private class Entry(var info: MessageInfo) {
        val parts = LinkedHashMap<String, Part>()
    }

    private val messages = LinkedHashMap<String, Entry>()
    private var version = 0L
    private var cached: LedgerSnapshot = LedgerSnapshot.Empty

    /** Forgets everything (another server, a reload from scratch). */
    public fun reset() {
        messages.clear()
        version++
    }

    /** Applies one gateway event; true when it changed what the ledger holds. */
    public fun apply(event: GatewayEvent): Boolean =
        when (event) {
            is GatewayEvent.MessageUpserted -> {
                if (event.message.sessionId != sessionId) false else upsertInfo(event.message)
            }
            is GatewayEvent.PartUpserted -> {
                if (event.part.sessionId != sessionId) false else upsertPart(event.part)
            }
            is GatewayEvent.PartDelta -> {
                if (event.sessionId.raw != sessionId || event.field != DeltaField.Text) {
                    false
                } else {
                    val entry = messages[event.messageId.raw]
                    val part = entry?.parts?.get(event.partId.raw)
                    if (part == null || part.text.isNotEmpty() || event.delta.isEmpty()) {
                        false
                    } else {
                        entry.parts[part.id] = part.copy(text = event.delta)
                        changed()
                    }
                }
            }
            else -> false
        }

    /** A history page (oldest first): its messages replace what the ledger knew of them. */
    public fun load(page: List<MessageWithParts>) {
        for (m in page) {
            if (m.info.sessionId != sessionId) continue
            val entry = messages.getOrPut(m.info.id) { Entry(m.info) }
            entry.info = m.info
            entry.parts.clear()
            for (p in m.parts) entry.parts[p.id] = p
        }
        changed()
    }

    /** The current snapshot; built once per change. */
    public fun snapshot(): LedgerSnapshot {
        if (cached.version == version) return cached
        // Server order: OpenCode message ids ascend in creation order; a stable sort keeps arrival order for equals.
        val ordered =
            messages.values
                .sortedWith(compareBy({ it.info.time?.createdMs ?: Long.MAX_VALUE }, { it.info.id }))
                .map { MessageWithParts(it.info, it.parts.values.toImmutableList()) }
        cached = LedgerSnapshot(version, ordered, group(ordered))
        return cached
    }

    private fun upsertInfo(info: MessageInfo): Boolean {
        val entry = messages[info.id]
        if (entry == null) {
            messages[info.id] = Entry(info)
        } else {
            if (entry.info == info) return false
            entry.info = info
        }
        return changed()
    }

    private fun upsertPart(part: Part): Boolean {
        val entry = messages[part.messageId] ?: return false
        if (entry.parts[part.id] == part) return false
        // A later upsert of a text part arrives without the words streamed so far; keep "has words" true.
        val old = entry.parts[part.id]
        var next = if (old != null && part.text.isEmpty() && old.text.isNotEmpty()) part.copy(text = old.text) else part
        // B-CHAT-117: a late "still running" report (a replay, a reconnect) never revives a finished call.
        val before = old?.tool
        val after = next.tool
        if (before != null && after != null) {
            val status = settledToolStatus(before.status, after.status)
            if (status != after.status) next = next.copy(tool = after.copy(status = status))
        }
        if (old == next) return false
        entry.parts[part.id] = next
        return changed()
    }

    private fun changed(): Boolean {
        version++
        return true
    }

    public companion object {
        public const val HEAD: String = "head"

        /** Turns by prompt (a user message with words starts one), as the transcript reducer groups them. */
        public fun group(ordered: List<MessageWithParts>): List<LedgerTurn> {
            val out = ArrayList<LedgerTurn>()
            var current: ArrayList<MessageWithParts>? = null
            var currentId = HEAD
            for (m in ordered) {
                val isPrompt = m.info.role == Role.User && m.parts.any { it.type == PartType.Text }
                if (isPrompt || current == null) {
                    current?.let { out += LedgerTurn(currentId, it) }
                    current = ArrayList()
                    currentId = if (isPrompt) m.info.id else HEAD
                }
                current.add(m)
            }
            current?.let { out += LedgerTurn(currentId, it) }
            return out
        }
    }
}
