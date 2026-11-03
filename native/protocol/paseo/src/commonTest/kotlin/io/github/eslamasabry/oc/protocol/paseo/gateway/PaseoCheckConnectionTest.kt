package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.model.StreamStatus
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.core.testing.TestDispatchers
import io.github.eslamasabry.oc.protocol.paseo.DIR
import io.github.eslamasabry.oc.protocol.paseo.FakeDaemon
import io.github.eslamasabry.oc.protocol.paseo.FakeSockets
import io.github.eslamasabry.oc.protocol.paseo.PaseoException
import io.github.eslamasabry.oc.protocol.paseo.PaseoFailureKind
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The app's pairing check (Settings › Agents › Add a computer) runs through the factory the connection uses. */
class PaseoCheckConnectionTest {
    private fun factory(sockets: FakeSockets, scope: kotlinx.coroutines.test.TestScope) =
        PaseoGatewayFactory(sockets, TestDispatchers(UnconfinedTestDispatcher(scope.testScheduler)), TestClock())

    @Test
    fun `B-PROTO-070 the app check refuses a public address before any socket opens`() = runTest {
        val sockets = FakeSockets(FakeDaemon().seedDefaults())
        val result = factory(sockets, this).checkConnection("ws://198.51.100.4:6767", "", DIR)
        assertIs<PaseoProbeResult.InvalidInput>(result)
        assertTrue(sockets.opened.isEmpty())
    }

    @Test
    fun `B-PROTO-070 the app check names the ready runtimes of a good daemon`() = runTest {
        val sockets = FakeSockets(FakeDaemon().seedDefaults())
        val result = factory(sockets, this).checkConnection("127.0.0.1:6767", "", DIR)
        val verified = assertIs<PaseoProbeResult.Verified>(result)
        assertEquals(listOf("Claude Code", "Pi"), verified.runtimes)
        assertEquals("ws://127.0.0.1:6767/ws", sockets.opened.first().first.url)
    }

    @Test
    fun `B-AGENT-160 the app check reads a wrong password as sign-in, not as a crash`() = runTest {
        val sockets = FakeSockets(FakeDaemon())
        sockets.refuse = PaseoException(PaseoFailureKind.Authentication)
        val result = factory(sockets, this).checkConnection("127.0.0.1:6767", "secret-pw", DIR)
        assertIs<Failure.SignIn>(assertIs<PaseoProbeResult.Refused>(result).failure)
    }

    @Test
    fun `B-AGENT-163 a connected computer listens, so its stream says Connected and sends can go`() = runTest {
        val daemon = FakeDaemon().seedDefaults()
        val scope = io.github.eslamasabry.oc.core.common.ConnectionCoroutineScope(
            kotlinx.coroutines.SupervisorJob() + UnconfinedTestDispatcher(testScheduler),
        )
        try {
            val spec = io.github.eslamasabry.oc.core.domain.GatewaySpec(
                profile = io.github.eslamasabry.oc.core.model.ServerProfile(
                    io.github.eslamasabry.oc.core.common.ids.ProfileId("p1"),
                    "Laptop",
                    io.github.eslamasabry.oc.core.model.BackendKind.Paseo,
                    io.github.eslamasabry.oc.core.model.ServerOrigin("ws", "127.0.0.1", 6767),
                    io.github.eslamasabry.oc.core.model.ProfileTrust(null, null),
                    managedOnPhone = false,
                    username = null,
                ),
                credentials = io.github.eslamasabry.oc.core.domain.ConnectionCredentials.None,
                scope = scope,
                location = io.github.eslamasabry.oc.core.model.ServerLocation(directory = DIR),
            )
            val pair = assertIs<io.github.eslamasabry.oc.core.common.Outcome.Ok<io.github.eslamasabry.oc.core.domain.GatewayPair>>(
                factory(FakeSockets(daemon), this).connect(spec),
            ).value
            testScheduler.runCurrent()
            assertEquals(StreamStatus.Connected, pair.live.events.status.value)
            assertTrue(daemon.of("fetch_agents_request").isNotEmpty())
        } finally {
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        }
    }

    @Test
    fun `B-AGENT-181 a connected computer shows the title saved for an untitled conversation`() = runTest {
        val daemon = FakeDaemon().seedDefaults().apply {
            handlers["fetch_agents_request"] = {
                "fetch_agents_response" to io.github.eslamasabry.oc.protocol.paseo.jsonOf(
                    "entries" to listOf(mapOf("agent" to agentJson("u1", title = null))),
                    "pageInfo" to mapOf("hasMore" to false),
                )
            }
        }
        val asked = mutableListOf<Pair<String, String>>()
        val factory = PaseoGatewayFactory(
            FakeSockets(daemon),
            TestDispatchers(UnconfinedTestDispatcher(testScheduler)),
            TestClock(),
            savedTitles = { profile, key ->
                asked += profile.raw to key
                if (profile.raw == "p1" && key == "claude:native-u1") "Fix the login page" else null
            },
        )
        val scope = io.github.eslamasabry.oc.core.common.ConnectionCoroutineScope(
            kotlinx.coroutines.SupervisorJob() + UnconfinedTestDispatcher(testScheduler),
        )
        try {
            val spec = io.github.eslamasabry.oc.core.domain.GatewaySpec(
                profile = io.github.eslamasabry.oc.core.model.ServerProfile(
                    io.github.eslamasabry.oc.core.common.ids.ProfileId("p1"),
                    "Laptop",
                    io.github.eslamasabry.oc.core.model.BackendKind.Paseo,
                    io.github.eslamasabry.oc.core.model.ServerOrigin("ws", "127.0.0.1", 6767),
                    io.github.eslamasabry.oc.core.model.ProfileTrust(null, null),
                    managedOnPhone = false,
                    username = null,
                ),
                credentials = io.github.eslamasabry.oc.core.domain.ConnectionCredentials.None,
                scope = scope,
                location = io.github.eslamasabry.oc.core.model.ServerLocation(directory = DIR),
            )
            val pair = assertIs<io.github.eslamasabry.oc.core.common.Outcome.Ok<io.github.eslamasabry.oc.core.domain.GatewayPair>>(
                factory.connect(spec),
            ).value
            val page = assertIs<io.github.eslamasabry.oc.core.common.Outcome.Ok<io.github.eslamasabry.oc.core.model.ServerPage<io.github.eslamasabry.oc.core.model.Session>>>(
                pair.live.sessionPage(null, 20),
            ).value
            assertEquals("Fix the login page", page.items.single().title)
            assertTrue(("p1" to "claude:native-u1") in asked)
        } finally {
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        }
    }
}
