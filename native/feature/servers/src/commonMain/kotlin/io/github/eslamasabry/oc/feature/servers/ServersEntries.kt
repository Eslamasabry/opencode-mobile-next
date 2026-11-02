package io.github.eslamasabry.oc.feature.servers

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import io.github.eslamasabry.oc.feature.servers.presentation.ServersViewModel
import io.github.eslamasabry.oc.feature.servers.ui.ServersScreen
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.routes.ServersRoute

/** Exactly one owner for the formerly unhandled New conversation route. */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class ServersEntries : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<ChatsRoute.New> { route ->
            val vm =
                assistedMetroViewModel<ServersViewModel, ServersViewModel.Factory> { create(route) }
            val state by vm.state.collectAsStateWithLifecycle()
            ServersScreen(state, vm)
        }
        entry<ServersRoute.Home> {
            val vm =
                assistedMetroViewModel<ServersViewModel, ServersViewModel.Factory> { create(null) }
            val state by vm.state.collectAsStateWithLifecycle()
            ServersScreen(state, vm)
        }
    }
}
