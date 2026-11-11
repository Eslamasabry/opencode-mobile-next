package io.github.eslamasabry.oc.feature.team.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.team.fleet.AgentState
import io.github.eslamasabry.oc.feature.team.presentation.FleetAgentRow
import io.github.eslamasabry.oc.feature.team.presentation.FleetAgentsActions
import io.github.eslamasabry.oc.feature.team.presentation.FleetAgentsState
import io.github.eslamasabry.oc.feature.team.presentation.FleetHomeKind
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowState
import io.github.eslamasabry.oc.ui.kit.row.KitRowStateTone
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * The Agents page of a computer's team (Dart `TeamAgentsScreen`, over the computer's own agents): one list ordered by
 * urgency, a worker busy now first ("Working · On “Sync engine” · 4 min"), then the rest ("Stopped · active 3 min ago").
 * A row opens the agent's page, where it is paused, nudged, restarted or stopped.
 */
@Composable
public fun FleetAgentsScreen(state: FleetAgentsState, actions: FleetAgentsActions) {
    val ui = state.ui
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.teamRolesTitle),
                subtitle = if (ui.kind == FleetHomeKind.Starting) null else fleetSubtitle(ui.place, ui.notAnswering),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
                actions =
                    listOf(
                        KitLineAction(
                            stringResource(Res.string.teamUiRefresh),
                            actions::refresh,
                            icon = KitIcons.retry,
                            testTag = "fleet-agents-refresh",
                        ),
                    ),
            )
        },
        loading = ui.kind == FleetHomeKind.Starting,
        loadingLabel = stringResource(Res.string.teamUiCardLoading),
        width = KitScreenWidth.List,
    ) {
        when (ui.kind) {
            FleetHomeKind.Starting -> Unit
            FleetHomeKind.Failed -> FleetNotFound(ui.failure, "fleet-agents-failed", actions::refresh, actions::openSettings)
            FleetHomeKind.Demo ->
                KitStateView(KitStateKind.Missing, stringResource(Res.string.teamUiRowNotAvailable), Modifier.testTag("fleet-agents-demo"))
            FleetHomeKind.Ready ->
                LazyColumn(
                    Modifier.fillMaxSize().testTag("fleet-agents"),
                    contentPadding = PaddingValues(top = Space.S2.dp, bottom = Space.S6.dp),
                    verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
                ) {
                    if (ui.stale) {
                        item(key = "stale") { FleetStaleNotice(ui.readAtMs, state.nowMs, "fleet-agents-stale", actions::refresh) }
                    }
                    item(key = "agents") {
                        if (ui.rows.isEmpty()) {
                            KitStateView(KitStateKind.Calm, stringResource(Res.string.teamFleetAgentsEmpty), Modifier.testTag("fleet-agents-empty"))
                        } else {
                            KitRowGroup {
                                ui.rows.forEach { row -> AgentRow(row, state.nowMs, actions) }
                            }
                        }
                    }
                }
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

/** The word for the state, then what it works on and for how long, or when it was last active. */
@Composable
private fun AgentRow(row: FleetAgentRow, nowMs: Long, actions: FleetAgentsActions) {
    val word = agentStateWord(row.state, row.paused)
    val line =
        if (row.state == AgentState.Working && row.task != null) {
            listOfNotNull(word, stringResource(Res.string.teamAgentWorksOn, row.task), row.sinceMs?.let { elapsedWords(it, nowMs) }).joinToString(FLEET_SEP)
        } else {
            listOfNotNull(word, row.lastActivityMs?.let { stringResource(Res.string.teamAgentLastActive, elapsedWords(it, nowMs)) }).joinToString(FLEET_SEP)
        }
    KitRow(
        title = agentTitleWords(row.title),
        supporting = line,
        titleMaxLines = 2,
        supportingMaxLines = 2,
        leading = KitRowLeading.Glyph(KitIcons.agent),
        trailing = KitRowTrailing.Chevron,
        state = KitRowState(null, row.state.tone()),
        modifier = Modifier.testTag("fleet-agent-${row.id}"),
        onClick = { actions.open(row.id) },
    )
}
