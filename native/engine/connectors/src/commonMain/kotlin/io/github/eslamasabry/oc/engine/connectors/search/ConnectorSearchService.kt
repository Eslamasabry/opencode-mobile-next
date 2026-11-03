package io.github.eslamasabry.oc.engine.connectors.search

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.McpGateway
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.connectors.bridge.BridgeSearchResult
import io.github.eslamasabry.oc.engine.connectors.catalogue.CatalogueCache
import io.github.eslamasabry.oc.engine.connectors.catalogue.CatalogueStatus
import io.github.eslamasabry.oc.engine.connectors.catalogue.CatalogueStore
import io.github.eslamasabry.oc.engine.connectors.registry.RegistryClient
import io.github.eslamasabry.oc.engine.connectors.registry.RegistryEntry
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** One agent that may read the connector search (Dart `GenUiAgent`). */
public data class SearchAgent(
    val id: String,
    /** The agent runs on this server's OpenCode (no Paseo provider), so the server's MCP list applies to it. */
    val usesServerInventory: Boolean,
    /** Agent cards are qualified for this agent (only those get a bridge). */
    val cardsQualified: Boolean = true,
)

/**
 * What the connection looks like right now, as far as connector search cares (fields of Dart `ConnectionController`).
 * Two equal values mean "the same source"; any change invalidates in-flight answers.
 */
public data class SearchContext(
    val profileId: String,
    val baseUrl: String,
    val username: String?,
    /** Bumps on every reconnect of the connection. */
    val generation: Long,
    /** Bumps whenever the gateway or operations object is replaced (Dart `identical(api, …)`). */
    val transport: Long,
    val activeProfileId: String?,
    val directory: String?,
    val workspace: String?,
    val locationRevision: Long,
    val connected: Boolean,
    /** Agent cards are managed here and turned on. */
    val cardsEnabled: Boolean,
    /** The profile is being deleted. */
    val deleting: Boolean,
    val serverCatalog: Boolean,
    val agents: List<SearchAgent>,
)

/** PORT: the live connection, read on every check. */
public interface ConnectorSearchEnvironment {
    /** Null when there is no connected owner (or the connection was disposed). */
    public fun context(): SearchContext?

    /** The current operations gateway for [context]'s `transport`; null when none. */
    public suspend fun operations(): McpGateway?
}

/**
 * Answers a helper's search from the saved catalogue (Dart `_findCachedConnectors`; B-AGENT-132/133). Never changes
 * consent and never asks the registry while the catalogue is off; refreshes a list older than 24 h and asks the
 * registry's own search, both inside a 4 s budget, falling back to the saved list; applies the server's MCP list
 * only for an agent on this server in exactly this project (otherwise `connected` is unknown).
 */
public class ConnectorSearchService(
    private val environment: ConnectorSearchEnvironment,
    private val cache: CatalogueCache,
    private val registry: RegistryClient,
    private val nowMs: () -> Long,
    private val redactor: Redactor,
) {
    public suspend fun find(profileId: String, agent: SearchAgent, request: JsonObject): BridgeSearchResult {
        if (request.keys.any { it != "arguments" && it != "directory" }) return BridgeSearchResult.BadRequest
        val folder = (request["directory"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: return BridgeSearchResult.BadRequest
        if (!folder.startsWith("/") || folder.length > 4096 || CONTROL.containsMatchIn(folder)) {
            return BridgeSearchResult.BadRequest
        }
        val arguments = request["arguments"]
        // Validate before anything else: an invalid request reads nothing.
        val query = ConnectorSearch.validQuery(arguments, redactor) ?: return BridgeSearchResult.BadRequest
        val selected = environment.context() ?: return BridgeSearchResult.Failed
        fun sourceCurrent(): Boolean {
            val now = environment.context() ?: return false
            return now == selected && now.profileId == profileId && now.activeProfileId == profileId && now.connected &&
                now.cardsEnabled && !now.deleting && agent in now.agents
        }
        if (!sourceCurrent()) return BridgeSearchResult.Failed
        val saved = CatalogueStore(cache, profileId, registry, nowMs, redactor)
        val searcher = CatalogueStore(cache, profileId, registry, nowMs, redactor)
        try {
            saved.load()
            if (!sourceCurrent()) return BridgeSearchResult.Failed
            // No consent, no request: the agent is told to ask the person.
            if (!saved.snapshot.value.optedIn) return BridgeSearchResult.Answer(ConnectorSearch.catalogueOff())
            val cachedAt = saved.snapshot.value.cachedAtMs
            val stale = cachedAt == null || nowMs() - cachedAt > MAX_AGE_MS
            searcher.load()
            val online = ArrayList<RegistryEntry>()
            // Offline, slow or refused: the saved catalogue answers.
            withTimeoutOrNull(ONLINE_BUDGET_MS) {
                coroutineScope {
                    if (stale) launch { saved.refresh() }
                    launch {
                        searcher.refresh(query = query)
                        val answer = searcher.snapshot.value
                        if (answer.query == query && answer.status == CatalogueStatus.Ready) online += answer.entries
                    }
                }
            }
            if (!sourceCurrent()) return BridgeSearchResult.Failed
            val list = saved.snapshot.value
            var entries: List<RegistryEntry>? =
                if (list.cachedAtMs == null && list.entries.isEmpty()) null else list.entries
            if (entries == null && online.isNotEmpty()) entries = emptyList()
            if (entries != null && online.isNotEmpty()) {
                val seen = HashSet<String>()
                entries = (online + entries).filter { seen.add(it.name) }
            }
            val connected =
                if (entries != null) inventory(selected, agent, folder) ?: return BridgeSearchResult.Failed else null
            return when (val answer = ConnectorSearch.search(arguments, entries, connected.orUnknown(), redactor)) {
                ConnectorSearchAnswer.Invalid -> BridgeSearchResult.BadRequest
                is ConnectorSearchAnswer.Reply -> BridgeSearchResult.Answer(answer.json)
            }
        } finally {
            searcher.dispose()
            saved.dispose()
        }
    }

    /** Wraps "unknown" so a null inventory is distinct from a source change. */
    private class Inventory(val names: Map<String, Boolean>?)

    private fun Inventory?.orUnknown(): Map<String, Boolean>? = this?.names

    /** The server's MCP list for an exact source; Inventory(null) when unknown; null when the source changed. */
    private suspend fun inventory(selected: SearchContext, agent: SearchAgent, folder: String): Inventory? {
        val exact =
            agent.usesServerInventory && selected.directory == folder && selected.workspace == null &&
                selected.serverCatalog
        if (!exact) return Inventory(null)
        fun locationSame(): Boolean {
            val now = environment.context() ?: return false
            return now.transport == selected.transport && now.locationRevision == selected.locationRevision &&
                now.directory == selected.directory && now.workspace == selected.workspace
        }
        val operations = environment.operations() ?: return Inventory(null)
        if (!locationSame()) return null
        val rows = withTimeoutOrNull(INVENTORY_BUDGET_MS) { operations.mcpServers() }
        if (!locationSame()) return null
        return when (rows) {
            is Outcome.Ok -> Inventory(rows.value.associate { it.name to (it.status == "connected") })
            else -> Inventory(null)
        }
    }

    public companion object {
        /** A saved catalogue older than this is refreshed before a search. */
        public const val MAX_AGE_MS: Long = 24 * 60 * 60 * 1000L

        /** Total time the online parts may take, inside the helper's 5 s request deadline. */
        public const val ONLINE_BUDGET_MS: Long = 4_000

        public const val INVENTORY_BUDGET_MS: Long = 900
        private val CONTROL = Regex("""[\x00-\x1f\x7f]""")
    }
}
