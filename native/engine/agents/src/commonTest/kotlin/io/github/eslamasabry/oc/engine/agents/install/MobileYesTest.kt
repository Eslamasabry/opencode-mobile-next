package io.github.eslamasabry.oc.engine.agents.install

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.removal.RemovalResult
import io.github.eslamasabry.oc.engine.agents.testing.FakeAgentPrefs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** M1-C: the yes on a sheet that said mobile data starts exactly the agents that sheet named, and nothing else. */
class MobileYesTest {
    private val claude = AgentId("claude")
    private val codex = AgentId("codex")
    private val gemini = AgentId("gemini")

    private fun item(id: String) =
        AgentUpdateItem(
            id = AgentId(id), name = id, fromVersion = "1", toVersion = "2", downloadBytes = 200_000_000L,
            installedBytes = null, group = AgentUpdateGroup.Optional, keepAllowed = true, removeAllowed = true,
            notKeptReason = null, choice = null, neededWhen = null,
        )

    private val disk =
        object : AgentChoiceHost {
            override suspend fun inventory() =
                AgentUpdateInventory(listOf(item("claude"), item("codex")), AgentUpdateNetwork.Metered, null)
            override suspend fun hasRoom(id: AgentId) = true
            override suspend fun busy() = false
            override suspend fun remove(id: AgentId): Outcome<RemovalResult> = error("not used")
        }

    private class Jobs : AgentUpdateJobs {
        val started = mutableListOf<AgentId>()
        override var running = false
        override var failed = false
        override suspend fun update(agent: AgentId): Boolean = true.also { started += agent }
    }

    private fun host(engine: AgentUpdateChoiceEngine, behindNow: Set<AgentId>, extraAllowed: Set<AgentId> = emptySet()) =
        object : AgentUpdateHost {
            override suspend fun refresh() {}
            override val readDue = false
            override suspend fun read() {}
            override val installed = listOf(claude, codex, gemini)
            override val behind = behindNow
            override val helperBehind = false
            override val pinKey = "pins"
            override val switchPending = false
            override val installProblem = false
            override val metered = true
            override fun updateAllowed(agent: AgentId) = engine.allowed(agent) || agent in extraAllowed
            override fun meteredYes(agent: AgentId) = engine.mobileYes(agent)
            override fun spendMeteredYes(agent: AgentId) = engine.spendMobileYes(agent)
        }

    private suspend fun sheetAnswered(): AgentUpdateChoiceEngine {
        val engine = AgentUpdateChoiceEngine("app1", FakeAgentPrefs(), disk)
        engine.refresh()
        engine.decide(mapOf(claude to AgentUpdateChoice.Update, codex to AgentUpdateChoice.Update))
        return engine
    }

    @Test
    fun `B-DL-440 on mobile data the agents the sheet named download after its yes`() = runTest {
        val engine = sheetAnswered()
        val jobs = Jobs()
        val updater = AgentAutoUpdater(host(engine, setOf(claude, codex)), jobs) { false }
        updater.changed()
        updater.changed()
        assertEquals(listOf(claude, codex), jobs.started)
    }

    @Test
    fun `B-DL-441 an update that was not on that sheet still waits, and a spent yes is not reused`() = runTest {
        val engine = sheetAnswered()
        val jobs = Jobs()
        // gemini is allowed some other way (a resumed job) but was never on the sheet that said mobile data.
        val updater = AgentAutoUpdater(host(engine, setOf(claude, codex, gemini), setOf(gemini)), jobs) { false }
        repeat(4) { updater.changed() }
        assertEquals(listOf(claude, codex), jobs.started)
        assertEquals(AgentUpdateStep.WaitingMetered, updater.decision.value.step)
        // claude's try ends without finishing and is given back: its yes was spent by the start, so it waits again.
        updater.settle(claude, AgentUpdateStep.Failed)
        updater.changed()
        assertEquals(listOf(claude, codex), jobs.started)
    }
}
