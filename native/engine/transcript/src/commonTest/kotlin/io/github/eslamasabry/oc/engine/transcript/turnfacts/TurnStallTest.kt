package io.github.eslamasabry.oc.engine.transcript.turnfacts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Port of test/turn_stall_test.dart.
class TurnStallTest {
    private val reachable = TurnStallEvidence(transportConnected = true, helperRunning = true, endpointReachable = true)

    @Test
    fun `B-CHAT-118 changed liveness evidence rechecks without resetting silence`() {
        val tracker = TurnStallTracker()
        tracker.begin("turn", 0)
        val now = 45_000L
        val token = tracker.takeProbe("turn", now)!!
        tracker.completeProbe(token, TurnStallEvidence(transportConnected = true, endpointReachable = true), now)
        assertEquals(TurnStallKind.ModelSlow, tracker.diagnosisFor("turn")?.kind)
        tracker.invalidateEvidence("turn")
        assertEquals(listOf("turn"), tracker.dueSessionIds(now))
        tracker.completeProbe(tracker.takeProbe("turn", now)!!, TurnStallEvidence(transportConnected = false), now)
        assertEquals(TurnStallKind.ConnectionLost, tracker.diagnosisFor("turn")?.kind)
    }

    @Test
    fun `B-CHAT-118 classifies healthy silence as model slow, stopped helper and connection loss`() {
        assertEquals(TurnStallKind.ModelSlow, classifyTurnStall(reachable))
        assertEquals(
            TurnStallKind.HelperStopped,
            classifyTurnStall(TurnStallEvidence(transportConnected = true, helperRunning = false, endpointReachable = true)),
        )
        assertEquals(
            TurnStallKind.ConnectionLost,
            classifyTurnStall(TurnStallEvidence(transportConnected = false, helperRunning = true, endpointReachable = true)),
        )
        assertEquals(
            TurnStallKind.ConnectionLost,
            classifyTurnStall(TurnStallEvidence(transportConnected = true, helperRunning = true, endpointReachable = false)),
        )
        assertEquals(TurnStallKind.ConnectionLost, classifyTurnStall(TurnStallEvidence(transportConnected = true)))
    }

    @Test
    fun `B-CHAT-118 timer cadence and one hung probe remain within sixty seconds`() {
        val tracker = TurnStallTracker()
        tracker.begin("one", 0)
        var now = TURN_STALL_SILENCE_MS + TURN_STALL_TICK_MS
        val token = tracker.takeProbe("one", now)!!
        // One millisecond before the budget the hung probe has not resolved.
        assertNull(tracker.expireProbe(token, transportConnected = true, nowMs = now + TURN_STALL_PROBE_BUDGET_MS - 1))
        now += TURN_STALL_PROBE_BUDGET_MS
        val result = tracker.expireProbe(token, transportConnected = true, nowMs = now)
        assertEquals(TurnStallKind.ConnectionLost, result?.kind)
        assertEquals(60_000L, result?.silentForMs)
        assertEquals(TurnStallNotice.ConnectionUnchecked, result?.notice)
        // The hung probe finally answers: discarded.
        assertNull(tracker.completeProbe(token, reachable, now + 1))
        assertEquals(TurnStallKind.ConnectionLost, tracker.diagnosisFor("one")?.kind)
    }

    @Test
    fun `B-CHAT-118 throwing probe returns only safe unknown evidence`() {
        val evidence = boundedTurnStallEvidence(null, transportConnected = true)
        assertNull(evidence.helperRunning)
        assertNull(evidence.endpointReachable)
        assertEquals(TurnStallKind.ConnectionLost, classifyTurnStall(evidence))
        assertEquals(reachable, boundedTurnStallEvidence(reachable, transportConnected = false))
    }

    @Test
    fun `B-CHAT-118 silence is per turn and progress invalidates an in-flight probe`() {
        val tracker = TurnStallTracker()
        tracker.begin("one", 0)
        tracker.begin("two", 0)
        var now = 44_000L
        assertTrue(tracker.dueSessionIds(now).isEmpty())
        tracker.noteProgress("two", now)
        now = 45_000L
        assertEquals(listOf("one"), tracker.dueSessionIds(now))
        val stale = tracker.takeProbe("one", now)!!
        assertNull(tracker.takeProbe("one", now))
        tracker.noteProgress("one", now)
        assertNull(tracker.completeProbe(stale, reachable, now))
        now += TURN_STALL_SILENCE_MS
        assertEquals(listOf("one", "two"), tracker.dueSessionIds(now))
        val fresh = tracker.takeProbe("one", now)!!
        assertEquals(TurnStallKind.ModelSlow, tracker.completeProbe(fresh, reachable, now)?.kind)
        assertEquals(TurnStallNotice.ModelSlow, tracker.diagnosisFor("one")?.notice)
        assertNull(tracker.takeProbe("one", now))
        tracker.noteProgress("one", now)
        assertNull(tracker.diagnosisFor("one"))
    }

    @Test
    fun `B-CHAT-118 waiting for user suppresses stalls and restarts silence when answered`() {
        val tracker = TurnStallTracker()
        tracker.begin("one", 0)
        var now = TURN_STALL_SILENCE_MS
        val token = tracker.takeProbe("one", now)!!
        tracker.setWaiting("one", true, now)
        assertNull(tracker.completeProbe(token, reachable, now))
        now = 5 * 60_000L
        assertTrue(tracker.dueSessionIds(now).isEmpty())
        tracker.setWaiting("one", false, now)
        assertNull(tracker.takeProbe("one", now))
        now += TURN_STALL_SILENCE_MS
        assertNotNull(tracker.takeProbe("one", now))
    }

    @Test
    fun `B-CHAT-118 completed, replaced and reset connections discard old probes`() {
        val tracker = TurnStallTracker()
        tracker.begin("one", 0)
        var now = TURN_STALL_SILENCE_MS
        val old = tracker.takeProbe("one", now)!!
        tracker.finish("one")
        tracker.begin("one", now)
        assertNull(tracker.completeProbe(old, reachable, now))
        now += TURN_STALL_SILENCE_MS
        val current = tracker.takeProbe("one", now)!!
        tracker.clear()
        assertNull(tracker.completeProbe(current, reachable, now))
        assertTrue(tracker.dueSessionIds(now).isEmpty())
    }

    @Test
    fun `B-CHAT-118 repeated busy statuses do not keep resetting silence`() {
        val tracker = TurnStallTracker()
        tracker.begin("one", 0)
        tracker.begin("one", 40_000L)
        assertNotNull(tracker.takeProbe("one", TURN_STALL_SILENCE_MS))
    }

    @Test
    fun `B-CHAT-118 a running tool is work, and the helper own exit record words the notice`() {
        val tracker = TurnStallTracker()
        tracker.begin("one", 0)
        tracker.setToolRunning("one", true, 0)
        assertTrue(tracker.dueSessionIds(10 * 60_000L).isEmpty())
        tracker.setToolRunning("one", false, 10 * 60_000L)
        assertNull(tracker.takeProbe("one", 10 * 60_000L + TURN_STALL_SILENCE_MS - 1))
        val token = tracker.takeProbe("one", 10 * 60_000L + TURN_STALL_SILENCE_MS)!!
        val killed =
            TurnStallEvidence(
                transportConnected = true,
                helperRunning = false,
                helperStatus =
                    TurnStallHelperFacts(running = false, lastExitAtMs = 1, lastStopRequested = false, possibleResourceKill = true),
            )
        val diagnosis = tracker.completeProbe(token, killed, 10 * 60_000L + TURN_STALL_SILENCE_MS)
        assertEquals(TurnStallNotice.HelperStoppedResourceKill, diagnosis?.notice)
        assertEquals(
            TurnStallNotice.HelperStoppedUnexpectedly,
            diagnosis?.copy(evidence = killed.copy(helperStatus = killed.helperStatus?.copy(possibleResourceKill = false)))?.notice,
        )
        assertEquals(TurnStallNotice.HelperStopped, diagnosis?.copy(evidence = killed.copy(helperStatus = null))?.notice)
        assertEquals(
            TurnStallNotice.ConnectionLost,
            TurnStallDiagnosis(TurnStallKind.ConnectionLost, TurnStallEvidence(true, endpointReachable = false), 0).notice,
        )
    }

    @Test
    fun `B-CHAT-119 tracker memory is bounded and blank session ids are ignored`() {
        val tracker = TurnStallTracker(maximumSessions = 2)
        tracker.begin("", 0)
        tracker.begin("one", 0)
        tracker.begin("two", 0)
        tracker.begin("three", 0)
        val now = TURN_STALL_SILENCE_MS
        assertEquals(listOf("two", "three"), tracker.dueSessionIds(now))
        tracker.noteProgress("one", now)
        tracker.setWaiting("unknown", true, now)
        assertNull(tracker.takeProbe("one", now))
    }
}
