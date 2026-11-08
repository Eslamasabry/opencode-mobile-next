package io.github.eslamasabry.oc.feature.team.presentation

import io.github.eslamasabry.oc.engine.team.TeamProject
import io.github.eslamasabry.oc.engine.team.TeamTask
import io.github.eslamasabry.oc.engine.team.TeamWorkspace
import kotlin.time.Instant

/** A team part's state, in Flutter's order (Dart `KitTeamState`); the screen maps it to the kit's. */
public enum class TeamMark { Empty, Loading, Running, NeedsYou, Stalled, Failed, Done, Stale }

/** A board card's mark (Dart `KitTaskState` as the board picks it). */
public enum class TaskMark { Waiting, Working, Done, Failed, NeedsYou }

/** The words a status reads as (Dart `_word`, team_projects_screen overview_helpers.dart). */
public enum class StatusWord { Working, Failed, Stalled, Interrupted, Paused, Stopped, Done, Review, Planning, PlanWaiting, NeedsYou, Waiting }

/** What a project row's status line says (Dart `_headline`). */
public sealed interface Headline {
    /** Shaping the spec, [minutes] since the last planner checkpoint. */
    public data class Planning(val minutes: Int) : Headline

    public data class Status(val word: StatusWord) : Headline

    /** No milestones: tasks done, total and working (Dart `teamProgressWords`). */
    public data class Progress(val done: Int, val total: Int, val working: Int) : Headline

    public data class AllTasksDone(val total: Int) : Headline

    /** The first milestone not accepted yet, one-based. */
    public data class Milestone(val number: Int, val of: Int, val working: Int) : Headline

    public data class MilestonesDone(val total: Int) : Headline
}

/** Flutter's board columns, in order (Backlog, Ready, Working, Review, Done). */
public enum class BoardColumn { Backlog, Ready, Working, Review, Done }

/** The rules every team page shares (Dart overview_helpers.dart and team_status_words.dart). */
public object TeamRules {
    /** Minutes after which shaping the spec is called slow (Dart `teamPlanningSlowMinutes`). */
    public const val PLANNING_SLOW_MINUTES: Int = 2

    public fun epochMs(raw: String): Long? = if (raw.isBlank()) null else Instant.parseOrNull(raw.trim())?.toEpochMilliseconds()

    /** Dart `_urgency`: something to answer, then running, then the rest, then done. */
    public fun urgency(p: TeamProject): Int =
        when {
            p.openRequests.isNotEmpty() -> 0
            p.status == "running" -> 1
            p.status == "done" -> 3
            else -> 2
        }

    /** The projects in the order the team page lists them (a stable sort, as Dart's). */
    public fun ordered(workspace: TeamWorkspace?): List<TeamProject> = workspace?.projects.orEmpty().sortedBy(::urgency)

    /** Dart `_state`. */
    public fun mark(status: String): TeamMark =
        when (status) {
            "running" -> TeamMark.Running
            "failed", "planFailed", "conflict" -> TeamMark.Failed
            "stalled", "interrupted" -> TeamMark.Stalled
            "findings", "review" -> TeamMark.NeedsYou
            "offline" -> TeamMark.Stale
            "done", "merged", "passed" -> TeamMark.Done
            else -> TeamMark.Empty
        }

    /** Dart `_word`. */
    public fun word(status: String): StatusWord =
        when (status) {
            "running" -> StatusWord.Working
            "failed", "planFailed", "conflict" -> StatusWord.Failed
            "stalled" -> StatusWord.Stalled
            "interrupted" -> StatusWord.Interrupted
            "paused" -> StatusWord.Paused
            "stopped" -> StatusWord.Stopped
            "done", "merged", "passed" -> StatusWord.Done
            "review", "verified", "findings" -> StatusWord.Review
            "spec" -> StatusWord.Planning
            "plan" -> StatusWord.PlanWaiting
            "waiting" -> StatusWord.NeedsYou
            else -> StatusWord.Waiting
        }

    /** Dart `teamPlanningMinutes`. */
    public fun planningMinutes(p: TeamProject, nowMs: Long): Int {
        val at = epochMs(p.planningState?.updatedAt.orEmpty()) ?: epochMs(p.updatedAt) ?: return 0
        return ((nowMs - at) / 60_000).toInt().coerceAtLeast(0)
    }

    /** Dart `_headline`. */
    public fun headline(p: TeamProject, nowMs: Long): Headline {
        if (p.status == "spec") return Headline.Planning(planningMinutes(p, nowMs))
        if (p.status != "running") return Headline.Status(word(p.status))
        val milestones = p.specDraft.milestones
        val working = p.tasks.count { it.status == "running" }
        if (milestones.isEmpty()) {
            val done = p.tasks.count(TeamTask::finished)
            return if (p.tasks.isNotEmpty() && done == p.tasks.size && working == 0) Headline.AllTasksDone(p.tasks.size)
            else Headline.Progress(done, p.tasks.size, working)
        }
        val current = milestones.indexOfFirst { !it.accepted }
        return if (current < 0) Headline.MilestonesDone(milestones.size)
        else Headline.Milestone(current + 1, milestones.size, working)
    }

    /** Dart board `column`. */
    public fun column(t: TeamTask): BoardColumn =
        when (t.status) {
            "running" -> BoardColumn.Working
            "review", "verified", "waiting", "failed", "stalled" -> BoardColumn.Review
            "done", "merged" -> BoardColumn.Done
            else -> if (t.dependsOn.isEmpty()) BoardColumn.Ready else BoardColumn.Backlog
        }

    /** Dart board `mark`. */
    public fun taskMark(t: TeamTask): TaskMark =
        when (column(t)) {
            BoardColumn.Working -> TaskMark.Working
            BoardColumn.Review -> if (t.status == "failed") TaskMark.Failed else TaskMark.NeedsYou
            BoardColumn.Done -> TaskMark.Done
            else -> TaskMark.Waiting
        }

    /** Dart overview `laneTotal`. */
    public fun laneTotal(p: TeamProject): Int = if (p.settings.mode == "single") 1 else p.settings.maxLanes

    /**
     * Dart overview's lanes: the running tasks, then the queued ones whose dependencies are all finished and that
     * nothing is asking about, as many as there are free lanes.
     */
    public fun lanes(p: TeamProject): Pair<List<TeamTask>, List<TeamTask>> {
        val busy = p.tasks.filter { it.status == "running" }
        val asked = p.openRequests.map { it.taskId }.toSet()
        val free = (laneTotal(p) - busy.size).coerceAtLeast(0)
        val waiting =
            p.tasks
                .filter { t ->
                    t.status == "queued" && t.id !in asked &&
                        t.dependsOn.all { id -> p.tasks.any { it.id == id && it.finished } }
                }
                .take(free)
        return busy to waiting
    }

    /**
     * The tasks of milestone [milestoneId] through its phases; with null, the tasks of every phase (Dart's board and
     * milestone rows both go through the phases).
     */
    public fun tasksOf(p: TeamProject, milestoneId: String?): List<TeamTask> {
        val phases = p.phases.filter { milestoneId == null || it.milestoneId == milestoneId }.map { it.id }.toSet()
        return p.tasks.filter { it.phaseId in phases }
    }

    /** Dart `_money`: whole dollars without cents. */
    public fun money(v: Double): String {
        if (v == kotlin.math.round(v)) return "$" + v.toLong()
        val cents = kotlin.math.round(v * 100).toLong()
        return "$" + (cents / 100) + "." + (cents % 100).toString().padStart(2, '0')
    }
}
