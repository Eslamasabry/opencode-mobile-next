package io.github.eslamasabry.oc.engine.agents.install

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.removal.RemovalResult
import io.github.eslamasabry.oc.engine.agents.testing.FakeAgentPrefs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * B-AGENT-METERED-001: on mobile data a shared part that is only waiting for Wi-Fi never holds back an update the
 * person already said yes to. The device's order: four agents chosen, a yes for OpenCode, and OpenCode started at once.
 */
class AgentUpdateChoiceEngineTest {
    private fun optional(id: AgentId, name: String, mb: Long) =
        AgentUpdateItem(id, name, "1", "2", mb * 1_000_000, 400_000_000, AgentUpdateGroup.Optional, true, true, null, null, null)

    private val helper =
        AgentUpdateItem(
            AgentId("agent-paseo"), "Agents helper", "1", "2", 330_000_000, null, AgentUpdateGroup.Needed, true, false, null, null,
            AgentNeedRule.WithAnyUpdate,
        )

    private fun host(network: AgentUpdateNetwork, items: List<AgentUpdateItem>) =
        object : AgentChoiceHost {
            override suspend fun inventory() = AgentUpdateInventory(items, network, 50_000_000_000)

            override suspend fun hasRoom(id: AgentId) = true

            override suspend fun busy() = false

            override suspend fun remove(id: AgentId): Outcome<RemovalResult> = error("no removal here")
        }

    private suspend fun chosen(network: AgentUpdateNetwork, ids: List<AgentId>): AgentUpdateChoiceEngine {
        val items = listOf(optional(AgentId.OPENCODE, "OpenCode", 130), optional(AgentId.CLAUDE, "Claude Code", 120), helper)
        val engine = AgentUpdateChoiceEngine("100", FakeAgentPrefs(), host(network, items))
        engine.refresh()
        engine.decide(ids.associateWith { AgentUpdateChoice.Update })
        return engine
    }

    @Test
    fun `B-AGENT-METERED-001 on mobile data a shared part waiting for Wi-Fi does not hold OpenCode back`() = runTest {
        val engine = chosen(AgentUpdateNetwork.Metered, listOf(AgentId.OPENCODE, AgentId.CLAUDE))
        var started = false
        assertTrue(engine.startIfChosen(AgentId.OPENCODE) { started = true; true }, "OpenCode goes at once")
        assertTrue(started)
    }

    @Test
    fun `B-AGENT-METERED-002 on a free network the shared part still goes ahead of OpenCode`() = runTest {
        val engine = chosen(AgentUpdateNetwork.Unmetered, listOf(AgentId.OPENCODE))
        assertFalse(engine.startIfChosen(AgentId.OPENCODE) { true })
    }

    @Test
    fun `B-AGENT-METERED-003 once the shared part has its own yes on mobile data it goes first again`() = runTest {
        val engine = chosen(AgentUpdateNetwork.Metered, listOf(AgentId.OPENCODE))
        assertTrue(engine.startShared(meteredAllowed = true) { true })
        assertFalse(engine.startIfChosen(AgentId.OPENCODE) { true })
    }
}
