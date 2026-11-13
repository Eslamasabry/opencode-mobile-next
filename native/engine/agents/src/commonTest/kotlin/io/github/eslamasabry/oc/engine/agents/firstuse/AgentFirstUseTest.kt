package io.github.eslamasabry.oc.engine.agents.firstuse

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.firstuse.AgentFirstUse.Phase
import io.github.eslamasabry.oc.engine.agents.firstuse.AgentFirstUse.Verdict
import io.github.eslamasabry.oc.engine.agents.testing.FakeAgentPrefs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Coordinator ruling 2026-11-13: agents whose sign-in no read-only check can tell (Qwen Code, Goose, omp) are set up at
 * first use. Feasibility (2026-11-13): Qwen Code 0.26.4 removed `qwen auth status`; Goose 1.54.0 can only check by
 * prompting the model; omp's ACP never refuses a session.
 */
class AgentFirstUseTest {
    private fun agent(id: String) = AgentCatalog.builtIn.byId(AgentId(id))!!

    private val owner = ProfileId("owner")

    @Test
    fun `B-AGENT-113 the agents set up at first use are exactly those the check script cannot read`() {
        for (id in listOf("qwen", "goose", "omp-acp", "gemini")) assertTrue(AgentFirstUse.applies(agent(id)), id)
        for (id in listOf("claude", "codex", "kilo", "cline", "fx", "antigravity", "opencode2"))
            assertFalse(AgentFirstUse.applies(agent(id)), id)
    }

    @Test
    fun `B-AGENT-113 Qwen Code's refused start needs setting up, whatever was set up before`() {
        val qwen = agent("qwen")
        for (said in listOf("Authentication required", "Use Qwen Code CLI to authenticate first.", "AuthRequired")) {
            for (done in listOf(false, true)) {
                assertEquals(Verdict.NeedsSetUp, AgentFirstUse.classify(qwen, Phase.StartConversation, said, done), said)
            }
        }
        // Anything else (the helper stopped, a network error) is a plain failure with Try again.
        assertEquals(Verdict.Plain, AgentFirstUse.classify(qwen, Phase.StartConversation, "Internal error", false))
        assertEquals(Verdict.Plain, AgentFirstUse.classify(qwen, Phase.FirstPrompt, null, false))
    }

    @Test
    fun `B-AGENT-113 a Goose that was never set up needs setting up when its start fails, after set-up it is plain`() {
        val goose = agent("goose")
        assertEquals(Verdict.NeedsSetUp, AgentFirstUse.classify(goose, Phase.StartConversation, "Internal error", false))
        assertEquals(Verdict.Plain, AgentFirstUse.classify(goose, Phase.StartConversation, "Internal error", true))
        assertEquals(Verdict.Plain, AgentFirstUse.classify(goose, Phase.FirstPrompt, "Internal error", false))
        assertEquals(Verdict.NeedsSetUp, AgentFirstUse.classify(goose, Phase.StartConversation, "auth_required", true))
    }

    @Test
    fun `B-AGENT-113 omp's first answer without a model or key needs setting up`() {
        val omp = agent("omp-acp")
        for (said in listOf("No model selected.\n\nUse /login", "No API key found for anthropic.")) {
            assertEquals(Verdict.NeedsSetUp, AgentFirstUse.classify(omp, Phase.FirstPrompt, said, true), said)
        }
        assertEquals(Verdict.Plain, AgentFirstUse.classify(omp, Phase.FirstPrompt, "Internal error", false))
        assertEquals(Verdict.Plain, AgentFirstUse.classify(omp, Phase.StartConversation, "Internal error", false))
    }

    @Test
    fun `B-AGENT-113 agents with a sign-in check keep their own path`() {
        for (id in listOf("claude", "codex", "kilo"))
            assertEquals(
                Verdict.Plain,
                AgentFirstUse.classify(agent(id), Phase.StartConversation, "Authentication required", false),
                id,
            )
    }

    @Test
    fun `B-AGENT-113 the set-up mark is kept per owner under oc agentSetUp and never twice`() = runTest {
        val prefs = FakeAgentPrefs()
        val store = AgentSetUpStore(prefs)
        assertFalse(store.isSetUp(owner, AgentId("goose")))
        assertIs<Outcome.Ok<Unit>>(store.markSetUp(owner, AgentId("goose")))
        assertIs<Outcome.Ok<Unit>>(store.markSetUp(owner, AgentId("goose")))
        assertIs<Outcome.Ok<Unit>>(store.markSetUp(owner, AgentId("qwen")))
        assertTrue(store.isSetUp(owner, AgentId("goose")))
        assertFalse(store.isSetUp(ProfileId("other"), AgentId("goose")))
        assertEquals(listOf("goose", "qwen"), prefs.stringList("oc.agentSetUp.owner"))
    }
}
