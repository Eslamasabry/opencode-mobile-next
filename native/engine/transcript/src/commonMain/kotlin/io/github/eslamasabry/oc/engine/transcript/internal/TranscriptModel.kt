package io.github.eslamasabry.oc.engine.transcript.internal

import io.github.eslamasabry.oc.core.model.transcript.MessageErrorKind
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
import io.github.eslamasabry.oc.engine.transcript.state.PromptFile
import io.github.eslamasabry.oc.engine.transcript.state.PromptRow
import io.github.eslamasabry.oc.engine.transcript.state.Role
import io.github.eslamasabry.oc.engine.transcript.state.RunFacts
import io.github.eslamasabry.oc.engine.transcript.state.RunOutcome
import io.github.eslamasabry.oc.engine.transcript.state.SearchText
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
    /**
     * Text parts only: the growing text and its blocks. The text lives here, never copied per
     * delta.
     */
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
     * The prompt row of a user text part, kept while the part's version holds (no text copy per
     * publish).
     */
    var promptRow: PromptRow? = null
    var promptVersion: Long = -1

    /**
     * `<messageId>/<partId>`. Plan 09 §3.7 says `b:<partId>:<offset>`, but OC2 part ids are
     * per-message ordinals (`text-0`, `reasoning-0`; 04 §7.6), so the part id alone repeats in
     * every step and turn.
     */
    val blockKeyBase: String = "${part.messageId}/${part.id}"
}

internal class MsgEntry(var info: MessageInfo, var version: Long) {
    val parts: LinkedHashMap<String, PartEntry> = LinkedHashMap()
}

/**
 * A derived turn and its rows; reused while every member message keeps its version (plan 09 §3.6
 * TurnCache).
 */
internal class TurnEntry(
    val turnId: String,
    val members: List<MsgEntry>,
    val versions: LongArray,
    val rows: List<TranscriptRow>,
    /** Find's text of this turn's thoughts, steps, notices and files, made with its rows (issue 147). */
    val search: List<SearchText>,
) {
    fun matches(other: List<MsgEntry>): Boolean {
        if (other.size != members.size) return false
        for (i in other.indices) if (other[i] !== members[i] || other[i].version != versions[i])
            return false
        return true
    }
}

/**
 * The working model of one open transcript (plan 09 §3.2). Single-threaded: every call runs on the
 * handle's lane. [apply] mutates and stamps versions synchronously; [derive] builds the immutable
 * newest-first rows, rebuilding only the turns whose messages changed and never copying the settled
 * prefix.
 */
internal class TranscriptModel(
    private val sessionId: String,
    private val counters: TranscriptCounters,
) {
    private val messages = LinkedHashMap<String, MsgEntry>()
    private val deferredParts = LinkedHashMap<String, MutableList<Part>>()
    private val deferredDeltas = LinkedHashMap<String, StringBuilder>()
    private var deferredChars = 0
    private var eventVersion = 0L
    val version: Long
        get() = eventVersion

    val messageIds: Set<String>
        get() = messages.keys.toSet()

    private var run = RunFacts()
    private var crossedDrop = false

    /**
     * Recent-history reads asked for (stale, reconnect, deferred overflow). The gateway read is
     * slice 2.
     */
    var refreshRequests: Int = 0
        private set

    // Derive caches
    private var turnCache = HashMap<String, TurnEntry>()
    private val prefixEntries = ArrayList<TurnEntry>()

    // Issue 147: the last grouping into turns, the turn each message is in, and the first turn a change since the last
    // derive can move. Messages only ever join at the end (a history page reorders, and regroups everything), so a
    // derive groups again only from the turn before the first changed one.
    private val turns = ArrayList<Pair<String, List<MsgEntry>>>()
    private val turnOf = HashMap<String, Int>()
    private val joined = ArrayList<MsgEntry>()
    private var changedFrom = REGROUP_ALL
    private var prefixList: PersistentList<TranscriptRow> = persistentListOf()
    private var prefixPositions: PersistentMap<String, Int> = persistentMapOf()
    private var prefixSearch: PersistentList<SearchText> = persistentListOf()
    private val rowCache = HashMap<String, TranscriptRow>()

    // STREAM-FIX newest-turn cache: the published list is prefix + newest rows; a publish rewrites
    // only the suffix of
    // the newest turn that changed (by identity), never all of its rows.
    private var newestBase: PersistentList<TranscriptRow>? = null
    private var newestTurnId: String? = null
    private var newestRows: List<TranscriptRow> = emptyList()
    private var fullList: PersistentList<TranscriptRow> = persistentListOf()
    private var fullPositions: PersistentMap<String, Int> = persistentMapOf()

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
            is GatewayEvent.Notice -> {
                if (event.messageId !in messages) {
                    refreshRequests++
                    false
                } else {
                    upsertPart(
                        Part(
                            event.noticeId,
                            event.messageId,
                            event.sessionId,
                            PartType.Notice,
                            text = event.kind.name,
                        )
                    )
                }
            }
        }
    }

    /**
     * Cache-first: the newest [limit] messages as they read now (streamed text and reasoning folded in), oldest
     * first, so a conversation's last page can be kept and drawn at once next time it opens.
     */
    fun kept(limit: Int): List<MessageWithParts> =
        messages.values.toList().takeLast(limit).map { m ->
            MessageWithParts(
                m.info,
                m.parts.values.map { pe ->
                    when (pe.part.type) {
                        PartType.Text -> pe.blocks?.text?.toString()?.let { pe.part.copy(text = it) } ?: pe.part
                        PartType.Reasoning -> pe.raw?.toString()?.let { pe.part.copy(text = it) } ?: pe.part
                        else -> pe.part
                    }
                },
            )
        }

    /** A history page (oldest first). Loaded parts never animate. */
    fun loadHistory(page: List<MessageWithParts>, beforeVersion: Long = Long.MAX_VALUE) {
        for (m in page) {
            val existing = messages[m.info.id]
            if (existing == null || existing.version <= beforeVersion) upsertMessage(m.info)
            for (p in m.parts) {
                val live = messages[m.info.id]?.parts?.get(p.id)
                if (live == null || live.version <= beforeVersion) upsertPart(p)
            }
        }
        // Pages request older rows. Preserve the page order before the already loaded suffix;
        // streaming additions
        // keep their relative order. A first load still uses the server's oldest-first ordering.
        val pageIds = page.map { it.info.id }
        val priorIds =
            messages.keys.toList().filter {
                it !in pageIds || (messages[it]?.version ?: 0) > beforeVersion
            }
        val overlap = messages.keys.firstOrNull { it in pageIds }
        val prefix =
            if (overlap == null) emptyList()
            else messages.keys.takeWhile { it != overlap }.filter { it !in pageIds }
        val ordered = LinkedHashMap<String, MsgEntry>()
        (prefix + pageIds + priorIds).forEach { id ->
            messages[id]?.let { ordered.putIfAbsent(id, it) }
        }
        messages.forEach { (id, m) -> ordered.putIfAbsent(id, m) }
        messages.clear()
        messages.putAll(ordered)
        // A page puts messages before the ones already loaded: group every turn again.
        changedFrom = REGROUP_ALL
    }

    /** [m] changed: its turn, and the turn before it (its prompt may have stopped being one), group again. */
    private fun touched(m: MsgEntry) {
        val at = turnOf[m.info.id] ?: (turns.size - 1)
        if (at < changedFrom) changedFrom = at.coerceAtLeast(0)
    }

    /** A new message: it joins after every message already grouped, so only the last turn can take it. */
    private fun joinedAtEnd(m: MsgEntry) {
        joined += m
        if (turns.size - 1 < changedFrom) changedFrom = (turns.size - 1).coerceAtLeast(0)
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
            messages[info.id] = MsgEntry(info, v).also(::joinedAtEnd)
        } else {
            entry.info = info
            entry.version = v
            touched(entry)
        }
        if (info.completedMs != null) messages[info.id]?.let(::endParts)
        deferredParts.remove(info.id)?.forEach { upsertPart(it) }
        return true
    }

    private fun upsertPart(part: Part, create: MessageInfo? = null): Boolean {
        val v = stamp()
        val msg =
            messages[part.messageId]
                ?: if (create != null) MsgEntry(create, v).also { messages[create.id] = it; joinedAtEnd(it) }
                else null
        if (msg == null) {
            deferredParts.getOrPut(part.messageId) { mutableListOf() } += part
            boundDeferred()
            return false
        }
        msg.version = v
        touched(msg)
        val existing = msg.parts[part.id]
        val entry = existing ?: PartEntry(part, v).also { msg.parts[part.id] = it }
        entry.part = part
        entry.version = v
        when (part.type) {
            PartType.Text -> {
                val blocks = entry.blocks ?: StreamingBlocks().also { entry.blocks = it }
                // Whole part wins: equal text keeps the blocks, `old + more` appends, anything else
                // re-parses (§3.8).
                if (part.text.isNotEmpty() || existing == null) replaceCounted(blocks, part.text)
            }
            PartType.Reasoning ->
                if (part.text.isNotEmpty() || entry.raw == null)
                    entry.raw = StringBuilder(part.text)
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
            deferredDeltas
                .getOrPut(deltaKey(e.messageId, e.partId)) { StringBuilder() }
                .append(e.delta)
            deferredChars += e.delta.length
            boundDeferred()
            return false
        }
        msg.version = v
        touched(msg)
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

    /**
     * Plan 09 §3.2 bounds [new]: 256 deferred keys and 1 MiB of deferred text; overflow drops the
     * oldest + refresh.
     */
    private fun boundDeferred() {
        while (
            deferredParts.size + deferredDeltas.size > MAX_DEFERRED_KEYS ||
                deferredChars > MAX_DEFERRED_CHARS
        ) {
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
                touched(m)
            }
        }
    }

    // ---------------------------------------------------------------- derive

    fun derive(revision: Long): TranscriptState {
        counters.derives++
        val from = regroup()
        val newestIndex = turns.lastIndex
        val settledCount = newestIndex.coerceAtLeast(0)
        // Settled prefix: every turn but the newest. The turns before [from] did not change and keep their entries (the
        // same objects); from there, reuse the cached rows of unchanged turns, and the previous persistent prefix when
        // it is a prefix of this one (the common case while a reply streams).
        val start = minOf(from, settledCount, prefixEntries.size)
        val fresh = ArrayList<TurnEntry>(settledCount - start)
        for (i in start until settledCount) {
            val (turnId, members) = turns[i]
            counters.historyVisits += 1 + members.size
            val cached = turnCache[turnId]
            fresh +=
                if (cached != null && cached.matches(members)) {
                    cached
                } else {
                    buildTurn(turnId, members, finished = true).also { turnCache[turnId] = it }
                }
        }
        var common = start
        while (
            common < prefixEntries.size &&
                common < settledCount &&
                prefixEntries[common] === fresh[common - start]
        ) {
            common++
            counters.historyVisits++
        }
        val oldSize = prefixEntries.size
        prefixEntries.subList(start, prefixEntries.size).clear()
        prefixEntries.addAll(fresh)
        val entries: List<TurnEntry> = prefixEntries
        var list: PersistentList<TranscriptRow>
        var positions: PersistentMap<String, Int>
        var search: PersistentList<SearchText>
        if (common == oldSize) {
            list = prefixList
            positions = prefixPositions
            search = prefixSearch
        } else {
            list = persistentListOf()
            positions = persistentMapOf()
            search = persistentListOf()
            common = 0
        }
        for (i in common until entries.size) {
            val (l, p) = appendRows(list, positions, entries[i].rows)
            list = l
            positions = p
            if (entries[i].search.isNotEmpty()) search = search.addAll(entries[i].search)
        }
        prefixList = list
        prefixPositions = positions
        prefixSearch = search
        if (newestIndex >= 0) {
            val (turnId, members) = turns[newestIndex]
            val newest = buildTurn(turnId, members, finished = !run.busy)
            val rows = newest.rows
            if (newest.search.isNotEmpty()) search = search.addAll(newest.search)
            if (newestBase === list && newestTurnId == turnId) {
                replaceNewest(list.size, rows)
            } else {
                val (l, p) = appendRows(list, positions, rows)
                counters.rowWrites += rows.size
                fullList = l
                fullPositions = p
            }
            newestBase = list
            newestTurnId = turnId
            newestRows = rows
            list = fullList
            positions = fullPositions
        } else {
            newestBase = null
            newestTurnId = null
            newestRows = emptyList()
        }
        return TranscriptState(
            revision,
            // The settled turns' rows go with them as one list, the same instance while none changed (issue 147).
            NewestFirst(list, prefixList),
            positions,
            run,
            search,
        )
    }

    /**
     * The newest turn's rows changed from [newestRows] to [rows] over the same prefix of [base]
     * rows: keep the common leading rows (same instances) and rewrite only the rest, so a streaming
     * publish writes O(changed rows).
     */
    private fun replaceNewest(base: Int, rows: List<TranscriptRow>) {
        val old = newestRows
        var keep = 0
        while (keep < old.size && keep < rows.size && old[keep] === rows[keep]) keep++
        if (keep == old.size && keep == rows.size) return
        val list = fullList.builder()
        val pos = fullPositions.builder()
        for (i in old.lastIndex downTo keep) {
            list.removeAt(base + i)
            pos.remove(old[i].key)
        }
        for (i in keep until rows.size) {
            list.add(rows[i])
            pos[rows[i].key] = base + i
        }
        counters.rowWrites += (old.size - keep) + (rows.size - keep)
        fullList = list.build()
        fullPositions = pos.build()
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

    /**
     * Turns by prompt (never by `role != assistant`, §3.6): a user message that is not a notice
     * starts a turn. Issue 147: only from the turn before the first one a change since the last derive touched (a
     * changed first message may have stopped being a prompt and joined it); the turns before it keep their grouping.
     * Returns the index of the first turn grouped again ([turns]' size when nothing changed).
     */
    private fun regroup(): Int {
        val from: Int
        val again: List<MsgEntry>
        if (changedFrom == REGROUP_ALL || turns.isEmpty()) {
            from = 0
            again = messages.values.toList()
            turns.clear()
            turnOf.clear()
        } else if (changedFrom == NOTHING_CHANGED) {
            return turns.size
        } else {
            from = (changedFrom - 1).coerceAtLeast(0)
            val tail = ArrayList<MsgEntry>()
            for (i in from until turns.size) tail.addAll(turns[i].second)
            tail.addAll(joined)
            again = tail
            turns.subList(from, turns.size).clear()
        }
        joined.clear()
        changedFrom = NOTHING_CHANGED
        var current: ArrayList<MsgEntry>? = null
        var currentId = "head"
        for (m in again) {
            counters.historyVisits++
            // A picture or file sent without words is a prompt too (its own turn, shown with its thumbnail).
            val isPrompt =
                m.info.role == Role.User &&
                    m.parts.values.any {
                        it.part.type == PartType.Text ||
                            (it.part.type == PartType.File && !it.part.synthetic)
                    }
            if (isPrompt || current == null) {
                current?.let { turns += currentId to it }
                current = ArrayList()
                currentId = if (isPrompt) m.info.id else "head"
            }
            current.add(m)
            turnOf[m.info.id] = turns.size
        }
        current?.let { turns += currentId to it }
        return from
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
        var stopped = false
        var reason: String? = null
        var errorKind: MessageErrorKind? = null
        for (m in members) {
            if (m.info.role == Role.Assistant) {
                hasAssistant = true
                // B-CHAT-101: the turn's outcome is its newest assistant step's (Flutter). A step
                // that failed and
                // was followed by one that finished is not a failed turn: each step resets what the
                // last one said.
                val error = m.info.errorText
                errorKind = m.info.errorKind
                stopped = errorKind == MessageErrorKind.Aborted || (error != null && isStopError(error))
                failed = (error != null || errorKind != null) && !stopped
                reason = if (failed) error?.trim()?.takeIf { it.isNotEmpty() } else null
            }
            val sentFiles =
                if (m.info.role != Role.User) emptyList()
                else
                    m.parts.values
                        .filter { it.part.type == PartType.File && !it.part.synthetic }
                        .map { PromptFile(it.part.filename.orEmpty(), it.part.mime, it.part.url) }
            var filesShown = sentFiles.isEmpty()
            for (pe in m.parts.values) {
                val p = pe.part
                when (p.type) {
                    PartType.Text ->
                        if (m.info.role == Role.User) {
                            val row = promptRowOf(pe, m.info.id, turnId, m.info.createdMs)
                            rows +=
                                if (filesShown) row
                                else {
                                    filesShown = true
                                    reuse(row.copy(files = sentFiles))
                                }
                        } else {
                            addBlocks(rows, turnId, pe, live = !p.ended && !finished)
                        }
                    PartType.Reasoning -> {
                        val text = pe.raw ?: continue
                        val first = text.lineSequence().firstOrNull { it.isNotBlank() } ?: continue
                        val made = text.toString()
                        counters.thoughtChars += made.length
                        rows +=
                            reuse(
                                ThoughtRow(
                                    "thought:${p.messageId}/${p.id}",
                                    turnId,
                                    first.trim(),
                                    text = made,
                                    live = !p.ended && !finished,
                                )
                            )
                    }
                    PartType.Tool -> {
                        if (workAt < 0) workAt = rows.size
                        calls++
                        when (p.toolStatus) {
                            ToolStatus.Error -> failedCalls++
                            ToolStatus.Pending,
                            ToolStatus.Running,
                            null -> running = true
                            else -> Unit
                        }
                    }
                    PartType.Notice,
                    PartType.Switch,
                    PartType.Compaction ->
                        // B0: part ids are only unique inside one message (OC2 gives every notice
                        // the same one), so the key names the message too, as ThoughtRow does.
                        rows +=
                            reuse(
                                NoticeRow("notice:${p.messageId}/${p.id}", turnId, noticeKindOf(p))
                            )
                    else -> Unit
                }
            }
            if (!filesShown)
                rows +=
                    reuse(
                        PromptRow(
                            "prompt:${m.info.id}",
                            turnId,
                            "",
                            m.info.createdMs,
                            files = sentFiles,
                        )
                    )
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
        if (finished && hasAssistant) {
            rows +=
                reuse(
                    FooterRow(
                        "footer:$turnId",
                        turnId,
                        failed,
                        reason,
                        stopped = stopped && !failed,
                        errorKind = errorKind,
                        createdMs =
                            members.lastOrNull { it.info.role == Role.Assistant }?.info?.createdMs,
                        providerId =
                            members
                                .lastOrNull { it.info.role == Role.Assistant }
                                ?.info
                                ?.providerId
                                ?.takeIf { it.isNotBlank() },
                    )
                )
        }
        return TurnEntry(
            turnId,
            members,
            LongArray(members.size) { members[it].version },
            rows,
            searchOf(turnId, members, rows),
        )
    }

    /**
     * Find's text of one turn's parts that draw no text row of their own (thoughts, steps, notices, files), each tied
     * to the row that shows it. Made with the turn's rows, so a settled turn's text is made once, never per publish
     * (issue 147). A thought reuses its row's text instead of copying the part again.
     */
    private fun searchOf(turnId: String, members: List<MsgEntry>, rows: List<TranscriptRow>): List<SearchText> {
        var out: ArrayList<SearchText>? = null
        var byKey: HashMap<String, TranscriptRow>? = null
        fun row(key: String): TranscriptRow? =
            (byKey ?: HashMap<String, TranscriptRow>(rows.size * 2).also { map ->
                for (r in rows) map.getOrPut(r.key) { r }
                byKey = map
            })[key]
        for (m in members) for (entry in m.parts.values) {
            counters.historyVisits++
            val part = entry.part
            val key =
                when (part.type) {
                    PartType.Reasoning -> "thought:${part.messageId}/${part.id}"
                    PartType.Tool -> "work:$turnId"
                    PartType.Notice,
                    PartType.Switch,
                    PartType.Compaction -> "notice:${part.messageId}/${part.id}"
                    PartType.File ->
                        "footer:$turnId".takeIf { row(it) != null }
                            ?: "prompt:$turnId".takeIf { row(it) != null }
                            ?: rows.firstOrNull()?.key
                    else -> null
                } ?: continue
            val shown = row(key) ?: continue
            val text =
                if (part.type == PartType.Reasoning) (shown as? ThoughtRow)?.text ?: entry.raw?.toString().orEmpty()
                else
                    listOf(part.searchText, entry.raw?.toString().orEmpty())
                        .filter { it.isNotBlank() }
                        .joinToString("\n")
                        .also { counters.searchChars += it.length }
            if (text.isNotBlank())
                (out ?: ArrayList<SearchText>().also { out = it }) +=
                    SearchText(key, "${part.messageId}/${part.id}", text)
        }
        return out ?: emptyList()
    }

    private fun promptRowOf(
        pe: PartEntry,
        messageId: String,
        turnId: String,
        createdMs: Long?,
    ): PromptRow {
        val cached = pe.promptRow
        if (
            cached != null &&
                pe.promptVersion == pe.version &&
                cached.turnId == turnId &&
                cached.createdMs == createdMs
        )
            return cached
        val row =
            reuse(
                PromptRow(
                    "prompt:$messageId",
                    turnId,
                    pe.blocks?.text?.toString() ?: pe.part.text,
                    createdMs,
                )
            )
        pe.promptRow = row
        pe.promptVersion = pe.version
        return row
    }

    private fun noticeKindOf(p: Part): NoticeKind =
        NoticeKind.entries.firstOrNull { it.name == p.text } ?: NoticeKind.Other

    private fun addBlocks(
        rows: MutableList<TranscriptRow>,
        turnId: String,
        pe: PartEntry,
        live: Boolean,
    ) {
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
            pe.settledRows +=
                BlockRow(
                    "b:${pe.blockKeyBase}:${b.start}",
                    turnId,
                    b.kind,
                    pe.part.id,
                    b.start,
                    b.text,
                    false,
                    false,
                    origin,
                    b.continued,
                    b.continues,
                    b.depth,
                )
        }
        rows.addAll(pe.settledRows)
        val before = blocks.tailReparses
        val charsBefore = blocks.tailChars
        val tail = blocks.tail()
        counters.tailReparses += blocks.tailReparses - before
        counters.tailChars += blocks.tailChars - charsBefore
        if (tail == null) return
        val prev = pe.tailRow
        val row =
            if (
                prev != null &&
                    prev.start == tail.start &&
                    prev.text == tail.text &&
                    prev.live == live &&
                    prev.kind == tail.kind &&
                    prev.open == tail.open &&
                    prev.continued == tail.continued &&
                    prev.depth == tail.depth
            ) {
                prev
            } else {
                BlockRow(
                    "b:${pe.blockKeyBase}:${tail.start}",
                    turnId,
                    tail.kind,
                    pe.part.id,
                    tail.start,
                    tail.text,
                    live,
                    tail.open,
                    origin,
                    tail.continued,
                    depth = tail.depth,
                )
            }
        pe.tailRow = row
        rows += row
    }

    /** Equal row → the previous instance, so the list skips it by identity. */
    private fun <T : TranscriptRow> reuse(row: T): T {
        val old = rowCache[row.key]
        @Suppress("UNCHECKED_CAST") if (old != null && old == row) return old as T
        rowCache[row.key] = row
        return row
    }

    private fun deltaKey(messageId: String, partId: String) = "$messageId\u0000$partId"

    private companion object {
        const val REGROUP_ALL = -1
        const val NOTHING_CHANGED = Int.MAX_VALUE
        const val MAX_DEFERRED_KEYS = 256
        const val MAX_DEFERRED_CHARS = 1 shl 20
    }
}

/**
 * QA wave 4 N3: the error a stopped step carries (OC2 `Step interrupted`, OC1
 * `MessageAbortedError`).
 */
internal fun isStopError(text: String): Boolean = STOP_TEXT.containsMatchIn(text)

private val STOP_TEXT =
    Regex(
        "^\\s*(the )?(step|run|tool execution|operation|request)?\\s*(was )?(interrupted|aborted)\\b|MessageAbortedError",
        RegexOption.IGNORE_CASE,
    )
