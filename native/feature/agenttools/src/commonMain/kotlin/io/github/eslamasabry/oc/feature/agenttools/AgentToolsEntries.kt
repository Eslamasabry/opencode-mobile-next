package io.github.eslamasabry.oc.feature.agenttools

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import io.github.eslamasabry.oc.feature.agenttools.presentation.AgentToolsViewModel
import io.github.eslamasabry.oc.feature.agenttools.ui.AgentToolsScreen
import io.github.eslamasabry.oc.ui.routes.AgentToolsRoute
import io.github.eslamasabry.oc.ui.routes.EntryInstaller

@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class AgentToolsEntries : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<AgentToolsRoute> { route ->
            val vm =
                assistedMetroViewModel<AgentToolsViewModel, AgentToolsViewModel.Factory> {
                    create(route)
                }
            val state by vm.state.collectAsStateWithLifecycle()
            AgentToolsScreen(state, vm)
        }
    }
}
