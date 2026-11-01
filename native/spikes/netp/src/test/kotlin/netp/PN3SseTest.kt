package netp

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import netp.support.Live
import netp.support.Oc2
import netp.support.TcpProxy
import netp.support.TinySseServer
import org.junit.jupiter.api.Tag

/** P-N3: SSE through Ktor's OkHttp engine (hardened client); stall detection with a paused proxy. */
class PN3SseTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val closeables = mutableListOf<AutoCloseable>()

    @AfterTest fun stop() { scope.cancel(); closeables.forEach { it.close() } }

    private fun loop(base: String, stallMs: Long, headers: Map<String, String> = emptyMap()): MiniStreamLoop {
        val ok = HardenedOkHttp.build(JvmTransportSpec(Oc2.originOf(base), true, null))
        val http = HardenedOkHttp.ktorClient(HardenedOkHttp.ktorEngine(ok))
        return MiniStreamLoop(http, "$base/api/event", headers, stallMs, scope)
    }

    private suspend fun MiniStreamLoop.next(match: (MiniStreamLoop.Signal) -> Boolean, ms: Long) =
        withTimeout(ms) { signals.filter(match).first() }

    @Test fun `P-N3 first event under 1 s on loopback, heartbeats arrive`() = runBlocking {
        val server = TinySseServer(heartbeatMs = 200).also { closeables += it }
        val l = loop("http://127.0.0.1:${server.port}", stallMs = 45_000)
        val t0 = System.nanoTime()
        val firstEvent = scope.async0 { l.next({ it is MiniStreamLoop.Signal.Event }, 5_000) }
        val heartbeats = scope.async0 {
            withTimeout(5_000) { l.signals.filter { (it as? MiniStreamLoop.Signal.Event)?.event is SseEvent.Comment }.take3() }
        }
        l.start()
        val first = firstEvent.await()
        assertTrue((first.atNanos - t0) / 1_000_000 < 1_000)
        assertEquals(3, heartbeats.await().size)
        l.stop()
    }

    @Test fun `P-N3 a paused proxy triggers the stall reconnect after the socket timeout`() = runBlocking {
        val server = TinySseServer(heartbeatMs = 200).also { closeables += it }
        val proxy = TcpProxy(server.port).also { closeables += it }
        val l = loop("http://127.0.0.1:${proxy.port}", stallMs = 1_500)
        val connected = scope.async0 { l.next({ it is MiniStreamLoop.Signal.Connected }, 5_000) }
        l.start()
        connected.await()
        val dropped = scope.async0 { l.next({ it is MiniStreamLoop.Signal.Dropped }, 10_000) }
        val again = scope.async0 { l.next({ it is MiniStreamLoop.Signal.Connected && it.generation == 2 }, 15_000) }
        val pausedAt = System.nanoTime()
        proxy.pauseAll()
        val d = dropped.await() as MiniStreamLoop.Signal.Dropped
        val stallMs = (d.atNanos - pausedAt) / 1_000_000
        assertTrue(stallMs in 1_300..3_000, "stall detected after $stallMs ms (${d.cause})")
        again.await() // new connection through the proxy (only old links are paused)
        println("NETP-RESULT pn3.mock.stallDetectedMs=$stallMs cause=${d.cause}")
        l.stop()
    }

    @Tag("live") @Test fun `P-N3 live OpenCode 2_1_1 - first event, heartbeat interval, 45 s stall`() = runBlocking {
        liveStream(Live.require(Live.oc2Url, "NETP_OC2_URL"), "2.1.1", stall = true)
    }

    @Tag("live") @Test fun `P-N4 live OpenCode 2_2_5 - first event and heartbeat interval`() = runBlocking {
        liveStream(Live.require(Live.oc2NextUrl, "NETP_OC2_URL_NEXT"), "2.2.5", stall = false)
    }

    private suspend fun liveStream(base: String, version: String, stall: Boolean) {
        val port = java.net.URI(base).port
        val proxy = TcpProxy(port).also { closeables += it }
        val l = loop("http://127.0.0.1:${proxy.port}", stallMs = 45_000, headers = mapOf("Authorization" to Live.basic()))
        val beats = mutableListOf<Long>()
        var commentText: String? = null
        val t0 = System.nanoTime()
        val first = scope.async0 { l.next({ it is MiniStreamLoop.Signal.Event }, 5_000) as MiniStreamLoop.Signal.Event }
        val collector = scope.async0 {
            withTimeout(50_000) {
                l.signals.filter { (it as? MiniStreamLoop.Signal.Event)?.event is SseEvent.Comment }.first {
                    commentText = ((it as MiniStreamLoop.Signal.Event).event as SseEvent.Comment).text
                    beats += it.atNanos; beats.size >= 4
                }
            }
        }
        l.start()
        val f = first.await()
        val firstMs = (f.atNanos - t0) / 1_000_000
        val firstType = (f.event as? SseEvent.Data)?.data?.let { Regex("\"type\":\"([^\"]+)\"").find(it)?.groupValues?.get(1) }
        collector.await()
        val gaps = beats.zipWithNext { a, b -> (b - a) / 1_000_000 }
        val comment = commentText
        Live.result("pn3.oc2-$version.firstEventMs=$firstMs firstEventType=$firstType heartbeatGapsMs=$gaps comment=': $comment'")
        assertTrue(firstMs < 1_000, "first event after $firstMs ms")
        assertTrue(gaps.all { it in 5_000..20_000 }, "heartbeat gaps $gaps")
        if (stall) {
            val dropped = scope.async0 { l.next({ it is MiniStreamLoop.Signal.Dropped }, 60_000) as MiniStreamLoop.Signal.Dropped }
            val again = scope.async0 { l.next({ it is MiniStreamLoop.Signal.Connected && it.generation == 2 }, 70_000) }
            val pausedAt = System.nanoTime()
            proxy.pauseAll()
            val d = dropped.await()
            val stallMs = (d.atNanos - pausedAt) / 1_000_000
            val back = again.await()
            Live.result("pn3.oc2-$version.stallDetectedMs=$stallMs cause=${d.cause} reconnectedAfterDropMs=${(back.atNanos - d.atNanos) / 1_000_000}")
            assertTrue(stallMs in 44_000..47_000)
        }
        l.stop()
    }
}

internal fun <T> CoroutineScope.async0(block: suspend () -> T): Deferred<T> =
    async(start = CoroutineStart.UNDISPATCHED) { block() }

internal suspend fun Flow<MiniStreamLoop.Signal>.take3(): List<MiniStreamLoop.Signal> = take(3).toList()
