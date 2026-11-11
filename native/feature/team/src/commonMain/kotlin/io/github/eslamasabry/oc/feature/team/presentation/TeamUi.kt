package io.github.eslamasabry.oc.feature.team.presentation

import io.github.eslamasabry.oc.engine.team.TeamPhase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

// ---- the team page (Dart TeamPage + TeamProjectsScreen)

/** One line of the team page's list, in Flutter's order: each project's open requests, then the project. */
public sealed interface HomeRow {
    public val key: String

    /** Dart `teamProjectInterruptedRow`: the work was interrupted; a tap resumes the project. */
    public data class Interrupted(val projectId: String, val name: String) : HomeRow {
        override val key: String
            get() = "interrupted:$projectId"
    }

    /** A request the team is waiting on (Dart `KitRow(title: request.title, supporting: p.name)`); a tap answers it. */
    public data class Request(
        val projectId: String,
        val requestId: String,
        val title: String,
        val projectName: String,
        val answer: AskAnswer = AskAnswer.None,
    ) : HomeRow {
        override val key: String
            get() = "request:$projectId:$requestId"
    }

    /** The project itself (Dart `KitProjectRow`). */
    public data class Project(
        val id: String,
        val name: String,
        val headline: Headline,
        val mark: TeamMark,
        val budgetWarning: Boolean,
        /** Today's spend and the daily limit (null without one); null when the engine reports no usage. */
        val spentToday: Double?,
        val dailyLimit: Double?,
        val updatedAtMs: Long?,
    ) : HomeRow {
        override val key: String
            get() = "project:$id"
    }
}

public data class TeamHomeUi(
    val phase: TeamPhase = TeamPhase.Loading,
    val rows: ImmutableList<HomeRow> = persistentListOf(),
    /** The last read failed: the rows are last known (Dart `errorCode == 'unavailable'`). */
    val stale: Boolean = false,
    /** The engine's workspace is the simulated demo. */
    val demo: Boolean = false,
    val loading: Boolean = false,
    val nowMs: Long = 0,
    /** Dart `TeamExecutionBlocked`: the engine answers but cannot run work; New project waits. */
    val blocked: Boolean = false,
    /** Dart `TeamProtectionLine`: what fences the team's files (`proot`, `landlock`); null when unknown. */
    val protection: String? = null,
    /** Dart `TeamResumeUnavailable`: an interrupted project, and the engine runs work but offers no Resume. */
    val resumeUnavailable: Boolean = false,
    /** Dart's bottom block (New project, Quick task): only while the engine can run work. */
    val canStart: Boolean = false,
    /** The last command was refused: its code and action, for the words. */
    val refusal: RefusalUi? = null,
    /** A milestone being accepted asks first. */
    val accepting: AskAnswer.AcceptMilestone? = null,
    /** [TeamPhase.Off]: how to turn the team on (Flutter team_intro_screen.dart). */
    val intro: TeamIntroUi = TeamIntroUi(),
    /** [TeamPhase.PhoneOff]: whether a check is already going (Flutter `PhoneTeamOffPage`). */
    val phoneOff: PhoneOffUi = PhoneOffUi(),
    /** [TeamPhase.Computer]: the team on a computer (Flutter `TeamHomeScreen` over Gas City). */
    val computer: FleetHomeUi = FleetHomeUi(),
)

public interface TeamHomeActions : TeamIntroActions {
    public fun back()

    public fun openProject(id: String)

    /** A request row: its project, where the request is shown with its answer. */
    public fun openRequest(projectId: String, requestId: String)

    /** The interrupted row: resume the project (Dart `resumeProject`). */
    public fun resumeProject(projectId: String)

    public fun retry()

    /** The top bar's Team settings (Dart `openTeamSettings`). */
    public fun openSettings() {}

    /** The phone-off page's action: the four-step set-up page (Dart `openPhoneTeamSetup`), which joins a check already going. */
    public fun openSetup() {}

    /** The top bar's Team defaults and Roles, and the bottom's New project and Quick task (Dart `openTeam*`). */
    public fun openEditor(kind: EditorKind, projectId: String = "") {}

    /** A question or permission answered from the list (Dart `_answer`'s input dialog). */
    public fun answer(projectId: String, requestId: String, text: String) {}

    public fun askAccept(projectId: String, answer: AskAnswer.AcceptMilestone) {}

    public fun confirmAccept() {}

    public fun cancelAccept() {}

    public fun resumeTask(projectId: String, taskId: String) {}

    public fun openTask(projectId: String, taskId: String) {}

    public fun dismissRefusal() {}

    /** A computer's team: read it again now, or look for it again after it was not found (Dart `_refresh`). */
    public fun refreshComputer() {}

    /** A computer's team: the finished tasks past the first three (Dart `team-home-completed-more`). */
    public fun showMoreDone() {}

    /** A computer's team: the agents row opens the Agents page. */
    public fun openFleetAgents() {}

    /** A computer's team: a task's row opens the task's page. */
    public fun openFleetRun(runId: String) {}
}

/**
 * What answering one of the team's requests does (Dart `_answer`, overview_helpers.dart): a typed answer, an editor,
 * a milestone's acceptance (asks first), resuming the task, or opening its conversation.
 */
public sealed interface AskAnswer {
    public data object Text : AskAnswer

    public data class Editor(val kind: EditorKind) : AskAnswer

    public data class AcceptMilestone(val projectId: String, val milestoneId: String, val text: String) : AskAnswer

    public data class Resume(val taskId: String) : AskAnswer

    public data class Task(val taskId: String) : AskAnswer

    public data object None : AskAnswer
}

/** The button's word for a request (Dart `_requestLabel`). */
public enum class AskLabel { Spec, Plan, Settings, Accept, Resume, Answer, Review }

// ---- one project (Dart TeamProjectOverview)

/** Something the team asks, as the project's needs-you card shows it (Dart `_needsYou`). */
public data class NeedsYouUi(
    val id: String,
    val title: String,
    val kind: String,
    /** The asking task's role and server, when the task is known. */
    val roleName: String?,
    val serverName: String?,
    /** How many tasks wait on the asking task. */
    val blocks: Int,
    val taskId: String = "",
    /** For a milestone ask: the milestone, its title and criteria (the confirm's body). */
    val milestoneId: String = "",
    val milestoneText: String = "",
    /** What the card's button does (Dart `_answer`). */
    val answer: AskAnswer = AskAnswer.None,
    val label: AskLabel = AskLabel.Review,
    /** False for an interrupted ask while the engine cannot resume (Dart hides the button). */
    val actionable: Boolean = true,
)

/** What a milestone row's status says (Dart overview). */
public sealed interface MilestoneStatus {
    public data object Done : MilestoneStatus

    public data object NoTasks : MilestoneStatus

    public data class Tasks(val done: Int, val total: Int) : MilestoneStatus

    /** Not started: waits on the [waitsOn] milestones before it. */
    public data class Waits(val waitsOn: Int) : MilestoneStatus
}

public data class MilestoneUi(
    val id: String,
    val number: Int,
    val title: String,
    val mark: TeamMark,
    val status: MilestoneStatus,
    /** The progress bar, while started and not accepted. */
    val completed: Int?,
    val total: Int?,
    /** Every task merged and every phase accepted: Dart offers "Accept milestone" under the row. */
    val canAccept: Boolean = false,
)

/** A lane row: a running task or one ready for a free lane. */
public data class LaneUi(
    val taskId: String,
    val roleName: String?,
    val title: String,
    val running: Boolean,
    val serverName: String,
    val serverOffline: Boolean,
    val changedAtMs: Long?,
)

/** The project's goal card status (Dart `_goalStatus`). */
public data class GoalUi(
    val goal: String,
    /** When the latest spec version was approved; null for a draft. */
    val approvedAtMs: Long?,
    val milestones: Int,
    val repos: Int,
)

public data class ProjectView(
    val id: String,
    val name: String,
    val headline: Headline,
    val needsYou: ImmutableList<NeedsYouUi>,
    val goal: GoalUi,
    val milestones: ImmutableList<MilestoneUi>,
    val lanes: ImmutableList<LaneUi>,
    val busy: Int,
    val laneTotal: Int,
    val singleLane: Boolean,
    val demo: Boolean,
    val taskCount: Int,
    /** The project menu (Dart overview `menu`). */
    val menu: ProjectMenu = ProjectMenu(),
    val status: String = "",
    /** The goal card's Plan (Dart: while the plan waits or is planned). */
    val showPlan: Boolean = false,
    val planFailed: PlanFailedUi? = null,
    val digest: DigestUi? = null,
    val merges: ImmutableList<MergeQueueUi> = persistentListOf(),
    val receipts: ImmutableList<ReceiptUi> = persistentListOf(),
    val promotions: ImmutableList<PromoteUi> = persistentListOf(),
    val decisions: ImmutableList<DecisionUi> = persistentListOf(),
    /** Milestones that hold tasks (the board row's "across n"). */
    val boardMilestones: Int = 1,
    val timelineCount: Int = 0,
    val timelineLastAtMs: Long? = null,
    val serverCount: Int = 0,
    val maxLanes: Int = 1,
    val cost: CostUi = CostUi(),
    val budgetWarning: Boolean = false,
    /** Dart: Delete project, for a real engine that can remove projects. */
    val canDelete: Boolean = false,
    /** Dart `TeamExecutionBlocked` on the project page. */
    val blocked: Boolean = false,
    val resumeUnavailable: Boolean = false,
)

/** Which project menu items show (Dart overview `menu`). */
public data class ProjectMenu(
    /** Shaping the spec takes minutes: ask the planner again (Dart `retryPlan`). */
    val askAgain: Boolean = false,
    val resumeInterrupted: Boolean = false,
    val pause: Boolean = false,
    val resume: Boolean = false,
)

/**
 * A plan that failed (Dart `_planFailed`): why in plain words ([code], worded by the page), and the way on: Resume
 * when the engine can pick it up, Choose a model, Ask again, or approve the spec again.
 */
public data class PlanFailedUi(val code: String, val canRetry: Boolean, val resumable: Boolean, val needsModel: Boolean)

/** "Since you were away" (Dart `KitDigest`): what is still true, newest first. */
public data class DigestUi(
    val items: ImmutableList<DigestItem>,
    val demo: Boolean,
    val needsModel: Boolean,
    val resume: Boolean,
)

public data class DigestItem(val text: String, val atMs: Long?)

/** One repo's merge queue (Dart `KitMergeQueue` on the overview). */
public data class MergeQueueUi(
    val repoId: String,
    val repoName: String,
    val items: ImmutableList<MergeItemUi>,
    /** Dart: Merge next, while the engine can merge. */
    val canMerge: Boolean,
    /** Queued items (the confirm's "n checked tasks"). */
    val waiting: Int,
)

/** [word] is the status word when [reason] is empty; [checking] says the checks still run. */
public data class MergeItemUi(
    val taskId: String,
    val title: String,
    val reason: String,
    val status: String,
    val checksPassed: Boolean,
    val mark: TeamMark,
)

/** A merge into dev or a promotion to main (Dart `teamReceiptItems`). */
public data class ReceiptUi(val repoName: String, val merge: Boolean, val before: String, val after: String, val atMs: Long?)

/** Dart `KitPromoteCard` on the overview: dev is ahead of main and everything passed. */
public data class PromoteUi(val repoId: String, val repoName: String, val mainCommit: String, val devCommit: String)

/** A decision the team made (Dart overview Decisions): [who] is the person, a role's name, or nobody. */
public data class DecisionUi(val text: String, val byPerson: Boolean, val roleName: String?, val atMs: Long?)

/** Dart `_costLine`. */
public data class CostUi(
    val reported: Boolean = false,
    val spentToday: Double = 0.0,
    val daily: Double? = null,
    val spent: Double = 0.0,
    val total: Double? = null,
)

public data class TeamProjectUi(
    val phase: TeamPhase = TeamPhase.Loading,
    /** Null once read when the project is gone (deleted elsewhere). */
    val project: ProjectView? = null,
    val stale: Boolean = false,
    val nowMs: Long = 0,
    /** A command is on its way: every action waits. */
    val busy: Boolean = false,
    /** The last command was refused (Dart `errorCode` → `teamProjectError`). */
    val refused: Boolean = false,
    /** Stop asks first (Dart `showKitConfirm` kind stop). */
    val confirmStop: Boolean = false,
    val refusal: RefusalUi? = null,
    /** The confirm open now: delete, merge into dev, promote. */
    val confirming: ProjectConfirm? = null,
)

/** The project page's questions (Dart `_confirmDelete`, `confirmAndMergeToDev`, `confirmAndPromote`). */
public sealed interface ProjectConfirm {
    public data object Delete : ProjectConfirm

    public data class Merge(val repoId: String) : ProjectConfirm

    public data class Promote(val repoId: String) : ProjectConfirm
}

public interface TeamProjectActions {
    public fun back()

    /** The board, filtered to [milestone] when given. */
    public fun openBoard(milestone: String? = null)

    public fun retry()

    public fun answer(requestId: String, text: String) {}

    public fun acceptMilestone(milestoneId: String) {}

    public fun resumeTask(taskId: String) {}

    /** `pauseProject`, `resumeProject` (paused or interrupted). */
    public fun pause() {}

    public fun resume() {}

    public fun askStop() {}

    public fun confirmStop() {}

    public fun cancelStop() {}

    public fun dismissRefusal() {}

    /** A lane row: the task's conversation. */
    public fun openTask(taskId: String) {}

    public fun openEditor(kind: EditorKind) {}

    /** The menu's Ask again, a failed plan's Ask again (Dart `retryPlan`). */
    public fun retryPlan() {}

    /** The digest's Mark as read (Dart `acknowledgeDigest`). */
    public fun markDigestRead() {}

    public fun openTimeline() {}

    public fun openServers() {}

    public fun ask(confirm: ProjectConfirm) {}

    public fun confirm() {}

    public fun cancelConfirm() {}
}

// ---- the board (Dart TeamProjectBoard)

public data class BoardCardUi(
    val id: String,
    val title: String,
    val mark: TaskMark,
    val column: BoardColumn,
    val status: String,
    val dependsOn: ImmutableList<String>,
)

/** A filter choice: [id] null is "Everything". */
public data class FilterChoice(val id: String?, val title: String)

public data class TeamBoardUi(
    val phase: TeamPhase = TeamPhase.Loading,
    val projectName: String? = null,
    /** False once read when the project is gone. */
    val found: Boolean = true,
    val graph: Boolean = false,
    val milestones: ImmutableList<FilterChoice> = persistentListOf(),
    val repos: ImmutableList<FilterChoice> = persistentListOf(),
    val servers: ImmutableList<FilterChoice> = persistentListOf(),
    val milestone: String? = null,
    val repo: String? = null,
    val server: String? = null,
    /** The filtered cards. */
    val cards: ImmutableList<BoardCardUi> = persistentListOf(),
    /** The lane shown: the person's choice, else the first with cards (Dart "opens where the work is"). */
    val column: BoardColumn = BoardColumn.Backlog,
)

public interface TeamBoardActions {
    public fun back()

    public fun showGraph(graph: Boolean)

    public fun filterMilestone(id: String?)

    public fun filterRepo(id: String?)

    public fun filterServer(id: String?)

    public fun selectColumn(column: BoardColumn)

    /** A card or graph node: the task's conversation. */
    public fun openTask(taskId: String) {}
}

// ---- one task (Dart TeamProjectConversation)

/** Who wrote a conversation line: the person, the team's instructions, or the worker. */
public enum class TaskSpeaker { Person, Instructions, Worker }

public data class TaskLineUi(val id: String, val speaker: TaskSpeaker, val text: String, val atMs: Long?)

public data class TaskAskUi(
    val id: String,
    val title: String,
    val kind: String,
    val createdAtMs: Long?,
    /** What the ask's card offers (Dart `_requestActions`); empty when nothing can be done here. */
    val actions: List<TaskAskAction> = emptyList(),
)

/** Dart `_requestActions`: answer, resume, start again, review the findings, or resolve a merge conflict. */
public sealed interface TaskAskAction {
    public data object Answer : TaskAskAction

    public data object Resume : TaskAskAction

    public data object Restart : TaskAskAction

    public data object ReviewFindings : TaskAskAction

    /** [how] is `agent`, `manual` or `recheck` (Dart `resolveConflict`'s text). */
    public data class Resolve(val mergeItemId: String, val how: String) : TaskAskAction
}

/** The phase card (Dart `KitPhaseCard`): accept a phase once every task of it merged. */
public data class PhaseUi(
    val id: String,
    val title: String,
    val accepted: Boolean,
    val failed: Boolean,
    val asking: Boolean,
    val taskDone: Boolean,
    val canAccept: Boolean,
)

/** One task line of the plan card (Dart `KitPlanTask`). */
public data class PlanTaskUi(
    val taskId: String,
    val number: Int,
    val title: String,
    val repoName: String?,
    val after: Int?,
    val criteria: Int,
    val roleName: String?,
    val serverName: String?,
    val serverId: String,
    val canMove: Boolean,
)

public data class PlanPhaseUi(val number: Int, val title: String, val risky: Boolean, val tasks: List<PlanTaskUi>)

/** The plan card while the plan waits (Dart `_planCard`). [milestone] is one-based; null for the project's plan. */
public data class PlanUi(
    val milestone: Int?,
    val phases: List<PlanPhaseUi>,
    val phaseCount: Int,
    val taskCount: Int,
    val repoCount: Int,
    val more: Int,
    val serverFixed: Boolean,
    val canApprove: Boolean,
)

/** The task's merge queue (Dart `KitMergeQueue` on the task page). */
public data class TaskMergeUi(val repoId: String, val status: String, val items: List<Pair<String, String>>, val canMerge: Boolean)

/** The task page's controls (Dart's action block), each shown only when it applies and the engine can run it. */
public data class TaskControls(
    val diff: Boolean = false,
    val pause: Boolean = false,
    val resume: Boolean = false,
    val restart: Boolean = false,
    val verify: Boolean = false,
    val stop: Boolean = false,
    /** The task failed: Report this failure is offered. */
    val report: Boolean = false,
)

/** The promote card on a task (Dart `_promoteCard`). */
public data class TaskPromoteUi(
    val repoId: String,
    val repoName: String,
    val milestone: Int?,
    val milestoneTitle: String,
    val projectName: String,
    val merged: Int,
    val tasks: Int,
    val days: Int?,
    val reviewed: Boolean,
    val commits: Int,
    val files: Int,
    val mainCommit: String,
    val devCommit: String,
    val canPromote: Boolean,
)

public data class CriterionUi(val criterion: String, val status: String)

public data class FindingUi(val id: String, val severity: String, val text: String, val location: String, val open: Boolean)

public data class TaskView(
    val id: String,
    val title: String,
    val roleName: String?,
    val serverName: String?,
    val status: String,
    val queued: Boolean,
    val lines: ImmutableList<TaskLineUi>,
    val asks: ImmutableList<TaskAskUi>,
    val criteria: ImmutableList<CriterionUi>,
    val findings: ImmutableList<FindingUi>,
    val running: Boolean = false,
    val plan: PlanUi? = null,
    val phase: PhaseUi? = null,
    /** The task's own timeline rows (Dart's work fold), oldest first; the last three while it runs. */
    val events: ImmutableList<DigestItem> = persistentListOf(),
    /** Done or merged: the fold says "Work", else "Work log". */
    val finished: Boolean = false,
    /** Dart `_findingsTitle`: the checker's name and the open counts by severity. */
    val checkerName: String? = null,
    val critical: Int = 0,
    val major: Int = 0,
    val minor: Int = 0,
    /** Dart: Fix selected and Check again need the engine to verify. */
    val canVerify: Boolean = false,
    val merge: TaskMergeUi? = null,
    val mergeReceipts: ImmutableList<ReceiptUi> = persistentListOf(),
    val promote: TaskPromoteUi? = null,
    val promoteReceipts: ImmutableList<ReceiptUi> = persistentListOf(),
    val controls: TaskControls = TaskControls(),
    val diff: String = "",
    val branch: String = "",
    val servers: ImmutableList<Pair<String, String>> = persistentListOf(),
)

public data class TeamTaskUi(
    val phase: TeamPhase = TeamPhase.Loading,
    /** Null once read when the task is gone (Dart `teamProjectTaskMissing`). */
    val task: TaskView? = null,
    val stale: Boolean = false,
    val busy: Boolean = false,
    val refused: Boolean = false,
    val nowMs: Long = 0,
    /** The findings the person ticked (Dart `_selected`). */
    val selected: ImmutableSet<String> = persistentSetOf(),
    val refusal: RefusalUi? = null,
    val confirming: TaskConfirm? = null,
)

/** The task page's questions: stop the task, merge into dev, promote. */
public sealed interface TaskConfirm {
    public data object Stop : TaskConfirm

    public data object Merge : TaskConfirm

    public data object Promote : TaskConfirm
}

public interface TeamTaskActions {
    public fun back()

    public fun answer(requestId: String, text: String)

    public fun resume()

    /** Dart `messageTask`. */
    public fun message(text: String)

    public fun retry()

    public fun dismissRefusal()

    public fun restart() {}

    /** Report this failure: opens Report a problem with the failed task attached. */
    public fun reportFailure() {}

    public fun pause() {}

    public fun verify() {}

    public fun recheck() {}

    public fun acceptPhase(phaseId: String) {}

    public fun resolve(mergeItemId: String, how: String) {}

    public fun toggleFinding(id: String, on: Boolean) {}

    /** Review findings: tick every open one. */
    public fun selectOpenFindings() {}

    public fun fixSelected() {}

    /** Ignore the one ticked finding, with the person's reason. */
    public fun ignoreSelected(reason: String) {}

    public fun approvePlan() {}

    public fun editPlan() {}

    /** The plan card's who: run [taskId] on [serverId] (Dart `_chooseServer`). */
    public fun move(taskId: String, serverId: String) {}

    public fun ask(confirm: TaskConfirm) {}

    public fun confirm() {}

    public fun cancelConfirm() {}
}
