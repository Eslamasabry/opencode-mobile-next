package io.github.eslamasabry.oc.engine.integrations.catalog

import io.github.eslamasabry.oc.core.model.ProviderInfo
import io.github.eslamasabry.oc.core.model.ProvidersResponse
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

// Flutter ProvidersResponse.fromJson (lib/domain/models/models_requests.dart). Provider config can carry API keys:
// nothing here is logged or shown beyond ids and names.

/**
 * Parses `/provider`. With a `connected` list, [ProvidersResponse.providers] holds only the connected providers in
 * connection order (an explicit empty list means none, never the catalogue) and the default comes from the first
 * connected provider that has one; [ProvidersResponse.availableProviders] is the whole catalogue in server order.
 * Model rows keep their raw metadata (variants included); hidden models are not offered.
 */
public fun parseProvidersResponse(json: JsonObject): ProvidersResponse {
    val connectedRaw = json["connected"] as? JsonArray
    val hasConnectedList = connectedRaw != null
    val connected = connectedRaw.orEmpty().mapNotNull { it.plainText() }.filter { it.isNotEmpty() }
    val rawList = (json["all"] as? JsonArray) ?: (json["providers"] as? JsonArray) ?: JsonArray(emptyList())
    val rawById = LinkedHashMap<String, JsonObject>()
    for (p in rawList) {
        if (p !is JsonObject) continue
        rawById[p["id"].plainText() ?: "null"] = p
    }
    val available = rawById.values.map(::parseProvider)
    val availableById = available.associateBy { it.id }
    val providers = if (hasConnectedList) connected.mapNotNull { availableById[it] } else available
    var defaultProvider: String? = null
    var defaultModel: String? = null
    when (val d = json["default"]) {
        is JsonObject ->
            if (d.isNotEmpty()) {
                val defaults =
                    if (!hasConnectedList) {
                        d.entries.map { it.key to it.value }
                    } else {
                        connected.mapNotNull { id -> d[id]?.takeIf { it !is JsonNull }?.let { id to it } }
                    }
                defaults.firstOrNull()?.let { (p, m) ->
                    defaultProvider = p
                    defaultModel = m.plainText() ?: m.toString()
                }
            }
        is JsonPrimitive ->
            if (d.isString) {
                val parts = d.content.split('/')
                if (parts.size == 2) {
                    defaultProvider = parts[0]
                    defaultModel = parts[1]
                }
            }
        else -> Unit
    }
    return ProvidersResponse(
        providers = providers.toImmutableList(),
        availableProviders = available.toImmutableList(),
        defaultProviderId = defaultProvider,
        defaultModelId = defaultModel,
    )
}

private fun parseProvider(p: JsonObject): ProviderInfo {
    val id = p["id"].plainText() ?: "null"
    val name = p["name"].plainText() ?: id
    val models = mutableListOf<String>()
    val data = LinkedHashMap<String, JsonObject>()
    (p["models"] as? JsonObject)?.forEach { (key, m) ->
        var hidden = false
        if (m is JsonObject) {
            data[key] = m
            hidden = ((m["options"] as? JsonObject)?.get("hidden") as? JsonPrimitive)?.booleanOrNull == true
        }
        if (!hidden) models += key
    }
    return ProviderInfo(id = id, name = name, modelIds = models.toImmutableList(), modelData = data.toImmutableMap())
}

/** Dart `toString()` of a JSON scalar; null for null/absent and for objects or arrays. */
private fun JsonElement?.plainText(): String? =
    when (this) {
        null, JsonNull -> null
        is JsonPrimitive -> content
        else -> null
    }
