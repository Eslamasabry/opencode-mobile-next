package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.domain.ConnectionCredentials
import io.github.eslamasabry.oc.core.model.ProfileTrust
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.net.EngineFactory
import io.github.eslamasabry.oc.core.net.HttpClientFactory
import io.github.eslamasabry.oc.core.net.TransportError
import io.github.eslamasabry.oc.core.net.TransportSpec
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.core.testing.TestDispatchers
import io.ktor.client.engine.HttpClientEngine
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

/** B-177 (audit 2026-11-08): a consent record never lets the OC2 factory dial plain HTTP on the public internet. */
class Oc2PublicCleartextTest {
    private class NoEngines : EngineFactory {
        val built = mutableListOf<TransportSpec>()

        override fun create(spec: TransportSpec): HttpClientEngine {
            built += spec
            throw AssertionError("no client may be built for a refused origin")
        }

        override fun classify(error: Throwable): TransportError = throw AssertionError("not reached")
    }

    @Test
    fun `B-177 a confirmed public http origin is refused before any client is built`() = runTest {
        val engines = NoEngines()
        val factory =
            Oc2GatewayFactory(
                HttpClientFactory(engines),
                TestDispatchers(StandardTestDispatcher(testScheduler)),
                TestClock(),
                Redactor(),
                Random(0),
            )
        val failure =
            assertIs<Outcome.Failed>(
                    factory.checkConnection(
                        ServerOrigin("http", "203.0.113.9", 4096),
                        ConnectionCredentials("opencode", "pw"),
                        ProfileTrust(pinnedLeafSha256 = null, cleartextConsentAtMs = 0L),
                    )
                )
                .failure
        assertEquals(Failure.Refused(RefusalReason.CleartextNotConfirmed), failure)
        assertTrue(engines.built.isEmpty())
    }
}
