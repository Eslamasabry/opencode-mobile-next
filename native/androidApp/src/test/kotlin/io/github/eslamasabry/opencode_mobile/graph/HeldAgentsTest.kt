package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.install.AgentNeedRule
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
        need: AgentNeedRule? = null,
    ) =
        AgentUpdateItem(
            id = AgentId(id), name = id, fromVersion = "1", toVersion = "2", downloadBytes = mb?.let { it * 1_000_000 },
            installedBytes = null, group = group, keepAllowed = true, removeAllowed = true, notKeptReason = null,
            choice = choice, neededWhen = need,
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

    @Test
    fun `B-DL-426 OpenCode with its helper is the sheet's sizes added, or the usual sizes without them`() {
        val needed =
            item("paseo", 330, choice = null, group = AgentUpdateGroup.Needed, need = AgentNeedRule.WithAnyUpdate)
        assertEquals(460, openCodeWithHelperMb(offer(listOf(item(AgentId.OPENCODE.raw, 130), needed))))
        assertEquals(460, openCodeWithHelperMb(offer(listOf(item(AgentId.OPENCODE.raw, null), needed))))
        assertNull(openCodeWithHelperMb(offer(listOf(item(AgentId.OPENCODE.raw, 130)))))
        assertNull(openCodeWithHelperMb(null))
    }
}

/** M1-C: Home's held-agents line follows the live network and the sheet's yes. */
class HeldAgentsLiveTest {
    private fun item(id: String) =
        AgentUpdateItem(
            id = AgentId(id), name = id, fromVersion = "1", toVersion = "2", downloadBytes = 200_000_000L,
            installedBytes = null, group = AgentUpdateGroup.Optional, keepAllowed = true, removeAllowed = true,
            notKeptReason = null, choice = AgentUpdateChoice.Update, neededWhen = null,
        )

    private val offer =
        AgentUpdateOffer(
            "k", listOf(item("claude"), item("codex"), item("gemini")), AgentUpdateNetwork.Metered, null,
            false, false, true,
        )

    @Test
    fun `B-DL-442 when the network turns free the line stops saying waiting for Wi-Fi at once`() {
        // The offer still carries the network of its last read; the live network is what counts.
        assertNull(heldAgentsOf(offer, liveNetwork = AgentUpdateNetwork.Unmetered))
        assertEquals(HomeHeldAgents(3, 600), heldAgentsOf(offer, liveNetwork = AgentUpdateNetwork.Metered))
    }

    @Test
    fun `B-DL-443 agents the sheet's yes covers are not held, the others still are`() {
        assertNull(heldAgentsOf(offer, consented = { true }))
        assertEquals(HomeHeldAgents(1, 200), heldAgentsOf(offer, consented = { it.raw != "gemini" }))
    }

    @Test
    fun `B-DL-447 the yes names the same agents and versions as the line, no more`() {
        assertEquals(
            mapOf(AgentId("claude") to "2", AgentId("gemini") to "2"),
            heldAgentVersionsOf(offer, consented = { it.raw == "codex" }),
        )
        assertEquals(
            mapOf(AgentId("codex") to "2"),
            heldAgentVersionsOf(offer, running = AgentId("claude"), consented = { it.raw == "gemini" }),
        )
    }
}
