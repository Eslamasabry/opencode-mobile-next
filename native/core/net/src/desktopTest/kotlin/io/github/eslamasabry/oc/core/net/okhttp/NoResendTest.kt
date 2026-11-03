package io.github.eslamasabry.oc.core.net.okhttp

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.net.HttpClientFactory
import io.github.eslamasabry.oc.core.net.NetClient
import io.github.eslamasabry.oc.core.net.TransportSpec
import io.github.eslamasabry.oc.core.security.HostClass
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Real sockets, so real time: runBlocking, not runTest (virtual time would fire HttpTimeout while IO is pending).
 *
 * Audit BLOCKER 1 (codex-04 #1): no mutation is ever sent twice by the stack, whatever the cause — a lost pooled
 * connection, `503 Retry-After: 0`, a redirect, or any other OkHttp follow-up. Full stack: our Ktor client factory
 * over the OkHttp engine against a real loopback socket server that counts what it receives.
 */
class NoResendTest {
    private val server = CountingServer()

    @AfterTest
    fun stop() = server.close()

    private fun client(extra: Interceptor? = null): NetClient {
        val engines = OkHttpEngineFactory(base = { spec ->
            hardenedBaseClient(spec).let { c -> if (extra == null) c else c.newBuilder().addInterceptor(extra).build() }
        })
        return HttpClientFactory(engines).create(
            TransportSpec(ServerOrigin("http", "127.0.0.1", server.port), HostClass.Loopback, cleartextPermitted = true),
        )
    }

    private fun assertDispatchUncertain(outcome: Outcome<*>) {
        val failure = assertIs<Outcome.Failed>(outcome).failure
        assertEquals(RefusalReason.DispatchUncertain, assertIs<Failure.Refused>(failure).reason)
    }

    @Test
    fun `B-PROTO-128 POST on a reused connection that drops after the request is sent once`() = runBlocking {
        server.handler = { req, _ -> if (req.method == "POST") Reply.CloseWithoutAnswer else Reply.Ok("warm") }
        val net = client()
        assertEquals("warm", net.exchange({ url(server.url("/warm")) }) { it.bodyAsText() }.let { (it as Outcome.Ok).value })
        val outcome = net.exchange({ method = HttpMethod.Post; url(server.url("/prompt")); setBody("{\"text\":\"hi\"}") }) {
            it.bodyAsText()
        }
        assertDispatchUncertain(outcome)
        assertEquals(1, server.count("POST"))
        assertEquals(listOf("GET", "POST"), server.requests.map { it.method })
        assertEquals(1, server.requests.last().connection) // the POST rode the pooled connection
        net.close()
    }

    @Test
    fun `B-PROTO-128 503 with Retry-After 0 is not followed for a POST with a body`() = runBlocking {
        server.handler = { _, _ -> Reply.Status(503, listOf("Retry-After" to "0")) }
        val net = client()
        val outcome = net.exchange({ method = HttpMethod.Post; url(server.url("/prompt")); setBody("{}") }) { it.bodyAsText() }
        assertIs<Failure.Busy>(assertIs<Outcome.Failed>(outcome).failure)
        assertEquals(1, server.count("POST"))
        net.close()
    }

    @Test
    fun `B-PROTO-128 503 with Retry-After 0 is not followed for a bodyless DELETE`() = runBlocking {
        server.handler = { _, _ -> Reply.Status(503, listOf("Retry-After" to "0")) }
        val net = client()
        val outcome = net.exchange({ method = HttpMethod.Delete; url(server.url("/session/s1")) }) { it.bodyAsText() }
        assertIs<Failure.Busy>(assertIs<Outcome.Failed>(outcome).failure)
        assertEquals(1, server.count("DELETE"))
        net.close()
    }

    @Test
    fun `B-PROTO-129 a redirect on a POST is never followed`() = runBlocking {
        server.handler = { _, _ -> Reply.Status(307, listOf("Location" to server.url("/elsewhere"))) }
        val net = client()
        val outcome = net.exchange({ method = HttpMethod.Post; url(server.url("/prompt")); setBody("{}") }) { it.bodyAsText() }
        assertEquals(307, assertIs<Failure.Rejected>(assertIs<Outcome.Failed>(outcome).failure).status)
        assertEquals(1, server.requests.size)
        net.close()
    }

    @Test
    fun `B-PROTO-128 any second network attempt of a mutation is refused by the guard`() = runBlocking {
        server.handler = { _, _ -> Reply.Ok("done") }
        // Stands for every OkHttp follow-up path (421 on a coalesced HTTP/2 connection, 408, future ones): something
        // below our application interceptor proceeds a second time with the same request.
        val replay = Interceptor { chain ->
            chain.proceed(chain.request()).close()
            chain.proceed(chain.request())
        }
        val net = client(extra = replay)
        val outcome = net.exchange({ method = HttpMethod.Post; url(server.url("/prompt")); setBody("{}") }) { it.bodyAsText() }
        assertDispatchUncertain(outcome)
        assertEquals(1, server.count("POST"))
        net.close()
    }

    @Test
    fun `B-PROTO-128 reads keep working and a GET is still a read`() = runBlocking {
        server.handler = { _, _ -> Reply.Ok("hello") }
        val net = client()
        val outcome = net.exchange({ url(server.url("/health")) }) { it.bodyAsText() }
        assertEquals("hello", assertIs<Outcome.Ok<String>>(outcome).value)
        net.close()
    }
}

internal data class SeenRequest(val method: String, val path: String, val connection: Int)

internal sealed interface Reply {
    data class Ok(val body: String) : Reply
    data class Status(val code: Int, val headers: List<Pair<String, String>>) : Reply
    data object CloseWithoutAnswer : Reply

    /** `Transfer-Encoding: chunked`: each chunk after [gapMs]; then the body ends, or the server goes quiet. */
    data class Chunked(val chunks: List<String>, val gapMs: Long, val hangAfter: Boolean = false) : Reply
}

/**
 * HTTP/1.1 keep-alive server on loopback that records every request it reads. [socket] may be a TLS server socket
 * ([scheme] `https`): a failed handshake then records nothing.
 */
internal class CountingServer(
    private val socket: ServerSocket = ServerSocket(0, 50, InetAddress.getLoopbackAddress()),
    private val scheme: String = "http",
    /** Like the OpenCode 2 server (6 s): a connection idle this long between requests is closed. 0 = never. */
    private val idleCloseMs: Int = 0,
) : AutoCloseable {
    val port: Int = socket.localPort
    val requests: MutableList<SeenRequest> = Collections.synchronizedList(mutableListOf())
    val connectionsSeen = AtomicInteger()

    @Volatile var handler: (SeenRequest, Int) -> Reply = { _, _ -> Reply.Ok("") }

    fun url(path: String) = "$scheme://127.0.0.1:$port$path"

    fun count(method: String) = requests.count { it.method == method }

    private val acceptor = thread(isDaemon = true, name = "counting-server") {
        while (!socket.isClosed) {
            val client = try { socket.accept() } catch (_: Exception) { break }
            val id = connectionsSeen.incrementAndGet()
            thread(isDaemon = true) { serve(client, id) }
        }
    }

    private fun serve(client: Socket, id: Int): Unit = client.use { s ->
        val input = BufferedInputStream(s.getInputStream())
        val out = s.getOutputStream()
        while (true) {
            s.soTimeout = idleCloseMs
            val head = try { readHead(input) } catch (_: java.net.SocketTimeoutException) { null } ?: return
            s.soTimeout = 0
            val (method, path) = head.first().split(" ").let { it[0] to it[1] }
            val length = head.drop(1).firstOrNull { it.startsWith("content-length:", ignoreCase = true) }
                ?.substringAfter(':')?.trim()?.toInt() ?: 0
            repeat(length) { input.read() }
            val seen = SeenRequest(method, path, id)
            requests += seen
            val text = when (val reply = handler(seen, id)) {
                Reply.CloseWithoutAnswer -> return
                is Reply.Chunked -> {
                    out.write("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nTransfer-Encoding: chunked\r\n\r\n".toByteArray())
                    out.flush()
                    for (chunk in reply.chunks) {
                        Thread.sleep(reply.gapMs)
                        val bytes = chunk.toByteArray()
                        out.write("${Integer.toHexString(bytes.size)}\r\n".toByteArray())
                        out.write(bytes)
                        out.write("\r\n".toByteArray())
                        out.flush()
                    }
                    if (reply.hangAfter) {
                        while (!socket.isClosed && !s.isClosed) Thread.sleep(50)
                        return
                    }
                    "0\r\n\r\n"
                }
                is Reply.Ok -> "HTTP/1.1 200 OK\r\nContent-Length: ${reply.body.length}\r\n\r\n${reply.body}"
                is Reply.Status ->
                    "HTTP/1.1 ${reply.code} X\r\n" + reply.headers.joinToString("") { "${it.first}: ${it.second}\r\n" } +
                        "Content-Length: 0\r\n\r\n"
            }
            out.write(text.toByteArray())
            out.flush()
        }
    }

    private fun readHead(input: InputStream): List<String>? {
        val lines = mutableListOf<String>()
        val line = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) return null
            if (b == '\n'.code) {
                val text = line.toString(Charsets.ISO_8859_1).trimEnd('\r')
                line.reset()
                if (text.isEmpty()) return lines.takeIf { it.isNotEmpty() }
                lines += text
            } else {
                line.write(b)
            }
        }
    }

    override fun close() {
        socket.close()
        acceptor.join(1_000)
    }
}
