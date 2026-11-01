package io.github.eslamasabry.oc.protocol.oc2.wire

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// Ported from lib/api2/models.dart (sessions). Ids are strings; times are epoch milliseconds.

@Serializable
public data class Oc2SessionTime(
    public val created: Long? = null,
    public val updated: Long? = null,
    public val idle: Long? = null,
    public val viewed: Long? = null,
    public val archived: Long? = null,
)

/** `{type: "before"|"through", messageID?}`. */
@Serializable
public data class Oc2ForkBoundary(public val type: String, public val messageID: String? = null)

@Serializable
public data class Oc2Fork(public val sessionID: String? = null, public val boundary: Oc2ForkBoundary? = null)

/** A staged revert. Only a non-empty `messageID` makes one. */
@Serializable
public data class Oc2SessionRevert(
    public val messageID: String,
    public val partID: String? = null,
    public val snapshot: String? = null,
    public val files: List<JsonObject>? = null,
) {
    public companion object {
        public fun fromJson(element: JsonElement?): Oc2SessionRevert? =
            Oc2Wire.decode(serializer(), element)?.takeIf { it.messageID.isNotEmpty() }
    }
}

@Serializable
public data class Oc2Session(
    public val id: String,
    public val parentID: String? = null,
    public val fork: Oc2Fork? = null,
    public val projectID: String? = null,
    public val agent: String? = null,
    public val model: Oc2ModelRef? = null,
    public val cost: Double = 0.0,
    public val tokens: Oc2Tokens = Oc2Tokens(),
    public val outcome: Oc2SessionOutcome? = null,
    public val time: Oc2SessionTime = Oc2SessionTime(),
    public val title: String? = null,
    public val location: Oc2Location? = null,
    public val subpath: String? = null,
    /** The raw `revert` value; any non-null value means the session has a staged revert. */
    @SerialName("revert") public val revertValue: JsonElement? = null,
    public val metadata: JsonObject? = null,
) {
    public val reverted: Boolean get() = revertValue != null
    public val revert: Oc2SessionRevert? get() = Oc2SessionRevert.fromJson(revertValue)
    public val archived: Boolean get() = (time.archived ?: 0) > 0
    public val directory: String? get() = location?.directory

    public companion object {
        /** Null without a string `id`. */
        public fun fromJson(obj: JsonObject): Oc2Session? = Oc2Wire.decode(serializer(), obj)
    }
}

/** Prompt attachment as stored on a user message: `{data?, mime?, source:{type, uri}?, name?, description?}`. */
@Serializable(with = Oc2FileAttachment.Serializer::class)
public data class Oc2FileAttachment(
    public val data: String? = null,
    public val mime: String? = null,
    public val sourceType: String? = null,
    public val sourceUri: String? = null,
    public val name: String? = null,
    public val description: String? = null,
) {
    @Serializable
    internal data class Source(val type: String? = null, val uri: String? = null)

    @Serializable
    internal data class Wire(
        val data: String? = null,
        val mime: String? = null,
        val source: Source? = null,
        val name: String? = null,
        val description: String? = null,
    )

    internal object Serializer : SurrogateSerializer<Oc2FileAttachment, Wire>("FileAttachment", Wire.serializer()) {
        override fun toValue(wire: Wire): Oc2FileAttachment =
            Oc2FileAttachment(wire.data, wire.mime, wire.source?.type, wire.source?.uri, wire.name, wire.description)

        override fun toWire(value: Oc2FileAttachment): Wire =
            Wire(
                value.data,
                value.mime,
                if (value.sourceType == null && value.sourceUri == null) null else Source(value.sourceType, value.sourceUri),
                value.name,
                value.description,
            )
    }
}
