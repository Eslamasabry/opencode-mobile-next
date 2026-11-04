package io.github.eslamasabry.oc.feature.integrations

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.eslamasabry.oc.feature.integrations.presentation.HostPanelViewModel
import io.github.eslamasabry.oc.feature.integrations.presentation.IntegrationsViewModel
import io.github.eslamasabry.oc.feature.integrations.ui.HostPanel
import io.github.eslamasabry.oc.feature.integrations.ui.IntegrationsScreen
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.routes.IntegrationsRoute

@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class IntegrationsEntries : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<IntegrationsRoute> {
            val vm = metroViewModel<IntegrationsViewModel>()
            val state by vm.state.collectAsStateWithLifecycle()
            val hosts = metroViewModel<HostPanelViewModel>()
            val hostState by hosts.state.collectAsStateWithLifecycle()
            IntegrationsScreen(state, vm) { HostPanel(hostState, hosts) }
        }
    }
}
