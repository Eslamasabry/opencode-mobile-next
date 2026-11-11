package io.github.eslamasabry.oc.engine.attention.dock

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.engine.attention.activity.FakeAttentionKeyStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

class DockOpenAlertAndLeaveTest {
    private val p = ProfileId("p1")
    private fun ref(n: String) = ConversationRef(p, SessionId(n))
    private val a = ref("a")
    private val b = ref("b")
    private val c = ref("c")

    private fun model(vararg events: DockEvent): DockModel = events.fold(DockModel.empty()) { m, e -> m.reduce(e) }

    // ---- the open conversation is not an alert (its ring raises no pulse; the dock never comes out by itself)

    @Test
    fun `the open conversation's own request raises no alert, another one's does, and neither brings the dock out`() {
        val own = model(DockEvent.Opened(a), DockEvent.RunStarted(a), DockEvent.RequestOpened(a, "permission:r"))
        assertEquals(DockPresence.Nub, own.ui.presence, "the person is looking at it")
        assertEquals(0, own.ui.entries.first { it.ref == a }.pulse)
        assertEquals(DockPresence.Nub, own.reduce(DockEvent.Opened(null)).ui.presence, "leaving it does not bring the dock out")
        val elsewhere = model(DockEvent.Opened(a), DockEvent.RunStarted(b), DockEvent.RequestOpened(b, "permission:r"))
        assertEquals(1, elsewhere.ui.entries.first { it.ref == b }.pulse, "the nub's dot for it changes")
        assertEquals(DockPresence.Nub, elsewhere.ui.presence, "and nothing opens")
    }

    @Test
    fun `a sub-agent's request still alerts while its parent is open, and the parent's while the sub-agent is open`() {
        // The chat shows only its own session's requests, so the dock is the only signal for these.
        val kid = ref("kid")
        val family = arrayOf<DockEvent>(DockEvent.RunStarted(a), DockEvent.RunStarted(kid), DockEvent.Link(kid, a.sessionId))
        val childAsks = model(*family, DockEvent.Opened(a), DockEvent.RequestOpened(kid, "permission:r"))
        assertEquals(1, childAsks.ui.entries.single().pulse)
        val parentAsks = model(*family, DockEvent.Opened(kid), DockEvent.RequestOpened(a, "permission:r"))
        assertEquals(1, parentAsks.ui.entries.single().pulse)
        val kidOwn = model(*family, DockEvent.Opened(kid), DockEvent.RequestOpened(kid, "permission:r"))
        assertEquals(0, kidOwn.ui.entries.single().pulse, "the sub-agent on screen shows its own request")
        for (m in listOf(childAsks, parentAsks, kidOwn)) assertEquals(DockPresence.Nub, m.ui.presence)
    }

    @Test
    fun `the open conversation follows every navigation report, and a pulled-out dock stays out across them`() {
        val asks = model(DockEvent.Untuck, DockEvent.Opened(a), DockEvent.RunStarted(a), DockEvent.RequestOpened(a, "permission:r"))
        assertEquals(DockPresence.Out, asks.ui.presence)
        val home = asks.reduce(DockEvent.Opened(null))
        assertNull(home.ui.open)
        assertEquals(DockPresence.Out, home.ui.presence)
        val back = home.reduce(DockEvent.Opened(a))
        assertEquals(a, back.ui.open)
        assertEquals(DockPresence.Out, back.ui.presence)
        val other = asks.reduce(DockEvent.Opened(b))
        assertEquals(b, other.ui.open)
        assertEquals(DockPresence.Out, other.ui.presence)
        assertEquals(DockPresence.Nub, other.reduce(DockEvent.Tuck).ui.presence, "until the person tucks it")
    }

    @Test
    fun `a fresh start is the nub, and a new request or failure elsewhere never untucks`() {
        val fresh = model(DockEvent.PinsLoaded(p, emptyList()), DockEvent.RunStarted(a), DockEvent.RunStarted(b))
        assertTrue(fresh.ui.tucked)
        assertEquals(DockPresence.Nub, fresh.ui.presence)
        val alerts = fresh.reduce(DockEvent.RequestOpened(a, "permission:r")).reduce(DockEvent.RunEnded(b, DockRunEnd.Failed))
        assertTrue(alerts.ui.tucked)
        assertEquals(DockPresence.Nub, alerts.ui.presence)
        assertEquals(listOf(1, 1), alerts.ui.entries.map { it.pulse }, "each ring's dot says it")
    }

    // ---- archive

    @Test
    fun `a conversation that left the list loses its ring but keeps its nickname`() {
        val m = model(DockEvent.NicknameSet(a, "Bank"), DockEvent.RunStarted(a), DockEvent.RunStarted(b), DockEvent.Pin(a))
        val left = m.reduce(DockEvent.Left(a))
        assertEquals(listOf(b), left.ui.entries.map { it.ref })
        assertTrue(a !in left.pinnedRefs)
        assertTrue(left.ui.hidden.isEmpty(), "no 'left the dock' entry: the home's own bar is the undo")
        assertTrue(left.removedRefs.isEmpty())
        assertEquals("Bank", left.reduce(DockEvent.RunStarted(a)).ui.entries.first { it.ref == a }.nickname)
    }

    @Test
    fun `the home list watch reports only what left a whole list`() {
        val w = DockHomeWatch()
        val all = setOf(p)
        assertTrue(w.events(listOf(a, b, c), complete = true, listed = all).isEmpty())
        assertTrue(w.events(listOf(a, c), complete = false, listed = all).isEmpty(), "a partial list proves nothing")
        assertEquals(listOf<DockEvent>(DockEvent.Left(b)), w.events(listOf(a, c), complete = true, listed = all))
        assertTrue(w.events(listOf(a, c), complete = true, listed = all).isEmpty(), "reported once")
        assertTrue(w.events(emptyList(), complete = true, listed = emptySet()).isEmpty(), "a hidden server loses nothing")
        assertTrue(w.events(listOf(a), complete = true, listed = all).isEmpty(), "and is learned again from scratch")
    }

    @Test
    fun `a server whose rows are all missing at once, or a list that is not whole, takes no ring away`() {
        val w = DockHomeWatch()
        val all = setOf(p)
        w.events(listOf(a, b), complete = true, listed = all)
        // The server's list is being replaced (a reconnect, an edit of the server): no row of it at all proves nothing.
        assertTrue(w.events(emptyList(), complete = true, listed = all).isEmpty())
        // A list of one project only, or with a source still loading or failed, is passed as not whole.
        assertTrue(w.events(listOf(a), complete = false, listed = all).isEmpty())
        // What was seen is kept, so a real archive is still noticed by the next whole list.
        assertEquals(listOf<DockEvent>(DockEvent.Left(b)), w.events(listOf(a), complete = true, listed = all))
    }

    @Test
    fun `a ring that left comes back only when it is live again`() {
        val m = model(DockEvent.RunStarted(a), DockEvent.RunEnded(a, DockRunEnd.Succeeded), DockEvent.Opened(b))
        assertTrue(m.ui.entries.any { it.ref == a })
        assertTrue(m.reduce(DockEvent.Left(a)).ui.entries.none { it.ref == a }, "an archived ring is not offered again")
    }

    // ---- one name

    private class RenameActions(val result: Outcome<Unit>) : DockActions {
        val renamed = mutableListOf<Pair<String, String>>()

        override suspend fun answerPermission(ref: ConversationRef, requestId: String, allow: Boolean): Outcome<Unit> = Outcome.Ok(Unit)

        override suspend fun stop(ref: ConversationRef): Outcome<Unit> = Outcome.Ok(Unit)

        override suspend fun renameConversation(ref: ConversationRef, title: String): Outcome<Unit> {
            renamed += ref.sessionId.raw to title
            return result
        }
    }

    @Test
    fun `renaming from the dock renames the conversation itself and keeps no second name`() = runTest(StandardTestDispatcher()) {
        val disk = FakeAttentionKeyStore()
        val actions = RenameActions(Outcome.Ok(Unit))
        val dock = DockController(this, DockNicknames(disk), {}, actions = actions)
        dock.feed(DockEvent.Meta(a, "Old title", null))
        dock.feed(DockEvent.RunStarted(a))
        val long = "Quarterly budget review for the finance team 2026"
        dock.rename(a, "  $long ")
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("a" to long), actions.renamed, "the whole title goes to the conversation, not a 32-letter nickname")
        val entry = dock.ui.value.entries.first { it.ref == a }
        assertEquals(long, entry.name)
        assertNull(entry.nickname)
        assertTrue(DockKeys.namesKey(p) !in disk.data)
    }

    @Test
    fun `a refused rename changes nothing and an old local nickname is dropped on load`() = runTest(StandardTestDispatcher()) {
        val disk = FakeAttentionKeyStore()
        DockNicknames(disk).set(a, "Quarterly budget review for the f")
        val actions = RenameActions(Outcome.Failed(Failure.Unavailable(FeatureId("x"))))
        val dock = DockController(this, DockNicknames(disk), {}, actions = actions)
        dock.loadNicknames(p)
        dock.feed(DockEvent.Meta(a, "Real title", null))
        dock.feed(DockEvent.RunStarted(a))
        assertEquals("Real title", dock.ui.value.entries.first { it.ref == a }.name, "the home's title shows, not the old nickname")
        assertTrue(DockKeys.namesKey(p) !in disk.data)
        var told = 0
        dock.rename(a, "New") { told++ }
        testScheduler.advanceUntilIdle()
        assertEquals("Real title", dock.ui.value.entries.first { it.ref == a }.name)
        assertEquals(1, told, "the person is told the rename did not happen")
        dock.rename(a, "   ") { told++ }
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("a" to "New"), actions.renamed, "a blank name is never sent")
        assertEquals(1, told)
    }
}
