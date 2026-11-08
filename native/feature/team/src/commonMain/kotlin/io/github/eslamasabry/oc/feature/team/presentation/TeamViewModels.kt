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
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.team.TeamAction
import io.github.eslamasabry.oc.engine.team.TeamCommand
import io.github.eslamasabry.oc.engine.team.TeamEngine
import io.github.eslamasabry.oc.engine.team.TeamPhase
import io.github.eslamasabry.oc.engine.team.TeamProject
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.PhoneSetupRoute
import io.github.eslamasabry.oc.ui.routes.TeamRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    private val facts: TeamServerFacts = TeamServerFacts { null },
) : ViewModel(), TeamHomeActions {
    private val profile = route.profileId.raw
    private val accepting = MutableStateFlow<AskAnswer.AcceptMilestone?>(null)
    private val intro = MutableStateFlow(TeamIntroUi())

    public val state: StateFlow<TeamHomeUi> =
        combine(engine.polled(profile) { clock.now().toEpochMilliseconds() }, accepting, intro) {
                (state, now),
                accept,
                off ->
                val home = TeamPresent.home(state, now).copy(accepting = accept)
                // The server is read again while the page is off, so a server that connects meanwhile is named.
                if (home.phase != TeamPhase.Off) home
                else home.copy(intro = off.copy(server = off.server ?: facts.server(route.profileId)))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MS), TeamHomeUi())

    init {
        viewModelScope.launch {
            val can = engine.canTurnOn(profile)
            intro.update { it.copy(canTurnOn = can) }
        }
    }

    /** The off page's primary on this phone: one turn-on at a time; the page becomes the team once it is on. */
    override fun turnOn() {
        var admitted = false
        intro.update {
            admitted = !it.turningOn && it.canTurnOn
            if (admitted) it.copy(turningOn = true, failure = null) else it
        }
        if (!admitted) return
        viewModelScope.launch {
            val done = engine.turnOn(profile)
            intro.update { it.copy(turningOn = false, failure = (done as? Outcome.Failed)?.failure) }
        }
    }

    /** Flutter's Termux path, and this phone where the app cannot turn it on itself: Add tools › AI Team. */
    override fun setUpOnPhone() {
        nav.open(PhoneSetupRoute.Customize(adding = true))
    }

    override fun openGuide() {
        intro.update { it.copy(guide = true) }
    }

    override fun closeGuide() {
        intro.update { it.copy(guide = false) }
    }

    private fun send(projectId: String, build: (TeamProject) -> TeamCommand) {
        val project = engine.state(profile).value.workspace?.project(projectId) ?: return
        viewModelScope.launch { engine.execute(profile, build(project)) }
    }

    override fun openEditor(kind: EditorKind, projectId: String) {
        nav.open(TeamRoute.Editor(route.profileId, kind.wire, projectId))
    }

    override fun answer(projectId: String, requestId: String, text: String) {
        if (text.isBlank()) return
        send(projectId) { TeamCommand.answer(it, requestId, text.trim()) }
    }

    override fun askAccept(projectId: String, answer: AskAnswer.AcceptMilestone) {
        accepting.value = answer
    }

    override fun cancelAccept() {
        accepting.value = null
    }

    override fun confirmAccept() {
        val a = accepting.value ?: return
        accepting.value = null
        send(a.projectId) { TeamCommand.acceptMilestone(it, a.milestoneId) }
    }

    override fun resumeTask(projectId: String, taskId: String) {
        send(projectId) { TeamCommand.resumeTask(it, taskId) }
    }

    override fun openTask(projectId: String, taskId: String) {
        nav.open(TeamRoute.Task(route.profileId, projectId, taskId))
    }

    override fun dismissRefusal() {
        engine.clearRefusal(profile)
    }

    override fun back(): Unit = nav.back()

    override fun openProject(id: String) {
        nav.open(TeamRoute.Project(route.profileId, id))
    }

    override fun openRequest(projectId: String, requestId: String) {
        nav.open(TeamRoute.Project(route.profileId, projectId))
    }

    override fun resumeProject(projectId: String) {
        val project = engine.state(profile).value.workspace?.project(projectId) ?: return
        viewModelScope.launch { engine.execute(profile, TeamCommand.project(project, TeamAction.RESUME_PROJECT)) }
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
    private val confirming = MutableStateFlow<ProjectConfirm?>(null)

    public val state: StateFlow<TeamProjectUi> =
        combine(engine.polled(profile) { clock.now().toEpochMilliseconds() }, confirmingStop, confirming) { (state, now), stop, ask ->
                TeamPresent.project(state, route.projectId, now).copy(confirmStop = stop, confirming = ask)
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
        send { TeamCommand.project(it, TeamAction.PAUSE_PROJECT) }
    }

    override fun resume() {
        send { TeamCommand.project(it, TeamAction.RESUME_PROJECT) }
    }

    override fun askStop() {
        confirmingStop.value = true
    }

    override fun cancelStop() {
        confirmingStop.value = false
    }

    override fun confirmStop() {
        confirmingStop.value = false
        send { TeamCommand.project(it, TeamAction.STOP_PROJECT) }
    }

    override fun dismissRefusal() {
        engine.clearRefusal(profile)
    }

    override fun openTask(taskId: String) {
        nav.open(TeamRoute.Task(route.profileId, route.projectId, taskId))
    }

    override fun openEditor(kind: EditorKind) {
        val team = kind == EditorKind.Defaults || kind == EditorKind.Roles
        nav.open(TeamRoute.Editor(route.profileId, kind.wire, if (team) "" else route.projectId))
    }

    override fun retryPlan() {
        send { TeamCommand.project(it, TeamAction.RETRY_PLAN) }
    }

    override fun markDigestRead() {
        send { TeamCommand.project(it, TeamAction.ACKNOWLEDGE_DIGEST) }
    }

    override fun openTimeline() {
        nav.open(TeamRoute.Timeline(route.profileId, route.projectId))
    }

    override fun openServers() {
        nav.open(TeamRoute.Servers(route.profileId, route.projectId))
    }

    override fun ask(confirm: ProjectConfirm) {
        confirming.value = confirm
    }

    override fun cancelConfirm() {
        confirming.value = null
    }

    /** Delete, merge into dev or promote, once the person said yes; a deleted project's page closes. */
    override fun confirm() {
        val ask = confirming.value ?: return
        confirming.value = null
        val project = engine.state(profile).value.workspace?.project(route.projectId) ?: return
        viewModelScope.launch {
            when (ask) {
                ProjectConfirm.Delete -> {
                    val result = engine.execute(profile, TeamCommand.delete(project))
                    if ((result as? io.github.eslamasabry.oc.core.common.Outcome.Ok)?.value?.accepted == true) nav.back()
                }
                is ProjectConfirm.Merge ->
                    project.repos.firstOrNull { it.id == ask.repoId }?.let { engine.execute(profile, TeamCommand.mergeToDev(project, it)) }
                is ProjectConfirm.Promote ->
                    project.repos.firstOrNull { it.id == ask.repoId }?.let { engine.execute(profile, TeamCommand.promote(project, it)) }
            }
        }
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

    override fun openTask(taskId: String) {
        nav.open(TeamRoute.Task(route.profileId, route.projectId, taskId))
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: TeamRoute.Board): TeamBoardViewModel
    }
}

/** One task's conversation (Dart `TeamProjectConversation`). */
@AssistedInject
public class TeamTaskViewModel(
    @Assisted private val route: TeamRoute.Task,
    private val engine: TeamEngine,
    private val nav: AppNavigator,
    private val clock: kotlin.time.Clock,
) : ViewModel(), TeamTaskActions {
    private val profile = route.profileId.raw

    private val selected = MutableStateFlow<Set<String>>(emptySet())
    private val confirming = MutableStateFlow<TaskConfirm?>(null)

    public val state: StateFlow<TeamTaskUi> =
        combine(engine.polled(profile) { clock.now().toEpochMilliseconds() }, selected, confirming) { (state, now), picked, ask ->
                TeamPresent.task(state, route.projectId, route.taskId, now, picked).copy(confirming = ask)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MS), TeamTaskUi())

    private fun task(action: String, confirmed: Boolean = false) {
        send { TeamCommand.task(it, action, route.taskId, confirmed) }
    }

    override fun restart(): Unit = task(TeamAction.RESTART_TASK)

    override fun pause(): Unit = task(TeamAction.PAUSE_TASK)

    override fun verify(): Unit = task(TeamAction.VERIFY_TASK)

    override fun recheck(): Unit = task(TeamAction.RECHECK_TASK)

    override fun acceptPhase(phaseId: String) {
        send { TeamCommand.task(it, TeamAction.ACCEPT_PHASE, phaseId) }
    }

    override fun resolve(mergeItemId: String, how: String) {
        send { TeamCommand.resolveConflict(it, mergeItemId, how) }
    }

    override fun toggleFinding(id: String, on: Boolean) {
        selected.update { if (on) it + id else it - id }
    }

    override fun selectOpenFindings() {
        val t = engine.state(profile).value.workspace?.project(route.projectId)?.tasks?.firstOrNull { it.id == route.taskId } ?: return
        selected.update { it + t.findings.filter { f -> f.status == "open" }.map { f -> f.id } }
    }

    /** The ticked findings that are still open (Dart `_selected.intersection(open)`). */
    private fun picked(): List<String> {
        val t = engine.state(profile).value.workspace?.project(route.projectId)?.tasks?.firstOrNull { it.id == route.taskId }
        val open = t?.findings?.filter { it.status == "open" }?.map { it.id }.orEmpty()
        return open.filter { it in selected.value }
    }

    override fun fixSelected() {
        val ids = picked()
        if (ids.isEmpty()) return
        send { TeamCommand.fixFindings(it, route.taskId, ids) }
    }

    override fun ignoreSelected(reason: String) {
        val id = picked().singleOrNull() ?: return
        if (reason.isBlank()) return
        send { TeamCommand.ignoreFinding(it, route.taskId, id, reason.trim()) }
    }

    override fun approvePlan() {
        send { TeamCommand.project(it, TeamAction.APPROVE_PLAN) }
    }

    override fun editPlan() {
        nav.open(TeamRoute.Editor(route.profileId, EditorKind.Plan.wire, route.projectId))
    }

    override fun move(taskId: String, serverId: String) {
        send { TeamCommand.move(it, taskId, serverId) }
    }

    override fun ask(confirm: TaskConfirm) {
        confirming.value = confirm
    }

    override fun cancelConfirm() {
        confirming.value = null
    }

    /** Stop (Dart `_stop`, confirmed), merge into dev, promote. */
    override fun confirm() {
        val ask = confirming.value ?: return
        confirming.value = null
        val project = engine.state(profile).value.workspace?.project(route.projectId) ?: return
        val repo = project.repos.firstOrNull { r -> project.tasks.any { it.id == route.taskId && it.repoId == r.id } }
        when (ask) {
            TaskConfirm.Stop -> task(TeamAction.STOP_TASK, confirmed = true)
            TaskConfirm.Merge -> if (repo != null) send { TeamCommand.mergeToDev(it, repo) }
            TaskConfirm.Promote -> if (repo != null) send { TeamCommand.promote(it, repo) }
        }
    }

    private fun send(build: (TeamProject) -> TeamCommand) {
        val project = engine.state(profile).value.workspace?.project(route.projectId) ?: return
        viewModelScope.launch { engine.execute(profile, build(project)) }
    }

    override fun back(): Unit = nav.back()

    override fun answer(requestId: String, text: String) {
        if (text.isBlank()) return
        send { TeamCommand.answer(it, requestId, text.trim()) }
    }

    override fun resume() {
        send { TeamCommand.resumeTask(it, route.taskId) }
    }

    override fun message(text: String) {
        if (text.isBlank()) return
        send { TeamCommand.message(it, route.taskId, text.trim()) }
    }

    override fun retry() {
        viewModelScope.launch { engine.refresh(profile) }
    }

    override fun dismissRefusal() {
        engine.clearRefusal(profile)
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: TeamRoute.Task): TeamTaskViewModel
    }
}
