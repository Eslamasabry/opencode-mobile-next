package io.github.eslamasabry.oc.engine.team

/*
 * The phone engine's schema-1 workspace (Dart lib/domain/team_project.dart). Field names and defaults are Dart's, so a
 * project Flutter's team started reads the same here. Only what native shows or sends is kept; the engine stays the
 * truth and nothing here is written back.
 */

/** A server a task can run on (Dart `TeamServer`). */
public data class TeamServer(
    val id: String = "",
    val name: String = "",
    val phone: Boolean = false,
    val online: Boolean = true,
    val laneCap: Int = 8,
    val chatWaiting: Boolean = false,
    val reason: String = "",
    /** Measured memory of the host, in MB; null when not measured. */
    val memoryMb: Double? = null,
)

/** A role a task is done by (Dart `TeamProjectRole`). */
public data class TeamRole(
    val id: String = "",
    val name: String = "",
    val model: String = "",
    val fallbackModel: String = "",
    val readOnly: Boolean = false,
    val instructions: String = "",
)

/** Dart `TeamBudget`. */
public data class TeamBudget(
    val chosen: Boolean = false,
    val unlimited: Boolean = false,
    val daily: Double? = null,
    val total: Double? = null,
    val taskTokens: Long? = null,
)

/** Dart `TeamProjectSettings`. */
public data class TeamProjectSettings(
    val keepWorkingScreenOff: Boolean = false,
    val mode: String = "",
    val maxLanes: Int = 1,
    val reviewLevel: String = "milestones",
    val chargingOnly: Boolean = false,
    val autoFix: Boolean = true,
    val maxFixRounds: Int = 2,
    val budget: TeamBudget = TeamBudget(),
)

/** Dart `TeamRepo`. */
public data class TeamRepo(
    val id: String = "",
    val name: String = "",
    val serverId: String = "",
    val path: String = "",
    val devCommit: String = "fixture-base",
    val mainCommit: String = "fixture-base",
    val checkCommand: String = "flutter test",
    val sharedRemote: Boolean = false,
)

/** Dart `TeamMilestone`. */
public data class TeamMilestone(
    val id: String = "",
    val title: String = "",
    val criteria: List<String> = emptyList(),
    val accepted: Boolean = false,
)

/** Dart `TeamSpec`. */
public data class TeamSpec(
    val version: Int = 1,
    val goal: String = "",
    val milestones: List<TeamMilestone> = emptyList(),
    val approvedAt: String = "",
    val contextFiles: List<String> = emptyList(),
    val constraints: String = "",
    val decisions: String = "",
    val outOfScope: String = "",
    val approvedBy: String = "",
)

/** Dart `TeamPhase` (a step of a milestone). */
public data class TeamProjectPhase(
    val id: String = "",
    val milestoneId: String = "",
    val title: String = "",
    val risky: Boolean = false,
    val accepted: Boolean = false,
)

/** One line of a task's conversation (Dart `TeamMessage`); [actor] is `person`, `team` (instructions) or a role. */
public data class TeamMessage(val id: String = "", val actor: String = "person", val text: String = "", val at: String = "")

/** A review finding on a task (Dart `TeamFinding`). */
public data class TeamFinding(
    val id: String = "",
    val severity: String = "major",
    val criterion: String = "",
    val location: String = "",
    val text: String = "",
    val status: String = "open",
)

/** How a task met one acceptance criterion (Dart `TeamCriterionResult`): met, unmet or not applicable. */
public data class TeamCriterionResult(val criterion: String = "", val status: String = "unmet")

/** Dart `TeamTask`. [status] is the engine's word: queued, running, review, verified, waiting, failed, … */
public data class TeamTask(
    val id: String = "",
    val title: String = "",
    val phaseId: String = "",
    val roleId: String = "frontend",
    val repoId: String = "",
    val serverId: String = "",
    val status: String = "queued",
    val dependsOn: List<String> = emptyList(),
    val criteria: List<String> = emptyList(),
    val branch: String = "",
    val reason: String = "",
    val changedAt: String = "",
    val steps: Int = 0,
    val tokens: Long = 0,
    val fixRounds: Int = 0,
    val messages: List<TeamMessage> = emptyList(),
    val findings: List<TeamFinding> = emptyList(),
    val criterionResults: List<TeamCriterionResult> = emptyList(),
    val affected: Boolean = false,
    /** The task's patch (unified diff), empty when none. */
    val diff: String = "",
) {
    /** Dart `finished`: merged or done. */
    public val finished: Boolean
        get() = status == "merged" || status == "done"
}

/** Something the team asks the person (Dart `TeamRequest`). */
public data class TeamRequest(
    val id: String = "",
    val kind: String = "question",
    val title: String = "",
    val taskId: String = "",
    val phaseId: String = "",
    val createdAt: String = "",
    val answered: Boolean = false,
    val answer: String = "",
)

/** Dart `TeamMergeItem`. */
public data class TeamMergeItem(
    val id: String = "",
    val taskId: String = "",
    val repoId: String = "",
    val status: String = "queued",
    val reason: String = "",
    val checksPassed: Boolean = false,
)

/** A merge into dev or a promotion to main (Dart `TeamProjectReceipt`). */
public data class TeamReceipt(
    val id: String = "",
    val kind: String = "",
    val repoId: String = "",
    val before: String = "",
    val after: String = "",
    val at: String = "",
    val actor: String = "person",
)

/** Dart `TeamTimelineEvent`. */
public data class TeamTimelineEvent(
    val id: String = "",
    val kind: String = "",
    val text: String = "",
    val actor: String = "fixture",
    val at: String = "",
    val taskId: String = "",
)

/** Dart `TeamPlanningState`. */
public data class TeamPlanningState(
    val jobId: String = "",
    val stage: String = "",
    val reason: String = "",
    val updatedAt: String = "",
)

/** One AI Team project (Dart `TeamProject`). */
public data class TeamProject(
    val id: String = "",
    val name: String = "",
    val status: String = "spec",
    val revision: Long = 0,
    val settings: TeamProjectSettings = TeamProjectSettings(),
    val repos: List<TeamRepo> = emptyList(),
    val specDraft: TeamSpec = TeamSpec(),
    val specVersions: List<TeamSpec> = emptyList(),
    val phases: List<TeamProjectPhase> = emptyList(),
    val tasks: List<TeamTask> = emptyList(),
    val requests: List<TeamRequest> = emptyList(),
    val mergeQueue: List<TeamMergeItem> = emptyList(),
    val receipts: List<TeamReceipt> = emptyList(),
    val timeline: List<TeamTimelineEvent> = emptyList(),
    val timelineTruncated: Boolean = false,
    val spendDay: String = "",
    val planningState: TeamPlanningState? = null,
    val spent: Double = 0.0,
    val spentToday: Double = 0.0,
    val digestReadAt: String = "",
    val updatedAt: String = "",
    val planApproved: Boolean = false,
    val quickTask: Boolean = false,
    val usageReported: Boolean = false,
    val budgetWarning: Boolean = false,
    val simulated: Boolean = true,
) {
    /** Requests not answered yet, in the engine's order. */
    public val openRequests: List<TeamRequest>
        get() = requests.filter { !it.answered }
}

/** The whole team (Dart `TeamWorkspace`). */
public data class TeamWorkspace(
    val schemaVersion: Int = 1,
    val revision: Long = 0,
    val projects: List<TeamProject> = emptyList(),
    val servers: List<TeamServer> = emptyList(),
    val roles: List<TeamRole> = emptyList(),
    val simulated: Boolean = true,
    /** The team's defaults for new projects (Dart `defaultSettings`); null when the engine has none. */
    val defaultSettings: TeamProjectSettings? = null,
) {
    public fun project(id: String): TeamProject? = projects.firstOrNull { it.id == id }
}
