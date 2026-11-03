package io.github.eslamasabry.oc.feature.agents.hub

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Agents hub's logic (no Compose, no platform types). One tap admits one operation; a failed step never
 * navigates or retries on its own. A computer is saved only when the person taps Save — the check alone saves
 * nothing (Flutter `probePaseoConnection` then save).
 */
public class AgentsPresenter(
    private val scope: CoroutineScope,
    private val computers: AgentComputers,
    phone: PhoneAgents,
    private val onBack: () -> Unit,
    private val openChat: (ConversationRef) -> Unit,
    private val openComputerPage: (ProfileId) -> Unit = {},
) : AgentsActions {
    private val local = MutableStateFlow(AgentsUi())

    public val state: StateFlow<AgentsUi> =
        combine(local, computers.computers, phone.state) { own, saved, phoneState ->
                own.copy(computers = saved, phone = phoneState)
            }
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                local.value.copy(computers = computers.computers.value, phone = phone.state.value),
            )

    override fun back(): Unit = onBack()

    override fun startAdding() {
        local.update { it.copy(adding = true, check = null, issue = null) }
    }

    override fun cancelAdding() {
        if (local.value.busy) return
        local.update { it.copy(adding = false, check = null, issue = null) }
    }

    override fun edited() {
        if (local.value.check != null) local.update { it.copy(check = null) }
    }

    override fun check(address: String, password: String, folder: String): Unit = operation {
        when (val result = computers.check(address.trim(), password, folder.trim())) {
            is Outcome.Ok -> local.update { it.copy(check = result.value) }
            is Outcome.Failed -> failed(AgentsIssue.CheckFailed, result.failure)
        }
    }

    override fun add(name: String, address: String, password: String, folder: String): Unit = operation {
        when (val result = computers.add(name.trim(), address.trim(), password, folder.trim())) {
            is Outcome.Ok -> {
                val ready = (local.value.check as? ComputerCheck.Ready)?.agents
                local.update {
                    it.copy(
                        adding = false,
                        check = null,
                        savedGeneration = it.savedGeneration + 1,
                        issue = AgentsProblem(AgentsIssue.Added),
                        ready = if (ready != null) (it.ready + (result.value to ready)).toPersistentMap() else it.ready,
                    )
                }
            }
            is Outcome.Failed -> failed(AgentsIssue.AddFailed, result.failure)
        }
    }

    override fun readAgents(id: ProfileId): Unit = operation {
        when (val result = computers.agents(id)) {
            is Outcome.Ok -> local.update { it.copy(ready = (it.ready + (id to result.value)).toPersistentMap()) }
            is Outcome.Failed -> failed(AgentsIssue.AgentsFailed, result.failure)
        }
    }

    override fun newConversation(id: ProfileId): Unit = operation {
        when (val result = computers.newConversation(id)) {
            is Outcome.Ok -> openChat(result.value)
            is Outcome.Failed -> failed(AgentsIssue.StartFailed, result.failure)
        }
    }

    override fun openComputer(id: ProfileId): Unit = openComputerPage(id)

    private fun operation(block: suspend () -> Unit) {
        if (local.value.busy) return
        local.update { it.copy(busy = true, issue = null) }
        scope.launch {
            try {
                block()
            } finally {
                local.update { it.copy(busy = false) }
            }
        }
    }

    private fun failed(kind: AgentsIssue, failure: Failure) {
        local.update { it.copy(issue = AgentsProblem(kind, failure)) }
    }
}
