package io.github.eslamasabry.oc.feature.chats

import io.github.eslamasabry.oc.engine.agents.feed.ChatStatus
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsState
import io.github.eslamasabry.oc.feature.chats.presentation.HomeRowStatusRules
import io.github.eslamasabry.oc.feature.chats.presentation.RowStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** H6 (Stopped is not Done) and H4 (an old agent conversation's row). */
class HomeRowFactsTest {
    @Test
    fun `H6 an idle row is None until finished, Stopped when the person stopped it, else Done`() {
        // Stopped in the open chat: the read mark already cleared the watermark, the memory of the Stop still counts.
        assertEquals(RowStatus.Stopped, HomeRowStatusRules.idle(finishedUnseen = false, stoppedByPerson = true))
        assertEquals(RowStatus.None, HomeRowStatusRules.idle(finishedUnseen = false, stoppedByPerson = false))
        assertEquals(RowStatus.Stopped, HomeRowStatusRules.idle(finishedUnseen = true, stoppedByPerson = true))
        assertEquals(RowStatus.Done, HomeRowStatusRules.idle(finishedUnseen = true, stoppedByPerson = false))
    }

    @Test
    fun `H6 the row carries Stopped from the feed item`() {
        val stopped = item("a", unseen = true).copy(stoppedByPerson = true)
        assertEquals(RowStatus.Stopped, ChatsState.row(stopped).status)
        assertEquals(RowStatus.Done, ChatsState.row(item("b", unseen = true)).status)
    }

    @Test
    fun `a request the server still holds wins over an earlier Stop, a dropped one leaves Stopped`() {
        val pending = item("a", status = ChatStatus.NeedsYou, unseen = true).copy(stoppedByPerson = true)
        assertEquals(RowStatus.NeedsYou, ChatsState.row(pending).status)
        val dropped = item("a", unseen = true).copy(stoppedByPerson = true)
        assertEquals(RowStatus.Stopped, ChatsState.row(dropped).status)
    }

    @Test
    fun `H4 an old agent conversation's row opens and keeps its own state`() {
        val done = ChatsState.row(item("a", unseen = true).copy(needsNewChat = true))
        assertTrue(done.canOpen) // the tap opens it to read and continue
        assertEquals(RowStatus.Done, done.status)
    }
}
