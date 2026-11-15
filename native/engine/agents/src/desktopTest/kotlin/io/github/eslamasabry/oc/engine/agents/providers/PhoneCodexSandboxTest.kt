package io.github.eslamasabry.oc.engine.agents.providers

import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.host.PaseoConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** Issue 147: Codex on the phone runs without its own OS sandbox, through the guard that makes it ask (see the guard test). */
class PhoneCodexSandboxTest {
    private fun providers(config: String): JsonObject =
        Json.parseToJsonElement(config).jsonObject["agents"]!!.jsonObject["providers"]!!.jsonObject

    @Test
    fun `B-AGENT-CODEX-001 the phone helper starts Codex through the guard and changes nothing else`() {
        val base = PaseoConfig.render().json
        val phone = PhoneHelperConfig.compose(base, emptyList(), emptyMap())
        val codex = providers(phone)["codex"]!!.jsonObject
        // Only the start command: no sandbox or approval option (the guard sets both per thread and turn; an option
        // would hide a read-only mode from it, and Codex 0.169.0 refuses `untrusted` there).
        assertEquals(setOf("command"), codex.keys)
        assertEquals(
            listOf("/usr/bin/python3", "-c", PhoneCodexSandbox.GUARD, "/home/oc/.local/bin/codex"),
            codex["command"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        // Every catalog entry is byte for byte what PaseoConfig rendered.
        assertEquals(providers(base), JsonObject(providers(phone).filterKeys { it != "codex" }))
        val rest = { c: String -> Json.parseToJsonElement(c).jsonObject.filterKeys { it != "agents" } }
        assertEquals(rest(base), rest(phone))
    }

    @Test
    fun `B-AGENT-CODEX-002 a computer-hosted Codex is untouched - the catalog's own config never carries it`() {
        // PaseoConfig.render is the catalog's config (the shape of the golden file); the app writes no config to a
        // computer, and only the phone helper's compose adds the override.
        assertNull(providers(PaseoConfig.render().json)["codex"])
        assertFalse(PaseoConfig.render().json.contains("app-server"))
    }

    @Test
    fun `B-AGENT-CODEX-003 own agents and an existing codex entry keep what they had`() {
        val agent = OwnAgent(AgentId("own-a"), "A", listOf("a", "acp"), null, emptyList())
        val base =
            """{"version":1,"agents":{"providers":{"codex":{"enabled":true,"options":{"web_search":"cached","sandbox_mode":"danger-full-access"}}}}}"""
        val composed = providers(PhoneHelperConfig.compose(base, listOf(agent), emptyMap()))
        val codex = composed["codex"]!!.jsonObject
        assertEquals("true", codex["enabled"]!!.jsonPrimitive.content)
        // Other options stay; the sandbox option goes (the guard sets it per thread and turn).
        assertEquals(mapOf("web_search" to "cached"), codex["options"]!!.jsonObject.mapValues { it.value.jsonPrimitive.content })
        assertEquals("/usr/bin/python3", codex["command"]!!.jsonArray.first().jsonPrimitive.content)
        assertEquals("A", composed["own-a"]!!.jsonObject["label"]!!.jsonPrimitive.content)
    }
}
