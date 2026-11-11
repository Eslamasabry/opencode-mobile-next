package io.github.eslamasabry.oc.engine.runtime.server

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.coroutines.CoroutineScope
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
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Wave 3 conn 1: an attempt to connect to This phone held the connection runtime's command lock for its whole wait for
 * the phone's server (up to about 150 s), so a switch to another server, an explicit Try again or a Stop waited behind
 * it and the home could only say "Connecting". The app as wired: the command lock (PreviewRuntime.command), its busy
 * flag (the lock held or an attempt waiting), the real phase-0 gate, and the attempt the way connectCommand runs it.
 */
class LocalServerWaitsTest {
    private val phone = ProfileId("phone")
    private val computer = ProfileId("computer")

    private class App(scope: CoroutineScope, test: TestScope, val phone: ProfileId) {
        val order = Mutex()
        var holding = false
        var answerAt = 20_000L
        var restartWorks = false
        var restarts = 0
        val waitingShown = mutableListOf<Boolean>()
        var active: ProfileId? = null
        var phoneShownInList = true

        private val gate =
            LocalServerGate(
                probe = { if (test.currentTime >= answerAt) ProbeAnswer.Answered else ProbeAnswer.NoAnswer },
                restart = {
                    restarts++
                    if (restartWorks) answerAt = test.currentTime
                    restartWorks
                },
                clock = object : Clock {
                    override fun now(): Instant = Instant.fromEpochMilliseconds(test.currentTime)
                },
                onWaiting = { _, waiting -> waitingShown += waiting },
            )

        val waits = LocalServerWaits(scope, gate = { id -> if (id == phone) gate.awaitReady(id) else LocalServerWait.Ready })

        val busy: Boolean
            get() = holding || waits.waiting > 0

        /** PreviewRuntime.connectCommand { connectForHome }. */
        suspend fun connect(id: ProfileId, makeActive: Boolean = true): String =
            waits.run(
                id,
                makeActive,
                waits = id == phone,
                locked = { block ->
                    order.withLock {
                        holding = true
                        try {
                            block()
                        } finally {
                            holding = false
                        }
                    }
                },
                stale = { "superseded" },
            ) { turn ->
                when {
                    turn.overtaken && !(id == phone && phoneShownInList) -> "superseded"
                    turn.local == LocalServerWait.NotAnswering -> "isn't answering"
                    else -> {
                        if (turn.activate) active = id
                        if (turn.activate) "connected ${id.raw}" else "connected ${id.raw} in the list"
                    }
                }
            }
    }

    @Test
    fun `W3-CONN-001 a switch to another server issued while This phone boots completes without waiting for it`() =
        runTest {
            val app = App(backgroundScope, this, phone)
            val first = async { app.connect(phone) }
            advanceTimeBy(1_000)
            runCurrent()
            assertTrue(app.busy, "the home reads Starting/Connecting while the phone's server boots")
            assertFalse(app.order.isLocked, "the wait holds no lock")
            val switched = async { app.connect(computer) }
            runCurrent()
            assertTrue(switched.isCompleted, "the switch waited behind the phone's wait")
            assertEquals("connected computer", switched.await())
            assertEquals(1_000, currentTime)
            assertEquals(computer, app.active)
            // The first attempt still ends when the server answers; it joins the list and never takes the home back.
            advanceTimeBy(30.seconds)
            runCurrent()
            assertEquals("connected phone in the list", first.await())
            assertEquals(computer, app.active)
            assertFalse(app.busy)
        }

    @Test
    fun `W3-CONN-002 an overtaken attempt for a server not shown in the list ends without connecting`() = runTest {
        val app = App(backgroundScope, this, phone)
        app.phoneShownInList = false
        val first = async { app.connect(phone) }
        runCurrent()
        assertEquals("connected computer", app.connect(computer))
        advanceTimeBy(30.seconds)
        runCurrent()
        assertEquals("superseded", first.await())
        assertEquals(computer, app.active)
        assertFalse(app.busy)
    }

    @Test
    fun `W3-CONN-003 an explicit Try again supersedes the waiting attempt and shares its wait`() = runTest {
        val app = App(backgroundScope, this, phone)
        val first = async { app.connect(phone) }
        advanceTimeBy(5_000)
        runCurrent()
        val again = async { app.connect(phone) }
        runCurrent()
        assertTrue(first.isCompleted, "the superseded attempt ends at once")
        assertEquals("superseded", first.await())
        assertEquals(5_000, currentTime)
        assertTrue(app.busy, "the Try again still waits for the server")
        advanceTimeBy(30.seconds)
        runCurrent()
        assertEquals("connected phone", again.await())
        assertEquals(phone, app.active)
        assertEquals(listOf(true, false), app.waitingShown, "one wait for the server, not one per attempt")
        assertEquals(0, app.restarts)
        assertFalse(app.busy)
        assertFalse(app.order.isLocked)
    }

    @Test
    fun `W3-CONN-004 the first attempt, not superseded, ends connected`() = runTest {
        val app = App(backgroundScope, this, phone)
        app.answerAt = 45_000 // slower than the gate's first wait: its one restart brings it back
        app.restartWorks = true
        val first = async { app.connect(phone) }
        advanceTimeBy(5.minutes)
        runCurrent()
        assertEquals("connected phone", first.await())
        assertEquals(1, app.restarts)
        assertFalse(app.busy)
    }

    @Test
    fun `W3-CONN-005 the first attempt, not superseded, ends failed when the server never answers`() = runTest {
        val app = App(backgroundScope, this, phone)
        app.answerAt = Long.MAX_VALUE
        val first = async { app.connect(phone) }
        advanceTimeBy(5.minutes)
        runCurrent()
        assertEquals("isn't answering", first.await())
        assertEquals(1, app.restarts)
        assertFalse(app.busy)
        assertFalse(app.order.isLocked)
    }

    @Test
    fun `W3-CONN-006 Disconnect or Stop ends the waiting attempt and its wait, and nothing stays busy`() = runTest {
        val app = App(backgroundScope, this, phone)
        app.answerAt = Long.MAX_VALUE
        val first = async { app.connect(phone) }
        advanceTimeBy(5_000)
        runCurrent()
        app.waits.cancel(listOf(phone))
        runCurrent()
        assertTrue(first.isCompleted)
        assertEquals("superseded", first.await())
        assertFalse(app.busy, "busy is left behind by the cancelled attempt")
        assertEquals(listOf(true, false), app.waitingShown, "the phone's Starting state ends with the wait")
        advanceTimeBy(5.minutes)
        runCurrent()
        assertEquals(0, app.restarts, "a cancelled wait never restarts the server")
        // A later attempt waits afresh.
        app.answerAt = currentTime + 2_000
        val later = async { app.connect(phone) }
        advanceTimeBy(10.seconds)
        runCurrent()
        assertEquals("connected phone", later.await())
    }

    @Test
    fun `W3-CONN-007 a background attempt never supersedes the person's choice of the same server`() = runTest {
        val app = App(backgroundScope, this, phone)
        val chosen = async { app.connect(phone) }
        advanceTimeBy(1_000)
        runCurrent()
        // The Agents page connects This phone in the background (connectInBackground).
        val background = async { app.connect(phone, makeActive = false) }
        advanceTimeBy(30.seconds)
        runCurrent()
        assertEquals("connected phone", chosen.await())
        assertEquals("connected phone in the list", background.await())
        assertEquals(phone, app.active)
        assertEquals(listOf(true, false), app.waitingShown)
    }
}
