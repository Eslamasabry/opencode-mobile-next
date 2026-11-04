package io.github.eslamasabry.oc.feature.agenttools.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.model.AgentFeature
import io.github.eslamasabry.oc.core.model.HostUpdatePhase
import io.github.eslamasabry.oc.core.model.HostUpdateResult
import io.github.eslamasabry.oc.core.model.ImportableConversation
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

public data class AgentModelChoice(val id: String, val label: String, val selected: Boolean)

public data class AgentToolsUi(
    val connected: Boolean = false,
    val name: String = "",
    val directory: String = "",
    val loading: Boolean = false,
    val busy: Boolean = false,
    val failure: Failure? = null,
    val canOptions: Boolean = false,
    val features: ImmutableList<AgentFeature> = persistentListOf(),
    val models: ImmutableList<AgentModelChoice> = persistentListOf(),
    val canImport: Boolean = false,
    val importsLoaded: Boolean = false,
    val imports: ImmutableList<ImportableConversation> = persistentListOf(),
    val alreadyImported: Int = 0,
    val canUpdate: Boolean = false,
    val confirmUpdate: Boolean = false,
    val updatePhase: HostUpdatePhase? = null,
    val updateResult: HostUpdateResult? = null,
    val uncertain: Boolean = false,
)

public interface AgentToolsActions {
    public fun back()

    public fun refresh()

    public fun setToggle(id: String, on: Boolean)

    public fun setChoice(id: String, value: String)

    public fun setModel(id: String)

    public fun readImports()

    public fun importConversation(handle: String, agentId: String)

    public fun requestUpdate()

    public fun cancelUpdate()

    public fun confirmUpdate()
}
