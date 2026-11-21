package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateChoice
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateGroup
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateItem
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateNetwork
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateOffer
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadSizes
import io.github.eslamasabry.oc.feature.chats.presentation.HomeHeldAgents

/**
 * The agents the person chose to update on the sheet that now wait for Wi-Fi, with their size as the sheet shows it
 * (decimal MB). OpenCode and the shared parts have their own lines and are not counted. Null when none waits, when the
 * network is free, or when a size is unknown (a total with a hole would be a guess).
 */
internal fun heldAgentsOf(
    offer: AgentUpdateOffer?,
    running: AgentId? = null,
    liveNetwork: AgentUpdateNetwork? = null,
    consented: (AgentId) -> Boolean = { false },
): HomeHeldAgents? {
    val held = heldItems(offer, running, liveNetwork, consented)
    val bytes = held.map { it.downloadBytes }
    return if (held.isEmpty() || bytes.any { it == null }) null
    else HomeHeldAgents(held.size, HomeDownloadSizes.roundedMb(bytes.sumOf { it ?: 0L }))
}

/** The (agent, version) pairs the agents-only line names: the same filter as [heldAgentsOf]. */
internal fun heldAgentVersionsOf(
    offer: AgentUpdateOffer?,
    running: AgentId? = null,
    consented: (AgentId) -> Boolean = { false },
): Map<AgentId, String> = heldItems(offer, running, null, consented).associate { it.id to it.toVersion }

private fun heldItems(
    offer: AgentUpdateOffer?,
    running: AgentId?,
    liveNetwork: AgentUpdateNetwork?,
    consented: (AgentId) -> Boolean,
): List<AgentUpdateItem> {
    val net = liveNetwork ?: offer?.network
    val waiting = offer?.takeIf { it.answered && net != AgentUpdateNetwork.Unmetered }
    return waiting?.items.orEmpty().filter {
        it.group == AgentUpdateGroup.Optional && it.id != AgentId.OPENCODE && it.id != running &&
            it.choice == AgentUpdateChoice.Update && !consented(it.id)
    }
}
