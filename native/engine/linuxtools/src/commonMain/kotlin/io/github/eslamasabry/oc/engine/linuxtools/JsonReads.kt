package io.github.eslamasabry.oc.engine.linuxtools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/** Dart `jsonDecode` without the throw: null when [text] is not JSON. */
internal fun decodeJson(text: String): JsonElement? =
    try {
        Json.parseToJsonElement(text)
    } catch (_: IllegalArgumentException) {
        null // SerializationException is an IllegalArgumentException.
    }

/** Dart `_int`: an int, a number truncated, a numeric string, else 0. */
internal fun JsonElement?.asInt(): Int {
    val primitive = this as? JsonPrimitive ?: return 0
    if (primitive is JsonNull) return 0
    if (primitive.isString) return primitive.content.toIntOrNull() ?: 0
    primitive.content.toLongOrNull()?.let { return it.toInt() }
    return primitive.doubleOrNull?.toInt() ?: 0
}

internal fun JsonElement?.asLong(): Long {
    val primitive = this as? JsonPrimitive ?: return 0
    if (primitive is JsonNull) return 0
    if (primitive.isString) return primitive.content.toLongOrNull() ?: 0
    primitive.content.toLongOrNull()?.let { return it }
    return primitive.doubleOrNull?.toLong() ?: 0
}

/** Dart `raw[k]?.toString()`: null for absent/null, the text of anything else. */
internal fun JsonElement?.asText(): String? =
    when (this) {
        null, is JsonNull -> null
        is JsonPrimitive -> content
        else -> toString()
    }

internal fun JsonElement?.isTrue(): Boolean = (this as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull == true

internal fun JsonElement?.asList(): List<JsonElement> = (this as? JsonArray) ?: emptyList()

internal fun JsonElement?.asMap(): JsonObject? = this as? JsonObject
