package io.github.eslamasabry.oc.engine.agents.persona

import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Port of test/agent_persona_test.dart (B-AGENT-023). The words half is `:ui:words` AgentPersonaWordsTest. */
class AgentPersonaTest {
    @Test
    fun `B-AGENT-023 every catalog agent id and the OpenCode id map to a persona`() {
        for (agent in AgentCatalog.builtIn.agents) {
            val persona = AgentPersona.of(agent.id.raw)
            assertEquals(persona.id, AgentPersona.of(agent.iconKey).id, agent.id.raw)
        }
        // fx has no bundled mark: it is "other" with the neutral glyph.
        assertFalse(AgentPersona.of("fx").hasMark)
        assertEquals(AgentPersonaId.OpenCode, AgentPersona.of(AgentId.OPENCODE.raw).id)
    }

    @Test
    fun `B-AGENT-023 the named agents and their aliases resolve`() {
        val expected = mapOf(
            "claude" to AgentPersonaId.ClaudeCode,
            "Claude-Code" to AgentPersonaId.ClaudeCode,
            " claude code " to AgentPersonaId.ClaudeCode,
            "codex" to AgentPersonaId.Codex,
            "gemini" to AgentPersonaId.Gemini,
            "gemini-cli" to AgentPersonaId.Gemini,
            "antigravity" to AgentPersonaId.Antigravity,
            "Antigravity CLI" to AgentPersonaId.Antigravity,
            "antigravity-cli" to AgentPersonaId.Antigravity,
            "agy" to AgentPersonaId.Antigravity,
            "kilo" to AgentPersonaId.Kilo,
            "kilocode" to AgentPersonaId.Kilo,
            "cline" to AgentPersonaId.Cline,
            "opencode" to AgentPersonaId.OpenCode,
            "opencode2" to AgentPersonaId.OpenCode,
        )
        for ((key, id) in expected) assertEquals(id, AgentPersona.of(key).id, key)
    }

    @Test
    fun `B-AGENT-023 unknown ids become other with a neutral mark`() {
        val persona = AgentPersona.of("totally-new-agent")
        assertEquals(AgentPersonaId.Other, persona.id)
        assertFalse(persona.hasMark)
        assertEquals(AgentPersonaId.Other, AgentPersona.of("").id)
    }

    @Test
    fun `B-AGENT-023 agents with a bundled mark but no persona keep their mark`() {
        val pi = AgentPersona.of("pi")
        assertEquals(AgentPersonaId.Other, pi.id)
        assertEquals("pi", pi.markSlug)
        assertEquals("pi", AgentPersona.of("omp-acp").markSlug)
        assertEquals("goose", AgentPersona.of("goose").markSlug)
    }

    @Test
    fun `B-AGENT-023 the seven named personas are distinct and each has a mark`() {
        assertEquals(7, AgentPersona.all.map { it.id }.toSet().size)
        assertTrue(AgentPersona.all.all { it.hasMark })
    }

    @Test
    fun `B-AGENT-023 B-116 Antigravity is Google's agent with its own mark, not Google's G or Gemini's`() {
        val persona = AgentPersona.of("antigravity")
        assertEquals(AgentMaker.Google, persona.maker)
        assertEquals("antigravity", persona.markSlug)
        assertEquals("antigravity", AgentPersona.agentMarkSlug("agy"))
        assertEquals("gemini", AgentPersona.of("gemini").markSlug)
    }
}
