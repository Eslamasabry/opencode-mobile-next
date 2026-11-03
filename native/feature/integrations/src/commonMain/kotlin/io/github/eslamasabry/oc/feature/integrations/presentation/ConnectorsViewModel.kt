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
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.IntegrationsRoute
import io.github.eslamasabry.oc.ui.routes.ServersRoute
import kotlinx.coroutines.flow.StateFlow

/**
 * The Connectors page (Flutter Tools › MCP servers): the live server's connectors at its current location, each with a
 * switch. A switch never claims success on its own: after the server accepts, the list is read again and the row shows
 * what the server now reports (B-AGENT-135). Double taps on one row coalesce while its change is in flight.
 */
@AssistedInject
public class ConnectorsViewModel(
    @Assisted route: IntegrationsRoute.Connectors,
    source: ConnectorsSource,
    private val nav: AppNavigator,
) : ViewModel(), ConnectorsActions {
    private val presenter = ConnectorsPresenter(route.profile, source, viewModelScope)
    public val state: StateFlow<ConnectorsUi> = presenter.state

    override fun back(): Unit = nav.back()

    override fun openServers(): Unit = nav.open(ServersRoute.Home)

    override fun openCatalogue(): Unit = nav.open(IntegrationsRoute.Catalogue(state.value.server))

    override fun refresh(): Unit = presenter.refresh()

    override fun chooseServer(id: ProfileId): Unit = presenter.chooseServer(id)

    override fun toggle(name: String, on: Boolean): Unit = presenter.toggle(name, on)

    override fun dismissProblem(): Unit = presenter.dismissProblem()

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: IntegrationsRoute.Connectors): ConnectorsViewModel
    }
}
