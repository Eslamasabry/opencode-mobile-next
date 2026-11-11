package io.github.eslamasabry.oc.engine.runtime.lifecycle

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.runtime.server.LocalServerGate
import io.github.eslamasabry.oc.engine.runtime.server.LocalServerWait
import io.github.eslamasabry.oc.engine.runtime.server.ProbeAnswer
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Device run 2, M1: after a phone restart the home read "This phone · Connecting" for more than six minutes with the
 * server answering, until a force-stop. The app's locks, as they are wired: the connection runtime's command lock
 * (PreviewRuntime.command, held for the whole first connection), the healing's connect lane (NativePhoneHealing.connect),
 * the phase-0 gate in between (it restarts a server that has not answered within its wait), and the reconnect the
 * healing runs when a start answered (BuiltinRuntime.startNow -> onManualReady).
 */
class ReadyReconnectTest {
    private val id = ProfileId("phone")

    private fun TestScope.clock() = object : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(currentTime)
    }

    private class App(clock: Clock, private val id: ProfileId) {
        val order = Mutex()
        val connectLane = Mutex()
        var serverUp = false
        var connected = false
        var commands = 0
        var restarts = 0

        /** What the start does once the server answers it (BuiltinRuntime.onManualReady). */
        var afterStart: suspend () -> Unit = {}

        // After a phone restart the server boots slower than the gate's first wait; the gate's one restart answers.
        val gate =
            LocalServerGate(
                probe = { if (serverUp) ProbeAnswer.Answered else ProbeAnswer.NoAnswer },
                restart = {
                    restarts++
                    serverUp = true
                    afterStart()
                    true
                },
                clock = clock,
            )

        /** PreviewRuntime.command { connectForHome }: the lock is held through the gate. */
        suspend fun connect(): LocalServerWait =
            order.withLock {
                commands++
                gate.awaitReady(id).also { if (it == LocalServerWait.Ready) connected = true }
            }

        /** NativePhoneHealing.connect: its lane, then the runtime's connect when nothing is connected. */
        suspend fun healingConnect() {
            connectLane.withLock { if (!connected) connect() }
        }
    }

    @Test
    fun `M1 the home's first connection ends when the gate's restart answers`() = runTest {
        val app = App(clock(), id)
        val reconnect = ReadyReconnect(backgroundScope) { app.healingConnect() }
        app.afterStart = { reconnect.announce() }
        val attempt = backgroundScope.async { app.connect() }
        advanceTimeBy(5.minutes)
        runCurrent()
        assertTrue(attempt.isCompleted, "the attempt is still waiting: the home would read Connecting for good")
        assertEquals(LocalServerWait.Ready, attempt.await())
        assertTrue(app.connected)
        assertFalse(app.order.isLocked, "the command lock is released")
        assertFalse(app.connectLane.isLocked, "the healing's lane is released")
        // The reconnect queued behind the attempt connects to a server that answers: no second restart, no wait
        // (ConnectionRegistry.open returns the live graph).
        assertEquals(1, app.restarts, "the server is restarted once")
        assertTrue(app.commands <= 2, "one attempt, at most one quick reconnect after it")
    }

    @Test
    fun `M1 the healing's own connect ends when the gate's restart answers`() = runTest {
        val app = App(clock(), id)
        val reconnect = ReadyReconnect(backgroundScope) { app.healingConnect() }
        app.afterStart = { reconnect.announce() }
        val attempt = backgroundScope.async { app.healingConnect() }
        advanceTimeBy(5.minutes)
        runCurrent()
        assertTrue(attempt.isCompleted, "the healing's connect is still waiting on its own lane")
        assertTrue(app.connected)
        assertFalse(app.order.isLocked)
        assertFalse(app.connectLane.isLocked)
    }

    @Test
    fun `M1 the reconnect awaited inside the start never ends, the cause kept as a guard`() = runTest {
        val app = App(clock(), id)
        // The wiring before the fix: onManualReady = { connect(automatic = false) }, awaited inside the start.
        app.afterStart = { app.healingConnect() }
        val attempt = backgroundScope.async { app.connect() }
        advanceTimeBy(10.minutes)
        runCurrent()
        assertTrue(app.serverUp, "the restarted server answers")
        assertFalse(attempt.isCompleted, "awaited inline, the reconnect waits for the lock its own caller holds")
        assertTrue(app.order.isLocked)
        assertFalse(app.connected)
    }
}
