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
import okhttp3.OkHttpClient

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
            val acceptor =
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
                            // Hold the connection open until the client closes or cancels it.
                            try {
                                while (input.read() >= 0) {
                                    // Drain until peer closes socket.
                                }
                            } catch (_: IOException) {
                                // Peer reset/closed socket.
                            } finally {
                                serverClosed.countDown()
                            }
                        }
                    } catch (_: IOException) {
                        serverClosed.countDown()
                    }
                }

            var capturedClient: OkHttpClient? = null
            val engines =
                OkHttpEngineFactory(
                    base = { spec -> hardenedBaseClient(spec).also { capturedClient = it } }
                )
            val origin = ServerOrigin("http", "127.0.0.1", server.localPort)
            val net =
                HttpClientFactory(engines)
                    .create(TransportSpec(origin, HostClass.Loopback, cleartextPermitted = true))
            val visibility = MutableStateFlow(Visibility.Foreground)
            val network =
                object : NetworkStatus {
                    override val state: StateFlow<NetworkState> =
                        MutableStateFlow(NetworkState(NetworkState.Online.Yes, defaultNetworkId = 1L))
                }
            val connector = KtorSseConnector(net.http) { url("$origin/api/event") }
            val loop =
                EventStreamLoop(
                    connector,
                    StreamPlan(BackoffPolicy.OpenCode2, HostClass.Loopback),
                    NetworkGate(network),
                    Clock.System,
                    Random(1),
                    StreamInputs(visibility = visibility),
                )
            val gotFrame = CompletableDeferred<Unit>()
            val suspended = CompletableDeferred<Unit>()
            val job =
                launch(Dispatchers.Default) {
                    loop.signals().collect { signal ->
                        when (signal) {
                            is StreamSignal.Frame -> if (signal.frame.data == "hi") gotFrame.complete(Unit)
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
                    (net.http.engine.config as io.ktor.client.engine.okhttp.OkHttpConfig)
                        .preconfigured!!
                        .interceptors
                        .filterIsInstance<LiveCalls>()
                        .single()
                assertEquals(0, liveCalls.trackedCallsCount(), "SSE call remained tracked after cancel")
            } finally {
                job.cancel()
                net.close()
                server.close()
                acceptor.join(2_000)
                check(capturedClient != null)
            }
        }

    private fun LiveCalls.trackedCallsCount(): Int {
        val field = LiveCalls::class.java.getDeclaredField("calls")
        field.isAccessible = true
        return (field.get(this) as Collection<*>).size
    }
}
