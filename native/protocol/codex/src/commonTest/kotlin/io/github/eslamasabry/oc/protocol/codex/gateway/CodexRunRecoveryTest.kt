package io.github.eslamasabry.oc.protocol.codex.gateway

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.protocol.codex.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlin.test.*

class CodexRunRecoveryTest {
    @Test fun `B-435 Codex resume refetches and ends a run completed while disconnected`() = runTest {
        val sid = SessionId("thread-1")
        val first = FakeAppServer().serveThread({ thread(busy = true, turns = listOf(turn(status = "inProgress"))) })
        val next = FakeAppServer().serveThread({ thread(turns = listOf(turn())) })
        val g = gateway(FakeSockets(first, next))
        val events = mutableListOf<GatewayEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { g.events.all.collect { events += it.event } }
        g.messages(sid); g.startListening(); runCurrent()
        first.event("turn/started", jsonOf("threadId" to sid.raw, "turn" to turn(status = "inProgress"))); runCurrent()
        first.drop(); runCurrent(); advanceTimeBy(1_001); runCurrent()
        assertIs<Outcome.Ok<*>>(g.reconcileRuns(setOf(sid)))
        runCurrent()
        assertTrue(next.calls("thread/read").isNotEmpty(), "authoritative thread was refetched")
        assertEquals(1, events.filterIsInstance<GatewayEvent.RunEnded>().count { it.sessionId == sid })
        assertTrue(events.any { it is GatewayEvent.TranscriptStale })
        assertTrue(next.calls("turn/start").isEmpty(), "recovery never resends")
        g.close()
    }
    @Test fun `B-435 Codex liveness reads fresh status instead of cached busy`() = runTest {
        var busy = true
        val server = FakeAppServer().serveThread({ thread(busy = busy, turns = listOf(turn(status = if (busy) "inProgress" else "completed"))) })
        val g = gateway(FakeSockets(server))
        g.messages(SessionId("thread-1"))
        busy = false
        val result = assertIs<Outcome.Ok<*>>(g.reconcileRuns(setOf(SessionId("thread-1"))))
        assertFalse((result.value as Map<*, *>).values.contains(io.github.eslamasabry.oc.core.model.SessionStatus.Busy))
        g.close()
    }
}
