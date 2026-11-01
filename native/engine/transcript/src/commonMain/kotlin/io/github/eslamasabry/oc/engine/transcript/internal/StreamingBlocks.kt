package io.github.eslamasabry.oc.engine.transcript.internal

import io.github.eslamasabry.oc.engine.transcript.state.RowKind

/** One Markdown block of a text part: [start, end) in the part text. Settled blocks keep their instance forever. */
internal class Block(val start: Int, val end: Int, val kind: RowKind, val text: String, val open: Boolean)

/**
 * P3 stand-in for `:core:markdown` `StreamingMarkdown` (plan 09 §3.8; JetBrains `StreamingMarkdownFile` arrives with
 * S2-MD). It splits a growing text into top-level blocks line by line, in one pass over each new line:
 * - stable blocks (closed by a blank line, a heading line, a new list item or a closing fence) are cached Blocks whose
 *   instances never change again;
 * - the unstable tail is the only block rebuilt per append ([tailReparses]);
 * - an open fence never re-scans its body: lines are only checked for the closing fence.
 * A text that is not `old + more` resets the splitter (a full parse, [fullParses]).
 */
internal class StreamingBlocks {
    val text: StringBuilder = StringBuilder()
    private val settled = ArrayList<Block>()
    private var blockStart = 0
    private var lineStart = 0
    private var inFence = false
    private var tail: Block? = null
    private var tailDirty = true

    var tailReparses: Int = 0
        private set
    var fullParses: Int = 0
        private set

    /** +1 on every full re-parse: cached rows built from older blocks are void. */
    var generation: Int = 0
        private set

    val settledBlocks: List<Block> get() = settled

    fun append(more: CharSequence) {
        if (more.isEmpty()) return
        text.append(more)
        scanLines()
        tailDirty = true
    }

    /** A whole-text replacement: append when it extends the current text, else start over. */
    fun replace(full: String) {
        if (full.length >= text.length && full.regionMatches(0, text, 0, text.length)) {
            append(full.substring(text.length))
            return
        }
        settled.clear()
        text.setLength(0)
        blockStart = 0
        lineStart = 0
        inFence = false
        fullParses++
        generation++
        append(full)
    }

    /** The unstable tail block, or null when the text ends exactly on a block border. Rebuilt only after an append. */
    fun tail(): Block? {
        if (!tailDirty) return tail
        tailDirty = false
        tail =
            if (blockStart >= text.length || isBlank(blockStart, text.length)) {
                null
            } else {
                tailReparses++
                val kind = if (inFence) RowKind.Code else kindOfLine(blockStart)
                Block(blockStart, text.length, kind, text.substring(blockStart, text.length).trimEnd('\n'), open = inFence)
            }
        return tail
    }

    private fun scanLines() {
        while (true) {
            val nl = text.indexOf('\n', lineStart)
            if (nl < 0) return
            onLine(lineStart, nl)
            lineStart = nl + 1
        }
    }

    private fun onLine(ls: Int, nl: Int) {
        if (inFence) {
            if (startsWithFence(ls, nl)) {
                emit(blockStart, nl + 1, RowKind.Code)
                blockStart = nl + 1
                inFence = false
            }
            return
        }
        when {
            startsWithFence(ls, nl) -> {
                emitPending(ls)
                blockStart = ls
                inFence = true
            }
            isBlank(ls, nl) -> {
                emitPending(ls)
                blockStart = nl + 1
            }
            text[firstNonSpace(ls, nl)] == '#' -> {
                emitPending(ls)
                emit(ls, nl + 1, RowKind.Heading)
                blockStart = nl + 1
            }
            isListItem(ls, nl) -> {
                emitPending(ls)
                blockStart = ls
            }
        }
    }

    private fun emitPending(upTo: Int) {
        if (blockStart < upTo && !isBlank(blockStart, upTo)) emit(blockStart, upTo, kindOfLine(blockStart))
    }

    private fun emit(start: Int, end: Int, kind: RowKind) {
        settled += Block(start, end, kind, text.substring(start, end).trimEnd('\n'), open = false)
    }

    private fun kindOfLine(ls: Int): RowKind {
        val end = text.indexOf('\n', ls).let { if (it < 0) text.length else it }
        val c = text[firstNonSpace(ls, end)]
        return when {
            startsWithFence(ls, end) -> RowKind.Code
            c == '#' -> RowKind.Heading
            c == '>' -> RowKind.Quote
            isListItem(ls, end) -> RowKind.ListItem
            else -> RowKind.Prose
        }
    }

    private fun firstNonSpace(from: Int, to: Int): Int {
        var i = from
        while (i < to && (text[i] == ' ' || text[i] == '\t')) i++
        return if (i < to) i else from.coerceAtMost(text.length - 1)
    }

    private fun startsWithFence(ls: Int, nl: Int): Boolean {
        val i = firstNonSpace(ls, nl)
        return nl - i >= 3 && text[i] == '`' && text[i + 1] == '`' && text[i + 2] == '`'
    }

    private fun isListItem(ls: Int, nl: Int): Boolean {
        val i = firstNonSpace(ls, nl)
        if (nl - i >= 2 && (text[i] == '-' || text[i] == '*' || text[i] == '+') && text[i + 1] == ' ') return true
        var j = i
        while (j < nl && text[j].isDigit()) j++
        return j > i && j + 1 < nl && text[j] == '.' && text[j + 1] == ' '
    }

    private fun isBlank(from: Int, to: Int): Boolean {
        for (i in from until to) if (!text[i].isWhitespace()) return false
        return true
    }
}
