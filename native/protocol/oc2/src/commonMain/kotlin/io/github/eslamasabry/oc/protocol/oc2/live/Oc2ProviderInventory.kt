package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.model.ProviderInfo
import io.github.eslamasabry.oc.core.model.ProvidersResponse
import io.github.eslamasabry.oc.protocol.oc2.wire.*
import kotlinx.collections.immutable.*
import kotlinx.serialization.json.*

/** Variant metadata survives, while only the explicit connected inventory is selectable. */
internal object Oc2ProviderInventory {
    fun parse(root: JsonObject): Outcome<ProvidersResponse>? {
        val rows = (root["all"] ?: root["providers"]) as? JsonArray ?: return null
        if (rows.size > 1000) return null
        val available = LinkedHashMap<String, ProviderInfo>()
        for (row in rows) {
            val obj = row as? JsonObject ?: return null
            val id = obj.str("id")?.takeIf { it.isNotBlank() } ?: return null
            if (id in available) return null
            val models = obj["models"] as? JsonObject ?: return null
            val metadata = models.mapValues { (_, value) -> value as? JsonObject ?: return null }
            val visible = metadata.filterValues { it.obj("options")?.bool("hidden") != true }.keys
            available[id] =
                ProviderInfo(
                    id,
                    obj.str("name") ?: id,
                    visible.toImmutableList(),
                    metadata.toImmutableMap(),
                )
        }
        val connected =
            if ("connected" in root) {
                val ids = root["connected"] as? JsonArray ?: return null
                if (ids.size > 1000) return null
                val seen = mutableSetOf<String>()
                ids.map {
                    val id = (it as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
                    if (!seen.add(id)) return null
                    available[id] ?: return null
                }
            } else available.values.toList()
        var provider: String? = null
        var model: String? = null
        when (val defaults = root["default"]) {
            null,
            JsonNull -> Unit
            is JsonObject ->
                connected
                    .firstOrNull { defaults[it.id] is JsonPrimitive }
                    ?.let {
                        val value = defaults[it.id] as JsonPrimitive
                        if (!value.isString) return null
                        provider = it.id
                        model = value.content
                    }
            is JsonPrimitive -> {
                if (!defaults.isString) return null
                val id = defaults.content.substringBefore('/')
                if (connected.any { it.id == id } && '/' in defaults.content) {
                    provider = id
                    model = defaults.content.substringAfter('/')
                }
            }
            else -> return null
        }
        return ProvidersResponse(
                connected.toImmutableList(),
                available.values.toImmutableList(),
                provider,
                model,
            )
            .ok()
    }
}
