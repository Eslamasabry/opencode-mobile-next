package io.github.eslamasabry.oc.ui.kitchat.diff

// A Markdown file's changes, drawn as Markdown (lib/ui/kit/diff/kit_diff_markdown.dart): each run of added, removed or
// unchanged lines is cut into its blocks (a heading, a paragraph, a list item, a quote, a table), and each block is
// drawn rendered with the first line's numbers and its + / − in the gutter, on its row's tint. Code fences stay code:
// their lines are drawn as the source rows they are. "Source" brings back the lines as they are written.

/** Whether [path] names a Markdown file (.md, .markdown, .mdx) (kit_diff_markdown.dart:11-16). */
internal fun isMarkdownPath(path: String): Boolean {
    val name = path.substringAfterLast('/').lowercase()
    return name.endsWith(".md") || name.endsWith(".markdown") || name.endsWith(".mdx")
}

/** The Dart file's four line patterns, verbatim (kit_diff_markdown.dart:18-21). */
internal object DiffMarkdownPatterns {
    val fence: Regex = Regex("^ {0,3}(`{3,}|~{3,})") // F: lib/ui/kit/diff/kit_diff_markdown.dart:18
    val heading: Regex = Regex("^ {0,3}#{1,6}(\\s|$)") // F: lib/ui/kit/diff/kit_diff_markdown.dart:19
    val rule: Regex = Regex("^\\s*(-{3,}|\\*{3,}|_{3,})\\s*$") // F: lib/ui/kit/diff/kit_diff_markdown.dart:20
    val item: Regex = Regex("^(\\s*)([-*+]|\\d{1,9}[.)])\\s+") // F: lib/ui/kit/diff/kit_diff_markdown.dart:21
}

/**
 * One rendered same-kind Markdown block (Dart `_MdUnit`); source lines retain both sides' numbers for selection and
 * copy. [segment] is the row key of its first line. [code]: a code fence (or part of one), drawn as source rows.
 */
internal data class DiffMarkdownUnit(val lines: List<DiffLine>, val segment: Int, val code: Boolean = false) {
    val kind: DiffLineKind
        get() = lines.first().kind

    /** Every line is blank: drawn as one empty line under the first line's numbers. */
    val blank: Boolean
        get() = lines.all { it.text.isBlank() }

    /** The first [count] lines, as a block of their own (a preview's cut). */
    fun take(count: Int): DiffMarkdownUnit = copy(lines = lines.take(count))

    /**
     * The Markdown to draw (kit_diff_markdown.dart:45-63): a list item's or a paragraph's wrapped lines joined into one,
     * so a hard-wrapped source reads as one sentence; a quote or a table keeps its lines.
     */
    val markdown: String
        get() {
            if (lines.size == 1) return lines.first().text
            val text = lines.first().text
            if (DiffMarkdownPatterns.item.containsMatchIn(text)) {
                val out = ArrayList<String>()
                for (line in lines) {
                    if (DiffMarkdownPatterns.item.containsMatchIn(line.text) || out.isEmpty()) {
                        out += line.text
                    } else {
                        out[out.lastIndex] = "${out.last().trimEnd()} ${line.text.trim()}"
                    }
                }
                return out.joinToString("\n")
            }
            if (text.trimStart().startsWith('>') || text.trimStart().startsWith('|')) {
                return lines.joinToString("\n") { it.text }
            }
            return lines.joinToString(" ") { it.text.trim() }
        }
}

/**
 * Cuts [run] (lines of one kind, in order) into blocks (Dart `_mdUnits`, kit_diff_markdown.dart:78-136). [inFence]: the
 * run starts inside a code fence opened above it. [segments] holds each line's row key (defaults to its index).
 */
internal fun diffMarkdownUnits(
    run: List<DiffLine>,
    inFence: Boolean,
    segments: List<Int> = run.indices.toList(),
): List<DiffMarkdownUnit> {
    val out = ArrayList<DiffMarkdownUnit>()
    fun fence(i: Int) = DiffMarkdownPatterns.fence.containsMatchIn(run[i].text)
    fun blank(i: Int) = run[i].text.isBlank()
    fun heading(i: Int) = DiffMarkdownPatterns.heading.containsMatchIn(run[i].text)
    fun rule(i: Int) = DiffMarkdownPatterns.rule.containsMatchIn(run[i].text)
    fun quote(i: Int) = run[i].text.trimStart().startsWith('>')
    fun table(i: Int) = run[i].text.trimStart().startsWith('|')
    fun startsBlock(i: Int) = blank(i) || fence(i) || heading(i) || rule(i) || quote(i) || table(i)
    fun unit(start: Int, end: Int, code: Boolean) =
        DiffMarkdownUnit(run.subList(start, end).toList(), segments[start], code)
    var open = inFence
    var i = 0
    while (i < run.size) {
        val start = i
        if (open || fence(i)) {
            // A fence, or the rest of one: its lines stay code. One that does not close inside the run is still code
            // to its last line.
            if (!open) i++
            while (i < run.size && !fence(i)) i++
            if (i < run.size) {
                i++
                open = false
            } else {
                open = true
            }
            out += unit(start, i, code = true)
            continue
        }
        val item = DiffMarkdownPatterns.item.find(run[i].text)
        if (quote(i) || table(i)) {
            val isQuote = quote(i)
            i++
            while (i < run.size && (if (isQuote) quote(i) else table(i))) i++
        } else if (item != null) {
            // A list item and its wrapped lines and nested items.
            val indent = item.groupValues[1].length
            i++
            while (i < run.size && !startsBlock(i)) {
                val next = DiffMarkdownPatterns.item.find(run[i].text)
                if (next != null && next.groupValues[1].length <= indent) break
                i++
            }
        } else if (blank(i) || heading(i) || rule(i)) {
            i++
        } else {
            i++
            while (i < run.size && !startsBlock(i) && !DiffMarkdownPatterns.item.containsMatchIn(run[i].text)) i++
        }
        out += unit(start, i, code = false)
    }
    return out
}

internal sealed interface DiffDisplay {
    val segment: Int

    data class Source(val row: DiffRow) : DiffDisplay {
        override val segment: Int
            get() = row.segment
    }

    data class Markdown(val unit: DiffMarkdownUnit) : DiffDisplay {
        override val segment: Int
            get() = unit.segment
    }

    data class Pair(val old: DiffLine?, val current: DiffLine?, override val segment: Int) : DiffDisplay
}

/**
 * The items a diff view draws (Dart `_itemsFor` and `_MdFileIndex`). [formatted]: a Markdown file drawn as Markdown —
 * each run of same-kind lines (a gap's revealed lines are a run of their own, as Dart draws them per gap) is cut by
 * [diffMarkdownUnits]; code units and blank units stay source rows (a blank unit draws one empty line under its
 * numbers, as a source row does). Old/new sides track fences independently ([fenceState]): an added fence cannot turn
 * removed prose into code. [file], when given, tells a gap's revealed lines from the file's own lines.
 */
internal fun diffDisplay(
    rows: List<DiffRow>,
    formatted: Boolean,
    split: Boolean,
    fenceState: Map<DiffLine, Boolean>,
    file: DiffFile? = null,
): List<DiffDisplay> {
    val out = ArrayList<DiffDisplay>()
    // A revealed gap line carries its gap's segment; the file's own lines sit at their own index.
    fun group(row: DiffRow.Line): Int =
        if (file != null && file.segments.getOrNull(row.segment) !== row.line) row.segment else -1
    var at = 0
    while (at < rows.size) {
        val row = rows[at]
        if (row !is DiffRow.Line) {
            out += DiffDisplay.Source(row)
            at++
            continue
        }
        if (formatted) {
            val run = arrayListOf(row)
            at++
            while (at < rows.size) {
                val next = rows[at] as? DiffRow.Line ?: break
                if (next.line.kind != row.line.kind || group(next) != group(row)) break
                run += next
                at++
            }
            val lines = run.map { it.line }
            var from = 0
            for (unit in diffMarkdownUnits(lines, fenceState[lines.first()] ?: false, run.map { it.segment })) {
                if (unit.code || unit.blank) {
                    for (k in unit.lines.indices) out += DiffDisplay.Source(run[from + k])
                } else {
                    out += DiffDisplay.Markdown(unit)
                }
                from += unit.lines.size
            }
        } else if (split) {
            if (row.line.kind == DiffLineKind.Context) {
                out += DiffDisplay.Pair(row.line, row.line, row.segment)
                at++
            } else {
                // A change: its removed lines face its added lines, row by row (kit_diff_model.dart:502-522).
                val removed = ArrayList<DiffRow.Line>()
                val added = ArrayList<DiffRow.Line>()
                while (at < rows.size) {
                    val next = rows[at] as? DiffRow.Line ?: break
                    when (next.line.kind) {
                        DiffLineKind.Removed -> if (added.isEmpty()) removed += next else break
                        DiffLineKind.Added -> added += next
                        else -> break
                    }
                    at++
                }
                for (i in 0 until maxOf(removed.size, added.size)) out +=
                    DiffDisplay.Pair(
                        removed.getOrNull(i)?.line,
                        added.getOrNull(i)?.line,
                        (removed.getOrNull(i) ?: added[i]).segment,
                    )
            }
        } else {
            out += DiffDisplay.Source(row)
            at++
        }
    }
    return out
}

/**
 * Where each line stands against code fences (Dart `_MdFileIndex`, kit_diff_markdown.dart:147-172). Known folded lines
 * take part before they are revealed; a gap whose lines are not known resets both sides.
 */
internal fun diffFenceStates(file: DiffFile): Map<DiffLine, Boolean> = buildMap {
    var old = false
    var current = false
    fun line(line: DiffLine) {
        put(line, if (line.kind == DiffLineKind.Removed) old else current)
        if (!DiffMarkdownPatterns.fence.containsMatchIn(line.text)) return
        if (line.kind != DiffLineKind.Added) old = !old
        if (line.kind != DiffLineKind.Removed) current = !current
    }
    for (segment in file.segments) when (segment) {
        is DiffLine -> if (segment.kind != DiffLineKind.Hunk) line(segment)
        is DiffGap ->
            if (segment.lines == null) {
                old = false
                current = false
            } else segment.lines.forEach(::line)
    }
}
