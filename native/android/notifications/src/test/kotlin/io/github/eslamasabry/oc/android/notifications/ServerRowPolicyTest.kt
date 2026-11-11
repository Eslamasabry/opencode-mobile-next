package io.github.eslamasabry.oc.android.notifications

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** The phone server's row never claims "nothing running" while a conversation works or waits for the person. */
class ServerRowPolicyTest {
    private fun step(
        shown: Boolean,
        lent: Boolean = false,
        watch: Boolean = false,
        live: Boolean = false,
        visible: Boolean = false,
    ) = ServerRowPolicy.step(shown, lent, watch, live, visible)

    @Test
    fun `a run waiting for the person after the watch ended puts the conversations' words in the server row`() {
        assertEquals(ServerRowStep.Lend, step(shown = true))
    }

    @Test
    fun `a run in progress puts them there too, and nothing running leaves the server's own words`() {
        assertEquals(ServerRowStep.Lend, step(shown = true, lent = true))
        assertEquals(ServerRowStep.Leave, step(shown = false))
    }

    @Test
    fun `after the answer, when nothing runs or waits, the server's own row comes back once`() {
        assertEquals(ServerRowStep.Restore, step(shown = false, lent = true))
        assertEquals(ServerRowStep.Leave, step(shown = false, lent = false))
    }

    @Test
    fun `the reply watch and live mode write the row themselves, so nothing is lent beside them`() {
        assertEquals(ServerRowStep.Leave, step(shown = true, watch = true))
        assertEquals(ServerRowStep.Leave, step(shown = true, live = true, lent = true))
    }

    @Test
    fun `coming back to the app takes a lent row away again`() {
        assertEquals(ServerRowStep.Restore, step(shown = true, lent = true, visible = true))
        assertEquals(ServerRowStep.Leave, step(shown = true, visible = true))
    }

    // ---- the lender: the one writer of the server's row outside live mode and reply watch

    private val chat = ConversationRef(ProfileId("p1"), SessionId("ses_1"))
    private val waiting =
        StatusContent(StatusKind.NeedsYou, "Fix login needs you", "Waiting for your answer", conversation = chat, turnId = "dock:1")
    private val working =
        StatusContent(StatusKind.Working, "Working on Fix login", actions = listOf(StatusAction.Open, StatusAction.StopReply), conversation = chat, turnId = "dock:0")

    /** The row as the shade would show it: what the lender posted, or the server's own "Ready". */
    private class Shade {
        var conversations: StatusContent? = null
        var watch = false
        var live = false
        var visible = false
        var serverHolds = true
        val posts = mutableListOf<StatusContent>()
        var restores = 0
        val row: String? get() = shown
        var shown: String? = "Ready"
    }

    private fun TestScope.lender(shade: Shade) =
        ServerRowLender(
            scope = backgroundScope,
            elapsedMs = { testScheduler.currentTime },
            facts = { ServerRowFacts(shade.conversations, shade.watch, shade.live, shade.visible) },
            serverHoldsRow = { shade.serverHolds },
            post = { shade.posts += it; shade.shown = it.title },
            restore = {
                if (shade.serverHolds) {
                    shade.restores++
                    shade.shown = "Ready"
                }
                shade.serverHolds
            },
        )

    @Test
    fun `a waiting row keeps Stop OpenCode, a working row does not get it`() {
        assertEquals(listOf(StatusAction.Open, StatusAction.StopServer), ServerRowPolicy.lentWords(waiting).actions)
        assertEquals(working, ServerRowPolicy.lentWords(working))
        val waitingWithStop = waiting.copy(actions = listOf(StatusAction.Open, StatusAction.StopReply))
        assertEquals(
            listOf(StatusAction.Open, StatusAction.StopReply, StatusAction.StopServer),
            ServerRowPolicy.lentWords(waitingWithStop).actions,
        )
    }

    @Test
    fun `unchanged words post nothing new for an hour, and the row says the chat waits the whole time`() = runTest {
        val shade = Shade().apply { conversations = waiting }
        val lender = lender(shade)
        lender.settle()
        repeat(60) {
            advanceTimeBy(60_000)
            lender.settle()
        }
        assertEquals(1, shade.posts.size)
        assertEquals("Fix login needs you", shade.row)
        assertEquals(0, shade.restores)
    }

    @Test
    fun `two writers - a delayed post never lands while reply watch writes the row`() = runTest {
        val shade = Shade().apply { conversations = waiting }
        val lender = lender(shade)
        lender.settle()
        advanceTimeBy(100)
        // The person answered: the run works again within the throttle gap, so a trailing post is pending ...
        shade.conversations = working
        lender.settle()
        // ... and reply watch starts (the rearm) and writes "Working" itself before the gap is over.
        shade.watch = true
        lender.settle()
        shade.shown = "Still working on your reply"
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(1, shade.posts.size)
        assertEquals("Still working on your reply", shade.row)
        assertEquals(0, shade.restores)
    }

    @Test
    fun `two writers - the watch's end hands the row back through the same decision, once, with no Ready flash`() = runTest {
        val shade = Shade().apply { conversations = waiting; watch = true }
        val lender = lender(shade)
        lender.settle()
        assertTrue(shade.posts.isEmpty())
        // The watch ends 10 s after the request alert (flag still set at that moment): the waiting words take over.
        shade.shown = "Fix login needs you"
        assertTrue(lender.settle(watchEnding = true))
        assertEquals(listOf(StatusAction.Open, StatusAction.StopServer), shade.posts.single().actions)
        assertEquals(0, shade.restores)
        // The flag then clears: same words, nothing new.
        shade.watch = false
        lender.settle()
        assertEquals(1, shade.posts.size)
        // A watch ending with nothing running or waiting gives the server its own row back.
        val idle = Shade().apply { watch = true }
        val second = lender(idle)
        second.settle()
        assertTrue(second.settle(watchEnding = true))
        assertEquals(1, idle.restores)
        assertTrue(idle.posts.isEmpty())
    }

    @Test
    fun `after the answer the run works, then ends, and the server's own row comes back exactly once`() = runTest {
        val shade = Shade().apply { conversations = waiting }
        val lender = lender(shade)
        lender.settle()
        advanceTimeBy(2_000)
        shade.conversations = working
        lender.settle()
        assertEquals("Working on Fix login", shade.row)
        advanceTimeBy(2_000)
        shade.conversations = null
        lender.settle()
        lender.settle()
        assertEquals("Ready", shade.row)
        assertEquals(1, shade.restores)
        assertFalse(lender.lending)
    }

    @Test
    fun `stale words cannot stay - nothing is lent once the server's service let go of the row`() = runTest {
        val shade = Shade().apply { conversations = waiting }
        val lender = lender(shade)
        lender.settle()
        advanceTimeBy(100)
        shade.conversations = working
        lender.settle()
        // Stop OpenCode: the server's service ends and takes its row (and the lent words) with it.
        shade.serverHolds = false
        shade.shown = null
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(1, shade.posts.size)
        assertEquals(null, shade.row)
        assertFalse(lender.lending)
        // A watch handing back with no server holding the row learns it, so it takes its own row away.
        assertFalse(lender.settle(watchEnding = true))
        // No later change brings lent words back without the server.
        shade.conversations = waiting
        assertFalse(lender.settle())
        assertEquals(1, shade.posts.size)
    }

    @Test
    fun `coming back to the app gives the server its own row back once, live mode is never written over`() = runTest {
        val shade = Shade().apply { conversations = waiting }
        val lender = lender(shade)
        lender.settle()
        shade.visible = true
        lender.settle()
        lender.settle()
        assertEquals(1, shade.restores)
        assertEquals("Ready", shade.row)
        shade.visible = false
        shade.live = true
        shade.shown = "Connected"
        lender.settle()
        advanceTimeBy(5_000)
        assertEquals("Connected", shade.row)
        assertEquals(1, shade.posts.size)
        assertEquals(1, shade.restores)
    }
}
