package io.github.eslamasabry.oc.engine.agents.install

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.removal.RemovalResult
import io.github.eslamasabry.oc.engine.agents.testing.FakeAgentPrefs
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** METERED-YES: the yes for OpenCode on mobile data starts the shared parts it waits for, and says what refused. */
class MeteredYesTest {
    private fun item(
        id: String,
        group: AgentUpdateGroup = AgentUpdateGroup.Optional,
        need: AgentNeedRule? = null,
    ) =
        AgentUpdateItem(
            id = AgentId(id), name = id, fromVersion = "1", toVersion = "2", downloadBytes = 100_000_000L,
            installedBytes = null, group = group, keepAllowed = true, removeAllowed = true, notKeptReason = null,
            choice = null, neededWhen = need,
        )

    private var sharedInstalled = false
    private val host =
        object : AgentChoiceHost {
            override suspend fun inventory() =
                AgentUpdateInventory(
                    items = listOf(
                        item(AgentId.OPENCODE.raw),
                        item("claude"), item("codex"), item("gemini"), item("qwen"),
                        item("paseo", AgentUpdateGroup.Needed, AgentNeedRule.WithAnyUpdate),
                    ).filterNot { sharedInstalled && it.group == AgentUpdateGroup.Needed },
                    network = AgentUpdateNetwork.Metered,
                    freeBytes = null,
                )

            override suspend fun hasRoom(id: AgentId) = true
            override suspend fun busy() = false
            override suspend fun remove(id: AgentId): Outcome<RemovalResult> = error("not used")
        }

    private suspend fun engineWithFourAgentsChosen(): AgentUpdateChoiceEngine {
        val engine = AgentUpdateChoiceEngine("app1", FakeAgentPrefs(), host)
        engine.refresh()
        val ids = listOf(AgentId.OPENCODE.raw, "claude", "codex", "gemini", "qwen")
        engine.decide(ids.associate { AgentId(it) to AgentUpdateChoice.Update })
        return engine
    }

    @Test
    fun `B-DL-421 the yes for OpenCode starts the shared job at once even with four agents chosen`() = runTest {
        val engine = engineWithFourAgentsChosen()
        var started = 0
        assertTrue(engine.startShared(meteredAllowed = true, alongsideAgents = true) { started++; true })
        assertEquals(1, started)
    }

    @Test
    fun `B-DL-422 without the yes the shared job still waits for the agents' own install`() = runTest {
        val engine = engineWithFourAgentsChosen()
        var started = 0
        assertFalse(engine.startShared(meteredAllowed = true) { started++; true })
        assertFalse(engine.startShared(meteredAllowed = false, alongsideAgents = true) { started++; true })
        assertEquals(0, started)
    }

    @Test
    fun `B-DL-423 OpenCode waits for the shared parts the offer lists, and the refusal names that`() = runTest {
        val engine = engineWithFourAgentsChosen()
        assertTrue(engine.openCodeNeedsShared)
        var started = 0
        assertFalse(engine.startIfChosen(AgentId.OPENCODE) { started++; true })
        assertEquals(0, started)
        assertEquals(StartRefusal.NeedsSharedParts, startRefusal(allowed = true, needsShared = true) { false })
        assertEquals(StartRefusal.NotChosen, startRefusal(allowed = false, needsShared = true) { false })
        assertEquals(StartRefusal.WorkRunning, startRefusal(allowed = true, needsShared = false) { true })
        assertEquals(null, startRefusal(allowed = true, needsShared = false) { false })
    }

    @Test
    fun `B-DL-433 one yes survives a refused helper start and releases both named updates`() = runTest {
        val engine = engineWithFourAgentsChosen()
        val starts = mutableListOf<String>()
        assertFalse(engine.startShared(meteredAllowed = true, alongsideAgents = true) { false })
        engine.refresh()
        assertFalse(engine.startIfChosen(AgentId.OPENCODE) { starts.add("OpenCode") })
        assertTrue(engine.startShared(meteredAllowed = false) { starts.add("helper") })
        assertFalse(engine.startShared(meteredAllowed = false) { starts.add("duplicate") })
        sharedInstalled = true
        engine.refresh()
        assertTrue(engine.startIfChosen(AgentId.OPENCODE) { starts.add("OpenCode") })
        assertEquals(listOf("helper", "OpenCode"), starts)
    }
}
