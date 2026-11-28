package io.github.eslamasabry.oc.core.net.okhttp

import io.github.eslamasabry.oc.core.common.pace.Visibility
import io.github.eslamasabry.oc.core.model.NetworkState
import io.github.eslamasabry.oc.core.model.NetworkStatus
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.model.StreamStatus
import io.github.eslamasabry.oc.core.net.HttpClientFactory
import io.github.eslamasabry.oc.core.net.TransportSpec
import io.github.eslamasabry.oc.core.net.gate.NetworkGate
import io.github.eslamasabry.oc.core.net.stream.BackoffPolicy
import io.github.eslamasabry.oc.core.net.stream.EventStreamLoop
import io.github.eslamasabry.oc.core.net.stream.KtorSseConnector
import io.github.eslamasabry.oc.core.net.stream.StreamInputs
import io.github.eslamasabry.oc.core.net.stream.StreamPlan
import io.github.eslamasabry.oc.core.net.stream.StreamSignal
import io.github.eslamasabry.oc.core.security.HostClass
import io.ktor.client.engine.okhttp.OkHttpConfig
import io.ktor.client.request.url
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * B-617: when EventStreamLoop exits readUntilEnd early (HiddenIdle), the underlying Ktor/OkHttp
 * SSE call must be cancelled immediately rather than staying blocked until the 45 s read timeout.
 */
class SseEarlyCancelTest {
    @Test
    fun `B-617 HiddenIdle cancels blocked SSE call immediately without waiting for socket timeout`() =
        runBlocking {
            val serverClosed = CountDownLatch(1)
            val server = ServerSocket(0, 5, InetAddress.getLoopbackAddress())
            val acceptor = startHangingSseServer(server, serverClosed)
            val origin = ServerOrigin("http", "127.0.0.1", server.localPort)
            val net =
                HttpClientFactory(OkHttpEngineFactory())
                    .create(TransportSpec(origin, HostClass.Loopback, cleartextPermitted = true))
            val visibility = MutableStateFlow(Visibility.Foreground)
            val loop = buildLoop(net.http, origin, visibility)
            val gotFrame = CompletableDeferred<Unit>()
            val suspended = CompletableDeferred<Unit>()
            val job =
                launch(Dispatchers.Default) {
                    loop.signals().collect { signal ->
                        when (signal) {
                            is StreamSignal.Frame ->
                                if (signal.frame.data == "hi") gotFrame.complete(Unit)
                            is StreamSignal.Status ->
                                if (signal.status == StreamStatus.Suspended) suspended.complete(Unit)
                        }
                    }
                }
            try {
                withTimeout(5_000) { gotFrame.await() }
                val startedAt = System.nanoTime()
                visibility.value = Visibility.HiddenIdle
                withTimeout(3_000) { suspended.await() }
                val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
                assertTrue(elapsedMs < 3_000, "early exit took $elapsedMs ms")
                assertTrue(
                    serverClosed.await(3, TimeUnit.SECONDS),
                    "underlying SSE socket stayed pinned open after HiddenIdle",
                )
                val liveCalls =
                    (net.http.engine.config as OkHttpConfig)
                        .preconfigured!!
                        .interceptors
                        .filterIsInstance<LiveCalls>()
                        .single()
                assertEquals(0, liveCalls.trackedCount, "SSE call remained tracked after cancel")
            } finally {
                job.cancel()
                net.close()
                server.close()
                acceptor.join(2_000)
            }
        }

    private fun buildLoop(
        http: io.ktor.client.HttpClient,
        origin: ServerOrigin,
        visibility: MutableStateFlow<Visibility>,
    ): EventStreamLoop {
        val network =
            object : NetworkStatus {
                override val state: StateFlow<NetworkState> =
                    MutableStateFlow(NetworkState(NetworkState.Online.Yes, defaultNetworkId = 1L))
            }
        val connector = KtorSseConnector(http) { url("$origin/api/event") }
        return EventStreamLoop(
            connector,
            StreamPlan(BackoffPolicy.OpenCode2, HostClass.Loopback),
            NetworkGate(network),
            Clock.System,
            Random(1),
            StreamInputs(visibility = visibility),
        )
    }

    private fun startHangingSseServer(server: ServerSocket, closed: CountDownLatch): Thread =
        thread(isDaemon = true, name = "sse-hang-server") {
            try {
                server.accept().use { client ->
                    val input = client.getInputStream()
                    val header = StringBuilder()
                    while (!header.endsWith("\r\n\r\n")) {
                        val b = input.read()
                        if (b < 0) return@use
                        header.append(b.toChar())
                    }
                    val out = client.getOutputStream()
                    val payload = "data: hi\n\n".toByteArray()
                    val head =
                        "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/event-stream\r\n" +
                            "Transfer-Encoding: chunked\r\n\r\n" +
                            "${Integer.toHexString(payload.size)}\r\n"
                    out.write(head.toByteArray())
                    out.write(payload)
                    out.write("\r\n".toByteArray())
                    out.flush()
                    try {
                        while (input.read() >= 0) {
                            // Hold open until peer cancels or closes the socket.
                        }
                    } catch (_: IOException) {
                        // Peer reset/closed socket.
                    } finally {
                        closed.countDown()
                    }
                }
            } catch (_: IOException) {
                closed.countDown()
            }
        }
}
