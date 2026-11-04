package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.model.*
import io.github.eslamasabry.oc.protocol.oc2.wire.*
import io.ktor.http.*
import kotlinx.collections.immutable.*
import kotlinx.serialization.json.*

/** Explicit, scoped reads only; no prompt/history cursor and no remote errors retained. */
internal class Oc2WebSearch(private val gateway: Oc2Gateway, private val location: ServerLocation) {
    private val scope =
        mapOf(
            "location[directory]" to location.directory,
            "location[workspace]" to location.workspace,
        )

    suspend fun providers(): Outcome<ImmutableList<WebSearchProvider>> {
        val reply = gateway.json("/websearch/provider", query = scope)
        if (reply is Outcome.Failed) return safeFailure(reply)
        val rows = checkedData((reply as Outcome.Ok).value) as? JsonArray ?: return unreadable()
        if (rows.size > 100) return unreadable()
        val ids = mutableSetOf<String>()
        val providers = rows.map {
            val obj = it as? JsonObject ?: return unreadable()
            val id = obj.str("id")?.takeIf(::validId) ?: return unreadable()
            val name =
                obj.str("name")?.takeIf {
                    it.isNotBlank() && it.length <= 200 && it.none { c -> c.isISOControl() }
                } ?: return unreadable()
            if (!ids.add(id)) return unreadable()
            WebSearchProvider(id, name)
        }
        return providers.toImmutableList().ok()
    }

    suspend fun search(query: String, provider: String): Outcome<WebSearchResponse> {
        val text = query.trim()
        if (text.isEmpty() || query.length > 1000 || !validId(provider))
            return Failure.Rejected(400).failed()
        val reply =
            gateway.json(
                "/websearch",
                HttpMethod.Post,
                buildJsonObject {
                    put("query", text)
                    put("providerID", provider)
                },
                scope,
            )
        if (reply is Outcome.Failed) return safeFailure(reply)
        val obj = checkedData((reply as Outcome.Ok).value) as? JsonObject ?: return unreadable()
        if (obj.str("providerID") != provider) return unreadable()
        val rows = obj["results"] as? JsonArray ?: return unreadable()
        if (rows.size > 100) return unreadable()
        val results = rows.map {
            val item = it as? JsonObject ?: return unreadable()
            val url = item.str("url")?.takeIf { it.length <= 8192 } ?: return unreadable()
            val parsed =
                try {
                    Url(url)
                } catch (_: IllegalArgumentException) {
                    return unreadable()
                }
            if (
                parsed.protocol.name !in setOf("http", "https") ||
                    parsed.host.isBlank() ||
                    parsed.user != null ||
                    parsed.password != null
            )
                return unreadable()
            val title = item.str("title")
            val content = item.str("content")
            if ((title?.length ?: 0) > 1000 || (content?.length ?: 0) > 100_000) return unreadable()
            WebSearchResult(url, title, content)
        }
        return WebSearchResponse(provider, results.toImmutableList()).ok()
    }

    private fun checkedData(value: JsonElement): JsonElement? {
        val root = value as? JsonObject ?: return null
        val place = root["location"] as? JsonObject ?: return null
        val directory = place.str("directory")?.takeIf { it.isNotEmpty() } ?: return null
        if (location.directory != null && directory != location.directory) return null
        if (location.workspace != null && place.str("workspaceID") != location.workspace)
            return null
        if (gateway.location.value != location) return null
        return root["data"]
    }

    private fun safeFailure(reply: Outcome.Failed): Outcome.Failed =
        when (reply.failure) {
            is Failure.Busy,
            is Failure.Server -> Outcome.Failed(Failure.Unavailable(FeatureId("webSearch")))
            else -> reply
        }

    private fun validId(id: String): Boolean =
        id.isNotBlank() && id.length <= 200 && id.none { it.isISOControl() }

    private fun unreadable(): Outcome.Failed = Outcome.Failed(Failure.UnreadableReply())
}
