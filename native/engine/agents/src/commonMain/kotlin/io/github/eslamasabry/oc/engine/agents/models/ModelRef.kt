package io.github.eslamasabry.oc.engine.agents.models

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * A provider + model pair (Flutter `ModelRef`). Some catalog shapes carry the model under a composite key
 * (`openai/gpt-5`) while prompt endpoints want the bare id; [normalized] guarantees the bare form (B-PROTO-062).
 */
public data class ModelRef(public val providerId: String, public val modelId: String) {
    /** This ref when already bare (the same instance), otherwise a copy with the provider prefix peeled. */
    public val normalized: ModelRef
        get() {
            val bare = bareModelId(providerId, modelId)
            return if (bare == modelId) this else ModelRef(providerId, bare)
        }

    /** `provider/model`, the display and comparison key. */
    public val wireName: String get() = "$providerId/$modelId"

    /** Same model after normalising both sides. */
    public fun sameModel(other: ModelRef?): Boolean {
        if (other == null) return false
        val a = normalized
        val b = other.normalized
        return a.providerId == b.providerId && a.modelId == b.modelId
    }

    override fun toString(): String = wireName
}

/**
 * Strips a leading `providerId/` from a composite model id. Ids that merely contain a slash keep it unless the prefix
 * is this provider; an empty provider or an empty remainder leaves the id unchanged.
 */
public fun bareModelId(providerId: String, modelId: String): String {
    val prefix = "$providerId/"
    if (providerId.isNotEmpty() && modelId.startsWith(prefix)) {
        val rest = modelId.substring(prefix.length)
        if (rest.isNotEmpty()) return rest
    }
    return modelId
}

/** Same-model check for nullable refs (Flutter `ModelLibrary.sameModel`): false when either is null. */
public fun sameModel(a: ModelRef?, b: ModelRef?): Boolean = a != null && a.sameModel(b)

/**
 * The OpenCode 2 wire model ref `{id, providerID, variant?}` (Flutter `Api2ModelRef`). A composite `id` is sent bare.
 */
public data class WireModelRef(public val id: String, public val providerId: String, public val variant: String? = null) {
    public fun toJson(): JsonObject = JsonObject(
        buildMap {
            put("id", JsonPrimitive(id))
            put("providerID", JsonPrimitive(providerId))
            if (variant != null) put("variant", JsonPrimitive(variant))
        },
    )

    public fun toModelRef(): ModelRef = ModelRef(providerId, id)

    public companion object {
        /** Null unless [value] is an object with string `id` and `providerID`. */
        public fun fromJson(value: JsonElement?): WireModelRef? {
            val obj = value as? JsonObject ?: return null
            val id = obj.string("id") ?: return null
            val providerId = obj.string("providerID") ?: return null
            val prefix = "$providerId/"
            return WireModelRef(
                id = if (id.startsWith(prefix) && id.length > prefix.length) id.substring(prefix.length) else id,
                providerId = providerId,
                variant = obj.string("variant"),
            )
        }
    }
}

/**
 * The bare model id of an OpenCode 2 catalog entry (Flutter `Api2ModelInfo.modelID`): the explicit `modelID` when
 * sent, otherwise a composite `id` with its `providerID/` prefix peeled; null when neither applies.
 */
public fun catalogEntryModelId(entry: JsonObject): String? {
    val id = entry.string("id") ?: return null
    val providerId = entry.string("providerID")
    val explicit = entry.string("modelID")
    if (explicit != null) return explicit
    if (providerId != null && id.startsWith("$providerId/")) return id.substring(providerId.length + 1)
    return null
}

internal fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
