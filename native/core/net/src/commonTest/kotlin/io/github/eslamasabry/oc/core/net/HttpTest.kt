package io.github.eslamasabry.oc.core.net

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.net.stream.BackoffPolicy
import io.github.eslamasabry.oc.core.net.stream.KtorSseConnector
import io.github.eslamasabry.oc.core.net.stream.SseConnectResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HttpTest {
    @Test
    fun `N4-D6 OpenCode 2 policy is 1 2 4 8 16 16 s with no jitter`() {
        val r = Random(1)
        assertEquals(listOf(1_000L, 2_000, 4_000, 8_000, 16_000, 16_000, 16_000), (1..7).map { BackoffPolicy.OpenCode2.delayMs(it, false, r) })
    }

    @Test
    fun `PB-51 hidden pace keeps two quick tries then waits 60 to 120 s`() {
        val r = Random(3)
        assertEquals(listOf(1_000L, 2_000), (1..2).map { BackoffPolicy.OpenCode2.delayMs(it, true, r) })
        repeat(1_000) { assertTrue(BackoffPolicy.OpenCode2.delayMs(3 + it % 9, true, r) in 60_000L..120_000L) }
    }

    @Test
    fun `N4-D6 Gas City policy is jittered and capped at 30 s`() {
        val r = Random(5)
        repeat(1_000) { attempt ->
            val d = BackoffPolicy.GasCity.delayMs(1 + attempt % 20, false, r)
            assertTrue(d in 250L..30_000L, "$d")
        }
    }

    @Test
    fun `B-PROTO-129 statuses become plain failures`() {
        fun f(code: Int) = StatusMapping.failureFor(code)
        assertIs<Failure.SignIn>(f(401))
        assertIs<Failure.SignIn>(f(403))
        assertIs<Failure.NotFound>(f(404))
        assertIs<Failure.NotFound>(f(410))
        assertIs<Failure.Conflict>(f(409))
        assertIs<Failure.TimedOut>(f(408))
        assertIs<Failure.Busy>(f(429))
        assertIs<Failure.Busy>(f(503))
        assertEquals(302, assertIs<Failure.Rejected>(f(302)).status)
        assertEquals(422, assertIs<Failure.Rejected>(f(422)).status)
        assertEquals(500, assertIs<Failure.Server>(f(500)).status)
        assertEquals("HTTP 500", f(500).details?.text) // never server text
    }

    @Test
    fun `B-PROTO-128 a mutation that may have landed is dispatch-uncertain, a read is a network failure`() {
        assertEquals(CallKind.Read, CallKind.of(HttpMethod.Get))
        assertEquals(CallKind.Read, CallKind.of(HttpMethod.Head))
        listOf(HttpMethod.Post, HttpMethod.Put, HttpMethod.Patch, HttpMethod.Delete).forEach {
            assertEquals(CallKind.Mutation, CallKind.of(it))
        }
        val e = IllegalStateException("secret sk-123 in text")
        val uncertain = TransportFailures.of(CallKind.Mutation, TransportError.Other, e)
        assertEquals(RefusalReason.DispatchUncertain, assertIs<Failure.Refused>(uncertain).reason)
        assertTrue("sk-123" !in uncertain.details!!.text)
        assertIs<Failure.Network>(TransportFailures.of(CallKind.Mutation, TransportError.ConnectFailed, e))
        assertIs<Failure.Network>(TransportFailures.of(CallKind.Read, TransportError.Other, e))
        assertIs<Failure.TimedOut>(TransportFailures.of(CallKind.Read, TransportError.Timeout, e))
    }

    private fun sseClient(engine: MockEngine) = HttpClient(engine) {
        expectSuccess = false
        install(HttpTimeout)
    }

    @Test
    fun `B-PROTO-033 the SSE request asks for an event stream with no request timeout and reads the body`() = runTest {
        var accept: String? = null
        var lastId: String? = null
        val engine = MockEngine { request ->
            accept = request.headers[HttpHeaders.Accept]
            lastId = request.headers["Last-Event-ID"]
            respond("data: hi\n\n", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/event-stream"))
        }
        val connector = KtorSseConnector(sseClient(engine)) { id -> url("http://127.0.0.1/api/event"); id?.let { headers.append("Last-Event-ID", it) } }
        val read = StringBuilder()
        val result = connector.connect("7") { body ->
            val buffer = ByteArray(64)
            while (true) {
                val n = body.read(buffer)
                if (n < 0) break
                read.append(buffer.decodeToString(0, n))
            }
        }
        assertEquals(SseConnectResult.Ended, result)
        assertEquals("text/event-stream", accept)
        assertEquals("7", lastId)
        assertEquals("data: hi\n\n", read.toString())
    }

    @Test
    fun `N4-D6 401 stops the stream, 503 and wrong content type retry`() = runTest {
        var status = HttpStatusCode.Unauthorized
        var type = "text/event-stream"
        val engine = MockEngine { respond("", status, headersOf(HttpHeaders.ContentType, type)) }
        val connector = KtorSseConnector(sseClient(engine)) { url("http://127.0.0.1/api/event") }
        val noSession: suspend (io.github.eslamasabry.oc.core.net.stream.SseBody) -> Unit = { error("not opened") }
        val signIn = assertIs<SseConnectResult.Failed>(connector.connect(null, noSession))
        assertTrue(signIn.stop)
        assertIs<Failure.SignIn>(signIn.failure)
        status = HttpStatusCode.ServiceUnavailable
        val busy = assertIs<SseConnectResult.Failed>(connector.connect(null, noSession))
        assertTrue(!busy.stop)
        status = HttpStatusCode.OK
        type = "text/html"
        val html = assertIs<SseConnectResult.Failed>(connector.connect(null, noSession))
        assertIs<Failure.UnreadableReply>(html.failure)
        assertTrue(!html.stop)
    }
}
