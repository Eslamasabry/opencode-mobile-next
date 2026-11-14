package io.github.eslamasabry.oc.feature.chat.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.core.model.transcript.MessageErrorKind
import io.github.eslamasabry.oc.engine.agents.errors.AgentErrorText
import io.github.eslamasabry.oc.feature.chat.presentation.FindUi
import io.github.eslamasabry.oc.feature.chat.presentation.RunTailRules
import io.github.eslamasabry.oc.feature.chat.presentation.ErrorFix
import io.github.eslamasabry.oc.feature.chat.presentation.ErrorRecovery
import io.github.eslamasabry.oc.feature.chat.presentation.StoppedSteps
import io.github.eslamasabry.oc.engine.transcript.state.BlockRow
import io.github.eslamasabry.oc.engine.transcript.state.FooterRow
import io.github.eslamasabry.oc.engine.transcript.state.NoticeKind
import io.github.eslamasabry.oc.engine.transcript.state.NoticeRow
import io.github.eslamasabry.oc.engine.transcript.state.Origin
import io.github.eslamasabry.oc.engine.transcript.state.PromptRow
import io.github.eslamasabry.oc.engine.transcript.state.RowKind
import io.github.eslamasabry.oc.engine.transcript.state.ThoughtRow
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptRow
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptState
import io.github.eslamasabry.oc.engine.transcript.state.WorkLineRow
import io.github.eslamasabry.oc.engine.transcript.state.WorkState
import io.github.eslamasabry.oc.feature.chat.cards.CardAnswerDisplay
import io.github.eslamasabry.oc.feature.chat.cards.ChatTurnCards
import io.github.eslamasabry.oc.feature.chat.presentation.CodeCopy
import io.github.eslamasabry.oc.feature.chat.presentation.FenceText
import io.github.eslamasabry.oc.feature.chat.presentation.LiveTurnUi
import io.github.eslamasabry.oc.feature.chat.presentation.LongTable
import io.github.eslamasabry.oc.feature.chat.presentation.ReceiptCheckResult
import io.github.eslamasabry.oc.feature.chat.presentation.ReceiptCheckUi
import io.github.eslamasabry.oc.feature.chat.presentation.ReplyFailureKind
import io.github.eslamasabry.oc.feature.chat.presentation.TranscriptDisplayChoices
import io.github.eslamasabry.oc.engine.transcript.ledger.WorkLineState
import io.github.eslamasabry.oc.feature.chat.presentation.LiveNoticeUi
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsUi
import io.github.eslamasabry.oc.feature.chat.world.PendingSend
import io.github.eslamasabry.oc.feature.chat.world.PendingState
import io.github.eslamasabry.oc.ui.design.effects.KitSurfaceStyle
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.motion.KitReveal
import io.github.eslamasabry.oc.ui.kitchat.turn.KitLiveAsk
import io.github.eslamasabry.oc.ui.kitchat.turn.KitTurnLiveLine
import io.github.eslamasabry.oc.ui.kitchat.transcript.kitTranscriptItemMotion
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitEarlierMessages
import io.github.eslamasabry.oc.feature.chat.presentation.OlderUi
import io.github.eslamasabry.oc.ui.words.res.chatUiShowEarlierMessages
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.words.res.chatStalledStop
import io.github.eslamasabry.oc.ui.words.res.chatStopAfterStepUndo
import io.github.eslamasabry.oc.ui.words.res.chatSteerAgentFallback
import io.github.eslamasabry.oc.ui.words.res.chatSteerReadsAfterCommand
import io.github.eslamasabry.oc.ui.words.res.chatSteerReadsAfterStep
import io.github.eslamasabry.oc.ui.words.res.chatSteerStopCommand
import io.github.eslamasabry.oc.ui.words.res.chatSteerStopStep
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitSteerNote
import io.github.eslamasabry.oc.feature.chat.presentation.SteerWaitRules
import io.github.eslamasabry.oc.ui.words.res.chatStarAskAction
import io.github.eslamasabry.oc.ui.words.res.chatStarAskDismiss
import io.github.eslamasabry.oc.ui.words.res.chatStarAskBody
import io.github.eslamasabry.oc.ui.words.res.chatStarAskTitle
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappableRole
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.style.KitRunningFeedLine
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppearance
import io.github.eslamasabry.oc.ui.kitchat.code.CodeLanguage
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeBlock
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeChunk
import io.github.eslamasabry.oc.ui.kitchat.code.KitCodeKind
import io.github.eslamasabry.oc.ui.kitchat.code.KitStandaloneRedaction
import io.github.eslamasabry.oc.ui.kitchat.header.rememberKitMask
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdownActions
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitFindMarks
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdownBlock
import io.github.eslamasabry.oc.ui.kitchat.markdown.LocalKitFindMarks
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMdBlock
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMdReveal
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMdSource
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitSentFile
import io.github.eslamasabry.oc.ui.kitchat.transcript.rememberKitSentFiles
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMdNest
import io.github.eslamasabry.oc.ui.kitchat.markdown.settle
import io.github.eslamasabry.oc.ui.kitchat.markdown.takesCaret
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import io.github.eslamasabry.oc.ui.kitchat.stream.RevealRegistry
import io.github.eslamasabry.oc.ui.kitchat.stream.kitPromptLift
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitBlockKind
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitMessageActions
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitMessageTimestamp
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitPromptBubble
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitQuietLine
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitTranscriptActions
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitReplyBlock
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitReplyFooter
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitReplyNote
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitReplyMeta
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitTranscriptList
import io.github.eslamasabry.oc.ui.kitchat.transcript.TranscriptListState
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.chatHomeMinutes
import io.github.eslamasabry.oc.ui.words.res.chatLiveHideSteps
import io.github.eslamasabry.oc.ui.words.res.chatLiveShowSteps
import io.github.eslamasabry.oc.ui.words.res.chatHomeSeconds
import io.github.eslamasabry.oc.ui.words.res.chatReceiptCheckAgain
import io.github.eslamasabry.oc.ui.words.res.chatReceiptChecking
import io.github.eslamasabry.oc.ui.words.res.chatReceiptLastChecked
import io.github.eslamasabry.oc.ui.words.res.kitCopy
import io.github.eslamasabry.oc.ui.words.res.liveFailedStillWorking
import io.github.eslamasabry.oc.ui.words.res.liveFailedStopped
import io.github.eslamasabry.oc.ui.words.res.kitQueuedUncertain
import io.github.eslamasabry.oc.ui.words.res.nativePendingReview
import io.github.eslamasabry.oc.ui.words.res.queuedKeepForReview
import io.github.eslamasabry.oc.ui.words.res.queuedResendConfirm
import io.github.eslamasabry.oc.ui.words.res.queuedResendMessage
import io.github.eslamasabry.oc.ui.words.res.queuedResendTitle
import io.github.eslamasabry.oc.ui.words.res.kitTurnMore
import io.github.eslamasabry.oc.ui.words.res.queuedReceiptStillUncertain
import io.github.eslamasabry.oc.ui.words.res.queuedReceiptStorageProblem
import kotlin.time.Instant
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

/**
 * The words the transcript shows that are not server text. The P3 overload of [ChatTranscript]
 * takes them as a parameter (`:quality`'s P3 stream test drives it); the chat screen passes
 * [rememberChatWords]' resources.
 */
@Immutable
public interface ChatTranscriptWords {
    public val jumpToLatest: String

    public fun workLine(calls: Int, failed: Int, state: WorkState): String

    public fun notice(kind: NoticeKind): String

    public fun footer(failed: Boolean): String

    /** QA wave 4 N3: the footer of a reply the user stopped ("Stopped"), never an error line. */
    public fun stoppedFooter(): String = footer(false)

    public fun thought(firstLine: String): String
}

/**
 * The words of the slice-2 items the P3 interface does not cover (resolved once per composition of
 * the screen).
 */
@Immutable
internal class ChatListWords(
    val messageActions: String,
    val working: String,
    val stopping: String,
    val stoppingAfterStep: String = stopping,
    val sending: String,
    val notSent: String,
    val unconfirmed: String,
    val tryAgain: String,
    /** B5: the plain reason and next step of a failed reply, by kind. */
    val replyFailed: (ReplyFailureKind) -> String = { "" },
    val messageError: (MessageErrorKind) -> String = { "" },
    /** B2/B5: the label and copy label of the Details fold under a failure. */
    val details: String = "",
    val copyDetails: String = "",
    /** B2: why a send did not leave, in plain words. */
    val sendFailure: (Failure) -> String = { "" },
    /** Set up at first use: "{agent} needs setting up before it can answer" and "Set up {agent}", by agent name. */
    val setUpNeeded: (String) -> String = { "" },
    val setUpAction: (String) -> String = { "" },
)

/**
 * P12-CHAT-FEEL: what a reply's own parts do — links through the chat's link policy, a code block's
 * Copy, a picture's tap. Null (the P3 overload's default) keeps the plain stream-only drawing.
 */
@Immutable
internal class ReplyActions(
    val markdown: KitMarkdownActions,
    val copyLabel: String,
    val copy: (String) -> Unit,
    /** QA wave 4 N9: the code block's Copy says so after a tap. */
    val copiedLabel: String? = null,
    /** B-CHAT-064: the paths a drawn block shows, to be checked on the server. */
    val checkPaths: (List<String>) -> Unit = {},
    /**
     * B-AGENT-024: the Continue next step of a reply that lost its provider connection (null = not
     * offered).
     */
    val continueLabel: String? = null,
    val onContinue: (String) -> Unit = {},
    val signInSettingsLabel: String? = null,
    val onSignInSettings: () -> Unit = {},
    /**
     * B-AGENT-189 (#137): the plain line for an agent whose sign-in ran out ("{agent} needs you to sign in
     * again"), by provider id; null = this conversation's agent cannot sign in from the chat.
     */
    val signInAgainWords: ((String) -> String?)? = null,
    val signInAgainLabel: String? = null,
    val onAgentSignIn: (String) -> Unit = {},
    /** Error-card recovery (Flutter `assistant_error_row.dart`); a null label hides that fix. */
    val models: List<io.github.eslamasabry.oc.feature.chat.world.ComposerModel> = emptyList(),
    val canCompact: Boolean = false,
    val onCompact: () -> Unit = {},
    val chooseModelLabel: String? = null,
    val onChooseModel: () -> Unit = {},
    val useModelLabel: (String) -> String = { it },
    val onUseModel: (turnId: String, providerId: String, modelId: String) -> Unit = { _, _, _ -> },
    val openProvidersLabel: String? = null,
    val openAccountLabel: String? = null,
    val compactLabel: String? = null,
    val onOpenProviders: () -> Unit = {},
    /** S28 story 6: a finished shell step's "Run this command again" (null = this server runs no shell here). */
    val onRerunCommand: ((String) -> Unit)? = null,
)

/**
 * B-169: what a failed reply a lasting limit stopped shows (its words and Switch model / Connect a provider). The chat
 * screen provides it to its transcript through [LocalLimitSwitchReply]; without it every failed turn keeps today's
 * fixes.
 */
@Immutable
internal class LimitSwitchReply(
    /** What each failed turn offers for a lasting limit (by turn id); missing = today's fixes. */
    val limitSwitch: Map<String, io.github.eslamasabry.oc.feature.chat.presentation.LimitSwitchUi>,
    /** §6: Switch model is disabled while the server is unreachable. */
    val switchOnline: Boolean,
    /** §4.3: "Attach {file} again to send" under the failed turn whose prompt went to the composer. */
    val attachAgain: io.github.eslamasabry.oc.feature.chat.presentation.AttachAgainUi?,
    val onSwitchModel: (turnId: String) -> Unit,
    val words: LimitSwitchWords,
)

/** B-169's words on a failed reply. */
@Immutable
internal class LimitSwitchWords(
    val switchModel: String,
    val connectProvider: String,
    val switchOffline: String,
    /** "{Model} is used up for now"; the argument is null when the model is not known. */
    val usedUp: (String?) -> String,
    val attachAgain: (String) -> String,
)

internal val LocalLimitSwitchReply =
    androidx.compose.runtime.compositionLocalOf<LimitSwitchReply?> { null }

/**
 * Set up at first use, the reply seam: the failed replies (by turn id) whose agent needs setting up, and what Set up
 * opens. The chat screen provides it; without it every failed reply keeps today's words.
 */
@Immutable
internal class AgentSetUpReply(
    val needs: Map<String, io.github.eslamasabry.oc.feature.chat.world.AgentSetUpNeed>,
    val onSetUp: (agentId: String) -> Unit,
)

internal val LocalAgentSetUpReply =
    androidx.compose.runtime.compositionLocalOf<AgentSetUpReply?> { null }

/**
 * B2: a short, safe code for a send failure (no server text, no address): what QA asked to see
 * under Details.
 */
internal fun failureCode(f: Failure): String =
    when (f) {
        is Failure.Refused -> "Refused.${f.reason.name}"
        is Failure.Network -> "Network.${f.why.name}"
        is Failure.Unavailable -> "Unavailable.${f.feature.name}"
        is Failure.Busy -> "Busy"
        is Failure.Server -> "Server.${f.status ?: "-"}"
        is Failure.SignIn -> "SignIn.${f.why.name}"
        else -> f::class.simpleName ?: "Failure"
    }

/** Keys of the two item kinds the feature adds above the engine rows (plan 09 §7.2). */
internal object ListKeys {
    const val LIVE: String = "live"

    /** The room the edge dock's Undo bar takes under the newest content (first item, only while the bar shows). */
    const val DOCK_ROOM: String = "dock-room"

    /** CHAT-TRANSCRIPT: background work still running (B-CHAT-007), just above the live line. */
    const val BACKGROUND: String = "background"

    /** S28 story 8: the continued line (Continued in / from …), above the oldest message. */
    const val CONTINUED: String = "continued"

    /** "Show earlier messages": the transcript's first row while older history is not loaded. */
    const val EARLIER: String = "earlier"

    fun pending(localId: Long): String = "pending:$localId"
}

/**
 * The P3 transcript: the engine's rows only (kept for `:quality`'s P3 stream test). P12-CHAT-FEEL:
 * it draws replies the way the chat screen does (Markdown, code with Copy), so the frame test
 * measures the shipping path; links and Copy do nothing here.
 */
@Composable
public fun ChatTranscript(
    state: TranscriptState,
    words: ChatTranscriptWords,
    list: TranscriptListState,
    reveal: RevealRegistry,
    motion: KitMotionLevel,
    modifier: Modifier = Modifier,
) {
    val reply = remember {
        ReplyActions(KitMarkdownActions(onLink = {}), copyLabel = "Copy", copy = {})
    }
    // Its callers (the P3 test, the benchmark proof screen) run without the app graph: a
    // pattern-masking redactor.
    KitStandaloneRedaction {
        ChatTranscriptList(
            rows = state.rows,
            live = null,
            pending = persistentListOf(),
            words = words,
            listWords = null,
            list = list,
            reveal = reveal,
            motion = motion,
            onOpenMenu = null,
            onRetrySend = {},
            modifier = modifier,
            reply = reply,
        )
    }
}

/**
 * The transcript (plan 09 §7.1): one reverse LazyColumn, newest first. Bottom to top: the live line
 * (only while a turn runs) → optimistic prompt bubbles → the engine's rows. String keys, the
 * RowKind as contentType, kit parts only. The list follows when the newest key changes and the
 * person has not scrolled away (§7.3, in the kit).
 */
@Composable
internal fun ChatTranscriptList(
    rows: ImmutableList<TranscriptRow>,
    live: LiveTurnUi?,
    pending: ImmutableList<PendingSend>,
    words: ChatTranscriptWords,
    listWords: ChatListWords?,
    list: TranscriptListState,
    reveal: RevealRegistry,
    motion: KitMotionLevel,
    onOpenMenu: ((String) -> Unit)?,
    onRetrySend: (Long) -> Unit,
    modifier: Modifier = Modifier,
    facts: TurnFactsUi = TurnFactsUi.Empty,
    onOpenSession: ((String) -> Unit)? = null,
    reply: ReplyActions? = null,
    onOpenFile: ((io.github.eslamasabry.oc.engine.transcript.turnfacts.ToolArtifact) -> Unit)? =
        null,
    reasoningExpanded: Boolean = false,
    onCompactAgain: (() -> Unit)? = null,
    /** Stop on the live line while a tool call has waited minutes (null hides it). */
    onStopLive: (() -> Unit)? = null,
    /** C5: Undo on "Stopping after this step". */
    onUndoStopAfterStep: (() -> Unit)? = null,
    /** C6: Stop on the steer note, given the steered message's key and words. */
    onStopForSteer: ((String, String) -> Unit)? = null,
    /** A finished turn's "All files" (the project's Files); null hides the link. */
    onAllFiles: (() -> Unit)? = null,
    /** "Send again" under the newest prompt that got no reply (Flutter no_reply_reason.dart:198); null hides it. */
    onSendAgain: (() -> Unit)? = null,
    receiptChecks: ImmutableMap<Long, ReceiptCheckUi> = persistentMapOf(),
    onCheckReceipt: ((Long) -> Unit)? = null,
    /** Set up at first use: opens the set-up of the agent (by id) a failed send needs; null hides it. */
    onSetUpAgent: ((String) -> Unit)? = null,
    onReviewPending: (() -> Unit)? = null,
    display: TranscriptDisplayChoices = TranscriptDisplayChoices(),
    onCopyReply: ((FooterRow) -> Unit)? = null,
    folds: TranscriptFolds = rememberTranscriptFolds(),
    /** S28 story 8: the handoff line ("Continued from …"), drawn above the oldest message; null = none. */
    topLine: (@Composable () -> Unit)? = null,
    /** The one-time GitHub star ask rides under the live line while it is up (a turn still waiting for its reply). */
    starAsk: Boolean = false,
    onStarTap: () -> Unit = {},
    onStarDismiss: () -> Unit = {},
    /** Extra room (px) under the newest content: the edge dock's Undo bar while it shows. */
    extraBottomPx: Int = 0,
    /** Earlier messages not loaded yet: "Show earlier messages" is the transcript's first row; null = all loaded. */
    older: OlderUi? = null,
    onLoadOlder: () -> Unit = {},
    /** Find, while it is open: the text parts mark its matches, and the current match's folded parts open. */
    find: FindUi? = null,
) {
    val currentRows by rememberUpdatedState(rows)
    FindOpensFolds(find, rows, facts, folds)
    // B-SEC-094 / R18: a code block's Copy hands over what the block shows (redacted), never the
    // raw fence; only
    // Copy reply copies the reply as written (plan 09 §6.6).
    val copyCode: (String, (String) -> String) -> Unit =
        remember(reply) {
            { key, mask ->
                val r = currentRows
                CodeCopy.of(r, r.indexOfFirst { it.key == key })?.let {
                    reply?.copy?.invoke(mask(it))
                }
            }
        }
    val anchor = rememberFoldAnchor()
    // B0: a repeated key kills the whole screen (LazyColumn throws), so the list never sees one.
    val unique = remember(rows) { rows.distinctKeys() }
    val tables = remember(unique) { LongTable.of(unique) }
    val copyableTurns =
        remember(unique) {
            unique
                .filterIsInstance<BlockRow>()
                .filter { it.text.isNotBlank() }
                .mapTo(hashSetOf()) { it.turnId }
        }
    val newestPrompt = unique.firstOrNull { it is PromptRow }?.key
    // B8: the newest turn's footer carries "Send again" when its reply was cut off before any words.
    val newestFooter =
        unique.firstOrNull { it is PromptRow || it is FooterRow }?.takeIf { it is FooterRow }?.key
    val newestCompleted =
        unique
            .filterIsInstance<FooterRow>()
            .firstOrNull { facts.reading.metadata[it.turnId] != null }
            ?.key
    val newestKey =
        when {
            live != null -> ListKeys.LIVE
            pending.isNotEmpty() -> ListKeys.pending(pending.first().localId)
            else -> unique.firstOrNull()?.key
        }
    // Rows fade in where they belong; nothing glides or fades out in place, so no row ever lies over another
    // (kitTranscriptItemMotion; audit 1, 2026-11-14).
    // The dock's room is a new item at index 0, but the newest key does not change, so the kit's follow (which asks
    // for index 0 when the newest key changes) does not run, and the list anchors on the old first key: on a long
    // transcript the room landed out of view below and the bar still covered the newest reply (gate, w4-undo-room).
    // While following, ask for index 0 in the same frame as the insert, as the kit does for a new row. A person reading
    // older text (not following) is never moved; the room's removal needs nothing (its key goes, index 0 stays).
    val roomBefore = remember { intArrayOf(0) }
    SideEffect {
        val came = extraBottomPx > 0 && roomBefore[0] == 0
        roomBefore[0] = extraBottomPx
        if (came && list.following && !list.lazy.isScrollInProgress) list.lazy.requestScrollToItem(0)
    }
    // Flutter `_liveStepsShown` (chat_stop_after_step.dart): a tap on the live line shows the running turn's steps;
    // cleared once the turn ends. Null = no running turn; false = the live line names the step and the work hides.
    var stepsShown by rememberSaveable { mutableStateOf(false) }
    if (live == null && stepsShown) stepsShown = false
    val runningSteps: Boolean? = if (live == null) null else stepsShown
    // The same keys, in the same order, as the items below: the reader's place is kept by key (see KeepScrollPlace).
    val itemKeys =
        remember(unique, pending, live != null, listWords != null, extraBottomPx > 0, facts.background.isNotEmpty(), topLine != null, older != null) {
            buildList {
                if (extraBottomPx > 0) add(ListKeys.DOCK_ROOM)
                if (live != null && listWords != null) add(ListKeys.LIVE)
                if (facts.background.isNotEmpty() && listWords != null) add(ListKeys.BACKGROUND)
                if (listWords != null) pending.forEach { add(ListKeys.pending(it.localId)) }
                unique.forEach { add(it.key) }
                if (topLine != null) add(ListKeys.CONTINUED)
                if (older != null) add(ListKeys.EARLIER)
            }
        }
    KeepScrollPlace(list, itemKeys)
    KitTranscriptList(
        state = list,
        newestKey = newestKey,
        jumpLabel = words.jumpToLatest,
        motion = motion,
        modifier = modifier,
        // F: chat_transcript.dart:160-165 and kit_turn.dart:591: the newest turn ends space2 + space2 above the
        // composer's clearance; the composer pane already keeps its own space2 on top (ChatScreen), the rest here.
        contentPadding = PaddingValues(top = Space.S4.dp, bottom = Space.S2.dp),
        // Reading back to the top of what is loaded brings the earlier messages by itself; the row stays the visible
        // way to them (and the way to try again after a read that failed).
        onReachOldest = if (older != null && !older.loading && older.failure == null) onLoadOlder else null,
    ) {
        // The dock's Undo bar takes this room under the newest content. An item, not padding: the list keeps the
        // row the person reads in place by its key, and at the end the newest content rides up with the room.
        if (extraBottomPx > 0) {
            item(key = ListKeys.DOCK_ROOM, contentType = "dock-room") {
                Spacer(Modifier.height(with(LocalDensity.current) { extraBottomPx.toDp() }))
            }
        }
        if (live != null && listWords != null) {
            item(key = ListKeys.LIVE, contentType = "live") {
              Box(kitTranscriptItemMotion()) {
                // No Stop here (plan 09 §4.4): Stop sits in the composer's row.
                // Retry, stall and shortening fold into the live line: no new bar
                // (CHAT-TRANSCRIPT).
                val notice = facts.live
                val liveWords =
                    when {
                        live.stopping -> listWords.stopping
                        live.stoppingAfterStep -> listWords.stoppingAfterStep
                        notice != null -> liveNoticeWords(notice)
                        else -> listWords.working
                    }
                val runTurn = unique.firstOrNull()?.turnId
                val runWork = runTurn?.let { facts.work[it] }
                if (LocalKitAppearance.current.style != KitSurfaceStyle.Signal) {
                    val line =
                        liveLineWords(
                            live = live,
                            listWords = listWords,
                            notice = notice,
                            work = runWork,
                            writing = unique.firstOrNull().let { it is BlockRow && it.live },
                            // The turn's clock (Flutter `since`): when the run started, else when its prompt was sent.
                            elapsedMs =
                                (facts.runStartedAtMs ?: unique.firstNotNullOfOrNull { (it as? PromptRow)?.createdMs })
                                    ?.let { facts.nowMs - it },
                        )
                    // The ask folds away as soon as the reply streams, or when a note about the turn replaces the words.
                    val streaming = unique.firstOrNull().let { it is BlockRow && it.live }
                    // A retry that keeps failing offers another model besides waiting or Stop.
                    val retryWayOut =
                        notice is LiveNoticeUi.Retry && notice.facts.offerWayOut && !live.canUndoStopAfterStep &&
                            reply?.chooseModelLabel != null
                    val askTitle = stringResource(Res.string.chatStarAskTitle)
                    val askBody = stringResource(Res.string.chatStarAskBody)
                    val askAction = stringResource(Res.string.chatStarAskAction)
                    val askDismiss = stringResource(Res.string.chatStarAskDismiss)
                    val ask =
                        if (starAsk && !streaming && notice == null && !live.stopping && !live.stoppingAfterStep)
                            KitLiveAsk(askTitle, askBody, askAction, onStarTap, askDismiss, onStarDismiss)
                        else null
                    // C6: under the steered prompt (the live item sits right below the newest row), a quiet note.
                    val steerWait = SteerWaitRules.wait(unique, facts.work, busy = true, joins = live.joinsTurn)
                    if (steerWait != null) {
                        val agent = live.agentName ?: stringResource(Res.string.chatSteerAgentFallback)
                        KitSteerNote(
                            text =
                                stringResource(
                                    if (steerWait.command) Res.string.chatSteerReadsAfterCommand
                                    else Res.string.chatSteerReadsAfterStep,
                                    agent,
                                ),
                            stopLabel =
                                stringResource(
                                    if (steerWait.command) Res.string.chatSteerStopCommand
                                    else Res.string.chatSteerStopStep
                                ),
                            onStop =
                                if (onStopForSteer != null && SteerWaitRules.showsStop(steerWait, facts.nowMs))
                                    ({ onStopForSteer(steerWait.promptKey, steerWait.text) })
                                else null,
                            stopping = live.stopping,
                            modifier = Modifier.testTag("chat-steer-wait"),
                        )
                    }
                    if (line != null) KitTurnLiveLine(
                        words = line.words,
                        tone = line.tone,
                        spoken = line.spoken,
                        step = line.step,
                        // D1: the steps are always in view above, so the live line opens nothing.
                        onTap = null,
                        tapLabel =
                            stringResource(
                                if (stepsShown) Res.string.chatLiveHideSteps else Res.string.chatLiveShowSteps
                            ),
                        modifier = Modifier.padding(horizontal = Space.S4.dp).testTag("chat-live-line"),
                        actionLabel =
                            if (live.canUndoStopAfterStep && onUndoStopAfterStep != null)
                                stringResource(Res.string.chatStopAfterStepUndo)
                            else if (notice is LiveNoticeUi.ToolWait && onStopLive != null)
                                stringResource(Res.string.chatStalledStop)
                            else if (retryWayOut) reply?.chooseModelLabel
                            else null,
                        onAction =
                            if (live.canUndoStopAfterStep) onUndoStopAfterStep
                            else if (retryWayOut) reply?.onChooseModel
                            else onStopLive,
                        ask = ask,
                    )
                } else if (
                    RunTailRules.liveLineShown(
                        RunTailRules.tail(
                            busy = true,
                            stopping = live.stopping || live.stoppingAfterStep,
                            waiting = runWork?.state == WorkLineState.WaitingForYou,
                        )
                    )
                ) {
                    val minutes = stringResource(Res.string.chatHomeMinutes)
                    val seconds = stringResource(Res.string.chatHomeSeconds)
                    val step = facts.work.values.lastOrNull { it.now != null }?.now
                    val mask = rememberKitMask()
                    KitRunningFeedLine(
                        sinceMs = step?.startedAtMs ?: facts.runStartedAtMs,
                        nowMs = facts.nowMs,
                        command = step?.facts?.subject?.let(mask),
                        runningLabel = liveWords,
                        minutesLabel = { minutes.replace("%1\$s", it) },
                        secondsLabel = { seconds.replace("%1\$s", it) },
                    )
                }
              }
            }
        }
        if (facts.background.isNotEmpty() && listWords != null) {
            item(key = ListKeys.BACKGROUND, contentType = "background") {
                KitQuietLine(
                    backgroundWords(facts.background, facts.nowMs),
                    kitTranscriptItemMotion().testTag("chat-background-work"),
                )
            }
        }
        if (listWords != null) {
            items(
                pending,
                key = { ListKeys.pending(it.localId) },
                contentType = { RowKind.Prompt },
            ) { p ->
              // The bubble rose from the field on its own; when the server's copy replaces it, neither fades (no
              // flash, no double bubble).
              Box(kitTranscriptItemMotion(fadeIn = false)) {
                PendingBubble(
                    p,
                    listWords,
                    onRetry = { onRetrySend(p.localId) },
                    check = receiptChecks[p.localId],
                    onCheck =
                        if (p.canCheckReceipt) onCheckReceipt?.let { check -> { check(p.localId) } }
                        else null,
                    onReview = onReviewPending,
                    onSetUp = onSetUpAgent,
                )
              }
            }
        }
        itemsIndexed(unique, key = { _, it -> it.key }, contentType = { _, it -> it.kind }) { index, row ->
            // P-010: a row the person opens stays under their finger. The person's own prompt replaces its pending
            // bubble in place, so it never fades in.
            Column(
                kitTranscriptItemMotion(fadeIn = row !is PromptRow)
                    .foldAnchor(row.key, anchor, list.lazy)
            ) {
              // Find: every match in this row is tinted by the kit's text parts; the current match of the current row
              // (a message or a prompt: one text) is the stronger one. Nothing is provided while Find is closed.
              val marks =
                  remember(find?.query, find?.currentKey, find?.currentOrdinal, row.key, row is BlockRow || row is PromptRow) {
                      find?.takeIf { it.query.isNotBlank() }?.let {
                          val current = row.key == it.currentKey && (row is BlockRow || row is PromptRow)
                          KitFindMarks(it.query, if (current) it.currentOrdinal else -1)
                      }
                  }
              CompositionLocalProvider(LocalKitFindMarks provides marks) {
                // The turn's rhythm (Flutter kit_turn.dart:484-516): the rows above stand apart as Flutter's turn.
                val gap = turnGap(row, unique.getOrNull(index + 1))
                if (gap > 0) Spacer(Modifier.height(gap.dp))
                // Keep the engine's item indices: a folded source has an empty item, not a removed
                // list
                // position.
                val source =
                    when (row) {
                        is BlockRow -> row.key.removePrefix("b:").substringBeforeLast(':')
                        is ThoughtRow -> row.key.removePrefix("thought:")
                        is NoticeRow -> row.key.removePrefix("notice:")
                        else -> null
                    }
                if (source in facts.reading.hiddenPartKeys) return@CompositionLocalProvider
                // A table longer than one chunk is drawn whole by its first chunk; its later chunks
                // draw nothing.
                if (reply != null && row is BlockRow && row.key in tables) {
                    val whole = tables[row.key]
                    if (whole != null)
                        ChatRow(
                            row.copy(text = whole, continues = false),
                            words,
                            listWords,
                            reveal,
                            motion,
                            onOpenMenu,
                            facts,
                            folds,
                            onOpenSession,
                            reply,
                            copyCode,
                            onOpenFile,
                            reasoningExpanded,
                            onCompactAgain,
                            onCopyReply.takeIf { row.turnId in copyableTurns },
                            teachDetails = row.key == newestCompleted,
                            runningSteps = runningSteps,
                            onHideSteps = { stepsShown = false },
                            stopping = live?.stopping == true,
                        )
                } else {
                    ChatRow(
                        row,
                        words,
                        listWords,
                        reveal,
                        motion,
                        onOpenMenu,
                        facts,
                        folds,
                        onOpenSession,
                        reply,
                        copyCode,
                        onOpenFile,
                        reasoningExpanded,
                        onCompactAgain,
                        onCopyReply.takeIf { row.turnId in copyableTurns },
                        teachDetails = row.key == newestCompleted,
                        onAllFiles = onAllFiles,
                        onSendAgain = onSendAgain.takeIf { row.key == newestPrompt || row.key == newestFooter },
                        runningSteps = runningSteps,
                        onHideSteps = { stepsShown = false },
                        stopping = live?.stopping == true,
                    )
                }
                if (display.timestampsVisible) {
                    val created =
                        when (row) {
                            is PromptRow -> row.createdMs
                            is FooterRow -> row.createdMs
                            else -> null
                        }
                    if (created != null) KitMessageTimestamp(created, facts.nowMs)
                }
              }
            }
        }
        // S28 story 8 (Flutter chat_handoff_link.dart): where this work came from or went sits above the
        // conversation's oldest loaded message (the list is newest first, so this item is drawn on top).
        if (topLine != null) item(key = ListKeys.CONTINUED, contentType = "continued") { topLine() }
        // Audit 1 (2026-11-14): the way to the earlier messages is the transcript's first row, not a pill over it.
        if (older != null)
            item(key = ListKeys.EARLIER, contentType = "earlier") {
                if (older.failure != null) HistoryReadFailure(older.failure, onLoadOlder)
                else
                    KitEarlierMessages(
                        stringResource(Res.string.chatUiShowEarlierMessages),
                        onLoadOlder,
                        loading = older.loading,
                        modifier = Modifier.padding(vertical = Space.S2.dp),
                    )
            }
    }
}

/**
 * [rows] without a second row of an already used key (the first, newest, wins); same list when
 * unique.
 */
internal fun ImmutableList<TranscriptRow>.distinctKeys(): ImmutableList<TranscriptRow> {
    val seen = HashSet<String>(size * 2)
    if (all { seen.add(it.key) }) return this
    val kept = HashSet<String>(size * 2)
    return filter { kept.add(it.key) }.toImmutableList()
}

@Composable
private fun PendingBubble(
    p: PendingSend,
    words: ChatListWords,
    onRetry: () -> Unit,
    check: ReceiptCheckUi? = null,
    onCheck: (() -> Unit)? = null,
    onReview: (() -> Unit)? = null,
    onSetUp: ((String) -> Unit)? = null,
) {
    Column {
        // The same thumbnails the echoed message will show (cached by address), so the swap does not flicker.
        val sent =
            rememberKitSentFiles(
                remember(p.files) { p.files.map { KitSentFile(it.name, it.mime, it.url) } }
            )
        KitPromptBubble(
            p.text,
            Modifier.kitPromptLift(p.localId, p.text),
            photos = sent.photos,
            attachments = sent.attachments,
        )
        val setUp = p.setUp?.takeIf { onSetUp != null && p.state != PendingState.Sending }
        // Set up at first use: the agent was never set up, so the one way on is its set-up (the words stay above).
        if (setUp != null) {
            KitQuietLine(words.setUpNeeded(setUp.name))
            KitButton(
                words.setUpAction(setUp.name),
                { onSetUp?.invoke(setUp.agentId) },
                role = KitButtonRole.Tertiary,
                modifier = Modifier.testTag("chat-send-set-up"),
            )
        } else when (p.state) {
            PendingState.Sending -> KitQuietLine(words.sending)
            // Review opens the waiting-sends page, which lists only sends with a record: without one it would
            // open empty (Paseo fault 2), so the bubble offers Send again itself.
            PendingState.Unconfirmed ->
                ReceiptCheckLine(
                    p.localId,
                    check,
                    onCheck,
                    onReview = onReview?.takeIf { p.reviewable },
                    onSendAgain = onRetry.takeIf { !p.reviewable },
                )
            PendingState.Failed -> {
                KitQuietLine(words.notSent)
                p.failure?.let { failure ->
                    val line = words.sendFailure(failure)
                    if (line.isNotEmpty()) KitQuietLine(line)
                    if (words.details.isNotEmpty())
                        KitDetailsFold(
                            label = words.details,
                            copyLabel = words.copyDetails,
                            notes = listOf(failureCode(failure)),
                            modifier = Modifier.testTag("chat-send-failure-details"),
                        )
                }
                KitButton(
                    words.tryAgain,
                    onRetry,
                    role = KitButtonRole.Tertiary,
                    modifier = Modifier.testTag("chat-send-error-try-again"),
                )
            }
        }
    }
}

/**
 * The unconfirmed bubble looks first: Check again and Review never resend. Its own Send again (a send with no record on
 * the waiting-sends page) asks first, the way that page does, because the words may already have arrived.
 */
@Composable
private fun ReceiptCheckLine(
    localId: Long,
    check: ReceiptCheckUi?,
    onCheck: (() -> Unit)?,
    onReview: (() -> Unit)? = null,
    onSendAgain: (() -> Unit)? = null,
) {
    val checking = check?.checking == true
    val words =
        when {
            checking -> stringResource(Res.string.chatReceiptChecking)
            check?.result == ReceiptCheckResult.StorageProblem ->
                stringResource(Res.string.queuedReceiptStorageProblem)
            check?.result == ReceiptCheckResult.StillUnknown ->
                stringResource(Res.string.queuedReceiptStillUncertain)
            else -> stringResource(Res.string.kitQueuedUncertain)
        }
    // One notice under the bubble, with Check again as its real action (owner 2026-11-06: the same problem was
    // said twice, and "Check again" read as a heading). The screen's status line leaves this to the bubble, so the
    // bubble also offers Review: the waiting sends, where Send again and Discard live (N-48).
    val checkAgain = stringResource(Res.string.chatReceiptCheckAgain)
    val review = stringResource(Res.string.nativePendingReview)
    val sendAgain = stringResource(Res.string.queuedResendConfirm)
    var askingSendAgain by remember(localId) { mutableStateOf(false) }
    // On the reply's own 16 dp edge, never against the screen's.
    Column(Modifier.padding(horizontal = Space.S4.dp).testTag("chat-receipt-$localId")) {
        KitNotice(
            message = words,
            tone = StatusTone.Attention,
            actions =
                listOfNotNull(
                    onCheck?.let {
                        KitLineAction(
                            checkAgain,
                            it,
                            enabled = !checking,
                            disabledReason = words,
                            testTag = "chat-receipt-check-$localId",
                        )
                    },
                    onReview?.let { KitLineAction(review, it, testTag = "chat-receipt-review-$localId") },
                    onSendAgain?.let {
                        KitLineAction(
                            sendAgain,
                            { askingSendAgain = true },
                            testTag = "chat-receipt-send-again-$localId",
                        )
                    },
                ),
        )
        // The same warning as the waiting-sends page (N-48): "It may already have reached OpenCode. Sending again can
        // duplicate it." Nothing is sent without this second tap.
        KitConfirm(
            spec =
                if (askingSendAgain && onSendAgain != null)
                    KitConfirmSpec(
                        title = stringResource(Res.string.queuedResendTitle),
                        confirmLabel = sendAgain,
                        kind = KitConfirmKind.Neutral,
                        body = stringResource(Res.string.queuedResendMessage),
                        cancelLabel = stringResource(Res.string.queuedKeepForReview),
                    )
                else null,
            onConfirm = {
                askingSendAgain = false
                onSendAgain?.invoke()
            },
            onCancel = { askingSendAgain = false },
        )
        check?.checkedAtMs?.let { at ->
            val time =
                Instant.fromEpochMilliseconds(at)
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                    .time
            val label =
                Numbers.digits(
                    time.hour.toString().padStart(2, '0') +
                        ":" +
                        time.minute.toString().padStart(2, '0')
                )
            KitQuietLine(stringResource(Res.string.chatReceiptLastChecked, label))
        }
    }
}

@Composable
private fun ChatRow(
    row: TranscriptRow,
    words: ChatTranscriptWords,
    listWords: ChatListWords?,
    reveal: RevealRegistry,
    motion: KitMotionLevel,
    onOpenMenu: ((String) -> Unit)?,
    facts: TurnFactsUi = TurnFactsUi.Empty,
    folds: TranscriptFolds? = null,
    onOpenSession: ((String) -> Unit)? = null,
    reply: ReplyActions? = null,
    copyCode: (String, (String) -> String) -> Unit = { _, _ -> },
    onOpenFile: ((io.github.eslamasabry.oc.engine.transcript.turnfacts.ToolArtifact) -> Unit)? =
        null,
    reasoningExpanded: Boolean = false,
    onCompactAgain: (() -> Unit)? = null,
    onCopyReply: ((FooterRow) -> Unit)? = null,
    teachDetails: Boolean = false,
    onAllFiles: (() -> Unit)? = null,
    /** The newest prompt that got no reply: its notice's "Send again"; null elsewhere. */
    onSendAgain: (() -> Unit)? = null,
    /** A turn runs: whether the person asked to see its steps (null = no turn runs). */
    runningSteps: Boolean? = null,
    onHideSteps: () -> Unit = {},
    /** The person pressed Stop: no step says it waits for them any more. */
    stopping: Boolean = false,
) {
    when (row) {
        is BlockRow -> {
            val content: @Composable () -> Unit = {
                if (reply != null) KitMdNest(row.depth) { RichBlock(row, reveal, motion, reply, copyCode) }
                else
                    KitReplyBlock(
                        text = row.text,
                        kind = row.kind.toKit(),
                        live = row.live,
                        streamed = row.origin == Origin.Streamed,
                        revealKey = row.key,
                        revealGroup = row.key.substringBeforeLast(':'),
                        registry = reveal,
                        motion = motion,
                        continued = row.continued,
                        continues = row.continues,
                    )
            }
            if (onOpenMenu != null && listWords != null) {
                KitMessageActions(
                    label = listWords.messageActions,
                    onOpen = { onOpenMenu(row.key) },
                ) {
                    content()
                }
            } else content()
        }
        is PromptRow ->
            Column {
                if (onOpenMenu != null && listWords != null) {
                    // Tap is the visible way to the message's actions; long-press is the familiar
                    // hidden twin (06 §11.1).
                    KitMessageActions(
                        label = listWords.messageActions,
                        onOpen = { onOpenMenu(row.key) },
                    ) {
                        SentPromptBubble(row)
                    }
                } else {
                    SentPromptBubble(row)
                }
                // A turn with no assistant row still has an explicit end next to its prompt.
                if (facts.reading.metadata[row.turnId] == null)
                    facts.reading.endNotes[row.turnId]?.let { note ->
                        if (note == io.github.eslamasabry.oc.feature.chat.presentation.TurnEndNote.Silent)
                            NoReplyNotice(onSendAgain)
                        else
                            TurnEndNote(
                                note,
                                reply?.let { r -> r.continueLabel?.let { w -> { r.onContinue(w) } } },
                                onSendAgain,
                            )
                    }
            }
        is ThoughtRow -> {
            val detail = facts.reading.details[row.key]
            // Review #13: a turn whose only work is thinking folds its thoughts under one work line (this row is its
            // first thought; the others' rows are hidden). A turn with steps folds them under its own work line.
            val thoughts = facts.reading.thoughtWork[row.turnId]
            if (thoughts != null && folds != null && facts.work[row.turnId] == null)
                ThoughtWorkLine(
                    row.turnId,
                    thoughts,
                    folds,
                    reasoningExpanded,
                    reply,
                    onCompactAgain,
                    onOpenSession,
                    thinking = row.live,
                )
            else if (detail != null && folds != null)
                TranscriptDetail(
                    detail,
                    folds,
                    reasoningExpanded,
                    reply,
                    onCompactAgain,
                    onOpenSession,
                    thinking = row.live,
                )
            else FoldedThoughtLine(row.key, row.text, folds)
        }
        is WorkLineRow ->
            // P12-CHAT-CARDS: a turn's agent cards stand right under its work line, in the reply
            // (never folded in).
            Column {
                val work = facts.work[row.turnId]
                if (work != null && folds != null) {
                    // D1 "steps only" (owner 2026-11-14): the steps stay in view while the turn runs (the newest five,
                    // the live one with its light); the live line under them no longer names the step they show.
                    TurnWorkLine(
                        work,
                        facts.nowMs,
                        folds,
                        onOpenSession,
                        onOpenFile,
                        facts.reading.workEntries[row.turnId].orEmpty(),
                        reasoningExpanded,
                        reply,
                        onCompactAgain,
                        stopping = stopping,
                    )
                }
                else
                    KitQuietLine(
                        if (listWords != null) workLineWords(row.calls, row.state)
                        else words.workLine(row.calls, row.failed, row.state)
                    )
                ChatTurnCards(row.turnId)
            }
        is NoticeRow -> {
            val detail = facts.reading.details[row.key]
            if (detail != null && folds != null)
                TranscriptDetail(
                    detail,
                    folds,
                    reasoningExpanded,
                    reply,
                    onCompactAgain,
                    onOpenSession,
                )
            else KitQuietLine(words.notice(row.notice))
        }
        is FooterRow ->
            Column {
                if (
                    row.failed &&
                        (!row.reason.isNullOrBlank() ||
                            row.errorKind.let { it != null && it != MessageErrorKind.Unknown }) &&
                        listWords != null &&
                        listWords.details.isNotEmpty()
                ) {
                    val reason = row.reason.orEmpty()
                    // B-SEC-014/B-SEC-133: the server's text may carry a key or an Authorization
                    // header; Details shows
                    // (and copies) it masked by the app's one redactor, as Flutter redacts notes
                    // before display.
                    val mask = rememberKitMask()
                    // B5: why the reply failed and what to do next; the server's own words only
                    // under Details.
                    Column(Modifier.testTag("chat-reply-failed")) {
                        // A failed reply keeps its actions (Copy, Share, Select text, Delete), as
                        // Flutter's does.
                        if (onOpenMenu != null)
                            KitTappable(
                                onClick = { onOpenMenu(row.key) },
                                onClickLabel = listWords.messageActions,
                                role = KitTappableRole.Row,
                                modifier = Modifier.testTag("chat-turn-footer"),
                            ) {
                                KitQuietLine(words.footer(row.failed))
                            }
                        else KitQuietLine(words.footer(row.failed))
                        // Say plainly whether the agent is still going: a retry or a step still
                        // running means yes; otherwise it has stopped and a message carries it on.
                        val stillWorking =
                            facts.live is LiveNoticeUi.Retry ||
                                facts.work[row.turnId]?.state == WorkLineState.Running
                        KitQuietLine(
                            stringResource(
                                if (stillWorking) Res.string.liveFailedStillWorking
                                else Res.string.liveFailedStopped
                            ),
                            modifier = Modifier.semantics {
                                liveRegion = LiveRegionMode.Polite
                            },
                        )
                        val kind = ReplyFailureKind.of(reason)
                        // The server's typed error class reads first (rest-1); the reason text only
                        // drives the generic words when there is no typed class.
                        val typed =
                            row.errorKind
                                ?.takeUnless { it == MessageErrorKind.Unknown }
                                ?.let(listWords.messageError)
                                ?.takeIf { it.isNotBlank() }
                        // B-169 §2: a lasting limit reads plainly ("{Model} is used up for now").
                        val limits = LocalLimitSwitchReply.current
                        val limit = reply?.let { limits?.limitSwitch?.get(row.turnId) }
                        // Set up at first use: the agent was never set up (omp's first answer); one line, one action.
                        val setUpReply = LocalAgentSetUpReply.current
                        val setUp = setUpReply?.needs?.get(row.turnId)
                        // B-AGENT-189: a lapsed sign-in names the agent and says "sign in again".
                        val signInAgain =
                            if (setUp == null && limit == null && AgentErrorText.signInExpired(reason))
                                row.providerId?.let { reply?.signInAgainWords?.invoke(it) }
                            else null
                        if (setUp != null)
                            KitQuietLine(listWords.setUpNeeded(setUp.name), modifier = Modifier.testTag("chat-reply-set-up"))
                        else if (limit != null && limits != null) KitQuietLine(limits.words.usedUp(limit.modelName))
                        else if (signInAgain != null)
                            KitQuietLine(signInAgain, modifier = Modifier.testTag("chat-sign-in-again"))
                        else if (typed != null) KitQuietLine(typed)
                        else {
                            val (headline, hint) = agentFailureWords(reason)
                            KitQuietLine(headline)
                            KitQuietLine(hint)
                        }
                        // B-099: the ways on and Details keep the quiet lines' inset, never the screen edge.
                        limits?.attachAgain
                            ?.takeIf { it.turnId == row.turnId }
                            ?.let { KitQuietLine(limits.words.attachAgain(it.file)) }
                        KitTranscriptActions {
                            if (setUp != null && setUpReply != null)
                                KitButton(
                                    listWords.setUpAction(setUp.name),
                                    { setUpReply.onSetUp(setUp.agentId) },
                                    role = KitButtonRole.Primary,
                                    modifier = Modifier.testTag("error-action-set-up"),
                                )
                            val signInAgainLabel = reply?.signInAgainLabel
                            val signInAgainAgent = row.providerId
                            if (signInAgain != null && signInAgainLabel != null && signInAgainAgent != null) {
                                val agentId = signInAgainAgent
                                KitButton(
                                    signInAgainLabel,
                                    { reply?.onAgentSignIn?.invoke(agentId) },
                                    role = KitButtonRole.Primary,
                                    modifier = Modifier.testTag("error-action-sign-in-again"),
                                )
                            }
                            if (
                                setUp == null &&
                                    AgentErrorText.classify(reason)?.offersSignIn == true &&
                                    reply?.signInSettingsLabel != null
                            ) {
                                KitButton(
                                    reply.signInSettingsLabel,
                                    reply.onSignInSettings,
                                    role = KitButtonRole.Secondary,
                                )
                            }
                            // Flutter error card: a sign-in, no-credit, wrong-model or full-context
                            // failure always offers the way on (never a dead end).
                            if (reply != null && setUp == null) {
                                val suggestion =
                                    if (
                                        row.errorKind == MessageErrorKind.ModelNotFound ||
                                            kind == ReplyFailureKind.Model
                                    )
                                        ErrorRecovery.suggestedModel(reason, reply.models)
                                    else null
                                val showSignIn =
                                    AgentErrorText.classify(reason)?.offersSignIn == true &&
                                        reply.signInSettingsLabel != null
                                (if (limit != null)
                                        ErrorRecovery.limitFixes(limit.offer, kind, reason, reply.canCompact)
                                    else
                                        ErrorRecovery.fixes(
                                            row.errorKind,
                                            kind,
                                            reason,
                                            suggestion != null,
                                            reply.canCompact,
                                        ))
                                    .forEach { fix ->
                                        when (fix) {
                                            ErrorFix.SwitchModel ->
                                                limits?.words?.switchModel?.let {
                                                    KitButton(
                                                        it,
                                                        { limits.onSwitchModel(row.turnId) },
                                                        role = KitButtonRole.Primary,
                                                        enabled =
                                                            io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled.of(
                                                                limits.switchOnline,
                                                                limits.words.switchOffline,
                                                            ),
                                                        modifier = Modifier.testTag("error-action-switch-model"),
                                                    )
                                                }
                                            ErrorFix.ConnectProvider ->
                                                limits?.words?.connectProvider?.let {
                                                    KitButton(
                                                        it,
                                                        reply.onOpenProviders,
                                                        role = KitButtonRole.Secondary,
                                                        modifier = Modifier.testTag("error-action-connect-provider"),
                                                    )
                                                }
                                            ErrorFix.UseModel ->
                                                if (suggestion != null)
                                                    KitButton(
                                                        reply.useModelLabel(suggestion.label),
                                                        {
                                                            reply.onUseModel(
                                                                row.turnId,
                                                                suggestion.providerId,
                                                                suggestion.modelId,
                                                            )
                                                        },
                                                        role = KitButtonRole.Secondary,
                                                        modifier = Modifier.testTag("error-action-use-model"),
                                                    )
                                            ErrorFix.ChooseModel ->
                                                reply.chooseModelLabel?.let {
                                                    KitButton(
                                                        it,
                                                        reply.onChooseModel,
                                                        role = KitButtonRole.Secondary,
                                                        modifier =
                                                            Modifier.testTag("error-action-choose-model"),
                                                    )
                                                }
                                            ErrorFix.Compact ->
                                                reply.compactLabel?.let {
                                                    KitButton(
                                                        it,
                                                        reply.onCompact,
                                                        role = KitButtonRole.Secondary,
                                                        modifier = Modifier.testTag("error-action-compact"),
                                                    )
                                                }
                                            ErrorFix.OpenProviders ->
                                                if (!showSignIn)
                                                    reply.openProvidersLabel?.let {
                                                        KitButton(
                                                            it,
                                                            reply.onOpenProviders,
                                                            role = KitButtonRole.Secondary,
                                                            modifier =
                                                                Modifier.testTag(
                                                                    "error-action-providers"
                                                                ),
                                                        )
                                                    }
                                            ErrorFix.OpenAccount ->
                                                reply.openAccountLabel?.let {
                                                    KitButton(
                                                        it,
                                                        reply.onOpenProviders,
                                                        role = KitButtonRole.Secondary,
                                                        modifier =
                                                            Modifier.testTag("error-action-account"),
                                                    )
                                                }
                                        }
                                    }
                            }
                            // B-AGENT-024: a lost connection's next step is a visible Continue (the
                            // turn picks up there).
                            val continueLabel = reply?.continueLabel
                            if (
                                setUp == null &&
                                    kind == ReplyFailureKind.ConnectionLost &&
                                    !continueLabel.isNullOrBlank()
                            )
                                KitButton(
                                    label = continueLabel,
                                    onClick = { reply.onContinue(continueLabel) },
                                    role = KitButtonRole.Secondary,
                                )
                            if (reason.isNotBlank() && setUp == null)
                                KitDetailsFold(
                                    label = listWords.details,
                                    copyLabel = listWords.copyDetails,
                                    notes = listOf(mask(reason)),
                                )
                        }
                    }
                } else {
                    // F: message_view.dart:611-621: a finished turn names the files it changed, each one tap from
                    // its contents, and the project's Files under them, between the reply and its footer.
                    // W4: a turn that ended on the person's own refusal is not a stopped turn (Flutter `declinedEnd`).
                    val stopped = row.stopped && !StoppedSteps.endedOnRefusal(facts.work[row.turnId])
                    if (!stopped)
                        TurnChangedFiles(facts.results[row.turnId]?.files.orEmpty(), onOpenFile, onAllFiles)
                    MessageDetailsTip(eligible = teachDetails) {
                        KitReplyFooter(
                            copyLabel = stringResource(Res.string.kitCopy),
                            moreLabel = stringResource(Res.string.kitTurnMore),
                            onCopy = onCopyReply?.let { copy -> { copy(row) } },
                            // No orphan More on a synthetic footer before the first real reply.
                            onMore =
                                if (onCopyReply != null || row.failed || row.stopped)
                                    onOpenMenu?.let { menu -> { menu(row.key) } }
                                else null,
                            // NV: Flutter's More reaches the window edge (kit_turn.dart:680-690 translate); the 48 dp
                            // target keeps 4 dp from it (LAYOUT-RULES edge-tap), a shift a person does not see.
                            modifier = Modifier.padding(end = FOOTER_EDGE_INSET_DP.dp).testTag("chat-turn-footer"),
                        ) {
                            // KitReplyFooter's line already sits S4 in from the list edge, exactly where the reply's text
                            // starts (KitReplyBlock pads S4 itself), so the note takes no offset: a -S4 offset put it at
                            // x = 0, outside the page margin (S24StoppedFooterTest measured the reply's clickable box,
                            // which starts at 0, instead of its text, and so pinned the bug).
                            val note = Modifier.testTag("chat-turn-footer-note")
                            val endNote = facts.reading.endNotes[row.turnId]
                            if (row.failed) KitReplyNote(words.footer(true), note)
                            else if (stopped && endNote?.isServerStop != true) KitReplyNote(words.stoppedFooter(), note)
                            endNote?.let { n ->
                                TurnEndNote(
                                    n,
                                    reply?.let { r -> r.continueLabel?.let { w -> { r.onContinue(w) } } },
                                    onSendAgain,
                                )
                            }
                            // D1 (owner 2026-11-14): every finished reply ends with its quiet line: clock time · how
                            // long · model (only when not the one in use now) · tokens · cost, each only when known.
                            facts.reading.metadata[row.turnId]
                                ?.let { replyFooterWords(it) }
                                ?.takeIf { it.isNotEmpty() }
                                ?.let { KitReplyMeta(it) }
                        }
                    }
                }
                // N-45: a turn with a work line already says what it edited and ran there (its steps carry each
                // file's counts and each command); a second "Edited … · ran …" row under the footer repeated it.
                val result = facts.results[row.turnId]
                if (result != null && folds != null && facts.work[row.turnId] == null)
                    TurnResultLine(row.turnId, result, folds)
            }
    }
}

/**
 * The extra gap above [row] that makes its turn read as Flutter's `KitTurn` column (kit_turn.dart:484-516): 16 dp
 * from the prompt to the turn's body (`add(blocks, tokens.space4)`), and its blocks 12 dp apart (`space3`, C-025).
 * Each row already brings its own inset (a reply block 4 dp above and below, the work line's own line box), so this
 * adds only the rest. [older] is the row just above it in reading order; another turn adds nothing.
 */
internal fun turnGap(row: TranscriptRow, older: TranscriptRow?): Int {
    if (older == null || older.turnId != row.turnId) return 0
    return when {
        // F: kit_turn.dart:516 space4 between the prompt and the body; the rows' own insets give space1.
        older is PromptRow && (row is WorkLineRow || row is BlockRow) -> Space.S3
        // F: kit_turn.dart:498 space3 between blocks; the work line and the reply block bring space1.
        older is WorkLineRow && row is BlockRow && !row.continued -> Space.S2
        // F: kit_markdown.dart kitMdGap 12 between paragraphs (C-025); the two reply blocks bring space2.
        older is BlockRow && row is BlockRow && !row.continued -> Space.S1
        else -> 0
    }
}

/** NV: LAYOUT-RULES.md edge-tap: a target keeps 4 dp from the window edge. */
private const val FOOTER_EDGE_INSET_DP = 4 // NV: LAYOUT-RULES.md edge-tap minimum

/**
 * Plan 09 R13 column (as the kit's reply block): 700 dp cap, the row gap only where the block does
 * not continue.
 */
private fun Modifier.replyColumn(continued: Boolean, continues: Boolean): Modifier =
    fillMaxWidth()
        .widthIn(max = 700.dp)
        .padding(
            start = Space.S4.dp,
            end = Space.S4.dp,
            top = if (continued) 0.dp else Space.S1.dp,
            bottom = if (continues) 0.dp else Space.S1.dp,
        )

/**
 * P12-CHAT-FEEL: a reply block drawn as Markdown (Flutter `KitMarkdown`): bold, links (through the
 * link policy), inline code, lists, tables and pictures, revealed word by word while it streams
 * (the same registry as the plain block). Code: a streaming fence keeps the stream-tuned block
 * (fence lines hidden); a settled one gets Copy and sideways scroll, coloured when it is one row,
 * seamless across STREAM-FIX chunks otherwise.
 */
@Composable
private fun RichBlock(
    row: BlockRow,
    reveal: RevealRegistry,
    motion: KitMotionLevel,
    reply: ReplyActions,
    copyCode: (String, (String) -> String) -> Unit,
) {
    val column = Modifier.replyColumn(row.continued, row.continues)
    if (row.kind == RowKind.Code) {
        val body = remember(row.text) { FenceText.body(row.text) }
        val mask = rememberKitMask()
        val streamed = row.origin == Origin.Streamed
        // A streamed fence keeps the stream-tuned block until its words have all landed and faded:
        // the settled view
        // takes over only then, so its reveal never stops half way (and never holds up the blocks
        // after it).
        val revealing =
            streamed &&
                reveal
                    .stateFor(row.key, row.key.substringBeforeLast(':'), row.live, body.length)
                    .active
        when {
            row.live || row.open || revealing ->
                KitReplyBlock(
                    text = body,
                    kind = KitBlockKind.Code,
                    live = row.live,
                    streamed = streamed,
                    revealKey = row.key,
                    revealGroup = row.key.substringBeforeLast(':'),
                    registry = reveal,
                    motion = motion,
                    continued = row.continued,
                    continues = row.continues,
                )
            !row.continued && !row.continues ->
                Box(column) {
                    KitCodeBlock(
                        source = body,
                        kind = KitCodeKind.Code,
                        language = CodeLanguage.of(FenceText.info(row.text)),
                        maxLines = Int.MAX_VALUE,
                        copyLabel = reply.copyLabel,
                        onCopy = { copyCode(row.key, mask) },
                        copiedLabel = reply.copiedLabel,
                    )
                }
            else ->
                KitCodeChunk(
                    source = body,
                    continued = row.continued,
                    continues = row.continues,
                    modifier = column,
                    copyLabel = reply.copyLabel,
                    onCopy = { copyCode(row.key, mask) },
                    copiedLabel = reply.copiedLabel,
                )
        }
        return
    }
    val blocks =
        remember(row.key, row.text, row.live) { KitMdSource.blocks(row.key, row.text, row.live) }
    // B-CHAT-064: the paths this block shows are checked once per settled text; confirmed ones
    // become tappable.
    val paths = remember(blocks) { pathsOf(blocks) }
    if (paths.isNotEmpty() && !row.live) LaunchedEffect(paths) { reply.checkPaths(paths) }
    val streamed = row.origin == Origin.Streamed
    val md =
        remember(reveal, motion, streamed, row.key) {
            KitMdReveal(reveal, row.key.substringBeforeLast(':'), motion, streamed)
        }
    // A growing row can re-parse into fewer blocks; the reveal of a block that went away is
    // finished, not orphaned.
    val drawn = remember(row.key) { IntArray(1) }
    SideEffect {
        for (i in blocks.size until drawn[0]) md.settle("${row.key}#$i")
        drawn[0] = blocks.size
    }
    Column(column) {
        val last = blocks.lastIndex
        blocks.forEachIndexed { i, block ->
            androidx.compose.runtime.key(block.key) {
                val tail = row.live && i == last
                KitMarkdownBlock(
                    block,
                    reply.markdown,
                    live = tail,
                    caret = tail && block.takesCaret,
                    reveal = md,
                )
            }
        }
    }
}

private fun RowKind.toKit(): KitBlockKind =
    when (this) {
        RowKind.Heading -> KitBlockKind.Heading
        RowKind.Quote -> KitBlockKind.Quote
        RowKind.ListItem -> KitBlockKind.ListItem
        RowKind.Code -> KitBlockKind.Code
        else -> KitBlockKind.Prose
    }

/** The file paths (B-CHAT-064 path spans) the blocks show, in order, each once. */
internal fun pathsOf(blocks: List<KitMdBlock>): List<String> {
    val out = LinkedHashSet<String>()
    fun scan(inlines: io.github.eslamasabry.oc.ui.kitchat.markdown.KitInlines) {
        for (span in inlines.spans) when (val kind = span.kind) {
            is io.github.eslamasabry.oc.ui.kitchat.markdown.KitSpanKind.Path -> out += kind.path
            is io.github.eslamasabry.oc.ui.kitchat.markdown.KitSpanKind.Picture -> out += kind.path
            else -> Unit
        }
    }
    fun walk(b: KitMdBlock) {
        when (b) {
            is KitMdBlock.Prose -> scan(b.inlines)
            is KitMdBlock.Heading -> scan(b.inlines)
            is KitMdBlock.ListItem -> scan(b.inlines)
            is KitMdBlock.Quote -> b.children.forEach(::walk)
            is KitMdBlock.Table -> {
                b.header.forEach(::scan)
                b.rows.forEach { r -> r.forEach(::scan) }
            }
            is KitMdBlock.Pictures -> b.pictures.mapNotNullTo(out) { it.path }
            else -> Unit
        }
    }
    blocks.forEach(::walk)
    return out.toList()
}


/** The person's prompt with what it carried: pictures as thumbnails under the words, other files as chips. */
@Composable
private fun SentPromptBubble(row: PromptRow) {
    val sent =
        rememberKitSentFiles(
            remember(row.files) { row.files.map { KitSentFile(it.name, it.mime, it.url) } }
        )
    KitPromptBubble(
        CardAnswerDisplay.text(row.text),
        photos = sent.photos,
        attachments = sent.attachments,
    )
}
