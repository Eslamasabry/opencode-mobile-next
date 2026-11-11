package io.github.eslamasabry.oc.feature.team.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.team.fleet.ReceiptStatus
import io.github.eslamasabry.oc.engine.team.fleet.RunState
import io.github.eslamasabry.oc.engine.team.fleet.WorkState
import io.github.eslamasabry.oc.feature.team.presentation.FleetRunActions
import io.github.eslamasabry.oc.feature.team.presentation.FleetRunFactKind
import io.github.eslamasabry.oc.feature.team.presentation.FleetRunKind
import io.github.eslamasabry.oc.feature.team.presentation.FleetRunState
import io.github.eslamasabry.oc.feature.team.presentation.FleetRunUi
import io.github.eslamasabry.oc.feature.team.presentation.FleetRunWord
import io.github.eslamasabry.oc.feature.team.presentation.FleetStage
import io.github.eslamasabry.oc.feature.team.presentation.FleetStepNode
import io.github.eslamasabry.oc.feature.team.presentation.FleetWorkFactKind
import io.github.eslamasabry.oc.feature.team.presentation.FleetWorkLink
import io.github.eslamasabry.oc.feature.team.presentation.FleetWorkUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.feature.team.fleet.FleetMergeActions
import io.github.eslamasabry.oc.feature.team.fleet.FleetMergeKind
import io.github.eslamasabry.oc.feature.team.fleet.FleetMergeSection
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowState
import io.github.eslamasabry.oc.ui.kit.row.KitRowStateTone
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitReceipt
import io.github.eslamasabry.oc.ui.kit.state.KitReceiptState
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateSize
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.state.KitTechnicalValue
import io.github.eslamasabry.oc.ui.kit.team.KitWorkGraph
import io.github.eslamasabry.oc.ui.kit.team.KitWorkGraphLayout
import io.github.eslamasabry.oc.ui.kit.team.KitWorkGraphMark
import io.github.eslamasabry.oc.ui.kit.team.KitWorkGraphNode
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * One task of a computer's team (Dart Task details + Work sheet, as a page): where the task stands (its state, the steps
 * done, how long), the four stages, its steps as a dependency graph where each opens its work sheet, Stop task, and
 * Technical details last. The questions that wait on the person show as rows; answering them is the question's own page.
 */
@Composable
public fun FleetRunScreen(state: FleetRunState, actions: FleetRunActions, merge: FleetMergeActions? = null) {
    val ui = state.ui
    KitSheetHost(Modifier.fillMaxSize()) {
        KitScreen(
            topBar = {
                KitTopBar(
                    title = if (ui.kind == FleetRunKind.Ready) ui.title else ui.id,
                    subtitle = if (ui.kind == FleetRunKind.Starting) null else fleetSubtitle(ui.place, ui.notAnswering),
                    exit = KitTopBarExit.Back,
                    exitLabel = stringResource(Res.string.kitTopBarBack),
                    onExit = actions::back,
                    actions =
                        listOf(
                            KitLineAction(
                                stringResource(Res.string.teamUiRefresh),
                                actions::refresh,
                                icon = KitIcons.retry,
                                testTag = "fleet-run-refresh",
                            ),
                        ),
                )
            },
            loading = ui.kind == FleetRunKind.Starting,
            loadingLabel = stringResource(Res.string.teamUiCardLoading),
            width = KitScreenWidth.Reading,
        ) {
            when (ui.kind) {
                FleetRunKind.Starting -> Unit
                FleetRunKind.Failed -> FleetNotFound(ui.failure, "fleet-run-failed", actions::refresh, actions::openSettings)
                FleetRunKind.Missing ->
                    KitStateView(
                        KitStateKind.Missing,
                        stringResource(Res.string.teamUiRunMissingTitle),
                        Modifier.testTag("fleet-run-missing"),
                        body = stringResource(Res.string.teamUiRunMissingHint),
                        icon = KitIcons.cloudOff,
                        primary = KitAction(stringResource(Res.string.teamUiRunBack), actions::back),
                    )
                FleetRunKind.Ready -> Page(state, actions, merge)
            }
        }
        KitConfirm(
            spec =
                if (state.confirmingStop) {
                    KitConfirmSpec(
                        title = stringResource(Res.string.teamChatStopConfirmTitle),
                        confirmLabel = stringResource(Res.string.teamChatStopTask),
                        kind = KitConfirmKind.Stop,
                        body = stringResource(Res.string.teamChatStopConfirmBody, ui.title),
                        cancelLabel = stringResource(Res.string.teamChatStopKeepRunning),
                    )
                } else {
                    null
                },
            onConfirm = actions::confirmStop,
            onCancel = actions::cancelStop,
        )
        WorkSheet(state.work, state.nowMs, actions)
    }
}

@Composable
private fun runWord(word: FleetRunWord): String =
    stringResource(
        when (word) {
            FleetRunWord.Planning -> Res.string.teamUiCardRunStatePlanning
            FleetRunWord.Working -> Res.string.teamUiCardRunStateWorking
            FleetRunWord.Waiting -> Res.string.teamUiCardRunStateWaiting
            FleetRunWord.Blocked -> Res.string.teamUiCardRunStateBlocked
            FleetRunWord.Failed -> Res.string.teamUiCardRunStateFailed
            FleetRunWord.Cancelled -> Res.string.teamUiCardRunStateCancelled
            FleetRunWord.Completed -> Res.string.teamUiCardRunStateCompleted
            FleetRunWord.Merged -> Res.string.teamUiCardRunStateMerged
            FleetRunWord.WaitingMerge -> Res.string.teamUiCardRunStateWaitingMerge
            FleetRunWord.Unknown -> Res.string.teamUiCardRunStateUnknown
        },
    )

@Composable
private fun stageWord(stage: FleetStage): String =
    stringResource(
        when (stage) {
            FleetStage.Waiting -> Res.string.teamUiRunStageWaiting
            FleetStage.Working -> Res.string.teamUiRunStageWorking
            FleetStage.Reviewing -> Res.string.teamUiRunStageReviewing
            FleetStage.Done -> Res.string.teamUiRunStageDone
        },
    )

/** Dart `KitSince.durationWords`: minutes, hours and minutes, or days. */
@Composable
private fun durationWords(ms: Long): String {
    val minutes = (ms / 60_000).toInt()
    return when {
        minutes < 60 -> stringResource(Res.string.teamUiRunElapsedMinutes, minutes)
        minutes < 24 * 60 -> stringResource(Res.string.teamUiRunElapsedHours, minutes / 60, minutes % 60)
        else -> stringResource(Res.string.teamUiRunElapsedDays, minutes / (24 * 60))
    }
}

/** Dart `teamWorkStateWord`. */
@Composable
internal fun workStateWord(state: WorkState): String =
    stringResource(
        when (state) {
            WorkState.Queued -> Res.string.teamUiWorkStateQueued
            WorkState.Ready -> Res.string.teamUiWorkStateReady
            WorkState.Working -> Res.string.teamUiWorkStateWorking
            WorkState.Waiting -> Res.string.teamUiWorkStateWaiting
            WorkState.Blocked -> Res.string.teamUiWorkStateBlocked
            WorkState.NeedsInput -> Res.string.teamUiWorkStateNeedsInput
            WorkState.Review -> Res.string.teamUiWorkStateReview
            WorkState.Failed -> Res.string.teamUiWorkStateFailed
            WorkState.Completed -> Res.string.teamUiWorkStateCompleted
            WorkState.Cancelled -> Res.string.teamUiWorkStateCancelled
            WorkState.Unknown -> Res.string.teamUiWorkStateUnknown
        },
    )

private fun RunState.tone(): KitRowStateTone =
    when (this) {
        RunState.Working, RunState.Planning -> KitRowStateTone.Running
        RunState.Failed -> KitRowStateTone.Failed
        else -> KitRowStateTone.Quiet
    }

/**
 * Dart `WorkGraphNode._mark`: a real "needs you" mark only for a state that truly needs the person, never for "blocked"
 * alone; the word always comes from [workStateWord], so the row says the true state.
 */
private fun WorkState.mark(): KitWorkGraphMark =
    when (this) {
        WorkState.NeedsInput -> KitWorkGraphMark.NeedsYou
        WorkState.Completed -> KitWorkGraphMark.Done
        WorkState.Failed -> KitWorkGraphMark.Failed
        WorkState.Cancelled -> KitWorkGraphMark.Stopped
        WorkState.Working, WorkState.Review -> KitWorkGraphMark.Working
        WorkState.Queued, WorkState.Ready, WorkState.Waiting, WorkState.Blocked, WorkState.Unknown -> KitWorkGraphMark.Waiting
    }

@Composable
private fun Page(state: FleetRunState, actions: FleetRunActions, merge: FleetMergeActions?) {
    val ui = state.ui
    LazyColumn(
        Modifier.fillMaxSize().testTag("fleet-run"),
        contentPadding = PaddingValues(top = Space.S2.dp, bottom = Space.S6.dp),
        verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
    ) {
        if (ui.stale) item(key = "stale") { FleetStaleNotice(ui.readAtMs, state.nowMs, "fleet-run-stale", actions::refresh) }
        if (ui.questions.isNotEmpty()) {
            item(key = "questions") {
                KitRowGroup(Modifier.testTag("fleet-run-questions")) {
                    ui.questions.forEach { q ->
                        KitRow(
                            title = q.title,
                            supporting = stringResource(Res.string.teamUiHomeRunNeedsYou),
                            titleMaxLines = 2,
                            state = KitRowState(null, KitRowStateTone.NeedsYou),
                            trailing = KitRowTrailing.Chevron,
                            modifier = Modifier.testTag("fleet-run-question-${q.id}"),
                            // The question's own page answers it (the Gate page).
                            onClick = { actions.openGate(q.id) },
                        )
                    }
                }
            }
        }
        item(key = "status") { Status(ui) }
        item(key = "steps") {
            Column(Modifier.testTag("fleet-run-steps")) {
                KitSectionLabel(stringResource(Res.string.teamUiRunStepsHeading))
                // The graph in rows: one step per row, its `needs` links in the gutter; each opens its work sheet.
                KitWorkGraph(
                    nodes = ui.steps.map { it.node() },
                    onOpen = actions::openStep,
                    layout = KitWorkGraphLayout.Rows,
                )
            }
        }
        ui.sent?.let { sent -> item(key = "receipt") { StopReceipt(sent.status, sent.retryable, actions) } }
        item(key = "technical") {
            val values =
                ui.facts.map { fact ->
                    KitTechnicalValue(
                        stringResource(
                            when (fact.kind) {
                                FleetRunFactKind.Kind -> Res.string.teamUiRunLabelKind
                                FleetRunFactKind.RunId -> Res.string.teamUiRunLabelId
                                FleetRunFactKind.Formula -> Res.string.teamUiRunLabelFormula
                                FleetRunFactKind.Project -> Res.string.teamUiRunLabelProject
                                FleetRunFactKind.RawState -> Res.string.teamUiRunLabelRawState
                                FleetRunFactKind.LastError -> Res.string.teamUiRunLabelLastError
                            },
                        ),
                        fact.value,
                    )
                }
            KitDetailsFold(
                label = stringResource(Res.string.teamUiTechnicalDetails),
                copyLabel = stringResource(Res.string.kitCopyDetails),
                modifier = Modifier.testTag("fleet-run-technical"),
                values = values,
            )
        }
        if (ui.canStop) {
            item(key = "stop") {
                KitRowGroup(
                    destructive = {
                        KitRow(
                            title = stringResource(Res.string.teamChatStopTask),
                            leading = KitRowLeading.Glyph(KitIcons.stopCircle),
                            enabled = !ui.busy,
                            disabledReason = if (ui.busy) stringResource(Res.string.teamUiReceiptSent) else null,
                            modifier = Modifier.testTag("fleet-run-stop"),
                            onClick = actions::askStop,
                        )
                    },
                ) {}
            }
        }
        // Approve, then merge into main: only once every step is done and the host's front has merge roles.
        if (merge != null && state.merge.kind != FleetMergeKind.Hidden) {
            item(key = "merge") {
                FleetMergeSection(state.merge, state.nowMs, merge, Modifier.testTag("fleet-run-merge"), onOpenWork = actions::openMergeWork)
            }
        }
    }
}

@Composable
private fun FleetStepNode.node(): KitWorkGraphNode =
    KitWorkGraphNode(
        id = id,
        title = title,
        mark = state.mark(),
        word = workStateWord(state),
        dependsOn = dependsOn,
        stuck = state == WorkState.Blocked || state == WorkState.NeedsInput || state == WorkState.Failed,
        open = state != WorkState.Completed && state != WorkState.Cancelled,
    )

/** "Working · 1 of 5 steps done · 3 h 12 min", then the stage the task is at. */
@Composable
private fun Status(ui: FleetRunUi) {
    val steps =
        if (ui.total > 1 && ui.state != RunState.Failed && ui.state != RunState.Cancelled) {
            pluralStringResource(Res.plurals.teamUiTaskSteps, ui.total, ui.total, ui.done.coerceIn(0, ui.total))
        } else {
            null
        }
    val line =
        listOfNotNull(
            steps,
            ui.elapsedMs?.let { durationWords(it) },
            ui.stage?.let { stringResource(Res.string.teamUiRunStageSemantics, it.ordinal + 1, stageWord(it)) },
        ).joinToString(FLEET_SEP)
    KitRowGroup(Modifier.testTag("fleet-run-status")) {
        KitRow(
            title = runWord(ui.word),
            supporting = line.ifEmpty { null },
            supportingMaxLines = 2,
            state = KitRowState(null, ui.state.tone()),
            modifier = Modifier.testTag("fleet-run-state"),
        )
    }
}

@Composable
private fun StopReceipt(status: ReceiptStatus, retryable: Boolean, actions: FleetRunActions) {
    KitReceipt(
        when (status) {
            ReceiptStatus.Accepted -> KitReceiptState.Confirmed
            ReceiptStatus.Pending -> KitReceiptState.NotConfirmed
            // The host's own words are technical: the page says it was not accepted and the computer decides.
            ReceiptStatus.Rejected -> KitReceiptState.Refused(null)
        },
        Modifier.testTag("fleet-run-receipt"),
        onRetry = if (retryable) actions::retryStop else null,
    )
}

/** The work sheet (Dart `WorkSheet`): a step's owner, description, what it needs and what waits on it, times, Details. */
@Composable
private fun WorkSheet(work: FleetWorkUi?, nowMs: Long, actions: FleetRunActions) {
    KitSheet(
        visible = work != null,
        onDismissRequest = actions::closeStep,
        title = work?.takeIf { it.found }?.title ?: stringResource(Res.string.teamWorkSheetMissingTitle),
        subtitle = work?.task,
        icon = KitIcons.checklist,
    ) {
        if (work == null) return@KitSheet
        if (!work.found) {
            KitStateView(
                KitStateKind.Missing,
                stringResource(Res.string.teamUiWorkSheetMissing),
                Modifier.testTag("fleet-work-missing"),
                body = stringResource(Res.string.teamWorkSheetMissingBody),
                icon = KitIcons.cloudOff,
                size = KitStateSize.Inline,
            )
            return@KitSheet
        }
        Column(Modifier.testTag("fleet-work-sheet"), verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
            KitRowGroup {
                val owner = work.owner
                KitRow(
                    title = owner?.name ?: stringResource(Res.string.teamUiWorkOwnerNone),
                    supporting = workStateWord(work.state),
                    leading = KitRowLeading.Glyph(KitIcons.person),
                    trailing = if (owner?.agentId != null) KitRowTrailing.Chevron else KitRowTrailing.None,
                    modifier = Modifier.testTag("fleet-work-owner"),
                    onClick = owner?.agentId?.let { id -> { actions.openAgent(id) } },
                )
            }
            work.description?.let { text ->
                KitRowGroup(label = stringResource(Res.string.teamUiWorkSheetDescription)) {
                    KitRow(title = text, titleMaxLines = 12, modifier = Modifier.testTag("fleet-work-description"))
                }
            }
            if (work.dependencies.isNotEmpty()) Links(Res.string.teamUiWorkSheetDependencies, "dependency", work.dependencies, actions)
            if (work.blocking.isNotEmpty()) Links(Res.string.teamUiWorkSheetBlocking, "blocking", work.blocking, actions)
            KitRowGroup(label = stringResource(Res.string.teamUiWorkSheetTimestamps)) {
                if (work.createdAtMs == null && work.updatedAtMs == null) {
                    KitRow(title = stringResource(Res.string.teamUiWorkSheetNoTimestamps), titleMaxLines = 2)
                }
                work.createdAtMs?.let { KitRow(title = stringResource(Res.string.teamUiWorkSheetCreated), supporting = ageWords(it, nowMs), modifier = Modifier.testTag("fleet-work-created")) }
                work.updatedAtMs?.let { KitRow(title = stringResource(Res.string.teamUiWorkSheetUpdated), supporting = ageWords(it, nowMs), modifier = Modifier.testTag("fleet-work-updated")) }
            }
            KitDetailsFold(
                label = stringResource(Res.string.teamUiTechnicalDetails),
                copyLabel = stringResource(Res.string.kitCopyDetails),
                modifier = Modifier.testTag("fleet-work-technical"),
                values =
                    work.facts.map { fact ->
                        KitTechnicalValue(
                            stringResource(
                                when (fact.kind) {
                                    FleetWorkFactKind.Branch -> Res.string.teamUiWorkSheetBranch
                                    FleetWorkFactKind.Id -> Res.string.teamUiWorkLabelId
                                    FleetWorkFactKind.RawState -> Res.string.teamUiWorkLabelRawState
                                    FleetWorkFactKind.Run -> Res.string.teamUiWorkLabelRun
                                    FleetWorkFactKind.Parent -> Res.string.teamUiWorkLabelParent
                                    FleetWorkFactKind.Project -> Res.string.teamUiWorkLabelProject
                                    FleetWorkFactKind.Assignee -> Res.string.teamUiWorkLabelAssignee
                                    FleetWorkFactKind.Session -> Res.string.teamUiWorkLabelSession
                                    FleetWorkFactKind.Labels -> Res.string.teamUiWorkLabelLabels
                                    FleetWorkFactKind.DependsOn -> Res.string.teamUiWorkLabelDependsOn
                                    FleetWorkFactKind.ClosedReason -> Res.string.teamUiWorkLabelClosedReason
                                },
                            ),
                            fact.value,
                        )
                    },
            )
        }
    }
}

/** What a step depends on, or what waits on it: each row opens that step's sheet in this one's place. */
@Composable
private fun Links(label: org.jetbrains.compose.resources.StringResource, tag: String, links: List<FleetWorkLink>, actions: FleetRunActions) {
    KitRowGroup(label = stringResource(label)) {
        links.forEach { link ->
            val listed = link.state != null
            KitRow(
                title = link.title ?: link.id,
                supporting = if (link.state != null) workStateWord(link.state!!) else stringResource(Res.string.teamWorkSheetNotOnHost),
                titleMaxLines = 2,
                trailing = if (listed) KitRowTrailing.Chevron else KitRowTrailing.None,
                modifier = Modifier.testTag("fleet-work-$tag-${link.id}"),
                onClick = if (listed) ({ actions.openStep(link.id) }) else null,
            )
        }
    }
}
