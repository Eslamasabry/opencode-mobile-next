package io.github.eslamasabry.oc.engine.integrations.catalog

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** A recording transport: answers with [status]/[payload], optionally waiting on [gate] first. */
internal class FakeTransport(var status: Int = 200, var payload: String = """{"data":[]}""") : CatalogTransport {
    val requests = mutableListOf<CatalogRequest>()
    var gate: CompletableDeferred<Unit>? = null
    var answer: ((CatalogRequest) -> Outcome<CatalogReply>)? = null

    override suspend fun send(request: CatalogRequest): Outcome<CatalogReply> {
        requests += request
        gate?.await()
        answer?.let { return it(request) }
        val body: JsonElement? = if (payload.isEmpty()) null else Json.parseToJsonElement(payload)
        return CatalogReply(status, body).ok()
    }
}

internal fun json(text: String): JsonElement = Json.parseToJsonElement(text)
