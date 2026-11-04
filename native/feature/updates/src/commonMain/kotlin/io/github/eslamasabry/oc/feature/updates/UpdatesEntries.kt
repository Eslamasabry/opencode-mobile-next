package io.github.eslamasabry.oc.feature.updates

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.eslamasabry.oc.engine.settings.teaching.ShowMeTarget
import io.github.eslamasabry.oc.engine.settings.teaching.ShowMeTargets
import io.github.eslamasabry.oc.feature.updates.presentation.UpdatesViewModel
import io.github.eslamasabry.oc.feature.updates.ui.UpdatesScreen
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.routes.UpdatesRoute

/**
 * The EntryInstaller; the only file in the module that imports androidx.navigation3.runtime
 * (Konsist K1).
 */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class UpdatesEntries(private val arrivals: ShowMeTargets) : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<UpdatesRoute.Home> {
            val vm = metroViewModel<UpdatesViewModel>()
            val state by vm.state.collectAsStateWithLifecycle()
            val arrival by arrivals.active.collectAsStateWithLifecycle()
            DisposableEffect(Unit) { onDispose { arrivals.dismiss(ShowMeTarget.Updates) } }
            UpdatesScreen(
                state,
                vm,
                arrival == ShowMeTarget.Updates,
                { arrivals.dismiss(ShowMeTarget.Updates) },
            )
        }
    }
}
