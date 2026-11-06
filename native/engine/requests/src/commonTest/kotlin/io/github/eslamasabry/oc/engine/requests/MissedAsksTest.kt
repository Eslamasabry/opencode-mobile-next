package io.github.eslamasabry.oc.engine.requests

import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.testing.TestClock
import io.github.eslamasabry.oc.core.testing.TestDispatchers
import io.github.eslamasabry.oc.core.testing.TestPacer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * N-40 (WALK-ISSUES-0bf0df4ff): an ask the server waits on must reach its chat's card even when the live event was
 * missed, and a chat in a folder other than the pinned one must not lose its card to a reread of the pinned folder.
 * [FakeRequestsPort.elsewhere] holds asks that only their own session's read returns.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MissedAsksTest {
    private val port =
        FakeRequestsPort().apply {
            capabilities.value = ServerCapabilities.Unknown.copy(permissionRequests = true)
        }
    private val clock = TestClock()

    private fun TestScope.engine(): RequestsEngine {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return RequestsEngine(port, backgroundScope, TestDispatchers(dispatcher), clock, TestPacer()).also { runCurrent() }
    }

    private fun RequestsEngine.ids(): List<String> = pending.value.map { it.id.raw }

    @Test
    fun `B-CHAT-075 an ask emitted before the chat subscribed is shown when the chat opens`() =
        runTest {
            port.elsewhere = listOf(permission("p1"))
            val engine = engine()
            assertEquals(emptyList(), engine.ids(), "the pinned folder's list does not have it")

            engine.watchSession(S1, open = true)
            runCurrent()
            assertEquals(listOf("p1"), engine.ids())
            assertEquals("p1", engine.forSession(S1).first().current?.id?.raw)
        }

    @Test
    fun `B-CHAT-075 a live ask from another folder survives a reread of the pinned folder`() =
        runTest {
            port.events.currentDirectory = "/pinned"
            val engine = engine()
            engine.watchSession(S1, open = true)
            runCurrent()
            port.elsewhere = listOf(permission("p1"))
            // The project-filtered stream drops it; the open chat still takes it.
            port.events.emit(GatewayEvent.PermissionAsked(permission("p1")), directory = "/elsewhere")
            runCurrent()
            assertEquals(listOf("p1"), engine.ids())

            engine.refresh()
            advanceTimeBy(RequestsTimers.REREAD.cadence.foreground!! * 2)
            runCurrent()
            assertEquals(listOf("p1"), engine.ids(), "only the session's own list may retire it")
        }

    @Test
    fun `B-PROTO-034 an ask lost while the stream reconnected is shown after the reconnect reread`() =
        runTest {
            val engine = engine()
            engine.watchSession(S1, open = true)
            runCurrent()
            assertEquals(emptyList(), engine.ids())

            // The ask is emitted while the stream is down: no event ever arrives.
            port.elsewhere = listOf(permission("p2"))
            port.events.connect()
            runCurrent()
            assertEquals(listOf("p2"), engine.ids())
        }

    @Test
    fun `B-CHAT-075 an answered ask leaves when its session no longer lists it`() =
        runTest {
            val engine = engine()
            port.elsewhere = listOf(permission("p1"), permission("p2"))
            engine.watchSession(S1, open = true)
            runCurrent()
            assertEquals(listOf("p1", "p2"), engine.ids())

            // Answered elsewhere, with the settle event lost.
            port.elsewhere = listOf(permission("p2"))
            engine.refreshSession(S1)
            assertEquals(listOf("p2"), engine.ids())

            // Answered with the settle event seen on the open chat, from another folder.
            port.events.currentDirectory = "/pinned"
            port.elsewhere = emptyList()
            port.events.emit(GatewayEvent.PermissionSettled(S1, RequestId("p2")), directory = "/elsewhere")
            runCurrent()
            assertEquals(emptyList(), engine.ids())
        }

    @Test
    fun `B-CHAT-123 an ask seen live and by every reread is one card`() =
        runTest {
            val engine = engine()
            port.permissions = listOf(permission("p1"))
            port.elsewhere = listOf(permission("p1"))
            engine.watchSession(S1, open = true)
            port.events.emit(GatewayEvent.PermissionAsked(permission("p1")))
            runCurrent()
            port.events.connect()
            runCurrent()
            engine.refresh()
            engine.refreshSession(S1)

            assertEquals(listOf("p1"), engine.ids())
            assertEquals(0, engine.forSession(S1).first().moreWaiting)
        }

    @Test
    fun `B-CHAT-075 a running turn that stays quiet re-reads its own asks`() =
        runTest {
            val engine = engine()
            engine.watchSession(S1, open = true)
            runCurrent()
            engine.runActivity(S1, running = true)
            port.elsewhere = listOf(permission("p3")) // emitted, but the event never arrived
            val before = port.calls.count { it == "sessionPermissions ${S1.raw}" }

            // Not yet quiet: the watchdog leaves the server alone.
            clock.advance(5.seconds)
            advanceTimeBy(RequestsTimers.QUIET_RUN.cadence.foreground!! + 1.seconds)
            runCurrent()
            assertEquals(before, port.calls.count { it == "sessionPermissions ${S1.raw}" })
            assertTrue(engine.ids().isEmpty())

            clock.advance(QUIET_RUN_AFTER)
            advanceTimeBy(RequestsTimers.QUIET_RUN.cadence.foreground!! + 1.seconds)
            runCurrent()
            assertEquals(listOf("p3"), engine.ids())
        }

    @Test
    fun `B-CHAT-075 a closed chat stops its session reads`() =
        runTest {
            val engine = engine()
            engine.watchSession(S1, open = true)
            runCurrent()
            engine.watchSession(S1, open = false)
            runCurrent()
            port.calls.clear()

            engine.refresh()
            assertTrue(port.calls.none { it.startsWith("sessionPermissions") })
        }
}
