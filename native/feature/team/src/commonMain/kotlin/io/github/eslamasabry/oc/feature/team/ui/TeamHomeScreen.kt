package io.github.eslamasabry.oc.feature.team.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.team.TeamPhase
import io.github.eslamasabry.oc.feature.team.presentation.AskAnswer
import io.github.eslamasabry.oc.feature.team.presentation.EditorKind
import io.github.eslamasabry.oc.feature.team.presentation.HomeRow
import io.github.eslamasabry.oc.feature.team.presentation.TeamHomeActions
import io.github.eslamasabry.oc.feature.team.presentation.TeamHomeUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitInputDialog
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.team.KitProjectRow
import io.github.eslamasabry.oc.ui.kit.team.KitTeamState
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * The team page (Dart `TeamPage` + `TeamProjectsScreen`): the phone team's projects most urgent first, each project's
 * open requests above it, answered from the list; Team defaults and Roles in the top bar; New project and Quick task
 * pinned while the engine can run work. Or why there are none (off, a computer's team, the phone's team not answering).
 */
@Composable
public fun TeamHomeScreen(state: TeamHomeUi, actions: TeamHomeActions) {
    val title = stringResource(Res.string.teamProjectHome)
    var answering by remember { mutableStateOf<HomeRow.Request?>(null) }
    val ready = state.phase == TeamPhase.Ready
    KitSheetHost(Modifier.fillMaxSize()) {
        KitScreen(
            topBar = {
                KitTopBar(
                    title = title,
                    subtitle = if (state.demo) stringResource(Res.string.teamProjectDemoChip) else null,
                    exit = KitTopBarExit.Back,
                    exitLabel = stringResource(Res.string.kitTopBarBack),
                    onExit = actions::back,
                    actions =
                        if (!ready) emptyList()
                        else
                            listOf(
                                KitLineAction(
                                    stringResource(Res.string.teamProjectEditorDefaults),
                                    { actions.openEditor(EditorKind.Defaults) },
                                    icon = KitIcons.settings,
                                    testTag = "team-defaults",
                                ),
                                KitLineAction(
                                    stringResource(Res.string.teamProjectRoles),
                                    { actions.openEditor(EditorKind.Roles) },
                                    icon = KitIcons.agent,
                                    testTag = "team-roles",
                                ),
                            ),
                )
            },
            loading = state.phase == TeamPhase.Loading || state.loading,
            loadingLabel = stringResource(Res.string.teamProjectLoad),
            width = KitScreenWidth.Reading,
            bottom =
                if (!ready || !state.canStart) null
                else
                    ({
                        KitActionBlock(
                            Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp, vertical = Space.S2.dp),
                            primary = KitAction(stringResource(Res.string.teamProjectNew), { actions.openEditor(EditorKind.Create) }),
                            tertiary =
                                listOf(KitAction(stringResource(Res.string.teamProjectQuick), { actions.openEditor(EditorKind.Quick) })),
                        )
                    }),
        ) {
            when (state.phase) {
                TeamPhase.Loading -> Unit
                TeamPhase.Off -> KitStateView(KitStateKind.Calm, stringResource(Res.string.phoneTeamOffTitle))
                TeamPhase.Computer ->
                    KitStateView(
                        KitStateKind.Missing,
                        stringResource(Res.string.teamUiHomeTitle),
                        body = stringResource(Res.string.nativeCapabilityTeamDeferred),
                    )
                TeamPhase.PhoneOff ->
                    KitStateView(
                        KitStateKind.Missing,
                        stringResource(Res.string.phoneTeamOffTitle),
                        body = stringResource(Res.string.phoneTeamOffBody),
                        primary = KitAction(stringResource(Res.string.teamProjectRetry), actions::retry),
                    )
                TeamPhase.Ready -> Projects(state, actions) { answering = it }
            }
        }
        KitInputDialog(
            visible = answering != null,
            title = answering?.title.orEmpty(),
            fieldLabel = stringResource(Res.string.teamProjectAnswerLabel),
            confirmLabel = stringResource(Res.string.teamProjectAnswer),
            operationKey = answering?.requestId,
        ) { text ->
            val ask = answering
            answering = null
            if (ask != null && text != null) actions.answer(ask.projectId, ask.requestId, text)
        }
        KitConfirm(
            spec =
                state.accepting?.let {
                    KitConfirmSpec(
                        title = stringResource(Res.string.teamProjectAcceptMilestoneConfirmTitle),
                        confirmLabel = stringResource(Res.string.teamProjectAccept),
                        body = it.text,
                        cancelLabel = stringResource(Res.string.kitConfirmCancel),
                    )
                },
            onConfirm = actions::confirmAccept,
            onCancel = actions::cancelAccept,
        )
    }
}

/** Dart `_answer` from the team page's request row. */
private fun answerRow(row: HomeRow.Request, actions: TeamHomeActions, text: (HomeRow.Request) -> Unit) {
    when (val a = row.answer) {
        AskAnswer.Text -> text(row)
        is AskAnswer.Editor -> actions.openEditor(a.kind, row.projectId)
        is AskAnswer.AcceptMilestone -> actions.askAccept(row.projectId, a)
        is AskAnswer.Resume -> actions.resumeTask(row.projectId, a.taskId)
        is AskAnswer.Task -> actions.openTask(row.projectId, a.taskId)
        AskAnswer.None -> actions.openRequest(row.projectId, row.requestId)
    }
}

/** Dart `TeamExecutionBlocked`, `TeamProtectionLine` and `TeamResumeUnavailable`: one plain line each. */
@Composable
internal fun ExecutionLines(blocked: Boolean, protection: String?, resumeUnavailable: Boolean) {
    if (blocked) KitNotice(stringResource(Res.string.phoneTeamBlocked), Modifier.testTag("team-execution-blocked"))
    when (protection) {
        "proot" -> KitNotice(stringResource(Res.string.phoneTeamProtectedProot), Modifier.testTag("team-protection-line"), icon = KitIcons.shield)
        "landlock" ->
            KitNotice(stringResource(Res.string.phoneTeamProtectedLandlock), Modifier.testTag("team-protection-line"), icon = KitIcons.shield)
    }
    if (resumeUnavailable) KitNotice(stringResource(Res.string.teamProjectResumeUnavailable), Modifier.testTag("team-resume-unavailable"))
}

@Composable
private fun Projects(state: TeamHomeUi, actions: TeamHomeActions, text: (HomeRow.Request) -> Unit) {
    val stale = stringResource(Res.string.teamProjectTaskStale)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = Space.S2.dp, bottom = Space.S6.dp),
        verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
    ) {
        item(key = "lines") { ExecutionLines(state.blocked, state.protection, state.resumeUnavailable) }
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
        if (state.rows.isEmpty() && !state.stale) {
            item(key = "empty") { KitStateView(KitStateKind.Empty, stringResource(Res.string.teamProjectEmpty)) }
        }
        items(state.rows, key = { it.key }) { row ->
            when (row) {
                is HomeRow.Interrupted ->
                    KitRow(
                        title = stringResource(Res.string.teamProjectInterruptedRow, row.name),
                        modifier = Modifier.testTag("team-resume-row-${row.projectId}"),
                        onClick = { actions.resumeProject(row.projectId) },
                    )
                is HomeRow.Request ->
                    KitRow(
                        title = row.title,
                        supporting = row.projectName,
                        trailing = KitRowTrailing.Chevron,
                        onClick = { answerRow(row, actions, text) },
                    )
                is HomeRow.Project ->
                    KitProjectRow(
                        title = row.name,
                        status = if (state.stale) stale else headlineWords(row.headline),
                        state = if (state.stale) KitTeamState.Stale else row.mark.kit(),
                        detail =
                            when {
                                row.budgetWarning -> stringResource(Res.string.teamProjectBudgetNear)
                                row.spentToday != null -> todayCost(row.spentToday, row.dailyLimit)
                                else -> null
                            },
                        meta = ageWords(row.updatedAtMs, state.nowMs),
                        onPressed = { actions.openProject(row.id) },
                    )
            }
        }
    }
}
