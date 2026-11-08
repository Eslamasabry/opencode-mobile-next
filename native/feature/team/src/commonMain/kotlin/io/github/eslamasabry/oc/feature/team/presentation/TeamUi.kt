package io.github.eslamasabry.oc.feature.team.presentation

import io.github.eslamasabry.oc.engine.team.TeamPhase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

// ---- the team page (Dart TeamPage + TeamProjectsScreen)

/** One line of the team page's list, in Flutter's order: each project's open requests, then the project. */
public sealed interface HomeRow {
    public val key: String

    /** A request the team is waiting on (Dart `KitRow(title: request.title, supporting: p.name)`). */
    public data class Request(val projectId: String, val requestId: String, val title: String, val projectName: String) :
        HomeRow {
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
)

public interface TeamHomeActions {
    public fun back()

    public fun openProject(id: String)

    /** A request row: its project, where the request is shown with its answer. */
    public fun openRequest(projectId: String, requestId: String)

    public fun retry()
}

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
)

public data class TeamProjectUi(
    val phase: TeamPhase = TeamPhase.Loading,
    /** Null once read when the project is gone (deleted elsewhere). */
    val project: ProjectView? = null,
    val stale: Boolean = false,
    val nowMs: Long = 0,
)

public interface TeamProjectActions {
    public fun back()

    /** The board, filtered to [milestone] when given. */
    public fun openBoard(milestone: String? = null)

    public fun retry()
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
}
