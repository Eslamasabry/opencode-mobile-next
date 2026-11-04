package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.WebSearchProvider
import io.github.eslamasabry.oc.core.model.WebSearchResponse
import io.github.eslamasabry.oc.core.model.WebSearchResult
import io.ktor.http.HttpMethod
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put

/**
 * Flutter `Api2Gateway.webSearchProviders` / `searchWeb` (B-PROTO-125). Explicit, read-only search: no history
 * mutation, no cursor. Failures carry no remote body, URL, query or credential (Dart WebSearchFailure kinds):
 * authentication → SignIn, 404/503 → Unavailable(webSearch), other HTTP → its status failure, a malformed or
 * other-location reply → UnreadableReply, an empty or oversize query → Rejected without a request.
 */
internal object Oc2WebSearch {
    const val MAX_QUERY: Int = 1000
    private val Control = Regex("[\\u0000-\\u001f\\u007f]")

    fun failureOf(failure: Failure): Failure =
        when {
            failure is Failure.SignIn -> failure
            failure is Failure.NotFound ||
                (failure is Failure.Busy && failure.kind != io.github.eslamasabry.oc.core.common.BusyKind.RateLimited) ||
                (failure is Failure.Server && failure.status == 503) -> Failure.Unavailable(FeatureId("webSearch"))
            failure is Failure.UnreadableReply -> Failure.UnreadableReply()
            else -> failure
        }

    /** The reply must name the location the request was sent for; the gateway must still be there. */
    fun locationOk(root: JsonElement, sent: ServerLocation, now: ServerLocation): Boolean {
        val place = (root as? JsonObject)?.get("location") as? JsonObject ?: return false
        val directory = (place["directory"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (directory.isNullOrEmpty()) return false
        if (sent.directory != null && directory != sent.directory) return false
        if (sent.workspace != null && (place["workspaceID"] as? JsonPrimitive)?.content != sent.workspace) return false
        return sent == now
    }

    fun providers(root: JsonElement): ImmutableList<WebSearchProvider>? {
        val data = (root as? JsonObject)?.get("data") as? JsonArray ?: return null
        if (data.size > 100) return null
        val seen = mutableSetOf<String>()
        return data.map { item ->
            val obj = item as? JsonObject ?: return null
            val id = (obj["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
            val name = (obj["name"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
            if (id.isBlank() || id.length > 200 || name.isBlank() || name.length > 200 || !seen.add(id) ||
                Control.containsMatchIn(id + name))
                return null
            WebSearchProvider(id, name)
        }.toImmutableList()
    }

    fun response(root: JsonElement, providerId: String): WebSearchResponse? {
        val data = (root as? JsonObject)?.get("data") as? JsonObject ?: return null
        if ((data["providerID"] as? JsonPrimitive)?.content != providerId) return null
        val rows = data["results"] as? JsonArray ?: return null
        if (rows.size > 100) return null
        val results = rows.map { item ->
            val obj = item as? JsonObject ?: return null
            val url = (obj["url"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
            val time = obj["time"] as? JsonObject ?: return null
            val title = optionalText(obj["title"]) ?: return null
            val content = optionalText(obj["content"]) ?: return null
            if (url.length > 8192 || (title.value?.length ?: 0) > 10_000 || (content.value?.length ?: 0) > 100_000)
                return null
            val published = time["published"]
            if (published != null && published != JsonNull &&
                ((published as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull?.isFinite() != true))
                return null
            WebSearchResult(url, title.value, content.value)
        }
        return WebSearchResponse(providerId, results.toImmutableList())
    }

    private class Optional(val value: String?)

    /** Absent/null → Optional(null); a string → its value; any other type → null (malformed). */
    private fun optionalText(value: JsonElement?): Optional? =
        when {
            value == null || value == JsonNull -> Optional(null)
            value is JsonPrimitive && value.isString -> Optional(value.content)
            else -> null
        }
}

internal suspend fun Oc2Gateway.searchProviders(): Outcome<ImmutableList<WebSearchProvider>> {
    val sent = location.value
    return when (val reply = json("/websearch/provider", query = loc(sent))) {
        is Outcome.Failed -> Oc2WebSearch.failureOf(reply.failure).failed()
        is Outcome.Ok ->
            if (!Oc2WebSearch.locationOk(reply.value, sent, location.value)) Failure.UnreadableReply().failed()
            else Oc2WebSearch.providers(reply.value)?.ok() ?: Failure.UnreadableReply().failed()
    }
}

internal suspend fun Oc2Gateway.search(query: String, providerId: String): Outcome<WebSearchResponse> {
    if (query.isBlank() || query.length > Oc2WebSearch.MAX_QUERY || providerId.isEmpty() || providerId.length > 200)
        return Failure.Rejected(null).failed()
    val sent = location.value
    val reply =
        json(
            "/websearch",
            HttpMethod.Post,
            buildJsonObject {
                put("query", query.trim())
                put("providerID", providerId)
            },
            loc(sent),
        )
    return when (reply) {
        is Outcome.Failed -> Oc2WebSearch.failureOf(reply.failure).failed()
        is Outcome.Ok ->
            if (!Oc2WebSearch.locationOk(reply.value, sent, location.value)) Failure.UnreadableReply().failed()
            else Oc2WebSearch.response(reply.value, providerId)?.ok() ?: Failure.UnreadableReply().failed()
    }
}
