package io.github.eslamasabry.oc.feature.team.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.team.TeamPhase
import io.github.eslamasabry.oc.feature.team.presentation.GoalUi
import io.github.eslamasabry.oc.feature.team.presentation.LaneUi
import io.github.eslamasabry.oc.feature.team.presentation.NeedsYouAnswer
import io.github.eslamasabry.oc.feature.team.presentation.NeedsYouUi
import io.github.eslamasabry.oc.feature.team.presentation.ProjectView
import io.github.eslamasabry.oc.feature.team.presentation.TeamProjectActions
import io.github.eslamasabry.oc.feature.team.presentation.TeamProjectUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitInputDialog
import io.github.eslamasabry.oc.ui.kit.modal.KitMenu
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
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
    var menuOpen by remember { mutableStateOf(false) }
    var answering by remember { mutableStateOf<NeedsYouUi?>(null) }
    var accepting by remember { mutableStateOf<NeedsYouUi?>(null) }
    val enabled = if (state.busy) KitEnabled.Disabled(stringResource(Res.string.teamProjectLoad)) else KitEnabled.Enabled
    val asks = NeedsYouHandlers(enabled, { answering = it }, { accepting = it }, actions)
    KitSheetHost(Modifier.fillMaxSize()) {
    KitScreen(
        topBar = {
            KitTopBar(
                title = p?.name ?: stringResource(Res.string.teamProjectHome),
                subtitle = p?.let { if (state.stale) stale else headlineWords(it.headline) },
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
                more =
                    if (p == null) null
                    else
                        ({ _ ->
                            val label = stringResource(Res.string.teamProjectMenu)
                            KitMenu(
                                expanded = menuOpen,
                                items = projectMenu(p, enabled, actions) { menuOpen = false },
                                onDismissRequest = { menuOpen = false },
                                title = label,
                            ) {
                                KitIconButton(KitIcons.more, label, { menuOpen = true })
                            }
                        }),
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
            else -> Overview(p, state, actions, asks)
        }
    }
    KitInputDialog(
        visible = answering != null,
        title = answering?.title.orEmpty(),
        fieldLabel = stringResource(Res.string.teamProjectAnswerLabel),
        confirmLabel = stringResource(Res.string.teamProjectAnswer),
        operationKey = answering?.id,
    ) { text ->
        val ask = answering
        answering = null
        if (ask != null && text != null) actions.answer(ask.id, text)
    }
    KitConfirm(
        spec =
            accepting?.let {
                KitConfirmSpec(
                    title = stringResource(Res.string.teamProjectAcceptMilestoneConfirmTitle),
                    confirmLabel = stringResource(Res.string.teamProjectAccept),
                    body = it.milestoneText,
                    cancelLabel = stringResource(Res.string.kitConfirmCancel),
                )
            },
        onConfirm = {
            accepting?.let { actions.acceptMilestone(it.milestoneId) }
            accepting = null
        },
        onCancel = { accepting = null },
    )
    KitConfirm(
        spec =
            if (!state.confirmStop || p == null) null
            else
                KitConfirmSpec(
                    title = stringResource(Res.string.teamProjectStopConfirmTitle),
                    confirmLabel = stringResource(Res.string.teamProjectStop),
                    kind = KitConfirmKind.Stop,
                    body =
                        if (p.name.isBlank()) stringResource(Res.string.teamProjectStopBody)
                        else stringResource(Res.string.teamProjectStopNamedBody, p.name.trim()),
                    cancelLabel = stringResource(Res.string.kitConfirmCancel),
                ),
        onConfirm = actions::confirmStop,
        onCancel = actions::cancelStop,
    )
    }
}

/** What the needs-you cards and milestone rows may do while nothing else is on its way. */
private class NeedsYouHandlers(
    val enabled: KitEnabled,
    val answer: (NeedsYouUi) -> Unit,
    val accept: (NeedsYouUi) -> Unit,
    val actions: TeamProjectActions,
)

/** Dart overview `menu`: Resume (interrupted), Pause or Resume while open, Stop (asks first). */
@Composable
private fun projectMenu(p: ProjectView, enabled: KitEnabled, actions: TeamProjectActions, close: () -> Unit): List<KitMenuItem> {
    fun item(label: String, destructive: Boolean = false, run: () -> Unit) =
        KitMenuItem(label, { close(); run() }, destructive = destructive, enabled = enabled)
    val resume = stringResource(Res.string.teamProjectResume)
    return listOfNotNull(
        item(resume, run = actions::resume).takeIf { p.menu.resumeInterrupted || p.menu.resume },
        item(stringResource(Res.string.teamProjectPause), run = actions::pause).takeIf { p.menu.pause },
        item(stringResource(Res.string.teamProjectStop), destructive = true, run = actions::askStop),
    )
}

@Composable
private fun Overview(p: ProjectView, state: TeamProjectUi, actions: TeamProjectActions, asks: NeedsYouHandlers) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = Space.S2.dp, bottom = Space.S6.dp),
        verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
    ) {
        if (state.refused) {
            item(key = "refused") {
                KitNotice(
                    stringResource(Res.string.teamProjectError),
                    dismiss = KitLineAction(stringResource(Res.string.kitConfirmCancel), actions::dismissRefusal),
                )
            }
        }
        if (state.stale) {
            item(key = "failure") {
                KitNotice(
                    stringResource(Res.string.teamProjectError),
                    actions = listOf(KitLineAction(stringResource(Res.string.teamProjectRetry), actions::retry)),
                )
            }
        }
        items(p.needsYou, key = { "ask:${it.id}" }) { NeedsYou(it, asks) }
        item(key = "goal") { Goal(p.goal, state.nowMs) }
        if (p.milestones.isNotEmpty()) {
            item(key = "milestones") { KitSectionLabel(stringResource(Res.string.teamProjectMilestones)) }
            items(p.milestones, key = { "m:${it.id}" }) { m ->
                Column {
                    KitMilestoneRow(
                        title = "${m.number} ${m.title}",
                        status = milestoneWords(m.status),
                        state = m.mark.kit(),
                        completed = m.completed,
                        total = m.total,
                        onPressed = { actions.openBoard(m.id) },
                    )
                    if (m.canAccept) {
                        KitButton(
                            stringResource(Res.string.teamProjectAccept),
                            { actions.acceptMilestone(m.id) },
                            role = KitButtonRole.Tertiary,
                            enabled = asks.enabled,
                        )
                    }
                }
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

/** Dart `_needsYou`: the ask, where it runs, what waits on it, and its answer where native has one. */
@Composable
private fun NeedsYou(r: NeedsYouUi, asks: NeedsYouHandlers) {
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
        primary =
            when (r.answer) {
                NeedsYouAnswer.Text -> KitAction(stringResource(Res.string.teamProjectAnswer), { asks.answer(r) }, asks.enabled)
                NeedsYouAnswer.AcceptMilestone ->
                    KitAction(stringResource(Res.string.teamProjectAccept), { asks.accept(r) }, asks.enabled)
                NeedsYouAnswer.Resume ->
                    KitAction(stringResource(Res.string.teamProjectInterruptedResume), { asks.actions.resumeTask(r.taskId) }, asks.enabled)
                null -> null
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
