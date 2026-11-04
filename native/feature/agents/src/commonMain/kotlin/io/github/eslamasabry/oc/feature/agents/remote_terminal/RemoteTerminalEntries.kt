package io.github.eslamasabry.oc.feature.agents.remote_terminal

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.eslamasabry.oc.feature.agents.remote_terminal.presentation.RemoteTerminalViewModel
import io.github.eslamasabry.oc.feature.agents.remote_terminal.ui.RemoteTerminalScreen
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.routes.RemoteTerminalRoute

@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class RemoteTerminalEntries : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<RemoteTerminalRoute> { route ->
            val vm = metroViewModel<RemoteTerminalViewModel>()
            androidx.lifecycle.compose.LifecycleStartEffect(vm, route.profile) {
                vm.resumeDevServer()
                onStopOrDispose { vm.pauseDevServer() }
            }
            LaunchedEffect(route.profile) { vm.start(route.profile) }
            DisposableEffect(vm, route.profile) { onDispose { vm.detach() } }
            val state by vm.state.collectAsStateWithLifecycle()
            RemoteTerminalScreen(state, vm)
        }
    }
}
