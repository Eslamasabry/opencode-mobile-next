package io.github.eslamasabry.oc.core.net

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.SignInWhy
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HttpTest {
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
    fun `N4-D6 OpenCode 2 401 and 403 keep the backoff, 503 and wrong content type retry (plan 04 section 4_8)`() = runTest {
        var status = HttpStatusCode.Unauthorized
        var type = "text/event-stream"
        val engine = MockEngine { respond("", status, headersOf(HttpHeaders.ContentType, type)) }
        val connector = KtorSseConnector(sseClient(engine)) { url("http://127.0.0.1/api/event") }
        val noSession: suspend (io.github.eslamasabry.oc.core.net.stream.SseBody) -> Unit = { error("not opened") }
        val signIn = assertIs<SseConnectResult.Failed>(connector.connect(null, noSession))
        assertTrue(!signIn.stop, "an OpenCode 2 401 must not stop the stream (review w1-core-net MAJOR 8)")
        assertEquals(SignInWhy.PasswordRejected, assertIs<Failure.SignIn>(signIn.failure).why)
        status = HttpStatusCode.Forbidden
        val forbidden = assertIs<SseConnectResult.Failed>(connector.connect(null, noSession))
        assertTrue(!forbidden.stop)
        assertEquals(SignInWhy.Forbidden, assertIs<Failure.SignIn>(forbidden.failure).why) // never PasswordRejected
        status = HttpStatusCode.ServiceUnavailable
        val busy = assertIs<SseConnectResult.Failed>(connector.connect(null, noSession))
        assertTrue(!busy.stop)
        status = HttpStatusCode.OK
        type = "text/html"
        val html = assertIs<SseConnectResult.Failed>(connector.connect(null, noSession))
        assertIs<Failure.UnreadableReply>(html.failure)
        assertTrue(!html.stop)
    }

    @Test
    fun `N4-D6 a backend whose sign-in failure is terminal stops on 401`() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.Unauthorized, headersOf(HttpHeaders.ContentType, "text/event-stream")) }
        val connector = KtorSseConnector(sseClient(engine), stopOnAuthFailure = true) { url("http://127.0.0.1/api/event") }
        val failed = assertIs<SseConnectResult.Failed>(connector.connect(null) { error("not opened") })
        assertTrue(failed.stop)
    }
}
