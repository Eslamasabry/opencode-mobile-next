package io.github.eslamasabry.oc.feature.chats

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.install.AgentRemovalFacts
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateChoice
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateChoices
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateGroup
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateItem
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateNetwork
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateOffer
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateStarted
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow

internal class FakeAgentUpdateChoices(initial: AgentUpdateOffer? = dueOffer()) : AgentUpdateChoices {
    override val offer = MutableStateFlow(initial)
    val reviews = mutableListOf<Boolean>()
    var finishReading: CompletableDeferred<Unit>? = null

    val current: AgentUpdateOffer get() = checkNotNull(offer.value)

    override suspend fun review(raise: Boolean): Outcome<Unit> {
        reviews += raise
        finishReading?.await()
        if (raise) offer.value = current.copy(sheetDue = true)
        return Outcome.Ok(Unit)
    }

    override suspend fun notNow(): Outcome<Unit> {
        offer.value = current.copy(answered = true, sheetDue = false)
        return Outcome.Ok(Unit)
    }

    override suspend fun decide(choices: Map<AgentId, AgentUpdateChoice>): Outcome<AgentUpdateStarted> {
        val items = current.items.map { it.copy(choice = choices.getValue(it.id)) }
        offer.value = current.copy(items = items, answered = true, sheetDue = false)
        fun ids(choice: AgentUpdateChoice) = choices.filterValues { it == choice }.keys.toList()
        return Outcome.Ok(AgentUpdateStarted(ids(AgentUpdateChoice.Update), ids(AgentUpdateChoice.Remove), ids(AgentUpdateChoice.Keep)))
    }

    override fun removalFacts(id: AgentId): AgentRemovalFacts = error("Unexpected removal")

    companion object {
        const val MIB = 1024L * 1024

        fun dueOffer(): AgentUpdateOffer {
            val item = AgentUpdateItem(
                id = AgentId("codex"), name = "Codex", fromVersion = "1", toVersion = "2",
                downloadBytes = 80 * MIB, installedBytes = 60 * MIB, group = AgentUpdateGroup.Optional,
                keepAllowed = true, removeAllowed = true, notKeptReason = null, choice = null, neededWhen = null,
            )
            return AgentUpdateOffer(
                appUpdateKey = "1", items = listOf(item), network = AgentUpdateNetwork.Unmetered,
                freeBytes = 100 * MIB, neededGoesAhead = false, sheetDue = true, answered = false,
            )
        }
    }
}
