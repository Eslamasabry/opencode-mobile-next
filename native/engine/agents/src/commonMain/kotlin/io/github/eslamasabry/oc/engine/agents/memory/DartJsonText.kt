package io.github.eslamasabry.oc.engine.agents.memory

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Dart `jsonEncode` / `jsonDecode` byte compatibility for the values this engine stores (plan 05 §2.5). `:core:data`
 * `DartJson` is the shared one; it is not usable while `:core:data` does not compile, so this module keeps a small
 * equivalent. TODO(move-to-core): replace with DartJson when the dependency is allowed.
 *
 * Dart's encoder: no whitespace, map keys in insertion order, `"` and `\` escaped, control characters below 0x20 as
 * `\b \t \n \f \r` or `\u00xx` (lowercase hex), nothing else escaped (not `/`, not non-ASCII).
 */
internal object DartJsonText {
    fun encode(value: Any?): String = StringBuilder().also { write(it, value) }.toString()

    private fun write(out: StringBuilder, value: Any?) {
        when (value) {
            null -> out.append("null")
            is String -> string(out, value)
            is Boolean -> out.append(value)
            is Int, is Long -> out.append(value.toString())
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
            is Iterable<*> -> {
                out.append('[')
                var first = true
                for (v in value) {
                    if (!first) out.append(',')
                    first = false
                    write(out, v)
                }
                out.append(']')
            }
            else -> throw IllegalArgumentException("Unsupported JSON value")
        }
    }

    private fun string(out: StringBuilder, text: String) {
        out.append('"')
        for (c in text) {
            when {
                c == '"' -> out.append("\\\"")
                c == '\\' -> out.append("\\\\")
                c == '\b' -> out.append("\\b")
                c == '\t' -> out.append("\\t")
                c == '\n' -> out.append("\\n")
                c == '\u000C' -> out.append("\\f")
                c == '\r' -> out.append("\\r")
                c.code < 0x20 -> out.append("\\u00").append(c.code.toString(16).padStart(2, '0'))
                else -> out.append(c)
            }
        }
        out.append('"')
    }

    /** Null for text Dart's `jsonDecode` would refuse. */
    fun decode(text: String): JsonElement? =
        try {
            Json.parseToJsonElement(text)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    /** A Dart `String` value of [key], else null. */
    fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

    /** A Dart `int` value (`is int`): an integer literal, never `1.0` or a string. */
    fun JsonObject.int(key: String): Long? {
        val p = get(key) as? JsonPrimitive ?: return null
        if (p.isString || p is JsonNull) return null
        if (p.content.any { it == '.' || it == 'e' || it == 'E' }) return null
        return p.longOrNull
    }

    /** Dart `value == true`. */
    fun JsonObject.isTrue(key: String): Boolean {
        val p = get(key) as? JsonPrimitive ?: return false
        return !p.isString && p.booleanOrNull == true
    }
}
