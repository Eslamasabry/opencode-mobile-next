package netp.support

import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import io.ktor.utils.io.streams.asByteWriteChannel
import io.ktor.websocket.RawWebSocket
import io.ktor.websocket.WebSocketSession
import java.io.BufferedInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * A WebSocket server whose handshake can be tampered with (P-N1's 101 validation) and whose frames are handled by
 * Ktor's RawWebSocket (server side, unmasked), so a test can see every control frame the client sends.
 */
class RawWsServer(
    /** Given the request headers (lower-case names) and the correct accept value, returns the raw response head. */
    private val handshake: (headers: Map<String, String>, accept: String) -> String = { h, accept -> ok(h, accept) },
    private val handler: suspend (WebSocketSession) -> Unit,
) : AutoCloseable {
    private val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
    val port: Int get() = server.localPort
    val requests = CopyOnWriteArrayList<Map<String, String>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, _ -> })

    init {
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val s = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) {
                    val input = BufferedInputStream(s.getInputStream())
                    val headers = linkedMapOf<String, String>()
                    readLine(input) // request line
                    while (true) {
                        val line = readLine(input)
                        if (line.isEmpty()) break
                        headers[line.substringBefore(':').trim().lowercase()] = line.substringAfter(':').trim()
                    }
                    requests += headers
                    val key = headers["sec-websocket-key"].orEmpty()
                    val head = handshake(headers, accept(key))
                    s.getOutputStream().write(head.toByteArray(Charsets.ISO_8859_1))
                    s.getOutputStream().flush()
                    if (!head.startsWith("HTTP/1.1 101")) { s.close(); return@thread }
                    scope.launch {
                        val ws = RawWebSocket(
                            input.toByteReadChannel(Dispatchers.IO),
                            s.getOutputStream().asByteWriteChannel(),
                            maxFrameSize = Long.MAX_VALUE,
                            masking = false,
                            coroutineContext = coroutineContext,
                        )
                        try { handler(ws) } finally { runCatching { s.close() } }
                    }
                }
            }
        }
    }

    override fun close() {
        scope.cancel()
        runCatching { server.close() }
    }

    companion object {
        fun accept(key: String): String = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-1").digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray()),
        )

        fun ok(headers: Map<String, String>, accept: String, extra: String = ""): String {
            val proto = headers["sec-websocket-protocol"]?.let { "Sec-WebSocket-Protocol: $it\r\n" }.orEmpty()
            return "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                "Sec-WebSocket-Accept: $accept\r\n$proto$extra\r\n"
        }

        private fun readLine(input: BufferedInputStream): String {
            val sb = StringBuilder()
            while (true) {
                val c = input.read()
                if (c < 0 || c == '\n'.code) break
                if (c != '\r'.code) sb.append(c.toChar())
            }
            return sb.toString()
        }
    }
}
