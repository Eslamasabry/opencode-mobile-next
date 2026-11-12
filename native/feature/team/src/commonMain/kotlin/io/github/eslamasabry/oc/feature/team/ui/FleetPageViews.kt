package io.github.eslamasabry.oc.feature.team.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.engine.team.fleet.AgentState
import io.github.eslamasabry.oc.engine.team.fleet.FleetFailure
import io.github.eslamasabry.oc.feature.team.presentation.FleetAgentRole
import io.github.eslamasabry.oc.feature.team.presentation.FleetAgentTitle
import io.github.eslamasabry.oc.feature.team.presentation.FleetHomeUi
import io.github.eslamasabry.oc.feature.team.presentation.FleetLooksAfter
import io.github.eslamasabry.oc.feature.team.presentation.FleetPlace
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/** The separator between the parts of one line (Dart `teamUsageSeparator`). */
internal const val FLEET_SEP = " · "

/** The page's subtitle: where the team runs, then "Not answering" when it does not (Dart `teamHostPhrase`). */
@Composable
internal fun fleetSubtitle(place: FleetPlace, notAnswering: Boolean): String = computerSubtitle(FleetHomeUi(place = place, notAnswering = notAnswering))

/** Why the team could not be found, in plain words with Try again and Team settings beside it (Dart `teamScreenState`). */
@Composable
internal fun FleetNotFound(failure: FleetFailure?, tag: String, onRetry: () -> Unit, onSettings: () -> Unit) {
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
        Modifier.testTag(tag),
        body = stringResource(body),
        icon = KitIcons.cloudOff,
        primary = KitAction(stringResource(Res.string.teamUiCardRetry), onRetry),
        secondary = KitAction(stringResource(Res.string.teamSettingsOpenTooltip), onSettings),
    )
}

/** "Showing data from 2 min ago · host unreachable" with Try again, when the rows are the last ones read. */
@Composable
internal fun FleetStaleNotice(readAtMs: Long?, nowMs: Long, tag: String, onRetry: () -> Unit) {
    KitNotice(
        ageWords(readAtMs, nowMs)?.let { stringResource(Res.string.teamUiCardStale, it) } ?: stringResource(Res.string.teamUiStateNotAnsweringTitle),
        Modifier.testTag(tag),
        actions = listOf(KitLineAction(stringResource(Res.string.teamUiCardRetry), onRetry)),
    )
}

/** The agent's name for the person (Dart `teamAgentTitle`): its role, its own name, then what tells it from the others. */
@Composable
internal fun agentTitleWords(title: FleetAgentTitle): String {
    val role =
        stringResource(
            when (title.role) {
                FleetAgentRole.Worker -> Res.string.teamUiAgentRoleWorker
                FleetAgentRole.Reviewer -> Res.string.teamUiAgentRoleReviewer
                FleetAgentRole.Planner -> Res.string.teamUiAgentRolePlanner
                FleetAgentRole.Supervisor -> Res.string.teamUiAgentRoleSupervisor
                FleetAgentRole.Helper -> Res.string.teamUiAgentRoleHelper
                FleetAgentRole.Other -> Res.string.teamUiAgentRoleOther
            },
        )
    val base = title.nickname?.let { stringResource(Res.string.teamAgentTitle, it, role) } ?: role
    val project = title.project?.let { FLEET_SEP + it }.orEmpty()
    val looks =
        title.looksAfter
            ?.let {
                stringResource(
                    when (it) {
                        FleetLooksAfter.Team -> Res.string.teamAgentLooksAfterTeam
                        FleetLooksAfter.Watchdog -> Res.string.teamAgentLooksAfterWatchdog
                        FleetLooksAfter.Workers -> Res.string.teamAgentLooksAfterWorkers
                    },
                )
            }
            ?.let { FLEET_SEP + it }
            .orEmpty()
    val number = title.number?.let { " $it" }.orEmpty()
    return base + project + looks + number
}

/** Dart `teamAgentStateWord`; a stopped agent that was switched off on purpose says Paused. */
@Composable
internal fun agentStateWord(state: AgentState, paused: Boolean): String =
    stringResource(
        when (state) {
            AgentState.Working -> Res.string.teamUiHomeAgentStateWorking
            AgentState.Idle -> Res.string.teamUiHomeAgentStateIdle
            AgentState.Waiting -> Res.string.teamUiHomeAgentStateWaiting
            AgentState.Blocked -> Res.string.teamUiHomeAgentStateBlocked
            AgentState.Stopped -> if (paused) Res.string.teamProjectPaused else Res.string.teamUiHomeAgentStateStopped
            AgentState.Crashed -> Res.string.teamUiHomeAgentStateCrashed
            AgentState.Unknown -> Res.string.teamUiHomeAgentStateUnknown
        },
    )
