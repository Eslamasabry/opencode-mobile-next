package io.github.eslamasabry.oc.feature.team

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import io.github.eslamasabry.oc.feature.team.presentation.TeamBoardViewModel
import io.github.eslamasabry.oc.feature.team.presentation.TeamHomeViewModel
import io.github.eslamasabry.oc.feature.team.presentation.TeamProjectViewModel
import io.github.eslamasabry.oc.feature.team.presentation.TeamTaskViewModel
import io.github.eslamasabry.oc.feature.team.ui.TeamBoardScreen
import io.github.eslamasabry.oc.feature.team.ui.TeamHomeScreen
import io.github.eslamasabry.oc.feature.team.ui.TeamProjectScreen
import io.github.eslamasabry.oc.feature.team.ui.TeamTaskScreen
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.routes.TeamRoute

/** The AI Team destinations: the team page, one project, its board, one task's conversation. */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class TeamEntries : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<TeamRoute.Home> { route ->
            val vm = assistedMetroViewModel<TeamHomeViewModel, TeamHomeViewModel.Factory> { create(route) }
            val state by vm.state.collectAsStateWithLifecycle()
            TeamHomeScreen(state, vm)
        }
        entry<TeamRoute.Project> { route ->
            val vm = assistedMetroViewModel<TeamProjectViewModel, TeamProjectViewModel.Factory> { create(route) }
            val state by vm.state.collectAsStateWithLifecycle()
            TeamProjectScreen(state, vm)
        }
        entry<TeamRoute.Board> { route ->
            val vm = assistedMetroViewModel<TeamBoardViewModel, TeamBoardViewModel.Factory> { create(route) }
            val state by vm.state.collectAsStateWithLifecycle()
            TeamBoardScreen(state, vm)
        }
        entry<TeamRoute.Task> { route ->
            val vm = assistedMetroViewModel<TeamTaskViewModel, TeamTaskViewModel.Factory> { create(route) }
            val state by vm.state.collectAsStateWithLifecycle()
            TeamTaskScreen(state, vm)
        }
    }
}
