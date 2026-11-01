package netp

import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.readReason
import io.ktor.websocket.readText
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import netp.support.Live
import netp.support.RawWsServer
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.jupiter.api.Tag

/** P-N1: WebSocket over an OkHttp HTTP/1.1 upgrade + Ktor RawWebSocket (plan 04 §5.2). */
class PN1WebSocketTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val closeables = mutableListOf<AutoCloseable>()

    @AfterTest fun stop() { scope.cancel(); closeables.forEach { it.close() } }

    private fun client(origin: String): OkHttpClient =
        HardenedOkHttp.build(JvmTransportSpec(origin = origin, cleartextPermitted = true, pinnedLeafSha256 = null))

    private fun spec(url: String, subprotocol: String? = null, max: Long = 8L * 1024 * 1024, pingMs: Long = 20_000, pongMs: Long = 15_000) =
        WsSpec(url = url, headers = listOf("Authorization" to "Bearer test"), subprotocol = subprotocol,
            maxMessageBytes = max, pingIntervalMs = pingMs, pongTimeoutMs = pongMs)

    private fun raw(
        handshake: (Map<String, String>, String) -> String = { h, a -> RawWsServer.ok(h, a) },
        handler: suspend (io.ktor.websocket.WebSocketSession) -> Unit = { ws -> ws.incoming.consumeEach { } },
    ) = RawWsServer(handshake, handler).also { closeables += it }

    @Test fun `P-N1 hello-answer round trip against mockwebserver3 (OkHttp server-side WebSocket)`() = runBlocking {
        val server = MockWebServer().also { closeables += it; it.start() }
        server.enqueue(MockResponse.Builder().webSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) { webSocket.send("answer:$text") }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, null) }
        }).build())
        val origin = "http://${server.hostName}:${server.port}"
        val ws = OkHttpWebSockets.connect(client(origin), spec("ws://${server.hostName}:${server.port}/ws"), scope)
        ws.sendText("hello")
        assertEquals(WsMessage.Text("answer:hello"), withTimeout(5_000) { ws.incoming.receive() })
        val req = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertNull(req.headers["Sec-WebSocket-Extensions"], "we never offer permessage-deflate")
        assertEquals("Bearer test", req.headers["Authorization"])
        ws.close()
        assertEquals(WsClose(1000, "", byPeer = true), ws.closeReason.await())
    }

    @Test fun `P-N1 no extension negotiated - a 101 that names one is refused`() = runBlocking {
        val s = raw(handshake = { h, a -> RawWsServer.ok(h, a, "Sec-WebSocket-Extensions: permessage-deflate\r\n") })
        val e = assertFailsWith<WsHandshakeFailure> { OkHttpWebSockets.connect(client(o(s)), spec(u(s)), scope) }
        assertEquals(WsHandshakeFailure.Kind.ExtensionNegotiated, e.kind)
    }

    @Test fun `P-N1 101 validation - wrong accept, missing upgrade, plain 200, 401 and 403 are refused`() = runBlocking {
        val cases = mapOf<WsHandshakeFailure.Kind, (Map<String, String>, String) -> String>(
            WsHandshakeFailure.Kind.BadAccept to { h, _ -> RawWsServer.ok(h, "AAAAAAAAAAAAAAAAAAAAAAAAAAA=") },
            WsHandshakeFailure.Kind.NotUpgraded to { _, a -> "HTTP/1.1 101 Switching Protocols\r\nConnection: Upgrade\r\nUpgrade: h2c\r\nSec-WebSocket-Accept: $a\r\n\r\n" },
            WsHandshakeFailure.Kind.NotSwitching to { _, _ -> "HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n" },
            WsHandshakeFailure.Kind.SignInNeeded to { _, _ -> "HTTP/1.1 401 Unauthorized\r\nContent-Length: 0\r\n\r\n" },
            WsHandshakeFailure.Kind.Forbidden to { _, _ -> "HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\n\r\n" },
        )
        for ((kind, hs) in cases) {
            val s = raw(handshake = hs)
            val e = assertFailsWith<WsHandshakeFailure>(kind.name) { OkHttpWebSockets.connect(client(o(s)), spec(u(s)), scope) }
            assertEquals(kind, e.kind)
        }
    }

    @Test fun `P-N1 subprotocol echo is checked - equal passes, different is refused`() = runBlocking {
        val good = raw()
        OkHttpWebSockets.connect(client(o(good)), spec(u(good), subprotocol = "paseo.bearer.x"), scope).close()
        assertEquals("paseo.bearer.x", good.requests.single()["sec-websocket-protocol"])
        val bad = raw(handshake = { _, a ->
            "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: $a\r\nSec-WebSocket-Protocol: other\r\n\r\n"
        })
        val e = assertFailsWith<WsHandshakeFailure> { OkHttpWebSockets.connect(client(o(bad)), spec(u(bad), subprotocol = "paseo.bearer.x"), scope) }
        assertEquals(WsHandshakeFailure.Kind.SubprotocolMismatch, e.kind)
    }

    @Test fun `P-N1 one frame of 8 MiB + 1 closes with 1009`() = runBlocking {
        val serverSawClose = CompletableDeferred<CloseReason?>()
        val s = raw { ws ->
            ws.outgoing.send(Frame.Text(fin = true, data = ByteArray(8 * 1024 * 1024 + 1) { 'a'.code.toByte() }))
            for (f in ws.incoming) if (f is Frame.Close) { serverSawClose.complete(f.readReason()); break }
        }
        val ws = OkHttpWebSockets.connect(client(o(s)), spec(u(s)), scope)
        val local = withTimeout(10_000) { ws.closeReason.await() }
        assertEquals(1009, local?.code?.toInt())
        assertEquals(1009, withTimeout(5_000) { serverSawClose.await() }?.code?.toInt())
    }

    @Test fun `P-N1 fragments that add up to 8 MiB + 1 close with 1009 (running total cap)`() = runBlocking {
        val serverSawClose = CompletableDeferred<CloseReason?>()
        val s = raw { ws ->
            val half = 4 * 1024 * 1024
            ws.outgoing.send(Frame.Text(fin = false, data = ByteArray(half) { 'a'.code.toByte() }))
            ws.outgoing.send(Frame.Text(fin = true, data = ByteArray(half + 1) { 'a'.code.toByte() })) // continuation
            for (f in ws.incoming) if (f is Frame.Close) { serverSawClose.complete(f.readReason()); break }
        }
        val ws = OkHttpWebSockets.connect(client(o(s)), spec(u(s)), scope)
        assertEquals(1009, withTimeout(10_000) { ws.closeReason.await() }?.code?.toInt())
        assertEquals(1009, withTimeout(5_000) { serverSawClose.await() }?.code?.toInt())
    }

    @Test fun `P-N1 exactly 8 MiB in fragments is delivered whole`() = runBlocking {
        val s = raw { ws ->
            ws.outgoing.send(Frame.Text(fin = false, data = ByteArray(4 * 1024 * 1024) { 'a'.code.toByte() }))
            ws.outgoing.send(Frame.Text(fin = true, data = ByteArray(4 * 1024 * 1024) { 'b'.code.toByte() }))
            ws.incoming.consumeEach { }
        }
        val ws = OkHttpWebSockets.connect(client(o(s)), spec(u(s)), scope)
        val m = withTimeout(10_000) { ws.incoming.receive() } as WsMessage.Text
        assertEquals(8 * 1024 * 1024, m.text.length)
        ws.close()
    }

    @Test fun `P-N1 ping every interval and pings from the server are answered`() = runBlocking {
        val pings = AtomicInteger()
        val pong = CompletableDeferred<String>()
        val s = raw { ws ->
            ws.outgoing.send(Frame.Ping("srv".toByteArray()))
            for (f in ws.incoming) when (f) {
                is Frame.Ping -> { pings.incrementAndGet(); ws.outgoing.send(Frame.Pong(f.data)) }
                is Frame.Pong -> pong.complete(String(f.data))
                else -> {}
            }
        }
        val ws = OkHttpWebSockets.connect(client(o(s)), spec(u(s), pingMs = 200, pongMs = 1_000), scope)
        assertEquals("srv", withTimeout(2_000) { pong.await() })
        delay(1_100)
        assertTrue(pings.get() in 4..6, "pings in 1.1 s at 200 ms: ${pings.get()}")
        assertTrue(!ws.closeReason.isCompleted, "answered pings keep the socket open")
        ws.close()
        assertEquals(20_000, WsSpec(url = "ws://x", headers = emptyList(), subprotocol = null, maxMessageBytes = 1).pingIntervalMs)
    }

    @Test fun `P-N1 no pong within the timeout closes with 1001`() = runBlocking {
        val s = raw { ws -> ws.incoming.consumeEach { } } // swallows pings, never answers
        val ws = OkHttpWebSockets.connect(client(o(s)), spec(u(s), pingMs = 200, pongMs = 300), scope)
        assertEquals(1001, withTimeout(3_000) { ws.closeReason.await() }?.code?.toInt())
    }

    @Test fun `P-N1 the trust gate runs for the upgrade too`() = runBlocking {
        val s = raw()
        val e = assertFailsWith<TrustRefusal> {
            OkHttpWebSockets.connect(client("http://127.0.0.1:1"), spec(u(s)), scope)
        }
        assertEquals(TrustRefusal.Kind.OriginChanged, e.kind)
        assertTrue(s.requests.isEmpty())
    }

    @Tag("live") @Test fun `P-N1 live - hello and server_info round trip with Paseo 0_11_2`() = runBlocking {
        val url = Live.require(Live.paseoUrl, "NETP_PASEO_URL")
        val password = Live.password()
        val origin = url.replace("ws://", "http://").substringBefore("/ws").let { if (it.count { c -> c == ':' } == 1) "$it:80" else it }
        val ws = OkHttpWebSockets.connect(
            client(origin),
            WsSpec(url = url, headers = listOf("Authorization" to "Bearer $password"), subprotocol = "paseo.bearer.$password",
                maxMessageBytes = 8L * 1024 * 1024, pingIntervalMs = 20_000),
            scope,
        )
        val t0 = System.nanoTime()
        ws.sendText(
            """{"type":"hello","clientId":"netp-spike","clientType":"mobile","protocolVersion":1,"appVersion":"0.8.0",""" +
                """"auth":{"kind":"password","password":${quote(password)}},"capabilities":{"hello_rejection":true}}""",
        )
        var version: String? = null
        withTimeout(8_000) {
            while (version == null) {
                val m = ws.incoming.receive() as? WsMessage.Text ?: continue
                if (m.text.contains("\"server_info\"")) version = Regex("\"version\":\"([^\"]+)\"").find(m.text)?.groupValues?.get(1)
            }
        }
        val ms = (System.nanoTime() - t0) / 1_000_000
        Live.result("pn1.paseo.serverInfo.version=$version helloToServerInfoMs=$ms extensions=none")
        assertEquals("0.11.2", version)
        // App-level ping, as Flutter sends every 20 s.
        ws.sendText("""{"type":"ping"}""")
        withTimeout(5_000) { while ((ws.incoming.receive() as? WsMessage.Text)?.text?.contains("pong") != true) Unit }
        ws.close()
        assertEquals(1000, withTimeout(5_000) { ws.closeReason.await() }?.code?.toInt())
    }

    @Tag("live") @Test fun `P-N1 live - Paseo wrong password is refused`() = runBlocking {
        val url = Live.require(Live.paseoUrl, "NETP_PASEO_URL")
        val origin = url.replace("ws://", "http://").substringBefore("/ws")
        val outcome = runCatching {
            val ws = OkHttpWebSockets.connect(
                client(origin),
                WsSpec(url = url, headers = listOf("Authorization" to "Bearer wrong"), subprotocol = "paseo.bearer.wrong",
                    maxMessageBytes = 8L * 1024 * 1024, pingIntervalMs = 20_000),
                scope,
            )
            ws.sendText("""{"type":"hello","clientId":"netp-spike","clientType":"mobile","protocolVersion":1,"appVersion":"0.8.0","auth":{"kind":"password","password":"wrong"},"capabilities":{"hello_rejection":true}}""")
            withTimeout(8_000) { ws.closeReason.await() }
        }
        Live.result("pn1.paseo.wrongPassword=${outcome.exceptionOrNull()?.let { (it as? WsHandshakeFailure)?.kind ?: it.javaClass.simpleName } ?: "close ${outcome.getOrNull()?.code}"}")
        assertTrue(outcome.isFailure || outcome.getOrNull()?.code?.toInt() == 4401)
    }

    private fun o(s: RawWsServer) = "http://127.0.0.1:${s.port}"
    private fun u(s: RawWsServer) = "ws://127.0.0.1:${s.port}/ws"
    private fun quote(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
