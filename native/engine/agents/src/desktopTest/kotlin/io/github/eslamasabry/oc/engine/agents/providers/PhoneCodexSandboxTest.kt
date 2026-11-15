package io.github.eslamasabry.oc.engine.agents.providers

import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.host.PaseoConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** Issue 147 launch blocker: Codex on the phone runs its shell tool without its own OS sandbox. */
class PhoneCodexSandboxTest {
    private fun providers(config: String): JsonObject =
        Json.parseToJsonElement(config).jsonObject["agents"]!!.jsonObject["providers"]!!.jsonObject

    @Test
    fun `B-AGENT-CODEX-001 the phone helper's config turns Codex's sandbox off and changes nothing else`() {
        val base = PaseoConfig.render().json
        val phone = PhoneHelperConfig.compose(base, emptyList(), emptyMap())
        val codex = providers(phone)["codex"]!!.jsonObject
        // Only the sandbox: no approval policy (the mode the person chose decides it), no extends, no command.
        assertEquals(setOf("options"), codex.keys)
        assertEquals(mapOf("sandbox_mode" to "danger-full-access"), codex["options"]!!.jsonObject.mapValues { it.value.jsonPrimitive.content })
        // Every catalog entry is byte for byte what PaseoConfig rendered.
        val before = providers(base)
        assertEquals(before, JsonObject(providers(phone).filterKeys { it != "codex" }))
        val rest = { c: String -> Json.parseToJsonElement(c).jsonObject.filterKeys { it != "agents" } }
        assertEquals(rest(base), rest(phone))
    }

    @Test
    fun `B-AGENT-CODEX-002 a computer-hosted Codex is untouched - the catalog's own config never carries it`() {
        // PaseoConfig.render is the catalog's config (the shape of the golden file); the app writes no config to a
        // computer, and only the phone helper's compose adds the override.
        assertNull(providers(PaseoConfig.render().json)["codex"])
        assertFalse(PaseoConfig.render().json.contains("danger-full-access"))
    }

    @Test
    fun `B-AGENT-CODEX-003 own agents and an existing codex entry keep what they had`() {
        val agent = OwnAgent(AgentId("own-a"), "A", listOf("a", "acp"), null, emptyList())
        val base = """{"version":1,"agents":{"providers":{"codex":{"enabled":true,"options":{"web_search":"cached"}}}}}"""
        val composed = providers(PhoneHelperConfig.compose(base, listOf(agent), emptyMap()))
        val codex = composed["codex"]!!.jsonObject
        assertEquals("true", codex["enabled"]!!.jsonPrimitive.content)
        assertEquals("cached", codex["options"]!!.jsonObject["web_search"]!!.jsonPrimitive.content)
        assertEquals("danger-full-access", codex["options"]!!.jsonObject["sandbox_mode"]!!.jsonPrimitive.content)
        assertEquals("A", composed["own-a"]!!.jsonObject["label"]!!.jsonPrimitive.content)
    }
}
