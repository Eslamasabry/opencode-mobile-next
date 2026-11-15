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
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.ui.routes.AgentsRoute
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.CardsSettingsRoute
import io.github.eslamasabry.oc.ui.routes.ComputerRoute
import io.github.eslamasabry.oc.ui.routes.PhoneRoute
import io.github.eslamasabry.oc.ui.routes.PhoneSetupRoute
import kotlin.time.Clock
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
    phoneHosts: Set<PhoneAgentHostStarter>,
    phoneCheckers: Set<PhoneAgentChecker>,
    accounts: AgentAccounts,
    drafts: AgentHandoffDrafts,
    clock: Clock,
    ownProviders: Set<PhoneOwnProviders>,
    ownAgents: Set<PhoneOwnAgents>,
) : ViewModel(), AgentsActions, AgentsHubActions, OwnProviderActions, OwnAgentActions {
    private val presenter =
        AgentsPresenter(
            viewModelScope,
            computers,
            phone,
            onBack = nav::back,
            openChat = { nav.openChat(it, focusComposer = true) },
            openComputerPage = { nav.open(ComputerRoute(it)) },
            phoneHost = phoneHosts.singleOrNull(),
            checker = phoneCheckers.singleOrNull(),
            accounts = accounts,
            clock = clock,
            drafts = drafts,
            openNewChat = { profile, agent ->
                nav.open(
                    io.github.eslamasabry.oc.ui.routes.ChatsRoute.New(
                        profile = profile,
                        phoneAgent = agent,
                    )
                )
            },
            openSignIn = { owner, agent -> nav.open(AgentsRoute.SignInTerminal(owner, agent)) },
        )

    public val state: StateFlow<AgentsUi> = presenter.state

    /**
     * Issue 147: the own-provider and "How this works" sheets; its state rides on [hubState] (no
     * second collector).
     */
    private val own =
        OwnProviderPresenter(viewModelScope, ownProviders.singleOrNull()) { phone.refresh() }

    /** Issue 147: "Add your own agent"; its state also rides on [hubState]. */
    private val ownAgent =
        OwnAgentsPresenter(viewModelScope, ownAgents.singleOrNull()) { phone.refresh() }

    private val localHub = MutableStateFlow(AgentsHubControlsUi())
    public val hubState: StateFlow<AgentsHubControlsUi> =
        combine(localHub, hub.state, own.state, ownAgent.state) { local, connection, ownUi, agentsUi
                ->
                local.copy(connection = connection, own = ownUi, ownAgents = agentsUi)
            }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                localHub.value.copy(
                    connection = hub.state.value,
                    own = own.state.value,
                    ownAgents = ownAgent.state.value,
                ),
            )

    override fun openOwnProvider(agent: String): Unit = own.openOwnProvider(agent)

    override fun closeOwnProvider(): Unit = own.closeOwnProvider()

    override fun editedOwnProvider(): Unit = own.editedOwnProvider()

    override fun saveOwnProvider(
        agent: String,
        endpoint: String,
        key: String,
        model: String,
    ): Unit = own.saveOwnProvider(agent, endpoint, key, model)

    override fun removeOwnProvider(agent: String): Unit = own.removeOwnProvider(agent)

    override fun openAgentsHelp(): Unit = own.openAgentsHelp()

    override fun closeAgentsHelp(): Unit = own.closeAgentsHelp()

    override fun addOwnAgent(): Unit = ownAgent.addOwnAgent()

    override fun editOwnAgent(id: String): Unit = ownAgent.editOwnAgent(id)

    override fun closeOwnAgent(): Unit = ownAgent.closeOwnAgent()

    override fun editedOwnAgent(): Unit = ownAgent.editedOwnAgent()

    override fun saveOwnAgent(
        name: String,
        start: String,
        install: String,
        env: List<Pair<String, String>>,
    ): Unit = ownAgent.saveOwnAgent(name, start, install, env)

    override fun checkOwnAgent(): Unit = ownAgent.checkOwnAgent()

    override fun installOwnAgent(): Unit = ownAgent.installOwnAgent()

    override fun removeOwnAgent(): Unit = ownAgent.removeOwnAgent()

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

    /** Leaves this page whatever is open on it (an agent's own page whose set-up just finished). */
    public fun leave() {
        presenter.closeAgent()
        nav.back()
    }

    override fun openAccount(id: ProfileId): Unit = presenter.openAccount(id)

    override fun closeAccount(): Unit = presenter.closeAccount()

    override fun refreshAccount(): Unit = presenter.refreshAccount()

    override fun signInAccount(): Unit = presenter.signInAccount()

    override fun cancelAccountSignIn(): Unit = presenter.cancelAccountSignIn()

    override fun openAccountSignIn(): Unit = presenter.openAccountSignIn()

    override fun onCleared() {
        presenter.close()
    }

    override fun selectComputerKind(kind: AgentComputerKind): Unit =
        presenter.selectComputerKind(kind)

    override fun openDrafts(id: ProfileId): Unit = presenter.openDrafts(id)

    override fun closeDrafts(): Unit = presenter.closeDrafts()

    override fun prepareDraft(id: SessionId): Unit = presenter.prepareDraft(id)

    override fun copyDraft(): Unit = presenter.copyDraft()

    override fun startAdding(): Unit = presenter.startAdding()

    override fun cancelAdding(): Unit = presenter.cancelAdding()

    override fun edited(): Unit = presenter.edited()

    override fun check(address: String, password: String, folder: String): Unit =
        presenter.check(address, password, folder)

    override fun add(name: String, address: String, password: String, folder: String): Unit =
        presenter.add(name, address, password, folder)

    override fun openPhone(): Unit =
        nav.open(if (state.value.phone.available) PhoneRoute.ThisPhone else PhoneSetupRoute.Setup)

    override fun checkPhoneAgents(): Unit = presenter.checkPhoneAgents()

    override fun readAgents(id: ProfileId): Unit = presenter.readAgents(id)

    override fun newConversation(id: ProfileId): Unit = presenter.newConversation(id)

    override fun openComputer(id: ProfileId): Unit = presenter.openComputer(id)

    override fun requestInstall(id: String): Unit = presenter.requestInstall(id)

    override fun dismissInstall(): Unit = presenter.dismissInstall()

    override fun signInPhone(id: String) {
        if (
            io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog.builtIn
                .forProvider(id)
                ?.setupMethod ==
                io.github.eslamasabry.oc.engine.agents.catalog.AgentSetupMethod.KeyOrLocalServer
        ) {
            presenter.closeAgent()
            own.openOwnProvider(id)
        } else presenter.signInPhone(id)
    }

    override fun installPhone(id: String): Unit = presenter.installPhone(id)

    override fun cancelPhoneInstall(): Unit = presenter.cancelPhoneInstall()

    override fun refreshPhone(): Unit = presenter.refreshPhone()

    override fun requestPreviousVersion(id: String): Unit = presenter.requestPreviousVersion(id)

    override fun cancelPreviousVersion(): Unit = presenter.cancelPreviousVersion()

    override fun confirmPreviousVersion(): Unit = presenter.confirmPreviousVersion()

    override fun connectPhoneHelper(): Unit = presenter.connectPhoneHelper()

    override fun checkPhoneAgent(id: String): Unit = presenter.checkPhoneAgent(id)

    override fun resumePhoneAgent(id: String): Unit = presenter.resumePhoneAgent(id)

    override fun openAgent(id: String): Unit = presenter.openAgent(id)

    override fun selectTab(tab: AgentsTab): Unit = presenter.selectTab(tab)

    override fun pullRefresh(): Unit = presenter.pullRefresh()

    override fun newPhoneConversation(agent: String): Unit = presenter.newPhoneConversation(agent)

    override fun closeAgent(): Unit = presenter.closeAgent()

    override fun requestSignInReset(): Unit = presenter.requestSignInReset()

    override fun cancelSignInReset(): Unit = presenter.cancelSignInReset()

    override fun confirmSignInReset(): Unit = presenter.confirmSignInReset()

    override fun requestSignOut(id: String): Unit = presenter.requestSignOut(id)

    override fun cancelSignOut(): Unit = presenter.cancelSignOut()

    override fun confirmSignOut(): Unit = presenter.confirmSignOut()

    override fun requestRemoval(id: String): Unit = presenter.requestRemoval(id)

    override fun cancelRemoval(): Unit = presenter.cancelRemoval()

    override fun confirmRemoval(): Unit = presenter.confirmRemoval()
}
