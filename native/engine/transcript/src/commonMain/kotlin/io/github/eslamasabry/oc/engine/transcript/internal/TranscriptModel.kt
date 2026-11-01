package io.github.eslamasabry.oc.engine.transcript.internal

import io.github.eslamasabry.oc.engine.transcript.TranscriptCounters
import io.github.eslamasabry.oc.engine.transcript.state.BlockRow
import io.github.eslamasabry.oc.engine.transcript.state.DeltaField
import io.github.eslamasabry.oc.engine.transcript.state.FooterRow
import io.github.eslamasabry.oc.engine.transcript.state.GatewayEvent
import io.github.eslamasabry.oc.engine.transcript.state.MessageInfo
import io.github.eslamasabry.oc.engine.transcript.state.MessageWithParts
import io.github.eslamasabry.oc.engine.transcript.state.NewestFirst
import io.github.eslamasabry.oc.engine.transcript.state.NoticeKind
import io.github.eslamasabry.oc.engine.transcript.state.NoticeRow
import io.github.eslamasabry.oc.engine.transcript.state.Origin
import io.github.eslamasabry.oc.engine.transcript.state.Part
import io.github.eslamasabry.oc.engine.transcript.state.PartType
import io.github.eslamasabry.oc.engine.transcript.state.PromptRow
import io.github.eslamasabry.oc.engine.transcript.state.Role
import io.github.eslamasabry.oc.engine.transcript.state.RunFacts
import io.github.eslamasabry.oc.engine.transcript.state.RunOutcome
import io.github.eslamasabry.oc.engine.transcript.state.ThoughtRow
import io.github.eslamasabry.oc.engine.transcript.state.ToolStatus
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptRow
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptState
import io.github.eslamasabry.oc.engine.transcript.state.WorkLineRow
import io.github.eslamasabry.oc.engine.transcript.state.WorkState
import io.github.eslamasabry.oc.engine.transcript.state.sessionIdOrNull
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

internal class PartEntry(var part: Part, var version: Long) {
    /** Text parts only: the growing text and its blocks. The text lives here, never copied per delta. */
    var blocks: StreamingBlocks? = null

    /** Reasoning and tool-input text, appended in place. */
    var raw: StringBuilder? = null

    /** True once a delta arrived for it in this session: its rows may animate (Origin.Streamed). */
    var streamed: Boolean = false

    /** Cached settled-block rows, parallel to blocks.settledBlocks (instances kept forever). */
    val settledRows: ArrayList<BlockRow> = ArrayList()
    var tailRow: BlockRow? = null
    var rowsGeneration: Int = 0

    /**
     * `<messageId>/<partId>`. Plan 09 §3.7 says `b:<partId>:<offset>`, but OC2 part ids are per-message ordinals
     * (`text-0`, `reasoning-0`; 04 §7.6), so the part id alone repeats in every step and turn.
     */
    val blockKeyBase: String = "${part.messageId}/${part.id}"
}

internal class MsgEntry(var info: MessageInfo, var version: Long) {
    val parts: LinkedHashMap<String, PartEntry> = LinkedHashMap()
}

/** A derived turn and its rows; reused while every member message keeps its version (plan 09 §3.6 TurnCache). */
internal class TurnEntry(val turnId: String, val members: List<MsgEntry>, val versions: LongArray, val rows: List<TranscriptRow>) {
    fun matches(other: List<MsgEntry>): Boolean {
        if (other.size != members.size) return false
        for (i in other.indices) if (other[i] !== members[i] || other[i].version != versions[i]) return false
        return true
    }
}

/**
 * The working model of one open transcript (plan 09 §3.2). Single-threaded: every call runs on the handle's lane.
 * [apply] mutates and stamps versions synchronously; [derive] builds the immutable newest-first rows, rebuilding only
 * the turns whose messages changed and never copying the settled prefix.
 */
internal class TranscriptModel(private val sessionId: String, private val counters: TranscriptCounters) {
    private val messages = LinkedHashMap<String, MsgEntry>()
    private val deferredParts = LinkedHashMap<String, MutableList<Part>>()
    private val deferredDeltas = LinkedHashMap<String, StringBuilder>()
    private var deferredChars = 0
    private var eventVersion = 0L
    private var run = RunFacts()
    private var crossedDrop = false

    /** Recent-history reads asked for (stale, reconnect, deferred overflow). The gateway read is slice 2. */
    var refreshRequests: Int = 0
        private set

    // Derive caches
    private var turnCache = HashMap<String, TurnEntry>()
    private var prefixEntries: List<TurnEntry> = emptyList()
    private var prefixList: PersistentList<TranscriptRow> = persistentListOf()
    private var prefixPositions: PersistentMap<String, Int> = persistentMapOf()
    private val rowCache = HashMap<String, TranscriptRow>()

    /** Applies one event; false when it cannot change what this transcript draws (no publish). */
    fun apply(event: GatewayEvent): Boolean {
        val sid = event.sessionIdOrNull
        if (sid != null && sid != sessionId) return false
        return when (event) {
            GatewayEvent.Connected -> {
                run = run.copy(connects = run.connects + 1)
                crossedDrop = run.busy
                if (run.connects > 1) refreshRequests++ // reconcile by refetch, never replay (R20)
                false
            }
            is GatewayEvent.RunStarted -> setRun(run.copy(busy = true))
            is GatewayEvent.RunEnded -> {
                if (crossedDrop) refreshRequests++
                crossedDrop = false
                endLiveParts()
                setRun(run.copy(busy = false, lastOutcome = event.outcome))
            }
            is GatewayEvent.MessageUpserted -> upsertMessage(event.message)
            is GatewayEvent.PartUpserted -> upsertPart(event.part)
            is GatewayEvent.PartDelta -> delta(event)
            is GatewayEvent.TranscriptStale -> {
                refreshRequests++
                false
            }
            is GatewayEvent.Notice ->
                upsertPart(
                    Part(event.noticeId, event.messageId, event.sessionId, PartType.Notice, text = event.kind.name),
                    create = MessageInfo(event.messageId, event.sessionId, Role.User),
                )
        }
    }

    /** A history page (oldest first). Loaded parts never animate. */
    fun loadHistory(page: List<MessageWithParts>) {
        for (m in page) {
            upsertMessage(m.info)
            for (p in m.parts) upsertPart(p)
        }
    }

    private fun setRun(next: RunFacts): Boolean {
        val changed = next != run
        run = next
        return changed
    }

    private fun stamp(): Long = ++eventVersion

    private fun upsertMessage(info: MessageInfo): Boolean {
        val v = stamp()
        val entry = messages[info.id]
        if (entry == null) {
            messages[info.id] = MsgEntry(info, v)
        } else {
            entry.info = info
            entry.version = v
        }
        if (info.completedMs != null) messages[info.id]?.let(::endParts)
        deferredParts.remove(info.id)?.forEach { upsertPart(it) }
        return true
    }

    private fun upsertPart(part: Part, create: MessageInfo? = null): Boolean {
        val v = stamp()
        val msg =
            messages[part.messageId]
                ?: if (create != null) MsgEntry(create, v).also { messages[create.id] = it } else null
        if (msg == null) {
            deferredParts.getOrPut(part.messageId) { mutableListOf() } += part
            boundDeferred()
            return false
        }
        msg.version = v
        val existing = msg.parts[part.id]
        val entry = existing ?: PartEntry(part, v).also { msg.parts[part.id] = it }
        entry.part = part
        entry.version = v
        when (part.type) {
            PartType.Text -> {
                val blocks = entry.blocks ?: StreamingBlocks().also { entry.blocks = it }
                // Whole part wins: equal text keeps the blocks, `old + more` appends, anything else re-parses (§3.8).
                if (part.text.isNotEmpty() || existing == null) replaceCounted(blocks, part.text)
            }
            PartType.Reasoning -> if (part.text.isNotEmpty() || entry.raw == null) entry.raw = StringBuilder(part.text)
            else -> Unit
        }
        deferredDeltas.remove(deltaKey(part.messageId, part.id))?.let { pending ->
            deferredChars -= pending.length
            appendDelta(entry, DeltaField.Text, pending)
        }
        return true
    }

    private fun replaceCounted(blocks: StreamingBlocks, full: String) {
        val before = blocks.fullParses
        blocks.replace(full)
        counters.markdownParses += blocks.fullParses - before
    }

    private fun delta(e: GatewayEvent.PartDelta): Boolean {
        val v = stamp()
        val msg = messages[e.messageId]
        val entry = msg?.parts?.get(e.partId)
        if (msg == null || entry == null) {
            if (e.field == DeltaField.ToolInputRaw) return false
            deferredDeltas.getOrPut(deltaKey(e.messageId, e.partId)) { StringBuilder() }.append(e.delta)
            deferredChars += e.delta.length
            boundDeferred()
            return false
        }
        msg.version = v
        entry.version = v
        appendDelta(entry, e.field, e.delta)
        // Streamed raw tool input is never parsed and draws nothing in P3 (§3.3).
        return e.field != DeltaField.ToolInputRaw
    }

    private fun appendDelta(entry: PartEntry, field: DeltaField, delta: CharSequence) {
        entry.streamed = true
        when {
            field == DeltaField.Text && entry.part.type == PartType.Text ->
                (entry.blocks ?: StreamingBlocks().also { entry.blocks = it }).append(delta)
            else -> (entry.raw ?: StringBuilder().also { entry.raw = it }).append(delta)
        }
    }

    /** Plan 09 §3.2 bounds [new]: 256 deferred keys and 1 MiB of deferred text; overflow drops the oldest + refresh. */
    private fun boundDeferred() {
        while (deferredParts.size + deferredDeltas.size > MAX_DEFERRED_KEYS || deferredChars > MAX_DEFERRED_CHARS) {
            val oldDelta = deferredDeltas.keys.firstOrNull()
            if (oldDelta != null) {
                deferredChars -= deferredDeltas.remove(oldDelta)!!.length
            } else {
                deferredParts.remove(deferredParts.keys.first())
            }
            refreshRequests++
        }
    }

    private fun endParts(msg: MsgEntry) {
        for (p in msg.parts.values) if (!p.part.ended) p.part = p.part.copy(ended = true)
    }

    private fun endLiveParts() {
        for (m in messages.values) {
            if (m.parts.values.any { !it.part.ended }) {
                endParts(m)
                m.version = stamp()
            }
        }
    }

    // ---------------------------------------------------------------- derive

    fun derive(revision: Long): TranscriptState {
        counters.derives++
        val turns = groupTurns()
        val newestIndex = turns.lastIndex
        // Settled prefix: every turn but the newest. Reuse the cached rows of unchanged turns, and the previous
        // persistent prefix when it is a prefix of this one (the common case while a reply streams).
        val entries = ArrayList<TurnEntry>(turns.size)
        for (i in 0 until newestIndex) {
            val (turnId, members) = turns[i]
            val cached = turnCache[turnId]
            entries +=
                if (cached != null && cached.matches(members)) {
                    cached
                } else {
                    buildTurn(turnId, members, finished = true).also { turnCache[turnId] = it }
                }
        }
        var common = 0
        while (common < prefixEntries.size && common < entries.size && prefixEntries[common] === entries[common]) common++
        var list: PersistentList<TranscriptRow>
        var positions: PersistentMap<String, Int>
        if (common == prefixEntries.size) {
            list = prefixList
            positions = prefixPositions
        } else {
            list = persistentListOf()
            positions = persistentMapOf()
            common = 0
        }
        for (i in common until entries.size) {
            val (l, p) = appendRows(list, positions, entries[i].rows)
            list = l
            positions = p
        }
        prefixEntries = entries
        prefixList = list
        prefixPositions = positions
        if (newestIndex >= 0) {
            val (turnId, members) = turns[newestIndex]
            val newest = buildTurn(turnId, members, finished = !run.busy)
            val (l, p) = appendRows(list, positions, newest.rows)
            list = l
            positions = p
        }
        return TranscriptState(revision, NewestFirst(list), positions, run)
    }

    private fun appendRows(
        list: PersistentList<TranscriptRow>,
        positions: PersistentMap<String, Int>,
        rows: List<TranscriptRow>,
    ): Pair<PersistentList<TranscriptRow>, PersistentMap<String, Int>> {
        if (rows.isEmpty()) return list to positions
        val base = list.size
        val pos = positions.builder()
        rows.forEachIndexed { i, r -> pos[r.key] = base + i }
        return list.addingAll(rows) to pos.build()
    }

    /** Turns by prompt (never by `role != assistant`, §3.6): a user message that is not a notice starts a turn. */
    private fun groupTurns(): List<Pair<String, List<MsgEntry>>> {
        val out = ArrayList<Pair<String, List<MsgEntry>>>()
        var current: ArrayList<MsgEntry>? = null
        var currentId = "head"
        for (m in messages.values) {
            val isPrompt = m.info.role == Role.User && m.parts.values.any { it.part.type == PartType.Text }
            if (isPrompt || current == null) {
                current?.let { out += currentId to it }
                current = ArrayList()
                currentId = if (isPrompt) m.info.id else "head"
            }
            current.add(m)
        }
        current?.let { out += currentId to it }
        return out
    }

    private fun buildTurn(turnId: String, members: List<MsgEntry>, finished: Boolean): TurnEntry {
        counters.displayPassMessages += members.size
        val rows = ArrayList<TranscriptRow>()
        var workAt = -1
        var calls = 0
        var failedCalls = 0
        var running = false
        var hasAssistant = false
        var failed = false
        for (m in members) {
            if (m.info.role == Role.Assistant) {
                hasAssistant = true
                if (m.info.errorText != null) failed = true
            }
            for (pe in m.parts.values) {
                val p = pe.part
                when (p.type) {
                    PartType.Text ->
                        if (m.info.role == Role.User) {
                            rows += reuse(PromptRow("prompt:${m.info.id}", turnId, pe.blocks?.text?.toString() ?: p.text))
                        } else {
                            addBlocks(rows, turnId, pe, live = !p.ended && !finished)
                        }
                    PartType.Reasoning -> {
                        val text = pe.raw ?: continue
                        val first = text.lineSequence().firstOrNull { it.isNotBlank() } ?: continue
                        rows += reuse(ThoughtRow("thought:${p.messageId}/${p.id}", turnId, first.trim()))
                    }
                    PartType.Tool -> {
                        if (workAt < 0) workAt = rows.size
                        calls++
                        when (p.toolStatus) {
                            ToolStatus.Error -> failedCalls++
                            ToolStatus.Pending, ToolStatus.Running, null -> running = true
                            else -> Unit
                        }
                    }
                    PartType.Notice, PartType.Switch, PartType.Compaction ->
                        rows += reuse(NoticeRow("notice:${p.id}", turnId, noticeKindOf(p)))
                    else -> Unit
                }
            }
        }
        if (workAt >= 0) {
            val state =
                when {
                    running && !finished -> WorkState.Running
                    run.lastOutcome is RunOutcome.Interrupted && finished -> WorkState.Stopped
                    failedCalls > 0 -> WorkState.EndedFailed
                    else -> WorkState.Done
                }
            rows.add(workAt, reuse(WorkLineRow("work:$turnId", turnId, calls, failedCalls, state)))
        }
        if (finished && hasAssistant) rows += reuse(FooterRow("footer:$turnId", turnId, failed))
        return TurnEntry(turnId, members, LongArray(members.size) { members[it].version }, rows)
    }

    private fun noticeKindOf(p: Part): NoticeKind = NoticeKind.entries.firstOrNull { it.name == p.text } ?: NoticeKind.Other

    private fun addBlocks(rows: MutableList<TranscriptRow>, turnId: String, pe: PartEntry, live: Boolean) {
        val blocks = pe.blocks ?: return
        val origin = if (pe.streamed) Origin.Streamed else Origin.Loaded
        if (pe.rowsGeneration != blocks.generation) {
            pe.settledRows.clear()
            pe.tailRow = null
            pe.rowsGeneration = blocks.generation
        }
        val settled = blocks.settledBlocks
        for (i in pe.settledRows.size until settled.size) {
            val b = settled[i]
            pe.settledRows += BlockRow("b:${pe.blockKeyBase}:${b.start}", turnId, b.kind, pe.part.id, b.start, b.text, false, false, origin)
        }
        rows.addAll(pe.settledRows)
        val before = blocks.tailReparses
        val tail = blocks.tail()
        counters.tailReparses += blocks.tailReparses - before
        if (tail == null) return
        val prev = pe.tailRow
        val row =
            if (prev != null && prev.start == tail.start && prev.text == tail.text && prev.live == live &&
                prev.kind == tail.kind && prev.open == tail.open
            ) {
                prev
            } else {
                BlockRow("b:${pe.blockKeyBase}:${tail.start}", turnId, tail.kind, pe.part.id, tail.start, tail.text, live, tail.open, origin)
            }
        pe.tailRow = row
        rows += row
    }

    /** Equal row → the previous instance, so the list skips it by identity. */
    private fun <T : TranscriptRow> reuse(row: T): T {
        val old = rowCache[row.key]
        @Suppress("UNCHECKED_CAST")
        if (old != null && old == row) return old as T
        rowCache[row.key] = row
        return row
    }

    private fun deltaKey(messageId: String, partId: String) = "$messageId\u0000$partId"

    private companion object {
        const val MAX_DEFERRED_KEYS = 256
        const val MAX_DEFERRED_CHARS = 1 shl 20
    }
}
