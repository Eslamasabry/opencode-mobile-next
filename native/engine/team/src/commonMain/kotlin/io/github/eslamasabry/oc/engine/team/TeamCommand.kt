package io.github.eslamasabry.oc.engine.team

/** The engine's command words (Dart `TeamProjectAction.name`), the ones the app sends. */
public object TeamAction {
    public const val USE_PLAN_AS_TASK: String = "usePlanAsTask"
    public const val RETRY_PLAN: String = "retryPlan"
    public const val REQUEST_SPEC_CHANGE: String = "requestSpecChange"
    public const val UPDATE_DEFAULTS: String = "updateDefaults"
    public const val CREATE_PROJECT: String = "createProject"
    public const val CREATE_QUICK_TASK: String = "createQuickTask"
    public const val SAVE_SPEC_DRAFT: String = "saveSpecDraft"
    public const val APPROVE_SPEC: String = "approveSpec"
    public const val APPROVE_PLAN: String = "approvePlan"
    public const val REPLAN: String = "replan"
    public const val ANSWER_REQUEST: String = "answerRequest"
    public const val MESSAGE_TASK: String = "messageTask"
    public const val PAUSE_PROJECT: String = "pauseProject"
    public const val RESUME_PROJECT: String = "resumeProject"
    public const val STOP_PROJECT: String = "stopProject"
    public const val PAUSE_TASK: String = "pauseTask"
    public const val RESUME_TASK: String = "resumeTask"
    public const val RESTART_TASK: String = "restartTask"
    public const val STOP_TASK: String = "stopTask"
    public const val MOVE_TASK: String = "moveTask"
    public const val VERIFY_TASK: String = "verifyTask"
    public const val FIX_FINDINGS: String = "fixFindings"
    public const val RECHECK_TASK: String = "recheckTask"
    public const val IGNORE_FINDING: String = "ignoreFinding"
    public const val UPDATE_SETTINGS: String = "updateSettings"
    public const val PROCESS_MERGE_QUEUE: String = "processMergeQueue"
    public const val RESOLVE_CONFLICT: String = "resolveConflict"
    public const val PROMOTE: String = "promote"
    public const val ACKNOWLEDGE_DIGEST: String = "acknowledgeDigest"
    public const val ACCEPT_PHASE: String = "acceptPhase"
    public const val ACCEPT_MILESTONE: String = "acceptMilestone"
    public const val SAVE_ROLE: String = "saveRole"
    public const val UPDATE_SERVER: String = "updateServer"
    public const val DELETE_PROJECT: String = "deleteProject"

    /** Every word above (for fakes and the coverage list). */
    public fun all(): Set<String> =
        setOf(
            USE_PLAN_AS_TASK, RETRY_PLAN, REQUEST_SPEC_CHANGE, UPDATE_DEFAULTS, CREATE_PROJECT, CREATE_QUICK_TASK,
            SAVE_SPEC_DRAFT, APPROVE_SPEC, APPROVE_PLAN, REPLAN, ANSWER_REQUEST, MESSAGE_TASK, PAUSE_PROJECT,
            RESUME_PROJECT, STOP_PROJECT, PAUSE_TASK, RESUME_TASK, RESTART_TASK, STOP_TASK, MOVE_TASK, VERIFY_TASK,
            FIX_FINDINGS, RECHECK_TASK, IGNORE_FINDING, UPDATE_SETTINGS, PROCESS_MERGE_QUEUE, RESOLVE_CONFLICT, PROMOTE,
            ACKNOWLEDGE_DIGEST, ACCEPT_PHASE, ACCEPT_MILESTONE, SAVE_ROLE, UPDATE_SERVER, DELETE_PROJECT,
        )
}

/**
 * One command to the phone engine (Dart `TeamProjectCommand`): it names its project and the revision the person saw,
 * so the engine refuses it when the project moved on. [action] is the engine's word ([TeamAction]). The typed values
 * ([settings], [spec], [tasks], [phases], [role], [server]) are whole edited values, as Dart sends them.
 */
public data class TeamCommand(
    val action: String,
    val projectId: String,
    val expectedRevision: Long?,
    val targetId: String = "",
    val text: String = "",
    val confirmed: Boolean = false,
    val name: String = "",
    val serverId: String = "",
    val roleId: String = "",
    val expectedDevCommit: String = "",
    val expectedMainCommit: String = "",
    val settings: TeamProjectSettings? = null,
    val spec: TeamSpec? = null,
    val repos: List<TeamRepo> = emptyList(),
    val tasks: List<TeamTask>? = null,
    val phases: List<TeamProjectPhase>? = null,
    val findingIds: List<String> = emptyList(),
    val role: TeamRole? = null,
    val server: TeamServer? = null,
) {
    /** The `POST /v1/commands` body, every field Dart `TeamProjectCommand.toJson` writes, in its order. */
    public fun body(requestId: String): Map<String, Any?> =
        linkedMapOf(
            "findingIds" to findingIds,
            "requestId" to requestId,
            "action" to action,
            "projectId" to projectId,
            "expectedRevision" to expectedRevision,
            "targetId" to targetId,
            "text" to text,
            "name" to name,
            "serverId" to serverId,
            "roleId" to roleId,
            "expectedDevCommit" to expectedDevCommit,
            "expectedMainCommit" to expectedMainCommit,
            "confirmed" to confirmed,
            "settings" to settings?.let(TeamEncode::settings),
            "spec" to spec?.let(TeamEncode::spec),
            "repos" to repos.map(TeamEncode::repo),
            "tasks" to tasks?.map(TeamEncode::task),
            "phases" to phases?.map(TeamEncode::phase),
            "role" to role?.let(TeamEncode::role),
            "server" to server?.let(TeamEncode::server),
        )

    /** Dart `PhoneEngineGateway._executionActions`: these run work and need a verified, executing engine. */
    public val runsWork: Boolean
        get() = action in EXECUTION_ACTIONS

    public companion object {
        public val EXECUTION_ACTIONS: Set<String> =
            setOf(
                "approvePlan", "retryPlan", "resumeProject", "resumeTask", "restartTask", "verifyTask", "fixFindings",
                "recheckTask", "processMergeQueue", "resolveConflict", "promote", "undoMerge", "moveTask",
            )

        public fun answer(project: TeamProject, requestId: String, text: String): TeamCommand =
            TeamCommand(TeamAction.ANSWER_REQUEST, project.id, project.revision, targetId = requestId, text = text)

        public fun acceptMilestone(project: TeamProject, milestoneId: String): TeamCommand =
            TeamCommand(TeamAction.ACCEPT_MILESTONE, project.id, project.revision, targetId = milestoneId)

        public fun resumeTask(project: TeamProject, taskId: String): TeamCommand =
            TeamCommand(TeamAction.RESUME_TASK, project.id, project.revision, targetId = taskId)

        /** Dart `messageTask`: the person's note to the task's worker. */
        public fun message(project: TeamProject, taskId: String, text: String): TeamCommand =
            TeamCommand(TeamAction.MESSAGE_TASK, project.id, project.revision, targetId = taskId, text = text)

        /** A command on the project itself (pause, resume, stop, retry the plan, mark the digest read). */
        public fun project(project: TeamProject, action: String): TeamCommand = TeamCommand(action, project.id, project.revision)

        /** A command on one task of [project] (pause, restart, stop, verify, recheck, accept its phase). */
        public fun task(project: TeamProject, action: String, targetId: String, confirmed: Boolean = false): TeamCommand =
            TeamCommand(action, project.id, project.revision, targetId = targetId, confirmed = confirmed)

        /** Dart `deleteProject`: refused by the engine without the confirmation flag. */
        public fun delete(project: TeamProject): TeamCommand =
            TeamCommand(TeamAction.DELETE_PROJECT, project.id, project.revision, confirmed = true)

        /** Dart `moveTask`: [note] is the hand-off; [confirmed] restarts it elsewhere when the branch cannot move. */
        public fun move(project: TeamProject, taskId: String, serverId: String, note: String = "", confirmed: Boolean = false): TeamCommand =
            TeamCommand(TeamAction.MOVE_TASK, project.id, project.revision, targetId = taskId, text = note, serverId = serverId, confirmed = confirmed)

        public fun fixFindings(project: TeamProject, taskId: String, findingIds: List<String>): TeamCommand =
            TeamCommand(TeamAction.FIX_FINDINGS, project.id, project.revision, targetId = taskId, findingIds = findingIds)

        public fun ignoreFinding(project: TeamProject, taskId: String, findingId: String, reason: String): TeamCommand =
            TeamCommand(TeamAction.IGNORE_FINDING, project.id, project.revision, targetId = taskId, text = reason, findingIds = listOf(findingId))

        /** Dart `resolveConflict`: [how] is `agent`, `manual` or `recheck`. */
        public fun resolveConflict(project: TeamProject, mergeItemId: String, how: String): TeamCommand =
            TeamCommand(TeamAction.RESOLVE_CONFLICT, project.id, project.revision, targetId = mergeItemId, text = how)

        /** Dart `confirmAndMergeToDev`: every checked task of [repo] into dev. */
        public fun mergeToDev(project: TeamProject, repo: TeamRepo): TeamCommand =
            TeamCommand(TeamAction.PROCESS_MERGE_QUEUE, project.id, project.revision, targetId = repo.id, confirmed = true)

        /** Dart `confirmAndPromote`: dev to main, pinning both commits the person saw. */
        public fun promote(project: TeamProject, repo: TeamRepo): TeamCommand =
            TeamCommand(
                TeamAction.PROMOTE,
                project.id,
                project.revision,
                targetId = repo.id,
                confirmed = true,
                expectedDevCommit = repo.devCommit,
                expectedMainCommit = repo.mainCommit,
            )

        /** Dart `updateServer`: a server's lane limit (not offered in Flutter, kept for the servers page). */
        public fun updateServer(revision: Long, server: TeamServer): TeamCommand =
            TeamCommand(TeamAction.UPDATE_SERVER, "", revision, server = server)
    }
}

/** The engine's answer (Dart `TeamCommandResult`); [code] is a safe protocol word when refused. */
public data class TeamCommandResult(
    val accepted: Boolean,
    val code: String = "",
    val projectId: String = "",
    val revision: Long = 0,
    val replayed: Boolean = false,
)
