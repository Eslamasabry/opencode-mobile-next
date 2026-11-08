package io.github.eslamasabry.oc.feature.integrations.catalog

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.McpServerDraft
import io.github.eslamasabry.oc.core.model.McpServerInfo
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.integrations.catalog.McpCatalogControllers
import io.github.eslamasabry.oc.engine.integrations.catalog.McpCatalogEntry
import io.github.eslamasabry.oc.engine.integrations.catalog.McpCatalogHost
import io.github.eslamasabry.oc.engine.integrations.catalog.parseMcpCatalog
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** Registry listings of every kind the catalogue shows: hosted, Node, Python, Docker, nothing to start. */
internal val REGISTRY: JsonObject =
    Json.parseToJsonElement(
            """
            {"servers": [
              {"server": {"name": "io.github.acme/weather-mcp", "version": "1.2.0", "title": "Weather",
                "description": "Forecasts and alerts for any city.",
                "packages": [{"registryType": "npm", "identifier": "@acme/weather-mcp", "version": "1.2.0",
                  "transport": {"type": "stdio"},
                  "environmentVariables": [{"name": "WEATHER_TOKEN", "isRequired": true, "isSecret": true}]}]}},
              {"server": {"name": "com.example/docs", "version": "2.0.0", "title": "Docs search",
                "description": "Search the product docs.",
                "remotes": [{"type": "streamable-http", "url": "https://mcp.example.com/docs",
                  "headers": [{"name": "X-Docs-Key", "isRequired": true, "isSecret": true}]}]}},
              {"server": {"name": "io.github.notes/notes-server", "version": "0.3.1", "title": "Notes",
                "description": "Read and write local notes.",
                "packages": [{"registryType": "pypi", "identifier": "notes-server", "version": "0.3.1",
                  "packageArguments": [{"isRequired": true}]}]}},
              {"server": {"name": "io.github.boxes/box", "version": "1.0.0", "title": "Box runner",
                "description": "Runs tools in a container.",
                "packages": [{"registryType": "oci", "identifier": "boxes/box", "version": "1.0.0"}]}},
              {"server": {"name": "io.github.empty/listing", "version": "0.1.0", "title": "Empty listing",
                "description": "Lists no way to start it."}}
            ]}
            """
        )
        .jsonObject

internal fun registryEntries(): List<McpCatalogEntry> = parseMcpCatalog(REGISTRY, Redactor())

internal fun entry(title: String): McpCatalogEntry = registryEntries().first { it.title == title }

/** A scripted catalogue host: an opted-in cache, a fixed registry, and saves that can fail. */
internal class FakeCatalogHost(optedIn: Boolean = true) : McpCatalogHost {
    var cache: JsonObject? = buildJsonObject {
        put("version", 1)
        put("optedIn", optedIn)
        put("entries", JsonArray(emptyList()))
    }
    var saveFails = false
    val saved = mutableListOf<JsonObject>()
    var installed = setOf<String>()
    private val source = Any()

    override suspend fun loadCache(profile: String): Outcome<JsonObject?> = Outcome.Ok(cache)

    override suspend fun saveCache(profile: String, value: JsonObject): Outcome<Unit> {
        if (saveFails) return Outcome.Failed(Failure.StorageRefused())
        saved += value
        cache = value
        return Outcome.Ok(Unit)
    }

    override suspend fun saveRefreshedCache(profile: String, value: JsonObject): Outcome<Unit> =
        saveCache(profile, value)

    override suspend fun fetchRegistry(query: String?): Outcome<JsonObject> = Outcome.Ok(REGISTRY)

    override fun sourceIdentity(profile: String): Any = source

    override fun current(profile: String, directory: String?, workspace: String?): Boolean = true

    override fun capabilities(profile: String): ServerCapabilities =
        ServerCapabilities(serverCatalog = true, mcpRuntimeAdds = true)

    override suspend fun inventory(
        profile: String,
        directory: String?,
        workspace: String?,
    ): Outcome<List<McpServerInfo>> =
        Outcome.Ok(installed.map { McpServerInfo(it, "connected", null) })

    override suspend fun add(
        profile: String,
        directory: String?,
        workspace: String?,
        draft: McpServerDraft,
    ): Outcome<Unit> = Outcome.Ok(Unit)
}

internal object FixedClock : Clock {
    override fun now(): Instant = Instant.fromEpochSeconds(1_800_000_000)
}

internal fun controllers(host: McpCatalogHost): McpCatalogControllers =
    McpCatalogControllers(host, Redactor(), FixedClock)

internal class RecordingNavigator : AppNavigator {
    val calls = mutableListOf<String>()
    override val top: StateFlow<AppRoute?> = MutableStateFlow(null)

    override fun open(route: AppRoute) {
        calls += "open $route"
    }

    override fun openChat(ref: ConversationRef, focusComposer: Boolean) {
        calls += "chat"
    }

    override fun land() {
        calls += "land"
    }

    override fun replaceRoot(root: AppRoute) {
        calls += "root"
    }

    override fun back() {
        calls += "back"
    }
}

internal class RecordingActions : McpCatalogActions {
    val calls = mutableListOf<String>()

    override fun back() {
        calls += "back"
    }

    override fun consent(enabled: Boolean) {
        calls += "consent $enabled"
    }

    override fun refresh() {
        calls += "refresh"
    }

    override fun search(query: String) {
        calls += "search $query"
    }

    override fun select(id: String) {
        calls += "select $id"
    }

    override fun add(name: String, headers: Map<String, String>, environment: Map<String, String>) {
        calls += "add $name"
    }

    override fun check() {
        calls += "check"
    }

    override fun forget() {
        calls += "forget"
    }
}
