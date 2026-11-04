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
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.git.GitLinkRepairService
import io.github.eslamasabry.oc.engine.git.GitServices
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.GitRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One per Git route entry; all behaviour is [GitPresenter]'s, this class only gives it the entry's
 * scope.
 */
@AssistedInject
public class GitViewModel(
    @Assisted private val route: GitRoute,
    services: GitServices,
    private val repair: GitLinkRepairService,
    private val nav: AppNavigator,
) : ViewModel(), GitActions {
    private val repairUi = MutableStateFlow(GitRepairUi(available = repair.available(route.ref)))
    private val presenter =
        GitPresenter(services.page(route.ref), services.account(route.ref.profileId), services) {
            block ->
            viewModelScope.launch { block() }
        }

    public val state: StateFlow<GitUi> =
        combine(presenter.states, repairUi) { base, repair -> base.copy(repair = repair) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_AFTER_MS),
                presenter.initial().copy(repair = repairUi.value),
            )

    init {
        presenter.start()
    }

    override fun back(): Unit = nav.back()

    override fun refresh() {
        if (!repairUi.value.running) presenter.refresh()
    }

    override fun repairObjects() {
        val directory = state.value.directory ?: return
        if (state.value.working || !repair.available(route.ref)) return
        repairUi.value = GitRepairUi(available = true, running = true)
        viewModelScope.launch {
            val result = repair.repair(route.ref, directory)
            repairUi.value =
                GitRepairUi(
                    available = repair.available(route.ref),
                    failed = result is Outcome.Failed,
                    report = (result as? Outcome.Ok)?.value,
                )
            presenter.refresh()
        }
    }

    override fun openRepairSettings() {
        repair.openTermuxSettings()
    }

    override fun pull(): Unit {
        if (!repairUi.value.running) presenter.pull()
    }

    override fun push(): Unit {
        if (!repairUi.value.running) presenter.push()
    }

    override fun toggleFile(path: String): Unit = presenter.toggleFile(path)

    override fun commit(message: String): Unit {
        if (!repairUi.value.running) presenter.commit(message)
    }

    override fun commitAndPush(message: String): Unit {
        if (!repairUi.value.running) presenter.commitAndPush(message)
    }

    override fun openBranches(): Unit = presenter.openBranches()

    override fun openRemotes(): Unit = presenter.openRemotes()

    override fun openClone(): Unit = presenter.openClone()

    override fun addRemote(name: String, url: String): Unit {
        if (!repairUi.value.running) presenter.addRemote(name, url)
    }

    override fun clone(url: String, folder: String): Unit {
        if (!repairUi.value.running) presenter.clone(url, folder)
    }

    override fun closeSheet(): Unit = presenter.closeSheet()

    override fun switchBranch(name: String): Unit {
        if (!repairUi.value.running) presenter.switchBranch(name)
    }

    override fun createBranch(name: String): Unit {
        if (!repairUi.value.running) presenter.createBranch(name)
    }

    override fun openDiff(path: String): Unit = presenter.openDiff(path)

    override fun closeDiff(): Unit = presenter.closeDiff()

    override fun askDiscard(path: String): Unit = presenter.askDiscard(path)

    override fun confirm(): Unit {
        if (!repairUi.value.running) presenter.confirm()
    }

    override fun dismissConfirm(): Unit = presenter.dismissConfirm()

    override fun setIdentity(name: String, email: String): Unit {
        if (!repairUi.value.running) presenter.setIdentity(name, email)
    }

    override fun signIn(): Unit {
        if (!repairUi.value.running) presenter.signIn()
    }

    override fun cancelSignIn(): Unit = presenter.cancelSignIn()

    override fun askSignOut(): Unit {
        if (!repairUi.value.running) presenter.askSignOut()
    }

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
