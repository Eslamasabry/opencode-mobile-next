package io.github.eslamasabry.oc.feature.chat.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.transcript.state.NoticeKind
import io.github.eslamasabry.oc.engine.transcript.state.WorkState
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerHost
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.presentation.ChatActions
import io.github.eslamasabry.oc.feature.chat.presentation.ChatConfirm
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.feature.chat.presentation.ChatStatusKind
import io.github.eslamasabry.oc.feature.chat.presentation.ChatStatusUi
import io.github.eslamasabry.oc.feature.chat.presentation.ChatUi
import io.github.eslamasabry.oc.feature.chat.presentation.FindUi
import io.github.eslamasabry.oc.feature.chat.presentation.MessageAction
import io.github.eslamasabry.oc.feature.chat.presentation.MessageMenuUi
import io.github.eslamasabry.oc.feature.chat.presentation.MessageOrder
import io.github.eslamasabry.oc.feature.chat.presentation.StatusPick
import io.github.eslamasabry.oc.feature.chat.requests.presentation.ChatRequestActions
import io.github.eslamasabry.oc.feature.chat.requests.presentation.RequestSlotUi
import io.github.eslamasabry.oc.feature.chat.requests.ui.ChatRequestSlot
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.modal.KitBackGuard
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitMenu
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.status.KitStatus
import io.github.eslamasabry.oc.ui.kit.status.KitStatusKind
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import io.github.eslamasabry.oc.ui.kitchat.stream.rememberRevealRegistry
import io.github.eslamasabry.oc.ui.kitchat.transcript.TranscriptListState
import io.github.eslamasabry.oc.ui.kitchat.transcript.rememberTranscriptListState
import io.github.eslamasabry.oc.ui.kitchat.composer.KitReadRequest
import io.github.eslamasabry.oc.ui.kitchat.composer.KitReplyReader
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.chatLoadFailedBody
import io.github.eslamasabry.oc.ui.words.res.chatLoadFailedTitle
import io.github.eslamasabry.oc.ui.words.res.chatLoadingConversation
import io.github.eslamasabry.oc.ui.words.res.chatSendFailed
import io.github.eslamasabry.oc.ui.words.res.chatUiContextCompacted
import io.github.eslamasabry.oc.ui.words.res.chatUiDeleteMessage
import io.github.eslamasabry.oc.ui.words.res.chatUiDeleteThisMessage
import io.github.eslamasabry.oc.ui.words.res.chatUiEarlierMessageCount
import io.github.eslamasabry.oc.ui.words.res.chatUiForkFromThisPrompt
import io.github.eslamasabry.oc.ui.words.res.chatUiNotConnectedToTheServerRightNow
import io.github.eslamasabry.oc.ui.words.res.chatUiOpenCodeIsReconnectingTryAgainShortly
import io.github.eslamasabry.oc.ui.words.res.chatUiPromptNotAnswered
import io.github.eslamasabry.oc.ui.words.res.chatUiSendPromptAgain
import io.github.eslamasabry.oc.ui.words.res.chatUiTheMessageAndAllOfItsParts
import io.github.eslamasabry.oc.ui.words.res.commandUntitledChat
import io.github.eslamasabry.oc.ui.words.res.commonRetry
import io.github.eslamasabry.oc.ui.words.res.draftLeaveAction
import io.github.eslamasabry.oc.ui.words.res.draftLeaveMessage
import io.github.eslamasabry.oc.ui.words.res.historyLoadOlder
import io.github.eslamasabry.oc.ui.words.res.kitConfirmFailed
import io.github.eslamasabry.oc.ui.words.res.kitCopy
import io.github.eslamasabry.oc.ui.words.res.voiceNativeReadAloud
import io.github.eslamasabry.oc.ui.words.res.kitDiscardTitle
import io.github.eslamasabry.oc.ui.words.res.kitJumpLatest
import io.github.eslamasabry.oc.ui.words.res.kitMessageActions
import io.github.eslamasabry.oc.ui.words.res.kitSheetDismiss
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.kitTopBarSearch
import io.github.eslamasabry.oc.ui.words.res.kitTurnLiveSending
import io.github.eslamasabry.oc.ui.words.res.kitTurnLiveWorking
import io.github.eslamasabry.oc.ui.words.res.kitTurnMore
import io.github.eslamasabry.oc.ui.words.res.kitWorkDidntFinish
import io.github.eslamasabry.oc.ui.words.res.kitWorkSeparator
import io.github.eslamasabry.oc.ui.words.res.kitWorkSteps
import io.github.eslamasabry.oc.ui.words.res.kitWorkStopped
import io.github.eslamasabry.oc.ui.words.res.kitWorkWorking
import io.github.eslamasabry.oc.ui.words.res.phoneServerCardStopping
import io.github.eslamasabry.oc.ui.words.res.queuedDiscardUnconfirmedMessage
import io.github.eslamasabry.oc.ui.words.res.sessionInstructionsUpdated
import io.github.eslamasabry.oc.ui.words.res.skillApplied
import io.github.eslamasabry.oc.ui.words.res.transcriptFindClose
import io.github.eslamasabry.oc.ui.words.res.transcriptFindCount
import io.github.eslamasabry.oc.ui.words.res.transcriptFindHint
import io.github.eslamasabry.oc.ui.words.res.transcriptFindNext
import io.github.eslamasabry.oc.ui.words.res.transcriptFindNone
import io.github.eslamasabry.oc.ui.words.res.transcriptFindPartial
import io.github.eslamasabry.oc.ui.words.res.transcriptFindPrevious
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The chat screen (plan 09 §6, §7): `ChatScreen(state, actions)` from kit parts only. Top to
 * bottom: the bar, the one status line, the find bar, the loading line, the transcript (or its
 * skeleton or the could-not-load state), the composer. The menu and the confirm are screen state,
 * never routes (ADR §9.4).
 */
@Composable
public fun ChatScreen(
    state: ChatUi,
    actions: ChatActions,
    composerHost: ComposerHost,
    modifier: Modifier = Modifier,
    requests: RequestSlotUi = RequestSlotUi(),
    requestActions: ChatRequestActions? = null,
    replyReader: KitReplyReader? = null,
) {
    KitBackGuard(enabled = state.guardBack, onBack = actions::leaveRequested)
    val requestState by rememberUpdatedState(requests)
    val words = rememberChatWords()
    val listWords =
        ChatListWords(
            messageActions = stringResource(Res.string.kitMessageActions),
            working = stringResource(Res.string.kitTurnLiveWorking),
            stopping = stringResource(Res.string.phoneServerCardStopping),
            sending = stringResource(Res.string.kitTurnLiveSending),
            notSent = stringResource(Res.string.chatSendFailed),
            unconfirmed = stringResource(Res.string.queuedDiscardUnconfirmedMessage),
            tryAgain = stringResource(Res.string.commonRetry),
        )
    val list = rememberTranscriptListState()
    val reveal = rememberRevealRegistry()
    val motion = kitChatMotion()
    val latest by rememberUpdatedState(state)
    val itemsBelowRows = (if (state.live != null) 1 else 0) + state.pending.size

    // Read acknowledgement follows the visible list (plan 09 §6.7).
    LaunchedEffect(list) {
        snapshotFlow { list.atLiveEdge }.collect { actions.liveEdgeChanged(it) }
    }
    // Find brings its current hit's row into view (plan 09 §6.6); a jump never animates across
    // thousands of rows (§7.5).
    LaunchedEffect(state.find?.currentKey, state.find?.current) {
        val index = latest.find?.currentIndex ?: -1
        if (index >= 0) {
            list.following = false
            list.lazy.scrollToItem(itemsBelowRows + index)
        }
    }
    // Arrivals (07 §2.5): land once on the failure; "ready to type" is the composer's to
    // acknowledge.
    LaunchedEffect(state.arrival?.id) {
        val arrival = latest.arrival ?: return@LaunchedEffect
        arrival.landOnIndex?.let {
            list.following = false
            list.lazy.scrollToItem(itemsBelowRows + it)
        }
        if (!arrival.focusComposer) actions.arrivalShown(arrival.id)
    }

    val chrome =
        remember(actions) {
            ComposerChrome(
                busy = { latest.live != null },
                requestWaiting = { requestState.card != null },
                onSent = { list.following = true },
                pendingInsert = { null }, // agent ```choices arrive with cards (S5)
                insertDone = {},
                focusRequest = { latest.arrival?.takeIf { it.focusComposer }?.id },
                focusDone = actions::arrivalShown,
                onStop = actions::stop,
                stopping = { latest.live?.stopping == true },
            )
        }

    // The frame, its sibling menu and its confirmation share one modal layer. KitScreen
    // reuses this ancestor rather than creating a host scoped only to its own content.
    KitSheetHost(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            KitScreen(
                modifier = Modifier.fillMaxSize(),
                page = true,
                topBar = {
                    KitTopBar(
                        title = state.title ?: stringResource(Res.string.commandUntitledChat),
                        exit = KitTopBarExit.Back,
                        exitLabel = stringResource(Res.string.kitTopBarBack),
                        onExit = actions::leaveRequested,
                        actions =
                            listOf(
                                KitLineAction(
                                    stringResource(Res.string.kitTopBarSearch),
                                    { actions.find(state.find?.query ?: "") },
                                    testTag = "chat-find-open",
                                )
                            ),
                    )
                },
                status = state.status?.let { statusLine(it, actions) },
                search = state.find?.let { find -> @Composable { FindBar(find, actions) } },
                loading = state.phase == ChatPhase.Loading,
                loadingLabel = stringResource(Res.string.chatLoadingConversation),
                bottom = {
                    Column {
                        // P12-VOICE: the reading strip (Stop, notes) and the once-per-phone consent.
                        replyReader?.Reader(
                            state.readAloud?.let { KitReadRequest(it.id, it.rowKey, it.text) },
                            actions::readAloudDone,
                            Modifier.padding(horizontal = Space.S4.dp),
                        )
                        if (requestActions != null)
                            ChatRequestSlot(
                                requests,
                                requestActions,
                                nowMs = null,
                                modifier = Modifier.padding(horizontal = Space.S4.dp),
                            )
                        composerHost.Composer(
                            ComposerTarget(state.ref.profileId.raw, state.ref.sessionId.raw),
                            chrome,
                            Modifier.fillMaxWidth()
                                .padding(horizontal = Space.S4.dp, vertical = Space.S2.dp),
                        )
                    }
                },
            ) {
                when (val phase = state.phase) {
                    // First load: the skeleton under the loading line, never an empty-state text
                    // (plan 09 §6.5).
                    ChatPhase.Loading ->
                        KitSkeletonRows(Modifier.testTag("chat-loading"), count = 6)
                    is ChatPhase.Failed ->
                        KitStateView(
                            kind = KitStateKind.Error,
                            title = stringResource(Res.string.chatLoadFailedTitle),
                            body = stringResource(Res.string.chatLoadFailedBody),
                            failure = phase.failure,
                            onRetry = actions::retryLoad,
                            modifier = Modifier.testTag("chat-load-error"),
                        )
                    ChatPhase.Live ->
                        Box(Modifier.fillMaxSize()) {
                            ChatTranscriptList(
                                rows = state.rows,
                                live = state.live,
                                pending = state.pending,
                                words = words,
                                listWords = listWords,
                                list = list,
                                reveal = reveal,
                                motion = motion,
                                onOpenMenu = actions::openMessageMenu,
                                onRetrySend = actions::retrySend,
                                modifier = Modifier.fillMaxSize(),
                            )
                            EarlierMessagesPill(
                                state,
                                list,
                                itemsBelowRows,
                                actions,
                                Modifier.align(Alignment.TopCenter).padding(top = Space.S2.dp),
                            )
                        }
                }
            }

            // The menu opens over the composer's end, where the action that opened it is under the
            // thumb.
            MessageMenu(state.menu, actions, Modifier.align(Alignment.BottomEnd), reads = replyReader != null)
            KitConfirm(
                spec = confirmSpec(state.confirm),
                onConfirm = {
                    when (latest.confirm) {
                        is ChatConfirm.DeleteMessage -> actions.confirmDelete()
                        ChatConfirm.LeaveUnsaved -> actions.confirmLeave()
                        null -> Unit
                    }
                },
                onCancel = actions::dismissConfirm,
            )
        }
    }
}

/**
 * The "N earlier messages" pill (plan 09 §7.3 rule 6). The count is a derivedStateOf over the
 * list's layout, read only here, so scrolling recomposes the pill only when the number changes.
 * Message order is built once per rows instance and only while the pill could show.
 */
@Composable
private fun EarlierMessagesPill(
    state: ChatUi,
    list: TranscriptListState,
    itemsBelowRows: Int,
    actions: ChatActions,
    modifier: Modifier,
) {
    val rows = state.rows
    val order = remember(rows) { lazy { MessageOrder(rows) } }
    val count by
        remember(order, itemsBelowRows) {
            derivedStateOf {
                if (!list.awayFromLatest) {
                    0
                } else {
                    val oldest =
                        list.lazy.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: -1
                    val o = order.value
                    val earlier = o.earlierCount(oldest - itemsBelowRows)
                    if (MessageOrder.pillVisible(true, o.messageCount, earlier)) earlier else 0
                }
            }
        }
    if (count > 0) {
        KitChip(
            label = pluralStringResource(Res.plurals.chatUiEarlierMessageCount, count, count),
            onClick = actions::openTimeline,
            modifier = modifier.testTag("earlier-messages-pill"),
        )
    }
}

@Composable
private fun FindBar(find: FindUi, actions: ChatActions) {
    val field = rememberTextFieldState(find.query)
    val query by rememberUpdatedState(find.query)
    LaunchedEffect(field) {
        snapshotFlow { field.text.toString() }.collect { if (it != query) actions.find(it) }
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Space.S4.dp).testTag("transcript-find-bar")
    ) {
        KitField(
            field,
            stringResource(Res.string.transcriptFindHint),
            Modifier.testTag("transcript-find-input"),
        )
        if (find.partial) {
            KitText(stringResource(Res.string.transcriptFindPartial))
            KitButton(
                stringResource(Res.string.historyLoadOlder),
                actions::loadOlder,
                Modifier.testTag("chat-load-older"),
                role = KitButtonRole.Tertiary,
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val count =
                when {
                    find.query.isBlank() -> ""
                    find.count == 0 -> stringResource(Res.string.transcriptFindNone)
                    else ->
                        pluralStringResource(
                            Res.plurals.transcriptFindCount,
                            find.count,
                            find.count,
                            find.current + 1,
                        )
                }
            KitText(count, Modifier.weight(1f))
            KitButton(
                stringResource(Res.string.transcriptFindPrevious),
                actions::findPrevious,
                Modifier.testTag("transcript-find-previous"),
                role = KitButtonRole.Tertiary,
            )
            KitButton(
                stringResource(Res.string.transcriptFindNext),
                actions::findNext,
                Modifier.testTag("transcript-find-next"),
                role = KitButtonRole.Tertiary,
            )
            KitButton(
                stringResource(Res.string.transcriptFindClose),
                { actions.find(null) },
                role = KitButtonRole.Tertiary,
            )
        }
    }
}

/**
 * One action list per message (plan 09 §6.6). Copy waits for a kit copy part (request K-3): absent,
 * never broken.
 */
@Composable
private fun MessageMenu(menu: MessageMenuUi?, actions: ChatActions, modifier: Modifier, reads: Boolean = false) {
    val items =
        menu
            ?.actions
            .orEmpty()
            .filter { it != MessageAction.Copy && (reads || it != MessageAction.ReadAloud) }
            .map { action ->
                KitMenuItem(
                    label =
                        when (action) {
                            MessageAction.Copy -> stringResource(Res.string.kitCopy)
                            MessageAction.ReadAloud -> stringResource(Res.string.voiceNativeReadAloud)
                            MessageAction.SendAgain ->
                                stringResource(Res.string.chatUiSendPromptAgain)
                            MessageAction.Fork ->
                                stringResource(Res.string.chatUiForkFromThisPrompt)
                            MessageAction.Delete -> stringResource(Res.string.chatUiDeleteMessage)
                        },
                    onSelect = { menu?.let { actions.messageAction(it.target, action) } },
                    destructive = action == MessageAction.Delete,
                )
            }
    KitMenu(
        expanded = menu != null,
        items = items,
        onDismissRequest = actions::closeMessageMenu,
        title = stringResource(Res.string.kitMessageActions),
        modifier = modifier.testTag("message-menu"),
    ) {}
}

@Composable
private fun confirmSpec(confirm: ChatConfirm?): KitConfirmSpec? =
    when (confirm) {
        null -> null
        is ChatConfirm.DeleteMessage ->
            KitConfirmSpec(
                title = stringResource(Res.string.chatUiDeleteThisMessage),
                confirmLabel = stringResource(Res.string.chatUiDeleteMessage),
                kind = KitConfirmKind.Destructive,
                consequences = listOf(stringResource(Res.string.chatUiTheMessageAndAllOfItsParts)),
            )
        ChatConfirm.LeaveUnsaved ->
            KitConfirmSpec(
                title = stringResource(Res.string.kitDiscardTitle),
                confirmLabel = stringResource(Res.string.draftLeaveAction),
                kind = KitConfirmKind.Discard,
                body = stringResource(Res.string.draftLeaveMessage),
            )
    }

/**
 * The one status line's words (plan 09 §6.5). Only a finished action's failure may be dismissed (it
 * changes nothing).
 */
@Composable
private fun statusLine(line: ChatStatusUi, actions: ChatActions): KitStatus =
    when (line.kind) {
        ChatStatusKind.Offline ->
            KitStatus(
                KitStatusKind.Connection,
                stringResource(Res.string.chatUiNotConnectedToTheServerRightNow),
                id = "chat-status-${line.id}",
            )
        ChatStatusKind.Connecting ->
            KitStatus(
                KitStatusKind.Connection,
                stringResource(Res.string.chatUiOpenCodeIsReconnectingTryAgainShortly),
                id = "chat-status-${line.id}",
            )
        ChatStatusKind.SendFailed ->
            KitStatus(
                KitStatusKind.Info,
                stringResource(Res.string.chatSendFailed),
                id = "chat-status-${line.id}",
                action =
                    line.localId?.let { id ->
                        KitLineAction(
                            stringResource(Res.string.commonRetry),
                            { actions.retrySend(id) },
                            testTag = "chat-send-error-try-again",
                        )
                    },
            )
        ChatStatusKind.SendUnconfirmed ->
            KitStatus(
                KitStatusKind.Info,
                stringResource(Res.string.queuedDiscardUnconfirmedMessage),
                id = "chat-status-${line.id}",
            )
        ChatStatusKind.RunFailed ->
            KitStatus(
                KitStatusKind.Info,
                stringResource(Res.string.chatUiPromptNotAnswered),
                id = "chat-status-${line.id}",
            )
        ChatStatusKind.ActionFailed ->
            KitStatus(
                KitStatusKind.Info,
                stringResource(Res.string.kitConfirmFailed),
                id = "chat-status-${line.id}",
                onDismiss = { actions.dismissStatus(StatusPick.ACTION) },
                dismissLabel = stringResource(Res.string.kitSheetDismiss),
            )
    }

/** The words the transcript shows that are not server text, from `:ui:words`. */
@Composable
internal fun rememberChatWords(): ChatTranscriptWords {
    val jump = stringResource(Res.string.kitJumpLatest)
    val more = stringResource(Res.string.kitTurnMore)
    val didntFinish = stringResource(Res.string.kitWorkDidntFinish)
    val compacted = stringResource(Res.string.chatUiContextCompacted)
    val instructions = stringResource(Res.string.sessionInstructionsUpdated)
    val skill = stringResource(Res.string.skillApplied)
    val working = stringResource(Res.string.kitWorkWorking)
    val stopped = stringResource(Res.string.kitWorkStopped)
    return remember(jump, more, didntFinish, compacted, instructions, skill, working, stopped) {
        object : ChatTranscriptWords {
            override val jumpToLatest: String = jump

            /**
             * Only the P3 overload calls this; the screen draws the plural through [workLineWords].
             */
            override fun workLine(calls: Int, failed: Int, state: WorkState): String =
                when (state) {
                    WorkState.Running -> working
                    WorkState.Stopped -> stopped
                    WorkState.EndedFailed -> didntFinish
                    WorkState.Done -> more
                }

            override fun notice(kind: NoticeKind): String =
                when (kind) {
                    NoticeKind.Compaction -> compacted
                    NoticeKind.InstructionsUpdated -> instructions
                    NoticeKind.SkillChanged -> skill
                    NoticeKind.ShellChanged,
                    NoticeKind.Other -> instructions
                }

            override fun footer(failed: Boolean): String = if (failed) didntFinish else more

            override fun thought(firstLine: String): String = firstLine
        }
    }
}

/**
 * The work line: "3 steps · Working" (plural rules per language, so it is drawn in composition).
 */
@Composable
internal fun workLineWords(calls: Int, state: WorkState): String {
    val steps = pluralStringResource(Res.plurals.kitWorkSteps, calls, calls)
    val word =
        when (state) {
            WorkState.Running -> stringResource(Res.string.kitWorkWorking)
            WorkState.Stopped -> stringResource(Res.string.kitWorkStopped)
            WorkState.EndedFailed -> stringResource(Res.string.kitWorkDidntFinish)
            WorkState.Done -> null
        }
    return if (word == null) steps else steps + stringResource(Res.string.kitWorkSeparator) + word
}

/** The kit's motion level, as the chat parts read it (08 §5.1 levels). */
@Composable
private fun kitChatMotion(): KitMotionLevel =
    when (KitMotion.state.level) {
        MotionLevel.Full -> KitMotionLevel.Full
        MotionLevel.Calm -> KitMotionLevel.Calm
        MotionLevel.Off -> KitMotionLevel.Off
    }
