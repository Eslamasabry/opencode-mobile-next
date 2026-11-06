package io.github.eslamasabry.oc.core.markdown

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * The lines a parse reads. [line] is the drawn line (CR/LF normalised, HTML comment blocks emptied, and while
 * streaming the last line with its unfinished syntax closed); [rawLine] is the line as received (code copy text);
 * [start]/[end] are raw offsets.
 */
internal interface ParseLines {
    val size: Int

    fun line(i: Int): String

    fun rawLine(i: Int): String

    fun start(i: Int): Int

    fun end(i: Int): Int

    /** Whether line [i] is the last line of the whole text (it may still be arriving). */
    fun isLast(i: Int): Boolean = i == size - 1
}

/**
 * Lines [index] of [parent] with the first [cut] characters of each removed: a container's content (the text after a
 * list marker or `>`), parsed again as blocks. Offsets stay absolute in the part's raw text.
 */
internal class SubLines(
    private val parent: ParseLines,
    private val index: IntArray,
    private val cut: IntArray,
) : ParseLines {
    override val size: Int get() = index.size

    override fun line(i: Int): String = parent.line(index[i]).drop(cut[i])

    override fun rawLine(i: Int): String = parent.rawLine(index[i]).drop(cut[i])

    override fun start(i: Int): Int = minOf(parent.start(index[i]) + cut[i], parent.end(index[i]))

    override fun end(i: Int): Int = parent.end(index[i])

    override fun isLast(i: Int): Boolean = parent.isLast(index[i])
}

/** What the parser remembers about an open fence, so a streaming fence can grow without a re-parse. */
internal class OpenFence(
    val marker: String,
    val indent: Int,
    val info: String?,
    /** Line of the opening fence. */
    val openLine: Int,
) {
    val bodyStart: Int get() = openLine + 1
    val closing = Regex("""^ {0,3}${marker[0]}{${marker.length},}[ \t]*${Dart.END}""")
}

/** Blocks that came from one run of source lines starting at [startLine]; a list gives one block per item. */
internal class Group(val startLine: Int, val blocks: List<Block>, val fence: OpenFence? = null)

/**
 * The block parser: a port of Flutter's `_KitMarkdownState._splitBlocks` (plan 09 §9.1 fallback). Line based,
 * forward only, and starting at any group's first line gives the same groups a parse from the top gives there, which
 * is what lets [StreamingMarkdown] re-parse only its tail.
 */
internal class BlockParser(private val partId: String, private val src: ParseLines) {
    private val fencePattern = Regex("""^( {0,3})(`{3,}|~{3,})(${Dart.DOT}*)${Dart.END}""")
    private val heading = Regex("""(#{1,6})\s+(${Dart.DOT}*)""")
    private val rule = Regex("""\s*(-{3,}|\*{3,}|_{3,})\s*""")
    private val bullet = Regex("""^(\s*)[-*+]\s+""")
    private val ordered = Regex("""^(\s*)(\d{1,9})[.)]\s+""")
    private val quoteMark = Regex("""^\s*>\s?""")
    private val space = Regex("""\s+""")

    private val n = src.size
    private val groups = ArrayList<Group>()
    private val paragraph = ArrayList<String>()
    private var paragraphFirst = -1
    private var paragraphLast = -1

    private fun key(start: Int) = "$partId:$start"

    /** The last line may still be arriving: a picture half written on it is held back. */
    private fun lineOf(i: Int): String = if (src.isLast(i)) PictureScanner.withoutUnfinished(src.line(i)) else src.line(i)

    private fun flushParagraph() {
        if (paragraph.isEmpty()) return
        val start = src.start(paragraphFirst)
        groups += Group(
            paragraphFirst,
            listOf(
                Block.Prose(
                    key(start),
                    start,
                    src.end(paragraphLast),
                    InlineParser.parse(paragraph.joinToString("\n")),
                ),
            ),
        )
        paragraph.clear()
    }

    fun parse(from: Int): List<Group> {
        var i = from
        while (i < n) {
            val line = src.line(i)

            val fence = fencePattern.find(line)
            if (fence != null && !(fence.groupValues[2].startsWith("`") && fence.groupValues[3].contains('`'))) {
                flushParagraph()
                i = fence(i, fence)
                continue
            }

            if (i + 1 < n) {
                val headers = TableScanner.cells(line)
                if (headers.size >= 2) {
                    val next = src.line(i + 1)
                    if (headers.size == TableScanner.cells(next).size && TableScanner.isDelimiter(next)) {
                        flushParagraph()
                        i = table(i, headers)
                        continue
                    }
                }
            }

            val h = heading.matchEntire(line)
            if (h != null) {
                flushParagraph()
                val start = src.start(i)
                groups += Group(
                    i,
                    listOf(
                        Block.Heading(
                            key(start),
                            start,
                            src.end(i),
                            h.groupValues[1].length,
                            InlineParser.parse(h.groupValues[2]),
                        ),
                    ),
                )
                i++
                continue
            }

            if (rule.matchEntire(line) != null) {
                flushParagraph()
                val start = src.start(i)
                groups += Group(i, listOf(Block.Rule(key(start), start, src.end(i))))
                i++
                continue
            }

            if (line.trimStart().startsWith(">")) {
                flushParagraph()
                i = quote(i)
                continue
            }

            val marker = if (bullet.find(line) != null) bullet else if (ordered.find(line) != null) ordered else null
            if (marker != null) {
                flushParagraph()
                i = list(i, marker === ordered)
                continue
            }

            if (line.isBlank()) {
                flushParagraph()
                i++
                continue
            }

            // Lines that are only pictures: one group, drawn as tiles.
            val pictures = pictureGroup(i)
            if (pictures != null) {
                i = pictures
                continue
            }

            val text = lineOf(i)
            if (text.isNotBlank()) {
                if (paragraph.isEmpty()) paragraphFirst = i
                paragraph += text
                paragraphLast = i
            }
            i++
        }
        flushParagraph()
        return groups
    }

    private fun fence(open: Int, m: MatchResult): Int {
        val marker = m.groupValues[2]
        val indent = m.groupValues[1].length
        val info = m.groupValues[3].trim()
        val lang = if (info.isEmpty()) null else info.split(space).first()
        val state = OpenFence(marker, indent, lang, open)
        val code = ArrayList<String>()
        var i = open + 1
        while (i < n && state.closing.matchEntire(src.line(i)) == null) {
            code += stripIndent(src.line(i), indent)
            i++
        }
        val closed = i < n
        val bodyStart = open + 1
        val original =
            (bodyStart until minOf(i, n)).joinToString("\n") { src.rawLine(it) } +
                if (closed && i > bodyStart) "\n" else ""
        val lastLine = if (closed) i else maxOf(open, i - 1)
        val body = code.joinToString("\n")
        val start = src.start(open)
        val end = src.end(lastLine)
        val type = AgentBlockType.of(lang)
        val block =
            if (closed && type != null) {
                Block.AgentBlock(key(start), start, end, type, AgentBlocks.parse(type, body))
            } else {
                Block.Code(key(start), start, end, lang, body, original, open = !closed, kind = CodeKind.Fence)
            }
        groups += Group(open, listOf(block), fence = if (closed) null else state)
        return if (closed) i + 1 else i
    }

    private fun table(first: Int, headers: List<String>): Int {
        val delimiter = TableScanner.cells(src.line(first + 1))
        val rows = ArrayList<List<Inlines>>()
        var i = first + 2
        while (i < n) {
            val cells = TableScanner.cells(src.line(i))
            if (src.line(i).isBlank() || cells.size < 2) break
            rows += cells.map(InlineParser::parse)
            i++
        }
        val start = src.start(first)
        groups += Group(
            first,
            listOf(
                Block.Table(
                    key(start),
                    start,
                    src.end(i - 1),
                    headers.map(InlineParser::parse).toImmutableList(),
                    delimiter.map(TableScanner::align).toImmutableList(),
                    rows.map { it.toImmutableList() }.toImmutableList(),
                ),
            ),
        )
        return i
    }

    /** [sub] (a container's content) parsed again as blocks. */
    private fun nested(sub: ParseLines): List<Block> =
        BlockParser(partId, sub).parse(0).flatMap { it.blocks }

    private fun quote(first: Int): Int {
        val index = ArrayList<Int>()
        val cut = ArrayList<Int>()
        var i = first
        while (i < n && src.line(i).trimStart().startsWith(">")) {
            index += i
            cut += quoteMark.find(src.line(i))!!.value.length
            i++
        }
        val children = nested(SubLines(src, index.toIntArray(), cut.toIntArray()))
        val start = src.start(first)
        groups += Group(first, listOf(Block.Quote(key(start), start, src.end(i - 1), children.toImmutableList())))
        return i
    }

    private fun indentOf(line: String): Int = line.length - line.trimStart().length

    /**
     * One list: its items, then each item's content parsed again as blocks (a nested list comes back as items one
     * level deeper, everything else becomes the item's children). A blank line does not end a list: the next
     * marker of the same kind still belongs to it.
     */
    private fun list(first: Int, isOrdered: Boolean): Int {
        val marker = if (isOrdered) ordered else bullet
        // CommonMark: an ordered list starts at its first item's number.
        val startNumber = if (isOrdered) ordered.find(src.line(first))!!.groupValues[2].toInt() else 1
        val out = ArrayList<Block>()
        var i = first
        var count = 0
        var listEnd = first
        while (i < n) {
            val m = marker.find(src.line(i)) ?: break
            val itemIndent = m.groupValues[1].length
            val content = m.range.last + 1
            val index = arrayListOf(i)
            val cut = arrayListOf(content)
            var last = i
            var j = i + 1
            while (j < n) {
                val line = src.line(j)
                if (line.isBlank()) {
                    j++
                    continue
                }
                val indent = indentOf(line)
                val isMarker = bullet.find(line) != null || ordered.find(line) != null
                // Indented two or more past the marker: the item's own. One space past, as a wrapped line (not a
                // marker, not a fence, no blank above): lazy continuation of its paragraph.
                val inside =
                    indent >= itemIndent + 2 ||
                        (indent > itemIndent && !isMarker && last == j - 1 && fencePattern.find(line) == null)
                if (!inside) break
                for (blank in last + 1 until j) {
                    index += blank
                    cut += 0
                }
                index += j
                cut += minOf(content, indent)
                last = j
                j++
            }
            val items = item(m, count == 0, startNumber + count, isOrdered, SubLines(src, index.toIntArray(), cut.toIntArray()), i, last)
            out += items
            count++
            listEnd = last + 1
            // The next marker of the same kind, past any blank lines, is the next item.
            var k = listEnd
            while (k < n && src.line(k).isBlank()) k++
            if (k >= n) break
            val next = src.line(k)
            if (marker.find(next) == null || indentOf(next) >= itemIndent + 2) break
            i = k
        }
        groups += Group(first, out)
        return listEnd
    }

    private fun item(
        m: MatchResult,
        firstInList: Boolean,
        number: Int,
        isOrdered: Boolean,
        sub: SubLines,
        firstLine: Int,
        lastLine: Int,
    ): List<Block> {
        val inner = nested(sub)
        val head = inner.firstOrNull() as? Block.Prose
        val start = src.start(firstLine)
        val own =
            Block.ListItem(
                key = key(start),
                start = start,
                end = src.end(lastLine),
                ordered = isOrdered,
                number = if (isOrdered) number else null,
                depth = 0,
                firstInList = firstInList,
                inlines = head?.inlines ?: Inlines("", persistentListOf()),
            )
        val emitted = arrayListOf<Block.ListItem>(own)
        for (b in if (head != null) inner.drop(1) else inner) {
            if (b is Block.ListItem) {
                emitted += b.copy(depth = (b.depth + 1).coerceAtMost(MAX_LIST_DEPTH), firstInList = false)
            } else {
                val tail = emitted.last()
                emitted[emitted.lastIndex] = tail.copy(children = (tail.children + b).toImmutableList())
            }
        }
        return emitted
    }

    /** The picture-only lines from [first] (blank lines between them included); the line after them, or null. */
    private fun pictureGroup(first: Int): Int? {
        val head = PictureScanner.only(lineOf(first)) ?: return null
        val pictures = ArrayList(head)
        var end = first + 1
        var last = first
        while (end < n) {
            var next = end
            while (next < n && src.line(next).isBlank()) next++
            val more = if (next < n) PictureScanner.only(lineOf(next)) else null
            if (more == null) break
            pictures += more
            last = next
            end = next + 1
        }
        // The same picture twice in a row (an agent that read one file twice) is shown once.
        val seen = HashSet<String>()
        pictures.retainAll { seen.add(it.identity) }
        flushParagraph()
        val start = src.start(first)
        groups += Group(first, listOf(Block.PictureGroup(key(start), start, src.end(last), pictures.toImmutableList())))
        return end
    }

    private fun stripIndent(line: String, indent: Int): String {
        var removed = 0
        while (removed < indent && removed < line.length && line[removed] == ' ') removed++
        return line.substring(removed)
    }

    companion object {
        private val commentStart = Regex("""^ {0,3}<!--""")
        private val anyFence = Regex("""^( {0,3})(`{3,}|~{3,})(${Dart.DOT}*)${Dart.END}""")

        /**
         * HTML comment blocks (a line starting `<!--` through the line that closes it with `-->`) emptied, outside
         * fences. Line count kept; a block still open at the end is hidden too; a comment inside a line stays.
         */
        fun blankComments(lines: MutableList<String>) {
            if (lines.none { it.contains("<!--") }) return
            var fence: String? = null
            var inComment = false
            for (i in lines.indices) {
                val line = lines[i]
                if (inComment) {
                    if (line.contains("-->")) inComment = false
                    lines[i] = ""
                    continue
                }
                val m = anyFence.find(line)
                val open = fence
                if (open != null) {
                    if (m != null) {
                        val marker = m.groupValues[2]
                        if (marker.startsWith(open[0]) && marker.length >= open.length && m.groupValues[3].isBlank()) {
                            fence = null
                        }
                    }
                } else if (m != null) {
                    fence = m.groupValues[2]
                } else if (commentStart.find(line) != null) {
                    inComment = !line.substring(line.indexOf("<!--") + 4).contains("-->")
                    lines[i] = ""
                }
            }
        }
    }
}

/** [source] with its HTML comment blocks emptied (Flutter `kitMarkdownBlankComments`). */
public fun blankHtmlComments(source: String): String {
    if (!source.contains("<!--")) return source
    val lines = source.split('\n').toMutableList()
    BlockParser.blankComments(lines)
    return lines.joinToString("\n")
}
