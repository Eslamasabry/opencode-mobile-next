package io.github.eslamasabry.oc.feature.terminal

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.eslamasabry.oc.feature.terminal.presentation.LocalTerminalViewModel
import io.github.eslamasabry.oc.feature.terminal.ui.LocalTerminalScreen
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.routes.LocalTerminalRoute

@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class LocalTerminalEntries : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<LocalTerminalRoute> {
            val vm = metroViewModel<LocalTerminalViewModel>()
            androidx.lifecycle.compose.LifecycleStartEffect(vm) {
                vm.resumeDevServer()
                onStopOrDispose { vm.pauseDevServer() }
            }
            val state by vm.state.collectAsStateWithLifecycle()
            LocalTerminalScreen(state, vm)
        }
    }
}
