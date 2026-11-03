package io.github.eslamasabry.oc.engine.connectors.registry

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.security.Redactor
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * PORT: one unauthenticated GET of the public MCP registry page (Dart `SetupRegistryClient.fetch` transport).
 *
 * The Android adapter requests [ENDPOINT] with `limit=100`, `version=latest` and, when [search] is non-null,
 * `search=<search>`; `Accept: application/json`; never follows redirects; 15 s connect/send/receive timeouts and a 30 s
 * overall deadline; only status 200 is accepted; the body is read up to [MAX_RESPONSE_BYTES] (more is a failure). It
 * must never reuse a server's authenticated client. Any failure is a `Failure` value (no exception text).
 */
public fun interface RegistryTransport {
    public suspend fun get(search: String?): Outcome<String>

    public companion object {
        public const val ENDPOINT: String = "https://registry.modelcontextprotocol.io/v0.1/servers"
        public const val MAX_RESPONSE_BYTES: Int = 512 * 1024
        public const val MAX_ENTRIES: Int = 100
    }
}

/** The registry the catalogue and connector search read (Dart `SetupRegistryClient`). */
public fun interface RegistryClient {
    /** [search] narrows the list on the registry's side; it is the person's own words, never a credential. */
    public suspend fun fetch(search: String?): Outcome<List<RegistryEntry>>
}

/** [RegistryClient] over a [RegistryTransport]: validates the search term and decodes the page. */
public class PublicRegistryClient(private val transport: RegistryTransport, private val redactor: Redactor) :
    RegistryClient {
    override suspend fun fetch(search: String?): Outcome<List<RegistryEntry>> =
        when (val body = transport.get(searchTerm(search, redactor))) {
            is Outcome.Failed -> body
            is Outcome.Ok -> decodeRegistryPage(body.value, redactor)
        }
}

/** A term the registry may see: trimmed, ≤ 100 characters, dropped when it looks like a credential. */
public fun searchTerm(value: String?, redactor: Redactor): String? {
    val term = value?.trim().orEmpty()
    if (term.isEmpty() || term.length > 100 || redactor.containsSecret(term)) return null
    return term
}

/**
 * Decodes one registry page (`{"servers":[{"server":{…},"_meta":{…}}]}`): at most 100 rows, deleted and deprecated
 * listings skipped, unusable listings dropped. A body that is not that shape is `UnreadableReply`.
 */
public fun decodeRegistryPage(body: String, redactor: Redactor): Outcome<List<RegistryEntry>> {
    if (body.encodeToByteArray().size > RegistryTransport.MAX_RESPONSE_BYTES) return Failure.UnreadableReply().failed()
    val decoded =
        try {
            Json.parseToJsonElement(body)
        } catch (_: SerializationException) {
            return Failure.UnreadableReply().failed()
        } catch (_: IllegalArgumentException) {
            return Failure.UnreadableReply().failed()
        }
    val servers = (decoded as? JsonObject)?.get("servers") as? JsonArray ?: return Failure.UnreadableReply().failed()
    val out = ArrayList<RegistryEntry>()
    for (item in servers.take(RegistryTransport.MAX_ENTRIES)) {
        if (item !is JsonObject) continue
        val official = (item["_meta"] as? JsonObject)?.get("io.modelcontextprotocol.registry/official") as? JsonObject
        val status = official?.string("status")
        if (status == "deleted" || status == "deprecated") continue
        RegistryEntry.fromJson(item["server"], redactor)?.let(out::add)
    }
    return out.toList().ok()
}
