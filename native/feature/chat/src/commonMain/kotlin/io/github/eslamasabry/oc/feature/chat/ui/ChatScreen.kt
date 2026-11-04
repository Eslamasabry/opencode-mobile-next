package io.github.eslamasabry.oc.feature.chat.ui

import io.github.eslamasabry.oc.feature.chat.context.ActiveContextModel
import io.github.eslamasabry.oc.feature.chat.context.ActiveContextSheet
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.engine.transcript.state.NoticeKind
import io.github.eslamasabry.oc.engine.transcript.state.WorkState
import io.github.eslamasabry.oc.feature.chat.composer.ChatPromptsDoor
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerHost
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.composer.InsertRequest
import io.github.eslamasabry.oc.feature.chat.header.ui.ApprovalChip
import io.github.eslamasabry.oc.feature.chat.header.ui.ChatHeaderOverlays
import io.github.eslamasabry.oc.feature.chat.header.ui.ChatHeaderSlot
import io.github.eslamasabry.oc.feature.chat.header.ui.ChatHeaderTopBar
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
import io.github.eslamasabry.oc.feature.chat.presentation.PictureUi
import io.github.eslamasabry.oc.feature.chat.presentation.ReplyFailureKind
import io.github.eslamasabry.oc.feature.chat.presentation.SelectTextUi
import io.github.eslamasabry.oc.feature.chat.presentation.ReceiptCheckResult
import io.github.eslamasabry.oc.feature.chat.presentation.StatusPick
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsUi
import io.github.eslamasabry.oc.feature.chat.requests.presentation.ChatRequestActions
import io.github.eslamasabry.oc.feature.chat.requests.presentation.RequestSlotUi
import io.github.eslamasabry.oc.feature.chat.requests.ui.ChatRequestSlot
import io.github.eslamasabry.oc.ui.design.effects.FrostRole
import io.github.eslamasabry.oc.feature.chat.start.presentation.ChatStartActions
import io.github.eslamasabry.oc.feature.chat.start.presentation.ChatStartUi
import io.github.eslamasabry.oc.feature.chat.start.ui.ChatStartArea
import io.github.eslamasabry.oc.feature.chat.tasks.ui.ChatTasksSheet
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrost
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrostSource
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.frame.kitScreenChromePadding
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.modal.KitBackGuard
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitMenu
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.status.KitStatus
import io.github.eslamasabry.oc.ui.kit.status.KitStatusKind
import io.github.eslamasabry.oc.ui.kitchat.composer.KitReadRequest
import io.github.eslamasabry.oc.ui.kitchat.composer.KitReplyReader
import io.github.eslamasabry.oc.ui.kitchat.image.KitPictureContent
import io.github.eslamasabry.oc.ui.kitchat.image.KitPictureView
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdownActions
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdownSelection
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import io.github.eslamasabry.oc.ui.kitchat.stream.KitPromptLiftHost
import io.github.eslamasabry.oc.ui.kitchat.stream.rememberRevealRegistry
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitFindExcerpt
import io.github.eslamasabry.oc.ui.kitchat.transcript.TranscriptListState
import io.github.eslamasabry.oc.ui.kitchat.transcript.rememberTranscriptListState
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.chatFeelDone
import io.github.eslamasabry.oc.ui.words.res.chatFeelEditAndResend
import io.github.eslamasabry.oc.ui.words.res.chatFeelLinkRefused
import io.github.eslamasabry.oc.ui.words.res.chatFeelOpenLinkAction
import io.github.eslamasabry.oc.ui.words.res.chatFeelOpenLinkBody
import io.github.eslamasabry.oc.ui.words.res.chatFeelOpenLinkTitle
import io.github.eslamasabry.oc.ui.words.res.chatFeelPicture
import io.github.eslamasabry.oc.ui.words.res.chatFeelPictureFailed
import io.github.eslamasabry.oc.ui.words.res.chatFeelPictureLoading
import io.github.eslamasabry.oc.ui.words.res.chatFeelPictureOnServer
import io.github.eslamasabry.oc.ui.words.res.chatFeelPictureOnWeb
import io.github.eslamasabry.oc.ui.words.res.chatFeelSelectText
import io.github.eslamasabry.oc.ui.words.res.chatFeelShare
import io.github.eslamasabry.oc.ui.words.res.chatFeelUndoAction
import io.github.eslamasabry.oc.ui.words.res.chatFeelUndoBody
import io.github.eslamasabry.oc.ui.words.res.chatFeelUndoTitle
import io.github.eslamasabry.oc.ui.words.res.chatLoadFailedBody
import io.github.eslamasabry.oc.ui.words.res.chatLoadFailedTitle
import io.github.eslamasabry.oc.ui.words.res.chatLoadingConversation
import io.github.eslamasabry.oc.ui.words.res.chatReplyFailedBusy
import io.github.eslamasabry.oc.ui.words.res.chatReplyFailedConnectionLost
import io.github.eslamasabry.oc.ui.words.res.chatReplyFailedCopy
import io.github.eslamasabry.oc.ui.words.res.chatReplyFailedDetails
import io.github.eslamasabry.oc.ui.words.res.chatReplyFailedModel
import io.github.eslamasabry.oc.ui.words.res.chatReplyFailedNoCredit
import io.github.eslamasabry.oc.ui.words.res.chatReplyFailedOther
import io.github.eslamasabry.oc.ui.words.res.chatReplyFailedSignIn
import io.github.eslamasabry.oc.ui.words.res.chatSendFailed
import io.github.eslamasabry.oc.ui.words.res.chatSendFailedChanged
import io.github.eslamasabry.oc.ui.words.res.chatSendFailedOffline
import io.github.eslamasabry.oc.ui.words.res.chatSendFailedRefused
import io.github.eslamasabry.oc.ui.words.res.chatUiChanges
import io.github.eslamasabry.oc.ui.words.res.chatUiCollapseReasoning
import io.github.eslamasabry.oc.ui.words.res.chatUiContextCompacted
import io.github.eslamasabry.oc.ui.words.res.chatUiDeleteMessage
import io.github.eslamasabry.oc.ui.words.res.chatUiDeleteThisMessage
import io.github.eslamasabry.oc.ui.words.res.chatUiEarlierMessageCount
import io.github.eslamasabry.oc.ui.words.res.chatUiExpandReasoning
import io.github.eslamasabry.oc.ui.words.res.chatUiFiles
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
import io.github.eslamasabry.oc.ui.words.res.gitSheetTitle
import io.github.eslamasabry.oc.ui.words.res.e7SharedHTTPIsNotEncryptedOtherDevicesOn
import io.github.eslamasabry.oc.ui.words.res.e7SharedOpenHTTPLink
import io.github.eslamasabry.oc.ui.words.res.e7SharedOpenInsecureHTTPLink
import io.github.eslamasabry.oc.ui.words.res.externalLinkDontOpen
import io.github.eslamasabry.oc.ui.words.res.historyLoadOlder
import io.github.eslamasabry.oc.ui.words.res.kitConfirmFailed
import io.github.eslamasabry.oc.ui.words.res.kitContextRingSemantics
import io.github.eslamasabry.oc.ui.words.res.kitCopied
import io.github.eslamasabry.oc.ui.words.res.kitCopy
import io.github.eslamasabry.oc.ui.words.res.kitDiscardTitle
import io.github.eslamasabry.oc.ui.words.res.kitJumpLatest
import io.github.eslamasabry.oc.ui.words.res.kitMessageActions
import io.github.eslamasabry.oc.ui.words.res.kitMessageThought
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
import io.github.eslamasabry.oc.ui.words.res.nlibTitle
import io.github.eslamasabry.oc.ui.words.res.mobileTasksProgress
import io.github.eslamasabry.oc.ui.words.res.phoneServerCardStopping
import io.github.eslamasabry.oc.ui.words.res.promptStashTitle
import io.github.eslamasabry.oc.ui.words.res.queuedDiscardUnconfirmedMessage
import io.github.eslamasabry.oc.ui.words.res.returnBriefContinue
import io.github.eslamasabry.oc.ui.words.res.queuedReceiptConfirmed
import io.github.eslamasabry.oc.ui.words.res.queuedReceiptStillUncertain
import io.github.eslamasabry.oc.ui.words.res.queuedReceiptStorageProblem
import io.github.eslamasabry.oc.ui.words.res.revertFromHere
import io.github.eslamasabry.oc.ui.words.res.schedulePhoneBusy
import io.github.eslamasabry.oc.ui.words.res.schedulePhoneCheckFailed
import io.github.eslamasabry.oc.ui.words.res.sessionInstructionsUpdated
import io.github.eslamasabry.oc.ui.words.res.skillApplied
import io.github.eslamasabry.oc.ui.words.res.transcriptFindAll
import io.github.eslamasabry.oc.ui.words.res.stateDemoDisclosure
import io.github.eslamasabry.oc.ui.words.res.transcriptFindClose
import io.github.eslamasabry.oc.ui.words.res.transcriptFindCount
import io.github.eslamasabry.oc.ui.words.res.transcriptFindHint
import io.github.eslamasabry.oc.ui.words.res.transcriptFindNext
import io.github.eslamasabry.oc.ui.words.res.transcriptFindNone
import io.github.eslamasabry.oc.ui.words.res.transcriptFindPartial
import io.github.eslamasabry.oc.ui.words.res.transcriptFindPrevious
import io.github.eslamasabry.oc.ui.words.res.transcriptFindStopSearchingAll
import io.github.eslamasabry.oc.ui.words.res.voiceNativeReadAloud
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
    /**
     * Bar actions other units contribute through the chat's entry (PREVIEW-QR: "Preview a running
     * app").
     */
    extraActions: List<KitLineAction> = emptyList(),
    prompts: ChatPromptsDoor? = null,
    facts: TurnFactsUi = TurnFactsUi.Empty,
    onOpenSession: ((String) -> Unit)? = null,
    header: ChatHeaderSlot? = null,
    replyReader: KitReplyReader? = null,
    /** B-CHAT-001..003: the active-context inspector this conversation opens from its bar (null where the server has none). */
    activeContext: ActiveContextModel? = null,
    start: ChatStartUi = ChatStartUi(),
    startActions: ChatStartActions? = null,
    demoMode: Boolean = false,
) {
    var contextOpen by remember { mutableStateOf(false) }
    var tasksOpen by remember(state.ref) { mutableStateOf(false) }
    val startState by rememberUpdatedState(start)
    val currentStartActions by rememberUpdatedState(startActions)
    KitBackGuard(enabled = state.guardBack, onBack = actions::leaveRequested)
    val requestState by rememberUpdatedState(requests)
    val headerState by rememberUpdatedState(header)
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
            replyFailed = rememberReplyFailedWords(),
            details = stringResource(Res.string.chatReplyFailedDetails),
            copyDetails = stringResource(Res.string.chatReplyFailedCopy),
            sendFailure = rememberSendFailureWords(),
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

    val frost = rememberKitFrostState()
    val overlays = state.phase == ChatPhase.Live && state.status == null && state.find == null
    val composerUnder by
        remember(list) {
            derivedStateOf {
                list.lazy.firstVisibleItemIndex > 0 || list.lazy.firstVisibleItemScrollOffset > 0
            }
        }
    val headerUnder by remember(list) { derivedStateOf { list.lazy.canScrollForward } }
    val chrome =
        remember(actions, prompts) {
            ComposerChrome(
                busy = { latest.live != null },
                requestWaiting = { requestState.card != null },
                onSent = { list.following = true },
                // "Edit and resend" (P12-CHAT-FEEL) first, then saved prompts and slash commands
                // (P12-PROMPTS); each
                // acknowledgement goes back to the side that offered it. Agent ```choices arrive
                // with cards (S5).
                pendingInsert = {
                    latest.insert?.let { InsertRequest(it.id, it.text, replace = true) }
                        ?: startState.insert?.let {
                            InsertRequest(it.id, it.text, replace = true, onlyIfEmpty = true)
                        }
                        ?: prompts?.pending()
                },
                insertDone = { id ->
                    when {
                        latest.insert?.id == id -> actions.insertDone(id)
                        startState.insert?.id == id -> currentStartActions?.insertDone(id)
                        else -> prompts?.done(id)
                    }
                },
                focusRequest = { latest.arrival?.takeIf { it.focusComposer }?.id },
                focusDone = actions::arrivalShown,
                onStop = actions::stop,
                stopping = { latest.live?.stopping == true },
                rowChips = { headerState?.approval?.let { ApprovalChip(it) } },
            )
        }

    // The frame, its sibling menu and its confirmation share one modal layer. KitScreen
    // reuses this ancestor rather than creating a host scoped only to its own content.
    KitPromptLiftHost {
        KitSheetHost(modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                KitScreen(
                    modifier = Modifier.fillMaxSize(),
                    page = true,
                    overlayChrome = overlays,
                    topBar = {
                        Box(
                            Modifier.fillMaxWidth()
                                .kitFrost(
                                    FrostRole.ChatHeader,
                                    frost,
                                    shown = overlays && headerUnder,
                                )
                        ) {
                            // CHAT-TRANSCRIPT (B-CHAT-019): the context reading folds into the
                            // bar's
                            // actions.
                            val context =
                                facts.context?.let { context ->
                                    KitLineAction(
                                        stringResource(
                                            Res.string.kitContextRingSemantics,
                                            Numbers.format(context.percent),
                                        ),
                                        { contextOpen = true },
                                        testTag = "chat-context-open",
                                    )
                                }
                            val unitActions =
                                // STATE: the offline demo offers no server actions.
                                (if (demoMode) emptyList()
                                else
                                listOfNotNull(
                                    facts.tasks?.let { tasks ->
                                        KitLineAction(
                                            stringResource(
                                                Res.string.mobileTasksProgress,
                                                tasks.done,
                                                tasks.total,
                                            ),
                                            { tasksOpen = true },
                                            testTag = "chat-tasks-open",
                                        )
                                    },
                                    KitLineAction(
                                        stringResource(Res.string.gitSheetTitle),
                                        actions::openGit,
                                        testTag = "chat-git-chip",
                                    ),
                                    KitLineAction(
                                        stringResource(Res.string.chatUiFiles),
                                        actions::openFiles,
                                        testTag = "chat-files-open",
                                    ),
                                    KitLineAction(
                                        stringResource(Res.string.chatUiChanges),
                                        actions::openChanges,
                                        testTag = "chat-changes-open",
                                    ),
                                    prompts?.let {
                                        KitLineAction(
                                            stringResource(Res.string.promptStashTitle),
                                            it::open,
                                            testTag = "chat-saved-prompts-open",
                                        )
                                    },
                                    KitLineAction(
                                        stringResource(
                                            if (state.reasoningExpanded) Res.string.chatUiCollapseReasoning
                                            else Res.string.chatUiExpandReasoning
                                        ),
                                        actions::toggleReasoning,
                                        testTag = "chat-reasoning-toggle",
                                    ),
                                    KitLineAction(
                                        stringResource(Res.string.nlibTitle),
                                        actions::openTools,
                                        testTag = "chat-tools-open",
                                    ),
                                )) + extraActions
                            if (header != null) {
                                // CHAT-HEADER replaces the plain bar; Find lives in its ⋯ menu, the
                                // other
                                // units' actions stay.
                                ChatHeaderTopBar(
                                    header,
                                    actions::leaveRequested,
                                    { actions.find(state.find?.query ?: "") },
                                    extraActions = listOfNotNull(context) + unitActions,
                                    onProject = actions::openFiles,
                                )
                            } else
                                KitTopBar(
                                    title =
                                        state.title
                                            ?: stringResource(Res.string.commandUntitledChat),
                                    exit = KitTopBarExit.Back,
                                    exitLabel = stringResource(Res.string.kitTopBarBack),
                                    onExit = actions::leaveRequested,
                                    actions =
                                        listOfNotNull(
                                            context,
                                            KitLineAction(
                                                stringResource(Res.string.kitTopBarSearch),
                                                { actions.find(state.find?.query ?: "") },
                                                testTag = "chat-find-open",
                                            ),
                                        ) + unitActions,
                                )
                        }
                    },
                    status =
                        if (demoMode)
                            KitStatus(
                                KitStatusKind.Info,
                                stringResource(Res.string.stateDemoDisclosure),
                                id = "offline-demo",
                            )
                        else state.status?.let { statusLine(it, actions) },
                    search = state.find?.let { find -> @Composable { FindBar(find, actions) } },
                    loading = state.phase == ChatPhase.Loading,
                    loadingLabel = stringResource(Res.string.chatLoadingConversation),
                    bottom = {
                        Column {
                            // P12-VOICE: the reading strip (Stop, notes) and the once-per-phone
                            // consent.
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
                                    .kitFrost(
                                        FrostRole.Composer,
                                        frost,
                                        shown = overlays && composerUnder,
                                    )
                                    .padding(horizontal = Space.S4.dp, vertical = Space.S2.dp),
                            )
                        }
                    },
                ) {
                    when (val phase = state.phase) {
                        // First load: the skeleton under the loading line, never an empty-state
                        // text
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
                            Box(Modifier.fillMaxSize().kitFrostSource(frost)) {
                                // PORT-SCREENS-1: an empty conversation offers its starters instead.
                                if (
                                    state.rows.isEmpty() &&
                                        state.pending.isEmpty() &&
                                        state.live == null &&
                                        start.emptyEligible &&
                                        startActions != null
                                ) {
                                    ChatStartArea(start, startActions)
                                } else
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
                                    receiptChecks = state.receiptChecks,
                                    onCheckReceipt = actions::checkReceipt,
                                    modifier = Modifier.fillMaxSize(),
                                    facts = facts,
                                    onOpenSession = onOpenSession,
                                    reply = rememberReplyActions(actions, state.confirmedPaths),
                                    onOpenFile = { file -> openArtifact(actions, file) },
                                    reasoningExpanded = state.reasoningExpanded,
                                    onCompactAgain =
                                        if (state.capabilities.compact) actions::compactAgain else null,
                                )
                                EarlierMessagesPill(
                                    state,
                                    list,
                                    itemsBelowRows,
                                    actions,
                                    Modifier.align(Alignment.TopCenter)
                                        .padding(
                                            top =
                                                Space.S2.dp +
                                                    kitScreenChromePadding().calculateTopPadding()
                                        ),
                                )
                            }
                    }
                }

                // The menu opens over the composer's end, where the action that opened it is under
                // the
                // thumb.
                MessageMenu(
                    state.menu,
                    actions,
                    Modifier.align(Alignment.BottomEnd),
                    reads = replyReader != null,
                )
                ChatContextSheet(facts.context, contextOpen) { contextOpen = false }
                ChatTasksSheet(facts.tasks, tasksOpen) { tasksOpen = false }
                if (activeContext != null) ActiveContextSheet(activeContext)
                SelectTextSheet(state.select, actions)
                PictureSheet(state.picture, actions)
                ChatTimelineSheet(state.timeline, actions)
                KitConfirm(
                    spec = confirmSpec(state.confirm),
                    onConfirm = {
                        when (latest.confirm) {
                            is ChatConfirm.DeleteMessage -> actions.confirmDelete()
                            ChatConfirm.LeaveUnsaved -> actions.confirmLeave()
                            is ChatConfirm.UndoFrom -> actions.confirmUndo()
                            is ChatConfirm.OpenLink -> actions.confirmOpenLink()
                            null -> Unit
                        }
                    },
                    onCancel = actions::dismissConfirm,
                )
                header?.let { ChatHeaderOverlays(it) }
            }
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
            label =
                pluralStringResource(
                    Res.plurals.chatUiEarlierMessageCount,
                    count,
                    Numbers.format(count),
                ),
            onClick = actions::openTimeline,
            modifier = modifier.testTag("earlier-messages-pill"),
        )
    }
}

@Composable
private fun FindBar(find: FindUi, actions: ChatActions) {
    val field = rememberTextFieldState(find.query)
    LaunchedEffect(find.query) {
        if (field.text.toString() != find.query) field.edit { replace(0, length, find.query) }
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Space.S4.dp).testTag("transcript-find-bar")
    ) {
        KitField(
            field,
            stringResource(Res.string.transcriptFindHint),
            Modifier.testTag("transcript-find-input"),
            onTextChanged = { actions.find(it) },
            onSubmit = actions::findNext,
        )
        if (find.partial) {
            KitText(stringResource(Res.string.transcriptFindPartial))
            KitButton(
                stringResource(
                    if (find.searchingAll) Res.string.transcriptFindStopSearchingAll
                    else Res.string.transcriptFindAll
                ),
                if (find.searchingAll) actions::cancelSearchAll else actions::searchAllHistory,
                Modifier.testTag(
                    if (find.searchingAll) "transcript-find-cancel-all" else "transcript-find-older"
                ),
                role = KitButtonRole.Tertiary,
            )
        }
        find.olderFailure?.let { HistoryReadFailure(it, actions::searchAllHistory) }
        find.excerpt?.let { KitFindExcerpt(it, find.query) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val count =
                when {
                    find.query.isBlank() -> ""
                    find.count == 0 -> stringResource(Res.string.transcriptFindNone)
                    else ->
                        pluralStringResource(
                            Res.plurals.transcriptFindCount,
                            find.count,
                            Numbers.format(find.count),
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
 * One action list per message (plan 09 §6.6, Flutter `_messageContextActions`). Copy, Share and
 * Select text leave the app through the chat's `ChatOutside`; a list without a platform (slice 2)
 * has no Copy.
 */
@Composable
private fun MessageMenu(
    menu: MessageMenuUi?,
    actions: ChatActions,
    modifier: Modifier,
    reads: Boolean = false,
) {
    val feel = menu?.actions?.contains(MessageAction.Share) == true
    val items =
        menu
            ?.actions
            .orEmpty()
            .filter {
                (feel || it != MessageAction.Copy) && (reads || it != MessageAction.ReadAloud)
            }
            .map { action ->
                KitMenuItem(
                    label =
                        when (action) {
                            MessageAction.Copy -> stringResource(Res.string.kitCopy)
                            MessageAction.Share -> stringResource(Res.string.chatFeelShare)
                            MessageAction.SelectText ->
                                stringResource(Res.string.chatFeelSelectText)
                            MessageAction.EditAndResend ->
                                stringResource(Res.string.chatFeelEditAndResend)
                            MessageAction.ReadAloud ->
                                stringResource(Res.string.voiceNativeReadAloud)
                            MessageAction.SendAgain ->
                                stringResource(Res.string.chatUiSendPromptAgain)
                            MessageAction.Fork ->
                                stringResource(Res.string.chatUiForkFromThisPrompt)
                            MessageAction.Changes -> stringResource(Res.string.chatUiChanges)
                            MessageAction.UndoFromHere -> stringResource(Res.string.revertFromHere)
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

/**
 * "Select text" (P12-CHAT-FEEL): the message's words in a sheet, selectable in full with the
 * transcript's filtered menu (Copy and Select all only, R17).
 */
@Composable
private fun SelectTextSheet(select: SelectTextUi?, actions: ChatActions) {
    KitSheet(
        visible = select != null,
        onDismissRequest = actions::closeSelection,
        title = stringResource(Res.string.chatFeelSelectText),
        closeLabel = stringResource(Res.string.chatFeelDone),
    ) {
        if (select != null) {
            KitMarkdownSelection(Modifier.fillMaxWidth().testTag("chat-select-text")) {
                KitText(select.text)
            }
        }
    }
}

/** P12-CHAT-FEEL: a reply's server picture, pinch to zoom; Back or Done closes it. */
@Composable
private fun PictureSheet(picture: PictureUi?, actions: ChatActions) {
    KitSheet(
        visible = picture != null,
        onDismissRequest = actions::closePicture,
        title = picture?.name ?: stringResource(Res.string.chatFeelPicture),
        closeLabel = stringResource(Res.string.chatFeelDone),
        height = io.github.eslamasabry.oc.ui.kit.modal.KitSheetHeight.Full,
    ) {
        if (picture != null) {
            KitPictureView(
                content =
                    when {
                        picture.failed -> KitPictureContent.Unavailable
                        picture.bytes == null -> KitPictureContent.Loading
                        else -> KitPictureContent.Bytes(picture.bytes)
                    },
                description = picture.name,
                loadingLabel = stringResource(Res.string.chatFeelPictureLoading),
                failedLabel = stringResource(Res.string.chatFeelPictureFailed),
            )
        }
    }
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
        is ChatConfirm.UndoFrom ->
            KitConfirmSpec(
                title = stringResource(Res.string.chatFeelUndoTitle),
                confirmLabel = stringResource(Res.string.chatFeelUndoAction),
                kind = KitConfirmKind.Destructive,
                body = stringResource(Res.string.chatFeelUndoBody),
            )
        is ChatConfirm.OpenLink ->
            KitConfirmSpec(
                title =
                    stringResource(
                        if (confirm.insecure) Res.string.e7SharedOpenInsecureHTTPLink
                        else Res.string.chatFeelOpenLinkTitle
                    ),
                confirmLabel =
                    stringResource(
                        if (confirm.insecure) Res.string.e7SharedOpenHTTPLink
                        else Res.string.chatFeelOpenLinkAction
                    ),
                body = stringResource(Res.string.chatFeelOpenLinkBody, confirm.host),
                kind = if (confirm.insecure) KitConfirmKind.Destructive else KitConfirmKind.Neutral,
                consequences =
                    if (confirm.insecure)
                        listOf(stringResource(Res.string.e7SharedHTTPIsNotEncryptedOtherDevicesOn))
                    else emptyList(),
                cancelLabel =
                    if (confirm.insecure) stringResource(Res.string.externalLinkDontOpen) else null,
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
        ChatStatusKind.ReceiptChecked ->
            KitStatus(
                KitStatusKind.Info,
                stringResource(
                    when (line.receiptResult) {
                        ReceiptCheckResult.Confirmed -> Res.string.queuedReceiptConfirmed
                        ReceiptCheckResult.StorageProblem -> Res.string.queuedReceiptStorageProblem
                        ReceiptCheckResult.StillUnknown,
                        null -> Res.string.queuedReceiptStillUncertain
                    }
                ),
                id = "chat-status-${line.id}",
                onDismiss = { actions.dismissStatus(line.id) },
                dismissLabel = stringResource(Res.string.kitSheetDismiss),
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
        ChatStatusKind.LinkRefused ->
            KitStatus(
                KitStatusKind.Info,
                stringResource(Res.string.chatFeelLinkRefused),
                id = "chat-status-${line.id}",
                onDismiss = { actions.dismissStatus(StatusPick.LINK) },
                dismissLabel = stringResource(Res.string.kitSheetDismiss),
            )
    }

/**
 * P12-CHAT-FEEL: what a reply's parts do. Links and web pictures go to [ChatActions.openLink] (the
 * link policy, then the one external opener); a code block's Copy copies its whole source verbatim.
 */
@Composable
internal fun rememberReplyActions(
    actions: ChatActions,
    confirmedPaths: kotlinx.collections.immutable.ImmutableSet<String> =
        kotlinx.collections.immutable.persistentSetOf(),
): ReplyActions {
    val copy = stringResource(Res.string.kitCopy)
    val copied = stringResource(Res.string.kitCopied)
    val picture = stringResource(Res.string.chatFeelPicture)
    val onServer = stringResource(Res.string.chatFeelPictureOnServer, "\u0000")
    val onWeb = stringResource(Res.string.chatFeelPictureOnWeb, "\u0000")
    val continueLabel = stringResource(Res.string.returnBriefContinue)
    return remember(
        actions,
        copy,
        copied,
        picture,
        onServer,
        onWeb,
        confirmedPaths,
        continueLabel,
    ) {
        ReplyActions(
            copiedLabel = copied,
            markdown =
                KitMarkdownActions(
                    onLink = actions::openLink,
                    // B-CHAT-064: a path the server confirmed opens its file (a picture in the
                    // viewer).
                    onPath = actions::openPath,
                    confirmedPaths = confirmedPaths,
                    copyCodeLabel = copy,
                    onCopyCode = actions::copyText,
                    onPicture = { p ->
                        val url = p.url
                        val path = p.path
                        when {
                            url != null -> actions.openLink(url)
                            path != null ->
                                actions.openPicture(path, p.label ?: path.substringAfterLast('/'))
                        }
                    },
                    openFilePictures = true,
                    readPicture = actions::readPicture,
                    pictureLabel = { p ->
                        p.label
                            ?: p.path?.let { onServer.replace("\u0000", it) }
                            ?: p.host?.let { onWeb.replace("\u0000", it) }
                            ?: picture
                    },
                ),
            copyLabel = copy,
            copy = actions::copyText,
            checkPaths = actions::checkPaths,
            continueLabel = continueLabel,
            onContinue = actions::continueReply,
        )
    }
}

/**
 * B-CHAT-114: a file a step produced opens in the file viewer; a web address goes through the link
 * policy.
 */
internal fun openArtifact(
    actions: ChatActions,
    file: io.github.eslamasabry.oc.engine.transcript.turnfacts.ToolArtifact,
) {
    val path = file.path
    val url = file.url
    when {
        path != null -> actions.openPath(path, null)
        url != null && (url.startsWith("https://") || url.startsWith("http://")) ->
            actions.openLink(url)
    }
}

/** Whether [openArtifact] can open [file]. */
internal fun canOpenArtifact(
    file: io.github.eslamasabry.oc.engine.transcript.turnfacts.ToolArtifact
): Boolean =
    file.path != null ||
        file.url?.let { it.startsWith("https://") || it.startsWith("http://") } == true

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
    val thoughtWord = stringResource(Res.string.kitMessageThought)
    val separator = stringResource(Res.string.kitWorkSeparator)
    return remember(
        jump,
        more,
        didntFinish,
        compacted,
        instructions,
        skill,
        working,
        stopped,
        thoughtWord,
        separator,
    ) {
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

            override fun stoppedFooter(): String = stopped

            // QA wave 4 B15: the thinking line says what it is ("Thought · …"), not unlabeled grey
            // text.
            override fun thought(firstLine: String): String = thoughtWord + separator + firstLine
        }
    }
}

/**
 * The work line: "3 steps · Working" (plural rules per language, so it is drawn in composition).
 */
@Composable
internal fun workLineWords(calls: Int, state: WorkState): String {
    val steps = pluralStringResource(Res.plurals.kitWorkSteps, calls, Numbers.format(calls))
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

/** B5: the plain line for each kind of failed reply (resolved once per composition). */
@Composable
private fun rememberReplyFailedWords(): (ReplyFailureKind) -> String {
    val signIn = stringResource(Res.string.chatReplyFailedSignIn)
    val model = stringResource(Res.string.chatReplyFailedModel)
    val busy = stringResource(Res.string.chatReplyFailedBusy)
    val other = stringResource(Res.string.chatReplyFailedOther)
    val noCredit = stringResource(Res.string.chatReplyFailedNoCredit)
    val lost = stringResource(Res.string.chatReplyFailedConnectionLost)
    return remember(signIn, model, busy, other, noCredit, lost) {
        { kind ->
            when (kind) {
                ReplyFailureKind.SignIn -> signIn
                ReplyFailureKind.NoCredit -> noCredit
                ReplyFailureKind.Model -> model
                ReplyFailureKind.Busy -> busy
                ReplyFailureKind.ConnectionLost -> lost
                ReplyFailureKind.Other -> other
            }
        }
    }
}

/** B2: why a send did not leave, in plain words (the code goes under Details). */
@Composable
private fun rememberSendFailureWords(): (Failure) -> String {
    val authored = rememberProductFailureWords()
    val changed = stringResource(Res.string.chatSendFailedChanged)
    val phoneBusy = stringResource(Res.string.schedulePhoneBusy)
    val phoneCheckFailed = stringResource(Res.string.schedulePhoneCheckFailed)
    return remember(authored, changed, phoneBusy, phoneCheckFailed) {
        { failure ->
            when {
                failure is Failure.Unavailable && failure.feature.name == "schedulePhoneBusy" ->
                    phoneBusy
                failure is Failure.Unavailable &&
                    failure.feature.name == "schedulePhoneCheckFailed" -> phoneCheckFailed
                failure is Failure.Refused && failure.reason == RefusalReason.StaleScope -> changed
                else -> authored(failure)
            }
        }
    }
}
