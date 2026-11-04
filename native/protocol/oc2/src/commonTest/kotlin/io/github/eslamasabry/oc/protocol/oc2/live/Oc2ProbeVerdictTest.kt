package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.BusyKind
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.IncompatibleKind
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.SignInWhy
import io.github.eslamasabry.oc.core.domain.ConnectionCredentials
import io.github.eslamasabry.oc.core.model.Health
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.net.NetClient
import io.github.eslamasabry.oc.core.net.TransportError
import io.github.eslamasabry.oc.core.security.Redactor
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/** Port of `test/server_probe_test.dart` probe verdicts (row B-PROTO-094), through the add-server probe. */
class Oc2ProbeVerdictTest {
    private val requests = mutableListOf<HttpRequestData>()

    private suspend fun probeWith(password: String? = null, answer: (path: String) -> Pair<String, HttpStatusCode>): Outcome<Health> {
        val gateway = Oc2Gateway(
            NetClient(
                HttpClient(
                    MockEngine { request ->
                        requests += request
                        val (body, status) = answer(request.url.encodedPath)
                        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
                    },
                ) {
                    expectSuccess = false
                    followRedirects = false
                },
                { TransportError.Other },
            ),
            ServerOrigin("https", "server.test", 4097),
            password?.let { ConnectionCredentials("opencode", it) } ?: ConnectionCredentials.None,
            object : Clock {
                override fun now(): Instant = Instant.fromEpochMilliseconds(1_000)
            },
            Redactor(),
        )
        return try {
            gateway.probe()
        } finally {
            gateway.close()
        }
    }

    private val notFound = "" to HttpStatusCode.NotFound

    private fun failure(result: Outcome<Health>): Failure = assertIs<Outcome.Failed>(result).failure

    @Test
    fun `B-PROTO-094 a v2 401 without a password needs the password, with one it is the wrong password`() = runTest {
        val missing = failure(probeWith { if (it == "/api/health") "" to HttpStatusCode.Unauthorized else notFound })
        assertEquals(SignInWhy.PasswordMissing, assertIs<Failure.SignIn>(missing).why)

        requests.clear()
        val wrong = failure(probeWith(password = "not-the-password") { if (it == "/api/health") "" to HttpStatusCode.Unauthorized else notFound })
        assertEquals(SignInWhy.PasswordRejected, assertIs<Failure.SignIn>(wrong).why)
        assertEquals(
            "Basic " + Base64.encode("opencode:not-the-password".encodeToByteArray()),
            requests.first().headers[HttpHeaders.Authorization],
            "the probe authenticates exactly like the transport",
        )
    }

    @Test
    fun `B-PROTO-094 a v2 200 health answer connects with its version`() = runTest {
        val ok = probeWith(password = "the-password") {
            if (it == "/api/health") """{"healthy":true,"version":"0.0.0-beta-18600","pid":42}""" to HttpStatusCode.OK else notFound
        }
        assertEquals(Outcome.Ok(Health(true, "0.0.0-beta-18600")), ok)
    }

    @Test
    fun `B-PROTO-094 a v2 503 while booting says the server is starting`() = runTest {
        val starting = failure(probeWith { if (it == "/api/health") """{"code":"service_starting"}""" to HttpStatusCode.ServiceUnavailable else notFound })
        assertEquals(BusyKind.Starting, assertIs<Failure.Busy>(starting).kind)
    }

    @Test
    fun `B-PROTO-094 an unhealthy v2 answer fails without a v1 fallback`() = runTest {
        val unhealthy = failure(probeWith { if (it == "/api/health") """{"healthy":false,"version":"0.0.0-beta-18600"}""" to HttpStatusCode.OK else notFound })
        assertEquals(BusyKind.Failed, assertIs<Failure.Busy>(unhealthy).kind)
        assertTrue(requests.none { it.url.encodedPath == "/global/health" }, "no v1 fallback")
    }

    @Test
    fun `B-PROTO-094 OpenCode 2_0_4 and later answer at api-info`() = runTest {
        val ok = probeWith { if (it == "/api/info") """{"version":"2.0.10","pid":4242,"urls":["http://127.0.0.1:4096"]}""" to HttpStatusCode.OK else notFound }
        assertEquals(Outcome.Ok(Health(true, "2.0.10")), ok)
    }

    @Test
    fun `B-PROTO-094 a real v1 server is never read as v2`() = runTest {
        val legacy = failure(probeWith { if (it == "/global/health") """{"healthy":true,"version":"0.3.5"}""" to HttpStatusCode.OK else notFound })
        assertEquals(IncompatibleKind.LegacyOpenCode1, assertIs<Failure.Incompatible>(legacy).kind)
    }

    @Test
    fun `B-PROTO-094 an address answering neither health route is not an OpenCode server`() = runTest {
        assertIs<Failure.NotFound>(failure(probeWith { notFound }))
    }

    @Test
    fun `B-PROTO-094 a 200 that is not the v2 health JSON still reaches the v1 check`() = runTest {
        val legacy = failure(probeWith {
            when (it) {
                "/api/health" -> """{"status":"ok"}""" to HttpStatusCode.OK
                "/global/health" -> """{"healthy":true,"version":"0.3.9"}""" to HttpStatusCode.OK
                else -> notFound
            }
        })
        assertEquals(IncompatibleKind.LegacyOpenCode1, assertIs<Failure.Incompatible>(legacy).kind)
    }
}
