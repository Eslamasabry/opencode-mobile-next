package io.github.eslamasabry.oc.engine.attention.dock

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.model.transcript.MessageInfo
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.engine.attention.activity.FakeAttentionKeyStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

class DockTuckAndLeaveTest {
    private val p = ProfileId("p1")
    private fun ref(n: String) = ConversationRef(p, SessionId(n))
    private val a = ref("a")
    private val b = ref("b")
    private val c = ref("c")

    private fun model(vararg events: DockEvent): DockModel = events.fold(DockModel.empty()) { m, e -> m.reduce(e) }

    // ---- the dock never comes out by itself

    @Test
    fun `tucked by default, two live and an alert elsewhere keep it tucked, the person pulls it out`() {
        val busy = model(DockEvent.RunStarted(a), DockEvent.RunStarted(b), DockEvent.RequestOpened(b, "permission:r"))
        assertEquals(DockPresence.Nub, busy.ui.presence)
        assertTrue(busy.ui.tucked)
        // The alert is still carried: the ring's state (the tucked form draws one dot per ring in its state colour).
        assertEquals(DockState.NeedsYou, busy.ui.entries.first { it.ref == b }.state)
        assertEquals(1, busy.ui.entries.first { it.ref == b }.pulse)
        val out = busy.reduce(DockEvent.Untuck)
        assertEquals(DockPresence.Out, out.ui.presence)
        assertEquals(DockPresence.Out, out.reduce(DockEvent.RunEnded(a, DockRunEnd.Failed)).ui.presence, "stays where the person put it")
    }

    @Test
    fun `entering a conversation and sending tuck it, the open conversation's own request changes nothing`() {
        val out = model(DockEvent.RunStarted(a), DockEvent.RunStarted(b), DockEvent.Untuck)
        assertEquals(DockPresence.Out, out.ui.presence)
        assertEquals(DockPresence.Nub, out.reduce(DockEvent.Opened(c)).ui.presence, "entering a conversation")
        assertEquals(DockPresence.Out, out.reduce(DockEvent.Opened(null)).ui.presence, "going back to the list does not")
        val inChat = out.reduce(DockEvent.Opened(a))
        assertEquals(DockPresence.Nub, inChat.ui.presence)
        val pulled = inChat.reduce(DockEvent.Untuck)
        assertEquals(DockPresence.Out, pulled.ui.presence)
        assertEquals(DockPresence.Out, pulled.reduce(DockEvent.RequestOpened(a, "permission:r")).ui.presence, "the open one's request")
        assertEquals(0, pulled.reduce(DockEvent.RequestOpened(a, "permission:r")).ui.entries.first { it.ref == a }.pulse)
        assertEquals(DockPresence.Out, pulled.reduce(DockEvent.Sent(b)).ui.presence, "a send elsewhere")
        assertEquals(DockPresence.Nub, pulled.reduce(DockEvent.Sent(a)).ui.presence, "sending in the open one")
    }

    @Test
    fun `a message of the person is a send, an assistant message is not`() {
        val user = MessageInfo(id = "m", sessionId = "a", role = Role.User)
        assertEquals(listOf<DockEvent>(DockEvent.Sent(a)), DockFeed.events(p, GatewayEvent.MessageUpserted(user)))
        val reply = MessageInfo(id = "m2", sessionId = "a", role = Role.Assistant)
        assertTrue(DockFeed.events(p, GatewayEvent.MessageUpserted(reply)).none { it is DockEvent.Sent })
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
