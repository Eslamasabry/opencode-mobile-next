package io.github.eslamasabry.oc.feature.team.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.team.TeamPhase
import io.github.eslamasabry.oc.feature.team.presentation.BoardColumn
import io.github.eslamasabry.oc.feature.team.presentation.FilterChoice
import io.github.eslamasabry.oc.feature.team.presentation.TaskMark
import io.github.eslamasabry.oc.feature.team.presentation.TeamBoardActions
import io.github.eslamasabry.oc.feature.team.presentation.TeamBoardUi
import io.github.eslamasabry.oc.feature.team.presentation.TeamRules
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.choice.KitSegment
import io.github.eslamasabry.oc.ui.kit.choice.KitSegmented
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.modal.KitMenu
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateSize
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.team.KitBoardCard
import io.github.eslamasabry.oc.ui.kit.team.KitBoardColumn
import io.github.eslamasabry.oc.ui.kit.team.KitBoardLane
import io.github.eslamasabry.oc.ui.kit.team.KitBoardLanes
import io.github.eslamasabry.oc.ui.kit.team.KitTaskCard
import io.github.eslamasabry.oc.ui.kit.team.KitWorkGraph
import io.github.eslamasabry.oc.ui.kit.team.KitWorkGraphMark
import io.github.eslamasabry.oc.ui.kit.team.KitWorkGraphNode
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * One project's board (Dart `TeamProjectBoard`): Board or Dependencies, filtered by milestone, repo and server; the
 * board opens on the first lane with work. Opening a task's conversation arrives with the team conversation slice.
 */
@Composable
public fun TeamBoardScreen(state: TeamBoardUi, actions: TeamBoardActions) {
    val board = stringResource(Res.string.teamProjectBoard)
    val ready = state.phase == TeamPhase.Ready && state.projectName != null
    KitScreen(
        topBar = {
            KitTopBar(
                title = board,
                subtitle = state.projectName,
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        header =
            if (!ready) null
            else
                ({
                    val gutter = LocalKitTokens.current.gutter.dp
                    KitSegmented(
                        segments =
                            listOf(
                                KitSegment(false, board),
                                KitSegment(true, stringResource(Res.string.teamProjectGraph)),
                            ),
                        selected = state.graph,
                        semanticsLabel = board,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = gutter, vertical = Space.S2.dp),
                        onChange = actions::showGraph,
                    )
                    Filter(stringResource(Res.string.teamProjectMilestoneFilter), state.milestones, state.milestone, actions::filterMilestone)
                    Filter(stringResource(Res.string.teamProjectRepoFilter), state.repos, state.repo, actions::filterRepo)
                    Filter(stringResource(Res.string.teamProjectServerFilter), state.servers, state.server, actions::filterServer)
                }),
        loading = state.phase == TeamPhase.Loading,
        loadingLabel = stringResource(Res.string.teamProjectLoad),
    ) {
        when {
            state.phase == TeamPhase.Loading -> Unit
            !ready -> KitStateView(KitStateKind.Empty, stringResource(Res.string.teamProjectSelect))
            state.graph -> Graph(state)
            else -> Lanes(state, actions)
        }
    }
}

/** Dart `KitPickerRow`: the filter's name, its choice, and the choices in a menu ("Everything" first). */
@Composable
private fun Filter(title: String, choices: List<FilterChoice>, selected: String?, onSelected: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val all = stringResource(Res.string.teamProjectAll)
    val options = listOf(FilterChoice(null, all)) + choices
    KitMenu(
        expanded = open,
        items = options.map { c -> KitMenuItem(c.title, { onSelected(c.id) }, checked = c.id == selected) },
        onDismissRequest = { open = false },
        title = title,
    ) {
        KitRow(
            title = title,
            trailing = KitRowTrailing.Value(options.firstOrNull { it.id == selected }?.title ?: all),
            onClick = { open = true },
        )
    }
}

@Composable
private fun Lanes(state: TeamBoardUi, actions: TeamBoardActions) {
    val columns = BoardColumn.entries
    val noTasks = stringResource(Res.string.teamProjectNoTasks)
    KitBoardLanes(
        columns = columns.map { c -> KitBoardColumn(columnWord(c), state.cards.count { it.column == c }) },
        selected = state.column.ordinal,
        onSelected = { actions.selectColumn(columns[it]) },
        modifier = Modifier.fillMaxSize(),
    ) { index ->
        KitBoardLane(
            cards =
                state.cards
                    .filter { it.column == columns[index] }
                    .map { card -> KitBoardCard(card.id) { KitTaskCard(card.title, card.mark.kit(), onOpen = {}) } },
            empty = { KitStateView(KitStateKind.Empty, noTasks, size = KitStateSize.Inline) },
        )
    }
}

@Composable
private fun Graph(state: TeamBoardUi) {
    val nodes =
        state.cards.map { card ->
            KitWorkGraphNode(
                id = card.id,
                title = card.title,
                mark =
                    when (card.mark) {
                        TaskMark.Waiting -> KitWorkGraphMark.Waiting
                        TaskMark.Working -> KitWorkGraphMark.Working
                        TaskMark.Done -> KitWorkGraphMark.Done
                        TaskMark.Failed -> KitWorkGraphMark.Failed
                        TaskMark.NeedsYou -> KitWorkGraphMark.NeedsYou
                    },
                word = statusWord(TeamRules.word(card.status)),
                dependsOn = card.dependsOn,
            )
        }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = Space.S2.dp)) {
        KitWorkGraph(nodes = nodes, onOpen = {}, emptyText = stringResource(Res.string.teamProjectNoTasks))
    }
}
