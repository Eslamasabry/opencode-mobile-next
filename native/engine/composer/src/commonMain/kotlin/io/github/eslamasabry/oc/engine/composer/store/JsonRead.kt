package io.github.eslamasabry.oc.engine.composer.store

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/** Thrown for a stored value the Dart reader would also have refused (`as String` on a wrong type, FormatException). */
internal class StoredFormatException(reason: String) : Exception(reason)

internal val ComposerJson: Json = Json { isLenient = false }

internal fun parseJson(raw: String): JsonElement =
    try {
        ComposerJson.parseToJsonElement(raw)
    } catch (e: IllegalArgumentException) {
        throw StoredFormatException("Not JSON")
    }

internal fun JsonElement?.asObject(): JsonObject = this as? JsonObject ?: throw StoredFormatException("Not an object")

internal fun JsonElement?.asArray(): JsonArray = this as? JsonArray ?: throw StoredFormatException("Not a list")

/** Dart `value as String`. */
internal fun JsonObject.str(key: String): String {
    val v = this[key]
    return if (v is JsonPrimitive && v.isString) v.content else throw StoredFormatException("$key is not text")
}

/** Dart `value as String?`. */
internal fun JsonObject.strOrNull(key: String): String? =
    when (val v = this[key]) {
        null, JsonNull -> null
        is JsonPrimitive -> if (v.isString) v.content else throw StoredFormatException("$key is not text")
        else -> throw StoredFormatException("$key is not text")
    }

/** Dart `value?.toString()` for loosely typed ids. */
internal fun JsonObject.loose(key: String): String? =
    when (val v = this[key]) {
        null, JsonNull -> null
        is JsonPrimitive -> v.content
        else -> v.toString()
    }

/** Dart `(value as num?)?.toInt()`. */
internal fun JsonObject.longOrNull(key: String): Long? =
    when (val v = this[key]) {
        null, JsonNull -> null
        is JsonPrimitive ->
            if (v.isString) throw StoredFormatException("$key is not a number")
            else v.longOrNull ?: v.doubleOrNull?.toLong() ?: throw StoredFormatException("$key is not a number")
        else -> throw StoredFormatException("$key is not a number")
    }

internal fun JsonObject.boolOrNull(key: String): Boolean? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull

internal fun jsonString(value: String?): JsonElement = if (value == null) JsonNull else JsonPrimitive(value)
