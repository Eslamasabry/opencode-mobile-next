package io.github.eslamasabry.oc.core.markdown

/**
 * Half-written Markdown at the end of a reply still streaming (Flutter `kit_markdown_unfinished.dart`, plan 06 §KC).
 * `**bol`, `` `cod ``, `~~stri`, `[label](htt` and a table row cut short are closed or hidden before the tail parses,
 * so raw marks never show. Only the last line is read. Fences keep their own rule (an open fence is never touched).
 * A finished reply is never passed here: its exact text is drawn, so one that truly ends with a lone `*` keeps it.
 */
public object UnfinishedTail {
    private const val DOT = Dart.DOT
    private const val END = Dart.END
    private val fence = Regex("""^ {0,3}(`{3,}|~{3,})($DOT*)$END""")
    private val prefix = Regex("""^\s*(?:(?:#{1,6}|>|[-*+]|\d{1,9}[.)])\s+|>)*""")
    private val markChars = Regex("""[*_~`\[]""")
    private val wordBefore = Regex("""[A-Za-z0-9_\])]""")
    private val dartInt = Regex("""[+-]?(?:\d+|0[xX][0-9a-fA-F]+)""")

    /** [source] with the unfinished inline syntax of its last line closed or hidden; [source] when none is. */
    public fun close(source: String): String {
        val cut = source.lastIndexOf('\n') + 1
        val last = source.substring(cut)
        val before = source.substring(0, cut)
        val previous = if (cut == 0) null else before.dropLast(1).substringAfterLast('\n')
        val drawn = closeLast(previous, last) { inOpenFence(before, last) }
        return if (drawn == last) source else before + drawn
    }

    /**
     * The drawn form of the [last] line given the line before it ([previous], null when [last] is the first line).
     * [inOpenFence] is asked only when the line has words.
     */
    internal fun closeLast(previous: String?, last: String, inOpenFence: () -> Boolean): String {
        if (last.isBlank()) return last
        if (inOpenFence()) return last
        // A table row with one cell is a paragraph to the parser: hide it until its second cell starts.
        if (previous != null && last.trimStart().startsWith("|")) {
            if (previous.trimStart().startsWith("|") && last.count { it == '|' } < 2) return ""
        }
        return closeLine(last)
    }

    /** Whether [last] sits in, or opens, a fence, reading [before] (every line before it, ending with `\n`). */
    internal fun inOpenFence(before: String, last: String): Boolean {
        if (!before.contains("```") && !before.contains("~~~")) return fence.find(last) != null
        var open: String? = null
        for (line in before.split('\n')) {
            val m = fence.find(line) ?: continue
            val marker = m.groupValues[1]
            val rest = m.groupValues[2]
            if (open == null) {
                if (!(marker[0] == '`' && rest.contains('`'))) open = marker
            } else if (marker[0] == open[0] && marker.length >= open.length && rest.isBlank()) {
                open = null
            }
        }
        return open != null || fence.find(last) != null
    }

    private fun closeLine(line: String): String {
        if (!markChars.containsMatchIn(line)) return line
        var head = prefix.find(line)!!.value
        var body = line.substring(head.length)
        // A table row: only its last cell is still being written.
        if (line.trimStart().startsWith("|")) {
            val at = line.lastIndexOf('|') + 1
            head = line.substring(0, at)
            body = line.substring(at)
        }
        return head + closeBody(body)
    }

    private fun closeBody(body: String): String {
        var text = closeLink(body)
        val tick = openTick(text)
        var code = ""
        if (tick >= 0) {
            code = text.substring(tick)
            text = text.substring(0, tick)
            // A lone backtick has no words to show yet.
            code = if (code.length == 1) "" else "$code`"
        }
        val stack = ArrayList<String>()
        val cleaned = closeEmphasis(text, stack)
        val out = StringBuilder(cleaned)
        if (code.isNotEmpty()) out.append(code)
        if (stack.isNotEmpty()) {
            // The closers go right after the words, before trailing spaces.
            val joined = out.toString()
            val trimmed = joined.trimEnd()
            val spaces = joined.substring(trimmed.length)
            return trimmed + stack.reversed().joinToString("") + spaces
        }
        return out.toString()
    }

    /** Where an unmatched single backtick opens a code span, or -1. */
    private fun openTick(s: String): Int {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\') {
                i += 2
                continue
            }
            if (c != '`') {
                i++
                continue
            }
            var run = i
            while (run < s.length && s[run] == '`') run++
            if (run - i > 1) {
                i = run
                continue
            }
            val close = s.indexOf('`', i + 1)
            if (close < 0) return i
            i = close + 1
        }
        return -1
    }

    /** [s] with a link being written reduced to its label. */
    private fun closeLink(s: String): String {
        val at = s.lastIndexOf('[')
        if (at < 0) return s
        if (at > 0 && (s[at - 1] == '\\' || s[at - 1] == '!')) return s
        if (openTick(s.substring(0, at)) >= 0) return s
        // `a[0` is an index, not a link.
        if (at > 0 && wordBefore.matches(s[at - 1].toString())) return s
        val rest = s.substring(at + 1)
        val close = rest.indexOf(']')
        val label: String
        if (close < 0) {
            label = rest
        } else {
            label = rest.substring(0, close)
            val after = rest.substring(close + 1)
            // Finished, or not a link at all: leave it be.
            if (after.isNotEmpty() && !after.startsWith("(")) return s
            if (after.startsWith("(") && (after.contains(')') || after.any { it.isWhitespace() })) return s
        }
        val t = label.trim()
        // `[ ]` and `[x]` are a task list's box, `[` alone has no label yet.
        if (t.isEmpty() || t == "x" || t == "X" || dartInt.matches(t)) {
            return if (t.isEmpty() && close < 0 && s.substring(0, at).isNotBlank()) s.substring(0, at) else s
        }
        return s.substring(0, at) + label
    }

    /** [s] without a trailing mark that opens nothing; the marks left open are pushed on [stack] (outermost first). */
    private fun closeEmphasis(s: String, stack: MutableList<String>): String {
        var out = s
        val openAt = ArrayList<Int>()
        var i = 0
        while (i < out.length) {
            val c = out[i]
            if (c == '\\') {
                i += 2
                continue
            }
            if (c == '`') {
                val close = out.indexOf('`', i + 1)
                i = if (close < 0) out.length else close + 1
                continue
            }
            var mark: String? = null
            if (c == '*') {
                mark = if (out.startsWith("***", i)) "***" else if (out.startsWith("**", i)) "**" else "*"
            } else if (c == '_' && out.startsWith("__", i)) {
                mark = "__"
            } else if (c == '~' && out.startsWith("~~", i)) {
                mark = "~~"
            } else if ((c == '~' || c == '_') && i == out.length - 1) {
                // The first half of `~~` or `__`: a lone one at a word start.
                if (i == 0 || out[i - 1].isWhitespace()) out = out.substring(0, i)
                break
            }
            if (mark == null) {
                i++
                continue
            }
            val end = i + mark.length
            val spaceBefore = i == 0 || out[i - 1].isWhitespace()
            val spaceAfter = end >= out.length || out[end].isWhitespace()
            val top = stack.lastIndexOf(mark)
            if (top >= 0 && !spaceBefore) {
                stack.subList(top, stack.size).clear()
                openAt.subList(top, openAt.size).clear()
            } else if (!spaceAfter) {
                stack += mark
                openAt += i
            } else if (end >= out.length) {
                // A mark at the very end that closes nothing: nothing to show.
                out = out.substring(0, i)
                break
            }
            i = end
        }
        // A mark opened right at the end has no words yet.
        while (stack.isNotEmpty() && openAt.last() + stack.last().length >= out.length) {
            out = out.substring(0, openAt.last())
            stack.removeAt(stack.lastIndex)
            openAt.removeAt(openAt.lastIndex)
        }
        return out
    }
}
