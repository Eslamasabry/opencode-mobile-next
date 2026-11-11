package io.github.eslamasabry.oc.feature.team.fleet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.choice.KitChoice
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceList
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateSize
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.state.KitTechnicalValue
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/** "Let the planner choose": the project choice with no project. */
private const val ANY_PROJECT = ""

/**
 * Start a run (Dart `StartRunSheet`), as a page of the team: **Objective**, **Project** ("Let the planner choose" by
 * default), **Supervision** (High / Balanced / Autonomous with their descriptions), the planner named on the primary
 * ("Send to the Mayor"). Where the planner is off but the host creates and assigns work, the **direct task** form
 * instead (a project, a task and details); where neither, why the team can't take a task and the way on (Wake the
 * planner, or Try again). What the person typed stays through a refusal; the host's words stay under Details.
 */
@Composable
public fun FleetStartRunSheet(ui: FleetStartUi, actions: FleetStartRunActions) {
    KitScreen(
        topBar = {
            KitTopBar(
                title =
                    stringResource(if (ui.mode == FleetStartMode.Blocked) Res.string.teamStartRunBlockedTitle else Res.string.teamUiStartRunTitle),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        loading = ui.mode == FleetStartMode.Waiting,
        loadingLabel = stringResource(Res.string.teamUiCardLoading),
        width = KitScreenWidth.Reading,
    ) {
        when (ui.mode) {
            FleetStartMode.Waiting -> Unit
            FleetStartMode.Planner -> PlannerForm(ui, actions)
            FleetStartMode.Direct -> DirectForm(ui, actions)
            FleetStartMode.Blocked -> Blocked(ui, actions)
        }
    }
}

@Composable
private fun Form(tag: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = Space.S2.dp, bottom = Space.S6.dp).testTag(tag),
        verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
    ) {
        content()
    }
}

@Composable
private fun PlannerForm(ui: FleetStartUi, actions: FleetStartRunActions) {
    val mine = ui.local
    val redactor = LocalKitRedactor.current
    val objective = remember { TextFieldState(mine.objective) }
    Form("fleet-start-form") {
        KitField(
            objective,
            stringResource(Res.string.teamUiStartRunObjectiveLabel),
            Modifier.testTag("fleet-start-objective"),
            kind = KitFieldKind.Multiline,
            placeholder = stringResource(Res.string.teamUiStartRunObjectiveHint),
            error = if (mine.showEmpty && mine.objective.isBlank()) stringResource(Res.string.teamUiStartRunObjectiveEmpty) else null,
            onTextChanged = actions::setObjective,
        )
        if (ui.projects.isNotEmpty()) {
            val label = stringResource(Res.string.teamUiStartRunProjectLabel)
            KitSectionLabel(label)
            KitChoiceList(
                choices = listOf(KitChoice(ANY_PROJECT, stringResource(Res.string.teamUiStartRunProjectAny))) + ui.projects.map { KitChoice(it.id, it.name) },
                selected = mine.projectId ?: ANY_PROJECT,
                semanticsLabel = label,
                modifier = Modifier.testTag("fleet-start-project"),
                onSelect = { actions.chooseProject(it.ifEmpty { null }) },
            )
        }
        val supervision = stringResource(Res.string.teamUiStartRunSupervisionLabel)
        KitSectionLabel(supervision)
        KitChoiceList(
            choices = FleetSupervision.entries.map { level -> supervisionWords(level).let { (title, hint) -> KitChoice(level, stringResource(title), stringResource(hint)) } },
            selected = mine.supervision,
            semanticsLabel = supervision,
            modifier = Modifier.testTag("fleet-start-supervision"),
            onSelect = actions::chooseSupervision,
        )
        if (mine.waking) KitNotice(stringResource(Res.string.teamUiStartRunWaking), Modifier.testTag("fleet-start-waking"), tone = StatusTone.Progress)
        mine.refused?.let { words ->
            KitNotice(
                stringResource(Res.string.teamStartRunRefusedKept),
                Modifier.testTag("fleet-start-refused"),
                title = stringResource(if (words.isEmpty()) Res.string.teamUiGateAnswerRejectedNoMessage else Res.string.teamUiStartRunRefused),
                tone = StatusTone.Failure,
                icon = KitIcons.error,
            )
            HostWords(words)
        }
        if (mine.unconfirmed) {
            KitNotice(stringResource(Res.string.teamUiReceiptUnconfirmed), Modifier.testTag("fleet-start-unconfirmed"), tone = StatusTone.Attention)
        }
        // The primary names the planner; its id waits under Details.
        KitActionBlock(
            Modifier.testTag("fleet-start-actions"),
            primary =
                KitAction(
                    stringResource(Res.string.teamUiStartRunSend, stringResource(Res.string.teamUiStartRunPlannerMayor)),
                    actions::sendToPlanner,
                    enabled = if (mine.sending) KitEnabled.Disabled(stringResource(Res.string.teamUiReceiptSent)) else KitEnabled.Enabled,
                    icon = KitIcons.send,
                    working = mine.sending,
                ),
        )
        ui.plannerId?.let { id ->
            KitDetailsFold(
                label = stringResource(Res.string.teamUiTechnicalDetails),
                copyLabel = stringResource(Res.string.kitCopyDetails),
                modifier = Modifier.testTag("fleet-start-technical"),
                values = listOf(KitTechnicalValue(stringResource(Res.string.teamUiStartRunPlannerLabel), redactor.details(id))),
            )
        }
    }
}

private fun supervisionWords(level: FleetSupervision) =
    when (level) {
        FleetSupervision.High -> Res.string.teamUiStartRunSupervisionHigh to Res.string.teamUiStartRunSupervisionHighHint
        FleetSupervision.Balanced -> Res.string.teamUiStartRunSupervisionBalanced to Res.string.teamUiStartRunSupervisionBalancedHint
        FleetSupervision.Autonomous -> Res.string.teamUiStartRunSupervisionAutonomous to Res.string.teamUiStartRunSupervisionAutonomousHint
    }

/** The host's own words: technical, redacted by the fold. */
@Composable
private fun HostWords(words: String) {
    if (words.isEmpty()) return
    KitDetailsFold(
        label = stringResource(Res.string.teamUiTechnicalDetails),
        copyLabel = stringResource(Res.string.kitCopyDetails),
        notes = listOf(stringResource(Res.string.teamDispatchHostWords)),
        text = LocalKitRedactor.current.details(words),
    )
}

/** The direct task: intro, project, task, details, the stage while it sends, what happened, Send to an agent. */
@Composable
private fun DirectForm(ui: FleetStartUi, actions: FleetStartRunActions) {
    val mine = ui.local
    val title = remember { TextFieldState(mine.task) }
    val details = remember { TextFieldState(mine.details) }
    Form("fleet-start-direct") {
        KitNotice(stringResource(Res.string.teamUiStartRunDirectIntro), icon = KitIcons.agent, liveRegion = false)
        val label = stringResource(Res.string.teamUiStartRunProjectLabel)
        KitSectionLabel(label)
        KitChoiceList(
            choices = ui.projects.map { KitChoice(it.id, it.name) },
            selected = ui.directProjectId,
            semanticsLabel = label,
            modifier = Modifier.testTag("fleet-start-direct-project"),
            onSelect = actions::chooseDirectProject,
        )
        KitField(
            title,
            stringResource(Res.string.teamUiStartRunDirectTitle),
            Modifier.testTag("fleet-start-direct-title"),
            placeholder = stringResource(Res.string.teamUiStartRunDirectTitleHint),
            error = if (mine.showTaskEmpty && mine.task.isBlank()) stringResource(Res.string.teamUiStartRunDirectTitleRequired) else null,
            onTextChanged = actions::setTask,
        )
        KitField(
            details,
            stringResource(Res.string.teamUiStartRunDirectDetails),
            Modifier.testTag("fleet-start-direct-details"),
            kind = KitFieldKind.Multiline,
            onTextChanged = actions::setDetails,
        )
        // What the host confirmed so far, never more.
        mine.stage?.let { stage ->
            KitNotice(
                stringResource(if (stage == FleetDirectStage.Creating) Res.string.teamDispatchCreating else Res.string.teamDispatchSending),
                Modifier.testTag("fleet-start-direct-stage"),
                tone = StatusTone.Progress,
            )
        }
        Outcome(mine)
        if (!ui.taskMade) {
            KitActionBlock(
                Modifier.testTag("fleet-start-direct-actions"),
                primary =
                    KitAction(
                        stringResource(Res.string.teamUiStartRunDirectSend),
                        actions::sendDirect,
                        enabled = if (mine.sending) KitEnabled.Disabled(stringResource(Res.string.teamUiReceiptSent)) else KitEnabled.Enabled,
                        icon = KitIcons.send,
                        working = mine.sending,
                    ),
            )
        }
    }
}

@Composable
private fun Outcome(mine: FleetStartLocal) {
    when (mine.outcome) {
        FleetDirectOutcome.None -> return
        FleetDirectOutcome.CreateRefused ->
            KitNotice(stringResource(Res.string.teamDispatchCreateRefused), Modifier.testTag("fleet-start-direct-error"), tone = StatusTone.Failure, icon = KitIcons.error)
        FleetDirectOutcome.CreateUnconfirmed ->
            KitNotice(
                stringResource(Res.string.teamDispatchCheckBoard),
                Modifier.testTag("fleet-start-direct-unconfirmed"),
                title = stringResource(Res.string.teamDispatchCreateUnconfirmed),
                tone = StatusTone.Attention,
            )
        FleetDirectOutcome.AssignRefused ->
            KitNotice(
                stringResource(Res.string.teamDispatchAssignRefusedHint),
                Modifier.testTag("fleet-start-direct-assign-refused"),
                title = stringResource(Res.string.teamDispatchAssignRefused),
                tone = StatusTone.Failure,
                icon = KitIcons.error,
            )
        FleetDirectOutcome.DispatchUnconfirmed ->
            KitNotice(
                stringResource(Res.string.teamDispatchCheckBoard),
                Modifier.testTag("fleet-start-direct-dispatch-unconfirmed"),
                title = stringResource(Res.string.teamDispatchDispatchUnconfirmed),
                tone = StatusTone.Attention,
            )
    }
    mine.directWords?.let { HostWords(it) }
}

/**
 * Why this team can't take a task, in plain words, and the way on: Wake the planner where the host takes agent
 * controls, else Try again. A state, not a form; once the team can take a task the page becomes the form by itself.
 */
@Composable
private fun Blocked(ui: FleetStartUi, actions: FleetStartRunActions) {
    val wake = ui.local.wake
    val why = ui.blocked ?: FleetBlockedWhy.NoPlanner
    val (title, body) =
        when (why) {
            FleetBlockedWhy.PlannerOff ->
                Res.string.teamStartRunPlannerOff to (if (ui.wakeable) Res.string.teamStartRunPlannerOffWakeBody else Res.string.teamStartRunPlannerOffHostBody)
            FleetBlockedWhy.NoPlanner -> Res.string.teamStartRunNoPlanner to Res.string.teamStartRunNoPlannerBody
            FleetBlockedWhy.NoProject -> Res.string.teamStartRunNoProject to Res.string.teamStartRunNoProjectBody
        }
    val busyReason = stringResource(Res.string.teamUiStartRunWaking)
    val check = KitAction(stringResource(Res.string.teamUiCardRetry), actions::check, enabled = if (wake.busy) KitEnabled.Disabled(stringResource(Res.string.teamUiCardLoading)) else KitEnabled.Enabled, icon = KitIcons.sync)
    // Waking is the one primary where the app can do it; after the host took it, Try again.
    val primary =
        if (ui.wakeable && !wake.asked) {
            KitAction(
                stringResource(Res.string.teamStartRunWake),
                actions::wake,
                enabled = if (wake.busy) KitEnabled.Disabled(busyReason) else KitEnabled.Enabled,
                icon = KitIcons.play,
                working = wake.busy,
            )
        } else {
            check
        }
    Form("fleet-start-blocked") {
        KitStateView(
            KitStateKind.Missing,
            stringResource(title),
            Modifier.testTag("fleet-start-blocked-state"),
            body = stringResource(body),
            icon = KitIcons.warning,
            primary = primary,
            size = KitStateSize.Inline,
        )
        if (wake.asked && wake.refused == null) {
            KitNotice(stringResource(Res.string.teamStartRunWakeAsked), Modifier.testTag("fleet-start-wake-asked"), tone = StatusTone.Progress)
        }
        if (wake.stillOff) {
            KitNotice(
                stringResource(if (why == FleetBlockedWhy.NoProject) Res.string.teamStartRunStillNoProject else Res.string.teamStartRunStillOff),
                Modifier.testTag("fleet-start-still-off"),
                icon = KitIcons.info,
            )
        }
        wake.refused?.let { words ->
            KitNotice(
                stringResource(Res.string.teamStartRunWakeRefusedNext),
                Modifier.testTag("fleet-start-wake-refused"),
                title = stringResource(Res.string.teamStartRunWakeRefused),
                tone = StatusTone.Failure,
                icon = KitIcons.error,
            )
            HostWords(words)
        }
    }
}
