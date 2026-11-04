package io.github.eslamasabry.oc.feature.devservices

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import io.github.eslamasabry.oc.feature.devservices.presentation.DevServicesViewModel
import io.github.eslamasabry.oc.feature.devservices.ui.DevServicesScreen
import io.github.eslamasabry.oc.ui.routes.DevServicesRoute
import io.github.eslamasabry.oc.ui.routes.EntryInstaller

@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class DevServicesEntries : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<DevServicesRoute.Home> { route ->
            val vm = assistedMetroViewModel<DevServicesViewModel, DevServicesViewModel.Factory> { create(route) }
            val state by vm.state.collectAsStateWithLifecycle()
            DevServicesScreen(state, vm)
        }
    }
}
