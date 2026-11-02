@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.eslamasabry.oc.protocol.paseo

import io.github.eslamasabry.oc.protocol.paseo.transport.PaseoBinaryFrame
import io.github.eslamasabry.oc.protocol.paseo.transport.PaseoPush
import io.github.eslamasabry.oc.protocol.paseo.transport.PaseoTransport
import io.github.eslamasabry.oc.protocol.paseo.transport.PaseoTransportListener
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Request bookkeeping (plan 04 §5.4 for Paseo) and the in-order large-frame decoding of test/paseo_background_decode_test.dart. */
class PaseoTransportTest {
    private class Recorder : PaseoTransportListener {
        val pushes = mutableListOf<PaseoPush>()
        val disconnects = mutableListOf<Int>()
        val binary = mutableListOf<PaseoBinaryFrame>()

        override fun onPush(push: PaseoPush) {
            pushes += push
        }

        override fun onDisconnect(epoch: Int) {
            disconnects += epoch
        }

        override fun onBinary(frame: PaseoBinaryFrame) {
            binary += frame
        }
    }

    private fun TestScope.connected(daemon: FakeDaemon = FakeDaemon(), password: String = ""): Pair<PaseoTransport, Recorder> {
        val t = transport(daemon, password)
        val r = Recorder()
        t.addListener(r)
        return t to r
    }

    @Test
    fun `WP-04-10 a request carries its id and type inside a session message and any payload with the id settles it`() = runTest {
        val daemon = FakeDaemon()
        daemon.handlers["fetch_agents_request"] = { "fetch_agents_response" to jsonOf("entries" to emptyList<Any>()) }
        daemon.handlers["archive_agent_request"] = { "agent_archived" to jsonOf("agentId" to "a1") }
        val (t, r) = connected(daemon)
        val answer = t.request("fetch_agents_request", jsonOf("page" to mapOf("limit" to 5)))
        assertEquals(0, answer.list("entries").size)
        val sent = daemon.of("fetch_agents_request").single()
        assertTrue(sent.str("requestId")!!.matches(Regex("m1_\\d+")))
        assertEquals(jsonOf("limit" to 5), sent.obj("page"))
        // An action answered by the push it causes: settles the request and is still delivered as a push.
        t.request("archive_agent_request", jsonOf("agentId" to "a1"), mutation = true)
        runCurrent()
        assertEquals(listOf("agent_archived"), r.pushes.map { it.type })
    }

    @Test
    fun `WP-04-10 a daemon error is a fixed failure whose reason never carries the password`() = runTest {
        val daemon = FakeDaemon()
        daemon.handlers["send_agent_message_request"] = { "send_agent_message_response" to jsonOf("error" to "bad   s3cret-pw\nhere") }
        daemon.handlers["create_agent_request"] = { "rpc_error" to jsonOf("error" to "boom s3cret-pw") }
        val (t, _) = connected(daemon, password = "s3cret-pw")
        val refused = assertFailsWith<PaseoException> { t.request("send_agent_message_request", jsonOf()) }
        assertEquals(PaseoFailureKind.Unavailable, refused.kind)
        assertEquals("bad ••• here", refused.reason)
        val rpc = assertFailsWith<PaseoException> { t.request("create_agent_request", jsonOf()) }
        assertEquals("boom •••", rpc.reason)
        assertEquals("The agent request could not be completed.", t.lastDaemonError)
        // answerErrors: the answer is a report to read even when it carries an error.
        val report = t.request("send_agent_message_request", jsonOf(), answerErrors = true)
        assertEquals("bad   s3cret-pw\nhere", report.str("error"))
    }

    @Test
    fun `B-AGENT-066 a read that times out is disconnected, a mutation is delivery-unknown and never resent`() = runTest {
        val daemon = FakeDaemon()
        val (t, _) = connected(daemon)
        t.connect()
        val read = async { assertFailsWith<PaseoException> { t.request("fetch_agent_request", jsonOf(), timeoutMs = 1_000) } }
        val write = async { assertFailsWith<PaseoException> { t.request("send_agent_message_request", jsonOf(), mutation = true, timeoutMs = 1_000) } }
        advanceTimeBy(1_001)
        assertEquals(PaseoFailureKind.Disconnected, read.await().kind)
        assertEquals(PaseoFailureKind.DeliveryUnknown, write.await().kind)
        assertEquals(1, daemon.of("send_agent_message_request").size)
        assertTrue(t.connected)
    }

    @Test
    fun `B-AGENT-066 a socket lost after the frame was written fails mutations as delivery-unknown and reads as disconnected`() = runTest {
        val daemon = FakeDaemon()
        val (t, r) = connected(daemon)
        t.connect()
        val read = async { assertFailsWith<PaseoException> { t.request("fetch_agent_request", jsonOf()) } }
        val write = async { assertFailsWith<PaseoException> { t.request("send_agent_message_request", jsonOf(), mutation = true) } }
        runCurrent()
        daemon.drop()
        runCurrent()
        assertEquals(PaseoFailureKind.Disconnected, read.await().kind)
        assertEquals(PaseoFailureKind.DeliveryUnknown, write.await().kind)
        assertEquals(listOf(1), r.disconnects)
        assertFalse(t.connected)
        assertNull(t.serverVersion)
    }

    @Test
    fun `WP-04-10 at most 64 requests wait and the next fails overloaded without writing`() = runTest {
        val daemon = FakeDaemon()
        val (t, _) = connected(daemon)
        t.connect()
        repeat(PaseoTransport.MAX_PENDING) { async { runCatchingPaseo { t.request("fetch_agent_request", jsonOf()) } } }
        runCurrent()
        val over = assertFailsWith<PaseoException> { t.request("fetch_agent_request", jsonOf()) }
        assertEquals(PaseoFailureKind.Overloaded, over.kind)
        assertEquals(PaseoTransport.MAX_PENDING, daemon.of("fetch_agent_request").size)
    }

    @Test
    fun `WP-04-10 a request stamped with an older epoch is stale and a reconnect gets a new epoch`() = runTest {
        val first = FakeDaemon()
        val second = FakeDaemon()
        val t = transport(FakeSockets(first, second))
        t.connect()
        val old = t.epoch
        first.drop()
        runCurrent()
        assertEquals(PaseoFailureKind.StaleRequest, assertFailsWith<PaseoException> { t.request("x", jsonOf(), expectedEpoch = old) }.kind)
        t.connect()
        assertTrue(t.epoch > old)
        assertTrue(t.connected)
    }

    @Test
    fun `WP-04-10 progress leaves the request waiting and reaches its callback`() = runTest {
        val daemon = FakeDaemon()
        val (t, r) = connected(daemon)
        t.connect()
        val progress = mutableListOf<String>()
        val answer = async {
            t.request("daemon.update.request", jsonOf(), onProgress = { type, payload -> progress += "$type:${payload.str("phase")}" })
        }
        runCurrent()
        val id = daemon.sent.last().str("requestId")!!
        daemon.push("daemon.update.progress", jsonOf("requestId" to id, "phase" to "download"))
        daemon.push("daemon.update.progress", jsonOf("requestId" to id, "phase" to "restart"))
        daemon.push("daemon.update.response", jsonOf("requestId" to id, "ok" to true))
        runCurrent()
        assertEquals(listOf("daemon.update.progress:download", "daemon.update.progress:restart"), progress)
        assertEquals("true", answer.await()["ok"].toString())
        assertTrue(r.pushes.isEmpty())
    }

    @Test
    fun `B-AGENT-150 long frames decode off the lane, keep arrival order, and equal inline decoding`() = runTest {
        val daemon = FakeDaemon()
        val (t, r) = connected(daemon)
        t.connect()
        val big = "x".repeat(PaseoTransport.BACKGROUND_DECODE_CHARS + 10)
        daemon.push("agent_stream", jsonOf("agentId" to "a1", "n" to 1, "text" to big))
        daemon.push("agent_stream", jsonOf("agentId" to "a1", "n" to 2))
        daemon.push("agent_stream", jsonOf("agentId" to "a1", "n" to 3, "text" to big))
        daemon.push("agent_update", jsonOf("n" to 4))
        runCurrent()
        assertEquals(listOf("1", "2", "3", "4"), r.pushes.map { it.payload["n"].toString() })
        assertEquals(big, r.pushes[0].payload.str("text"))
    }

    @Test
    fun `B-AGENT-150 malformed JSON drops the connection, small or long`() = runTest {
        for (frame in listOf("{not json", "{\"type\":\"session\",\"message\":" + "[".repeat(PaseoTransport.BACKGROUND_DECODE_CHARS))) {
            val daemon = FakeDaemon()
            val (t, r) = connected(daemon)
            t.connect()
            daemon.pushText(frame)
            runCurrent()
            assertFalse(t.connected)
            assertEquals(1, r.disconnects.size)
        }
        // A session message without a type is malformed too.
        val daemon = FakeDaemon()
        val (t, _) = connected(daemon)
        t.connect()
        daemon.pushRaw(jsonOf("type" to "session", "message" to mapOf("payload" to mapOf<String, Any>())))
        runCurrent()
        assertFalse(t.connected)
    }

    @Test
    fun `B-AGENT-150 a long history answer is read while later frames wait, then they follow in order`() = runTest {
        val daemon = FakeDaemon()
        val big = "y".repeat(PaseoTransport.BACKGROUND_DECODE_CHARS * 2)
        daemon.handlers["fetch_agent_timeline_request"] = { "fetch_agent_timeline_response" to jsonOf("entries" to listOf(big)) }
        val (t, r) = connected(daemon)
        t.connect()
        val seen = mutableListOf<String>()
        val read = async {
            t.requestThen("fetch_agent_timeline_request", jsonOf()) { payload ->
                // A push that arrived right behind the answer is not handled yet.
                seen += "read:${r.pushes.size}"
                kotlinx.coroutines.yield()
                seen += "read-done:${r.pushes.size}"
                payload.list("entries").size
            }
        }
        runCurrent()
        daemon.push("agent_stream", jsonOf("agentId" to "a1"))
        runCurrent()
        assertEquals(1, read.await())
        runCurrent()
        assertEquals(listOf("read:0", "read-done:0"), seen)
        assertEquals(1, r.pushes.size)
    }

    @Test
    fun `WP-04-10 binary frames carry opcode and slot, too-short frames are ignored`() = runTest {
        val daemon = FakeDaemon()
        val (t, r) = connected(daemon)
        t.connect()
        daemon.pushBinary(1, 3, "hi".encodeToByteArray())
        runCurrent()
        assertEquals(1, r.binary.single().opcode)
        assertEquals(3, r.binary.single().slot)
        assertContentEquals("hi".encodeToByteArray(), r.binary.single().payload)
    }

    @Test
    fun `WP-04-10 the keepalive pings every 20 s and close forgets the password and stops`() = runTest {
        val daemon = FakeDaemon()
        val sockets = FakeSockets(daemon)
        val t = transport(sockets, password = "pw-123")
        t.connect()
        advanceTimeBy(PaseoTransport.KEEPALIVE_MS * 2 + 1)
        // Pings are not session messages: the daemon recorded none of them as requests.
        assertTrue(daemon.sent.isEmpty())
        assertTrue(t.connected)
        t.close()
        assertTrue(t.isClosed)
        assertTrue(daemon.closed)
        assertEquals(PaseoFailureKind.Disconnected, assertFailsWith<PaseoException> { t.connect() }.kind)
    }

    @Test
    fun `WP-04-10 the bounded history connection refuses an untested daemon and closes`() = runTest {
        val live = FakeDaemon().apply { version = "0.11.2" }
        val untested = FakeDaemon().apply { version = "0.13.0" }
        val t = transport(FakeSockets(live, untested))
        t.connect()
        val failure = assertFailsWith<PaseoException> { t.requestBoundedHistory(jsonOf("agentId" to "a1")) }
        assertEquals(PaseoFailureKind.Unavailable, failure.kind)
        assertTrue(untested.closed)
        assertTrue(t.connected)
    }

    private suspend fun runCatchingPaseo(block: suspend () -> Unit) {
        try {
            block()
        } catch (_: PaseoException) {
        }
    }
}
