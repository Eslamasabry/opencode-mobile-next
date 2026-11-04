package io.github.eslamasabry.oc.feature.setupassistant

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.*
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import io.github.eslamasabry.oc.feature.setupassistant.presentation.SetupViewModel
import io.github.eslamasabry.oc.feature.setupassistant.ui.SetupScreen
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.routes.SetupRoute

@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class SetupEntries : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<SetupRoute.Home> { route ->
            val vm =
                assistedMetroViewModel<SetupViewModel, SetupViewModel.Factory> { create(route) }
            val state by vm.state.collectAsStateWithLifecycle()
            SetupScreen(state, vm)
        }
    }
}
