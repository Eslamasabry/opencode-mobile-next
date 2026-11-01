package io.github.eslamasabry.oc.engine.transcript.internal

import io.github.eslamasabry.oc.engine.transcript.state.RowKind

/**
 * One Markdown block of a text part, or one chunk of a long block: [start, end) in the part text. Settled blocks keep
 * their instance forever. [continued]: a chunk after the first of its block; [continues]: more chunks of the same
 * block follow.
 */
internal class Block(
    val start: Int,
    val end: Int,
    val kind: RowKind,
    val text: String,
    val open: Boolean,
    val continued: Boolean = false,
    val continues: Boolean = false,
)

/** STREAM-FIX: how many lines one row of a long block holds. */
internal object StreamingChunk {
    /**
     * 16 lines: the live chunk's re-layout per word and the fade's offscreen layer stay under half a phone screen
     * (16 × 20 sp), whatever the length of the fence or paragraph it belongs to.
     */
    const val LINES: Int = 16
}

/**
 * P3 stand-in for `:core:markdown` `StreamingMarkdown` (plan 09 §3.8; S2-MD's parser replaces it with S2-TX). It splits
 * a growing text into top-level blocks line by line, in one pass over each new line:
 * - stable blocks (closed by a blank line, a heading line, a new list item or a closing fence) are cached Blocks whose
 *   instances never change again;
 * - the unstable tail is the only block rebuilt per append ([tailReparses]);
 * - an open fence never re-scans its body: lines are only checked for the closing fence;
 * - **STREAM-FIX:** a block longer than [StreamingChunk.LINES] lines is cut at line ends into settled chunks plus a
 *   short live tail chunk. The emulator showed the 6,000-word reply's frames growing with the 400-line open fence: one
 *   live block meant each word re-laid out, and each publish copied, the whole fence. Now both are bounded by a chunk.
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

    /** Lines of the current block (or chunk) seen so far. */
    private var lines = 0

    /** The current chunk continues a block cut earlier; [blockKind] is that block's kind. */
    private var continued = false
    private var blockKind = RowKind.Prose

    var tailReparses: Int = 0
        private set
    var fullParses: Int = 0
        private set

    /** Characters copied into tail blocks (STREAM-FIX: bounded by one chunk, whatever the reply's length). */
    var tailChars: Long = 0
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
        lines = 0
        continued = false
        tail = null
        fullParses++
        generation++
        append(full)
    }

    /** The unstable tail block, or null when the text ends exactly on a block border. Rebuilt only after an append. */
    fun tail(): Block? {
        if (!tailDirty) return tail
        tailDirty = false
        tail =
            if (blockStart >= text.length || (!inFence && isBlank(blockStart, text.length))) {
                null
            } else {
                tailReparses++
                tailChars += text.length - blockStart
                val kind = if (inFence) RowKind.Code else if (continued) blockKind else kindOfLine(blockStart)
                Block(blockStart, text.length, kind, text.substring(blockStart, text.length).trimEnd('\n'), inFence, continued)
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
            cutIfFull(ls, RowKind.Code)
            lines++
            if (startsWithFence(ls, nl)) {
                emit(blockStart, nl + 1, RowKind.Code)
                blockStart = nl + 1
                inFence = false
                lines = 0
            }
            return
        }
        when {
            startsWithFence(ls, nl) -> {
                emitPending(ls)
                blockStart = ls
                inFence = true
                lines = 1
            }
            isBlank(ls, nl) -> {
                emitPending(ls)
                blockStart = nl + 1
                lines = 0
            }
            text[firstNonSpace(ls, nl)] == '#' -> {
                emitPending(ls)
                emit(ls, nl + 1, RowKind.Heading)
                blockStart = nl + 1
                lines = 0
            }
            isListItem(ls, nl) -> {
                emitPending(ls)
                blockStart = ls
                lines = 1
            }
            else -> {
                if (lines > 0) cutIfFull(ls, if (continued) blockKind else kindOfLine(blockStart))
                lines++
            }
        }
    }

    /**
     * The block already holds [StreamingChunk.LINES] lines and line [ls] has just completed: the lines before it settle
     * as a chunk that [Block.continues], and the block goes on from [ls]. The live tail always keeps ≥ 1 line.
     */
    private fun cutIfFull(ls: Int, kind: RowKind) {
        if (lines < StreamingChunk.LINES) return
        settled += Block(blockStart, ls, kind, text.substring(blockStart, ls - 1), open = false, continued = continued, continues = true)
        blockStart = ls
        blockKind = kind
        continued = true
        lines = 0
    }

    private fun emitPending(upTo: Int) {
        if (blockStart < upTo && !isBlank(blockStart, upTo)) {
            emit(blockStart, upTo, if (continued) blockKind else kindOfLine(blockStart))
        }
        continued = false
    }

    private fun emit(start: Int, end: Int, kind: RowKind) {
        settled += Block(start, end, kind, text.substring(start, end).trimEnd('\n'), open = false, continued = continued)
        continued = false
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
