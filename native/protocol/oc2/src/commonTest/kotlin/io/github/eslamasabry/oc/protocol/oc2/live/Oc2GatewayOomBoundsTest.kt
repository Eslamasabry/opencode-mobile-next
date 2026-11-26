package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.ConnectionCredentials
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.net.NetClient
import io.github.eslamasabry.oc.core.net.TransportError
import io.github.eslamasabry.oc.core.security.Redactor
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
import kotlinx.coroutines.launch
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
                    repeat(256) {
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
                    written <= 256 * 1024L,
                    "readFailure drained $written bytes of a 2 MiB error response",
                )
            } finally {
                g.close()
            }
        }
}
