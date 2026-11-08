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
import io.github.eslamasabry.oc.engine.team.TeamCapabilities
import io.github.eslamasabry.oc.engine.team.TeamEngine
import io.github.eslamasabry.oc.engine.team.TeamMilestone
import io.github.eslamasabry.oc.engine.team.TeamPhase
import io.github.eslamasabry.oc.engine.team.TeamProject
import io.github.eslamasabry.oc.engine.team.TeamProjectPhase
import io.github.eslamasabry.oc.engine.team.TeamRepo
import io.github.eslamasabry.oc.engine.team.TeamRole
import io.github.eslamasabry.oc.engine.team.TeamServer
import io.github.eslamasabry.oc.engine.team.TeamSpec
import io.github.eslamasabry.oc.engine.team.TeamState
import io.github.eslamasabry.oc.engine.team.TeamTask
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.TeamRoute
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** A refused editor command, for its sentence and way forward (Dart `TeamRefusal`). */
public data class RefusalUi(val code: String, val action: String)

/** What the editor page draws (Dart `_EditorState.build`). */
public data class TeamEditorUi(
    val kind: EditorKind,
    val phase: TeamPhase = TeamPhase.Loading,
    /** False once read when the edited project is gone. */
    val found: Boolean = true,
    val form: EditorForm = EditorForm(),
    /** Bumped when the editor rewrites fields itself (restore, a spec change, a reload), so text fields reseed. */
    val generation: Int = 0,
    val problem: EditorProblem? = null,
    val refusal: RefusalUi? = null,
    val working: Boolean = false,
    val restoring: Boolean = true,
    val draftRestored: Boolean = false,
    val starterSeeded: Boolean = false,
    val typePath: Boolean = false,
    val history: Boolean = false,
    /** Reload asks first (Dart `_reload`). */
    val confirmReload: Boolean = false,
    /** The project moved on since the editor opened (Dart `_project.revision != _reviewedRevision`). */
    val changedElsewhere: Boolean = false,
    /** The engine cannot run work and this editor would start some (Dart `_open`'s gate): the page says so. */
    val blocked: Boolean = false,
    val projectStatus: String = "",
    val simulated: Boolean = false,
    val servers: ImmutableList<TeamServer> = persistentListOf(),
    val roles: ImmutableList<TeamRole> = persistentListOf(),
    /** The tasks given to the open role, with their project's name (the role page's list). */
    val roleTasks: ImmutableList<Pair<String, String>> = persistentListOf(),
    val specVersions: ImmutableList<TeamSpec> = persistentListOf(),
    val nowMs: Long = 0,
) {
    /** Dart `creationError` for the pinned button's reason; null outside new project and quick task. */
    public val creationError: EditorProblem?
        get() = if (kind.creating) TeamEditorRules.creationError(kind, form) else null

    /** Dart's `primary == null` cases: the roles list, and a plan that failed. */
    public val hasPrimary: Boolean
        get() = !((kind == EditorKind.Roles && form.role == null) || (kind == EditorKind.Plan && projectStatus == "planFailed"))

    public val cost: CostLine
        get() = TeamEditorRules.costLine(form, servers)
}

public interface TeamEditorActions {
    public fun back()

    public fun setText(key: String, text: String)

    public fun chooseServer(id: String) {}

    public fun addRepo() {}

    public fun removeRepo(id: String) {}

    public fun chooseRole(id: String) {}

    public fun setPlanFirst(on: Boolean) {}

    public fun setMode(mode: String) {}

    public fun setCharging(on: Boolean) {}

    public fun setScreenOff(on: Boolean) {}

    public fun setReview(level: String) {}

    public fun setBudget(choice: String) {}

    public fun setAutoFix(on: Boolean) {}

    public fun typePath() {}

    public fun toggleHistory() {}

    public fun addMilestone() {}

    public fun moveMilestone(id: String, up: Boolean) {}

    public fun removeMilestone(id: String) {}

    public fun setRisky(phaseId: String, risky: Boolean) {}

    public fun setTaskRole(taskId: String, roleId: String) {}

    public fun setTaskRepo(taskId: String, repoId: String) {}

    public fun setTaskServer(taskId: String, serverId: String) {}

    public fun toggleDependency(taskId: String, dependsOn: String) {}

    public fun removeTask(taskId: String) {}

    /** The roles editor: open [roleId], or a new role when null. */
    public fun editRole(roleId: String?) {}

    public fun allRoles() {}

    /** The pinned primary (Dart `_save`). */
    public fun save()

    /** The spec's Save draft. */
    public fun saveDraft() {}

    /** The spec's Ask for a change. */
    public fun requestChange() {}

    /** A failed plan: use it as one task, or ask again. */
    public fun recoverPlan(useAsTask: Boolean) {}

    public fun askReload() {}

    public fun confirmReload() {}

    public fun cancelReload() {}

    /** The restored draft's Start fresh. */
    public fun startFresh() {}
}

/**
 * One project editor (Dart `_Editor`): new project, quick task, spec, plan, project settings, roles, team defaults.
 * Nothing is sent while typing; what was typed is kept as a draft (`oc.teamEditorDrafts`) and cleared once the engine
 * took it. A refusal keeps the form, with the plain sentence and the code under details.
 */
@AssistedInject
public class TeamEditorViewModel(
    @Assisted private val route: TeamRoute.Editor,
    private val engine: TeamEngine,
    private val nav: AppNavigator,
    private val clock: kotlin.time.Clock,
) : ViewModel(), TeamEditorActions {
    private val profile = route.profileId.raw
    private val kind = EditorKind.of(route.kind)
    private val target = TeamEditorRules.draftTarget(kind, route.projectId)
    private val local = MutableStateFlow(TeamEditorUi(kind))
    private var opened = false
    private var complete = false
    private var serial = 0

    public val state: StateFlow<TeamEditorUi> =
        combine(engine.polled(profile) { clock.now().toEpochMilliseconds() }, local) { (team, now), ui -> present(team, ui, now) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TeamEditorUi(kind))

    private fun newId(): String = "edit-${clock.now().toEpochMilliseconds() * 1000}-${serial++}"

    private fun project(team: TeamState = engine.state(profile).value): TeamProject? = team.workspace?.project(route.projectId)

    private fun present(team: TeamState, ui: TeamEditorUi, now: Long): TeamEditorUi {
        val workspace = team.workspace
        if (!opened && team.phase == TeamPhase.Ready && workspace != null) open(team)
        val p = project(team)
        val form = local.value.form
        val starts = kind.creating || (kind == EditorKind.Plan && p?.status == "plan")
        return local.value.copy(
            phase = team.phase,
            found = route.projectId.isEmpty() || workspace == null || p != null,
            changedElsewhere = p != null && form.reviewedRevision != null && p.revision != form.reviewedRevision,
            blocked = starts && !team.capabilities.lanes,
            projectStatus = p?.status.orEmpty(),
            simulated = workspace?.simulated == true,
            servers = workspace?.servers.orEmpty().toImmutableList(),
            roles = TeamEditorRules.orderedRoles(workspace?.roles.orEmpty()).toImmutableList(),
            roleTasks =
                form.role?.let { r ->
                    workspace?.projects.orEmpty().flatMap { pr -> pr.tasks.filter { it.roleId == r.id }.map { it.title to pr.name } }
                }.orEmpty().toImmutableList(),
            specVersions = p?.specVersions.orEmpty().toImmutableList(),
            nowMs = now,
        )
    }

    /** The first read: the form from the project or the defaults, then any saved draft over it (Dart `_restore`). */
    private fun open(team: TeamState) {
        opened = true
        val (form, seeded) = TeamEditorRules.initial(kind, team.workspace, project(team), ::newId)
        local.value = local.value.copy(form = form, starterSeeded = seeded, restoring = true)
        viewModelScope.launch {
            val saved = engine.draft(profile, target)
            local.update { ui ->
                if (saved.isNullOrEmpty()) return@update ui.copy(restoring = false)
                val restored = TeamEditorRules.decode(saved, ui.form)
                if (restored == null) ui.copy(restoring = false, problem = EditorProblem.DraftFailed)
                else ui.copy(form = restored, restoring = false, draftRestored = kind.creating, starterSeeded = false, generation = ui.generation + 1)
            }
        }
    }

    /** Dart `_change`: every edit is kept as a draft, never sent. */
    private fun change(edit: (EditorForm) -> EditorForm) {
        local.update { it.copy(form = edit(it.form)) }
        persist()
    }

    private fun persist() {
        val ui = local.value
        if (ui.restoring || complete) return
        val payload = TeamEditorRules.encode(ui.form)
        viewModelScope.launch {
            if (engine.saveDraft(profile, target, payload) is Outcome.Failed && !complete) {
                local.update { it.copy(problem = EditorProblem.DraftFailed) }
            }
        }
    }

    private fun servers(): List<TeamServer> = engine.state(profile).value.workspace?.servers.orEmpty()

    private fun hour(): Int = clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour

    private fun charging(f: EditorForm): EditorForm = TeamEditorRules.updateCharging(f, servers(), hour())

    override fun back(): Unit = nav.back()

    override fun setText(key: String, text: String) {
        if (local.value.form.text(key) == text) return
        change {
            val next = it.with(key, text)
            if (key == "repoName" || key == "repoPath") next.copy(pendingRepoId = null) else next
        }
    }

    override fun chooseServer(id: String) {
        change { charging(it.copy(serverId = id)) }
    }

    override fun addRepo() {
        val f = local.value.form
        if (f.serverId == null || f.value("repoPath").isEmpty() || f.value("repoName").isEmpty()) {
            local.update { it.copy(problem = EditorProblem.RepoRequired) }
            return
        }
        change {
            val repo = TeamRepo(id = newId(), name = it.value("repoName"), path = it.value("repoPath"), serverId = it.serverId!!)
            charging(it.copy(repos = it.repos + repo, pendingRepoId = null).with("repoName", "").with("repoPath", ""))
        }
        local.update { it.copy(problem = null, generation = it.generation + 1) }
    }

    override fun removeRepo(id: String) {
        change { it.copy(repos = it.repos.filterNot { r -> r.id == id }) }
    }

    override fun chooseRole(id: String) {
        change { it.copy(roleId = id) }
    }

    override fun setPlanFirst(on: Boolean) {
        change { it.copy(planFirst = on) }
    }

    override fun setMode(mode: String) {
        change { charging(it.copy(settings = it.settings.copy(mode = mode))) }
    }

    override fun setCharging(on: Boolean) {
        change { it.copy(chargingTouched = true, settings = it.settings.copy(chargingOnly = on)) }
    }

    override fun setScreenOff(on: Boolean) {
        change { it.copy(settings = it.settings.copy(keepWorkingScreenOff = on)) }
    }

    override fun setReview(level: String) {
        change { it.copy(settings = it.settings.copy(reviewLevel = level)) }
    }

    override fun setBudget(choice: String) {
        change { it.copy(budgetChoice = choice) }
    }

    override fun setAutoFix(on: Boolean) {
        change { it.copy(settings = it.settings.copy(autoFix = on)) }
    }

    override fun typePath() {
        local.update { it.copy(typePath = true) }
    }

    override fun toggleHistory() {
        local.update { it.copy(history = !it.history) }
    }

    override fun addMilestone() {
        change { it.copy(milestones = it.milestones + TeamMilestone(id = newId())) }
    }

    override fun moveMilestone(id: String, up: Boolean) {
        change {
            val list = it.milestones.toMutableList()
            val i = list.indexOfFirst { m -> m.id == id }
            val j = if (up) i - 1 else i + 1
            if (i < 0 || j !in list.indices) return@change it
            list.add(j, list.removeAt(i))
            it.copy(milestones = list)
        }
    }

    override fun removeMilestone(id: String) {
        change { it.copy(milestones = it.milestones.filterNot { m -> m.id == id }) }
    }

    override fun setRisky(phaseId: String, risky: Boolean) {
        change { it.copy(phases = it.phases.map { p: TeamProjectPhase -> if (p.id == phaseId) p.copy(risky = risky) else p }) }
    }

    private fun task(taskId: String, edit: (TeamTask, EditorForm) -> TeamTask) {
        change { f -> f.copy(tasks = f.tasks.map { if (it.id == taskId) edit(it, f) else it }) }
    }

    override fun setTaskRole(taskId: String, roleId: String): Unit = task(taskId) { t, _ -> t.copy(roleId = roleId) }

    override fun setTaskRepo(taskId: String, repoId: String) {
        task(taskId) { t, f ->
            val repo = f.repos.firstOrNull { it.id == repoId } ?: return@task t
            t.copy(repoId = repo.id, serverId = repo.serverId)
        }
    }

    override fun setTaskServer(taskId: String, serverId: String): Unit = task(taskId) { t, _ -> t.copy(serverId = serverId) }

    override fun toggleDependency(taskId: String, dependsOn: String) {
        task(taskId) { t, _ -> t.copy(dependsOn = if (dependsOn in t.dependsOn) t.dependsOn - dependsOn else t.dependsOn + dependsOn) }
    }

    override fun removeTask(taskId: String) {
        change { f ->
            f.copy(tasks = f.tasks.filterNot { it.id == taskId }.map { it.copy(dependsOn = it.dependsOn - taskId) })
        }
    }

    override fun editRole(roleId: String?) {
        val role = roleId?.let { id -> engine.state(profile).value.workspace?.roles?.firstOrNull { it.id == id } } ?: TeamRole(id = newId())
        change {
            it.copy(role = role)
                .with("roleName", role.name)
                .with("roleInstructions", role.instructions)
                .with("roleModel", role.model)
                .with("roleFallback", role.fallbackModel)
        }
        local.update { it.copy(generation = it.generation + 1, problem = null, refusal = null) }
    }

    override fun allRoles() {
        change { it.copy(role = null) }
    }

    /**
     * Dart `_send`: one command at a time; an accepted one takes the engine's revision, and with [close] clears the
     * drafts and closes the page. A stale revision says it changed elsewhere; any other refusal keeps its code.
     */
    private suspend fun send(
        action: String,
        spec: TeamSpec? = null,
        tasks: List<TeamTask>? = null,
        phases: List<TeamProjectPhase>? = null,
        role: TeamRole? = null,
        text: String? = null,
        close: Boolean = true,
    ): Boolean {
        val ui = local.value
        if (ui.working || complete) return false
        local.update { it.copy(working = true, problem = null, refusal = null) }
        val command = TeamEditorRules.command(kind, action, ui.form, route.projectId, spec, tasks, phases, role, text)
        val result =
            when (val sent = engine.execute(profile, command)) {
                is Outcome.Ok -> sent.value
                is Outcome.Failed -> null
            }
        val accepted = result?.accepted == true
        if (accepted) local.update { it.copy(form = it.form.copy(reviewedRevision = result!!.revision)) }
        if (accepted && close) {
            complete = true
            engine.clearRefusal(profile)
            val cleared = engine.saveDraft(profile, target, null)
            if (cleared is Outcome.Failed) {
                local.update { it.copy(working = false, problem = EditorProblem.DraftClearFailed) }
                return true
            }
            local.update { it.copy(working = false) }
            nav.back()
            return true
        }
        val code = result?.code?.ifEmpty { null } ?: "saveFailed"
        local.update {
            it.copy(
                working = false,
                refusal = if (accepted || code == "staleRevision") null else RefusalUi(code, action),
                problem = if (!accepted && code == "staleRevision") EditorProblem.ChangedElsewhere else it.problem,
            )
        }
        if (!accepted) engine.clearRefusal(profile)
        return accepted
    }

    override fun save() {
        val ui = local.value
        if (complete || ui.working || ui.restoring) return
        val f = ui.form
        if (kind.creating || kind == EditorKind.Settings || kind == EditorKind.Defaults) {
            TeamEditorRules.settingsError(f)?.let { p ->
                local.update { it.copy(problem = p) }
                return
            }
        }
        viewModelScope.launch {
            when (kind) {
                EditorKind.Create, EditorKind.Quick -> {
                    TeamEditorRules.creationError(kind, f)?.let { p ->
                        local.update { it.copy(problem = p) }
                        return@launch
                    }
                    // Dart `_pendingRepo` gives the typed repo one id for the whole send.
                    if (TeamEditorRules.pendingRepo(f) != null && f.pendingRepoId == null) {
                        local.update { it.copy(form = it.form.copy(pendingRepoId = newId())) }
                    }
                    send(
                        TeamEditorRules.saveAction(kind, null)!!,
                        spec = TeamSpec(goal = f.value("goal"), contextFiles = TeamEditorRules.lines(f.value("contextFiles"))),
                    )
                }
                EditorKind.Spec -> saveSpec(approve = true)
                EditorKind.Plan ->
                    send(TeamEditorRules.saveAction(kind, project())!!, tasks = TeamEditorRules.editedTasks(f), phases = f.phases)
                EditorKind.Settings, EditorKind.Defaults -> send(TeamEditorRules.saveAction(kind, null)!!)
                EditorKind.Roles -> {
                    val role = f.role ?: return@launch
                    if (f.value("roleName").isEmpty()) {
                        local.update { it.copy(problem = EditorProblem.RoleRequired) }
                        return@launch
                    }
                    val edited =
                        role.copy(
                            name = f.value("roleName"),
                            instructions = f.value("roleInstructions"),
                            model = f.value("roleModel"),
                            fallbackModel = f.value("roleFallback"),
                        )
                    if (send(TeamAction.SAVE_ROLE, role = edited, close = false)) change { it.copy(role = null) }
                }
            }
        }
    }

    /** Dart `_saveSpec`: save the draft, then (to approve) approve it. */
    private suspend fun saveSpec(approve: Boolean) {
        val spec = TeamEditorRules.currentSpec(local.value.form, project())
        TeamEditorRules.specError(spec, approve)?.let { p ->
            local.update { it.copy(problem = p) }
            return
        }
        if (send(TeamAction.SAVE_SPEC_DRAFT, spec = spec, close = !approve) && approve) send(TeamAction.APPROVE_SPEC, spec = spec)
    }

    override fun saveDraft() {
        viewModelScope.launch { saveSpec(approve = false) }
    }

    /** Dart `_requestChange`: save what is typed, ask the team, then show the spec it wrote back. */
    override fun requestChange() {
        val f = local.value.form
        val text = f.value("changeRequest")
        if (text.isEmpty() || f.value("goal").isEmpty()) {
            local.update { it.copy(problem = EditorProblem.ChangeRequired) }
            return
        }
        viewModelScope.launch {
            if (!send(TeamAction.SAVE_SPEC_DRAFT, spec = TeamEditorRules.currentSpec(f, project()), close = false)) return@launch
            if (!send(TeamAction.REQUEST_SPEC_CHANGE, text = text, close = false)) return@launch
            val spec = project()?.specDraft ?: return@launch
            change { form ->
                var next =
                    form.copy(milestones = spec.milestones)
                        .with("goal", spec.goal)
                        .with("constraints", spec.constraints)
                        .with("decisions", spec.decisions)
                        .with("outOfScope", spec.outOfScope)
                        .with("contextFiles", spec.contextFiles.joinToString("\n"))
                        .with("changeRequest", "")
                spec.milestones.forEach { m ->
                    next = next.with("milestone-${m.id}", m.title).with("milestoneCriteria-${m.id}", m.criteria.joinToString("\n"))
                }
                next
            }
            local.update { it.copy(generation = it.generation + 1) }
        }
    }

    /** Dart `_recoverPlan`: use the plan as one task, or ask again; then show the plan the engine has now. */
    override fun recoverPlan(useAsTask: Boolean) {
        viewModelScope.launch {
            if (!send(if (useAsTask) TeamAction.USE_PLAN_AS_TASK else TeamAction.RETRY_PLAN, close = false)) return@launch
            val p = project() ?: return@launch
            change { form ->
                var next = form.copy(tasks = p.tasks, phases = p.phases)
                p.tasks.forEach { t -> next = next.with("task-${t.id}", t.title).with("criteria-${t.id}", t.criteria.joinToString("\n")) }
                next
            }
            local.update { it.copy(generation = it.generation + 1) }
        }
    }

    override fun askReload() {
        local.update { it.copy(confirmReload = true) }
    }

    override fun cancelReload() {
        local.update { it.copy(confirmReload = false) }
    }

    override fun confirmReload() {
        local.update { it.copy(confirmReload = false) }
        startFresh()
    }

    /** Dart `_discardAndReopen`: drop the draft and open the form again from what the engine has. */
    override fun startFresh() {
        viewModelScope.launch {
            if (engine.saveDraft(profile, target, null) is Outcome.Failed) {
                local.update { it.copy(problem = EditorProblem.DraftFailed) }
                return@launch
            }
            val team = engine.state(profile).value
            val (form, seeded) = TeamEditorRules.initial(kind, team.workspace, project(team), ::newId)
            local.update {
                TeamEditorUi(kind, form = form, starterSeeded = seeded, restoring = false, generation = it.generation + 1)
            }
        }
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: TeamRoute.Editor): TeamEditorViewModel
    }
}

/** For callers that only read capabilities (the pages' gates). */
internal val TeamState.can: TeamCapabilities
    get() = capabilities
