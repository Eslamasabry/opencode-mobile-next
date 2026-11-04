package io.github.eslamasabry.oc.feature.integrations.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.engine.integrations.catalog.McpCatalogControllers
import io.github.eslamasabry.oc.engine.integrations.catalog.McpCatalogState
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.McpCatalogRoute
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

public interface McpCatalogActions {
    public fun back()

    public fun consent(enabled: Boolean)

    public fun refresh()

    public fun search(query: String)

    public fun select(id: String)

    public fun add(name: String, headers: Map<String, String>, environment: Map<String, String>)

    public fun check()
}

@AssistedInject
public class McpCatalogViewModel(
    @Assisted route: McpCatalogRoute,
    private val navigator: AppNavigator,
    controllers: McpCatalogControllers,
) : ViewModel(), McpCatalogActions {
    private val model = controllers.open(route.profile.raw, route.directory, route.workspace)
    public val state: StateFlow<McpCatalogState> = model.state
    private var read: Job? = null

    init {
        read = viewModelScope.launch { model.load() }
    }

    override fun back() {
        if (state.value.selected != null) model.select(null) else navigator.back()
    }

    override fun consent(enabled: Boolean) {
        read?.cancel()
        read = viewModelScope.launch { model.setOptIn(enabled) }
    }

    override fun refresh() {
        read?.cancel()
        read = viewModelScope.launch { model.refresh() }
    }

    override fun search(query: String) {
        read?.cancel()
        read = viewModelScope.launch { model.refresh(query) }
    }

    override fun select(id: String): Unit = model.select(id)

    override fun add(name: String, headers: Map<String, String>, environment: Map<String, String>) {
        viewModelScope.launch { model.add(name, headers, environment) }
    }

    override fun check() {
        viewModelScope.launch { model.check() }
    }

    override fun onCleared() {
        model.close()
        super.onCleared()
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: McpCatalogRoute): McpCatalogViewModel
    }
}
