package io.github.eslamasabry.oc.feature.servers.account

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.*
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import io.github.eslamasabry.oc.feature.servers.account.presentation.AgentAccountViewModel
import io.github.eslamasabry.oc.feature.servers.account.ui.AgentAccountScreen
import io.github.eslamasabry.oc.ui.routes.AgentAccountRoute
import io.github.eslamasabry.oc.ui.routes.EntryInstaller

@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class AgentAccountEntries : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<AgentAccountRoute> { route ->
            val vm =
                assistedMetroViewModel<AgentAccountViewModel, AgentAccountViewModel.Factory> {
                    create(route)
                }
            val state by vm.state.collectAsStateWithLifecycle()
            LifecycleResumeEffect(vm) {
                vm.refresh()
                onPauseOrDispose {}
            }
            AgentAccountScreen(state, vm)
        }
    }
}
