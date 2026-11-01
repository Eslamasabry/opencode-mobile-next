package io.github.eslamasabry.oc.engine.transcript.internal

import io.github.eslamasabry.oc.core.domain.events.DeltaField
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.domain.events.InboxChange
import io.github.eslamasabry.oc.core.domain.events.NoticeKind
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.engine.transcript.RefreshReason
import io.github.eslamasabry.oc.engine.transcript.TranscriptCounters
import io.github.eslamasabry.oc.engine.transcript.state.RetryFacts
import io.github.eslamasabry.oc.engine.transcript.state.RunFacts
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList

/** One part of the working model. Streaming text lives in [blocks] / [raw], never copied per delta (§3.8). */
internal class PartEntry(part: Part, var version: Long) {
    /** The last structural part; its text may lag the live text, read [snapshot] instead. */
    var part: Part = part
        private set

    /** Text parts: the growing text and its blocks. */
    var blocks: StreamingBlocks? = null

    /** Reasoning text, appended in place. */
    var raw: StringBuilder? = null

    /** Pending tool: the streamed raw input, never parsed (§3.3, cards §13.3). */
    var inputRaw: StringBuilder? = null

    /** A delta arrived for it in this handle's life: its rows may animate (Origin.Streamed). */
    var streamed: Boolean = false

    private var snap: Part? = null

    // Row caches, owned by RowBuilder: settled-block rows parallel to blocks.settledBlocks, and the tail row.
    val settledRows: ArrayList<io.github.eslamasabry.oc.engine.transcript.state.BlockRow> = ArrayList()
    var tailRow: io.github.eslamasabry.oc.engine.transcript.state.BlockRow? = null
    var rowsGeneration: Int = -1

    val blockKeyBase: String = "${part.messageId}/${part.id}"

    fun setPart(next: Part) {
        part = next
        snap = null
    }

    fun touched() {
        snap = null
    }

    /** The immutable part as drawn now. Built at most once per change (once per publish while streaming). */
    fun snapshot(): Part {
        snap?.let { return it }
        val p = part
        val built =
            when {
                p.type == PartType.Text && blocks != null -> {
                    val t = blocks!!.text
                    if (t.length == p.text.length && p.text.contentEquals(t)) p else p.copy(text = t.toString())
                }
                p.type == PartType.Reasoning && raw != null -> {
                    val t = raw!!
                    if (t.length == p.text.length && p.text.contentEquals(t)) p else p.copy(text = t.toString())
                }
                p.type == PartType.Tool && inputRaw != null && p.tool != null ->
                    p.copy(tool = p.tool!!.copy(inputRaw = inputRaw.toString()))
                else -> p
            }
        snap = built
        return built
    }
}

internal class MsgEntry(info: MessageInfo, var version: Long) {
    var info: MessageInfo = info
        private set
    val parts: LinkedHashMap<String, PartEntry> = LinkedHashMap()
    private var snap: MessageWithParts? = null

    fun setInfo(next: MessageInfo) {
        info = next
        snap = null
    }

    fun touched() {
        snap = null
    }

    /** Replaced, never mutated, on every change: identity (`===`) says "unchanged" to the turn cache (§3.2). */
    fun snapshot(): MessageWithParts {
        snap?.let { return it }
        val built = MessageWithParts(info, parts.values.map { it.snapshot() }.toImmutableList())
        snap = built
        return built
    }
}

private class DeferredDelta(val field: DeltaField, val text: StringBuilder, var version: Long)

/**
 * The working model of one open transcript (plan 09 §3.2, map CHAT-GENUI-02). Single-threaded: every call runs on the
 * handle's lane. Every message or part event stamps `++eventVersion` synchronously before anything else; a history
 * read remembers the version it started at and [adoptHydrated] keeps what moved since (CHAT-GENUI-03).
 */
internal class TranscriptModel(
    private val sessionId: String,
    private val counters: TranscriptCounters,
    private val refresh: (RefreshReason) -> Unit = {},
) {
    val messages: LinkedHashMap<String, MsgEntry> = LinkedHashMap()
    private val deferredParts = LinkedHashMap<String, LinkedHashMap<String, Part>>()
    private val deferredDeltas = LinkedHashMap<String, DeferredDelta>()
    private val deferredMessages = LinkedHashMap<String, MessageInfo>()
    private var deferredChars = 0
    private val messageVersions = HashMap<String, Long>()
    private val partVersions = HashMap<String, Long>()

    var eventVersion: Long = 0L
        private set

    /** +1 whenever the message order changes (add, remove, history adoption): the message-order map's key. */
    var orderRevision: Long = 0L
        private set
    var run: RunFacts = RunFacts()
        private set

    /** Inbox item ids: a user message with one of these ids is not a prompt row (§3.3 InboxChanged). */
    val inbox: MutableSet<String> = LinkedHashSet()

    /** Set by the handle: older pages exist, so a late edit to an unknown message may be old history. */
    var olderCursorKnown: Boolean = false

    /** History was read at least once: a Connected is then a reconnect that must re-read (R20). */
    var historyLoaded: Boolean = false

    /** True when the session was deleted on the server. */
    var deleted: Boolean = false
        private set

    fun messageVersion(id: String): Long = messageVersions[id] ?: 0L

    fun partVersion(messageId: String, partId: String): Long = partVersions[key(messageId, partId)] ?: 0L

    /** Applies one event; false when it cannot change what this transcript draws (no publish). */
    fun apply(event: GatewayEvent): Boolean =
        when (event) {
            GatewayEvent.Connected -> {
                run = run.copy(connects = run.connects + 1, crossedDrop = run.busy)
                if (historyLoaded) refresh(RefreshReason.Reconnected) // reconcile by refetch, never replay (R20)
                true // run facts changed (connects, crossedDrop)
            }
            is GatewayEvent.RunStarted -> mine(event.sessionId.raw) && setRun(run.copy(busy = true, retry = null))
            is GatewayEvent.RunRetrying ->
                mine(event.sessionId.raw) && setRun(run.copy(busy = true, retry = RetryFacts(event.attempt, event.nextAtMs)))
            is GatewayEvent.RunEnded -> mine(event.sessionId.raw) && runEnded(event)
            is GatewayEvent.MessageUpserted -> mine(event.message.sessionId) && upsertMessage(event.message)
            is GatewayEvent.PartUpserted -> mine(event.part.sessionId) && upsertPart(event.part)
            is GatewayEvent.PartDelta -> mine(event.sessionId.raw) && delta(event)
            is GatewayEvent.TranscriptStale -> {
                if (mine(event.sessionId.raw)) refresh(RefreshReason.Stale)
                false
            }
            is GatewayEvent.Notice -> mine(event.sessionId.raw) && notice(event)
            is GatewayEvent.RevertChanged -> {
                if (mine(event.sessionId.raw)) {
                    run = run.copy(historyEpoch = run.historyEpoch + 1)
                    refresh(RefreshReason.HistoryReset)
                }
                false
            }
            is GatewayEvent.InboxChanged -> mine(event.sessionId.raw) && inboxChanged(event)
            is GatewayEvent.SessionDeleted ->
                if (mine(event.sessionId.raw)) {
                    deleted = true
                    true
                } else {
                    false
                }
            // Requests, sessions, hints and server facts belong to other engines (§3.3 last row).
            else -> false
        }

    private fun mine(id: String) = id == sessionId

    private fun setRun(next: RunFacts): Boolean {
        val changed = next != run
        run = next
        return changed
    }

    /**
     * B-PROTO-040: only the end of the execution ends the run; a finished step does not. A failure is never
     * overwritten by a later idle (there is no idle event after RunEnded in the neutral vocabulary).
     */
    private fun runEnded(event: GatewayEvent.RunEnded): Boolean {
        if (run.crossedDrop) refresh(RefreshReason.RunEndedAfterDrop)
        endLiveParts()
        return setRun(run.copy(busy = false, lastOutcome = event.outcome, crossedDrop = false, retry = null)) || true
    }

    private fun inboxChanged(event: GatewayEvent.InboxChanged): Boolean {
        val id = event.inboxId.raw
        return when (event.change) {
            InboxChange.Enqueued, InboxChange.DeliveryChanged -> inbox.add(id)
            InboxChange.Cancelled -> inbox.remove(id)
            InboxChange.Delivered -> {
                refresh(RefreshReason.InboxDelivered)
                inbox.remove(id)
            }
        }
    }

    private fun stamp(): Long = ++eventVersion

    // ------------------------------------------------------------------------------------------------ messages

    private fun upsertMessage(info: MessageInfo): Boolean {
        val v = stamp()
        messageVersions[info.id] = v
        val entry = messages[info.id]
        if (entry != null) {
            entry.setInfo(info)
            entry.version = v
            if (info.time?.completedMs != null) endParts(entry)
        } else {
            // A late edit to old history is not a new row (chat_stream.dart message.updated): with older pages
            // unread, a message older than the newest known one waits, and re-reads when it may be recent.
            val created = info.time?.createdMs
            val known = messages.values.mapNotNull { it.info.time?.createdMs }
            if (olderCursorKnown && (created == null || known.isEmpty() || created <= known.last())) {
                deferredMessages[info.id] = info
                if (created == null || known.isEmpty() || created >= known.first()) refresh(RefreshReason.LateEdit)
                return false
            }
            messages[info.id] = MsgEntry(info, v)
            orderRevision++
        }
        val deferred = deferredParts.remove(info.id)
        deferred?.values?.forEach { upsertPart(it) }
        return true
    }

    private fun upsertPart(part: Part): Boolean {
        val v = stamp() // version stamp before anything else (§3.3)
        partVersions[key(part.messageId, part.id)] = v
        val msg = messages[part.messageId]
        if (msg == null) {
            deferredParts.getOrPut(part.messageId) { LinkedHashMap() }[part.id] = part
            boundDeferred()
            return false
        }
        msg.version = v
        msg.touched()
        val existing = msg.parts[part.id]
        val entry = existing ?: PartEntry(part, v).also { msg.parts[part.id] = it }
        entry.version = v
        setWholePart(entry, part, isNew = existing == null)
        flushDeferredDelta(part.messageId, part.id, entry, minVersion = 0)
        return true
    }

    /**
     * Whole part wins over accumulated deltas (§3.3): equal text keeps the blocks, `old + more` appends, anything else
     * re-parses once (§3.8). An empty text on a part that already streamed words is a start frame, not a rewrite.
     */
    private fun setWholePart(entry: PartEntry, part: Part, isNew: Boolean) {
        entry.setPart(part)
        when (part.type) {
            PartType.Text -> {
                val blocks = entry.blocks ?: StreamingBlocks().also { entry.blocks = it }
                if (part.text.isNotEmpty() || isNew) {
                    val before = blocks.fullParses
                    blocks.replace(part.text)
                    counters.markdownParses += blocks.fullParses - before
                }
            }
            PartType.Reasoning -> if (part.text.isNotEmpty() || entry.raw == null) entry.raw = StringBuilder(part.text)
            PartType.Tool -> entry.inputRaw = null
            else -> Unit
        }
    }

    private fun delta(e: GatewayEvent.PartDelta): Boolean {
        val messageId = e.messageId.raw
        val partId = e.partId.raw
        val v = stamp()
        partVersions[key(messageId, partId)] = v
        val msg = messages[messageId]
        val entry = msg?.parts?.get(partId)
        if (msg == null || entry == null) {
            val k = key(messageId, partId)
            val d = deferredDeltas.getOrPut(k) { DeferredDelta(e.field, StringBuilder(), v) }
            d.text.append(e.delta)
            d.version = v
            deferredChars += e.delta.length
            boundDeferred()
            return false
        }
        msg.version = v
        msg.touched()
        entry.version = v
        appendDelta(entry, e.field, e.delta)
        // Streamed raw tool input is never parsed and draws nothing (§3.3).
        return e.field != DeltaField.ToolInputRaw
    }

    private fun appendDelta(entry: PartEntry, field: DeltaField, delta: CharSequence) {
        entry.touched()
        when {
            field == DeltaField.ToolInputRaw -> (entry.inputRaw ?: StringBuilder().also { entry.inputRaw = it }).append(delta)
            entry.part.type == PartType.Text -> {
                entry.streamed = true
                (entry.blocks ?: StreamingBlocks().also { entry.blocks = it }).append(delta)
            }
            else -> {
                entry.streamed = true
                (entry.raw ?: StringBuilder(entry.part.text).also { entry.raw = it }).append(delta)
            }
        }
    }

    private fun flushDeferredDelta(messageId: String, partId: String, entry: PartEntry, minVersion: Long) {
        val pending = deferredDeltas.remove(key(messageId, partId)) ?: return
        deferredChars -= pending.text.length
        if (pending.version > minVersion) appendDelta(entry, pending.field, pending.text)
    }

    private fun notice(e: GatewayEvent.Notice): Boolean {
        // Trap T10: only the kind is kept; instruction, skill and note text never reach the model.
        if (e.kind == NoticeKind.ShellChanged) refresh(RefreshReason.ShellChanged)
        val messageId = e.messageId.raw
        if (messages[messageId] == null) {
            messages[messageId] = MsgEntry(MessageInfo(messageId, sessionId, Role.User), stamp())
            messageVersions[messageId] = eventVersion
            orderRevision++
        }
        return upsertPart(
            Part(
                id = NOTICE_PART,
                messageId = messageId,
                sessionId = sessionId,
                type = PartType.Notice,
                rawType = "v2:notice",
                noticeMetadata = persistentMapOf("kind" to e.kind.raw),
            ),
        )
    }

    /** Plan 09 §3.2 bounds [new]: 256 deferred keys and 1 MiB of deferred text; overflow drops the oldest + refresh. */
    private fun boundDeferred() {
        while (deferredParts.size + deferredDeltas.size > MAX_DEFERRED_KEYS || deferredChars > MAX_DEFERRED_CHARS) {
            val oldDelta = deferredDeltas.keys.firstOrNull()
            if (oldDelta != null) {
                deferredChars -= deferredDeltas.remove(oldDelta)!!.text.length
            } else {
                deferredParts.remove(deferredParts.keys.first())
            }
            refresh(RefreshReason.DeferredOverflow)
        }
    }

    private fun endParts(msg: MsgEntry) {
        for (p in msg.parts.values) {
            if (!p.part.ended && (p.part.type == PartType.Text || p.part.type == PartType.Reasoning)) {
                p.setPart(p.part.copy(ended = true))
                msg.touched()
            }
        }
    }

    private fun endLiveParts() {
        for (m in messages.values) {
            if (m.parts.values.any { !it.part.ended && it.part.type == PartType.Text }) {
                endParts(m)
                m.version = stamp()
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ history

    /** The current transcript as immutable snapshots, server order. */
    fun snapshots(): List<MessageWithParts> = messages.values.map { it.snapshot() }

    /** Deferred parts of messages not loaded yet, for the merge (they count as live parts, `chat_history.dart:376`). */
    fun takeDeferredParts(messageId: String): Map<String, Part>? = deferredParts.remove(messageId)

    fun takeDeferredMessage(messageId: String): MessageInfo? = deferredMessages.remove(messageId)

    /**
     * Adopts a merged message list (from [mergeHydrated]). Entries whose snapshot is the merged value are kept as they
     * are (streaming state, blocks, row caches); others take the hydrated value (loaded rows never animate). Deferred
     * deltas newer than [versionAtStart] are applied; older ones for hydrated messages are dropped.
     */
    fun adoptHydrated(merged: List<MessageWithParts>, hydratedIds: Set<String>, versionAtStart: Long) {
        val next = LinkedHashMap<String, MsgEntry>(merged.size * 2)
        for (m in merged) {
            val existing = messages[m.info.id]
            if (existing != null && existing.snapshot() === m) {
                next[m.info.id] = existing
                continue
            }
            val entry = existing ?: MsgEntry(m.info, messageVersion(m.info.id))
            if (entry.info != m.info) entry.setInfo(m.info)
            val parts = LinkedHashMap<String, PartEntry>(m.parts.size * 2)
            for (p in m.parts) {
                val pe = entry.parts[p.id]
                if (pe != null && pe.snapshot() === p) {
                    parts[p.id] = pe
                } else if (pe != null) {
                    setWholePart(pe, p, isNew = false)
                    parts[p.id] = pe
                } else {
                    parts[p.id] = PartEntry(p, partVersion(m.info.id, p.id)).also { setWholePart(it, p, isNew = true) }
                }
                flushDeferredDelta(m.info.id, p.id, parts[p.id]!!, minVersion = versionAtStart)
            }
            entry.parts.clear()
            entry.parts.putAll(parts)
            entry.touched()
            next[m.info.id] = entry
        }
        messages.clear()
        messages.putAll(next)
        orderRevision++
        // chat_history.dart:430: stale deferred deltas of hydrated messages go.
        val it = deferredDeltas.entries.iterator()
        while (it.hasNext()) {
            val (k, d) = it.next()
            if (d.version <= versionAtStart && k.substringBefore(SEP) in hydratedIds) {
                deferredChars -= d.text.length
                it.remove()
            }
        }
        historyLoaded = true
    }

    /** `reload(reset = true)`: drops deferred maps first (§3.3 history resets). */
    fun clearDeferred() {
        deferredParts.clear()
        deferredDeltas.clear()
        deferredMessages.clear()
        deferredChars = 0
    }

    /** Local removal after a confirmed server delete (§6.6): stamped, so a stale page cannot bring it back. */
    fun removeMessage(id: String): Boolean {
        messageVersions[id] = stamp()
        deferredParts.remove(id)
        deferredMessages.remove(id)
        deferredDeltas.keys.removeAll { k -> k.substringBefore(SEP) == id }
        orderRevision++
        return messages.remove(id) != null
    }

    fun deferredKeys(): Int = deferredParts.size + deferredDeltas.size

    private fun key(messageId: String, partId: String) = "$messageId$SEP$partId"

    internal companion object {
        const val MAX_DEFERRED_KEYS = 256
        const val MAX_DEFERRED_CHARS = 1 shl 20
        const val SEP = '\u0000'
        const val NOTICE_PART = "notice"
    }
}
