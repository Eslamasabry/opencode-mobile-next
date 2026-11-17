@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.testing.TestDispatchers
import io.github.eslamasabry.oc.engine.integrations.quota.QuotaRequest
import io.github.eslamasabry.opencode_mobile.DirectLoopbackHttp
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.runtime.host.RuntimeStatus
import io.github.eslamasabry.oc.engine.runtime.server.LaunchMode
import io.github.eslamasabry.oc.engine.runtime.server.PhoneServerPort
import io.github.eslamasabry.oc.engine.runtime.server.PhoneServerStarter
import io.github.eslamasabry.oc.engine.runtime.server.ProbeAnswer
import io.github.eslamasabry.oc.engine.runtime.server.StartTarget
import java.io.Closeable
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.ServerSocket
import java.net.SocketAddress
import java.net.SocketException
import java.net.URI
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.flow.collect
import kotlinx.collections.immutable.persistentMapOf
import okhttp3.Request

/** A live owned listener, an unrelated system proxy, and the real phone-start readiness loop. */
class PhoneLoopbackProbeTest {
    @Test
    fun `B-LINUX-037 setup reaches its own listener when the system proxy cannot reach phone loopback`() = runTest {
        val correctCredential = AtomicInteger()
        Listener { request ->
            if (request.any { it == "Authorization: $AUTH" }) correctCredential.incrementAndGet()
            if (request.first().contains(" /api/health ")) Reply(404) else Reply(200)
        }.use { origin ->
            Listener { Reply(503) }.use { proxy ->
                val previous = ProxySelector.getDefault()
                ProxySelector.setDefault(object : ProxySelector() {
                    override fun select(uri: URI): List<Proxy> =
                        listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", proxy.port)))
                    override fun connectFailed(uri: URI, address: SocketAddress, error: java.io.IOException) {}
                })
                try {
                    var launches = 0
                    val port = object : PhoneServerPort {
                        override suspend fun checkHistoryMove(): Outcome<Unit> = Outcome.Ok(Unit)
                        override suspend fun writePassword(password: String): Outcome<Unit> = Outcome.Ok(Unit)
                        override suspend fun launch(profile: ProfileId, mode: LaunchMode): Outcome<Unit> {
                            launches++
                            return Outcome.Ok(Unit)
                        }
                        override suspend fun probe(password: String): ProbeAnswer =
                            PhoneLoopbackProbe.probe(origin.port, password)
                        override suspend fun refreshStatus(): Outcome<RuntimeStatus> =
                            Outcome.Ok(RuntimeStatus(installed = true, serverRunning = true))
                        override suspend fun confirmRecovery(expectedGeneration: Long): Outcome<Unit> = Outcome.Ok(Unit)
                    }
                    val clock = object : Clock {
                        override fun now(): Instant = Instant.fromEpochMilliseconds(testScheduler.currentTime)
                    }
                    val failure = PhoneServerStarter(port, clock).start(StartTarget(ProfileId("phone"), PASSWORD))
                    assertNull(failure, "an answering owned server must complete setup, not time out behind a proxy")
                    assertEquals(1, launches, "the server is started once")
                    assertEquals(2, origin.requests.get(), "read-only 404 falls back to the stable info endpoint")
                    assertEquals(2, correctCredential.get(), "both owned-loopback requests are authenticated")
                    assertEquals(0, proxy.requests.get(), "the unrelated proxy must receive no request or credential")
                } finally {
                    ProxySelector.setDefault(previous)
                }
            }
        }
    }

    @Test
    fun `B-LINUX-037 a loopback redirect is not followed to a different listener`() {
        Listener { Reply(200) }.use { other ->
            Listener { Reply(302, "Location: http://127.0.0.1:${other.port}/api/info\r\n") }.use { origin ->
                assertEquals(ProbeAnswer.NoAnswer, PhoneLoopbackProbe.probe(origin.port, PASSWORD))
                assertEquals(0, other.requests.get(), "a redirect cannot move the authenticated readiness check")
            }
        }
    }

    @Test
    fun `B-LINUX-037 a rejected password stays rejected and is not hidden by endpoint fallback`() {
        Listener { Reply(401) }.use { origin ->
            assertEquals(ProbeAnswer.NeedsPassword, PhoneLoopbackProbe.probe(origin.port, PASSWORD))
            assertEquals(1, origin.requests.get())
        }
    }

    @Test
    fun `B-LINUX-037 helper routes cannot replace the owned loopback authority`() {
        for (path in listOf("http://example.test/health", "//example.test/health", "/health\r\nHost: example.test")) {
            assertFailsWith<IllegalArgumentException> { DirectLoopbackHttp.open(4098, path) }
        }
        assertFailsWith<IllegalArgumentException> { DirectLoopbackHttp.open(0, "/health") }
    }

    @Test
    fun `B-LINUX-037 phone-engine setup health bypasses a system proxy`() = runTest {
        Listener { Reply(200) }.use { origin ->
            Listener { Reply(503) }.use { proxy ->
                val previous = ProxySelector.getDefault()
                ProxySelector.setDefault(object : ProxySelector() {
                    override fun select(uri: URI): List<Proxy> =
                        listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", proxy.port)))
                    override fun connectFailed(uri: URI, address: SocketAddress, error: java.io.IOException) {}
                })
                try {
                    val transport = AndroidQuotaTransport(TestDispatchers(StandardTestDispatcher(testScheduler)))
                    try {
                        val answer = transport.fetch(QuotaRequest(
                            "GET", "http://127.0.0.1:${origin.port}/v1/health",
                            persistentMapOf("Authorization" to AUTH), false, 0, 2_000, 2_000, 2_000,
                        ))
                        answer.body.collect { }
                        assertEquals(200, answer.status, "the phone engine's setup health read is direct")
                    } finally {
                        transport.close(true)
                    }
                    assertEquals(0, proxy.requests.get(), "no helper credential reaches the system proxy")
                } finally {
                    ProxySelector.setDefault(previous)
                }
            }
        }
    }

    @Test
    fun `B-LINUX-037 quota-helper bootstrap bypasses a system proxy`() {
        Listener { Reply(200) }.use { origin ->
            Listener { Reply(503) }.use { proxy ->
                val previous = ProxySelector.getDefault()
                ProxySelector.setDefault(object : ProxySelector() {
                    override fun select(uri: URI): List<Proxy> =
                        listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", proxy.port)))
                    override fun connectFailed(uri: URI, address: SocketAddress, error: java.io.IOException) {}
                })
                try {
                    val status = bootstrapHealthStatus(origin)
                    assertEquals(200, status, "the quota helper's bootstrap health read is direct")
                    assertEquals(0, proxy.requests.get(), "no bootstrap credential reaches the system proxy")
                } finally {
                    ProxySelector.setDefault(previous)
                }
            }
        }
    }

    /** The status of the quota helper's bootstrap health read, made through the readiness client. */
    private fun bootstrapHealthStatus(origin: Listener): Int {
        val client = quotaCollectorReadinessClient()
        try {
            return client.newCall(Request.Builder()
                .url("http://127.0.0.1:${origin.port}/provider-quota")
                .header("Authorization", AUTH).build()).execute().use { it.code }
        } finally {
            client.dispatcher.cancelAll()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    private data class Reply(val status: Int, val headers: String = "")

    private class Listener(private val reply: (List<String>) -> Reply) : Closeable {
        private val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val port: Int get() = socket.localPort
        val requests = AtomicInteger()
        private val worker = Thread {
            while (!socket.isClosed) {
                try {
                    socket.accept().use { client ->
                        client.soTimeout = 2_000
                        val lines = mutableListOf<String>()
                        val reader = client.getInputStream().bufferedReader()
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                            lines += line
                        }
                        requests.incrementAndGet()
                        val answer = reply(lines)
                        client.getOutputStream().write(
                            ("HTTP/1.1 ${answer.status} Test\r\n" + answer.headers +
                                "Content-Length: 0\r\nConnection: close\r\n\r\n").toByteArray()
                        )
                    }
                } catch (error: SocketException) {
                    if (!socket.isClosed) throw error
                }
            }
        }.apply { isDaemon = true; start() }

        override fun close() {
            socket.close()
            worker.join(2_000)
            assertTrue(!worker.isAlive, "the test listener stopped")
        }
    }

    companion object {
        // Synthetic fixture only; never a stored app or provider credential.
        private const val PASSWORD = "test-password"
        private val AUTH = "Basic " + Base64.getEncoder().encodeToString("opencode:$PASSWORD".toByteArray())
    }
}
