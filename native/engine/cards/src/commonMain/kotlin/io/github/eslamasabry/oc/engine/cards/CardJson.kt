package io.github.eslamasabry.oc.engine.cards

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The plain value tree the validator works on (Dart's `jsonDecode` shape): `Map<String, Any?>` in insertion order,
 * `List<Any?>`, `String`, `Long` (Dart int), `Double`, `Boolean` and `null`. [encode] prints it the way Dart's
 * `jsonEncode` does, byte for byte, because the card revision is the sha256 of that text (plan 09 §13.3): integers
 * without a point, integral doubles with `.0`, JS-style shortest doubles with `e+N`, and Dart's escapes.
 */
public object CardJson {
    private val parser = Json { isLenient = false }

    /** kotlinx JSON → tree. A number with a point or exponent is a double, as Dart's parser reads it. */
    public fun tree(element: JsonElement): Any? =
        when (element) {
            JsonNull -> null
            is JsonObject -> LinkedHashMap<String, Any?>().also { map -> element.forEach { (k, v) -> map[k] = tree(v) } }
            is JsonArray -> element.map(::tree)
            is JsonPrimitive ->
                when {
                    element.isString -> element.content
                    element.content == "true" -> true
                    element.content == "false" -> false
                    else -> number(element.content)
                }
        }

    private fun number(raw: String): Any {
        val integral = raw.none { it == '.' || it == 'e' || it == 'E' }
        if (integral) raw.toLongOrNull()?.let { return it }
        return raw.toDouble()
    }

    /** Parses JSON text into the tree; throws on malformed text (callers treat that as "not a receipt"). */
    public fun parse(text: String): Any? = tree(parser.parseToJsonElement(text))

    /** Dart `jsonEncode`, compact. */
    public fun encode(value: Any?): String = StringBuilder().also { write(it, value) }.toString()

    private fun write(out: StringBuilder, value: Any?) {
        when (value) {
            null -> out.append("null")
            is Boolean -> out.append(value)
            is String -> string(out, value)
            is Int -> out.append(value)
            is Long -> out.append(value)
            is Double -> out.append(dartDouble(value))
            is Float -> out.append(dartDouble(value.toDouble()))
            is Map<*, *> -> {
                out.append('{')
                var first = true
                for ((k, v) in value) {
                    if (!first) out.append(',')
                    first = false
                    string(out, k as String)
                    out.append(':')
                    write(out, v)
                }
                out.append('}')
            }
            is List<*> -> {
                out.append('[')
                value.forEachIndexed { i, v ->
                    if (i > 0) out.append(',')
                    write(out, v)
                }
                out.append(']')
            }
            else -> throw IllegalArgumentException("not JSON")
        }
    }

    private fun string(out: StringBuilder, s: String) {
        out.append('"')
        for (c in s) {
            when {
                c == '"' -> out.append("\\\"")
                c == '\\' -> out.append("\\\\")
                c == '\b' -> out.append("\\b")
                c == '\t' -> out.append("\\t")
                c == '\n' -> out.append("\\n")
                c == '\u000c' -> out.append("\\f")
                c == '\r' -> out.append("\\r")
                c.code < 0x20 -> out.append("\\u00").append(HEX[c.code shr 4]).append(HEX[c.code and 0xf])
                else -> out.append(c)
            }
        }
        out.append('"')
    }

    private const val HEX = "0123456789abcdef"

    /** Dart `double.toString()`: the JS shortest form, plus `.0` when the result reads as an integer. */
    public fun dartDouble(d: Double): String {
        if (d.isNaN()) return "NaN"
        if (d.isInfinite()) return if (d > 0) "Infinity" else "-Infinity"
        if (d == 0.0) return if (1.0 / d < 0) "-0.0" else "0.0"
        val js = jsNumber(d)
        return if (js.any { it == '.' || it == 'e' }) js else "$js.0"
    }

    /** ECMAScript Number::toString for a finite, non-zero double, from the shortest digits the platform prints. */
    private fun jsNumber(d: Double): String {
        val negative = d < 0
        val repr = (if (negative) -d else d).toString() // JDK 19+: the shortest round-tripping digits
        val mantissa = repr.substringBefore('E')
        val exp = if ('E' in repr) repr.substringAfter('E').toInt() else 0
        val intPart = mantissa.substringBefore('.')
        val fracPart = if ('.' in mantissa) mantissa.substringAfter('.') else ""
        var digits = intPart + fracPart
        var n = intPart.length + exp
        val lead = digits.indexOfFirst { it != '0' }
        digits = digits.substring(lead)
        n -= lead
        digits = digits.trimEnd('0')
        val k = digits.length
        val body =
            when {
                n in k..21 -> digits + "0".repeat(n - k)
                n in 1..21 -> digits.substring(0, n) + "." + digits.substring(n)
                n in -5..0 -> "0." + "0".repeat(-n) + digits
                else -> {
                    val e = n - 1
                    val sign = if (e < 0) "-" else "+"
                    val head = if (k == 1) digits else digits[0] + "." + digits.substring(1)
                    "${head}e$sign${kotlin.math.abs(e)}"
                }
            }
        return if (negative) "-$body" else body
    }
}
