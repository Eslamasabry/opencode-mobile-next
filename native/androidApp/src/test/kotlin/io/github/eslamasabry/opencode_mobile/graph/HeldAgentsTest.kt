package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateChoice
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateGroup
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateItem
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateNetwork
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateOffer
import io.github.eslamasabry.oc.feature.chats.presentation.HomeHeldAgents
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** DOWNLOAD-LINE: which agent updates the Home row says wait for Wi-Fi, and how big they are. */
class HeldAgentsTest {
    private fun item(
        id: String,
        mb: Long?,
        choice: AgentUpdateChoice? = AgentUpdateChoice.Update,
        group: AgentUpdateGroup = AgentUpdateGroup.Optional,
    ) =
        AgentUpdateItem(
            id = AgentId(id), name = id, fromVersion = "1", toVersion = "2", downloadBytes = mb?.let { it * 1_000_000 },
            installedBytes = null, group = group, keepAllowed = true, removeAllowed = true, notKeptReason = null,
            choice = choice, neededWhen = null,
        )

    private fun offer(
        items: List<AgentUpdateItem>,
        network: AgentUpdateNetwork = AgentUpdateNetwork.Metered,
        answered: Boolean = true,
    ) =
        AgentUpdateOffer("k", items, network, null, false, false, answered)

    @Test
    fun `B-DL-411 chosen agents on mobile data are counted with the sheet's own sizes`() {
        val three = listOf(item("claude", 220), item("codex", 230), item("gemini", 212))
        assertEquals(HomeHeldAgents(3, 660), heldAgentsOf(offer(three)))
    }

    @Test
    fun `B-DL-412 OpenCode, kept agents, an unanswered sheet and a free network are not counted`() {
        val rows =
            listOf(
                item("claude", 220),
                item(AgentId.OPENCODE.raw, 130),
                item("codex", 230, AgentUpdateChoice.Keep),
            )
        assertEquals(HomeHeldAgents(1, 220), heldAgentsOf(offer(rows)))
        assertNull(heldAgentsOf(offer(listOf(item("claude", 220)), answered = false)))
        assertNull(heldAgentsOf(offer(listOf(item("claude", 220)), network = AgentUpdateNetwork.Unmetered)))
        assertNull(heldAgentsOf(null))
    }

    @Test
    fun `B-DL-413 a size that is not known is not guessed`() {
        assertNull(heldAgentsOf(offer(listOf(item("claude", 220), item("codex", null)))))
    }
}
