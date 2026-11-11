package io.github.eslamasabry.oc.feature.chats.ui

import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.ui.kit.text.kitRelativeAge
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.chats.presentation.AllConversationsUi
import io.github.eslamasabry.oc.feature.chats.presentation.AllPhase
import io.github.eslamasabry.oc.feature.chats.presentation.AllProblemKind
import io.github.eslamasabry.oc.feature.chats.presentation.AllRowUi
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsAllActions
import io.github.eslamasabry.oc.feature.chats.presentation.RowAct
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitConsequence
import io.github.eslamasabry.oc.ui.kit.modal.KitConsequenceMark
import io.github.eslamasabry.oc.feature.chats.presentation.RowTitle
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipKind
import io.github.eslamasabry.oc.ui.kit.chip.KitChipTone
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceMark
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceRow
import io.github.eslamasabry.oc.ui.kit.choice.KitSegment
import io.github.eslamasabry.oc.ui.kit.choice.KitSegmented
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenRefresh
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.input.KitSearchField
import io.github.eslamasabry.oc.ui.kit.modal.KitBackGuard
import io.github.eslamasabry.oc.ui.kit.modal.KitMenu
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.modal.LocalKitToasts
import io.github.eslamasabry.oc.ui.kit.modal.KitUndoBar
import io.github.eslamasabry.oc.ui.kit.modal.KitUndoOffer
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowState
import io.github.eslamasabry.oc.ui.kit.row.KitRowStateTone
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateSize
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import io.github.eslamasabry.oc.ui.kitchat.rememberKitRedactedCopyAction
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
import io.github.eslamasabry.oc.ui.words.res.globalSessionsWorking
import io.github.eslamasabry.oc.ui.words.res.globalSessionsArchivedShort
import io.github.eslamasabry.oc.ui.words.res.kitChoiceSelectedCount
import io.github.eslamasabry.oc.ui.words.res.kitCopied
import io.github.eslamasabry.oc.ui.words.res.kitCopyDetails
import io.github.eslamasabry.oc.ui.words.res.kitDetails
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.kitTryAgain
import io.github.eslamasabry.oc.ui.words.res.p12ArchiveFailed
import io.github.eslamasabry.oc.ui.words.res.p12ArchiveSelected
import io.github.eslamasabry.oc.ui.words.res.p12ArchiveUnavailable
import io.github.eslamasabry.oc.ui.words.res.p12DeleteFailed
import io.github.eslamasabry.oc.ui.words.res.p12DeleteSelected
import io.github.eslamasabry.oc.ui.words.res.p12Deleted
import io.github.eslamasabry.oc.ui.words.res.p12DeletedMany
import io.github.eslamasabry.oc.ui.words.res.p12MoreFor
import io.github.eslamasabry.oc.ui.words.res.p12NoServerBody
import io.github.eslamasabry.oc.ui.words.res.p12NoServerTitle
import io.github.eslamasabry.oc.ui.words.res.p12Rename
import io.github.eslamasabry.oc.ui.words.res.p12RenameFailed
import io.github.eslamasabry.oc.ui.words.res.p12Select
import io.github.eslamasabry.oc.ui.words.res.p12SelectCancel
import io.github.eslamasabry.oc.ui.words.res.serversTitle
import io.github.eslamasabry.oc.ui.words.res.sessionMenuArchive
import io.github.eslamasabry.oc.ui.words.res.sessionMenuDelete
import io.github.eslamasabry.oc.ui.words.res.sessionsReload
import io.github.eslamasabry.oc.ui.words.res.stateContextUsage
import io.github.eslamasabry.oc.ui.words.res.statePin
import io.github.eslamasabry.oc.ui.words.res.statePinFailed
import io.github.eslamasabry.oc.ui.words.res.stateSavedPreview
import io.github.eslamasabry.oc.ui.words.res.stateUnpin
import io.github.eslamasabry.oc.ui.words.res.workspaceNewSession
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * All conversations (07 §5 B12). Arranges kit parts only (K1): search first, the project menu and archive scope, one
 * row per conversation, a visible "Load more conversations" (also on reaching the end) with Try
 * again, and a select mode whose actions sit at the bottom. A row's long-press opens its menu;
 * Select is the visible way to the same acts.
 */
@Composable
public fun ChatsAllScreen(state: AllConversationsUi, actions: ChatsAllActions) {
    // Back leaves select mode first (predictive back), then the page.
    KitBackGuard(enabled = state.selecting, onBack = actions::stopSelecting)
    val listed = state.phase == AllPhase.List || state.phase == AllPhase.NoMatch
    // One text state for the page, so the field keeps its words when select mode hides and brings
    // it back.
    val searchText = rememberTextFieldState(state.query)
    val project = state.projects.firstOrNull { it.selected }
    LaunchedEffect(state.query) {
        if (state.query.isEmpty() && searchText.text.isNotEmpty()) searchText.edit { replace(0, length, "") }
    }
    // Flutter's top-bar menu entry: no need to open some conversation first.
    val importAction =
        if (state.canImport)
            KitLineAction(
                stringResource(Res.string.libraryImportAConversation),
                actions::openImport,
                icon = KitIcons.download,
            )
        else null
    val barActions =
        when {
            state.selecting ->
                listOf(
                    KitLineAction(
                        stringResource(Res.string.p12SelectCancel),
                        actions::stopSelecting,
                        icon = KitIcons.close,
                    )
                )
            state.phase == AllPhase.List ->
                listOf(
                    // Glyphs on the bar (the words are what TalkBack says): as words they were cut to "Ref…" and "Sel…".
                    KitLineAction(stringResource(Res.string.sessionsReload), actions::retry, icon = KitIcons.retry),
                    KitLineAction(
                        stringResource(Res.string.p12Select),
                        { actions.startSelecting(null) },
                        icon = KitIcons.checkboxChecked,
                    ),
                ) + listOfNotNull(importAction)
            state.phase == AllPhase.NoServer -> emptyList()
            else -> listOfNotNull(importAction)
        }
    val title =
        if (state.selecting) {
            pluralStringResource(
                Res.plurals.kitChoiceSelectedCount,
                state.selectedCount,
                state.selectedCount,
            )
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
            if (state.phase != AllPhase.NoServer && !state.selecting) {
                {
                    KitSearchField(
                        label = stringResource(Res.string.globalSessionsSearchLabel),
                        onSearch = actions::search,
                        state = searchText,
                        resultCount = if (state.query.isBlank() || state.phase == AllPhase.Opening) null else state.rows.size,
                        partial = state.canLoadMore,
                        filters = buildList {
                            if (state.projects.size > 1) state.projects.forEach { choice ->
                                add(KitMenuItem(choice.name, { actions.chooseProject(choice.directory) }, icon = KitIcons.files, checked = choice.selected))
                            }
                            add(KitMenuItem(stringResource(Res.string.chatsHomeRunning), actions::toggleRunning, checked = state.running))
                        },
                        activeFilter = project?.name,
                        removeFilterLabel = stringResource(Res.string.kitSearchClear),
                        onClearFilter = project?.let { { actions.chooseProject(it.directory) } },
                    )
                }
            } else {
                null
            },
        bottom = { Bottom(state, actions) },
        header = if (state.phase == AllPhase.NoServer || state.selecting) null else ({ Filters(state, actions) }),
        loading = state.phase == AllPhase.Opening || state.loadingMore,
        loadingLabel = stringResource(Res.string.globalSessionsRefresh),
        refresh = KitScreenRefresh(refreshing = false, onRefresh = actions::retry, enabled = listed && !state.selecting),
        width = KitScreenWidth.List,
        edges = KitScreenEdges.Bleed,
    ) {
        val list = rememberLazyListState()
        val rowWords = rememberAllRowWords()
        // Reaching the end asks for the next page; after a failure only the visible Try again does.
        // Keyed on the pages
        // read, so a page that brings no matching row (search, Running, Archived) still leads to
        // the next one.
        val autoLoad = state.canLoadMore && state.loadMoreFailure == null && !state.selecting
        LaunchedEffect(list, autoLoad, state.loadedPages) {
            if (!autoLoad) return@LaunchedEffect
            snapshotFlow {
                val info = list.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                info.totalItemsCount > 0 && last >= info.totalItemsCount - END_SLACK
            }
                .collect { near -> if (near) actions.loadMore() }
        }
        LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
            if (state.incomplete.isNotEmpty()) {
                item(key = "incomplete", contentType = "incomplete") {
                    KitNotice(
                        stringResource(
                            Res.string.chatsHomeIncompleteNamed,
                            state.incomplete.joinToString(", "),
                        )
                    )
                }
            }
            state.problem?.let { problem ->
                item(key = "problem", contentType = "problem") {
                    KitErrorNotice(
                        message =
                            stringResource(
                                when (problem.kind) {
                                    AllProblemKind.Rename -> Res.string.p12RenameFailed
                                    AllProblemKind.Delete -> Res.string.p12DeleteFailed
                                    AllProblemKind.Archive -> Res.string.p12ArchiveFailed
                                    AllProblemKind.Pin -> Res.string.statePinFailed
                                }
                            ),
                        failure = problem.failure,
                        detailsLabel = stringResource(Res.string.kitDetails),
                        copyLabel = stringResource(Res.string.kitCopyDetails),
                    )
                }
            }
            when (state.phase) {
                AllPhase.Opening -> item(key = "opening", contentType = "opening") { KitSkeletonRows(count = 7) } // F: lib/ui/screens/global_sessions_screen.dart:814
                AllPhase.NoServer ->
                    item(key = "no-server", contentType = "no-server") {
                        KitStateView(
                            kind = KitStateKind.Calm,
                            title = stringResource(Res.string.p12NoServerTitle),
                            body = stringResource(Res.string.p12NoServerBody),
                            primary =
                                KitAction(
                                    stringResource(Res.string.serversTitle),
                                    actions::openServers,
                                ),
                        )
                    }
                AllPhase.Failed ->
                    item(key = "failed", contentType = "failed") {
                        KitStateView(
                            kind = KitStateKind.Error,
                            title = stringResource(Res.string.globalSessionsLoadFailedTitle),
                            failure = state.failure,
                            onRetry = actions::retry,
                            retryLabel = stringResource(Res.string.kitTryAgain),
                        )
                    }
                AllPhase.Empty ->
                    item(key = "empty", contentType = "empty") {
                        KitStateView(
                            kind = KitStateKind.Empty,
                            title = stringResource(if (state.archived) Res.string.globalSessionsArchivedEmptyTitle else Res.string.globalSessionsEmptyTitle),
                            body = stringResource(if (state.archived) Res.string.globalSessionsArchivedEmptyMessage else Res.string.globalSessionsEmptyMessage),
                            icon = if (state.archived) KitIcons.archive else KitIcons.searchList,
                            secondary = if (state.archived) KitAction(stringResource(Res.string.globalSessionsShowActive), actions::toggleArchived) else null,
                            tertiary = if (state.archived) emptyList() else listOf(KitAction(stringResource(Res.string.globalSessionsRefresh), actions::retry)),
                        )
                    }
                AllPhase.NoMatch ->
                    item(key = "no-match", contentType = "no-match") {
                        if (state.query.isNotBlank()) KitStateView(
                            kind = KitStateKind.Empty,
                            title = stringResource(Res.string.globalSessionsNoMatchTitle),
                            body = stringResource(if (state.archived) Res.string.globalSessionsArchivedNoMatchMessage else Res.string.globalSessionsNoMatchMessage),
                            icon = KitIcons.searchList,
                            secondary = KitAction(stringResource(Res.string.commonClearSearch), { actions.search("") }),
                            size = KitStateSize.Inline,
                        )
                        else if (state.archived && project == null && !state.running) KitStateView(
                            kind = KitStateKind.Empty,
                            title = stringResource(Res.string.globalSessionsArchivedEmptyTitle),
                            body = stringResource(Res.string.globalSessionsArchivedEmptyMessage),
                            icon = KitIcons.archive,
                            secondary = KitAction(stringResource(Res.string.globalSessionsShowActive), actions::toggleArchived),
                            size = KitStateSize.Inline,
                        )
                        else KitStateView(
                            kind = KitStateKind.Empty,
                            title = stringResource(Res.string.globalSessionsNoMatchTitle),
                            primary = KitAction(stringResource(Res.string.chatsHomeClearFilters), actions::clearFilters),
                            size = KitStateSize.Inline,
                        )
                    }
                AllPhase.List ->
                    items(state.rows, key = { it.key }, contentType = { "conversation" }) { row ->
                        AllRow(row, state.selecting, rowWords, state.nowMs, actions)
                    }
            }
            // Under a filter with no loaded match, older pages may still hold one: Load more and
            // its Try again stay.
            if (listed && (state.canLoadMore || state.loadMoreFailure != null)) {
                item(key = "more", contentType = "more") {
                    AllConversationsPagingTail(state, actions::loadMore)
                }
            }
        }
        ConversationRenameDialog(state.renaming, actions::finishRename)
        ContinueHereDialog(state, actions)
        StartNewConversationConfirm(state.startNew, actions::confirmStartNew, actions::dismissStartNew)
        ImportConversationSheet(state.import, actions)
    }
}

/** Flutter's paging tail: the screen loading bar owns progress; the explicit button returns after the read. */
@Composable
internal fun AllConversationsPagingTail(state: AllConversationsUi, onLoadMore: () -> Unit) {
    val failed = state.loadMoreFailure
    if (failed == null && (!state.canLoadMore || state.loadingMore || state.phase == AllPhase.Opening)) return
    val tokens = LocalKitTokens.current
    Column(Modifier.padding(horizontal = tokens.gutter.dp).padding(top = if (state.rows.isEmpty()) 0.dp else tokens.sectionGap.dp)) {
        if (failed != null) {
            KitErrorNotice(
                message = stringResource(Res.string.globalSessionsLoadMoreFailed),
                failure = failed,
                detailsLabel = stringResource(Res.string.kitDetails),
                copyLabel = stringResource(Res.string.kitCopyDetails),
                retry = KitLineAction(stringResource(Res.string.commonRetry), onLoadMore),
            )
        } else {
            KitButton(
                label = stringResource(Res.string.globalSessionsLoadMore),
                onClick = onLoadMore,
                role = KitButtonRole.Secondary,
            )
        }
    }
}

/** "Continue here" (Flutter `global-sessions-continue-here-sheet`): names both projects; the move runs inside it. */
@Composable
private fun ContinueHereDialog(state: AllConversationsUi, actions: ChatsAllActions) {
    val offer = state.moveHere
    val unknown = stringResource(Res.string.e7WorkspaceUnknownProject)
    val from = offer?.from ?: unknown
    val to = offer?.to ?: unknown
    val title = offer?.let { rowTitle(it.title) }.orEmpty()
    KitConfirm(
        spec =
            offer?.let {
                KitConfirmSpec(
                    title = stringResource(Res.string.globalSessionsMoveTitle, to),
                    confirmLabel =
                        stringResource(
                            if (it.failed) Res.string.kitTryAgain else Res.string.e7SharedMoveSession
                        ),
                    kind = KitConfirmKind.Neutral,
                    body = stringResource(Res.string.globalSessionsMoveBody, title, from, to),
                    consequenceItems =
                        listOfNotNull(
                            if (it.sessionWorking)
                                KitConsequence(
                                    stringResource(Res.string.globalSessionsMoveWhileWorking),
                                    KitConsequenceMark.Lost,
                                )
                            else null,
                            KitConsequence(stringResource(Res.string.globalSessionsMoveBack, from)),
                        ),
                )
            },
        onConfirm = actions::confirmContinueHere,
        onCancel = actions::cancelContinueHere,
        working = offer?.working == true,
        failed = offer?.failed == true,
    )
}

/** Rows near the end that already count as "reached the end". */
private const val END_SLACK = 3

@Composable
private fun Filters(state: AllConversationsUi, actions: ChatsAllActions) {
    KitSegmented(
        segments = listOf(
            KitSegment(false, stringResource(Res.string.globalSessionsFilterActive)),
            KitSegment(true, stringResource(Res.string.globalSessionsArchivedShort), icon = KitIcons.archive),
        ),
        selected = state.archived,
        semanticsLabel = stringResource(Res.string.globalSessionsFilterLabel),
        modifier = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp).padding(top = Space.S1.dp, bottom = Space.S2.dp),
        onChange = { archived -> if (archived != state.archived) actions.toggleArchived() },
    )
    if (state.running) KitChip(
        label = stringResource(Res.string.chatsHomeRunning),
        kind = KitChipKind.Action,
        tone = KitChipTone.Active,
        selected = true,
        onClick = actions::toggleRunning,
        modifier = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp),
    )
}

@Composable
private fun rowTitle(title: RowTitle): String = rowTitleWords(title)

/** The words every All conversations row shares, read once by the list. */
private data class AllRowWords(
    val working: String,
    val archivedShort: String,
    val unknownLocation: String,
    val needsYou: String,
    val unread: String,
    val stopped: String,
    val interrupted: String,
    val failed: String,
)

@Composable
private fun rememberAllRowWords(): AllRowWords =
    AllRowWords(
        working = stringResource(Res.string.globalSessionsWorking),
        archivedShort = stringResource(Res.string.globalSessionsArchivedShort),
        unknownLocation = stringResource(Res.string.globalSessionsUnknownLocation),
        needsYou = stringResource(Res.string.chatsHomeNeedsYou),
        unread = stringResource(Res.string.sessionUnread),
        stopped = stringResource(Res.string.chatsHomeStopped),
        interrupted = stringResource(Res.string.chatsHomeInterrupted),
        failed = stringResource(Res.string.chatsHomeFailed),
    )

@Composable
private fun AllRow(row: AllRowUi, selecting: Boolean, words: AllRowWords, nowMs: Long, actions: ChatsAllActions) {
    val title = rowTitle(row.title)
    val project = row.project ?: words.unknownLocation
    val location = row.server?.let { "$it · $project" } ?: project
    val place =
        row.contextPercent?.let {
            "$location · " + stringResource(Res.string.stateContextUsage, it)
        } ?: location
    val running = words.working
    // The state word leads, then how long ago, then where (Flutter global_sessions_screen.dart:1080-1110).
    val status = when {
        row.needsYou -> words.needsYou
        row.running -> running
        row.archived -> words.archivedShort
        row.stopped -> words.stopped
        row.interrupted -> words.interrupted
        row.failed -> words.failed
        row.unread -> words.unread
        else -> null
    }
    val ago = row.updatedMs.takeIf { it > 0 && nowMs > 0 }?.let { kitRelativeAge(it, nowMs) }
    // ANDR-1: the visible More action takes the trailing slot, so Running moves into the line.
    val supporting = listOfNotNull(status, ago, place).joinToString(" · ")
    if (row.readOnly) {
        KitRow(
            title = title,
            titleMaxLines = 2, // F: lib/ui/screens/global_sessions_screen.dart:1107
            supporting = listOfNotNull(ago, place).joinToString(" · "),
            enabled = false,
            disabledReason = stringResource(Res.string.stateSavedPreview),
        )
        return
    }
    if (selecting) {
        KitChoiceRow(
            title = title,
            selected = row.selected,
            supporting = supporting,
            mark = KitChoiceMark.Check,
            tag = if (row.needsYou) words.needsYou else if (row.running) running else null,
            onClick = { actions.tapRow(row.key) },
        )
        return
    }
    var menu by remember(row.key) { mutableStateOf(false) }
    // Built only while the menu is open; a closed menu keeps what it last showed for its fade.
    val items = if (menu) allRowMenu(row, words.unknownLocation, actions) else emptyList()
    val moreFor = stringResource(Res.string.p12MoreFor, title)
    KitMenu(expanded = menu, items = items, onDismissRequest = { menu = false }, title = title) {
        KitRow(
            title = title,
            titleMaxLines = 2, // F: lib/ui/screens/global_sessions_screen.dart:1107
            supportingMaxLines = 2, // F: lib/ui/screens/global_sessions_screen.dart:1108
            leading = if (row.running || row.needsYou) null else KitRowLeading.Glyph(if (row.archived) KitIcons.archive else KitIcons.chat),
            state =
                when {
                    row.needsYou -> KitRowState(null, KitRowStateTone.NeedsYou)
                    row.running -> KitRowState(null, KitRowStateTone.Running)
                    else -> null
                },
            supporting = supporting,
            // The ⋯ glyph, its words for TalkBack only: as words "More for <title>" pushed the title out of the row.
            trailing = KitRowTrailing.IconAction(KitLineAction(moreFor, { menu = true }, icon = KitIcons.more)),
            growKey = row.ref,
            onLongClick = { menu = true },
            onLongClickLabel = moreFor,
            onClick = { actions.tapRow(row.key) },
        )
    }
}

@Composable
private fun allRowMenu(row: AllRowUi, unknownLocation: String, actions: ChatsAllActions): List<KitMenuItem> {
    val archiveReason = stringResource(Res.string.p12ArchiveUnavailable)
    // A folder path is server text: it goes through the one redacting copy like every other copy of server text.
    val copyFolder =
        rememberKitRedactedCopyAction(
            label = stringResource(Res.string.globalSessionsCopyFolder),
            copiedLabel = stringResource(Res.string.kitCopied),
            text = { row.folder ?: unknownLocation },
        )
    // The menu closes on the tap, so the copy action's own "Copied" label is never seen: the toast says it.
    val toasts = LocalKitToasts.current
    val copiedWords = stringResource(Res.string.kitCopied)
    return listOfNotNull(
        if (row.pinAvailable) KitMenuItem(
            stringResource(if (row.pinned) Res.string.stateUnpin else Res.string.statePin),
            { actions.togglePin(row.key) },
            icon = if (row.pinned) KitIcons.unpin else KitIcons.pin,
        ) else null,
        KitMenuItem(stringResource(Res.string.p12Rename), { actions.rename(row.key) }, icon = KitIcons.edit),
        KitMenuItem(stringResource(Res.string.chatUiShareSession), { actions.rowAct(row.key, RowAct.Share) }, icon = KitIcons.link),
        KitMenuItem(stringResource(Res.string.sessionMenuDetails), { actions.rowAct(row.key, RowAct.Details) }, icon = KitIcons.info),
        if (row.canContinueHere) KitMenuItem(stringResource(Res.string.globalSessionsContinueHere), { actions.continueHere(row.key) }, icon = KitIcons.download) else null,
        KitMenuItem(stringResource(Res.string.sessionOpenRelated), { actions.rowAct(row.key, RowAct.OpenRelated) }, icon = KitIcons.branch),
        KitMenuItem(stringResource(Res.string.sessionCopyHandoff), { actions.rowAct(row.key, RowAct.CopyHandoff) }, icon = KitIcons.terminal),
        KitMenuItem(
            stringResource(Res.string.globalSessionsCopyFolder),
            {
                copyFolder.onClick?.invoke()
                toasts?.show(copiedWords)
            },
            icon = KitIcons.copy,
        ),
        KitMenuItem(
            stringResource(Res.string.sessionMenuArchive),
            { actions.archive(row.key) },
            icon = KitIcons.archive,
            enabled = if (row.canArchive) KitEnabled.Enabled else KitEnabled.Disabled(archiveReason),
        ),
        KitMenuItem(stringResource(Res.string.p12Select), { actions.startSelecting(row.key) }, icon = KitIcons.checkboxChecked),
        KitMenuItem(
            stringResource(Res.string.sessionMenuDelete),
            { actions.delete(row.key) },
            icon = KitIcons.delete,
            destructive = true,
        ),
    )
}

/** Select mode's acts, then the one undo offer, at the bottom of the page. */
@Composable
private fun Bottom(state: AllConversationsUi, actions: ChatsAllActions) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
        if (state.selecting && state.selectedCount > 0) {
            val n = state.selectedCount
            if (n == 1) {
                val selected = state.rows.singleOrNull { it.selected }
                if (selected?.pinAvailable == true) KitButton(
                    label =
                        stringResource(
                            if (selected?.pinned == true) Res.string.stateUnpin
                            else Res.string.statePin
                        ),
                    onClick = actions::toggleSelectedPin,
                    role = KitButtonRole.Secondary,
                )
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
            offer =
                if (undo != null && message != null) KitUndoOffer("delete-${undo.id}", message)
                else null,
            onUndo = actions::undoDelete,
            onTimeout = actions::commitDelete,
        )
    }
}
