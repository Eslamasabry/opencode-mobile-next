package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.engine.transcript.state.RunFacts
import io.github.eslamasabry.oc.feature.chat.presentation.ChatConfirm
import io.github.eslamasabry.oc.feature.chat.presentation.ChatLauncher
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.ChatStatusKind
import io.github.eslamasabry.oc.feature.chat.presentation.LiveTurnUi
import io.github.eslamasabry.oc.feature.chat.presentation.MessageAction
import io.github.eslamasabry.oc.feature.chat.presentation.StatusPick
import io.github.eslamasabry.oc.feature.chat.presentation.TargetKind
import io.github.eslamasabry.oc.feature.chat.world.ChatLoad
import io.github.eslamasabry.oc.feature.chat.world.PendingSend
import io.github.eslamasabry.oc.feature.chat.world.PendingState
import kotlinx.collections.immutable.persistentListOf
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The chat screen's state and intents (plan 09 §6, 07 §2.5 and §3.4) on a scripted world. */
class ChatPresenterTest {
    private val world = FakeChatWorld()
    private val nav = RecordingNavigator()

    private fun presenter(focusComposer: Boolean = false, landOnFailure: Boolean = false, launcher: ChatLauncher = Immediate) =
        ChatPresenter(world, nav, launcher, focusComposer, landOnFailure)

    // ---- Arrivals (07 §2.5) ----

    @Test
    fun `B-NAV-005 a chat opened to reply arrives ready to type, exactly once, and a plain open does not`() {
        val p = presenter(focusComposer = true)
        val arrival = assertNotNull(p.current().arrival)
        assertTrue(arrival.focusComposer)
        p.arrivalShown(arrival.id + 99) // someone else's id changes nothing
        assertNotNull(p.current().arrival)
        p.arrivalShown(arrival.id)
        assertNull(p.current().arrival, "consumed once; a recomposition never replays it")
        assertNull(presenter(focusComposer = false).current().arrival, "Open (not Reply) does not raise the keyboard")
    }

    @Test
    fun `09-6_7 landing on a failure brings the newest failed turn into view once`() {
        world.transcript.value = stateOf(*turn(1, failed = true).toTypedArray(), *turn(2).toTypedArray())
        val p = presenter(landOnFailure = true)
        val arrival = assertNotNull(p.current().arrival)
        assertFalse(arrival.focusComposer)
        assertEquals(world.transcript.value.indexOf("footer:u1"), arrival.landOnIndex)
        p.arrivalShown(arrival.id)
        assertNull(p.current().arrival)
    }

    // ---- The Back guard (07 §3.4) ----

    @Test
    fun `B-STATE-102 leaving with a draft that could not be saved asks first, and Leave without saving pops`() {
        val p = presenter()
        world.draftChanged("half a thought")
        assertTrue(p.current().guardBack, "the guard is on while there is something to lose")
        p.leaveRequested()
        assertEquals(listOf("saveDraft"), world.calls)
        assertEquals(ChatConfirm.LeaveUnsaved, p.current().confirm)
        assertTrue(nav.calls.isEmpty(), "nothing popped while the words are unsaved")
        p.dismissConfirm() // Keep editing
        assertNull(p.current().confirm)
        assertTrue(nav.calls.isEmpty())
        p.leaveRequested()
        p.confirmLeave()
        assertEquals(listOf("back"), nav.calls)
    }

    @Test
    fun `07-3_4 a draft that saves leaves at once, and an untouched composer never guards Back`() {
        val p = presenter()
        assertFalse(p.current().guardBack, "nothing to protect: the system preview plays")
        p.leaveRequested()
        assertEquals(listOf("back"), nav.calls)
        assertTrue(world.calls.isEmpty(), "no save without words")

        nav.calls.clear()
        world.saveResult = Outcome.Ok(Unit)
        world.draftChanged("keep me")
        p.leaveRequested()
        assertEquals(listOf("saveDraft"), world.calls)
        assertEquals(listOf("back"), nav.calls)
        assertNull(p.current().confirm)
    }

    @Test
    fun `07-3_4 a second Back while the draft is saving does not save or pop twice`() {
        var resume: Continuation<Outcome<Unit>>? = null
        val slow =
            object : io.github.eslamasabry.oc.feature.chat.world.ChatWorld by world {
                override suspend fun saveDraft(): Outcome<Unit> {
                    world.calls += "saveDraft"
                    return suspendCoroutine { resume = it }
                }
            }
        val p = ChatPresenter(slow, nav, Immediate)
        world.draftChanged("words")
        p.leaveRequested()
        p.leaveRequested()
        assertEquals(listOf("saveDraft"), world.calls)
        assertNotNull(resume).resumeWith(Result.success(Outcome.Ok(Unit)))
        assertEquals(listOf("back"), nav.calls)
    }

    @Test
    fun `07-3_3 Back closes the open menu and find before it ever leaves the chat`() {
        world.transcript.value = stateOf(*turn(1).toTypedArray())
        val p = presenter()
        p.find("answer")
        p.openMessageMenu("prompt:u1")
        assertTrue(p.current().guardBack)
        p.leaveRequested()
        assertNull(p.current().menu)
        assertNotNull(p.current().find)
        p.leaveRequested()
        assertNull(p.current().find)
        assertFalse(p.current().guardBack)
        p.leaveRequested()
        assertEquals(listOf("back"), nav.calls)
    }

    // ---- Message actions (plan 09 §6.6) ----

    @Test
    fun `09-6_6 delete asks first, deletes once on the server, and offers no undo`() {
        world.transcript.value = stateOf(*turn(1).toTypedArray(), *turn(2).toTypedArray())
        val p = presenter()
        p.openMessageMenu("footer:u2")
        val menu = assertNotNull(p.current().menu)
        assertEquals(TargetKind.Reply, menu.target.kind)
        assertEquals("a2", menu.target.messageId, "a reply's footer acts on its newest assistant message")
        assertEquals(MessageAction.Delete, menu.actions.last(), "destructive last")
        p.messageAction(menu.target, MessageAction.Delete)
        assertNull(p.current().menu)
        assertEquals(ChatConfirm.DeleteMessage(menu.target), p.current().confirm)
        assertTrue(world.calls.isEmpty(), "nothing deleted before the confirm")
        p.confirmDelete()
        p.confirmDelete() // a double tap
        assertEquals(listOf("delete a2"), world.calls)
        assertNull(p.current().confirm)
        assertNull(p.current().status, "the message leaving is the confirmation; no undo line")
    }

    @Test
    fun `09-6_6 a delete the server refuses says so on the status line, dismissible`() {
        world.transcript.value = stateOf(*turn(1).toTypedArray())
        world.deleteResult = Outcome.Failed(Failure.Network(NetworkWhy.Reset))
        val p = presenter()
        p.openMessageMenu("prompt:u1")
        p.messageAction(p.current().menu!!.target, MessageAction.Delete)
        p.confirmDelete()
        val line = assertNotNull(p.current().status)
        assertEquals(ChatStatusKind.ActionFailed, line.kind)
        assertEquals(Failure.Network(NetworkWhy.Reset), line.failure)
        p.dismissStatus(line.id)
        assertNull(p.current().status)
    }

    @Test
    fun `09-6_6 copy puts the prompt's own words or the whole reply on the clipboard once`() {
        world.transcript.value =
            stateOf(prompt("u1", "Fix **the** build"), block("u1", "a1", "p", 0, "Done."), block("u1", "a1", "p", 7, "All green."), footer("u1"))
        val p = presenter()
        p.openMessageMenu("prompt:u1")
        p.messageAction(p.current().menu!!.target, MessageAction.Copy)
        val clip = assertNotNull(p.current().clip)
        assertEquals("Fix **the** build", clip.text, "the person's words verbatim")
        p.clipDone(clip.id)
        assertNull(p.current().clip)
        p.openMessageMenu("footer:u1")
        p.messageAction(p.current().menu!!.target, MessageAction.Copy)
        assertEquals("Done.\n\nAll green.", p.current().clip?.text)
    }

    @Test
    fun `09-6_6 send again sends the prompt's words once, and fork opens the new conversation`() {
        world.transcript.value = stateOf(*turn(1, ask = "try again please").toTypedArray())
        val p = presenter()
        p.openMessageMenu("prompt:u1")
        val target = p.current().menu!!.target
        assertEquals(
            listOf(MessageAction.Copy, MessageAction.SendAgain, MessageAction.Fork, MessageAction.Delete),
            p.current().menu!!.actions,
        )
        p.messageAction(target, MessageAction.SendAgain)
        p.messageAction(target, MessageAction.Fork)
        assertEquals(listOf("send try again please", "fork u1"), world.calls)
        assertEquals(listOf("openChat s2"), nav.calls)
    }

    @Test
    fun `09-6_6 the menu follows the server's flags, never the backend kind`() {
        world.transcript.value = stateOf(*turn(1).toTypedArray())
        world.capabilities.value = ServerCapabilities(sessionFork = false, messageDelete = false)
        val p = presenter()
        p.openMessageMenu("prompt:u1")
        assertEquals(listOf(MessageAction.Copy, MessageAction.SendAgain), p.current().menu!!.actions)
        p.openMessageMenu("b:a1/p1:0") // a block is not a way into the menu
        assertEquals(TargetKind.Prompt, p.current().menu!!.target.kind)
        assertEquals(false, p.current().capabilities.fork)
    }

    // ---- Find (plan 09 §6.6) ----

    @Test
    fun `B-CHAT-115 find reports n of m, steps through hits and follows the transcript as it changes`() {
        world.transcript.value = stateOf(*turn(1, answer = "red").toTypedArray(), *turn(2, answer = "red again").toTypedArray())
        val p = presenter()
        p.find("red")
        var find = assertNotNull(p.current().find)
        assertEquals(2, find.count)
        assertEquals(0, find.current, "starts at the first hit in reading order (Flutter)")
        assertEquals(world.transcript.value.indexOf("b:a1/p1:0"), find.currentIndex)
        assertTrue(p.current().guardBack, "Back closes find first")
        p.findNext()
        assertEquals("b:a2/p2:0", p.current().find?.currentKey)
        // A new reply streams in with another hit; the current hit stays the current one.
        world.transcript.value = stateOf(*turn(1, answer = "red").toTypedArray(), *turn(2, answer = "red again").toTypedArray(), *turn(3, answer = "red").toTypedArray())
        find = p.current().find!!
        assertEquals(3, find.count)
        assertEquals(1, find.current)
        p.findNext()
        p.findNext()
        assertEquals(0, p.current().find?.current, "wraps")
        p.findPrevious()
        assertEquals(2, p.current().find?.current)
        p.find(null)
        assertNull(p.current().find)
    }

    @Test
    fun `09-7_3 the earlier-messages pill opens find while the timeline sheet is not built`() {
        val p = presenter()
        p.openTimeline()
        assertEquals("", p.current().find?.query)
    }

    // ---- The live line, stop and sends ----

    @Test
    fun `09-4_4 stop asks the server once, the live line says stopping, and a failed stop is reported`() {
        world.transcript.value = stateOf(*turn(1).toTypedArray(), run = RunFacts(busy = true))
        var resume: Continuation<Unit>? = null
        val parked = ChatLauncher { block -> block.startCoroutine(Continuation(EmptyCoroutineContext) { it.getOrThrow() }) }
        val p = ChatPresenter(world, nav, { block -> parked.launch { suspendCoroutine<Unit> { resume = it }; block() } })
        assertEquals(LiveTurnUi(stopping = false), p.current().live)
        p.stop()
        p.stop()
        assertEquals(LiveTurnUi(stopping = true), p.current().live)
        world.stopResult = Outcome.Failed(Failure.TimedOut())
        assertNotNull(resume).resumeWith(Result.success(Unit))
        assertEquals(listOf("stop"), world.calls)
        assertEquals(LiveTurnUi(stopping = false), p.current().live)
        assertEquals(ChatStatusKind.ActionFailed, p.current().status?.kind)
        world.transcript.value = stateOf(*turn(1).toTypedArray())
        assertNull(p.current().live, "no live line once the turn ended")
    }

    @Test
    fun `09-4_6 pending bubbles show newest first and Try again resends only on the person's tap`() {
        world.pending.value = persistentListOf(PendingSend(1, "first", PendingState.Unconfirmed), PendingSend(2, "second", PendingState.Failed))
        val p = presenter()
        assertEquals(listOf(2L, 1L), p.current().pending.map { it.localId })
        assertEquals(ChatStatusKind.SendFailed, p.current().status?.kind)
        assertTrue(world.calls.isEmpty(), "never resent on its own")
        p.retrySend(2)
        assertEquals(listOf("retrySend 2"), world.calls)
    }

    // ---- Load phases (plan 09 §6.5) ----

    @Test
    fun `09-6_5 loading, failed and live phases come from the world, and Try again reloads`() {
        val p = presenter()
        world.load.value = ChatLoad.Loading
        assertEquals(ChatPhase.Loading, p.current().phase)
        world.load.value = ChatLoad.Failed(Failure.Network(NetworkWhy.Offline))
        assertEquals(ChatPhase.Failed(Failure.Network(NetworkWhy.Offline)), p.current().phase)
        p.retryLoad()
        assertEquals(listOf("reload"), world.calls)
        world.load.value = ChatLoad.Live
        assertEquals(ChatPhase.Live, p.current().phase)
        assertEquals("Fix the build", p.current().title)
    }

    @Test
    fun `09-6_1 rows pass through untouched, by reference`() {
        val s = stateOf(*turn(1).toTypedArray())
        world.transcript.value = s
        val ui = presenter().current()
        assertSame(s.rows, ui.rows)
        assertEquals("footer:u1", ui.newestRowKey)
    }

    // ---- Reading (plan 09 §6.7) ----

    @Test
    fun `B-CHAT-012 the chat is acknowledged read when it is read, not while loading or scrolled away`() {
        world.load.value = ChatLoad.Loading
        world.transcript.value = stateOf(*turn(1).toTypedArray(), run = RunFacts(busy = true))
        val p = presenter()
        p.syncRead()
        assertEquals(0, world.reads, "never while loading")
        world.load.value = ChatLoad.Live
        p.syncRead()
        assertEquals(1, world.reads, "loaded at the live edge")
        p.syncRead()
        assertEquals(1, world.reads, "once per arrival at the edge, not per publish")
        p.liveEdgeChanged(false)
        world.transcript.value = stateOf(*turn(1).toTypedArray(), *turn(2).toTypedArray())
        p.syncRead()
        assertEquals(1, world.reads, "scrolled up: a finished reply is not read yet")
        p.liveEdgeChanged(true)
        assertEquals(2, world.reads, "back at the edge")
        world.transcript.value = stateOf(*turn(1).toTypedArray(), run = RunFacts(busy = true))
        p.syncRead()
        world.transcript.value = stateOf(*turn(1).toTypedArray(), *turn(2).toTypedArray())
        p.syncRead()
        assertEquals(3, world.reads, "a reply that ends while it is being read is read")
    }

    @Test
    fun `09-6_5 the status line's id is stable per condition`() {
        world.transcript.value = stateOf(*turn(1).toTypedArray())
        world.deleteResult = Outcome.Failed(Failure.Unknown())
        val p = presenter()
        p.openMessageMenu("prompt:u1")
        p.messageAction(p.current().menu!!.target, MessageAction.Delete)
        p.confirmDelete()
        assertEquals(StatusPick.ACTION, p.current().status?.id)
    }
}
