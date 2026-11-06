package io.github.eslamasabry.oc.core.markdown


/**
 * One line of inline Markdown, scanned the CommonMark way (spec 6.2, "delimiter run" algorithm): the line becomes a
 * list of nodes (plain text, atoms that are already final such as code spans, links and pictures, and `*` `_` `~~`
 * delimiter runs); [run] then pairs the delimiter runs by the flanking rules; [writeTo] lays the words out without
 * their marks and records the spans. [start]..[end] is the part of [src] this scan owns (a link label is scanned
 * again as its own range; the characters around it still decide flanking).
 *
 * Chat deviations from the spec, on purpose: a `*` or `_` run between two ASCII letters or digits never opens or
 * closes (`2*3*4`, `a*b*c`, `x**2`, `snake_case`), and a `_` run glued to a `.` between word characters
 * (`__init__.py`, `os.__name__`) stays literal. Raw HTML is never interpreted. `<https://x>` and bare http(s)
 * addresses become links; reference links, footnotes and titles are not supported (a title is dropped).
 */
internal class InlineScan(
    private val src: String,
    private val start: Int,
    private val end: Int,
    private val pictures: Map<Int, MarkdownPicture>,
    private val links: Boolean,
) {
    private sealed class Node

    private class Text(val text: String) : Node()

    private class Atom(val text: String, val spans: List<InlineSpan>) : Node()

    private class Delim(
        val ch: Char,
        val srcStart: Int,
        val orig: Int,
        var count: Int,
        val canOpen: Boolean,
        val canClose: Boolean,
        /** A `_` run glued to a dotted name (`__init__.py`): no pair reaches across it. */
        val barrier: Boolean = false,
    ) : Node() {
        var active = true
    }

    private class Pairing(val opener: Int, val closer: Int, val kind: SpanKind)

    private val nodes = ArrayList<Node>()
    private val pairs = ArrayList<Pairing>()
    private val pending = StringBuilder()

    fun run() {
        scan()
        pairEmphasis()
    }

    /** Delimiter runs with marks left over (never paired, or paired only in part), in text order. */
    fun leftovers(): List<InlineParser.Leftover> =
        nodes.filterIsInstance<Delim>().let { delims ->
            delims.withIndex()
                .filter { it.value.count > 0 }
                .map { (i, d) ->
                    // An opener with a dotted name after it (`__init__.py`) would never close: not open.
                    val walled = delims.drop(i + 1).any { it.barrier && it.ch == d.ch }
                    InlineParser.Leftover(d.srcStart, d.ch, d.count, d.orig, d.active && d.canOpen && !walled)
                }
        }

    /** Appends the words to [out] and the spans (offsets into [out]) to [spans]. */
    fun writeTo(out: StringBuilder, spans: MutableList<InlineSpan>) {
        val first = IntArray(nodes.size)
        val last = IntArray(nodes.size)
        for ((i, node) in nodes.withIndex()) {
            first[i] = out.length
            when (node) {
                is Text -> out.append(node.text)
                is Delim -> repeat(node.count) { out.append(node.ch) }
                is Atom -> {
                    val at = out.length
                    out.append(node.text)
                    for (s in node.spans) spans += InlineSpan(at + s.start, at + s.end, s.kind)
                }
            }
            last[i] = out.length
        }
        for (p in pairs) spans += InlineSpan(last[p.opener], first[p.closer], p.kind)
    }

    // -- Scanning ----------------------------------------------------------------------------------------------

    private fun flush() {
        if (pending.isEmpty()) return
        nodes += Text(pending.toString())
        pending.setLength(0)
    }

    private fun atom(text: String, spans: List<InlineSpan>) {
        flush()
        nodes += Atom(text, spans)
    }

    private fun scan() {
        var i = start
        while (i < end) {
            val c = src[i]
            val picture = pictures[i]
            when {
                picture != null -> {
                    atom(PLACEHOLDER.toString(), listOf(InlineSpan(0, 1, SpanKind.Picture(picture))))
                    i++
                }
                c == '\\' && i + 1 < end && isAsciiPunctuation(src[i + 1]) && pictures[i + 1] == null -> {
                    pending.append(src[i + 1])
                    i += 2
                }
                c == '`' -> i = codeSpan(i)
                c == '&' -> i = entity(i)
                c == '<' && links -> i = autolink(i)
                c == '[' && links -> i = link(i)
                c == 'h' && links && isBareUrlStart(i) -> i = bareUrl(i)
                c == '*' || c == '_' || c == '~' -> i = delimiterRun(i)
                else -> {
                    pending.append(c)
                    i++
                }
            }
        }
        flush()
    }

    /** A run of backticks closed by a run of the same length is a code span; otherwise the backticks are text. */
    private fun codeSpan(at: Int): Int {
        var open = at
        while (open < end && src[open] == '`') open++
        val length = open - at
        var j = open
        while (j < end) {
            if (src[j] != '`') {
                j++
                continue
            }
            var run = j
            while (run < end && src[run] == '`') run++
            if (run - j == length) {
                var code = src.substring(open, j)
                // One space on both sides is padding, unless the span is only spaces.
                if (code.length >= 2 && code.startsWith(" ") && code.endsWith(" ") && code.any { it != ' ' }) {
                    code = code.substring(1, code.length - 1)
                }
                val spans = ArrayList<InlineSpan>()
                spans += InlineSpan(0, code.length, SpanKind.Code)
                if (PathChipScanner.looksLikeFilePath(code)) {
                    val (path, number) = PathChipScanner.stripLineSuffix(code)
                    spans += InlineSpan(0, code.length, SpanKind.PathCandidate(path, number))
                }
                atom(code, spans)
                return run
            }
            j = run
        }
        pending.append(src, at, open)
        return open
    }

    private fun entity(at: Int): Int {
        val semi = src.indexOf(';', at + 1)
        if (semi < 0 || semi >= end || semi - at > 33) {
            pending.append('&')
            return at + 1
        }
        val name = src.substring(at + 1, semi)
        val decoded = decodeEntity(name)
        if (decoded == null) {
            pending.append('&')
            return at + 1
        }
        pending.append(decoded)
        return semi + 1
    }

    /** `<http://…>` / `<https://…>`: a link whose words are the address. Any other `<` is plain text. */
    private fun autolink(at: Int): Int {
        val close = src.indexOf('>', at + 1)
        if (close < 0 || close >= end) {
            pending.append('<')
            return at + 1
        }
        val url = src.substring(at + 1, close)
        val scheme = if (url.startsWith("https://")) 8 else if (url.startsWith("http://")) 7 else 0
        if (scheme == 0 || url.length == scheme || url.any { it.isWhitespace() || it == '<' }) {
            pending.append('<')
            return at + 1
        }
        atom(url, listOf(InlineSpan(0, url.length, SpanKind.Link(url))))
        return close + 1
    }

    private fun isBareUrlStart(at: Int): Boolean {
        if (!(src.startsWith("http://", at) || src.startsWith("https://", at))) return false
        if (at > start && src[at - 1].isLetterOrDigit()) return false
        val scheme = if (src.startsWith("https://", at)) 8 else 7
        return at + scheme < end && !src[at + scheme].isWhitespace()
    }

    /** A bare address runs to white space or `<>[]` or a backtick; its parentheses must balance, and trailing punctuation is left out. */
    private fun bareUrl(at: Int): Int {
        var j = at
        var depth = 0
        while (j < end) {
            val c = src[j]
            if (c.isWhitespace() || c == '<' || c == '>' || c == '[' || c == ']' || c == '`' || pictures.containsKey(j)) break
            if (c == '(') {
                depth++
            } else if (c == ')') {
                if (depth == 0) break
                depth--
            }
            j++
        }
        // Trailing punctuation belongs to the sentence; an unclosed `(` at the end is not part of the address.
        while (j > at && src[j - 1] in TRAILING) j--
        while (j > at && src[j - 1] == '(') j--
        val scheme = if (src.startsWith("https://", at)) 8 else 7
        if (j - at <= scheme) {
            pending.append(src[at])
            return at + 1
        }
        val url = src.substring(at, j)
        atom(url, listOf(InlineSpan(0, url.length, SpanKind.Link(url))))
        return j
    }

    /** `[label](target "title")`; the label is scanned again, so it can hold emphasis and code. Anything else is text. */
    private fun link(at: Int): Int {
        val close = matchingBracket(at)
        if (close < 0 || close + 1 >= end || src[close + 1] != '(') {
            pending.append('[')
            return at + 1
        }
        val target = linkTarget(close + 2)
        if (target == null || target.first.isEmpty()) {
            pending.append('[')
            return at + 1
        }
        val label = InlineScan(src, at + 1, close, pictures, links = false)
        label.run()
        val text = StringBuilder()
        val inner = ArrayList<InlineSpan>()
        label.writeTo(text, inner)
        if (text.isEmpty()) {
            pending.append('[')
            return at + 1
        }
        atom(text.toString(), listOf(InlineSpan(0, text.length, SpanKind.Link(target.first))) + inner)
        return target.second
    }

    /** The `]` that closes the `[` at [open], skipping escapes, code spans and nested brackets; -1 when none. */
    private fun matchingBracket(open: Int): Int {
        var depth = 0
        var j = open
        while (j < end) {
            when (src[j]) {
                '\\' -> j++
                '`' -> {
                    var run = j
                    while (run < end && src[run] == '`') run++
                    var k = run
                    var closed = -1
                    while (k < end) {
                        if (src[k] != '`') {
                            k++
                            continue
                        }
                        var r = k
                        while (r < end && src[r] == '`') r++
                        if (r - k == run - j) {
                            closed = r
                            break
                        }
                        k = r
                    }
                    j = if (closed >= 0) closed - 1 else run - 1
                }
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) return j
                }
            }
            j++
        }
        return -1
    }

    /** The target after `(`: (address, index after the `)`), or null. Parentheses in the address must balance. */
    private fun linkTarget(from: Int): Pair<String, Int>? {
        var j = from
        while (j < end && (src[j] == ' ' || src[j] == '\t')) j++
        val url = StringBuilder()
        if (j < end && src[j] == '<') {
            j++
            while (j < end && src[j] != '>') {
                if (src[j] == '<' || src[j] == '\n') return null
                if (src[j] == '\\' && j + 1 < end && isAsciiPunctuation(src[j + 1])) j++
                url.append(src[j])
                j++
            }
            if (j >= end) return null
            j++
        } else {
            var depth = 0
            while (j < end) {
                val c = src[j]
                if (c == ' ' || c == '\t' || c.code < 0x20) break
                if (c == '\\' && j + 1 < end && isAsciiPunctuation(src[j + 1])) {
                    url.append(src[j + 1])
                    j += 2
                    continue
                }
                if (c == '(') {
                    depth++
                } else if (c == ')') {
                    if (depth == 0) break
                    depth--
                }
                url.append(c)
                j++
            }
            if (depth != 0) return null
        }
        while (j < end && (src[j] == ' ' || src[j] == '\t')) j++
        if (j < end && (src[j] == '"' || src[j] == '\'' || src[j] == '(')) {
            val closer = if (src[j] == '(') ')' else src[j]
            var k = j + 1
            while (k < end && src[k] != closer) {
                if (src[k] == '\\') k++
                k++
            }
            if (k >= end) return null
            j = k + 1
            while (j < end && (src[j] == ' ' || src[j] == '\t')) j++
        }
        if (j >= end || src[j] != ')') return null
        return url.toString() to j + 1
    }

    // -- Emphasis ----------------------------------------------------------------------------------------------

    private fun delimiterRun(at: Int): Int {
        val ch = src[at]
        var stop = at
        while (stop < end && src[stop] == ch) stop++
        val count = stop - at
        // `~` strikes through only as a pair (`~~`); one or three is text.
        if (ch == '~' && count != 2) {
            pending.append(src, at, stop)
            return stop
        }
        val beforeWs = at == 0 || isWhitespace(at - 1)
        val afterWs = stop >= src.length || isWhitespace(stop)
        val beforePunct = at > 0 && isPunctuation(at - 1)
        val afterPunct = stop < src.length && isPunctuation(stop)
        val left = !afterWs && (!afterPunct || beforeWs || beforePunct)
        val right = !beforeWs && (!beforePunct || afterWs || afterPunct)
        var canOpen: Boolean
        var canClose: Boolean
        var barrier = false
        if (ch == '_') {
            canOpen = left && (!right || beforePunct)
            canClose = right && (!left || afterPunct)
            // `__init__.py`, `os.__name__`: a dot between word characters is part of a name.
            if (at >= 2 && src[at - 1] == '.' && isAsciiWord(at - 2)) {
                canOpen = false
                barrier = true
            }
            if (stop + 1 < src.length && src[stop] == '.' && isAsciiWord(stop + 1)) {
                canClose = false
                barrier = true
            }
        } else {
            canOpen = left
            canClose = right
        }
        // `2*3*4`, `a*b*c`, `x**2 + y**2`: between two ASCII word characters a mark is just a character.
        if (at > 0 && stop < src.length && isAsciiWord(at - 1) && isAsciiWord(stop)) {
            canOpen = false
            canClose = false
        }
        flush()
        nodes += Delim(ch, at, count, count, canOpen, canClose, barrier)
        return stop
    }

    /** CommonMark's process-emphasis: closers look back for the nearest opener of the same mark. */
    private fun pairEmphasis() {
        val bottom = HashMap<String, Int>()
        var ci = 0
        while (ci < nodes.size) {
            val closer = nodes[ci] as? Delim
            if (closer == null || !closer.active || !closer.canClose || closer.count == 0) {
                ci++
                continue
            }
            val key = "${closer.ch}${closer.canOpen}${closer.orig % 3}"
            var oi = ci - 1
            val floor = bottom[key] ?: -1
            var found = -1
            while (oi > floor) {
                val o = nodes[oi] as? Delim
                if (o != null && o.barrier && o.ch == closer.ch) break
                if (o != null && o.active && o.ch == closer.ch && o.canOpen && o.count > 0) {
                    // The rule of three: runs whose lengths sum to a multiple of 3 pair only when both are.
                    val odd =
                        (closer.canOpen || o.canClose) && (o.orig + closer.orig) % 3 == 0 &&
                            !(o.orig % 3 == 0 && closer.orig % 3 == 0)
                    if (!odd) {
                        found = oi
                        break
                    }
                }
                oi--
            }
            if (found < 0) {
                bottom[key] = ci - 1
                if (!closer.canOpen) closer.active = false
                ci++
                continue
            }
            val opener = nodes[found] as Delim
            val use = if (closer.ch == '~') 2 else if (opener.count >= 2 && closer.count >= 2) 2 else 1
            val kind =
                when {
                    closer.ch == '~' -> SpanKind.Strike
                    use == 2 -> SpanKind.Bold
                    else -> SpanKind.Italic
                }
            pairs += Pairing(found, ci, kind)
            opener.count -= use
            closer.count -= use
            for (k in found + 1 until ci) (nodes[k] as? Delim)?.active = false
            if (closer.count == 0) ci++
        }
    }

    // -- Character classes -------------------------------------------------------------------------------------

    private fun isWhitespace(i: Int): Boolean = pictures[i] == null && src[i].isWhitespace()

    private fun isPunctuation(i: Int): Boolean = pictures[i] == null && isPunctuationChar(src[i])

    private fun isAsciiWord(i: Int): Boolean {
        val c = src[i]
        return pictures[i] != null || (c.code < 128 && c.isLetterOrDigit())
    }

    companion object {
        private const val PLACEHOLDER = '￼'
        private const val TRAILING = ".,;:!?'\"*_~"

        fun isAsciiPunctuation(c: Char): Boolean =
            c in '!'..'/' || c in ':'..'@' || c in '['..'`' || c in '{'..'~'

        /** Unicode punctuation and symbols (CommonMark 0.31 "punctuation character"), plus ASCII punctuation. */
        fun isPunctuationChar(c: Char): Boolean {
            if (c.code < 128) return isAsciiPunctuation(c)
            return when (c.category) {
                CharCategory.CONNECTOR_PUNCTUATION,
                CharCategory.DASH_PUNCTUATION,
                CharCategory.START_PUNCTUATION,
                CharCategory.END_PUNCTUATION,
                CharCategory.INITIAL_QUOTE_PUNCTUATION,
                CharCategory.FINAL_QUOTE_PUNCTUATION,
                CharCategory.OTHER_PUNCTUATION,
                CharCategory.MATH_SYMBOL,
                CharCategory.CURRENCY_SYMBOL,
                CharCategory.MODIFIER_SYMBOL,
                CharCategory.OTHER_SYMBOL,
                -> true
                else -> false
            }
        }

        private val named =
            mapOf(
                "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
                "copy" to "©", "reg" to "®", "trade" to "™", "hellip" to "…", "mdash" to "—", "ndash" to "–",
                "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“", "rdquo" to "”", "laquo" to "«", "raquo" to "»",
                "bull" to "•", "middot" to "·", "times" to "×", "divide" to "÷", "deg" to "°", "plusmn" to "±",
                "larr" to "←", "rarr" to "→", "uarr" to "↑", "darr" to "↓", "harr" to "↔", "le" to "≤", "ge" to "≥",
                "ne" to "≠", "euro" to "€", "pound" to "£", "yen" to "¥", "cent" to "¢", "sect" to "§",
                "para" to "¶", "check" to "✓", "hearts" to "♥",
            )

        /** The text an entity name stands for (`amp`, `#35`, `#x22`), or null when it is not one we know. */
        fun decodeEntity(name: String): String? {
            if (name.isEmpty()) return null
            if (name[0] != '#') return named[name]
            val hex = name.length > 1 && (name[1] == 'x' || name[1] == 'X')
            val digits = name.substring(if (hex) 2 else 1)
            if (digits.isEmpty() || digits.length > (if (hex) 6 else 7)) return null
            val radix = if (hex) 16 else 10
            if (digits.any { it.digitToIntOrNull(radix) == null }) return null
            var cp = digits.toInt(radix)
            if (cp == 0 || cp > 0x10FFFF || cp in 0xD800..0xDFFF) cp = 0xFFFD
            if (cp < 0x10000) return cp.toChar().toString()
            val v = cp - 0x10000
            return charArrayOf((0xD800 + (v shr 10)).toChar(), (0xDC00 + (v and 0x3FF)).toChar()).concatToString()
        }
    }
}
