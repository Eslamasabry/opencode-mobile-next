package io.github.eslamasabry.oc.core.net.okhttp

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.net.HttpClientFactory
import io.github.eslamasabry.oc.core.net.NetClient
import io.github.eslamasabry.oc.core.net.TransportSpec
import io.github.eslamasabry.oc.core.security.HostClass
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import kotlinx.coroutines.runBlocking
import okhttp3.ConnectionPool
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * QA wave 4 B2 (S1): sends failed with `Network.Reset` and no request reached the server. The OpenCode 2 server closes
 * an idle keep-alive socket after 6 s; the app kept pooled sockets 15 s and never retried, so the first GET of a send
 * 6–15 s after the last request was written into a dead socket. Real sockets, so real time (runBlocking).
 */
class StaleConnectionTest {
    private var server: CountingServer? = null

    @AfterTest
    fun stop() {
        server?.close()
    }

    private fun serve(idleCloseMs: Int) = CountingServer(idleCloseMs = idleCloseMs).also { server = it }

    private fun client(on: CountingServer, keepAliveMs: Long? = null): NetClient {
        val engines = OkHttpEngineFactory(base = { spec ->
            val base = hardenedBaseClient(spec)
            if (keepAliveMs == null) base
            else base.newBuilder().connectionPool(ConnectionPool(5, keepAliveMs, TimeUnit.MILLISECONDS)).build()
        })
        return HttpClientFactory(engines).create(
            TransportSpec(ServerOrigin("http", "127.0.0.1", on.port), HostClass.Loopback, cleartextPermitted = true),
        )
    }

    private suspend fun NetClient.get(on: CountingServer, path: String) =
        exchange({ url(on.url(path)) }) { it.bodyAsText() }

    private suspend fun NetClient.post(on: CountingServer, path: String) =
        exchange({ method = HttpMethod.Post; url(on.url(path)); setBody("{\"text\":\"hi\"}") }) { it.bodyAsText() }

    @Test
    fun `B2 B-PROTO-128 the pool drops idle sockets before the server's 6 s idle close`() {
        assertTrue(IDLE_KEEP_ALIVE_MS < 6_000)
    }

    @Test
    fun `B2 B-PROTO-128 a send 7 s after the last request reaches a 6 s idle-closing server exactly once`() = runBlocking {
        val server = serve(idleCloseMs = 6_000)
        server.handler = { req, _ -> Reply.Ok(req.path) }
        val net = client(server)
        assertEquals("/api/agent", assertIs<Outcome.Ok<String>>(net.get(server, "/api/agent")).value)
        Thread.sleep(7_000) // inside the old 6–15 s failure window
        assertEquals("/api/session/s1", assertIs<Outcome.Ok<String>>(net.get(server, "/api/session/s1")).value)
        assertEquals("/api/session/s1/prompt", assertIs<Outcome.Ok<String>>(net.post(server, "/api/session/s1/prompt")).value)
        assertEquals(1, server.count("POST"))
        assertEquals(listOf("/api/agent", "/api/session/s1", "/api/session/s1/prompt"), server.requests.map { it.path })
        net.close()
    }

    @Test
    fun `B2 B-PROTO-128 a read on a stale pooled socket is retried once on a fresh connection`() = runBlocking {
        val server = serve(idleCloseMs = 1_000)
        server.handler = { req, _ -> Reply.Ok(req.path) }
        val net = client(server, keepAliveMs = 60_000) // a pool that outlives the server's idle close
        assertIs<Outcome.Ok<String>>(net.get(server, "/warm"))
        Thread.sleep(2_000)
        assertEquals("/api/session/s1", assertIs<Outcome.Ok<String>>(net.get(server, "/api/session/s1")).value)
        assertEquals(1, server.requests.count { it.path == "/api/session/s1" })
        assertEquals(2, server.connectionsSeen.get())
        net.close()
    }

    @Test
    fun `B2 B-PROTO-128 a prompt after a stale pooled socket is sent exactly once`() = runBlocking {
        val server = serve(idleCloseMs = 1_000)
        server.handler = { req, _ -> Reply.Ok(req.path) }
        val net = client(server, keepAliveMs = 60_000)
        assertIs<Outcome.Ok<String>>(net.get(server, "/warm"))
        Thread.sleep(2_000)
        assertIs<Outcome.Ok<String>>(net.post(server, "/api/session/s1/prompt"))
        assertEquals(1, server.count("POST"))
        net.close()
    }

    @Test
    fun `B2 B-PROTO-128 a read that fails on a fresh connection is not retried`() = runBlocking {
        val server = serve(idleCloseMs = 0)
        server.handler = { _, _ -> Reply.CloseWithoutAnswer }
        val net = client(server)
        assertIs<Outcome.Failed>(net.get(server, "/api/session/s1"))
        assertEquals(1, server.requests.size)
        net.close()
    }
}
