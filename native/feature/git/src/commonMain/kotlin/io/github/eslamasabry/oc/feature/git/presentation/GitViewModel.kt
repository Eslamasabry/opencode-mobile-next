package io.github.eslamasabry.oc.feature.git.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.engine.git.GitServices
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.GitRoute
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One per Git route entry; all behaviour is [GitPresenter]'s, this class only gives it the entry's scope. */
@AssistedInject
public class GitViewModel(@Assisted route: GitRoute, services: GitServices, private val nav: AppNavigator) :
    ViewModel(), GitActions {
    private val presenter =
        GitPresenter(services.page(route.ref), services.account(route.ref.profileId), services) { block ->
            viewModelScope.launch { block() }
        }

    public val state: StateFlow<GitUi> =
        presenter.states.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MS), presenter.initial())

    init {
        presenter.start()
    }

    override fun back(): Unit = nav.back()

    override fun refresh(): Unit = presenter.refresh()

    override fun pull(): Unit = presenter.pull()

    override fun push(): Unit = presenter.push()

    override fun toggleFile(path: String): Unit = presenter.toggleFile(path)

    override fun commit(message: String): Unit = presenter.commit(message)

    override fun commitAndPush(message: String): Unit = presenter.commitAndPush(message)

    override fun openBranches(): Unit = presenter.openBranches()

    override fun openRemotes(): Unit = presenter.openRemotes()

    override fun openClone(): Unit = presenter.openClone()

    override fun addRemote(name: String, url: String): Unit = presenter.addRemote(name, url)

    override fun clone(url: String, folder: String): Unit = presenter.clone(url, folder)

    override fun closeSheet(): Unit = presenter.closeSheet()

    override fun switchBranch(name: String): Unit = presenter.switchBranch(name)

    override fun createBranch(name: String): Unit = presenter.createBranch(name)

    override fun openDiff(path: String): Unit = presenter.openDiff(path)

    override fun closeDiff(): Unit = presenter.closeDiff()

    override fun askDiscard(path: String): Unit = presenter.askDiscard(path)

    override fun confirm(): Unit = presenter.confirm()

    override fun dismissConfirm(): Unit = presenter.dismissConfirm()

    override fun setIdentity(name: String, email: String): Unit = presenter.setIdentity(name, email)

    override fun signIn(): Unit = presenter.signIn()

    override fun cancelSignIn(): Unit = presenter.cancelSignIn()

    override fun askSignOut(): Unit = presenter.askSignOut()

    override fun openGitHub(): Unit = presenter.openGitHub()

    override fun manageGitHubAccess(): Unit = presenter.manageGitHubAccess()

    override fun dismissResult(): Unit = presenter.dismissResult()

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: GitRoute): GitViewModel
    }

    private companion object {
        const val STOP_AFTER_MS = 5_000L
    }
}
