package io.github.eslamasabry.oc.engine.integrations.catalog

import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** The project location a request is pinned to (OpenCode 2 `location[directory]` / `location[workspace]`). */
public data class CatalogLocation(val directory: String?, val workspace: String?) {
    /** The location query, without empty parts. */
    public fun query(): Map<String, String> =
        buildMap {
            directory?.takeIf { it.isNotEmpty() }?.let { put("location[directory]", it) }
            workspace?.takeIf { it.isNotEmpty() }?.let { put("location[workspace]", it) }
        }
}

/** One authenticated request on the profile's connection; the path is relative to the `/api` prefix. */
public data class CatalogRequest(
    val method: String,
    val path: String,
    val query: Map<String, String> = emptyMap(),
    val body: JsonElement? = null,
)

/** A reply with its status. [body] is the decoded JSON, or null when the body was empty or not JSON. */
public data class CatalogReply(val status: Int, val body: JsonElement?) {
    public val isSuccess: Boolean get() = status in 200..299

    /** The server's symbolic tag (`_tag` or `code`), for logic only; never shown. */
    public val tag: String?
        get() {
            val obj = body as? JsonObject ?: return null
            return ((obj["_tag"] ?: obj["code"]) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
        }
}

/**
 * The port to the server. Transport problems (nothing answered) come back as [Outcome.Failed] with a flat core
 * Failure; every answered request, including 4xx/5xx, comes back as a [CatalogReply] so the rules can read its status.
 * The binding adds authentication; credentials never appear in a [CatalogRequest].
 */
public fun interface CatalogTransport {
    public suspend fun send(request: CatalogRequest): Outcome<CatalogReply>
}

/** A refresh hint forwarded from a server event. Carries only fixed fields, never server prose. */
public data class CatalogHint(val type: String, val properties: Map<String, String> = emptyMap())
