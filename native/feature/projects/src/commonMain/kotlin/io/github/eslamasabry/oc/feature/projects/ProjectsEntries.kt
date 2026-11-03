package io.github.eslamasabry.oc.feature.projects

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectPageViewModel
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectsPickerViewModel
import io.github.eslamasabry.oc.feature.projects.ui.ProjectPageScreen
import io.github.eslamasabry.oc.feature.projects.ui.ProjectsPickerScreen
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.routes.ProjectsRoute

/** The EntryInstaller; the ONLY file in the module that imports androidx.navigation3.runtime (Konsist K1). */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class ProjectsEntries : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<ProjectsRoute.Picker> { route ->
            val vm = assistedMetroViewModel<ProjectsPickerViewModel, ProjectsPickerViewModel.Factory> { create(route) }
            val state by vm.state.collectAsStateWithLifecycle()
            ProjectsPickerScreen(state, vm)
        }
        entry<ProjectsRoute.Project> { route ->
            val vm = assistedMetroViewModel<ProjectPageViewModel, ProjectPageViewModel.Factory> { create(route) }
            val state by vm.state.collectAsStateWithLifecycle()
            ProjectPageScreen(state, vm)
        }
    }
}
