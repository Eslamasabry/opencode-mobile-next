package io.github.eslamasabry.oc.protocol.codex

import io.github.eslamasabry.oc.core.common.WireJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * Strict wire readers, ports of the Dart helpers at the top of lib/codex/mappers.dart. Each throws
 * CodexException(InvalidResponse) and never echoes the value.
 */

/** The shared wire JSON instance (never echoes input into exceptions). */
public val CodexWireJson: Json = WireJson

internal val EmptyObject: JsonObject = JsonObject(emptyMap())

public fun codexObject(value: JsonElement?): JsonObject = value as? JsonObject ?: codexFail(CodexFailureKind.InvalidResponse)

public fun codexString(value: JsonElement?, max: Int = 4096, empty: Boolean = false): String {
    val text = value.stringOrNull() ?: codexFail(CodexFailureKind.InvalidResponse)
    if (text.length > max || (!empty && text.isEmpty())) codexFail(CodexFailureKind.InvalidResponse)
    return text
}

/** The same bound on an app-side string. */
public fun codexString(value: String, max: Int = 4096, empty: Boolean = false): String {
    if (value.length > max || (!empty && value.isEmpty())) codexFail(CodexFailureKind.InvalidResponse)
    return value
}

public fun codexList(value: JsonElement?, max: Int = 10_000): JsonArray {
    val list = value as? JsonArray ?: codexFail(CodexFailureKind.InvalidResponse)
    if (list.size > max) codexFail(CodexFailureKind.InvalidResponse)
    return list
}

/** Unix seconds → epoch ms; null stays null; anything else bounded or InvalidResponse. */
public fun codexMillis(value: JsonElement?): Long? {
    if (value == null || value is JsonNull) return null
    val seconds = value.intOrNull() ?: codexFail(CodexFailureKind.InvalidResponse)
    if (seconds < 0 || seconds > 8_640_000_000_000) codexFail(CodexFailureKind.InvalidResponse)
    return seconds * 1000
}

/** A JSON string's content, else null. */
public fun JsonElement?.stringOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

/** A JSON integer (Dart `is int`), else null. */
public fun JsonElement?.intOrNull(): Long? {
    val p = this as? JsonPrimitive ?: return null
    if (p.isString || p is JsonNull) return null
    return if (INTEGER.matches(p.content)) p.content.toLongOrNull() else null
}

/** A JSON boolean, else null. */
public fun JsonElement?.boolOrNull(): Boolean? {
    val p = this as? JsonPrimitive ?: return null
    if (p.isString) return null
    return when (p.content) {
        "true" -> true
        "false" -> false
        else -> null
    }
}

private val INTEGER = Regex("-?\\d{1,18}")

/** Builds a JSON object from Kotlin values (maps, lists, strings, numbers, booleans, null, JSON). */
public fun jsonOf(vararg pairs: Pair<String, Any?>): JsonObject = JsonObject(linkedMapOf(*pairs.map { (k, v) -> k to toJson(v) }.toTypedArray()))

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

/** Encodes [value] as wire text. */
public fun encodeJson(value: JsonElement): String = CodexWireJson.encodeToString(JsonElement.serializer(), value)
