package io.github.eslamasabry.oc.engine.attention.dock

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The owner's "always available" rule: never hidden for lack of live conversations, recent ones fill free places. */
class DockAlwaysAvailableTest {
    private val p = ProfileId("p1")
    private val q = ProfileId("p2")
    private fun ref(n: String, profile: ProfileId = p) = ConversationRef(profile, SessionId(n))
    private val a = ref("a")
    private val b = ref("b")
    private val c = ref("c")
    private val d = ref("d")

    private fun model(vararg events: DockEvent, cap: Int = DockModel.DEFAULT_CAP): DockModel =
        events.fold(DockModel.empty(cap)) { m, e -> m.reduce(e) }

    private fun recent(vararg rows: Pair<String, Long>, profile: ProfileId = p) =
        DockEvent.Recent(profile, rows.map { (id, at) -> DockRecent(SessionId(id), "T-$id", at) })

    private fun DockModel.ids() = ui.entries.map { it.ref }

    @Test
    fun `one idle conversation is the tucked nub with one quiet ring`() {
        val listed = model(recent("a" to 100L))
        assertEquals(listOf(a), listed.ids())
        val ring = listed.ui.entries.single()
        assertEquals(DockState.Idle, ring.state)
        assertEquals("T-a", ring.name)
        assertEquals(0, ring.pulse)
        assertEquals(DockPresence.Nub, listed.ui.presence)
        assertEquals(0, listed.ui.liveCount)
        // The same from what the dock heard: a plain chat that ran and stopped stays as a quiet ring.
        val ran = model(DockEvent.RunStarted(a), DockEvent.RunEnded(a, DockRunEnd.Stopped))
        assertEquals(listOf(a), ran.ids())
        assertEquals(DockPresence.Nub, ran.ui.presence)
        // A finish the person saw and left is quiet too.
        val seen = model(DockEvent.RunStarted(a), DockEvent.RunEnded(a, DockRunEnd.Succeeded), DockEvent.Opened(a), DockEvent.Opened(null))
        assertEquals(DockState.Idle, seen.ui.entries.single().state)
        assertEquals(DockPresence.Nub, seen.ui.presence)
        // Nothing in the dock and no server connected (first run): hidden.
        assertEquals(DockPresence.Hidden, DockModel.empty().ui.presence)
    }

    @Test
    fun `an empty dock is the nub while a server is connected and hidden when none is`() {
        val none = DockModel.empty()
        assertEquals(DockPresence.Hidden, none.ui.presence, "first run, nothing connected")
        // Connecting (the wiring always loads the pins on attach) gives the nub with no ring: its "+" has a server.
        val connected = none.reduce(DockEvent.PinsLoaded(p, emptyList()))
        assertTrue(connected.ids().isEmpty())
        assertEquals(DockPresence.Nub, connected.ui.presence)
        assertEquals(0, connected.ui.liveCount)
        assertEquals(DockPresence.Hidden, connected.reduce(DockEvent.ProfileOffline(p)).ui.presence, "the only server went offline")
        val two = connected.reduce(DockEvent.PinsLoaded(q, emptyList()))
        assertEquals(DockPresence.Nub, two.reduce(DockEvent.ProfileOffline(q)).ui.presence, "another server is still connected")
        assertEquals(DockPresence.Hidden, two.reduce(DockEvent.ProfileGone(p)).reduce(DockEvent.ProfileOffline(q)).ui.presence)
        // The last conversation deleted while connected: still the nub.
        val last = model(DockEvent.PinsLoaded(p, emptyList()), recent("a" to 1L))
        assertEquals(listOf(a), last.ids())
        assertEquals(DockPresence.Nub, last.reduce(DockEvent.Deleted(a)).ui.presence)
    }

    @Test
    fun `nothing live but five recent gives five idle rings, most recent first, and a state change never reshuffles`() {
        val m =
            model(
                DockEvent.Recent(
                    p,
                    listOf(
                        DockRecent(a.sessionId, "A", 10), DockRecent(b.sessionId, "B", 50), DockRecent(c.sessionId, "C", 30),
                        DockRecent(d.sessionId, "D", 40), DockRecent(SessionId("e"), "E", 20),
                        // A sub-agent is never a ring of its own.
                        DockRecent(SessionId("k"), "kid", 99, parent = b.sessionId),
                    ),
                ),
            )
        assertEquals(listOf(b, d, c, ref("e"), a), m.ids())
        assertTrue(m.ui.entries.all { it.state == DockState.Idle })
        assertEquals(DockPresence.Nub, m.ui.presence)
        assertEquals(0, m.ui.overflow)
        val working = m.reduce(DockEvent.RunStarted(c))
        assertEquals(listOf(b, d, c, ref("e"), a), working.ids(), "c works in its own place")
        assertEquals(DockState.Working, working.ui.entries[2].state)
        assertEquals(listOf(b, d, c, ref("e"), a), working.reduce(DockEvent.RunEnded(c, DockRunEnd.Stopped)).ids())
    }

    @Test
    fun `live rings outrank idle fillers at the cap and the overflow counts only live ones`() {
        val rows = (1..6).map { "r$it" to it.toLong() }.toTypedArray()
        val (x, y, z, w, v) = listOf("x", "y", "z", "w", "v").map { ref(it) }
        val idle = model(recent(*rows), cap = 4)
        assertEquals(listOf("r6", "r5", "r4", "r3"), idle.ids().map { it.sessionId.raw })
        val two = idle.reduce(DockEvent.RunStarted(x)).reduce(DockEvent.RunStarted(y))
        assertEquals(listOf(ref("r6"), ref("r5"), x, y), two.ids(), "fillers give up their places, live ones join last")
        assertEquals(0, two.ui.overflow)
        assertEquals(DockPresence.Nub, two.ui.presence, "two live never bring it out by itself")
        val full = two.reduce(DockEvent.RunStarted(z)).reduce(DockEvent.RunStarted(w)).reduce(DockEvent.RunStarted(v))
        assertEquals(listOf(x, y, z, w), full.ids())
        assertTrue(full.ui.entries.none { it.state == DockState.Idle }, "no idle filler while live ones overflow")
        assertEquals(1, full.ui.overflow)
        val asks = full.reduce(DockEvent.RequestOpened(v, "permission:r"))
        assertEquals(listOf(x, y, z, v), asks.ids(), "what needs the person still wins a place")
        assertEquals(1, asks.ui.overflow)
        // Pinned and idle beats a filler too.
        val pinned = model(DockEvent.PinsLoaded(p, listOf(SessionId("pin"))), recent(*rows), cap = 2)
        assertEquals(listOf(ref("pin"), ref("r6")), pinned.ids())
    }

    @Test
    fun `a removed idle filler stays out and the next most recent takes its place`() {
        val m = model(recent("a" to 30L, "b" to 20L, "c" to 10L, "d" to 5L), cap = 3)
        assertEquals(listOf(a, b, c), m.ids())
        val removed = m.reduce(DockEvent.Remove(b))
        assertEquals(listOf(a, c, d), removed.ids())
        assertEquals(listOf(b), removed.removedRefs)
        assertTrue(removed.ui.hidden.isEmpty(), "only live removed conversations are listed as hidden")
        assertEquals(listOf(b), removed.reduce(DockEvent.RequestsRead(p, emptyMap())).removedRefs, "a full read keeps it out")
        // Plain working does not bring it back; it is listed as hidden while live. Restore does.
        val works = removed.reduce(DockEvent.RunStarted(b))
        assertFalse(b in works.ids())
        assertEquals(listOf(b), works.ui.hidden.map { it.ref })
        assertTrue(b in works.reduce(DockEvent.Restore(b)).ids())
        // Undo right away.
        assertEquals(setOf(a, b, c), removed.reduce(DockEvent.Restore(b)).ids().toSet())
        // After a restart the saved set meets the listing and the first read: still out.
        val restarted = model(DockEvent.RemovedLoaded(p, listOf(b.sessionId)), recent("a" to 30L, "b" to 20L, "c" to 10L), DockEvent.RequestsRead(p, emptyMap()))
        assertEquals(listOf(b), restarted.removedRefs)
        assertEquals(listOf(a, c), restarted.ids())
    }

    @Test
    fun `a deleted conversation and a disconnected server's conversations leave, and recent ones are bounded`() {
        val x = ref("x", q)
        val m = model(recent("a" to 10L, "b" to 20L), recent("x" to 30L, profile = q))
        assertEquals(listOf(b, a, x), m.ids())
        val deleted = m.reduce(DockEvent.Deleted(b))
        assertEquals(listOf(a, x), deleted.ids())
        val offline = deleted.reduce(DockEvent.ProfileOffline(q))
        assertEquals(listOf(a), offline.ids())
        assertEquals(listOf(a, x), offline.reduce(recent("x" to 30L, profile = q)).ids(), "the listing on reconnect brings it back")
        val gone = offline.reduce(DockEvent.ProfileGone(p))
        assertTrue(gone.ids().isEmpty())
        assertEquals(DockPresence.Hidden, gone.ui.presence, "no ring and no server connected (nothing loaded pins here)")
        // At most MAX_RECENT remembered: the newest.
        val many = model(recent(*(1..30).map { "s$it" to it.toLong() }.toTypedArray()), cap = 25)
        assertEquals(DockModel.MAX_RECENT, many.ids().size)
        assertEquals((11..30).map { ref("s$it") }.toSet(), many.ids().toSet())
    }

    @Test
    fun `the open conversation stays, highlighted and not removable`() {
        val open = model(DockEvent.Opened(a))
        assertEquals(listOf(a), open.ids())
        assertEquals(a, open.ui.open)
        assertFalse(open.ui.entries.single().removable)
        assertEquals(DockPresence.Nub, open.ui.presence)
        assertTrue(open.reduce(DockEvent.Remove(a)).removedRefs.isEmpty())
        val left = open.reduce(DockEvent.Opened(null))
        assertEquals(listOf(a), left.ids(), "left, it stays as a recent ring")
        assertTrue(left.ui.entries.single().removable)
        // Not in the listing and the listing fills the dock: the open one still has its place.
        val z = ref("z")
        val full = model(recent(*(1..10).map { "s$it" to it.toLong() }.toTypedArray()), DockEvent.Opened(z))
        assertTrue(z in full.ids())
        assertEquals(DockModel.DEFAULT_CAP, full.ids().size)
        // A sub-agent on screen: its parent's ring is the one shown.
        val child = model(DockEvent.Link(ref("c1"), a.sessionId), DockEvent.Opened(ref("c1")))
        assertEquals(listOf(a), child.ids())
        assertEquals(ref("c1"), child.ui.openChild)
    }
}
