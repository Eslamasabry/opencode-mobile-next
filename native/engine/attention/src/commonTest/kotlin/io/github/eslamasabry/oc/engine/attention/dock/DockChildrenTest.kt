package io.github.eslamasabry.oc.engine.attention.dock

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.engine.attention.activity.FakeAttentionKeyStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class DockChildrenTest {
    private val p = ProfileId("p1")
    private fun ref(n: String) = ConversationRef(p, SessionId(n))
    private val a = ref("a")
    private val b = ref("b")
    private val c1 = ref("c1")
    private val c2 = ref("c2")

    private fun child(c: ConversationRef, parent: ConversationRef = a, title: String? = "t-${c.sessionId.raw}", agent: String? = null) =
        DockEvent.Meta(c, title, agent, parent.sessionId)

    private fun model(vararg events: DockEvent): DockModel = events.fold(DockModel.empty()) { m, e -> m.reduce(e) }

    private fun DockUi.entry(r: ConversationRef) = entries.first { it.ref == r }

    @Test
    fun `children group under their parent and never become rings`() {
        val ui = model(child(c1, agent = "explore"), DockEvent.RunStarted(a), DockEvent.RunStarted(c1)).ui
        assertEquals(listOf(a), ui.entries.map { it.ref })
        assertEquals(listOf(c1), ui.entry(a).children.map { it.ref })
        assertEquals("explore", ui.entry(a).children.single().name, "the agent names it")
        assertEquals("t-c1", model(child(c1), DockEvent.RunStarted(c1)).ui.entry(a).children.single().name, "else the title")
        // The parent arrives after the child started: the child is regrouped once the session record says whose it is.
        val late = model(DockEvent.RunStarted(c1), DockEvent.RunStarted(b)).reduce(child(c1))
        assertEquals(listOf(b, a).toSet(), late.ui.entries.map { it.ref }.toSet())
        assertTrue(late.ui.entries.none { it.ref == c1 })
        // Another server's session with the same id is not a child.
        val other = ConversationRef(ProfileId("p2"), c1.sessionId)
        assertTrue(model(child(c1), DockEvent.RunStarted(other)).ui.entries.single().ref == other)
    }

    @Test
    fun `a child that needs you or failed raises the parent and names the cause`() {
        data class Case(val label: String, val events: List<DockEvent>, val state: DockState, val own: DockState, val cause: ConversationRef?)
        val kid = listOf(child(c1), DockEvent.RunStarted(a), DockEvent.RunStarted(c1))
        val cases =
            listOf(
                Case("needs you", kid + DockEvent.RequestOpened(c1, "r"), DockState.NeedsYou, DockState.Working, c1),
                Case("failed", kid + DockEvent.RunEnded(c1, DockRunEnd.Failed), DockState.Failed, DockState.Working, c1),
                Case("own request outranks a child failure", kid + DockEvent.RequestOpened(a, "r") + DockEvent.RunEnded(c1, DockRunEnd.Failed), DockState.NeedsYou, DockState.NeedsYou, null),
                Case("a child request outranks own failure", kid + DockEvent.RunEnded(a, DockRunEnd.Failed) + DockEvent.RequestOpened(c1, "r"), DockState.NeedsYou, DockState.Failed, c1),
                Case("own failure stays when a child also failed", kid + DockEvent.RunEnded(a, DockRunEnd.Failed) + DockEvent.RunEnded(c1, DockRunEnd.Failed), DockState.Failed, DockState.Failed, null),
                Case("a working child changes nothing for a working parent", kid, DockState.Working, DockState.Working, null),
                Case("an answered request lowers it again", kid + DockEvent.RequestOpened(c1, "r") + DockEvent.RequestSettled(c1, "r"), DockState.Working, DockState.Working, null),
            )
        for (c in cases) {
            val e = model(*c.events.toTypedArray()).ui.entry(a)
            assertEquals(c.state, e.state, c.label)
            assertEquals(c.own, e.ownState, c.label)
            assertEquals(c.cause, e.causedBy, c.label)
        }
    }

    @Test
    fun `a child alert pulses the parent and untucks`() {
        val m = model(child(c1), DockEvent.RunStarted(a), DockEvent.RunStarted(b), DockEvent.RunStarted(c1), DockEvent.Tuck)
        assertEquals(0, m.ui.entry(a).pulse)
        val alerted = m.reduce(DockEvent.RequestOpened(c1, "r"))
        assertEquals(1, alerted.ui.entry(a).pulse)
        assertEquals(1, alerted.ui.entry(a).children.single().pulse)
        assertEquals(DockPresence.Out, alerted.ui.presence)
        assertEquals(DockPresence.Nub, m.ui.presence)
    }

    @Test
    fun `a parent is live while a child works even if it is idle between steps`() {
        val ui = model(child(c1), DockEvent.RunStarted(c1)).ui
        assertEquals(listOf(a), ui.entries.map { it.ref }, "the idle parent is kept for its working child")
        assertEquals(DockState.Idle, ui.entry(a).ownState)
        assertEquals(DockState.Working, ui.entry(a).state)
        assertEquals(1, ui.liveCount)
        assertEquals(DockPresence.Nub, ui.presence)
        assertEquals(DockPresence.Out, model(child(c1), DockEvent.RunStarted(c1), DockEvent.RunStarted(b)).ui.presence)
        // The child stops: the child leaves, the parent stays as a quiet recent ring.
        val quiet = model(child(c1), DockEvent.RunStarted(c1), DockEvent.RunEnded(c1, DockRunEnd.Stopped)).ui
        assertEquals(listOf(a), quiet.entries.map { it.ref })
        assertEquals(DockState.Idle, quiet.entry(a).state)
        assertTrue(quiet.entry(a).children.isEmpty())
    }

    @Test
    fun `seen - opening the child clears it, opening the parent clears only finished children`() {
        val failed = model(child(c1), DockEvent.RunStarted(a), DockEvent.RunStarted(b), DockEvent.RunStarted(c1), DockEvent.RunEnded(c1, DockRunEnd.Failed))
        assertEquals(DockState.Failed, failed.ui.entry(a).state)
        val parentOpened = failed.reduce(DockEvent.Opened(a))
        assertEquals(DockState.Failed, parentOpened.ui.entry(a).children.single().state, "a failed child stays until it is opened")
        assertEquals(DockState.Failed, parentOpened.ui.entry(a).state)
        val childOpened = failed.reduce(DockEvent.Opened(c1))
        assertEquals(DockState.Idle, childOpened.ui.entry(a).children.single().state)
        assertEquals(DockState.Working, childOpened.ui.entry(a).state)
        assertEquals(a, childOpened.ui.open, "the parent stays highlighted")
        assertEquals(c1, childOpened.ui.openChild)
        assertNull(failed.ui.openChild)
        assertEquals(DockState.Working, failed.reduce(DockEvent.Seen(c1)).ui.entry(a).state)

        val finished = model(child(c1), DockEvent.RunStarted(a), DockEvent.RunStarted(b), DockEvent.RunStarted(c1), DockEvent.RunEnded(c1, DockRunEnd.Succeeded))
        assertEquals(DockState.Working, finished.ui.entry(a).state, "a finished child does not raise a working parent")
        assertEquals(DockState.Finished, finished.ui.entry(a).children.single().state)
        assertTrue(finished.reduce(DockEvent.Opened(a)).ui.entry(a).children.isEmpty(), "opening the parent shows the result")
    }

    @Test
    fun `a failure that ends in the child on screen is seen at once`() {
        val ui = model(child(c1), DockEvent.RunStarted(a), DockEvent.RunStarted(c1), DockEvent.Opened(c1), DockEvent.RunEnded(c1, DockRunEnd.Failed)).ui
        assertEquals(DockState.Working, ui.entry(a).state)
        assertEquals(0, ui.entry(a).pulse)
    }

    @Test
    fun `each parent keeps alerts and working children first and counts the rest`() {
        val kids = (1..6).map { ref("k$it") }
        val events =
            kids.map { child(it) } + DockEvent.RunStarted(a) + kids.map { DockEvent.RunStarted(it) } +
                DockEvent.RequestOpened(kids[4], "r") + DockEvent.RunEnded(kids[5], DockRunEnd.Failed)
        val e = model(*events.toTypedArray()).ui.entry(a)
        assertEquals(DockModel.DEFAULT_CHILD_CAP, e.children.size)
        assertEquals(2, e.childOverflow)
        assertEquals(listOf("k1", "k2", "k5", "k6"), e.children.map { it.ref.sessionId.raw }, "alerts kept, creation order")
        assertEquals(DockState.NeedsYou, e.state, "the roll-up sees hidden children too")
        val roomy = events.fold(DockModel.empty(childCap = 8)) { m, ev -> m.reduce(ev) }.ui.entry(a)
        assertEquals(6, roomy.children.size)
        assertEquals(0, roomy.childOverflow)
        // An alert on a child that did not fit still raises the parent and names it.
        val hidden = model(*(events + DockEvent.RequestOpened(kids[0], "q")).toTypedArray()).ui.entry(a)
        assertEquals(kids[0], hidden.causedBy)
    }

    @Test
    fun `children keep their creation order across state changes`() {
        val events =
            listOf(child(c1), child(c2), DockEvent.RunStarted(a), DockEvent.RunStarted(c2), DockEvent.RunStarted(c1),
                DockEvent.RequestOpened(c1, "r"), DockEvent.Tasks(c2, 1, 3), DockEvent.RunEnded(c2, DockRunEnd.Failed))
        var m = DockModel.empty()
        for (ev in events) {
            m = m.reduce(ev)
            val order = m.ui.entries.firstOrNull { it.ref == a }?.children?.map { it.ref }.orEmpty()
            // c2 started first, so it is the older record.
            assertEquals(listOf(c2, c1).filter { it in order }, order, "after $ev")
        }
    }

    @Test
    fun `deleting the parent or closing its server takes the children with it`() {
        val m = model(child(c1), DockEvent.RunStarted(a), DockEvent.RunStarted(c1), DockEvent.RunStarted(b))
        assertEquals(listOf(b), m.reduce(DockEvent.Deleted(a)).ui.entries.map { it.ref })
        assertTrue(m.reduce(DockEvent.ProfileOffline(p)).ui.entries.isEmpty())
        assertTrue(m.reduce(DockEvent.ProfileGone(p)).ui.entries.isEmpty())
    }

    @Test
    fun `the session record and the event stream carry the parent id`() {
        val events = DockFeed.events(p, GatewayEvent.SessionUpserted(Session(c1.sessionId, title = "T", agent = "explore", parentId = a.sessionId)))
        assertEquals(DockEvent.Meta(c1, "T", "explore"), events.first())
        assertTrue(DockEvent.Link(c1, a.sessionId) in events)
        val ui = (events + DockFeed.events(p, GatewayEvent.RunStarted(c1.sessionId))).fold(DockModel.empty()) { m, e -> m.reduce(e) }.ui
        assertEquals("explore", ui.entry(a).children.single().name)
    }

    @Test
    fun `open on a child navigates to the child and keeps the parent highlighted`() = runTest {
        val opened = mutableListOf<ConversationRef>()
        val dock = DockController(this, DockNicknames(FakeAttentionKeyStore()), { opened += it })
        dock.feed(child(c1))
        dock.feed(DockEvent.RunStarted(a))
        dock.feed(DockEvent.RunStarted(c1))
        dock.feed(DockEvent.RunEnded(c1, DockRunEnd.Failed))
        dock.open(c1)
        assertEquals(listOf(c1), opened)
        assertEquals(a, dock.ui.value.open)
        assertEquals(c1, dock.ui.value.openChild)
        assertEquals(DockState.Idle, dock.ui.value.entry(a).children.single().state)
        dock.conversationShown(a)
        assertNull(dock.ui.value.openChild)
    }
}
