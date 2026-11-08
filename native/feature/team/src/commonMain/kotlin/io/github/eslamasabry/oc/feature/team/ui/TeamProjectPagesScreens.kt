package io.github.eslamasabry.oc.feature.team.ui

import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.team.TeamPhase
import io.github.eslamasabry.oc.feature.team.presentation.PhoneServerProblem
import io.github.eslamasabry.oc.feature.team.presentation.TeamRules
import io.github.eslamasabry.oc.feature.team.presentation.TeamServersActions
import io.github.eslamasabry.oc.feature.team.presentation.TeamServersUi
import io.github.eslamasabry.oc.feature.team.presentation.TeamTimelineActions
import io.github.eslamasabry.oc.feature.team.presentation.TeamTimelineUi
import io.github.eslamasabry.oc.feature.team.presentation.TimelineFilter
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextRole
import io.github.eslamasabry.oc.ui.kit.choice.KitChoice
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceList
import io.github.eslamasabry.oc.ui.kit.choice.KitSegment
import io.github.eslamasabry.oc.ui.kit.choice.KitSegmented
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitInputDialog
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.team.KitPhaseCard
import io.github.eslamasabry.oc.ui.kit.team.KitTeamItem
import io.github.eslamasabry.oc.ui.kit.team.KitTeamState
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * One project's servers (Dart `TeamProjectServers`): each server's state, its busy lanes and tasks; a server's lane
 * limit; moving a task to another server with a hand-off note, and restarting it there when its branch cannot follow.
 */
@Composable
public fun TeamServersScreen(state: TeamServersUi, actions: TeamServersActions) {
    val stale = stringResource(Res.string.teamProjectTaskStale)
    KitSheetHost(Modifier.fillMaxSize()) {
        KitScreen(
            topBar = {
                KitTopBar(
                    title = stringResource(Res.string.teamProjectServers),
                    subtitle = state.projectName,
                    exit = KitTopBarExit.Back,
                    exitLabel = stringResource(Res.string.kitTopBarBack),
                    onExit = actions::back,
                )
            },
            loading = state.phase == TeamPhase.Loading,
            loadingLabel = stringResource(Res.string.teamProjectLoad),
            width = KitScreenWidth.Reading,
            edges = KitScreenEdges.Bleed,
        ) {
            if (state.phase == TeamPhase.Loading) return@KitScreen
            if (state.projectName == null) {
                KitStateView(KitStateKind.Empty, stringResource(Res.string.teamProjectSelect))
                return@KitScreen
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = Space.S2.dp, bottom = Space.S6.dp),
                verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
            ) {
                item(key = "problem") {
                    when (state.phoneProblem) {
                        PhoneServerProblem.NotReady -> KitNotice(stringResource(Res.string.teamServerPhoneNotReady), Modifier.testTag("team-server-problem"))
                        PhoneServerProblem.NoAnswer ->
                            KitNotice(
                                stringResource(Res.string.teamServerPhoneNoAnswer),
                                Modifier.testTag("team-server-problem"),
                                actions = listOf(KitLineAction(stringResource(Res.string.teamProjectRetry), actions::retry)),
                            )
                        null -> ExecutionLines(state.blocked, null, false)
                    }
                }
                state.refusal?.let { r ->
                    item(key = "refused") {
                        KitNotice(
                            refusalWords(r.action, r.code).message,
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
                if (state.demo) item(key = "demo") { KitText(stringResource(Res.string.teamProjectCostDemo), KitTextRole.Supporting) }
                items(state.servers, key = { "s:${it.id}" }) { s ->
                    Column(verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
                        KitPhaseCard(
                            title = s.name,
                            status =
                                when {
                                    state.stale -> stale
                                    s.online -> stringResource(Res.string.teamProjectOnline)
                                    else -> stringResource(Res.string.teamProjectOffline)
                                },
                            state = if (state.stale || !s.online) KitTeamState.Stale else KitTeamState.Done,
                            summary = stringResource(Res.string.teamProjectLaneCount, s.running, s.lanes),
                            items =
                                s.tasks.map { t ->
                                    KitTeamItem(
                                        t.title,
                                        detail = if (state.stale) stale else statusWord(TeamRules.word(t.status)),
                                        onPressed = { actions.openTask(t.id) },
                                    )
                                },
                        )
                        KitButton(
                            stringResource(Res.string.teamProjectEditorMaxLanes),
                            { actions.editLanes(s.id) },
                            role = KitButtonRole.Tertiary,
                        )
                        s.tasks.filter { it.movable }.forEach { t ->
                            val label = "${stringResource(Res.string.teamProjectMove)} · ${t.title}"
                            KitSectionLabel(label)
                            KitChoiceList(
                                choices = state.servers.map { KitChoice(it.id, it.name) },
                                selected = s.id,
                                semanticsLabel = label,
                            ) { target -> actions.chooseMove(t.id, target) }
                        }
                    }
                }
            }
        }
        val editing = state.servers.firstOrNull { it.id == state.editingLanes }
        val positive = stringResource(Res.string.teamProjectEditorPositiveLanes)
        KitInputDialog(
            visible = editing != null,
            title = editing?.name.orEmpty(),
            fieldLabel = stringResource(Res.string.teamProjectEditorMaxLanes),
            confirmLabel = stringResource(Res.string.teamProjectEditorSave),
            initial = editing?.laneCap?.toString().orEmpty(),
            kind = KitFieldKind.Number,
            validate = { if ((it.trim().toIntOrNull() ?: 0) > 0) null else positive },
            operationKey = editing?.id,
        ) { text ->
            val s = editing
            if (s == null || text == null) actions.editLanes(null) else actions.saveLanes(s.id, text.trim().toInt())
        }
        val move = state.moving
        KitInputDialog(
            visible = move != null && !move.restartElsewhere,
            title = stringResource(Res.string.teamProjectMoveTo),
            fieldLabel = stringResource(Res.string.teamProjectHandoff),
            confirmLabel = stringResource(Res.string.teamProjectMove),
            operationKey = move?.let { "${it.taskId}:${it.serverId}" },
        ) { note -> actions.moveWithNote(note) }
        KitConfirm(
            spec =
                if (move?.restartElsewhere != true) null
                else
                    KitConfirmSpec(
                        title = stringResource(Res.string.teamProjectRestartElsewhereConfirmTitle),
                        confirmLabel = stringResource(Res.string.teamProjectRestartElsewhere),
                        kind = KitConfirmKind.Neutral,
                        body = stringResource(Res.string.teamProjectRestartElsewhereBody),
                        cancelLabel = stringResource(Res.string.teamProjectWaitForServer),
                    ),
            onConfirm = { actions.restartElsewhere(true) },
            onCancel = { actions.restartElsewhere(false) },
        )
    }
}

/** One project's timeline (Dart `TeamProjectTimeline`): newest first by day, filtered, repeats folded. */
@Composable
public fun TeamTimelineScreen(state: TeamTimelineUi, actions: TeamTimelineActions) {
    val label = stringResource(Res.string.teamProjectTimeline)
    KitScreen(
        topBar = {
            KitTopBar(
                title = label,
                subtitle = state.projectName,
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        header = {
            KitSegmented(
                segments =
                    listOf(
                        KitSegment(TimelineFilter.All, stringResource(Res.string.teamProjectAll)),
                        KitSegment(TimelineFilter.Decisions, stringResource(Res.string.teamProjectDecisions)),
                        KitSegment(TimelineFilter.Merges, stringResource(Res.string.teamProjectMerges)),
                        KitSegment(TimelineFilter.Problems, stringResource(Res.string.teamProjectProblems)),
                    ),
                selected = state.filter,
                semanticsLabel = label,
                modifier = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp),
                onChange = actions::filter,
            )
        },
        loading = state.phase == TeamPhase.Loading,
        loadingLabel = stringResource(Res.string.teamProjectLoad),
        width = KitScreenWidth.Reading,
        edges = KitScreenEdges.Bleed,
    ) {
        if (state.phase == TeamPhase.Loading) return@KitScreen
        if (state.days.isEmpty()) {
            KitStateView(KitStateKind.Empty, stringResource(Res.string.teamProjectNoTasks))
            return@KitScreen
        }
        val unknown = stringResource(Res.string.teamProjectUnknown)
        val demo = if (state.demo) stringResource(Res.string.teamProjectDemo) else ""
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = Space.S2.dp, bottom = Space.S6.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            items(state.days, key = { "d:${it.day}" }) { day ->
                KitPhaseCard(
                    title = day.day ?: unknown,
                    status = demo,
                    items =
                        day.rows.map { r ->
                            val words = timelineWords(r.text)
                            KitTeamItem(
                                if (r.count > 1) stringResource(Res.string.teamProjectTimelineRepeated, words, r.count) else words,
                                detail =
                                    if (r.actor == "person") stringResource(Res.string.teamProjectYou)
                                    else r.roleName ?: stringResource(Res.string.teamProjectHome),
                                meta = ageWords(r.atMs, state.nowMs),
                                onPressed = if (r.taskId.isEmpty()) null else ({ actions.openTask(r.taskId) }),
                            )
                        },
                )
            }
        }
    }
}

