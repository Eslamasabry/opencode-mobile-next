package io.github.eslamasabry.oc.feature.search.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.FormatterHealth
import io.github.eslamasabry.oc.core.model.LanguageServiceHealth
import io.github.eslamasabry.oc.core.model.VersionControlHealth
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.SearchRoute
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

public data class ProjectHealthSnapshot(
    val vcs: VersionControlHealth? = null,
    val languages: ImmutableList<LanguageServiceHealth> = persistentListOf(),
    val formatters: ImmutableList<FormatterHealth> = persistentListOf(),
    val languagesAvailable: Boolean = false,
    val formattersAvailable: Boolean = false,
)

public data class ProjectHealthUi(
    val directory: String,
    val loading: Boolean = false,
    val snapshot: ProjectHealthSnapshot? = null,
    val failure: Failure? = null,
)

public interface ProjectHealthActions {
    public fun back()

    public fun refresh()
}

/** Read-only health, bound to the exact searched server and project. */
@AssistedInject
public class ProjectHealthViewModel(
    @Assisted private val route: SearchRoute.ProjectHealth,
    private val source: ConversationSearchSource,
    private val nav: AppNavigator,
) : ViewModel(), ProjectHealthActions {
    private val local = MutableStateFlow(ProjectHealthUi(route.directory))
    public val state: StateFlow<ProjectHealthUi> = local.asStateFlow()

    init {
        refresh()
    }

    override fun back(): Unit = nav.back()

    override fun refresh() {
        if (local.value.loading) return
        local.value = local.value.copy(loading = true, failure = null)
        viewModelScope.launch {
            local.value =
                when (val read = source.projectHealth(route.profile, route.directory)) {
                    is Outcome.Ok -> local.value.copy(loading = false, snapshot = read.value)
                    is Outcome.Failed -> local.value.copy(loading = false, failure = read.failure)
                }
        }
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: SearchRoute.ProjectHealth): ProjectHealthViewModel
    }
}
