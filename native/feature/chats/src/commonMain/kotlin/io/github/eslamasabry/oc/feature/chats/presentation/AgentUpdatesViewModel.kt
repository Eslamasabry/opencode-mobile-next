package io.github.eslamasabry.oc.feature.chats.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateChoice
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateChoices
import io.github.eslamasabry.oc.ui.routes.AgentsRoute
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.LinuxRoute
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * "Updates for your agents" on Home (owner 2026-11-19; the engine's update-choice contract). Its own ViewModel so
 * Home's grows no further; Home's entry joins its state and actions to Home's ([HomeWithAgentUpdates]).
 * [choices] null: no offer ever (desktop, previews).
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class AgentUpdatesViewModel(
    private val nav: AppNavigator,
    choices: AgentUpdateChoices? = null,
) : ViewModel(), AgentUpdateActions {
    private val model = AgentUpdateSheetModel(choices, viewModelScope)

    public val state: StateFlow<AgentUpdateHomeUi> =
        model.ui.stateIn(viewModelScope, SharingStarted.WhileSubscribed(KEEP_MS), model.current())

    override fun chooseAgentUpdate(id: AgentId, choice: AgentUpdateChoice): Unit = model.choose(id, choice)

    override fun proceedAgentUpdates(): Unit = model.proceed()

    override fun confirmAgentRemoval(): Unit = model.confirmRemoval()

    override fun cancelAgentRemoval(): Unit = model.cancelRemoval()

    override fun agentUpdatesNotNow(): Unit = model.notNow()

    override fun reviewAgentUpdates(): Unit = model.review()

    override fun openAgentsFromUpdates() {
        // Agents is where an agent is removed: a "wasn't removed" line is answered by going there.
        if (model.current().notRemoved.isNotEmpty()) model.dismissNotRemoved()
        leaveSheet()
        nav.open(AgentsRoute.Home)
    }

    override fun openLinuxPackages() {
        leaveSheet()
        nav.open(LinuxRoute.Packages)
    }

    /** Leaving the sheet for another page is Not now: Home's Review brings it back. */
    private fun leaveSheet() {
        if (model.current().sheet.visible) model.notNow()
    }

    private companion object {
        /** The state outlives a short pause in collection (a configuration change), as Home's does. */
        const val KEEP_MS = 5_000L
    }
}

/** Home's actions with the agents' update sheet's own ([updates]) joined in; everything else goes to [home]. */
public class HomeWithAgentUpdates(home: ChatsActions, updates: AgentUpdateActions) : ChatsActions by home {
    override val agentUpdates: AgentUpdateActions = updates
}
