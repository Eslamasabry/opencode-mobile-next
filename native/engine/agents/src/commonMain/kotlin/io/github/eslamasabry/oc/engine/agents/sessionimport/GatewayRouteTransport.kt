package io.github.eslamasabry.oc.engine.agents.sessionimport

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.ApiRouteGateway
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * The route transport over the connected gateway: the gateway owns the origin and the Basic credentials, so the
 * request carries none ([Oc2Routes] is built without auth here). A body that is not JSON is a null body.
 */
public class GatewayRouteTransport(private val gateway: ApiRouteGateway) : RouteTransport {
    override suspend fun send(request: RouteRequest): Outcome<RouteAnswer> {
        val path = request.path.removePrefix("/api") // the gateway adds the prefix itself
        return when (val reply = gateway.apiRoute(request.method, path, request.body?.toString())) {
            is Outcome.Failed -> reply.failure.failed()
            is Outcome.Ok -> RouteAnswer(reply.value.status, reply.value.body?.let(::parse)).ok()
        }
    }

    private fun parse(text: String): JsonElement? =
        try {
            Json.parseToJsonElement(text)
        } catch (e: kotlinx.serialization.SerializationException) {
            null
        }
}
