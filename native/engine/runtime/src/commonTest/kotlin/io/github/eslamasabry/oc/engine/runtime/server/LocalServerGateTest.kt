package io.github.eslamasabry.oc.engine.runtime.server

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class LocalServerGateTest {
    private val id = ProfileId("phone")

    private fun TestScope.clock() = object : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(currentTime)
    }

    @Test
    fun `P0-SERVER-001 the server is not up so no connect is allowed until it answers`() = runTest {
        var answerAt = 7_000L
        var probes = 0
        val gate = LocalServerGate(
            probe = { probes++; if (currentTime >= answerAt) ProbeAnswer.Answered else ProbeAnswer.NoAnswer },
            restart = { error("must not restart a server that answers in time") },
            clock = clock(),
        )
        val waiting = mutableListOf<Boolean>()
        val shown = LocalServerGate(
            probe = { if (currentTime >= answerAt) ProbeAnswer.Answered else ProbeAnswer.NoAnswer },
            restart = { false },
            clock = clock(),
            onWaiting = { _, w -> waiting += w },
        )
        assertEquals(LocalServerWait.Ready, gate.awaitReady(id))
        assertTrue(currentTime >= answerAt, "returned Ready before the server answered")
        assertTrue(probes > 2, "polled with backoff")
        answerAt = currentTime + 3_000
        assertEquals(LocalServerWait.Ready, shown.awaitReady(id))
        assertEquals(listOf(true, false), waiting)
    }

    @Test
    fun `P0-SERVER-002 an answering server needs no wait and no restart`() = runTest {
        val gate = LocalServerGate({ ProbeAnswer.Answered }, { error("no restart") }, clock())
        assertEquals(LocalServerWait.Ready, gate.awaitReady(id))
        assertEquals(0, currentTime)
    }

    @Test
    fun `P0-SERVER-003 a server that never answers is restarted once, then Not answering`() = runTest {
        var restarts = 0
        val gate = LocalServerGate({ ProbeAnswer.NoAnswer }, { restarts++; true }, clock())
        assertEquals(LocalServerWait.NotAnswering, gate.awaitReady(id))
        assertEquals(1, restarts)
        assertTrue(currentTime in 60_000..70_000, "bounded near 60 s, was $currentTime")
    }

    @Test
    fun `P0-SERVER-004 the one restart can bring it back`() = runTest {
        var restarted = false
        val gate = LocalServerGate(
            { if (restarted) ProbeAnswer.Answered else ProbeAnswer.NoAnswer },
            { restarted = true; true },
            clock(),
        )
        assertEquals(LocalServerWait.Ready, gate.awaitReady(id))
    }

    @Test
    fun `P0-SERVER-005 a server the person stopped is neither waited on nor restarted`() = runTest {
        val gate = LocalServerGate({ ProbeAnswer.NoAnswer }, { error("no restart") }, clock(), mayWait = { false })
        assertEquals(LocalServerWait.NotAnswering, gate.awaitReady(id))
        assertEquals(0, currentTime)
    }

    // M1 follow-up: the server process started with the phone (t = 0), so its age is the test clock.
    private fun TestScope.bootAge(): suspend (ProfileId) -> kotlin.time.Duration =
        { LaunchStartRules.stillBooting(currentTime.milliseconds) }

    @Test
    fun `M1 a server still booting is waited on, not restarted, and answers at 50 s`() = runTest {
        var restarts = 0
        val gate = LocalServerGate(
            { if (currentTime >= 50_000) ProbeAnswer.Answered else ProbeAnswer.NoAnswer },
            { restarts++; true },
            clock(),
            booting = bootAge(),
        )
        assertEquals(LocalServerWait.Ready, gate.awaitReady(id))
        assertEquals(0, restarts, "a booting server is never killed for a second boot")
        assertTrue(currentTime in 50_000..55_000, "connected near 50 s, was $currentTime")
    }

    @Test
    fun `M1 a server that never answers is restarted once after the booting window, then Not answering`() = runTest {
        val restartedAt = mutableListOf<Long>()
        val gate = LocalServerGate(
            { ProbeAnswer.NoAnswer },
            { restartedAt += currentTime; true },
            clock(),
            booting = bootAge(),
        )
        assertEquals(LocalServerWait.NotAnswering, gate.awaitReady(id))
        assertEquals(1, restartedAt.size)
        assertTrue(restartedAt.single() >= LaunchStartRules.staleAfter.inWholeMilliseconds, "restarted at ${restartedAt.single()}")
        assertTrue(currentTime <= (LaunchStartRules.staleAfter + 30.seconds + 10.seconds).inWholeMilliseconds, "bounded, was $currentTime")
    }

    @Test
    fun `M1 a server the person stops while it boots is not started`() = runTest {
        var stopped = false
        val gate = LocalServerGate(
            { ProbeAnswer.NoAnswer },
            { error("never start what the person stopped") },
            clock(),
            mayWait = { !stopped },
            booting = { stopped = true; LaunchStartRules.stillBooting(currentTime.milliseconds) },
        )
        assertEquals(LocalServerWait.NotAnswering, gate.awaitReady(id))
    }
}
