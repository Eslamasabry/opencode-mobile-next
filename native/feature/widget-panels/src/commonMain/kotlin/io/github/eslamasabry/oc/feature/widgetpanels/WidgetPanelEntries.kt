package io.github.eslamasabry.oc.feature.widgetpanels

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.eslamasabry.oc.feature.widgetpanels.presentation.WidgetPanelHost
import io.github.eslamasabry.oc.feature.widgetpanels.presentation.WidgetPanelViewModel
import io.github.eslamasabry.oc.feature.widgetpanels.ui.WidgetPanelScreen
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.routes.WidgetPanel

/**
 * A secondary activity installs its own host, never the main app's connection graph or navigator.
 */
public class WidgetPanelEntries(private val host: WidgetPanelHost) : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<WidgetPanel.Style> { route ->
            val vm = viewModel { WidgetPanelViewModel(route, host) }
            val state by vm.state.collectAsStateWithLifecycle()
            WidgetPanelScreen(state, vm)
        }
        entry<WidgetPanel.Project> { route ->
            val vm = viewModel { WidgetPanelViewModel(route, host) }
            val state by vm.state.collectAsStateWithLifecycle()
            WidgetPanelScreen(state, vm)
        }
        entry<WidgetPanel.Steer> { route ->
            val vm = viewModel { WidgetPanelViewModel(route, host) }
            val state by vm.state.collectAsStateWithLifecycle()
            WidgetPanelScreen(state, vm)
        }
    }
}
