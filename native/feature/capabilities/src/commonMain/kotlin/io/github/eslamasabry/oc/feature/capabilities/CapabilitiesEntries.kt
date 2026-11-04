package io.github.eslamasabry.oc.feature.capabilities

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.eslamasabry.oc.feature.capabilities.presentation.CapabilitiesViewModel
import io.github.eslamasabry.oc.feature.capabilities.ui.CapabilitiesScreen
import io.github.eslamasabry.oc.ui.routes.CapabilitiesRoute
import io.github.eslamasabry.oc.ui.routes.EntryInstaller

@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class CapabilitiesEntries : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<CapabilitiesRoute> {
            val vm = metroViewModel<CapabilitiesViewModel>()
            val state by vm.state.collectAsStateWithLifecycle()
            CapabilitiesScreen(state, vm)
        }
    }
}
