package io.github.eslamasabry.oc.feature.handoff

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.*
import dev.zacsweers.metro.*
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import io.github.eslamasabry.oc.feature.handoff.presentation.AgentHandoffViewModel
import io.github.eslamasabry.oc.feature.handoff.ui.AgentHandoffScreen
import io.github.eslamasabry.oc.ui.routes.*

@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class AgentHandoffEntries : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<AgentHandoffRoute> { route ->
            val vm =
                assistedMetroViewModel<AgentHandoffViewModel, AgentHandoffViewModel.Factory> {
                    create(route)
                }
            val state by vm.state.collectAsStateWithLifecycle()
            AgentHandoffScreen(state, vm)
        }
    }
}
