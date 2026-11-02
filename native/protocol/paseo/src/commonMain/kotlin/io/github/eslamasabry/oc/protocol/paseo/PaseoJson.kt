package io.github.eslamasabry.oc.protocol.paseo

import io.github.eslamasabry.oc.core.common.WireJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.time.Instant

/*
 * Wire readers, ports of the Dart helpers at the top of lib/paseo/mappers.dart. The strict ones throw
 * PaseoException(InvalidResponse); the lenient ones (text, millis) clip or blank display-only values.
 */

/** The shared JSON instance (never echoes input into exceptions). */
public val PaseoWireJson: kotlinx.serialization.json.Json = WireJson

internal val EmptyObject: JsonObject = JsonObject(emptyMap())

/** `value as JsonObject`, else InvalidResponse. */
public fun paseoObject(value: JsonElement?): JsonObject = value as? JsonObject ?: paseoFail(PaseoFailureKind.InvalidResponse)

/** A bounded string; empty only when [empty]. */
public fun paseoString(value: JsonElement?, max: Int = 4096, empty: Boolean = false): String {
    val text = value.stringOrNull() ?: paseoFail(PaseoFailureKind.InvalidResponse)
    if (text.length > max || (!empty && text.isEmpty())) paseoFail(PaseoFailureKind.InvalidResponse)
    return text
}

/** A bounded app-side string (Dart `paseoString` on a value the app holds). */
public fun paseoString(value: String, max: Int = 4096, empty: Boolean = false): String {
    if (value.length > max || (!empty && value.isEmpty())) paseoFail(PaseoFailureKind.InvalidResponse)
    return value
}

public fun paseoList(value: JsonElement?, max: Int = 10_000): JsonArray {
    val list = value as? JsonArray ?: paseoFail(PaseoFailureKind.InvalidResponse)
    if (list.size > max) paseoFail(PaseoFailureKind.InvalidResponse)
    return list
}

public const val PASEO_TEXT_MAX: Int = 4 * 1024 * 1024

/** Lenient text: missing → "", a non-string → its JSON text, oversized → clipped. */
public fun paseoText(value: JsonElement?, max: Int = PASEO_TEXT_MAX): String {
    val text = when (value) {
        null, JsonNull -> return ""
        is JsonPrimitive -> if (value.isString) value.content else value.toString()
        else -> value.toString()
    }
    return if (text.length > max) text.substring(0, max) else text
}

/** ISO-8601 → epoch ms, null when missing, oversized or unreadable (Dart `DateTime.tryParse`). */
public fun paseoMillis(value: JsonElement?): Long? {
    val text = value.stringOrNull() ?: return null
    if (text.length > 64) return null
    return parseInstantMillis(text)
}

internal fun parseInstantMillis(text: String): Long? =
    try {
        Instant.parse(text).toEpochMilliseconds()
    } catch (_: IllegalArgumentException) {
        null
    }

/** Reads the daemon's [reply] with [read]; an unreadable part names the reply (never its content) in the reason. */
public inline fun <T> paseoReply(reply: String, read: () -> T): T =
    try {
        read()
    } catch (error: PaseoException) {
        if (error.kind != PaseoFailureKind.InvalidResponse || error.reason != null) throw error
        throw PaseoException(PaseoFailureKind.InvalidResponse, reply)
    }

// ---- small tolerant accessors

public fun JsonElement?.stringOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

public fun JsonElement?.objectOrNull(): JsonObject? = this as? JsonObject

public fun JsonElement?.arrayOrNull(): JsonArray? = this as? JsonArray

public fun JsonElement?.boolOrNull(): Boolean? = (this as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull

/** A JSON number (not a string) as a Long, when it is integral. */
public fun JsonElement?.longOrNull(): Long? =
    (this as? JsonPrimitive)?.takeIf { !it.isString && it !is JsonNull }?.let { it.longOrNull ?: it.doubleOrNull?.takeIf { d -> d % 1.0 == 0.0 }?.toLong() }

public fun JsonElement?.numberOrNull(): Double? = (this as? JsonPrimitive)?.takeIf { !it.isString && it !is JsonNull }?.doubleOrNull

public fun JsonObject.str(key: String): String? = this[key].stringOrNull()

public fun JsonObject.obj(key: String): JsonObject? = this[key].objectOrNull()

public fun JsonObject.arr(key: String): JsonArray? = this[key].arrayOrNull()

/** Builds a JSON object from Kotlin values (String, Number, Boolean, null, JsonElement, Map, List). */
public fun jsonOf(vararg pairs: Pair<String, Any?>): JsonObject = JsonObject(pairs.associate { (k, v) -> k to toJson(v) })

public fun toJson(value: Any?): JsonElement =
    when (value) {
        null -> JsonNull
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to toJson(v) })
        is Iterable<*> -> JsonArray(value.map(::toJson))
        is Array<*> -> JsonArray(value.map(::toJson))
        else -> JsonPrimitive(value.toString())
    }

/** [this] plus [extra] (later keys win). */
public operator fun JsonObject.plus(extra: Map<String, JsonElement>): JsonObject = JsonObject(LinkedHashMap(this).apply { putAll(extra) })
