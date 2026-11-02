package io.github.eslamasabry.oc.core.markdown

/** GitHub pipe tables (Flutter `kit_markdown_table.dart` scanner). */
internal object TableScanner {
    private val delimiterCell = Regex(""":?-{3,}:?""")

    /** The cells of a row: outer pipes optional, `\|` is a literal pipe, cells trimmed. Empty when no `|`. */
    fun cells(line: String): List<String> {
        val trimmed = line.trim()
        if (!trimmed.contains('|')) return emptyList()
        val cells = ArrayList<String>()
        var cell = StringBuilder()
        var at = if (trimmed.startsWith("|")) 1 else 0
        var endedWithSeparator = false
        while (at < trimmed.length) {
            val c = trimmed[at]
            if (c == '\\' && at + 1 < trimmed.length) {
                val next = trimmed[at + 1]
                if (next == '|') {
                    cell.append('|')
                    at += 2
                    endedWithSeparator = false
                    continue
                }
                if (next == '\\') {
                    cell.append("\\\\")
                    at += 2
                    endedWithSeparator = false
                    continue
                }
            }
            if (c == '|') {
                cells += cell.toString().trim()
                cell = StringBuilder()
                endedWithSeparator = true
            } else {
                cell.append(c)
                endedWithSeparator = false
            }
            at++
        }
        if (!endedWithSeparator) cells += cell.toString().trim()
        return cells
    }

    fun isDelimiter(line: String): Boolean {
        val cells = cells(line)
        return cells.size >= 2 && cells.all { delimiterCell.matchEntire(it) != null }
    }

    fun align(cell: String): Align {
        val start = cell.startsWith(":")
        val end = cell.endsWith(":")
        return when {
            start && end -> Align.Center
            end -> Align.End
            start -> Align.Start
            else -> Align.None
        }
    }
}
