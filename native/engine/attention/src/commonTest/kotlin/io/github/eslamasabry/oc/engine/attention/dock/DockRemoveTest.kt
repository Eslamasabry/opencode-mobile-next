package io.github.eslamasabry.oc.engine.attention.dock

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.engine.attention.activity.FakeAttentionKeyStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

class DockRemoveTest {
    private val p = ProfileId("p1")
    private fun ref(n: String) = ConversationRef(p, SessionId(n))
    private val a = ref("a")
    private val b = ref("b")
    private val c1 = ref("c1")

    private fun link(c: ConversationRef, parent: ConversationRef = a) = DockEvent.Link(c, parent.sessionId)

    private fun model(vararg events: DockEvent): DockModel = events.fold(DockModel.empty()) { m, e -> m.reduce(e) }

    private fun DockModel.ids() = ui.entries.map { it.ref }

    @Test
    fun `remove takes the ring out at once and lists it as hidden`() {
        val m = model(DockEvent.RunStarted(a), DockEvent.RunStarted(b)).reduce(DockEvent.Remove(a))
        assertEquals(listOf(b), m.ids())
        assertEquals(listOf(a), m.ui.hidden.map { it.ref })
        assertEquals(1, m.ui.hiddenCount)
        assertEquals(DockPresence.Nub, m.ui.presence, "a hidden conversation is not counted live")
        assertEquals(1, m.ui.liveCount)
        assertEquals(listOf(a), m.removedRefs)
        // Not tracked: nothing to remove.
        assertTrue(model().reduce(DockEvent.Remove(a)).removedRefs.isEmpty())
    }

    @Test
    fun `only a new request or failure brings it back, plain progress does not`() {
        data class Case(val label: String, val events: List<DockEvent>, val back: Boolean)
        val base = listOf(DockEvent.RunStarted(a), DockEvent.RunStarted(b))
        val cases =
            listOf(
                Case("still working", listOf(DockEvent.Tasks(a, 1, 3), DockEvent.Reasoning(a, true)), false),
                Case("finished", listOf(DockEvent.RunEnded(a, DockRunEnd.Succeeded)), false),
                Case("stopped", listOf(DockEvent.RunEnded(a, DockRunEnd.Stopped)), false),
                Case("needs you", listOf(DockEvent.RequestOpened(a, "permission:r")), true),
                Case("failed", listOf(DockEvent.RunEnded(a, DockRunEnd.Failed)), true),
                Case("the session record upgrades a finish to a failure", listOf(DockEvent.RunEnded(a, DockRunEnd.Succeeded), DockEvent.LastRunFailed(a, true)), true),
                Case("a request found by the reconcile read", listOf(DockEvent.RequestsRead(p, mapOf(a.sessionId to setOf("permission:r")))), true),
                Case("a sub-agent of a removed parent needs you", listOf(link(c1), DockEvent.RunStarted(c1), DockEvent.RequestOpened(c1, "permission:r")), true),
            )
        for (c in cases) {
            val m = (base + DockEvent.Remove(a) + c.events).fold(DockModel.empty()) { m, e -> m.reduce(e) }
            assertEquals(c.back, a in m.ids(), c.label)
            if (c.back) assertTrue(a !in m.removedRefs, c.label)
        }
        // An alert that was already there when it was removed stays hidden, and a second request is not a new alert.
        val waiting = model(DockEvent.RunStarted(a), DockEvent.RunStarted(b), DockEvent.RequestOpened(a, "permission:r1"), DockEvent.Remove(a))
        assertFalse(a in waiting.ids())
        assertFalse(a in waiting.reduce(DockEvent.RequestOpened(a, "permission:r2")).ids())
        assertEquals(DockState.NeedsYou, waiting.ui.hidden.single().state)
    }

    @Test
    fun `opening a removed conversation brings it back`() {
        val live = model(DockEvent.RunStarted(a), DockEvent.RunStarted(b), DockEvent.Remove(a)).reduce(DockEvent.Opened(a))
        assertTrue(a in live.ids())
        assertTrue(live.removedRefs.isEmpty())
        val done =
            model(DockEvent.RunStarted(a), DockEvent.RunStarted(b), DockEvent.RunEnded(a, DockRunEnd.Succeeded), DockEvent.Remove(a), DockEvent.Opened(a))
        assertTrue(a in done.ids(), "the open conversation is always in the dock, so opening it undoes the removal")
        assertTrue(done.removedRefs.isEmpty())
        assertTrue(a in done.reduce(DockEvent.Opened(null)).ids(), "left, it stays as a quiet recent ring")
    }

    @Test
    fun `a removed parent takes its sub-agents, a removed sub-agent hides only itself`() {
        val kid = listOf(link(c1), DockEvent.RunStarted(a), DockEvent.RunStarted(c1), DockEvent.RunStarted(b))
        val parentGone = model(*kid.toTypedArray()).reduce(DockEvent.Remove(a))
        assertEquals(listOf(b), parentGone.ids())
        assertEquals(listOf(a), parentGone.ui.hidden.map { it.ref }, "one item for the parent; its sub-agent goes with it")
        assertEquals(DockPresence.Nub, parentGone.ui.presence)
        // The sub-agent was removed first, then the parent: the parent entry covers both.
        assertEquals(listOf(a), model(*kid.toTypedArray()).reduce(DockEvent.Remove(c1)).reduce(DockEvent.Remove(a)).removedRefs)

        val childGone = model(*kid.toTypedArray()).reduce(DockEvent.Remove(c1))
        assertTrue(a in childGone.ids())
        assertTrue(childGone.ui.entries.first { it.ref == a }.children.isEmpty())
        assertEquals(listOf(c1), childGone.ui.hidden.map { it.ref })
        assertEquals(a, childGone.ui.hidden.single().parent)
        // The parent's own working goes on; the hidden child's plain work changes nothing.
        assertEquals(DockState.WorkingWithTasks(1, 2), childGone.reduce(DockEvent.Tasks(a, 1, 2)).ui.entries.first { it.ref == a }.state)
        // A hidden child's earlier alert does not raise the parent; a new one does, and shows the child again.
        val oldAlert =
            model(*kid.toTypedArray(), DockEvent.RequestOpened(c1, "permission:r")).reduce(DockEvent.Remove(c1))
        assertEquals(DockState.Working, oldAlert.ui.entries.first { it.ref == a }.state)
        val newAlert = childGone.reduce(DockEvent.RequestOpened(c1, "permission:r"))
        assertEquals(DockState.NeedsYou, newAlert.ui.entries.first { it.ref == a }.state)
        assertEquals(c1, newAlert.ui.entries.first { it.ref == a }.causedBy)
        assertEquals(listOf(c1), newAlert.ui.entries.first { it.ref == a }.children.map { it.ref })
        // A removed parent stays out while its hidden sub-agent still works, even after the parent itself stops.
        val quiet = parentGone.reduce(DockEvent.RunEnded(a, DockRunEnd.Stopped))
        assertFalse(a in quiet.ids())
        assertEquals(listOf(a), quiet.removedRefs)
        // Both quiet: it is still a recent conversation, so it stays out instead of coming back as an idle ring.
        val allQuiet = quiet.reduce(DockEvent.RunEnded(c1, DockRunEnd.Stopped))
        assertEquals(listOf(a), allQuiet.removedRefs)
        assertFalse(a in allQuiet.ids())
    }

    @Test
    fun `restore and undo bring it back, the screen's own conversation cannot be removed`() {
        val m = model(DockEvent.RunStarted(a), DockEvent.RunStarted(b), DockEvent.Remove(a))
        assertEquals(listOf(a, b).toSet(), m.reduce(DockEvent.Restore(a)).ids().toSet())
        assertTrue(m.reduce(DockEvent.Restore(b)).removedRefs == listOf(a), "restoring what is not removed changes nothing")
        val open = model(DockEvent.Opened(a), DockEvent.RunStarted(a), DockEvent.RunStarted(b))
        assertFalse(open.ui.entries.first { it.ref == a }.removable)
        assertTrue(open.ui.entries.first { it.ref == b }.removable)
        assertTrue(open.reduce(DockEvent.Remove(a)).removedRefs.isEmpty())
        // With a sub-agent on screen the parent ring and that sub-agent are the two that stay.
        val child = model(link(c1), DockEvent.RunStarted(a), DockEvent.RunStarted(c1), DockEvent.RunStarted(b), DockEvent.Opened(c1))
        assertFalse(child.ui.entries.first { it.ref == a }.removable)
        assertFalse(child.ui.entries.first { it.ref == a }.children.single().removable)
        assertTrue(child.reduce(DockEvent.Remove(c1)).removedRefs.isEmpty())
        assertTrue(child.reduce(DockEvent.Remove(a)).removedRefs.isEmpty())
    }

    @Test
    fun `undo of an idle filler brings back that ring in its own place`() {
        val c = ref("c")
        val d = ref("d")
        fun recent(vararg rows: Pair<String, Long>) = DockEvent.Recent(p, rows.map { (id, at) -> DockRecent(SessionId(id), "T-$id", at) })
        val idle = DockModel.empty(3).reduce(recent("a" to 4L, "b" to 3L, "c" to 2L, "d" to 1L))
        assertEquals(listOf(a, b, c), idle.ids())
        val out = idle.reduce(DockEvent.Remove(b))
        assertEquals(listOf(a, c, d), out.ids(), "the next most recent takes the place")
        val back = out.reduce(DockEvent.Restore(b))
        assertEquals(listOf(a, b, c), back.ids(), "undo returns the same ring to its place")
        assertTrue(back.removedRefs.isEmpty())
    }

    @Test
    fun `undo of a live ring and of a sub-agent restores exactly it`() {
        val kid = model(link(c1), DockEvent.RunStarted(a), DockEvent.RunStarted(c1), DockEvent.RunStarted(b))
        val live = kid.reduce(DockEvent.Remove(a))
        assertEquals(listOf(a, b), live.reduce(DockEvent.Restore(a)).ids())
        val sub = kid.reduce(DockEvent.Remove(c1))
        assertEquals(listOf(c1), sub.reduce(DockEvent.Restore(c1)).ui.entries.first { it.ref == a }.children.map { it.ref })
        val pinned = model(DockEvent.PinsLoaded(p, emptyList()), DockEvent.RunStarted(a), DockEvent.RunStarted(b), DockEvent.Pin(b)).reduce(DockEvent.Remove(b))
        assertFalse(b in pinned.ids())
        assertEquals(listOf(b, a), pinned.reduce(DockEvent.Restore(b)).ids())
    }

    @Test
    fun `the removed set is capped, forgets what ended and what was deleted`() {
        val many = (1..DockModel.MAX_REMOVED + 5).map { ref("s$it") }
        var m = many.fold(DockModel.empty()) { acc, r -> acc.reduce(DockEvent.RunStarted(r)) }
        for (r in many) m = m.reduce(DockEvent.Remove(r))
        assertEquals(DockModel.MAX_REMOVED, m.removedRefs.size)
        assertEquals(ref("s6"), m.removedRefs.first(), "the oldest five were forgotten")
        assertEquals(many.last(), m.removedRefs.last())

        val two = model(DockEvent.RunStarted(a), DockEvent.RunStarted(b), DockEvent.Remove(a), DockEvent.Remove(b))
        assertEquals(listOf(b), two.reduce(DockEvent.Deleted(a)).removedRefs)
        val stopped = two.reduce(DockEvent.RunEnded(b, DockRunEnd.Stopped))
        assertEquals(listOf(a, b), stopped.removedRefs, "idle but still a recent conversation: it stays out")
        assertTrue(stopped.ids().isEmpty())
        assertTrue(two.reduce(DockEvent.ProfileGone(p)).removedRefs.isEmpty())
        // A saved set meets the first full read: what the server no longer has is dropped, what is live stays.
        val loaded = DockModel.empty().reduce(DockEvent.RemovedLoaded(p, listOf(a.sessionId, b.sessionId)))
        assertEquals(listOf(a, b), loaded.removedRefs)
        val read = loaded.reduce(DockEvent.StatusesRead(p, setOf(a.sessionId))).reduce(DockEvent.RequestsRead(p, emptyMap()))
        assertEquals(listOf(a), read.removedRefs)
        assertTrue(read.ids().isEmpty(), "and a stays hidden")
        assertEquals(listOf(a), read.ui.hidden.map { it.ref })
    }

    @Test
    fun `entries carry what the answer and stop paths need`() {
        val m = model(link(c1), DockEvent.RunStarted(a), DockEvent.RequestOpened(a, "permission:r1"), DockEvent.RequestOpened(a, "question:q1"), DockEvent.RunStarted(c1), DockEvent.RequestOpened(c1, "form:f1"))
        val e = m.ui.entries.single()
        assertEquals(a, e.ref)
        assertEquals(listOf(DockRequestRef(DockRequestKind.Permission, "r1"), DockRequestRef(DockRequestKind.Question, "q1")), e.requests)
        assertEquals(c1, e.children.single().ref)
        assertEquals(listOf(DockRequestRef(DockRequestKind.Form, "f1")), e.children.single().requests)
        val fed = DockFeed.events(p, GatewayEvent.RunStarted(a.sessionId)).fold(DockModel.empty()) { acc, ev -> acc.reduce(ev) }
        assertTrue(fed.ui.entries.single().requests.isEmpty())
    }

    @Test
    fun `the children source can be swapped`() {
        val source = DockChildSource { profile, event ->
            if (event is GatewayEvent.RunStarted) listOf(DockEvent.Link(ConversationRef(profile, event.sessionId), SessionId("boss"))) else emptyList()
        }
        val events = DockFeed.events(p, GatewayEvent.RunStarted(c1.sessionId), source)
        assertTrue(DockEvent.Link(c1, SessionId("boss")) in events)
        val ui = events.fold(DockModel.empty()) { acc, e -> acc.reduce(e) }.ui
        assertEquals(ref("boss"), ui.entries.single().ref)
        assertEquals(c1, ui.entries.single().children.single().ref)
    }

    @Test
    fun `the removed set survives a restart, per profile, and an empty set removes the key`() = runTest(StandardTestDispatcher()) {
        val disk = FakeAttentionKeyStore()
        val removals = DockRemovals(disk)
        val first = DockController(this, DockNicknames(disk), {}, removals = removals)
        first.feed(DockEvent.RunStarted(a))
        first.feed(DockEvent.RunStarted(b))
        first.remove(a)
        testScheduler.advanceUntilIdle()
        assertEquals("""["a"]""", disk.data[DockKeys.removedKey(p)])
        assertTrue(DockKeys.removedKey(ProfileId("p2")) !in disk.data)

        // A new process: the set is read first, then the first status read lands on it.
        val second = DockController(this, DockNicknames(disk), {}, removals = DockRemovals(disk))
        second.loadRemoved(p)
        second.feed(DockFeed.statusesRead(p, mapOf(a.sessionId to io.github.eslamasabry.oc.core.model.SessionStatus.Busy, b.sessionId to io.github.eslamasabry.oc.core.model.SessionStatus.Busy)))
        assertEquals(listOf(b), second.ui.value.entries.map { it.ref })
        assertEquals(listOf(a), second.ui.value.hidden.map { it.ref })

        second.undoRemove(a)
        testScheduler.advanceUntilIdle()
        assertFalse(DockKeys.removedKey(p) in disk.data)
        assertEquals(listOf(a, b).toSet(), second.ui.value.entries.map { it.ref }.toSet())

        // Garbage on disk reads as an empty set.
        disk.data[DockKeys.removedKey(p)] = "not json"
        assertTrue(DockRemovals(disk).load(p).isEmpty())
        disk.data[DockKeys.removedKey(p)] = """["x","x",7,""]"""
        assertEquals(listOf(SessionId("x")), DockRemovals(disk).load(p))
    }
}
