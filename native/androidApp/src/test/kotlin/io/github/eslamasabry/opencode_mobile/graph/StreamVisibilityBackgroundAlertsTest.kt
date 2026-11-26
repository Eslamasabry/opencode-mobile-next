package io.github.eslamasabry.opencode_mobile.graph

import android.app.Activity
import android.app.Application
import io.github.eslamasabry.oc.core.common.AppBuildInfo
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Channel
import io.github.eslamasabry.oc.core.common.pace.Visibility
import io.github.eslamasabry.oc.core.data.migration.StateMigrationGate
import io.github.eslamasabry.oc.core.model.NetworkState
import io.github.eslamasabry.oc.core.model.NetworkStatus
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.model.StreamStatus
import io.github.eslamasabry.oc.core.net.HttpClientFactory
import io.github.eslamasabry.oc.core.net.NetClient
import io.github.eslamasabry.oc.core.net.TransportSpec
import io.github.eslamasabry.oc.core.net.gate.NetworkGate
import io.github.eslamasabry.oc.core.net.okhttp.OkHttpEngineFactory
import io.github.eslamasabry.oc.core.net.stream.BackoffPolicy
import io.github.eslamasabry.oc.core.net.stream.EventStreamLoop
import io.github.eslamasabry.oc.core.net.stream.KtorSseConnector
import io.github.eslamasabry.oc.core.net.stream.StreamInputs
import io.github.eslamasabry.oc.core.net.stream.StreamPlan
import io.github.eslamasabry.oc.core.net.stream.StreamSignal
import io.github.eslamasabry.oc.core.security.HostClass
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.engine.connection.ConnectionVisibilitySource
import io.github.eslamasabry.oc.feature.background.presentation.BackgroundLiveFacts
import io.ktor.client.engine.okhttp.OkHttpConfig
import io.ktor.client.request.url
import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * B-617 background-alerts regression guard:
 * - hidden + ReplyWatch active or Live Mode active must report HiddenWatching and keep the SSE
 *   stream alive so reply-finished and live-mode alerts still arrive;
 * - hidden + nothing watching must report HiddenIdle and close/suspend the SSE stream without
 *   leaking tracked OkHttp calls.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class StreamVisibilityBackgroundAlertsTest {
    private class WiringFixture(
        val job: CompletableJob,
        val appVisibility: AppVisibility,
        val replyWatch: ReplyWatchFlag,
        val background: BackgroundHub,
        val streamVisibility: StateFlow<Visibility>,
    )

    private class PushSseServer : AutoCloseable {
        private val server = ServerSocket(0, 5, InetAddress.getLoopbackAddress())
        private val outReady = CountDownLatch(1)
        val clientClosed = CountDownLatch(1)
        @Volatile private var out: OutputStream? = null
        val port: Int = server.localPort

        private val worker =
            thread(isDaemon = true, name = "sse-push-server") {
                try {
                    server.accept().use { socket ->
                        val input = socket.getInputStream()
                        val header = StringBuilder()
                        while (!header.endsWith("\r\n\r\n")) {
                            val b = input.read()
                            if (b < 0) return@use
                            header.append(b.toChar())
                        }
                        val stream = socket.getOutputStream()
                        val head =
                            "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: text/event-stream\r\n" +
                                "Transfer-Encoding: chunked\r\n\r\n"
                        stream.write(head.toByteArray())
                        stream.flush()
                        out = stream
                        outReady.countDown()
                        try {
                            while (input.read() >= 0) {
                                // Wait until the client closes or cancels the socket.
                            }
                        } catch (_: IOException) {
                            // Peer closed or reset the socket.
                        } finally {
                            clientClosed.countDown()
                        }
                    }
                } catch (_: IOException) {
                    outReady.countDown()
                    clientClosed.countDown()
                }
            }

        fun sendEvent(data: String) {
            assertTrue(outReady.await(5, TimeUnit.SECONDS), "SSE client did not connect")
            val stream = requireNotNull(out)
            val payload = "data: $data\n\n".toByteArray()
            stream.write("${Integer.toHexString(payload.size)}\r\n".toByteArray())
            stream.write(payload)
            stream.write("\r\n".toByteArray())
            stream.flush()
        }

        override fun close() {
            server.close()
            worker.join(2_000)
        }
    }

    @Test
    fun `B-617 hidden with ReplyWatch active stays HiddenWatching and delivers reply-finished event`() =
        runBlocking {
            val fixture = createFixture()
            val server = PushSseServer()
            val net = createClient(server.port)
            val frames = CopyOnWriteArrayList<String>()
            val statuses = CopyOnWriteArrayList<StreamStatus>()
            val gotFirst = CompletableDeferred<Unit>()
            val gotFinished = CompletableDeferred<Unit>()
            var collector: Job? = null
            try {
                val activity = Robolectric.buildActivity(Activity::class.java).create().start()
                awaitVisibility(fixture.streamVisibility, Visibility.Foreground)
                collector =
                    launch(Dispatchers.Default) {
                        buildLoop(net, server.port, fixture.streamVisibility).signals().collect { signal ->
                            when (signal) {
                                is StreamSignal.Status -> statuses += signal.status
                                is StreamSignal.Frame -> {
                                    frames += signal.frame.data
                                    if (signal.frame.data == "started") gotFirst.complete(Unit)
                                    if (signal.frame.data == "reply-finished") gotFinished.complete(Unit)
                                }
                            }
                        }
                    }
                server.sendEvent("started")
                withTimeout(5_000) { gotFirst.await() }

                fixture.replyWatch.setPending(true)
                activity.stop()
                awaitVisibility(fixture.streamVisibility, Visibility.HiddenWatching)
                assertEquals(
                    Visibility.HiddenWatching,
                    fixture.streamVisibility.value,
                    "pending ReplyWatch handoff must report HiddenWatching while hidden",
                )
                delay(50)
                fixture.replyWatch.set(true)
                delay(50)
                assertEquals(
                    Visibility.HiddenWatching,
                    fixture.streamVisibility.value,
                    "active ReplyWatch must report HiddenWatching while hidden",
                )

                delay(100)
                server.sendEvent("reply-finished")
                withTimeout(3_000) { gotFinished.await() }
                assertFalse(
                    statuses.any { it == StreamStatus.Suspended },
                    "stream suspended while ReplyWatch was watching: $statuses",
                )
                assertTrue("reply-finished" in frames, "reply-finished event missed: $frames")
            } finally {
                collector?.cancel()
                net.close()
                server.close()
                fixture.job.cancel()
            }
        }

    @Test
    fun `B-617 hidden with Live Mode active stays HiddenWatching and keeps SSE alive`() =
        runBlocking {
            val fixture = createFixture()
            val server = PushSseServer()
            val net = createClient(server.port)
            val gotFirst = CompletableDeferred<Unit>()
            val gotAlert = CompletableDeferred<Unit>()
            var collector: Job? = null
            try {
                val activity = Robolectric.buildActivity(Activity::class.java).create().start()
                awaitVisibility(fixture.streamVisibility, Visibility.Foreground)
                collector =
                    launch(Dispatchers.Default) {
                        buildLoop(net, server.port, fixture.streamVisibility).signals().collect { signal ->
                            if (signal is StreamSignal.Frame) {
                                if (signal.frame.data == "started") gotFirst.complete(Unit)
                                if (signal.frame.data == "live-alert") gotAlert.complete(Unit)
                            }
                        }
                    }
                server.sendEvent("started")
                withTimeout(5_000) { gotFirst.await() }

                setLiveModeActive(fixture.background, active = true)
                activity.stop()
                awaitVisibility(fixture.streamVisibility, Visibility.HiddenWatching)
                assertEquals(
                    Visibility.HiddenWatching,
                    fixture.streamVisibility.value,
                    "active Live Mode must report HiddenWatching while hidden",
                )

                delay(100)
                server.sendEvent("live-alert")
                withTimeout(3_000) { gotAlert.await() }
            } finally {
                collector?.cancel()
                net.close()
                server.close()
                fixture.job.cancel()
            }
        }

    @Test
    fun `B-617 hidden and idle closes and suspends SSE stream without leaking calls`() =
        runBlocking {
            val fixture = createFixture()
            val server = PushSseServer()
            val net = createClient(server.port)
            val gotFirst = CompletableDeferred<Unit>()
            val suspended = CompletableDeferred<Unit>()
            var collector: Job? = null
            try {
                val activity = Robolectric.buildActivity(Activity::class.java).create().start()
                awaitVisibility(fixture.streamVisibility, Visibility.Foreground)
                collector =
                    launch(Dispatchers.Default) {
                        buildLoop(net, server.port, fixture.streamVisibility).signals().collect { signal ->
                            when (signal) {
                                is StreamSignal.Frame ->
                                    if (signal.frame.data == "started") gotFirst.complete(Unit)
                                is StreamSignal.Status ->
                                    if (signal.status == StreamStatus.Suspended) suspended.complete(Unit)
                            }
                        }
                    }
                server.sendEvent("started")
                withTimeout(5_000) { gotFirst.await() }

                fixture.replyWatch.set(true)
                activity.stop()
                awaitVisibility(fixture.streamVisibility, Visibility.HiddenWatching)
                assertEquals(Visibility.HiddenWatching, fixture.streamVisibility.value)

                fixture.replyWatch.set(false)
                awaitVisibility(fixture.streamVisibility, Visibility.HiddenIdle)
                withTimeout(3_000) { suspended.await() }
                assertTrue(
                    server.clientClosed.await(3, TimeUnit.SECONDS),
                    "SSE socket stayed open after transitioning to HiddenIdle",
                )
                assertEquals(0, liveCallsCount(net), "LiveCalls retained SSE call after HiddenIdle")
            } finally {
                collector?.cancel()
                net.close()
                server.close()
                fixture.job.cancel()
            }
        }

    @Test
    fun `B-617 armed rearm keeps HiddenWatching after watch ends until rearm ends`() =
        runBlocking {
            val fixture = createFixture()
            val server = PushSseServer()
            val net = createClient(server.port)
            val statuses = CopyOnWriteArrayList<StreamStatus>()
            val gotFirst = CompletableDeferred<Unit>()
            val gotRearmFrame = CompletableDeferred<Unit>()
            val suspended = CompletableDeferred<Unit>()
            var collector: Job? = null
            try {
                val activity = Robolectric.buildActivity(Activity::class.java).create().start()
                awaitVisibility(fixture.streamVisibility, Visibility.Foreground)
                collector =
                    launch(Dispatchers.Default) {
                        buildLoop(net, server.port, fixture.streamVisibility).signals().collect { signal ->
                            when (signal) {
                                is StreamSignal.Status -> {
                                    statuses += signal.status
                                    if (signal.status == StreamStatus.Suspended) suspended.complete(Unit)
                                }
                                is StreamSignal.Frame -> {
                                    if (signal.frame.data == "started") gotFirst.complete(Unit)
                                    if (signal.frame.data == "rearm-wake") gotRearmFrame.complete(Unit)
                                }
                            }
                        }
                    }
                server.sendEvent("started")
                withTimeout(5_000) { gotFirst.await() }

                fixture.replyWatch.set(true)
                activity.stop()
                awaitVisibility(fixture.streamVisibility, Visibility.HiddenWatching)

                fixture.replyWatch.setRearming(true)
                fixture.replyWatch.set(false)
                delay(100)
                assertEquals(
                    Visibility.HiddenWatching,
                    fixture.streamVisibility.value,
                    "armed re-arm must keep stream HiddenWatching after watch ends",
                )
                assertFalse(
                    statuses.any { it == StreamStatus.Suspended },
                    "stream suspended while re-arm was armed: $statuses",
                )
                server.sendEvent("rearm-wake")
                withTimeout(3_000) { gotRearmFrame.await() }

                fixture.replyWatch.setRearming(false)
                awaitVisibility(fixture.streamVisibility, Visibility.HiddenIdle)
                withTimeout(3_000) { suspended.await() }
                assertTrue(
                    server.clientClosed.await(3, TimeUnit.SECONDS),
                    "SSE socket stayed open after re-arm cleared",
                )
                assertEquals(0, liveCallsCount(net), "LiveCalls retained SSE call after re-arm cleared")
            } finally {
                collector?.cancel()
                net.close()
                server.close()
                fixture.job.cancel()
            }
        }

    private fun createFixture(): WiringFixture {
        val app = RuntimeEnvironment.getApplication()
        val dispatchers =
            object : AppDispatchers {
                override val main = Dispatchers.Default
                override val default = Dispatchers.Default
                override val io = Dispatchers.IO

                override fun serial(name: String) = Dispatchers.Default.limitedParallelism(1, name)
            }
        val redactor = Redactor()
        val clock = TestClock()
        val job = SupervisorJob()
        val scope = AppCoroutineScope(job + dispatchers.default)
        val build = AppBuildInfo("3.0.2", 10130, Channel.Preview, debuggable = true)
        val migration = MigrationWiring(app, build, redactor, scope, dispatchers, clock)
        val stores =
            PreviewStores(app, dispatchers, redactor, StateMigrationGate.alwaysOpen(), migration, build)
        val appVisibility = AppVisibility(app)
        val replyWatch = ReplyWatchFlag()
        val background = BackgroundHub(app, scope, dispatchers, clock, stores, ResumedActivities(app))
        val streamVisibility = resolveStreamVisibility(appVisibility, replyWatch, background, scope)
        return WiringFixture(job, appVisibility, replyWatch, background, streamVisibility)
    }

    private fun resolveStreamVisibility(
        visibility: AppVisibility,
        replyWatch: ReplyWatchFlag,
        background: BackgroundHub,
        appScope: AppCoroutineScope,
    ): StateFlow<Visibility> {
        val method =
            ProtoNetworkWiring::class.java.declaredMethods.single { it.name == "visibility" }
        val args =
            method.parameterTypes.map { type ->
                when (type) {
                    AppVisibility::class.java -> visibility
                    ReplyWatchFlag::class.java -> replyWatch
                    BackgroundHub::class.java -> background
                    AppCoroutineScope::class.java -> appScope
                    else -> {
                        val ctor = type.constructors.single()
                        ctor.newInstance(visibility, replyWatch, background, appScope)
                    }
                }
            }
        val source =
            method.invoke(ProtoNetworkWiring, *args.toTypedArray()) as ConnectionVisibilitySource
        return source.visibility()
    }

    @Suppress("UNCHECKED_CAST")
    private fun setLiveModeActive(background: BackgroundHub, active: Boolean) {
        val field = BackgroundHub::class.java.getDeclaredField("facts")
        field.isAccessible = true
        val flow = field.get(background) as MutableStateFlow<BackgroundLiveFacts>
        flow.value = BackgroundLiveFacts(supported = true, enabled = active, active = active)
    }

    private suspend fun awaitVisibility(flow: StateFlow<Visibility>, expected: Visibility) {
        withTimeout(2_000) {
            while (flow.value != expected) delay(10)
        }
    }

    private fun createClient(port: Int): NetClient =
        HttpClientFactory(OkHttpEngineFactory())
            .create(
                TransportSpec(
                    ServerOrigin("http", "127.0.0.1", port),
                    HostClass.Loopback,
                    cleartextPermitted = true,
                )
            )

    private fun buildLoop(
        net: NetClient,
        port: Int,
        visibility: StateFlow<Visibility>,
    ): EventStreamLoop {
        val network =
            object : NetworkStatus {
                override val state: StateFlow<NetworkState> =
                    MutableStateFlow(NetworkState(NetworkState.Online.Yes, defaultNetworkId = 1L))
            }
        val connector = KtorSseConnector(net.http) { url("http://127.0.0.1:$port/api/event") }
        return EventStreamLoop(
            connector,
            StreamPlan(BackoffPolicy.OpenCode2, HostClass.Loopback),
            NetworkGate(network),
            Clock.System,
            Random(1),
            StreamInputs(visibility = visibility),
        )
    }

    private fun liveCallsCount(net: NetClient): Int {
        val client = (net.http.engine.config as OkHttpConfig).preconfigured!!
        val liveCalls = client.interceptors.single { it.javaClass.simpleName == "LiveCalls" }
        val field = liveCalls.javaClass.getDeclaredField("calls")
        field.isAccessible = true
        return (field.get(liveCalls) as Collection<*>).size
    }
}
