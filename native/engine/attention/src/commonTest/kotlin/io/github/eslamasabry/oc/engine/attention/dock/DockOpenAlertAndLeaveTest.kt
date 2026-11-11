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

    // ---- the open conversation is not an alert

    @Test
    fun `the open conversation's own request or failure does not bring the dock out, another one's does`() {
        val own = model(DockEvent.Opened(a), DockEvent.RunStarted(a), DockEvent.RequestOpened(a, "permission:r"))
        assertEquals(DockPresence.Nub, own.ui.presence, "the person is looking at it")
        assertEquals(0, own.ui.entries.first { it.ref == a }.pulse)
        assertEquals(DockPresence.Nub, model(DockEvent.Opened(a), DockEvent.RunStarted(a), DockEvent.RunEnded(a, DockRunEnd.Failed)).ui.presence)
        // The same request counts once the person is on the home or on another conversation (today's rule).
        assertEquals(DockPresence.Out, own.reduce(DockEvent.Opened(null)).ui.presence)
        assertEquals(DockPresence.Out, own.reduce(DockEvent.Opened(b)).ui.presence)
        // An alert elsewhere still brings it out while a conversation is open.
        val elsewhere = model(DockEvent.Opened(a), DockEvent.RunStarted(b), DockEvent.RequestOpened(b, "permission:r"))
        assertEquals(DockPresence.Out, elsewhere.ui.presence)
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
        dock.rename(a, "New")
        testScheduler.advanceUntilIdle()
        assertEquals("Real title", dock.ui.value.entries.first { it.ref == a }.name)
    }
}
