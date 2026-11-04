package io.github.eslamasabry.oc.protocol.codex.probe

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.SignInWhy
import io.github.eslamasabry.oc.core.model.Health
import io.github.eslamasabry.oc.core.model.ServerPage
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.protocol.codex.CodexEndpoint
import io.github.eslamasabry.oc.protocol.codex.CodexException
import io.github.eslamasabry.oc.protocol.codex.CodexFailureKind
import io.github.eslamasabry.oc.protocol.codex.FakeAppServer
import io.github.eslamasabry.oc.protocol.codex.FakeSockets
import io.github.eslamasabry.oc.protocol.codex.gateway.CodexGateway
import io.github.eslamasabry.oc.protocol.codex.jsonOf
import io.github.eslamasabry.oc.protocol.codex.params
import io.github.eslamasabry.oc.protocol.codex.transport.CodexTransport
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Ports of test/codex_connection_probe_test.dart and the probe case of test/server_codex_connect_flow_test.dart. */
class CodexConnectionProbeTest {
    private class ProbeGateway(
        val healthy: Boolean = true,
        val healthFailure: Failure? = null,
        val healthThrows: Throwable? = null,
        val sessionsFailure: Failure? = null,
    ) : CodexProbeGateway {
        var healthCalls = 0
        var pageCalls = 0
        var closed = false

        override suspend fun health(): Outcome<Health> {
            healthCalls++
            healthThrows?.let { throw it }
            healthFailure?.let { return Outcome.Failed(it) }
            return Outcome.Ok(Health(healthy, "0.153.4"))
        }

        override suspend fun sessionPage(cursor: String?, limit: Int): Outcome<ServerPage<Session>> {
            pageCalls++
            assertEquals(1, limit)
            sessionsFailure?.let { return Outcome.Failed(it) }
            return Outcome.Ok(ServerPage(persistentListOf(), null))
        }

        override suspend fun close() {
            closed = true
        }
    }

    @Test
    fun `B-AGENT-060 a successful probe checks health and scope then closes the gateway`() = runTest {
        lateinit var g: ProbeGateway
        val result = probeCodexConnection("wss://codex.example.test", "test-token", "/work/project") { url, token, dir ->
            assertEquals("wss://codex.example.test", url)
            assertEquals("test-token", token)
            assertEquals("/work/project", dir)
            ProbeGateway().also { g = it }
        }
        assertEquals(CodexProbeResult.Verified("0.153.4"), result)
        assertTrue(result.ok)
        assertEquals(1, g.healthCalls)
        assertEquals(1, g.pageCalls)
        assertTrue(g.closed)
    }

    @Test
    fun `B-AGENT-060 a failed scoped read is not reported as verified and still closes`() = runTest {
        val g = ProbeGateway(sessionsFailure = CodexException(CodexFailureKind.ScopeMismatch).toFailure())
        val result = probeCodexConnection("wss://codex.example.test", "test-token", "/work/project") { _, _, _ -> g }
        assertFalse(result.ok)
        assertEquals(1, g.healthCalls)
        assertEquals(1, g.pageCalls)
        assertTrue(g.closed)
    }

    @Test
    fun `B-AGENT-060 an unhealthy server is not reported as verified`() = runTest {
        val g = ProbeGateway(healthy = false)
        val result = probeCodexConnection("wss://codex.example.test", "test-token", "/work/project") { _, _, _ -> g }
        assertEquals(CodexProbeResult.NotVerified(Failure.Unknown()), result)
        assertEquals(0, g.pageCalls)
        assertTrue(g.closed)
    }

    @Test
    fun `B-AGENT-060 a failed gateway probe still closes the gateway`() = runTest {
        val g = ProbeGateway(healthFailure = CodexException(CodexFailureKind.Authentication).toFailure())
        val result = probeCodexConnection("wss://codex.example.test", "test-token", "/work/project") { _, _, _ -> g }
        assertEquals(CodexProbeResult.NotVerified(Failure.SignIn(SignInWhy.TokenRejected)), result)
        assertEquals(1, g.healthCalls)
        assertEquals(0, g.pageCalls)
        assertTrue(g.closed)
    }

    @Test
    fun `B-AGENT-060 invalid input returns before calling the gateway factory`() = runTest {
        var called = false
        val result = probeCodexConnection("https://codex.example.test", "test-token", "/work/project") { _, _, _ ->
            called = true
            error("must not be called")
        }
        assertIs<CodexProbeResult.InvalidInput>(result)
        assertFalse(called)
        assertFalse("test-token" in result.toString())
        assertIs<CodexProbeResult.InvalidInput>(probeCodexConnection("wss://codex.example.test", "bad token", "/work/project") { _, _, _ -> error("no") })
        assertIs<CodexProbeResult.InvalidInput>(probeCodexConnection("wss://codex.example.test", "t", "relative") { _, _, _ -> error("no") })
    }

    @Test
    fun `B-AGENT-060 unexpected failures use fixed sanitized copy`() = runTest {
        val g = ProbeGateway(healthThrows = IllegalStateException("token=super-secret-token"))
        val result = probeCodexConnection("wss://codex.example.test", "super-secret-token", "/work/project") { _, _, _ -> g }
        assertEquals(CodexProbeResult.NotVerified(Failure.Unknown()), result)
        assertFalse("super-secret-token" in result.toString())
        assertTrue(g.closed)
    }

    @Test
    fun `B-AGENT-220 the Codex probe performs an authenticated handshake and a scoped thread read`() = runTest {
        val server = FakeAppServer().apply { answers["thread/list"] = { jsonOf("data" to emptyList<Any>(), "nextCursor" to null) } }
        val sockets = object : io.github.eslamasabry.oc.protocol.codex.transport.CodexSocketFactory {
            var secret: String? = null
            var endpoint: CodexEndpoint? = null

            override suspend fun open(endpoint: CodexEndpoint, secret: String): io.github.eslamasabry.oc.protocol.codex.transport.CodexSocket {
                this.endpoint = endpoint
                this.secret = secret
                return server
            }
        }
        var captured: Triple<String, String, String>? = null
        val result = probeCodexConnection("ws://127.0.0.1:4500", "token-value", "/work/project") { url, token, dir ->
            captured = Triple(url, token, dir)
            CodexGateway(
                CodexTransport(CodexEndpoint.parse(url)!!, token, sockets, backgroundScope, StandardTestDispatcher(testScheduler)),
                backgroundScope,
                TestClock(),
                dir,
            )
        }
        assertTrue(result.ok)
        assertEquals(Triple("ws://127.0.0.1:4500", "token-value", "/work/project"), captured)
        assertEquals("token-value", sockets.secret)
        assertEquals(listOf<String?>("initialize", "initialized", "thread/list"), server.methods())
        assertEquals("/work/project", server.sent.last().params()["cwd"].toString().trim('"'))
        assertTrue(server.closed)
    }

    @Test
    fun `B-AGENT-220 the factory pairing check closes everything it opened`() = runTest {
        val server = FakeAppServer().apply { answers["thread/list"] = { jsonOf("data" to emptyList<Any>(), "nextCursor" to null) } }
        val factory = io.github.eslamasabry.oc.protocol.codex.gateway.CodexGatewayFactory(
            FakeSockets(server),
            io.github.eslamasabry.oc.core.testing.TestDispatchers(StandardTestDispatcher(testScheduler)),
            TestClock(),
        )
        assertTrue(factory.checkConnection("127.0.0.1:4500", "token-value", "/work/project").ok)
        assertEquals(listOf<String?>("initialize", "initialized", "thread/list"), server.methods())
        assertTrue(server.closed)
    }
}
