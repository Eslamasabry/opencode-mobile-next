package io.github.eslamasabry.oc.feature.agents.tools

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.*
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.eslamasabry.oc.feature.agents.tools.presentation.ToolsViewModel
import io.github.eslamasabry.oc.feature.agents.tools.ui.ToolsScreen
import io.github.eslamasabry.oc.ui.routes.*

@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class ToolsEntries : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<ToolsRoute> { route ->
            val vm = metroViewModel<ToolsViewModel>()
            LaunchedEffect(route.profile, route.session) { vm.open(route.profile, route.session) }
            val state by vm.state.collectAsStateWithLifecycle()
            ToolsScreen(state, vm)
        }
    }
}
