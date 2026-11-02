package io.github.eslamasabry.oc.core.markdown

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
    private fun lineOf(i: Int): String = if (i == n - 1) PictureScanner.withoutUnfinished(src.line(i)) else src.line(i)

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
                val first = i
                val quote = ArrayList<String>()
                while (i < n && src.line(i).trimStart().startsWith(">")) {
                    quote += quoteMark.replaceFirst(src.line(i), "")
                    i++
                }
                val start = src.start(first)
                groups += Group(
                    first,
                    listOf(
                        Block.Quote(key(start), start, src.end(i - 1), InlineParser.parse(quote.joinToString("\n"))),
                    ),
                )
                continue
            }

            val marker = if (bullet.find(line) != null) bullet else if (ordered.find(line) != null) ordered else null
            if (marker != null) {
                flushParagraph()
                i = list(i, marker)
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

    private class Item(val indent: Int, val text: String, val first: Int, val last: Int)

    private fun list(first: Int, marker: Regex): Int {
        val isOrdered = marker === ordered
        // CommonMark: an ordered list starts at its first item's number.
        val startNumber = if (isOrdered) ordered.find(src.line(first))!!.groupValues[2].toInt() else 1
        val items = ArrayList<Item>()
        var i = first
        while (i < n) {
            val m = marker.find(src.line(i)) ?: break
            val indent = m.groupValues[1].length
            var text = src.line(i).substring(m.range.last + 1)
            val itemFirst = i
            i++
            // A wrapped item: the lines indented under it are its own words (CommonMark continuation).
            while (i < n) {
                val next = src.line(i)
                if (next.isBlank() || bullet.find(next) != null || ordered.find(next) != null) break
                if (fencePattern.find(next.trimStart()) != null) break
                if (next.length - next.trimStart().length < maxOf(2, indent + 1)) break
                text = text + "\n" + next.trim()
                i++
            }
            items += Item(indent, text, itemFirst, i - 1)
        }
        val levels = levels(items.map { it.indent })
        val counters = ArrayList<Int>()
        val blocks = ArrayList<Block>(items.size)
        for ((index, item) in items.withIndex()) {
            val level = levels[index]
            while (counters.size > level + 1) counters.removeAt(counters.lastIndex)
            while (counters.size < level + 1) counters += if (counters.isEmpty()) startNumber - 1 else 0
            counters[level] = counters[level] + 1
            val start = src.start(item.first)
            blocks += Block.ListItem(
                key = key(start),
                start = start,
                end = src.end(item.last),
                ordered = isOrdered,
                number = if (isOrdered) counters[level] else null,
                depth = level,
                firstInList = index == 0,
                inlines = InlineParser.parse(item.text),
            )
        }
        groups += Group(first, blocks)
        return i
    }

    /** Nesting depth per item, normalised by the smallest indent step so 2- and 4-space nesting land one deeper. */
    private fun levels(indents: List<Int>): List<Int> {
        val base = indents.min()
        var unit = 0
        for (d in indents) {
            val step = d - base
            if (step > 0 && (unit == 0 || step < unit)) unit = step
        }
        return indents.map { if (unit == 0) 0 else ((it - base) / unit).coerceIn(0, 4) }
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
