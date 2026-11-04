package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.domain.ConnectionCredentials
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.net.NetClient
import io.github.eslamasabry.oc.core.net.TransportError
import io.github.eslamasabry.oc.core.security.Redactor
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** One recorded request, its body already read (the engine body is single-use). */
internal data class Seen(val request: HttpRequestData, val body: String) {
    val method: String get() = request.method.value
    val path: String get() = request.url.encodedPath
    val query: Map<String, String> get() = request.url.parameters.entries().associate { it.key to it.value.single() }
    val json: JsonElement? get() = body.takeIf { it.isNotEmpty() }?.let { Json.parseToJsonElement(it) }
}

internal fun HttpRequestData.text(): String =
    when (val content = body) {
        is TextContent -> content.text
        is OutgoingContent.ByteArrayContent -> content.bytes().decodeToString()
        else -> ""
    }

internal fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK): HttpResponseData =
    respond(body, status, headersOf("Content-Type", "application/json"))

/** A real Oc2Gateway over a MockEngine; [answer] sees every request after it was recorded in [seen]. */
internal class RowServer(
    dialect: Oc2Dialect? = Oc2Dialect.Stable,
    location: ServerLocation = ServerLocation.Default,
    credentials: ConnectionCredentials = ConnectionCredentials("opencode", "fixture"),
    origin: ServerOrigin = ServerOrigin("https", "example.test", 443),
    classify: (Throwable) -> TransportError = { TransportError.Other },
    answer: suspend MockRequestHandleScope.(Seen) -> HttpResponseData,
) {
    val seen = mutableListOf<Seen>()
    val gateway: Oc2Gateway =
        Oc2Gateway(
            NetClient(
                HttpClient(
                    MockEngine { request ->
                        val one = Seen(request, request.text())
                        seen += one
                        answer(one)
                    }
                ) {
                    expectSuccess = false
                    followRedirects = false
                },
                classify,
            ),
            origin,
            credentials,
            object : Clock {
                override fun now(): Instant = Instant.fromEpochMilliseconds(1_000)
            },
            Redactor(),
            location,
            initialDialect = dialect,
        )
}
