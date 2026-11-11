package io.github.eslamasabry.oc.feature.team.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.team.fleet.FleetFailure
import io.github.eslamasabry.oc.engine.team.fleet.RunState
import io.github.eslamasabry.oc.feature.team.presentation.FleetHomeKind
import io.github.eslamasabry.oc.feature.team.presentation.FleetHomeUi
import io.github.eslamasabry.oc.feature.team.presentation.FleetMark
import io.github.eslamasabry.oc.feature.team.presentation.FleetPlace
import io.github.eslamasabry.oc.feature.team.presentation.FleetTaskRow
import io.github.eslamasabry.oc.feature.team.presentation.TeamHomeActions
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowState
import io.github.eslamasabry.oc.ui.kit.row.KitRowStateTone
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The separator between the parts of one line (Dart `teamUsageSeparator`). */
private const val SEP = " · "

/** Dart `teamHostPhrase` for a computer's team: where it runs, then " · Not answering" when it does not answer. */
@Composable
internal fun computerSubtitle(ui: FleetHomeUi): String {
    val place =
        when (val p = ui.place) {
            FleetPlace.Phone -> stringResource(Res.string.teamUiHostPhrasePhone)
            FleetPlace.Computer -> stringResource(Res.string.teamUiHostPhraseComputer)
            is FleetPlace.Named -> stringResource(Res.string.teamUiHostPhraseComputerNamed, p.name)
        }
    return if (ui.notAnswering) place + SEP + stringResource(Res.string.teamUiHostPhraseNotAnswering) else place
}

/**
 * The team page for a team on a computer (Dart `TeamHomeScreen` over Gas City): one list of its tasks, most urgent
 * first, each question on its task's row; the agents and what it spent today; its projects. Answering, the agents'
 * controls, a task's own page and giving a task are the follow-up pages: until they exist the page says where to do
 * those (on the computer), never a dead button.
 */
@Composable
internal fun TeamComputerHome(ui: FleetHomeUi, nowMs: Long, actions: TeamHomeActions) {
    when (ui.kind) {
        FleetHomeKind.Starting -> Unit
        FleetHomeKind.Demo ->
            KitStateView(
                KitStateKind.Missing,
                stringResource(Res.string.teamUiRowNotAvailable),
                Modifier.testTag("team-computer-demo"),
                primary = KitAction(stringResource(Res.string.teamSettingsOpenTooltip), actions::openSettings),
            )
        FleetHomeKind.Failed -> Failed(ui.failure, actions)
        FleetHomeKind.Ready -> Ready(ui, nowMs, actions)
    }
}

/** Dart `teamScreenState`'s failed branches: why, in plain words, Try again, and Team settings (Turn off) beside it. */
@Composable
private fun Failed(failure: FleetFailure?, actions: TeamHomeActions) {
    val (title, body) =
        when (failure) {
            FleetFailure.NotGasCity -> Res.string.teamUiStateNotGasCityTitle to Res.string.teamUiCardErrorNotGasCity
            FleetFailure.CityNotRunning -> Res.string.teamUiStateStartingTitle to Res.string.teamUiCardErrorCityNotRunning
            FleetFailure.PlainHttpRefused -> Res.string.teamUiStatePlainHttpTitle to Res.string.teamUiCardErrorPlainHttp
            FleetFailure.Unreachable, null -> Res.string.teamUiStateUnreachableTitle to Res.string.teamUiCardErrorUnreachable
        }
    KitStateView(
        KitStateKind.Missing,
        stringResource(title),
        Modifier.testTag("team-computer-failed"),
        body = stringResource(body),
        icon = KitIcons.cloudOff,
        primary = KitAction(stringResource(Res.string.teamUiCardRetry), actions::refreshComputer),
        secondary = KitAction(stringResource(Res.string.teamSettingsOpenTooltip), actions::openSettings),
    )
}

@Composable
private fun runWord(row: FleetTaskRow): String =
    when (row.state) {
        RunState.Planning -> stringResource(Res.string.teamUiCardRunStatePlanning)
        RunState.Working -> stringResource(Res.string.teamUiCardRunStateWorking)
        RunState.Waiting -> stringResource(Res.string.teamUiCardRunStateWaiting)
        RunState.Blocked -> stringResource(Res.string.teamUiCardRunStateBlocked)
        RunState.Failed -> stringResource(Res.string.teamUiCardRunStateFailed)
        RunState.Cancelled -> stringResource(Res.string.teamUiCardRunStateCancelled)
        RunState.Completed -> stringResource(if (row.merged) Res.string.teamUiCardRunStateMerged else Res.string.teamUiCardRunStateCompleted)
        RunState.Unknown -> stringResource(Res.string.teamUiCardRunStateUnknown)
    }

/** Dart `teamTaskLine`, the part this page has: the state, the steps done, when it finished; a question's own words. */
@Composable
private fun taskLine(row: FleetTaskRow, nowMs: Long): String {
    val question = row.question
    if (question != null) return listOfNotNull(stringResource(Res.string.teamUiHomeRunNeedsYou), question, ageWords(row.questionAtMs, nowMs)).joinToString(SEP)
    if (row.needsYou && row.state != RunState.Failed) return stringResource(Res.string.teamUiHomeRunNeedsYou)
    val finished = row.state == RunState.Completed || row.state == RunState.Cancelled
    val age = ageWords(row.finishedAtMs, nowMs)
    if (finished && age != null) {
        return when {
            row.state == RunState.Cancelled -> stringResource(Res.string.teamUiTaskCancelledAgo, age)
            row.merged -> stringResource(Res.string.teamUiTaskMergedAgo, age)
            else -> stringResource(Res.string.teamUiTaskDoneAgo, age)
        }
    }
    val total = row.total
    val steps =
        if (!finished && total != null && total > 1) {
            pluralStringResource(Res.plurals.teamUiTaskSteps, total, total, (row.done ?: 0).coerceIn(0, total))
        } else {
            null
        }
    return listOfNotNull(runWord(row), steps).joinToString(SEP)
}

private fun FleetMark.tone(): KitRowStateTone =
    when (this) {
        FleetMark.NeedsYou -> KitRowStateTone.NeedsYou
        FleetMark.Working -> KitRowStateTone.Running
        FleetMark.Failed -> KitRowStateTone.Failed
        FleetMark.Waiting, FleetMark.Done, FleetMark.Stopped -> KitRowStateTone.Quiet
    }

@Composable
private fun Ready(ui: FleetHomeUi, nowMs: Long, actions: TeamHomeActions) {
    LazyColumn(
        Modifier.fillMaxSize().testTag("team-computer-home"),
        contentPadding = PaddingValues(top = Space.S2.dp, bottom = Space.S6.dp),
        verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
    ) {
        if (ui.stale) {
            item(key = "stale") {
                KitNotice(
                    ageWords(ui.readAtMs, nowMs)?.let { stringResource(Res.string.teamUiCardStale, it) }
                        ?: stringResource(Res.string.teamUiStateNotAnsweringTitle),
                    Modifier.testTag("team-computer-stale"),
                    actions = listOf(KitLineAction(stringResource(Res.string.teamUiCardRetry), actions::refreshComputer)),
                )
            }
        }
        // Answering from the phone is the Gate sheet's (not here yet): say where to answer, once, above the list.
        if (ui.needsYou) {
            item(key = "answer-where") {
                KitNotice(
                    stringResource(
                        if (ui.place == FleetPlace.Phone) Res.string.teamUiHomeGateAnswerOnPhone else Res.string.teamUiHomeGateAnswerOnComputer,
                    ),
                    Modifier.testTag("team-computer-answer-where"),
                )
            }
        }
        item(key = "tasks") {
            if (ui.rows.isEmpty() && ui.questions.isEmpty()) {
                KitStateView(
                    KitStateKind.Calm,
                    stringResource(Res.string.teamUiCardEmptyTitle),
                    Modifier.testTag("team-computer-empty"),
                    body = stringResource(Res.string.teamUiCardEmptyHint),
                )
            } else {
                KitRowGroup(Modifier.testTag("team-computer-tasks")) {
                    ui.questions.forEach { q ->
                        KitRow(
                            title = q.title,
                            supporting = listOfNotNull(stringResource(Res.string.teamUiHomeRunNeedsYou), ageWords(q.atMs, nowMs)).joinToString(SEP),
                            titleMaxLines = 2,
                            state = KitRowState(null, KitRowStateTone.NeedsYou),
                            modifier = Modifier.testTag("team-computer-gate-${q.id}"),
                        )
                    }
                    ui.rows.forEach { row ->
                        KitRow(
                            title = row.title,
                            supporting = taskLine(row, nowMs),
                            titleMaxLines = 2,
                            supportingMaxLines = 2,
                            state = KitRowState(null, row.mark.tone()),
                            modifier = Modifier.testTag("team-computer-run-${row.id}"),
                        )
                    }
                    if (ui.hiddenDone > 0) {
                        KitRow(
                            title = pluralStringResource(Res.plurals.teamUiHomeDoneMore, ui.hiddenDone, ui.hiddenDone),
                            leading = KitRowLeading.Glyph(KitIcons.chevronDown),
                            modifier = Modifier.testTag("team-computer-done-more"),
                            onClick = actions::showMoreDone,
                        )
                    }
                }
            }
        }
        if (ui.agents != null || ui.costToday != null) {
            item(key = "team") {
                KitRowGroup(Modifier.testTag("team-computer-team")) {
                    ui.agents?.let { a ->
                        KitRow(
                            title = stringResource(Res.string.teamUiCardAgentsSummary, a.total, a.working, a.waiting, a.idle, a.stopped),
                            titleMaxLines = 2,
                            leading = KitRowLeading.Glyph(KitIcons.agent),
                            modifier = Modifier.testTag("team-computer-agents"),
                        )
                    }
                    ui.costToday?.let { cost ->
                        KitRow(
                            title = stringResource(Res.string.teamUiUsageCostEstimated, todayCost(cost, null)),
                            leading = KitRowLeading.Glyph(KitIcons.usage),
                            modifier = Modifier.testTag("team-computer-cost"),
                        )
                    }
                }
            }
        }
        if (ui.projects.isNotEmpty()) {
            item(key = "projects") {
                KitRowGroup(Modifier.testTag("team-computer-projects"), label = stringResource(Res.string.teamHomeProjectsTitle)) {
                    ui.projects.forEach { p ->
                        KitRow(
                            title = p.name,
                            supporting = stringResource(if (p.paused) Res.string.teamRigStatePaused else Res.string.teamRigStateActive),
                            leading = KitRowLeading.Glyph(KitIcons.projects),
                            modifier = Modifier.testTag("team-computer-project-${p.id}"),
                        )
                    }
                }
            }
        }
    }
}
