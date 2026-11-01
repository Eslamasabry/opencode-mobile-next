package io.github.eslamasabry.oc.protocol.oc2.wire

import io.github.eslamasabry.oc.core.common.WireJson
import io.github.eslamasabry.oc.core.common.json.RawObjectSerializer
import io.github.eslamasabry.oc.core.common.json.TolerantJson
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * Tolerant decoding for OpenCode 2 wire DTOs (plan 04 §3.7, §7.4).
 *
 * The beta server adds and reshapes fields between builds, and the Flutter client (`lib/api2/models.dart`) reads every
 * field through `_asString` / `_asInt` / `_asMap`, so a wrong-typed value reads as absent instead of failing the whole
 * object. [decode] reproduces that with one descriptor-driven pass before the generated serializer runs:
 * - a null or wrong-typed field is dropped, so the property default applies (Dart's `?? default`);
 * - an integer field accepts any JSON number and truncates it toward zero (Dart `num.toInt()`);
 * - a nested object that lacks a required property is dropped (Dart's `fromJson` returning null), and so is such a
 *   list item, along with non-object items in object lists (Dart `_mapList`);
 * - a whole top-level object that lacks a required property decodes to null.
 *
 * The machinery ([TolerantJson], [RawObjectSerializer], `SurrogateSerializer`, `OpenEnumSerializer`) lives in
 * `:core:common` `json/` (plan 04 §2.2; moved there by INTEGRATE-W1). This file keeps the OC2 entry point and the
 * Dart-style readers.
 */
public object Oc2Wire {
    /** [WireJson] (ignore unknown keys, no explicit nulls, coerced input values). */
    public val json: Json = WireJson

    /** Decodes [element] with [deserializer] after the tolerant pass; null when it cannot be read at all. */
    public fun <T> decode(deserializer: DeserializationStrategy<T>, element: JsonElement?): T? =
        TolerantJson.decode(deserializer, element, json)

    /** Parses [text] as JSON; null when it is not JSON. */
    public fun parse(text: String): JsonElement? = TolerantJson.parse(text, json)
}

// ------------------------------------------------------------------ small readers (Dart _asString/_asInt/_asMap)

internal fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun JsonObject.long(key: String): Long? = TolerantJson.integral(this[key])

internal fun JsonObject.int(key: String): Int? = TolerantJson.integral(this[key], PrimitiveKind.INT)?.toInt()

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.bool(key: String): Boolean? =
    (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull

/** Dart `_nonBlank`: trimmed text, or null when it is not a string or holds only whitespace. */
internal fun JsonObject.nonBlank(key: String): String? = str(key)?.trim()?.takeIf { it.isNotEmpty() }

internal val EmptyObject: JsonObject = JsonObject(emptyMap())

/** Dart `v?.toString()` on a decoded JSON value: strings as-is, other primitives by their literal, null for null. */
internal fun JsonElement?.dartToString(): String? =
    when (this) {
        null, JsonNull -> null
        is JsonPrimitive -> content
        else -> toString()
    }

/** Dart `==` on decoded JSON values: numbers compare by value (1 == 1.0), everything else structurally. */
internal fun jsonValueEquals(a: JsonElement?, b: JsonElement?): Boolean {
    val left = a ?: JsonNull
    val right = b ?: JsonNull
    val ln = TolerantJson.number(left)
    val rn = TolerantJson.number(right)
    return if (ln != null && rn != null) ln == rn else left == right
}
