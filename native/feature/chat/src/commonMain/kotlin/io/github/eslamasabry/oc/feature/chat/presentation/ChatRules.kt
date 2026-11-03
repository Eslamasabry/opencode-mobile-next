package io.github.eslamasabry.oc.feature.chat.presentation

import io.github.eslamasabry.oc.engine.transcript.state.BlockRow
import io.github.eslamasabry.oc.engine.transcript.state.FooterRow
import io.github.eslamasabry.oc.engine.transcript.state.PromptRow
import io.github.eslamasabry.oc.engine.transcript.state.RowKind
import io.github.eslamasabry.oc.engine.transcript.state.RunFacts
import io.github.eslamasabry.oc.engine.transcript.state.RunOutcome
import io.github.eslamasabry.oc.engine.transcript.state.ThoughtRow
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptRow
import io.github.eslamasabry.oc.feature.chat.world.ChatLink
import io.github.eslamasabry.oc.feature.chat.world.ChatLoad
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.feature.chat.world.PendingSend
import io.github.eslamasabry.oc.feature.chat.world.PendingState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/*
 * The chat screen's pure rules. Each is O(the side table) or O(one turn) except where it says otherwise; none parses
 * Markdown, sorts rows or builds text for display. Every rule is table-tested in commonTest.
 */

/**
 * The message a row belongs to. The P3 rows carry it only in their key (`prompt:<m>`, `b:<m>/<p>:<s>`,
 * `thought:<m>/<p>`); notices, work lines and footers carry none. S2-TX request: a `messageId` field on every row.
 */
public object RowIds {
    public fun messageIdOf(row: TranscriptRow): String? =
        when (row) {
            is PromptRow -> row.key.removePrefix("prompt:").takeIf { it.isNotEmpty() && it != row.key }
            is BlockRow -> row.key.between("b:", "/")
            is ThoughtRow -> row.key.between("thought:", "/")
            else -> null
        }

    private fun String.between(prefix: String, end: String): String? {
        if (!startsWith(prefix)) return null
        val stop = indexOf(end, prefix.length)
        return if (stop > prefix.length) substring(prefix.length, stop) else null
    }
}

/** The one status line (plan 09 §6.5 order; 07 §10.1 puts the connection first). Most urgent wins; ties keep first. */
public object StatusPick {
    public fun pick(
        link: ChatLink,
        pending: List<PendingSend>,
        run: RunFacts,
        rows: List<TranscriptRow>,
        actionFailure: Failure?,
        linkRefused: Boolean = false,
    ): ChatStatusUi? {
        when (link) {
            ChatLink.Offline -> return ChatStatusUi(CONNECTION, ChatStatusKind.Offline)
            ChatLink.Connecting -> return ChatStatusUi(CONNECTION, ChatStatusKind.Connecting)
            ChatLink.Live -> Unit
        }
        // The newest bubble speaks for the sends; an older failure stays on its own bubble.
        pending.lastOrNull { it.state == PendingState.Failed }?.let {
            return ChatStatusUi("send-${it.localId}", ChatStatusKind.SendFailed, it.failure, it.localId)
        }
        pending.lastOrNull { it.state == PendingState.Unconfirmed }?.let {
            return ChatStatusUi("send-${it.localId}", ChatStatusKind.SendUnconfirmed, null, it.localId)
        }
        if (!run.busy && run.lastOutcome is RunOutcome.Failed && !newestTurnSaysFailed(rows)) {
            return ChatStatusUi(RUN, ChatStatusKind.RunFailed)
        }
        if (actionFailure != null) return ChatStatusUi(ACTION, ChatStatusKind.ActionFailed, actionFailure)
        if (linkRefused) return ChatStatusUi(LINK, ChatStatusKind.LinkRefused)
        return null
    }

    /** B-CHAT-071: no second error when the reply already says why (its footer is a failed one). O(newest turn). */
    internal fun newestTurnSaysFailed(rows: List<TranscriptRow>): Boolean {
        val turn = rows.firstOrNull()?.turnId ?: return false
        for (row in rows) {
            if (row.turnId != turn) return false
            if (row is FooterRow) return row.failed
        }
        return false
    }

    public const val CONNECTION: String = "connection"
    public const val RUN: String = "run"
    public const val ACTION: String = "action"
    public const val LINK: String = "link"
}

/** One find hit: [start] until [end] in the row's own text. Hits are in reading order (oldest first). */
public data class FindHit(val rowKey: String, val start: Int, val end: Int, val sourceId: String = rowKey)

/**
 * Find over the loaded rows (plan 09 §6.6; port of Flutter `TranscriptSearchIndex.search`): literal, case-insensitive,
 * non-overlapping, in reading order, over the person's prompts, reply blocks and thought lines (never app words).
 * O(rows); the ViewModel runs it off the main thread. S2-TX request: `handle.search` (B-CHAT-115) replaces it.
 */
public object TranscriptFind {
    public fun search(state: io.github.eslamasabry.oc.engine.transcript.state.TranscriptState, query: String): ImmutableList<FindHit> {
        val q = query.trim()
        if (q.isEmpty()) return persistentListOf()
        val extra = state.searchText.groupBy { it.rowKey }
        val hits = ArrayList<FindHit>()
        for (row in state.rows.asReversed()) {
            val sources = extra[row.key]
            if (sources == null) hits += search(listOf(row), q) else for (source in sources) {
                var at = source.text.indexOf(q, ignoreCase = true)
                while (at >= 0) {
                    hits += FindHit(row.key, at, at + q.length, source.sourceId)
                    at = source.text.indexOf(q, at + q.length, ignoreCase = true)
                }
            }
        }
        return hits.toImmutableList()
    }

    public fun search(rows: List<TranscriptRow>, query: String): ImmutableList<FindHit> {
        val q = query.trim()
        if (q.isEmpty()) return persistentListOf()
        val hits = ArrayList<FindHit>()
        for (i in rows.indices.reversed()) {
            val row = rows[i]
            val text = searchable(row) ?: continue
            var at = text.indexOf(q, 0, ignoreCase = true)
            while (at >= 0) {
                hits += FindHit(row.key, at, at + q.length)
                at = text.indexOf(q, at + q.length, ignoreCase = true)
            }
        }
        return hits.toImmutableList()
    }

    private fun searchable(row: TranscriptRow): String? =
        when (row) {
            is PromptRow -> row.text
            is BlockRow -> row.text
            is ThoughtRow -> row.firstLine
            else -> null
        }

    /**
     * Flutter `_syncFind`: the current hit is kept by identity across updates (streaming, older pages, deletion);
     * when it is gone the cursor stays at its number, clamped. Returns the new cursor (-1 with no hits).
     */
    public fun keep(hits: List<FindHit>, key: FindHit?, cursor: Int): Int {
        if (hits.isEmpty()) return -1
        val kept = if (key == null) -1 else hits.indexOf(key)
        return if (kept >= 0) kept else cursor.coerceIn(0, hits.size - 1)
    }

    /** Next (+1) or previous (-1), wrapping (Flutter `_navigateFind`). */
    public fun step(size: Int, cursor: Int, by: Int): Int = if (size == 0) -1 else ((cursor + by) % size + size) % size
}

/** One action list per message for every way in (plan 09 §6.6), in menu order, destructive last. */
public object MessageActions {
    public fun of(kind: TargetKind, caps: ChatCapabilitiesUi, busy: Boolean, messageId: String?, hasText: Boolean): ImmutableList<MessageAction> =
        of(kind, caps, busy, messageId, hasText, feel = false)

    /**
     * P12-CHAT-FEEL, Flutter `_messageContextActions` order: Copy, Share, Select text, Edit and resend (prompt), Send
     * again (prompt, not while a reply runs: steer, never queue), Fork (prompt), Undo from here (a saved prompt, the
     * server undoes, not while a reply runs), Delete last. [feel] = false keeps the slice-2 list.
     */
    public fun of(
        kind: TargetKind,
        caps: ChatCapabilitiesUi,
        busy: Boolean,
        messageId: String?,
        hasText: Boolean,
        feel: Boolean,
    ): ImmutableList<MessageAction> {
        val out = ArrayList<MessageAction>(8)
        val prompt = kind == TargetKind.Prompt
        if (hasText) out += MessageAction.Copy
        if (feel && hasText) out += MessageAction.Share
        if (feel && hasText) out += MessageAction.SelectText
        if (kind == TargetKind.Reply && hasText) out += MessageAction.ReadAloud
        if (feel && prompt && hasText) out += MessageAction.EditAndResend
        if (prompt && hasText && !busy) out += MessageAction.SendAgain
        if (prompt && caps.fork && messageId != null) out += MessageAction.Fork
        if (kind == TargetKind.Reply && caps.changes && messageId != null) out += MessageAction.Changes
        if (prompt && caps.undo && messageId != null && !busy) out += MessageAction.UndoFromHere
        if (caps.deleteMessage && messageId != null) out += MessageAction.Delete
        return out.toImmutableList()
    }
}

/**
 * Copy reply (plan 09 §6.6): the turn's assistant prose in order, notices, thoughts and work lines skipped, the
 * person's words verbatim. P3 blocks drop the blank lines between them, so blocks are joined with one blank line.
 * S2-TX request: the part's own text, so the copy is byte-exact. O(rows).
 */
public object ReplyText {
    public fun of(rows: List<TranscriptRow>, turnId: String): String? {
        val parts = ArrayList<String>()
        var part: String? = null
        var previous: BlockRow? = null
        val sb = StringBuilder()
        for (i in rows.indices.reversed()) {
            val row = rows[i] as? BlockRow ?: continue
            if (row.turnId != turnId) continue
            if (part != row.partId && sb.isNotEmpty()) {
                parts += sb.toString()
                sb.clear()
                previous = null
            }
            part = row.partId
            // Chunks of one long block were cut at a line end: they join with that one newline, so a fence or a
            // line continuation pastes back exactly as written. Separate blocks keep their blank line.
            val sameBlock = previous != null && previous.continues && row.continued
            if (sb.isNotEmpty()) sb.append(if (sameBlock) "\n" else "\n\n")
            sb.append(row.text)
            previous = row
        }
        if (sb.isNotEmpty()) parts += sb.toString()
        return parts.joinToString("\n\n").takeIf { it.isNotBlank() }
    }
}

/**
 * The "N earlier messages" pill (plan 09 §7.3 rule 6; Flutter `_earlierMessageCount`): the count of whole messages
 * fully above the viewport is the oldest-first ordinal of the oldest visible row's message. Built once per rows
 * instance (O(rows)), then O(1) per scroll. S2-TX request: `TranscriptState.messageOrder` makes this free.
 */
public class MessageOrder(rows: List<TranscriptRow>) {
    private val ordinal = IntArray(rows.size)

    /** Distinct messages among the rows. */
    public val messageCount: Int

    init {
        val seen = HashMap<String, Int>()
        var current = 0
        for (i in rows.indices.reversed()) {
            val id = RowIds.messageIdOf(rows[i])
            if (id != null) current = seen.getOrPut(id) { seen.size }
            ordinal[i] = current
        }
        messageCount = seen.size
    }

    /** Messages fully above the row at newest-first [oldestVisibleRow]; 0 outside the rows. */
    public fun earlierCount(oldestVisibleRow: Int): Int = if (oldestVisibleRow in ordinal.indices) ordinal[oldestVisibleRow] else 0

    public companion object {
        /** Flutter shows the pill only for conversations longer than 30 messages. */
        public const val MIN_MESSAGES: Int = 30

        public fun pillVisible(awayFromLatest: Boolean, messageCount: Int, earlier: Int): Boolean =
            awayFromLatest && messageCount > MIN_MESSAGES && earlier > 0
    }
}

/** Read acknowledgement (plan 09 §6.7): only while loaded, without a load error, at the live edge. */
public object ReadAck {
    public fun allowed(load: ChatLoad, atLiveEdge: Boolean): Boolean = load is ChatLoad.Live && atLiveEdge
}

/** `LandOnFailure` (plan 09 §6.7): the newest failed turn's footer. O(rows) worst case, once per arrival. */
public object Landing {
    public fun newestFailedKey(rows: List<TranscriptRow>): String? = rows.firstOrNull { it is FooterRow && it.failed }?.key
}

/** Who owns Back, top wins (07 §3.3): menu, dialog, the find bar, then the page's own guard (§3.4), then Nav3. */
public enum class BackStep { CloseMenu, CloseConfirm, CloseFind, SaveDraftThenLeave, Leave }

/** P12-CHAT-FEEL: a code block split into chunk rows (STREAM-FIX) copies as one block. */
public object CodeCopy {
    /**
     * The whole fence [rowKey] belongs to, without its fence lines: walks the adjacent chunk rows of the same part
     * (`continued`/`continues`). O(chunks of one block).
     */
    public fun of(rows: List<TranscriptRow>, index: Int): String? {
        val row = rows.getOrNull(index) as? BlockRow ?: return null
        if (row.kind != RowKind.Code) return null
        var newest = index
        while ((rows[newest] as BlockRow).continues) {
            val next = rows.getOrNull(newest - 1) as? BlockRow ?: break
            if (next.partId != row.partId || !next.continued) break
            newest--
        }
        var oldest = index
        while ((rows[oldest] as BlockRow).continued) {
            val prev = rows.getOrNull(oldest + 1) as? BlockRow ?: break
            if (prev.partId != row.partId || !prev.continues) break
            oldest++
        }
        val joined = (oldest downTo newest).joinToString("\n") { (rows[it] as BlockRow).text }
        return FenceText.body(joined)
    }
}

/**
 * A Markdown table longer than one STREAM-FIX chunk (plan 09 §9.1; Flutter parses the whole table): only the first
 * chunk has the header and delimiter lines, so each chunk parsed alone turned the later rows into pipe-filled prose.
 * [of] maps the rows of each such block: its first chunk's key to the whole block's text (drawn there, as one table),
 * and every later chunk's key to null (drawn empty). Other rows are absent. O(rows), once per rows instance.
 */
public object LongTable {
    private val DELIMITER = Regex("^\\s*\\|?\\s*:?-+:?\\s*(\\|\\s*:?-+:?\\s*)*\\|?\\s*$")

    public fun of(rows: List<TranscriptRow>): Map<String, String?> {
        var out: HashMap<String, String?>? = null
        var i = rows.lastIndex
        // Rows are newest first: a block's first chunk is its oldest (highest index).
        while (i >= 0) {
            val head = rows[i] as? BlockRow
            if (head == null) {
                i--
                continue
            }
            if (head.kind == RowKind.Code || head.continued || !head.continues || !opensTable(head.text)) {
                i--
                continue
            }
            val sb = StringBuilder(head.text)
            val tails = ArrayList<String>()
            var j = i - 1
            var previous: BlockRow = head
            while (j >= 0 && previous.continues) {
                val next = rows[j] as? BlockRow ?: break
                if (next.partId != head.partId || !next.continued) break
                sb.append('\n').append(next.text)
                tails += next.key
                previous = next
                j--
            }
            val map = out ?: HashMap<String, String?>().also { out = it }
            map[head.key] = sb.toString()
            for (key in tails) map[key] = null
            i = j
        }
        return out ?: emptyMap()
    }

    private fun opensTable(text: String): Boolean {
        val lines = text.lineSequence().dropWhile { it.isBlank() }.take(2).toList()
        return lines.size == 2 && '|' in lines[0] && '|' in lines[1] && DELIMITER.matches(lines[1])
    }
}

/** The body of a fenced block's source: the opening ``` line and a closing fence line dropped. */
public object FenceText {
    public fun body(source: String): String {
        var lines = source.split('\n')
        if (lines.firstOrNull()?.trimStart()?.startsWith("```") == true) lines = lines.drop(1)
        if (lines.isNotEmpty() && lines.last().trim().startsWith("```") && lines.last().trim().trim('`').isEmpty()) {
            lines = lines.dropLast(1)
        }
        return lines.joinToString("\n")
    }

    /** The language word after the opening fence, or null. */
    public fun info(source: String): String? {
        val first = source.substringBefore('\n').trimStart()
        if (!first.startsWith("```")) return null
        return first.trimStart('`').trim().substringBefore(' ').ifEmpty { null }
    }
}

public object BackOrder {
    public fun next(menuOpen: Boolean, confirmOpen: Boolean, findOpen: Boolean, draftUnsaved: Boolean): BackStep =
        when {
            menuOpen -> BackStep.CloseMenu
            confirmOpen -> BackStep.CloseConfirm
            findOpen -> BackStep.CloseFind
            draftUnsaved -> BackStep.SaveDraftThenLeave
            else -> BackStep.Leave
        }

    /** A handler that is enabled turns off the predictive preview, so it is on only while it has something to do. */
    public fun handles(menuOpen: Boolean, confirmOpen: Boolean, findOpen: Boolean, draftUnsaved: Boolean): Boolean =
        next(menuOpen, confirmOpen, findOpen, draftUnsaved) != BackStep.Leave
}

/** Row kinds the person can open a message menu from (prompt long-press or tap, the reply's footer). */
public fun TranscriptRow.targetKind(): TargetKind? =
    when (this) {
        is PromptRow -> TargetKind.Prompt
        is FooterRow -> TargetKind.Reply
        else -> if (kind == RowKind.Prompt) TargetKind.Prompt else null
    }
