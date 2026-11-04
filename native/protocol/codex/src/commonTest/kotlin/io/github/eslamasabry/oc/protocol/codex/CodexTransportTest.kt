package io.github.eslamasabry.oc.protocol.codex

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.protocol.codex.transport.CodexRpcEvent
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ports of test/codex_transport_test.dart and the transport cases of test/codex_gateway_test.dart. */
class CodexTransportTest {
    @Test
    fun `B-AGENT-062 socket loss fails all pending work and never replays a mutation`() = runTest {
        val first = FakeAppServer().apply { answers["turn/start"] = { null }; answers["thread/list"] = { null } }
        val second = FakeAppServer().apply { answers["thread/list"] = { jsonOf("data" to emptyList<Any>(), "nextCursor" to null) } }
        val t = transport(FakeSockets(first, second))
        val mutation = async { assertFailsWith<CodexException> { t.request("turn/start", jsonOf("threadId" to "thread-1"), mutation = true) } }
        val read = async { assertFailsWith<CodexException> { t.request("thread/list", JsonObject(emptyMap())) } }
        runCurrent()
        assertEquals(1, first.calls("turn/start").size)
        assertEquals(1, first.calls("thread/list").size)
        first.drop()
        runCurrent()
        assertEquals(CodexFailureKind.DeliveryUnknown, mutation.await().kind)
        assertEquals(CodexFailureKind.Disconnected, read.await().kind)

        val recovered = t.request("thread/list", JsonObject(emptyMap()))
        assertTrue((recovered["data"] as kotlinx.serialization.json.JsonArray).isEmpty())
        assertTrue(second.calls("turn/start").isEmpty())
        assertEquals(1, second.calls("thread/list").size)
        t.close()
    }

    @Test
    fun `B-AGENT-062 a lost mutation response is uncertain and never retried`() = runTest {
        val server = FakeAppServer()
        server.beforeAnswer = { if (it.str("method") == "turn/start") server.drop() }
        val t = transport(FakeSockets(server))
        val e = assertFailsWith<CodexException> { t.request("turn/start", jsonOf("threadId" to "thread-1"), mutation = true) }
        assertEquals(CodexFailureKind.DeliveryUnknown, e.kind)
        assertEquals(1, server.calls("turn/start").size)
        assertEquals(Failure.Refused(io.github.eslamasabry.oc.core.common.RefusalReason.DispatchUncertain), e.toFailure())
        t.close()
    }

    @Test
    fun `B-AGENT-062 server errors never expose a remote body`() = runTest {
        val server = FakeAppServer().apply { errors["thread/list"] = -32600 }
        val t = transport(FakeSockets(server))
        val e = assertFailsWith<CodexException> { t.request("thread/list", JsonObject(emptyMap())) }
        assertEquals(CodexFailureKind.Unavailable, e.kind)
        assertFalse("secret" in e.toString() || "remote" in e.toString())
        assertNull(e.toFailure().details)
        assertEquals(CodexFailureKind.Unavailable, e.toFailure().codexKind())
        t.close()
    }

    @Test
    fun `B-AGENT-064 an old epoch cannot answer an approval after socket loss`() = runTest {
        val server = FakeAppServer()
        val t = transport(FakeSockets(server))
        val events = mutableListOf<CodexRpcEvent>()
        t.addListener(object : io.github.eslamasabry.oc.protocol.codex.transport.CodexTransportListener {
            override fun onEvent(event: CodexRpcEvent) {
                events += event
            }
        })
        t.connect()
        val oldEpoch = t.epoch
        server.drop()
        runCurrent()
        assertTrue(t.epoch > oldEpoch)
        val e = assertFailsWith<CodexException> { t.reply(CodexRpcEvent(oldEpoch, "request", JsonObject(emptyMap()), JsonPrimitive(7)), JsonObject(emptyMap())) }
        assertEquals(CodexFailureKind.StaleRequest, e.kind)
        assertTrue(events.isEmpty())
        t.close()
    }

    @Test
    fun `B-AGENT-062 a request waits for the replacement initialize and never escapes the handoff`() = runTest {
        val first = FakeAppServer()
        val second = FakeAppServer().apply { answers["initialize"] = { null } }
        val t = transport(FakeSockets(first, second), requestTimeoutMs = 10)
        t.connect()
        first.drop()
        runCurrent()
        val read = async { assertFailsWith<CodexException> { t.request("thread/list", JsonObject(emptyMap())) } }
        advanceTimeBy(50)
        runCurrent()
        assertEquals(CodexFailureKind.Disconnected, read.await().kind)
        assertEquals(listOf<String?>("initialize"), second.methods())
        t.close()
    }

    @Test
    fun `B-AGENT-062 the handshake sends initialize then initialized and pins the account schema`() = runTest {
        val server = FakeAppServer()
        val t = transport(FakeSockets(server))
        t.connect()
        assertEquals(listOf<String?>("initialize", "initialized"), server.methods())
        assertTrue(t.accountApiSupported)
        val other = FakeAppServer(userAgent = "codex_cli_rs/0.154.0 (fixture)")
        val t2 = transport(FakeSockets(other))
        t2.connect()
        assertTrue(t2.connected)
        assertFalse(t2.accountApiSupported)
        t.close()
        t2.close()
    }

    @Test
    fun `B-AGENT-062 endpoint policy rejects remote cleartext, URL credentials, queries and paths`() {
        for (url in listOf("ws://203.0.113.8:4099", "wss://user:secret@example.com", "wss://example.com?token=secret", "wss://example.com/path", "https://example.com")) {
            assertNull(CodexEndpoint.parse(url), url)
        }
        assertEquals("::1", CodexEndpoint.parse("ws://[::1]:4099")!!.host)
        assertEquals("wss", CodexEndpoint.parse("wss://example.com")!!.scheme)
        assertEquals("ws://127.0.0.1:4500", CodexProfileRules.normalizeUrl("127.0.0.1:4500"))
        assertEquals("wss://codex.example.test", CodexProfileRules.normalizeUrl("codex.example.test"))
    }
}
