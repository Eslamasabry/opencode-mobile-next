package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.common.Failure
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
}
