package io.github.eslamasabry.oc.feature.chat.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.collectAsState
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
import io.github.eslamasabry.oc.core.model.transcript.MessageErrorKind
import io.github.eslamasabry.oc.engine.transcript.state.NoticeKind
import io.github.eslamasabry.oc.engine.transcript.state.WorkState
import io.github.eslamasabry.oc.feature.chat.composer.ChatPromptsDoor
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerHost
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.composer.InsertRequest
import io.github.eslamasabry.oc.feature.chat.context.ActiveContextModel
import io.github.eslamasabry.oc.feature.chat.context.ActiveContextSheet
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
import io.github.eslamasabry.oc.feature.chat.presentation.MessageAction
import io.github.eslamasabry.oc.feature.chat.presentation.MessageOrder
import io.github.eslamasabry.oc.feature.chat.presentation.PictureUi
import io.github.eslamasabry.oc.feature.chat.presentation.ReceiptCheckResult
import io.github.eslamasabry.oc.feature.chat.presentation.ReplyFailureKind
import io.github.eslamasabry.oc.feature.chat.presentation.SelectTextUi
import io.github.eslamasabry.oc.feature.chat.presentation.StatusPick
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsUi
import io.github.eslamasabry.oc.feature.chat.requests.presentation.ChatRequestActions
import io.github.eslamasabry.oc.feature.chat.requests.presentation.RequestSlotUi
import io.github.eslamasabry.oc.feature.chat.requests.ui.ChatRequestSlot
import io.github.eslamasabry.oc.feature.chat.start.presentation.ChatStartActions
import io.github.eslamasabry.oc.feature.chat.start.presentation.ChatStartUi
import io.github.eslamasabry.oc.feature.chat.start.ui.ChatStartArea
import io.github.eslamasabry.oc.feature.chat.tasks.ui.ChatTasksSheet
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.effects.FrostRole
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrost
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrostSource
import io.github.eslamasabry.oc.ui.kit.foundation.rememberKitFrostState
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.frame.kitScreenChromePadding
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitBackGuard
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
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
import io.github.eslamasabry.oc.ui.kitchat.code.CodeLanguage
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeBlock
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeKind
import io.github.eslamasabry.oc.ui.kitchat.header.KitChatMoreMenu
import io.github.eslamasabry.oc.ui.kitchat.image.KitPictureContent
import io.github.eslamasabry.oc.ui.kitchat.image.KitPictureView
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdownActions
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMdBlock
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdownSelection
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import io.github.eslamasabry.oc.ui.kitchat.stream.KitPromptLiftHost
import io.github.eslamasabry.oc.ui.kitchat.stream.rememberRevealRegistry
import io.github.eslamasabry.oc.ui.kitchat.transcript.TranscriptListState
import io.github.eslamasabry.oc.ui.kitchat.transcript.rememberTranscriptListState
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.agentErrorOpenSettings
import io.github.eslamasabry.oc.ui.words.res.chatErrorContentFilter
import io.github.eslamasabry.oc.ui.words.res.chatErrorContextOverflow
import io.github.eslamasabry.oc.ui.words.res.chatErrorModelNotFound
import io.github.eslamasabry.oc.ui.words.res.chatErrorOutputLength
import io.github.eslamasabry.oc.ui.words.res.chatErrorProviderAuth
import io.github.eslamasabry.oc.ui.words.res.chatErrorUnknown
import io.github.eslamasabry.oc.ui.words.res.chatFeelDone
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
import io.github.eslamasabry.oc.ui.words.res.chatUiChanges
import io.github.eslamasabry.oc.ui.words.res.chatUiCollapseReasoning
import io.github.eslamasabry.oc.ui.words.res.chatCollapseAllSteps
import io.github.eslamasabry.oc.ui.words.res.composerFullCommands
import io.github.eslamasabry.oc.ui.words.res.workCount
import io.github.eslamasabry.oc.ui.words.res.chatSendFailureAccountAction
import io.github.eslamasabry.oc.ui.words.res.chatUiChooseAnotherModel
import io.github.eslamasabry.oc.ui.words.res.chatUiOpenProviders
import io.github.eslamasabry.oc.ui.words.res.chatUiUseModelAndResend
import io.github.eslamasabry.oc.ui.words.res.chatUiCompactConfirmAction
import io.github.eslamasabry.oc.ui.words.res.chatUiCompactConfirmBody
import io.github.eslamasabry.oc.ui.words.res.chatUiCompactConfirmTitle
import io.github.eslamasabry.oc.ui.words.res.chatUiCompactSession
import io.github.eslamasabry.oc.ui.words.res.chatUiContextCompacted
import io.github.eslamasabry.oc.ui.words.res.chatUiDeleteMessage
import io.github.eslamasabry.oc.ui.words.res.chatUiDeleteThisMessage
import io.github.eslamasabry.oc.ui.words.res.chatUiEarlierMessageCount
import io.github.eslamasabry.oc.ui.words.res.chatUiExpandReasoning
import io.github.eslamasabry.oc.ui.words.res.chatUiFiles
import io.github.eslamasabry.oc.ui.words.res.chatUiNotConnectedToTheServerRightNow
import io.github.eslamasabry.oc.ui.words.res.chatUiOpenCodeIsReconnectingTryAgainShortly
import io.github.eslamasabry.oc.ui.words.res.chatUiPromptNotAnswered
import io.github.eslamasabry.oc.ui.words.res.chatUiTheMessageAndAllOfItsParts
import io.github.eslamasabry.oc.ui.words.res.chatUiTimeline
import io.github.eslamasabry.oc.ui.words.res.commandUntitledChat
import io.github.eslamasabry.oc.ui.words.res.commonRetry
import io.github.eslamasabry.oc.ui.words.res.draftLeaveAction
import io.github.eslamasabry.oc.ui.words.res.draftLeaveQuestion
import io.github.eslamasabry.oc.ui.words.res.draftLeaveMessage
import io.github.eslamasabry.oc.ui.words.res.e7SharedHTTPIsNotEncryptedOtherDevicesOn
import io.github.eslamasabry.oc.ui.words.res.e7SharedOpenHTTPLink
import io.github.eslamasabry.oc.ui.words.res.e7SharedOpenInsecureHTTPLink
import io.github.eslamasabry.oc.ui.words.res.externalLinkDontOpen
import io.github.eslamasabry.oc.ui.words.res.gitSheetTitle
import io.github.eslamasabry.oc.ui.words.res.kitConfirmFailed
import io.github.eslamasabry.oc.ui.words.res.kitContextRingSemantics
import io.github.eslamasabry.oc.ui.words.res.kitCopied
import io.github.eslamasabry.oc.ui.words.res.markdownReaderTitle
import io.github.eslamasabry.oc.ui.words.res.terminalOpenFull
import io.github.eslamasabry.oc.ui.words.res.kitCopy
import io.github.eslamasabry.oc.ui.words.res.kitJumpLatest
import io.github.eslamasabry.oc.ui.words.res.kitMessageActions
import io.github.eslamasabry.oc.ui.words.res.kitMessageThought
import io.github.eslamasabry.oc.ui.words.res.kitSheetDismiss
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.kitTopBarMore
import io.github.eslamasabry.oc.ui.words.res.kitTopBarSearch
import io.github.eslamasabry.oc.ui.words.res.kitTurnLiveSending
import io.github.eslamasabry.oc.ui.words.res.kitTurnLiveWorking
import io.github.eslamasabry.oc.ui.words.res.kitTurnMore
import io.github.eslamasabry.oc.ui.words.res.kitWorkDidntFinish
import io.github.eslamasabry.oc.ui.words.res.kitWorkSeparator
import io.github.eslamasabry.oc.ui.words.res.kitWorkSteps
import io.github.eslamasabry.oc.ui.words.res.kitWorkStopped
import io.github.eslamasabry.oc.ui.words.res.kitWorkWorking
import io.github.eslamasabry.oc.ui.words.res.mobileTasksProgress
import io.github.eslamasabry.oc.ui.words.res.nlibTitle
import io.github.eslamasabry.oc.ui.words.res.phoneFileInstallAction
import io.github.eslamasabry.oc.ui.words.res.phoneFileInstallBody
import io.github.eslamasabry.oc.ui.words.res.phoneFileInstallTitle
import io.github.eslamasabry.oc.ui.words.res.phoneFileOpenAction
import io.github.eslamasabry.oc.ui.words.res.phoneFileOpenBody
import io.github.eslamasabry.oc.ui.words.res.phoneFileOpenTitle
import io.github.eslamasabry.oc.ui.words.res.phoneServerCardStopping
import io.github.eslamasabry.oc.ui.words.res.phoneSharedAccessBody
import io.github.eslamasabry.oc.ui.words.res.phoneSharedAccessTitle
import io.github.eslamasabry.oc.ui.words.res.promptStashTitle
import io.github.eslamasabry.oc.ui.words.res.queuedDiscardUnconfirmedMessage
import io.github.eslamasabry.oc.ui.words.res.queuedReceiptConfirmed
import io.github.eslamasabry.oc.ui.words.res.queuedReceiptStillUncertain
import io.github.eslamasabry.oc.ui.words.res.queuedReceiptStorageProblem
import io.github.eslamasabry.oc.ui.words.res.returnBriefContinue
import io.github.eslamasabry.oc.ui.words.res.schedulePhoneBusy
import io.github.eslamasabry.oc.ui.words.res.schedulePhoneCheckFailed
import io.github.eslamasabry.oc.ui.words.res.sessionInstructionsUpdated
import io.github.eslamasabry.oc.ui.words.res.skillApplied
import io.github.eslamasabry.oc.ui.words.res.stateDemoDisclosure
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
    /**
     * B-CHAT-001..003: the active-context inspector this conversation opens from its bar (null
     * where the server has none).
     */
    activeContext: ActiveContextModel? = null,
    start: ChatStartUi = ChatStartUi(),
    startActions: ChatStartActions? = null,
    demoMode: Boolean = false,
    appStatus: KitStatus? = null,
    replyOffer: (@Composable () -> Unit)? = null,
    formWaiting: Boolean = false,
    formSlot: (@Composable (Modifier) -> Unit)? = null,
    monitoredForm: (@Composable () -> Unit)? = null,
    transcriptDisplay: io.github.eslamasabry.oc.feature.chat.presentation.TranscriptDisplayChoices =
        io.github.eslamasabry.oc.feature.chat.presentation.TranscriptDisplayChoices(),
    /** The models the error card may suggest ("Use {model} and resend"). */
    modelChoices: List<io.github.eslamasabry.oc.feature.chat.world.ComposerModel> = emptyList(),
    /** Background commands, Stop command and Run shell command (null = not offered here). */
    work: io.github.eslamasabry.oc.feature.chat.work.ChatWorkUi =
        io.github.eslamasabry.oc.feature.chat.work.ChatWorkUi(),
    workModel: io.github.eslamasabry.oc.feature.chat.work.ChatWorkModel? = null,
    serverCommands: List<io.github.eslamasabry.oc.feature.chat.world.ComposerCommand> =
        emptyList(),
    onLoadServerCommands: () -> Unit = {},
) {
    var commandsOpen by remember(state.ref) { mutableStateOf(false) }
    var workOpen by remember(state.ref) { mutableStateOf(false) }
    var commandInsert by
        remember(state.ref) {
            mutableStateOf<io.github.eslamasabry.oc.feature.chat.composer.InsertRequest?>(null)
        }
    var commandInsertId by remember(state.ref) { mutableStateOf(0L) }
    var modelPicker by remember(state.ref) { mutableStateOf<Long?>(null) }
    var codeReader by remember(state.ref) { mutableStateOf<KitMdBlock.Code?>(null) }
    val folds = rememberTranscriptFolds()
    var contextOpen by remember { mutableStateOf(false) }
    var tasksOpen by remember(state.ref) { mutableStateOf(false) }
    var barMenuOpen by remember(state.ref) { mutableStateOf(false) }
    val startState by rememberUpdatedState(start)
    val currentStartActions by rememberUpdatedState(startActions)
    KitBackGuard(enabled = state.guardBack, onBack = actions::leaveRequested)
    val requestState by rememberUpdatedState(requests)
    val formPending by rememberUpdatedState(formWaiting)
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
            messageError = rememberMessageErrorWords(),
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
    val headerUnder by remember(list) { derivedStateOf { list.lazy.canScrollForward } }
    val scheduledId by actions.laterScheduled.collectAsState()
    val chrome =
        remember(actions, prompts) {
            ComposerChrome(
                busy = { latest.live != null },
                requestWaiting = { requestState.card != null || formPending },
                onSent = { list.following = true },
                // "Edit and resend" (P12-CHAT-FEEL) first, then saved prompts and slash commands
                // (P12-PROMPTS); each
                // acknowledgement goes back to the side that offered it. Agent ```choices arrive
                // with cards (S5).
                pendingInsert = {
                    commandInsert
                        ?: latest.insert?.let { InsertRequest(it.id, it.text, replace = true) }
                        ?: startState.insert?.let {
                            InsertRequest(it.id, it.text, replace = true, onlyIfEmpty = true)
                        }
                        ?: prompts?.pending()
                },
                insertDone = { id ->
                    when {
                        commandInsert?.id == id -> commandInsert = null
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
                freeModel = { headerState?.state?.freeModel == true },
                agentName = { headerState?.state?.agentName ?: "OpenCode" },
                openCommands =
                    if (demoMode) null
                    else {
                        {
                            onLoadServerCommands()
                            commandsOpen = true
                        }
                    },
                sendLater = if (demoMode) null else actions::sendLater,
                laterScheduled = { scheduledId },
                laterShown = actions::laterShown,
                modelPickerRequest = { modelPicker },
                modelPickerDone = { id -> if (modelPicker == id) modelPicker = null },
            )
        }

    val commandEntries =
        rememberChatCommandEntries(
            state = state,
            actions = actions,
            work = workModel,
            serverCommands = serverCommands,
            serverGroup = header?.state?.agentName ?: "OpenCode",
            onFind = { actions.find(state.find?.query ?: "") },
            onTasks = {
                if (workModel != null && work.shells.isNotEmpty()) {
                    workModel.refresh()
                    workOpen = true
                } else tasksOpen = true
            },
            onChooseModel = { modelPicker = (modelPicker ?: 0L) + 1L },
            onRename = header?.actions?.let { h -> { h.renameRequested() } },
            onInsert = { text ->
                // Negative ids: never equal to the chat's or the saved prompts' own.
                commandInsertId -= 1
                commandInsert =
                    io.github.eslamasabry.oc.feature.chat.composer.InsertRequest(
                        commandInsertId,
                        text,
                        replace = true,
                    )
            },
        )

    // The frame, its sibling menu and its confirmation share one modal layer. KitScreen
    // reuses this ancestor rather than creating a host scoped only to its own content.
    KitPromptLiftHost {
        KitSheetHost(modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                KitScreen(
                    modifier = Modifier.fillMaxSize(),
                    page = true,
                    overlayChrome = overlays,
                    hideStatusWhenTyping = true,
                    topBar = {
                        Box(
                            Modifier.fillMaxWidth()
                                .kitFrost(
                                    FrostRole.ChatHeader,
                                    frost,
                                    // N-45: the header is always a solid surface; scrolled rows drew over its title
                                    // when it was only a blur. (The blur returns once its tint keeps the title readable.)
                                    shown = false,
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
                                        icon = KitIcons.usageRing,
                                    )
                                }
                            val unitActions =
                                // STATE: the offline demo offers no server actions.
                                (if (demoMode) emptyList()
                                else
                                    listOfNotNull(
                                        // Project navigation is in the scope line; these actions
                                        // stay in the session menu.
                                        KitLineAction(
                                            stringResource(Res.string.chatUiFiles),
                                            actions::openFiles,
                                            testTag = "chat-files-open",
                                            icon = KitIcons.files,
                                        ),
                                        KitLineAction(
                                            stringResource(Res.string.chatUiTimeline),
                                            actions::openTimeline,
                                            testTag = "chat-timeline-open",
                                            icon = KitIcons.timeline,
                                        ),
                                        KitLineAction(
                                            stringResource(Res.string.composerFullCommands),
                                            {
                                                onLoadServerCommands()
                                                commandsOpen = true
                                            },
                                            testTag = "chat-commands-open",
                                            icon = KitIcons.terminal,
                                        ),
                                        if (workModel != null && work.running > 0)
                                            KitLineAction(
                                                stringResource(Res.string.workCount, work.running),
                                                {
                                                    workModel.refresh()
                                                    workOpen = true
                                                },
                                                testTag = "chat-work-open",
                                                icon = KitIcons.branch,
                                            )
                                        else null,
                                        if (folds.anyOpen)
                                            KitLineAction(
                                                stringResource(Res.string.chatCollapseAllSteps),
                                                folds::collapseAll,
                                                testTag = "chat-collapse-all",
                                                icon = KitIcons.unfoldLess,
                                            )
                                        else null,
                                        if (state.capabilities.compact)
                                            KitLineAction(
                                                stringResource(Res.string.chatUiCompactSession),
                                                actions::compactRequested,
                                                enabled = state.live == null,
                                                testTag = "chat-compact-open",
                                                icon = KitIcons.layers,
                                            )
                                        else null,
                                        facts.tasks?.let { tasks ->
                                            KitLineAction(
                                                stringResource(
                                                    Res.string.mobileTasksProgress,
                                                    tasks.done,
                                                    tasks.total,
                                                ),
                                                { tasksOpen = true },
                                                testTag = "chat-tasks-open",
                                                icon = KitIcons.checklist,
                                            )
                                        },
                                        KitLineAction(
                                            stringResource(Res.string.gitSheetTitle),
                                            actions::openGit,
                                            testTag = "chat-git-chip",
                                            icon = KitIcons.branch,
                                        ),
                                        KitLineAction(
                                            stringResource(Res.string.chatUiChanges),
                                            actions::openChanges,
                                            testTag = "chat-changes-open",
                                            icon = KitIcons.review,
                                        ),
                                        prompts?.let {
                                            KitLineAction(
                                                stringResource(Res.string.promptStashTitle),
                                                it::open,
                                                testTag = "chat-saved-prompts-open",
                                                icon = KitIcons.bookmarks,
                                            )
                                        },
                                        KitLineAction(
                                            stringResource(
                                                if (state.reasoningExpanded)
                                                    Res.string.chatUiCollapseReasoning
                                                else Res.string.chatUiExpandReasoning
                                            ),
                                            actions::toggleReasoning,
                                            testTag = "chat-reasoning-toggle",
                                            icon = KitIcons.idea,
                                        ),
                                        KitLineAction(
                                            stringResource(Res.string.nlibTitle),
                                            actions::openTools,
                                            testTag = "chat-tools-open",
                                            icon = KitIcons.tools,
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
                                    extraActions = unitActions + listOfNotNull(context),
                                    onProject = actions::openFiles,
                                    onBranch = actions::openGit,
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
                                                icon = KitIcons.search,
                                            ),
                                        ) + unitActions,
                                    more = { hidden ->
                                        KitChatMoreMenu(
                                            label = stringResource(Res.string.kitTopBarMore),
                                            expanded = barMenuOpen,
                                            items =
                                                hidden.map { extra ->
                                                    KitMenuItem(
                                                        label = extra.label,
                                                        onSelect = {
                                                            barMenuOpen = false
                                                            extra.onClick()
                                                        },
                                                        icon = extra.icon,
                                                        enabled =
                                                            if (extra.enabled) KitEnabled.Enabled
                                                            else
                                                                KitEnabled.Disabled(
                                                                    extra.disabledReason.orEmpty()
                                                                ),
                                                    )
                                                },
                                            onOpen = { barMenuOpen = true },
                                            onDismissRequest = { barMenuOpen = false },
                                        )
                                    },
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
                        else
                            KitStatus.highest(
                                listOf(
                                    // An unconfirmed send is said once, on its own bubble with Check again.
                                    state.status
                                        ?.takeUnless { it.kind == ChatStatusKind.SendUnconfirmed }
                                        ?.let { statusLine(it, actions) },
                                    appStatus,
                                )
                            ),
                    loading = state.phase == ChatPhase.Loading,
                    loadingLabel = stringResource(Res.string.chatLoadingConversation),
                    bottom = {
                        Column {
                            // Notification opt-in is a review sheet; pending-send recovery uses the
                            // status slot.
                            replyOffer?.invoke()
                            // Optional permission asks never compete with the conversation.
                            // P12-VOICE: the reading strip (Stop, notes) and the once-per-phone
                            // consent.
                            replyReader?.Reader(
                                state.readAloud?.let { KitReadRequest(it.id, it.rowKey, it.text) },
                                actions::readAloudDone,
                                Modifier,
                            )
                            monitoredForm?.invoke()
                            if (requestActions != null)
                                ChatRequestSlot(
                                    requests,
                                    requestActions,
                                    nowMs = null,
                                    modifier = Modifier,
                                )
                            if (requests.card == null) formSlot?.invoke(Modifier)
                            val find = state.find
                            if (find != null) TranscriptFindBar(find, actions)
                            else
                            // Find owns the bottom input while it is open. The same entry's
                            // composer ViewModel keeps the draft, attachments and selected model;
                            // closing Find restores them without stacking two fields above IME.
                            composerHost.Composer(
                                    ComposerTarget(
                                        state.ref.profileId.raw,
                                        state.ref.sessionId.raw,
                                    ),
                                    chrome,
                                    Modifier.fillMaxWidth().padding(vertical = Space.S2.dp),
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
                                retryLabel = stringResource(Res.string.commonRetry),
                                modifier = Modifier.testTag("chat-load-error"),
                            )
                        ChatPhase.Live ->
                            Box(Modifier.fillMaxSize().kitFrostSource(frost)) {
                                // PORT-SCREENS-1: an empty conversation offers its starters
                                // instead.
                                if (
                                    state.rows.isEmpty() &&
                                        state.pending.isEmpty() &&
                                        state.live == null &&
                                        start.emptyEligible &&
                                        startActions != null
                                ) {
                                    ChatStartArea(
                                        start,
                                        startActions,
                                        projectName = header?.state?.projectName,
                                    )
                                } else
                                    ChatTranscriptList(
                                        folds = folds,
                                        rows = state.rows,
                                        live = state.live,
                                        pending = state.pending,
                                        words = words,
                                        listWords = listWords,
                                        list = list,
                                        reveal = reveal,
                                        motion = motion,
                                        onOpenMenu = actions::openMessageMenu,
                                        onCopyReply = { footer ->
                                            actions.messageAction(
                                                io.github.eslamasabry.oc.feature.chat.presentation
                                                    .MessageTarget(
                                                        footer.turnId,
                                                        footer.key,
                                                        io.github.eslamasabry.oc.feature.chat
                                                            .presentation
                                                            .TargetKind
                                                            .Reply,
                                                        null,
                                                    ),
                                                MessageAction.Copy,
                                            )
                                        },
                                        onRetrySend = actions::retrySend,
                                        receiptChecks = state.receiptChecks,
                                        onCheckReceipt = actions::checkReceipt,
                                        modifier = Modifier.fillMaxSize(),
                                        facts = facts,
                                        display = transcriptDisplay,
                                        onOpenSession = onOpenSession,
                                        reply =
                                            rememberReplyActions(
                                                actions,
                                                state.confirmedPaths,
                                                models = modelChoices,
                                                canCompact =
                                                    state.capabilities.compact && state.live == null,
                                                onChooseModel = {
                                                    modelPicker = (modelPicker ?: 0L) + 1L
                                                },
                                                onOpenCode = { codeReader = it },
                                            ),
                                        onOpenFile = { file -> openArtifact(actions, file) },
                                        reasoningExpanded = state.reasoningExpanded,
                                        onCompactAgain =
                                            if (state.capabilities.compact) actions::compactAgain
                                            else null,
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
                    facts = facts,
                )
                ChatContextSheet(facts.context, contextOpen) { contextOpen = false }
                ChatTasksSheet(
                    facts.tasks,
                    tasksOpen,
                    onDismiss = { tasksOpen = false },
                    running = state.live != null,
                )
                ChatCommandsSheet(
                    visible = commandsOpen,
                    entries = commandEntries,
                    onDismiss = { commandsOpen = false },
                )
                if (workModel != null) {
                    ChatWorkSheet(work, workOpen, workModel) {
                        workOpen = false
                        workModel.closeOutput()
                    }
                    ChatShellDialog(work, workModel)
                }
                if (activeContext != null) ActiveContextSheet(activeContext)
                SelectTextSheet(state.select, actions)
                PictureSheet(state.picture, actions)
                CodeReaderSheet(codeReader, actions) { codeReader = null }
                ChatTimelineSheet(state.timeline, actions, facts.nowMs)
                KitConfirm(
                    spec = confirmSpec(state.confirm),
                    onConfirm = {
                        when (latest.confirm) {
                            is ChatConfirm.DeleteMessage -> actions.confirmDelete()
                            ChatConfirm.LeaveUnsaved -> actions.confirmLeave()
                            is ChatConfirm.UndoFrom -> actions.confirmUndo()
                            ChatConfirm.Compact -> actions.confirmCompact()
                            is ChatConfirm.OpenLink -> actions.confirmOpenLink()
                            is ChatConfirm.PhoneFile,
                            is ChatConfirm.PhoneFileAccess -> actions.confirmPhoneFile()
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

/**
 * "Code reader" (Flutter markdown_code_reader): a reply's code block in full, every line, with
 * Copy and Wrap lines on the block itself.
 */
@Composable
private fun CodeReaderSheet(code: KitMdBlock.Code?, actions: ChatActions, onClose: () -> Unit) {
    KitSheet(
        visible = code != null,
        onDismissRequest = onClose,
        title = stringResource(Res.string.markdownReaderTitle),
        closeLabel = stringResource(Res.string.chatFeelDone),
        height = io.github.eslamasabry.oc.ui.kit.modal.KitSheetHeight.Full,
    ) {
        if (code != null) {
            KitCodeBlock(
                source = code.text,
                kind = KitCodeKind.Code,
                language = CodeLanguage.of(code.info),
                open = code.open,
                maxLines = Int.MAX_VALUE,
                copyLabel = stringResource(Res.string.kitCopy),
                onCopy = actions::copyText,
                copiedLabel = stringResource(Res.string.kitCopied),
                modifier = Modifier.testTag("chat-code-reader"),
            )
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
                title = stringResource(Res.string.draftLeaveQuestion),
                confirmLabel = stringResource(Res.string.draftLeaveAction),
                kind = KitConfirmKind.Discard,
                body = stringResource(Res.string.draftLeaveMessage),
            )
        ChatConfirm.Compact ->
            KitConfirmSpec(
                title = stringResource(Res.string.chatUiCompactConfirmTitle),
                confirmLabel = stringResource(Res.string.chatUiCompactConfirmAction),
                body = stringResource(Res.string.chatUiCompactConfirmBody),
            )
        is ChatConfirm.UndoFrom ->
            KitConfirmSpec(
                title = stringResource(Res.string.chatFeelUndoTitle),
                confirmLabel = stringResource(Res.string.chatFeelUndoAction),
                kind = KitConfirmKind.Destructive,
                body = stringResource(Res.string.chatFeelUndoBody),
            )
        is ChatConfirm.PhoneFile -> {
            val name = confirm.path.substringAfterLast('/')
            if (confirm.install)
                KitConfirmSpec(
                    title = stringResource(Res.string.phoneFileInstallTitle, name),
                    confirmLabel = stringResource(Res.string.phoneFileInstallAction),
                    body = stringResource(Res.string.phoneFileInstallBody),
                )
            else
                KitConfirmSpec(
                    title = stringResource(Res.string.phoneFileOpenTitle, name),
                    confirmLabel = stringResource(Res.string.phoneFileOpenAction),
                    body = stringResource(Res.string.phoneFileOpenBody),
                )
        }
        is ChatConfirm.PhoneFileAccess ->
            KitConfirmSpec(
                title = stringResource(Res.string.phoneSharedAccessTitle),
                body = stringResource(Res.string.phoneSharedAccessBody),
                confirmLabel = stringResource(Res.string.commonRetry),
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
                action =
                    KitLineAction(
                        stringResource(Res.string.commonRetry),
                        actions::retryLoad,
                        testTag = "chat-offline-retry",
                    ),
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
                tone = StatusTone.Failure,
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
                tone = StatusTone.Attention,
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
                tone =
                    when (line.receiptResult) {
                        ReceiptCheckResult.Confirmed -> StatusTone.Neutral
                        ReceiptCheckResult.StorageProblem -> StatusTone.Failure
                        else -> StatusTone.Attention
                    },
                onDismiss = { actions.dismissStatus(line.id) },
                dismissLabel = stringResource(Res.string.kitSheetDismiss),
            )
        ChatStatusKind.RunFailed ->
            KitStatus(
                KitStatusKind.Info,
                stringResource(Res.string.chatUiPromptNotAnswered),
                id = "chat-status-${line.id}",
                tone = StatusTone.Failure,
            )
        ChatStatusKind.ActionFailed ->
            KitStatus(
                KitStatusKind.Info,
                stringResource(Res.string.kitConfirmFailed),
                id = "chat-status-${line.id}",
                tone = StatusTone.Failure,
                onDismiss = { actions.dismissStatus(StatusPick.ACTION) },
                dismissLabel = stringResource(Res.string.kitSheetDismiss),
            )
        ChatStatusKind.LinkRefused ->
            KitStatus(
                KitStatusKind.Info,
                stringResource(Res.string.chatFeelLinkRefused),
                id = "chat-status-${line.id}",
                tone = StatusTone.Failure,
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
    models: List<io.github.eslamasabry.oc.feature.chat.world.ComposerModel> = emptyList(),
    canCompact: Boolean = false,
    onChooseModel: () -> Unit = {},
    onOpenCode: (KitMdBlock.Code) -> Unit = {},
): ReplyActions {
    val openFullCode = stringResource(Res.string.terminalOpenFull, "\u0000")
    val chooseModel = stringResource(Res.string.chatUiChooseAnotherModel)
    val useModelTemplate = stringResource(Res.string.chatUiUseModelAndResend, "\u0000")
    val openProviders = stringResource(Res.string.chatUiOpenProviders)
    val openAccount = stringResource(Res.string.chatSendFailureAccountAction)
    val compactLabel = stringResource(Res.string.chatUiCompactSession)
    val copy = stringResource(Res.string.kitCopy)
    val copied = stringResource(Res.string.kitCopied)
    val picture = stringResource(Res.string.chatFeelPicture)
    val onServer = stringResource(Res.string.chatFeelPictureOnServer, "\u0000")
    val onWeb = stringResource(Res.string.chatFeelPictureOnWeb, "\u0000")
    val continueLabel = stringResource(Res.string.returnBriefContinue)
    val signInSettings =
        stringResource(io.github.eslamasabry.oc.ui.words.res.Res.string.agentErrorOpenSettings)
    return remember(
        actions,
        copy,
        copied,
        picture,
        onServer,
        onWeb,
        confirmedPaths,
        continueLabel,
        signInSettings,
        models,
        canCompact,
        onChooseModel,
        chooseModel,
        useModelTemplate,
        openProviders,
        openAccount,
        compactLabel,
        openFullCode,
        onOpenCode,
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
                    moreCodeLabel = { hidden -> openFullCode.replace("\u0000", hidden.toString()) },
                    onOpenCode = onOpenCode,
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
            signInSettingsLabel = signInSettings,
            onSignInSettings = actions::openSignInSettings,
            models = models,
            canCompact = canCompact,
            onCompact = actions::compactAgain,
            chooseModelLabel = chooseModel,
            onChooseModel = onChooseModel,
            useModelLabel = { name -> useModelTemplate.replace("\u0000", name) },
            onUseModel = actions::useModelAndResend,
            openProvidersLabel = openProviders,
            openAccountLabel = openAccount,
            compactLabel = compactLabel,
            onOpenProviders = actions::openProviders,
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
            override fun thought(firstLine: String): String =
                if (firstLine.isBlank()) thoughtWord else thoughtWord + separator + firstLine
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

/** Typed failures use existing native resources even when the provider sent no message. */
@Composable
private fun rememberMessageErrorWords(): (MessageErrorKind) -> String {
    val model = stringResource(Res.string.chatErrorModelNotFound)
    val context = stringResource(Res.string.chatErrorContextOverflow)
    val auth = stringResource(Res.string.chatErrorProviderAuth)
    val length = stringResource(Res.string.chatErrorOutputLength)
    val filter = stringResource(Res.string.chatErrorContentFilter)
    val unknown = stringResource(Res.string.chatErrorUnknown)
    return remember(model, context, auth, length, filter, unknown) {
        { kind ->
            when (kind) {
                MessageErrorKind.ModelNotFound -> model
                MessageErrorKind.ContextOverflow -> context
                MessageErrorKind.ProviderAuth -> auth
                MessageErrorKind.OutputLength -> length
                MessageErrorKind.ContentFilter -> filter
                MessageErrorKind.Aborted,
                MessageErrorKind.Unknown -> unknown
            }
        }
    }
}
