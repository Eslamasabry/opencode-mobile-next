package io.github.eslamasabry.oc.feature.chats.monitor

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.*
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import io.github.eslamasabry.oc.ui.routes.*

@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class MonitorInboxEntries : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<MonitorInboxRoute> { route ->
            val vm =
                assistedMetroViewModel<MonitorInboxViewModel, MonitorInboxViewModel.Factory> {
                    create(route)
                }
            val state by vm.state.collectAsStateWithLifecycle()
            MonitorInboxScreen(state, vm)
        }
    }
}
