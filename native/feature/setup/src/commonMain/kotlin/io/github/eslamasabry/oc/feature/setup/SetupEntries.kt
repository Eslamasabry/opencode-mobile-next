package io.github.eslamasabry.oc.feature.setup

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.eslamasabry.oc.feature.setup.presentation.SetupViewModel
import io.github.eslamasabry.oc.feature.setup.ui.SetupScreen
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.routes.SetupRoute

/** The EntryInstaller; the ONLY file in the module that imports androidx.navigation3.runtime. */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class SetupEntries : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<SetupRoute.Phone> {
            val vm = metroViewModel<SetupViewModel>()
            val state by vm.state.collectAsStateWithLifecycle()
            SetupScreen(state, vm)
        }
    }
}
