package io.github.eslamasabry.oc.feature.files.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.model.FileNode
import io.github.eslamasabry.oc.feature.files.presentation.FolderActions
import io.github.eslamasabry.oc.feature.files.presentation.FolderPhase
import io.github.eslamasabry.oc.feature.files.presentation.FolderUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.input.KitSearchField
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.chatStartEmptyFolder
import io.github.eslamasabry.oc.ui.words.res.filesHideHidden
import io.github.eslamasabry.oc.ui.words.res.filesLoadFailedTitle
import io.github.eslamasabry.oc.ui.words.res.filesNotConnected
import io.github.eslamasabry.oc.ui.words.res.filesOnlyHidden
import io.github.eslamasabry.oc.ui.words.res.filesProjectRoot
import io.github.eslamasabry.oc.ui.words.res.filesShowHidden
import io.github.eslamasabry.oc.ui.words.res.filesUnavailable
import io.github.eslamasabry.oc.ui.words.res.kitCopyDetails
import io.github.eslamasabry.oc.ui.words.res.kitDetails
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.kitTryAgain
import io.github.eslamasabry.oc.ui.words.res.libraryTerminalTitle
import io.github.eslamasabry.oc.ui.words.res.readerUiNoFiles
import io.github.eslamasabry.oc.ui.words.res.readerUiSearchFiles
import io.github.eslamasabry.oc.ui.words.res.readerUiTryFileName
import io.github.eslamasabry.oc.ui.words.res.storageAccessAllow
import io.github.eslamasabry.oc.ui.words.res.storageAccessBody
import io.github.eslamasabry.oc.ui.words.res.storageAccessTitle
import io.github.eslamasabry.oc.ui.words.res.storageAccessWhyAgent
import io.github.eslamasabry.oc.ui.words.res.storageAccessWhyOff
import io.github.eslamasabry.oc.ui.words.res.storageAccessWhyScope
import org.jetbrains.compose.resources.stringResource

/**
 * D02 (07 §5): one folder of the conversation's project, arranged from kit parts only. Folders and
 * files are plain rows (a folder has the chevron); Show hidden files is a visible bar action, never
 * a gesture; search sits under the bar and searches the whole project by name.
 */
@Composable
public fun FolderScreen(state: FolderUi, actions: FolderActions) {
    val title =
        if (state.path.isEmpty()) stringResource(Res.string.filesProjectRoot) else state.name
    val hiddenLabel =
        stringResource(
            if (state.showHidden) Res.string.filesHideHidden else Res.string.filesShowHidden
        )
    val terminalLabel = stringResource(Res.string.libraryTerminalTitle)
    val barActions = buildList {
        if (state.canTerminal) add(KitLineAction(terminalLabel, actions::terminal))
        if (state.phase == FolderPhase.Ready && (state.hiddenCount > 0 || state.showHidden)) {
            add(KitLineAction(hiddenLabel, actions::toggleHidden, testTag = "files-show-hidden"))
        }
    }
    val searchLabel = stringResource(Res.string.readerUiSearchFiles)
    KitScreen(
        topBar = {
            KitTopBar(
                title = title,
                subtitle = state.path.ifEmpty { null },
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
                actions = barActions,
            )
        },
        search =
            if (state.phase == FolderPhase.Ready || state.query != null)
                ({ KitSearchField(searchLabel, actions::search) })
            else null,
        width = KitScreenWidth.List,
    ) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
            val query = state.query
            when {
                state.storageBlocked ->
                    item(key = "storage-access") {
                        KitNotice(
                            title = stringResource(Res.string.storageAccessTitle),
                            message = stringResource(Res.string.storageAccessBody),
                            notes =
                                listOf(
                                    stringResource(Res.string.storageAccessWhyScope),
                                    stringResource(Res.string.storageAccessWhyAgent),
                                    stringResource(Res.string.storageAccessWhyOff),
                                ),
                            actions =
                                if (state.requestingStorage) emptyList()
                                else
                                    listOf(
                                        KitLineAction(
                                            stringResource(Res.string.storageAccessAllow),
                                            actions::requestStorageAccess,
                                        )
                                    ),
                        )
                    }
                query != null -> searchItems(state, actions)
                state.phase == FolderPhase.Loading -> item(key = "loading") { KitSkeletonRows() }
                state.phase == FolderPhase.NotConnected ->
                    item(key = "not-connected") {
                        KitStateView(
                            kind = KitStateKind.Error,
                            title = stringResource(Res.string.filesNotConnected),
                            onRetry = actions::retry,
                            retryLabel = stringResource(Res.string.kitTryAgain),
                        )
                    }
                state.phase == FolderPhase.Unavailable ->
                    item(key = "unavailable") {
                        KitStateView(
                            kind = KitStateKind.Calm,
                            title = stringResource(Res.string.filesUnavailable),
                        )
                    }
                state.phase == FolderPhase.Failed ->
                    item(key = "failed") {
                        KitStateView(
                            kind = KitStateKind.Error,
                            title = stringResource(Res.string.filesLoadFailedTitle),
                            failure = state.failure,
                            onRetry = actions::retry,
                            retryLabel = stringResource(Res.string.kitTryAgain),
                        )
                    }
                state.onlyHidden ->
                    item(key = "only-hidden") {
                        KitStateView(
                            kind = KitStateKind.Calm,
                            title = stringResource(Res.string.filesOnlyHidden),
                            primary =
                                KitAction(
                                    stringResource(Res.string.filesShowHidden),
                                    actions::toggleHidden,
                                ),
                        )
                    }
                state.empty ->
                    item(key = "empty") {
                        KitStateView(
                            kind = KitStateKind.Empty,
                            title = stringResource(Res.string.chatStartEmptyFolder),
                        )
                    }
                else -> items(state.entries, key = { it.path }) { node -> EntryRow(node, actions) }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.searchItems(
    state: FolderUi,
    actions: FolderActions,
) {
    val failure = state.searchFailure
    when {
        state.searching -> item(key = "searching") { KitSkeletonRows(count = 3) }
        failure != null ->
            item(key = "search-failed") {
                KitErrorNotice(
                    stringResource(Res.string.filesLoadFailedTitle),
                    failure = failure,
                    detailsLabel = stringResource(Res.string.kitDetails),
                    copyLabel = stringResource(Res.string.kitCopyDetails),
                    retry = KitLineAction(stringResource(Res.string.kitTryAgain), actions::retry),
                )
            }
        state.results.isEmpty() ->
            item(key = "no-results") {
                KitStateView(
                    kind = KitStateKind.Empty,
                    title = stringResource(Res.string.readerUiNoFiles),
                    body = stringResource(Res.string.readerUiTryFileName),
                )
            }
        else ->
            items(state.results, key = { "found:" + it.path }) { node ->
                KitRow(
                    title = node.name,
                    supporting = node.path,
                    titleIsFileName = true,
                    onClick = { actions.open(node) },
                )
            }
    }
}

@Composable
private fun EntryRow(node: FileNode, actions: FolderActions) {
    KitRow(
        title = node.name,
        titleIsFileName = true,
        trailing = if (node.isDir) KitRowTrailing.Chevron else KitRowTrailing.None,
        onClick = { actions.open(node) },
    )
}
