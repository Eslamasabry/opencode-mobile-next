package io.github.eslamasabry.oc.core.net.okhttp

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.net.HttpClientFactory
import io.github.eslamasabry.oc.core.net.NetClient
import io.github.eslamasabry.oc.core.net.TransportError
import io.github.eslamasabry.oc.core.net.TransportSpec
import io.github.eslamasabry.oc.core.security.HostClass
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Ports test/perf_transport_decode_test.dart (B-PROTO-072). Real sockets, so real time (runBlocking, as in
 * StaleConnectionTest). Deviation: the Dart test paused 4 s; the native pool deliberately keeps an idle socket only
 * [IDLE_KEEP_ALIVE_MS] (4 s, under the server's 6 s idle close, B-PROTO-128), so the pause here is 3 s.
 */
class TransportReuseDecodeTest {
    private var server: CountingServer? = null

    @AfterTest
    fun stop() {
        server?.close()
    }

    @Test
    fun `B-PROTO-072 the native transport reuses its socket after a short pause`() = runBlocking {
        val on = CountingServer().also { server = it }
        on.handler = { _, _ -> Reply.Ok("{\"healthy\":true}") }
        val net = HttpClientFactory(OkHttpEngineFactory()).create(
            TransportSpec(ServerOrigin("http", "127.0.0.1", on.port), HostClass.Loopback, cleartextPermitted = true),
        )
        suspend fun health() = assertIs<Outcome.Ok<String>>(net.exchange({ url(on.url("/api/health")) }) { it.bodyAsText() }).value
        assertTrue("healthy" in health())
        Thread.sleep(3_000)
        assertTrue("healthy" in health())
        assertEquals(1, on.connectionsSeen.get(), "one TCP connection for both requests")
        net.close()
        net.close() // closing twice is harmless
    }

    @Test
    fun `B-PROTO-072 large JSON decoding preserves data with and without a length header`() = runBlocking {
        val rows = buildJsonArray {
            repeat(2000) { i -> add(buildJsonObject { put("id", i); put("text", "tail $i") }) }
        }
        val payload = rows.toString()
        assertTrue(payload.encodeToByteArray().size > 50 * 1024)
        for (withLength in listOf(false, true)) {
            val headers =
                if (withLength) {
                    headersOf(HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.ContentLength to listOf("${payload.encodeToByteArray().size}"))
                } else {
                    headersOf(HttpHeaders.ContentType, "application/json")
                }
            val net = NetClient(HttpClient(MockEngine { respond(payload, HttpStatusCode.OK, headers) }) { expectSuccess = false }, { TransportError.Other })
            val decoded = assertIs<Outcome.Ok<JsonArray>>(net.exchange({ url("http://localhost/sessions") }) { Json.parseToJsonElement(it.bodyAsText()) as JsonArray }).value
            assertEquals(rows, decoded)
            net.close()
        }
    }
}
