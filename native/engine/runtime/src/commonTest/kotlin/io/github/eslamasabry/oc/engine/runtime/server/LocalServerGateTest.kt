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
}
