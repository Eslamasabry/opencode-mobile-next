package io.github.eslamasabry.oc.engine.integrations.catalog

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.McpGateway
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * Metadata resolution never accepts addresses or commands from a card; the id must match the public
 * catalogue.
 */
public interface McpChatConnectors {
    public suspend fun resolve(
        profile: String,
        catalogId: String,
        current: () -> Boolean,
    ): McpChatSuggestion?

    public fun create(
        item: McpChatSuggestion,
        gateway: suspend () -> McpGateway,
        capabilities: () -> ServerCapabilities,
        current: () -> Boolean,
    ): McpChatConnect
}

@ContributesIntoSet(AppScope::class, binding = binding<McpChatConnectors>())
@Inject
public class CatalogMcpChatConnectors(
    private val host: McpCatalogHost,
    private val redactor: Redactor,
) : McpChatConnectors {
    override suspend fun resolve(
        profile: String,
        catalogId: String,
        current: () -> Boolean,
    ): McpChatSuggestion? {
        if (
            !current() ||
                !Regex("^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$").matches(catalogId) ||
                redactor.containsSecret(catalogId)
        )
            return null
        val cache = (host.loadCache(profile) as? Outcome.Ok)?.value ?: return null
        if (!current() || (cache["optedIn"] as? JsonPrimitive)?.booleanOrNull != true) return null
        var item = parseMcpCatalog(cache, redactor).firstOrNull { it.id == catalogId }
        if (item == null) {
            val result = withTimeoutOrNull(4_000) { host.fetchRegistry(catalogId) }
            if (!current()) return null
            item =
                (result as? Outcome.Ok)
                    ?.value
                    ?.let { parseMcpCatalog(it, redactor) }
                    ?.firstOrNull { it.id == catalogId }
        }
        val chosen = item ?: return null
        val latest = (host.loadCache(profile) as? Outcome.Ok)?.value
        if (!current() || (latest?.get("optedIn") as? JsonPrimitive)?.booleanOrNull != true)
            return null
        return McpChatSuggestion(
            chosen.serverName,
            chosen.draft,
            chosen.runtime == CatalogRuntime.Hosted,
            needsKey =
                chosen.draft?.let { it.headers.isNotEmpty() || it.environment.isNotEmpty() }
                    ?: false,
            needsSettings = chosen.requiresArguments,
        )
    }

    override fun create(
        item: McpChatSuggestion,
        gateway: suspend () -> McpGateway,
        capabilities: () -> ServerCapabilities,
        current: () -> Boolean,
    ): McpChatConnect = McpChatConnect(item, gateway, capabilities, current)
}
