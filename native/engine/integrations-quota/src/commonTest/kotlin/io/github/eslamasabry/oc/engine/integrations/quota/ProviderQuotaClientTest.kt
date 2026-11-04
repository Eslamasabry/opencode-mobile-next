package io.github.eslamasabry.oc.engine.integrations.quota

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ports test/provider_quota_client_test.dart (rows B-PROTO-084, B-SEC-138, B-SEC-139, B-SEC-140). */
class ProviderQuotaClientTest {
    // ---- B-PROTO-084

    @Test
    fun `B-PROTO-084 GLM uses its fixed collector route with unknown window duration`() =
        runTest {
            val value = quotaFixture(provider = QuotaProvider.Glm)
            value.firstWindow().remove("durationSeconds")
            value.firstWindow().remove("resetsAtMs")
            val transport = FakeQuotaTransport { jsonResponse(value) }
            val gateway = gateway(MutableProfile(), transport, QuotaProvider.Glm)
            val snapshot = gateway.readSnapshot().snapshot()
            assertEquals("https://collector.example:8443/ocmn/quota/v1/glm", transport.requests.single().url)
            assertEquals(QuotaAccountStatus.SourceBound, snapshot.account.status)
            assertNull(snapshot.windows.first().durationSeconds)
            assertNull(snapshot.windows.first().resetsAt)
            assertNull(snapshot.ordinaryUsageAllowed)
            gateway.close()
        }

    @Test
    fun `B-PROTO-084 MiniMax reads its fixed collector route and rejects a different provider`() =
        runTest {
            var wrongProvider = false
            val transport =
                FakeQuotaTransport {
                    jsonResponse(quotaFixture(provider = if (wrongProvider) QuotaProvider.Codex else QuotaProvider.Minimax))
                }
            val gateway = gateway(MutableProfile(), transport, QuotaProvider.Minimax)
            val snapshot = gateway.readSnapshot().snapshot()
            assertEquals(QuotaProvider.Minimax, snapshot.provider)
            assertTrue(transport.requests.single().url.endsWith("/ocmn/quota/v1/minimax"))
            wrongProvider = true
            assertEquals(QuotaFailureKind.InvalidResponse, gateway.readSnapshot().kind())
            gateway.close()
        }

    @Test
    fun `B-PROTO-084 Claude collection is unavailable before any authenticated request`() =
        runTest {
            val transport = FakeQuotaTransport { jsonResponse(quotaFixture(provider = QuotaProvider.Claude)) }
            val gateway = gateway(MutableProfile(), transport, QuotaProvider.Claude)
            assertEquals(QuotaFailureKind.Unsupported, gateway.readSnapshot().kind())
            assertTrue(transport.requests.isEmpty())
            gateway.close()
        }

    @Test
    fun `B-PROTO-084 one fixed same-origin GET, header-only Basic, dedicated options`() =
        runTest {
            val transport = FakeQuotaTransport { jsonResponse() }
            val gateway = gateway(MutableProfile(), transport)
            assertEquals(ProviderQuotaStatus.Ok, gateway.readSnapshot().snapshot().status)
            assertEquals(1, transport.requests.size)
            val request = transport.requests.single()
            assertEquals("https://collector.example:8443/ocmn/quota/v1", request.url)
            assertEquals("GET", request.method)
            assertFalse('?' in request.url)
            assertFalse('#' in request.url)
            assertFalse('@' in request.url)
            assertEquals(basic("fixture-user", PASSWORD), request.headers["Authorization"])
            assertFalse(request.headers.containsKey("Cookie"))
            assertEquals("application/json", request.headers["Accept"])
            assertNull(request.body)
            assertFalse(request.followRedirects)
            assertEquals(0, request.maxRedirects)
            assertEquals(10_000L, request.connectTimeoutMs)
            assertEquals(10_000L, request.sendTimeoutMs)
            assertEquals(10_000L, request.receiveTimeoutMs)
            assertEquals("HttpProviderQuotaGateway", gateway.toString())
            gateway.close()
        }

    @Test
    fun `B-PROTO-084 snapshots profile values rather than following mutable credentials or URL`() =
        runTest {
            val transport = FakeQuotaTransport { jsonResponse() }
            val profile = MutableProfile()
            val gateway = gateway(profile, transport)
            profile.baseUrl = "https://other.example"
            profile.username = "changed-user"
            profile.password = "fixture-changed-password"
            gateway.readSnapshot()
            assertTrue(transport.requests.single().url.startsWith("https://collector.example:8443/"))
            assertEquals(basic("fixture-user", PASSWORD), transport.requests.single().headers["Authorization"])
            gateway.close()
        }

    // ---- B-SEC-138

    @Test
    fun `B-SEC-138 rejects unsafe origins before the transport is called`() =
        runTest {
            val unsafe =
                listOf(
                    "",
                    "collector.example",
                    "http://remote.example",
                    "http://192.0.2.10:4097",
                    "http://localhost.attacker.example",
                    "http://[::ffff:127.0.0.1]",
                    "ftp://collector.example",
                    "https://fixture-user:fixture-password@collector.example",
                    "https://@collector.example",
                    "https://collector.example?auth_token=fixture-only",
                    "https://collector.example?",
                    "https://collector.example#fixture-only",
                    "https://collector.example#",
                    "https://collector.example/api",
                    "https://collector.example/ocmn/quota/v1",
                    "https://collector.example:0",
                    "https://collector.example:65536",
                    "https://${"a".repeat(2048)}.example",
                )
            for ((index, url) in unsafe.withIndex()) {
                val transport = FakeQuotaTransport { jsonResponse() }
                val gateway = gateway(MutableProfile(baseUrl = url), transport)
                assertEquals(QuotaFailureKind.Unavailable, gateway.readSnapshot().kind(), "unsafe origin case $index")
                assertEquals(0, transport.requests.size)
                gateway.close()
            }
        }

    @Test
    fun `B-SEC-138 rejects raw empty userinfo even when a URL parser would remove it`() =
        runTest {
            for (url in listOf("https://@collector.example", "  HTTPS://@collector.example:8443/  ", "http://@localhost:4097", "http://@[::1]:4097/")) {
                val profile = MutableProfile(baseUrl = url)
                assertFalse(HttpProviderQuotaGateway.canReadProfile(profile.baseUrl, profile.username, profile.password, false))
                val transport = FakeQuotaTransport { jsonResponse() }
                val gateway = gateway(profile, transport)
                assertEquals(QuotaFailureKind.Unavailable, gateway.readSnapshot().kind())
                assertEquals(0, transport.requests.size)
                gateway.close()
            }
        }

    @Test
    fun `B-SEC-138 no empty-password, unreadable-keyring, or ambiguous Basic fallback`() =
        runTest {
            val profiles =
                listOf(
                    MutableProfile(password = ""),
                    MutableProfile(baseUrl = "http://127.0.0.1", password = ""),
                    MutableProfile(requiresPasswordReentry = true),
                    MutableProfile(username = "ambiguous:user"),
                    MutableProfile(username = "fixture\nuser"),
                    MutableProfile(password = "fixture\npassword"),
                )
            for (profile in profiles) {
                val transport = FakeQuotaTransport { jsonResponse() }
                val gateway = gateway(profile, transport)
                assertEquals(QuotaFailureKind.Unavailable, gateway.readSnapshot().kind())
                assertEquals(0, transport.requests.size)
                gateway.close()
            }
        }

    @Test
    fun `B-SEC-138 approved loopback forms still send authentication and only the fixed path`() =
        runTest {
            for (url in listOf("http://localhost:4097", "http://127.0.0.1:4097/", "http://[::1]:4097")) {
                val transport = FakeQuotaTransport { jsonResponse() }
                val gateway = gateway(MutableProfile(baseUrl = url), transport)
                gateway.readSnapshot().snapshot()
                val request = transport.requests.single()
                assertTrue(request.url.endsWith(":4097$PROVIDER_QUOTA_PATH"), request.url)
                assertTrue(request.headers.containsKey("Authorization"))
                assertFalse(request.followRedirects)
                gateway.close()
            }
        }

    @Test
    fun `B-SEC-138 empty user is preserved, not silently substituted with another principal`() =
        runTest {
            val transport = FakeQuotaTransport { jsonResponse() }
            val gateway = gateway(MutableProfile(username = ""), transport)
            gateway.readSnapshot().snapshot()
            assertEquals(basic("", PASSWORD), transport.requests.single().headers["Authorization"])
            gateway.close()
        }

    // ---- B-SEC-139

    @Test
    fun `B-SEC-139 HTTP failures are status-only, never follow redirects or probe fallbacks`() =
        runTest {
            val statuses =
                mapOf(
                    204 to QuotaFailureKind.InvalidResponse,
                    301 to QuotaFailureKind.InvalidResponse,
                    302 to QuotaFailureKind.InvalidResponse,
                    307 to QuotaFailureKind.InvalidResponse,
                    308 to QuotaFailureKind.InvalidResponse,
                    400 to QuotaFailureKind.InvalidResponse,
                    401 to QuotaFailureKind.CollectorAuth,
                    403 to QuotaFailureKind.CollectorAuth,
                    404 to QuotaFailureKind.Unsupported,
                    405 to QuotaFailureKind.Unsupported,
                    408 to QuotaFailureKind.Unavailable,
                    429 to QuotaFailureKind.Unavailable,
                    500 to QuotaFailureKind.Unavailable,
                    502 to QuotaFailureKind.Unavailable,
                    503 to QuotaFailureKind.Unavailable,
                )
            for ((status, kind) in statuses) {
                var bodyListened = false
                val transport =
                    FakeQuotaTransport {
                        QuotaResponse(status, flow { bodyListened = true; emit("fixture-private-response".encodeToByteArray()) })
                    }
                val gateway = gateway(MutableProfile(), transport)
                assertEquals(kind, gateway.readSnapshot().kind(), "status $status")
                assertEquals(1, transport.requests.size)
                assertFalse(bodyListened)
                gateway.close()
            }
        }

    @Test
    fun `B-SEC-139 unexpected redirect metadata is also refused`() =
        runTest {
            val transport = FakeQuotaTransport { QuotaResponse(200, bodyOf(encode(quotaFixture())), isRedirect = true) }
            val gateway = gateway(MutableProfile(), transport)
            assertEquals(QuotaFailureKind.InvalidResponse, gateway.readSnapshot().kind())
            assertEquals(1, transport.requests.size)
            gateway.close()
        }

    @Test
    fun `B-SEC-139 JSON, UTF-8, schema and account mismatch errors become safe failures`() =
        runTest {
            val mismatch = quotaFixture()
            mismatch.account()["status"] = "mismatch"
            val bodies =
                listOf(
                    ByteArray(0),
                    "{\"private\":\"fixture-only\",".encodeToByteArray(),
                    "<html>proxy login</html>".encodeToByteArray(),
                    byteArrayOf(0xff.toByte(), 0xfe.toByte()),
                    "null".encodeToByteArray(),
                    encode(quotaFixture().also { it["schemaVersion"] = 2 }).encodeToByteArray(),
                    encode(mismatch).encodeToByteArray(),
                )
            for (body in bodies) {
                val gateway = gateway(MutableProfile(), FakeQuotaTransport { QuotaResponse(200, flow { emit(body) }) })
                assertEquals(QuotaFailureKind.InvalidResponse, gateway.readSnapshot().kind())
                gateway.close()
            }
        }

    @Test
    fun `B-SEC-139 transport exceptions never expose raw errors, URL, or credentials`() =
        runTest {
            val errors: List<Exception> =
                listOf(
                    IllegalStateException("fixture-private-response $PASSWORD https://collector.example"),
                    RuntimeException("connection timeout $PASSWORD"),
                    UnsupportedOperationException("bad certificate $PASSWORD"),
                )
            for (error in errors) {
                val gateway = gateway(MutableProfile(), FakeQuotaTransport { throw error })
                val read = gateway.readSnapshot()
                assertEquals(QuotaFailureKind.Unavailable, read.kind())
                assertEquals("ProviderQuotaFailure(unavailable)", (read as QuotaRead.Failed).failure.toString())
                assertFalse(PASSWORD in read.toString())
                gateway.close()
            }
        }

    @Test
    fun `B-SEC-139 stream errors are sanitized too`() =
        runTest {
            val transport = FakeQuotaTransport { QuotaResponse(200, flow { throw IllegalStateException("fixture-private-response") }) }
            val gateway = gateway(MutableProfile(), transport)
            val read = gateway.readSnapshot()
            assertEquals(QuotaFailureKind.Unavailable, read.kind())
            assertFalse("fixture-private-response" in read.toString())
            gateway.close()
        }

    // ---- B-SEC-140

    @Test
    fun `B-SEC-140 accepts exactly 64 KiB while a single oversized chunk is refused`() =
        runTest {
            val json = encode(quotaFixture())
            for (extra in listOf(0, 1)) {
                val text = json + " ".repeat(HttpProviderQuotaGateway.MAX_RESPONSE_BYTES - json.encodeToByteArray().size + extra)
                val gateway = gateway(MutableProfile(), FakeQuotaTransport { QuotaResponse(200, bodyOf(text)) })
                if (extra == 0) {
                    assertEquals(2, gateway.readSnapshot().snapshot().windows.size)
                } else {
                    assertEquals(QuotaFailureKind.InvalidResponse, gateway.readSnapshot().kind())
                }
                gateway.close()
            }
        }

    @Test
    fun `B-SEC-140 caps streaming before buffering and stops an oversized source early`() =
        runTest {
            var produced = 0
            var stopped = false
            val source =
                flow {
                    try {
                        repeat(1024) {
                            produced++
                            emit(ByteArray(1024))
                        }
                    } finally {
                        stopped = true
                    }
                }
            val gateway = gateway(MutableProfile(), FakeQuotaTransport { QuotaResponse(200, source) })
            assertEquals(QuotaFailureKind.InvalidResponse, gateway.readSnapshot().kind())
            assertEquals(65, produced)
            assertTrue(stopped)
            gateway.close()
        }

    @Test
    fun `B-SEC-140 close is idempotent and a closed gateway never calls the transport`() =
        runTest {
            val transport = FakeQuotaTransport { jsonResponse() }
            val gateway = gateway(MutableProfile(), transport)
            gateway.close()
            gateway.close()
            assertEquals(QuotaFailureKind.Unavailable, gateway.readSnapshot().kind())
            assertEquals(0, transport.requests.size)
            assertEquals(1, transport.closes)
            assertTrue(transport.forceClosed)
        }

    @Test
    fun `B-SEC-140 close settles a read whose headers have not arrived`() =
        runTest {
            val headers = CompletableDeferred<QuotaResponse>()
            val transport = FakeQuotaTransport { headers.await() }
            val gateway = gateway(MutableProfile(), transport)
            val pending = async { gateway.readSnapshot() }
            runCurrent()
            assertTrue(transport.called.isCompleted)
            gateway.close()
            assertEquals(QuotaFailureKind.Unavailable, pending.await().kind())
            assertEquals(1, transport.cancellations)
            assertEquals(1, transport.closes)
            headers.completeExceptionally(IllegalStateException("fixture-late-transport-error"))
        }

    @Test
    fun `B-SEC-140 close cancels a partially received body, not just the request`() =
        runTest {
            var listening = false
            var cancelled = false
            val body =
                flow {
                    listening = true
                    emit("{".encodeToByteArray())
                    try {
                        awaitCancellation()
                    } finally {
                        cancelled = true
                    }
                }
            val transport = FakeQuotaTransport { QuotaResponse(200, body) }
            val gateway = gateway(MutableProfile(), transport)
            val pending = async { gateway.readSnapshot() }
            runCurrent()
            assertTrue(listening)
            gateway.close()
            assertEquals(QuotaFailureKind.Unavailable, pending.await().kind())
            assertTrue(cancelled)
            assertEquals(1, transport.closes)
        }

    @Test
    fun `B-SEC-140 total deadline covers stalled headers without wall-clock waiting`() =
        runTest {
            val headers = CompletableDeferred<QuotaResponse>()
            val transport = FakeQuotaTransport { headers.await() }
            val gateway = gateway(MutableProfile(), transport)
            val pending = async { gateway.readSnapshot() }
            runCurrent()
            advanceTimeBy(9_000)
            runCurrent()
            assertFalse(pending.isCompleted)
            advanceTimeBy(1_001)
            runCurrent()
            assertEquals(QuotaFailureKind.Unavailable, pending.await().kind())
            assertEquals(1, transport.cancellations)
            gateway.close()
            headers.complete(jsonResponse())
        }

    @Test
    fun `B-SEC-140 total body deadline defeats slow-dripping responses`() =
        runTest {
            val body =
                flow {
                    while (true) {
                        emit(byteArrayOf(32))
                        delay(1_000)
                    }
                }
            val gateway = gateway(MutableProfile(), FakeQuotaTransport { QuotaResponse(200, body) })
            val pending = async { gateway.readSnapshot() }
            runCurrent()
            advanceTimeBy(9_000)
            runCurrent()
            assertFalse(pending.isCompleted)
            advanceTimeBy(1_001)
            runCurrent()
            assertEquals(QuotaFailureKind.Unavailable, pending.await().kind())
            gateway.close()
        }
}
