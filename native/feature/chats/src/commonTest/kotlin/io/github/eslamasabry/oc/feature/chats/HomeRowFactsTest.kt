package io.github.eslamasabry.oc.feature.chats

import io.github.eslamasabry.oc.engine.agents.feed.ChatStatus
import io.github.eslamasabry.oc.feature.chats.presentation.AgentResumeRules
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsState
import io.github.eslamasabry.oc.feature.chats.presentation.HomeRowStatusRules
import io.github.eslamasabry.oc.feature.chats.presentation.RowStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** H6 (Stopped is not Done) and H4 (an old agent conversation asks before opening). */
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
    fun `H4 a conversation that needs a new chat asks, and the question names the agent`() {
        val old = item("old").copy(agentId = "claude", needsNewChat = true)
        val ask = AgentResumeRules.ask(old, "Claude Code")!!
        assertEquals(old.ref, ask.ref)
        assertEquals("Claude Code", ask.agentName)
        assertNull(AgentResumeRules.ask(item("fine"), null))
        assertNull(AgentResumeRules.ask(null, null))
    }

    @Test
    fun `H4 start new opens the form on the same server with the same agent`() {
        val ask = AgentResumeRules.ask(item("old").copy(agentId = "claude", needsNewChat = true), "Claude Code")!!
        val route = AgentResumeRules.route(ask)
        assertEquals(ask.ref.profileId, route.profile)
        assertTrue(route.phoneAgent!!.isNotBlank())
    }

    @Test
    fun `H4 the row says Can't reopen only for a finished or quiet row`() {
        val quiet = ChatsState.row(item("a").copy(needsNewChat = true))
        assertTrue(HomeRowStatusRules.saysCantReopen(quiet))
        assertTrue(quiet.canOpen) // it is tappable: the tap asks
        val working = ChatsState.row(item("b", status = ChatStatus.Running).copy(needsNewChat = true))
        assertFalse(HomeRowStatusRules.saysCantReopen(working))
        assertFalse(HomeRowStatusRules.saysCantReopen(ChatsState.row(item("c"))))
    }

    @Test
    fun `H4 start new carries the old conversation's folder, and none when it has none`() {
        val old = item("old").copy(agentId = "claude", needsNewChat = true, directory = "/root/projects/old")
        assertEquals("/root/projects/old", AgentResumeRules.route(AgentResumeRules.ask(old, null)!!).directory)
        val bare = old.copy(directory = " ")
        assertNull(AgentResumeRules.route(AgentResumeRules.ask(bare, null)!!).directory)
    }
}
