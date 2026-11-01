package io.github.eslamasabry.oc.engine.agents.feed

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.testing.FakeAgentPrefs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The saved feed rows (`_AgentFeedCache`, phone_agents_cache.dart): exact Flutter JSON, both ways. */
class SavedChatFeedTest {
    private val owner = ProfileId("phone-1")

    @Test
    fun `B-AGENT-P01 agent rows are written in the Flutter row shape, at most 200, and read back as idle`() = runTest {
        val prefs = FakeAgentPrefs()
        val feed = SavedChatFeed(prefs)
        val live = row("ses_1", ChatStatus.Running, agent = "claude", time = 1730000000000)
            .copy(sourceId = "paseo:/root/projects/app", sourceLabel = "Claude Code", canReopen = false, parentId = null)
        assertIs<Outcome.Ok<Unit>>(feed.saveAgentRows(owner, List(250) { live.copy(sessionId = "ses_$it") }))
        val raw = prefs.string("oc.agentFeed.phone-1")!!
        assertTrue(raw.startsWith(
            """[{"sourceId":"paseo:/root/projects/app","sessionID":"ses_0","title":"Conversation","directory":"/work/project",""" +
                """"projectName":"Project","isGit":false,"at":1730000000000,"parentID":null,"agentId":"claude",""" +
                """"agentLabel":"Agent","sourceLabel":"Claude Code","canReopen":false},""",
        ))
        val back = feed.agentRows(owner)
        assertEquals(200, back.size)
        assertEquals(ChatStatus.Idle, back.first().status)
        assertFalse(back.first().canReopen)
        assertEquals(AgentId("claude"), back.first().agentId)
        val commits = prefs.commits
        feed.saveAgentRows(owner, List(250) { live.copy(sessionId = "ses_$it") })
        assertEquals(commits, prefs.commits) // unchanged rows are not written again
    }

    @Test
    fun `B-AGENT-P01 a Flutter-written row reads with Dart's fallbacks and bad rows are skipped`() {
        val prefs = FakeAgentPrefs(
            mapOf(
                "oc.agentFeed.phone-1" to
                    """[{"sourceId":"paseo:/root/projects/app","sessionID":"a","directory":"/root/projects/app","at":5,"canReopen":true},""" +
                    """{"sourceId":"opencode","sessionID":"b","directory":"/x","at":5},""" +
                    """{"sourceId":"paseo:/x","sessionID":"c","directory":"/x","at":5.0},""" +
                    """{"sourceId":"paseo:/x","sessionID":"d","at":5},4,"text"]""",
                "oc.mainFeed.phone-1" to """[{"sourceId":"opencode","sessionID":"m","directory":"/w/app","at":7}]""",
                "oc.sideFeed.pc" to "not json",
            ),
        )
        val feed = SavedChatFeed(prefs)
        val rows = feed.agentRows(owner)
        assertEquals(listOf("a"), rows.map { it.sessionId })
        assertNull(rows.single().title) // the UI says "New chat"
        assertEquals("app", rows.single().projectName)
        assertEquals(AgentId("paseo"), rows.single().agentId)
        assertTrue(rows.single().canReopen)
        assertEquals(listOf("m"), feed.mainRows(owner).map { it.sessionId })
        assertTrue(feed.sideRows(ProfileId("pc")).isEmpty())
    }

    @Test
    fun `B-AGENT-P01 signed-out agents and the used flag are kept for the first paint`() = runTest {
        val prefs = FakeAgentPrefs()
        val feed = SavedChatFeed(prefs)
        assertFalse(feed.usedBefore(owner))
        feed.rememberUsed(owner, true)
        assertEquals(true, prefs.bool("oc.phoneAgentsUsed.phone-1"))
        feed.saveSignedOut(owner, mapOf(AgentId("claude") to "Claude Code"))
        assertEquals("""{"claude":"Claude Code"}""", prefs.string("oc.agentSignedOut.phone-1"))
        assertEquals(mapOf(AgentId("claude") to "Claude Code"), feed.signedOut(owner))
        feed.saveSignedOut(owner, emptyMap())
        assertNull(prefs.string("oc.agentSignedOut.phone-1"))
    }
}
