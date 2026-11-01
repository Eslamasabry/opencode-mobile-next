package io.github.eslamasabry.oc.ui.kitchat.code

/*
 * Display rules of code text (B-CHAT-055), ported from `lib/ui/kit/kit_code_block_parts.dart`. Pure functions: the
 * copied text is always the source; these only change what is drawn.
 */

private const val ZERO_WIDTH_SPACE = '​'

/** Marks a wrapping code line may break after, besides spaces (`kit_code_block_parts.dart:3`). */
private val breakAfter = setOf('.', ',', ';', ':', '(', '[', '{', '=', '&', '|', '/')

/** Nothing breaks before a closer or separator: it stays with what it closes (`kit_code_block_parts.dart:7`). */
private val noBreakBefore = setOf('.', ',', ';', ':', ')', ']', '}')

/**
 * [line] with a zero-width break chance after punctuation inside a long token, so a wrapping code line breaks at
 * "intent." / "getStringExtra(" instead of mid-identifier. A run of the same mark ("::", "==", "&&", "//") stays whole,
 * nothing breaks before a closer, and a short ending after a dot (a file extension) stays with its name. Port of
 * `kitCodeBreakable` (`kit_code_block_parts.dart:18`).
 */
public fun codeBreakable(line: String): String {
    if (line.length < 2) return line
    var out: StringBuilder? = null
    for (i in line.indices) {
        val char = line[i]
        out?.append(char)
        if (i == line.length - 1 || char !in breakAfter) continue
        val next = line[i + 1]
        if (next == char || next.isWhitespace() || next in noBreakBefore) continue
        if (char == '.' && shortTail(line, i + 1)) continue
        if (out == null) out = StringBuilder(line.length + 8).append(line, 0, i + 1)
        out.append(ZERO_WIDTH_SPACE)
    }
    return out?.toString() ?: line
}

/** A selection copied from a wrapping line copies the source again (Dart `_CopiesSource`). */
public fun stripCodeBreaks(text: String): String = text.replace(ZERO_WIDTH_SPACE.toString(), "")

private fun isAlnum(c: Char) = c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9'

/** What makes a short word after a dot part of a longer expression ("a.b.c", "x.map(", "list.first["). */
private fun continues(c: Char) = isAlnum(c) || c == '_' || c == '(' || c == '[' || c == '{' || c == '.'

private fun shortTail(line: String, from: Int): Boolean {
    var i = from
    while (i < line.length && isAlnum(line[i])) i++
    val length = i - from
    if (length == 0 || length > 4) return false
    return i == line.length || !continues(line[i])
}

private val heredoc = Regex("""<<(-?)\s*(['"]?)([A-Za-z_][A-Za-z0-9_]*)\2""")
private val blockOpen = setOf("if", "for", "while", "until", "case", "select")
private val blockClose = setOf("fi", "done", "esac")

/**
 * For each line of a shell command, whether it starts a new command, so a command block draws its `$` prompt only
 * there. A heredoc's body and terminator, a line continued by a trailing `\`, `|`, `&&` or `(`, a string or bracket
 * left open, and the body of an `if`/`for`/`while`/`case` block continue the command above, the way a shell shows its
 * `>` prompt. Port of `kitCommandLineStarts` (`kit_code_block_parts.dart:267`).
 */
public fun commandLineStarts(lines: List<String>): List<Boolean> {
    val starts = BooleanArray(lines.size)
    val heredocs = ArrayDeque<Pair<String, Boolean>>()
    var quote: Char? = null
    var depth = 0
    var continued = false
    for (i in lines.indices) {
        val line = lines[i]
        if (heredocs.isNotEmpty()) {
            val (word, stripTabs) = heredocs.first()
            val body = if (stripTabs) line.trimStart('\t') else line
            if (body.trimEnd() == word) heredocs.removeFirst()
            continue
        }
        starts[i] = quote == null && depth <= 0 && !continued
        val pending = ArrayList<Pair<String, Boolean>>()
        val code = StringBuilder()
        var wordStart = true
        var firstWord = true
        val word = StringBuilder()
        fun endWord() {
            val w = word.toString()
            if (w.isNotEmpty() && firstWord) {
                if (w in blockOpen) depth++
                if (w in blockClose) depth--
            }
            if (w.isNotEmpty()) firstWord = false
            word.setLength(0)
        }
        var c = 0
        while (c < line.length) {
            val ch = line[c]
            if (quote != null) {
                if (ch == '\\' && quote == '"' && c + 1 < line.length) {
                    c++
                } else if (ch == quote) {
                    quote = null
                }
                c++
                continue
            }
            if (ch == '#' && wordStart) break
            if (ch == '\\' && c + 1 < line.length) {
                c += 2
                code.append("xx")
                wordStart = false
                continue
            }
            code.append(ch)
            if (ch == '\'' || ch == '"') {
                quote = ch
                wordStart = false
                c++
                continue
            }
            if (ch == '<' && line.startsWith("<<", c) && !line.startsWith("<<<", c)) {
                val match = heredoc.matchAt(line, c)
                if (match != null) {
                    pending += match.groupValues[3] to (match.groupValues[1] == "-")
                    code.append(line, c + 1, match.range.last + 1)
                    c = match.range.last + 1
                    continue
                }
            }
            if (ch == '(' || ch == '{') depth++
            if (ch == ')' || ch == '}') depth--
            val separator = ch.isWhitespace() || ch in ";&|()"
            if (separator) {
                endWord()
                if (ch != ' ' && ch != '\t') firstWord = true
            } else {
                word.append(ch)
            }
            wordStart = separator
            c++
        }
        endWord()
        heredocs.addAll(pending)
        val tail = code.toString().trimEnd()
        continued = tail.endsWith('\\') || tail.endsWith('|') || tail.endsWith("&&") || tail.endsWith('(')
    }
    if (lines.isNotEmpty()) starts[0] = true
    return starts.toList()
}
