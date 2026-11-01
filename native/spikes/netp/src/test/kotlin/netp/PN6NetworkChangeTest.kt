package netp

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import netp.support.Live
import netp.support.Oc2
import netp.support.TcpProxy
import netp.support.TinySseServer
import org.junit.jupiter.api.Tag

/**
 * P-N6 on the JVM (no emulator: this container has no KVM). A network change is modelled the way a socket sees it:
 * every open connection goes silent without FIN or RST (the old interface is gone), new connections work. The
 * reaction under test is plan 04 §6.4: evictAll() on the pool + retryNow() on the stream; the in-flight prompt
 * becomes DeliveryUnknown (cancelled, never resent) and is confirmed by a receipt lookup.
 */
class PN6NetworkChangeTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val closeables = mutableListOf<AutoCloseable>()

    @AfterTest fun stop() { scope.cancel(); closeables.forEach { it.close() } }

    private suspend fun MiniStreamLoop.connected(gen: Int, ms: Long) =
        withTimeoutOrNull(ms) { signals.filter { it is MiniStreamLoop.Signal.Connected && it.generation == gen }.first() }

    @Test fun `P-N6 network change - stream back under 2 s and the in-flight prompt is seen once`() = runBlocking {
        val server = TinySseServer(heartbeatMs = 500, promptDelayMs = 3_000).also { closeables += it }
        val proxy = TcpProxy(server.port).also { closeables += it }
        val base = "http://127.0.0.1:${proxy.port}"
        val ok = HardenedOkHttp.build(JvmTransportSpec(Oc2.originOf(base), true, null))
        val http = HardenedOkHttp.ktorClient(HardenedOkHttp.ktorEngine(ok))
        val loop = MiniStreamLoop(http, "$base/api/event", emptyMap(), stallMs = 45_000, scope = scope)
        val first = scope.async0 { loop.connected(1, 5_000) }
        loop.start()
        assertTrue(first.await() != null)

        val prompt = scope.async0 { runCatching { http.post("$base/api/session/s/prompt") { setBody("{\"text\":\"hi\"}") } } }
        withTimeout(3_000) { while (server.promptsReceived.get() == 0) delay(10) }

        val back = scope.async0 { loop.connected(2, 5_000) }
        val changeAt = System.nanoTime()
        proxy.freezeExisting()
        ok.connectionPool.evictAll()
        loop.retryNow()
        prompt.cancel() // DeliveryUnknown: the delivery engine looks the id up; it never resends
        val b = back.await()
        val backMs = b?.let { (it.atNanos - changeAt) / 1_000_000 }
        println("NETP-RESULT pn6.mock.streamBackAfterChangeMs=$backMs")
        assertTrue(backMs != null && backMs < 2_000, "stream back after $backMs ms")
        delay(4_000) // longer than the server's reply delay: a resend would have landed by now
        assertEquals(1, server.promptsReceived.get())
        assertEquals(2, server.streamsOpened.get())
        loop.stop()
    }

    @Test fun `P-N6 evidence - evicting the pool alone does not rescue an open stream`() = runBlocking {
        val server = TinySseServer(heartbeatMs = 500).also { closeables += it }
        val proxy = TcpProxy(server.port).also { closeables += it }
        val base = "http://127.0.0.1:${proxy.port}"
        val ok = HardenedOkHttp.build(JvmTransportSpec(Oc2.originOf(base), true, null))
        val loop = MiniStreamLoop(HardenedOkHttp.ktorClient(HardenedOkHttp.ktorEngine(ok)), "$base/api/event", emptyMap(), 45_000, scope)
        val first = scope.async0 { loop.connected(1, 5_000) }
        loop.start()
        assertTrue(first.await() != null)
        val back = scope.async0 { loop.connected(2, 3_000) }
        proxy.freezeExisting()
        ok.connectionPool.evictAll() // in-use connections are not evicted: the stream keeps waiting on a dead path
        assertNull(back.await(), "without retryNow() cancelling the attempt, recovery waits for the 45 s stall")
        loop.stop()
    }

    @Tag("live") @Test fun `P-N6 live OpenCode 2_1_1 - network change with a stream open and a prompt in flight`() = runBlocking {
        val target = Live.require(Live.oc2Url, "NETP_OC2_URL")
        val direct = Oc2(target)
        val proxy = TcpProxy(java.net.URI(target).port).also { closeables += it }
        val oc = Oc2("http://127.0.0.1:${proxy.port}")
        val sid = oc.createSession("netp P-N6")
        val loop = MiniStreamLoop(oc.http, "${oc.base}/api/event", mapOf("Authorization" to Live.basic()), 45_000, scope)
        val first = scope.async0 { loop.connected(1, 5_000) }
        loop.start()
        assertTrue(first.await() != null)

        oc.ok.connectionPool.evictAll() // the prompt opens a fresh connection whose reply we hold
        proxy.holdResponsesOfNewConnections = true
        val mid = Oc2.clientMessageId()
        val prompt = scope.async0 { runCatching { oc.post("/api/session/$sid/prompt", """{"id":"$mid","text":"netp P-N6: reply with ok"}""") } }
        withTimeout(5_000) { while (direct.get("/api/session/$sid/message/$mid").status.value != 200) delay(50) }
        assertTrue(!prompt.isCompleted, "the reply is still held: the prompt is in flight from the client's view")

        val back = scope.async0 { loop.connected(2, 5_000) }
        val changeAt = System.nanoTime()
        proxy.freezeExisting()
        proxy.holdResponsesOfNewConnections = false
        oc.ok.connectionPool.evictAll()
        loop.retryNow()
        prompt.cancel()
        val b = back.await()
        val backMs = b?.let { (it.atNanos - changeAt) / 1_000_000 }

        // Receipt lookup over the new path confirms delivery; nothing resends.
        val lookup = oc.get("/api/session/$sid/message/$mid").status.value
        delay(3_000)
        val list = direct.get("/api/session/$sid/message").bodyAsText()
        val copies = Regex("\"id\":\"${Regex.escape(mid)}\"").findAll(list).count()
        Live.result("pn6.oc2-2.1.1.streamBackAfterChangeMs=$backMs receiptLookup=$lookup userMessageCopies=$copies")
        assertTrue(backMs != null && backMs < 2_000)
        assertEquals(200, lookup)
        assertEquals(1, copies)
        loop.stop()
    }
}
