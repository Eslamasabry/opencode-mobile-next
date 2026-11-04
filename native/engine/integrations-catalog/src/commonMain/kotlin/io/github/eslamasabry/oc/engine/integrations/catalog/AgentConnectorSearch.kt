package io.github.eslamasabry.oc.engine.integrations.catalog

import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.security.Redactor
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*

/**
 * A private live helper endpoint. Its bearer is never included by toString, logging or public
 * snapshots.
 */
public interface ConnectorSearchEndpoint {
    public val endpoint: String
    public val bearer: String

    public suspend fun close()
}

public fun interface ConnectorSearchServers {
    public suspend fun open(
        current: () -> Boolean,
        search: suspend (JsonObject) -> JsonObject,
    ): ConnectorSearchEndpoint
}

/**
 * Read-only Claude search. Its helper cwd never grants permission to borrow another project's
 * inventory.
 */
@Inject
public class AgentConnectorSearch(
    private val host: McpCatalogHost,
    private val redactor: Redactor,
    private val clock: Clock,
) {
    public suspend fun find(
        profile: String,
        request: JsonObject,
        current: () -> Boolean,
    ): JsonObject {
        require(request.keys.all { it in setOf("arguments", "directory") })
        val folder = request.string("directory") ?: throw IllegalArgumentException()
        require(
            folder.startsWith('/') &&
                folder.length <= 4096 &&
                !Regex("[\\x00-\\x1f\\x7f]").containsMatchIn(folder)
        )
        val arguments = request["arguments"] as? JsonObject ?: throw IllegalArgumentException()
        require(searchMcpCatalog(arguments, emptyList(), null, redactor) != null)
        check(current())
        val source = host.sourceIdentity(profile)
        val read = host.loadCache(profile)
        check(current() && host.sourceIdentity(profile) === source)
        val cache = (read as? Outcome.Ok)?.value ?: return off()
        if ((cache.get("optedIn") as? JsonPrimitive)?.booleanOrNull != true) return off()
        val saved = cache.let { parseMcpCatalog(it, redactor) }
        val at =
            cache.string("cachedAt")?.let {
                try {
                    Instant.parse(it)
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
        var entries: List<McpCatalogEntry>? = saved.takeIf { at != null || it.isNotEmpty() }
        val stale =
            at == null ||
                clock.now().toEpochMilliseconds() - at.toEpochMilliseconds() > 24 * 60 * 60 * 1000L
        val query = arguments.string("query")!!.trim()
        coroutineScope {
            val refreshed = if (stale) async { host.fetchRegistry(null) } else null
            val searched = async { host.fetchRegistry(query) }
            withTimeoutOrNull(4_000) {
                val full = refreshed?.await()
                check(current() && host.sourceIdentity(profile) === source)
                if (full is Outcome.Ok && full.value["servers"] is JsonArray) {
                    val latest = parseMcpCatalog(full.value, redactor)
                    val reread = host.loadCache(profile)
                    check(current() && host.sourceIdentity(profile) === source)
                    val latestCache = (reread as? Outcome.Ok)?.value
                    if ((latestCache?.get("optedIn") as? JsonPrimitive)?.booleanOrNull == true) {
                        val payload = buildJsonObject {
                            put("version", 1)
                            put("optedIn", true)
                            put("cachedAt", clock.now().toString())
                            put("entries", encodeMcpCatalog(latest))
                        }
                        host.saveRefreshedCache(profile, payload)
                        check(current() && host.sourceIdentity(profile) === source)
                        entries = latest
                    }
                }
                val found = searched.await()
                check(current() && host.sourceIdentity(profile) === source)
                if (found is Outcome.Ok && found.value["servers"] is JsonArray) {
                    entries =
                        (parseMcpCatalog(found.value, redactor) + entries.orEmpty()).distinctBy {
                            it.id
                        }
                }
            }
            refreshed?.cancel()
            searched.cancel()
        }
        check(current() && host.sourceIdentity(profile) === source)
        val reread = host.loadCache(profile)
        check(current() && host.sourceIdentity(profile) === source)
        if (
            ((reread as? Outcome.Ok)?.value?.get("optedIn") as? JsonPrimitive)?.booleanOrNull !=
                true
        )
            return off()
        return searchMcpCatalog(arguments, entries, null, redactor)
            ?: throw IllegalArgumentException()
    }

    private fun off(): JsonObject = buildJsonObject {
        put("status", "catalogue_off")
        put(
            "message",
            "The connector catalogue is off. Ask the person to tap Turn on in this step, then search again.",
        )
        put("matches", JsonArray(emptyList()))
    }
}
