package io.github.eslamasabry.oc.engine.connection

import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.ConnectionCoroutineScope
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.GatewayPair
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.ProfileTrust
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.model.ServerProfile
import io.github.eslamasabry.oc.core.model.StreamStatus
import io.github.eslamasabry.oc.core.testing.FakeGatewayFactory
import io.github.eslamasabry.oc.core.testing.TestDispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

// Tests never build a graph (plan 03 §13.1 rule 6): a hand-written factory stands in for the Metro extension.
class ConnectionRegistryTest {
    private val profile =
        ServerProfile(
            ProfileId("p1"), "Laptop", BackendKind.OpenCode2, ServerOrigin("http", "10.0.0.2", 4096),
            ProfileTrust(null, null), managedOnPhone = false, username = null,
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
        assertEquals(1, fake.connected.size)
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
}
