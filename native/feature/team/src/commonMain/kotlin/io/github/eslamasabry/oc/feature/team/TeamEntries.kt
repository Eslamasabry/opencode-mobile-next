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
import io.github.eslamasabry.oc.feature.team.presentation.FleetAgentViewModel
import io.github.eslamasabry.oc.feature.team.presentation.FleetAgentsViewModel
import io.github.eslamasabry.oc.feature.team.presentation.TeamBoardViewModel
import io.github.eslamasabry.oc.feature.team.presentation.TeamEditorViewModel
import io.github.eslamasabry.oc.feature.team.presentation.TeamHomeViewModel
import io.github.eslamasabry.oc.feature.team.presentation.TeamProjectViewModel
import io.github.eslamasabry.oc.feature.team.presentation.TeamServersViewModel
import io.github.eslamasabry.oc.feature.team.presentation.TeamSettingsViewModel
import io.github.eslamasabry.oc.feature.team.presentation.TeamSetupViewModel
import io.github.eslamasabry.oc.feature.team.presentation.TeamTaskViewModel
import io.github.eslamasabry.oc.feature.team.presentation.TeamTimelineViewModel
import io.github.eslamasabry.oc.feature.team.ui.FleetAgentScreen
import io.github.eslamasabry.oc.feature.team.ui.FleetAgentsScreen
import io.github.eslamasabry.oc.feature.team.ui.TeamBoardScreen
import io.github.eslamasabry.oc.feature.team.ui.TeamEditorScreen
import io.github.eslamasabry.oc.feature.team.ui.TeamHomeScreen
import io.github.eslamasabry.oc.feature.team.ui.TeamProjectScreen
import io.github.eslamasabry.oc.feature.team.ui.TeamServersScreen
import io.github.eslamasabry.oc.feature.team.ui.TeamSettingsScreen
import io.github.eslamasabry.oc.feature.team.ui.TeamSetupScreen
import io.github.eslamasabry.oc.feature.team.ui.TeamTaskScreen
import io.github.eslamasabry.oc.feature.team.ui.TeamTimelineScreen
import io.github.eslamasabry.oc.ui.kitchat.KitChatEnvironment
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.routes.TeamRoute

/** The AI Team destinations: the team page, one project, its board, one task's conversation. */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class TeamEntries(private val environment: KitChatEnvironment) : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<TeamRoute.Home> { route ->
            val vm = assistedMetroViewModel<TeamHomeViewModel, TeamHomeViewModel.Factory> { create(route) }
            val state by vm.state.collectAsStateWithLifecycle()
            // The off page's host guide draws its commands in the kit's code block, which redacts what it shows.
            environment.Provide { TeamHomeScreen(state, vm) }
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
        entry<TeamRoute.Editor> { route ->
            val vm = assistedMetroViewModel<TeamEditorViewModel, TeamEditorViewModel.Factory> { create(route) }
            val state by vm.state.collectAsStateWithLifecycle()
            TeamEditorScreen(state, vm)
        }
        entry<TeamRoute.Servers> { route ->
            val vm = assistedMetroViewModel<TeamServersViewModel, TeamServersViewModel.Factory> { create(route) }
            val state by vm.state.collectAsStateWithLifecycle()
            TeamServersScreen(state, vm)
        }
        entry<TeamRoute.Timeline> { route ->
            val vm = assistedMetroViewModel<TeamTimelineViewModel, TeamTimelineViewModel.Factory> { create(route) }
            val state by vm.state.collectAsStateWithLifecycle()
            TeamTimelineScreen(state, vm)
        }
        entry<TeamRoute.TurnOn> { route ->
            val vm = assistedMetroViewModel<TeamSetupViewModel, TeamSetupViewModel.Factory> { create(route) }
            val state by vm.state.collectAsStateWithLifecycle()
            TeamSetupScreen(state, vm)
        }
        entry<TeamRoute.Settings> { route ->
            val vm = assistedMetroViewModel<TeamSettingsViewModel, TeamSettingsViewModel.Factory> { create(route) }
            val state by vm.state.collectAsStateWithLifecycle()
            TeamSettingsScreen(state, vm)
        }
        entry<TeamRoute.FleetAgents> { route ->
            val vm = assistedMetroViewModel<FleetAgentsViewModel, FleetAgentsViewModel.Factory> { create(route) }
            val state by vm.state.collectAsStateWithLifecycle()
            FleetAgentsScreen(state, vm)
        }
        entry<TeamRoute.FleetAgent> { route ->
            val vm = assistedMetroViewModel<FleetAgentViewModel, FleetAgentViewModel.Factory> { create(route) }
            val state by vm.state.collectAsStateWithLifecycle()
            FleetAgentScreen(state, vm)
        }
        entry<TeamRoute.Task> { route ->
            val vm = assistedMetroViewModel<TeamTaskViewModel, TeamTaskViewModel.Factory> { create(route) }
            val state by vm.state.collectAsStateWithLifecycle()
            TeamTaskScreen(state, vm)
        }
    }
}
