package io.github.eslamasabry.oc.feature.agents.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import kotlin.time.Clock
import kotlinx.coroutines.flow.StateFlow

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
    nav: AppNavigator,
    accounts: AgentAccounts,
    drafts: AgentHandoffDrafts,
    clock: Clock,
) : ViewModel(), AgentsActions {
    private val presenter =
        AgentsPresenter(
            viewModelScope,
            computers,
            phone,
            onBack = nav::back,
            openChat = { nav.openChat(it, focusComposer = true) },
            accounts = accounts,
            clock = clock,
            drafts = drafts,
        )

    public val state: StateFlow<AgentsUi> = presenter.state

    override fun back(): Unit = presenter.back()

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

    override fun checkPhoneAgents(): Unit = presenter.checkPhoneAgents()

    override fun readAgents(id: ProfileId): Unit = presenter.readAgents(id)

    override fun newConversation(id: ProfileId): Unit = presenter.newConversation(id)
}
