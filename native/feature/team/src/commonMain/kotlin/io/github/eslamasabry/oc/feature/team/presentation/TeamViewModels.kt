package io.github.eslamasabry.oc.feature.team.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.engine.team.TeamCommand
import io.github.eslamasabry.oc.engine.team.TeamEngine
import io.github.eslamasabry.oc.engine.team.TeamProject
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.TeamRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How long a page keeps reading after it stops being shown (a rotation does not restart the poll). */
private const val STOP_AFTER_MS = 5_000L

/** The team page: the projects, most urgent first (Dart `TeamPage` + `TeamProjectsScreen`). */
@AssistedInject
public class TeamHomeViewModel(
    @Assisted private val route: TeamRoute.Home,
    private val engine: TeamEngine,
    private val nav: AppNavigator,
    private val clock: kotlin.time.Clock,
) : ViewModel(), TeamHomeActions {
    private val profile = route.profileId.raw

    public val state: StateFlow<TeamHomeUi> =
        engine
            .polled(profile) { clock.now().toEpochMilliseconds() }
            .map { (state, now) -> TeamPresent.home(state, now) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MS), TeamHomeUi())

    override fun back(): Unit = nav.back()

    override fun openProject(id: String) {
        nav.open(TeamRoute.Project(route.profileId, id))
    }

    override fun openRequest(projectId: String, requestId: String) {
        nav.open(TeamRoute.Project(route.profileId, projectId))
    }

    override fun resumeProject(projectId: String) {
        val project = engine.state(profile).value.workspace?.project(projectId) ?: return
        viewModelScope.launch { engine.execute(profile, TeamCommand.project(project, "resumeProject")) }
    }

    override fun retry() {
        viewModelScope.launch { engine.refresh(profile) }
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: TeamRoute.Home): TeamHomeViewModel
    }
}

/** One project (Dart `TeamProjectOverview`). */
@AssistedInject
public class TeamProjectViewModel(
    @Assisted private val route: TeamRoute.Project,
    private val engine: TeamEngine,
    private val nav: AppNavigator,
    private val clock: kotlin.time.Clock,
) : ViewModel(), TeamProjectActions {
    private val profile = route.profileId.raw

    private val confirmingStop = MutableStateFlow(false)

    public val state: StateFlow<TeamProjectUi> =
        combine(engine.polled(profile) { clock.now().toEpochMilliseconds() }, confirmingStop) { (state, now), stop ->
                TeamPresent.project(state, route.projectId, now).copy(confirmStop = stop)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MS), TeamProjectUi())

    /** Sends a command built from the project as the engine last showed it (its current revision). */
    private fun send(build: (TeamProject) -> TeamCommand) {
        val project = engine.state(profile).value.workspace?.project(route.projectId) ?: return
        viewModelScope.launch { engine.execute(profile, build(project)) }
    }

    override fun answer(requestId: String, text: String) {
        if (text.isBlank()) return
        send { TeamCommand.answer(it, requestId, text.trim()) }
    }

    override fun acceptMilestone(milestoneId: String) {
        send { TeamCommand.acceptMilestone(it, milestoneId) }
    }

    override fun resumeTask(taskId: String) {
        send { TeamCommand.resumeTask(it, taskId) }
    }

    override fun pause() {
        send { TeamCommand.project(it, "pauseProject") }
    }

    override fun resume() {
        send { TeamCommand.project(it, "resumeProject") }
    }

    override fun askStop() {
        confirmingStop.value = true
    }

    override fun cancelStop() {
        confirmingStop.value = false
    }

    override fun confirmStop() {
        confirmingStop.value = false
        send { TeamCommand.project(it, "stopProject") }
    }

    override fun dismissRefusal() {
        engine.clearRefusal(profile)
    }

    override fun back(): Unit = nav.back()

    override fun openBoard(milestone: String?) {
        nav.open(TeamRoute.Board(route.profileId, route.projectId, milestone))
    }

    override fun retry() {
        viewModelScope.launch { engine.refresh(profile) }
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: TeamRoute.Project): TeamProjectViewModel
    }
}

/** One project's board with its filters (Dart `TeamProjectBoard`); the filters live here across rotation. */
@AssistedInject
public class TeamBoardViewModel(
    @Assisted private val route: TeamRoute.Board,
    private val engine: TeamEngine,
    private val nav: AppNavigator,
    private val clock: kotlin.time.Clock,
) : ViewModel(), TeamBoardActions {
    private val profile = route.profileId.raw
    private val filters = MutableStateFlow(TeamPresent.BoardFilters(milestone = route.milestone))

    public val state: StateFlow<TeamBoardUi> =
        combine(engine.polled(profile) { clock.now().toEpochMilliseconds() }, filters) { (state, _), f ->
                TeamPresent.board(state, route.projectId, f)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MS), TeamBoardUi(milestone = route.milestone))

    override fun back(): Unit = nav.back()

    override fun showGraph(graph: Boolean) {
        filters.update { it.copy(graph = graph) }
    }

    // Flutter's board keeps a lane the person chose across filter changes (`_column`).
    override fun filterMilestone(id: String?) {
        filters.update { it.copy(milestone = id) }
    }

    override fun filterRepo(id: String?) {
        filters.update { it.copy(repo = id) }
    }

    override fun filterServer(id: String?) {
        filters.update { it.copy(server = id) }
    }

    override fun selectColumn(column: BoardColumn) {
        filters.update { it.copy(column = column) }
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: TeamRoute.Board): TeamBoardViewModel
    }
}
