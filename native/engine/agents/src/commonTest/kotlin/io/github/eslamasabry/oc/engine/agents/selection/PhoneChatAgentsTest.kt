package io.github.eslamasabry.oc.engine.agents.selection

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.certification.certifiedClaude327Matrix
import io.github.eslamasabry.oc.engine.agents.host.AgentHostProblem
import io.github.eslamasabry.oc.engine.agents.host.agentHostProblem
import io.github.eslamasabry.oc.engine.agents.testing.FakeAgentPrefs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** The agents New conversation offers on a phone (Flutter new_chat_screen.dart `choices` and `setupRows`). */
class PhoneChatAgentsTest {
    private val claude = AgentId.CLAUDE
    private val codex = AgentId("codex")
    private val profile = ProfileId("local")

    private fun fact(id: AgentId, ready: Boolean, fixable: Boolean = false) =
        PhoneChatFact(id, id.raw, id.raw, ready = ready, fixable = fixable)

    @Test
    fun `PHONE-CHAT a ready certified agent is a choice, a certified one with a way forward is set up`() {
        val ready = PhoneChatAgents.offer(listOf(fact(claude, ready = true)), AgentId.OPENCODE, certifiedClaude327Matrix)
        assertEquals(listOf(claude), ready.choices.map { it.id })
        assertEquals(emptyList(), ready.needsSetup.map { it.id })

        val signedOut = PhoneChatAgents.offer(listOf(fact(claude, ready = false, fixable = true)), AgentId.OPENCODE, certifiedClaude327Matrix)
        assertEquals(emptyList(), signedOut.choices.map { it.id })
        assertEquals(listOf(claude), signedOut.needsSetup.map { it.id }, "a dimmed row that opens its setup")
    }

    // Changed on purpose (owner decision 2026-11-11, "Antigravity not showing"): a ready agent is a choice whether or
    // not the bundled matrix certifies it, as the Agents page's card offers it a conversation. The set-up half stands.
    @Test
    fun `PHONE-CHAT a ready agent is a choice even when not certified for chat, an uncertified one is no set-up row`() {
        val antigravity = AgentId("antigravity")
        val offer =
            PhoneChatAgents.offer(
                listOf(fact(antigravity, ready = true), fact(AgentId("gemini"), ready = false, fixable = true)),
                AgentId.OPENCODE,
            )
        assertEquals(listOf(antigravity), offer.choices.map { it.id }, "the card offers it a conversation, so does this list")
        assertEquals(emptyList(), offer.needsSetup.map { it.id })
    }

    // Owner report 2026-11-14: after an app update Codex and Antigravity were installed and signed in, yet absent from
    // "Who works on it": neither has a matrix row at its new pin, and a not-ready uncertified agent was dropped.
    @Test
    fun `B-AGENT-REG-001 an installed agent that needs something is a set-up row whether or not the matrix knows it`() {
        val antigravity = AgentId("antigravity")
        val offer =
            PhoneChatAgents.offer(
                listOf(
                    fact(codex, ready = false, fixable = true).copy(installed = true),
                    fact(antigravity, ready = false, fixable = true).copy(installed = true),
                    // Not on this phone and not certified: still left to the Agents page.
                    fact(AgentId("gemini"), ready = false, fixable = true),
                ),
                AgentId.OPENCODE,
            )
        assertEquals(emptyList(), offer.choices.map { it.id })
        assertEquals(listOf(codex, antigravity), offer.needsSetup.map { it.id })
    }

    @Test
    fun `B-AGENT-REG-002 an agent being installed or checked stays listed, as checking`() {
        val antigravity = AgentId("antigravity")
        val offer =
            PhoneChatAgents.offer(
                listOf(fact(antigravity, ready = false, fixable = false).copy(installed = true, checking = true)),
                AgentId.OPENCODE,
            )
        assertEquals(listOf(antigravity), offer.needsSetup.map { it.id })
        assertEquals(true, offer.needsSetup.single().checking)
    }

    @Test
    fun `PHONE-CHAT a certified agent with no way forward is left out`() {
        val offer = PhoneChatAgents.offer(listOf(fact(claude, ready = false, fixable = false)), AgentId.OPENCODE)
        assertEquals(emptyList(), offer.choices.map { it.id })
        assertEquals(emptyList(), offer.needsSetup.map { it.id })
    }

    @Test
    fun `PHONE-CHAT the agent already chosen always shows, in the catalog's order`() {
        val offer =
            PhoneChatAgents.offer(
                listOf(fact(codex, ready = true), fact(claude, ready = true)),
                selected = codex,
            )
        assertEquals(listOf(claude, codex), offer.choices.map { it.id }, "Claude Code first, then the chosen Codex")
    }

    @Test
    fun `PHONE-CHAT the saved agent stands while it can start a conversation and falls back to OpenCode`() = runTest {
        val prefs = FakeAgentPrefs(mapOf("oc.chatAgent.local" to "claude"))
        val selection = AgentSelection(prefs)
        assertEquals(claude, selection.selectedAmong(profile, setOf(claude), true))
        assertEquals(claude, selection.selectedAmong(profile, null, true), "rows not read yet: no flicker")
        assertEquals(AgentId.OPENCODE, selection.selectedAmong(profile, emptySet(), true))
        assertEquals(AgentId.OPENCODE, selection.selectedAmong(profile, setOf(claude), false))
        assertEquals("claude", prefs.string("oc.chatAgent.local"), "the saved key is kept")
    }

    @Test
    fun `PHONE-CHAT choosing is saved in the Flutter key and refused for an agent that cannot start`() = runTest {
        val prefs = FakeAgentPrefs()
        val selection = AgentSelection(prefs)
        assertIs<Outcome.Ok<Unit>>(selection.selectAmong(profile, claude, setOf(claude), true))
        assertEquals("claude", prefs.string("oc.chatAgent.local"))
        val refused = selection.selectAmong(profile, codex, setOf(claude), true)
        assertEquals(AgentHostProblem.AgentUnavailable, (refused as Outcome.Failed).failure.agentHostProblem())
        assertEquals("claude", prefs.string("oc.chatAgent.local"))
        assertIs<Outcome.Ok<Unit>>(selection.selectAmong(profile, AgentId.OPENCODE, emptySet(), false))
        assertEquals("opencode", prefs.string("oc.chatAgent.local"))
    }
}
