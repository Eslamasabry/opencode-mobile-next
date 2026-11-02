package io.github.eslamasabry.oc.engine.agents.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Dart-compatible JSON for the stored formats this engine shares with the Flutter app (gates, receipts, config). Object
 * key order is kept as read; [encode] writes what Dart `jsonEncode` writes for the same value (no spaces, `"`/`\`
 * escaped, control characters as `\b \t \n \f \r` or lowercase `\u00xx`, lone surrogates as `\udxxx`, everything else
 * raw). `:core:data` `DartJson` is the shared version; this local copy exists only while `:core:data` does not compile.
 */
public object AgentJson {
    private val parser = Json { isLenient = false }

    /** Null when [text] is not JSON. Never throws. */
    public fun parseOrNull(text: String?): JsonElement? =
        if (text == null) null else try { parser.parseToJsonElement(text) } catch (_: IllegalArgumentException) { null }

    public fun encode(value: JsonElement): String = StringBuilder().also { write(it, value) }.toString()

    public fun encodeStrings(values: List<String?>): String =
        encode(JsonArray(values.map { if (it == null) JsonNull else JsonPrimitive(it) }))

    private fun write(out: StringBuilder, value: JsonElement) {
        when (value) {
            is JsonNull -> out.append("null")
            is JsonPrimitive -> if (value.isString) writeString(out, value.content) else out.append(value.content)
            is JsonArray -> {
                out.append('[')
                value.forEachIndexed { i, e -> if (i > 0) out.append(','); write(out, e) }
                out.append(']')
            }
            is JsonObject -> {
                out.append('{')
                var first = true
                for ((k, v) in value) {
                    if (!first) out.append(',')
                    first = false
                    writeString(out, k)
                    out.append(':')
                    write(out, v)
                }
                out.append('}')
            }
        }
    }

    private fun writeString(out: StringBuilder, s: String) {
        out.append('"')
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '"' -> out.append("\\\"")
                c == '\\' -> out.append("\\\\")
                c == '\b' -> out.append("\\b")
                c == '\t' -> out.append("\\t")
                c == '\n' -> out.append("\\n")
                c == '\u000c' -> out.append("\\f")
                c == '\r' -> out.append("\\r")
                c < ' ' -> out.append("\\u00").append(hex(c.code shr 4)).append(hex(c.code and 0xf))
                c.isHighSurrogate() && i + 1 < s.length && s[i + 1].isLowSurrogate() -> { out.append(c).append(s[i + 1]); i++ }
                c.isSurrogate() -> out.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                else -> out.append(c)
            }
            i++
        }
        out.append('"')
    }

    private fun hex(n: Int): Char = "0123456789abcdef"[n]
}
