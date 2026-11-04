package io.github.eslamasabry.oc.feature.files.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.model.FileNode
import io.github.eslamasabry.oc.feature.files.presentation.FileNotice
import io.github.eslamasabry.oc.feature.files.presentation.FileSearchSurface
import io.github.eslamasabry.oc.feature.files.presentation.FilesStorageBlock
import io.github.eslamasabry.oc.feature.files.presentation.FilesStorageResult
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
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kitchat.header.KitClipboardEffect
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.chatStartEmptyFolder
import io.github.eslamasabry.oc.ui.words.res.fileReload
import io.github.eslamasabry.oc.ui.words.res.filesAccessNeededBody
import io.github.eslamasabry.oc.ui.words.res.filesAccessNeededTitle
import io.github.eslamasabry.oc.ui.words.res.filesAskAboutFile
import io.github.eslamasabry.oc.ui.words.res.filesAttachFailed
import io.github.eslamasabry.oc.ui.words.res.filesAttachmentReady
import io.github.eslamasabry.oc.ui.words.res.filesChangeAdded
import io.github.eslamasabry.oc.ui.words.res.filesChangeDeleted
import io.github.eslamasabry.oc.ui.words.res.filesChangeModified
import io.github.eslamasabry.oc.ui.words.res.filesCopyName
import io.github.eslamasabry.oc.ui.words.res.filesHideHidden
import io.github.eslamasabry.oc.ui.words.res.filesLoadFailedTitle
import io.github.eslamasabry.oc.ui.words.res.filesLoadingFolder
import io.github.eslamasabry.oc.ui.words.res.filesMentionFailed
import io.github.eslamasabry.oc.ui.words.res.filesNoTextBody
import io.github.eslamasabry.oc.ui.words.res.filesNoTextTitle
import io.github.eslamasabry.oc.ui.words.res.filesNotConnected
import io.github.eslamasabry.oc.ui.words.res.filesOnlyHidden
import io.github.eslamasabry.oc.ui.words.res.filesOpenPhone
import io.github.eslamasabry.oc.ui.words.res.filesPreferencesFailed
import io.github.eslamasabry.oc.ui.words.res.filesProjectRoot
import io.github.eslamasabry.oc.ui.words.res.filesReviewStorage
import io.github.eslamasabry.oc.ui.words.res.filesRowActions
import io.github.eslamasabry.oc.ui.words.res.filesScopedEmpty
import io.github.eslamasabry.oc.ui.words.res.filesSearchInside
import io.github.eslamasabry.oc.ui.words.res.filesSearchNames
import io.github.eslamasabry.oc.ui.words.res.filesSearchText
import io.github.eslamasabry.oc.ui.words.res.filesSearching
import io.github.eslamasabry.oc.ui.words.res.filesSelectedStorageBody
import io.github.eslamasabry.oc.ui.words.res.filesShowHidden
import io.github.eslamasabry.oc.ui.words.res.filesStorageFailed
import io.github.eslamasabry.oc.ui.words.res.filesStorageRestart
import io.github.eslamasabry.oc.ui.words.res.filesTextHintBody
import io.github.eslamasabry.oc.ui.words.res.filesTextLimited
import io.github.eslamasabry.oc.ui.words.res.filesTextMatchLine
import io.github.eslamasabry.oc.ui.words.res.filesTextSurface
import io.github.eslamasabry.oc.ui.words.res.filesUnavailable
import io.github.eslamasabry.oc.ui.words.res.filesViewOptions
import io.github.eslamasabry.oc.ui.words.res.kitCopyDetails
import io.github.eslamasabry.oc.ui.words.res.kitDetails
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.kitTopBarClose
import io.github.eslamasabry.oc.ui.words.res.kitTryAgain
import io.github.eslamasabry.oc.ui.words.res.readerUiAttachPrompt
import io.github.eslamasabry.oc.ui.words.res.readerUiChangedCount
import io.github.eslamasabry.oc.ui.words.res.readerUiCopyPath
import io.github.eslamasabry.oc.ui.words.res.readerUiIndicatorsFailed
import io.github.eslamasabry.oc.ui.words.res.libraryTerminalTitle
import io.github.eslamasabry.oc.ui.words.res.readerUiNoFiles
import io.github.eslamasabry.oc.ui.words.res.readerUiNoSymbols
import io.github.eslamasabry.oc.ui.words.res.readerUiOpenReview
import io.github.eslamasabry.oc.ui.words.res.readerUiSearchFiles
import io.github.eslamasabry.oc.ui.words.res.readerUiSearchSymbols
import io.github.eslamasabry.oc.ui.words.res.readerUiServerOrder
import io.github.eslamasabry.oc.ui.words.res.readerUiSourceFirst
import io.github.eslamasabry.oc.ui.words.res.readerUiSymbolClass
import io.github.eslamasabry.oc.ui.words.res.readerUiSymbolConstant
import io.github.eslamasabry.oc.ui.words.res.readerUiSymbolConstructor
import io.github.eslamasabry.oc.ui.words.res.readerUiSymbolEnum
import io.github.eslamasabry.oc.ui.words.res.readerUiSymbolEnummember
import io.github.eslamasabry.oc.ui.words.res.readerUiSymbolEvent
import io.github.eslamasabry.oc.ui.words.res.readerUiSymbolField
import io.github.eslamasabry.oc.ui.words.res.readerUiSymbolFile
import io.github.eslamasabry.oc.ui.words.res.readerUiSymbolFunction
import io.github.eslamasabry.oc.ui.words.res.readerUiSymbolInterface
import io.github.eslamasabry.oc.ui.words.res.readerUiSymbolMethod
import io.github.eslamasabry.oc.ui.words.res.readerUiSymbolModule
import io.github.eslamasabry.oc.ui.words.res.readerUiSymbolNamespace
import io.github.eslamasabry.oc.ui.words.res.readerUiSymbolOperator
import io.github.eslamasabry.oc.ui.words.res.readerUiSymbolPackage
import io.github.eslamasabry.oc.ui.words.res.readerUiSymbolProperty
import io.github.eslamasabry.oc.ui.words.res.readerUiSymbolStruct
import io.github.eslamasabry.oc.ui.words.res.readerUiSymbolSymbol
import io.github.eslamasabry.oc.ui.words.res.readerUiSymbolTypeparameter
import io.github.eslamasabry.oc.ui.words.res.readerUiSymbolVariable
import io.github.eslamasabry.oc.ui.words.res.readerUiSymbols
import io.github.eslamasabry.oc.ui.words.res.readerUiSymbolsHint
import io.github.eslamasabry.oc.ui.words.res.readerUiTryFileName
import io.github.eslamasabry.oc.ui.words.res.storageAccessAllow
import io.github.eslamasabry.oc.ui.words.res.storageAccessBody
import io.github.eslamasabry.oc.ui.words.res.storageAccessTitle
import io.github.eslamasabry.oc.ui.words.res.storageAccessWhyAgent
import io.github.eslamasabry.oc.ui.words.res.storageAccessWhyOff
import io.github.eslamasabry.oc.ui.words.res.storageAccessWhyScope
import org.jetbrains.compose.resources.pluralStringResource
import io.github.eslamasabry.oc.ui.words.res.stateReaderSaveFailed
import org.jetbrains.compose.resources.stringResource

/** The project's real file, text and symbol reads; every uncommon act also has a visible button. */
@Composable
public fun FolderScreen(state: FolderUi, actions: FolderActions) {
    var viewOptions by remember { mutableStateOf(false) }
    val title =
        if (state.path.isEmpty()) stringResource(Res.string.filesProjectRoot) else state.name
    val loadingSince =
        remember(state.phase, state.query, state.surface, state.searching) {
            kotlin.time.TimeSource.Monotonic.markNow()
        }
    val surfaceLabel =
        stringResource(
            when (state.surface) {
                FileSearchSurface.Files -> Res.string.readerUiSearchFiles
                FileSearchSurface.Text -> Res.string.filesSearchText
                FileSearchSurface.Symbols -> Res.string.readerUiSearchSymbols
            }
        )
    KitClipboardEffect(state.clipId, state.clipText, actions::clipDone)
    KitSheetHost(Modifier.fillMaxSize()) {
        KitScreen(
            topBar = {
                KitTopBar(
                    title = title,
                    subtitle = state.path.ifEmpty { null },
                    exit = KitTopBarExit.Back,
                    exitLabel = stringResource(Res.string.kitTopBarBack),
                    onExit = actions::back,
                    actions =
                        buildList {
                            // The project's terminal, only where the server offers one.
                            if (state.canTerminal)
                                add(
                                    KitLineAction(
                                        stringResource(Res.string.libraryTerminalTitle),
                                        actions::terminal,
                                    )
                                )
                            add(KitLineAction(stringResource(Res.string.fileReload), actions::retry))
                            add(
                                KitLineAction(
                                    stringResource(Res.string.filesViewOptions),
                                    { viewOptions = true },
                                )
                            )
                        },
                )
            },
            search =
                if (state.phase == FolderPhase.Ready || state.query != null)
                    ({
                        KitSearchField(
                            surfaceLabel,
                            actions::search,
                            activeFilter =
                                if (state.surface == FileSearchSurface.Files) null
                                else surfaceLabel,
                            removeFilterLabel = stringResource(Res.string.filesSearchNames),
                            onClearFilter = { actions.selectSurface(FileSearchSurface.Files) },
                        )
                    })
                else null,
            width = KitScreenWidth.List,
        ) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
                if (state.actionFailure != null)
                    item("action-failed") {
                        KitErrorNotice(
                            stringResource(Res.string.filesMentionFailed),
                            failure = state.actionFailure,
                            detailsLabel = stringResource(Res.string.kitDetails),
                            copyLabel = stringResource(Res.string.kitCopyDetails),
                        )
                    }
                if (state.storageResult == FilesStorageResult.Failed)
                    item("storage-failed") {
                        KitStateView(
                            KitStateKind.Calm,
                            stringResource(Res.string.filesStorageFailed),
                            primary =
                                KitAction(
                                    stringResource(Res.string.filesReviewStorage),
                                    actions::reviewStorage,
                                ),
                        )
                    }
                if (state.storageResult == FilesStorageResult.RestartRequired)
                    item("storage-restart") {
                        KitStateView(
                            KitStateKind.Calm,
                            stringResource(Res.string.filesStorageRestart),
                            primary =
                                KitAction(
                                    stringResource(Res.string.filesOpenPhone),
                                    actions::openPhone,
                                ),
                            tertiary =
                                listOf(
                                    KitAction(stringResource(Res.string.fileReload), actions::retry)
                                ),
                        )
                    }
                if (state.attachNotice != null)
                    item("attach-notice") {
                        KitNotice(
                            stringResource(
                                if (state.attachNotice == FileNotice.Attached)
                                    Res.string.filesAttachmentReady
                                else Res.string.filesAttachFailed
                            )
                        )
                    }
                if (state.preferencesFailed)
                    item("preferences-warning") {
                        KitNotice(stringResource(Res.string.filesPreferencesFailed))
                    }
                if (state.changesFailure != null)
                    item("change-warning") {
                        KitNotice(stringResource(Res.string.readerUiIndicatorsFailed))
                    }
                when {
                    state.query != null || state.surface != FileSearchSurface.Files ->
                        searchItems(state, actions, loadingSince)
                    state.phase == FolderPhase.Loading ->
                        item("loading") {
                            KitStateView(
                                KitStateKind.Loading,
                                stringResource(Res.string.filesLoadingFolder),
                                since = loadingSince,
                                onSlow =
                                    listOf(
                                        KitAction(
                                            stringResource(Res.string.kitTryAgain),
                                            actions::retry,
                                        )
                                    ),
                            )
                        }
                    state.phase == FolderPhase.NotConnected ->
                        item("not-connected") {
                            KitStateView(
                                KitStateKind.Error,
                                stringResource(Res.string.filesNotConnected),
                                onRetry = actions::retry,
                                retryLabel = stringResource(Res.string.kitTryAgain),
                            )
                        }
                    state.phase == FolderPhase.Unavailable ->
                        item("unavailable") {
                            KitStateView(
                                KitStateKind.Calm,
                                stringResource(Res.string.filesUnavailable),
                            )
                        }
                    state.phase == FolderPhase.Failed ->
                        item("failed") {
                            KitStateView(
                                KitStateKind.Error,
                                stringResource(Res.string.filesLoadFailedTitle),
                                failure = state.failure,
                                onRetry = actions::retry,
                                retryLabel = stringResource(Res.string.kitTryAgain),
                            )
                        }
                    state.storageBlock != FilesStorageBlock.None &&
                        state.storageResult != FilesStorageResult.RestartRequired ->
                        item("storage-access") {
                            KitStateView(
                                KitStateKind.Calm,
                                stringResource(Res.string.filesAccessNeededTitle),
                                body =
                                    stringResource(
                                        if (state.storageBlock == FilesStorageBlock.SelectedFolder)
                                            Res.string.filesSelectedStorageBody
                                        else Res.string.filesAccessNeededBody
                                    ),
                                primary =
                                    KitAction(
                                        stringResource(Res.string.storageAccessAllow),
                                        actions::allowStorage,
                                        working = state.requestingStorage,
                                    ),
                                secondary =
                                    if (
                                        state.storageBlock == FilesStorageBlock.SelectedFolder &&
                                            state.storageResult != FilesStorageResult.Failed
                                    )
                                        KitAction(
                                            stringResource(Res.string.filesReviewStorage),
                                            actions::reviewStorage,
                                        )
                                    else null,
                                tertiary =
                                    listOf(
                                        KitAction(
                                            stringResource(Res.string.fileReload),
                                            actions::retry,
                                        )
                                    ),
                            )
                        }
                    state.onlyHidden ->
                        item("only-hidden") {
                            KitStateView(
                                KitStateKind.Calm,
                                stringResource(Res.string.filesOnlyHidden),
                                body = stringResource(Res.string.filesScopedEmpty),
                                primary =
                                    KitAction(
                                        stringResource(Res.string.filesShowHidden),
                                        actions::toggleHidden,
                                    ),
                            )
                        }
                    state.empty ->
                        item("empty") {
                            KitStateView(
                                KitStateKind.Empty,
                                stringResource(Res.string.chatStartEmptyFolder),
                                body = stringResource(Res.string.filesScopedEmpty),
                                primary =
                                    KitAction(
                                        stringResource(Res.string.fileReload),
                                        actions::retry,
                                    ),
                            )
                        }
                    else ->
                        items(state.entries, key = { it.path }) { node ->
                            EntryRow(node, state, actions)
                        }
                }
            }
        }
        KitConfirm(
            if (state.confirmingStorage)
                KitConfirmSpec(
                    title = stringResource(Res.string.storageAccessTitle),
                    confirmLabel = stringResource(Res.string.storageAccessAllow),
                    body = stringResource(Res.string.storageAccessBody),
                    consequences =
                        listOf(
                            stringResource(Res.string.storageAccessWhyScope),
                            stringResource(Res.string.storageAccessWhyAgent),
                            stringResource(Res.string.storageAccessWhyOff),
                        ),
                )
            else null,
            actions::confirmStorage,
            actions::cancelStorage,
        )
        KitSheet(
            visible = viewOptions,
            onDismissRequest = { viewOptions = false },
            title = stringResource(Res.string.filesViewOptions),
            closeLabel = stringResource(Res.string.kitTopBarClose),
        ) {
            Column {
                KitRow(
                    stringResource(Res.string.filesSearchNames),
                    selected = state.surface == FileSearchSurface.Files,
                    onClick = {
                        actions.selectSurface(FileSearchSurface.Files)
                        viewOptions = false
                    },
                )
                if (state.canSearchText)
                    KitRow(
                        stringResource(Res.string.filesTextSurface),
                        selected = state.surface == FileSearchSurface.Text,
                        onClick = {
                            actions.selectSurface(FileSearchSurface.Text)
                            viewOptions = false
                        },
                    )
                if (state.canSearchSymbols)
                    KitRow(
                        stringResource(Res.string.readerUiSymbols),
                        selected = state.surface == FileSearchSurface.Symbols,
                        onClick = {
                            actions.selectSurface(FileSearchSurface.Symbols)
                            viewOptions = false
                        },
                    )
                KitRow(
                    stringResource(
                        if (state.showHidden) Res.string.filesHideHidden
                        else Res.string.filesShowHidden
                    ),
                    onClick = {
                        actions.toggleHidden()
                        viewOptions = false
                    },
                )
                if (state.canOrder)
                    KitRow(
                        stringResource(
                            if (state.sourceFirst) Res.string.readerUiServerOrder
                            else Res.string.readerUiSourceFirst
                        ),
                        onClick = {
                            actions.sourceFirst()
                            viewOptions = false
                        },
                    )
            }
        }
        val node = state.actionsFor
        KitSheet(
            visible = node != null,
            onDismissRequest = actions::closeActions,
            title = node?.name ?: stringResource(Res.string.filesRowActions),
            closeLabel = stringResource(Res.string.kitTopBarClose),
        ) {
            if (node != null)
                Column {
                    val deleted = state.changes[node.path]?.status == "deleted"
                    if (!node.isDir && !deleted)
                        KitRow(stringResource(Res.string.filesAskAboutFile), onClick = actions::ask)
                    if (!node.isDir && !deleted && state.canAttach && !state.attaching)
                        KitRow(
                            stringResource(Res.string.readerUiAttachPrompt),
                            onClick = actions::attach,
                        )
                    if (state.changes[node.path] != null)
                        KitRow(
                            stringResource(Res.string.readerUiOpenReview),
                            onClick = actions::review,
                        )
                    KitRow(stringResource(Res.string.readerUiCopyPath), onClick = actions::copyPath)
                    KitRow(stringResource(Res.string.filesCopyName), onClick = actions::copyName)
                }
        }
    }
}

private fun LazyListScope.searchItems(
    state: FolderUi,
    actions: FolderActions,
    since: kotlin.time.TimeMark,
) {
    if (state.surface == FileSearchSurface.Files && state.canSearchText && state.query != null)
        item("inside") {
            KitRow(
                stringResource(Res.string.filesSearchInside, state.query),
                onClick = { actions.selectSurface(FileSearchSurface.Text) },
            )
        }
    when {
        state.searching ->
            item("searching") {
                KitStateView(
                    KitStateKind.Loading,
                    stringResource(Res.string.filesSearching),
                    since = since,
                    onSlow =
                        listOf(KitAction(stringResource(Res.string.kitTryAgain), actions::retry)),
                )
            }
        state.searchFailure != null ->
            item("search-failed") {
                KitErrorNotice(
                    stringResource(Res.string.filesLoadFailedTitle),
                    failure = state.searchFailure,
                    detailsLabel = stringResource(Res.string.kitDetails),
                    copyLabel = stringResource(Res.string.kitCopyDetails),
                    retry = KitLineAction(stringResource(Res.string.kitTryAgain), actions::retry),
                )
            }
        state.query == null ->
            item("hint") {
                KitStateView(
                    KitStateKind.Calm,
                    stringResource(
                        if (state.surface == FileSearchSurface.Symbols)
                            Res.string.readerUiSymbolsHint
                        else Res.string.filesTextHintBody
                    ),
                )
            }
        state.surface == FileSearchSurface.Text -> {
            if (state.textMatches.isEmpty())
                item("empty-text") {
                    KitStateView(
                        KitStateKind.Empty,
                        stringResource(Res.string.filesNoTextTitle),
                        body = stringResource(Res.string.filesNoTextBody),
                    )
                }
            items(state.textMatches, key = { "${it.path}:${it.lineNumber}:${it.snippet}" }) { match
                ->
                KitRow(
                    match.snippet.trim().ifEmpty { match.path.substringAfterLast('/') },
                    supporting =
                        stringResource(Res.string.filesTextMatchLine, match.lineNumber) +
                            " · " +
                            match.path,
                    supportingMaxLines = 2,
                    onClick = { actions.openMatch(match) },
                )
            }
            if (state.resultsLimited)
                item("limited") {
                    KitNotice(stringResource(Res.string.filesTextLimited, state.textMatches.size))
                }
        }
        state.surface == FileSearchSurface.Symbols -> {
            if (state.symbols.isEmpty())
                item("empty-symbols") {
                    KitStateView(KitStateKind.Empty, stringResource(Res.string.readerUiNoSymbols))
                }
            items(state.symbols, key = { "${it.path}:${it.line}:${it.column}:${it.name}" }) { symbol
                ->
                KitRow(
                    symbol.name,
                    supporting =
                        symbolKind(symbol.kind) +
                            " · ${symbol.path}:${symbol.line}:${symbol.column}",
                    supportingMaxLines = 2,
                    onClick = { actions.openSymbol(symbol) },
                )
            }
        }
        state.results.isEmpty() ->
            item("no-results") {
                KitStateView(
                    KitStateKind.Empty,
                    stringResource(Res.string.readerUiNoFiles),
                    body = stringResource(Res.string.readerUiTryFileName),
                )
            }
        else ->
            items(state.results, key = { "found:" + it.path }) { node ->
                EntryRow(node, state, actions)
            }
    }
}

@Composable
private fun EntryRow(node: FileNode, state: FolderUi, actions: FolderActions) {
    val change = state.changes[node.path]
    val descendants =
        if (node.isDir) state.changes.keys.count { it.startsWith(node.path + "/") } else 0
    val detail = buildList {
        if (state.query != null) add(node.path)
        if (change != null)
            add(
                stringResource(
                    when (change.status) {
                        "deleted" -> Res.string.filesChangeDeleted
                        "added" -> Res.string.filesChangeAdded
                        else -> Res.string.filesChangeModified
                    }
                )
            )
        else if (descendants > 0)
            add(pluralStringResource(Res.plurals.readerUiChangedCount, descendants, descendants))
    }
        .joinToString(" · ")
        .ifEmpty { null }
    KitRow(
        title = node.name,
        supporting = detail,
        supportingMaxLines = 2,
        titleIsFileName = true,
        trailing =
            KitRowTrailing.Action(
                KitLineAction(
                    stringResource(Res.string.filesRowActions),
                    { actions.showActions(node) },
                )
            ),
        onClick = { actions.open(node) },
    )
}

@Composable
private fun symbolKind(kind: Int): String =
    stringResource(
        when (kind) {
            1 -> Res.string.readerUiSymbolFile
            2 -> Res.string.readerUiSymbolModule
            3 -> Res.string.readerUiSymbolNamespace
            4 -> Res.string.readerUiSymbolPackage
            5 -> Res.string.readerUiSymbolClass
            6 -> Res.string.readerUiSymbolMethod
            7 -> Res.string.readerUiSymbolProperty
            8 -> Res.string.readerUiSymbolField
            9 -> Res.string.readerUiSymbolConstructor
            10 -> Res.string.readerUiSymbolEnum
            11 -> Res.string.readerUiSymbolInterface
            12 -> Res.string.readerUiSymbolFunction
            13 -> Res.string.readerUiSymbolVariable
            14 -> Res.string.readerUiSymbolConstant
            22 -> Res.string.readerUiSymbolEnummember
            23 -> Res.string.readerUiSymbolStruct
            24 -> Res.string.readerUiSymbolEvent
            25 -> Res.string.readerUiSymbolOperator
            26 -> Res.string.readerUiSymbolTypeparameter
            else -> Res.string.readerUiSymbolSymbol
        }
    )
