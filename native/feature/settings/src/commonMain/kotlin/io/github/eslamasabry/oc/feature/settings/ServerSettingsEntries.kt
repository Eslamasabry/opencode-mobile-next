package io.github.eslamasabry.oc.feature.settings

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import io.github.eslamasabry.oc.feature.settings.presentation.ServerSettingsViewModel
import io.github.eslamasabry.oc.feature.settings.ui.ServerSettingsScreen
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.routes.ServerSettingsRoute

@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class ServerSettingsEntries : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<ServerSettingsRoute> { route ->
            val vm = assistedMetroViewModel<ServerSettingsViewModel, ServerSettingsViewModel.Factory> { create(route) }
            val state by vm.state.collectAsStateWithLifecycle()
            ServerSettingsScreen(state, vm)
        }
    }
}
