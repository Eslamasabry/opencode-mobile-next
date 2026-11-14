package io.github.eslamasabry.oc.feature.chat.requests.ui

/**
 * The lines of a change the person reads in Details (audit 1, item 4): the changed lines, not the diff tool's header.
 * The file's name is shown once, in plain form, on the block itself, so "Index: a1.txt", the row of "=", the
 * `--- a1.txt` / `+++ a1.txt` pair and git's `diff --git` / `index` / mode lines are left out. Only the lines before the
 * first changed line are ever dropped; a changed line that reads like a header is never touched.
 */
internal fun changeBodyText(patch: String): String {
    val lines = patch.split('\n')
    var first = 0
    while (first < lines.size && isHeaderLine(lines[first].removeSuffix("\r"))) first++
    return if (first == 0) patch else lines.drop(first).joinToString("\n")
}

private val modeLine = Regex("^(old mode|new mode|deleted file mode|new file mode|similarity index|dissimilarity index|rename from|rename to|copy from|copy to) ")

private fun isHeaderLine(line: String): Boolean =
    line.startsWith("Index: ") ||
        (line.length >= 4 && line.all { it == '=' }) ||
        line.startsWith("diff --git ") ||
        line.startsWith("index ") ||
        line.startsWith("--- ") ||
        line.startsWith("+++ ") ||
        modeLine.containsMatchIn(line)
