package io.github.eslamasabry.oc.feature.settings.integrations

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.*
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import io.github.eslamasabry.oc.ui.routes.*

@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class ServerIntegrationEntries : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<ServerIntegrationRoute> { route ->
            val vm =
                assistedMetroViewModel<
                    ServerIntegrationViewModel,
                    ServerIntegrationViewModel.Factory,
                > {
                    create(route)
                }
            val state by vm.state.collectAsStateWithLifecycle()
            LifecycleResumeEffect(vm) {
                vm.refresh()
                onPauseOrDispose {}
            }
            ServerIntegrationScreen(state, vm)
        }
    }
}
