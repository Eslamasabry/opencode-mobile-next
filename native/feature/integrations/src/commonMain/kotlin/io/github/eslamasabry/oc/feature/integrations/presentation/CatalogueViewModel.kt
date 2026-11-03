package io.github.eslamasabry.oc.feature.integrations.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.IntegrationsRoute
import io.github.eslamasabry.oc.ui.routes.ServersRoute
import kotlinx.coroutines.flow.StateFlow

/**
 * The MCP catalogue (Flutter `McpCatalogScreen`, map `mcp-catalog`): asks before the first registry request, then lists
 * public registry servers as switches. On adds the listing at the server's current location until OpenCode restarts;
 * off removes it. Each asks first, and the server's list is read again after (B-AGENT-212, B-AGENT-133).
 */
@AssistedInject
public class CatalogueViewModel(
    @Assisted route: IntegrationsRoute.Catalogue,
    source: CatalogueSource,
    private val nav: AppNavigator,
) : ViewModel(), CatalogueActions {
    private val presenter = CatalogueScreenPresenter(route.profile, source, viewModelScope)
    public val state: StateFlow<CatalogueUi> = presenter.state

    override fun back(): Unit = nav.back()

    override fun openServers(): Unit = nav.open(ServersRoute.Home)

    override fun load(): Unit = presenter.load()

    override fun forget(): Unit = presenter.forget()

    override fun search(query: String): Unit = presenter.search(query)

    override fun retry(): Unit = presenter.retry()

    override fun toggle(catalogId: String, on: Boolean): Unit = presenter.toggle(catalogId, on)

    override fun confirm(): Unit = presenter.confirm()

    override fun cancel(): Unit = presenter.cancel()

    override fun dismissProblem(): Unit = presenter.dismissProblem()

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: IntegrationsRoute.Catalogue): CatalogueViewModel
    }
}
