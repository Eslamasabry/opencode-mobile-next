package io.github.eslamasabry.oc.protocol.oc2.wire

import io.github.eslamasabry.oc.core.common.WireJson
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

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
 * Unions and hand-shaped types use serializers whose serial name starts with `oc2.raw.`; they receive the JSON object
 * untouched and do their own dispatch. Nothing here throws on server input.
 *
 * TODO(move-to-core): plan 04 §2.2 places the tolerant helpers (`TolerantListSerializer`, `LenientDoubleSerializer`,
 * `OpenEnum`) in `:core:common` `json/`. They live here until S0-CORE lands them; then this file shrinks to [json].
 */
public object Oc2Wire {
    /** [WireJson] (ignore unknown keys, no explicit nulls, coerced input values). */
    public val json: Json = WireJson

    /** Decodes [element] with [deserializer] after the tolerant pass; null when it cannot be read at all. */
    public fun <T> decode(deserializer: DeserializationStrategy<T>, element: JsonElement?): T? {
        val fitted = Tolerance.fit(element ?: return null, deserializer.descriptor) ?: return null
        return try {
            json.decodeFromJsonElement(deserializer, fitted)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /** Parses [text] as JSON; null when it is not JSON. */
    public fun parse(text: String): JsonElement? =
        try {
            json.parseToJsonElement(text)
        } catch (_: SerializationException) {
            null
        }
}

internal object Tolerance {
    const val RAW_PREFIX = "oc2.raw."
    private const val JSON_ELEMENT = "kotlinx.serialization.json.JsonElement"
    private const val JSON_OBJECT = "kotlinx.serialization.json.JsonObject"
    private const val JSON_ARRAY = "kotlinx.serialization.json.JsonArray"
    private const val JSON_PRIMITIVE = "kotlinx.serialization.json.JsonPrimitive"

    @OptIn(ExperimentalSerializationApi::class)
    fun fit(value: JsonElement, descriptor: SerialDescriptor): JsonElement? {
        if (value is JsonNull) return null
        val name = descriptor.serialName.removeSuffix("?")
        when {
            name.startsWith(RAW_PREFIX) || name == JSON_OBJECT -> return value as? JsonObject
            name == JSON_ELEMENT -> return value
            name == JSON_ARRAY -> return value as? JsonArray
            name == JSON_PRIMITIVE -> return value as? JsonPrimitive
        }
        return when (val kind = descriptor.kind) {
            PrimitiveKind.STRING, SerialKind.ENUM, PrimitiveKind.CHAR -> (value as? JsonPrimitive)?.takeIf { it.isString }
            PrimitiveKind.BOOLEAN -> (value as? JsonPrimitive)?.takeIf { !it.isString && it.booleanOrNull != null }
            PrimitiveKind.INT, PrimitiveKind.LONG, PrimitiveKind.SHORT, PrimitiveKind.BYTE ->
                integral(value, kind)?.let(::JsonPrimitive)
            PrimitiveKind.DOUBLE, PrimitiveKind.FLOAT -> number(value)?.let { value }
            StructureKind.CLASS, StructureKind.OBJECT -> (value as? JsonObject)?.let { fitClass(it, descriptor) }
            StructureKind.LIST -> {
                val item = descriptor.getElementDescriptor(0)
                (value as? JsonArray)?.let { array -> JsonArray(array.mapNotNull { fit(it, item) }) }
            }
            StructureKind.MAP -> value as? JsonObject
            else -> value
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun fitClass(obj: JsonObject, descriptor: SerialDescriptor): JsonObject? {
        val out = LinkedHashMap<String, JsonElement>()
        for (i in 0 until descriptor.elementsCount) {
            val key = descriptor.getElementName(i)
            val fitted = obj[key]?.let { fit(it, descriptor.getElementDescriptor(i)) }
            if (fitted != null) {
                out[key] = fitted
            } else if (!descriptor.isElementOptional(i)) {
                return null
            }
        }
        return JsonObject(out)
    }

    /** A JSON number (not a quoted one); Dart `v is num`. */
    fun number(value: JsonElement?): Double? =
        (value as? JsonPrimitive)?.takeIf { !it.isString && it.booleanOrNull == null }?.doubleOrNull

    /** Dart `(v as num).toInt()`: any JSON number, truncated toward zero; out-of-range for [kind] reads as absent. */
    fun integral(value: JsonElement?, kind: SerialKind = PrimitiveKind.LONG): Long? {
        val primitive = (value as? JsonPrimitive)?.takeIf { !it.isString && it.booleanOrNull == null } ?: return null
        val long = primitive.longOrNull ?: primitive.doubleOrNull?.takeIf { it.isFinite() }?.toLong() ?: return null
        val range =
            when (kind) {
                PrimitiveKind.INT -> Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()
                PrimitiveKind.SHORT -> Short.MIN_VALUE.toLong()..Short.MAX_VALUE.toLong()
                PrimitiveKind.BYTE -> Byte.MIN_VALUE.toLong()..Byte.MAX_VALUE.toLong()
                else -> Long.MIN_VALUE..Long.MAX_VALUE
            }
        return long.takeIf { it in range }
    }
}

// ------------------------------------------------------------------ small readers (Dart _asString/_asInt/_asMap)

internal fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun JsonObject.long(key: String): Long? = Tolerance.integral(this[key])

internal fun JsonObject.int(key: String): Int? = Tolerance.integral(this[key], PrimitiveKind.INT)?.toInt()

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
    val ln = Tolerance.number(left)
    val rn = Tolerance.number(right)
    return if (ln != null && rn != null) ln == rn else left == right
}

// ------------------------------------------------------------------ serializer bases

/** A serializer that takes the JSON object as-is and shapes it by hand (unions, Dart-only rules). JSON only. */
internal abstract class RawObjectSerializer<T>(name: String) : KSerializer<T> {
    final override val descriptor: SerialDescriptor = buildClassSerialDescriptor(Tolerance.RAW_PREFIX + name)

    abstract fun read(obj: JsonObject): T

    abstract fun write(value: T): JsonElement

    final override fun deserialize(decoder: Decoder): T {
        val element = (decoder as? JsonDecoder ?: throw SerializationException("JSON only")).decodeJsonElement()
        return read(element as? JsonObject ?: EmptyObject)
    }

    final override fun serialize(encoder: Encoder, value: T) {
        (encoder as? JsonEncoder ?: throw SerializationException("JSON only")).encodeJsonElement(write(value))
    }
}

/** Decodes a wire-shaped surrogate [W] (which the tolerant pass sees) and converts it to the public type [T]. */
internal abstract class SurrogateSerializer<T, W>(name: String, private val wire: KSerializer<W>) : KSerializer<T> {
    @OptIn(ExperimentalSerializationApi::class)
    final override val descriptor: SerialDescriptor = SerialDescriptor("oc2.$name", wire.descriptor)

    abstract fun toValue(wire: W): T

    abstract fun toWire(value: T): W

    final override fun deserialize(decoder: Decoder): T = toValue(decoder.decodeSerializableValue(wire))

    final override fun serialize(encoder: Encoder, value: T) {
        encoder.encodeSerializableValue(wire, toWire(value))
    }
}
