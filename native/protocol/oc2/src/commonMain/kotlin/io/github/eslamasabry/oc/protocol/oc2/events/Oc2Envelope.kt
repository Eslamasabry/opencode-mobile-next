package io.github.eslamasabry.oc.protocol.oc2.events

import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Location
import io.github.eslamasabry.oc.protocol.oc2.wire.Oc2Wire
import io.github.eslamasabry.oc.protocol.oc2.wire.long
import io.github.eslamasabry.oc.protocol.oc2.wire.obj
import io.github.eslamasabry.oc.protocol.oc2.wire.str
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** The `durable` marker of event-sourced events; [seq] is the per-session cursor for `?after=` on the durable log. */
@Serializable
public data class Oc2Durable(
    public val aggregateID: String? = null,
    public val seq: Long? = null,
    public val version: Long? = null,
)

/**
 * The wire envelope `{id, created, type, location?, metadata?, durable?, data}`. [data] is the `data` object, or the
 * whole envelope when there is none (the bare `log.synced` marker). Deltas and `tool.progress` carry no [durable].
 */
public data class Oc2Envelope(
    public val id: String?,
    public val created: Long?,
    public val type: String,
    public val location: Oc2Location?,
    public val metadata: JsonObject?,
    public val durable: Oc2Durable?,
    public val data: JsonObject,
    public val event: Oc2Event,
) {
    public val isDurable: Boolean get() = durable != null

    public companion object {
        private val Empty = JsonObject(emptyMap())

        /** Never throws: a non-object reads as an empty envelope with an [Oc2Event.Unknown] event. */
        public fun decode(element: JsonElement): Oc2Envelope {
            val envelope = element as? JsonObject ?: Empty
            val type = envelope.str("type") ?: ""
            val data = envelope.obj("data") ?: envelope
            return Oc2Envelope(
                id = envelope.str("id"),
                created = envelope.long("created"),
                type = type,
                location = Oc2Location.fromJson(envelope["location"]),
                metadata = envelope.obj("metadata"),
                durable = Oc2Wire.decode(Oc2Durable.serializer(), envelope["durable"]),
                data = data,
                event = Oc2EventRegistry.decode(type, data),
            )
        }

        /** One SSE `data:` payload. Text that is not JSON reads as an empty envelope with an Unknown event. */
        public fun parse(text: String): Oc2Envelope = decode(Oc2Wire.parse(text) ?: Empty)
    }
}
