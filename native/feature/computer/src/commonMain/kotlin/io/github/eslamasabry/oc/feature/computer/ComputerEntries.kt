package io.github.eslamasabry.oc.feature.computer

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import io.github.eslamasabry.oc.feature.computer.ui.ComputerScreen
import io.github.eslamasabry.oc.ui.routes.ComputerRoute
import io.github.eslamasabry.oc.ui.routes.EntryInstaller

/** The module's only navigation3 import (Konsist K1): one ComputerViewModel per ComputerRoute entry. */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class ComputerEntries : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<ComputerRoute> { route ->
            val vm = assistedMetroViewModel<ComputerViewModel, ComputerViewModel.Factory> { create(route) }
            val state by vm.state.collectAsStateWithLifecycle()
            ComputerScreen(state, vm)
        }
    }
}
