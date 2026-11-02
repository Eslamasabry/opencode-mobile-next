package io.github.eslamasabry.oc.feature.preview

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import io.github.eslamasabry.oc.feature.preview.presentation.DevPreviewViewModel
import io.github.eslamasabry.oc.feature.preview.presentation.PortsViewModel
import io.github.eslamasabry.oc.feature.preview.ui.DevPreviewScreen
import io.github.eslamasabry.oc.feature.preview.ui.PortsScreen
import io.github.eslamasabry.oc.feature.preview.ui.WebPreviewSlot
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.routes.PreviewRoute

/** The preview's two destinations (unit PREVIEW-QR). Reached from the chat bar's "Preview a running app". */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class PreviewEntries(private val slot: WebPreviewSlot) : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<PreviewRoute.Ports> { route ->
            val vm = assistedMetroViewModel<PortsViewModel, PortsViewModel.Factory> { create(route) }
            val state by vm.state.collectAsStateWithLifecycle()
            PortsScreen(state, vm)
        }
        entry<PreviewRoute.Page> { route ->
            val vm = assistedMetroViewModel<DevPreviewViewModel, DevPreviewViewModel.Factory> { create(route) }
            val state by vm.state.collectAsStateWithLifecycle()
            DevPreviewScreen(state, vm, slot, vm.pageEgress)
        }
    }
}
