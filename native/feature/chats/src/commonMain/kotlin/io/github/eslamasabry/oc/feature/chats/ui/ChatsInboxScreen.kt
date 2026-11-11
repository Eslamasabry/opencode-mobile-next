package io.github.eslamasabry.oc.feature.chats.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.chats.presentation.*
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitDivider
import io.github.eslamasabry.oc.ui.kit.foundation.KitDividerInset
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.frame.*
import io.github.eslamasabry.oc.ui.kit.row.KitFeedItem
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowState
import io.github.eslamasabry.oc.ui.kit.row.KitRowStateTone
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitInboxEmptyAnswer
import io.github.eslamasabry.oc.ui.kit.text.KitBidi
import io.github.eslamasabry.oc.ui.kit.text.kitRelativeAge
import io.github.eslamasabry.oc.ui.kitchat.header.rememberKitMask
import io.github.eslamasabry.oc.ui.kitchat.request.KitInboxRequestRow
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Flutter Inbox's supported requests and finished conversations; ownership stays in ChatsActions. */
@Composable
internal fun ChatsInboxScreen(state: ChatsUi, actions: ChatsActions) {
    val watched = state.home.monitoring
    val waiting = state.inbox.count { row -> row.permission?.let { it.requestId in state.answering } != true } + watched.requests.size
    val hasRequests = state.inbox.isNotEmpty() || watched.requests.isNotEmpty()
    KitScreen(
        topBar = {
            KitTopBar(
                stringResource(Res.string.inboxTitle),
                subtitle = if (waiting == 0) null else pluralStringResource(Res.plurals.goToNeedsYouHint, waiting, waiting),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::closeHomeSheet,
            )
        },
        width = KitScreenWidth.Reading,
        edges = KitScreenEdges.Bleed,
    ) {
        LazyColumn(Modifier.fillMaxSize().testTag("inbox-scroll")) {
            state.answerFailure?.let { failure ->
                item("answer-failure", contentType = "answer-failure") { KitErrorNotice(stringResource(Res.string.chatHomeAnswerFailed), failure = failure) }
            }
            watched.failure?.let { failure ->
                item("watched-failure", contentType = "watched-failure") { KitErrorNotice(stringResource(Res.string.homeWatchedOpenFailed), failure = failure) }
            }
            if (hasRequests) {
                item("requests", contentType = "requests") {
                    val primary = state.inbox.indexOfFirst { it.permission != null }
                    Column(Modifier.padding(top = Space.S2.dp)) {
                        state.inbox.forEachIndexed { index, row ->
                            key(row.key) {
                                if (index > 0) KitDivider(inset = KitDividerInset.Gutter)
                                InboxRequest(row, state, actions, primary = index == primary)
                            }
                        }
                        watched.requests.forEachIndexed { index, row ->
                            key(row.key) {
                                if (state.inbox.isNotEmpty() || index > 0) KitDivider(inset = KitDividerInset.Gutter)
                                val conversation = inboxTitle(row.title)
                                val mask = rememberKitMask()
                                KitInboxRequestRow(
                                    title = stringResource(if (row.kind == ChatsWatchedKind.Permission) Res.string.inboxAsksPermission else Res.string.inboxAsksQuestion),
                                    place = listOf(row.sourceName, conversation).joinToString(" · ") { KitBidi.auto(mask(it)) },
                                    openLabel = stringResource(Res.string.inboxOpenOnServer, KitBidi.auto(mask(row.sourceName))),
                                    openActionLabel = stringResource(Res.string.kitRequestOpen),
                                    onOpen = { actions.openWatched(row.key) },
                                    ifIgnored = stringResource(Res.string.profileMonitorIfIgnored),
                                    modifier = Modifier.padding(horizontal = Space.S4.dp, vertical = Space.S4.dp),
                                )
                            }
                        }
                    }
                }
            } else {
                item("empty", contentType = "empty") {
                    Box(
                        Modifier.fillParentMaxHeight(if (state.finished.isEmpty()) 1f else .4f), // F: lib/ui/screens/inbox/inbox_screen.dart:244
                        contentAlignment = Alignment.Center,
                    ) {
                        val down = state.home.sources.filter { it.shown && it.unreachable }
                        val names = state.incomplete.ifEmpty { down.map { it.name } }
                        val mask = rememberKitMask()
                        val partial = if (names.isEmpty()) null else stringResource(
                            Res.string.inboxEmptyPartialLine,
                            names.joinToString(", ") { KitBidi.auto(mask(it)) },
                        )
                        KitInboxEmptyAnswer(
                            answer = stringResource(Res.string.inboxEmptyTitle),
                            uncertain = names.isNotEmpty() || watched.failure != null,
                            line = partial,
                            retry = if (down.isEmpty()) null else KitAction(
                                stringResource(Res.string.commonRetry),
                                { down.forEach { actions.retrySource(it.profile) } },
                            ),
                            modifier = Modifier.padding(horizontal = Space.S4.dp, vertical = Space.S6.dp),
                        )
                    }
                }
                if (state.finished.isNotEmpty()) item("finished", contentType = "finished") {
                    KitRowGroup(label = stringResource(Res.string.inboxFinishedLabel)) {
                        state.finished.take(5).forEach { row -> // F: lib/ui/screens/inbox/inbox_screen.dart:247
                            KitFeedItem(
                                title = inboxTitle(row.title),
                                project = row.project,
                                time = row.updatedAtMs?.takeIf { state.nowMs > 0 }?.let { kitRelativeAge(it, state.nowMs) }
                                    ?: row.ago?.let { agoText(it) },
                                state = KitRowState(
                                    stringResource(if (row.status == RowStatus.Stopped) Res.string.chatsHomeStopped else Res.string.chatsHomeDone),
                                    KitRowStateTone.Quiet,
                                ),
                                done = true,
                                onClick = if (row.canOpen) ({ actions.openConversation(row.ref) }) else null,
                            )
                        }
                    }
                }
            }
            if (watched.sources.any { it.enabled } || watched.requests.isNotEmpty() || watched.failure != null) item("refresh-watched", contentType = "refresh-watched") {
                KitButton(stringResource(Res.string.homeRefreshWatched), actions::refreshWatched,
                    role = KitButtonRole.Tertiary, working = watched.refreshing)
            }
            item("end", contentType = "end") { Spacer(Modifier.height(Space.S4.dp)) }
        }
    }
}

@Composable
private fun InboxRequest(row: ConversationRowUi, state: ChatsUi, actions: ChatsActions, primary: Boolean) {
    val conversation = inboxTitle(row.title)
    val permission = row.permission
    val mask = rememberKitMask()
    val answering = permission != null && permission.requestId in state.answering
    val enabled = if (answering) KitEnabled.Disabled(stringResource(Res.string.serversWorking))
        else if (!row.canOpen) KitEnabled.Disabled(stringResource(Res.string.e7WorkspaceConnecting)) else KitEnabled.Enabled
    val source = state.home.sources.firstOrNull { it.profile == row.ref.profileId }?.name
    KitInboxRequestRow(
        title = if (permission != null) stringResource(Res.string.chatRequestAskRunNamed, mask(permission.command))
            else stringResource(Res.string.inboxWaitingForAnswer),
        place = listOfNotNull(source, row.project.takeIf(String::isNotBlank), conversation).joinToString(" · ") { KitBidi.auto(mask(it)) },
        openLabel = stringResource(Res.string.inboxOpenLabel, KitBidi.auto(mask(conversation))),
        openActionLabel = stringResource(Res.string.kitRequestOpen),
        onOpen = { actions.openConversation(row.ref) },
        command = permission?.command?.let(mask),
        allowLabel = if (permission == null) null else stringResource(Res.string.kitRequestAllowOnce),
        rejectLabel = if (permission == null) null else stringResource(Res.string.kitRequestReject),
        onAllow = if (permission == null) null else ({ actions.answerPermission(row, true) }),
        onReject = if (permission == null) null else ({ actions.answerPermission(row, false) }),
        primary = primary,
        enabled = enabled,
        openEnabled = if (row.canOpen) KitEnabled.Enabled else KitEnabled.Disabled(stringResource(Res.string.e7WorkspaceConnecting)),
        ifIgnored = if (permission == null) stringResource(Res.string.chatRequestIfIgnored) else null,
        modifier = Modifier.padding(horizontal = Space.S4.dp, vertical = Space.S4.dp).testTag("inbox-request-${row.key}"),
    )
}

@Composable
private fun inboxTitle(value: RowTitle): String = rowTitleWords(value)
