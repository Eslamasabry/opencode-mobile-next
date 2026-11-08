package io.github.eslamasabry.oc.engine.team

/**
 * One command to the phone engine (Dart `TeamProjectCommand`): it names its project and the revision the person saw,
 * so the engine refuses it when the project moved on. [action] is the engine's word (`answerRequest`, `pauseProject`…).
 */
public data class TeamCommand(
    val action: String,
    val projectId: String,
    val expectedRevision: Long?,
    val targetId: String = "",
    val text: String = "",
    val confirmed: Boolean = false,
) {
    /** The `POST /v1/commands` body, every field Dart `TeamProjectCommand.toJson` writes. */
    public fun body(requestId: String): Map<String, Any?> =
        linkedMapOf(
            "findingIds" to emptyList<String>(),
            "requestId" to requestId,
            "action" to action,
            "projectId" to projectId,
            "expectedRevision" to expectedRevision,
            "targetId" to targetId,
            "text" to text,
            "name" to "",
            "serverId" to "",
            "roleId" to "",
            "expectedDevCommit" to "",
            "expectedMainCommit" to "",
            "confirmed" to confirmed,
            "settings" to null,
            "spec" to null,
            "repos" to emptyList<Any?>(),
            "tasks" to null,
            "phases" to null,
            "role" to null,
            "server" to null,
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
            TeamCommand("answerRequest", project.id, project.revision, targetId = requestId, text = text)

        public fun acceptMilestone(project: TeamProject, milestoneId: String): TeamCommand =
            TeamCommand("acceptMilestone", project.id, project.revision, targetId = milestoneId)

        public fun resumeTask(project: TeamProject, taskId: String): TeamCommand =
            TeamCommand("resumeTask", project.id, project.revision, targetId = taskId)

        public fun project(project: TeamProject, action: String): TeamCommand =
            TeamCommand(action, project.id, project.revision)
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
