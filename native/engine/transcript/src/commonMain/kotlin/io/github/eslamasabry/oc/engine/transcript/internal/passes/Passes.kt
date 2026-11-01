package io.github.eslamasabry.oc.engine.transcript.internal.passes

import io.github.eslamasabry.oc.core.model.transcript.MessageWithParts
import io.github.eslamasabry.oc.core.model.transcript.Part
import io.github.eslamasabry.oc.core.model.transcript.PartType
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonPrimitive

/*
 * The display passes (plan 09 §3.6, map CHAT-GENUI-05), ported as pure functions over immutable snapshots:
 * - settleStaleLive    ← lib/domain/transcript_settle.dart
 * - markFoldedWork     ← transcript_turns.dart `_markFoldedWork` (+ `_isPassingWords`)
 * - timelineDisplay    ← transcript_turns.dart `_timelineDisplayParts`
 * - mergeTextParts     ← transcript_turns.dart `_mergeTextParts` (linear)
 * - turnActionOwners   ← transcript_turns.dart `_turnActionOwners`
 * - deriveTurns        ← transcript_turns.dart `_isPrompt` / `_endsTurn`
 * Never remove a message in a pass; neutralise it (the 2026-10-28 index bug, 43fabb514).
 */

/** The kind a running compaction line takes once its moment has passed: it draws nothing (Dart `compactionPassed`). */
internal const val COMPACTION_PASSED: String = "passed"

/** Dart `v2VariantPart`: the mapper's v2 variant tag (switch / notice / compaction, or any other `v2:` type). */
internal fun MessageWithParts.variantPart(): Part? = parts.firstOrNull { it.isVariant() }

internal fun Part.isVariant(): Boolean =
    type == PartType.Switch || type == PartType.Notice || type == PartType.Compaction || rawType.startsWith("v2:")

/** Dart `_isPrompt`: something a person wrote, not a notice the server filed under the same role. */
internal fun MessageWithParts.isPrompt(): Boolean = info.role == Role.User && variantPart() == null

/** Dart `_endsTurn`: nothing but notices stands between the assistant message at [index] and the next prompt. */
internal fun endsTurn(messages: List<MessageWithParts>, index: Int): Boolean {
    if (messages[index].info.role != Role.Assistant) return false
    for (next in index + 1 until messages.size) {
        if (messages[next].isPrompt()) return true
        if (messages[next].info.role == Role.Assistant) return false
    }
    return true
}

/** Trap T1 (plan 09 §2.3): status error + the person's refusal words. Local until S2-MODEL adds `ToolState.declined`. */
internal val ToolState.isDeclined: Boolean
    get() {
        if (status != ToolStatus.Error) return false
        val out = output?.lowercase() ?: return false
        return "user declined" in out || "user rejected permission" in out
    }

// ------------------------------------------------------------------------------------------------ settleStaleLive

/**
 * Live state that outlived what it described (B-CHAT-116, B-CHAT-117):
 * - a running compaction line is neutralised to [COMPACTION_PASSED] once anything follows it or the chat is not busy;
 * - a tool still running is shown finished when the same turn wrote words after it or the chat is idle, unless it is a
 *   background process.
 * Returns [messages] itself when nothing needed settling. [moreFollows]: a prompt follows this stretch.
 */
internal fun settleStaleLive(
    messages: List<MessageWithParts>,
    busy: Boolean,
    moreFollows: Boolean = false,
): List<MessageWithParts> {
    var replace: HashMap<Int, MessageWithParts>? = null
    var wordsLater = false
    for (i in messages.indices.reversed()) {
        val message = messages[i]
        // Dart's settle tests prompts by any `v2:` part, the same rule as _isPrompt.
        if (message.isPrompt()) {
            wordsLater = false
            continue
        }
        val compaction = message.parts.any { it.type == PartType.Compaction && it.toolName == "running" }
        if (compaction && (!busy || moreFollows || i < messages.size - 1)) {
            val parts =
                message.parts.map {
                    if (it.type == PartType.Compaction && it.toolName == "running") it.copy(toolName = COMPACTION_PASSED) else it
                }
            (replace ?: HashMap<Int, MessageWithParts>().also { replace = it })[i] =
                MessageWithParts(message.info, parts.toImmutableList())
            continue
        }
        if (message.info.role != Role.Assistant) continue
        var parts: MutableList<Part>? = null
        for (p in message.parts.indices.reversed()) {
            val part = message.parts[p]
            val tool = part.tool
            if (part.type == PartType.Text && part.text.isNotBlank()) {
                wordsLater = true
            } else if (part.type == PartType.Tool && tool != null &&
                (tool.status == ToolStatus.Running || tool.status == ToolStatus.Pending) &&
                tool.ran && (wordsLater || !busy) && !part.isBackground()
            ) {
                val copy = parts ?: message.parts.toMutableList().also { parts = it }
                copy[p] = part.copy(tool = tool.copy(status = ToolStatus.Completed))
            }
        }
        parts?.let { (replace ?: HashMap<Int, MessageWithParts>().also { replace = it })[i] = MessageWithParts(message.info, it.toImmutableList()) }
    }
    val r = replace ?: return messages
    return List(messages.size) { r[it] ?: messages[it] }
}

private fun Part.isBackground(): Boolean {
    val t = tool ?: return false
    fun JsonPrimitive?.isTrue() = this != null && !isString && content == "true"
    return (t.metadata?.get("background") as? JsonPrimitive).isTrue() || (t.input["background"] as? JsonPrimitive).isTrue()
}

// ------------------------------------------------------------------------------------------------ fold marks

/** What [markFoldedWork] folded: part keys (`<messageId>/<partId>`) and notice message ids. */
internal class FoldMarks(val parts: Set<String>, val notices: Set<String>) {
    fun folded(part: Part): Boolean = partKey(part) in parts

    fun foldedNotice(message: MessageWithParts): Boolean = message.info.id in notices

    companion object {
        val None = FoldMarks(emptySet(), emptySet())
    }
}

internal fun partKey(part: Part): String = "${part.messageId}/${part.id}"

/**
 * Dart `_isPassingWords`: one short line, or one line that leads into the next step with a colon. A second line, a
 * paragraph break or a code block makes it an explanation, which stays in view ("the work folds; the prose never
 * hides", decision 2026-09-28).
 */
internal fun isPassingWords(text: String): Boolean {
    val words = text.trim()
    if (words.isEmpty()) return true
    if ('\n' in words || "```" in words) return false
    return words.length <= 80 || (words.endsWith(":") && words.length <= 200)
}

/** Dart `_foldableNotice` (the background-agent-result exception arrives with BackgroundTasks; plan 09 §3.10). */
private fun MessageWithParts.foldableNotice(): Boolean = variantPart()?.type == PartType.Notice

/**
 * Dart `_markFoldedWork`: for each finished turn, the passing words between steps and the mid-turn notices fold under
 * its work line. The running turn ([liveTail]) keeps everything in view.
 */
internal fun markFoldedWork(messages: List<MessageWithParts>, liveTail: Boolean = false): FoldMarks {
    val parts = HashSet<String>()
    val notices = HashSet<String>()
    var start = 0
    fun closeTurn(end: Int, live: Boolean) {
        val entries = ArrayList<Any>() // a Part, or a notice MessageWithParts
        var seenAssistant = false
        for (i in start until end) {
            val message = messages[i]
            if (message.info.role == Role.Assistant) {
                seenAssistant = true
                for (part in message.parts) if (part.isRenderable) entries += part
            } else if (seenAssistant && message.foldableNotice()) {
                entries += message
            }
        }
        if (live) return
        fun isWork(e: Any) = e is Part && (e.type == PartType.Tool || e.type == PartType.Reasoning)
        fun isText(e: Any) = e is Part && e.type == PartType.Text
        val lastWork = entries.indexOfLast(::isWork)
        if (lastWork < 0) return
        var cutoff = lastWork
        if (entries.subList(lastWork + 1, entries.size).none(::isText)) {
            cutoff = entries.indexOfLast(::isText)
            while (cutoff > 0 && isText(entries[cutoff - 1])) cutoff--
        }
        for (i in entries.indices) {
            val e = entries[i]
            if (e is Part && isText(e) && i < cutoff && isPassingWords(e.text)) parts += partKey(e)
            if (e is MessageWithParts) notices += e.info.id
        }
    }
    for (index in messages.indices) {
        if (!messages[index].isPrompt()) continue
        closeTurn(index, live = false)
        start = index + 1
    }
    closeTurn(messages.size, live = liveTail)
    return FoldMarks(parts, notices)
}

// ------------------------------------------------------------------------------------------------ display

/** One drawn item of a message: a merged text stretch, a stretch of work, or one other part (file). */
internal sealed interface Display {
    val renderable: Boolean

    /** Adjacent text parts of one reply (merged for copy by [mergeTextParts]; rows keep one block list per part). */
    class Text(val parts: List<Part>) : Display {
        override val renderable: Boolean get() = parts.any { it.isRenderable }
    }

    /** Thoughts, tool calls and (finished turns) folded words and notices, drawn as one work line. */
    class Work(val parts: MutableList<Part>) : Display {
        override val renderable: Boolean get() = parts.any { it.isRenderable }
    }

    class Other(val part: Part) : Display {
        override val renderable: Boolean get() = part.isRenderable
    }
}

/**
 * Dart `_timelineDisplayParts`: what each message draws. A finished turn gathers its work into one line (the first
 * stretch's place) with the explanation it kept in view after it; the running turn ([liveTail]) keeps its work where
 * it happened, between the words.
 */
internal fun timelineDisplay(
    messages: List<MessageWithParts>,
    marks: FoldMarks,
    liveTail: Boolean = false,
): List<List<Display>> {
    val display = List(messages.size) { ArrayList<Display>() }
    val pending = ArrayList<Part>()
    var pendingText: Boolean? = null
    var pendingOwner: Int? = null
    var lastAssistant: Int? = null
    val liveFrom = if (liveTail) messages.indexOfLast { it.isPrompt() } + 1 else null
    var pool: Display.Work? = null

    fun flushPending() {
        val owner = pendingOwner
        if (owner != null) {
            if (pendingText == true) {
                display[owner] += Display.Text(ArrayList(pending))
            } else if (pool != null) {
                pool!!.parts += pending
            } else {
                val work = Display.Work(ArrayList(pending))
                display[owner] += work
                if (liveFrom == null || owner < liveFrom) pool = work
            }
        }
        pending.clear()
        pendingText = null
        pendingOwner = null
    }

    fun appendPart(owner: Int, part: Part) {
        val folded = marks.folded(part)
        val mergeable = part.type == PartType.Tool || part.type == PartType.Text || part.type == PartType.Reasoning || folded
        if (!mergeable) {
            flushPending()
            pool = null
            display[owner] += Display.Other(part)
            return
        }
        val text = part.type == PartType.Text && !folded
        if (pendingText != null && pendingText != text) flushPending()
        if (pendingText == null) pendingText = text
        if (pendingOwner == null) pendingOwner = owner
        pending += part
    }

    for (index in messages.indices) {
        val message = messages[index]
        if (marks.foldedNotice(message)) {
            for (part in message.parts) {
                if (part.type == PartType.Notice) appendPart(pendingOwner ?: lastAssistant ?: index, part)
            }
            continue
        }
        if (message.info.role != Role.Assistant) {
            flushPending()
            pool = null
            for (part in message.parts) if (part.isRenderable) display[index] += Display.Other(part)
            continue
        }
        lastAssistant = index
        val parts = message.parts.filter { it.isRenderable }
        if (message.info.errorText != null && parts.isEmpty()) flushPending()
        for (part in parts) appendPart(index, part)
        if (message.info.errorText != null) {
            flushPending()
            pool = null // an error is drawn where it happened: work after it starts anew
        }
        val nextIsAssistant =
            index + 1 < messages.size &&
                (messages[index + 1].info.role == Role.Assistant || marks.foldedNotice(messages[index + 1]))
        if (!nextIsAssistant) flushPending()
    }
    flushPending()
    return display
}

// ------------------------------------------------------------------------------------------------ merge

/** Counts characters the merge reads or writes: LinearMergeTest's probe (plan 09 §14.2), null in production. */
internal fun interface CopyProbe {
    fun copied(chars: Int)
}

/**
 * Dart `_mergeTextParts`, linear (codex-perf chat.md #1): adjacent text parts of one reply join with a paragraph
 * break unless the previous one ended with, or the next one starts with, a newline. "Ends with newline" is tracked
 * from the last fragment written, never read back from the buffer. Whitespace-only fragments are skipped; the first
 * part keeps its identity; a single part is returned as is (B-CHAT-096).
 */
internal fun mergeTextParts(parts: List<Part>, probe: CopyProbe? = null): Part {
    require(parts.isNotEmpty())
    if (parts.size == 1) return parts[0]
    val first = parts[0]
    val buffer = StringBuilder()
    var endsWithNewline = false
    for (part in parts) {
        if (part.text.isBlank()) continue
        if (buffer.isNotEmpty() && !endsWithNewline && !part.text.startsWith('\n')) {
            buffer.append("\n\n")
            probe?.copied(2)
        }
        buffer.append(part.text)
        probe?.copied(part.text.length)
        endsWithNewline = part.text.endsWith('\n')
    }
    return Part(
        id = first.id,
        messageId = first.messageId,
        sessionId = first.sessionId,
        type = first.type,
        rawType = first.rawType,
        text = buffer.toString(),
    )
}

// ------------------------------------------------------------------------------------------------ owners

/**
 * Dart `_turnActionOwners`: for each turn, the one message that carries its footer: the step that ends the turn when
 * it draws anything, otherwise the last step that does. The running turn ([running]) has none. Message indexes from
 * [from] on. The model-changed meta label (`metaAlways`, `_messageMeta().modelLabel`) is not ported yet (S2-CHAT).
 */
internal fun turnActionOwners(
    messages: List<MessageWithParts>,
    display: List<List<Display>>,
    running: Boolean = false,
    ignore: Set<String> = emptySet(),
    from: Int = 0,
): Set<Int> {
    val owners = HashSet<Int>()
    var lastStep: Int? = null
    var lastWithBody: Int? = null
    fun hasBody(i: Int) =
        display[i].any { it.renderable } || messages[i].info.errorText != null || messages[i].info.finish == "length"
    fun closeTurn(last: Boolean) {
        val end = lastStep
        if (end != null && !(last && running)) {
            val owner = if (hasBody(end)) end else lastWithBody
            if (owner != null) owners += owner
        }
        lastStep = null
        lastWithBody = null
    }
    for (index in from until messages.size) {
        val message = messages[index]
        if (message.info.id in ignore) continue
        if (message.isPrompt()) {
            closeTurn(last = false)
        } else if (message.info.role == Role.Assistant) {
            lastStep = index
            if (hasBody(index)) lastWithBody = index
        }
    }
    closeTurn(last = true)
    return owners
}

// ------------------------------------------------------------------------------------------------ turns

/** One turn: messages [start, end) of the list; [id] is its prompt's id, or "head" for what precedes every prompt. */
internal class TurnSpan(val id: String, val start: Int, val end: Int)

/**
 * Groups by prompt (never by `role != assistant`, §3.6). A user message filed as a notice never starts a turn, and a
 * prompt still waiting in the server inbox ([ignore]) is not a prompt.
 */
internal fun deriveTurns(messages: List<MessageWithParts>, ignore: Set<String> = emptySet()): List<TurnSpan> {
    val out = ArrayList<TurnSpan>()
    var start = 0
    var id = "head"
    for (i in messages.indices) {
        val m = messages[i]
        if (m.isPrompt() && m.info.id !in ignore) {
            if (i > start) out += TurnSpan(id, start, i)
            start = i
            id = m.info.id
        }
    }
    if (messages.size > start) out += TurnSpan(id, start, messages.size)
    return out
}
