package io.github.eslamasabry.oc.engine.agents.models

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Personal model shortcuts for one server (Flutter `ModelLibrary`): favorites and the [RECENT_LIMIT] newest choices,
 * all normalised. These never choose a model for a session; selection is [ModelChoices]'s job (B-STATE-032, B-NAV-033).
 */
public data class ModelLibrary(
    public val favorites: ImmutableList<ModelRef> = persistentListOf(),
    public val recent: ImmutableList<ModelRef> = persistentListOf(),
) {
    public constructor(favorites: List<ModelRef> = emptyList(), recent: List<ModelRef>) :
        this(favorites.toImmutableList(), recent.toImmutableList())

    public val isEmpty: Boolean get() = favorites.isEmpty() && recent.isEmpty()

    public fun isFavorite(model: ModelRef): Boolean = favorites.any { it.sameModel(model) }

    public fun toggleFavorite(model: ModelRef): ModelLibrary = copy(
        favorites = (if (isFavorite(model)) favorites.filterNot { it.sameModel(model) } else favorites + model.normalized)
            .toImmutableList(),
    )

    /** Newest first, deduplicated, at most [RECENT_LIMIT]. */
    public fun remember(model: ModelRef): ModelLibrary = copy(
        recent = (listOf(model.normalized) + recent.filterNot { it.sameModel(model) }).take(RECENT_LIMIT)
            .toImmutableList(),
    )

    /** Only after a successful catalog refresh: a failed request must never erase saved shortcuts. */
    public fun retainWhere(available: (ModelRef) -> Boolean): ModelLibrary =
        ModelLibrary(favorites.filter(available).toImmutableList(), recent.filter(available).toImmutableList())

    /**
     * The next available model after [current] in the recent (or favorites) ring, wrapping both ways; the first (or,
     * reversed, the last) when [current] is not in it; null when nothing is available or the only choice is [current].
     */
    public fun next(
        current: ModelRef?,
        reverse: Boolean = false,
        favoritesOnly: Boolean = false,
        available: (ModelRef) -> Boolean,
    ): ModelRef? {
        val models = (if (favoritesOnly) favorites else recent).filter(available)
        if (models.isEmpty()) return null
        val index = models.indexOfFirst { sameModel(it, current) }
        val nextIndex = if (index < 0) {
            if (reverse) models.size - 1 else 0
        } else {
            (index + (if (reverse) -1 else 1)).mod(models.size)
        }
        val next = models[nextIndex]
        return if (sameModel(next, current)) null else next
    }

    /** The Flutter stored shape: `{"favorites":[{providerID,modelID}],"recent":[…]}`. */
    public fun toJson(): JsonObject = JsonObject(
        mapOf("favorites" to encode(favorites), "recent" to encode(recent)),
    )

    public companion object {
        public const val RECENT_LIMIT: Int = 8

        /** Tolerant read: non-objects, wrong types and blank parts are skipped; duplicates dropped; recent bounded. */
        public fun fromJson(value: JsonElement?): ModelLibrary {
            val obj = value as? JsonObject ?: return ModelLibrary()
            return ModelLibrary(read(obj["favorites"], null), read(obj["recent"], RECENT_LIMIT))
        }

        private fun read(raw: JsonElement?, limit: Int?): ImmutableList<ModelRef> {
            val items = raw as? JsonArray ?: return persistentListOf()
            val result = mutableListOf<ModelRef>()
            val seen = mutableSetOf<Pair<String, String>>()
            for (item in items) {
                val entry = item as? JsonObject ?: continue
                val provider = entry.string("providerID") ?: continue
                val id = entry.string("modelID") ?: continue
                if (provider.isBlank() || id.isBlank()) continue
                val model = ModelRef(provider, id).normalized
                if (seen.add(model.providerId to model.modelId)) result += model
                if (limit != null && result.size >= limit) break
            }
            return result.toImmutableList()
        }

        private fun encode(models: List<ModelRef>): JsonArray = JsonArray(models.map(::refJson))

        internal fun refJson(model: ModelRef): JsonObject = JsonObject(
            mapOf("providerID" to JsonPrimitive(model.providerId), "modelID" to JsonPrimitive(model.modelId)),
        )
    }
}
