package io.github.eslamasabry.oc.engine.agents.providers

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.json.AgentJson
import io.github.eslamasabry.oc.engine.agents.testing.FakeAgentPrefs
import io.github.eslamasabry.oc.engine.agents.testing.FakeAgentSecrets
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Issue 147: "Add your own agent" (fake values only). */
class OwnAgentStoreTest {
    private val owner = ProfileId("owner-a")
    private val fakeKey = "fake-key-not-real-0000"

    @Test
    fun `B-AGENT-OWN-050 an own agent keeps its values only in the secure store, its names and command in preferences`() = runTest {
        val prefs = FakeAgentPrefs()
        val secrets = FakeAgentSecrets()
        val redactor = Redactor()
        val store = OwnAgentStore(prefs, secrets, redactor::registerSecret)
        val saved = assertIs<Outcome.Ok<OwnAgent>>(
            store.save(owner, null, "My Agent", "my-agent --acp \"two words\"", "npm i -g my-agent", listOf("MY_KEY" to fakeKey)),
        ).value
        assertEquals(AgentId("own-my-agent"), saved.id)
        assertEquals(listOf("my-agent", "--acp", "two words"), saved.start)
        assertEquals(listOf(saved), store.read(owner))
        assertEquals(mapOf(saved.id to mapOf("MY_KEY" to fakeKey)), (store.env(owner) as Outcome.Ok).value)
        assertTrue(prefs.values.values.none { it.toString().contains(fakeKey) })
        assertFalse(saved.toString().contains(fakeKey))
        assertFalse(redactor.redact("x $fakeKey").contains(fakeKey))

        // Editing without retyping the value keeps it; removing takes both away.
        store.save(owner, saved.id, "My Agent", "my-agent acp", "", listOf("MY_KEY" to ""))
        assertEquals(fakeKey, (store.env(owner) as Outcome.Ok).value[saved.id]?.get("MY_KEY"))
        assertEquals(listOf("my-agent", "acp"), store.read(owner).single().start)
        assertNull(store.read(owner).single().install)
        store.remove(owner, saved.id)
        assertTrue(prefs.values.isEmpty())
        assertTrue(secrets.values.isEmpty())
    }

    @Test
    fun `B-AGENT-OWN-051 deleting the connection forgets every own agent and value, under swept names`() = runTest {
        val prefs = FakeAgentPrefs()
        val secrets = FakeAgentSecrets()
        val store = OwnAgentStore(prefs, secrets) {}
        store.save(owner, null, "A", "a acp", "", listOf("K" to fakeKey))
        assertEquals(setOf("oc.ownAgents.owner-a"), prefs.values.keys)
        assertEquals(setOf("oc.ownAgentEnv.owner-a"), secrets.values.keys)
        store.forget(owner)
        assertTrue(prefs.values.isEmpty())
        assertTrue(secrets.values.isEmpty())
    }

    @Test
    fun `B-AGENT-OWN-052 typed values are checked and ids never clash with built-in agents or each other`() {
        assertNull(OwnAgentRules.problem("A", "a acp", "", listOf("API_KEY" to "x")))
        assertEquals(OwnAgentProblem.Name, OwnAgentRules.problem(" ", "a", "", emptyList()))
        assertEquals(OwnAgentProblem.Start, OwnAgentRules.problem("A", "", "", emptyList()))
        assertEquals(OwnAgentProblem.Start, OwnAgentRules.problem("A", "a \"open", "", emptyList()))
        assertEquals(OwnAgentProblem.EnvName, OwnAgentRules.problem("A", "a", "", listOf("PATH" to "/x")))
        assertEquals(OwnAgentProblem.EnvName, OwnAgentRules.problem("A", "a", "", listOf("bad name" to "x")))
        assertEquals(OwnAgentProblem.EnvName, OwnAgentRules.problem("A", "a", "", listOf("K" to "1", "K" to "2")))
        assertEquals(AgentId("own-claude-code"), OwnAgentRules.idFor("Claude Code!", emptySet()))
        assertEquals(AgentId("own-x-2"), OwnAgentRules.idFor("x", setOf(AgentId("own-x"))))
        assertEquals(AgentId("own-agent"), OwnAgentRules.idFor("ü", emptySet()))
        assertEquals(listOf("a b", "c'd"), OwnAgentRules.words("'a b' \"c'd\""))
    }

    @Test
    fun `B-AGENT-OWN-053 the helper's config gains one ACP provider per own agent, built-in entries untouched`() {
        val base = """{"version":1,"agents":{"providers":{"omp-acp":{"extends":"acp","label":"Oh My Pi","command":["/x/omp","acp"]}}}}"""
        val agent = OwnAgent(AgentId("own-my-agent"), "My Agent", listOf("my-agent", "--acp"), null, listOf("MY_KEY"))
        val merged = AgentJson.parseOrNull(
            OwnAgentConfig.merge(base, listOf(agent), mapOf(agent.id to mapOf("MY_KEY" to fakeKey, "OTHER" to "dropped"))),
        ) as JsonObject
        val providers = merged["agents"]!!.jsonObject["providers"]!!.jsonObject
        assertEquals(setOf("omp-acp", "own-my-agent"), providers.keys)
        val own = providers["own-my-agent"]!!.jsonObject
        assertEquals("acp", own["extends"]!!.jsonPrimitive.content)
        assertEquals("My Agent", own["label"]!!.jsonPrimitive.content)
        assertEquals(OwnAgentConfig.LAUNCHER + listOf("my-agent", "--acp"), own["command"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("PATH=\"\$HOME/.local/bin:/home/oc/.local/bin:\$PATH\" exec \"\$0\" \"\$@\"", OwnAgentConfig.LAUNCHER[2])
        assertEquals(mapOf("MY_KEY" to fakeKey), own["env"]!!.jsonObject.mapValues { it.value.jsonPrimitive.content })
        assertEquals(1, merged["version"]!!.jsonPrimitive.content.toInt())
        assertEquals(base, OwnAgentConfig.merge(base, emptyList(), emptyMap()))
    }

    @Test
    fun `B-AGENT-OWN-054 the check's values travel on stdin only and its exit codes read as plain results`() {
        val agent = OwnAgent(AgentId("own-a"), "A", listOf("a"), null, listOf("K"))
        assertFalse(OwnAgentCheckScript.script(owner).contains(fakeKey))
        assertTrue(OwnAgentCheckScript.stdin(agent, mapOf("K" to fakeKey)).contains(fakeKey))
        assertEquals(OwnAgentCheckResult.Answered, OwnAgentCheckResult.of(0))
        assertEquals(OwnAgentCheckResult.NoAnswer, OwnAgentCheckResult.of(10))
        assertEquals(OwnAgentCheckResult.NotAcp, OwnAgentCheckResult.of(11))
        assertEquals(OwnAgentCheckResult.NotFound, OwnAgentCheckResult.of(12))
        assertEquals(OwnAgentCheckResult.CouldNotRun, OwnAgentCheckResult.of(-1))
    }
}
