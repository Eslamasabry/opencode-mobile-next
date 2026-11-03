package io.github.eslamasabry.oc.engine.connectors

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.connectors.bridge.BodyRead
import io.github.eslamasabry.oc.engine.connectors.bridge.BoundLoopback
import io.github.eslamasabry.oc.engine.connectors.bridge.BridgeBody
import io.github.eslamasabry.oc.engine.connectors.bridge.BridgeReply
import io.github.eslamasabry.oc.engine.connectors.bridge.BridgeRequest
import io.github.eslamasabry.oc.engine.connectors.bridge.BridgeSearchResult
import io.github.eslamasabry.oc.engine.connectors.bridge.ConnectorSearchBridge
import io.github.eslamasabry.oc.engine.connectors.bridge.LoopbackServerPort
import io.github.eslamasabry.oc.engine.connectors.bridge.SecureRandomBytes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A loopback "socket" that hands requests straight to the handler; null after close means connection refused. */
internal class FakeLoopback(private val port: Int = 40_000 + Random.nextInt(1000)) : LoopbackServerPort {
    var handler: (suspend (BridgeRequest) -> BridgeReply?)? = null
    var closes = 0

    override suspend fun bind(handler: suspend (BridgeRequest) -> BridgeReply?): Outcome<BoundLoopback> {
        this.handler = handler
        return Outcome.Ok(
            object : BoundLoopback {
                override val port: Int = this@FakeLoopback.port

                override suspend fun close() {
                    closes++
                    this@FakeLoopback.handler = null
                }
            },
        )
    }

    /** Null: the connection was refused (Dart SocketException). */
    suspend fun send(request: BridgeRequest): BridgeReply? = handler?.invoke(request)
}

internal val secureRandom = SecureRandomBytes { Random.nextBytes(it) }

internal fun bridgeRequest(
    bearer: String?,
    body: String = """{"query":"design"}""",
    method: String = "POST",
    path: String = ConnectorSearchBridge.PATH,
): BridgeRequest {
    val bytes = body.encodeToByteArray()
    return BridgeRequest(
        method = method,
        path = path,
        hasQuery = false,
        authorization = listOfNotNull(bearer?.let { "Bearer $it" }),
        contentLength = bytes.size.toLong(),
        body = BridgeBody { max -> if (bytes.size > max) BodyRead.TooLarge else BodyRead.Bytes(bytes) },
    )
}

class ConnectorSearchBridgeTest {
    private class H(scope: TestScope) {
        val socket = FakeLoopback()
        var current = true
        var search: suspend (JsonObject) -> BridgeSearchResult = { query ->
            BridgeSearchResult.Answer(buildJsonObject { put("results", buildJsonArray { add(query["query"]!!) }) })
        }
        val bridge =
            scope.async {
                (
                    ConnectorSearchBridge.start(socket, secureRandom, scope.backgroundScope, { current }) { search(it) }
                        as Outcome.Ok
                ).value
            }

        suspend fun request(
            body: String = """{"query":"design"}""",
            token: String? = null,
            authorize: Boolean = true,
            method: String = "POST",
            path: String = ConnectorSearchBridge.PATH,
        ): BridgeReply? {
            val b = bridge.await()
            return socket.send(bridgeRequest(if (authorize) token ?: b.bearer else null, body, method, path))
        }
    }

    @Test
    fun `B-AGENT-136 binds loopback with an ephemeral port and independent bearer`() = runTest {
        val h = H(this)
        val bridge = h.bridge.await()
        assertTrue(bridge.endpoint.startsWith("http://127.0.0.1:"))
        assertTrue(bridge.port > 0)
        assertTrue(bridge.endpoint.endsWith("/find-connectors"))
        assertFalse('@' in bridge.endpoint)
        assertFalse('?' in bridge.endpoint)
        assertTrue(Regex("^[a-f0-9]{64}$").matches(bridge.bearer))
        assertFalse(bridge.bearer in bridge.toString())
        assertFalse(bridge.bearer in bridge.endpoint)
        val other = H(this).bridge.await()
        assertNotEquals(bridge.bearer, other.bearer)
        other.close()
        bridge.close()
    }

    @Test
    fun `B-AGENT-136 returns only the supplied search response for an authorized map`() = runTest {
        val reply = H(this).request()!!
        assertEquals(200, reply.status)
        assertEquals(json("""{"results":["design"]}"""), json(reply.bodyText))
        assertEquals("no-store", BridgeReply.HEADERS["Cache-Control"])
    }

    @Test
    fun `B-AGENT-136 rejects missing or incorrect bearer without calling search`() = runTest {
        val h = H(this)
        var calls = 0
        h.search = {
            calls++
            BridgeSearchResult.Answer(JsonObject(emptyMap()))
        }
        assertEquals(401, h.request(authorize = false)!!.status)
        assertEquals(401, h.request(token = "wrong")!!.status)
        assertEquals(0, calls)
    }

    @Test
    fun `B-AGENT-136 accepts only POST on the fixed path`() = runTest {
        val h = H(this)
        assertEquals(405, h.request(method = "GET")!!.status)
        assertEquals(404, h.request(path = "/different")!!.status)
    }

    @Test
    fun `B-AGENT-136 rejects malformed JSON and non-map JSON`() = runTest {
        val h = H(this)
        assertEquals(400, h.request(body = "{")!!.status)
        assertEquals(400, h.request(body = "[]")!!.status)
        assertEquals(400, h.request(body = "null")!!.status)
    }

    @Test
    fun `B-AGENT-136 enforces the request limit in utf-8 bytes`() = runTest {
        val h = H(this)
        assertEquals(413, h.request(body = """{"query":"${"é".repeat(1024)}"}""")!!.status)
        assertEquals(413, h.request(body = """{"query":"${"x".repeat(2048)}"}""")!!.status)
    }

    @Test
    fun `B-AGENT-136 rejects a stale source before invoking search`() = runTest {
        val h = H(this)
        var calls = 0
        h.search = {
            calls++
            BridgeSearchResult.Answer(JsonObject(emptyMap()))
        }
        h.current = false
        assertEquals(403, h.request()!!.status)
        assertEquals(0, calls)
    }

    @Test
    fun `B-AGENT-136 rejects a source that changes during asynchronous search`() = runTest {
        val h = H(this)
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<BridgeSearchResult>()
        h.search = {
            entered.complete(Unit)
            finish.await()
        }
        val pending = async { h.request() }
        entered.await()
        h.current = false
        finish.complete(BridgeSearchResult.Answer(buildJsonObject { put("private", "old source result") }))
        val reply = pending.await()!!
        assertEquals(403, reply.status)
        assertFalse("old source result" in reply.bodyText)
    }

    @Test
    fun `B-AGENT-136 limits concurrent search calls to four`() = runTest {
        val h = H(this)
        var calls = 0
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<BridgeSearchResult>()
        h.search = {
            if (++calls == 4) entered.complete(Unit)
            finish.await()
        }
        val pending = (0 until 4).map { async { h.request() } }
        entered.await()
        assertEquals(429, h.request()!!.status)
        assertEquals(4, calls)
        finish.complete(BridgeSearchResult.Answer(buildJsonObject { put("results", buildJsonArray { }) }))
        assertTrue(pending.awaitAll().all { it!!.status == 200 })
        runCurrent()
        assertEquals(200, h.request()!!.status)
    }

    @Test
    fun `B-AGENT-136 returns a generic error without leaking search failures`() = runTest {
        val h = H(this)
        h.search = { error("secret-token-and-host-details") }
        val reply = h.request()!!
        assertEquals(500, reply.status)
        assertFalse("secret-token" in reply.bodyText)
    }

    @Test
    fun `B-AGENT-136 rejects a response exceeding 16384 utf-8 bytes`() = runTest {
        val h = H(this)
        h.search = { BridgeSearchResult.Answer(buildJsonObject { put("results", "é".repeat(8192)) }) }
        val reply = h.request()!!
        assertEquals(502, reply.status)
        assertTrue(reply.body.size <= 16384)
    }

    @Test
    fun `B-AGENT-136 times out search after five seconds without releasing its slot`() = runTest {
        val h = H(this)
        var calls = 0
        val finish = CompletableDeferred<BridgeSearchResult>()
        h.search = {
            calls++
            finish.await()
        }
        val pending = (0 until 4).map { async { h.request() } }
        advanceTimeBy(ConnectorSearchBridge.DEADLINE_MS + 1)
        assertTrue(pending.awaitAll().all { it!!.status == 504 })
        assertEquals(429, h.request()!!.status)
        assertEquals(4, calls)
        finish.complete(BridgeSearchResult.Answer(JsonObject(emptyMap())))
        runCurrent()
        assertEquals(200, h.request()!!.status)
    }

    @Test
    fun `B-AGENT-136 close is idempotent and stops accepting connections`() = runTest {
        val h = H(this)
        val bridge = h.bridge.await()
        bridge.close()
        bridge.close()
        assertEquals(1, h.socket.closes)
        assertNull(h.request())
        assertNull(bridge.handle(bridgeRequest(bridge.bearer)))
    }
}
