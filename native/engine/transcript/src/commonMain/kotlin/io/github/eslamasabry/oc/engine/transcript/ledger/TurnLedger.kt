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
    private class Entry(var info: MessageInfo, var stamp: Long) {
        val parts = LinkedHashMap<String, Part>()

        /** When each part last changed (the ledger's [version] at that change): the history fence reads it. */
        val partStamps = HashMap<String, Long>()
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
                        entry.partStamps[part.id] = version + 1
                        changed()
                    }
                }
            }
            else -> false
        }

    /** The version to pass to [load] for a history read that starts now. */
    public fun historyVersion(): Long = version

    /**
     * A history page (oldest first): its messages replace what the ledger knew of them, except what changed after
     * [beforeVersion] (the [historyVersion] read when the request started, B-CHAT-112/117): a live event that arrived
     * while the page was on its way is newer than the page, so its message info and parts are kept as they are.
     */
    public fun load(page: List<MessageWithParts>, beforeVersion: Long = Long.MAX_VALUE) {
        val stamp = version + 1
        for (m in page) {
            if (m.info.sessionId != sessionId) continue
            val existing = messages[m.info.id]
            if (existing == null) {
                val entry = Entry(m.info, stamp)
                for (p in m.parts) {
                    entry.parts[p.id] = p
                    entry.partStamps[p.id] = stamp
                }
                messages[m.info.id] = entry
                continue
            }
            if (existing.stamp <= beforeVersion) {
                existing.info = m.info
                existing.stamp = stamp
            }
            val pageIds = m.parts.mapTo(HashSet()) { it.id }
            // Parts the page no longer has are gone, unless they arrived live after the request started.
            existing.parts.keys.retainAll { it in pageIds || (existing.partStamps[it] ?: 0) > beforeVersion }
            existing.partStamps.keys.retainAll(existing.parts.keys)
            val kept = LinkedHashMap<String, Part>()
            for (p in m.parts) {
                val live = existing.parts[p.id]
                if (live != null && (existing.partStamps[p.id] ?: 0) > beforeVersion) {
                    kept[p.id] = live
                } else {
                    kept[p.id] = p
                    existing.partStamps[p.id] = stamp
                }
            }
            for ((id, p) in existing.parts) if (id !in kept) kept[id] = p
            existing.parts.clear()
            existing.parts.putAll(kept)
        }
        changed()
    }

    /** The current snapshot; built once per change. */
    public fun snapshot(): LedgerSnapshot {
        if (cached.version == version) return cached
        // Server order (B-CHAT-100): by creation time; a stable sort keeps the order the server gave equal times in
        // (history pages and live arrivals both insert in server order). Ids are not compared: they need not ascend.
        val ordered =
            messages.values
                .sortedWith(compareBy { it.info.time?.createdMs ?: Long.MAX_VALUE })
                .map { MessageWithParts(it.info, it.parts.values.toImmutableList()) }
        cached = LedgerSnapshot(version, ordered, group(ordered))
        return cached
    }

    private fun upsertInfo(info: MessageInfo): Boolean {
        val entry = messages[info.id]
        if (entry == null) {
            messages[info.id] = Entry(info, version + 1)
        } else {
            if (entry.info == info) return false
            entry.info = info
            entry.stamp = version + 1
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
            // B-CHAT-109: a later report without times (a progress or result event) keeps the times already known.
            val start = after.startedAtMs ?: before.startedAtMs
            val end = after.completedAtMs ?: before.completedAtMs.takeIf { status == before.status }
            if (status != after.status || start != after.startedAtMs || end != after.completedAtMs)
                next = next.copy(tool = after.copy(status = status, startedAtMs = start, completedAtMs = end))
        }
        if (old == next) return false
        entry.parts[part.id] = next
        entry.partStamps[part.id] = version + 1
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
