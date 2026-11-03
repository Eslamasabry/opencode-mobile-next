package io.github.eslamasabry.oc.feature.plans

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.eslamasabry.oc.feature.plans.presentation.PlansViewModel
import io.github.eslamasabry.oc.feature.plans.ui.PlansScreen
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.routes.PlansRoute

/** The EntryInstaller; the only file in the module that imports androidx.navigation3.runtime (Konsist K1). */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class PlansEntries : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<PlansRoute> {
            val vm = metroViewModel<PlansViewModel>()
            val state by vm.state.collectAsStateWithLifecycle()
            PlansScreen(state, vm)
        }
    }
}
