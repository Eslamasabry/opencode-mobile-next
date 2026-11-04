package io.github.eslamasabry.oc.feature.agents.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.CardsSettingsRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * `AgentsRoute.Home`. All logic is in [AgentsPresenter]; this only gives it a lifecycle scope and
 * navigation.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class AgentsViewModel(
    computers: AgentComputers,
    phone: PhoneAgents,
    private val nav: AppNavigator,
    private val hub: AgentsHubConnection,
) : ViewModel(), AgentsActions, AgentsHubActions {
    private val presenter =
        AgentsPresenter(
            viewModelScope,
            computers,
            phone,
            onBack = nav::back,
            openChat = { nav.openChat(it, focusComposer = true) },
        )

    public val state: StateFlow<AgentsUi> = presenter.state

    private val localHub = MutableStateFlow(AgentsHubControlsUi())
    public val hubState: StateFlow<AgentsHubControlsUi> =
        combine(localHub, hub.state) { own, connection -> own.copy(connection = connection) }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                localHub.value.copy(connection = hub.state.value),
            )

    override fun openCardsSettings(): Unit = nav.open(CardsSettingsRoute)

    override fun switchToBuiltIn() {
        if (localHub.value.working || !hub.state.value.builtInAvailable) return
        localHub.update { it.copy(working = true, failure = null) }
        viewModelScope.launch {
            try {
                when (val answer = hub.switchToBuiltIn()) {
                    is Outcome.Ok -> presenter.refreshPhone()
                    is Outcome.Failed -> localHub.update { it.copy(failure = answer.failure) }
                }
            } finally {
                localHub.update { it.copy(working = false) }
            }
        }
    }

    override fun back(): Unit = presenter.back()

    override fun startAdding(): Unit = presenter.startAdding()

    override fun cancelAdding(): Unit = presenter.cancelAdding()

    override fun edited(): Unit = presenter.edited()

    override fun check(address: String, password: String, folder: String): Unit =
        presenter.check(address, password, folder)

    override fun add(name: String, address: String, password: String, folder: String): Unit =
        presenter.add(name, address, password, folder)

    override fun readAgents(id: ProfileId): Unit = presenter.readAgents(id)

    override fun newConversation(id: ProfileId): Unit = presenter.newConversation(id)

    override fun installPhone(id: String): Unit = presenter.installPhone(id)

    override fun cancelPhoneInstall(): Unit = presenter.cancelPhoneInstall()

    override fun refreshPhone(): Unit = presenter.refreshPhone()

    override fun requestRemoval(id: String): Unit = presenter.requestRemoval(id)

    override fun cancelRemoval(): Unit = presenter.cancelRemoval()

    override fun confirmRemoval(): Unit = presenter.confirmRemoval()
}
