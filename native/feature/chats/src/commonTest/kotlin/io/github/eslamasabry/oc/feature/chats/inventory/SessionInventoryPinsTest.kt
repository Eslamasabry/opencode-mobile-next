package io.github.eslamasabry.oc.feature.chats.inventory

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.SessionTime
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SessionInventoryPinsTest {
    private fun TestScope.inventory(source: FakeSource?): SessionInventory =
        SessionInventory(backgroundScope, FakePins()).apply { connect(source, ServerLocation(directory = "/one")) }

    private fun ids(inventory: SessionInventory): List<String> = inventory.sortedSessions().map { it.id.raw }

    @Test
    fun `B-NAV-065 pins sort first with recency kept and unpinning restores the ordinary order`() = runTest {
        val inv = inventory(null)
        inv.adopt(listOf(session("old", updated = 1), session("new", updated = 10)))
        assertEquals(Outcome.Ok(Unit), inv.setPinned(sid("old"), true, inv.locationRevision))
        assertEquals(listOf("old", "new"), ids(inv))
        inv.setPinned(sid("old"), false, inv.locationRevision)
        assertEquals(listOf("new", "old"), ids(inv))
        assertIs<Outcome.Failed>(inv.setPinned(sid("old"), true, inv.locationRevision - 1))
        assertFalse(inv.isPinned(sid("old")))
    }

    @Test
    fun `B-NAV-065 pinned history outside the head page is read once and stays reachable`() = runTest {
        val source = FakeSource().apply {
            page = { Outcome.Ok(page(session("new", updated = 100), next = "older-page")) }
            one = { Outcome.Ok(session(it.raw, updated = 1)) }
        }
        val inv = inventory(source)
        inv.setPinned(sid("old"), true, inv.locationRevision)
        inv.refresh()
        inv.ensureSession(sid("old"))
        runCurrent()
        assertEquals(listOf("old", "new"), ids(inv))
        val reads = source.detailReads.size
        assertEquals(1, reads)
        inv.refresh()
        runCurrent()
        assertEquals(reads, source.detailReads.size)
        assertTrue(inv.hasMore)
        assertEquals(listOf("old", "new"), ids(inv))
    }

    @Test
    fun `B-NAV-065 failed pinned reads stay retryable and keep the pin`() = runTest {
        var fail = true
        val source = FakeSource().apply {
            page = { Outcome.Ok(page(session("new", updated = 100), next = "older-page")) }
            one = { if (fail) Outcome.Failed(offline) else Outcome.Ok(session(it.raw, updated = 1)) }
        }
        val inv = inventory(source)
        inv.setPinned(sid("old"), true, inv.locationRevision)
        inv.refresh()
        inv.ensureSession(sid("old"))
        runCurrent()
        assertTrue(inv.pinnedLoadFailed)
        assertTrue(inv.isPinned(sid("old")))
        fail = false
        inv.refresh()
        inv.ensureSession(sid("old"))
        runCurrent()
        assertFalse(inv.pinnedLoadFailed)
        assertEquals("old", inv.sortedSessions().first().id.raw)
    }

    @Test
    fun `B-STATE-054 5000 sessions across 20 unrelated reads project once`() = runTest {
        val inv = SessionInventory(backgroundScope)
        inv.adopt(List(5000) { session("$it", updated = (it * 7919L) % 5000) })
        var previous: List<*>? = null
        var projections = 0
        repeat(20) {
            val sorted = inv.sortedSessions()
            if (previous !== sorted) projections++
            previous = sorted
        }
        assertEquals(1, projections)
        val old = inv.sortedSessions()
        assertFalse(old is MutableList<*>, "the snapshot is immutable")
        assertEquals(5000, old.size)
        inv.applyUpdated(old.last().copy(time = SessionTime(updatedMs = 99999)))
        val next = inv.sortedSessions()
        assertNotSame(old, next)
        assertEquals(old.last().id, next.first().id)
        assertEquals(4999L, old.first().time!!.updatedMs)
        inv.applyDeleted(old.first().id)
        assertEquals(4999, inv.sortedSessions().size)
        assertSame(inv.sortedSessions(), inv.sortedSessions())
    }
}
