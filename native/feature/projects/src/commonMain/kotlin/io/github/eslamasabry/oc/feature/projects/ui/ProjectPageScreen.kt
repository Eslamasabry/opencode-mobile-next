package io.github.eslamasabry.oc.feature.projects.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
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
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.chatsHomeRunning
import io.github.eslamasabry.oc.ui.words.res.globalSessionsLoadFailedTitle
import io.github.eslamasabry.oc.ui.words.res.globalSessionsLoadMore
import io.github.eslamasabry.oc.ui.words.res.globalSessionsLoadMoreFailed
import io.github.eslamasabry.oc.ui.words.res.globalSessionsUnknownLocation
import io.github.eslamasabry.oc.ui.words.res.kitCopyDetails
import io.github.eslamasabry.oc.ui.words.res.kitDetails
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.kitTryAgain
import io.github.eslamasabry.oc.ui.words.res.linuxSvcTitle
import io.github.eslamasabry.oc.ui.words.res.p12CreateFailed
import io.github.eslamasabry.oc.ui.words.res.p12ElsewhereTitle
import io.github.eslamasabry.oc.ui.words.res.p12NewHere
import io.github.eslamasabry.oc.ui.words.res.p12NewHereBody
import io.github.eslamasabry.oc.ui.words.res.p12NoServerBody
import io.github.eslamasabry.oc.ui.words.res.p12NoServerTitle
import io.github.eslamasabry.oc.ui.words.res.p12ProjectConversations
import io.github.eslamasabry.oc.ui.words.res.p12ProjectNoConversations
import io.github.eslamasabry.oc.ui.words.res.serversTitle
import io.github.eslamasabry.oc.ui.words.res.servicesSubtitle
import io.github.eslamasabry.oc.ui.words.res.servicesTitle
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
            if (onTerminal != null)
                item(key = "terminal") {
                    KitRow(
                        title = stringResource(Res.string.terminal40Title),
                        trailing = KitRowTrailing.Chevron,
                        onClick = onTerminal,
                    )
                }
            item(key = "development-services") {
                KitRow(
                    title = stringResource(Res.string.linuxSvcTitle),
                    trailing = KitRowTrailing.Chevron,
                    onClick = actions::developmentServices,
                )
            }
            item(key = "new-body") {
                KitText(
                    stringResource(Res.string.p12NewHereBody, state.name),
                    KitTextRole.Supporting,
                )
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
                    // BUILD-RUNTIME-A: Development services of this folder (Flutter project hub).
                    if (onDevServices != null) {
                        item(key = "services") {
                            KitRowGroup {
                                KitRow(
                                    title = stringResource(Res.string.servicesTitle),
                                    supporting = stringResource(Res.string.servicesSubtitle),
                                    trailing = KitRowTrailing.Chevron,
                                    onClick = onDevServices,
                                )
                            }
                        }
                    }
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
                    if (state.elsewhere.isNotEmpty()) {
                        item(key = "elsewhere") {
                            KitRowGroup(label = stringResource(Res.string.p12ElsewhereTitle)) {
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
