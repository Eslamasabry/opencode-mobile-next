package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.ConnectionCoroutineScope
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.pace.Visibility
import io.github.eslamasabry.oc.core.domain.ConnectionCredentials
import io.github.eslamasabry.oc.core.domain.GatewaySpec
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.ProfileTrust
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.model.ServerProfile
import io.github.eslamasabry.oc.core.model.StreamStatus
import io.github.eslamasabry.oc.core.net.NetClient
import io.github.eslamasabry.oc.core.net.TransportError
import io.github.eslamasabry.oc.core.net.stream.SseConnectResult
import io.github.eslamasabry.oc.core.net.stream.SseConnector
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.core.testing.TestDispatchers
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeFully
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield

/**
 * B-617: error responses larger than 64 KiB must stop reading once the cap is exceeded instead of
 * buffering the entire response body into memory first.
 */
class Oc2GatewayOomBoundsTest {
    private val clock =
        object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(1_000)
        }

    private fun gateway(engine: MockEngine): Oc2Gateway =
        Oc2Gateway(
            NetClient(
                HttpClient(engine) {
                    expectSuccess = false
                    followRedirects = false
                },
                { TransportError.Other },
            ),
            ServerOrigin("https", "example.test", 443),
            ConnectionCredentials("opencode", "test-secret"),
            clock,
            Redactor(),
            initialDialect = Oc2Dialect.Stable,
        )

    @Test
    fun `B-617 readFailure bounds error body at 64 KiB without reading the entire stream`() =
        runTest {
            val channel = ByteChannel(autoFlush = true)
            val piece = ByteArray(8 * 1024) { 'x'.code.toByte() }
            var written = 0L
            backgroundScope.launch {
                try {
                    repeat(512) {
                        channel.writeFully(piece)
                        written += piece.size
                        yield()
                    }
                    channel.flushAndClose()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
            }
            val g = gateway(MockEngine { respond(channel, HttpStatusCode.InternalServerError) })
            try {
                val failed = assertIs<Outcome.Failed>(g.session(SessionId("s1")))
                val server = assertIs<Failure.Server>(failed.failure)
                assertEquals(500, server.status)
                assertNull(server.tag)
                assertTrue(
                    written <= 64 * 1024L + 1024 * 1024L,
                    "readFailure drained $written bytes of a 4 MiB error response",
                )
            } finally {
                g.close()
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `B-617 HiddenIdle visibility in GatewaySpec suspends Oc2Gateway SSE reconnection`() =
        runTest {
            val g = gateway(MockEngine { respond("{}", HttpStatusCode.OK) })
            val visibility = MutableStateFlow(Visibility.HiddenIdle)
            var attempts = 0
            val connector = SseConnector { _, _ ->
                attempts += 1
                SseConnectResult.Failed(Failure.Network(NetworkWhy.Refused), stop = false)
            }
            val profile =
                ServerProfile(
                    ProfileId("p1"),
                    "Test",
                    BackendKind.OpenCode2,
                    ServerOrigin("https", "example.test", 443),
                    ProfileTrust(null, null),
                    managedOnPhone = false,
                    username = "opencode",
                )
            val spec =
                GatewaySpec(
                    profile = profile,
                    credentials = ConnectionCredentials("opencode", "test-secret"),
                    scope = ConnectionCoroutineScope(backgroundScope.coroutineContext),
                    visibility = visibility,
                )
            try {
                g.start(
                    spec,
                    TestDispatchers(StandardTestDispatcher(testScheduler)),
                    streamConnector = connector,
                )
                advanceTimeBy(120_000)
                runCurrent()
                assertEquals(0, attempts, "HiddenIdle must not dial or reconnect SSE")
                assertEquals(StreamStatus.Suspended, g.events.status.value)

                visibility.value = Visibility.Foreground
                runCurrent()
                assertTrue(attempts >= 1, "returning to Foreground must resume SSE dialing")

                val beforeIdle = attempts
                visibility.value = Visibility.HiddenIdle
                runCurrent()
                advanceTimeBy(120_000)
                runCurrent()
                assertEquals(beforeIdle, attempts, "returning to HiddenIdle must halt reconnects")
                assertEquals(StreamStatus.Suspended, g.events.status.value)
            } finally {
                g.close()
            }
        }
}
