package io.github.eslamasabry.oc.feature.integrations.catalog

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.routes.McpCatalogRoute

@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class McpCatalogEntries : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<McpCatalogRoute> { route ->
            val vm =
                assistedMetroViewModel<McpCatalogViewModel, McpCatalogViewModel.Factory> {
                    create(route)
                }
            val state by vm.state.collectAsStateWithLifecycle()
            McpCatalogScreen(state, vm)
        }
    }
}
