package io.github.eslamasabry.oc.protocol.oc2.wire

import io.github.eslamasabry.oc.core.common.json.SurrogateSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// Ported from lib/api2/models/models_catalog.dart: models, providers, agents, commands, skills, config, fs.

@Serializable
public data class Oc2ModelLimit(public val context: Long? = null, public val input: Long? = null, public val output: Long? = null)

@Serializable
public data class Oc2ModelCapabilities(
    public val tools: Boolean = false,
    public val input: List<String> = emptyList(),
    public val output: List<String> = emptyList(),
)

/**
 * One `Model.Cost` entry, USD per million tokens. [tierSize] is set on context-tier overrides
 * (`tier: {type: "context", size}`); the base price has none.
 */
@Serializable(with = Oc2ModelCost.Serializer::class)
public data class Oc2ModelCost(
    public val input: Double = 0.0,
    public val output: Double = 0.0,
    public val cacheRead: Double = 0.0,
    public val cacheWrite: Double = 0.0,
    public val tierSize: Long? = null,
) {
    @Serializable
    internal data class Cache(val read: Double = 0.0, val write: Double = 0.0)

    @Serializable
    internal data class Tier(val type: String? = null, val size: Long? = null)

    @Serializable
    internal data class Wire(
        val input: Double = 0.0,
        val output: Double = 0.0,
        val cache: Cache? = null,
        val tier: Tier? = null,
    )

    internal object Serializer : SurrogateSerializer<Oc2ModelCost, Wire>("oc2.ModelCost", Wire.serializer()) {
        override fun toValue(wire: Wire): Oc2ModelCost =
            Oc2ModelCost(wire.input, wire.output, wire.cache?.read ?: 0.0, wire.cache?.write ?: 0.0, wire.tier?.size)

        override fun toWire(value: Oc2ModelCost): Wire =
            Wire(value.input, value.output, Cache(value.cacheRead, value.cacheWrite), value.tierSize?.let { Tier("context", it) })
    }

    public companion object {
        public fun fromJson(element: JsonElement?): Oc2ModelCost? = Oc2Wire.decode(serializer(), element)
    }
}

/**
 * A catalog model. Older betas list models under a composite `provider/model` id without `modelID`; the provider
 * prefix is peeled so prompts get the bare id. [cost] is the server's price list (base first, then tiers), accepting
 * a single object too. [raw] keeps the whole wire object for fields this projection does not model (variant settings).
 */
public data class Oc2ModelInfo(
    public val id: String,
    public val modelID: String? = null,
    public val providerID: String? = null,
    public val family: String? = null,
    public val name: String? = null,
    public val status: String? = null,
    public val enabled: Boolean = true,
    public val capabilities: Oc2ModelCapabilities = Oc2ModelCapabilities(),
    public val variants: List<String> = emptyList(),
    public val limit: Oc2ModelLimit = Oc2ModelLimit(),
    public val released: Long? = null,
    public val cost: List<Oc2ModelCost> = emptyList(),
    public val raw: JsonObject = EmptyObject,
) {
    /** The base (non-tiered) price, or the first entry when every entry is tiered. */
    public val baseCost: Oc2ModelCost? get() = cost.firstOrNull { it.tierSize == null } ?: cost.firstOrNull()

    public fun ref(variant: String? = null): Oc2ModelRef = Oc2ModelRef(modelID ?: id, providerID ?: "", variant)

    @Serializable
    internal data class VariantId(val id: String)

    @Serializable
    internal data class Time(val released: Long? = null)

    @Serializable
    internal data class Wire(
        val id: String,
        val modelID: String? = null,
        val providerID: String? = null,
        val family: String? = null,
        val name: String? = null,
        val status: String? = null,
        val enabled: Boolean = true,
        val capabilities: Oc2ModelCapabilities = Oc2ModelCapabilities(),
        val variants: List<VariantId> = emptyList(),
        val limit: Oc2ModelLimit = Oc2ModelLimit(),
        val time: Time? = null,
        val cost: JsonElement? = null,
    )

    public companion object {
        public fun fromJson(obj: JsonObject): Oc2ModelInfo? {
            val w = Oc2Wire.decode(Wire.serializer(), obj) ?: return null
            val modelID =
                w.modelID ?: w.providerID?.let { p -> w.id.takeIf { it.startsWith("$p/") }?.removePrefix("$p/") }
            val cost =
                when (val c = w.cost) {
                    is JsonArray -> c.mapNotNull(Oc2ModelCost::fromJson)
                    else -> listOfNotNull(Oc2ModelCost.fromJson(c))
                }
            return Oc2ModelInfo(
                id = w.id,
                modelID = modelID,
                providerID = w.providerID,
                family = w.family,
                name = w.name,
                status = w.status,
                enabled = w.enabled,
                capabilities = w.capabilities,
                variants = w.variants.map { it.id },
                limit = w.limit,
                released = w.time?.released,
                cost = cost,
                raw = obj,
            )
        }
    }
}

@Serializable
public data class Oc2ProviderInfo(
    public val id: String,
    public val integrationID: String? = null,
    public val name: String? = null,
    public val activation: String? = null,
    @SerialName("package") public val packageName: String? = null,
) {
    public companion object {
        public fun fromJson(obj: JsonObject): Oc2ProviderInfo? = Oc2Wire.decode(serializer(), obj)
    }
}

/** An agent; the id falls back to the name and the name to the id. */
public data class Oc2AgentInfo(
    public val id: String,
    public val name: String,
    public val description: String? = null,
    public val mode: String? = null,
    public val hidden: Boolean = false,
    public val color: String? = null,
    public val model: Oc2ModelRef? = null,
    public val permissions: List<Oc2PermissionRule> = emptyList(),
) {
    /** Primary agents a person can pick: not hidden and not `subagent`. */
    public val selectable: Boolean get() = !hidden && mode != "subagent"

    @Serializable
    internal data class Wire(
        val id: String? = null,
        val name: String? = null,
        val description: String? = null,
        val mode: String? = null,
        val hidden: Boolean = false,
        val color: String? = null,
        val model: Oc2ModelRef? = null,
        val permissions: List<Oc2PermissionRule> = emptyList(),
    )

    public companion object {
        public fun fromJson(obj: JsonObject): Oc2AgentInfo? {
            val w = Oc2Wire.decode(Wire.serializer(), obj) ?: return null
            val id = w.id ?: w.name ?: return null
            return Oc2AgentInfo(id, w.name ?: id, w.description, w.mode, w.hidden, w.color, w.model, w.permissions)
        }
    }
}

@Serializable
public data class Oc2Command(public val name: String, public val description: String? = null) {
    public companion object {
        public fun fromJson(obj: JsonObject): Oc2Command? = Oc2Wire.decode(serializer(), obj)
    }
}

/** A skill; the id falls back to the name. */
public data class Oc2Skill(
    public val id: String,
    public val name: String? = null,
    public val description: String? = null,
    public val location: String? = null,
    public val content: String? = null,
    public val slash: Boolean = false,
) {
    @Serializable
    internal data class Wire(
        val id: String? = null,
        val name: String? = null,
        val description: String? = null,
        val location: String? = null,
        val content: String? = null,
        val slash: Boolean = false,
    )

    public companion object {
        public fun fromJson(obj: JsonObject): Oc2Skill? {
            val w = Oc2Wire.decode(Wire.serializer(), obj) ?: return null
            return Oc2Skill(w.id ?: w.name ?: return null, w.name, w.description, w.location, w.content, w.slash)
        }
    }
}

/** One entry of the priority-ordered `GET /api/config` list. */
@Serializable
public data class Oc2ConfigEntry(public val type: String, public val path: String? = null, public val info: JsonObject = EmptyObject) {
    public companion object {
        public fun fromJson(obj: JsonObject): Oc2ConfigEntry? = Oc2Wire.decode(serializer(), obj)
    }
}

@Serializable
public data class Oc2FsEntry(public val path: String, public val type: String = "file") {
    public val isDirectory: Boolean get() = type == "directory"

    public companion object {
        public fun fromJson(obj: JsonObject): Oc2FsEntry? = Oc2Wire.decode(serializer(), obj)
    }
}
