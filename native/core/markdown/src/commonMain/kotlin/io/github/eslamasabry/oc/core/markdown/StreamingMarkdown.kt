package io.github.eslamasabry.oc.core.markdown

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * The Markdown of one text part as it streams (plan 09 §3.8, §9.1). Not thread-safe: the transcript engine owns one
 * per part on its serial lane.
 *
 * - [append] adds a delta. CR/LF split across two chunks is normalised (the raw text is kept; lines are read with
 *   their `\r` dropped). Only the unstable tail — from the last group that began before the line above the previous
 *   last line — is re-parsed; every block before it is handed back as the very same instance
 *   ([BlockDelta.stable]), and a tail block equal to the one it replaces is handed back as that instance too.
 * - An open fence at the tail grows without any re-parse until a line could close it ([openFenceAppends]).
 * - While [streaming], the last line's unfinished inline syntax is closed or hidden ([UnfinishedTail]); [finish]
 *   draws the exact text, re-parsing only the tail.
 * - [replaceAll] is for non-append edits: a full parse that keeps every block that did not change (same instance,
 *   same key).
 *
 * Text with an HTML comment block (`<!--`) parses in full on each update: a comment can hide lines far below it.
 */
public class StreamingMarkdown(
    private val partId: String,
    streaming: Boolean = true,
) {
    private val raw = StringBuilder()
    private val lineStarts = ArrayList<Int>().apply { add(0) }
    private var groups: List<Group> = emptyList()
    private var hasComment = false

    /** The last line index when the blocks were last built: the first line an append can change. */
    private var lastParsedLine = 0

    /** Every block, in order. */
    public var blocks: ImmutableList<Block> = persistentListOf()
        private set

    /** True while deltas still arrive: the tail's unfinished syntax is drawn closed. */
    public var streaming: Boolean = streaming
        private set

    /** Full parses (from the first line): the first append, [replaceAll], text with comment blocks. */
    public var markdownParses: Int = 0
        private set

    /** Re-parses of the unstable tail only. */
    public var tailReparses: Int = 0
        private set

    /** Appends that grew an open fence without parsing. */
    public var openFenceAppends: Int = 0
        private set

    /** The raw text received so far. */
    public val text: String get() = raw.toString()

    public fun append(chunk: String): BlockDelta {
        if (chunk.isEmpty()) return BlockDelta(blocks, persistentListOf())
        val oldLines = lineStarts.size
        val base = raw.length
        raw.append(chunk)
        for (j in chunk.indices) if (chunk[j] == '\n') lineStarts += base + j + 1
        if (!hasComment) hasComment = raw.indexOf("<!--", maxOf(0, base - 3)) >= 0
        if (!hasComment) growOpenFence(oldLines)?.let { return it }
        return reparse(fromTail = !hasComment)
    }

    /** Replaces the whole text (an edit that is not an append). Unchanged blocks keep their instances and keys. */
    public fun replaceAll(text: String): BlockDelta {
        raw.setLength(0)
        raw.append(text)
        lineStarts.clear()
        lineStarts += 0
        for (j in text.indices) if (text[j] == '\n') lineStarts += j + 1
        hasComment = text.contains("<!--")
        return reparse(fromTail = false)
    }

    /** The part ended: draw its exact text (the tail re-parses only when its drawn form differed). */
    public fun finish(): BlockDelta {
        if (!streaming) return BlockDelta(blocks, persistentListOf())
        streaming = false
        return reparse(fromTail = !hasComment)
    }

    // -- Parsing -----------------------------------------------------------------------------------------------

    private inner class Lines(private val drawnLast: String?, private val blanked: List<String>?) : ParseLines {
        override val size: Int get() = lineStarts.size

        override fun line(i: Int): String {
            blanked?.let { return it[i] }
            return drawn(i)
        }

        fun drawn(i: Int): String = if (i == size - 1 && drawnLast != null) drawnLast else normalized(i)

        override fun rawLine(i: Int): String = raw.substring(start(i), end(i))

        override fun start(i: Int): Int = lineStarts[i]

        override fun end(i: Int): Int = if (i + 1 < lineStarts.size) lineStarts[i + 1] - 1 else raw.length
    }

    /** Line [i] with the `\r` of a CR/LF dropped (the last line keeps a lone `\r`: its `\n` may still come). */
    private fun normalized(i: Int): String {
        val start = lineStarts[i]
        val last = i == lineStarts.size - 1
        var end = if (last) raw.length else lineStarts[i + 1] - 1
        if (!last && end > start && raw[end - 1] == '\r') end--
        return raw.substring(start, end)
    }

    /** The drawn last line while streaming (null: draw it as it is). [from] is where the fence scan may begin. */
    private fun drawnLast(from: Int): String? {
        if (!streaming) return null
        val last = lineStarts.size - 1
        val line = normalized(last)
        val previous = if (last > 0) normalized(last - 1) else null
        val drawn =
            UnfinishedTail.closeLast(previous, line) {
                val before = StringBuilder()
                for (i in from until last) before.append(normalized(i)).append('\n')
                UnfinishedTail.inOpenFence(before.toString(), line)
            }
        return if (drawn == line) null else drawn
    }

    private fun reparse(fromTail: Boolean): BlockDelta {
        // The line that was last (L) may still grow, and a growing line can join the group before it (`-` becomes a
        // list item, `|` a table row, a blank line a list item's continuation). A table is also recognised from the
        // line after its header, so a group starting at L - 1 may still dissolve into the one before it. So the tail
        // starts at the last group that began before L - 1: it and every group before it start on lines whose
        // meaning no longer changes.
        val keep = if (fromTail) maxOf(0, groups.indexOfLast { it.startLine < lastParsedLine - 1 }) else 0
        val from = if (keep == 0) 0 else groups[keep].startLine
        val drawnLast = drawnLast(from)
        val lines =
            if (hasComment) {
                val probe = Lines(drawnLast, null)
                val all = MutableList(lineStarts.size) { probe.drawn(it) }
                BlockParser.blankComments(all)
                Lines(drawnLast, all)
            } else {
                Lines(drawnLast, null)
            }
        if (from == 0) markdownParses++ else tailReparses++
        val stable = groups.subList(0, keep)
        val fresh = BlockParser(partId, lines).parse(from)
        lastParsedLine = lineStarts.size - 1
        return publish(stable, fresh)
    }

    /** Swaps in [fresh] after [stable], reusing every previous block instance that is equal to its replacement. */
    private fun publish(stable: List<Group>, fresh: List<Group>): BlockDelta {
        val stableBlocks = stable.flatMap { it.blocks }
        val old = blocks
        val reused =
            fresh.map { group ->
                Group(group.startLine, group.blocks.map { block -> reuse(old, block) }, group.fence)
            }
        groups = stable + reused
        val tail = reused.flatMap { it.blocks }
        blocks = (stableBlocks + tail).toImmutableList()
        return BlockDelta(stableBlocks.toImmutableList(), tail.toImmutableList())
    }

    private fun reuse(old: List<Block>, block: Block): Block {
        // Blocks are keyed by start offset; equal content at the same key is the same block.
        val at = old.binarySearch { it.start.compareTo(block.start) }
        if (at < 0) return block
        val previous = old[at]
        return if (previous == block) previous else block
    }

    /**
     * The tail is an open fence whose opening line is complete and no new line could close it: extend its body
     * without parsing. Null when the fast path does not apply.
     */
    private fun growOpenFence(oldLines: Int): BlockDelta? {
        val group = groups.lastOrNull() ?: return null
        val fence = group.fence ?: return null
        val code = group.blocks.single() as? Block.Code ?: return null
        // The line that was last before this chunk is the first one that changed; it must be a body line.
        if (oldLines - 1 <= fence.openLine) return null
        val last = lineStarts.size - 1
        for (i in (oldLines - 1)..last) if (fence.closing.matchEntire(normalized(i)) != null) return null
        val text = StringBuilder()
        val copy = StringBuilder()
        for (i in fence.bodyStart..last) {
            if (i > fence.bodyStart) {
                text.append('\n')
                copy.append('\n')
            }
            text.append(stripIndent(normalized(i), fence.indent))
            val end = if (i + 1 < lineStarts.size) lineStarts[i + 1] - 1 else raw.length
            copy.append(raw, lineStarts[i], end)
        }
        lastParsedLine = last
        val grown = code.copy(end = raw.length, text = text.toString(), copyText = copy.toString())
        openFenceAppends++
        val stable = groups.subList(0, groups.size - 1)
        groups = stable + Group(group.startLine, listOf(grown), fence)
        val stableBlocks = stable.flatMap { it.blocks }.toImmutableList()
        blocks = (stableBlocks + grown).toImmutableList()
        return BlockDelta(stableBlocks, persistentListOf(grown))
    }

    private fun stripIndent(line: String, indent: Int): String {
        var removed = 0
        while (removed < indent && removed < line.length && line[removed] == ' ') removed++
        return line.substring(removed)
    }
}

/** One-shot parses for finished text. */
public object MarkdownBlocks {
    /** The blocks of a finished (or, with [streaming], still growing) [text]. */
    public fun parse(partId: String, text: String, streaming: Boolean = false): ImmutableList<Block> =
        StreamingMarkdown(partId, streaming).apply { replaceAll(text) }.blocks

    /** A part that is all code in one [language] (Flutter `KitMarkdown.codeLanguage`): one closed code block. */
    public fun wholeCode(partId: String, text: String, language: String): Block.Code =
        Block.Code("$partId:0", 0, text.length, language, text, text, open = false, kind = CodeKind.WholePart)
}
