package io.github.eslamasabry.oc.engine.attention.dock

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A ring must not say "Needs you" for a request that ended with its run (the home row reads the server and says so). */
class DockStaleRequestTest {
    private val p = ProfileId("p1")
    private val a = ConversationRef(p, SessionId("a"))
    private val ask = "permission:r1"

    private fun model(vararg events: DockEvent): DockModel = events.fold(DockModel.empty()) { m, e -> m.reduce(e) }

    private fun DockModel.stateOfA() = ui.entries.first { it.ref == a }.state

    private val waiting = arrayOf(DockEvent.RunStarted(a), DockEvent.RequestOpened(a, ask))

    @Test
    fun `stopping a run with a pending request drops the request`() {
        val m = model(*waiting)
        assertEquals(DockState.NeedsYou, m.stateOfA())
        val stopped = m.reduce(DockEvent.RunEnded(a, DockRunEnd.Stopped))
        assertEquals(DockState.Idle, stopped.stateOfA())
        assertTrue(stopped.ui.entries.first { it.ref == a }.requests.isEmpty())
    }

    @Test
    fun `a request answered elsewhere leaves the ring`() {
        val m = model(*waiting).reduce(DockEvent.RequestSettled(a, ask))
        assertEquals(DockState.Working, m.stateOfA())
    }

    @Test
    fun `a run that ended or failed holds no request`() {
        val done = model(*waiting).reduce(DockEvent.RunEnded(a, DockRunEnd.Succeeded))
        assertTrue(done.ui.entries.first { it.ref == a }.requests.isEmpty())
        val failed = model(*waiting).reduce(DockEvent.RunEnded(a, DockRunEnd.Failed))
        assertEquals(DockState.Failed, failed.stateOfA())
    }

    @Test
    fun `a request still pending while the run waits stays`() {
        val m = model(*waiting).reduce(DockEvent.RequestsRead(p, mapOf(a.sessionId to setOf(ask))))
        assertEquals(DockState.NeedsYou, m.stateOfA())
    }

    @Test
    fun `the server reading idle after a missed end drops the request`() {
        val m = model(*waiting).reduce(DockEvent.StatusesRead(p, emptySet()))
        assertTrue(m.ui.entries.first { it.ref == a }.requests.isEmpty())
    }
}
