package io.github.eslamasabry.oc.core.common.json

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
 * Tolerant decoding for wire DTOs (plan 04 §2.2 `json/`, §3.7, §7.4). Moved unchanged from `:protocol:oc2` by
 * INTEGRATE-W1 so every protocol reads the server the same way.
 *
 * Servers add and reshape fields between builds, and the Flutter clients read every field through `_asString` /
 * `_asInt` / `_asMap`, so a wrong-typed value reads as absent instead of failing the whole object. [decode] reproduces
 * that with one descriptor-driven pass before the generated serializer runs:
 * - a null or wrong-typed field is dropped, so the property default applies (Dart's `?? default`);
 * - an integer field accepts any JSON number and truncates it toward zero (Dart `num.toInt()`);
 * - a nested object that lacks a required property is dropped (Dart's `fromJson` returning null), and so is such a
 *   list item, along with non-object items in object lists (Dart `_mapList`);
 * - a whole top-level object that lacks a required property decodes to null.
 *
 * Unions and hand-shaped types use a [RawObjectSerializer] (serial name starts with [RAW_PREFIX]); they receive the
 * JSON object untouched and do their own dispatch. Nothing here throws on server input, and nothing echoes it.
 */
public object TolerantJson {
    public const val RAW_PREFIX: String = "wire.raw."
    private const val JSON_ELEMENT = "kotlinx.serialization.json.JsonElement"
    private const val JSON_OBJECT = "kotlinx.serialization.json.JsonObject"
    private const val JSON_ARRAY = "kotlinx.serialization.json.JsonArray"
    private const val JSON_PRIMITIVE = "kotlinx.serialization.json.JsonPrimitive"

    /** Decodes [element] with [deserializer] after the tolerant pass; null when it cannot be read at all. */
    public fun <T> decode(deserializer: DeserializationStrategy<T>, element: JsonElement?, json: Json = WireJson): T? {
        val fitted = fit(element ?: return null, deserializer.descriptor) ?: return null
        return try {
            json.decodeFromJsonElement(deserializer, fitted)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /** Parses [text] as JSON; null when it is not JSON. */
    public fun parse(text: String, json: Json = WireJson): JsonElement? =
        try {
            json.parseToJsonElement(text)
        } catch (_: SerializationException) {
            null
        }

    @OptIn(ExperimentalSerializationApi::class)
    public fun fit(value: JsonElement, descriptor: SerialDescriptor): JsonElement? {
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
    public fun number(value: JsonElement?): Double? =
        (value as? JsonPrimitive)?.takeIf { !it.isString && it.booleanOrNull == null }?.doubleOrNull

    /** Dart `(v as num).toInt()`: any JSON number, truncated toward zero; out-of-range for [kind] reads as absent. */
    public fun integral(value: JsonElement?, kind: SerialKind = PrimitiveKind.LONG): Long? {
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

/** A serializer that takes the JSON object as-is and shapes it by hand (unions, Dart-only rules). JSON only. */
public abstract class RawObjectSerializer<T>(name: String) : KSerializer<T> {
    final override val descriptor: SerialDescriptor = buildClassSerialDescriptor(TolerantJson.RAW_PREFIX + name)

    public abstract fun read(obj: JsonObject): T

    public abstract fun write(value: T): JsonElement

    final override fun deserialize(decoder: Decoder): T {
        val element = (decoder as? JsonDecoder ?: throw SerializationException("JSON only")).decodeJsonElement()
        return read(element as? JsonObject ?: JsonObject(emptyMap()))
    }

    final override fun serialize(encoder: Encoder, value: T) {
        (encoder as? JsonEncoder ?: throw SerializationException("JSON only")).encodeJsonElement(write(value))
    }
}

/** Decodes a wire-shaped surrogate [W] (which the tolerant pass sees) and converts it to the public type [T]. */
public abstract class SurrogateSerializer<T, W>(serialName: String, private val wire: KSerializer<W>) : KSerializer<T> {
    @OptIn(ExperimentalSerializationApi::class)
    final override val descriptor: SerialDescriptor = SerialDescriptor(serialName, wire.descriptor)

    public abstract fun toValue(wire: W): T

    public abstract fun toWire(value: T): W

    final override fun deserialize(decoder: Decoder): T = toValue(decoder.decodeSerializableValue(wire))

    final override fun serialize(encoder: Encoder, value: T) {
        encoder.encodeSerializableValue(wire, toWire(value))
    }
}
