package io.github.eslamasabry.oc.core.net

import io.github.eslamasabry.oc.core.common.Outcome
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The core:net half of test/perf_trace_test.dart's "http" group (the span itself: :engine:diagnostics). */
class HttpCallObserverTest {
    private class Recorder : HttpCallObserver {
        val calls = mutableListOf<List<Any?>>()

        override suspend fun begin(method: String, path: String): HttpCallEnd =
            HttpCallEnd { status, bytes, failed -> calls += listOf(method, path, status, bytes, failed) }
    }

    private fun client(status: HttpStatusCode, bytes: Int, seen: MutableList<String> = mutableListOf()) =
        NetClient(
            HttpClient(
                MockEngine { request ->
                    seen += request.url.toString()
                    respond("x".repeat(bytes), status, headersOf(HttpHeaders.ContentLength, bytes.toString()))
                },
            ) { expectSuccess = false },
            { TransportError.Other },
        )

    @Test
    fun `B-BUILD-026 every request reports method, path without query, status and bytes`() = runTest {
        val seen = mutableListOf<String>()
        val net = client(HttpStatusCode.OK, 4096, seen)
        val recorder = Recorder()
        net.attach(recorder)
        val out = net.exchange({ method = HttpMethod.Get; url("http://host:4097/session/ses_abc123XYZ/message?directory=%2Fhome%2Fme%2Fprivate-project") }) { it.status.value }
        assertIs<Outcome.Ok<Int>>(out)
        assertEquals(listOf<Any?>("GET", "/session/ses_abc123XYZ/message", 200, 4096L, false), recorder.calls.single())
        assertTrue("private-project" !in recorder.calls.toString())
        // The request itself still carried its query.
        assertTrue("directory=" in seen.single())
    }

    @Test
    fun `B-BUILD-026 a failed request is recorded as an error with its status`() = runTest {
        val net = client(HttpStatusCode.InternalServerError, 10)
        val recorder = Recorder()
        net.attach(recorder)
        assertIs<Outcome.Failed>(net.exchange({ method = HttpMethod.Get; url("http://host/provider") }) { Unit })
        assertEquals(listOf<Any?>("GET", "/provider", 500, 10L, true), recorder.calls.single())
    }

    @Test
    fun `B-BUILD-026 attaching twice adds one observer and a throwing observer never breaks the call`() = runTest {
        val net = client(HttpStatusCode.OK, 2)
        val recorder = Recorder()
        assertTrue(net.attach(recorder))
        assertFalse(net.attach(recorder))
        assertEquals(1, net.observerCount)
        net.attach(object : HttpCallObserver {
            override suspend fun begin(method: String, path: String): HttpCallEnd = error("boom")
        })
        assertIs<Outcome.Ok<Unit>>(net.exchange({ method = HttpMethod.Get; url("http://host/a") }) { Unit })
        assertEquals(1, recorder.calls.size)
    }
}
