package io.github.eslamasabry.oc.core.common.json

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/** An enum read from a wire string (plan 04 §2.2 `json/OpenEnum.kt`). */
public interface WireEnum {
    public val wire: String
}

/** Values this client does not know read as the [unknown] entry, never an error. Moved from `:protocol:oc2`. */
public open class OpenEnumSerializer<E>(serialName: String, private val entries: List<E>, private val unknown: E) :
    KSerializer<E> where E : Enum<E>, E : WireEnum {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(serialName, PrimitiveKind.STRING)

    public fun fromWire(text: String): E = entries.firstOrNull { it != unknown && it.wire == text } ?: unknown

    /** Dart `parse(v)`: null when [value] is not a string. */
    public fun parseOrNull(value: JsonElement?): E? =
        (value as? JsonPrimitive)?.takeIf { it.isString }?.let { fromWire(it.content) }

    override fun deserialize(decoder: Decoder): E = fromWire(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: E) {
        encoder.encodeString(value.wire)
    }
}
