package io.github.eslamasabry.oc.feature.team.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.team.TeamPhase
import io.github.eslamasabry.oc.feature.team.presentation.TaskAskUi
import io.github.eslamasabry.oc.feature.team.presentation.TaskSpeaker
import io.github.eslamasabry.oc.feature.team.presentation.TaskView
import io.github.eslamasabry.oc.feature.team.presentation.TeamTaskActions
import io.github.eslamasabry.oc.feature.team.presentation.TeamTaskUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.modal.KitInputDialog
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.team.KitPhaseCard
import io.github.eslamasabry.oc.ui.kit.team.KitTeamItem
import io.github.eslamasabry.oc.ui.kit.team.KitTeamState
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitPromptBubble
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * One task's conversation (Dart `TeamProjectConversation`): the person's notes, the team's instructions and the
 * worker's replies in order; what the task asks, with its answer; the acceptance results and findings; and a line to
 * message the worker.
 */
@Composable
public fun TeamTaskScreen(state: TeamTaskUi, actions: TeamTaskActions) {
    val t = state.task
    var answering by remember { mutableStateOf<TaskAskUi?>(null) }
    var messaging by remember { mutableStateOf(false) }
    val enabled = if (state.busy) KitEnabled.Disabled(stringResource(Res.string.teamProjectTaskWaiting)) else KitEnabled.Enabled
    KitSheetHost(Modifier.fillMaxSize()) {
        KitScreen(
            topBar = {
                KitTopBar(
                    title = t?.title ?: stringResource(Res.string.teamProjectHome),
                    subtitle = t?.let { listOfNotNull(it.roleName, it.serverName).joinToString(" · ").ifEmpty { null } },
                    exit = KitTopBarExit.Back,
                    exitLabel = stringResource(Res.string.kitTopBarBack),
                    onExit = actions::back,
                )
            },
            loading = state.phase == TeamPhase.Loading,
            loadingLabel = stringResource(Res.string.teamProjectLoad),
            width = KitScreenWidth.Reading,
            bottom =
                if (t == null) null
                else
                    ({
                        KitButton(
                            stringResource(Res.string.teamProjectTaskMessage),
                            { messaging = true },
                            Modifier.fillMaxWidth().padding(horizontal = LocalKitTokens.current.gutter.dp, vertical = Space.S2.dp),
                            role = KitButtonRole.Secondary,
                            enabled = enabled,
                        )
                    }),
        ) {
            when {
                state.phase == TeamPhase.Loading -> Unit
                t == null ->
                    KitStateView(
                        KitStateKind.Missing,
                        stringResource(Res.string.teamProjectTaskMissing),
                        primary = KitAction(stringResource(Res.string.teamProjectRetry), actions::retry),
                    )
                else -> Conversation(t, state, actions, enabled) { answering = it }
            }
        }
        KitInputDialog(
            visible = answering != null,
            title = answering?.title.orEmpty(),
            fieldLabel = stringResource(Res.string.teamProjectTaskAnswerLabel),
            confirmLabel = stringResource(Res.string.teamProjectTaskAnswer),
            operationKey = answering?.id,
        ) { text ->
            val ask = answering
            answering = null
            if (ask != null && text != null) actions.answer(ask.id, text)
        }
        KitInputDialog(
            visible = messaging,
            title = t?.title.orEmpty(),
            fieldLabel = stringResource(Res.string.teamProjectTaskMessage),
            confirmLabel = stringResource(Res.string.teamProjectTaskAnswer),
            operationKey = "message:${t?.id}",
        ) { text ->
            messaging = false
            if (text != null) actions.message(text)
        }
    }
}

@Composable
private fun Conversation(t: TaskView, state: TeamTaskUi, actions: TeamTaskActions, enabled: KitEnabled, answer: (TaskAskUi) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = Space.S2.dp, bottom = Space.S6.dp),
        verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
    ) {
        if (state.stale) item(key = "stale") { KitNotice(stringResource(Res.string.teamProjectTaskStale)) }
        if (state.refused) {
            item(key = "refused") {
                KitNotice(
                    stringResource(Res.string.teamProjectTaskSaveFailed),
                    dismiss = KitLineAction(stringResource(Res.string.kitConfirmCancel), actions::dismissRefusal),
                )
            }
        }
        if (t.lines.isEmpty() && t.queued) item(key = "empty") { KitNotice(stringResource(Res.string.teamProjectTaskEmpty)) }
        items(t.lines, key = { "line:${it.id}" }) { line ->
            val pad = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)
            when (line.speaker) {
                TaskSpeaker.Person -> KitPromptBubble(line.text, pad)
                TaskSpeaker.Instructions ->
                    KitPhaseCard(
                        title = stringResource(Res.string.teamProjectTaskInstructions),
                        status = "",
                        state = KitTeamState.Empty,
                        summary = line.text,
                    )
                TaskSpeaker.Worker -> KitText(line.text, KitTextRole.Body, pad)
            }
        }
        items(t.asks, key = { "ask:${it.id}" }) { ask ->
            KitPhaseCard(
                title = ask.title,
                status = ageWords(ask.createdAtMs, state.nowMs).orEmpty(),
                state = KitTeamState.NeedsYou,
                summary = if (ask.kind == "interrupted") stringResource(Res.string.teamProjectInterruptedResumeNote) else null,
                actions =
                    when (ask.kind) {
                        "question", "permission" ->
                            listOf(KitAction(stringResource(Res.string.teamProjectTaskAnswer), { answer(ask) }, enabled))
                        "interrupted" ->
                            listOf(KitAction(stringResource(Res.string.teamProjectInterruptedResume), actions::resume, enabled))
                        else -> emptyList()
                    },
            )
        }
        if (t.criteria.isNotEmpty()) {
            item(key = "criteria") {
                KitPhaseCard(
                    title = stringResource(Res.string.teamProjectTaskCriteria),
                    status = stringResource(Res.string.teamProjectTaskVerificationResults),
                    state = KitTeamState.Done,
                    items =
                        t.criteria.map { c ->
                            KitTeamItem(
                                title = c.criterion,
                                detail =
                                    stringResource(
                                        when (c.status) {
                                            "met" -> Res.string.teamProjectTaskCriterionMet
                                            "unmet" -> Res.string.teamProjectTaskCriterionUnmet
                                            else -> Res.string.teamProjectTaskCriterionNotApplicable
                                        }
                                    ),
                                state = if (c.status == "unmet") KitTeamState.Failed else KitTeamState.Done,
                            )
                        },
                )
            }
        }
        if (t.findings.isNotEmpty()) {
            item(key = "findings") {
                val open = t.findings.any { it.open }
                KitPhaseCard(
                    title = stringResource(Res.string.teamProjectTaskFindings),
                    status =
                        stringResource(if (open) Res.string.teamProjectTaskOpenFindings else Res.string.teamProjectTaskFindingsAddressed),
                    state = if (open) KitTeamState.NeedsYou else KitTeamState.Done,
                    items =
                        t.findings.map { f ->
                            KitTeamItem(
                                title = f.text,
                                detail =
                                    stringResource(
                                        when (f.severity) {
                                            "critical" -> Res.string.teamProjectTaskCritical
                                            "minor" -> Res.string.teamProjectTaskMinor
                                            else -> Res.string.teamProjectTaskMajor
                                        }
                                    ),
                                meta = f.location.ifEmpty { null },
                                state = if (f.open) KitTeamState.NeedsYou else KitTeamState.Done,
                            )
                        },
                )
            }
        }
    }
}
