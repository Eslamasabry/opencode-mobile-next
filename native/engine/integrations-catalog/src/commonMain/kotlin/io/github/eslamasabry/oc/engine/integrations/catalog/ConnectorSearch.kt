package io.github.eslamasabry.oc.engine.integrations.catalog

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

// Flutter lib/domain/connector_search_result.dart (docs/design/BD3-mcp-chat-contract.md "Agent connector search").

/** How a connector search step ended, as the helper reports it. */
public enum class ConnectorSearchStatus { Ok, CatalogueNotLoaded, CatalogueOff, Unavailable }

/** One match in plain fields. [runtime] is `hosted`, `npx`, `uvx`, `docker` or `unavailable`; [connected] null = unknown. */
public data class ConnectorSearchMatch(val name: String, val runtime: String, val connected: Boolean?)

public data class ConnectorSearchResult(val status: ConnectorSearchStatus, val matches: List<ConnectorSearchMatch>)

private const val MAX_TEXT = 32_768
private const val MAX_MATCHES = 10
private const val MAX_NAME = 120

/**
 * Reads a finished connector search from its structured output ([value]) or its JSON [text]; null when the output is
 * not a search result, so the step falls back to its plain output.
 */
public fun parseConnectorSearchResult(value: JsonElement? = null, text: String? = null): ConnectorSearchResult? {
    var data: JsonElement? = value
    if (data !is JsonObject) {
        val raw = text?.trim()
        if (raw.isNullOrEmpty() || raw.length > MAX_TEXT) return null
        data =
            try {
                Json.parseToJsonElement(raw)
            } catch (_: IllegalArgumentException) {
                return null
            }
    }
    if (data !is JsonObject) return null
    val status =
        when ((data["status"] as? JsonPrimitive)?.takeIf { it.isString }?.content) {
            "ok" -> ConnectorSearchStatus.Ok
            "catalogue_not_loaded" -> ConnectorSearchStatus.CatalogueNotLoaded
            "catalogue_off" -> ConnectorSearchStatus.CatalogueOff
            "unavailable" -> ConnectorSearchStatus.Unavailable
            else -> return null
        }
    val rows = data["matches"]
    val matches = mutableListOf<ConnectorSearchMatch>()
    if (status == ConnectorSearchStatus.Ok && rows is JsonArray) {
        for (row in rows.take(MAX_MATCHES)) {
            if (row !is JsonObject) continue
            val name = row.string("name")
            val id = row.string("catalogId")
            val label =
                when {
                    name != null && name.trim().isNotEmpty() -> name.trim()
                    id != null -> id.trim()
                    else -> ""
                }
            if (label.isEmpty()) continue
            matches +=
                ConnectorSearchMatch(
                    name = label.takeCodePoints(MAX_NAME),
                    runtime = row.string("runtime") ?: "unavailable",
                    connected = (row["connected"] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull,
                )
        }
    }
    return ConnectorSearchResult(status, matches)
}

internal fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

/** The first [count] code points (Dart `runes.take`), never splitting a surrogate pair. */
internal fun String.takeCodePoints(count: Int): String {
    var i = 0
    var taken = 0
    while (i < length && taken < count) {
        i += if (this[i].isHighSurrogate() && i + 1 < length && this[i + 1].isLowSurrogate()) 2 else 1
        taken++
    }
    return substring(0, i)
}
