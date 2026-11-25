package io.github.eslamasabry.oc.feature.chat.ui

import io.github.eslamasabry.oc.ui.design.effects.KitSurfaceStyle
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppearance
import io.github.eslamasabry.oc.feature.chat.world.ChatServerWait
import io.github.eslamasabry.oc.ui.kit.wait.KitWaitKind
import androidx.compose.runtime.saveable.rememberSaveable
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import io.github.eslamasabry.oc.core.common.text.ConversationTitle
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.core.model.transcript.MessageErrorKind
import io.github.eslamasabry.oc.engine.transcript.state.NoticeKind
import io.github.eslamasabry.oc.engine.transcript.state.WorkState
import io.github.eslamasabry.oc.feature.chat.composer.ChatPromptsDoor
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerHost
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.composer.InsertRequest
import io.github.eslamasabry.oc.feature.chat.composer.PictureRequest
import io.github.eslamasabry.oc.ui.kit.content.image.KitGalleryPicture
import io.github.eslamasabry.oc.ui.kit.content.image.KitImageGallery
import io.github.eslamasabry.oc.ui.kit.content.image.KitPngEncoder
import io.github.eslamasabry.oc.feature.chat.context.ActiveContextModel
import io.github.eslamasabry.oc.feature.chat.context.ActiveContextSheet
import io.github.eslamasabry.oc.feature.chat.header.ui.ApprovalChip
import io.github.eslamasabry.oc.feature.chat.header.ui.ApprovalSettingsRows
import io.github.eslamasabry.oc.feature.chat.header.ui.ChatHeaderOverlays
import io.github.eslamasabry.oc.feature.chat.header.ui.ChatHeaderSlot
import io.github.eslamasabry.oc.feature.chat.header.ui.ChatHeaderTopBar
import io.github.eslamasabry.oc.feature.chat.presentation.RunTailRules
import io.github.eslamasabry.oc.feature.chat.presentation.ChatActions
import io.github.eslamasabry.oc.feature.chat.presentation.ChatConfirm
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPlace
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.feature.chat.presentation.ChatStatusKind
import io.github.eslamasabry.oc.feature.chat.presentation.ChatStatusUi
import io.github.eslamasabry.oc.feature.chat.presentation.ChatUi
import io.github.eslamasabry.oc.feature.chat.presentation.MessageAction
import io.github.eslamasabry.oc.feature.chat.presentation.PictureUi
import io.github.eslamasabry.oc.feature.chat.presentation.ReceiptCheckResult
import io.github.eslamasabry.oc.feature.chat.presentation.ReplyFailureKind
import io.github.eslamasabry.oc.feature.chat.presentation.SelectTextUi
import io.github.eslamasabry.oc.feature.chat.presentation.StatusPick
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsUi
import io.github.eslamasabry.oc.feature.chat.requests.presentation.ChatRequestActions
import io.github.eslamasabry.oc.feature.chat.requests.presentation.RequestSlotUi
import io.github.eslamasabry.oc.feature.chat.requests.presentation.InPlaceRules
import io.github.eslamasabry.oc.feature.chat.requests.ui.ChatRequestInPlace
import io.github.eslamasabry.oc.feature.chat.requests.ui.ChatRequestSlot
import io.github.eslamasabry.oc.feature.chat.requests.ui.InPlaceRequests
import io.github.eslamasabry.oc.feature.chat.requests.ui.LocalInPlaceRequests
import io.github.eslamasabry.oc.feature.chat.requests.ui.RequestShow
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.ui.words.res.chatReplyInHint
import io.github.eslamasabry.oc.ui.words.res.chatWaitingForAnswerAbove
import io.github.eslamasabry.oc.ui.words.res.commandUntitledChat
import io.github.eslamasabry.oc.ui.words.res.workspaceNewSession
import io.github.eslamasabry.oc.feature.chat.header.ui.LocalChatHeaderActs
import io.github.eslamasabry.oc.feature.chat.start.presentation.ChatStartActions
import io.github.eslamasabry.oc.feature.chat.start.presentation.ChatStartUi
import io.github.eslamasabry.oc.feature.chat.start.ui.ChatStartArea
import io.github.eslamasabry.oc.feature.chat.tasks.ui.ChatTasksSheet
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.foundation.kitFrostSource
import io.github.eslamasabry.oc.ui.kit.foundation.kitOnHeightChanged
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
import io.github.eslamasabry.oc.ui.kit.modal.LocalKitSheetHost
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.motion.KitSwap
import io.github.eslamasabry.oc.ui.kit.status.KitStatus
import io.github.eslamasabry.oc.ui.kit.status.KitStatusKind
import io.github.eslamasabry.oc.ui.kitchat.composer.KitReadRequest
import io.github.eslamasabry.oc.ui.kitchat.composer.KitReplyReader
import io.github.eslamasabry.oc.ui.kitchat.code.CodeLanguage
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeBlock
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeKind
import io.github.eslamasabry.oc.ui.kitchat.header.KitChatMoreMenu
import io.github.eslamasabry.oc.ui.kitchat.header.KitCopyAnnouncement
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
import io.github.eslamasabry.oc.ui.words.res.backgroundWorkShortcut
import io.github.eslamasabry.oc.ui.words.res.chatSendFailureSignInAgain
import io.github.eslamasabry.oc.ui.words.res.messageViewSendAgain
import io.github.eslamasabry.oc.ui.words.res.chatSendFailureSignedInPast
import io.github.eslamasabry.oc.ui.words.res.agentsSignInAgain
import io.github.eslamasabry.oc.ui.words.res.chatSendFailureNeedsSetUp
import io.github.eslamasabry.oc.ui.words.res.agentsSetUpAction
import io.github.eslamasabry.oc.ui.words.res.chatStripBackground
import io.github.eslamasabry.oc.ui.words.res.revertResolveBeforeSending
import io.github.eslamasabry.oc.ui.words.res.revertReview
import io.github.eslamasabry.oc.ui.words.res.revertStaged
import io.github.eslamasabry.oc.ui.words.res.undoneStatus
import io.github.eslamasabry.oc.ui.words.res.undonePutBack
import io.github.eslamasabry.oc.ui.words.res.agentErrorOpenSettings
import io.github.eslamasabry.oc.ui.words.res.chatErrorContentFilter
import io.github.eslamasabry.oc.ui.words.res.chatErrorContextOverflow
import io.github.eslamasabry.oc.ui.words.res.chatErrorModelNotFound
import io.github.eslamasabry.oc.ui.words.res.chatErrorModelNotFoundNamed
import io.github.eslamasabry.oc.ui.words.res.chatErrorOutputLength
import io.github.eslamasabry.oc.ui.words.res.chatErrorProviderAuth
import io.github.eslamasabry.oc.ui.words.res.chatErrorUnknown
import io.github.eslamasabry.oc.ui.words.res.chatModelReplacedNotice
import io.github.eslamasabry.oc.ui.words.res.kitSheetClose
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
import io.github.eslamasabry.oc.ui.words.res.agentsSetUpAction
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
import io.github.eslamasabry.oc.ui.words.res.chatSendFailureAccountAction
import io.github.eslamasabry.oc.ui.words.res.chatUiChooseAnotherModel
import io.github.eslamasabry.oc.ui.words.res.chatUiChooseOption
import io.github.eslamasabry.oc.ui.words.res.chatUiRunOnYourComputer
import io.github.eslamasabry.oc.ui.words.res.chatUiRunOnThisPhone
import io.github.eslamasabry.oc.ui.words.res.chatUiTodo
import io.github.eslamasabry.oc.ui.words.res.modelChoiceDone
import io.github.eslamasabry.oc.ui.words.res.chatUiOpenProviders
import io.github.eslamasabry.oc.ui.words.res.chatUiUseModelAndResend
import io.github.eslamasabry.oc.ui.words.res.b169AskSend
import io.github.eslamasabry.oc.ui.words.res.b169AskTitle
import io.github.eslamasabry.oc.ui.words.res.b169AttachAgain
import io.github.eslamasabry.oc.ui.words.res.b169ConnectProvider
import io.github.eslamasabry.oc.ui.words.res.b169Offline
import io.github.eslamasabry.oc.ui.words.res.b169SwitchModel
import io.github.eslamasabry.oc.ui.words.res.b169UsedUp
import io.github.eslamasabry.oc.ui.words.res.b169UsedUpNoName
import io.github.eslamasabry.oc.ui.words.res.chatUiCompactConfirmAction
import io.github.eslamasabry.oc.ui.words.res.chatUiCompactConfirmBody
import io.github.eslamasabry.oc.ui.words.res.chatUiCompactConfirmTitle
import io.github.eslamasabry.oc.ui.words.res.chatUiCompactSession
import io.github.eslamasabry.oc.ui.words.res.chatUiContextCompacted
import io.github.eslamasabry.oc.ui.words.res.chatUiDeleteMessage
import io.github.eslamasabry.oc.ui.words.res.chatUiDeleteThisMessage
import io.github.eslamasabry.oc.ui.words.res.chatUiExpandReasoning
import io.github.eslamasabry.oc.ui.words.res.chatUiFiles
import io.github.eslamasabry.oc.ui.words.res.chatUiNotConnectedToTheServerRightNow
import io.github.eslamasabry.oc.ui.words.res.chatConnectingToAgent
import io.github.eslamasabry.oc.ui.words.res.chatUiOpenCodeIsReconnectingTryAgainShortly
import io.github.eslamasabry.oc.ui.words.res.chatUiPromptNotAnswered
import io.github.eslamasabry.oc.ui.words.res.chatUiTheMessageAndAllOfItsParts
import io.github.eslamasabry.oc.ui.words.res.chatUiTimeline
import io.github.eslamasabry.oc.ui.words.res.commandDestination
import io.github.eslamasabry.oc.ui.words.res.commonRetry
import io.github.eslamasabry.oc.ui.words.res.chatStopAfterStepPending
import io.github.eslamasabry.oc.ui.words.res.chatStopAfterStepPrompt
import io.github.eslamasabry.oc.ui.words.res.connectionReconnectingQuiet
import io.github.eslamasabry.oc.ui.words.res.connectionAgentStartFailed
import io.github.eslamasabry.oc.ui.words.res.connectionAgentStarting
import io.github.eslamasabry.oc.ui.words.res.connectStartingPhone
import io.github.eslamasabry.oc.ui.words.res.connectAgentNeedsSignIn
import io.github.eslamasabry.oc.ui.words.res.connectUpdatingAgents
import io.github.eslamasabry.oc.ui.words.res.connectUpdatingPhone
import io.github.eslamasabry.oc.ui.words.res.launchUiSessionWaiting
import io.github.eslamasabry.oc.ui.words.res.stageOpeningConversation
import io.github.eslamasabry.oc.ui.words.res.stageStartingAgent
import io.github.eslamasabry.oc.ui.words.res.stageStartingHelper
import io.github.eslamasabry.oc.ui.words.res.stageStartingLinux
import io.github.eslamasabry.oc.ui.words.res.stageStartingOpenCode
import io.github.eslamasabry.oc.ui.words.res.connectionRestartAgent
import io.github.eslamasabry.oc.ui.words.res.connectionRestartPhoneServer
import io.github.eslamasabry.oc.ui.words.res.phoneServerCardStartOpenCode
import io.github.eslamasabry.oc.ui.words.res.phoneStoppedByYou
import io.github.eslamasabry.oc.ui.words.res.workServerNotAnswering
import io.github.eslamasabry.oc.ui.words.res.connectionTryNow
import io.github.eslamasabry.oc.ui.words.res.chatUiDelegatedSession
import io.github.eslamasabry.oc.ui.words.res.chatUiOpenParentSession
import io.github.eslamasabry.oc.ui.words.res.chatUiShowAllSubagentSessions
import io.github.eslamasabry.oc.ui.words.res.connectionPasswordRejectedFor
import io.github.eslamasabry.oc.ui.words.res.connectionTokenRejectedFor
import io.github.eslamasabry.oc.ui.words.res.e7BannerDetails
import io.github.eslamasabry.oc.ui.words.res.e7BannerUpdatePassword
import io.github.eslamasabry.oc.ui.words.res.productStatesSwitchServer
import io.github.eslamasabry.oc.ui.words.res.updateConnectionToken
import io.github.eslamasabry.oc.ui.words.res.connectionTryingAgainIn
import io.github.eslamasabry.oc.ui.words.res.connectionTryingAgainNow
import io.github.eslamasabry.oc.ui.words.res.workServerNotAnsweringPhone
import io.github.eslamasabry.oc.ui.words.res.workServerRestart
import io.github.eslamasabry.oc.ui.words.res.workServerRestartBody
import io.github.eslamasabry.oc.ui.words.res.workServerRestartTitle
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
import io.github.eslamasabry.oc.ui.words.res.kitCopiedMasked
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
import io.github.eslamasabry.oc.ui.words.res.chatSendFolderMissing
import io.github.eslamasabry.oc.ui.words.res.chatSendPhoneFolder
import io.github.eslamasabry.oc.ui.words.res.chatSendPhoneHelperNoAnswer
import io.github.eslamasabry.oc.ui.words.res.chatSendFailureAgentFallback
import io.github.eslamasabry.oc.ui.words.res.chatSendNoPlanMode
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
    /** S24-CHAT story 3: with an encoder the viewer is the swipeable gallery with Edit and Send. */
    encodePng: KitPngEncoder? = null,
    formWaiting: Boolean = false,
    formSlot: (@Composable (Modifier) -> Unit)? = null,
    monitoredForm: (@Composable () -> Unit)? = null,
    transcriptDisplay: io.github.eslamasabry.oc.feature.chat.presentation.TranscriptDisplayChoices =
        io.github.eslamasabry.oc.feature.chat.presentation.TranscriptDisplayChoices(),
    /** C7: saves an inline file the agent produced (null where the device cannot). */
    toolFileSaver: io.github.eslamasabry.oc.feature.chat.world.ToolFileOutputs? = null,
    /** C7: puts an inline file the agent produced on the next prompt. */
    onAttachToolFile: ((io.github.eslamasabry.oc.engine.transcript.turnfacts.ToolArtifact) -> Unit)? = null,
    /** C10: flips message timestamps (the Settings choice); null where it cannot be saved. */
    onToggleTimestamps: (() -> Unit)? = null,
    /** The models the error card may suggest ("Use {model} and resend"). */
    modelChoices: List<io.github.eslamasabry.oc.feature.chat.world.ComposerModel> = emptyList(),
    /** [modelChoices] are being read now (the same models state's `loading`). */
    modelChoicesLoading: Boolean = false,
    /** Background commands, Stop command and Run shell command (null = not offered here). */
    work: io.github.eslamasabry.oc.feature.chat.work.ChatWorkUi =
        io.github.eslamasabry.oc.feature.chat.work.ChatWorkUi(),
    workModel: io.github.eslamasabry.oc.feature.chat.work.ChatWorkModel? = null,
    serverCommands: List<io.github.eslamasabry.oc.feature.chat.world.ComposerCommand> =
        emptyList(),
    onLoadServerCommands: () -> Unit = {},
    /** N-48: an unconfirmed bubble's Review opens the waiting sends (Send again, Discard). */
    onReviewPending: (() -> Unit)? = null,
    /**
     * Back shows where the chat was opened from somewhere (Flutter `KitTopBarExit.auto`); false for a chat at its
     * window's root, whose title starts on the gutter.
     */
    canGoBack: Boolean = true,
    /** S28 story 8: the conversation's handoff line, drawn at the top of the transcript; null = none. */
    handoffLine: (@Composable () -> Unit)? = null,
    /** The edge dock over the transcript, under the chat's sheets and viewers; null = none (see [ChatEdgeDock]). */
    edgeDock: ChatEdgeDock? = null,
    /** The request the edge light asks this page to show (already filtered to this conversation), or null. */
    requestFocus: io.github.eslamasabry.oc.ui.routes.RequestFocus? = null,
    /** The page showed request [seq] (scrolled to its card): the navigator drops it. */
    onRequestShown: (seq: Long) -> Unit = {},
    /** This page was just switched to with the edge light (filtered to this conversation), or null. */
    switchArrival: io.github.eslamasabry.oc.feature.chat.arrival.ChatArrival? = null,
    onHintTyped: (seq: Long) -> Unit = {},
    onBackNoteShown: (seq: Long) -> Unit = {},
) {
    var commandsOpen by rememberSaveable(state.ref) { mutableStateOf(false) }
    val slashCommands =
        remember(state.ref) {
            mutableStateOf<List<io.github.eslamasabry.oc.feature.chat.composer.ComposerSlashCommand>>(emptyList())
        }
    var workOpen by rememberSaveable(state.ref) { mutableStateOf(false) }
    var connectionDetails by rememberSaveable(state.ref) { mutableStateOf(false) }
    fun openConnectionDetails() {
        connectionDetails = true
    }
    var commandInsert by
        remember(state.ref) {
            mutableStateOf<io.github.eslamasabry.oc.feature.chat.composer.InsertRequest?>(null)
        }
    var commandInsertId by rememberSaveable(state.ref) { mutableStateOf(0L) }
    var modelPicker by remember(state.ref) { mutableStateOf<Long?>(null) }
    var modelCycle by remember(state.ref) { mutableStateOf<io.github.eslamasabry.oc.feature.chat.composer.ModelCycleRequest?>(null) }
    var editorRequest by remember(state.ref) { mutableStateOf<Long?>(null) }
    var sentPicture by remember(state.ref) { mutableStateOf<PictureRequest?>(null) }
    var pictureSeq by rememberSaveable(state.ref) { mutableStateOf(0L) }
    var codeReader by remember(state.ref) { mutableStateOf<KitMdBlock.Code?>(null) }
    val folds = rememberTranscriptFolds()
    var contextOpen by rememberSaveable { mutableStateOf(false) }
    var tasksOpen by rememberSaveable(state.ref) { mutableStateOf(false) }
    var barMenuOpen by rememberSaveable(state.ref) { mutableStateOf(false) }
    val startState by rememberUpdatedState(start)
    val currentStartActions by rememberUpdatedState(startActions)
    KitBackGuard(enabled = state.guardBack, onBack = actions::leaveRequested)
    val requestState by rememberUpdatedState(requests)
    val formPending by rememberUpdatedState(formWaiting)
    val headerState by rememberUpdatedState(header)
    // Closed on purpose when the person leaves the chat (plain `remember`, so neither Back nor rotation brings a
    // sheet they did not just open): the transcript itself comes back exactly where it was (KeepScrollPlace).
    var toolFile by remember {
        mutableStateOf<io.github.eslamasabry.oc.engine.transcript.turnfacts.ToolArtifact?>(null)
    }
    val factsState by rememberUpdatedState(facts)
    val words = rememberChatWords()
    val stopNote = stringResource(Res.string.chatStopAfterStepPrompt)
    // One instance while the words are the same: a new instance every pass kept the transcript list from skipping.
    val messageActions = stringResource(Res.string.kitMessageActions)
    val working = stringResource(Res.string.kitTurnLiveWorking)
    val stopping = stringResource(Res.string.phoneServerCardStopping)
    val stoppingAfterStep = stringResource(Res.string.chatStopAfterStepPending)
    val sending = stringResource(Res.string.kitTurnLiveSending)
    val notSent = stringResource(Res.string.chatSendFailed)
    val unconfirmed = stringResource(Res.string.queuedDiscardUnconfirmedMessage)
    val tryAgain = stringResource(Res.string.commonRetry)
    val replyFailed = rememberReplyFailedWords()
    val messageError = rememberMessageErrorWords()
    val details = stringResource(Res.string.chatReplyFailedDetails)
    val copyDetails = stringResource(Res.string.chatReplyFailedCopy)
    // Where this conversation runs decides what a failed reach is called: on this phone it is the phone's agents
    // helper, never "your computer".
    val onThisPhone = remember(actions) { actions.phoneAgentSignIn() }
    val sendFailure = rememberSendFailureWords(onThisPhone)
    val setUpNeeded = stringResource(Res.string.chatSendFailureNeedsSetUp, "\u0000")
    val setUpAction = stringResource(Res.string.agentsSetUpAction, "\u0000")
    val signInAgainLine = stringResource(Res.string.chatSendFailureSignInAgain, "\u0000")
    val signInAgainAction = stringResource(Res.string.agentsSignInAgain)
    val listWords =
        remember(
            messageActions,
            working,
            stopping,
            stoppingAfterStep,
            sending,
            notSent,
            unconfirmed,
            tryAgain,
            replyFailed,
            messageError,
            details,
            copyDetails,
            sendFailure,
            setUpNeeded,
            setUpAction,
            signInAgainLine,
            signInAgainAction,
        ) {
            ChatListWords(
                messageActions = messageActions,
                working = working,
                stopping = stopping,
                stoppingAfterStep = stoppingAfterStep,
                sending = sending,
                notSent = notSent,
                unconfirmed = unconfirmed,
                tryAgain = tryAgain,
                replyFailed = replyFailed,
                messageError = messageError,
                details = details,
                copyDetails = copyDetails,
                sendFailure = sendFailure,
                setUpNeeded = { name -> setUpNeeded.replace("\u0000", name) },
                setUpAction = { name -> setUpAction.replace("\u0000", name) },
                signInAgain = { name -> signInAgainLine.replace("\u0000", name) },
                signInAgainAction = signInAgainAction,
            )
        }
    val list = rememberTranscriptListState()
    val failedSendId = state.status?.takeIf { it.kind == ChatStatusKind.SendFailed }?.localId
    val failedBubbleInView by
        remember(list, failedSendId) {
            derivedStateOf {
                failedSendId != null &&
                    !io.github.eslamasabry.oc.feature.chat.presentation.SendFailureRules.bannerNeeded(
                        ListKeys.pending(failedSendId),
                        list.lazy.layoutInfo.visibleItemsInfo.map { it.key },
                    )
            }
        }
    var dockRoomPx by remember { mutableIntStateOf(0) }
    val reveal = rememberRevealRegistry()
    val motion = kitChatMotion()
    val latest by rememberUpdatedState(state)
    // The background line counts the stream's background work and, once the reply has ended, this conversation's
    // commands still running on the server (owner 2026-11-14: "where is the background activity?").
    val backgroundTasks =
        remember(facts.background, work.shells, state.live != null) {
            io.github.eslamasabry.oc.feature.chat.work.RunningWork.lineTasks(
                facts.background,
                work.shells,
                replyBusy = state.live != null,
            )
        }
    val transcriptFacts =
        remember(facts, backgroundTasks) {
            if (backgroundTasks == facts.background) facts else facts.copy(background = backgroundTasks)
        }
    fun openWork() {
        val m = workModel ?: return
        m.refresh()
        workOpen = true
    }
    // Every item the transcript draws below the engine's rows (ChatTranscriptList): a jump to a row (a timeline pick, a
    // Find hit, an arrival) counts them, or it lands one row off while background work shows.
    val signalStyle = LocalKitAppearance.current.style == KitSurfaceStyle.Signal
    val liveIsRow = state.live != null && LiveSlot.host(state.live, state.pending, signalStyle) == null
    val itemsBelowRows =
        (if (liveIsRow) 1 else 0) + state.pending.size + (if (dockRoomPx > 0) 1 else 0) +
            (if (transcriptFacts.background.isNotEmpty()) 1 else 0)

    // Read acknowledgement follows the visible list (plan 09 §6.7).
    LaunchedEffect(list) {
        snapshotFlow { list.atLiveEdge }.collect { actions.liveEdgeChanged(it) }
    }
    // Find brings its current hit's row into view (plan 09 §6.6); a jump never animates across
    // thousands of rows (§7.5).
    FindJump(state.find, state.phase == ChatPhase.Live, itemsBelowRows, list)
    // Arrivals (07 §2.5): land once on the failure; "ready to type" is the composer's to
    // acknowledge.
    LaunchedEffect(state.arrival?.id) {
        val arrival = latest.arrival ?: return@LaunchedEffect
        arrival.landOnIndex?.let {
            list.following = false
            list.lazy.scrollToItem(list.lazyIndex(itemsBelowRows + it))
        }
        if (!arrival.focusComposer) actions.arrivalShown(arrival.id)
    }

    // After an edge-light switch the keyboard stays down, the box says "Reply in ‹title›" until the person types,
    // and (the first three times) a note under the top bar says where Back goes.
    val arrivalFocus = androidx.compose.ui.platform.LocalFocusManager.current
    LaunchedEffect(switchArrival?.seq) { if (switchArrival != null) arrivalFocus.clearFocus() }
    var backNoteTitle by remember(switchArrival?.seq) { mutableStateOf(switchArrival?.takeIf { it.backNote }?.leftTitle) }
    LaunchedEffect(switchArrival?.seq) {
        if (switchArrival != null && backNoteTitle != null) onBackNoteShown(switchArrival.seq)
    }
    // The title is worded by the one rule every list uses (blank is "Untitled conversation"), never empty.
    val untitledWords = stringResource(Res.string.commandUntitledChat)
    val newWords = stringResource(Res.string.workspaceNewSession)
    val replyInWords =
        state.title?.let { stringResource(Res.string.chatReplyInHint, ConversationTitle.display(it, untitledWords, newWords)) }
    val arrivalHint =
        remember(switchArrival?.seq, switchArrival?.hint, replyInWords) {
            if (switchArrival?.hint == true && replyInWords != null)
                ReplyHint(replyInWords) { onHintTyped(switchArrival.seq) }
            else null
        }
    // D1 "steps only" (owner 2026-11-14): a permission ask for a step of the turn in view is answered in place under that
    // step; the slot above the message box skips it, and the box says the answer is above while it waits.
    val requestDetails = remember { mutableStateOf<RequestId?>(null) }
    val placedRequests = remember(requests.cards, facts.work) { InPlaceRules.placed(requests.cards, facts.work.values.lastOrNull()) }
    val inPlaceRequests =
        remember(placedRequests, requestActions) {
            val answer = requestActions
            if (answer == null || placedRequests.isEmpty()) null
            else InPlaceRequests(placedRequests) { card -> ChatRequestInPlace(card, answer, onDetails = { requestDetails.value = card.id }) }
        }
    // F-023: the turn's row that holds a decision still waiting, so the list can bring it back into view.
    val waitingKey =
        remember(placedRequests, facts.work) {
            val waiting = placedRequests.filterValues { it.state == io.github.eslamasabry.oc.feature.chat.requests.presentation.CardState.Waiting }.keys
            if (waiting.isEmpty()) null
            else facts.work.entries.firstOrNull { (_, w) -> w.steps.any { it.callId in waiting } }?.key?.let { "work:$it" }
        }
    var waitingJump by remember { mutableStateOf(0) }
    val waitingWords = stringResource(Res.string.chatWaitingForAnswerAbove)
    val waitingHint = remember(waitingWords) { ReplyHint(waitingWords) {} }
    val replyHint = if (inPlaceRequests != null && InPlaceRules.waiting(placedRequests)) waitingHint else arrivalHint
    val frost = rememberKitFrostState()
    // The conversation keeps the frame's overlaid layout in every state: loading, live, could not load, with or without a
    // status line, with Find open (its one row stands in the bottom block over the message box). Switching layouts moved
    // the message box, with its own subcomposition, to another parent inside the navigation's lookahead pass, which
    // crashed the app right after a conversation went from loading to live (2026-11-14).
    val headerUnder by remember(list) { derivedStateOf { list.lazy.canScrollForward } }
    val scheduledId by actions.laterScheduled.collectAsState()
    val chrome =
        remember(actions, prompts) {
            ComposerChrome(
                busy = { latest.live != null },
                // The box stands in its place while the conversation loads; Send waits for the load.
                loading = { latest.phase == ChatPhase.Loading },
                // Review #12: the box agrees with the run's state; a decision on its way waits on no one.
                requestWaiting = { RunTailRules.waitsForYou(requestState.cards.map { it.state }) || formPending },
                // F-023: a tap in the box while a decision waits takes the page to it.
                jumpToWaiting = { waitingJump += 1 },
                requestAsking = { RunTailRules.asksYou(requestState.cards.map { it.state }) || formPending },
                // A send brings the newest message into view at once (audit 1, k6: "Jump to latest" showed after a send
                // made while reading back, until the next row arrived).
                onSent = {
                    list.following = true
                    list.keepAtBottom()
                },
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
                // S28 story 14: the live line's stuck-command notice also turns Send into "Stop and send".
                stuck = {
                    factsState.live is io.github.eslamasabry.oc.feature.chat.presentation.LiveNoticeUi.StuckCommand ||
                        factsState.live is io.github.eslamasabry.oc.feature.chat.presentation.LiveNoticeUi.ToolWait
                },
                stopAfterStep = {
                    if (latest.live?.canStopAfterStep == true) { { actions.stopAfterStep(stopNote) } }
                    else null
                },
                rowChips = { headerState?.approval?.let { ApprovalChip(it) } },
                // Read when the sheet draws, like rowChips: the approval state may change after the chrome is made.
                promptSettings = { close ->
                    headerState?.approval?.let { ApprovalSettingsRows(it, close) }
                    // S30 story 3 (Flutter `_composerSettings` 'background'): while a reply runs work the server can
                    // background, Background moves it there and the conversation stays usable.
                    if (latest.canBackground)
                        io.github.eslamasabry.oc.ui.kit.row.KitRow(
                            title = stringResource(Res.string.chatStripBackground),
                            supporting = stringResource(Res.string.backgroundWorkShortcut),
                            supportingMaxLines = 2,
                            leading = io.github.eslamasabry.oc.ui.kit.row.KitRowLeading.Glyph(KitIcons.lowPriority),
                            onClick = {
                                close()
                                actions.backgroundWork()
                            },
                            modifier = Modifier.testTag("composer-setting-background"),
                        )
                },
                freeModel = { headerState?.state?.freeModel == true },
                // Open-transitions: an agent not read yet is named by no one, never "OpenCode" for another agent's box.
                agentName = { headerState?.state?.let { if (it.agentKnown) it.agentName else "" } ?: "OpenCode" },
                openCommands =
                    if (demoMode) null
                    else {
                        {
                            onLoadServerCommands()
                            commandsOpen = true
                        }
                    },
                // S30 story 2: read when `/word` is typed; filled below from the commands launcher's own entries.
                slashCommands = { slashCommands.value },
                openSavedPrompts = if (demoMode) null else prompts?.let { door -> door::open },
                sendLater = if (demoMode) null else actions::sendLater,
                laterScheduled = { scheduledId },
                laterShown = actions::laterShown,
                modelPickerRequest = { modelPicker },
                editorRequest = { editorRequest },
                pendingPicture = { sentPicture },
                pictureDone = { id -> if (sentPicture?.id == id) sentPicture = null },
                editorDone = { id -> if (editorRequest == id) editorRequest = null },
                modelPickerDone = { id -> if (modelPicker == id) modelPicker = null },
                modelCycleRequest = { modelCycle },
                modelCycleDone = { id -> if (modelCycle?.id == id) modelCycle = null },
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
            header = header,
            onContext = facts.context?.let { { contextOpen = true } },
            timestamps =
                onToggleTimestamps?.let { toggle -> transcriptDisplay.timestampsVisible to toggle },
            onEditor =
                if (composerHost.hasPromptEditor) {
                    { editorRequest = (editorRequest ?: 0L) + 1L }
                } else null,
        )
    // S30 story 2: the "/" menu offers what the Commands sheet offers (Flutter's one `_chatCommands` list).
    val slashRows =
        if (demoMode) emptyList()
        else
            commandEntries.map { entry ->
                io.github.eslamasabry.oc.feature.chat.composer.ComposerSlashCommand(
                    key = entry.key,
                    slash = entry.slash,
                    title = entry.title,
                    description = entry.description,
                    aliases = entry.aliases,
                    inserts = entry.key.startsWith(SERVER_COMMAND_KEY),
                    onRun = entry.onRun,
                )
            }
    androidx.compose.runtime.SideEffect { slashCommands.value = slashRows }

    // The frame, its sibling menu and its confirmation share one modal layer. KitScreen
    // reuses this ancestor rather than creating a host scoped only to its own content.
    KitPromptLiftHost {
        KitSheetHost(modifier.fillMaxSize()) {
            // w4-focus (sweep P79/P80): a sheet or dialog over the conversation owns typing; the kit's sheet host takes
            // the page's focus and hands it back (KitSheetHost); when that finds nothing (the box moved while covered),
            // the box takes it back only if nothing on the page has it. Find's field takes focus itself; closing Find
            // gives the message box its focus back if it had it.
            val sheets = LocalKitSheetHost.current
            val covered = sheets != null && (sheets.isShowing || sheets.isDialogShowing)
            val coveredNow by rememberUpdatedState(covered)
            ComposerYields(state.find != null && state.phase == ChatPhase.Live, chrome.typing) { !coveredNow }
            val pageFocus = remember { ChatPageFocus() }
            ComposerAfterCover(covered, chrome.typing, pageFocus) { latest.find == null }
            // N-55: the shell steers a stray hardware key into the page (FocusDirection.Enter); it lands on the
            // conversation itself, which hands it to the message field, never to the first button
            // ([chatTypingLanding]: a request for a field inside, as Find's, is never redirected).
            Box(
                Modifier.fillMaxSize()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        val key =
                            ChatKeys.of(event.key, event.isCtrlPressed, event.isMetaPressed, event.isAltPressed, event.isShiftPressed)
                                ?: return@onPreviewKeyEvent false
                        val scene =
                            ChatKeyScene(
                                demo = demoMode,
                                overlayOpen =
                                    commandsOpen || workOpen || contextOpen || tasksOpen || connectionDetails || barMenuOpen,
                                findOpen = latest.find != null,
                                terminal = latest.capabilities.terminal,
                                canBackground = latest.canBackground,
                            )
                        if (!ChatKeys.available(key, scene)) return@onPreviewKeyEvent false
                        when (key) {
                            ChatKey.Launcher -> {
                                onLoadServerCommands()
                                commandsOpen = true
                            }
                            ChatKey.Find -> actions.find(latest.find?.query ?: "")
                            ChatKey.FindNext -> actions.findNext()
                            ChatKey.FindPrevious -> actions.findPrevious()
                            ChatKey.Terminal -> actions.openPlace(ChatPlace.Terminal)
                            ChatKey.Background -> actions.backgroundWork()
                            ChatKey.ModelNext ->
                                modelCycle = io.github.eslamasabry.oc.feature.chat.composer.ModelCycleRequest((modelCycle?.id ?: 0L) + 1L, false)
                            ChatKey.ModelPrevious ->
                                modelCycle = io.github.eslamasabry.oc.feature.chat.composer.ModelCycleRequest((modelCycle?.id ?: 0L) + 1L, true)
                        }
                        true
                    }
                    .chatTypingLanding(chrome.typing, pageFocus) { coveredNow }
            ) {
                val screenStatus =
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
                                    // The failed bubble under the message says it with its own action: this line is
                                    // only for a failed message that has scrolled out of sight.
                                    ?.takeUnless { it.kind == ChatStatusKind.SendFailed && failedBubbleInView }
                                    ?.let {
                                        statusLine(
                                            it,
                                            actions,
                                            header?.state?.serverName,
                                            ::openConnectionDetails,
                                            agentName = header?.state?.takeIf { h -> h.agentKnown }?.agentName,
                                        )
                                    },
                                appStatus,
                            )
                        )
                KitScreen(
                    modifier = Modifier.fillMaxSize(),
                    page = true,
                    overlayChrome = true,
                    hideStatusWhenTyping = true,
                    // B-438: the header is one floating capsule, the message box floats at the bottom, the transcript runs
                    // behind both with no band, mask or fade, and the jump control is a round arrow at the box's corner.
                    floating = true,
                    // N-52/N-57: the header and the composer lie on the page's ground (frosted under Glass), so the
                    // transcript never draws through them.
                    frost = frost,
                    topBar = {
                        Box(Modifier.fillMaxWidth()) {
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
                                        // Always here (owner 2026-11-14): with a count while work runs, and the sheet
                                        // says when nothing runs or when this server does not report it.
                                        runningWorkAction(
                                            offered = workModel != null,
                                            running =
                                                io.github.eslamasabry.oc.feature.chat.work.RunningWork.count(
                                                    facts.background,
                                                    work.shells,
                                                ),
                                            onOpen = ::openWork,
                                        ),
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
                                            stringResource(Res.string.nlibTitle),
                                            actions::openTools,
                                            testTag = "chat-tools-open",
                                            icon = KitIcons.tools,
                                        ),
                                    )) + extraActions
                            // P-018: acts on the conversation's view, not places: they sit under
                            // More in the header's menu.
                            val unitActs =
                                if (demoMode) emptyList()
                                else
                                    listOfNotNull(
                                        if (folds.anyOpen)
                                            KitLineAction(
                                                stringResource(Res.string.chatCollapseAllSteps),
                                                folds::collapseAll,
                                                testTag = "chat-collapse-all",
                                                icon = KitIcons.unfoldLess,
                                            )
                                        else null,
                                        // S30 story 3: with the header, Compact context is a ⋯ menu entry of its own (Flutter's
                                        // place); the plain bar keeps it here.
                                        if (state.capabilities.compact && header == null)
                                            KitLineAction(
                                                stringResource(Res.string.chatUiCompactSession),
                                                actions::compactRequested,
                                                enabled = state.live == null,
                                                testTag = "chat-compact-open",
                                                icon = KitIcons.layers,
                                            )
                                        else null,
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
                                    )
                            BackNoteTip(backNoteTitle, { backNoteTitle = null }) {
                            if (header != null) {
                                // CHAT-HEADER replaces the plain bar; Find lives in its ⋯ menu, the
                                // other
                                // units' actions stay.
                                CompositionLocalProvider(LocalChatHeaderActs provides unitActs) {
                                    ChatHeaderTopBar(
                                        header,
                                        actions::leaveRequested,
                                        { actions.find(state.find?.query ?: "") },
                                        extraActions = unitActions + listOfNotNull(context),
                                        onProject = actions::openFiles,
                                        onBranch = actions::openGit,
                                        canGoBack = canGoBack,
                                        onCompact = actions::compactRequested,
                                    )
                                }
                            } else
                                KitTopBar(
                                    title =
                                        state.title
                                            ?: stringResource(Res.string.commandDestination),
                                    exit = if (canGoBack) KitTopBarExit.Back else KitTopBarExit.None,
                                    exitLabel = stringResource(Res.string.kitTopBarBack),
                                    onExit = actions::leaveRequested,
                                    backBesideTitle = true, // E20-S13: the chat bar is one compact block
                                    actions =
                                        listOfNotNull(
                                            context,
                                            KitLineAction(
                                                stringResource(Res.string.kitTopBarSearch),
                                                { actions.find(state.find?.query ?: "") },
                                                testTag = "chat-find-open",
                                                icon = KitIcons.search,
                                            ),
                                        ) + (if (demoMode) emptyList() else unitActions) + unitActs,
                                    more = { hidden ->
                                        KitChatMoreMenu(
                                            label = stringResource(Res.string.kitTopBarMore),
                                            expanded = barMenuOpen,
                                            items =
                                                // The demo's own acts (Reset demo, Leave) sit in the menu: "Reset demo"
                                                // in words beside Search crowded the bar (emulator walk 2026-11-10).
                                                ((if (demoMode) unitActions else emptyList()) + hidden).map { extra ->
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
                        }
                    },
                    status = screenStatus,
                    loading = state.phase == ChatPhase.Loading,
                    loadingLabel = stringResource(Res.string.chatLoadingConversation),
                    bottom = {
                        Column(Modifier.fillMaxWidth()) {
                            // Find sits by the keyboard it is typed with, over what needs the person and the
                            // composer (Flutter chat_composer_region.dart:279-301); the composer stays, as in Flutter.
                            state.find?.let { find ->
                                if (state.phase == ChatPhase.Live)
                                    TranscriptFindBar(find, actions, Modifier.fillMaxWidth().chatPane())
                            }
                            // E15-S06: a copy the redactor changed says so in the screen's status
                            // line. It must be composed inside KitScreen: the screen owns the one
                            // status slot, and a contribution from outside it has nowhere to go.
                            CopyKeyHiddenNotes(state.copied, header?.state?.clip)
                            // Notification opt-in is a review sheet; pending-send recovery uses the
                            // status slot.
                            replyOffer?.invoke()
                            // Optional permission asks never compete with the conversation.
                            // P12-VOICE: the reading strip (Stop, notes) and the once-per-phone
                            // consent.
                            replyReader?.Reader(
                                state.readAloud?.let { KitReadRequest(it.id, it.rowKey, it.text, chooseVoice = it.chooseVoice) },
                                actions::readAloudDone,
                                Modifier,
                            )
                            monitoredForm?.invoke()
                            // The card above the box takes at most the room MEASURED between the top block's foot and
                            // the box (not a share of the window); in less than the title, two body lines and the
                            // answers need, its body shows one line and "Details" has the rest, before anything could
                            // draw over the pinned notice (look 10098).
                            // The room moves with the keyboard, frame by frame: the card reads it where it is
                            // measured, and this page is composed again only when the card crosses the compact line.
                            val bottomRoom = io.github.eslamasabry.oc.ui.kit.frame.LocalKitBottomRoom.current
                            val composerPx = remember { androidx.compose.runtime.mutableIntStateOf(0) }
                            val roomDensity = androidx.compose.ui.platform.LocalDensity.current
                            val cardRoom: (() -> androidx.compose.ui.unit.Dp)? =
                                remember(bottomRoom, roomDensity) {
                                    bottomRoom?.let { room ->
                                        { room() - with(roomDensity) { composerPx.intValue.toDp() } }
                                    }
                                }
                            val fontScale = roomDensity.fontScale
                            val compact by remember(cardRoom, fontScale) {
                                androidx.compose.runtime.derivedStateOf {
                                    cardRoom != null && cardRoom() < REQUEST_FULL_ROOM_DP.dp * fontScale
                                }
                            }
                            androidx.compose.runtime.CompositionLocalProvider(
                                io.github.eslamasabry.oc.ui.kitchat.request.LocalKitRequestRoom provides cardRoom,
                                io.github.eslamasabry.oc.ui.kitchat.request.LocalKitRequestCompact provides compact,
                            ) {
                            if (requestActions != null)
                                ChatRequestSlot(
                                    requests,
                                    requestActions,
                                    nowMs = null,
                                    modifier = Modifier,
                                    show = requestFocus?.let { RequestShow(RequestId(it.requestId), it.seq) },
                                    onShown = onRequestShown,
                                    placed = if (inPlaceRequests == null) emptySet() else placedRequests.values.mapTo(HashSet()) { it.id },
                                    details = requestDetails,
                                )
                            }
                            if (requests.card == null) formSlot?.invoke(Modifier)
                            // The composer is the conversation's, as wide as the conversation's pane (E20-S27). It
                            // stands in its place while the conversation loads (owner 2026-11-14: the page lost its
                            // shape and showed nothing), so the person sees where they will type; Send waits for the
                            // load (ComposerChrome.loading). The could-not-load page has none.
                            if (state.phase == ChatPhase.Live || state.phase == ChatPhase.Loading)
                              CompositionLocalProvider(LocalReplyHint provides replyHint) {
                                composerHost.Composer(
                                    ComposerTarget(
                                        state.ref.profileId.raw,
                                        state.ref.sessionId.raw,
                                    ),
                                    chrome,
                                    // F1: the message box sits 8 dp above the screen's bottom edge, as Flutter's
                                    // (golden chat_approval_ask: box bottom at 907 of 915); the screen's bottom slot
                                    // already keeps that room. E: on wide windows it shares the 700 dp pane.
                                    Modifier.fillMaxWidth().chatPane().testTag("chat-composer-pane").padding(top = Space.S2.dp)
                                        .kitOnHeightChanged { composerPx.intValue = it },
                                )
                              }
                        }
                    },
                    edges = KitScreenEdges.Bleed,
                ) {
                    // Before and after: the placeholder crosses over to the page with the kit's short swap, never a pop; the
                    // header, the box and the scroll place are outside it and stay put. Motion off: instant.
                    KitSwap(state.phase, Modifier.fillMaxSize()) { phase ->
                    when (phase) {
                        // First load: the skeleton under the loading line, never an empty-state
                        // text
                        // (plan 09 §6.5).
                        // A slow load says what it does in words, unless the screen's line already says something.
                        // Only once the page has arrived: while the page transition runs the body is empty, so no
                        // skeleton is ever drawn over the page it leaves (device report, build 10054).
                        ChatPhase.Loading ->
                            // Motion off: no page arrives, so frame one already shows the skeleton (NEW-f).
                            if (pageSettled() || KitMotion.state.reduced) {
                                ChatLoadingBody(stringResource(Res.string.chatLoadingConversation).takeIf { screenStatus == null })
                            }
                        is ChatPhase.Failed ->
                            ChatLoadError(
                                title = stringResource(Res.string.chatLoadFailedTitle),
                                body = chatLoadBody(phase),
                                failure = phase.failure,
                                retry = KitAction(stringResource(Res.string.commonRetry), actions::retryLoad),
                                onReport = actions::reportProblem,
                                setUp =
                                    phase.agent?.let { agent ->
                                        val id = agent.agentId ?: return@let null
                                        val name = agent.name ?: return@let null
                                        KitAction(
                                            stringResource(Res.string.agentsSetUpAction, name),
                                            onClick = { actions.setUpAgent(id) },
                                        )
                                    },
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
                                    androidx.compose.runtime.CompositionLocalProvider(
                                        LocalLimitSwitchReply provides rememberLimitSwitchReply(state, actions),
                                        LocalAgentSetUpReply provides
                                            remember(state.setUpReplies, actions) {
                                                AgentSetUpReply(state.setUpReplies, actions::setUpAgent)
                                            },
                                        LocalSignInTurnReply provides
                                            run {
                                                val sendAgain = stringResource(Res.string.messageViewSendAgain)
                                                remember(state.signInTurns, sendAgain, actions) {
                                                    SignInTurnReply(
                                                        state.signInTurns,
                                                        sendAgain,
                                                        actions::retryLastPrompt,
                                                    )
                                                }
                                            },
                                        LocalInPlaceRequests provides inPlaceRequests,
                                        // A send refused while an undo waits: its row opens the undo's review, as
                                        // the staged line does (that line hides while the keyboard is up).
                                        LocalUndoReview provides
                                            remember(state.canPutBack, actions) {
                                                if (state.canPutBack) actions::putBack else null
                                            },
                                    ) {
                                        ChatTranscriptList(
                                            waitingKey = waitingKey,
                                            waitingJump = waitingJump,
                                            placeKey = "${state.ref.profileId.raw}/${state.ref.sessionId.raw}",
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
                                            onSetUpAgent = actions::setUpAgent,
                                            onSignInAgent = if (onThisPhone) actions::openAgentSignIn else null,
                                            onReviewPending = onReviewPending,
                                            modifier = Modifier.fillMaxSize().chatPane(),
                                            extraBottomPx = dockRoomPx,
                                            facts = transcriptFacts,
                                            onOpenBackground = if (workModel != null) ::openWork else null,
                                            display = transcriptDisplay,
                                            onOpenSession = onOpenSession,
                                            onStopLive = actions::stop,
                                            onUndoStopAfterStep = actions::undoStopAfterStep,
                                            onStopForSteer = actions::stopForSteer,
                                            replyAsk = state.replyAsk?.let { ReplyAskHost(it, actions) },
                                            reply =
                                                rememberReplyActions(
                                                    actions,
                                                    state,
                                                    modelChoices,
                                                    ReplyCallbacks(
                                                        onChooseModel = {
                                                            modelPicker = (modelPicker ?: 0L) + 1L
                                                        },
                                                        onOpenCode = { codeReader = it },
                                                        onChoice = { text ->
                                                            // An agent choice goes after the person's words
                                                            // (negative ids, never equal to the chat's own).
                                                            commandInsertId -= 1
                                                            commandInsert =
                                                                io.github.eslamasabry.oc.feature.chat.composer
                                                                    .InsertRequest(
                                                                        commandInsertId,
                                                                        text,
                                                                    )
                                                        },
                                                        onRerunCommand =
                                                            if (
                                                                workModel != null &&
                                                                    state.capabilities.shell &&
                                                                    !demoMode
                                                            )
                                                                workModel::openShellDialog
                                                            else null,
                                                    ),
                                                ),
                                            onOpenFile = { file ->
                                                if (io.github.eslamasabry.oc.feature.chat.world.isInlineFile(file.url) && file.path == null)
                                                    toolFile = file
                                                else openArtifact(actions, file)
                                            },
                                            onAllFiles = actions::openFiles,
                                            onSendAgain = actions::retryLastPrompt,
                                            reasoningExpanded = state.reasoningExpanded,
                                            onCompactAgain =
                                                if (state.capabilities.compact) actions::compactAgain
                                                else null,
                                            topLine = handoffLine,
                                            older = state.older,
                                            onLoadOlder = actions::loadOlder,
                                            find = state.find,
                                        )
                                    }
                            }
                    }
                    }
                    // The edge dock: over the transcript, clear of the header and the composer. Beside the
                    // transcript's recording, not in it: under Glass it bends that recording (never itself).
                    // Open-transitions (S22 frame item2): the light was mounted while the page still grew out of its
                    // row and before the page's chrome was measured, so it stood at a place computed from no chrome,
                    // clipped by the growing page, then jumped to its place. It comes once the page is settled and its
                    // chrome known, at its own place from its first frame.
                    val chrome = kitScreenChromePadding()
                    // (A frame that lays its chrome above the body, Find open, reports no chrome room: nothing to wait for.)
                    val chromeKnown by remember(chrome) {
                        androidx.compose.runtime.derivedStateOf {
                            chrome == androidx.compose.foundation.layout.PaddingValues() ||
                                (chrome.calculateTopPadding() > 0.dp && chrome.calculateBottomPadding() > 0.dp)
                        }
                    }
                    if (state.phase == ChatPhase.Live && chromeKnown && pageSettled())
                        androidx.compose.runtime.CompositionLocalProvider(
                            io.github.eslamasabry.oc.ui.kit.dock.LocalKitDockGlass provides frost
                        ) {
                            edgeDock?.Content(chrome, onBottomRoom = { dockRoomPx = it })
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
                ToolFileSheet(toolFile, toolFileSaver, onAttachToolFile) { toolFile = null }
                ChatContextSheet(
                    facts.context,
                    contextOpen,
                    facts.context?.let {
                        ContextTotals(header?.state?.serverCost, header?.state?.serverTokens, it.sheet.sessionCost)
                    },
                ) {
                    contextOpen = false
                }
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
                    val closeWork = {
                        workOpen = false
                        workModel.closeOutput()
                    }
                    ChatWorkSheet(
                        work,
                        workOpen,
                        workModel,
                        onDismiss = closeWork,
                        tasks = facts.background,
                        clock = facts.clock,
                        subagents = state.capabilities.subagents && !demoMode,
                        onShowSubagents = {
                            closeWork()
                            actions.showSubagents()
                        },
                        onShowTask = { task ->
                            closeWork()
                            if (task.messageId.isNotEmpty()) actions.showWorkStart(task.messageId)
                        },
                    )
                    ChatShellDialog(work, workModel)
                }
                if (activeContext != null) ActiveContextSheet(activeContext)
                SelectTextSheet(state.select, actions)
                val shown = state.picture
                if (shown != null && encodePng != null && shown.gallery.isNotEmpty()) {
                    // S24-CHAT story 3 (Flutter `chat_gallery.dart`): every server picture of the
                    // conversation, a swipe apart; Edit marks one up, Send puts it in the draft.
                    val pages =
                        remember(shown.id) {
                            shown.gallery.map { g ->
                                KitGalleryPicture(g.path, g.name) { actions.readPicture(g.path) }
                            }
                        }
                    KitImageGallery(
                        pictures = pages,
                        onClose = actions::closePicture,
                        encodePng = encodePng,
                        modifier = Modifier.fillMaxSize().testTag("chat-picture-gallery"),
                        initialIndex = shown.index,
                        onSend = { bytes, name, _ ->
                            pictureSeq += 1
                            sentPicture = PictureRequest(pictureSeq, name, bytes)
                            true
                        },
                    )
                } else PictureSheet(shown, actions)
                CodeReaderSheet(codeReader, actions) { codeReader = null }
                ConnectionDetailsSheet(
                    visible = connectionDetails && state.status?.kind.let { it == ChatStatusKind.Offline || it == ChatStatusKind.Connecting },
                    line = state.status,
                    actions = actions,
                    onDismiss = { connectionDetails = false },
                )
                ChatTimelineSheet(state.timeline, actions, facts.clock)
                // B-169 §3: Switch model on a failed reply.
                io.github.eslamasabry.oc.feature.chat.composer.SwitchModelSheet(
                    state.switching,
                    modelChoices,
                    actions,
                    loading = modelChoicesLoading,
                )
                KitConfirm(
                    spec = confirmSpec(state.confirm),
                    onConfirm = {
                        when (latest.confirm) {
                            is ChatConfirm.DeleteMessage -> actions.confirmDelete()
                            ChatConfirm.LeaveUnsaved -> actions.confirmLeave()
                            is ChatConfirm.UndoFrom -> actions.confirmUndo()
                            ChatConfirm.Compact -> actions.confirmCompact()
                            ChatConfirm.RestartPhoneServer -> actions.confirmRestartPhoneServer()
                            is ChatConfirm.OpenLink -> actions.confirmOpenLink()
                            is ChatConfirm.PhoneFile,
                            is ChatConfirm.PhoneFileAccess -> actions.confirmPhoneFile()
                            is ChatConfirm.SendAgainAfterSteps -> actions.confirmSwitch()
                            null -> Unit
                        }
                    },
                    onCancel = actions::dismissConfirm,
                )
                header?.let { ChatHeaderOverlays(it) }
                // E15-S05: one announcement per copy (Flutter KitCopy.copy), saying when a key was hidden.
                KitCopyAnnouncement(
                    state.copied?.id,
                    stringResource(
                        if (state.copied?.masked == true) Res.string.kitCopiedMasked else Res.string.kitCopied
                    ),
                )
            }
        }
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
        closeLabel = stringResource(Res.string.kitSheetClose),
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
        closeLabel = stringResource(Res.string.kitSheetClose),
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
        closeLabel = stringResource(Res.string.kitSheetClose),
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
        ChatConfirm.RestartPhoneServer ->
            KitConfirmSpec(
                title = stringResource(Res.string.workServerRestartTitle),
                confirmLabel = stringResource(Res.string.workServerRestart),
                kind = KitConfirmKind.Stop,
                icon = KitIcons.restart,
                body = stringResource(Res.string.workServerRestartBody),
            )
        ChatConfirm.Compact ->
            KitConfirmSpec(
                title = stringResource(Res.string.chatUiCompactConfirmTitle),
                confirmLabel = stringResource(Res.string.chatUiCompactConfirmAction),
                kind = KitConfirmKind.Neutral,
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
                    kind = KitConfirmKind.Neutral,
                    body = stringResource(Res.string.phoneFileInstallBody),
                )
            else
                KitConfirmSpec(
                    title = stringResource(Res.string.phoneFileOpenTitle, name),
                    confirmLabel = stringResource(Res.string.phoneFileOpenAction),
                    kind = KitConfirmKind.Neutral,
                    body = stringResource(Res.string.phoneFileOpenBody),
                )
        }
        is ChatConfirm.SendAgainAfterSteps ->
            KitConfirmSpec(
                title =
                    pluralStringResource(Res.plurals.b169AskTitle, confirm.steps, confirm.steps, confirm.modelLabel),
                confirmLabel = stringResource(Res.string.b169AskSend),
                kind = KitConfirmKind.Neutral,
            )
        is ChatConfirm.PhoneFileAccess ->
            KitConfirmSpec(
                title = stringResource(Res.string.phoneSharedAccessTitle),
                body = stringResource(Res.string.phoneSharedAccessBody),
                confirmLabel = stringResource(Res.string.commonRetry),
                kind = KitConfirmKind.Neutral,
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
 * S24-CHAT story 1 (Flutter `connectionKitStatus`): while the link tries again by itself the line
 * says when ("trying again in 12 s") and offers Try now; this phone's server can be restarted from
 * More. With no try scheduled, Restart (this phone) or Retry is the action.
 */
@Composable
private fun connectionLine(
    line: ChatStatusUi,
    actions: ChatActions,
    serverName: String?,
    onDetails: () -> Unit,
    agentName: String? = null,
): KitStatus {
    val offline = line.kind == ChatStatusKind.Offline
    val seconds = line.retryInSeconds
    val details =
        KitLineAction(
            stringResource(Res.string.e7BannerDetails),
            onDetails,
            testTag = "chat-connection-details",
        )
    val changeServer =
        KitLineAction(
            stringResource(Res.string.productStatesSwitchServer),
            actions::openServers,
            testTag = "chat-connection-change-server",
        )
    // A stopped link that retrying cannot fix: the saved password or token is wrong or missing, so the way
    // forward is to type it again where it is kept (Flutter `credentialsRequired`).
    val signIn = (line.failure as? io.github.eslamasabry.oc.core.common.Failure.SignIn)?.why
    if (signIn != null) {
        val token =
            signIn == io.github.eslamasabry.oc.core.common.SignInWhy.TokenMissing ||
                signIn == io.github.eslamasabry.oc.core.common.SignInWhy.TokenRejected
        val name = serverName ?: "OpenCode"
        return KitStatus(
            KitStatusKind.Connection,
            stringResource(
                if (token) Res.string.connectionTokenRejectedFor else Res.string.connectionPasswordRejectedFor,
                name,
            ),
            id = "chat-status-${line.id}",
            tone = StatusTone.Failure,
            action =
                KitLineAction(
                    stringResource(if (token) Res.string.updateConnectionToken else Res.string.e7BannerUpdatePassword),
                    actions::editServer,
                    testTag = if (token) "chat-banner-update-token" else "chat-banner-update-password",
                ),
            more = listOf(details, changeServer),
        )
    }
    // Open while starting: the page waits for its server and opens by itself, so the line says what the server is doing
    // and offers no Try again (Details and Change server stay one tap away).
    val wait = line.serverWait
    // The person's own stop is a state of its own: one sentence and Start OpenCode, no countdown, no Try now.
    if (wait == ChatServerWait.StoppedByPerson)
        return KitStatus(
            KitStatusKind.Connection,
            stringResource(Res.string.phoneStoppedByYou),
            id = "chat-status-${line.id}",
            action =
                KitLineAction(
                    stringResource(Res.string.phoneServerCardStartOpenCode),
                    actions::startPhoneServer,
                    testTag = "chat-connection-start-phone",
                ),
            more = listOf(details, changeServer),
        )
    if (wait != null) {
        // A wait that ends by itself looks alive: the kit's working mark, and the agent's name when it is known.
        val signIn = wait == io.github.eslamasabry.oc.feature.chat.world.ChatServerWait.AgentNeedsSignIn
        return KitStatus(
            KitStatusKind.Connection,
            when (wait) {
                ChatServerWait.StoppedByPerson -> stringResource(Res.string.phoneStoppedByYou)
                io.github.eslamasabry.oc.feature.chat.world.ChatServerWait.Updating -> stringResource(Res.string.connectUpdatingPhone)
                io.github.eslamasabry.oc.feature.chat.world.ChatServerWait.UpdatingAgents -> stringResource(Res.string.connectUpdatingAgents)
                io.github.eslamasabry.oc.feature.chat.world.ChatServerWait.AgentNeedsSignIn -> stringResource(Res.string.connectAgentNeedsSignIn)
                io.github.eslamasabry.oc.feature.chat.world.ChatServerWait.Starting -> stringResource(Res.string.connectStartingPhone)
                io.github.eslamasabry.oc.feature.chat.world.ChatServerWait.StartingLinux -> stringResource(Res.string.stageStartingLinux)
                io.github.eslamasabry.oc.feature.chat.world.ChatServerWait.StartingOpenCode -> stringResource(Res.string.stageStartingOpenCode)
                io.github.eslamasabry.oc.feature.chat.world.ChatServerWait.StartingHelper -> stringResource(Res.string.stageStartingHelper)
                io.github.eslamasabry.oc.feature.chat.world.ChatServerWait.OpeningConversation -> stringResource(Res.string.stageOpeningConversation)
                io.github.eslamasabry.oc.feature.chat.world.ChatServerWait.StartingAgent ->
                    if (agentName != null) stringResource(Res.string.stageStartingAgent, agentName)
                    else stringResource(Res.string.launchUiSessionWaiting)
                io.github.eslamasabry.oc.feature.chat.world.ChatServerWait.Connecting ->
                    if (agentName != null) stringResource(Res.string.chatConnectingToAgent, agentName)
                    else stringResource(Res.string.launchUiSessionWaiting)
            },
            id = "chat-status-${line.id}",
            more = listOf(details, changeServer),
            working = !signIn,
            // The waits the app names by their kind: the phone's own server coming up, and an agent or its helper
            // getting ready. The kit draws their marks in the working mark's place; the other waits keep it.
            wait = waitOf(wait),
        )
    }
    // The phone agents' helper: starting says so; a failed start says why in plain words with the action again.
    if (line.startingAgent)
        return KitStatus(
            KitStatusKind.Connection,
            stringResource(Res.string.connectionAgentStarting),
            id = "chat-status-${line.id}",
            more = listOf(details, changeServer),
            working = true,
            wait = KitWaitKind.Warming,
        )
    val restartAgent =
        if (line.canRestartAgent)
            KitLineAction(
                stringResource(Res.string.connectionRestartAgent),
                actions::restartAgent,
                testTag = "chat-connection-restart-agent",
            )
        else null
    val message =
        if (line.agentFailed) stringResource(Res.string.connectionAgentStartFailed)
        else if (line.canRestartAgent && offline)
            stringResource(Res.string.workServerNotAnswering, "Claude Code")
        else
        stringResource(
            when {
                !offline && seconds != null -> Res.string.connectionReconnectingQuiet
                !offline -> Res.string.chatUiOpenCodeIsReconnectingTryAgainShortly
                line.canRestart -> Res.string.workServerNotAnsweringPhone
                else -> Res.string.chatUiNotConnectedToTheServerRightNow
            }
        )
    val restart =
        restartAgent
            ?: if (line.canRestart)
                KitLineAction(
                    stringResource(Res.string.connectionRestartPhoneServer),
                    actions::restartPhoneServer,
                    testTag = "chat-connection-restart",
                )
            else null
    val id = "chat-status-${line.id}"
    if (seconds != null)
        return KitStatus(
            KitStatusKind.Connection,
            message,
            id = id,
            supporting =
                if (seconds <= 0) stringResource(Res.string.connectionTryingAgainNow)
                else stringResource(Res.string.connectionTryingAgainIn, seconds),
            action =
                KitLineAction(
                    stringResource(Res.string.connectionTryNow),
                    actions::tryLinkNow,
                    testTag = "chat-connection-try-now",
                ),
            more = listOfNotNull(restart, details, changeServer),
        )
    // Already reconnecting with no schedule: waiting is the way out, so nothing is offered.
    if (!offline)
        return KitStatus(KitStatusKind.Connection, message, id = id, more = listOfNotNull(restartAgent, details, changeServer))
    val retry =
        KitLineAction(
            stringResource(Res.string.commonRetry),
            actions::retryLoad,
            testTag = "chat-offline-retry",
        )
    return KitStatus(
        KitStatusKind.Connection,
        message,
        id = id,
        action = restart ?: retry,
        more = listOfNotNull(restart?.let { retry }, details, changeServer),
    )
}

/**
 * The one status line's words (plan 09 §6.5). Only a finished action's failure may be dismissed (it
 * changes nothing).
 */
@Composable
private fun statusLine(
    line: ChatStatusUi,
    actions: ChatActions,
    serverName: String? = null,
    onDetails: () -> Unit = {},
    agentName: String? = null,
): KitStatus =
    when (line.kind) {
        ChatStatusKind.Offline,
        ChatStatusKind.Connecting -> connectionLine(line, actions, serverName, onDetails, agentName)
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
        // S28 story 3 (Flutter `_stagedRevertStatus`, `_undoneStatus`): the way back sits on the line.
        ChatStatusKind.StagedUndo ->
            KitStatus(
                KitStatusKind.Info,
                stringResource(Res.string.revertStaged),
                id = "chat-status-${line.id}",
                tone = StatusTone.Neutral,
                action =
                    KitLineAction(
                        stringResource(Res.string.revertReview),
                        actions::putBack,
                        testTag = "chat-status-revert-review",
                        icon = KitIcons.history,
                    ),
            )
        // Flutter `_subagentStatus`: this conversation was delegated; the way back to the one that delegated it.
        ChatStatusKind.Subagent ->
            KitStatus(
                KitStatusKind.Info,
                stringResource(Res.string.chatUiDelegatedSession),
                id = "chat-status-${line.id}",
                tone = StatusTone.Neutral,
                action =
                    KitLineAction(
                        stringResource(Res.string.chatUiOpenParentSession),
                        actions::openParent,
                        testTag = "subagent-parent-session",
                    ),
                icon = KitIcons.nested,
                more =
                    listOf(
                        KitLineAction(
                            stringResource(Res.string.chatUiShowAllSubagentSessions),
                            actions::showSubagents,
                            testTag = "subagent-session-list",
                        )
                    ),
            )
        ChatStatusKind.Undone ->
            KitStatus(
                KitStatusKind.Info,
                stringResource(Res.string.undoneStatus),
                id = "chat-status-${line.id}",
                tone = StatusTone.Neutral,
                action =
                    KitLineAction(
                        stringResource(Res.string.undonePutBack),
                        actions::putBack,
                        testTag = "chat-status-undo-put-back",
                        icon = KitIcons.restore,
                    ),
            )
        ChatStatusKind.ModelReplaced ->
            KitStatus(
                KitStatusKind.Info,
                stringResource(
                    Res.string.chatModelReplacedNotice,
                    line.oldModel.orEmpty(),
                    line.newModel.orEmpty(),
                ),
                id = "chat-status-${line.id}",
                tone = StatusTone.Neutral,
                onDismiss = { actions.dismissStatus(line.id) },
                dismissLabel = stringResource(Res.string.kitSheetDismiss),
            )
    }

/**
 * P12-CHAT-FEEL: what a reply's parts do. Links and web pictures go to [ChatActions.openLink] (the
 * link policy, then the one external opener); a code block's Copy copies its whole source verbatim.
 */
@androidx.compose.runtime.Immutable
internal data class ReplyCallbacks(
    val onChooseModel: () -> Unit = {},
    val onOpenCode: (KitMdBlock.Code) -> Unit = {},
    val onChoice: ((String) -> Unit)? = null,
    val onRerunCommand: ((String) -> Unit)? = null,
)

@androidx.compose.runtime.Immutable
private data class AgentSignInCopy(
    val again: ((String) -> String?)?,
    val past: ((String) -> String?)?,
    val label: String,
)

private fun phoneAgentLabel(provider: String): String? =
    when (provider) {
        "claude" -> "Claude Code"
        "codex" -> "Codex"
        else -> null
    }

@Composable
private fun rememberAgentSignInCopy(phoneAgentSignIn: Boolean): AgentSignInCopy {
    // B-AGENT-189 (#137): a lapsed sign-in of an agent on this phone says "sign in again" and offers Sign in.
    val againTemplate = stringResource(Res.string.chatSendFailureSignInAgain, "\u0000")
    val label = stringResource(Res.string.agentsSignInAgain)
    val pastTemplate = stringResource(Res.string.chatSendFailureSignedInPast, "\u0000")
    return remember(phoneAgentSignIn, againTemplate, pastTemplate, label) {
        AgentSignInCopy(
            again =
                if (phoneAgentSignIn) { p -> phoneAgentLabel(p)?.let { againTemplate.replace("\u0000", it) } }
                else null,
            past =
                if (phoneAgentSignIn) { p -> phoneAgentLabel(p)?.let { pastTemplate.replace("\u0000", it) } }
                else null,
            label = label,
        )
    }
}

@Composable
private fun rememberReplyMarkdownActions(
    actions: ChatActions,
    confirmedPaths: kotlinx.collections.immutable.ImmutableSet<String>,
    phoneAgentWords: Boolean,
    callbacks: ReplyCallbacks,
): KitMarkdownActions {
    val chooseTemplate = stringResource(Res.string.chatUiChooseOption, "\u0000")
    val doneWord = stringResource(Res.string.modelChoiceDone)
    val todoWord = stringResource(Res.string.chatUiTodo)
    val runOnComputer =
        stringResource(if (phoneAgentWords) Res.string.chatUiRunOnThisPhone else Res.string.chatUiRunOnYourComputer)
    val openFullCode = stringResource(Res.string.terminalOpenFull, "\u0000")
    val copy = stringResource(Res.string.kitCopy)
    val picture = stringResource(Res.string.chatFeelPicture)
    val onServer = stringResource(Res.string.chatFeelPictureOnServer, "\u0000")
    val onWeb = stringResource(Res.string.chatFeelPictureOnWeb, "\u0000")
    return remember(
        actions,
        confirmedPaths,
        callbacks,
        chooseTemplate,
        doneWord,
        todoWord,
        runOnComputer,
        openFullCode,
        copy,
        picture,
        onServer,
        onWeb,
    ) {
        KitMarkdownActions(
            onLink = actions::openLink,
            onPath = actions::openPath,
            confirmedPaths = confirmedPaths,
            copyCodeLabel = copy,
            onCopyCode = actions::copyText,
            moreCodeLabel = { hidden -> openFullCode.replace("\u0000", hidden.toString()) },
            onOpenCode = callbacks.onOpenCode,
            onChoice = callbacks.onChoice,
            chooseLabel = { text -> chooseTemplate.replace("\u0000", text) },
            doneWord = doneWord,
            todoWord = todoWord,
            runOnComputerLabel = runOnComputer,
            onPicture = { p ->
                val url = p.url
                val path = p.path
                when {
                    url != null -> actions.openLink(url)
                    path != null -> actions.openPicture(path, p.label ?: path.substringAfterLast('/'))
                }
            },
            openFilePictures = true,
            readPicture = actions::readPicture,
            pictureScope = actions.pictureScope,
            pictureLabel = { p ->
                p.label
                    ?: p.path?.let { onServer.replace("\u0000", it) }
                    ?: p.host?.let { onWeb.replace("\u0000", it) }
                    ?: picture
            },
        )
    }
}

@Composable
internal fun rememberReplyActions(
    actions: ChatActions,
    state: ChatUi,
    models: List<io.github.eslamasabry.oc.feature.chat.world.ComposerModel> = emptyList(),
    callbacks: ReplyCallbacks = ReplyCallbacks(),
): ReplyActions {
    val latestRows by rememberUpdatedState(state.rows)
    val phoneAgentSignIn = remember(actions) { actions.phoneAgentSignIn() }
    val markdown = rememberReplyMarkdownActions(actions, state.confirmedPaths, phoneAgentSignIn, callbacks)
    val signIn = rememberAgentSignInCopy(phoneAgentSignIn)
    val chooseModel = stringResource(Res.string.chatUiChooseAnotherModel)
    val useModelTemplate = stringResource(Res.string.chatUiUseModelAndResend, "\u0000")
    val modelGoneTemplate = stringResource(Res.string.chatErrorModelNotFoundNamed, "\u0000", "\u0001")
    val openProviders = stringResource(Res.string.chatUiOpenProviders)
    val openAccount = stringResource(Res.string.chatSendFailureAccountAction)
    val compactLabel = stringResource(Res.string.chatUiCompactSession)
    val copy = stringResource(Res.string.kitCopy)
    val copied = stringResource(Res.string.kitCopied)
    val continueLabel = stringResource(Res.string.returnBriefContinue)
    val signInSettings =
        stringResource(io.github.eslamasabry.oc.ui.words.res.Res.string.agentErrorOpenSettings)
    val canCompact = state.capabilities.compact && state.live == null
    val copyKeys =
        listOf(
            chooseModel, useModelTemplate, modelGoneTemplate, openProviders, openAccount,
            compactLabel, copy, copied, continueLabel, signInSettings,
        )
    return remember(actions, markdown, signIn, phoneAgentSignIn, models, canCompact, callbacks, copyKeys) {
        ReplyActions(
            copiedLabel = copied,
            markdown = markdown,
            copyLabel = copy,
            copy = actions::copyText,
            checkPaths = actions::checkPaths,
            continueLabel = continueLabel,
            onContinue = actions::continueReply,
            signInSettingsLabel = signInSettings,
            onSignInSettings = actions::openSignInSettings,
            signInAgainWords = signIn.again,
            signInAgainLabel = signIn.label,
            onAgentSignIn = actions::openAgentSignIn,
            signInPastWords = signIn.past,
            onThisPhone = phoneAgentSignIn,
            models = models,
            canCompact = canCompact,
            onCompact = actions::compactAgain,
            chooseModelLabel = chooseModel,
            onChooseModel = callbacks.onChooseModel,
            useModelLabel = { name -> useModelTemplate.replace("\u0000", name) },
            onUseModel = actions::useModelAndResend,
            openProvidersLabel = openProviders,
            openAccountLabel = openAccount,
            compactLabel = compactLabel,
            onOpenProviders = actions::openProviders,
            onRerunCommand = callbacks.onRerunCommand,
            onOpenChanges = actions::openChanges,
            answered = { io.github.eslamasabry.oc.feature.chat.world.ModelFallback.answered(latestRows) },
            modelGoneLabel = { model, provider ->
                modelGoneTemplate.replace("\u0000", model).replace("\u0001", provider)
            },
        )
    }
}

/** B-169: the words and actions of a failed reply a lasting limit stopped, for [LocalLimitSwitchReply]. */
@Composable
internal fun rememberLimitSwitchReply(state: ChatUi, actions: ChatActions): LimitSwitchReply {
    val switchModel = stringResource(Res.string.b169SwitchModel)
    val connectProvider = stringResource(Res.string.b169ConnectProvider)
    val switchOffline = stringResource(Res.string.b169Offline)
    val usedUpTemplate = stringResource(Res.string.b169UsedUp, "\u0000")
    val usedUpNoName = stringResource(Res.string.b169UsedUpNoName)
    val attachAgainTemplate = stringResource(Res.string.b169AttachAgain, "\u0000")
    val words =
        remember(switchModel, connectProvider, switchOffline, usedUpTemplate, usedUpNoName, attachAgainTemplate) {
            LimitSwitchWords(
                switchModel = switchModel,
                connectProvider = connectProvider,
                switchOffline = switchOffline,
                usedUp = { name -> name?.let { usedUpTemplate.replace("\u0000", it) } ?: usedUpNoName },
                attachAgain = { file -> attachAgainTemplate.replace("\u0000", file) },
            )
        }
    return remember(state.limitSwitch, state.switchOnline, state.attachAgain, actions, words) {
        LimitSwitchReply(
            limitSwitch = state.limitSwitch,
            switchOnline = state.switchOnline,
            attachAgain = state.attachAgain,
            onSwitchModel = { turnId -> actions.switchModel(turnId) },
            words = words,
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
        io.github.eslamasabry.oc.feature.chat.world.isInlineFile(file.url) ||
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
            // The footer says "Stopped" once; the work line keeps to how much work happened.
            WorkState.Stopped -> null
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
private fun rememberSendFailureWords(onThisPhone: Boolean): (Failure) -> String {
    val authored = rememberProductFailureWords()
    val changed = stringResource(Res.string.chatSendFailedChanged)
    val phoneBusy = stringResource(Res.string.schedulePhoneBusy)
    val phoneCheckFailed = stringResource(Res.string.schedulePhoneCheckFailed)
    val folderMissing = stringResource(Res.string.chatSendFolderMissing)
    val phoneFolder = stringResource(Res.string.chatSendPhoneFolder)
    val noPlanMode = stringResource(Res.string.chatSendNoPlanMode)
    val undoWaiting = stringResource(Res.string.revertResolveBeforeSending)
    val phoneHelper = stringResource(Res.string.chatSendPhoneHelperNoAnswer)
    val signInAgainWords = stringResource(Res.string.chatSendFailureSignInAgain, "\u0000")
    val agentFallback = stringResource(Res.string.chatSendFailureAgentFallback)
    return remember(authored, changed, phoneBusy, phoneCheckFailed, folderMissing, phoneFolder, noPlanMode, undoWaiting, phoneHelper, signInAgainWords, agentFallback, onThisPhone) {
        { failure ->
            when {
                // The helper that answered with the agent's own words for a sign-in that ran out is not "can't reach".
                io.github.eslamasabry.oc.feature.chat.presentation.SendFailureRules.signInRanOut(failure) ->
                    signInAgainWords.replace("\u0000", agentFallback)
                // On this phone the thing that did not answer is the phone's agents helper, not a computer.
                onThisPhone && failure is Failure.ComputerDidNotAnswer -> phoneHelper
                // B-063: the server refused the message because the project's folder is gone.
                io.github.eslamasabry.oc.feature.chat.presentation.SendFailureRules.folderMissing(failure) -> folderMissing
                // Issue 147: never sent into a folder in the phone's storage the agent cannot reach now.
                failure is Failure.Unavailable &&
                    failure.feature.name == io.github.eslamasabry.oc.feature.chat.world.PHONE_FOLDER_FEATURE -> phoneFolder
                failure is Failure.Unavailable && failure.feature.name == "schedulePhoneBusy" ->
                    phoneBusy
                // The person chose Plan and this agent has no plan or read-only mode: said, never run in its own mode.
                failure is Failure.Unavailable && failure.feature.name == "agentPlanMode" -> noPlanMode
                failure is Failure.Unavailable &&
                    failure.feature.name == "schedulePhoneCheckFailed" -> phoneCheckFailed
                failure is Failure.Refused && failure.reason == RefusalReason.StaleScope -> changed
                // An undo waits for the person: the staged-undo line above says so, and the draft is kept.
                failure is Failure.Refused && failure.reason == RefusalReason.UndoUnresolved -> undoWaiting
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

/**
 * The room, dp at text 1.0, a request card needs for its title, two body lines and its answers; below it the body shows
 * one line.
 */
private const val REQUEST_FULL_ROOM_DP: Int = 170

/** The kind of wait a server wait is, where the app has a mark for it: the server coming up, an agent getting ready. */
private fun waitOf(wait: ChatServerWait): KitWaitKind? =
    when (wait) {
        ChatServerWait.Starting, ChatServerWait.StartingLinux, ChatServerWait.StartingOpenCode -> KitWaitKind.Starting
        ChatServerWait.StartingHelper, ChatServerWait.StartingAgent -> KitWaitKind.Warming
        else -> null
    }
