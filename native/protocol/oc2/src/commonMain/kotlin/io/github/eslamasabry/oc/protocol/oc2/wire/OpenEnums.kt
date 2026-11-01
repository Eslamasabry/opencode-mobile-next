package io.github.eslamasabry.oc.protocol.oc2.wire

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/** An enum read from a wire string; values this client does not know read as the `Unknown` entry, never an error. */
public interface Oc2WireEnum {
    public val wire: String
}

/** TODO(move-to-core): plan 04 §2.2 `json/OpenEnum.kt` in `:core:common`. */
internal open class OpenEnumSerializer<E>(name: String, private val entries: List<E>, private val unknown: E) :
    KSerializer<E> where E : Enum<E>, E : Oc2WireEnum {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("oc2.enum.$name", PrimitiveKind.STRING)

    fun fromWire(text: String): E = entries.firstOrNull { it != unknown && it.wire == text } ?: unknown

    /** Dart `parse(v)`: null when [value] is not a string. */
    fun parseOrNull(value: JsonElement?): E? =
        (value as? JsonPrimitive)?.takeIf { it.isString }?.let { fromWire(it.content) }

    override fun deserialize(decoder: Decoder): E = fromWire(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: E) {
        encoder.encodeString(value.wire)
    }
}

@Serializable(with = Oc2SessionOutcome.Serializer::class)
public enum class Oc2SessionOutcome(override val wire: String) : Oc2WireEnum {
    Succeeded("succeeded"),
    Failed("failed"),
    Interrupted("interrupted"),
    Unknown("unknown"),
    ;

    internal object Serializer : OpenEnumSerializer<Oc2SessionOutcome>("SessionOutcome", entries, Unknown)

    public companion object {
        /** Null when [value] is not a string; an unknown string is [Unknown]. */
        public fun parse(value: JsonElement?): Oc2SessionOutcome? = Serializer.parseOrNull(value)
    }
}

/** How a prompt joins a running turn: `steer` (now) or `queue` (after it). */
@Serializable(with = Oc2Delivery.Serializer::class)
public enum class Oc2Delivery(override val wire: String) : Oc2WireEnum {
    Steer("steer"),
    Queue("queue"),
    Unknown("unknown"),
    ;

    internal object Serializer : OpenEnumSerializer<Oc2Delivery>("Delivery", entries, Unknown)

    public companion object {
        public fun parse(value: JsonElement?): Oc2Delivery? = Serializer.parseOrNull(value)
    }
}

@Serializable(with = Oc2PermissionEffect.Serializer::class)
public enum class Oc2PermissionEffect(override val wire: String) : Oc2WireEnum {
    Allow("allow"),
    Deny("deny"),
    Ask("ask"),
    Unknown("unknown"),
    ;

    internal object Serializer : OpenEnumSerializer<Oc2PermissionEffect>("PermissionEffect", entries, Unknown)

    public companion object {
        public fun parse(value: JsonElement?): Oc2PermissionEffect? = Serializer.parseOrNull(value)
    }
}

/** Reply to a permission request; [wire] is the body value (beta `reply`, stable `decision`). */
public enum class Oc2PermissionReply(public val wire: String) {
    Once("once"),
    Always("always"),
    Reject("reject"),
}

@Serializable(with = Oc2FormFieldType.Serializer::class)
public enum class Oc2FormFieldType(override val wire: String) : Oc2WireEnum {
    String("string"),
    Number("number"),
    Integer("integer"),
    Boolean("boolean"),
    Multiselect("multiselect"),
    External("external"),
    Unknown("unknown"),
    ;

    internal object Serializer : OpenEnumSerializer<Oc2FormFieldType>("FormFieldType", entries, Unknown)

    public companion object {
        /** Never null: anything this client cannot read is [Unknown] (Dart `Api2FormFieldType.parse`). */
        public fun parse(value: JsonElement?): Oc2FormFieldType = Serializer.parseOrNull(value) ?: Unknown
    }
}

@Serializable(with = Oc2FormStatus.Serializer::class)
public enum class Oc2FormStatus(override val wire: String) : Oc2WireEnum {
    Pending("pending"),
    Answered("answered"),
    Cancelled("cancelled"),
    Unknown("unknown"),
    ;

    internal object Serializer : OpenEnumSerializer<Oc2FormStatus>("FormStatus", entries, Unknown)

    public companion object {
        public fun parse(value: JsonElement?): Oc2FormStatus = Serializer.parseOrNull(value) ?: Unknown
    }
}
