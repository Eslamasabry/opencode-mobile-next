package io.github.eslamasabry.oc.protocol.oc2.wire

import io.github.eslamasabry.oc.core.common.json.OpenEnumSerializer
import io.github.eslamasabry.oc.core.common.json.WireEnum
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable(with = Oc2SessionOutcome.Serializer::class)
public enum class Oc2SessionOutcome(override val wire: String) : WireEnum {
    Succeeded("succeeded"),
    Failed("failed"),
    Interrupted("interrupted"),
    Unknown("unknown"),
    ;

    internal object Serializer : OpenEnumSerializer<Oc2SessionOutcome>("oc2.enum.SessionOutcome", entries, Unknown)

    public companion object {
        /** Null when [value] is not a string; an unknown string is [Unknown]. */
        public fun parse(value: JsonElement?): Oc2SessionOutcome? = Serializer.parseOrNull(value)
    }
}

/** How a prompt joins a running turn: `steer` (now) or `queue` (after it). */
@Serializable(with = Oc2Delivery.Serializer::class)
public enum class Oc2Delivery(override val wire: String) : WireEnum {
    Steer("steer"),
    Queue("queue"),
    Unknown("unknown"),
    ;

    internal object Serializer : OpenEnumSerializer<Oc2Delivery>("oc2.enum.Delivery", entries, Unknown)

    public companion object {
        public fun parse(value: JsonElement?): Oc2Delivery? = Serializer.parseOrNull(value)
    }
}

@Serializable(with = Oc2PermissionEffect.Serializer::class)
public enum class Oc2PermissionEffect(override val wire: String) : WireEnum {
    Allow("allow"),
    Deny("deny"),
    Ask("ask"),
    Unknown("unknown"),
    ;

    internal object Serializer : OpenEnumSerializer<Oc2PermissionEffect>("oc2.enum.PermissionEffect", entries, Unknown)

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
public enum class Oc2FormFieldType(override val wire: String) : WireEnum {
    String("string"),
    Number("number"),
    Integer("integer"),
    Boolean("boolean"),
    Multiselect("multiselect"),
    External("external"),
    Unknown("unknown"),
    ;

    internal object Serializer : OpenEnumSerializer<Oc2FormFieldType>("oc2.enum.FormFieldType", entries, Unknown)

    public companion object {
        /** Never null: anything this client cannot read is [Unknown] (Dart `Api2FormFieldType.parse`). */
        public fun parse(value: JsonElement?): Oc2FormFieldType = Serializer.parseOrNull(value) ?: Unknown
    }
}

@Serializable(with = Oc2FormStatus.Serializer::class)
public enum class Oc2FormStatus(override val wire: String) : WireEnum {
    Pending("pending"),
    Answered("answered"),
    Cancelled("cancelled"),
    Unknown("unknown"),
    ;

    internal object Serializer : OpenEnumSerializer<Oc2FormStatus>("oc2.enum.FormStatus", entries, Unknown)

    public companion object {
        public fun parse(value: JsonElement?): Oc2FormStatus = Serializer.parseOrNull(value) ?: Unknown
    }
}
