package io.github.eslamasabry.oc.feature.settings

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import io.github.eslamasabry.oc.feature.settings.presentation.ModelDefaultsViewModel
import io.github.eslamasabry.oc.feature.settings.ui.ModelDefaultsScreen
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.routes.SettingsRoute

@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class ModelDefaultsEntries : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<SettingsRoute.ModelDefaults> { route ->
            val vm =
                assistedMetroViewModel<ModelDefaultsViewModel, ModelDefaultsViewModel.Factory> {
                    create(route)
                }
            val state by vm.state.collectAsStateWithLifecycle()
            ModelDefaultsScreen(state, vm)
        }
    }
}
