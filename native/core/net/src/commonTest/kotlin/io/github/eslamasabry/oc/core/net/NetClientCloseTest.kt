package io.github.eslamasabry.oc.core.net

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

/**
 * B-PROTO-148 without timing: the engine may end a body read cut by [NetClient.close] as a clean end of input, so the
 * exchange itself must not report such a read as an answer the server finished.
 */
class NetClientCloseTest {
    private fun client(): NetClient =
        NetClient(HttpClient(MockEngine { respond("data: a\n\n", HttpStatusCode.OK) }), { TransportError.Other })

    @Test
    fun `B-PROTO-148 a read that ends after the client was closed is a cut connection, not an answer`() = runTest {
        val net = client()
        val outcome = net.exchange({ url("http://127.0.0.1/api/event") }) { response ->
            val text = response.bodyAsText()
            net.close() // the client closes while the stream is open; the reader then sees the end of input
            text
        }
        assertEquals(NetworkWhy.Reset, assertIs<Failure.Network>(assertIs<Outcome.Failed>(outcome).failure).why)
    }

    @Test
    fun `B-PROTO-148 a read that finished before the client was closed keeps its answer`() = runTest {
        val net = client()
        val outcome = net.exchange({ url("http://127.0.0.1/api/event") }) { it.bodyAsText() }
        net.close()
        assertEquals("data: a\n\n", assertIs<Outcome.Ok<String>>(outcome).value)
    }

    @Test
    fun `B-PROTO-148 closing a client never throws when releasing its engine fails`() = runTest {
        // Every place that closes a connection (disconnect, switch, remove, a probe's finally) ends in
        // NetClient.close(): a teardown fault there would reach a view model's coroutine, which has no handler, and
        // take the app down (line run 44079402632 met such a fault in a test's teardown).
        val net =
            NetClient(
                HttpClient(MockEngine { respond("ok", HttpStatusCode.OK) }),
                { TransportError.Other },
                closeEngine = { throw NoSuchElementException() },
            )
        net.close()
        assertFalse(net.http.isActive, "the client is closed all the same")
    }

    @Test
    fun `B-PROTO-148 a failed close is handed once to the fault sink, as it happened`() = runTest {
        val fault = NoSuchElementException()
        val told = mutableListOf<Throwable>()
        val net =
            NetClient(
                HttpClient(MockEngine { respond("ok", HttpStatusCode.OK) }),
                { TransportError.Other },
                closeEngine = { throw fault },
                closeFault = { told += it },
            )
        net.close()
        assertEquals(listOf<Throwable>(fault), told)
    }
}
