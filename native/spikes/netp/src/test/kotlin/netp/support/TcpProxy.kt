package netp.support

import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/**
 * A loopback TCP proxy that can misbehave like a real network:
 * - [pauseAll]: stop moving bytes on every open connection without closing it (a stalled path; no FIN, no RST).
 * - [freezeExisting]: the "old network" disappears: open connections go silent forever, new ones work
 *   (what a Wi-Fi to mobile switch looks like to a socket that is not told).
 * - [holdResponsesOfNewConnections]: requests reach the server, replies never come back.
 * - [killAll]: close every connection (RST/FIN).
 */
class TcpProxy(private val targetPort: Int) : AutoCloseable {
    private val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
    val port: Int get() = server.localPort
    private val links = CopyOnWriteArrayList<Link>()
    @Volatile var acceptedConnections = 0
        private set

    private inner class Link(val client: Socket, val upstream: Socket) {
        @Volatile var frozen = false
        @Volatile var paused = false
        @Volatile var downstreamHeld = false

        fun start() {
            pump(client.getInputStream(), upstream.getOutputStream(), downstream = false)
            pump(upstream.getInputStream(), client.getOutputStream(), downstream = true)
        }

        private fun pump(input: InputStream, output: OutputStream, downstream: Boolean) = thread(isDaemon = true) {
            val buf = ByteArray(16 * 1024)
            try {
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    while (paused || frozen || (downstream && downstreamHeld)) Thread.sleep(5)
                    output.write(buf, 0, n)
                    output.flush()
                }
            } catch (_: Exception) {
            } finally {
                if (!frozen) close()
            }
        }

        fun close() {
            runCatching { client.close() }
            runCatching { upstream.close() }
        }
    }

    init {
        thread(isDaemon = true, name = "tcp-proxy-accept") {
            while (!server.isClosed) {
                val c = runCatching { server.accept() }.getOrNull() ?: break
                acceptedConnections++
                val up = Socket(InetAddress.getLoopbackAddress(), targetPort)
                val link = Link(c, up).also { it.downstreamHeld = holdResponsesOfNewConnections }
                links += link
                link.start()
            }
        }
    }

    /** New connections deliver requests upstream but hold every response byte (a reply lost in flight). */
    @Volatile var holdResponsesOfNewConnections = false

    fun pauseAll() = links.forEach { it.paused = true }
    fun resumeAll() = links.forEach { it.paused = false }
    fun freezeExisting() = links.forEach { it.frozen = true }
    fun killAll() = links.forEach { it.close() }

    override fun close() {
        runCatching { server.close() }
        links.forEach { it.close() }
    }
}
