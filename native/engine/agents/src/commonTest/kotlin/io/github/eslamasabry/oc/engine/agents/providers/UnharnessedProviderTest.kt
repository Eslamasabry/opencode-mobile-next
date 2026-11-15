package io.github.eslamasabry.oc.engine.agents.providers

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCommandApproval
import io.github.eslamasabry.oc.engine.agents.catalog.AgentSetupMethod
import io.github.eslamasabry.oc.engine.agents.testing.FakeAgentPrefs
import io.github.eslamasabry.oc.engine.agents.testing.FakeAgentSecrets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class UnharnessedProviderTest {
    private val agent = AgentId("unharnessed")
    private val owner = ProfileId("owner-a")

    @Test
    fun `B-AGENT-UH-010 the pending download is unavailable and the descriptor owns never asks`() {
        val descriptor = AgentCatalog.builtIn.byId(agent)!!
        assertEquals(AgentCommandApproval.NeverAsks, descriptor.commandApproval)
        assertEquals(AgentSetupMethod.KeyOrLocalServer, descriptor.setupMethod)
        assertNull(descriptor.recipe)
        assertTrue(descriptor.runsOnPhoneHost)
        assertFalse(
            descriptor.installableOn(
                io.github.eslamasabry.oc.engine.agents.catalog.AgentArchitecture.Arm64
            )
        )
    }

    @Test
    fun `B-AGENT-UH-011 a remote provider needs a key while a local server needs a model`() {
        assertEquals(
            OwnProviderProblem.Key,
            OwnProviderRules.problem(agent, "https://example.invalid/v1", "", "fixture-model"),
        )
        assertEquals(
            OwnProviderProblem.Model,
            OwnProviderRules.problem(agent, "http://localhost:8000/v1", "", null),
        )
        assertNull(OwnProviderRules.problem(agent, "http://localhost:8000/v1", "", "fixture-model"))
        assertNull(
            OwnProviderRules.problem(
                agent,
                "https://example.invalid/v1",
                "fixture-value",
                "fixture-model",
            )
        )
        assertEquals(
            OwnProviderProblem.Endpoint,
            OwnProviderRules.problem(
                agent,
                "http://example.invalid/v1",
                "fixture-value",
                "fixture-model",
            ),
        )
    }

    @Test
    fun `B-AGENT-UH-012 configured means a saved key or local server and deletion forgets it`() =
        runTest {
            val prefs = FakeAgentPrefs()
            val secrets = FakeAgentSecrets()
            val store = OwnProviderStore(prefs, secrets) {}
            assertTrue((store.configured(owner) as Outcome.Ok).value.isEmpty())
            assertIs<Outcome.Ok<Unit>>(
                store.save(
                    owner,
                    agent,
                    "https://example.invalid/v1",
                    "fixture-value",
                    "fixture-model",
                )
            )
            assertEquals(setOf(agent), (store.configured(owner) as Outcome.Ok).value)
            assertTrue(prefs.values.values.none { it.toString().contains("fixture-value") })
            secrets.values.clear()
            assertTrue((store.configured(owner) as Outcome.Ok).value.isEmpty())
            assertIs<Outcome.Ok<Unit>>(
                store.save(owner, agent, "http://localhost:8000/v1", "", "fixture-model")
            )
            assertEquals(setOf(agent), (store.configured(owner) as Outcome.Ok).value)
            assertIs<Outcome.Ok<Unit>>(store.remove(owner, agent))
            assertTrue((store.configured(owner) as Outcome.Ok).value.isEmpty())
            assertTrue(prefs.values.isEmpty())
            assertTrue(secrets.values.isEmpty())
        }
}
