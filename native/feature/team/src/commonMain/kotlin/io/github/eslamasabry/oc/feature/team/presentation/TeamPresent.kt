package io.github.eslamasabry.oc.feature.team.presentation

import io.github.eslamasabry.oc.engine.team.TeamEngine
import io.github.eslamasabry.oc.engine.team.TeamProject
import io.github.eslamasabry.oc.engine.team.TeamState
import io.github.eslamasabry.oc.engine.team.TeamWorkspace
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow

/**
 * The engine's state for [profileId] paired with the clock, reading the engine again every [TeamEngine.POLL_MS]
 * while someone collects it (Dart `PhoneEngineGateway`'s 3 s poll). Stops when the page stops collecting.
 */
internal fun TeamEngine.polled(profileId: String, nowMs: () -> Long): Flow<Pair<TeamState, Long>> {
    val ticks =
        flow {
            while (true) {
                refresh(profileId)
                emit(nowMs())
                delay(TeamEngine.POLL_MS)
            }
        }
    return combine(state(profileId), ticks) { state, _ -> state to nowMs() }
}

/** Pure mapping from the engine's state to each page (tested without Compose). */
public object TeamPresent {
    public fun home(state: TeamState, nowMs: Long): TeamHomeUi {
        val rows =
            TeamRules.ordered(state.workspace).flatMap { p ->
                p.openRequests.map { HomeRow.Request(p.id, it.id, it.title, p.name) } + projectRow(p, state.stale, nowMs)
            }
        return TeamHomeUi(
            phase = state.phase,
            rows = rows.toImmutableList(),
            stale = state.stale,
            demo = state.workspace?.simulated == true,
            loading = state.loading && state.workspace == null,
            nowMs = nowMs,
        )
    }

    private fun projectRow(p: TeamProject, stale: Boolean, nowMs: Long): HomeRow.Project {
        val budget = p.settings.budget
        return HomeRow.Project(
            id = p.id,
            name = p.name,
            headline = TeamRules.headline(p, nowMs),
            mark = if (stale) TeamMark.Stale else TeamRules.mark(p.status),
            budgetWarning = p.budgetWarning,
            spentToday = p.spentToday.takeIf { p.usageReported },
            dailyLimit = budget.daily.takeIf { !budget.unlimited },
            updatedAtMs = TeamRules.epochMs(p.updatedAt),
        )
    }

    public fun project(state: TeamState, projectId: String, nowMs: Long): TeamProjectUi {
        val workspace = state.workspace
        val p = workspace?.project(projectId)
        return TeamProjectUi(state.phase, p?.let { view(workspace, it, nowMs) }, state.stale, nowMs)
    }

    private fun view(workspace: TeamWorkspace, p: TeamProject, nowMs: Long): ProjectView {
        fun role(id: String) = workspace.roles.firstOrNull { it.id == id }?.name
        val needsYou =
            p.openRequests.map { r ->
                val task = p.tasks.firstOrNull { it.id == r.taskId }
                NeedsYouUi(
                    id = r.id,
                    title = r.title,
                    kind = r.kind,
                    roleName = task?.let { role(it.roleId) },
                    serverName = task?.let { t -> workspace.servers.firstOrNull { it.id == t.serverId }?.name },
                    blocks = task?.let { t -> p.tasks.count { t.id in it.dependsOn } } ?: 0,
                )
            }
        val milestones = p.specDraft.milestones
        val current = milestones.indexOfFirst { !it.accepted }
        val milestoneRows =
            milestones.mapIndexed { i, m ->
                val tasks = TeamRules.tasksOf(p, m.id)
                val complete = tasks.count { it.finished }
                val started = i == current || tasks.any { it.status != "queued" }
                val showBar = started && !m.accepted
                MilestoneUi(
                    id = m.id,
                    number = i + 1,
                    title = m.title,
                    mark =
                        when {
                            m.accepted -> TeamMark.Done
                            tasks.any { it.status == "running" } -> TeamMark.Running
                            else -> TeamMark.Empty
                        },
                    status =
                        when {
                            m.accepted -> MilestoneStatus.Done
                            tasks.isEmpty() -> MilestoneStatus.NoTasks
                            started -> MilestoneStatus.Tasks(complete, tasks.size)
                            else -> MilestoneStatus.Waits(i)
                        },
                    completed = complete.takeIf { showBar },
                    total = tasks.size.takeIf { showBar },
                )
            }
        val (busy, waiting) = TeamRules.lanes(p)
        val lanes =
            (busy + waiting).map { t ->
                val server = workspace.servers.firstOrNull { it.id == t.serverId }
                LaneUi(
                    taskId = t.id,
                    roleName = role(t.roleId),
                    title = t.title,
                    running = t.status == "running",
                    serverName = server?.name.orEmpty(),
                    serverOffline = server != null && !server.online,
                    changedAtMs = TeamRules.epochMs(t.changedAt),
                )
            }
        val spec = p.specDraft
        val approved = TeamRules.epochMs(p.specVersions.lastOrNull()?.approvedAt.orEmpty())
        // The spec editor drafts one milestone from the goal the first time it opens; a draft counts it.
        val drafted = if (spec.milestones.isEmpty() && spec.goal.isNotBlank()) 1 else spec.milestones.size
        return ProjectView(
            id = p.id,
            name = p.name,
            headline = TeamRules.headline(p, nowMs),
            needsYou = needsYou.toImmutableList(),
            goal = GoalUi(spec.goal, approved, if (approved == null) drafted else spec.milestones.size, p.repos.size),
            milestones = milestoneRows.toImmutableList(),
            lanes = lanes.toImmutableList(),
            busy = busy.size,
            laneTotal = TeamRules.laneTotal(p),
            singleLane = p.settings.mode == "single",
            demo = p.simulated,
            taskCount = p.tasks.size,
        )
    }

    public data class BoardFilters(
        val graph: Boolean = false,
        val milestone: String? = null,
        val repo: String? = null,
        val server: String? = null,
        val column: BoardColumn? = null,
    )

    public fun board(state: TeamState, projectId: String, filters: BoardFilters): TeamBoardUi {
        val workspace = state.workspace
        val p = workspace?.project(projectId)
        if (workspace == null || p == null) return TeamBoardUi(state.phase, found = workspace == null)
        val cards =
            TeamRules.tasksOf(p, filters.milestone)
                .filter { (filters.repo == null || it.repoId == filters.repo) && (filters.server == null || it.serverId == filters.server) }
                .map { BoardCardUi(it.id, it.title, TeamRules.taskMark(it), TeamRules.column(it), it.status, it.dependsOn.toImmutableList()) }
        val column = filters.column ?: BoardColumn.entries.firstOrNull { c -> cards.any { it.column == c } } ?: BoardColumn.Backlog
        return TeamBoardUi(
            phase = state.phase,
            projectName = p.name,
            graph = filters.graph,
            milestones = p.specDraft.milestones.map { FilterChoice(it.id, it.title) }.toImmutableList(),
            repos = p.repos.map { FilterChoice(it.id, it.name) }.toImmutableList(),
            servers = workspace.servers.map { FilterChoice(it.id, it.name) }.toImmutableList(),
            milestone = filters.milestone,
            repo = filters.repo,
            server = filters.server,
            cards = cards.toImmutableList(),
            column = column,
        )
    }
}
