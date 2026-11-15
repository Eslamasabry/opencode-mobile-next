@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.eslamasabry.oc.feature.agents.hub

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.agents.providers.OwnAgentCheckResult
import io.github.eslamasabry.oc.engine.agents.providers.OwnAgentProblem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

/** Issue 147: "Add your own agent" on the Agents page (fake values only). */
class OwnAgentsPresenterTest {
    private val fakeKey = "fake-key-not-real-0000"

    private class Port : PhoneOwnAgents {
        override val state = MutableStateFlow<ImmutableList<OwnAgentView>>(persistentListOf())
        var refuse: OwnAgentProblem? = null
        var checkAnswer = OwnAgentCheckResult.Answered
        override val pickup = MutableStateFlow(OwnAgentPickup.None)
        var pickupAfterSave = OwnAgentPickup.Ready
        val calls = mutableListOf<String>()

        override suspend fun save(id: String?, name: String, start: String, install: String, env: List<Pair<String, String>>): Outcome<OwnAgentSave> {
            calls += "save:$id:$name:${env.size}"
            refuse?.let { return OwnAgentSave.Refused(it).ok() }
            pickup.value = pickupAfterSave
            val newId = id ?: "own-mine"
            state.value = persistentListOf(OwnAgentView(newId, name, start, install.ifEmpty { null }, persistentListOf()))
            return OwnAgentSave.Saved(newId).ok()
        }

        override suspend fun install(id: String): Outcome<Boolean> = true.ok().also { calls += "install:$id" }

        override suspend fun check(id: String): Outcome<OwnAgentCheckResult> = checkAnswer.ok().also { calls += "check:$id" }

        override suspend fun remove(id: String): Outcome<Unit> {
            calls += "remove:$id"
            state.value = persistentListOf()
            return Unit.ok()
        }
    }

    @Test
    fun `B-AGENT-OWN-070 add, check, install and remove an own agent from its sheet, values never kept`() =
        runTest(UnconfinedTestDispatcher()) {
            val port = Port()
            var reads = 0
            val presenter = OwnAgentsPresenter(backgroundScope, port) { reads++ }
            presenter.addOwnAgent()
            assertTrue(presenter.state.value.sheetOpen)
            assertNull(presenter.state.value.editingId)
            presenter.saveOwnAgent("Mine", "mine --acp", "npm i -g mine", listOf("MY_KEY" to fakeKey))
            // Saved: the sheet stays on the saved agent so Check and Install follow.
            assertEquals("own-mine", presenter.state.value.editingId)
            assertEquals(1, reads)
            presenter.checkOwnAgent()
            assertEquals(OwnAgentCheckResult.Answered, presenter.state.value.checked)
            presenter.installOwnAgent()
            assertEquals(true, presenter.state.value.installed)
            assertFalse(presenter.state.value.toString().contains(fakeKey))
            presenter.removeOwnAgent()
            assertFalse(presenter.state.value.sheetOpen)
            assertTrue(presenter.state.value.agents.isEmpty())
            assertEquals(listOf("save:null:Mine:1", "check:own-mine", "install:own-mine", "remove:own-mine"), port.calls)
            assertEquals(2, reads)
        }

    @Test
    fun `B-AGENT-OWN-071 a refused value stays on the sheet with its reason and a failed check says why`() =
        runTest(UnconfinedTestDispatcher()) {
            val port = Port().apply { refuse = OwnAgentProblem.Start }
            val presenter = OwnAgentsPresenter(backgroundScope, port) {}
            presenter.addOwnAgent()
            presenter.saveOwnAgent("Mine", "mine \"open", "", emptyList())
            assertEquals(OwnAgentProblem.Start, presenter.state.value.problem)
            assertTrue(presenter.state.value.sheetOpen)
            presenter.editedOwnAgent()
            assertNull(presenter.state.value.problem)

            port.refuse = null
            port.checkAnswer = OwnAgentCheckResult.NotAcp
            presenter.saveOwnAgent("Mine", "mine", "", emptyList())
            presenter.checkOwnAgent()
            assertEquals(OwnAgentCheckResult.NotAcp, presenter.state.value.checked)
            assertFalse(presenter.state.value.working)
        }

    @Test
    fun `B-AGENT-OWN-072 after a save the sheet says when the helper takes the agent, and only then`() =
        runTest(UnconfinedTestDispatcher()) {
            val port = Port().apply { pickupAfterSave = OwnAgentPickup.WhenIdle }
            val presenter = OwnAgentsPresenter(backgroundScope, port) {}
            presenter.addOwnAgent()
            assertEquals(OwnAgentPickup.None, presenter.state.value.pickup)
            presenter.saveOwnAgent("Mine", "mine acp", "", emptyList())
            assertEquals(OwnAgentPickup.WhenIdle, presenter.state.value.pickup)
            // The helper took it once the running task ended.
            port.pickup.value = OwnAgentPickup.Ready
            assertEquals(OwnAgentPickup.Ready, presenter.state.value.pickup)
            // Opening the sheet again starts without an old answer.
            presenter.closeOwnAgent()
            presenter.editOwnAgent("own-mine")
            assertEquals(OwnAgentPickup.None, presenter.state.value.pickup)
        }
}
