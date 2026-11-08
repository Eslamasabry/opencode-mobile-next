package io.github.eslamasabry.oc.feature.team.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.team.TeamPhase
import io.github.eslamasabry.oc.feature.team.presentation.HomeRow
import io.github.eslamasabry.oc.feature.team.presentation.TeamHomeActions
import io.github.eslamasabry.oc.feature.team.presentation.TeamHomeUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.team.KitProjectRow
import io.github.eslamasabry.oc.ui.kit.team.KitTeamState
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * The team page (Dart `TeamPage`): the phone team's projects most urgent first, each project's open requests above
 * it; or why there are none (off, a computer's team, the phone's team not answering).
 */
@Composable
public fun TeamHomeScreen(state: TeamHomeUi, actions: TeamHomeActions) {
    val title = stringResource(Res.string.teamProjectHome)
    KitScreen(
        topBar = {
            KitTopBar(
                title = title,
                subtitle = if (state.demo) stringResource(Res.string.teamProjectDemoChip) else null,
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        loading = state.phase == TeamPhase.Loading || state.loading,
        loadingLabel = stringResource(Res.string.teamProjectLoad),
        width = KitScreenWidth.Reading,
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
            TeamPhase.Ready -> Projects(state, actions)
        }
    }
}

@Composable
private fun Projects(state: TeamHomeUi, actions: TeamHomeActions) {
    val stale = stringResource(Res.string.teamProjectTaskStale)
    if (state.rows.isEmpty() && !state.stale) {
        KitStateView(KitStateKind.Empty, stringResource(Res.string.teamProjectEmpty))
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = Space.S2.dp, bottom = Space.S6.dp),
        verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
    ) {
        if (state.stale) {
            item(key = "failure") {
                KitNotice(
                    stringResource(Res.string.teamProjectError),
                    actions = listOf(KitLineAction(stringResource(Res.string.teamProjectRetry), actions::retry)),
                )
            }
        }
        items(state.rows, key = { it.key }) { row ->
            when (row) {
                is HomeRow.Interrupted ->
                    KitRow(
                        title = stringResource(Res.string.teamProjectInterruptedRow, row.name),
                        onClick = { actions.resumeProject(row.projectId) },
                    )
                is HomeRow.Request ->
                    KitRow(
                        title = row.title,
                        supporting = row.projectName,
                        trailing = KitRowTrailing.Chevron,
                        onClick = { actions.openRequest(row.projectId, row.requestId) },
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
