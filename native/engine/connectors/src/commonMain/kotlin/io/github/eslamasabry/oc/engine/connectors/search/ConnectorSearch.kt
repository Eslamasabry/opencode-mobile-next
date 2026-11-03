package io.github.eslamasabry.oc.engine.connectors.search

import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.connectors.catalog.McpCatalogItem
import io.github.eslamasabry.oc.engine.connectors.catalog.McpCatalogRuntime
import io.github.eslamasabry.oc.engine.connectors.registry.RegistryEntry
import io.github.eslamasabry.oc.engine.connectors.text.Scalars
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The answer of a connector search: a reply for the agent, or an invalid request (input never echoed). */
public sealed interface ConnectorSearchAnswer {
    public data class Reply(val json: JsonObject) : ConnectorSearchAnswer

    public data object Invalid : ConnectorSearchAnswer
}

/**
 * Bounded, read-only discovery in catalogue metadata already loaded by the person (Dart `searchMcpConnectors`,
 * lib/domain/mcp_connector_search.dart; B-AGENT-137).
 *
 * The reply never includes endpoint URLs, commands, settings or credential values, and registry metadata never
 * establishes OAuth support (`needsSignIn` is always `unknown`). [entries] null means "not loaded" (distinct from an
 * empty list); [connected] null means "unknown" for every row.
 */
public object ConnectorSearch {
    /** What the agent is told while the person has not turned the catalogue on. Agent-facing fixed text. */
    public const val CATALOGUE_OFF_MESSAGE: String =
        "The connector catalogue is off. Ask the person to tap Turn on in this step, then search again."

    /** What the agent is told when the catalogue is on but could not be loaded. */
    public const val CATALOGUE_NOT_LOADED_MESSAGE: String =
        "The connector catalogue could not be loaded right now. Try again in a moment."

    /** Dart's FormatException message for every invalid request. */
    public const val INVALID_REQUEST_MESSAGE: String = "Invalid connector search request."

    public const val DEFAULT_LIMIT: Int = 5
    public const val MAX_LIMIT: Int = 10
    public const val MAX_QUERY_SCALARS: Int = 100
    public const val MAX_INSPECTED: Int = 100
    public const val MAX_DESCRIPTION_SCALARS: Int = 300

    private val CATALOG_ID = Regex("""^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$""")
    private val URL = Regex("""\b(?:[a-z][a-z0-9+.-]*:(?://)?|www\.)[^\s<>]+""", RegexOption.IGNORE_CASE)

    // Dart `\s` is Unicode-aware; Java's is not, so the Unicode spaces are listed.
    private val SPACING =
        Regex(
            "[\\s\\u0000-\\u001f\\u007f-\\u009f\\u00a0\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000" +
                "\\ufeff\\u061c\\u200b-\\u200f\\u202a-\\u202e\\u2060-\\u206f]+",
        )

    /** The reply for a person who has not turned the catalogue on. Makes no request of any kind. */
    public fun catalogueOff(): JsonObject =
        buildJsonObject {
            put("status", "catalogue_off")
            put("message", CATALOGUE_OFF_MESSAGE)
            put("matches", JsonArray(emptyList()))
        }

    /** The trimmed query of a valid request; null when [arguments] is invalid. */
    public fun validQuery(arguments: JsonElement?, redactor: Redactor): String? = parse(arguments, redactor)?.query

    public fun search(
        arguments: JsonElement?,
        entries: Iterable<RegistryEntry>?,
        connected: Map<String, Boolean>?,
        redactor: Redactor,
    ): ConnectorSearchAnswer {
        val request = parse(arguments, redactor) ?: return ConnectorSearchAnswer.Invalid
        if (entries == null) {
            return ConnectorSearchAnswer.Reply(
                buildJsonObject {
                    put("status", "catalogue_not_loaded")
                    put("message", CATALOGUE_NOT_LOADED_MESSAGE)
                    put("matches", JsonArray(emptyList()))
                },
            )
        }
        class Candidate(val entry: RegistryEntry, val title: String, val description: String)
        val candidates = ArrayList<Candidate>()
        for (entry in entries.asSequence().take(MAX_INSPECTED)) {
            if (entry.name.length > 256 || !CATALOG_ID.matches(entry.name) || redactor.containsSecret(entry.name)) {
                continue
            }
            val title = prose(entry.title, 120, redactor)
            val full = prose(entry.description, 1000, redactor)
            val searchable = "${entry.name} $title $full".lowercase()
            if (!request.terms.all { searchable.contains(it) }) continue
            candidates += Candidate(entry, title, Scalars.take(full, MAX_DESCRIPTION_SCALARS))
        }
        // Duplicate versions resolve consistently without exposing the version.
        candidates.sortWith(
            compareBy<Candidate> { it.entry.name.lowercase() }
                .thenBy { it.entry.id }
                .thenBy { it.title }
                .thenBy { it.description },
        )
        val seen = HashSet<String>()
        val matches = ArrayList<JsonObject>()
        for (candidate in candidates) {
            val entry = candidate.entry
            if (!seen.add(entry.name)) continue
            val item = McpCatalogItem.from(entry)
            matches +=
                buildJsonObject {
                    put("catalogId", entry.name)
                    put("name", candidate.title.ifEmpty { item.serverName })
                    put("description", candidate.description)
                    put("runtime", runtimeName(item.runtime))
                    put("needsSignIn", "unknown")
                    val isOn = connected?.let { JsonPrimitive(it[item.serverName] ?: false) } ?: JsonNull
                    put("connected", isOn)
                }
            if (matches.size == request.limit) break
        }
        return ConnectorSearchAnswer.Reply(
            buildJsonObject {
                put("status", "ok")
                put("matches", JsonArray(matches))
            },
        )
    }

    private fun runtimeName(runtime: McpCatalogRuntime): String =
        when (runtime) {
            McpCatalogRuntime.Hosted -> "hosted"
            McpCatalogRuntime.Node -> "npx"
            McpCatalogRuntime.Python -> "uvx"
            McpCatalogRuntime.Docker -> "docker"
            McpCatalogRuntime.None -> "unavailable"
        }

    private class Request(val query: String, val terms: List<String>, val limit: Int)

    private fun parse(arguments: JsonElement?, redactor: Redactor): Request? {
        if (arguments !is JsonObject || arguments.keys.any { it != "query" && it != "limit" }) return null
        val rawQuery = (arguments["query"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
        val limit =
            if ("limit" in arguments) {
                val raw = arguments["limit"] as? JsonPrimitive ?: return null
                if (raw.isString || !INTEGER.matches(raw.content)) return null
                raw.content.toIntOrNull() ?: return null
            } else {
                DEFAULT_LIMIT
            }
        if (limit < 1 || limit > MAX_LIMIT) return null
        val query = rawQuery.trim()
        if (query.isEmpty() || Scalars.count(query) > MAX_QUERY_SCALARS || redactor.containsSecret(query) ||
            URL.containsMatchIn(query)
        ) {
            return null
        }
        val terms = prose(query, MAX_QUERY_SCALARS, redactor).lowercase().split(' ')
        if (terms.all { it.isEmpty() }) return null
        return Request(query, terms, limit)
    }

    private val INTEGER = Regex("""^-?\d+$""")

    private fun prose(value: String, maxScalars: Int, redactor: Redactor): String {
        val stripped = SPACING.replace(URL.replace(redactor.redact(value), " "), " ")
        return Scalars.take(redactor.redact(stripped).trim(), maxScalars)
    }
}
