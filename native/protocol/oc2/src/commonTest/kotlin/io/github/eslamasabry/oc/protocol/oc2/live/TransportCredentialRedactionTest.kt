package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.domain.ConnectionCredentials
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.net.NetClient
import io.github.eslamasabry.oc.core.net.TransportError
import io.github.eslamasabry.oc.core.security.Redactor
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

/** Ports the transport cases of test/credential_ingress_redaction_test.dart. */
class TransportCredentialRedactionTest {
    @Test
    fun `B-SEC-056 a new authenticated transport registers its password before any request`() = runTest {
        val password = "transport-password-canary-77"
        val redactor = Redactor()
        var requests = 0
        val gateway = Oc2Gateway(
            NetClient(HttpClient(MockEngine { requests++; respond("{}", HttpStatusCode.OK) }) { expectSuccess = false }, { TransportError.Other }),
            ServerOrigin("http", "localhost", 4097),
            ConnectionCredentials("opencode", password),
            object : Clock { override fun now(): Instant = Instant.fromEpochMilliseconds(1_000) },
            redactor,
            initialDialect = Oc2Dialect.Stable,
        )
        try {
            assertEquals(0, requests)
            assertFalse(password in redactor.redact("Rejected $password"))
        } finally {
            gateway.close()
        }
    }

    @Test
    fun `B-SEC-056 provider credentials in a config reply are registered by field name`() {
        val providerKey = listOf("submitted", "provider", "key", "91").joinToString("-")
        val redactor = Redactor()
        redactor.registerCredentialValues(
            kotlinx.serialization.json.Json.parseToJsonElement("{\"providers\":[{\"id\":\"x\",\"options\":{\"apiKey\":\"" + providerKey + "\"}}]}"),
        )
        assertFalse(providerKey in redactor.redact("key $providerKey"))
    }
}
