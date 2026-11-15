package io.github.eslamasabry.oc.engine.schedules

import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Runs that must not spend each other's time, and "replied" only when the reply was seen. */
class ScheduleTruthTest {
    private val budget = 7 * MIN
    private val other = "p2"

    private suspend fun Harness.saveOther(): ScheduledPrompt {
        profiles.fingerprints[other] = "n1:server-b"
        profiles.gateways[other] = ScriptedGateway()
        return (engine.save(other, draft()) as Outcome.Ok).value
    }

    @Test
    fun `B-AGENT-215 a slow run on one server never holds up a run for another server`() = runTest {
        val h = Harness()
        h.saved()
        h.saveOther()
        val slow = h.armed().single()
        val fast = h.alarms.armed.getValue(other).single()
        h.gateway.holdPrompt = CompletableDeferred()
        h.clock.now = T0 + HOUR
        val first = async { h.engine.runOccurrence(slow, budget) }
        runCurrent()
        val second = async { h.engine.runOccurrence(fast, budget) }
        runCurrent()
        assertTrue(second.isCompleted, "the other server's run waited for the slow one")
        assertEquals("ran", second.await()!!.status)
        assertEquals(1, h.profiles.gateways.getValue(other).prompts.size)
        assertFalse(first.isCompleted)
        h.gateway.holdPrompt!!.complete(Unit)
        assertEquals("ran", first.await()!!.status)
    }

    @Test
    fun `B-AGENT-215 the time a run waits for its turn never counts against its sending budget`() = runTest {
        val h = Harness()
        h.saved()
        h.saved()
        val (one, two) = h.armed().sortedBy { it.scheduleId }
        h.gateway.holdPrompt = CompletableDeferred()
        h.clock.now = T0 + HOUR
        val first = async { h.engine.runOccurrence(one, budget) }
        runCurrent()
        val second = async { h.engine.runOccurrence(two, budget) }
        runCurrent()
        // The first run is slow: more than a whole budget passes while the second waits behind it.
        h.clock.now += 8 * MIN
        h.gateway.holdPrompt!!.complete(Unit)
        val late = second.await()!!
        assertEquals("ran", late.status)
        assertNull(late.errorCode)
        assertEquals(2, h.gateway.prompts.size)
        assertEquals("ran", first.await()!!.status)
    }

    @Test
    fun `B-AGENT-215 a timed run that never got its turn is not sent, not spent and runs again`() = runTest {
        val h = Harness(laneWaitMs = 1_000)
        h.saved()
        h.saved()
        val (one, two) = h.armed().sortedBy { it.scheduleId }
        h.gateway.holdPrompt = CompletableDeferred()
        h.clock.now = T0 + HOUR
        val first = async { h.engine.runOccurrence(one, budget) }
        runCurrent()
        val second = async { h.engine.runOccurrence(two, budget) }
        runCurrent()
        advanceTimeBy(1_100)
        runCurrent()
        assertTrue(second.isCompleted)
        assertNull(second.await())
        assertTrue(h.gateway.prompts.isEmpty())
        // No failed "timeout" record; the occurrence is still armed and still owed.
        assertTrue(h.engine.store.history(P).none { it.scheduleId == two.scheduleId })
        assertTrue(two in h.armed())
        h.gateway.holdPrompt!!.complete(Unit)
        first.await()
        assertEquals("ran", h.engine.runOccurrence(two, budget)!!.status)
        assertEquals(2, h.gateway.prompts.size)
    }

    @Test
    fun `B-AGENT-215 a Run now that never got its turn is recorded as not sent`() = runTest {
        val h = Harness(laneWaitMs = 1_000)
        val one = h.saved()
        val two = h.saved()
        h.gateway.holdPrompt = CompletableDeferred()
        h.clock.now = T0 + HOUR
        val first = async { h.engine.runOccurrence(h.armed().first { it.scheduleId == one.id }, budget) }
        runCurrent()
        val tapped = async { h.engine.runManual(P, two.id, 1, budget) }
        runCurrent()
        advanceTimeBy(1_100)
        runCurrent()
        val report = tapped.await()!!
        assertEquals("skipped", report.status)
        assertEquals("busy", report.errorCode)
        assertTrue(h.gateway.prompts.isEmpty())
        val record = h.engine.store.history(P).single { it.scheduleId == two.id }
        assertEquals(RunStatus.Skipped, record.status)
        h.gateway.holdPrompt!!.complete(Unit)
        first.await()
    }

    // ---- accepted but not followed

    private suspend fun Harness.unconfirmedRun(): ScheduledRun {
        gateway.failReads = true
        saved()
        run {
            clock.now = T0 + HOUR
            val report = engine.runOccurrence(armed().first(), budget)!!
            assertEquals("ran", report.status)
            assertEquals(ScheduleRunner.UNCONFIRMED, report.errorCode)
        }
        return engine.store.history(P).single()
    }

    @Test
    fun `B-AGENT-215 a reply that cannot be followed after the server accepted is recorded as sent, not replied`() = runTest {
        val h = Harness()
        val run = h.unconfirmedRun()
        assertEquals(RunStatus.Ran, run.status)
        assertEquals(ScheduleRunner.UNCONFIRMED, run.errorCode)
        assertTrue(run.sessionId != null)
        assertEquals(1, h.gateway.prompts.size)
    }

    @Test
    fun `B-AGENT-215 once the server answers again an unconfirmed run settles from the conversation, sending nothing`() = runTest {
        val h = Harness()
        val run = h.unconfirmedRun()
        // Still unreachable: nothing is claimed either way.
        assertEquals(0, h.engine.settleUnconfirmed(P))
        assertEquals(ScheduleRunner.UNCONFIRMED, h.engine.store.history(P).single().errorCode)
        // Reachable, and the conversation holds a finished reply that began after the prompt.
        h.gateway.failReads = false
        h.gateway.replies = listOf(ScriptedGateway.reply("r9", run.sessionId!!, "All good", at = run.startedAtMs + MIN))
        assertEquals(1, h.engine.settleUnconfirmed(P))
        val settled = h.engine.store.history(P).single()
        assertEquals(RunStatus.Ran, settled.status)
        assertNull(settled.errorCode)
        assertEquals(1, h.gateway.prompts.size)
        // A settled run is never looked at again.
        assertEquals(0, h.engine.settleUnconfirmed(P))
    }

    @Test
    fun `B-AGENT-215 opening the list settles an unconfirmed run that failed`() = runTest {
        val h = Harness()
        val run = h.unconfirmedRun()
        h.gateway.failReads = false
        val failed = ScriptedGateway.reply("r9", run.sessionId!!, "x", at = run.startedAtMs + MIN)
        h.gateway.replies = listOf(failed.copy(info = failed.info.copy(errorText = "boom")))
        h.engine.load(P)
        val settled = h.engine.store.history(P).single()
        assertEquals(RunStatus.Failed, settled.status)
        assertEquals("agent_failed", settled.errorCode)
        assertEquals(1, h.gateway.prompts.size)
    }

    @Test
    fun `B-AGENT-215 an idle conversation with no reply stays unknown at first and is not a reply later`() = runTest {
        val h = Harness()
        h.unconfirmedRun()
        h.gateway.failReads = false
        h.gateway.replies = emptyList()
        h.clock.now += 2 * MIN
        assertEquals(0, h.engine.settleUnconfirmed(P))
        h.clock.now += 20 * MIN
        assertEquals(1, h.engine.settleUnconfirmed(P))
        val settled = h.engine.store.history(P).single()
        assertEquals(RunStatus.Failed, settled.status)
        assertEquals("uncertain_delivery", settled.errorCode)
        assertEquals(1, h.gateway.prompts.size)
    }

    @Test
    fun `B-MIGR-013 a run stored before this change reads as it did and is never touched`() = runTest {
        val h = Harness()
        val saved = h.saved()
        val old = ScheduledRun("${saved.id}:1:$T0", saved.id, T0, T0, RunStatus.Ran, finishedAtMs = T0, sessionId = "x")
        h.engine.store.recordRun(P, old)
        assertEquals(0, h.engine.settleUnconfirmed(P))
        assertEquals(old, h.engine.store.history(P).single())
        assertEquals(0, h.profiles.opens)
    }
}
