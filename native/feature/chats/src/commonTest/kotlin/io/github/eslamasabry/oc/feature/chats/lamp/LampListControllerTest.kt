package io.github.eslamasabry.oc.feature.chats.lamp

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.attention.dock.DockListHidden
import io.github.eslamasabry.oc.engine.attention.dock.DockListModel
import io.github.eslamasabry.oc.engine.attention.dock.DockListState.NeedsYou
import io.github.eslamasabry.oc.engine.attention.dock.DockListState.Working
import io.github.eslamasabry.oc.engine.attention.dock.DockListState.Idle
import io.github.eslamasabry.oc.feature.chats.presentation.HeldAnswers
import io.github.eslamasabry.oc.feature.chats.presentation.HomeHeldReply
import io.github.eslamasabry.oc.feature.chats.presentation.HomePermissionReply
import io.github.eslamasabry.oc.feature.chats.presentation.HomeStop
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** The list's acts (slice M1): each does what it says, holds live in the shared stores, Undo takes them back. */
@OptIn(ExperimentalCoroutinesApi::class)
class LampListControllerTest {
    private class Nav : AppNavigator {
        val switches = mutableListOf<Pair<ConversationRef, String?>>()
        override val top: StateFlow<AppRoute?> = MutableStateFlow(null)

        override fun open(route: AppRoute) {}

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) {
            error("the light never uses openChat")
        }

        override fun land() {}

        override fun replaceRoot(root: AppRoute) {}

        override fun back() {}

        override fun switchTo(target: ConversationRef, request: String?) {
            switches += target to request
        }
    }

    private class Port : LampPort {
        val calls = mutableListOf<String>()
        var renameFails = false
        var lastPlace: LampPlace? = null

        override fun pin(ref: ConversationRef) { calls += "pin ${ref.sessionId.raw}" }

        override fun unpin(ref: ConversationRef) { calls += "unpin ${ref.sessionId.raw}" }

        override fun reorder(from: ConversationRef, to: ConversationRef) { calls += "reorder ${from.sessionId.raw}>${to.sessionId.raw}" }

        override fun rename(ref: ConversationRef, name: String, onFailed: () -> Unit) {
            calls += "rename ${ref.sessionId.raw}=$name"
            if (renameFails) onFailed()
        }

        override fun hide(ref: ConversationRef) { calls += "hide ${ref.sessionId.raw}" }

        override fun restore(ref: ConversationRef) { calls += "restore ${ref.sessionId.raw}" }

        override fun newConversation() { calls += "new" }

        override fun allConversations() { calls += "all" }

        override fun place(place: LampPlace) { lastPlace = place; calls += "place" }

        override fun turnOff() { calls += "off" }
    }

    private class World(val scope: TestScope, val panel: CoroutineScope, model: DockListModel) {
        val list = MutableStateFlow(model)
        val nav = Nav()
        val port = Port()
        val sent = mutableListOf<String>()
        val stopped = mutableListOf<ConversationRef>()
        var stopResult: Outcome<Unit> = Unit.ok()
        val place = MutableStateFlow(LampPlace())
        val held = HeldAnswers(scope.backgroundScope) { ref, id, reply -> sent += "${ref.sessionId.raw}/$id/$reply" }
        val stops = HeldStops(scope.backgroundScope, HomeStop { ref -> stopped += ref; stopResult })
        val clock = object : Clock { override fun now(): Instant = Instant.fromEpochMilliseconds(10_000_000) }
        val lamp = LampListController(panel, list, nav, held, stops, port, clock, place)
    }

    private fun TestScope.world(vararg rows: io.github.eslamasabry.oc.engine.attention.dock.DockListRow): World =
        World(this, CoroutineScope(coroutineContext + Job()), lmodel(*rows))

    @Test
    fun `B-LAMP-M1 a tap on a row switches to it, Answer opens it at its request`() = runTest(UnconfinedTestDispatcher()) {
        val w = world(lrow("a", NeedsYou, request = question("q7")))
        w.lamp.open(lref("a"))
        w.lamp.answer(lref("a"), "q7")
        assertEquals(listOf(lref("a") to null, lref("a") to "q7"), w.nav.switches)
    }

    @Test
    fun `B-LAMP-M1 Allow once is held three seconds with its receipt, then sent once`() = runTest(UnconfinedTestDispatcher()) {
        val w = world(lrow("a", NeedsYou, request = perm("r1")))
        w.lamp.allowOnce(lref("a"), "r1")
        assertEquals(LampAnswerUi.Held(HomeHeldReply.Allowed), w.lamp.ui.value.row("a").request!!.answer)
        // a second tap on the same request changes nothing
        w.lamp.deny(lref("a"), "r1")
        assertEquals(LampAnswerUi.Held(HomeHeldReply.Allowed), w.lamp.ui.value.row("a").request!!.answer)
        advanceTimeBy(2_900)
        assertTrue(w.sent.isEmpty(), "still inside the window")
        advanceTimeBy(200)
        runCurrent()
        assertEquals(listOf("a/r1/${HomePermissionReply.Once}"), w.sent)
        assertEquals(LampAnswerUi.AllowDeny, w.lamp.ui.value.row("a").request!!.answer)
    }

    @Test
    fun `B-LAMP-M1 Deny is held the same way and Undo sends nothing`() = runTest(UnconfinedTestDispatcher()) {
        val w = world(lrow("a", NeedsYou, request = perm("r1")))
        w.lamp.deny(lref("a"), "r1")
        assertEquals(LampAnswerUi.Held(HomeHeldReply.Rejected), w.lamp.ui.value.row("a").request!!.answer)
        advanceTimeBy(1_000)
        w.lamp.undoAnswer("r1")
        assertEquals(LampAnswerUi.AllowDeny, w.lamp.ui.value.row("a").request!!.answer)
        advanceTimeBy(10_000)
        runCurrent()
        assertTrue(w.sent.isEmpty())
    }

    @Test
    fun `B-LAMP-M1 closing the panel does not cancel a hold, the store still sends it once`() = runTest(UnconfinedTestDispatcher()) {
        val w = world(lrow("a", NeedsYou, request = perm("r1")), lrow("b", Working))
        w.lamp.allowOnce(lref("a"), "r1")
        w.lamp.stop(lref("b"))
        w.panel.coroutineContext[Job]!!.cancel() // the panel closes
        advanceTimeBy(3_100)
        runCurrent()
        assertEquals(listOf("a/r1/${HomePermissionReply.Once}"), w.sent)
        assertEquals(listOf(lref("b")), w.stopped)
    }

    @Test
    fun `B-LAMP-M1 Stop holds three seconds saying Stopping, then stops once and says Stopped`() = runTest(UnconfinedTestDispatcher()) {
        val w = world(lrow("a", Working))
        assertEquals(LampStopUi.Button, w.lamp.ui.value.row("a").stop)
        w.lamp.stop(lref("a"))
        w.lamp.stop(lref("a")) // a second tap is the same hold
        assertEquals(LampStopUi.Holding, w.lamp.ui.value.row("a").stop)
        advanceTimeBy(2_900)
        assertTrue(w.stopped.isEmpty())
        advanceTimeBy(200)
        runCurrent()
        assertEquals(listOf(lref("a")), w.stopped)
        assertEquals(LampStopUi.Stopped, w.lamp.ui.value.row("a").stop)
        advanceTimeBy(HeldStops.STOPPED_SHOWN_MS + 100)
        runCurrent()
        assertEquals(LampStopUi.Button, w.lamp.ui.value.row("a").stop)
        assertEquals(1, w.stopped.size)
    }

    @Test
    fun `B-LAMP-M1 Undo during the hold stops nothing and brings the stop square back`() = runTest(UnconfinedTestDispatcher()) {
        val w = world(lrow("a", Working))
        w.lamp.stop(lref("a"))
        advanceTimeBy(1_000)
        w.lamp.undoStop(lref("a"))
        assertEquals(LampStopUi.Button, w.lamp.ui.value.row("a").stop)
        advanceTimeBy(10_000)
        runCurrent()
        assertTrue(w.stopped.isEmpty())
    }

    @Test
    fun `B-LAMP-M1 a stop the server did not take says so and the square comes back`() = runTest(UnconfinedTestDispatcher()) {
        val w = world(lrow("a", Working, name = "Build"))
        w.stopResult = Outcome.Failed(Failure.Unavailable(FeatureId("stop")))
        w.lamp.stop(lref("a"))
        advanceTimeBy(3_100)
        runCurrent()
        assertEquals(LampNotice.StopFailed(lref("a"), "Build"), w.lamp.ui.value.notice)
        assertEquals(LampStopUi.Button, w.lamp.ui.value.row("a").stop)
    }

    @Test
    fun `B-LAMP-M1 a sub-agent stops by its own ref`() = runTest(UnconfinedTestDispatcher()) {
        val w = world(lrow("p", Working, kids = listOf(kid("k", Working))))
        assertEquals(LampStopUi.Button, w.lamp.ui.value.row("p").tree!!.children.single().stop)
        w.lamp.stop(lref("k"))
        assertEquals(LampStopUi.Holding, w.lamp.ui.value.row("p").tree!!.children.single().stop)
        advanceTimeBy(3_100)
        runCurrent()
        assertEquals(listOf(lref("k")), w.stopped)
    }

    @Test
    fun `B-LAMP-M1 edit mode toggles, pin and unpin go to the one pin store, reorder and rename too`() = runTest(UnconfinedTestDispatcher()) {
        val w = world(lrow("a", Idle, pinned = true), lrow("b", Idle, pinned = true), lrow("c", Idle, pinned = true), lrow("d", Idle))
        assertEquals(false, w.lamp.ui.value.editing)
        w.lamp.edit()
        assertTrue(w.lamp.ui.value.editing)
        assertEquals(true, w.lamp.ui.value.row("a").edit!!.pinned)
        w.lamp.pin(lref("d"))
        w.lamp.unpin(lref("a"))
        w.lamp.reorder(lref("c"), lref("a"))
        w.lamp.moveDown(lref("a"))
        w.lamp.moveUp(lref("c"))
        w.lamp.moveUp(lref("a")) // first: nothing
        w.lamp.moveDown(lref("c")) // last: nothing
        w.lamp.rename(lref("d"), "  New name ")
        w.lamp.rename(lref("d"), "   ")
        assertEquals(listOf("pin d", "unpin a", "reorder c>a", "reorder a>b", "reorder c>b", "rename d=New name"), w.port.calls)
        w.lamp.done()
        assertEquals(false, w.lamp.ui.value.editing)
    }

    @Test
    fun `B-LAMP-M1 a rename the server refused is told`() = runTest(UnconfinedTestDispatcher()) {
        val w = world(lrow("d", Idle, name = "Old"))
        w.port.renameFails = true
        w.lamp.rename(lref("d"), "New")
        assertEquals(LampNotice.RenameFailed(lref("d"), "Old"), w.lamp.ui.value.notice)
        w.lamp.dismissNotice()
        assertNull(w.lamp.ui.value.notice)
    }

    @Test
    fun `B-LAMP-M1 hide offers Undo for eight seconds, Undo restores it`() = runTest(UnconfinedTestDispatcher()) {
        val w = world(lrow("d", Idle, name = "Docs"))
        w.lamp.hide(lref("d"))
        assertEquals(LampNotice.Hidden(lref("d"), "Docs"), w.lamp.ui.value.notice)
        advanceTimeBy(7_900)
        assertEquals(LampNotice.Hidden(lref("d"), "Docs"), w.lamp.ui.value.notice)
        w.lamp.restore(lref("d"))
        assertNull(w.lamp.ui.value.notice)
        assertEquals(listOf("hide d", "restore d"), w.port.calls)
        w.lamp.hide(lref("d"))
        advanceTimeBy(8_100)
        runCurrent()
        assertNull(w.lamp.ui.value.notice, "the offer ends after eight seconds")
    }

    @Test
    fun `B-LAMP-M1 Hidden - n - Show again brings back one or all`() = runTest(UnconfinedTestDispatcher()) {
        val hidden = listOf(DockListHidden(lref("h1"), "One", Working, null), DockListHidden(lref("h2"), "Two", Idle, null))
        val w = world(lrow("a"))
        w.list.value = lmodel(lrow("a"), hidden = hidden)
        assertEquals(2, w.lamp.ui.value.hidden!!.count)
        w.lamp.restore(lref("h1"))
        w.lamp.restoreAll()
        assertEquals(listOf("restore h1", "restore h1", "restore h2"), w.port.calls)
    }

    @Test
    fun `B-LAMP-M1 New conversation, All conversations, Move and Turn off`() = runTest(UnconfinedTestDispatcher()) {
        val w = world(lrow("a"))
        w.lamp.newConversation()
        w.lamp.allConversations()
        w.lamp.move(LampPlace(LampSide.Start, LampHeight.High))
        w.lamp.turnOff()
        assertEquals(listOf("new", "all", "place", "off"), w.port.calls)
        assertEquals(LampPlace(LampSide.Start, LampHeight.High), w.port.lastPlace)
        assertEquals(LampNotice.TurnedOff, w.lamp.ui.value.notice)
        w.place.value = LampPlace(LampSide.Start, LampHeight.High)
        assertEquals(LampPlace(LampSide.Start, LampHeight.High), w.lamp.ui.value.place)
    }

    @Test
    fun `B-LAMP-M1 the panel remembers a fold while it lives and folds a block that finished`() = runTest(UnconfinedTestDispatcher()) {
        val w = world(lrow("p", Working, kids = listOf(kid("a", Working), kid("b", Working))))
        assertEquals(false, w.lamp.ui.value.row("p").tree!!.folded)
        w.lamp.setFolded(lref("p"), true)
        assertTrue(w.lamp.ui.value.row("p").tree!!.folded)
        w.list.value = lmodel(lrow("p", Working, kids = listOf(kid("a", Idle), kid("b", Idle))))
        assertTrue(w.lamp.ui.value.row("p").tree!!.folded)
        w.lamp.setFolded(lref("p"), false)
        assertEquals(false, w.lamp.ui.value.row("p").tree!!.folded)
        // two ran at once: it is parallel from then on
        assertEquals(LampTreeKind.Parallel, w.lamp.ui.value.row("p").tree!!.kind)
    }

    @Test
    fun `B-LAMP-M1 the face follows the list as it changes`() = runTest(UnconfinedTestDispatcher()) {
        val w = world(lrow("a", Idle))
        assertTrue(w.lamp.ui.value.face.quiet)
        w.list.value = lmodel(lrow("a", NeedsYou, request = perm()))
        assertEquals(1, w.lamp.ui.value.face.number)
        assertEquals(1, w.lamp.ui.value.face.needs)
    }
}
