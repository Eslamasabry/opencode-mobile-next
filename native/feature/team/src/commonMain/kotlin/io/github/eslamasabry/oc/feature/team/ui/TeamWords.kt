package io.github.eslamasabry.oc.feature.team.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.feature.team.presentation.BoardColumn
import io.github.eslamasabry.oc.feature.team.presentation.Headline
import io.github.eslamasabry.oc.feature.team.presentation.MilestoneStatus
import io.github.eslamasabry.oc.feature.team.presentation.StatusWord
import io.github.eslamasabry.oc.feature.team.presentation.TaskMark
import io.github.eslamasabry.oc.feature.team.presentation.TeamMark
import io.github.eslamasabry.oc.feature.team.presentation.TeamRules
import io.github.eslamasabry.oc.ui.kit.team.KitTaskState
import io.github.eslamasabry.oc.ui.kit.team.KitTeamState
import io.github.eslamasabry.oc.ui.kit.text.kitRelativeAge
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Dart `_word` (overview_helpers.dart). */
@Composable
internal fun statusWord(word: StatusWord): String =
    stringResource(
        when (word) {
            StatusWord.Working -> Res.string.teamProjectWorking
            StatusWord.Failed -> Res.string.teamProjectFailed
            StatusWord.Stalled -> Res.string.teamProjectStalled
            StatusWord.Interrupted -> Res.string.teamProjectInterrupted
            StatusWord.Paused -> Res.string.teamProjectPaused
            StatusWord.Stopped -> Res.string.teamProjectStopped
            StatusWord.Done -> Res.string.teamProjectDone
            StatusWord.Review -> Res.string.teamProjectReview
            StatusWord.Planning -> Res.string.teamProjectPlanning
            StatusWord.PlanWaiting -> Res.string.teamProjectPlanWaiting
            StatusWord.NeedsYou -> Res.string.teamProjectNeedsYou
            StatusWord.Waiting -> Res.string.teamProjectWaiting
        }
    )

/** Dart `_headline`, `teamProgressWords` and `teamPlanningWords`. */
@Composable
internal fun headlineWords(headline: Headline): String =
    when (headline) {
        is Headline.Planning ->
            when {
                headline.minutes < 1 -> stringResource(Res.string.teamProjectPlanning)
                headline.minutes >= TeamRules.PLANNING_SLOW_MINUTES ->
                    stringResource(Res.string.teamProjectPlanningSlow, headline.minutes)
                else -> stringResource(Res.string.teamProjectPlanningElapsed, headline.minutes)
            }
        is Headline.Status -> statusWord(headline.word)
        is Headline.Progress ->
            if (headline.working == 0) stringResource(Res.string.teamProjectProgress__eq0, headline.done, headline.total)
            else
                pluralStringResource(
                    Res.plurals.teamProjectProgress,
                    headline.working,
                    headline.done,
                    headline.total,
                    headline.working,
                )
        is Headline.AllTasksDone -> stringResource(Res.string.teamProjectAllTasksDone, headline.total)
        is Headline.Milestone ->
            if (headline.working == 0) stringResource(Res.string.teamProjectHeadlineMilestone__eq0, headline.number, headline.of)
            else
                pluralStringResource(
                    Res.plurals.teamProjectHeadlineMilestone,
                    headline.working,
                    headline.number,
                    headline.of,
                    headline.working,
                )
        is Headline.MilestonesDone -> stringResource(Res.string.teamProjectHeadlineDone, headline.total)
    }

/** Dart `_todayCost`. */
@Composable
internal fun todayCost(spent: Double, daily: Double?): String =
    if (daily == null) stringResource(Res.string.teamProjectCostToday, TeamRules.money(spent))
    else stringResource(Res.string.teamProjectCostTodayOf, TeamRules.money(spent), TeamRules.money(daily))

@Composable
internal fun milestoneWords(status: MilestoneStatus): String =
    when (status) {
        MilestoneStatus.Done -> stringResource(Res.string.teamProjectDone)
        MilestoneStatus.NoTasks -> stringResource(Res.string.teamProjectMilestoneNoTasks)
        is MilestoneStatus.Tasks -> stringResource(Res.string.teamProjectMilestoneTasks, status.done, status.total)
        is MilestoneStatus.Waits -> stringResource(Res.string.teamProjectMilestoneWaits, status.waitsOn)
    }

@Composable
internal fun columnWord(column: BoardColumn): String =
    stringResource(
        when (column) {
            BoardColumn.Backlog -> Res.string.teamProjectBacklog
            BoardColumn.Ready -> Res.string.teamProjectReady
            BoardColumn.Working -> Res.string.teamProjectWorking
            BoardColumn.Review -> Res.string.teamProjectReview
            BoardColumn.Done -> Res.string.teamProjectDone
        }
    )

/** Dart `_elapsed`. */
@Composable
internal fun elapsedWords(fromMs: Long?, nowMs: Long): String {
    if (fromMs == null) return ""
    val seconds = ((nowMs - fromMs) / 1000).coerceAtLeast(0)
    return when {
        seconds < 60 -> stringResource(Res.string.teamProjectElapsedSeconds, seconds.toInt())
        seconds < 3600 -> stringResource(Res.string.teamProjectElapsedMinutes, (seconds / 60).toInt())
        else -> stringResource(Res.string.teamProjectElapsedHours, (seconds / 3600).toInt(), ((seconds / 60) % 60).toInt())
    }
}

/** Dart `_age`: empty when the engine gave no time. */
@Composable
internal fun ageWords(atMs: Long?, nowMs: Long): String? = atMs?.let { kitRelativeAge(it, nowMs) }

internal fun TeamMark.kit(): KitTeamState =
    when (this) {
        TeamMark.Empty -> KitTeamState.Empty
        TeamMark.Loading -> KitTeamState.Loading
        TeamMark.Running -> KitTeamState.Running
        TeamMark.NeedsYou -> KitTeamState.NeedsYou
        TeamMark.Stalled -> KitTeamState.Stalled
        TeamMark.Failed -> KitTeamState.Failed
        TeamMark.Done -> KitTeamState.Done
        TeamMark.Stale -> KitTeamState.Stale
    }

internal fun TaskMark.kit(): KitTaskState =
    when (this) {
        TaskMark.Waiting -> KitTaskState.Waiting
        TaskMark.Working -> KitTaskState.Working
        TaskMark.Done -> KitTaskState.Done
        TaskMark.Failed -> KitTaskState.Failed
        TaskMark.NeedsYou -> KitTaskState.NeedsYou
    }
