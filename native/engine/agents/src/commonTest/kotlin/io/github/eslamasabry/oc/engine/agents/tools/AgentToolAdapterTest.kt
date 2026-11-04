package io.github.eslamasabry.oc.engine.agents.tools

import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AgentToolAdapterTest {
    @Test
    fun `B-AGENT-029 connector search identities are exact and separate from cards`() {
        assertEquals(listOf("show", "find_connectors"), PhoneTool.CARDS.tools)
        assertEquals("mcp__oc-ui__find_connectors", AgentToolAdapter.CLAUDE.connectorSearchName)
        assertEquals("oc-ui_find_connectors", AgentToolAdapter.OPEN_CODE_2.connectorSearchName)
        assertNull(AgentToolAdapter.PI.connectorSearchName)
        for (id in listOf("mcp__oc-ui__find_connectors", "oc-ui_find_connectors")) {
            assertTrue(AgentToolAdapters.isConnectorSearchTool(id))
            assertFalse(AgentToolAdapters.isAgentCardTool(id))
        }
        assertFalse(AgentToolAdapters.isConnectorSearchTool("mcp__other__find_connectors"))
        assertFalse(AgentToolAdapters.isConnectorSearchTool("oc-ui_find_connectors_extra"))
    }

    @Test
    fun `B-AGENT-029 ids and Paseo providers are unique`() {
        val ids = AgentToolAdapters.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        val providers = AgentToolAdapters.all.mapNotNull { it.paseoProvider }
        assertEquals(providers.size, providers.toSet().size)
    }

    @Test
    fun `B-AGENT-029 an agent with tools names them and stores them, run by the user its config format expects`() {
        for (agent in AgentToolAdapters.all) {
            assertEquals(agent.naming == null, agent.config == null, "${agent.id}: naming and config come together")
            if (agent.config == McpConfigFormat.ClaudeCli) assertEquals(AgentRunUser.AgentUser, agent.runsAs, agent.id)
            if (agent.config == McpConfigFormat.OpenCodeV2) assertEquals(AgentRunUser.Root, agent.runsAs, agent.id)
        }
    }

    @Test
    fun `B-AGENT-029 only an agent with tools can be qualified or pre-allow cards`() {
        for (agent in AgentToolAdapters.all) {
            if (agent.cardsQualified || agent.preAllowsCards) assertTrue(agent.supportsTools, agent.id)
        }
    }

    @Test
    fun `B-AGENT-029 a plan or question tool only exists with native answers`() {
        for (agent in AgentToolAdapters.all) {
            if (agent.planToolName != null || agent.questionToolName != null) {
                assertTrue(agent.answersNativeQuestions, agent.id)
            }
        }
    }

    @Test
    fun `B-AGENT-029 each naming scheme gives the name captured from its runtime`() {
        assertEquals("mcp__oc-ui__show", AgentToolAdapter.CLAUDE.cardShowName)
        assertEquals("oc-ui_show", AgentToolAdapter.OPEN_CODE_2.cardShowName)
        assertNull(AgentToolAdapter.PI.cardShowName)
        assertEquals(setOf("mcp__oc-ui__show", "oc-ui_show"), AgentToolAdapters.cardShowNames)
    }

    @Test
    fun `B-AGENT-029 OpenCode adapter names the supported runtime`() {
        assertEquals("OpenCode 2", AgentToolAdapter.OPEN_CODE_2.displayName)
        assertSame(AgentToolAdapter.OPEN_CODE_2, AgentToolAdapters.forOpenCode())
    }

    @Test
    fun `B-AGENT-029 a Paseo provider finds its agent and anything else finds none`() {
        assertSame(AgentToolAdapter.CLAUDE, AgentToolAdapters.forPaseoProvider("claude"))
        assertSame(AgentToolAdapter.OH_MY_PI, AgentToolAdapters.forPaseoProvider("omp"))
        assertNull(AgentToolAdapters.forPaseoProvider("gemini"))
        assertNull(AgentToolAdapters.forPaseoProvider(null))
        assertNull(AgentToolAdapters.forPaseoProvider(42))
        assertNull(AgentToolAdapters.forPaseoProvider(listOf("claude")))
    }

    @Test
    fun `B-AGENT-029 tools are registered only for agents that support them and only Claude Code is qualified for cards`() {
        assertEquals(AgentToolAdapter.VALUES, AgentToolAdapters.withTools)
        assertEquals(listOf(AgentToolAdapter.CLAUDE), AgentToolAdapters.all.filter { it.cardsQualified })
    }

    @Test
    fun `B-AGENT-029 ids stay the former enum names (they name helper folders)`() {
        assertEquals(listOf("claude", "openCode2"), AgentToolAdapter.VALUES.map { it.id })
        assertSame(AgentToolAdapter.OPEN_CODE_2, AgentToolAdapters.byId("openCode2"))
        assertNull(AgentToolAdapters.byId("openCode1"))
        assertNull(AgentToolAdapters.byId("nope"))
    }

    @Test
    fun `B-AGENT-029 adapter catalog ids name agents in the native catalog`() {
        for (agent in AgentToolAdapters.all) {
            val id = agent.catalogAgent ?: continue
            assertNotNull(AgentCatalog.builtIn.byId(id), "${agent.id} -> ${id.raw}")
        }
    }
}
