package io.github.eslamasabry.oc.engine.agents.selection

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentArchitecture
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.host.AgentHostProblem
import io.github.eslamasabry.oc.engine.agents.host.agentHostProblem
import io.github.eslamasabry.oc.engine.agents.readiness.AgentReadiness
import io.github.eslamasabry.oc.engine.agents.readiness.AgentRow
import io.github.eslamasabry.oc.engine.agents.readiness.AgentSignInPhase
import io.github.eslamasabry.oc.engine.agents.readiness.PhoneAgentRuntime
import io.github.eslamasabry.oc.engine.agents.testing.FakeAgentPrefs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * AGENTS-BACKENDS-03 (plan 17 §1.6). From test/phone_agents_controller_test.dart "rows, chip choices and a persisted,
 * revalidated selection" and the model-name memory of lib/state/connection/models.dart. No catalog row names these
 * clauses yet: B-AGENT-SEL-001…004 are proposed (S3-AGENTSE report).
 */
class AgentSelectionTest {
    private val profile = ProfileId("local")
    private val catalog = AgentCatalog.builtIn

    private fun rows(claudeReady: Boolean = true): List<AgentRow> = catalog.agents.map { d ->
        val fact = if (d.id == AgentId.CLAUDE && claudeReady) {
            PhoneAgentRuntime(d.id, installed = true, hostAvailable = true, architectureQualified = true,
                signInPhase = AgentSignInPhase.SignedIn)
        } else {
            null
        }
        AgentReadiness.row(d, AgentArchitecture.Arm64, ServerCapabilities(), fact)
    }

    @Test
    fun `B-AGENT-SEL-001 a persisted selection is revalidated against the rows`() = runTest {
        val prefs = FakeAgentPrefs()
        val selection = AgentSelection(prefs)
        assertEquals(AgentId.OPENCODE, selection.selected(profile, rows(), true))
        assertIs<Outcome.Ok<Unit>>(selection.select(profile, AgentId.CLAUDE, rows(), true))
        assertEquals(AgentId.CLAUDE, selection.selected(profile, rows(), true))
        assertEquals("claude", prefs.string("oc.chatAgent.local"))
        val refused = selection.select(profile, AgentId("gemini"), rows(), true)
        assertEquals(AgentHostProblem.AgentUnavailable, (refused as Outcome.Failed).failure.agentHostProblem())
        assertEquals("claude", prefs.string("oc.chatAgent.local"))
        // The host loses the agent: the stored choice falls back to OpenCode, and the key is kept.
        assertEquals(AgentId.OPENCODE, selection.selected(profile, rows(claudeReady = false), true))
        assertEquals("claude", prefs.string("oc.chatAgent.local"))
    }

    @Test
    fun `B-AGENT-SEL-001 before the first row read the saved choice stands, so the chip does not flicker`() = runTest {
        val prefs = FakeAgentPrefs(mapOf("oc.chatAgent.local" to "claude"))
        val selection = AgentSelection(prefs)
        assertEquals(AgentId.CLAUDE, selection.selected(profile, emptyList(), true))
        assertEquals(AgentId.OPENCODE, selection.selected(profile, emptyList(), phoneAgentsAvailable = false))
        assertIs<Outcome.Failed>(selection.select(profile, AgentId.CLAUDE, rows(), phoneAgentsAvailable = false))
        assertIs<Outcome.Ok<Unit>>(selection.select(profile, AgentId.OPENCODE, emptyList(), phoneAgentsAvailable = false))
    }

    @Test
    fun `B-AGENT-SEL-002 the model per agent is one JSON map and clearing forgets one agent`() = runTest {
        val prefs = FakeAgentPrefs()
        val selection = AgentSelection(prefs)
        selection.selectModel(profile, AgentId.CLAUDE, "claude-model")
        selection.selectModel(profile, AgentId("codex"), "gpt")
        assertEquals("""{"claude":"claude-model","codex":"gpt"}""", prefs.string("oc.agentModel.local"))
        assertEquals("claude-model", selection.selectedModel(profile, AgentId.CLAUDE))
        selection.selectModel(profile, AgentId.CLAUDE, null)
        assertNull(selection.selectedModel(profile, AgentId.CLAUDE))
        assertEquals("gpt", selection.selectedModel(profile, AgentId("codex")))
        prefs.values["oc.agentModel.local"] = "not json"
        assertNull(selection.selectedModel(profile, AgentId("codex")))
        prefs.values["oc.agentModel.local"] = """{"claude":7,"codex":"gpt"}"""
        assertNull(selection.selectedModel(profile, AgentId.CLAUDE))
        assertEquals("gpt", selection.selectedModel(profile, AgentId("codex")))
    }

    @Test
    fun `B-AGENT-SEL-003 model names are remembered, newest last, at most 40`() = runTest {
        val prefs = FakeAgentPrefs()
        val selection = AgentSelection(prefs)
        selection.rememberModelName(profile, "anthropic", "m0", "Sonnet")
        assertEquals("Sonnet", selection.knownModelName(profile, "anthropic", "m0"))
        val commits = prefs.commits
        selection.rememberModelName(profile, "anthropic", "m0", "Sonnet")
        selection.rememberModelName(profile, "anthropic", "m1", " ")
        assertEquals(commits, prefs.commits)
        for (i in 1..40) selection.rememberModelName(profile, "p", "m$i", "Model $i")
        assertNull(selection.knownModelName(profile, "anthropic", "m0"))
        assertEquals("Model 1", selection.knownModelName(profile, "p", "m1"))
        assertEquals("Model 40", selection.knownModelName(profile, "p", "m40"))
    }

    @Test
    fun `B-AGENT-SEL-002 a refused write keeps the last choice`() = runTest {
        val prefs = FakeAgentPrefs()
        val selection = AgentSelection(prefs)
        selection.selectModel(profile, AgentId.CLAUDE, "a")
        prefs.refuseWrites = true
        assertIs<Outcome.Failed>(selection.selectModel(profile, AgentId.CLAUDE, "b"))
        assertEquals("a", selection.selectedModel(profile, AgentId.CLAUDE))
    }

    @Test
    fun `B-AGENT-SEL-004 phone agents work only in projects under root projects and never in a separate copy`() {
        assertTrue(AgentSelection.folderAllowed(AgentId.CLAUDE, "/root/projects/app"))
        assertFalse(AgentSelection.folderAllowed(AgentId.CLAUDE, "/root/projects"))
        assertFalse(AgentSelection.folderAllowed(AgentId.CLAUDE, "/sdcard/app"))
        assertFalse(AgentSelection.folderAllowed(AgentId.CLAUDE, null))
        assertTrue(AgentSelection.folderAllowed(AgentId.OPENCODE, "/sdcard/app"))
        assertFalse(AgentSelection.separateCopyOffered(AgentId.CLAUDE))
        assertTrue(AgentSelection.separateCopyOffered(AgentId.OPENCODE))
    }
}
