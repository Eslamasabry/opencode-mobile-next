package io.github.eslamasabry.oc.feature.team.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.team.TeamPhase
import io.github.eslamasabry.oc.feature.team.presentation.GoalUi
import io.github.eslamasabry.oc.feature.team.presentation.LaneUi
import io.github.eslamasabry.oc.feature.team.presentation.NeedsYouUi
import io.github.eslamasabry.oc.feature.team.presentation.ProjectView
import io.github.eslamasabry.oc.feature.team.presentation.TeamProjectActions
import io.github.eslamasabry.oc.feature.team.presentation.TeamProjectUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowState
import io.github.eslamasabry.oc.ui.kit.row.KitRowStateTone
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.team.KitMilestoneRow
import io.github.eslamasabry.oc.ui.kit.team.KitPlanCard
import io.github.eslamasabry.oc.ui.kit.team.KitTeamState
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * One project (Dart `TeamProjectOverview`): what needs the person, the goal, the milestones (each opens the board
 * filtered to it), the lanes, and the board.
 */
@Composable
public fun TeamProjectScreen(state: TeamProjectUi, actions: TeamProjectActions) {
    val p = state.project
    val stale = stringResource(Res.string.teamProjectTaskStale)
    KitScreen(
        topBar = {
            KitTopBar(
                title = p?.name ?: stringResource(Res.string.teamProjectHome),
                subtitle = p?.let { if (state.stale) stale else headlineWords(it.headline) },
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        loading = state.phase == TeamPhase.Loading,
        loadingLabel = stringResource(Res.string.teamProjectLoad),
        width = KitScreenWidth.Reading,
    ) {
        when {
            state.phase == TeamPhase.Loading -> Unit
            p == null ->
                KitStateView(
                    KitStateKind.Empty,
                    stringResource(Res.string.teamProjectSelect),
                    primary =
                        if (state.phase == TeamPhase.Ready) null
                        else KitAction(stringResource(Res.string.teamProjectRetry), actions::retry),
                )
            else -> Overview(p, state, actions)
        }
    }
}

@Composable
private fun Overview(p: ProjectView, state: TeamProjectUi, actions: TeamProjectActions) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = Space.S2.dp, bottom = Space.S6.dp),
        verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
    ) {
        if (state.stale) {
            item(key = "failure") {
                KitNotice(
                    stringResource(Res.string.teamProjectError),
                    actions = listOf(KitLineAction(stringResource(Res.string.teamProjectRetry), actions::retry)),
                )
            }
        }
        items(p.needsYou, key = { "ask:${it.id}" }) { NeedsYou(it) }
        item(key = "goal") { Goal(p.goal, state.nowMs) }
        if (p.milestones.isNotEmpty()) {
            item(key = "milestones") { KitSectionLabel(stringResource(Res.string.teamProjectMilestones)) }
            items(p.milestones, key = { "m:${it.id}" }) { m ->
                KitMilestoneRow(
                    title = "${m.number} ${m.title}",
                    status = milestoneWords(m.status),
                    state = m.mark.kit(),
                    completed = m.completed,
                    total = m.total,
                    onPressed = { actions.openBoard(m.id) },
                )
            }
        }
        if (p.lanes.isNotEmpty()) {
            item(key = "lanes") {
                KitSectionLabel(stringResource(Res.string.teamProjectLanesTitle, p.busy, p.laneTotal))
            }
            items(p.lanes, key = { "lane:${it.taskId}" }) { Lane(it, state.stale, state.nowMs) }
            item(key = "laneNote") {
                val note =
                    if (p.singleLane) stringResource(Res.string.teamProjectLaneNoteSingle)
                    else pluralStringResource(Res.plurals.teamProjectLaneNoteParallel, p.laneTotal, p.laneTotal)
                KitText(if (p.demo) stringResource(Res.string.teamProjectLaneNoteDemo, note) else note, KitTextRole.Supporting)
            }
        }
        item(key = "board") {
            KitRow(
                title = stringResource(Res.string.teamProjectBoard),
                supporting =
                    if (p.taskCount == 0) stringResource(Res.string.teamProjectBoardEmpty)
                    else pluralStringResource(Res.plurals.teamProjectBoardSummary__tasks, p.taskCount, p.taskCount),
                trailing = KitRowTrailing.Chevron,
                onClick = { actions.openBoard(null) },
            )
        }
    }
}

/** Dart `_needsYou`; answering arrives with the next slice, so the card states the ask. */
@Composable
private fun NeedsYou(r: NeedsYouUi) {
    val where =
        if (r.roleName != null && r.serverName != null) stringResource(Res.string.teamProjectRequestWhere, r.roleName, r.serverName)
        else null
    KitPlanCard(
        title = r.title,
        status = listOfNotNull(stringResource(Res.string.teamProjectNeedsYou), where).joinToString(" · "),
        state = KitTeamState.NeedsYou,
        summary =
            when {
                r.kind == "interrupted" -> stringResource(Res.string.teamProjectInterruptedResumeNote)
                r.roleName != null && r.blocks > 0 ->
                    pluralStringResource(Res.plurals.teamProjectRequestBlocks, r.blocks, r.roleName, r.blocks)
                else -> null
            },
    )
}

/** Dart `_goalStatus`. */
@Composable
private fun Goal(goal: GoalUi, nowMs: Long) {
    val status =
        if (goal.approvedAtMs == null)
            stringResource(
                Res.string.teamProjectGoalStatusDraft,
                pluralStringResource(Res.plurals.teamProjectGoalStatusDraft__milestones, goal.milestones, goal.milestones),
                pluralStringResource(Res.plurals.teamProjectGoalStatusDraft__repos, goal.repos, goal.repos),
            )
        else
            stringResource(
                Res.string.teamProjectGoalStatus,
                ageWords(goal.approvedAtMs, nowMs).orEmpty(),
                pluralStringResource(Res.plurals.teamProjectGoalStatus__milestones, goal.milestones, goal.milestones),
                pluralStringResource(Res.plurals.teamProjectGoalStatus__repos, goal.repos, goal.repos),
            )
    KitPlanCard(title = goal.goal, status = status)
}

/** Dart `_laneLine` and the lane row (`KitStatusMark` working/waiting). */
@Composable
private fun Lane(lane: LaneUi, stale: Boolean, nowMs: Long) {
    val line =
        when {
            stale -> "${lane.serverName} · ${stringResource(Res.string.teamProjectTaskStale)}"
            lane.serverOffline -> "${lane.serverName} · ${stringResource(Res.string.teamProjectOffline)}"
            lane.running -> stringResource(Res.string.teamProjectLaneRunning, lane.serverName, elapsedWords(lane.changedAtMs, nowMs))
            else -> stringResource(Res.string.teamProjectLaneWaiting, lane.serverName)
        }
    KitRow(
        title =
            stringResource(
                Res.string.teamProjectLaneTitle,
                lane.roleName ?: stringResource(Res.string.teamProjectHome),
                lane.title,
            ),
        supporting = line,
        state =
            if (lane.running && !stale) KitRowState(stringResource(Res.string.teamProjectWorking), KitRowStateTone.Running)
            else null,
    )
}
