package io.github.eslamasabry.oc.engine.agents.install

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.removal.RemovalResult
import io.github.eslamasabry.oc.engine.agents.testing.FakeAgentPrefs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A copy on the phone that is newer than this app's pin (the agent updated outside the app, a release moved a pin
 * back, a side build was installed) was offered as "Codex · Version 0.172.0 → 0.171.0 · Update" and counted in
 * "Update 2 agents": a downgrade sold as an update, which the automatic update then ran. Versions were compared as
 * text ("is it another version"), never in order. Only a copy older than the pin is an update.
 */
class AgentUpdateNoDowngradeTest {
    private val claude = AgentId("claude")
    private val codex = AgentId("codex")
    private val antigravity = AgentId("antigravity")
    private val helper = AgentId("agent-paseo")
    private val node = AgentId("agent-node")

    private fun agent(id: AgentId, from: String?, to: String) =
        AgentUpdateItem(
            id = id, name = id.raw, fromVersion = from, toVersion = to, downloadBytes = 1_000L,
            installedBytes = null, group = AgentUpdateGroup.Optional, keepAllowed = true, removeAllowed = true,
            notKeptReason = null, choice = null, neededWhen = null,
        )

    /** A shared part as the phone's inventory lists it: no choice, with the rule that says when it comes. */
    private fun part(id: AgentId, from: String?, to: String, rule: AgentNeedRule) =
        AgentUpdateItem(
            id = id, name = id.raw, fromVersion = from, toVersion = to, downloadBytes = 1_000L,
            installedBytes = null, group = AgentUpdateGroup.Needed, keepAllowed = false, removeAllowed = false,
            notKeptReason = AgentKeepBlock.HelperTooNew, choice = null, neededWhen = rule,
        )

    private class Disk(var items: List<AgentUpdateItem>) : AgentChoiceHost {
        override suspend fun inventory() = AgentUpdateInventory(items, AgentUpdateNetwork.Unmetered, null)
        override suspend fun hasRoom(id: AgentId) = true
        override suspend fun busy() = false
        override suspend fun remove(id: AgentId): Outcome<RemovalResult> = error("not used")
    }

    private suspend fun engine(disk: Disk): AgentUpdateChoiceEngine =
        AgentUpdateChoiceEngine("app1", FakeAgentPrefs(), disk).also { it.refresh() }

    private suspend fun offered(from: String?, to: String): Boolean =
        engine(Disk(listOf(agent(codex, from, to)))).offer.value != null

    private class Jobs : AgentUpdateJobs {
        val started = mutableListOf<AgentId>()
        override val running = false
        override val failed = false
        override suspend fun update(agent: AgentId): Boolean = true.also { started += agent }
    }

    /** The updater's host as the phone binds it: an agent moves only when the sheet's answer chose Update for it. */
    private fun host(engine: AgentUpdateChoiceEngine, installedNow: List<AgentId>) =
        object : AgentUpdateHost {
            override suspend fun refresh() = Unit
            override val readDue = false
            override suspend fun read() = Unit
            override val installed = installedNow
            // Even a reading that still names every copy "not the pin": the newer ones must not move.
            override val behind = installedNow.toSet()
            override val helperBehind = false
            override val pinKey = "pins"
            override val switchPending = false
            override val installProblem = false
            override val metered = false
            override fun updateAllowed(agent: AgentId) = engine.allowed(agent)
        }

    @Test
    fun `B-AGENT-UPD-020 an agent newer than this app's pin is never offered, counted or downgraded`() = runTest {
        val disk =
            Disk(
                listOf(
                    agent(codex, "0.172.0", "0.171.0"),
                    agent(antigravity, "1.5.17", "1.5.15"),
                    agent(claude, "2.1.319", "2.1.330"),
                )
            )
        val engine = engine(disk)

        // The sheet's rows: the one agent that is behind. Its button counts these rows ("Update 1 agent").
        val rows = engine.offer.value?.items.orEmpty()
        assertEquals(listOf(claude), rows.map { it.id }, "only the older copy is offered")
        assertEquals(1, rows.count { it.group == AgentUpdateGroup.Optional }, "Update N agents counts 1")

        // No answer can choose Update for a newer copy: it is not on the sheet.
        val all = listOf(codex, antigravity, claude).associateWith { AgentUpdateChoice.Update }
        assertTrue(engine.decide(all) is Outcome.Failed, "an answer naming the newer copies is refused")
        assertTrue(engine.decide(mapOf(claude to AgentUpdateChoice.Update)) is Outcome.Ok)
        assertFalse(engine.allowed(codex))
        assertFalse(engine.allowed(antigravity))

        // The automatic update installs the pin for the older copy only; the newer ones are never moved back.
        val jobs = Jobs()
        val updater = AgentAutoUpdater(host(engine, listOf(codex, antigravity, claude)), jobs) { false }
        repeat(4) { updater.changed() }
        assertEquals(listOf(claude), jobs.started, "a newer copy was downgraded by itself")
    }

    @Test
    fun `B-AGENT-UPD-021 with nothing older than the pin the update sheet stays closed`() = runTest {
        val disk =
            Disk(
                listOf(
                    part(node, "v22.14.0", "v22.12.0", AgentNeedRule.WithAnyUpdate),
                    agent(AgentId.OPENCODE, "2.0.0", "1.9.9"),
                    agent(codex, "0.172.0", "0.171.0"),
                    agent(antigravity, "1.5.17", "1.5.15"),
                )
            )
        val engine = engine(disk)
        assertNull(engine.offer.value, "nothing is older than its pin: no offer, so no sheet and no Home line")

        // A person's Review cannot raise a sheet about nothing either.
        engine.review(raise = true)
        assertNull(engine.offer.value)

        // The sheet still opens for a real update: one copy that is older than its pin.
        disk.items = disk.items + agent(claude, "2.1.319", "2.1.330")
        engine.refresh()
        val offer = assertNotNull(engine.offer.value)
        assertTrue(offer.sheetDue)
        assertEquals(listOf(claude), offer.items.map { it.id })
    }

    @Test
    fun `B-AGENT-UPD-022 only an older version is offered, a newer or unreadable one is not`() = runTest {
        // Older than the pin, compared as numbers part by part (0.9.9 is before 0.10.0), with or without a "v".
        val older =
            listOf(
                "0.171.0" to "0.172.0", "1.5.15" to "1.5.17", "1.9.9" to "2.0.0", "v22.12.0" to "v22.14.0",
                "0.9.9" to "0.10.0", "1.5.15" to "v1.5.17", "v1.5.15" to "1.5.17", "2.0.0-rc.1" to "2.0.0",
            )
        for ((installed, pin) in older) {
            assertTrue(offered(installed, pin), "$installed is older than $pin: an update")
            assertFalse(offered(pin, installed), "$pin is newer than $installed: never an update")
        }
        // A version that cannot be read is never moved: nothing says the pin is newer.
        for (unreadable in listOf("", " ", "unknown", "not a version", "1.5.x", "1..5", "1.2.3.4", null)) {
            assertFalse(offered(unreadable, "1.5.15"), "installed '$unreadable' cannot be read")
        }
        assertFalse(offered("1.5.15", "unknown"), "a pin that cannot be read")
    }

    @Test
    fun `B-AGENT-UPD-023 a part this app cannot run newer is its own needed case, never an Update row`() = runTest {
        val disk =
            Disk(
                listOf(
                    // The agent helper is newer than the pin and outside the tested list: this app cannot run it.
                    part(helper, "0.18.0", "0.17.3", AgentNeedRule.Always),
                    // Node.js is newer too, and runs as it is: nothing requires the older pin.
                    part(node, "v24.23.0", "v24.22.3", AgentNeedRule.WithAnyUpdate),
                    agent(codex, "0.172.0", "0.171.0"),
                )
            )
        val engine = engine(disk)

        val offer = assertNotNull(engine.offer.value, "the required part is told")
        assertEquals(listOf(helper), offer.items.map { it.id }, "the newer Node.js and Codex are left as they are")
        val row = offer.items.single()
        // Its own case: the needed group with its own words, no Update choice, and not in the button's count.
        assertEquals(AgentUpdateGroup.Needed, row.group)
        assertEquals(AgentNeedRule.Always, row.neededWhen)
        assertEquals(AgentKeepBlock.HelperTooNew, row.notKeptReason)
        assertEquals(0, offer.items.count { it.group == AgentUpdateGroup.Optional }, "nothing counts as an Update")
        assertTrue(offer.sheetDue)
        assertTrue(engine.decide(mapOf(helper to AgentUpdateChoice.Update)) is Outcome.Failed, "it is no Update row")
        assertFalse(engine.allowed(helper))
        assertFalse(engine.allowed(codex))
    }
}
