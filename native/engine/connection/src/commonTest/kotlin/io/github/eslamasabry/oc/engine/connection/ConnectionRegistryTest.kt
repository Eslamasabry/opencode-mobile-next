package io.github.eslamasabry.oc.engine.connection

import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.ConnectionCoroutineScope
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.IncompatibleKind
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.ConnectionCredentials
import io.github.eslamasabry.oc.core.domain.GatewayPair
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.ProfileTrust
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.model.ServerProfile
import io.github.eslamasabry.oc.core.model.StreamStatus
import io.github.eslamasabry.oc.core.testing.FakeGatewayFactory
import io.github.eslamasabry.oc.core.testing.TestDispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

// Tests never build a graph (plan 03 §13.1 rule 6): a hand-written factory stands in for the Metro
// extension.
class ConnectionRegistryTest {
    private val profile =
        ServerProfile(
            ProfileId("p1"),
            "Laptop",
            BackendKind.OpenCode2,
            ServerOrigin("http", "10.0.0.2", 4096),
            ProfileTrust(null, null),
            managedOnPhone = false,
            username = null,
        )

    private class HandGraph(
        override val profile: ServerProfile,
        pair: GatewayPair,
        override val scope: ConnectionCoroutineScope,
    ) : ConnectionGraph {
        override val session = ConnectionSession(profile, pair.live)
    }

    @Test
    fun `open is idempotent and close cancels the connection scope`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val fake = FakeGatewayFactory()
        val registry =
            ConnectionRegistry(
                setOf(fake),
                { p, pair, scope -> HandGraph(p, pair, scope) },
                AppCoroutineScope(backgroundScope.coroutineContext),
                TestDispatchers(dispatcher),
            )
        val first = assertIs<Outcome.Ok<ConnectionGraph>>(registry.open(profile)).value
        val second = assertIs<Outcome.Ok<ConnectionGraph>>(registry.open(profile)).value
        assertSame(first, second)
        assertEquals(1, fake.opened.size)
        assertSame(first.scope, fake.opened.single().scope)
        assertEquals(StreamStatus.Connected, first.session.status.value)

        registry.close(profile.id)
        assertFalse(first.scope.isActive)
        assertTrue(fake.gateway.closed)
        assertTrue(registry.live.value.isEmpty())
    }

    @Test
    fun `a backend with no factory is Unavailable, not a crash`() = runTest {
        val registry =
            ConnectionRegistry(
                emptySet(),
                { p, pair, scope -> HandGraph(p, pair, scope) },
                AppCoroutineScope(backgroundScope.coroutineContext),
                TestDispatchers(StandardTestDispatcher(testScheduler)),
            )
        assertIs<Outcome.Failed>(registry.open(profile))
    }

    @Test
    fun `a failed connect cancels its scope and leaves nothing live`() = runTest {
        val fake =
            FakeGatewayFactory(
                failNextConnect = Failure.Incompatible(IncompatibleKind.ProtocolMismatch)
            )
        val registry =
            ConnectionRegistry(
                setOf(fake),
                { p, pair, scope -> HandGraph(p, pair, scope) },
                AppCoroutineScope(backgroundScope.coroutineContext),
                TestDispatchers(StandardTestDispatcher(testScheduler)),
            )
        val failed = assertIs<Outcome.Failed>(registry.open(profile))
        assertIs<Failure.Incompatible>(failed.failure)
        assertTrue(registry.live.value.isEmpty())
        assertTrue(fake.opened.isEmpty())
        // The next try connects normally.
        assertIs<Outcome.Ok<ConnectionGraph>>(registry.open(profile))
    }

    @Test
    fun `B-SEC-135 unreadable credentials refuse before transport without anonymous fallback`() =
        runTest {
            val fake = FakeGatewayFactory()
            var reads = 0
            val registry =
                ConnectionRegistry(
                    setOf(fake),
                    { p, pair, scope -> HandGraph(p, pair, scope) },
                    AppCoroutineScope(backgroundScope.coroutineContext),
                    TestDispatchers(StandardTestDispatcher(testScheduler)),
                    setOf(
                        ConnectionCredentialSource {
                            reads++
                            Outcome.Failed(Failure.SecretUnavailable())
                        }
                    ),
                )
            assertIs<Failure.SecretUnavailable>(
                assertIs<Outcome.Failed>(registry.open(profile)).failure
            )
            assertEquals(1, reads)
            assertTrue(fake.opened.isEmpty())
            assertTrue(registry.live.value.isEmpty())
        }

    @Test
    fun `B-SEC-135 resolved Basic credentials reach only the target gateway specification`() =
        runTest {
            val fake = FakeGatewayFactory()
            val credentials = ConnectionCredentials("opencode", "test-password")
            val registry =
                ConnectionRegistry(
                    setOf(fake),
                    { p, pair, scope -> HandGraph(p, pair, scope) },
                    AppCoroutineScope(backgroundScope.coroutineContext),
                    TestDispatchers(StandardTestDispatcher(testScheduler)),
                    setOf(
                        ConnectionCredentialSource { target ->
                            assertEquals(profile, target)
                            Outcome.Ok(credentials)
                        }
                    ),
                )
            assertIs<Outcome.Ok<ConnectionGraph>>(registry.open(profile))
            assertSame(credentials, fake.opened.single().credentials)
            registry.close(profile.id)
        }
}
