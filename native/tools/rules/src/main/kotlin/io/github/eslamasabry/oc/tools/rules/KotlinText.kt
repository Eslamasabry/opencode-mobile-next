package io.github.eslamasabry.oc.tools.rules

/** Text helpers of the K-SEC rules, copied from the security guard (SecTree.kt) so both read source the same way. */
internal object KotlinText {
    fun stripComments(src: String): String {
        val out = StringBuilder(src.length)
        var i = 0
        var inString = false
        var raw = false
        while (i < src.length) {
            val c = src[i]
            val next = if (i + 1 < src.length) src[i + 1] else '\u0000'
            when {
                inString && raw -> {
                    if (src.startsWith("\"\"\"", i)) { out.append("\"\"\""); i += 3; inString = false; continue }
                    out.append(c)
                }
                inString -> {
                    if (c == '\\' && i + 1 < src.length) { out.append(c).append(next); i += 2; continue }
                    if (c == '"') inString = false
                    out.append(c)
                }
                src.startsWith("\"\"\"", i) -> { inString = true; raw = true; out.append("\"\"\""); i += 3; continue }
                c == '"' -> { inString = true; raw = false; out.append(c) }
                c == '\'' && i + 2 < src.length && (src[i + 2] == '\'' || (next == '\\' && i + 3 < src.length)) -> {
                    // a char literal: copy through its closing quote so '"' does not open a string
                    val end = src.indexOf('\'', i + if (next == '\\') 3 else 2)
                    if (end < 0) { out.append(c) } else { out.append(src, i, end + 1); i = end + 1; continue }
                }
                c == '/' && next == '/' -> {
                    while (i < src.length && src[i] != '\n') i++
                    continue
                }
                c == '/' && next == '*' -> {
                    var depth = 1
                    i += 2
                    while (i < src.length && depth > 0) {
                        if (src.startsWith("/*", i)) { depth++; i += 2; continue }
                        if (src.startsWith("*/", i)) { depth--; i += 2; continue }
                        if (src[i] == '\n') out.append('\n')
                        i++
                    }
                    continue
                }
                else -> out.append(c)
            }
            i++
        }
        return out.toString()
    }

    /** Mask literal text, preserving offsets and executable string-template expressions. */
    fun executable(src: String): String {
        val code = stripComments(src)
        val out = code.toCharArray()
        fun blank(start: Int, end: Int) {
            for (j in start until end) if (out[j] != '\n') out[j] = ' '
        }
        fun scan(start: Int, template: Boolean): Int {
            var i = start
            var braces = 0
            while (i < code.length) {
                val c = code[i]
                if (template && c == '}' && braces == 0) return i + 1
                if (c == '{') braces++
                if (c == '}') braces--
                if (c == '\'') {
                    val end = if (code.getOrNull(i + 1) == '\\') i + 4 else i + 3
                    blank(i, end.coerceAtMost(code.length)); i = end; continue
                }
                if (c != '"') { i++; continue }
                val raw = code.startsWith("\"\"\"", i)
                val delimiter = if (raw) 3 else 1
                blank(i, i + delimiter); i += delimiter
                while (i < code.length) {
                    if ((!raw && code[i] == '"') || (raw && code.startsWith("\"\"\"", i))) {
                        blank(i, i + delimiter); i += delimiter; break
                    }
                    if (!raw && code[i] == '\\') {
                        blank(i, (i + 2).coerceAtMost(code.length)); i += 2; continue
                    }
                    if (code[i] == '$' && code.getOrNull(i + 1) == '{') {
                        blank(i, i + 2); i = scan(i + 2, true); continue
                    }
                    blank(i, i + 1); i++
                }
            }
            return i
        }
        scan(0, false)
        return String(out)
    }

    /** The text inside the parentheses that open at [openParen] (balanced; strings skipped). */
    fun balancedArgs(code: String, openParen: Int): String {
        var depth = 0
        var i = openParen
        var inString = false
        while (i < code.length) {
            val c = code[i]
            if (inString) {
                if (c == '\\') { i += 2; continue }
                if (c == '"') inString = false
            } else when (c) {
                '"' -> inString = true
                '(' -> depth++
                ')' -> { depth--; if (depth == 0) return code.substring(openParen + 1, i) }
            }
            i++
        }
        return code.substring(openParen + 1)
    }
}
