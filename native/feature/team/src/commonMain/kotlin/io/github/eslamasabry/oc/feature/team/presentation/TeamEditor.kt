package io.github.eslamasabry.oc.feature.team.presentation

import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.engine.team.TeamAction
import io.github.eslamasabry.oc.engine.team.TeamBudget
import io.github.eslamasabry.oc.engine.team.TeamCommand
import io.github.eslamasabry.oc.engine.team.TeamJson
import io.github.eslamasabry.oc.engine.team.TeamMilestone
import io.github.eslamasabry.oc.engine.team.TeamProject
import io.github.eslamasabry.oc.engine.team.TeamProjectPhase
import io.github.eslamasabry.oc.engine.team.TeamProjectSettings
import io.github.eslamasabry.oc.engine.team.TeamRepo
import io.github.eslamasabry.oc.engine.team.TeamRole
import io.github.eslamasabry.oc.engine.team.TeamServer
import io.github.eslamasabry.oc.engine.team.TeamSpec
import io.github.eslamasabry.oc.engine.team.TeamTask
import io.github.eslamasabry.oc.engine.team.TeamWorkspace

/** The project editors (Dart `_Kind`, team_project_editors.dart); [wire] is Dart's name, part of the draft target. */
public enum class EditorKind(public val wire: String) {
    Create("create"),
    Quick("quick"),
    Spec("spec"),
    Plan("plan"),
    Settings("settings"),
    Roles("roles"),
    Defaults("defaults");

    /** New project and quick task. */
    public val creating: Boolean
        get() = this == Create || this == Quick

    public companion object {
        public fun of(wire: String): EditorKind = entries.firstOrNull { it.wire == wire } ?: Create
    }
}

/** What stops the form, worded by the page (Dart's `_error` strings, team_project_editors.dart). */
public enum class EditorProblem {
    ChooseModeBudget, ChooseMode, PositiveLanes, ChooseBudget, PositiveBudget, FixRoundsRange, PositiveTokens,
    Required, GoalRequired, RepoIncomplete, RepoMissing, ChooseRoleServer, RepoRequired, SpecRequired, ChangeRequired,
    RoleRequired, DraftFailed, DraftClearFailed, ChangedElsewhere,
}

/**
 * Everything the person typed or chose in one editor (Dart `_EditorState`'s fields). Text lives in [fields] under
 * Dart's keys (`goal`, `repoName`, `milestone-<id>`, `task-<id>`, `roleName`, ...), so a draft Flutter left reads here.
 */
public data class EditorForm(
    val fields: Map<String, String> = emptyMap(),
    val settings: TeamProjectSettings = TeamProjectSettings(),
    val repos: List<TeamRepo> = emptyList(),
    val milestones: List<TeamMilestone> = emptyList(),
    val tasks: List<TeamTask> = emptyList(),
    val phases: List<TeamProjectPhase> = emptyList(),
    val serverId: String? = null,
    val roleId: String? = null,
    /** `limited`, `unlimited`, or null before the person chose. */
    val budgetChoice: String? = null,
    val planFirst: Boolean = true,
    val chargingTouched: Boolean = false,
    /** The role open in the roles editor; null on the list. */
    val role: TeamRole? = null,
    val reviewedRevision: Long? = null,
    val pendingRepoId: String? = null,
) {
    public fun text(key: String): String = fields[key].orEmpty()

    /** Dart `_value`: trimmed. */
    public fun value(key: String): String = text(key).trim()

    public fun with(key: String, text: String): EditorForm = copy(fields = fields + (key to text))
}

/** Where the work would run and what it costs there (Dart `_costLine`). */
public sealed interface CostLine {
    public data object NoHost : CostLine

    public data class NotMeasured(val name: String, val phone: Boolean) : CostLine

    public data class Measured(val name: String, val phone: Boolean, val memoryMb: Long) : CostLine
}

/** The pure rules of the editors (Dart `_EditorState`), tested without Compose. */
public object TeamEditorRules {
    /** Dart `_draftTarget`. */
    public fun draftTarget(kind: EditorKind, projectId: String): String = "team-editor.${kind.wire}.$projectId"

    private fun plain(v: Double?): String =
        when {
            v == null -> ""
            v == kotlin.math.round(v) -> v.toLong().toString()
            else -> v.toString()
        }

    /** Dart `_creationDefaults`: a simple task still just starts, with one lane and a modest limit. */
    public fun creationDefaults(defaults: TeamProjectSettings): TeamProjectSettings =
        defaults.copy(
            mode = defaults.mode.ifEmpty { "single" },
            budget = if (defaults.budget.chosen) defaults.budget else TeamBudget(chosen = true, daily = 5.0, total = 20.0),
        )

    /**
     * Dart `initState`: the form as the editor opens on [project] (or a new one). A first spec drafts one milestone
     * from the goal ([starterSeeded]); [newId] makes the milestone's id.
     */
    public fun initial(kind: EditorKind, workspace: TeamWorkspace?, project: TeamProject?, newId: () -> String): Pair<EditorForm, Boolean> {
        val defaults = workspace?.defaultSettings ?: TeamProjectSettings()
        val settings = project?.settings ?: if (kind.creating) creationDefaults(defaults) else defaults
        var milestones = project?.specDraft?.milestones.orEmpty()
        var seeded = false
        if (kind == EditorKind.Spec && project != null && milestones.isEmpty()) {
            val goal = project.specDraft.goal.trim()
            if (goal.isNotEmpty()) {
                val line = goal.lines().first().trim()
                milestones = listOf(TeamMilestone(newId(), if (line.length > 60) line.take(60).trim() + "…" else line, listOf(line)))
                seeded = true
            }
        }
        val spec = project?.specDraft ?: TeamSpec()
        val fields =
            linkedMapOf(
                "name" to project?.name.orEmpty(),
                "goal" to spec.goal,
                "constraints" to spec.constraints,
                "decisions" to spec.decisions,
                "outOfScope" to spec.outOfScope,
                "contextFiles" to spec.contextFiles.joinToString("\n"),
                "lanes" to settings.maxLanes.toString(),
                "daily" to plain(settings.budget.daily),
                "total" to plain(settings.budget.total),
                "tokens" to (settings.budget.taskTokens?.toString() ?: ""),
                "rounds" to settings.maxFixRounds.toString(),
            )
        milestones.forEach {
            fields["milestone-${it.id}"] = it.title
            fields["milestoneCriteria-${it.id}"] = it.criteria.joinToString("\n")
        }
        project?.tasks?.forEach {
            fields["task-${it.id}"] = it.title
            fields["criteria-${it.id}"] = it.criteria.joinToString("\n")
        }
        val form =
            EditorForm(
                fields = fields,
                settings = settings,
                repos = project?.repos.orEmpty(),
                milestones = milestones,
                tasks = project?.tasks.orEmpty(),
                phases = project?.phases.orEmpty(),
                budgetChoice = if (settings.budget.chosen) (if (settings.budget.unlimited) "unlimited" else "limited") else null,
                reviewedRevision = project?.revision,
            )
        return form to seeded
    }

    /** Dart `_editedSettings`. */
    public fun editedSettings(f: EditorForm): TeamProjectSettings {
        val unlimited = f.budgetChoice == "unlimited"
        return f.settings.copy(
            maxLanes = if (f.settings.mode == "single") 1 else f.value("lanes").toIntOrNull() ?: 0,
            maxFixRounds = f.value("rounds").toIntOrNull() ?: 0,
            budget =
                TeamBudget(
                    chosen = f.budgetChoice != null,
                    unlimited = unlimited,
                    daily = if (unlimited) null else f.value("daily").toDoubleOrNull(),
                    total = if (unlimited) null else f.value("total").toDoubleOrNull(),
                    taskTokens = f.value("tokens").toLongOrNull(),
                ),
        )
    }

    /** Dart `_settingsError`. */
    public fun settingsError(f: EditorForm): EditorProblem? {
        val s = editedSettings(f)
        return when {
            s.mode.isEmpty() && !s.budget.chosen -> EditorProblem.ChooseModeBudget
            s.mode.isEmpty() -> EditorProblem.ChooseMode
            s.maxLanes !in 1..32 -> EditorProblem.PositiveLanes
            !s.budget.chosen -> EditorProblem.ChooseBudget
            !s.budget.unlimited && ((s.budget.daily ?: 0.0) <= 0 || (s.budget.total ?: 0.0) <= 0) -> EditorProblem.PositiveBudget
            s.maxFixRounds !in 0..3 -> EditorProblem.FixRoundsRange
            f.value("tokens").isNotEmpty() && (s.budget.taskTokens ?: 0) <= 0 -> EditorProblem.PositiveTokens
            else -> null
        }
    }

    /** Dart `_repoTyped`: a repo the person typed but has not added yet. */
    public fun repoTyped(f: EditorForm): Boolean = f.value("repoName").isNotEmpty() || f.value("repoPath").isNotEmpty()

    /** Dart `_pendingRepo`: it counts once it has a name, a folder and a place to run. */
    public fun pendingRepo(f: EditorForm): TeamRepo? {
        val server = f.serverId ?: return null
        if (f.value("repoName").isEmpty() || f.value("repoPath").isEmpty()) return null
        return TeamRepo(id = f.pendingRepoId.orEmpty(), name = f.value("repoName"), path = f.value("repoPath"), serverId = server)
    }

    public fun allRepos(f: EditorForm): List<TeamRepo> = f.repos + listOfNotNull(pendingRepo(f))

    /** Dart `_creationError`. */
    public fun creationError(kind: EditorKind, f: EditorForm): EditorProblem? {
        settingsError(f)?.let { return it }
        val noGoal = f.value("goal").isEmpty()
        val repos = allRepos(f)
        if (noGoal || repos.isEmpty()) {
            if (noGoal && repos.isEmpty() && !repoTyped(f)) return EditorProblem.Required
            if (noGoal) return EditorProblem.GoalRequired
            return if (repoTyped(f)) EditorProblem.RepoIncomplete else EditorProblem.RepoMissing
        }
        if (kind == EditorKind.Quick && (f.roleId == null || f.serverId == null)) return EditorProblem.ChooseRoleServer
        return null
    }

    /** Dart `_updateCharging`: on the phone, in parallel, late at night, it waits for the charger unless told. */
    public fun updateCharging(f: EditorForm, servers: List<TeamServer>, hour: Int): EditorForm {
        if (f.chargingTouched) return f
        val repos = allRepos(f)
        val phone = servers.any { s -> s.phone && (f.serverId == s.id || repos.any { it.serverId == s.id }) }
        return f.copy(settings = f.settings.copy(chargingOnly = phone && f.settings.mode == "parallel" && hour >= 23))
    }

    /** Dart `_costLine`: only measured numbers are shown. */
    public fun costLine(f: EditorForm, servers: List<TeamServer>): CostLine {
        val id = f.serverId ?: allRepos(f).firstOrNull()?.serverId
        val host = servers.firstOrNull { it.id == id } ?: return CostLine.NoHost
        val memory = host.memoryMb
        return if (memory == null || memory <= 0) CostLine.NotMeasured(host.name, host.phone)
        else CostLine.Measured(host.name, host.phone, kotlin.math.round(memory).toLong())
    }

    /** Dart `_projectName`: the typed name, else the goal's first line (40 characters). */
    public fun projectName(f: EditorForm): String {
        val name = f.value("name")
        if (name.isNotEmpty()) return name
        val goal = f.value("goal").lines().first().trim()
        return if (goal.length > 40) goal.take(40).trim() + "…" else goal
    }

    /** Dart `_lines`. */
    public fun lines(value: String): List<String> = value.split('\n').map { it.trim() }.filter { it.isNotEmpty() }

    /** Dart `_currentSpec`: the next version, from the fields. */
    public fun currentSpec(f: EditorForm, project: TeamProject?): TeamSpec =
        TeamSpec(
            version = (project?.specVersions?.lastOrNull()?.version ?: 0) + 1,
            goal = f.value("goal"),
            constraints = f.value("constraints"),
            decisions = f.value("decisions"),
            outOfScope = f.value("outOfScope"),
            contextFiles = lines(f.value("contextFiles")),
            milestones =
                f.milestones.map { m ->
                    m.copy(title = f.value("milestone-${m.id}"), criteria = lines(f.value("milestoneCriteria-${m.id}")))
                },
        )

    /** Dart `_saveSpec`'s check: a goal always; to approve, every milestone has a title and criteria. */
    public fun specError(spec: TeamSpec, approve: Boolean): EditorProblem? =
        if (spec.goal.isEmpty() || (approve && (spec.milestones.isEmpty() || spec.milestones.any { it.title.isEmpty() || it.criteria.isEmpty() })))
            EditorProblem.SpecRequired
        else null

    /** The plan's tasks with the edited titles and criteria (Dart `_save` for the plan). */
    public fun editedTasks(f: EditorForm): List<TeamTask> =
        f.tasks.map { t -> t.copy(title = f.value("task-${t.id}"), criteria = lines(f.value("criteria-${t.id}"))) }

    /** Dart `_orderedRoles`: Planner, Worker, Checker, then the others as they were made. */
    public fun orderedRoles(roles: List<TeamRole>): List<TeamRole> {
        val first = listOf("planner", "worker", "checker")
        return first.flatMap { id -> roles.filter { it.id == id } } + roles.filter { it.id !in first }
    }

    /**
     * Dart `_send`'s command: every editor sends the edited settings, the repos and the typed name and goal, so the
     * engine reads one complete edit.
     */
    public fun command(
        kind: EditorKind,
        action: String,
        f: EditorForm,
        projectId: String,
        spec: TeamSpec? = null,
        tasks: List<TeamTask>? = null,
        phases: List<TeamProjectPhase>? = null,
        role: TeamRole? = null,
        text: String? = null,
        expectedRevision: Long? = null,
    ): TeamCommand =
        TeamCommand(
            action = action,
            projectId = projectId,
            expectedRevision = expectedRevision ?: f.reviewedRevision,
            name = if (kind.creating) projectName(f) else f.value("name"),
            text = text ?: f.value("goal"),
            settings = editedSettings(f),
            spec = spec,
            tasks = tasks,
            phases = phases,
            repos = allRepos(f),
            roleId = f.roleId.orEmpty(),
            serverId = f.serverId.orEmpty(),
            role = role,
            confirmed = !f.planFirst,
        )

    /** Dart `_persist`'s payload: the whole form as JSON, under the editor's draft target. */
    public fun encode(f: EditorForm): String =
        DartJson.encode(
            linkedMapOf(
                "fields" to f.fields,
                "settings" to TeamJson.settings(f.settings),
                "repos" to f.repos.map(TeamJson::repo),
                "milestones" to f.milestones.map(TeamJson::milestone),
                "tasks" to f.tasks.map(TeamJson::task),
                "phases" to f.phases.map(TeamJson::phase),
                "server" to f.serverId,
                "roleId" to f.roleId,
                "budget" to f.budgetChoice,
                "planFirst" to f.planFirst,
                "chargingTouched" to f.chargingTouched,
                "role" to f.role?.let(TeamJson::role),
                "reviewedRevision" to f.reviewedRevision,
            )
        )

    /** Dart `_restore`: a saved payload over [base]; null when it does not read (the page then says so). */
    public fun decode(text: String, base: EditorForm): EditorForm? {
        val data = DartJson.decodeOrNull(text) as? Map<*, *> ?: return null
        val fields = data["fields"] as? Map<*, *> ?: return null
        fun maps(key: String) = (data[key] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
        return base.copy(
            fields = base.fields + fields.entries.mapNotNull { (k, v) -> (k as? String)?.let { it to (v as? String).orEmpty() } },
            settings = (data["settings"] as? Map<*, *>)?.let(TeamJson::readSettings) ?: base.settings,
            repos = maps("repos").map(TeamJson::readRepo),
            milestones = maps("milestones").map(TeamJson::readMilestone),
            tasks = maps("tasks").map(TeamJson::readTask),
            phases = maps("phases").map(TeamJson::readPhase),
            serverId = data["server"] as? String,
            roleId = data["roleId"] as? String,
            budgetChoice = data["budget"] as? String,
            planFirst = data["planFirst"] as? Boolean ?: true,
            chargingTouched = data["chargingTouched"] as? Boolean ?: false,
            role = (data["role"] as? Map<*, *>)?.let(TeamJson::readRole),
            reviewedRevision = (data["reviewedRevision"] as? Number)?.toLong() ?: base.reviewedRevision,
        )
    }

    /** The project command that saves this kind of editor (Dart `_save`), for the page's primary button. */
    public fun saveAction(kind: EditorKind, project: TeamProject?): String? =
        when (kind) {
            EditorKind.Create -> TeamAction.CREATE_PROJECT
            EditorKind.Quick -> TeamAction.CREATE_QUICK_TASK
            EditorKind.Spec -> TeamAction.APPROVE_SPEC
            EditorKind.Plan -> if (project?.status == "plan") TeamAction.APPROVE_PLAN else TeamAction.REPLAN
            EditorKind.Settings -> TeamAction.UPDATE_SETTINGS
            EditorKind.Defaults -> TeamAction.UPDATE_DEFAULTS
            EditorKind.Roles -> TeamAction.SAVE_ROLE
        }
}
