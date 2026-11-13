package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.feature.chat.arrival.BackNoteCount
import io.github.eslamasabry.oc.feature.chat.arrival.ChatArrivals
import io.github.eslamasabry.oc.feature.chat.presentation.ChatSwitchFeed
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.RequestFocus
import io.github.eslamasabry.oc.ui.routes.RequestFocusSignal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/**
 * B-LAMP-C1: what the edge light hands a conversation's page: the request to show (a fresh page and a kept one), the
 * arrival after a switch (the "Reply in" hint, the Back note three times, then never) and the draft flush.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatSwitchFeedTest {
    private val a = ConversationRef(ProfileId("p"), SessionId("a"))
    private val b = ConversationRef(ProfileId("p"), SessionId("b"))

    private class SignalNav : AppNavigator {
        val signal = RequestFocusSignal()
        val consumed = mutableListOf<Long>()
        override val top: StateFlow<AppRoute?> = MutableStateFlow(null)
        override val requestFocus: StateFlow<RequestFocus?> get() = signal.state

        override fun consumeRequestFocus(seq: Long) {
            consumed += seq
            signal.consume(seq)
        }

        override fun open(route: AppRoute) = Unit

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) = Unit

        override fun land() = Unit

        override fun replaceRoot(root: AppRoute) = Unit

        override fun back() = Unit
    }

    private class Count : BackNoteCount {
        var n = 0

        override fun shown(): Int = n

        override fun recordShown() {
            n += 1
        }
    }

    private fun kotlinx.coroutines.test.TestScope.feed(
        ref: ConversationRef,
        nav: SignalNav,
        arrivals: ChatArrivals = ChatArrivals(emptySet()),
        saved: MutableList<String> = mutableListOf(),
    ): ChatSwitchFeed {
        val feed = ChatSwitchFeed(ref, nav, arrivals, backgroundScope, saveDraft = { saved += "saved" })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { feed.requestFocus.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { feed.arrival.collect {} }
        testScheduler.runCurrent()
        return feed
    }

    @Test
    fun `B-LAMP-C1-001 requestFocus_freshEntry_receivesAfterEmit`() = runTest {
        val nav = SignalNav()
        val sent = nav.signal.emit(b, "req-1") // the switch asked before the page for b exists
        testScheduler.runCurrent()
        val page = feed(b, nav)
        assertEquals(sent, page.requestFocus.value)
    }

    @Test
    fun `B-LAMP-C1-001 requestFocus_keptEntry_receivesOnReturn`() = runTest {
        val nav = SignalNav()
        val page = feed(b, nav) // already open (kept), composed again later
        assertNull(page.requestFocus.value)
        val sent = nav.signal.emit(b, "req-2")
        testScheduler.runCurrent()
        assertEquals(sent, page.requestFocus.value)
    }

    @Test
    fun `B-LAMP-C1-001 requestFocus_onlyForItsOwnConversation`() = runTest {
        val nav = SignalNav()
        val other = feed(a, nav)
        val mine = feed(b, nav)
        nav.signal.emit(b, "req-3")
        testScheduler.runCurrent()
        assertNull(other.requestFocus.value, "a's page ignores b's request")
        assertNotNull(mine.requestFocus.value)
    }

    @Test
    fun `B-LAMP-C1-001 requestFocus_consumedOnce`() = runTest {
        val nav = SignalNav()
        val page = feed(b, nav)
        val sent = nav.signal.emit(b, "req-4")
        testScheduler.runCurrent()
        page.requestShown(sent.seq)
        testScheduler.runCurrent()
        assertEquals(listOf(sent.seq), nav.consumed)
        assertNull(page.requestFocus.value, "shown once, not offered again")
        assertNull(feed(b, nav).requestFocus.value, "a page composed later does not jump to it")
    }

    @Test
    fun `B-LAMP-C1-001 replyIn_hintStandsUntilTyped`() = runTest {
        val nav = SignalNav()
        val arrivals = ChatArrivals(emptySet())
        val page = feed(b, nav, arrivals)
        assertNull(page.arrival.value)
        val arrival = arrivals.switched(b, leftTitle = "Fix login")
        testScheduler.runCurrent()
        assertTrue(page.arrival.value!!.hint)
        assertEquals(arrival.seq, page.arrival.value!!.seq)
        page.hintTyped(arrival.seq)
        testScheduler.runCurrent()
        assertFalse(page.arrival.value!!.hint, "the usual hint is back once the person types")
        assertNull(feed(a, nav, arrivals).arrival.value, "the conversation left has no arrival")
    }

    @Test
    fun `B-LAMP-C1-001 backNote_firstThreeSwitchesThenNever`() = runTest {
        val nav = SignalNav()
        val count = Count()
        val arrivals = ChatArrivals(setOf(count))
        val page = feed(b, nav, arrivals)
        val shown = mutableListOf<Boolean>()
        repeat(5) {
            val arrival = arrivals.switched(b, leftTitle = "Fix login")
            testScheduler.runCurrent()
            val due = page.arrival.value!!.backNote
            shown += due
            if (due) page.backNoteShown(arrival.seq)
            page.backNoteShown(arrival.seq) // asking twice counts once
        }
        assertEquals(listOf(true, true, true, false, false), shown)
        assertEquals(3, count.n, "stored per device: three shown, then no more")
    }

    @Test
    fun `B-LAMP-C1-001 backNote_notCountedUntilShown_andNeedsATitle`() = runTest {
        val count = Count()
        val arrivals = ChatArrivals(setOf(count))
        repeat(4) { arrivals.switched(b, leftTitle = "Fix login") } // switched away before the page drew it
        assertEquals(0, count.n)
        assertFalse(arrivals.switched(b, leftTitle = null).backNote, "no title, no note")
        assertFalse(arrivals.switched(b, leftTitle = "  ").backNote)
    }

    @Test
    fun `B-LAMP-C1-001 arrival_endsWhenItsPageIsClosed`() = runTest {
        val nav = SignalNav()
        val arrivals = ChatArrivals(emptySet())
        val page = feed(b, nav, arrivals)
        arrivals.switched(b, leftTitle = "x")
        page.closed()
        testScheduler.runCurrent()
        assertNull(page.arrival.value)
    }

    @Test
    fun `B-LAMP-C1-001 flushOnOpen_savesDraft`() = runTest {
        val saved = mutableListOf<String>()
        val page = feed(a, SignalNav(), saved = saved)
        page.flushDraft() // the page being left, when the new conversation opens
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("saved"), saved)
    }
}
