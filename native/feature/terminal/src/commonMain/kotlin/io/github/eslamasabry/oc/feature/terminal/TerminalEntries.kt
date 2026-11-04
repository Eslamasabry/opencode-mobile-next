package io.github.eslamasabry.oc.feature.terminal

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import io.github.eslamasabry.oc.feature.terminal.presentation.TerminalViewModel
import io.github.eslamasabry.oc.feature.terminal.ui.TerminalScreen
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.routes.TerminalRoute

@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class TerminalEntries : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<TerminalRoute.Conversation> { route ->
            val vm =
                assistedMetroViewModel<TerminalViewModel, TerminalViewModel.Factory> {
                    create(route)
                }
            val state by vm.state.collectAsStateWithLifecycle()
            TerminalScreen(state, vm)
        }
    }
}
