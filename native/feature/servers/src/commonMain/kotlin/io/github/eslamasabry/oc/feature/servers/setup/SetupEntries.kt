package io.github.eslamasabry.oc.feature.servers.setup

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.*
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import io.github.eslamasabry.oc.feature.servers.setup.presentation.SetupViewModel
import io.github.eslamasabry.oc.feature.servers.setup.ui.SetupScreen
import io.github.eslamasabry.oc.ui.routes.*

@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class SetupEntries : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<ServersRoute.Setup> { route ->
            val vm =
                assistedMetroViewModel<SetupViewModel, SetupViewModel.Factory> {
                    create(route.profileId)
                }
            val state by vm.state.collectAsStateWithLifecycle()
            SetupScreen(state, vm)
        }
    }
}
