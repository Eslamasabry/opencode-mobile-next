package io.github.eslamasabry.oc.feature.team.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.team.fleet.AgentState
import io.github.eslamasabry.oc.engine.team.fleet.FleetAgentAction
import io.github.eslamasabry.oc.engine.team.fleet.ReceiptStatus
import io.github.eslamasabry.oc.feature.team.presentation.FleetAgentActions
import io.github.eslamasabry.oc.feature.team.presentation.FleetAgentAsk
import io.github.eslamasabry.oc.feature.team.presentation.FleetAgentKind
import io.github.eslamasabry.oc.feature.team.presentation.FleetAgentNotice
import io.github.eslamasabry.oc.feature.team.presentation.FleetAgentSent
import io.github.eslamasabry.oc.feature.team.presentation.FleetAgentState
import io.github.eslamasabry.oc.feature.team.presentation.FleetAgentUi
import io.github.eslamasabry.oc.feature.team.presentation.FleetBlock
import io.github.eslamasabry.oc.feature.team.presentation.FleetFactKind
import io.github.eslamasabry.oc.feature.team.presentation.FleetHold
import io.github.eslamasabry.oc.feature.team.presentation.FleetOutputState
import io.github.eslamasabry.oc.feature.team.presentation.FleetStep
import io.github.eslamasabry.oc.feature.team.presentation.FleetStepKind
import io.github.eslamasabry.oc.feature.team.presentation.FleetUnavailable
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowState
import io.github.eslamasabry.oc.ui.kit.row.KitRowStateTone
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitReceipt
import io.github.eslamasabry.oc.ui.kit.state.KitReceiptState
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.state.KitTechnicalValue
import io.github.eslamasabry.oc.ui.words.res.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** How many of the newest lines of the live output the page shows; the rest is in Technical details. */
private const val OUTPUT_ROWS = 8

/**
 * One agent of a computer's team (Dart `AgentScreen`), built from kit parts in one list ordered by urgency: the question
 * that waits on the person, what went wrong with "Start fox again" beside it, where the agent stands (state, context,
 * session age, the newest step, the model), its controls (Pause, Nudge, Restart; Stop and Force stop last), the newest
 * control's receipt, its live output and Technical details. A control is drawn only when the computer takes it, and
 * a Stop or Restart asks first, naming the agent.
 */
@Composable
public fun FleetAgentScreen(state: FleetAgentState, actions: FleetAgentActions) {
    val ui = state.ui
    KitSheetHost(Modifier.fillMaxSize()) {
        KitScreen(
            topBar = {
                KitTopBar(
                    title = if (ui.kind == FleetAgentKind.Ready) agentTitleWords(ui.title) else ui.id,
                    subtitle =
                        when {
                            ui.kind == FleetAgentKind.Starting -> null
                            ui.work != null -> workSubtitle(ui)
                            else -> fleetSubtitle(ui.place, ui.notAnswering)
                        },
                    exit = KitTopBarExit.Back,
                    exitLabel = stringResource(Res.string.kitTopBarBack),
                    onExit = actions::back,
                    actions =
                        listOf(
                            KitLineAction(
                                stringResource(Res.string.teamUiRefresh),
                                actions::refresh,
                                icon = KitIcons.retry,
                                testTag = "fleet-agent-refresh",
                            ),
                        ),
                )
            },
            loading = ui.kind == FleetAgentKind.Starting,
            loadingLabel = stringResource(Res.string.teamUiCardLoading),
            width = KitScreenWidth.Reading,
        ) {
            when (ui.kind) {
                FleetAgentKind.Starting -> Unit
                FleetAgentKind.Failed -> FleetNotFound(ui.failure, "fleet-agent-failed", actions::refresh, actions::openSettings)
                FleetAgentKind.Missing ->
                    KitStateView(
                        KitStateKind.Missing,
                        stringResource(Res.string.teamUiAgentMissingTitle),
                        Modifier.testTag("fleet-agent-missing"),
                        body = stringResource(Res.string.teamUiAgentMissingHint),
                        icon = KitIcons.cloudOff,
                        primary = KitAction(stringResource(Res.string.teamUiRunBack), actions::back),
                    )
                FleetAgentKind.Ready -> Page(state, actions)
            }
        }
        Ask(state, actions)
    }
}

/** "On “Sync engine” · nothing blocking it": what it works on and what holds it up, said once in the bar. */
@Composable
private fun workSubtitle(ui: FleetAgentUi): String {
    val work = ui.work ?: return fleetSubtitle(ui.place, ui.notAnswering)
    val hold =
        when (val h = work.hold) {
            FleetHold.Blocked -> stringResource(Res.string.teamAgentWorkBlockedShort)
            is FleetHold.Steps -> pluralStringResource(Res.plurals.teamUiRunBlockedByDeps, h.open, h.open)
            FleetHold.Nothing -> stringResource(Res.string.teamAgentWorkUnblockedShort)
        }
    return stringResource(Res.string.teamAgentWorksOn, work.title) + FLEET_SEP + hold
}

@Composable
private fun Page(state: FleetAgentState, actions: FleetAgentActions) {
    val ui = state.ui
    LazyColumn(
        Modifier.fillMaxSize().testTag("fleet-agent"),
        contentPadding = PaddingValues(top = Space.S2.dp, bottom = Space.S6.dp),
        verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
    ) {
        if (ui.stale) item(key = "stale") { FleetStaleNotice(ui.readAtMs, state.nowMs, "fleet-agent-stale", actions::refresh) }
        // 1. What needs the person. Answering is the question's own page; the row says who waits.
        ui.gate?.let { gate ->
            item(key = "gate") {
                KitRowGroup {
                    KitRow(
                        title = gate.title,
                        supporting = stringResource(Res.string.teamAgentScreenGateIfIgnored, ui.name),
                        titleMaxLines = 2,
                        supportingMaxLines = 2,
                        state = KitRowState(null, KitRowStateTone.NeedsYou),
                        trailing = KitRowTrailing.Chevron,
                        modifier = Modifier.testTag("fleet-agent-gate"),
                        // The question's own page answers it (the Gate page).
                        onClick = { actions.openGate(gate.id) },
                    )
                }
            }
        }
        // 2. What went wrong.
        if (ui.notice != FleetAgentNotice.None) item(key = "notice") { Notice(ui, actions) }
        if (ui.recycling) {
            item(key = "recycling") {
                KitNotice(
                    stringResource(Res.string.teamAgentScreenRecyclingBody),
                    Modifier.testTag("fleet-agent-recycling"),
                    title = stringResource(Res.string.teamUiAgentRecyclingSoon),
                    icon = KitIcons.restart,
                )
            }
        }
        // 3. Where it stands.
        item(key = "status") { Status(ui, state.nowMs) }
        // 4. What the person can do about it, then the newest control's receipt.
        if (ui.controls.pause || ui.controls.nudge || ui.controls.restart || ui.controls.stop || ui.controls.forceStop) {
            item(key = "controls") { Controls(ui, actions) }
        }
        ui.sent?.let { sent -> item(key = "receipt") { Receipt(ui, sent, state.undoPause, actions) } }
        if (ui.controlsElsewhere) {
            item(key = "elsewhere") {
                KitNotice(stringResource(Res.string.teamAgentScreenControlsElsewhere, ui.name), Modifier.testTag("fleet-agent-controls-elsewhere"), icon = KitIcons.info)
            }
        }
        // 5. What it says and does.
        item(key = "output") { Output(ui) }
        // 6. The technical truth, last.
        item(key = "technical") { Technical(ui) }
    }
}

@Composable
private fun Notice(ui: FleetAgentUi, actions: FleetAgentActions) {
    val title = agentTitleWords(ui.title)
    val start = ui.controls.start
    val startLabel = stringResource(Res.string.teamAgentScreenResume, ui.name)
    when (ui.notice) {
        FleetAgentNotice.None -> Unit
        FleetAgentNotice.DidNotStart ->
            KitNotice(
                stringResource(Res.string.teamAgentDidNotStartBody),
                Modifier.testTag("fleet-agent-did-not-start"),
                title = stringResource(Res.string.teamAgentDidNotStartTitle),
                icon = KitIcons.warning,
                actions =
                    listOfNotNull(
                        start?.let { KitLineAction(stringResource(Res.string.teamAgentStartIt), { actions.start(it) }, enabled = !ui.busy, testTag = "fleet-agent-start") },
                    ),
            )
        FleetAgentNotice.StopStuck ->
            KitNotice(
                stringResource(Res.string.teamAgentStopStuckBody),
                Modifier.testTag("fleet-agent-stop-stuck"),
                title = stringResource(Res.string.teamAgentStopStuckTitle, title),
                tone = StatusTone.Failure,
                icon = KitIcons.warning,
                actions =
                    listOf(
                        KitLineAction(
                            stringResource(Res.string.teamAgentForceStop, ui.name),
                            { actions.ask(FleetAgentAsk.ForceStop) },
                            enabled = !ui.busy,
                            testTag = "fleet-agent-stop-stuck-force",
                        ),
                    ),
            )
        FleetAgentNotice.Unavailable ->
            KitNotice(
                stringResource(
                    Res.string.teamAgentUnavailableBody,
                    stringResource(if (ui.unavailable == FleetUnavailable.Model) Res.string.teamAgentUnavailableModelWords else Res.string.teamAgentUnavailableOtherWords),
                ),
                Modifier.testTag("fleet-agent-unavailable"),
                title = stringResource(Res.string.teamAgentUnavailableTitle, title),
                tone = StatusTone.Failure,
                icon = KitIcons.error,
            )
        FleetAgentNotice.Stopped, FleetAgentNotice.Crashed -> {
            val crashed = ui.notice == FleetAgentNotice.Crashed
            KitNotice(
                stringResource(if (crashed) Res.string.teamAgentScreenCrashedBody else Res.string.teamAgentScreenStoppedBody),
                Modifier.testTag("fleet-agent-stopped"),
                title = stringResource(if (crashed) Res.string.teamAgentScreenCrashedTitle else Res.string.teamAgentScreenStoppedTitle, ui.name),
                tone = if (crashed) StatusTone.Failure else StatusTone.Neutral,
                icon = if (crashed) KitIcons.error else KitIcons.stopCircle,
                actions = listOfNotNull(start?.let { KitLineAction(startLabel, { actions.start(it) }, enabled = !ui.busy, testTag = "fleet-agent-start") }),
            )
        }
    }
}

private fun AgentState.tone(): KitRowStateTone =
    when (this) {
        AgentState.Working -> KitRowStateTone.Running
        AgentState.Waiting, AgentState.Blocked -> KitRowStateTone.NeedsYou
        AgentState.Crashed -> KitRowStateTone.Failed
        AgentState.Idle, AgentState.Stopped, AgentState.Unknown -> KitRowStateTone.Quiet
    }

/** The status panel: the state with context and session age, the newest step, the model, and "No current work". */
@Composable
private fun Status(ui: FleetAgentUi, nowMs: Long) {
    val facts =
        listOfNotNull(
            ui.contextPercent?.let { stringResource(Res.string.teamUiAgentContextSemantics, it) },
            ui.sessionStartedAtMs?.let { stringResource(Res.string.teamUiAgentSessionAge, elapsedWords(it, nowMs)) },
        ).joinToString(FLEET_SEP)
    KitRowGroup(Modifier.testTag("fleet-agent-header")) {
        KitRow(
            title = if (ui.unavailable != null) stringResource(Res.string.teamUiHomeAgentStateStopped) else agentStateWord(ui.state, ui.paused),
            supporting = facts.ifEmpty { null },
            supportingMaxLines = 2,
            leading = KitRowLeading.Glyph(KitIcons.agent),
            state = KitRowState(null, ui.state.tone()),
            modifier = Modifier.testTag("fleet-agent-state"),
        )
        if (ui.lastStep != null || ui.lastActivityMs != null) {
            val active = ui.lastActivityMs?.let { stringResource(Res.string.teamAgentLastActive, elapsedWords(it, nowMs)) }
            KitRow(
                title = ui.lastStep?.let { stringResource(Res.string.teamAgentLastStep, stepWords(it)) } ?: active.orEmpty(),
                supporting = if (ui.lastStep != null) active else null,
                leading = KitRowLeading.Glyph(KitIcons.terminal),
                modifier = Modifier.testTag("fleet-agent-activity"),
            )
        }
        ui.model?.let { model ->
            KitRow(
                title = stringResource(Res.string.teamUiAgentLabelModel),
                supporting = ui.modelFrom?.let { stringResource(Res.string.teamAgentScreenModelFrom, model, it) } ?: model,
                leading = KitRowLeading.Glyph(KitIcons.model),
                modifier = Modifier.testTag("fleet-agent-model"),
            )
        }
        if (ui.work == null) {
            KitRow(
                title = stringResource(Res.string.teamUiHomeAgentNoWork),
                leading = KitRowLeading.Glyph(KitIcons.checklist),
                modifier = Modifier.testTag("fleet-agent-no-work"),
            )
        }
    }
}

/** Pause, Nudge and Restart, then Stop and Force stop last; each names the agent, and each waits while one is on its way. */
@Composable
private fun Controls(ui: FleetAgentUi, actions: FleetAgentActions) {
    val c = ui.controls
    val why = if (ui.busy) stringResource(Res.string.teamUiReceiptSent) else null
    val ending: (@Composable () -> Unit)? =
        if (c.stop || c.forceStop) {
            {
                if (c.stop) {
                    KitRow(
                        title = stringResource(Res.string.teamAgentScreenStop, ui.name),
                        leading = KitRowLeading.Glyph(KitIcons.stopCircle),
                        enabled = !ui.busy,
                        disabledReason = why,
                        modifier = Modifier.testTag("fleet-agent-control-stop"),
                        onClick = { actions.ask(FleetAgentAsk.Stop) },
                    )
                }
                if (c.forceStop) {
                    KitRow(
                        title = stringResource(Res.string.teamAgentForceStop, ui.name),
                        leading = KitRowLeading.Glyph(KitIcons.stopCircle),
                        enabled = !ui.busy,
                        disabledReason = why,
                        modifier = Modifier.testTag("fleet-agent-control-force-stop"),
                        onClick = { actions.ask(FleetAgentAsk.ForceStop) },
                    )
                }
            }
        } else {
            null
        }
    KitRowGroup(Modifier.testTag("fleet-agent-controls"), destructive = ending    ) {
        if (c.pause) {
            KitRow(
                title = stringResource(Res.string.teamAgentScreenPause, ui.name),
                leading = KitRowLeading.Glyph(KitIcons.pause),
                enabled = !ui.busy,
                disabledReason = why,
                modifier = Modifier.testTag("fleet-agent-control-pause"),
                onClick = actions::pause,
            )
        }
        if (c.nudge) {
            KitRow(
                title = stringResource(Res.string.teamAgentScreenNudge, ui.name),
                leading = KitRowLeading.Glyph(KitIcons.lightning),
                enabled = !ui.busy,
                disabledReason = why,
                modifier = Modifier.testTag("fleet-agent-control-nudge"),
                onClick = actions::nudge,
            )
        }
        if (c.restart) {
            KitRow(
                title = stringResource(Res.string.teamAgentScreenRestart, ui.name),
                leading = KitRowLeading.Glyph(KitIcons.restart),
                enabled = !ui.busy,
                disabledReason = why,
                modifier = Modifier.testTag("fleet-agent-control-restart"),
                onClick = { actions.ask(FleetAgentAsk.Restart) },
            )
        }
    }
}

/** The newest control's receipt: sent, done (Pause with Undo), not confirmed (Try again when nothing was stored), refused. */
@Composable
private fun Receipt(ui: FleetAgentUi, sent: FleetAgentSent, undo: Boolean, actions: FleetAgentActions) {
    val state =
        when (sent.status) {
            ReceiptStatus.Accepted -> KitReceiptState.Confirmed
            ReceiptStatus.Pending -> KitReceiptState.NotConfirmed
            // The host's own words are technical: they wait under Details.
            ReceiptStatus.Rejected -> KitReceiptState.Refused(null)
        }
    val paused = sent.action == FleetAgentAction.Pause && sent.status == ReceiptStatus.Accepted
    val until = remember(sent, undo) { if (paused && undo) TimeSource.Monotonic.markNow() + 8.seconds else null }
    KitReceipt(
        state,
        Modifier.testTag("fleet-agent-receipt"),
        onRetry = if (sent.retryable) actions::retry else null,
        label = if (paused) stringResource(Res.string.teamAgentScreenPaused, ui.name) else null,
        onUndo = if (until != null) actions::undoPause else null,
        undoUntil = until,
    )
}

@Composable
private fun stepWords(step: FleetStep): String =
    when (step.kind) {
        FleetStepKind.Command -> stringResource(Res.string.teamAgentStepCommand)
        FleetStepKind.Test -> stringResource(Res.string.teamAgentStepTest)
        FleetStepKind.Read -> stringResource(Res.string.teamAgentStepRead)
        FleetStepKind.Edit -> stringResource(Res.string.teamAgentStepEdit)
        FleetStepKind.Search -> stringResource(Res.string.teamAgentStepSearch)
        FleetStepKind.Other -> if (step.tool.isBlank()) stringResource(Res.string.teamAgentStepCommand) else stringResource(Res.string.teamAgentStepTool, step.tool)
    }

private fun FleetStepKind.icon() =
    when (this) {
        FleetStepKind.Read -> KitIcons.fileText
        FleetStepKind.Edit -> KitIcons.edit
        FleetStepKind.Search -> KitIcons.search
        FleetStepKind.Command, FleetStepKind.Test, FleetStepKind.Other -> KitIcons.terminal
    }

/** One line of the live output: what the agent said, or a step it took in plain words (the raw command waits under Details). */
private sealed interface OutputLine {
    data class Said(val text: String) : OutputLine

    data class Did(val step: FleetStep) : OutputLine
}

/** Live output (Dart `AgentOutputTail`): connecting, the newest of what it said and did, or that the session ended. */
@Composable
private fun Output(ui: FleetAgentUi) {
    val out = ui.output
    val lines = out.blocks.flatMap { b -> if (b is FleetBlock.Prose) listOf(OutputLine.Said(b.text)) else (b as FleetBlock.Steps).steps.map { OutputLine.Did(it) } }.takeLast(OUTPUT_ROWS)
    val label =
        if (out.state == FleetOutputState.Live) stringResource(Res.string.teamUiAgentOutputTitle) + FLEET_SEP + stringResource(Res.string.teamUiAgentOutputLive)
        else stringResource(Res.string.teamUiAgentOutputTitle)
    KitRowGroup(Modifier.testTag("fleet-agent-output"), label = label) {
        when (out.state) {
            FleetOutputState.Unavailable -> KitRow(title = stringResource(Res.string.teamUiAgentOutputUnavailable), titleMaxLines = 2, modifier = Modifier.testTag("fleet-agent-output-unavailable"))
            FleetOutputState.Connecting -> KitRow(title = stringResource(Res.string.teamUiAgentOutputConnecting), modifier = Modifier.testTag("fleet-agent-output-connecting"))
            FleetOutputState.Live, FleetOutputState.Ended -> {
                lines.forEach { line ->
                    when (line) {
                        is OutputLine.Said -> KitRow(title = line.text, titleMaxLines = 4, leading = KitRowLeading.Glyph(KitIcons.chat))
                        is OutputLine.Did -> KitRow(title = stepWords(line.step), leading = KitRowLeading.Glyph(line.step.kind.icon()))
                    }
                }
                if (out.state == FleetOutputState.Ended) {
                    KitRow(title = stringResource(Res.string.teamUiAgentOutputEnded), titleMaxLines = 2, modifier = Modifier.testTag("fleet-agent-output-ended"))
                }
            }
        }
    }
}

@Composable
private fun factLabel(kind: FleetFactKind): String =
    stringResource(
        when (kind) {
            FleetFactKind.AgentId -> Res.string.teamAgentScreenLabelId
            FleetFactKind.SessionId -> Res.string.teamUiAgentLabelSessionId
            FleetFactKind.SessionName -> Res.string.teamUiAgentLabelSessionName
            FleetFactKind.Provider -> Res.string.teamUiLabelProvider
            FleetFactKind.Model -> Res.string.teamUiAgentLabelModel
            FleetFactKind.Harness -> Res.string.teamUiAgentLabelHarness
            FleetFactKind.WorkDir -> Res.string.teamUiAgentLabelWorkDir
            FleetFactKind.Branch -> Res.string.teamUiAgentLabelBranch
            FleetFactKind.WorkId -> Res.string.teamUiGateLabelWorkId
            FleetFactKind.LastCommand -> Res.string.teamAgentLastCommandLabel
            FleetFactKind.HostSays -> Res.string.teamAgentUnavailableHostSays
            FleetFactKind.RawState -> Res.string.teamUiRunLabelRawState
            FleetFactKind.Pool -> Res.string.teamUiAgentLabelPool
            FleetFactKind.Pack -> Res.string.teamUiAgentLabelPack
        },
    )

/** Technical details: every value the computer reports about the agent once, then the newest raw output. */
@Composable
private fun Technical(ui: FleetAgentUi) {
    val values = ui.facts.map { KitTechnicalValue(factLabel(it.kind), it.value) }
    KitDetailsFold(
        label = stringResource(Res.string.teamUiTechnicalDetails),
        copyLabel = stringResource(Res.string.kitCopyDetails),
        modifier = Modifier.testTag("fleet-agent-technical"),
        values = values,
        text = ui.output.raw,
    )
}

/** Stop and Force stop name the agent and what may be lost; Restart says what it forgets. Nothing is sent before the answer. */
@Composable
private fun Ask(state: FleetAgentState, actions: FleetAgentActions) {
    val ui = state.ui
    val title = agentTitleWords(ui.title)
    val spec =
        when (state.asking) {
            FleetAgentAsk.Stop ->
                KitConfirmSpec(
                    title = stringResource(Res.string.teamUiControlStopConfirmTitle, title),
                    confirmLabel = stringResource(Res.string.teamAgentScreenStop, ui.name),
                    kind = KitConfirmKind.Stop,
                    body =
                        ui.stopTask?.let { stringResource(Res.string.teamAgentScreenStopBody, ui.name, it) }
                            ?: stringResource(Res.string.teamUiControlStopConfirmBody),
                    cancelLabel = stringResource(Res.string.teamChatStopKeepRunning),
                )
            FleetAgentAsk.ForceStop ->
                KitConfirmSpec(
                    title = stringResource(Res.string.teamAgentForceStopTitle, ui.name),
                    confirmLabel = stringResource(Res.string.teamAgentForceStop, ui.name),
                    kind = KitConfirmKind.Stop,
                    body = stringResource(Res.string.teamAgentForceStopBody, ui.name),
                    cancelLabel = stringResource(Res.string.teamChatStopKeepRunning),
                )
            FleetAgentAsk.Restart ->
                KitConfirmSpec(
                    title = stringResource(Res.string.teamUiControlRestartConfirmTitle, title),
                    confirmLabel = stringResource(Res.string.teamAgentScreenRestart, ui.name),
                    kind = KitConfirmKind.Neutral,
                    body = stringResource(Res.string.teamUiControlRestartConfirmBody),
                    cancelLabel = stringResource(Res.string.kitConfirmCancel),
                    icon = KitIcons.restart,
                )
            null -> null
        }
    KitConfirm(spec = spec, onConfirm = actions::confirm, onCancel = actions::cancelAsk)
}
