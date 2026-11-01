package netp.support

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Minimal HTTP/1.1 server for the offline halves of P-N3 and P-N6 (mockwebserver3 cannot hold an endless body):
 * - `GET /api/event` answers `text/event-stream` and writes `: heartbeat` every [heartbeatMs] until the socket dies;
 * - `POST /api/session/s/prompt` records the request and answers after [promptDelayMs].
 */
class TinySseServer(private val heartbeatMs: Long, private val promptDelayMs: Long = 0) : AutoCloseable {
    private val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
    val port: Int get() = server.localPort
    val streamsOpened = AtomicInteger()
    val promptsReceived = AtomicInteger()
    private val sockets = CopyOnWriteArrayList<Socket>()

    init {
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val s = runCatching { server.accept() }.getOrNull() ?: break
                sockets += s
                thread(isDaemon = true) { runCatching { serve(s) }; runCatching { s.close() } }
            }
        }
    }

    private fun serve(s: Socket) {
        val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.ISO_8859_1))
        val out = s.getOutputStream()
        while (true) {
            val requestLine = reader.readLine() ?: return
            var contentLength = 0
            while (true) {
                val h = reader.readLine() ?: return
                if (h.isEmpty()) break
                if (h.startsWith("content-length:", ignoreCase = true)) contentLength = h.substringAfter(':').trim().toInt()
            }
            repeat(contentLength) { reader.read() }
            when {
                requestLine.startsWith("GET /api/event") -> {
                    streamsOpened.incrementAndGet()
                    out.write("HTTP/1.1 200 OK\r\ncontent-type: text/event-stream\r\ncache-control: no-cache\r\nconnection: close\r\n\r\n".toByteArray())
                    out.write("data: {\"type\":\"server.connected\"}\n\n".toByteArray())
                    out.flush()
                    while (true) {
                        Thread.sleep(heartbeatMs)
                        out.write(": heartbeat\n\n".toByteArray())
                        out.flush()
                    }
                }
                requestLine.startsWith("POST /api/session/s/prompt") -> {
                    promptsReceived.incrementAndGet()
                    Thread.sleep(promptDelayMs)
                    val body = "{\"data\":{\"type\":\"user\"}}"
                    out.write("HTTP/1.1 200 OK\r\ncontent-type: application/json\r\ncontent-length: ${body.length}\r\n\r\n$body".toByteArray())
                    out.flush()
                }
                else -> {
                    out.write("HTTP/1.1 404 Not Found\r\ncontent-length: 0\r\n\r\n".toByteArray())
                    out.flush()
                }
            }
        }
    }

    override fun close() {
        runCatching { server.close() }
        sockets.forEach { runCatching { it.close() } }
    }
}
