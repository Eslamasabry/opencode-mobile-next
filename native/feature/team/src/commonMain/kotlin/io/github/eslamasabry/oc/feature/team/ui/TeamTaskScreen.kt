package io.github.eslamasabry.oc.feature.team.ui

import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.frame.kitPageGutter
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.team.TeamPhase
import io.github.eslamasabry.oc.feature.team.presentation.PlanUi
import io.github.eslamasabry.oc.feature.team.presentation.TaskAskAction
import io.github.eslamasabry.oc.feature.team.presentation.TaskAskUi
import io.github.eslamasabry.oc.feature.team.presentation.TaskConfirm
import io.github.eslamasabry.oc.feature.team.presentation.TaskPromoteUi
import io.github.eslamasabry.oc.feature.team.presentation.TaskSpeaker
import io.github.eslamasabry.oc.feature.team.presentation.TaskView
import io.github.eslamasabry.oc.feature.team.presentation.TeamTaskActions
import io.github.eslamasabry.oc.feature.team.presentation.TeamTaskUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitMonoText
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextRole
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.choice.KitChoice
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceList
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitInputDialog
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.row.KitExpandRow
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.team.KitFindingSeverity
import io.github.eslamasabry.oc.ui.kit.team.KitFindingsCard
import io.github.eslamasabry.oc.ui.kit.team.KitMergeQueue
import io.github.eslamasabry.oc.ui.kit.team.KitPhaseCard
import io.github.eslamasabry.oc.ui.kit.team.KitPlanCard
import io.github.eslamasabry.oc.ui.kit.team.KitPlanPhase
import io.github.eslamasabry.oc.ui.kit.team.KitPlanTask
import io.github.eslamasabry.oc.ui.kit.team.KitPromoteCard
import io.github.eslamasabry.oc.ui.kit.team.KitTeamFinding
import io.github.eslamasabry.oc.ui.kit.team.KitTeamItem
import io.github.eslamasabry.oc.ui.kit.team.KitTeamState
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitPromptBubble
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * One task's conversation (Dart `TeamProjectConversation`): the person's notes, the team's instructions and the
 * worker's replies in order; the plan while it waits; the phase to accept; what the task asks, with its answer; the
 * work log; the acceptance results and findings to fix or ignore; the merge queue, promotion and receipts; the task's
 * controls; and a line to message the worker.
 */
@Composable
public fun TeamTaskScreen(state: TeamTaskUi, actions: TeamTaskActions) {
    val t = state.task
    var answering by remember { mutableStateOf<TaskAskUi?>(null) }
    var messaging by remember { mutableStateOf(false) }
    var ignoring by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf<Pair<String, String>?>(null) }
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
                            Modifier.fillMaxWidth().padding(vertical = Space.S2.dp),
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
                        primary = KitAction(stringResource(Res.string.teamProjectRefreshTask), actions::retry),
                    )
                else ->
                    Conversation(
                        t,
                        state,
                        actions,
                        enabled,
                        answer = { answering = it },
                        ignore = { ignoring = true },
                        move = { taskId, serverId -> moving = taskId to serverId },
                    )
            }
        }
        KitInputDialog(
            visible = answering != null,
            title = answering?.title.orEmpty(),
            fieldLabel = stringResource(Res.string.teamProjectTaskAnswerLabel),
            confirmLabel = stringResource(Res.string.teamProjectTaskAnswer),
            kind = io.github.eslamasabry.oc.ui.kit.input.KitFieldKind.Multiline,
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
        val reasonRequired = stringResource(Res.string.teamProjectTaskReasonRequired)
        KitInputDialog(
            visible = ignoring,
            title = stringResource(Res.string.teamProjectTaskIgnore),
            fieldLabel = stringResource(Res.string.teamProjectTaskIgnoreReason),
            confirmLabel = stringResource(Res.string.teamProjectTaskIgnore),
            kind = io.github.eslamasabry.oc.ui.kit.input.KitFieldKind.Multiline,
            validate = { if (it.isBlank()) reasonRequired else null },
            operationKey = "ignore:${state.selected.firstOrNull()}",
        ) { text ->
            ignoring = false
            if (text != null) actions.ignoreSelected(text)
        }
        if (t != null && moving != null) {
            val (taskId, current) = moving!!
            val title = t.plan?.phases?.flatMap { it.tasks }?.firstOrNull { it.taskId == taskId }?.title.orEmpty()
            KitSheet(
                visible = true,
                onDismissRequest = { moving = null },
                title = stringResource(Res.string.teamProjectPlanServerTitle, title),
            ) {
                KitChoiceList(
                    choices = t.servers.map { (id, name) -> KitChoice(id, name) },
                    selected = current,
                    semanticsLabel = stringResource(Res.string.teamProjectPlanServerTitle, title),
                    sends = true,
                ) { id ->
                    moving = null
                    if (id != current) actions.move(taskId, id)
                }
            }
        }
        KitConfirm(spec = t?.let { confirmSpec(it, state.confirming) }, onConfirm = actions::confirm, onCancel = actions::cancelConfirm)
    }
}

/** Dart `_stop`, `confirmAndMergeToDev` and `confirmAndPromote` on the task page. */
@Composable
private fun confirmSpec(t: TaskView, ask: TaskConfirm?): KitConfirmSpec? =
    when (ask) {
        null -> null
        TaskConfirm.Stop ->
            KitConfirmSpec(
                title = stringResource(Res.string.teamProjectTaskStopConfirmTitle),
                confirmLabel = stringResource(Res.string.teamProjectTaskStop),
                kind = KitConfirmKind.Stop,
                body =
                    if (t.title.isBlank()) stringResource(Res.string.teamProjectTaskStopBody)
                    else stringResource(Res.string.teamProjectTaskStopNamedBody, t.title.trim()),
                cancelLabel = stringResource(Res.string.kitConfirmCancel),
            )
        TaskConfirm.Merge ->
            KitConfirmSpec(
                title = stringResource(Res.string.teamProjectMergeConfirmTitle),
                confirmLabel = stringResource(Res.string.teamProjectMergeNext),
                kind = KitConfirmKind.Neutral,
                body = stringResource(Res.string.teamProjectMergeConfirmBody),
                cancelLabel = stringResource(Res.string.kitConfirmCancel),
                consequences = listOf(stringResource(Res.string.teamProjectMergeEffectMain)),
            )
        TaskConfirm.Promote ->
            KitConfirmSpec(
                title = stringResource(Res.string.teamProjectTaskPromoteConfirmTitle),
                confirmLabel = stringResource(Res.string.teamProjectTaskPromote),
                kind = KitConfirmKind.Neutral,
                body = stringResource(Res.string.teamProjectTaskPromoteBody),
                cancelLabel = stringResource(Res.string.kitConfirmCancel),
                consequences = listOfNotNull(t.promote?.let { "${it.repoName}: ${it.mainCommit} → ${it.devCommit}" }),
            )
    }

/** Dart `_state` for a task status on this page. */
private fun taskState(status: String): KitTeamState =
    when (status) {
        "running", "verifying", "fixing" -> KitTeamState.Running
        "needsYou", "needs_you", "question", "review" -> KitTeamState.NeedsYou
        "failed" -> KitTeamState.Failed
        "stalled", "interrupted" -> KitTeamState.Stalled
        "done", "merged", "accepted" -> KitTeamState.Done
        else -> KitTeamState.Empty
    }

@Composable
private fun taskStatus(status: String): String =
    stringResource(
        when (taskState(status)) {
            KitTeamState.Running -> Res.string.teamProjectTaskRunning
            KitTeamState.NeedsYou -> Res.string.teamProjectTaskReview
            KitTeamState.Failed -> Res.string.teamProjectTaskFailed
            KitTeamState.Done -> Res.string.teamProjectTaskDone
            else -> Res.string.teamProjectTaskWaiting
        }
    )

@Composable
private fun Conversation(
    t: TaskView,
    state: TeamTaskUi,
    actions: TeamTaskActions,
    enabled: KitEnabled,
    answer: (TaskAskUi) -> Unit,
    ignore: () -> Unit,
    move: (String, String) -> Unit,
) {
    val pad = Modifier.padding(horizontal = kitPageGutter())
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = Space.S2.dp, bottom = Space.S6.dp),
        verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
    ) {
        if (state.stale) item(key = "stale") { KitNotice(stringResource(Res.string.teamProjectTaskStale)) }
        state.refusal?.let { r ->
            item(key = "refused") {
                val words = refusalWords(r.action, r.code)
                KitNotice(
                    words.message,
                    title = stringResource(Res.string.teamProjectTaskSaveFailed),
                    notes = listOf(words.next),
                    // F: team_project_conversation.dart:643, "Refresh task" reads the task again.
                    actions = listOf(KitLineAction(stringResource(Res.string.teamProjectRefreshTask), actions::retry)),
                    dismiss = KitLineAction(stringResource(Res.string.kitConfirmCancel), actions::dismissRefusal),
                )
            }
        }
        if (t.lines.isEmpty() && t.queued) item(key = "empty") { KitNotice(stringResource(Res.string.teamProjectTaskEmpty)) }
        items(t.lines, key = { "line:${it.id}" }) { line ->
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
        t.plan?.let { plan -> item(key = "plan") { Plan(plan, actions, enabled, move) } }
        t.phase?.let { ph ->
            item(key = "phase") {
                KitPhaseCard(
                    title = ph.title,
                    status =
                        stringResource(
                            when {
                                ph.accepted -> Res.string.teamProjectTaskAccepted
                                ph.failed -> Res.string.teamProjectTaskFailed
                                else -> Res.string.teamProjectTaskCriteria
                            }
                        ),
                    state =
                        when {
                            ph.failed -> KitTeamState.Failed
                            ph.asking -> KitTeamState.NeedsYou
                            else -> KitTeamState.Done
                        },
                    items = listOf(KitTeamItem(t.title, state = if (ph.taskDone) KitTeamState.Done else KitTeamState.Empty)),
                    actions =
                        listOfNotNull(
                            KitAction(stringResource(Res.string.teamProjectTaskAcceptPhase), { actions.acceptPhase(ph.id) }, enabled)
                                .takeIf { ph.canAccept }
                        ),
                )
            }
        }
        items(t.asks, key = { "ask:${it.id}" }) { ask ->
            KitPhaseCard(
                title = ask.title,
                status = ageWords(ask.createdAtMs, state.nowMs).orEmpty(),
                state = KitTeamState.NeedsYou,
                summary = if (ask.kind == "interrupted") stringResource(Res.string.teamProjectInterruptedResumeNote) else null,
                actions = ask.actions.map { askAction(it, ask, actions, enabled, answer) },
            )
        }
        if (t.events.isNotEmpty()) {
            item(key = "work") {
                var open by remember { mutableStateOf(false) }
                KitExpandRow(
                    title =
                        stringResource(
                            when {
                                t.running -> Res.string.teamProjectTaskWorkLive
                                t.finished -> Res.string.teamProjectTaskWork
                                else -> Res.string.teamProjectTaskWorkLog
                            }
                        ),
                    expanded = open,
                    onExpandedChange = { open = it },
                ) {
                    t.events.forEach { e -> KitText("${timelineWords(e.text)} · ${ageWords(e.atMs, state.nowMs).orEmpty()}", KitTextRole.Body) }
                }
            }
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
        if (t.findings.isNotEmpty()) item(key = "findings") { Findings(t, state, actions, enabled, ignore) }
        t.merge?.let { m ->
            item(key = "merge") {
                KitMergeQueue(
                    title = stringResource(Res.string.teamProjectTaskMerge),
                    status = taskStatus(m.status),
                    state = taskState(m.status),
                    items = m.items.map { (reason, status) -> KitTeamItem(t.title, detail = reason.ifEmpty { null }, meta = taskStatus(status)) },
                    actions =
                        listOfNotNull(
                            KitAction(stringResource(Res.string.teamProjectTaskMergeRun), { actions.ask(TaskConfirm.Merge) }, enabled)
                                .takeIf { m.canMerge }
                        ),
                )
            }
        }
        items(t.mergeReceipts.size, key = { "mr:$it" }) { i ->
            val r = t.mergeReceipts[i]
            KitPhaseCard(
                title = stringResource(Res.string.teamProjectReceiptMerged, r.repoName),
                status = ageWords(r.atMs, state.nowMs).orEmpty(),
                state = KitTeamState.Done,
                summary = "${r.before.take(7)} → ${r.after.take(7)}",
            )
        }
        t.promote?.let { pr -> item(key = "promote") { Promote(pr, actions, enabled) } }
        items(t.promoteReceipts.size, key = { "pr:$it" }) { i ->
            val r = t.promoteReceipts[i]
            KitPhaseCard(
                title = stringResource(Res.string.teamProjectTaskReceipt),
                status = ageWords(r.atMs, state.nowMs).orEmpty(),
                state = KitTeamState.Done,
                summary = "${r.before} → ${r.after}",
            )
        }
        item(key = "controls") { Controls(t, actions, enabled) }
        if (t.branch.isNotEmpty()) {
            item(key = "branch") {
                KitDetailsFold(
                    label = stringResource(Res.string.kitDetails),
                    copyLabel = stringResource(Res.string.kitCopyDetails),
                    text = LocalKitRedactor.current.details(t.branch),
                )
            }
        }
    }
}

@Composable
private fun askAction(
    a: TaskAskAction,
    ask: TaskAskUi,
    actions: TeamTaskActions,
    enabled: KitEnabled,
    answer: (TaskAskUi) -> Unit,
): KitAction =
    when (a) {
        TaskAskAction.Answer -> KitAction(
            stringResource(Res.string.teamProjectTaskAnswer),
            { answer(ask) },
            enabled,
            icon = KitIcons.send,
        )
        TaskAskAction.Resume -> KitAction(
            stringResource(Res.string.teamProjectInterruptedResume),
            actions::resume,
            enabled,
            icon = KitIcons.play,
        )
        TaskAskAction.Restart -> KitAction(
            stringResource(Res.string.teamProjectTaskRestart),
            actions::restart,
            enabled,
            icon = KitIcons.restart,
        )
        TaskAskAction.ReviewFindings -> KitAction(
            stringResource(Res.string.teamProjectTaskReviewFindings),
            actions::selectOpenFindings,
            enabled,
            icon = KitIcons.checks,
        )
        is TaskAskAction.Resolve ->
            KitAction(
                stringResource(
                    when (a.how) {
                        "agent" -> Res.string.teamProjectTaskResolveAgent
                        "manual" -> Res.string.teamProjectTaskResolveManually
                        else -> Res.string.teamProjectTaskRecheckResolution
                    }
                ),
                { actions.resolve(a.mergeItemId, a.how) },
                enabled,
            )
    }

/** Dart `_planCard`. */
@Composable
private fun Plan(plan: PlanUi, actions: TeamTaskActions, enabled: KitEnabled, move: (String, String) -> Unit) {
    KitPlanCard(
        title =
            plan.milestone?.let { stringResource(Res.string.teamProjectPlanFor, it) } ?: stringResource(Res.string.teamProjectPlanForProject),
        status =
            stringResource(
                Res.string.teamProjectPlanSummary,
                pluralStringResource(Res.plurals.teamProjectPlanSummary__phases, plan.phaseCount, plan.phaseCount),
                pluralStringResource(Res.plurals.teamProjectPlanSummary__tasks, plan.taskCount, plan.taskCount),
                pluralStringResource(Res.plurals.teamProjectPlanSummary__repos, plan.repoCount, plan.repoCount),
            ),
        state = KitTeamState.NeedsYou,
        summary = if (plan.serverFixed) stringResource(Res.string.teamProjectPlanServerFixed) else null,
        phases =
            plan.phases.map { ph ->
                KitPlanPhase(
                    title = stringResource(Res.string.teamProjectPlanPhase, ph.number, ph.title),
                    flagLabel = if (ph.risky) stringResource(Res.string.teamProjectPlanReviewPoint) else null,
                    tasks =
                        ph.tasks.map { task ->
                            KitPlanTask(
                                number = task.number,
                                title = task.title,
                                detail =
                                    listOfNotNull(
                                        task.repoName?.let { stringResource(Res.string.teamProjectPlanRepo, it) },
                                        task.after?.let { stringResource(Res.string.teamProjectPlanAfter, it) },
                                        pluralStringResource(Res.plurals.teamProjectPlanCriteria, task.criteria, task.criteria),
                                    ).joinToString(" · "),
                                who = listOfNotNull(task.roleName, task.serverName).joinToString(" · "),
                                onChangeWho = if (task.canMove) ({ move(task.taskId, task.serverId) }) else null,
                            )
                        },
                )
            },
        more = if (plan.more > 0) pluralStringResource(Res.plurals.teamProjectPlanMore, plan.more, plan.more) else null,
        primary =
            if (plan.canApprove) KitAction(stringResource(Res.string.teamProjectTaskApprovePlan), actions::approvePlan, enabled) else null,
        secondary = KitAction(stringResource(Res.string.teamProjectPlanNotYet), actions::back),
        actions = listOf(KitAction(stringResource(Res.string.teamProjectPlanEdit), actions::editPlan, enabled)),
    )
}

/** Dart's findings card: tick findings, Fix selected, Check again, Ignore one with a reason. */
@Composable
private fun Findings(t: TaskView, state: TeamTaskUi, actions: TeamTaskActions, enabled: KitEnabled, ignore: () -> Unit) {
    val open = t.findings.any { it.open }
    val parts =
        listOfNotNull(
            stringResource(Res.string.teamProjectFindingsCritical, t.critical).takeIf { t.critical > 0 },
            stringResource(Res.string.teamProjectFindingsMajor, t.major).takeIf { t.major > 0 },
            stringResource(Res.string.teamProjectFindingsMinor, t.minor).takeIf { t.minor > 0 },
        )
    KitFindingsCard(
        title =
            if (t.checkerName == null || parts.isEmpty()) stringResource(Res.string.teamProjectTaskFindings)
            else stringResource(Res.string.teamProjectFindingsTitle, t.checkerName, parts.joinToString(" · ")),
        status = stringResource(if (open) Res.string.teamProjectTaskOpenFindings else Res.string.teamProjectTaskFindingsAddressed),
        state = if (open && t.status == "review") KitTeamState.NeedsYou else KitTeamState.Done,
        findings =
            t.findings.map { f ->
                KitTeamFinding(
                    id = f.id,
                    title = f.text,
                    severityLabel =
                        stringResource(
                            when (f.severity) {
                                "critical" -> Res.string.teamProjectTaskCritical
                                "minor" -> Res.string.teamProjectTaskMinor
                                else -> Res.string.teamProjectTaskMajor
                            }
                        ),
                    severity =
                        when (f.severity) {
                            "critical" -> KitFindingSeverity.Critical
                            "minor" -> KitFindingSeverity.Minor
                            else -> KitFindingSeverity.Major
                        },
                    location = f.location.ifEmpty { null },
                    selected = f.id in state.selected,
                    onChanged = if (f.open) ({ on -> actions.toggleFinding(f.id, on) }) else null,
                )
            },
        primary =
            if (state.selected.isNotEmpty() && t.canVerify) KitAction(stringResource(Res.string.teamProjectTaskFix), actions::fixSelected, enabled)
            else null,
        actions =
            listOfNotNull(
                KitAction(
                    stringResource(Res.string.teamProjectTaskRecheck),
                    actions::recheck,
                    enabled,
                    icon = KitIcons.retry,
                ).takeIf { t.canVerify },
                KitAction(stringResource(Res.string.teamProjectTaskIgnore), ignore, enabled).takeIf { state.selected.size == 1 },
            ),
    )
}

/** Dart `_promoteCard`. */
@Composable
private fun Promote(pr: TaskPromoteUi, actions: TeamTaskActions, enabled: KitEnabled) {
    KitPromoteCard(
        title = stringResource(Res.string.teamProjectPromoteTitle),
        status = stringResource(Res.string.teamProjectPromoteStatus, pr.repoName),
        state = KitTeamState.NeedsYou,
        items =
            listOf(
                KitTeamItem(
                    pr.milestone?.let { stringResource(Res.string.teamProjectPromoteMilestone, it, pr.milestoneTitle) } ?: pr.projectName,
                    detail =
                        listOfNotNull(
                            stringResource(Res.string.teamProjectPromoteMerged, pr.merged, pr.tasks),
                            pr.days?.let { pluralStringResource(Res.plurals.teamProjectPromoteDays, it, it) },
                        ).joinToString(" · "),
                ),
                KitTeamItem(stringResource(Res.string.teamProjectPromoteChecks), detail = stringResource(Res.string.teamProjectPromoteChecksPassed)),
                KitTeamItem(
                    stringResource(Res.string.teamProjectPromoteReview),
                    detail =
                        if (pr.reviewed && pr.milestone != null) stringResource(Res.string.teamProjectPromoteAccepted, pr.milestone)
                        else stringResource(Res.string.teamProjectPromoteNoReview),
                ),
                KitTeamItem(
                    listOfNotNull(
                        pluralStringResource(Res.plurals.teamProjectPromoteChanges, pr.commits, pr.commits),
                        pr.files.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.teamProjectPromoteFiles, it, it) },
                    ).joinToString(" · "),
                    detail = "${pr.mainCommit.take(7)} → ${pr.devCommit.take(7)}",
                ),
            ),
        primary =
            if (pr.canPromote) KitAction(stringResource(Res.string.teamProjectTaskPromote), { actions.ask(TaskConfirm.Promote) }, enabled)
            else null,
        actions = listOf(KitAction(stringResource(Res.string.teamProjectPromoteNotYet), actions::back)),
    )
}

/** Dart's action block: the diff, Pause, Resume, Start again, Check this task, Stop task. */
@Composable
private fun Controls(t: TaskView, actions: TeamTaskActions, enabled: KitEnabled) {
    val c = t.controls
    var diffOpen by remember { mutableStateOf(false) }
    if (c.diff) {
        KitExpandRow(title = stringResource(Res.string.teamProjectTaskDiff), expanded = diffOpen, onExpandedChange = { diffOpen = it }) {
            KitMonoText(t.diff, Modifier.testTag("team-task-diff"))
        }
    }
    // C05: the task's acts carry their glyphs, so "Stop task" and the rest read as actions, not loose words.
    val tertiary =
        listOfNotNull(
            KitAction(
                stringResource(Res.string.teamProjectTaskPause),
                actions::pause,
                enabled,
                icon = KitIcons.pause,
            ).takeIf { c.pause },
            KitAction(
                stringResource(Res.string.teamProjectTaskResume),
                actions::resume,
                enabled,
                icon = KitIcons.play,
            ).takeIf { c.resume },
            KitAction(
                stringResource(Res.string.teamProjectTaskRestart),
                actions::restart,
                enabled,
                icon = KitIcons.restart,
            ).takeIf { c.restart },
            KitAction(
                stringResource(Res.string.teamProjectTaskVerify),
                actions::verify,
                enabled,
                icon = KitIcons.checks,
            ).takeIf { c.verify },
            KitAction(
                stringResource(Res.string.teamProjectTaskStop),
                { actions.ask(TaskConfirm.Stop) },
                enabled,
                icon = KitIcons.stopCircle,
                destructive = true,
            ).takeIf { c.stop },
        )
    KitActionBlock(Modifier.padding(horizontal = kitPageGutter()), tertiary = tertiary)
}

