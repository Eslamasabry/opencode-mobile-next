package io.github.eslamasabry.oc.core.common.dart

/**
 * Byte-exact ports of the Dart encodings that stored keys and digests depend on (plan 05 §2.5).
 *
 * Lives in `:core:common` (plan 05 §2.5; moved unchanged from `:core:data` by INTEGRATE-W1). The Dart golden vectors
 * (`tools/flutter-fixtures/dart_vectors.dart`) are not generated yet; the tests pin the vectors derived from
 * dart:convert / dart:core source by hand.
 *
 * Decoding is a lenient `jsonDecode`: `null`, [Boolean], [Long] (integers that fit), [Double], [String], [List],
 * [LinkedHashMap] (insertion order kept). Copied from `flutter-migration-readers` `MiniJson` (P5).
 */
public object DartJson {

    public class ParseException internal constructor(message: String, public val offset: Int) : Exception(message)

    /** Parse one JSON document. Throws [ParseException]; messages never include input text. */
    public fun decode(text: String): Any? {
        val p = Parser(text)
        p.ws()
        val v = p.value(0)
        p.ws()
        if (p.i != text.length) throw ParseException("trailing content", p.i)
        return v
    }

    public fun decodeOrNull(text: String): Any? =
        try {
            decode(text)
        } catch (e: ParseException) {
            null
        }

    /**
     * dart:convert `jsonEncode` for null, Boolean, Int/Long, Double, String, List, Map<String, *> (insertion order).
     * Escaping follows `_JsonStringifier.writeStringContent`: `\b \t \n \f \r`, `\"`, `\\`, other code units below
     * 0x20 and unpaired surrogates as `\u` + four lowercase hex digits; `/` and non-ASCII are written as is.
     */
    public fun encode(value: Any?): String = StringBuilder().also { write(it, value) }.toString()

    /** Dart `Uri.encodeComponent`: keeps `A-Z a-z 0-9 - _ . ! ~ * ' ( )`, UTF-8 percent-escapes the rest, uppercase hex. */
    public fun encodeComponent(text: String): String {
        val sb = StringBuilder(text.length)
        for (b in utf8(text)) {
            val c = (b.toInt() and 0xFF)
            if (c < 0x80 && isUnreserved(c.toChar())) {
                sb.append(c.toChar())
            } else {
                sb.append('%').append(HEX_UPPER[c shr 4]).append(HEX_UPPER[c and 0xF])
            }
        }
        return sb.toString()
    }

    /**
     * Dart `DateTime.fromMicrosecondsSinceEpoch(us, isUtc: true).toIso8601String()`: `yyyy-MM-ddTHH:mm:ss.mmmZ`, with six
     * fraction digits when the microseconds are not zero; years outside 0..9999 as a sign and six digits.
     */
    public fun iso8601Utc(epochMicros: Long): String {
        val days = floorDiv(epochMicros, MICROS_PER_DAY)
        val inDay = epochMicros - days * MICROS_PER_DAY
        val (y, m, d) = civilFromDays(days)
        val micros = (inDay % 1_000).toInt()
        val millis = ((inDay / 1_000) % 1_000).toInt()
        val totalSeconds = inDay / 1_000_000
        val sec = (totalSeconds % 60).toInt()
        val min = ((totalSeconds / 60) % 60).toInt()
        val hour = (totalSeconds / 3600).toInt()
        val year = if (y in 0..9999) pad(y.toLong(), 4) else (if (y < 0) "-" else "+") + pad(kotlin.math.abs(y).toLong(), 6)
        val frac = pad(millis.toLong(), 3) + if (micros != 0) pad(micros.toLong(), 3) else ""
        return "$year-${pad(m.toLong(), 2)}-${pad(d.toLong(), 2)}T${pad(hour.toLong(), 2)}:${pad(min.toLong(), 2)}:" +
            "${pad(sec.toLong(), 2)}.${frac}Z"
    }

    /** Lowercase hex SHA-256 of the UTF-8 bytes (Dart `sha256.convert(utf8.encode(s)).toString()`). */
    public fun sha256Hex(text: String): String = Sha256.hex(utf8(text))

    /** Lowercase hex SHA-256 of [bytes]. Pure Kotlin until plan 03 §10.1's `expect class Sha256` lands. */
    public fun sha256Hex(bytes: ByteArray): String = Sha256.hex(bytes)

    /** Dart `utf8.encode`: an unpaired surrogate becomes U+FFFD (`EF BF BD`), unlike the JVM's `?`. */
    public fun utf8(text: String): ByteArray {
        val out = ArrayList<Byte>(text.length + 8)
        fun put(v: Int) = out.add(v.toByte())
        var i = 0
        while (i < text.length) {
            val c = text[i]
            var cp = c.code
            if (c.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()) {
                cp = 0x10000 + ((c.code - 0xD800) shl 10) + (text[i + 1].code - 0xDC00)
                i++
            } else if (c.isSurrogate()) {
                cp = 0xFFFD
            }
            when {
                cp < 0x80 -> put(cp)
                cp < 0x800 -> { put(0xC0 or (cp shr 6)); put(0x80 or (cp and 0x3F)) }
                cp < 0x10000 -> {
                    put(0xE0 or (cp shr 12)); put(0x80 or ((cp shr 6) and 0x3F)); put(0x80 or (cp and 0x3F))
                }
                else -> {
                    put(0xF0 or (cp shr 18)); put(0x80 or ((cp shr 12) and 0x3F))
                    put(0x80 or ((cp shr 6) and 0x3F)); put(0x80 or (cp and 0x3F))
                }
            }
            i++
        }
        return out.toByteArray()
    }

    /**
     * Dart `double.toString()` as `jsonEncode` writes it: integral values keep `.0`; exponent form (`1e+21`, `1e-7`)
     * outside 1e-6 ≤ |x| < 1e21. Digits come from the platform's shortest repr ([U] on JDK 17, whose `Double.toString`
     * is not always shortest; Dart's golden vectors will pin it). NaN and infinities are refused, as Dart does.
     */
    public fun dartDouble(d: Double): String {
        require(!d.isNaN() && !d.isInfinite()) { "Dart jsonEncode refuses NaN and infinity" }
        if (d == 0.0) return if (1.0 / d < 0) "-0.0" else "0.0"
        val (digits, pointPos) = decimalDigits(kotlin.math.abs(d))
        val sign = if (d < 0) "-" else ""
        val abs = kotlin.math.abs(d)
        return if (abs >= 1e-6 && abs < 1e21) {
            sign + when {
                pointPos <= 0 -> "0." + "0".repeat(-pointPos) + digits
                pointPos >= digits.length -> digits + "0".repeat(pointPos - digits.length) + ".0"
                else -> digits.substring(0, pointPos) + "." + digits.substring(pointPos)
            }
        } else {
            val exp = pointPos - 1
            val mantissa = if (digits.length == 1) digits else digits[0] + "." + digits.substring(1)
            "$sign${mantissa}e${if (exp < 0) "-" else "+"}${kotlin.math.abs(exp)}"
        }
    }

    // ---- internals ----

    private const val MICROS_PER_DAY = 86_400_000_000L
    private const val HEX_UPPER = "0123456789ABCDEF"

    private fun isUnreserved(c: Char): Boolean =
        c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c in "-_.!~*'()"

    private fun pad(v: Long, width: Int): String = v.toString().padStart(width, '0')

    private fun floorDiv(a: Long, b: Long): Long {
        val q = a / b
        return if ((a % b != 0L) && ((a < 0) != (b < 0))) q - 1 else q
    }

    /** Howard Hinnant's days-from-civil inverse. */
    private fun civilFromDays(z0: Long): Triple<Int, Int, Int> {
        val z = z0 + 719_468
        val era = (if (z >= 0) z else z - 146_096) / 146_097
        val doe = z - era * 146_097
        val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
        val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
        val y = (yoe + era * 400 + if (m <= 2) 1 else 0).toInt()
        return Triple(y, m, d)
    }

    /** Significant digits (no leading/trailing zeros) and the decimal point position, from the platform repr. */
    private fun decimalDigits(abs: Double): Pair<String, Int> {
        val s = abs.toString()
        val eAt = s.indexOfFirst { it == 'E' || it == 'e' }
        val mantissa = if (eAt >= 0) s.substring(0, eAt) else s
        val exp = if (eAt >= 0) s.substring(eAt + 1).toInt() else 0
        val dot = mantissa.indexOf('.')
        val intPart = if (dot >= 0) mantissa.substring(0, dot) else mantissa
        val fracPart = if (dot >= 0) mantissa.substring(dot + 1) else ""
        var all = intPart + fracPart
        var point = intPart.length + exp
        val lead = all.indexOfFirst { it != '0' }
        all = all.substring(lead)
        point -= lead
        all = all.trimEnd('0')
        return all to point
    }

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

    private fun writeString(sb: StringBuilder, s: String) {
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
                c.isHighSurrogate() ->
                    if (idx + 1 < s.length && s[idx + 1].isLowSurrogate()) {
                        sb.append(c).append(s[idx + 1])
                        idx++
                    } else {
                        hex(sb, c)
                    }
                c.isLowSurrogate() -> hex(sb, c)
                else -> sb.append(c)
            }
            idx++
        }
        sb.append('"')
    }

    private fun hex(sb: StringBuilder, c: Char) {
        sb.append("\\u").append(c.code.toString(16).padStart(4, '0'))
    }

    private class Parser(val s: String) {
        var i = 0

        fun ws() {
            while (i < s.length && (s[i] == ' ' || s[i] == '\t' || s[i] == '\n' || s[i] == '\r')) i++
        }

        fun value(depth: Int): Any? {
            if (depth > MAX_DEPTH) throw ParseException("too deep", i)
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

        @Suppress("CyclomaticComplexMethod")
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
            when {
                s[i] == '0' -> i++
                s[i] in '1'..'9' -> while (i < s.length && s[i].isAsciiDigit()) i++
                else -> throw ParseException("bad number", start)
            }
            var isDouble = false
            if (i < s.length && s[i] == '.') {
                isDouble = true
                i++
                if (i >= s.length || !s[i].isAsciiDigit()) throw ParseException("bad number", start)
                while (i < s.length && s[i].isAsciiDigit()) i++
            }
            if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                isDouble = true
                i++
                if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
                if (i >= s.length || !s[i].isAsciiDigit()) throw ParseException("bad number", start)
                while (i < s.length && s[i].isAsciiDigit()) i++
            }
            val t = s.substring(start, i)
            if (!isDouble) t.toLongOrNull()?.let { return it }
            return t.toDouble()
        }

        private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'

        private companion object {
            const val MAX_DEPTH = 512
        }
    }
}

/** FIPS 180-4 SHA-256, so the digest is the same on every platform without a crypto dependency in common code. */
internal object Sha256 {
    private val K: IntArray = longArrayOf(
        0x428a2f98L, 0x71374491L, 0xb5c0fbcfL, 0xe9b5dba5L, 0x3956c25bL, 0x59f111f1L,
        0x923f82a4L, 0xab1c5ed5L, 0xd807aa98L, 0x12835b01L, 0x243185beL, 0x550c7dc3L,
        0x72be5d74L, 0x80deb1feL, 0x9bdc06a7L, 0xc19bf174L, 0xe49b69c1L, 0xefbe4786L,
        0x0fc19dc6L, 0x240ca1ccL, 0x2de92c6fL, 0x4a7484aaL, 0x5cb0a9dcL, 0x76f988daL,
        0x983e5152L, 0xa831c66dL, 0xb00327c8L, 0xbf597fc7L, 0xc6e00bf3L, 0xd5a79147L,
        0x06ca6351L, 0x14292967L, 0x27b70a85L, 0x2e1b2138L, 0x4d2c6dfcL, 0x53380d13L,
        0x650a7354L, 0x766a0abbL, 0x81c2c92eL, 0x92722c85L, 0xa2bfe8a1L, 0xa81a664bL,
        0xc24b8b70L, 0xc76c51a3L, 0xd192e819L, 0xd6990624L, 0xf40e3585L, 0x106aa070L,
        0x19a4c116L, 0x1e376c08L, 0x2748774cL, 0x34b0bcb5L, 0x391c0cb3L, 0x4ed8aa4aL,
        0x5b9cca4fL, 0x682e6ff3L, 0x748f82eeL, 0x78a5636fL, 0x84c87814L, 0x8cc70208L,
        0x90befffaL, 0xa4506cebL, 0xbef9a3f7L, 0xc67178f2L,
    ).let { a -> IntArray(a.size) { a[it].toInt() } }

    fun hex(message: ByteArray): String {
        val h = longArrayOf(
            0x6a09e667L, 0xbb67ae85L, 0x3c6ef372L, 0xa54ff53aL, 0x510e527fL, 0x9b05688cL,
            0x1f83d9abL, 0x5be0cd19L,
        ).let { a -> IntArray(a.size) { a[it].toInt() } }
        val bitLen = message.size.toLong() * 8
        val padded = ByteArray(((message.size + 9 + 63) / 64) * 64)
        message.copyInto(padded)
        padded[message.size] = 0x80.toByte()
        for (k in 0 until 8) padded[padded.size - 1 - k] = (bitLen ushr (8 * k)).toByte()
        val w = IntArray(64)
        for (chunk in padded.indices step 64) {
            for (t in 0 until 16) {
                val o = chunk + t * 4
                w[t] = ((padded[o].toInt() and 0xFF) shl 24) or ((padded[o + 1].toInt() and 0xFF) shl 16) or
                    ((padded[o + 2].toInt() and 0xFF) shl 8) or (padded[o + 3].toInt() and 0xFF)
            }
            for (t in 16 until 64) {
                val s0 = w[t - 15].rotateRight(7) xor w[t - 15].rotateRight(18) xor (w[t - 15] ushr 3)
                val s1 = w[t - 2].rotateRight(17) xor w[t - 2].rotateRight(19) xor (w[t - 2] ushr 10)
                w[t] = w[t - 16] + s0 + w[t - 7] + s1
            }
            var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]
            var e = h[4]; var f = h[5]; var g = h[6]; var hh = h[7]
            for (t in 0 until 64) {
                val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
                val ch = (e and f) xor (e.inv() and g)
                val t1 = hh + s1 + ch + K[t] + w[t]
                val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
                val maj = (a and b) xor (a and c) xor (b and c)
                val t2 = s0 + maj
                hh = g; g = f; f = e; e = d + t1; d = c; c = b; b = a; a = t1 + t2
            }
            h[0] += a; h[1] += b; h[2] += c; h[3] += d; h[4] += e; h[5] += f; h[6] += g; h[7] += hh
        }
        val sb = StringBuilder(64)
        for (v in h) sb.append((v.toLong() and 0xFFFFFFFFL).toString(16).padStart(8, '0'))
        return sb.toString()
    }
}
