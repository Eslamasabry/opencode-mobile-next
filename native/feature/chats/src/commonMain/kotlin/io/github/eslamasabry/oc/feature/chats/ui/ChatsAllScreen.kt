package io.github.eslamasabry.oc.feature.chats.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.chats.presentation.AllConversationsUi
import io.github.eslamasabry.oc.feature.chats.presentation.AllPhase
import io.github.eslamasabry.oc.feature.chats.presentation.AllProblemKind
import io.github.eslamasabry.oc.feature.chats.presentation.AllRowUi
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsAllActions
import io.github.eslamasabry.oc.feature.chats.presentation.RowTitle
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipKind
import io.github.eslamasabry.oc.ui.kit.chip.KitChipTone
import io.github.eslamasabry.oc.ui.kit.chip.KitChipWrap
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceMark
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceRow
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.input.KitSearchField
import io.github.eslamasabry.oc.ui.kit.modal.KitBackGuard
import io.github.eslamasabry.oc.ui.kit.modal.KitInputDialog
import io.github.eslamasabry.oc.ui.kit.modal.KitMenu
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.modal.KitUndoBar
import io.github.eslamasabry.oc.ui.kit.modal.KitUndoOffer
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.chatsHomeClearFilters
import io.github.eslamasabry.oc.ui.words.res.chatsHomeIncompleteNamed
import io.github.eslamasabry.oc.ui.words.res.chatsHomeRunning
import io.github.eslamasabry.oc.ui.words.res.chats_all_title
import io.github.eslamasabry.oc.ui.words.res.globalSessionsEmptyMessage
import io.github.eslamasabry.oc.ui.words.res.globalSessionsEmptyTitle
import io.github.eslamasabry.oc.ui.words.res.globalSessionsLoadFailedTitle
import io.github.eslamasabry.oc.ui.words.res.globalSessionsLoadMore
import io.github.eslamasabry.oc.ui.words.res.globalSessionsLoadMoreFailed
import io.github.eslamasabry.oc.ui.words.res.globalSessionsNoMatchTitle
import io.github.eslamasabry.oc.ui.words.res.globalSessionsSearchLabel
import io.github.eslamasabry.oc.ui.words.res.globalSessionsUnknownLocation
import io.github.eslamasabry.oc.ui.words.res.kitChoiceSelectedCount
import io.github.eslamasabry.oc.ui.words.res.kitCopyDetails
import io.github.eslamasabry.oc.ui.words.res.kitDetails
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.kitTryAgain
import io.github.eslamasabry.oc.ui.words.res.p12ArchiveFailed
import io.github.eslamasabry.oc.ui.words.res.p12ArchiveSelected
import io.github.eslamasabry.oc.ui.words.res.p12ArchiveUnavailable
import io.github.eslamasabry.oc.ui.words.res.p12ArchivedFilter
import io.github.eslamasabry.oc.ui.words.res.p12DeleteFailed
import io.github.eslamasabry.oc.ui.words.res.p12DeleteSelected
import io.github.eslamasabry.oc.ui.words.res.p12Deleted
import io.github.eslamasabry.oc.ui.words.res.p12DeletedMany
import io.github.eslamasabry.oc.ui.words.res.p12MoreFor
import io.github.eslamasabry.oc.ui.words.res.p12NoServerBody
import io.github.eslamasabry.oc.ui.words.res.p12NoServerTitle
import io.github.eslamasabry.oc.ui.words.res.p12Rename
import io.github.eslamasabry.oc.ui.words.res.p12RenameEmpty
import io.github.eslamasabry.oc.ui.words.res.p12RenameFailed
import io.github.eslamasabry.oc.ui.words.res.p12RenameField
import io.github.eslamasabry.oc.ui.words.res.p12RenameTitle
import io.github.eslamasabry.oc.ui.words.res.p12Select
import io.github.eslamasabry.oc.ui.words.res.p12SelectCancel
import io.github.eslamasabry.oc.ui.words.res.serversTitle
import io.github.eslamasabry.oc.ui.words.res.sessionMenuArchive
import io.github.eslamasabry.oc.ui.words.res.sessionMenuDelete
import io.github.eslamasabry.oc.ui.words.res.workspaceNewSession
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * All conversations (07 §5 B12). Arranges kit parts only (K1): search first, the filter chips, one row per
 * conversation, a visible "Load more conversations" (also on reaching the end) with Try again, and a select mode
 * whose actions sit at the bottom. A row's long-press opens its menu; Select is the visible way to the same acts.
 */
@Composable
public fun ChatsAllScreen(state: AllConversationsUi, actions: ChatsAllActions) {
    // Back leaves select mode first (predictive back), then the page.
    KitBackGuard(enabled = state.selecting, onBack = actions::stopSelecting)
    val listed = state.phase == AllPhase.List || state.phase == AllPhase.NoMatch
    val barActions =
        when {
            state.selecting -> listOf(KitLineAction(stringResource(Res.string.p12SelectCancel), actions::stopSelecting))
            state.phase == AllPhase.List -> listOf(KitLineAction(stringResource(Res.string.p12Select), { actions.startSelecting(null) }))
            else -> emptyList()
        }
    val title =
        if (state.selecting) {
            pluralStringResource(Res.plurals.kitChoiceSelectedCount, state.selectedCount, state.selectedCount)
        } else {
            stringResource(Res.string.chats_all_title)
        }
    KitScreen(
        topBar = {
            KitTopBar(
                title = title,
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
                actions = barActions,
            )
        },
        search =
            if (listed && !state.selecting) {
                { KitSearchField(label = stringResource(Res.string.globalSessionsSearchLabel), onSearch = actions::search) }
            } else {
                null
            },
        bottom = { Bottom(state, actions) },
        width = KitScreenWidth.Reading,
    ) {
        val list = rememberLazyListState()
        // Reaching the end asks for the next page; after a failure only the visible Try again does.
        LaunchedEffect(list, state.canLoadMore, state.loadMoreFailure == null) {
            if (!state.canLoadMore || state.loadMoreFailure != null) return@LaunchedEffect
            snapshotFlow {
                    val info = list.layoutInfo
                    val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                    info.totalItemsCount > 0 && last >= info.totalItemsCount - END_SLACK
                }
                .collect { near -> if (near) actions.loadMore() }
        }
        LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
            if (listed && !state.selecting) item(key = "filters") { Filters(state, actions) }
            if (state.incomplete.isNotEmpty()) {
                item(key = "incomplete") {
                    KitNotice(stringResource(Res.string.chatsHomeIncompleteNamed, state.incomplete.joinToString(", ")))
                }
            }
            state.problem?.let { problem ->
                item(key = "problem") {
                    KitErrorNotice(
                        message =
                            stringResource(
                                when (problem.kind) {
                                    AllProblemKind.Rename -> Res.string.p12RenameFailed
                                    AllProblemKind.Delete -> Res.string.p12DeleteFailed
                                    AllProblemKind.Archive -> Res.string.p12ArchiveFailed
                                }
                            ),
                        failure = problem.failure,
                        detailsLabel = stringResource(Res.string.kitDetails),
                        copyLabel = stringResource(Res.string.kitCopyDetails),
                    )
                }
            }
            when (state.phase) {
                AllPhase.Opening -> item(key = "opening") { KitSkeletonRows() }
                AllPhase.NoServer ->
                    item(key = "no-server") {
                        KitStateView(
                            kind = KitStateKind.Calm,
                            title = stringResource(Res.string.p12NoServerTitle),
                            body = stringResource(Res.string.p12NoServerBody),
                            primary = KitAction(stringResource(Res.string.serversTitle), actions::openServers),
                        )
                    }
                AllPhase.Failed ->
                    item(key = "failed") {
                        KitStateView(
                            kind = KitStateKind.Error,
                            title = stringResource(Res.string.globalSessionsLoadFailedTitle),
                            failure = state.failure,
                            onRetry = actions::retry,
                            retryLabel = stringResource(Res.string.kitTryAgain),
                        )
                    }
                AllPhase.Empty ->
                    item(key = "empty") {
                        KitStateView(
                            kind = KitStateKind.Empty,
                            title = stringResource(Res.string.globalSessionsEmptyTitle),
                            body = stringResource(Res.string.globalSessionsEmptyMessage),
                        )
                    }
                AllPhase.NoMatch ->
                    item(key = "no-match") {
                        KitStateView(
                            kind = KitStateKind.Empty,
                            title = stringResource(Res.string.globalSessionsNoMatchTitle),
                            primary = KitAction(stringResource(Res.string.chatsHomeClearFilters), actions::clearFilters),
                        )
                    }
                AllPhase.List -> items(state.rows, key = { it.key }) { row -> AllRow(row, state.selecting, actions) }
            }
            if (state.phase == AllPhase.List && state.canLoadMore) {
                item(key = "more") {
                    val failed = state.loadMoreFailure
                    if (failed != null) {
                        KitErrorNotice(
                            message = stringResource(Res.string.globalSessionsLoadMoreFailed),
                            failure = failed,
                            detailsLabel = stringResource(Res.string.kitDetails),
                            copyLabel = stringResource(Res.string.kitCopyDetails),
                            retry = KitLineAction(stringResource(Res.string.kitTryAgain), actions::loadMore),
                        )
                    } else {
                        KitButton(
                            label = stringResource(Res.string.globalSessionsLoadMore),
                            onClick = actions::loadMore,
                            role = KitButtonRole.Tertiary,
                            working = state.loadingMore,
                        )
                    }
                }
            }
        }
    }
    val renaming = state.renaming
    val emptyTitle = stringResource(Res.string.p12RenameEmpty)
    KitInputDialog(
        visible = renaming != null,
        title = stringResource(Res.string.p12RenameTitle),
        fieldLabel = stringResource(Res.string.p12RenameField),
        confirmLabel = stringResource(Res.string.p12Rename),
        initial = renaming?.initial.orEmpty(),
        validate = { if (it.isBlank()) emptyTitle else null },
        operationKey = renaming?.key,
        onResult = actions::finishRename,
    )
}

/** Rows near the end that already count as "reached the end". */
private const val END_SLACK = 3

@Composable
private fun Filters(state: AllConversationsUi, actions: ChatsAllActions) {
    KitChipWrap {
        KitChip(
            label = stringResource(Res.string.chatsHomeRunning),
            kind = KitChipKind.Action,
            tone = if (state.running) KitChipTone.Active else KitChipTone.Neutral,
            selected = state.running,
            onClick = actions::toggleRunning,
        )
        if (state.hasArchived) {
            KitChip(
                label = stringResource(Res.string.p12ArchivedFilter),
                kind = KitChipKind.Action,
                tone = if (state.archived) KitChipTone.Active else KitChipTone.Neutral,
                selected = state.archived,
                onClick = actions::toggleArchived,
            )
        }
        state.projects.forEach { project ->
            KitChip(
                label = project.name,
                kind = KitChipKind.Action,
                tone = if (project.selected) KitChipTone.Active else KitChipTone.Neutral,
                selected = project.selected,
                onClick = { actions.chooseProject(project.directory) },
            )
        }
    }
}

@Composable
private fun rowTitle(title: RowTitle): String =
    when (title) {
        is RowTitle.Text -> title.text
        RowTitle.New,
        RowTitle.Untitled -> stringResource(Res.string.workspaceNewSession)
    }

@Composable
private fun AllRow(row: AllRowUi, selecting: Boolean, actions: ChatsAllActions) {
    val title = rowTitle(row.title)
    val project = row.project ?: stringResource(Res.string.globalSessionsUnknownLocation)
    val supporting = row.server?.let { "$it · $project" } ?: project
    val running = stringResource(Res.string.chatsHomeRunning)
    if (selecting) {
        KitChoiceRow(
            title = title,
            selected = row.selected,
            supporting = supporting,
            mark = KitChoiceMark.Check,
            tag = if (row.running) running else null,
            onClick = { actions.tapRow(row.key) },
        )
        return
    }
    var menu by remember(row.key) { mutableStateOf(false) }
    val archiveReason = stringResource(Res.string.p12ArchiveUnavailable)
    val items =
        listOf(
            KitMenuItem(stringResource(Res.string.p12Rename), { actions.rename(row.key) }),
            KitMenuItem(
                stringResource(Res.string.sessionMenuArchive),
                { actions.archive(row.key) },
                enabled = if (row.canArchive) KitEnabled.Enabled else KitEnabled.Disabled(archiveReason),
            ),
            KitMenuItem(stringResource(Res.string.p12Select), { actions.startSelecting(row.key) }),
            KitMenuItem(stringResource(Res.string.sessionMenuDelete), { actions.delete(row.key) }, destructive = true),
        )
    KitMenu(expanded = menu, items = items, onDismissRequest = { menu = false }, title = title) {
        KitRow(
            title = title,
            supporting = supporting,
            trailing = if (row.running) KitRowTrailing.Value(running, chevron = false) else KitRowTrailing.None,
            growKey = row.ref,
            onLongClick = { menu = true },
            onLongClickLabel = stringResource(Res.string.p12MoreFor, title),
            onClick = { actions.tapRow(row.key) },
        )
    }
}

/** Select mode's acts, then the one undo offer, at the bottom of the page. */
@Composable
private fun Bottom(state: AllConversationsUi, actions: ChatsAllActions) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
        if (state.selecting && state.selectedCount > 0) {
            val n = state.selectedCount
            if (n == 1) {
                KitButton(
                    label = stringResource(Res.string.p12Rename),
                    onClick = actions::renameSelected,
                    role = KitButtonRole.Secondary,
                )
            }
            KitButton(
                label = pluralStringResource(Res.plurals.p12ArchiveSelected, n, n),
                onClick = actions::archiveSelected,
                role = KitButtonRole.Secondary,
                enabled =
                    if (state.archiveAvailable) KitEnabled.Enabled
                    else KitEnabled.Disabled(stringResource(Res.string.p12ArchiveUnavailable)),
            )
            KitButton(
                label = pluralStringResource(Res.plurals.p12DeleteSelected, n, n),
                onClick = actions::deleteSelected,
                role = KitButtonRole.Primary,
                destructive = true,
            )
        }
        val undo = state.undo
        val message =
            when {
                undo == null -> null
                undo.title != null -> stringResource(Res.string.p12Deleted, rowTitle(undo.title))
                else -> pluralStringResource(Res.plurals.p12DeletedMany, undo.count, undo.count)
            }
        KitUndoBar(
            offer = if (undo != null && message != null) KitUndoOffer("delete-${undo.id}", message) else null,
            onUndo = actions::undoDelete,
            onTimeout = actions::commitDelete,
        )
    }
}
