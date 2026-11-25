package io.github.eslamasabry.oc.engine.agents.install

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.removal.RemovalResult
import io.github.eslamasabry.oc.engine.agents.testing.FakeAgentPrefs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    private fun diskFor(network: AgentUpdateNetwork) =
        object : AgentChoiceHost {
            override suspend fun inventory() =
                AgentUpdateInventory(listOf(item("claude"), item("codex")), network, null)
            override suspend fun hasRoom(id: AgentId) = true
            override suspend fun busy() = false
            override suspend fun remove(id: AgentId): Outcome<RemovalResult> = error("not used")
        }

    private val disk = diskFor(AgentUpdateNetwork.Metered)

    private class Jobs : AgentUpdateJobs {
        val started = mutableListOf<AgentId>()
        override var running = false
        override var failed = false
        override suspend fun update(agent: AgentId): Boolean = true.also { started += agent }
    }

    private fun host(
        engine: AgentUpdateChoiceEngine,
        behindNow: Set<AgentId>,
        extraAllowed: Set<AgentId> = emptySet(),
        meteredNow: () -> Boolean = { true },
    ) =
        object : AgentUpdateHost {
            override suspend fun refresh() = Unit
            override val readDue = false
            override suspend fun read() = Unit
            override val installed = listOf(claude, codex, gemini)
            override val behind = behindNow
            override val helperBehind = false
            override val pinKey = "pins"
            override val switchPending = false
            override val installProblem = false
            override val metered get() = meteredNow()
            override fun updateAllowed(agent: AgentId) = engine.allowed(agent) || agent in extraAllowed
            override fun meteredYes(agent: AgentId) = engine.mobile.covers(agent)
            override fun spendMeteredYes(agent: AgentId) = engine.mobile.spend(agent)
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

    @Test
    fun `B-DL-446 the yes on Home's line starts exactly the agents it names, another still waits`() = runTest {
        // The sheet was never answered on mobile data (an app restart): nothing is covered yet.
        val engine = AgentUpdateChoiceEngine("app1", FakeAgentPrefs(), disk)
        engine.refresh()
        engine.decide(mapOf(claude to AgentUpdateChoice.Update, codex to AgentUpdateChoice.Update))
        engine.mobile.record(emptyMap())
        val jobs = Jobs()
        val updater = AgentAutoUpdater(host(engine, setOf(claude, codex)), jobs) { false }
        updater.changed()
        assertEquals(emptyList(), jobs.started)
        engine.mobile.grant(mapOf(claude to "2"))
        repeat(3) { updater.changed() }
        assertEquals(listOf(claude), jobs.started, "codex was not named by the yes")
    }

    @Test
    fun `B-DL-448 on mobile data waitForWifi holds updates until unmetered then starts them`() = runTest {
        val engine = AgentUpdateChoiceEngine("app1", FakeAgentPrefs(), disk)
        engine.refresh()
        val chosen = mapOf(claude to AgentUpdateChoice.Update, codex to AgentUpdateChoice.Update)
        assertTrue(engine.decide(chosen, waitForWifi = true) is Outcome.Ok)
        assertTrue(engine.allowed(claude) && engine.allowed(codex))
        assertFalse(engine.mobile.covers(claude))
        assertFalse(engine.mobile.covers(codex))
        var metered = true
        val jobs = Jobs()
        val updater = AgentAutoUpdater(host(engine, setOf(claude, codex), meteredNow = { metered }), jobs) { false }
        updater.changed()
        assertEquals(emptyList(), jobs.started)
        assertEquals(AgentUpdateStep.WaitingMetered, updater.decision.value.step)
        metered = false
        updater.changed()
        updater.changed()
        assertEquals(listOf(claude, codex), jobs.started)
    }

    @Test
    fun `B-DL-449 waitForWifi choice survives an app restart and starts on unmetered`() = runTest {
        val prefs = FakeAgentPrefs()
        val first = AgentUpdateChoiceEngine("app1", prefs, disk)
        first.refresh()
        val chosen = mapOf(claude to AgentUpdateChoice.Update, codex to AgentUpdateChoice.Update)
        assertTrue(first.decide(chosen, waitForWifi = true) is Outcome.Ok)
        assertFalse(first.mobile.covers(claude))
        val restarted = AgentUpdateChoiceEngine("app1", prefs, disk)
        restarted.refresh()
        assertTrue(restarted.offer.value?.answered == true)
        assertTrue(restarted.allowed(claude) && restarted.allowed(codex))
        assertFalse(restarted.mobile.covers(claude))
        var metered = true
        val jobs = Jobs()
        val updater = AgentAutoUpdater(host(restarted, setOf(claude, codex), meteredNow = { metered }), jobs) { false }
        updater.changed()
        assertEquals(emptyList(), jobs.started)
        assertEquals(AgentUpdateStep.WaitingMetered, updater.decision.value.step)
        metered = false
        updater.changed()
        updater.changed()
        assertEquals(listOf(claude, codex), jobs.started)
    }

    @Test
    fun `B-DL-450 waitForWifi off downloads on mobile data and failures are values`() = runTest {
        val engine = AgentUpdateChoiceEngine("app1", FakeAgentPrefs(), disk)
        engine.refresh()
        val chosen = mapOf(claude to AgentUpdateChoice.Update, codex to AgentUpdateChoice.Update)
        assertTrue(engine.decide(chosen, waitForWifi = false) is Outcome.Ok)
        assertTrue(engine.mobile.covers(claude) && engine.mobile.covers(codex))
        val jobs = Jobs()
        val updater = AgentAutoUpdater(host(engine, setOf(claude, codex)), jobs) { false }
        updater.changed()
        updater.changed()
        assertEquals(listOf(claude, codex), jobs.started)

        val wifiEngine = AgentUpdateChoiceEngine("app1", FakeAgentPrefs(), diskFor(AgentUpdateNetwork.Unmetered))
        wifiEngine.refresh()
        assertTrue(wifiEngine.decide(chosen, waitForWifi = false) is Outcome.Ok)
        assertFalse(wifiEngine.mobile.covers(claude))
        val wifiJobs = Jobs()
        val wifiUpdater =
            AgentAutoUpdater(host(wifiEngine, setOf(claude, codex), meteredNow = { false }), wifiJobs) { false }
        wifiUpdater.changed()
        wifiUpdater.changed()
        assertEquals(listOf(claude, codex), wifiJobs.started)

        val refusing = FakeAgentPrefs().apply { refuseWrites = true }
        val failEngine = AgentUpdateChoiceEngine("app1", refusing, disk)
        failEngine.refresh()
        val failed = failEngine.decide(chosen, waitForWifi = true)
        assertTrue(failed is Outcome.Failed && failed.failure is Failure.StorageRefused)
        assertFalse(failEngine.allowed(claude))
        assertFalse(failEngine.mobile.covers(claude))
    }
}
