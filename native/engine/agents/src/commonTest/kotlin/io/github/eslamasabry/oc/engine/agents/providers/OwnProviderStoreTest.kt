package io.github.eslamasabry.oc.engine.agents.providers

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.testing.FakeAgentPrefs
import io.github.eslamasabry.oc.engine.agents.testing.FakeAgentSecrets
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Issue 147: a person's own provider for a built-in phone agent (Claude Code). Fake values only. */
class OwnProviderStoreTest {
    private val owner = ProfileId("owner-a")
    private val claude = AgentId.CLAUDE
    private val fakeKey = "fake-key-not-real-0000"
    private val redactor = Redactor()

    @Test
    fun `B-AGENT-OWN-001 the key is kept only in the secure store, endpoint and model in preferences`() = runTest {
        val prefs = FakeAgentPrefs()
        val secrets = FakeAgentSecrets()
        val store = OwnProviderStore(prefs, secrets, redactor::registerSecret)
        assertIs<Outcome.Ok<Unit>>(store.save(owner, claude, " https://llm.example.test/anthropic ", fakeKey, "glm-test"))

        assertEquals(mapOf(claude to OwnProvider("https://llm.example.test/anthropic", "glm-test")), store.read(owner))
        assertEquals(fakeKey, (store.key(owner, claude) as Outcome.Ok).value)
        assertEquals(setOf("oc.agentProviderKeys.owner-a"), secrets.values.keys)
        assertEquals(setOf("oc.agentOwnProviders.owner-a"), prefs.values.keys)
        // No preference, and no toString, carries the key.
        assertTrue(prefs.values.values.none { it.toString().contains(fakeKey) })
        assertFalse(store.read(owner).toString().contains(fakeKey))
    }

    @Test
    fun `B-AGENT-OWN-002 removing the provider and deleting the connection leave no key and no preference`() = runTest {
        val prefs = FakeAgentPrefs()
        val secrets = FakeAgentSecrets()
        val store = OwnProviderStore(prefs, secrets, redactor::registerSecret)
        store.save(owner, claude, "https://llm.example.test", fakeKey, null)
        assertIs<Outcome.Ok<Unit>>(store.remove(owner, claude))
        assertTrue(secrets.values.isEmpty())
        assertTrue(prefs.values.isEmpty())
        assertNull((store.key(owner, claude) as Outcome.Ok).value)

        store.save(owner, claude, "https://llm.example.test", fakeKey, null)
        assertIs<Outcome.Ok<Unit>>(store.forget(owner))
        assertTrue(secrets.values.isEmpty())
        assertTrue(prefs.values.isEmpty())
        // The names are `oc.<what>.<profileId>`: the profile deletion sweep finds them by prefix.
        assertEquals(OwnProviderKeys.SECRETS_PREFIX + owner.raw, OwnProviderKeys.secrets(owner))
        assertEquals(OwnProviderKeys.PROVIDERS_PREFIX + owner.raw, OwnProviderKeys.providers(owner))
    }

    @Test
    fun `B-AGENT-OWN-003 a refused secure-store write saves nothing at all`() = runTest {
        val prefs = FakeAgentPrefs()
        val secrets = FakeAgentSecrets().apply { refuseWrites = true }
        val store = OwnProviderStore(prefs, secrets, redactor::registerSecret)
        assertIs<Outcome.Failed>(store.save(owner, claude, "https://llm.example.test", fakeKey, null))
        assertTrue(prefs.values.isEmpty())
        assertTrue(store.read(owner).isEmpty())
    }

    @Test
    fun `B-AGENT-OWN-006 a saved or read-back key is masked in every log line and diagnostics text`() = runTest {
        val prefs = FakeAgentPrefs()
        val secrets = FakeAgentSecrets()
        OwnProviderStore(prefs, secrets) {}.save(owner, claude, "https://llm.example.test", fakeKey, null)
        // A fresh process: nothing registered until the store reads the key back.
        val fresh = Redactor()
        val line = "agent said: bad key $fakeKey"
        assertTrue(fresh.redact(line).contains(fakeKey))
        OwnProviderStore(prefs, secrets, fresh::registerSecret).key(owner, claude)
        assertFalse(fresh.redact(line).contains(fakeKey))
        assertFalse(fresh.diagnostics(line).contains(fakeKey))
        // The typed key is registered before it is checked or saved, even when the save is refused.
        val typed = Redactor()
        OwnProviderStore(FakeAgentPrefs(), FakeAgentSecrets().apply { refuseWrites = true }, typed::registerSecret)
            .save(owner, claude, "https://llm.example.test", fakeKey, null)
        assertFalse(typed.redact(line).contains(fakeKey))
    }

    @Test
    fun `B-AGENT-OWN-004 typed values are checked before saving and only supported agents take one`() {
        val ok = "https://llm.example.test/v1"
        assertNull(OwnProviderRules.problem(claude, ok, fakeKey, ""))
        assertNull(OwnProviderRules.problem(claude, "http://127.0.0.1:4000", fakeKey, "local-model"))
        assertEquals(OwnProviderProblem.Unsupported, OwnProviderRules.problem(AgentId("codex"), ok, fakeKey, null))
        assertEquals(OwnProviderProblem.Endpoint, OwnProviderRules.problem(claude, "http://llm.example.test", fakeKey, null))
        assertEquals(OwnProviderProblem.Endpoint, OwnProviderRules.problem(claude, "https://user:pw@llm.example.test", fakeKey, null))
        assertEquals(OwnProviderProblem.Endpoint, OwnProviderRules.problem(claude, "", fakeKey, null))
        assertEquals(OwnProviderProblem.Key, OwnProviderRules.problem(claude, ok, "", null))
        assertEquals(OwnProviderProblem.Key, OwnProviderRules.problem(claude, ok, "two words", null))
        assertEquals(OwnProviderProblem.Model, OwnProviderRules.problem(claude, ok, fakeKey, "bad model\""))
    }

    @Test
    fun `B-AGENT-OWN-005 the key travels on stdin only, never in the script text`() {
        val script = OwnProviderScript.script(owner)
        val stdin = OwnProviderScript.apply(OwnProvider("https://llm.example.test", "glm-test"), fakeKey)
        assertFalse(script.contains(fakeKey))
        assertTrue(stdin.contains(fakeKey))
        assertTrue(script.contains("/home/oc/.oc-profiles/owner-a/claude/settings.json"))
        assertFalse(OwnProviderScript.clear().contains(fakeKey))
    }
}
