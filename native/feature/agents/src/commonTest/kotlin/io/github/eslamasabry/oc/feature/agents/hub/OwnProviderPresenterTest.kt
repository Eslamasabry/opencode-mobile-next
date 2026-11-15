@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.eslamasabry.oc.feature.agents.hub

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.agents.providers.OwnProviderProblem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/** Issue 147: the own-provider sheet on the Agents page (fake values only). */
class OwnProviderPresenterTest {
    private val fakeKey = "fake-key-not-real-0000"

    private class Port(var answer: Outcome<OwnProviderSave> = OwnProviderSave.Saved.ok()) : PhoneOwnProviders {
        override val state = MutableStateFlow<ImmutableMap<String, OwnProviderView>>(persistentMapOf())
        override val supported: Set<String> = setOf("claude")
        val saved = mutableListOf<List<String>>()
        var removed = 0

        override suspend fun save(agent: String, endpoint: String, key: String, model: String): Outcome<OwnProviderSave> {
            saved += listOf(agent, endpoint, key, model)
            if ((answer as? Outcome.Ok)?.value == OwnProviderSave.Saved) state.value = persistentMapOf(agent to OwnProviderView(endpoint, model.ifEmpty { null }))
            return answer
        }

        override suspend fun remove(agent: String): Outcome<Unit> {
            removed++
            state.value = persistentMapOf()
            return Unit.ok()
        }
    }

    @Test
    fun `B-AGENT-OWN-030 a saved provider closes the sheet, re-reads the phone's agents and never keeps the key`() =
        runTest(UnconfinedTestDispatcher()) {
            val port = Port()
            var reads = 0
            val presenter = OwnProviderPresenter(backgroundScope, port) { reads++ }
            presenter.openOwnProvider("claude")
            assertEquals("claude", presenter.state.value.editing)
            presenter.saveOwnProvider("claude", "https://llm.example.test", fakeKey, "glm-test")
            assertEquals(listOf(listOf("claude", "https://llm.example.test", fakeKey, "glm-test")), port.saved)
            assertNull(presenter.state.value.editing)
            assertEquals(1, reads)
            assertEquals(OwnProviderView("https://llm.example.test", "glm-test"), presenter.state.value.saved["claude"])
            assertFalse(presenter.state.value.toString().contains(fakeKey))

            presenter.openOwnProvider("claude")
            presenter.removeOwnProvider("claude")
            assertEquals(1, port.removed)
            assertTrue(presenter.state.value.saved.isEmpty())
            assertEquals(2, reads)
        }

    @Test
    fun `B-AGENT-OWN-031 a refused value stays on the sheet with its reason, a failed save says so, unsupported agents open nothing`() =
        runTest(UnconfinedTestDispatcher()) {
            val port = Port(OwnProviderSave.Refused(OwnProviderProblem.Endpoint).ok())
            var reads = 0
            val presenter = OwnProviderPresenter(backgroundScope, port) { reads++ }
            presenter.openOwnProvider("codex")
            assertNull(presenter.state.value.editing)

            presenter.openOwnProvider("claude")
            presenter.saveOwnProvider("claude", "http://llm.example.test", fakeKey, "")
            assertEquals("claude", presenter.state.value.editing)
            assertEquals(OwnProviderProblem.Endpoint, presenter.state.value.problem)
            presenter.editedOwnProvider()
            assertNull(presenter.state.value.problem)

            port.answer = Outcome.Failed(Failure.Unexpected())
            presenter.saveOwnProvider("claude", "https://llm.example.test", fakeKey, "")
            assertEquals("claude", presenter.state.value.editing)
            assertTrue(presenter.state.value.failure is Failure.Unexpected)
            assertEquals(0, reads)
            assertFalse(presenter.state.value.working)
        }

    @Test
    fun `B-AGENT-OWN-032 without a phone Linux nothing is offered`() = runTest(UnconfinedTestDispatcher()) {
        val presenter = OwnProviderPresenter(backgroundScope, null) {}
        assertFalse(presenter.state.value.available)
        presenter.openOwnProvider("claude")
        assertNull(presenter.state.value.editing)
        presenter.openAgentsHelp()
        assertTrue(presenter.state.value.helpOpen)
    }
}
