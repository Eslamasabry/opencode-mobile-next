package io.github.eslamasabry.oc.ui.kitchat.diff

/**
 * One rendered same-kind Markdown block; source lines retain both sides' numbers for selection and
 * copy.
 */
internal data class DiffMarkdownUnit(val lines: List<DiffLine>, val segment: Int)

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

    data class Pair(val old: DiffLine?, val current: DiffLine?, override val segment: Int) :
        DiffDisplay
}

private val fence = Regex("^ {0,3}(`{3,}|~{3,})")
private val boundary = Regex("^ {0,3}(#{1,6}(\\s|$)|[-*_]{3,}\\s*$|>|\\||([-*+]|\\d{1,9}[.)])\\s)")

/** Old/new sides track fences independently: an added fence cannot turn removed prose into code. */
internal fun diffDisplay(
    rows: List<DiffRow>,
    formatted: Boolean,
    split: Boolean,
    fenceState: Map<DiffLine, Boolean>,
): List<DiffDisplay> {
    val out = ArrayList<DiffDisplay>()
    var at = 0
    while (at < rows.size) {
        val row = rows[at]
        if (row !is DiffRow.Line) {
            out += DiffDisplay.Source(row)
            at++
            continue
        }
        if (formatted) {
            val line = row.line
            val open = fenceState[line] ?: false
            if (fence.containsMatchIn(line.text)) {
                out += DiffDisplay.Source(row)
                at++
            } else if (open || line.text.isBlank()) {
                out += DiffDisplay.Source(row)
                at++
            } else {
                val run = arrayListOf(line)
                at++
                val firstText = line.text.trimStart()
                val table = firstText.startsWith('|')
                val quote = firstText.startsWith('>')
                val list = Regex("^\\s*([-*+]|\\d{1,9}[.)])\\s").containsMatchIn(line.text)
                while (at < rows.size) {
                    val next = rows[at] as? DiffRow.Line ?: break
                    val text = next.line.text.trimStart()
                    if (
                        next.line.kind != line.kind ||
                            next.line.text.isBlank() ||
                            fence.containsMatchIn(next.line.text)
                    )
                        break
                    if (firstText.startsWith('#')) break
                    if (table && !text.startsWith('|') || quote && !text.startsWith('>')) break
                    if (
                        !table &&
                            !quote &&
                            boundary.containsMatchIn(next.line.text) &&
                            !(list &&
                                Regex("^\\s*([-*+]|\\d{1,9}[.)])\\s")
                                    .containsMatchIn(next.line.text))
                    )
                        break
                    run += next.line
                    at++
                }
                out += DiffDisplay.Markdown(DiffMarkdownUnit(run, row.segment))
            }
        } else if (split) {
            if (row.line.kind == DiffLineKind.Context) {
                out += DiffDisplay.Pair(row.line, row.line, row.segment)
                at++
            } else {
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
 * Known folded lines participate in fence tracking even before they are expanded. Unknown patch
 * gaps reset it.
 */
internal fun diffFenceStates(file: DiffFile): Map<DiffLine, Boolean> = buildMap {
    var old = false
    var current = false
    fun line(line: DiffLine) {
        put(line, if (line.kind == DiffLineKind.Removed) old else current)
        if (!fence.containsMatchIn(line.text)) return
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
