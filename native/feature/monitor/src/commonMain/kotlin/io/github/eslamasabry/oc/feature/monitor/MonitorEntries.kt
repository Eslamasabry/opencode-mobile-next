package io.github.eslamasabry.oc.feature.monitor

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import io.github.eslamasabry.oc.feature.monitor.presentation.MonitorViewModel
import io.github.eslamasabry.oc.feature.monitor.ui.MonitorScreen
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.routes.MonitorRoute

@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class MonitorEntries : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<MonitorRoute> { route ->
            val vm =
                assistedMetroViewModel<MonitorViewModel, MonitorViewModel.Factory> { create(route) }
            val state by vm.state.collectAsStateWithLifecycle()
            MonitorScreen(state, vm)
        }
    }
}
