package io.github.eslamasabry.oc.feature.team.ui

import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import io.github.eslamasabry.oc.feature.team.presentation.AskLabel
import io.github.eslamasabry.oc.feature.team.presentation.CostUi
import io.github.eslamasabry.oc.feature.team.presentation.EditorKind
import io.github.eslamasabry.oc.feature.team.presentation.GoalUi
import io.github.eslamasabry.oc.feature.team.presentation.LaneUi
import io.github.eslamasabry.oc.feature.team.presentation.MergeItemUi
import io.github.eslamasabry.oc.feature.team.presentation.NeedsYouUi
import io.github.eslamasabry.oc.feature.team.presentation.PlanFailedUi
import io.github.eslamasabry.oc.feature.team.presentation.ProjectConfirm
import io.github.eslamasabry.oc.feature.team.presentation.ProjectView
import io.github.eslamasabry.oc.feature.team.presentation.TeamProjectActions
import io.github.eslamasabry.oc.feature.team.presentation.TeamProjectUi
import io.github.eslamasabry.oc.feature.team.presentation.TeamRules
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextRole
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitConsequence
import io.github.eslamasabry.oc.ui.kit.modal.KitConsequenceMark
import io.github.eslamasabry.oc.ui.kit.modal.KitInputDialog
import io.github.eslamasabry.oc.ui.kit.modal.KitMenu
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowState
import io.github.eslamasabry.oc.ui.kit.row.KitRowStateTone
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.state.KitTechnicalValue
import io.github.eslamasabry.oc.ui.kit.team.KitDigest
import io.github.eslamasabry.oc.ui.kit.team.KitMergeQueue
import io.github.eslamasabry.oc.ui.kit.team.KitMilestoneRow
import io.github.eslamasabry.oc.ui.kit.team.KitPlanCard
import io.github.eslamasabry.oc.ui.kit.team.KitPromoteCard
import io.github.eslamasabry.oc.ui.kit.team.KitTeamItem
import io.github.eslamasabry.oc.ui.kit.team.KitTeamState
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * One project (Dart `TeamProjectOverview`): what needs the person, a failed plan's way on, "Since you were away", the
 * goal, the milestones, the lanes, the merge queue, receipts and promotion, the decisions, the project's pages, what
 * it costs, and Delete project.
 */
@Composable
public fun TeamProjectScreen(state: TeamProjectUi, actions: TeamProjectActions) {
    val p = state.project
    val stale = stringResource(Res.string.teamProjectTaskStale)
    var menuOpen by remember { mutableStateOf(false) }
    var answering by remember { mutableStateOf<NeedsYouUi?>(null) }
    var accepting by remember { mutableStateOf<String?>(null) }
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
                    actions =
                        if (p == null) emptyList()
                        else
                            listOf(
                                KitLineAction(
                                    stringResource(Res.string.teamProjectSpec),
                                    { actions.openEditor(EditorKind.Spec) },
                                    icon = KitIcons.edit,
                                    testTag = "team-project-spec",
                                )
                            ),
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
            edges = KitScreenEdges.Bleed,
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
        val accept = (p?.needsYou?.firstOrNull { (it.answer as? AskAnswer.AcceptMilestone)?.milestoneId == accepting }?.answer
            as? AskAnswer.AcceptMilestone)
        KitConfirm(
            spec =
                accepting?.let {
                    KitConfirmSpec(
                        title = stringResource(Res.string.teamProjectAcceptMilestoneConfirmTitle),
                        confirmLabel = stringResource(Res.string.teamProjectAccept),
                        kind = KitConfirmKind.Neutral,
                        body = accept?.text,
                        cancelLabel = stringResource(Res.string.kitConfirmCancel),
                    )
                },
            onConfirm = {
                accepting?.let { actions.acceptMilestone(it) }
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
        KitConfirm(spec = p?.let { confirmSpec(it, state.confirming) }, onConfirm = actions::confirm, onCancel = actions::cancelConfirm)
    }
}

/** Dart `_confirmDelete`, `confirmAndMergeToDev` and `confirmAndPromote`: each names what will happen. */
@Composable
private fun confirmSpec(p: ProjectView, ask: ProjectConfirm?): KitConfirmSpec? =
    when (ask) {
        null -> null
        ProjectConfirm.Delete ->
            KitConfirmSpec(
                title = stringResource(Res.string.teamProjectDeleteTitle, p.name),
                confirmLabel = stringResource(Res.string.teamProjectDeleteConfirm),
                kind = KitConfirmKind.Destructive,
                body = stringResource(Res.string.teamProjectDeleteBody, p.name),
                cancelLabel = stringResource(Res.string.kitConfirmCancel),
                consequenceItems =
                    listOf(
                        KitConsequence(stringResource(Res.string.teamProjectDeleteLostPlan), KitConsequenceMark.Lost),
                        KitConsequence(stringResource(Res.string.teamProjectDeleteLostRunning), KitConsequenceMark.Lost),
                        KitConsequence(stringResource(Res.string.teamProjectDeleteKept), KitConsequenceMark.Kept),
                    ),
            )
        is ProjectConfirm.Merge -> {
            val queue = p.merges.firstOrNull { it.repoId == ask.repoId }
            KitConfirmSpec(
                title = stringResource(Res.string.teamProjectMergeConfirmTitle),
                confirmLabel = stringResource(Res.string.teamProjectMergeNext),
                kind = KitConfirmKind.Neutral,
                body = stringResource(Res.string.teamProjectMergeConfirmBody),
                cancelLabel = stringResource(Res.string.kitConfirmCancel),
                consequences =
                    listOf(
                        pluralStringResource(Res.plurals.teamProjectMergeEffectDev, queue?.waiting ?: 0, queue?.repoName.orEmpty(), queue?.waiting ?: 0),
                        stringResource(Res.string.teamProjectMergeEffectMain),
                    ),
            )
        }
        is ProjectConfirm.Promote -> {
            val promote = p.promotions.firstOrNull { it.repoId == ask.repoId }
            KitConfirmSpec(
                title = stringResource(Res.string.teamProjectTaskPromoteConfirmTitle),
                confirmLabel = stringResource(Res.string.teamProjectTaskPromote),
                kind = KitConfirmKind.Neutral,
                body = stringResource(Res.string.teamProjectTaskPromoteBody),
                cancelLabel = stringResource(Res.string.kitConfirmCancel),
                consequences = listOfNotNull(promote?.let { "${it.repoName}: ${it.mainCommit} → ${it.devCommit}" }),
            )
        }
    }

/** What the needs-you cards and milestone rows may do while nothing else is on its way. */
private class NeedsYouHandlers(
    val enabled: KitEnabled,
    val answer: (NeedsYouUi) -> Unit,
    val accept: (String) -> Unit,
    val actions: TeamProjectActions,
)

/** Dart overview `menu`: Ask again (slow spec), Resume (interrupted), Pause or Resume while open, Stop (asks first). */
@Composable
private fun projectMenu(p: ProjectView, enabled: KitEnabled, actions: TeamProjectActions, close: () -> Unit): List<KitMenuItem> {
    fun item(label: String, destructive: Boolean = false, run: () -> Unit) =
        KitMenuItem(label, { close(); run() }, destructive = destructive, enabled = enabled)
    val resume = stringResource(Res.string.teamProjectResume)
    return listOfNotNull(
        item(stringResource(Res.string.teamProjectEditorAskAgain), run = actions::retryPlan).takeIf { p.menu.askAgain },
        item(resume, run = actions::resume).takeIf { p.menu.resumeInterrupted || p.menu.resume },
        item(stringResource(Res.string.teamProjectPause), run = actions::pause).takeIf { p.menu.pause },
        item(stringResource(Res.string.teamProjectStop), destructive = true, run = actions::askStop),
    )
}

/** Dart `_short`: a commit's first seven characters. */
private fun short(v: String): String = if (v.length > 7) v.take(7) else v

@Composable
private fun Overview(p: ProjectView, state: TeamProjectUi, actions: TeamProjectActions, asks: NeedsYouHandlers) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = Space.S2.dp, bottom = Space.S6.dp),
        verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
    ) {
        item(key = "lines") { ExecutionLines(p.blocked, null, p.resumeUnavailable) }
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
        items(p.needsYou, key = { "ask:${it.id}" }) { NeedsYou(it, asks) }
        p.planFailed?.let { f -> item(key = "planFailed") { PlanFailed(f, actions) } }
        p.digest?.let { d ->
            item(key = "digest") {
                KitDigest(
                    title = stringResource(Res.string.teamProjectDigest),
                    status = if (d.demo) stringResource(Res.string.teamProjectDemo) else "",
                    items = d.items.map { KitTeamItem(timelineWords(it.text), meta = ageWords(it.atMs, state.nowMs)) },
                    actions =
                        listOfNotNull(
                            KitAction(stringResource(Res.string.teamRefusalModelNotConfiguredAction), { actions.openEditor(EditorKind.Defaults) })
                                .takeIf { d.needsModel },
                            KitAction(stringResource(Res.string.teamUiControlResume), actions::resume, asks.enabled).takeIf { d.resume },
                            KitAction(stringResource(Res.string.teamProjectDigestRead), actions::markDigestRead, asks.enabled),
                        ),
                )
            }
        }
        item(key = "goal") { Goal(p, state.nowMs, actions) }
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
                KitSectionLabel(
                    stringResource(Res.string.teamProjectLanesTitle, p.busy, p.laneTotal),
                    trailing = {
                        KitButton(
                            stringResource(Res.string.teamProjectLanesChange),
                            { actions.openEditor(EditorKind.Settings) },
                            role = KitButtonRole.Tertiary,
                        )
                    },
                )
            }
            items(p.lanes, key = { "lane:${it.taskId}" }) { Lane(it, state.stale, state.nowMs) { actions.openTask(it.taskId) } }
            item(key = "laneNote") {
                val note =
                    if (p.singleLane) stringResource(Res.string.teamProjectLaneNoteSingle)
                    else pluralStringResource(Res.plurals.teamProjectLaneNoteParallel, p.laneTotal, p.laneTotal)
                KitText(if (p.demo) stringResource(Res.string.teamProjectLaneNoteDemo, note) else note, KitTextRole.Supporting)
            }
        }
        items(p.merges, key = { "merge:${it.repoId}" }) { q ->
            KitMergeQueue(
                title = "${stringResource(Res.string.teamProjectMerge)} · ${q.repoName}",
                status = "dev",
                items = q.items.map { i -> KitTeamItem(i.title, detail = mergeDetail(i), state = i.mark.kit(), onPressed = { actions.openTask(i.taskId) }) },
                actions =
                    listOfNotNull(
                        KitAction(stringResource(Res.string.teamProjectMergeNext), { actions.ask(ProjectConfirm.Merge(q.repoId)) }, asks.enabled)
                            .takeIf { q.canMerge }
                    ),
            )
        }
        items(p.receipts.size, key = { "receipt:$it" }) { i ->
            val r = p.receipts[i]
            KitRow(
                title =
                    if (r.merge) stringResource(Res.string.teamProjectReceiptMerged, r.repoName)
                    else stringResource(Res.string.teamProjectReceiptPromoted, r.repoName),
                supporting = "${short(r.before)} → ${short(r.after)}",
                trailing = KitRowTrailing.Value(ageWords(r.atMs, state.nowMs).orEmpty(), chevron = false),
            )
        }
        items(p.promotions, key = { "promote:${it.repoId}" }) { pr ->
            KitPromoteCard(
                title = stringResource(Res.string.teamProjectPromoteTitle),
                status = stringResource(Res.string.teamProjectPromoteStatus, pr.repoName),
                state = KitTeamState.NeedsYou,
                items = listOf(KitTeamItem(pr.repoName, detail = "${short(pr.mainCommit)} → ${short(pr.devCommit)}")),
                primary = KitAction(stringResource(Res.string.teamProjectTaskPromote), { actions.ask(ProjectConfirm.Promote(pr.repoId)) }, asks.enabled),
            )
        }
        if (p.decisions.isNotEmpty()) {
            item(key = "decisions") { KitSectionLabel(stringResource(Res.string.teamProjectDecisions)) }
            items(p.decisions.size, key = { "decision:$it" }) { i ->
                val d = p.decisions[i]
                val who = if (d.byPerson) stringResource(Res.string.teamProjectYou) else d.roleName.orEmpty()
                val age = ageWords(d.atMs, state.nowMs).orEmpty()
                KitRow(title = d.text, supporting = if (who.isEmpty()) age else stringResource(Res.string.teamProjectDecisionBy, who, age))
            }
        }
        item(key = "pages") { Pages(p, state.nowMs, actions) }
        item(key = "cost") { Cost(p, actions) }
        if (p.canDelete) {
            item(key = "delete") {
                KitRow(
                    title = stringResource(Res.string.teamProjectDelete, p.name),
                    modifier = Modifier.testTag("team-project-delete"),
                    leading = KitRowLeading.Glyph(KitIcons.delete),
                    titleMaxLines = 2,
                    onClick = { actions.ask(ProjectConfirm.Delete) },
                )
            }
        }
    }
}

/** Dart `_mergeWord`: a queued item waits for its turn or its checks, never for dependencies. */
@Composable
private fun mergeDetail(i: MergeItemUi): String =
    i.reason.ifEmpty {
        if (i.status == "queued") stringResource(if (i.checksPassed) Res.string.teamProjectMergeReady else Res.string.teamProjectMergeChecking)
        else statusWord(TeamRules.word(i.status))
    }

/** Dart `_planFailed`: why in plain words and what to do next; the engine's code under details. */
@Composable
private fun PlanFailed(f: PlanFailedUi, actions: TeamProjectActions) {
    val why = if (f.code.isEmpty()) null else reasonWords(f.code)
    KitNotice(
        why?.message ?: stringResource(Res.string.teamProjectFailed),
        Modifier.testTag("team-plan-failed"),
        title = stringResource(Res.string.teamProjectPlanFailedTitle),
        notes = listOfNotNull(why?.next ?: stringResource(Res.string.teamProjectApproveAgainNote).takeIf { !f.canRetry }),
        actions =
            listOfNotNull(
                KitLineAction(stringResource(Res.string.teamUiControlResume), actions::resume, testTag = "team-plan-failed-resume").takeIf { f.resumable },
                KitLineAction(stringResource(Res.string.teamRefusalModelNotConfiguredAction), { actions.openEditor(EditorKind.Defaults) })
                    .takeIf { f.needsModel },
                KitLineAction(stringResource(Res.string.teamProjectEditorAskAgain), actions::retryPlan, testTag = "team-plan-failed-retry")
                    .takeIf { f.canRetry && !f.resumable },
                KitLineAction(stringResource(Res.string.teamProjectApproveAgain), { actions.openEditor(EditorKind.Spec) }, testTag = "team-plan-failed-approve")
                    .takeIf { !f.resumable && !f.canRetry },
            ),
    )
    if (f.code.isNotEmpty()) {
        KitDetailsFold(
            label = stringResource(Res.string.kitDetails),
            copyLabel = stringResource(Res.string.kitCopyDetails),
            values = listOf(KitTechnicalValue(stringResource(Res.string.teamRefusalCode), LocalKitRedactor.current.details(f.code))),
        )
    }
}

/** Dart `_needsYou`: the ask, where it runs, what waits on it, and its answer. */
@Composable
private fun NeedsYou(r: NeedsYouUi, asks: NeedsYouHandlers) {
    val where =
        if (r.roleName != null && r.serverName != null) stringResource(Res.string.teamProjectRequestWhere, r.roleName, r.serverName)
        else null
    val label =
        stringResource(
            when (r.label) {
                AskLabel.Spec -> Res.string.teamProjectSpec
                AskLabel.Plan -> Res.string.teamProjectPlan
                AskLabel.Settings -> Res.string.teamProjectSettings
                AskLabel.Accept -> Res.string.teamProjectAccept
                AskLabel.Resume -> Res.string.teamProjectInterruptedResume
                AskLabel.Answer -> Res.string.teamProjectAnswer
                AskLabel.Review -> Res.string.teamProjectReview
            }
        )
    val run: () -> Unit = {
        when (val a = r.answer) {
            AskAnswer.Text -> asks.answer(r)
            is AskAnswer.Editor -> asks.actions.openEditor(a.kind)
            is AskAnswer.AcceptMilestone -> asks.accept(a.milestoneId)
            is AskAnswer.Resume -> asks.actions.resumeTask(a.taskId)
            is AskAnswer.Task -> asks.actions.openTask(a.taskId)
            AskAnswer.None -> Unit
        }
    }
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
        actions = if (r.actionable && r.answer != AskAnswer.None) listOf(KitAction(label, run, asks.enabled)) else emptyList(),
    )
}

/** Dart `_goalStatus` and the goal card's Open spec and Plan. */
@Composable
private fun Goal(p: ProjectView, nowMs: Long, actions: TeamProjectActions) {
    val goal: GoalUi = p.goal
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
    KitPlanCard(
        title = goal.goal,
        status = status,
        actions =
            listOfNotNull(
                KitAction(stringResource(Res.string.teamProjectOpenSpec), { actions.openEditor(EditorKind.Spec) }),
                KitAction(stringResource(Res.string.teamProjectPlan), { actions.openEditor(EditorKind.Plan) }).takeIf { p.showPlan },
            ),
    )
}

/** Dart overview Pages: Board, Timeline, Servers, Settings. */
@Composable
private fun Pages(p: ProjectView, nowMs: Long, actions: TeamProjectActions) {
    Column {
        KitSectionLabel(stringResource(Res.string.teamProjectPages))
        KitRow(
            title = stringResource(Res.string.teamProjectBoard),
            leading = KitRowLeading.Glyph(KitIcons.kanban),
            supporting =
                if (p.taskCount == 0) stringResource(Res.string.teamProjectBoardEmpty)
                else
                    stringResource(
                        Res.string.teamProjectBoardSummary,
                        pluralStringResource(Res.plurals.teamProjectBoardSummary__tasks, p.taskCount, p.taskCount),
                        pluralStringResource(Res.plurals.teamProjectBoardSummary__milestones, p.boardMilestones, p.boardMilestones),
                    ),
            trailing = KitRowTrailing.Chevron,
            onClick = { actions.openBoard(null) },
        )
        KitRow(
            title = stringResource(Res.string.teamProjectTimeline),
            leading = KitRowLeading.Glyph(KitIcons.timeline),
            supporting =
                if (p.timelineCount == 0) stringResource(Res.string.teamProjectTimelineEmpty)
                else
                    pluralStringResource(
                        Res.plurals.teamProjectTimelineSummary,
                        p.timelineCount,
                        p.timelineCount,
                        ageWords(p.timelineLastAtMs, nowMs).orEmpty(),
                    ),
            trailing = KitRowTrailing.Chevron,
            onClick = actions::openTimeline,
        )
        KitRow(
            title = stringResource(Res.string.teamProjectServers),
            leading = KitRowLeading.Glyph(KitIcons.server),
            supporting = pluralStringResource(Res.plurals.teamProjectServersSummary, p.serverCount, p.serverCount),
            trailing = KitRowTrailing.Chevron,
            onClick = actions::openServers,
        )
        KitRow(
            title = stringResource(Res.string.teamProjectSettings),
            leading = KitRowLeading.Glyph(KitIcons.settings),
            supporting =
                if (p.singleLane) stringResource(Res.string.teamProjectSettingsSummarySingle)
                else stringResource(Res.string.teamProjectSettingsSummaryParallel, p.maxLanes),
            trailing = KitRowTrailing.Chevron,
            onClick = { actions.openEditor(EditorKind.Settings) },
        )
    }
}

/** Dart `_costLine`. */
@Composable
private fun costLine(c: CostUi): String {
    if (!c.reported) return stringResource(Res.string.teamProjectCostNotReported)
    val today = todayCost(c.spentToday, c.daily)
    val all =
        if (c.total == null) stringResource(Res.string.teamProjectCostTotal, TeamRules.money(c.spent))
        else stringResource(Res.string.teamProjectCostTotalOf, TeamRules.money(c.spent), TeamRules.money(c.total))
    return listOfNotNull(today, all, stringResource(Res.string.teamProjectNoLimit).takeIf { c.daily == null && c.total == null })
        .joinToString(" · ")
}

@Composable
private fun Cost(p: ProjectView, actions: TeamProjectActions) {
    Column {
        KitSectionLabel(stringResource(Res.string.teamProjectCost))
        if (p.budgetWarning) KitNotice(stringResource(Res.string.teamProjectBudgetNear))
        KitRow(
            title = costLine(p.cost),
            leading = KitRowLeading.Glyph(KitIcons.usage),
            trailing = KitRowTrailing.Chevron,
            onClick = { actions.openEditor(EditorKind.Settings) },
        )
    }
}

/** Dart `_laneLine` and the lane row (`KitStatusMark` working/waiting). */
@Composable
private fun Lane(lane: LaneUi, stale: Boolean, nowMs: Long, open: () -> Unit) {
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
        trailing = KitRowTrailing.Chevron,
        onClick = open,
    )
}
