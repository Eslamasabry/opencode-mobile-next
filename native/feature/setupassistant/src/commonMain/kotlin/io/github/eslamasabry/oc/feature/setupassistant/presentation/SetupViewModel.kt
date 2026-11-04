package io.github.eslamasabry.oc.feature.setupassistant.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.*
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.runtime.setupassistant.SetupInspectionPhase
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.SetupRoute
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@AssistedInject
public class SetupViewModel(
    @Assisted private val route: SetupRoute.Home,
    private val host: SetupHost,
    private val nav: AppNavigator,
    private val app: AppCoroutineScope,
) : ViewModel(), SetupActions {
    private val mutable = MutableStateFlow(SetupUi())
    public val state: StateFlow<SetupUi> = mutable.asStateFlow()
    private var workspace: SetupWorkspace? = null

    init {
        viewModelScope.launch {
            when (val opened = host.open(route.profile)) {
                is Outcome.Failed -> mutable.value = SetupUi(loading = false, unavailable = true)
                is Outcome.Ok -> {
                    val owner = opened.value
                    workspace = owner
                    mutable.value =
                        SetupUi(
                            loading = false,
                            unavailable = owner.inspection == null,
                            registry = owner.registry.state.value,
                        )
                    audit()
                    launch {
                        owner.registry.state.collect { registry ->
                            mutable.update { it.copy(registry = registry) }
                        }
                    }
                    owner.inspection?.let { controller ->
                        launch {
                            controller.state.collect { inspection ->
                                mutable.update { it.copy(inspection = inspection) }
                            }
                        }
                        controller.setOnline(owner.online())
                        controller.refresh()
                    }
                }
            }
        }
    }

    override fun back(): Unit = nav.back()

    override fun refresh() {
        val owner = workspace ?: return
        viewModelScope.launch {
            owner.inspection?.let {
                it.setOnline(owner.online())
                it.refresh()
            }
            audit()
        }
    }

    override fun setRegistryConsent(enabled: Boolean) {
        val owner = workspace ?: return
        viewModelScope.launch { result(owner.registry.setOptIn(enabled)) }
    }

    override fun refreshRegistry(query: String?) {
        val owner = workspace ?: return
        viewModelScope.launch { result(owner.registry.refresh(owner.online(), query)) }
    }

    override fun clearRegistry() {
        val owner = workspace ?: return
        viewModelScope.launch { result(owner.registry.clear()) }
    }

    override fun showSaved() {
        workspace?.registry?.showSaved()
    }

    override fun recordReview() {
        val owner = workspace ?: return
        if (
            mutable.value.savingReview ||
                owner.inspection?.state?.value?.phase != SetupInspectionPhase.Ready
        )
            return
        mutable.update { it.copy(savingReview = true, failed = false) }
        viewModelScope.launch {
            result(owner.audit.append(owner.newId(), "verified", "review"))
            audit()
            mutable.update { it.copy(savingReview = false) }
        }
    }

    private fun audit() {
        val records = workspace?.audit?.records()
        mutable.update {
            it.copy(audit = records ?: persistentListOf(), auditUnavailable = records == null)
        }
    }

    private fun result(value: Outcome<Unit>) {
        mutable.update { it.copy(failed = value is Outcome.Failed) }
    }

    override fun onCleared() {
        workspace?.inspection?.let { controller -> app.launch { controller.close() } }
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: SetupRoute.Home): SetupViewModel
    }
}
