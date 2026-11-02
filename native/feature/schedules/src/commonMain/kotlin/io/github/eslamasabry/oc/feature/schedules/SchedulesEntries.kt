package io.github.eslamasabry.oc.feature.schedules

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import io.github.eslamasabry.oc.feature.schedules.presentation.ScheduleEditViewModel
import io.github.eslamasabry.oc.feature.schedules.presentation.SchedulesViewModel
import io.github.eslamasabry.oc.feature.schedules.ui.ScheduleEditScreen
import io.github.eslamasabry.oc.feature.schedules.ui.SchedulesScreen
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.routes.SchedulesRoute

/** The two scheduled-prompt destinations (plan 07 row 78; K01 list, K02 editor). */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class SchedulesEntries : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<SchedulesRoute.List> { route ->
            val vm = assistedMetroViewModel<SchedulesViewModel, SchedulesViewModel.Factory> { create(route) }
            val state by vm.state.collectAsStateWithLifecycle()
            SchedulesScreen(state, vm)
        }
        entry<SchedulesRoute.Edit> { route ->
            val vm = assistedMetroViewModel<ScheduleEditViewModel, ScheduleEditViewModel.Factory> { create(route) }
            val state by vm.state.collectAsStateWithLifecycle()
            ScheduleEditScreen(state, vm)
        }
    }
}
