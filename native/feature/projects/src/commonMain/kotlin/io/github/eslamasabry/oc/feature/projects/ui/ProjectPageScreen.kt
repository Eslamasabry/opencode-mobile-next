package io.github.eslamasabry.oc.feature.projects.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.projects.presentation.ChatTitle
import io.github.eslamasabry.oc.feature.projects.presentation.IsolatedPhase
import io.github.eslamasabry.oc.feature.projects.presentation.IsolatedTaskUi
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
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.words.res.*
import io.github.eslamasabry.oc.ui.words.res.chatsHomeRunning
import io.github.eslamasabry.oc.ui.words.res.globalSessionsLoadFailedTitle
import io.github.eslamasabry.oc.ui.words.res.globalSessionsLoadMore
import io.github.eslamasabry.oc.ui.words.res.globalSessionsLoadMoreFailed
import io.github.eslamasabry.oc.ui.words.res.globalSessionsUnknownLocation
import io.github.eslamasabry.oc.ui.words.res.kitCopyDetails
import io.github.eslamasabry.oc.ui.words.res.kitDetails
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.kitTryAgain
import io.github.eslamasabry.oc.ui.words.res.p12CreateFailed
import io.github.eslamasabry.oc.ui.words.res.p12ElsewhereTitle
import io.github.eslamasabry.oc.ui.words.res.p12NewHere
import io.github.eslamasabry.oc.ui.words.res.p12NewHereBody
import io.github.eslamasabry.oc.ui.words.res.p12NoServerBody
import io.github.eslamasabry.oc.ui.words.res.p12NoServerTitle
import io.github.eslamasabry.oc.ui.words.res.p12ProjectConversations
import io.github.eslamasabry.oc.ui.words.res.p12ProjectNoConversations
import io.github.eslamasabry.oc.ui.words.res.serversTitle
import io.github.eslamasabry.oc.ui.words.res.workspaceNewSession
import org.jetbrains.compose.resources.stringResource

/**
 * One project (07 §5 D15). Kit parts only (K1). "New conversation here" is the first, visible act;
 * then this project's conversations with a visible Load more, then "In other projects" (B-NAV-010).
 */
@Composable
public fun ProjectPageScreen(state: ProjectPageUi, actions: ProjectPageActions) {
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
        LazyColumn(verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
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
            item(key = "new") {
                KitButton(
                    label = stringResource(Res.string.p12NewHere),
                    onClick = actions::newConversationHere,
                    role = KitButtonRole.Primary,
                    working = state.creating,
                )
            }
            item(key = "new-body") {
                KitText(
                    stringResource(Res.string.p12NewHereBody, state.name),
                    KitTextRole.Supporting,
                )
            }
            if (state.canIsolate && state.isolated?.canStartAgain != false) {
                item(key = "isolated-start") {
                    KitRow(
                        title = stringResource(Res.string.agentIsolatedStart),
                        supporting = stringResource(Res.string.agentIsolatedIntro),
                        onClick = actions::startIsolated,
                    )
                }
            }
            state.isolated?.let { isolated ->
                item(key = "isolated") { IsolatedTask(isolated, actions) }
            }
            state.createFailure?.let { failure ->
                item(key = "create-failed") {
                    KitErrorNotice(
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
                            if (state.conversations.isEmpty()) {
                                KitText(
                                    stringResource(Res.string.p12ProjectNoConversations),
                                    KitTextRole.Supporting,
                                )
                            }
                            state.conversations.forEach {
                                ChatRow(it, showProject = false, actions = actions)
                            }
                        }
                    }
                    if (state.canLoadMore) item(key = "more") { LoadMore(state, actions) }
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
    }
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
private fun IsolatedTask(state: IsolatedTaskUi, actions: ProjectPageActions) {
    val phase =
        stringResource(
            when (state.phase) {
                IsolatedPhase.Creating -> Res.string.agentIsolatedCreating
                IsolatedPhase.Preparing -> Res.string.agentIsolatedPreparing
                IsolatedPhase.Ready -> Res.string.agentIsolatedReady
                IsolatedPhase.Unconfirmed -> Res.string.agentIsolatedUnconfirmed
                IsolatedPhase.Failed -> Res.string.agentIsolatedFailed
                IsolatedPhase.Opening -> Res.string.agentIsolatedOpening
                IsolatedPhase.Opened -> Res.string.agentIsolatedOpened
                IsolatedPhase.Cancelled -> Res.string.agentIsolatedCancelled
            }
        )
    KitRowGroup(label = stringResource(Res.string.agentIsolatedTitle)) {
        KitText(phase, KitTextRole.Supporting)
        state.copy?.let { KitText(it.directory, KitTextRole.Supporting) }
        state.failure?.let { failure ->
            KitErrorNotice(
                message = stringResource(Res.string.agentIsolatedFailed),
                failure = failure,
                detailsLabel = stringResource(Res.string.kitDetails),
                copyLabel = stringResource(Res.string.kitCopyDetails),
            )
        }
        when (state.phase) {
            IsolatedPhase.Ready ->
                KitButton(stringResource(Res.string.agentIsolatedOpen), { actions.openIsolated() })
            IsolatedPhase.Unconfirmed -> {
                KitButton(
                    stringResource(Res.string.agentIsolatedKeepWaiting),
                    actions::keepWaitingForCopy,
                )
                KitButton(
                    stringResource(Res.string.agentIsolatedOpenAnyway),
                    { actions.openIsolated(acceptUnconfirmed = true) },
                    role = KitButtonRole.Tertiary,
                )
            }
            IsolatedPhase.Failed ->
                if (state.copy != null) {
                    KitText(
                        stringResource(Res.string.agentIsolatedFailedBody),
                        KitTextRole.Supporting,
                    )
                    KitButton(
                        stringResource(Res.string.agentIsolatedStartAnyway),
                        { actions.openIsolated(acceptFailedSetup = true) },
                        role = KitButtonRole.Tertiary,
                    )
                }
            else -> Unit
        }
        if (state.canCancel)
            KitButton(
                stringResource(Res.string.agentIsolatedCancel),
                actions::cancelIsolated,
                role = KitButtonRole.Tertiary,
            )
    }
}
