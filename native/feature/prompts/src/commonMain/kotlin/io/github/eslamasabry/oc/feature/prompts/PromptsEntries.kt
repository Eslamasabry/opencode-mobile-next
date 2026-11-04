package io.github.eslamasabry.oc.feature.prompts

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.eslamasabry.oc.feature.prompts.presentation.CommandsViewModel
import io.github.eslamasabry.oc.feature.prompts.presentation.KeptPromptsViewModel
import io.github.eslamasabry.oc.feature.prompts.presentation.SavedPromptsViewModel
import io.github.eslamasabry.oc.feature.prompts.presentation.TasksViewModel
import io.github.eslamasabry.oc.feature.prompts.ui.CommandsScreen
import io.github.eslamasabry.oc.feature.prompts.ui.KeptPromptsScreen
import io.github.eslamasabry.oc.feature.prompts.ui.SavedPromptsScreen
import io.github.eslamasabry.oc.feature.prompts.ui.TasksScreen
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.routes.KeptPromptsRoute
import io.github.eslamasabry.oc.ui.routes.PromptsRoute

/** Saved prompts, slash commands and tasks (P12-PROMPTS), each opened from a conversation. */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class PromptsEntries : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<KeptPromptsRoute> {
            val vm = metroViewModel<KeptPromptsViewModel>()
            val state by vm.state.collectAsStateWithLifecycle()
            KeptPromptsScreen(state, vm)
        }
        entry<PromptsRoute.Saved> { route ->
            val vm =
                assistedMetroViewModel<SavedPromptsViewModel, SavedPromptsViewModel.Factory> {
                    create(route)
                }
            val state by vm.state.collectAsStateWithLifecycle()
            SavedPromptsScreen(state, vm)
        }
        entry<PromptsRoute.Commands> { route ->
            val vm =
                assistedMetroViewModel<CommandsViewModel, CommandsViewModel.Factory> {
                    create(route)
                }
            val state by vm.state.collectAsStateWithLifecycle()
            CommandsScreen(state, vm)
        }
        entry<PromptsRoute.Tasks> { route ->
            val vm =
                assistedMetroViewModel<TasksViewModel, TasksViewModel.Factory> { create(route) }
            val state by vm.state.collectAsStateWithLifecycle()
            TasksScreen(state, vm)
        }
    }
}
