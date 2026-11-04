package io.github.eslamasabry.oc.feature.agents.providers

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.*
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.eslamasabry.oc.feature.agents.providers.presentation.ProvidersViewModel
import io.github.eslamasabry.oc.feature.agents.providers.ui.ProvidersScreen
import io.github.eslamasabry.oc.ui.routes.*

@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class ProvidersEntries : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<ProvidersRoute> { route ->
            val model = metroViewModel<ProvidersViewModel>()
            LaunchedEffect(route.profile) { model.open(route.profile) }
            val state by model.state.collectAsStateWithLifecycle()
            ProvidersScreen(state, model)
        }
    }
}
