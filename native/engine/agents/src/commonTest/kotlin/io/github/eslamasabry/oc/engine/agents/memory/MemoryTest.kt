package io.github.eslamasabry.oc.engine.agents.memory

import io.github.eslamasabry.oc.engine.agents.json.DartJsonText
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.agents.testing.FakeAgentPrefs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** ConversationTitleMemory and AgentTurnFailureMemory (plan 17 §1.8), byte-compatible with the Flutter memories. */
class MemoryTest {
    private val p1 = ProfileId("p1")

    @Test
    fun `B-AGENT-149 a title survives the archive of its record and a restart`() = runTest {
        val prefs = FakeAgentPrefs()
        val memory = ConversationTitleMemory(prefs)
        assertEquals(Outcome.Ok(true), memory.remember(p1, "claude:ses_1", "Fix the \"login\" bug"))
        assertEquals(listOf("""{"k":"claude:ses_1","t":"Fix the \"login\" bug"}"""), prefs.stringList("oc.conversationTitles.p1"))
        assertEquals("Fix the \"login\" bug", ConversationTitleMemory(prefs).read(p1, "claude:ses_1"))
        assertNull(memory.read(ProfileId("p2"), "claude:ses_1"))
    }

    @Test
    fun `B-AGENT-181 a lone record takes its saved title, and with none the stand-in stays`() = runTest {
        val memory = ConversationTitleMemory(FakeAgentPrefs())
        assertNull(memory.read(p1, "kilo:ses_9"))
        memory.remember(p1, "kilo:ses_9", "Old")
        memory.remember(p1, "kilo:ses_9", "Newer")
        assertEquals("Newer", memory.read(p1, "kilo:ses_9"))
        assertEquals(Outcome.Ok(false), memory.remember(p1, "kilo:ses_9", "   "))
        assertEquals("Newer", memory.read(p1, "kilo:ses_9"))
    }

    @Test
    fun `B-AGENT-149 titles keep the newest 300 and an unreadable list reads as none`() = runTest {
        val prefs = FakeAgentPrefs()
        val memory = ConversationTitleMemory(prefs)
        repeat(305) { memory.remember(p1, "c:$it", "T$it") }
        assertEquals(300, prefs.stringList("oc.conversationTitles.p1")!!.size)
        assertNull(memory.read(p1, "c:0"))
        assertEquals("T304", memory.read(p1, "c:304"))
        val broken = ConversationTitleMemory(FakeAgentPrefs(mapOf("oc.conversationTitles.p1" to listOf("""{"k":"a","t":"A"}""", "{oops"))))
        assertNull(broken.read(p1, "a"))
    }

    @Test
    fun `B-AGENT-P02 a turn failure survives a restart, redacted, with provider and model`() = runTest {
        val prefs = FakeAgentPrefs()
        val memory = AgentTurnFailureMemory(prefs) { it.replace("sk-secret", "•••") }
        memory.remember(p1, "ses_1", RememberedTurnFailure("auth failed sk-secret", provider = "anthropic", model = "sonnet"))
        assertEquals(listOf("""{"s":"ses_1","r":"auth failed •••","p":"anthropic","m":"sonnet"}"""), prefs.stringList("oc.agentTurnFailures.p1"))
        val reopened = AgentTurnFailureMemory(prefs) { it }
        assertEquals(RememberedTurnFailure("auth failed •••", "anthropic", "sonnet"), reopened.read(p1, "ses_1"))
        memory.remember(p1, "ses_2", RememberedTurnFailure("limit"))
        assertEquals("""{"s":"ses_2","r":"limit"}""", prefs.stringList("oc.agentTurnFailures.p1")!!.last())
    }

    @Test
    fun `B-AGENT-P02 another record of the same conversation finds its failure and a later answer forgets it`() = runTest {
        val prefs = FakeAgentPrefs()
        val memory = AgentTurnFailureMemory(prefs) { it }
        memory.remember(p1, "old-fork", RememberedTurnFailure("signed out"))
        assertEquals("signed out", memory.read(p1, "ses_new", alsoAs = listOf("old-fork"))?.reason)
        val commits = prefs.commits
        assertIs<Outcome.Ok<Boolean>>(memory.forget(p1, "nothing"))
        assertEquals(commits, prefs.commits) // nothing matched: no write
        memory.forget(p1, "ses_new", alsoAs = listOf("old-fork"))
        assertNull(memory.read(p1, "old-fork"))
        assertTrue(prefs.stringList("oc.agentTurnFailures.p1")!!.isEmpty())
    }

    @Test
    fun `B-AGENT-P02 failures keep the newest 100 and a refused write is reported`() = runTest {
        val prefs = FakeAgentPrefs()
        val memory = AgentTurnFailureMemory(prefs) { it }
        repeat(102) { memory.remember(p1, "s$it", RememberedTurnFailure("r$it")) }
        assertEquals(100, prefs.stringList("oc.agentTurnFailures.p1")!!.size)
        assertNull(memory.read(p1, "s0"))
        prefs.refuseWrites = true
        assertIs<Outcome.Failed>(memory.remember(p1, "s200", RememberedTurnFailure("x")))
    }

    @Test
    fun `B-AGENT-P02 Dart escapes control characters the same way`() {
        assertEquals("\"a\\nb\\u0001\\\\/é\"", DartJsonText.encode("a\nb\u0001\\/é"))
    }
}
