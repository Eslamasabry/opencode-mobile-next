package io.github.eslamasabry.oc.protocol.oc2.wire

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Ported from lib/api2/models.dart (server, location, shared value types, pagination).

@Serializable
public data class Oc2Project(
    public val id: String,
    public val directory: String? = null,
    public val canonical: String? = null,
)

/**
 * The `location` object on events and sessions, and the `GET /api/location` resolution (which adds `project`).
 * The workspace id is read from `workspaceID`, or from `workspace` as older builds send it.
 */
@Serializable(with = Oc2Location.Serializer::class)
public data class Oc2Location(
    public val directory: String? = null,
    public val workspaceID: String? = null,
    public val project: Oc2Project? = null,
) {
    /** The query/body shape the client sends (`directory`, `workspaceID`). */
    public fun toJson(): JsonObject =
        JsonObject(
            buildMap {
                directory?.let { put("directory", JsonPrimitive(it)) }
                workspaceID?.let { put("workspaceID", JsonPrimitive(it)) }
            },
        )

    @Serializable
    internal data class Wire(
        val directory: String? = null,
        val workspaceID: String? = null,
        val workspace: String? = null,
        val project: Oc2Project? = null,
    )

    internal object Serializer : SurrogateSerializer<Oc2Location, Wire>("Location", Wire.serializer()) {
        override fun toValue(wire: Wire): Oc2Location =
            Oc2Location(wire.directory, wire.workspaceID ?: wire.workspace, wire.project)

        override fun toWire(value: Oc2Location): Wire = Wire(value.directory, value.workspaceID, null, value.project)
    }

    public companion object {
        /** Null when [element] is not an object. */
        public fun fromJson(element: JsonElement?): Oc2Location? = Oc2Wire.decode(serializer(), element)
    }
}

/** Token counts; `cache.read` / `cache.write` on the wire. A missing or malformed object reads as zeros. */
@Serializable(with = Oc2Tokens.Serializer::class)
public data class Oc2Tokens(
    public val input: Long = 0,
    public val output: Long = 0,
    public val reasoning: Long = 0,
    public val cacheRead: Long = 0,
    public val cacheWrite: Long = 0,
) {
    public val cache: Long get() = cacheRead + cacheWrite
    public val total: Long get() = input + output + reasoning + cache

    @Serializable
    internal data class Cache(val read: Long = 0, val write: Long = 0)

    @Serializable
    internal data class Wire(
        val input: Long = 0,
        val output: Long = 0,
        val reasoning: Long = 0,
        val cache: Cache = Cache(),
    )

    internal object Serializer : SurrogateSerializer<Oc2Tokens, Wire>("Tokens", Wire.serializer()) {
        override fun toValue(wire: Wire): Oc2Tokens =
            Oc2Tokens(wire.input, wire.output, wire.reasoning, wire.cache.read, wire.cache.write)

        override fun toWire(value: Oc2Tokens): Wire =
            Wire(value.input, value.output, value.reasoning, Cache(value.cacheRead, value.cacheWrite))
    }

    public companion object {
        public fun fromJson(element: JsonElement?): Oc2Tokens = Oc2Wire.decode(serializer(), element) ?: Oc2Tokens()
    }
}

/**
 * `{id, providerID, variant?}`. A composite id that repeats its own provider (`openai/gpt-5`) is read bare
 * (B-PROTO-062); a foreign prefix (`anthropic/claude` under `openrouter`) is kept. `"default"` is kept as sent; the
 * mapper reads it as no variant (B-PROTO-018).
 */
@Serializable(with = Oc2ModelRef.Serializer::class)
public data class Oc2ModelRef(
    public val id: String,
    public val providerID: String,
    public val variant: String? = null,
) {
    /** `provider/id#variant`, the Dart `toString`. */
    override fun toString(): String = "$providerID/$id" + (variant?.let { "#$it" } ?: "")

    @Serializable
    internal data class Wire(val id: String, val providerID: String, val variant: String? = null)

    internal object Serializer : SurrogateSerializer<Oc2ModelRef, Wire>("ModelRef", Wire.serializer()) {
        override fun toValue(wire: Wire): Oc2ModelRef {
            val prefix = "${wire.providerID}/"
            val bare = if (wire.id.startsWith(prefix) && wire.id.length > prefix.length) wire.id.removePrefix(prefix) else wire.id
            return Oc2ModelRef(bare, wire.providerID, wire.variant)
        }

        override fun toWire(value: Oc2ModelRef): Wire = Wire(value.id, value.providerID, value.variant)
    }

    public companion object {
        /** Null unless both `id` and `providerID` are strings. */
        public fun fromJson(element: JsonElement?): Oc2ModelRef? = Oc2Wire.decode(serializer(), element)
    }
}

/**
 * `{type|name, message, status, …}`. A blank type or message is no text at all: servers send `""` for some provider
 * failures, and an empty string would pass every fallback and leave the error details empty.
 */
@Serializable(with = Oc2StructuredError.Serializer::class)
public data class Oc2StructuredError(
    public val type: String? = null,
    public val message: String? = null,
    public val status: Int? = null,
    public val raw: JsonObject = EmptyObject,
) {
    internal object Serializer : RawObjectSerializer<Oc2StructuredError>("StructuredError") {
        override fun read(obj: JsonObject): Oc2StructuredError =
            Oc2StructuredError(obj.nonBlank("type") ?: obj.nonBlank("name"), obj.nonBlank("message"), obj.int("status"), obj)

        override fun write(value: Oc2StructuredError): JsonElement = value.raw
    }

    public companion object {
        public fun fromJson(element: JsonElement?): Oc2StructuredError? = (element as? JsonObject)?.let(Serializer::read)
    }
}

/** `{data: [...], cursor: {previous?, next?}}`; cursors are opaque and passed back verbatim as `?cursor=`. */
public data class Oc2Page<T>(
    public val data: List<T> = emptyList(),
    public val previousCursor: String? = null,
    public val nextCursor: String? = null,
) {
    public val hasNext: Boolean get() = nextCursor != null
    public val hasPrevious: Boolean get() = previousCursor != null

    public companion object {
        /** Items that are not objects, or that [item] cannot read, are skipped (Dart `_mapList`). */
        public fun <T> decode(element: JsonElement?, item: (JsonObject) -> T?): Oc2Page<T> {
            val obj = element as? JsonObject ?: return Oc2Page()
            val cursor = obj.obj("cursor")
            val data = (obj["data"] as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(item) }
            return Oc2Page(data, cursor?.str("previous"), cursor?.str("next"))
        }
    }
}
