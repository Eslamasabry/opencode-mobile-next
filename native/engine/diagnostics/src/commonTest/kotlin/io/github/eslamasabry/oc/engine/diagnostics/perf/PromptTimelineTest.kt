package io.github.eslamasabry.oc.engine.diagnostics.perf

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.net.NetClient
import io.github.eslamasabry.oc.core.net.TransportError
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.core.testing.TestClock
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.TestTimeSource

/** Ports the "http" and "prompt timeline" groups of test/perf_trace_test.dart. */
class PromptTimelineTest {
    private val logged = mutableListOf<String>()
    private val trace = PerfTrace(Redactor().apply { registerSecret("secret-password-1") }, TestClock(), TestTimeSource(), logSink = logged::add)

    private fun only(name: String) = trace.spans().single { it.name == name }

    private fun net(status: HttpStatusCode, bytes: Int) =
        NetClient(
            HttpClient(MockEngine { respond("x".repeat(bytes), status, headersOf(HttpHeaders.ContentLength, bytes.toString())) }) {
                expectSuccess = false
            },
            { TransportError.Other },
        )

    @Test
    fun `B-BUILD-026 every request is a span with method, path template, status and bytes`() = runTest {
        val client = net(HttpStatusCode.OK, 4096)
        client.attach(HttpSpanRecorder(trace, "oc2"))
        trace.span("location.select") {
            client.exchange({ method = HttpMethod.Get; url("http://host:4097/session/ses_abc123XYZ/message?directory=/home/me/private-project") }) { Unit }
        }
        val span = only("http oc2 GET /session/:id/message")
        assertEquals("200", span.attrs["status"])
        assertEquals("4096", span.attrs["bytes"])
        assertEquals("location.select", span.parent)
        assertEquals(PerfOutcome.Ok, span.outcome)
        val everything = trace.spans().toString() + logged.joinToString()
        assertFalse("private-project" in everything)
        assertFalse("secret-password-1" in everything)
    }

    @Test
    fun `B-BUILD-026 a failed request is recorded as an error with its status`() = runTest {
        val client = net(HttpStatusCode.InternalServerError, 10)
        client.attach(HttpSpanRecorder(trace, "oc1"))
        assertIs<Outcome.Failed>(client.exchange({ method = HttpMethod.Get; url("http://host/provider") }) { Unit })
        val span = only("http oc1 GET /provider")
        assertTrue(span.failed)
        assertEquals("500", span.attrs["status"])
        assertTrue(logged.single { "/provider" in it }.contains("outcome=error"))
    }

    @Test
    fun `B-BUILD-026 attaching the recorder twice adds one`() {
        val client = net(HttpStatusCode.OK, 1)
        val recorder = HttpSpanRecorder(trace, "oc1")
        client.attach(recorder)
        client.attach(recorder)
        assertEquals(1, client.observerCount)
    }

    @Test
    fun `B-BUILD-027 the prompt timeline records sent, accepted, first token and turn done`() = runTest {
        val timeline = PromptTimeline(trace)
        timeline.track("ses_1") {}
        // The user's own text part echoes back first and does not count.
        timeline.observe(PromptSignal.Part("ses_1", "msg_user", "text"))
        // A stale idle before the turn visibly starts does not end it.
        timeline.observe(PromptSignal.Idle("ses_1"))
        assertTrue(trace.spans().none { it.name == "prompt.turn" })
        timeline.observe(PromptSignal.Busy("ses_1"))
        timeline.observe(PromptSignal.Message("ses_1", "msg_a", assistant = true))
        assertTrue(trace.spans().none { it.name == "prompt.first_token" })
        timeline.observe(PromptSignal.Part("ses_1", "msg_a", "text"))
        timeline.observe(PromptSignal.Delta("ses_1"))
        timeline.observe(PromptSignal.Idle("ses_1"))
        timeline.observe(PromptSignal.Idle("ses_1"))
        assertEquals(listOf("prompt.sent", "prompt.accepted", "prompt.first_token", "prompt.turn"), trace.spans().map { it.name })
    }

    @Test
    fun `B-BUILD-027 a refused prompt is an error and ends the tracking`() = runTest {
        val timeline = PromptTimeline(trace)
        assertFailsWith<IllegalStateException> { timeline.track("ses_2") { error("no") } }
        assertTrue(only("prompt.accepted").failed)
        timeline.observe(PromptSignal.Delta("ses_2"))
        assertTrue(trace.spans().none { it.name == "prompt.first_token" })
    }
}
