package io.github.eslamasabry.oc.engine.transcript.internal

import io.github.eslamasabry.oc.core.domain.events.RunOutcome
import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import io.github.eslamasabry.oc.engine.transcript.TranscriptCounters
import io.github.eslamasabry.oc.engine.transcript.internal.passes.COMPACTION_PASSED
import io.github.eslamasabry.oc.engine.transcript.internal.passes.Display
import io.github.eslamasabry.oc.engine.transcript.internal.passes.FoldMarks
import io.github.eslamasabry.oc.engine.transcript.internal.passes.TurnSpan
import io.github.eslamasabry.oc.engine.transcript.internal.passes.deriveTurns
import io.github.eslamasabry.oc.engine.transcript.internal.passes.endsTurn
import io.github.eslamasabry.oc.engine.transcript.internal.passes.isDeclined
import io.github.eslamasabry.oc.engine.transcript.internal.passes.isPrompt
import io.github.eslamasabry.oc.engine.transcript.internal.passes.isVariant
import io.github.eslamasabry.oc.engine.transcript.internal.passes.markFoldedWork
import io.github.eslamasabry.oc.engine.transcript.internal.passes.settleStaleLive
import io.github.eslamasabry.oc.engine.transcript.internal.passes.timelineDisplay
import io.github.eslamasabry.oc.engine.transcript.internal.passes.turnActionOwners
import io.github.eslamasabry.oc.engine.transcript.state.AssistantErrorRow
import io.github.eslamasabry.oc.engine.transcript.state.BlockRow
import io.github.eslamasabry.oc.engine.transcript.state.FooterRow
import io.github.eslamasabry.oc.engine.transcript.state.MarkerKind
import io.github.eslamasabry.oc.engine.transcript.state.MarkerRow
import io.github.eslamasabry.oc.engine.transcript.state.NewestFirst
import io.github.eslamasabry.oc.engine.transcript.state.NoticeKind
import io.github.eslamasabry.oc.engine.transcript.state.NoticeRow
import io.github.eslamasabry.oc.engine.transcript.state.OlderRow
import io.github.eslamasabry.oc.engine.transcript.state.OlderState
import io.github.eslamasabry.oc.engine.transcript.state.Origin
import io.github.eslamasabry.oc.engine.transcript.state.PromptRow
import io.github.eslamasabry.oc.engine.transcript.state.RunFacts
import io.github.eslamasabry.oc.engine.transcript.state.ThoughtRow
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptPhase
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptRow
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptState
import io.github.eslamasabry.oc.engine.transcript.state.TurnPosition
import io.github.eslamasabry.oc.engine.transcript.state.WorkLineRow
import io.github.eslamasabry.oc.engine.transcript.state.WorkState
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList

/** A derived turn and its rows; reused while every member snapshot is the same instance (plan 09 §3.6 TurnCache). */
internal class TurnEntry(
    val turnId: String,
    val members: Array<MessageWithParts>,
    val busy: Boolean,
    val rows: List<TranscriptRow>,
) {
    /** Settling a finished turn reads `busy` only when it still holds a running tool (settleStaleLive). */
    private val busySensitive: Boolean =
        members.any { m -> m.parts.any { it.tool?.status == ToolStatus.Running || it.tool?.status == ToolStatus.Pending } }

    fun matches(messages: List<MessageWithParts>, span: TurnSpan, busy: Boolean): Boolean {
        if ((busySensitive && busy != this.busy) || span.end - span.start != members.size) return false
        for (i in members.indices) if (members[i] !== messages[span.start + i]) return false
        return true
    }

    fun sameRows(other: List<TranscriptRow>): Boolean {
        if (other.size != rows.size) return false
        for (i in rows.indices) if (rows[i] !== other[i]) return false
        return true
    }
}

/**
 * Turns the model into the published newest-first state (plan 09 §3.6, §3.7). Every turn before the newest prompt is
 * cached per turn; the newest turn is re-derived on each publish and written into the persistent list in place: only
 * rows that changed are removed and added again (the slice-0 P3 open problem 3), so a 5,000-row turn costs one tail
 * `set` per publish, not a 5,000-row rebuild.
 */
internal class RowBuilder(private val model: TranscriptModel, private val counters: TranscriptCounters) {
    private val turnCache = HashMap<String, TurnEntry>()
    private var prefixEntries: List<Any> = emptyList()
    private var prefixList: PersistentList<TranscriptRow> = persistentListOf()
    private var prefixPositions: PersistentMap<String, Int> = persistentMapOf()

    // The newest turn as last written: its rows sit at [prefixList.size, list.size) of [lastList].
    private var newestRows: List<TranscriptRow> = emptyList()
    private var lastList: PersistentList<TranscriptRow> = persistentListOf()
    private var lastPositions: PersistentMap<String, Int> = persistentMapOf()
    private val rowCache = HashMap<String, TranscriptRow>()

    fun derive(revision: Long, phase: TranscriptPhase, older: OlderState): TranscriptState {
        counters.derives++
        val run = model.run
        val messages = model.snapshots()
        val ignore = model.inbox.filterTo(HashSet()) { it in model.messages }
        val turns = deriveTurns(messages, ignore)
        val newestIndex = turns.lastIndex
        val entries = ArrayList<Any>(turns.size + 1)
        if (older == OlderState.Available || older == OlderState.Loading || older == OlderState.NeedsReload) {
            entries += olderEntry(older)
        }
        for (i in 0 until newestIndex) {
            val span = turns[i]
            val cached = turnCache[span.id]
            entries +=
                if (cached != null && cached.matches(messages, span, run.busy)) {
                    cached
                } else {
                    val built = buildTurn(messages, span, run, newest = false, ignore)
                    // Same rows under a new key (busy flipped and nothing settled): keep the old entry so the
                    // persistent prefix stays shared.
                    val keep = cached != null && cached.sameRows(built.rows)
                    (if (keep) TurnEntry(cached.turnId, built.members, built.busy, cached.rows) else built)
                        .also { turnCache[span.id] = it }
                }
        }
        // Shared persistent prefix: reuse it when the cached entries are a prefix of these (the streaming case).
        var common = 0
        while (common < prefixEntries.size && common < entries.size && sameEntry(prefixEntries[common], entries[common])) common++
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
            val rows = rowsOf(entries[i])
            counters.rowsReplaced += rows.size
            val base = list.size
            val pos = positions.builder()
            rows.forEachIndexed { k, r -> pos[r.key] = base + k }
            positions = pos.build()
            list = list.addingAll(rows)
        }
        val prefixChanged = list !== prefixList
        prefixEntries = entries
        prefixList = list
        prefixPositions = positions

        val newest =
            if (newestIndex >= 0) buildTurn(messages, turns[newestIndex], run, newest = true, ignore).rows else emptyList()
        if (!prefixChanged && lastList.size == list.size + newestRows.size) {
            // Patch the newest turn in place: keep the identical leading rows, replace the rest.
            var same = 0
            while (same < newest.size && same < newestRows.size && newest[same] === newestRows[same]) same++
            val b = lastList.builder()
            val pos = lastPositions.builder()
            for (k in newestRows.size - 1 downTo same) {
                val r = b.removeAt(b.size - 1)
                if (pos[r.key] == list.size + k) pos.remove(r.key)
            }
            for (k in same until newest.size) {
                pos[newest[k].key] = b.size
                b += newest[k]
            }
            counters.rowsReplaced += newest.size - same
            list = b.build()
            positions = pos.build()
        } else {
            val base = list.size
            val pos = positions.builder()
            newest.forEachIndexed { k, r -> pos[r.key] = base + k }
            positions = pos.build()
            list = list.addingAll(newest)
            counters.rowsReplaced += newest.size
        }
        newestRows = newest
        lastList = list
        lastPositions = positions
        val order = messageOrder(messages)
        return TranscriptState(
            revision = revision,
            rows = NewestFirst(list),
            positions = positions,
            run = run,
            phase = phase,
            older = older,
            messageOrder = order,
            messageCount = messages.size,
        )
    }

    private var orderRevision = -1L
    private var order: PersistentMap<String, Int> = persistentMapOf()

    /** Changes only on a new message, a removal or an older-page prepend (§7.3 pill); otherwise the same map. */
    private fun messageOrder(messages: List<MessageWithParts>): PersistentMap<String, Int> {
        if (orderRevision == model.orderRevision) return order
        val b = persistentMapOf<String, Int>().builder()
        for (i in messages.indices) b[messages[i].info.id] = i
        order = b.build()
        orderRevision = model.orderRevision
        return order
    }

    private class OlderEntry(val row: OlderRow)

    private var older: OlderEntry? = null

    private fun olderEntry(state: OlderState): OlderEntry {
        val o = older
        if (o != null && o.row.state == state) return o
        return OlderEntry(OlderRow(state)).also { older = it }
    }

    private fun sameEntry(a: Any, b: Any): Boolean = a === b

    private fun rowsOf(entry: Any): List<TranscriptRow> =
        when (entry) {
            is TurnEntry -> entry.rows
            is OlderEntry -> listOf(entry.row)
            else -> emptyList()
        }

    // ------------------------------------------------------------------------------------------------ one turn

    private fun buildTurn(
        all: List<MessageWithParts>,
        span: TurnSpan,
        run: RunFacts,
        newest: Boolean,
        ignore: Set<String>,
    ): TurnEntry {
        val raw = all.subList(span.start, span.end)
        counters.displayPassMessages += raw.size
        val live = newest && run.busy
        // A prompt follows every turn but the newest: what settles at the very end does not settle earlier (§3.6).
        val settled = settleStaleLive(raw, busy = run.busy, moreFollows = !newest)
        val marks = markFoldedWork(settled, liveTail = live)
        val display = timelineDisplay(settled, marks, liveTail = live)
        val owners = turnActionOwners(settled, display, running = live, ignore = ignore)
        val rows = ArrayList<TranscriptRow>()
        val turnId = span.id
        var workLines = 0
        for (i in settled.indices) {
            val m = settled[i]
            val id = m.info.id
            if (id in ignore) continue // a prompt still waiting in the server inbox is not drawn (§3.3 InboxChanged)
            when {
                m.isPrompt() -> rows += reuse(promptRow(turnId, m))
                m.info.role == Role.User -> if (!marks.foldedNotice(m)) variantRows(rows, turnId, m)
                else -> {
                    for (d in display[i]) {
                        when (d) {
                            is Display.Text -> for (p in d.parts) blockRows(rows, turnId, p, live = live && !p.ended)
                            is Display.Work -> {
                                if (live) {
                                    for (p in d.parts) if (p.type == PartType.Reasoning) thoughtRow(rows, turnId, p)
                                    if (d.parts.any { it.type == PartType.Tool }) {
                                        rows += reuse(workRow(turnId, workLines++, id, d, marks, live = true, run, newest))
                                    }
                                } else {
                                    rows += reuse(workRow(turnId, workLines++, id, d, marks, live = false, run, newest))
                                }
                            }
                            is Display.Other -> Unit // files in replies: picture groups arrive with :core:markdown (S2-MD)
                        }
                    }
                    if (m.info.errorText != null) {
                        rows += reuse(AssistantErrorRow("err:$id", turnId, m.info.errorKind, recovered = !endsTurn(settled, i), id))
                    }
                    if (i in owners) {
                        val failed = m.info.errorText != null || (newest && run.lastOutcome is RunOutcome.Failed)
                        val stopped = newest && run.lastOutcome is RunOutcome.Interrupted
                        rows += reuse(FooterRow("footer:$turnId", turnId, failed, id, stopped))
                    }
                }
            }
        }
        positionEnds(rows)
        return TurnEntry(turnId, Array(raw.size) { raw[it] }, run.busy, rows)
    }

    private fun promptRow(turnId: String, m: MessageWithParts): PromptRow {
        var text: String? = null
        var files = 0
        for (p in m.parts) {
            when {
                p.type == PartType.File -> files++
                p.type == PartType.Text && !p.synthetic && p.text.isNotBlank() -> text = if (text == null) p.text else "$text\n${p.text}"
            }
        }
        return PromptRow("prompt:${m.info.id}", turnId, text.orEmpty(), m.info.id, files)
    }

    /** Trap T9: switch / notice / compaction rows; an unknown `v2:*` part degrades to a generic notice. */
    private fun variantRows(rows: MutableList<TranscriptRow>, turnId: String, m: MessageWithParts) {
        for (p in m.parts) {
            if (!p.isVariant()) continue
            val key = "${p.messageId}/${p.id}"
            when (p.type) {
                PartType.Switch -> rows += reuse(MarkerRow("marker:$key", turnId, MarkerKind.Switch, m.info.id))
                PartType.Compaction ->
                    when (p.toolName) {
                        COMPACTION_PASSED -> Unit
                        "running" -> rows += reuse(MarkerRow("marker:$key", turnId, MarkerKind.Compacting, m.info.id))
                        else -> rows += reuse(NoticeRow("notice:$key", turnId, NoticeKind.Compaction, m.info.id))
                    }
                else -> rows += reuse(NoticeRow("notice:$key", turnId, noticeKindOf(p), m.info.id))
            }
        }
    }

    private fun noticeKindOf(p: Part): NoticeKind =
        when (p.noticeMetadata["kind"]) {
            io.github.eslamasabry.oc.core.domain.events.NoticeKind.InstructionsUpdated.raw -> NoticeKind.InstructionsUpdated
            io.github.eslamasabry.oc.core.domain.events.NoticeKind.SkillChanged.raw -> NoticeKind.SkillChanged
            io.github.eslamasabry.oc.core.domain.events.NoticeKind.ShellChanged.raw -> NoticeKind.ShellChanged
            else -> NoticeKind.Other
        }

    /** Placeholder thoughts ("No reasoning available.", markup only) are dropped (CHAT-GENUI-06). */
    private fun thoughtRow(rows: MutableList<TranscriptRow>, turnId: String, p: Part) {
        val first = p.text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return
        if (first == "No reasoning available." || first.all { it == '*' || it == '_' || it == '#' || it == '`' }) return
        rows += reuse(ThoughtRow("thought:${p.messageId}/${p.id}", turnId, first, p.messageId))
    }

    private fun workRow(
        turnId: String,
        n: Int,
        messageId: String,
        d: Display.Work,
        marks: FoldMarks,
        live: Boolean,
        run: RunFacts,
        newest: Boolean,
    ): WorkLineRow {
        var calls = 0
        var failed = 0
        var thoughts = 0
        var folded = 0
        var running = false
        var cancelled = false
        var lastFailed = false
        val callIds = ArrayList<String>()
        for (p in d.parts) {
            when (p.type) {
                PartType.Tool -> {
                    calls++
                    callIds += p.callId ?: p.id
                    val t = p.tool
                    val status = t?.status ?: ToolStatus.Running
                    lastFailed = status == ToolStatus.Error && t?.isDeclined != true
                    if (lastFailed) failed++
                    if (status == ToolStatus.Running || status == ToolStatus.Pending) running = true
                    if (status == ToolStatus.Cancelled) cancelled = true
                }
                PartType.Reasoning -> thoughts++
                else -> if (marks.folded(p) || p.type == PartType.Notice) folded++
            }
        }
        // Recovered failures stay quiet: only a failure the turn ended on reads as failed (memory chat-transcript-turn-model).
        val state =
            when {
                live && running -> WorkState.Running
                cancelled || (newest && !run.busy && run.lastOutcome is RunOutcome.Interrupted) -> WorkState.Stopped
                lastFailed -> WorkState.EndedFailed
                else -> WorkState.Done
            }
        val key = if (n == 0) "work:$turnId" else "work:$turnId#$n"
        return WorkLineRow(key, turnId, calls, failed, state, messageId, thoughts, folded, callIds.toImmutableList())
    }

    /** One row per block of a text part; settled block rows are built once and kept forever (§3.8). */
    private fun blockRows(rows: MutableList<TranscriptRow>, turnId: String, p: Part, live: Boolean) {
        val pe = model.messages[p.messageId]?.parts?.get(p.id) ?: return
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
            pe.settledRows += BlockRow("b:${pe.blockKeyBase}:${b.start}", turnId, b.kind, p.id, b.start, b.text, false, false, origin, p.messageId)
        }
        for (r in pe.settledRows) rows += if (r.turnId == turnId) r else reuse(r.copy(turnId = turnId))
        val before = blocks.tailReparses
        val tail = blocks.tail()
        counters.tailReparses += blocks.tailReparses - before
        if (tail == null) return
        val prev = pe.tailRow
        val row =
            if (prev != null && prev.start == tail.start && prev.text == tail.text && prev.live == live &&
                prev.kind == tail.kind && prev.open == tail.open && prev.turnId == turnId
            ) {
                prev
            } else {
                BlockRow("b:${pe.blockKeyBase}:${tail.start}", turnId, tail.kind, p.id, tail.start, tail.text, live, tail.open, origin, p.messageId)
            }
        pe.tailRow = row
        rows += row
    }

    /** First and last rows of a turn carry their place; middle rows keep the default (no copy per publish). */
    private fun positionEnds(rows: MutableList<TranscriptRow>) {
        if (rows.isEmpty()) return
        if (rows.size == 1) {
            rows[0] = reuse(rows[0].at(TurnPosition.Only))
            return
        }
        rows[0] = reuse(rows[0].at(TurnPosition.First))
        rows[rows.size - 1] = reuse(rows[rows.size - 1].at(TurnPosition.Last))
    }

    private fun TranscriptRow.at(p: TurnPosition): TranscriptRow {
        if (position == p) return this
        return when (this) {
            is PromptRow -> copy(position = p)
            is BlockRow -> copy(position = p)
            is ThoughtRow -> copy(position = p)
            is WorkLineRow -> copy(position = p)
            is NoticeRow -> copy(position = p)
            is MarkerRow -> copy(position = p)
            is AssistantErrorRow -> copy(position = p)
            is FooterRow -> copy(position = p)
            is OlderRow -> this
        }
    }

    /** Equal row → the previous instance, so the list skips it by identity. */
    private fun <T : TranscriptRow> reuse(row: T): T {
        val old = rowCache[row.key]
        @Suppress("UNCHECKED_CAST")
        if (old != null && old == row) return old as T
        rowCache[row.key] = row
        return row
    }

    /** Forget cached rows (history reset): keys may come back with other content. */
    fun reset() {
        turnCache.clear()
        rowCache.clear()
        prefixEntries = emptyList()
        prefixList = persistentListOf()
        prefixPositions = persistentMapOf()
        newestRows = emptyList()
        lastList = persistentListOf()
        lastPositions = persistentMapOf()
    }
}
