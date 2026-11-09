package io.github.eslamasabry.oc.engine.team

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * Flutter's "Turn on AI Team on this phone" (Dart `PhoneTeamSetupController`): four real steps with their own times,
 * the running reply finishes first, OpenCode and the terminals stop only after a yes, a failure leaves the phone as it
 * was. The rows are B-AGENT-294 (the team page, on or off) and B-AGENT-043 (a failure never silently changes the team).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PhoneTeamSetupTest {
    private val safe = SetupHealth(boundary = true, canExecute = true, boundaryTier = "landlock")

    private class Ports : PhoneSetupPorts {
        val calls = mutableListOf<String>()
        var server = true
        var busyReads = 0
        var host = SetupHost(serverRunning = false, terminals = 0, restartRequired = false)
        var engine: Outcome<SetupHealth> = Outcome.Ok(SetupHealth(boundary = true, canExecute = true, boundaryTier = "landlock"))
        var probe: Outcome<SetupHealth> = Outcome.Ok(SetupHealth(boundary = true, canExecute = true, boundaryTier = "landlock"))
        var startServer: Outcome<Unit> = Outcome.Ok(Unit)
        var save: Outcome<Unit> = Outcome.Ok(Unit)
        var restored = true
        var engineAnswers = ArrayDeque<Outcome<SetupHealth>>()

        override suspend fun hasServer(): Boolean = server

        override suspend fun replyRunning(): Boolean {
            calls += "replyRunning"
            return busyReads-- > 0
        }

        override suspend fun inspect(): SetupHost {
            calls += "inspect"
            return host
        }

        override suspend fun stopServer(): Outcome<Unit> {
            calls += "stopServer"
            return Outcome.Ok(Unit)
        }

        override suspend fun closeTerminals(): Outcome<Unit> {
            calls += "closeTerminals"
            return Outcome.Ok(Unit)
        }

        override suspend fun startEngine(): Outcome<SetupHealth> {
            calls += "startEngine"
            return engineAnswers.removeFirstOrNull() ?: engine
        }

        override suspend fun stopEngine() {
            calls += "stopEngine"
        }

        override suspend fun probeEngine(): Outcome<SetupHealth> {
            calls += "probeEngine"
            return probe
        }

        override suspend fun startServer(): Outcome<Unit> {
            calls += "startServer"
            return startServer
        }

        override suspend fun saveTeam(): Outcome<Unit> {
            calls += "saveTeam"
            return save
        }

        override suspend fun restoreServer(): Boolean {
            calls += "restoreServer"
            return restored
        }
    }

    private fun TestScope.clock(): Clock =
        object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(testScheduler.currentTime)
        }

    private fun TestScope.setup(ports: Ports, attempts: Int = 3, replyWaitMs: Long = 10_000) =
        PhoneTeamSetup(ports, clock(), readyAttempts = attempts, readyGapMs = 1_000, replyWaitMs = replyWaitMs, replyPollMs = 2_000)

    @Test
    fun `B-AGENT-294 nothing to stop runs the four steps and keeps the team`() = runTest {
        val ports = Ports()
        val run = setup(ports)
        run.run()
        val state = run.state.value
        assertEquals(SetupPhase.Done, state.phase)
        assertEquals(listOf("replyRunning", "inspect", "startEngine", "startServer", "probeEngine", "saveTeam"), ports.calls)
        // Every step has its own end time; the stop step was not needed and says so.
        assertEquals(SetupStep.entries.toSet(), state.tookMs.keys)
        assertEquals(setOf(SetupStep.Stop), state.skipped)
        assertEquals("landlock", state.boundaryTier)
    }

    @Test
    fun `B-AGENT-294 a running reply finishes before OpenCode is touched`() = runTest {
        val ports = Ports().apply { busyReads = 2; host = host.copy(serverRunning = true) }
        val run = setup(ports)
        val job = launch { run.run() }
        runCurrent()
        assertEquals(SetupStep.Reply, run.state.value.step)
        assertTrue(SetupStep.Reply !in run.state.value.tookMs)
        assertFalse("stopServer" in ports.calls)
        advanceTimeBy(4_001)
        runCurrent()
        // The reply ended: the only thing left is the question; nothing has stopped yet.
        assertEquals(SetupPhase.Confirming, run.state.value.phase)
        assertFalse("stopServer" in ports.calls)
        run.answer(false)
        job.join()
    }

    @Test
    fun `B-AGENT-294 a reply that never ends fails the run without touching OpenCode`() = runTest {
        val ports = Ports().apply { busyReads = Int.MAX_VALUE; host = host.copy(serverRunning = true) }
        val run = setup(ports, replyWaitMs = 6_000)
        run.run()
        val state = run.state.value
        assertEquals(SetupPhase.Failed, state.phase)
        assertEquals(SetupProblem.Engine, state.problem)
        assertEquals(SetupStep.Reply, state.failedStep)
        assertEquals("timeout:replyStillRunning", state.details)
        assertFalse("stopServer" in ports.calls)
        assertFalse("startEngine" in ports.calls)
    }

    @Test
    fun `B-AGENT-294 OpenCode and the terminals stop only after a yes`() = runTest {
        val ports = Ports().apply { host = SetupHost(serverRunning = true, terminals = 2, restartRequired = false) }
        val run = setup(ports)
        val job = launch { run.run() }
        advanceUntilIdle()
        assertEquals(SetupPhase.Confirming, run.state.value.phase)
        assertFalse("stopServer" in ports.calls)
        assertFalse("startEngine" in ports.calls)
        run.answer(true)
        job.join()
        val state = run.state.value
        assertEquals(SetupPhase.Done, state.phase)
        assertTrue(state.terminalsClosed)
        val order = ports.calls.filter { it in setOf("stopServer", "closeTerminals", "startEngine", "startServer") }
        assertEquals(listOf("stopServer", "closeTerminals", "startEngine", "startServer"), order)
    }

    @Test
    fun `B-AGENT-043 a no changes nothing and the team stays off`() = runTest {
        val ports = Ports().apply { host = SetupHost(serverRunning = true, terminals = 0, restartRequired = false) }
        val run = setup(ports)
        val job = launch { run.run() }
        advanceUntilIdle()
        run.answer(false)
        job.join()
        val state = run.state.value
        assertEquals(SetupPhase.Failed, state.phase)
        assertEquals(SetupProblem.Declined, state.problem)
        assertEquals(SetupServerState.Untouched, state.serverState)
        assertFalse("stopServer" in ports.calls)
        assertFalse("startEngine" in ports.calls)
    }

    @Test
    fun `B-AGENT-043 a failed safety check stops the engine and brings OpenCode back`() = runTest {
        val ports =
            Ports().apply {
                host = host.copy(serverRunning = true)
                engine = Outcome.Ok(SetupHealth(boundary = false, canExecute = false, boundaryReason = "boundary_proof_failed:native.signal-31"))
            }
        val run = setup(ports)
        val job = launch { run.run() }
        advanceUntilIdle()
        run.answer(true)
        job.join()
        val state = run.state.value
        assertEquals(SetupProblem.Unsafe, state.problem)
        assertEquals(SetupStep.Check, state.failedStep)
        assertEquals("boundary_proof_failed:native.signal-31", state.details)
        assertEquals(SetupServerState.BackOn, state.serverState)
        assertEquals(listOf("stopEngine", "restoreServer"), ports.calls.takeLast(2))
        assertFalse("startServer" in ports.calls)
    }

    @Test
    fun `B-AGENT-043 OpenCode that does not come back is said to be still off`() = runTest {
        val ports =
            Ports().apply {
                host = host.copy(serverRunning = true)
                startServer = Outcome.Failed(Failure.Unexpected())
            }
        val run = setup(ports)
        val job = launch { run.run() }
        advanceUntilIdle()
        run.answer(true)
        job.join()
        val state = run.state.value
        assertEquals(SetupProblem.ServerStart, state.problem)
        assertEquals(SetupServerState.StillOff, state.serverState)
        assertEquals(SetupStep.Server, state.failedStep)
        // It just failed to start: no second start is waited for, but the engine is released.
        assertFalse("restoreServer" in ports.calls)
        assertTrue("stopEngine" in ports.calls)
    }

    @Test
    fun `B-AGENT-043 an engine that never says it can run work ends in Not ready and is released`() = runTest {
        val ports = Ports().apply { probe = Outcome.Ok(SetupHealth(boundary = true, canExecute = false)) }
        val run = setup(ports, attempts = 3)
        run.run()
        val state = run.state.value
        assertEquals(SetupProblem.NotReady, state.problem)
        assertEquals("notExecutable", state.details)
        assertEquals(3, ports.calls.count { it == "probeEngine" })
        assertFalse("saveTeam" in ports.calls)
        assertTrue("stopEngine" in ports.calls)
    }

    @Test
    fun `B-AGENT-043 a team that could not be kept fails the run and puts the phone back`() = runTest {
        val ports = Ports().apply { host = host.copy(serverRunning = true); save = Outcome.Failed(Failure.Unexpected()) }
        val run = setup(ports)
        val job = launch { run.run() }
        advanceUntilIdle()
        run.answer(true)
        job.join()
        assertEquals(SetupProblem.Engine, run.state.value.problem)
        assertEquals("saveFailed", run.state.value.details)
        assertEquals(listOf("stopEngine", "restoreServer"), ports.calls.takeLast(2))
    }

    @Test
    fun `B-AGENT-294 an engine that finds an old OpenCode asks, then proves the phone again`() = runTest {
        val ports =
            Ports().apply {
                engineAnswers = ArrayDeque(listOf(Outcome.Ok(SetupHealth(boundary = true, canExecute = false, restartRequired = true))))
            }
        val run = setup(ports)
        val job = launch { run.run() }
        advanceUntilIdle()
        assertEquals(SetupPhase.Confirming, run.state.value.phase)
        run.answer(true)
        job.join()
        assertEquals(SetupPhase.Done, run.state.value.phase)
        assertEquals(2, ports.calls.count { it == "startEngine" })
        assertFalse(SetupStep.Stop in run.state.value.skipped)
    }

    @Test
    fun `B-AGENT-294 no server on this phone ends calmly at add a server first`() = runTest {
        val ports = Ports().apply { server = false }
        val run = setup(ports)
        run.run()
        assertEquals(SetupProblem.NoServer, run.state.value.problem)
        assertEquals(listOf<String>(), ports.calls)
    }

    @Test
    fun `B-AGENT-294 a run already going is joined, not doubled`() = runTest {
        val ports = Ports().apply { host = host.copy(serverRunning = true) }
        val run = setup(ports)
        val first = launch { run.run() }
        advanceUntilIdle()
        val second = launch { run.run() }
        advanceUntilIdle()
        second.join()
        run.answer(true)
        first.join()
        assertEquals(1, ports.calls.count { it == "startEngine" })
        assertEquals(1, ports.calls.count { it == "inspect" })
    }
}
