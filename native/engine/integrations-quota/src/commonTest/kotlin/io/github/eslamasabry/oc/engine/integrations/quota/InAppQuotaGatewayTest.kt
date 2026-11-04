package io.github.eslamasabry.oc.engine.integrations.quota

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ports test/in_app_quota_gateway_test.dart (row B-PROTO-058). */
class InAppQuotaGatewayTest {
    private val inApp = MutableProfile(baseUrl = "http://127.0.0.1:4097", username = "opencode", password = "basic-fixture-password")
    private val remote = MutableProfile(baseUrl = "https://collector.example:8443", password = "basic-fixture-password")

    @Test
    fun `B-PROTO-058 In-app Ubuntu reads loopback 4195 with the Bearer token only`() =
        runTest {
            val transport = FakeQuotaTransport { jsonResponse() }
            val gateway = gateway(inApp, transport, inAppToken = { "fixture-read-token-fixture-read-token-fixture-1234" })
            gateway.readSnapshot().snapshot()
            val request = transport.requests.single()
            assertEquals("http://127.0.0.1:4195/ocmn/quota/v1", request.url)
            assertEquals("Bearer fixture-read-token-fixture-read-token-fixture-1234", request.headers["Authorization"])
            assertFalse(request.headers.values.any { "Basic" in it })
            gateway.close()
        }

    @Test
    fun `B-PROTO-058 a remote server keeps its origin and Basic sign-in`() =
        runTest {
            val transport = FakeQuotaTransport { jsonResponse() }
            val gateway = gateway(remote, transport)
            gateway.readSnapshot().snapshot()
            val request = transport.requests.single()
            assertTrue(request.url.startsWith("https://collector.example:8443/"))
            assertTrue(request.headers["Authorization"]!!.startsWith("Basic "))
            gateway.close()
        }

    @Test
    fun `B-PROTO-058 In-app Ubuntu without a token says unsupported and sends nothing`() =
        runTest {
            val transport = FakeQuotaTransport { jsonResponse() }
            val gateway = gateway(inApp, transport, inAppToken = { null })
            assertEquals(QuotaFailureKind.Unsupported, gateway.readSnapshot().kind())
            assertTrue(transport.requests.isEmpty())
            gateway.close()
        }

    @Test
    fun `B-PROTO-058 only the app's own server gets a token reader`() {
        val secrets = FakeSecrets()
        val remoteProfile = QuotaServerProfile(ProfileId("remote"), remote.baseUrl, isOpenCode = true)
        val inAppProfile = QuotaServerProfile(ProfileId("builtin"), inApp.baseUrl, isOpenCode = true)
        assertNull(InAppQuotaCollector.tokenReaderFor(remoteProfile, secrets))
        assertNotNull(InAppQuotaCollector.tokenReaderFor(inAppProfile, secrets))
        // A Paseo or Codex profile at the same address is not the app's OpenCode.
        assertNull(InAppQuotaCollector.tokenReaderFor(inAppProfile.copy(isOpenCode = false), secrets))
        // Termux's 4096 is not the in-app server either.
        assertNull(InAppQuotaCollector.tokenReaderFor(inAppProfile.copy(baseUrl = "http://127.0.0.1:4096"), secrets))
    }
}
