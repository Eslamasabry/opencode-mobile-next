package io.github.eslamasabry.oc.feature.team.presentation

import io.github.eslamasabry.oc.engine.team.TeamEngine
import io.github.eslamasabry.oc.engine.team.TeamCapabilities
import io.github.eslamasabry.oc.engine.team.TeamProject
import io.github.eslamasabry.oc.engine.team.TeamProjectPhase
import io.github.eslamasabry.oc.engine.team.TeamTask
import io.github.eslamasabry.oc.engine.team.TeamRepo
import io.github.eslamasabry.oc.engine.team.TeamRequest
import io.github.eslamasabry.oc.engine.team.TeamState
import io.github.eslamasabry.oc.engine.team.TeamWorkspace
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
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
    private val RECEIPT_KINDS = setOf("merge", "promote", "promotion")

    /** Dart `_answer` and `_requestLabel` for one request of [p]. */
    public fun ask(p: TeamProject, r: TeamRequest, can: TeamCapabilities): Triple<AskAnswer, AskLabel, Boolean> =
        when (r.kind) {
            "spec" -> Triple(AskAnswer.Editor(EditorKind.Spec), AskLabel.Spec, true)
            "plan", "planFormat" -> Triple(AskAnswer.Editor(EditorKind.Plan), AskLabel.Plan, true)
            "budget" -> Triple(AskAnswer.Editor(EditorKind.Settings), AskLabel.Settings, true)
            "milestone" -> {
                val m = p.specDraft.milestones.firstOrNull { it.id == r.phaseId }
                Triple(
                    m?.let { AskAnswer.AcceptMilestone(p.id, it.id, (listOf(it.title) + it.criteria).joinToString("\n")) } ?: AskAnswer.None,
                    AskLabel.Accept,
                    true,
                )
            }
            "phase" -> Triple(p.tasks.firstOrNull { it.phaseId == r.phaseId }?.let { AskAnswer.Task(it.id) } ?: AskAnswer.None, AskLabel.Review, true)
            "interrupted" -> Triple(AskAnswer.Resume(r.taskId), AskLabel.Resume, can.resume)
            "question", "permission" -> Triple(AskAnswer.Text, AskLabel.Answer, true)
            else -> Triple(if (r.taskId.isNotEmpty()) AskAnswer.Task(r.taskId) else AskAnswer.None, AskLabel.Review, true)
        }

    private fun refusal(state: TeamState): RefusalUi? = state.refusal?.let { RefusalUi(it, state.refusedAction.orEmpty()) }

    public fun home(state: TeamState, nowMs: Long): TeamHomeUi {
        val can = state.capabilities
        val rows =
            TeamRules.ordered(state.workspace).flatMap { p ->
                listOfNotNull(HomeRow.Interrupted(p.id, p.name).takeIf { p.status == "interrupted" && can.resume }) +
                    p.openRequests.map { HomeRow.Request(p.id, it.id, it.title, p.name, ask(p, it, can).first) } +
                    projectRow(p, state.stale, nowMs)
            }
        val real = state.workspace?.let { !it.simulated } == true
        return TeamHomeUi(
            phase = state.phase,
            rows = rows.toImmutableList(),
            stale = state.stale,
            demo = state.workspace?.simulated == true,
            loading = state.loading && state.workspace == null,
            nowMs = nowMs,
            blocked = real && !can.lanes,
            protection = can.boundaryTier.takeIf { real && can.lanes && (it == "proot" || it == "landlock") },
            resumeUnavailable = state.workspace?.projects.orEmpty().any { it.status == "interrupted" } && can.lanes && !can.resume,
            canStart = can.lanes,
            refusal = refusal(state),
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
        return TeamProjectUi(
            phase = state.phase,
            project = p?.let { view(workspace, it, nowMs, state.capabilities) },
            stale = state.stale,
            nowMs = nowMs,
            busy = state.busy,
            refused = state.refusal != null,
            refusal = refusal(state),
        )
    }

    /** Dart `teamCanPromote`: dev is ahead of main, every task of the repo merged clean, every check passed. */
    public fun canPromote(p: TeamProject, repo: TeamRepo?): Boolean {
        if (repo == null || !p.planApproved || repo.devCommit == repo.mainCommit) return false
        val tasks = p.tasks.filter { it.repoId == repo.id }
        val merges = p.mergeQueue.filter { it.repoId == repo.id }
        if (tasks.isEmpty() || tasks.any { t -> t.status != "merged" || t.findings.any { it.status == "open" } }) return false
        if (merges.isEmpty() || merges.any { it.status != "merged" || !it.checksPassed }) return false
        val phases = tasks.map { it.phaseId }.toSet()
        return p.phases.none { it.id in phases && (it.risky || p.settings.reviewLevel == "everyStep") && !it.accepted }
    }

    /** Dart overview's digest: shown when there is a timeline and it was not read in the last four hours. */
    private fun digest(p: TeamProject, nowMs: Long, can: TeamCapabilities): DigestUi? {
        val readAt = TeamRules.epochMs(p.digestReadAt)
        if (p.timeline.isEmpty() || (readAt != null && nowMs - readAt < 4 * 3_600_000L)) return null
        val events = TeamReasons.digest(p.timeline, readAt)
        return DigestUi(
            items = events.take(6).map { DigestItem(it.text, TeamRules.epochMs(it.at)) }.toImmutableList(),
            demo = p.simulated,
            needsModel = events.any { e -> TeamReasons.timelineCode(e.text)?.let(TeamReasons::needsModel) == true },
            resume = p.status == "interrupted" && can.resume,
        )
    }

    private fun view(workspace: TeamWorkspace, p: TeamProject, nowMs: Long, can: TeamCapabilities): ProjectView {
        fun role(id: String) = workspace.roles.firstOrNull { it.id == id }?.name
        val needsYou =
            p.openRequests.map { r ->
                val task = p.tasks.firstOrNull { it.id == r.taskId }
                val (answer, label, actionable) = ask(p, r, can)
                NeedsYouUi(
                    answer = answer,
                    label = label,
                    actionable = actionable,
                    id = r.id,
                    title = r.title,
                    kind = r.kind,
                    roleName = task?.let { role(it.roleId) },
                    serverName = task?.let { t -> workspace.servers.firstOrNull { it.id == t.serverId }?.name },
                    blocks = task?.let { t -> p.tasks.count { t.id in it.dependsOn } } ?: 0,
                    taskId = r.taskId,
                    milestoneId = p.specDraft.milestones.firstOrNull { it.id == r.phaseId }?.id.orEmpty(),
                    milestoneText =
                        p.specDraft.milestones.firstOrNull { it.id == r.phaseId }?.let { m ->
                            (listOf(m.title) + m.criteria).joinToString("\n")
                        }.orEmpty(),
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
                    canAccept =
                        !m.accepted && tasks.isNotEmpty() && tasks.all { it.status == "merged" } &&
                            p.phases.filter { it.milestoneId == m.id }.all { it.accepted },
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
            menu =
                (p.status != "stopped" && p.status != "done").let { open ->
                    ProjectMenu(
                        askAgain = p.status == "spec" && TeamRules.planningMinutes(p, nowMs) >= TeamRules.PLANNING_SLOW_MINUTES,
                        resumeInterrupted = p.status == "interrupted" && can.resume,
                        // F: overview.dart:90-101 offers Pause on an interrupted project too, beside Resume.
                        pause = open && p.status != "paused",
                        resume = open && p.status == "paused" && can.lanes,
                    )
                },
            status = p.status,
            showPlan = p.status == "plan" || p.status == "planned",
            planFailed =
                if ((p.status == "failed" || p.status == "planFailed") && !p.planApproved) {
                    val code = p.planningState?.reason.orEmpty()
                    PlanFailedUi(
                        code = code,
                        canRetry = p.status == "planFailed",
                        resumable =
                            p.planningState?.stage == "interrupted" &&
                                (code == "restartNeedsReconciliation" || code == "pauseNeedsReconciliation") && can.resume,
                        needsModel = TeamReasons.needsModel(code),
                    )
                } else null,
            digest = digest(p, nowMs, can),
            merges =
                p.repos.mapNotNull { repo ->
                    val open = p.mergeQueue.filter { it.repoId == repo.id && it.status != "merged" }
                    if (open.isEmpty()) return@mapNotNull null
                    MergeQueueUi(
                        repoId = repo.id,
                        repoName = repo.name,
                        items =
                            open.map { i ->
                                MergeItemUi(
                                    taskId = i.taskId,
                                    title = p.tasks.firstOrNull { it.id == i.taskId }?.title ?: repo.name,
                                    reason = i.reason,
                                    status = i.status,
                                    checksPassed = i.checksPassed,
                                    mark = TeamRules.mark(i.status),
                                )
                            }.toImmutableList(),
                        canMerge = can.mergeQueue,
                        waiting = p.mergeQueue.count { it.repoId == repo.id && it.status == "queued" },
                    )
                }.toImmutableList(),
            receipts =
                p.repos.flatMap { repo ->
                    p.receipts.filter { it.repoId == repo.id && it.kind in RECEIPT_KINDS }.takeLast(3).map {
                        ReceiptUi(repo.name, it.kind == "merge", it.before, it.after, TeamRules.epochMs(it.at))
                    }
                }.toImmutableList(),
            promotions =
                p.repos.filter { canPromote(p, it) && can.promotion }
                    .map { PromoteUi(it.id, it.name, it.mainCommit, it.devCommit) }
                    .toImmutableList(),
            decisions =
                p.timeline.filter { it.kind == "decision" }.reversed().take(3).map { e ->
                    DecisionUi(e.text, e.actor == "person", workspace.roles.firstOrNull { it.id == e.actor }?.name, TeamRules.epochMs(e.at))
                }.toImmutableList(),
            boardMilestones =
                p.specDraft.milestones.count { m -> p.tasks.any { t -> p.phases.any { it.id == t.phaseId && it.milestoneId == m.id } } }
                    .coerceAtLeast(1),
            timelineCount = p.timeline.size,
            timelineLastAtMs = p.timeline.lastOrNull()?.let { TeamRules.epochMs(it.at) },
            serverCount = (p.repos.map { it.serverId } + p.tasks.map { it.serverId }).filter { it.isNotEmpty() }.toSet().size,
            maxLanes = p.settings.maxLanes,
            cost =
                p.settings.budget.let { b ->
                    CostUi(p.usageReported, p.spentToday, b.daily.takeIf { !b.unlimited }, p.spent, b.total.takeIf { !b.unlimited })
                },
            budgetWarning = p.budgetWarning,
            canDelete = can.lifecycle && !p.simulated,
            blocked = !p.simulated && !can.lanes,
            resumeUnavailable = p.status == "interrupted" && can.lanes && !can.resume,
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

    /** Dart `_requestActions` for one ask of [t]. */
    private fun askActions(p: TeamProject, t: TeamTask, r: TeamRequest, can: TeamCapabilities): List<TaskAskAction> =
        when (r.kind) {
            "question", "permission" -> listOf(TaskAskAction.Answer)
            "interrupted" -> listOfNotNull(TaskAskAction.Resume.takeIf { can.resume })
            "stalled", "failed" -> listOfNotNull(TaskAskAction.Restart.takeIf { can.lanes })
            "findings" -> listOf(TaskAskAction.ReviewFindings)
            "conflict" -> {
                val item = p.mergeQueue.firstOrNull { it.taskId == t.id && (it.status == "conflict" || it.status == "manual") }
                if (item == null) emptyList()
                else
                    listOfNotNull(
                        TaskAskAction.Resolve(item.id, "agent"),
                        TaskAskAction.Resolve(item.id, "manual"),
                        TaskAskAction.Resolve(item.id, "recheck").takeIf { item.reason == "Waiting for your conflict resolution" },
                    )
            }
            else -> emptyList()
        }

    /** Dart `_planCard`: the plan for the task's milestone, four task lines at most. */
    private fun plan(workspace: TeamWorkspace, p: TeamProject, phase: TeamProjectPhase?, can: TeamCapabilities): PlanUi {
        val milestones = p.specDraft.milestones
        val mIndex = phase?.let { ph -> milestones.indexOfFirst { it.id == ph.milestoneId } } ?: -1
        val phases = p.phases.filter { phase == null || it.milestoneId == phase.milestoneId }
        val ordered = phases.flatMap { f -> p.tasks.filter { it.phaseId == f.id } }
        val movable = workspace.servers.size > 1 && can.placement
        var shown = 0
        val cards =
            phases.mapIndexedNotNull { i, f ->
                val tasks =
                    p.tasks.filter { it.phaseId == f.id }.mapNotNull { task ->
                        if (shown >= 4) return@mapNotNull null
                        shown++
                        val server = workspace.servers.firstOrNull { it.id == task.serverId }
                        PlanTaskUi(
                            taskId = task.id,
                            number = ordered.indexOf(task) + 1,
                            title = task.title,
                            repoName = p.repos.firstOrNull { it.id == task.repoId }?.name,
                            after = task.dependsOn.map { id -> ordered.indexOfFirst { it.id == id } + 1 }.firstOrNull { it > 0 },
                            criteria = task.criteria.size,
                            roleName = workspace.roles.firstOrNull { it.id == task.roleId }?.name,
                            serverName = server?.name,
                            serverId = task.serverId,
                            canMove = movable && server != null,
                        )
                    }
                if (tasks.isEmpty()) null else PlanPhaseUi(i + 1, f.title, f.risky, tasks)
            }
        return PlanUi(
            milestone = (mIndex + 1).takeIf { mIndex >= 0 },
            phases = cards,
            phaseCount = phases.size,
            taskCount = ordered.size,
            repoCount = ordered.map { it.repoId }.toSet().size,
            more = (ordered.size - shown).coerceAtLeast(0),
            serverFixed = workspace.servers.size > 1 && !can.placement,
            canApprove = can.lanes,
        )
    }

    /** Dart `_promoteCard`. */
    private fun promote(p: TeamProject, t: TeamTask, repo: TeamRepo, can: TeamCapabilities, nowMs: Long): TaskPromoteUi {
        val phase = p.phases.firstOrNull { it.id == t.phaseId }
        val milestones = p.specDraft.milestones
        val mIndex = phase?.let { ph -> milestones.indexOfFirst { it.id == ph.milestoneId } } ?: -1
        val phaseIds = p.phases.filter { phase == null || it.milestoneId == phase.milestoneId }.map { it.id }.toSet()
        val tasks = p.tasks.filter { it.phaseId in phaseIds }
        val started = p.timeline.mapNotNull { TeamRules.epochMs(it.at) }.minOrNull()
        val repoTasks = p.tasks.filter { it.repoId == repo.id }
        return TaskPromoteUi(
            repoId = repo.id,
            repoName = repo.name,
            milestone = (mIndex + 1).takeIf { mIndex >= 0 },
            milestoneTitle = milestones.getOrNull(mIndex)?.title.orEmpty(),
            projectName = p.name,
            merged = tasks.count { it.status == "merged" },
            tasks = tasks.size,
            days = started?.let { ((nowMs - it) / 86_400_000L).toInt() }?.takeIf { it > 0 },
            reviewed = phase?.accepted == true && mIndex >= 0,
            commits = p.mergeQueue.count { it.repoId == repo.id && it.status == "merged" },
            files = repoTasks.sumOf { x -> Regex("^diff --git", RegexOption.MULTILINE).findAll(x.diff).count() },
            mainCommit = repo.mainCommit,
            devCommit = repo.devCommit,
            canPromote = can.promotion,
        )
    }

    public fun task(state: TeamState, projectId: String, taskId: String, nowMs: Long, selected: Set<String> = emptySet()): TeamTaskUi {
        val workspace = state.workspace
        val p = workspace?.project(projectId)
        val t = p?.tasks?.firstOrNull { it.id == taskId }
        val can = state.capabilities
        val view =
            if (workspace == null || p == null || t == null) null
            else {
                val running = !state.stale && t.status in RUNNING
                val requests = p.openRequests.filter { it.taskId == t.id }
                val phase = p.phases.firstOrNull { it.id == t.phaseId }
                val repo = p.repos.firstOrNull { it.id == t.repoId }
                val queue = p.mergeQueue.filter { it.taskId == t.id }
                val events = p.timeline.filter { it.taskId == t.id }
                val planning = p.status == "plan" || p.status == "planned"
                val promotable = canPromote(p, repo)
                val phaseTasks = phase?.let { ph -> p.tasks.filter { it.phaseId == ph.id } }.orEmpty()
                val phaseAsks = phase != null && p.openRequests.any { it.phaseId == phase.id }
                val phaseMerged = phaseTasks.isNotEmpty() && phaseTasks.all { it.status == "merged" }
                val open = t.findings.filter { it.status == "open" }
                val interruptedAsk = requests.any { it.kind == "interrupted" }
                TaskView(
                    id = t.id,
                    title = t.title,
                    roleName = workspace.roles.firstOrNull { it.id == t.roleId }?.name,
                    serverName = workspace.servers.firstOrNull { it.id == t.serverId }?.name,
                    status = t.status,
                    queued = t.status == "queued" && !planning,
                    lines =
                        t.messages
                            .map { m ->
                                TaskLineUi(
                                    m.id,
                                    when (m.actor) {
                                        "person" -> TaskSpeaker.Person
                                        "team" -> TaskSpeaker.Instructions
                                        else -> TaskSpeaker.Worker
                                    },
                                    m.text,
                                    TeamRules.epochMs(m.at),
                                )
                            }
                            .toImmutableList(),
                    asks =
                        requests.map { TaskAskUi(it.id, it.title, it.kind, TeamRules.epochMs(it.createdAt), askActions(p, t, it, can)) }
                            .toImmutableList(),
                    criteria = t.criterionResults.map { CriterionUi(it.criterion, it.status) }.toImmutableList(),
                    findings =
                        t.findings.map { FindingUi(it.id, it.severity, it.text, it.location, it.status == "open") }.toImmutableList(),
                    running = running,
                    plan = if (planning) plan(workspace, p, phase, can) else null,
                    phase =
                        phase?.takeIf { t.status == "failed" || phaseAsks || (!it.accepted && phaseMerged) }?.let {
                            PhaseUi(it.id, it.title, it.accepted, t.status == "failed", phaseAsks, t.finished, !it.accepted && phaseMerged)
                        },
                    events =
                        (if (running) events.takeLast(3) else events).map { DigestItem(it.text, TeamRules.epochMs(it.at)) }
                            .toImmutableList(),
                    finished = t.status in DONE,
                    checkerName = workspace.roles.firstOrNull { it.readOnly }?.name,
                    critical = open.count { it.severity == "critical" },
                    major = open.count { it.severity == "major" },
                    minor = open.count { it.severity == "minor" },
                    canVerify = can.verification,
                    merge =
                        queue.takeIf { q -> q.any { it.status != "merged" } }?.let { q ->
                            TaskMergeUi(t.repoId, q.first().status, q.map { it.reason to it.status }, can.mergeQueue)
                        },
                    mergeReceipts =
                        p.receipts.filter { it.repoId == t.repoId && it.kind == "merge" }.takeLast(3)
                            .map { ReceiptUi(repo?.name.orEmpty(), true, it.before, it.after, TeamRules.epochMs(it.at)) }
                            .toImmutableList(),
                    promote = if (promotable && repo != null) promote(p, t, repo, can, nowMs) else null,
                    promoteReceipts =
                        p.receipts.filter { it.repoId == t.repoId && (it.kind == "promote" || it.kind == "promotion") }
                            .map { ReceiptUi(repo?.name.orEmpty(), false, it.before, it.after, TeamRules.epochMs(it.at)) }
                            .toImmutableList(),
                    controls =
                        TaskControls(
                            diff = t.diff.isNotEmpty() && !promotable,
                            pause = running,
                            resume =
                                (t.status == "paused" || t.status == "interrupted") && !interruptedAsk &&
                                    (if (t.status == "interrupted") can.resume else can.lanes),
                            restart = (t.status == "failed" || t.status == "stopped") && can.lanes,
                            verify = t.status == "done" && can.verification,
                            stop = running || t.status == "paused" || requests.isNotEmpty(),
                        ),
                    diff = t.diff,
                    branch = t.branch,
                    servers = workspace.servers.map { it.id to it.name }.toImmutableList(),
                )
            }
        val open = t?.findings?.filter { it.status == "open" }?.map { it.id }?.toSet().orEmpty()
        return TeamTaskUi(
            state.phase,
            view,
            state.stale,
            state.busy,
            state.refusal != null,
            nowMs,
            selected = selected.intersect(open).toImmutableSet(),
            refusal = refusal(state),
        )
    }

    private val RUNNING = setOf("running", "verifying", "fixing")
    private val DONE = setOf("done", "merged", "accepted")
}
