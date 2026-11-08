package io.github.eslamasabry.oc.feature.projects.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.eslamasabry.oc.core.domain.platform.SharedStorageBlock
import io.github.eslamasabry.oc.feature.projects.presentation.ChatTitle
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectChatUi
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectPageActions
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectPagePhase
import io.github.eslamasabry.oc.feature.projects.presentation.ProjectPageUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextRole
import io.github.eslamasabry.oc.ui.kit.action.rememberKitCopyAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.agentElsewhereCounts
import io.github.eslamasabry.oc.ui.words.res.agentElsewhereStale
import io.github.eslamasabry.oc.ui.words.res.agentProjectWorkspace
import io.github.eslamasabry.oc.ui.words.res.chatsHomeRunning
import io.github.eslamasabry.oc.ui.words.res.e7LibraryProjectHealth
import io.github.eslamasabry.oc.ui.words.res.kitCopied
import io.github.eslamasabry.oc.ui.words.res.projectHubCopyFolderPath
import io.github.eslamasabry.oc.ui.words.res.shellTabFiles
import io.github.eslamasabry.oc.ui.words.res.globalSessionsLoadFailedTitle
import io.github.eslamasabry.oc.ui.words.res.globalSessionsLoadMore
import io.github.eslamasabry.oc.ui.words.res.globalSessionsLoadMoreFailed
import io.github.eslamasabry.oc.ui.words.res.globalSessionsUnknownLocation
import io.github.eslamasabry.oc.ui.words.res.isolatedTaskCopies
import io.github.eslamasabry.oc.ui.words.res.isolatedTaskIntro
import io.github.eslamasabry.oc.ui.words.res.isolatedTaskRefreshCopies
import io.github.eslamasabry.oc.ui.words.res.isolatedTaskTitle
import io.github.eslamasabry.oc.ui.words.res.kitCopyDetails
import io.github.eslamasabry.oc.ui.words.res.kitDetails
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.kitTryAgain
import io.github.eslamasabry.oc.ui.words.res.linuxSvcTitle
import io.github.eslamasabry.oc.ui.words.res.navProjectChangesCount
import io.github.eslamasabry.oc.ui.words.res.navProjectDevServer
import io.github.eslamasabry.oc.ui.words.res.p12CreateFailed
import io.github.eslamasabry.oc.ui.words.res.p12ElsewhereTitle
import io.github.eslamasabry.oc.ui.words.res.p12NewHere
import io.github.eslamasabry.oc.ui.words.res.p12NewHereBody
import io.github.eslamasabry.oc.ui.words.res.p12NoServerBody
import io.github.eslamasabry.oc.ui.words.res.p12NoServerTitle
import io.github.eslamasabry.oc.ui.words.res.p12ProjectConversations
import io.github.eslamasabry.oc.ui.words.res.p12ProjectNoConversations
import io.github.eslamasabry.oc.ui.words.res.scheduleTitle
import io.github.eslamasabry.oc.ui.words.res.serversTitle
import io.github.eslamasabry.oc.ui.words.res.servicesSubtitle
import io.github.eslamasabry.oc.ui.words.res.servicesTitle
import io.github.eslamasabry.oc.ui.words.res.stateStorageAppAction
import io.github.eslamasabry.oc.ui.words.res.stateStorageAppBody
import io.github.eslamasabry.oc.ui.words.res.stateStorageAppTitle
import io.github.eslamasabry.oc.ui.words.res.terminal40Title
import io.github.eslamasabry.oc.ui.words.res.workspaceNewSession
import org.jetbrains.compose.resources.stringResource

/**
 * One project (07 §5 D15). Kit parts only (K1). "New conversation here" is the first, visible act;
 * then this project's conversations with a visible Load more, then "In other projects" (B-NAV-010).
 */
@Composable
public fun ProjectPageScreen(
    state: ProjectPageUi,
    actions: ProjectPageActions,
    onDevServices: (() -> Unit)? = null,
    onTerminal: (() -> Unit)? = null,
) {
    LifecycleResumeEffect(actions) {
        actions.onResume()
        onPauseOrDispose {}
    }
    KitScreen(
        topBar = {
            KitTopBar(
                title = state.name,
                subtitle = state.directory,
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        width = KitScreenWidth.Reading,
    ) {
        IsolatedTaskSheet(state, actions)
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
            contentPadding = PaddingValues(top = Space.S2.dp, bottom = Space.S4.dp),
        ) {
            if (state.storageBlock != SharedStorageBlock.None) {
                item(key = "shared-storage-access") { SharedStorageNotice(state, actions) }
                return@LazyColumn
            }
            if (state.phase == ProjectPagePhase.Opening) {
                item(key = "opening") { KitSkeletonRows() }
                return@LazyColumn
            }
            if (state.phase == ProjectPagePhase.NoServer) {
                item(key = "no-server") {
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
                return@LazyColumn
            }
            // N-50: the page's one act on the gutter, full width; the button already says where it starts.
            item(key = "new") {
                Box(Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)) {
                    KitButton(
                        label = stringResource(Res.string.p12NewHere),
                        onClick = actions::newConversationHere,
                        role = KitButtonRole.Primary,
                        working = state.creating,
                        fullWidth = true,
                    )
                }
            }
            // N-50: the project's places in one group (separate copy, running server, Terminal, services, changes),
            // one rhythm and one divider style, instead of four loose blocks with uneven gaps.
            item(key = "places") {
                KitRowGroup {
                    if (state.canStartIsolated)
                        KitRow(
                            title = stringResource(Res.string.isolatedTaskTitle),
                            supporting = stringResource(Res.string.isolatedTaskIntro),
                            trailing = KitRowTrailing.Chevron,
                            onClick = actions::startIsolatedTask,
                        )
                    state.devServerPort?.let { port ->
                        KitRow(
                            title = stringResource(Res.string.navProjectDevServer, port),
                            onClick = actions::developmentServices,
                        )
                    }
                    // Flutter's project hub is the project's Files; here it is one row, no conversation needed.
                    if (state.canBrowseFiles)
                        KitRow(
                            title = stringResource(Res.string.shellTabFiles),
                            trailing = KitRowTrailing.Chevron,
                            onClick = actions::openFiles,
                        )
                    if (onTerminal != null)
                        KitRow(
                            title = stringResource(Res.string.terminal40Title),
                            trailing = KitRowTrailing.Chevron,
                            onClick = onTerminal,
                        )
                    KitRow(
                        title = stringResource(Res.string.servicesTitle),
                        supporting = stringResource(Res.string.servicesSubtitle),
                        trailing = KitRowTrailing.Chevron,
                        onClick = onDevServices ?: actions::developmentServices,
                    )
                    if (state.canSchedule)
                        KitRow(
                            title = stringResource(Res.string.scheduleTitle),
                            trailing = KitRowTrailing.Chevron,
                            onClick = actions::openSchedules,
                        )
                    if (state.workspace.available)
                        KitRow(
                            title =
                                state.changesCount?.let {
                                    stringResource(Res.string.navProjectChangesCount, it)
                                } ?: stringResource(Res.string.agentProjectWorkspace),
                            trailing = KitRowTrailing.Chevron,
                            onClick = actions::openWorkspace,
                        )
                    if (state.canHealth)
                        KitRow(
                            title = stringResource(Res.string.e7LibraryProjectHealth),
                            trailing = KitRowTrailing.Chevron,
                            onClick = actions::openHealth,
                        )
                    CopyFolderPathRow(state.directory)
                }
            }
            // N-50: the project itself is not one of its separate copies; the group shows only real copies.
            val copies = state.copies.filter { it.directory.trimEnd('/') != state.directory.trimEnd('/') }
            if (state.canStartIsolated && (copies.isNotEmpty() || state.copiesFailure != null))
                item(key = "copies") {
                    KitRowGroup(label = stringResource(Res.string.isolatedTaskCopies)) {
                        copies.forEach { copy ->
                            KitRow(
                                title = copy.name,
                                supporting = copy.branch,
                                trailing = KitRowTrailing.Chevron,
                                onClick = { actions.openCopy(copy.directory) },
                            )
                        }
                        state.copiesFailure?.let {
                            KitErrorNotice(
                                stringResource(Res.string.isolatedTaskCopies),
                                failure = it,
                            )
                        }
                    }
                }
            state.createFailure?.let { failure ->
                item(key = "create-failed") {
                    // N-50: a notice on the page keeps the gutter, like the button above it.
                    KitErrorNotice(
                        modifier = Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp),
                        message = stringResource(Res.string.p12CreateFailed),
                        failure = failure,
                        detailsLabel = stringResource(Res.string.kitDetails),
                        copyLabel = stringResource(Res.string.kitCopyDetails),
                        retry =
                            KitLineAction(
                                stringResource(Res.string.kitTryAgain),
                                actions::newConversationHere,
                            ),
                    )
                }
            }
            when (state.phase) {
                ProjectPagePhase.Opening -> item(key = "opening") { KitSkeletonRows() }
                ProjectPagePhase.Failed ->
                    item(key = "failed") {
                        KitStateView(
                            kind = KitStateKind.Error,
                            title = stringResource(Res.string.globalSessionsLoadFailedTitle),
                            failure = state.failure,
                            onRetry = actions::retry,
                            retryLabel = stringResource(Res.string.kitTryAgain),
                        )
                    }
                ProjectPagePhase.NoServer -> Unit
                ProjectPagePhase.Ready -> {
                    item(key = "here") {
                        KitRowGroup(label = stringResource(Res.string.p12ProjectConversations)) {
                            if (state.conversations.isEmpty())
                                KitGroupNote(stringResource(Res.string.p12ProjectNoConversations))
                            state.conversations.forEach {
                                ChatRow(it, showProject = false, actions = actions)
                            }
                        }
                    }
                    // N-50: Load more starts on the gutter, never at the screen's edge.
                    if (state.canLoadMore)
                        item(key = "more") {
                            Box(Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)) {
                                LoadMore(state, actions)
                            }
                        }
                    if (state.elsewhere.isNotEmpty() || state.elsewhereAttention.isNotEmpty()) {
                        item(key = "elsewhere") {
                            KitRowGroup(label = stringResource(Res.string.p12ElsewhereTitle)) {
                                state.elsewhereAttention.forEach { project ->
                                    KitRow(
                                        title = project.name,
                                        supporting =
                                            stringResource(
                                                Res.string.agentElsewhereCounts,
                                                project.waiting,
                                                project.running,
                                                project.failed,
                                            ) +
                                                if (project.stale)
                                                    " · " +
                                                        stringResource(
                                                            Res.string.agentElsewhereStale
                                                        )
                                                else "",
                                        onClick = { actions.openOtherProject(project.directory) },
                                    )
                                }
                                state.elsewhere.forEach {
                                    ChatRow(it, showProject = true, actions = actions)
                                }
                            }
                        }
                    }
                }
            }
        }
        ProjectWorkspaceSheet(state.workspace, actions)
    }
}

/** Flutter's hub "Copy folder path": copies the project's folder, then reads Copied for a moment. */
@Composable
private fun CopyFolderPathRow(directory: String) {
    if (directory.isEmpty()) return
    val copy =
        rememberKitCopyAction(
            stringResource(Res.string.projectHubCopyFolderPath),
            stringResource(Res.string.kitCopied),
            { directory },
        )
    KitRow(title = copy.label, onClick = { copy.onClick?.invoke() })
}

@Composable
private fun LoadMore(state: ProjectPageUi, actions: ProjectPageActions) {
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

@Composable
private fun ChatRow(row: ProjectChatUi, showProject: Boolean, actions: ProjectPageActions) {
    val title =
        when (val t = row.title) {
            is ChatTitle.Text -> t.text
            ChatTitle.New,
            ChatTitle.Untitled -> stringResource(Res.string.workspaceNewSession)
        }
    val running = stringResource(Res.string.chatsHomeRunning)
    KitRow(
        title = title,
        supporting =
            if (showProject) row.project ?: stringResource(Res.string.globalSessionsUnknownLocation)
            else null,
        trailing =
            if (row.running) KitRowTrailing.Value(running, chevron = false)
            else KitRowTrailing.None,
        growKey = row.ref,
        onClick = { actions.openConversation(row.ref) },
    )
}

@Composable
private fun SharedStorageNotice(state: ProjectPageUi, actions: ProjectPageActions) {
    val block = state.storageBlock
    val title =
        when (block) {
            SharedStorageBlock.AppAccess -> Res.string.stateStorageAppTitle
            SharedStorageBlock.None -> return
        }
    val body =
        when (block) {
            SharedStorageBlock.AppAccess -> Res.string.stateStorageAppBody
            SharedStorageBlock.None -> return
        }
    val action =
        when (block) {
            SharedStorageBlock.AppAccess -> Res.string.stateStorageAppAction
            SharedStorageBlock.None -> return
        }
    KitStateView(
        kind = KitStateKind.Calm,
        title = stringResource(title),
        body = stringResource(body),
        failure = state.failure,
        primary =
            KitAction(
                stringResource(action),
                actions::requestStorage,
                working = state.requestingStorage,
            ),
        secondary = KitAction(stringResource(Res.string.kitTryAgain), actions::retry),
    )
}
