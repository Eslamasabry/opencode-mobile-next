package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateChoice
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateGroup
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateItem
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateNetwork
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateOffer
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateStep
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadFact
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadKind
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadPhase
import io.github.eslamasabry.oc.feature.chats.presentation.HomeHeldAgents
import io.github.eslamasabry.oc.feature.chats.presentation.HomeHelperStep
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * DOWNLOAD-LINE follows the real state (10114, journey M1: Update 3 agents on mobile data, then Download now): a
 * running install is never "waiting for Wi-Fi", the running agent is not still counted as waiting, and every item the
 * person said yes to is named.
 */
class DownloadsFollowTest {
    private fun item(id: String, mb: Long, choice: AgentUpdateChoice? = AgentUpdateChoice.Update) =
        AgentUpdateItem(
            id = AgentId(id), name = id, fromVersion = "1", toVersion = "2", downloadBytes = mb * 1_000_000,
            installedBytes = null, group = AgentUpdateGroup.Optional, keepAllowed = true, removeAllowed = true,
            notKeptReason = null, choice = choice, neededWhen = null,
        )

    private fun offer(items: List<AgentUpdateItem>, network: AgentUpdateNetwork = AgentUpdateNetwork.Metered) =
        AgentUpdateOffer("k", items, network, null, false, false, true)

    @Test
    fun `B-DL-428 an install that runs is Updating on Home, whatever the updater says for the next agent`() {
        // 10114 23:51:51: the updater read WaitingMetered for the agents left, while the helper downloaded 400 MB.
        assertEquals(HomeHelperStep.Updating, helperStepOf(AgentUpdateStep.WaitingMetered, installing = true, networkRead = true))
        assertEquals(HomeHelperStep.Waiting, helperStepOf(AgentUpdateStep.WaitingMetered, installing = false, networkRead = true))
        assertEquals(HomeHelperStep.None, helperStepOf(AgentUpdateStep.WaitingMetered, installing = false, networkRead = false))
        assertEquals(HomeHelperStep.Failed, helperStepOf(AgentUpdateStep.Failed, installing = false, networkRead = true))
    }

    @Test
    fun `B-DL-429 the updater sees the agents' own install job, not only the setup page's`() {
        assertEquals(true, agentsJobRunning(setupRunning = false, installing = true))
        assertEquals(true, agentsJobRunning(setupRunning = true, installing = false))
        assertEquals(false, agentsJobRunning(setupRunning = false, installing = false))
    }

    @Test
    fun `B-DL-430 the agent that runs now leaves the more-agents-wait count`() {
        val rows = listOf(item("claude", 360), item("codex", 180), item("antigravity", 130))
        assertEquals(HomeHeldAgents(3, 670), heldAgentsOf(offer(rows)))
        assertEquals(HomeHeldAgents(2, 310), heldAgentsOf(offer(rows), running = AgentId("claude")))
    }

    @Test
    fun `B-DL-431 OpenCode chosen for update is named in the line even before its own updater says it waits`() {
        val rows = listOf(item(AgentId.OPENCODE.raw, 130), item("claude", 360))
        val helper = HomeDownloadFact(HomeDownloadKind.Helper, HomeDownloadPhase.Waiting)
        val named = withChosenOpenCode(listOf(helper), offer(rows))
        assertEquals(listOf(HomeDownloadKind.OpenCode, HomeDownloadKind.Helper), named.map { it.kind }.sortedBy { it.ordinal })
        // Kept, on Wi-Fi, or with a line of its own already: nothing is added.
        val kept = listOf(item(AgentId.OPENCODE.raw, 130, AgentUpdateChoice.Keep), item("claude", 360))
        assertEquals(listOf(helper), withChosenOpenCode(listOf(helper), offer(kept)))
        assertEquals(listOf(helper), withChosenOpenCode(listOf(helper), offer(rows, AgentUpdateNetwork.Unmetered)))
        val own = HomeDownloadFact(HomeDownloadKind.OpenCode, HomeDownloadPhase.Running(40))
        assertEquals(listOf(own), withChosenOpenCode(listOf(own), offer(rows)))
    }
}
