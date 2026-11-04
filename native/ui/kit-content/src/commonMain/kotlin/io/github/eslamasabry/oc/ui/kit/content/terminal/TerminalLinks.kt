package io.github.eslamasabry.oc.ui.kit.content.terminal

/**
 * One row of a terminal screen as the link finder reads it: its [text] and whether the terminal itself carried it on
 * from the row above ([wrapped], a soft wrap).
 */
public data class TerminalRow(val text: String, val wrapped: Boolean = false)

/**
 * Links in terminal output (B-XPLAT-010). Port of Dart `KitTerminalLinks.urlAt` (`lib/ui/kit/kit_terminal_links.dart`):
 * a row that reaches the last column and carries on in the next row is one line of text, whether the terminal wrapped
 * it (soft) or the program did (a hard newline), so a sign-in link spread over several rows opens whole. Trailing
 * punctuation is not part of the link; a closing bracket is kept only when the link opened one.
 */
public object TerminalLinks {
    private val URL = Regex("""https?://[^\s<>"'`\u0000-\u001f\u007f]+""")
    private const val MAX_ROWS = 60

    /** The link under column [x] of row [y], its wrapped rows joined; null when that cell is not on a link. */
    public fun urlAt(rows: List<TerminalRow>, columns: Int, x: Int, y: Int): String? {
        if (y < 0 || y >= rows.size) return null
        fun full(row: Int): Boolean {
            if (columns < 1) return false
            val c = rows[row].text.getOrNull(columns - 1)
            return c != null && c != ' ' && c != '\u0000'
        }
        fun continues(row: Int): Boolean {
            if (row <= 0 || !full(row - 1)) return false
            if (rows[row].wrapped) return true
            val c = rows[row].text.firstOrNull()
            return c != null && c != ' ' && c != '\u0000'
        }
        var first = y
        while (first > 0 && y - first < MAX_ROWS && continues(first)) first--
        var last = y
        while (last + 1 < rows.size && last - y < MAX_ROWS && continues(last + 1)) last++
        val text = StringBuilder()
        var index = -1
        for (row in first..last) {
            val line = rows[row].text.trimEnd('\u0000')
            if (row == y) index = text.length + x.coerceIn(0, if (line.isNotEmpty()) line.length - 1 else 0)
            text.append(line)
        }
        for (match in URL.findAll(text)) {
            if (index < match.range.first || index > match.range.last) continue
            return trim(match.value)
        }
        return null
    }

    /** Lays [output] out on a screen [columns] wide the way a terminal does: CR LF ends a row, a full row soft-wraps. */
    public fun layout(output: String, columns: Int): List<TerminalRow> {
        val rows = mutableListOf<TerminalRow>()
        for (line in output.replace("\r\n", "\n").split('\n')) {
            if (line.isEmpty()) {
                rows += TerminalRow("")
                continue
            }
            line.chunked(columns).forEachIndexed { i, part -> rows += TerminalRow(part, wrapped = i > 0) }
        }
        if (rows.lastOrNull()?.text?.isEmpty() == true) rows.removeAt(rows.lastIndex)
        return rows
    }

    private fun trim(url: String): String {
        var end = url.length
        while (end > 0) {
            val c = url[end - 1]
            val head = url.substring(0, end)
            end =
                when {
                    c in ".,;:!?'\"]}" -> end - 1
                    c == ')' && head.count { it == ')' } > head.count { it == '(' } -> end - 1
                    else -> break
                }
        }
        return url.substring(0, end)
    }
}
