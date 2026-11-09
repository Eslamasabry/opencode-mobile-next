package io.github.eslamasabry.oc.engine.team

/**
 * How the app words the phone engine's workspace before any page sees it (Dart `_presentPhoneProject`,
 * phone_engine_gateway.dart at v2.1.5+63). The engine stays the truth: this only maps its raw words onto the ones the
 * pages use (an unapproved plan that failed is `planFailed`, a restart cut-off is `paused` so Resume shows, a checker
 * that finished but whose answer could not be read becomes an ask) and adds the two checkpoint lines to the timeline.
 * Reads never replay a prompt; nothing here is written back.
 */
public object TeamPhonePresentation {
    /** The engine's reason for a checker that finished but whose final answer could not be parsed. */
    public const val CHECKER_ANSWER_UNREADABLE: String = "structuredOutputInvalid"

    /** Dart `_resumableInterruption`: the restart and pause checkpoints, and a restart that cut a step off. */
    public fun resumableInterruption(reason: String): Boolean =
        reason == "restartNeedsReconciliation" || reason == "pauseNeedsReconciliation" || reason == "serverRestarted"

    /** Dart `_resumableTaskInterruption`: also a finished checker whose answer could not be read. */
    public fun resumableTaskInterruption(reason: String): Boolean =
        resumableInterruption(reason) || reason == CHECKER_ANSWER_UNREADABLE

    private val activeTaskStates =
        setOf(
            "starting", "resuming", "planning", "preparing", "submitting", "working", "checking", "review",
            "collecting", "merging", "promoting", "running",
        )

    private val admissionReasons =
        setOf(
            "jobNotQueued", "projectNotRunning", "serverOffline", "chatBusy", "chatStateUnknown", "chargingRequired",
            "chargingUnknown", "chooseExecutionMode", "serverCapUnknown", "laneCap", "invalidDependencies",
            "dependencyPending", "missingDependency", "chooseBudget", "totalUsageUnknown", "dailyUsageUnknown",
            "invalidBudget", "budgetReached", "tokenUsageUnknown", "taskTokenBudgetReached",
        )

    /** Dart `teamWorkspace()`'s map over projects; a simulated workspace (the demo) is left as it is. */
    public fun present(workspace: TeamWorkspace, commandActions: Set<String>): TeamWorkspace =
        if (workspace.simulated) workspace
        else workspace.copy(projects = workspace.projects.map { present(it, commandActions) })

    public fun present(project: TeamProject, commandActions: Set<String>): TeamProject {
        val planning = project.planningState
        val canResumeProject = TeamAction.RESUME_PROJECT in commandActions
        val canResumeTask = TeamAction.RESUME_TASK in commandActions
        var status = project.status
        if (status == "running" && hasCompletedPromotion(project)) status = "done"
        if (status == "needsPlanApproval") {
            status = "plan"
        } else if (
            !project.planApproved && planning != null && planning.stage in setOf("failed", "interrupted") &&
            status !in setOf("paused", "pausedBudget", "stopped") && !resumableInterruption(planning.reason) &&
            TeamAction.RETRY_PLAN in commandActions
        ) {
            // The editor's affordance for starting another plan; the failed checkpoint stays reviewable.
            status = "planFailed"
        } else if (!project.planApproved && (status == "planning" || status == "interrupted")) {
            status =
                when (planning?.stage) {
                    "starting", "planning", "preparing", "submitting", "running", "resuming" -> "running"
                    "failed" -> "failed"
                    "interrupted" -> if (resumableInterruption(planning.reason) && canResumeProject) "paused" else "failed"
                    "paused" -> "paused"
                    "stopped" -> "stopped"
                    else -> "waiting"
                }
        } else if (
            status == "interrupted" ||
            (status == "running" && project.planApproved && project.tasks.any { it.status == "interrupted" } &&
                project.tasks.none { it.status in activeTaskStates })
        ) {
            val interrupted = project.tasks.filter { it.status == "interrupted" }
            status =
                if (interrupted.isNotEmpty() && interrupted.all { resumableTaskInterruption(it.reason) } && canResumeProject) "paused"
                else "failed"
        }
        val summary =
            if (!project.planApproved && planning != null && planning.stage in setOf("failed", "interrupted")) {
                planningSummary(
                    planning,
                    canResume = project.status != "stopped" && resumableInterruption(planning.reason) && canResumeProject,
                )
            } else null
        val tasks =
            project.tasks.map { task ->
                val needsFix = task.status == "needsFix"
                task.copy(
                    status =
                        when {
                            task.status == "checked" -> "verified"
                            needsFix -> "review"
                            task.status == "merging" || task.status == "resuming" -> "running"
                            task.status == "interrupted" && project.status != "stopped" &&
                                resumableTaskInterruption(task.reason) && canResumeTask -> "paused"
                            task.status == "interrupted" -> "review"
                            else -> task.status
                        },
                    reason = if (needsFix && task.reason.isEmpty()) "checkerFindings" else task.reason,
                    // A checker proposal cannot close its own findings.
                    findings = if (needsFix) task.findings.map { it.copy(status = "open") } else task.findings,
                )
            }
        val unreadable =
            if (project.status != "stopped" && canResumeTask) {
                project.tasks
                    .filter { it.status == "interrupted" && it.reason == CHECKER_ANSWER_UNREADABLE }
                    .map {
                        TeamRequest(
                            id = "phone-interrupted-${it.id}",
                            kind = "interrupted",
                            title = "The check finished, but its answer could not be read",
                            taskId = it.id,
                            createdAt = it.changedAt,
                        )
                    }
            } else emptyList()
        val checkpoints =
            buildList {
                if (summary != null) {
                    add(
                        TeamTimelineEvent(
                            id = "phone-planning-checkpoint", kind = "planningCheckpoint", actor = "engine",
                            at = planning?.updatedAt.orEmpty(), text = summary,
                        ),
                    )
                }
                project.tasks.filter { it.status == "interrupted" }.forEach { task ->
                    add(
                        TeamTimelineEvent(
                            id = "phone-task-checkpoint-${task.id}", kind = "taskCheckpoint", actor = "engine",
                            at = task.changedAt, taskId = task.id,
                            text =
                                taskSummary(
                                    task,
                                    canResume = project.status != "stopped" && resumableTaskInterruption(task.reason) && canResumeTask,
                                ),
                        ),
                    )
                }
            }
        return project.copy(
            status = status,
            tasks = tasks,
            requests = project.requests + unreadable,
            timeline = project.timeline + checkpoints,
        )
    }

    /** Dart `_taskCheckpointSummary`: application-authored codes only, never paths or model text. */
    internal fun taskSummary(task: TeamTask, canResume: Boolean): String {
        val reason =
            when {
                task.reason == CHECKER_ANSWER_UNREADABLE -> "checkerAnswerUnreadable"
                task.reason in TASK_REASONS || task.reason in admissionReasons -> task.reason
                else -> ""
            }
        if (canResume) return "Work was interrupted. Resume to check its existing session ($reason)."
        return if (reason.isEmpty()) "Work was interrupted and needs review." else "Work was interrupted and needs review ($reason)."
    }

    /** Dart `_planningCheckpointSummary`. */
    internal fun planningSummary(planning: TeamPlanningState, canResume: Boolean): String {
        val reason = if (planning.reason in PLANNING_REASONS || planning.reason in admissionReasons) planning.reason else ""
        if (reason.isEmpty()) return "Planning stopped and needs review."
        if (canResume && planning.stage == "interrupted") {
            return "Planning was interrupted. Resume to check its existing session ($reason)."
        }
        return if (planning.stage == "failed") "Planning failed ($reason)." else "Planning needs review ($reason)."
    }

    private val TASK_REASONS =
        setOf(
            "restartNeedsReconciliation", "pauseNeedsReconciliation", "serverRestarted", "restartResend",
            "recoveryNeedsReview", "sessionUnknown", "sessionFailed", "sessionUncertain", "promptUncertain",
            "sessionCreateUncertain",
        )

    private val PLANNING_REASONS =
        setOf(
            "sessionFailed", "promptUncertain", "modelUnavailable", "modelNotConfigured", "modelInvalid", "invalid_model",
            "invalid_role", "authentication_failed", "transport_unavailable", "cloneFailed", "sessionCreateUncertain",
            "permission_policy_unconfirmed", "sessionUnknown", "sessionUncertain", "needsAnswer", "structuredOutputInvalid",
            "invalidPlan", "planTaskTitleRequired", "planPhaseInvalid", "usageUncertain", "recoveryNeedsReview",
            "restartNeedsReconciliation", "pauseNeedsReconciliation", "serverRestarted", "restartResend",
        )

    /** Dart `_hasCompletedPhonePromotion`: presentation follows confirmed repository truth, no synthetic receipt. */
    internal fun hasCompletedPromotion(project: TeamProject): Boolean {
        if (!project.planApproved || project.tasks.isEmpty() || project.mergeQueue.isEmpty()) return false
        val tasks = LinkedHashMap<String, TeamTask>()
        for (task in project.tasks) {
            if (task.id.isEmpty() || tasks.containsKey(task.id) || task.repoId.isEmpty() || task.status != "merged" ||
                task.findings.any { it.status == "open" }
            ) {
                return false
            }
            tasks[task.id] = task
        }
        val covered = HashSet<String>()
        val mergeIds = HashSet<String>()
        for (item in project.mergeQueue) {
            val task = tasks[item.taskId]
            if (item.id.isEmpty() || !mergeIds.add(item.id) || !item.checksPassed || item.status != "merged" ||
                task == null || item.repoId != task.repoId
            ) {
                return false
            }
            covered += task.id
        }
        if (covered.size != tasks.size) return false
        for (repoId in tasks.values.map { it.repoId }.toSet()) {
            val repos = project.repos.filter { it.id == repoId }
            if (repos.size != 1) return false
            val repo = repos.single()
            if (repo.devCommit.isEmpty() || repo.devCommit != repo.mainCommit) return false
            val promoted =
                project.receipts.any {
                    it.id.isNotEmpty() && it.kind == "promote" && it.actor == "engine" && it.repoId == repoId &&
                        it.before.isNotEmpty() && it.before != it.after && it.after == repo.mainCommit
                }
            if (!promoted) return false
        }
        return true
    }
}
