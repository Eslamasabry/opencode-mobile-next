package io.github.eslamasabry.oc.android.migration.readers.json

/**
 * A small, dependency-free JSON reader and a writer that matches Dart's `jsonEncode` byte for byte for the
 * value types Flutter stores (strings, bools, ints, doubles, lists, maps with string keys).
 *
 * Parsed values: `null`, [Boolean], [Long] (integers that fit), [Double], [String], [List], [LinkedHashMap]
 * (insertion order kept, as Dart's `jsonDecode` does).
 */
internal object MiniJson {

    class ParseException(message: String, public val offset: Int) : Exception(message)

    /** Parse one JSON document. Throws [ParseException]; messages never include input text. */
    fun parse(text: String): Any? {
        val p = P(text)
        p.ws()
        val v = p.value(0)
        p.ws()
        if (p.i != text.length) throw ParseException("trailing content", p.i)
        return v
    }

    fun parseOrNull(text: String): Any? = try {
        parse(text)
    } catch (e: ParseException) {
        null
    }

    private class P(val s: String) {
        var i = 0

        fun ws() {
            while (i < s.length && (s[i] == ' ' || s[i] == '\t' || s[i] == '\n' || s[i] == '\r')) i++
        }

        fun value(depth: Int): Any? {
            if (depth > 512) throw ParseException("too deep", i)
            if (i >= s.length) throw ParseException("unexpected end", i)
            return when (val c = s[i]) {
                '{' -> obj(depth)
                '[' -> arr(depth)
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c in '0'..'9') num() else throw ParseException("unexpected character", i)
            }
        }

        fun lit(word: String, v: Any?): Any? {
            if (!s.startsWith(word, i)) throw ParseException("bad literal", i)
            i += word.length
            return v
        }

        fun obj(depth: Int): LinkedHashMap<String, Any?> {
            i++
            val m = LinkedHashMap<String, Any?>()
            ws()
            if (i < s.length && s[i] == '}') {
                i++
                return m
            }
            while (true) {
                ws()
                if (i >= s.length || s[i] != '"') throw ParseException("expected key", i)
                val k = str()
                ws()
                if (i >= s.length || s[i] != ':') throw ParseException("expected colon", i)
                i++
                ws()
                m[k] = value(depth + 1)
                ws()
                if (i >= s.length) throw ParseException("unexpected end", i)
                when (s[i]) {
                    ',' -> i++
                    '}' -> {
                        i++
                        return m
                    }
                    else -> throw ParseException("expected comma", i)
                }
            }
        }

        fun arr(depth: Int): List<Any?> {
            i++
            val l = ArrayList<Any?>()
            ws()
            if (i < s.length && s[i] == ']') {
                i++
                return l
            }
            while (true) {
                ws()
                l += value(depth + 1)
                ws()
                if (i >= s.length) throw ParseException("unexpected end", i)
                when (s[i]) {
                    ',' -> i++
                    ']' -> {
                        i++
                        return l
                    }
                    else -> throw ParseException("expected comma", i)
                }
            }
        }

        fun str(): String {
            i++
            val sb = StringBuilder()
            while (true) {
                if (i >= s.length) throw ParseException("unterminated string", i)
                val c = s[i++]
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> {
                        if (i >= s.length) throw ParseException("unterminated escape", i)
                        when (s[i++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) throw ParseException("bad unicode escape", i)
                                val code = s.substring(i, i + 4).toIntOrNull(16) ?: throw ParseException("bad unicode escape", i)
                                sb.append(code.toChar())
                                i += 4
                            }
                            else -> throw ParseException("bad escape", i)
                        }
                    }
                    c.code < 0x20 -> throw ParseException("control character in string", i - 1)
                    else -> sb.append(c)
                }
            }
        }

        fun num(): Any {
            val start = i
            if (s[i] == '-') i++
            if (i >= s.length) throw ParseException("bad number", start)
            if (s[i] == '0') i++ else if (s[i] in '1'..'9') while (i < s.length && s[i].isDigit()) i++ else throw ParseException("bad number", start)
            var isDouble = false
            if (i < s.length && s[i] == '.') {
                isDouble = true
                i++
                if (i >= s.length || !s[i].isDigit()) throw ParseException("bad number", start)
                while (i < s.length && s[i].isDigit()) i++
            }
            if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                isDouble = true
                i++
                if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
                if (i >= s.length || !s[i].isDigit()) throw ParseException("bad number", start)
                while (i < s.length && s[i].isDigit()) i++
            }
            val t = s.substring(start, i)
            if (!isDouble) t.toLongOrNull()?.let { return it }
            return t.toDouble()
        }
    }

    /**
     * Dart `jsonEncode` for null, Boolean, Int/Long, Double, String, List, Map<String, *>.
     *
     * String escaping follows dart:convert `_JsonStringifier.writeStringContent`: `\b \t \n \f \r`, `\"`, `\\`,
     * other code units below 0x20 and unpaired surrogates as `\u` + four lowercase hex digits; `/` and other
     * non-ASCII text are written as is. Doubles use Dart's `toString()` rules for the common cases (`1.0`,
     * `0.5`, `1e+21`); see [dartDouble].
     */
    fun encode(value: Any?): String = StringBuilder().also { write(it, value) }.toString()

    private fun write(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is Boolean -> sb.append(v)
            is Int -> sb.append(v)
            is Long -> sb.append(v)
            is Double -> sb.append(dartDouble(v))
            is String -> writeString(sb, v)
            is List<*> -> {
                sb.append('[')
                v.forEachIndexed { idx, e ->
                    if (idx > 0) sb.append(',')
                    write(sb, e)
                }
                sb.append(']')
            }
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, e) in v) {
                    require(k is String) { "JSON object keys must be strings" }
                    if (!first) sb.append(',')
                    first = false
                    writeString(sb, k)
                    sb.append(':')
                    write(sb, e)
                }
                sb.append('}')
            }
            else -> throw IllegalArgumentException("not JSON-encodable: ${v::class.simpleName}")
        }
    }

    fun writeString(sb: StringBuilder, s: String) {
        sb.append('"')
        var idx = 0
        while (idx < s.length) {
            val c = s[idx]
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\b' -> sb.append("\\b")
                c == '\t' -> sb.append("\\t")
                c == '\n' -> sb.append("\\n")
                c == '\u000C' -> sb.append("\\f")
                c == '\r' -> sb.append("\\r")
                c.code < 0x20 -> hex(sb, c)
                Character.isHighSurrogate(c) -> {
                    if (idx + 1 < s.length && Character.isLowSurrogate(s[idx + 1])) {
                        sb.append(c).append(s[idx + 1])
                        idx++
                    } else {
                        hex(sb, c)
                    }
                }
                Character.isLowSurrogate(c) -> hex(sb, c)
                else -> sb.append(c)
            }
            idx++
        }
        sb.append('"')
    }

    private fun hex(sb: StringBuilder, c: Char) {
        sb.append("\\u")
        val h = c.code.toString(16)
        repeat(4 - h.length) { sb.append('0') }
        sb.append(h)
    }

    /**
     * Dart `double.toString()`: shortest round-trip digits; integral values keep `.0`; exponent form
     * (`1e+21`, `1e-7`) outside 1e-6 ≤ |x| < 1e21; `NaN`, `Infinity`. Dart's jsonEncode refuses NaN and
     * infinities, so callers must not pass them.
     */
    fun dartDouble(d: Double): String {
        require(!d.isNaN() && !d.isInfinite()) { "Dart jsonEncode refuses NaN and infinity" }
        if (d == 0.0) return if (1.0 / d < 0) "-0.0" else "0.0"
        val abs = Math.abs(d)
        val bd = java.math.BigDecimal(java.lang.Double.toString(d)).stripTrailingZeros()
        if (abs >= 1e-6 && abs < 1e21) {
            val plain = bd.toPlainString()
            return if (plain.contains('.')) plain else "$plain.0"
        }
        // Exponent form: d.ddd e±x, with no trailing ".0" in the mantissa when it is integral (Dart: "1e+21").
        val unscaled = bd.unscaledValue().abs().toString()
        val exponent = unscaled.length - 1 - bd.scale()
        val mantissa = if (unscaled.length == 1) unscaled else unscaled[0] + "." + unscaled.substring(1)
        val sign = if (d < 0) "-" else ""
        val expSign = if (exponent < 0) "-" else "+"
        return "$sign${mantissa}e$expSign${Math.abs(exponent)}"
    }
}
