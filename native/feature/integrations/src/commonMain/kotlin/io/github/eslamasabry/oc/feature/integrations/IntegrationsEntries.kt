package io.github.eslamasabry.oc.feature.integrations

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import io.github.eslamasabry.oc.feature.integrations.presentation.CatalogueViewModel
import io.github.eslamasabry.oc.feature.integrations.presentation.ConnectorsViewModel
import io.github.eslamasabry.oc.feature.integrations.ui.CatalogueScreen
import io.github.eslamasabry.oc.feature.integrations.ui.ConnectorsScreen
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.routes.IntegrationsRoute

/** The EntryInstaller; the only file in the module that imports androidx.navigation3.runtime (Konsist K1). */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class IntegrationsEntries : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<IntegrationsRoute.Connectors> { route ->
            val vm = assistedMetroViewModel<ConnectorsViewModel, ConnectorsViewModel.Factory> { create(route) }
            val state by vm.state.collectAsStateWithLifecycle()
            ConnectorsScreen(state, vm)
        }
        entry<IntegrationsRoute.Catalogue> { route ->
            val vm = assistedMetroViewModel<CatalogueViewModel, CatalogueViewModel.Factory> { create(route) }
            val state by vm.state.collectAsStateWithLifecycle()
            CatalogueScreen(state, vm)
        }
    }
}
