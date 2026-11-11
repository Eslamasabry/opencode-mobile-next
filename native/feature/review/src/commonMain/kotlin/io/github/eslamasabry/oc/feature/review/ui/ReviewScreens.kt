package io.github.eslamasabry.oc.feature.review.ui

import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.eslamasabry.oc.ui.kit.action.rememberKitCopyAction
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipKind
import io.github.eslamasabry.oc.ui.kit.chip.KitChipTone
import io.github.eslamasabry.oc.ui.kit.chip.KitChipWrap
import io.github.eslamasabry.oc.ui.words.res.kitDiffHideUnchanged
import io.github.eslamasabry.oc.ui.words.res.reviewCopyFile
import io.github.eslamasabry.oc.ui.words.res.reviewCopyPatch
import io.github.eslamasabry.oc.ui.words.res.reviewExpandContext
import io.github.eslamasabry.oc.ui.words.res.s40iCopied
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import org.jetbrains.compose.resources.StringResource
import io.github.eslamasabry.oc.ui.words.res.reviewWorkspaceBackToChat
import io.github.eslamasabry.oc.ui.words.res.reviewWorkspaceAllViewedTitle
import io.github.eslamasabry.oc.ui.words.res.readerUiBranchScopeHint
import io.github.eslamasabry.oc.ui.words.res.readerUiWorkingScopeHint
import io.github.eslamasabry.oc.ui.words.res.readerUiSessionScopeHint
import io.github.eslamasabry.oc.ui.words.res.readerUiBranch
import io.github.eslamasabry.oc.ui.words.res.kitDiffViewed
import io.github.eslamasabry.oc.feature.review.presentation.ReviewScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.model.FileDiff
import io.github.eslamasabry.oc.feature.review.presentation.ChangeRowUi
import io.github.eslamasabry.oc.feature.review.presentation.ChangesActions
import io.github.eslamasabry.oc.feature.review.presentation.ChangesUi
import io.github.eslamasabry.oc.feature.review.presentation.CommentLines
import io.github.eslamasabry.oc.feature.review.presentation.CommentSide
import io.github.eslamasabry.oc.feature.review.presentation.CommentStatus
import io.github.eslamasabry.oc.feature.review.presentation.FileDiffActions
import io.github.eslamasabry.oc.feature.review.presentation.FileDiffUi
import io.github.eslamasabry.oc.feature.review.presentation.FileNotice
import io.github.eslamasabry.oc.feature.review.presentation.GuideUi
import io.github.eslamasabry.oc.feature.review.presentation.LineComment
import io.github.eslamasabry.oc.feature.review.presentation.ReviewGuide
import io.github.eslamasabry.oc.ui.kit.motion.KitEntrance
import io.github.eslamasabry.oc.ui.kit.row.KitExpandRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowState
import io.github.eslamasabry.oc.ui.kit.row.KitRowStateTone
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import io.github.eslamasabry.oc.ui.words.res.reviewCommentAdd
import io.github.eslamasabry.oc.ui.words.res.reviewCommentDraft
import io.github.eslamasabry.oc.ui.words.res.reviewCommentField
import io.github.eslamasabry.oc.ui.words.res.reviewCommentFixed
import io.github.eslamasabry.oc.ui.words.res.reviewCommentHint
import io.github.eslamasabry.oc.ui.words.res.reviewCommentLine
import io.github.eslamasabry.oc.ui.words.res.reviewCommentLines
import io.github.eslamasabry.oc.ui.words.res.reviewCommentNotPlaced
import io.github.eslamasabry.oc.ui.words.res.reviewCommentRemove
import io.github.eslamasabry.oc.ui.words.res.reviewCommentRemovedLine
import io.github.eslamasabry.oc.ui.words.res.reviewCommentRemovedLines
import io.github.eslamasabry.oc.ui.words.res.reviewCommentSending
import io.github.eslamasabry.oc.ui.words.res.reviewCommentTitleLine
import io.github.eslamasabry.oc.ui.words.res.reviewCommentTitleLines
import io.github.eslamasabry.oc.ui.words.res.reviewCommentWaiting
import io.github.eslamasabry.oc.ui.words.res.reviewCommentsLabel
import io.github.eslamasabry.oc.ui.words.res.reviewCommentsSendFailed
import io.github.eslamasabry.oc.ui.words.res.reviewCommentsWaitingNotice
import io.github.eslamasabry.oc.ui.words.res.reviewGuideAlsoChanged
import io.github.eslamasabry.oc.ui.words.res.reviewGuideBusy
import io.github.eslamasabry.oc.ui.words.res.reviewGuideFallback
import io.github.eslamasabry.oc.ui.words.res.reviewGuideMaking
import io.github.eslamasabry.oc.ui.words.res.reviewGuided
import io.github.eslamasabry.oc.ui.words.res.reviewGuidedSupporting
import io.github.eslamasabry.oc.ui.words.res.reviewSendComments
import io.github.eslamasabry.oc.feature.review.presentation.ReviewLoad
import io.github.eslamasabry.oc.feature.review.presentation.StagedActions
import io.github.eslamasabry.oc.feature.review.presentation.StagedConfirm
import io.github.eslamasabry.oc.feature.review.presentation.StagedPhase
import io.github.eslamasabry.oc.feature.review.presentation.StagedUi
import io.github.eslamasabry.oc.feature.review.presentation.UndoAllUi
import io.github.eslamasabry.oc.feature.review.presentation.UndoBlock
import io.github.eslamasabry.oc.feature.review.presentation.UndoFromHereActions
import io.github.eslamasabry.oc.feature.review.presentation.UndoFromHereUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitInputDialog
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kitchat.diff.DiffFile
import io.github.eslamasabry.oc.ui.kitchat.diff.DiffFileStatus
import io.github.eslamasabry.oc.ui.kitchat.diff.DiffModel
import io.github.eslamasabry.oc.ui.kitchat.diff.DiffRow
import io.github.eslamasabry.oc.ui.kitchat.diff.KitDiffView
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.chatUiChanges
import io.github.eslamasabry.oc.ui.words.res.commandUntitledChat
import io.github.eslamasabry.oc.ui.words.res.defaultReviewScopeNotice
import io.github.eslamasabry.oc.ui.words.res.kitCopyDetails
import io.github.eslamasabry.oc.ui.words.res.kitDetails
import io.github.eslamasabry.oc.ui.words.res.kitDiffBinary
import io.github.eslamasabry.oc.ui.words.res.kitDiffFoldBetween
import io.github.eslamasabry.oc.ui.words.res.kitDiffLines
import io.github.eslamasabry.oc.ui.words.res.kitDiffLoadFailed
import io.github.eslamasabry.oc.ui.words.res.kitDiffNoChanges
import io.github.eslamasabry.oc.ui.words.res.kitDiffTooBig
import io.github.eslamasabry.oc.ui.words.res.kitRequestSendBack
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.kitTryAgain
import io.github.eslamasabry.oc.ui.words.res.readerUiWorkingTree
import io.github.eslamasabry.oc.ui.words.res.revertAttachmentPrompt
import io.github.eslamasabry.oc.ui.words.res.revertBusy
import io.github.eslamasabry.oc.ui.words.res.revertPreviewUnavailable
import io.github.eslamasabry.oc.ui.words.res.revertPromptLoading
import io.github.eslamasabry.oc.ui.words.res.revertPromptUnavailable
import io.github.eslamasabry.oc.ui.words.res.revertReviewChanged
import io.github.eslamasabry.oc.ui.words.res.revertReviewLatest
import io.github.eslamasabry.oc.ui.words.res.reviewFileGone
import io.github.eslamasabry.oc.ui.words.res.reviewIncomplete
import io.github.eslamasabry.oc.ui.words.res.reviewLooksGood
import io.github.eslamasabry.oc.ui.words.res.reviewNoChangesBody
import io.github.eslamasabry.oc.ui.words.res.reviewNoDiff
import io.github.eslamasabry.oc.ui.words.res.reviewOneFileUnavailable
import io.github.eslamasabry.oc.ui.words.res.reviewRevertBackAction
import io.github.eslamasabry.oc.ui.words.res.reviewRevertFailed
import io.github.eslamasabry.oc.ui.words.res.reviewRevertFileLines
import io.github.eslamasabry.oc.ui.words.res.reviewRevertFilesLabel
import io.github.eslamasabry.oc.ui.words.res.reviewRevertFilesToggle
import io.github.eslamasabry.oc.ui.words.res.reviewRevertFilesToggleHint
import io.github.eslamasabry.oc.ui.words.res.reviewRevertKeepConfirmAction
import io.github.eslamasabry.oc.ui.words.res.reviewRevertKeepConfirmBody
import io.github.eslamasabry.oc.ui.words.res.reviewRevertKeepConfirmTitle
import io.github.eslamasabry.oc.ui.words.res.reviewRevertKeepConsequenceCount
import io.github.eslamasabry.oc.ui.words.res.reviewRevertKeepConsequenceCount__eq0
import io.github.eslamasabry.oc.ui.words.res.reviewRevertKeepConsequenceFiles
import io.github.eslamasabry.oc.ui.words.res.reviewRevertKeepConsequenceMessages
import io.github.eslamasabry.oc.ui.words.res.reviewRevertKeepTitle
import io.github.eslamasabry.oc.ui.words.res.reviewRevertKeptBody
import io.github.eslamasabry.oc.ui.words.res.reviewRevertKeptTitle
import io.github.eslamasabry.oc.ui.words.res.reviewRevertNoFiles
import io.github.eslamasabry.oc.ui.words.res.reviewRevertNoneBody
import io.github.eslamasabry.oc.ui.words.res.reviewRevertNoneTitle
import io.github.eslamasabry.oc.ui.words.res.reviewRevertPromptLabel
import io.github.eslamasabry.oc.ui.words.res.reviewRevertRestoreConfirmBody
import io.github.eslamasabry.oc.ui.words.res.reviewRevertPutBackQuestion
import io.github.eslamasabry.oc.ui.words.res.reviewRevertRestoreConsequenceFiles
import io.github.eslamasabry.oc.ui.words.res.reviewRevertRestoreConsequenceMessages
import io.github.eslamasabry.oc.ui.words.res.reviewRevertRestoreConsequenceUnknownFiles
import io.github.eslamasabry.oc.ui.words.res.reviewRevertRestoreTitle
import io.github.eslamasabry.oc.ui.words.res.reviewRevertRestoredBody
import io.github.eslamasabry.oc.ui.words.res.reviewRevertRestoredTitle
import io.github.eslamasabry.oc.ui.words.res.reviewRevertScreenIntro
import io.github.eslamasabry.oc.ui.words.res.reviewRevertScreenIntroCount
import io.github.eslamasabry.oc.ui.words.res.reviewRevertScreenIntroCount__eq0
import io.github.eslamasabry.oc.ui.words.res.reviewRevertScreenTitle
import io.github.eslamasabry.oc.ui.words.res.reviewRevertSheetAction
import io.github.eslamasabry.oc.ui.words.res.reviewRevertSheetBody
import io.github.eslamasabry.oc.ui.words.res.reviewRevertSheetTitle
import io.github.eslamasabry.oc.ui.words.res.reviewRevertStageFailed
import io.github.eslamasabry.oc.ui.words.res.reviewRevertStaleTitle
import io.github.eslamasabry.oc.ui.words.res.reviewSendBackFailed
import io.github.eslamasabry.oc.ui.words.res.reviewSendBackWithNote
import io.github.eslamasabry.oc.ui.words.res.reviewWorkspaceSlowTitle
import io.github.eslamasabry.oc.ui.words.res.reviewWorkspaceSlowBody
import io.github.eslamasabry.oc.ui.words.res.reviewAlreadyApplied
import io.github.eslamasabry.oc.ui.words.res.reviewWorkspaceAddFileToPrompt
import io.github.eslamasabry.oc.ui.words.res.readerUiReferenceAdded
import io.github.eslamasabry.oc.ui.words.res.readerUiReferenceDuplicate
import io.github.eslamasabry.oc.ui.words.res.filesMentionFailed
import io.github.eslamasabry.oc.ui.words.res.reviewSummary
import io.github.eslamasabry.oc.ui.words.res.reviewToolEvidence
import io.github.eslamasabry.oc.ui.words.res.reviewTurnTitle
import io.github.eslamasabry.oc.ui.words.res.reviewUndoAll
import io.github.eslamasabry.oc.ui.words.res.reviewUndoBody
import io.github.eslamasabry.oc.ui.words.res.reviewUndoTitle
import io.github.eslamasabry.oc.ui.words.res.reviewUndoUnavailable
import io.github.eslamasabry.oc.ui.words.res.reviewUndoUnknown
import io.github.eslamasabry.oc.ui.words.res.reviewWaiting
import io.github.eslamasabry.oc.ui.words.res.reviewWorkspaceCommentEmpty
import io.github.eslamasabry.oc.ui.words.res.reviewWorkspaceCommentLabel
import io.github.eslamasabry.oc.ui.words.res.undoFromHereBody
import io.github.eslamasabry.oc.ui.words.res.undoFromHereBodyUnknown
import io.github.eslamasabry.oc.ui.words.res.undoFromHereBody__eq0
import io.github.eslamasabry.oc.ui.words.res.undoFromHereFilesLabel
import io.github.eslamasabry.oc.ui.words.res.undoFromHereNoEdits
import io.github.eslamasabry.oc.ui.words.res.undoFromHereNowAction
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

// The review pages arrange kit parts only (owner rule "kit only"). Every string is :ui:words;
// server text (paths,
// prompts) is shown as is; failures are a plain line with Details, never raw text.

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    KitTopBar(
        title = title,
        exit = KitTopBarExit.Back,
        exitLabel = stringResource(Res.string.kitTopBarBack),
        onExit = onBack,
    )
}

/** The page body: a scrolling column on the reading rails. */
@Composable
private fun Page(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = LocalKitTokens.current.gutter.dp, vertical = Space.S3.dp),
        verticalArrangement = Arrangement.spacedBy(Space.S4.dp),
    ) {
        content()
    }
}

@Composable
private fun FailedNotice(
    message: String,
    failure: Failure,
    retry: (() -> Unit)? = null,
) {
    KitErrorNotice(
        message,
        failure = failure,
        detailsLabel = stringResource(Res.string.kitDetails),
        copyLabel = stringResource(Res.string.kitCopyDetails),
        retry = retry?.let { KitLineAction(stringResource(Res.string.kitTryAgain), it) },
    )
}

private fun lines(added: Int?, removed: Int?): Pair<Int, Int>? =
    if (added != null && removed != null) added to removed else null

/**
 * "Changes" of a turn or the conversation (Flutter `review_workspace.dart` question +
 * `chat_files.dart` rows).
 */
@Composable
public fun ChangesScreen(state: ChangesUi, actions: ChangesActions) {
    // The page and its question share one modal layer (the chat's arrangement).
    KitSheetHost(Modifier.fillMaxSize()) { ChangesContent(state, actions) }
}

/** After [SLOW_READ] of waiting: "Still reading the changes", why, and Try again (F `review-slow`). */
@Composable
private fun SlowRead(retry: () -> Unit) {
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(SLOW_READ)
        slow = true
    }
    if (slow)
        KitStateView(
            KitStateKind.Working,
            stringResource(Res.string.reviewWorkspaceSlowTitle),
            Modifier.testTag("review-slow"),
            body = stringResource(Res.string.reviewWorkspaceSlowBody),
            icon = KitIcons.review,
            tertiary = listOf(KitAction(stringResource(Res.string.kitTryAgain), retry)),
        )
}

private val SLOW_READ = kotlin.time.Duration.parse("8s") // F: KitSince.isSlow, lib/ui/kit/kit_since.dart

@Composable
private fun ChangesContent(state: ChangesUi, actions: ChangesActions) {
    val busyReason = stringResource(Res.string.revertBusy)
    KitScreen(
        topBar = {
            TopBar(
                stringResource(
                    if (state.turn) Res.string.reviewTurnTitle else Res.string.chatUiChanges
                ),
                actions::back,
            )
        },
        width = KitScreenWidth.Reading,
        loading = state.load == ReviewLoad.Loading || state.working || state.updating,
        bottom =
            if (state.load == ReviewLoad.Ready && state.files.isNotEmpty()) {
                {
                    KitActionBlock(
                        modifier =
                            Modifier.padding(horizontal = Space.S4.dp, vertical = Space.S2.dp),
                        primary =
                            KitAction(stringResource(Res.string.reviewLooksGood), actions::keepAll),
                        secondary =
                            when (state.undoAll) {
                                UndoAllUi.Offered ->
                                    KitAction(
                                        stringResource(Res.string.reviewUndoAll),
                                        actions::undoAll,
                                        enabled =
                                            if (state.busy) KitEnabled.Disabled(busyReason)
                                            else KitEnabled.Enabled,
                                        working = state.working,
                                    )
                                UndoAllUi.Waiting ->
                                    KitAction(
                                        stringResource(Res.string.reviewRevertScreenTitle),
                                        actions::openStaged,
                                    )
                                else -> null
                            },
                        // Flutter: "Send back" is a note in the chat's box, offered beside Looks
                        // good / Undo all.
                        tertiary =
                            buildList {
                                if (state.drafts > 0)
                                    add(
                                        KitAction(
                                            pluralStringResource(
                                                Res.plurals.reviewSendComments,
                                                state.drafts,
                                                state.drafts,
                                            ),
                                            actions::sendComments,
                                            working = state.sendingComments,
                                        )
                                    )
                                if (state.canSendBack)
                                    add(KitAction(stringResource(Res.string.reviewSendBackWithNote), actions::sendBack))
                            },
                    )
                }
            } else null,
        edges = KitScreenEdges.Bleed,
    ) {
        when (val load = state.load) {
            // Flutter `review-slow`: after a few seconds the wait is explained, with Try again.
            ReviewLoad.Loading -> if (!state.updating) SlowRead(actions::retry)
            is ReviewLoad.Failed ->
                KitStateView(
                    KitStateKind.Error,
                    stringResource(Res.string.kitDiffLoadFailed),
                    failure = load.failure,
                    onRetry = actions::retry,
                    retryLabel = stringResource(Res.string.kitTryAgain),
                )
            ReviewLoad.Ready ->
                if (state.files.isEmpty()) {
                    if (state.canPickScope) {
                        // An empty view keeps its picker, so another view is one tap away (Flutter keeps it in the
                        // header), and says what this view covers.
                        Page {
                            ScopePicker(state, actions)
                            KitText(
                                stringResource(scopeHint(state.scope)),
                                Modifier.testTag("review-scope-hint"),
                                role = TypeRole.Body,
                                tone = TextTone.Secondary,
                            )
                            KitStateView(
                                KitStateKind.Empty,
                                stringResource(Res.string.kitDiffNoChanges),
                                body = stringResource(Res.string.reviewNoChangesBody),
                            )
                        }
                    } else
                        KitStateView(
                            KitStateKind.Empty,
                            stringResource(Res.string.kitDiffNoChanges),
                            body = stringResource(Res.string.reviewNoChangesBody),
                        )
                } else {
                    Page {
                        val sum = lines(state.added, state.removed)
                        if (sum != null) {
                            KitText(
                                pluralStringResource(
                                    Res.plurals.reviewSummary,
                                    state.files.size,
                                    state.files.size,
                                    sum.first,
                                    sum.second,
                                ),
                                Modifier.testTag("review-summary"),
                                role = TypeRole.Title,
                            )
                        }
                        // Flutter `reviewAlreadyApplied`: says what "Undo all" undoes.
                        if (state.undoAll == UndoAllUi.Offered || state.undoAll == UndoAllUi.Waiting)
                            KitText(
                                stringResource(Res.string.reviewAlreadyApplied),
                                Modifier.testTag("review-already-applied"),
                                role = TypeRole.Body,
                                tone = TextTone.Secondary,
                            )
                        if (state.canPickScope) ScopePicker(state, actions)
                        if (state.scope != ReviewScope.Conversation) {
                            KitNotice(
                                stringResource(
                                    Res.string.defaultReviewScopeNotice,
                                    stringResource(scopeLabel(state.scope, state.turn)),
                                )
                            )
                        } else {
                            KitText(
                                stringResource(Res.string.reviewToolEvidence),
                                role = TypeRole.Body,
                                tone = TextTone.Secondary,
                            )
                        }
                        // Flutter `review-all-viewed`: every file seen with comments written, the way back is offered.
                        if (state.allViewed && state.drafts > 0)
                            KitNotice(
                                stringResource(Res.string.reviewWorkspaceAllViewedTitle),
                                Modifier.testTag("review-all-viewed"),
                                tone = StatusTone.Ok,
                                actions =
                                    listOf(
                                        KitLineAction(
                                            stringResource(Res.string.reviewWorkspaceBackToChat),
                                            actions::back,
                                        )
                                    ),
                            )
                        if (state.undoAll == UndoAllUi.Waiting)
                            KitNotice(stringResource(Res.string.reviewWaiting))
                        if (!state.complete) KitNotice(stringResource(Res.string.reviewIncomplete))
                        state.actionFailure?.let {
                            FailedNotice(stringResource(Res.string.reviewRevertStageFailed), it)
                        }
                        state.sendBackFailure?.let {
                            FailedNotice(stringResource(Res.string.reviewSendBackFailed), it)
                        }
                        state.commentsFailure?.let {
                            FailedNotice(stringResource(Res.string.reviewCommentsSendFailed), it)
                        }
                        if (state.waiting > 0)
                            KitNotice(
                                pluralStringResource(
                                    Res.plurals.reviewCommentsWaitingNotice,
                                    state.waiting,
                                    state.waiting,
                                )
                            )
                        if (state.canGuide)
                            KitSwitchRow(
                                title = stringResource(Res.string.reviewGuided),
                                checked = state.guide != GuideUi.Off,
                                supporting = stringResource(Res.string.reviewGuidedSupporting),
                                modifier = Modifier.testTag("review-guided"),
                                onCheckedChange = actions::guided,
                            )
                        when (val guide = state.guide) {
                            is GuideUi.Ready ->
                                KitEntrance(key = guide.guide) { Guide(guide.guide, state.files, actions) }
                            else -> {
                                when (guide) {
                                    GuideUi.Making -> KitNotice(stringResource(Res.string.reviewGuideMaking))
                                    is GuideUi.Fallback ->
                                        KitNotice(
                                            stringResource(
                                                if (guide.failure is Failure.Busy) Res.string.reviewGuideBusy
                                                else Res.string.reviewGuideFallback
                                            )
                                        )
                                    else -> Unit
                                }
                                KitRowGroup(Modifier.testTag("review-files")) {
                                    state.files.forEach { row -> ChangeRow(row, actions) }
                                }
                            }
                        }
                        KitText(
                            stringResource(
                                when (state.undoAll) {
                                    UndoAllUi.Unavailable -> Res.string.reviewUndoUnavailable
                                    UndoAllUi.Unknown -> Res.string.reviewUndoUnknown
                                    else -> Res.string.reviewOneFileUnavailable
                                }
                            ),
                            role = TypeRole.Body,
                            tone = TextTone.Secondary,
                        )
                    }
                }
        }
    }
    val emptyNote = stringResource(Res.string.reviewWorkspaceCommentEmpty)
    KitInputDialog(
        visible = state.noting,
        title = stringResource(Res.string.reviewSendBackWithNote),
        fieldLabel = stringResource(Res.string.reviewWorkspaceCommentLabel),
        confirmLabel = stringResource(Res.string.kitRequestSendBack),
        validate = { if (it.isBlank()) emptyNote else null },
        onResult = actions::sendBackNote,
    )
    KitConfirm(
        if (state.confirming) {
            KitConfirmSpec(
                title =
                    pluralStringResource(
                        Res.plurals.reviewUndoTitle,
                        state.files.size,
                        state.files.size,
                    ),
                confirmLabel = stringResource(Res.string.reviewUndoAll),
                kind = KitConfirmKind.Destructive,
                body = stringResource(Res.string.reviewUndoBody),
            )
        } else null,
        working = state.working,
        failed = state.actionFailure != null,
        onConfirm = actions::confirmUndoAll,
        onCancel = actions::dismissConfirm,
    )
}

/**
 * The Guided view (B-155): the agent's sections, core first, each opening with its one line of what and why; the glue
 * files fold under "Also changed".
 */
@Composable
private fun Guide(guide: ReviewGuide, files: List<ChangeRowUi>, actions: ChangesActions) {
    val byPath = remember(files) { files.associateBy { it.path } }
    Column(Modifier.testTag("review-guide"), verticalArrangement = Arrangement.spacedBy(Space.S4.dp)) {
        guide.sections.forEach { section ->
            Column(verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                KitText(section.title, role = TypeRole.Title)
                if (section.why.isNotEmpty()) KitText(section.why, role = TypeRole.Body, tone = TextTone.Secondary)
                KitRowGroup {
                    section.files.mapNotNull(byPath::get).forEach { row -> ChangeRow(row, actions) }
                }
            }
        }
        val also = guide.alsoChanged.mapNotNull(byPath::get)
        if (also.isNotEmpty()) {
            var open by remember(guide) { mutableStateOf(false) }
            KitExpandRow(
                title = stringResource(Res.string.reviewGuideAlsoChanged),
                expanded = open,
                supporting = also.size.toString(),
                onExpandedChange = { open = it },
            ) {
                KitRowGroup { also.forEach { row -> ChangeRow(row, actions) } }
            }
        }
    }
}

@Composable
private fun ChangeRow(row: ChangeRowUi, actions: ChangesActions) {
    val counts =
        lines(row.added, row.removed)?.let { (a, r) ->
            stringResource(Res.string.reviewRevertFileLines, a, r)
        }
    KitRow(
        title = row.name,
        supporting = listOfNotNull(row.folder, counts).joinToString(" · ").ifEmpty { null },
        titleIsFileName = true,
        trailing =
            when {
                row.viewed -> KitRowTrailing.Value(stringResource(Res.string.kitDiffViewed), chevron = row.hasDiff)
                row.hasDiff -> KitRowTrailing.Chevron
                else -> KitRowTrailing.None
            },
        modifier = Modifier.testTag("review-file-${row.path}"),
        onClick = if (row.hasDiff) ({ actions.openFile(row.path) }) else null,
    )
}

/** The change-scope chips (Flutter `_ReviewScopePicker`, "Changes to show"), in the server's offered order. */
@Composable
private fun ScopePicker(state: ChangesUi, actions: ChangesActions) {
    KitChipWrap(Modifier.testTag("review-scope-picker")) {
        state.scopes.forEach { scope ->
            KitChip(
                label = stringResource(scopeLabel(scope, state.turn)),
                kind = KitChipKind.Action,
                tone = if (state.scope == scope) KitChipTone.Active else KitChipTone.Neutral,
                selected = state.scope == scope,
                modifier = Modifier.testTag("review-scope-${scope.name}"),
                onClick = { actions.chooseScope(scope) },
            )
        }
    }
}

private fun scopeLabel(scope: ReviewScope, turn: Boolean): StringResource =
    when (scope) {
        ReviewScope.Conversation -> if (turn) Res.string.reviewTurnTitle else Res.string.chatUiChanges
        ReviewScope.WorkingTree -> Res.string.readerUiWorkingTree
        ReviewScope.Branch -> Res.string.readerUiBranch
    }

private fun scopeHint(scope: ReviewScope): StringResource =
    when (scope) {
        ReviewScope.Conversation -> Res.string.readerUiSessionScopeHint
        ReviewScope.WorkingTree -> Res.string.readerUiWorkingScopeHint
        ReviewScope.Branch -> Res.string.readerUiBranchScopeHint
    }

/**
 * One file's diff, full width, in the kit diff view (left to right, sign column, number gutter).
 */
@Composable
public fun FileDiffScreen(state: FileDiffUi, onBack: () -> Unit, onRetry: () -> Unit) {
    FileDiffScreen(
        state,
        object : FileDiffActions {
            override fun back() = onBack()

            override fun retry() = onRetry()
        },
    )
}

/** One file's diff with line comments for the agent (B-156). */
@Composable
public fun FileDiffScreen(state: FileDiffUi, actions: FileDiffActions) {
    val onRetry = actions::retry
    var fullContext by remember(state.path) { mutableStateOf(false) }
    KitScreen(
        topBar = {
            FileTopBar(state, fullContext, { fullContext = !fullContext }, actions::back, actions::addToPrompt)
        },
        loading = state.load == ReviewLoad.Loading || state.updating,
        bottom = if (state.canComment && state.drafts > 0) ({ SendCommentsBlock(state, actions) }) else null,
        edges = KitScreenEdges.Bleed,
    ) {
        when (val load = state.load) {
            ReviewLoad.Loading -> Unit
            is ReviewLoad.Failed ->
                KitStateView(
                    KitStateKind.Error,
                    stringResource(Res.string.kitDiffLoadFailed),
                    failure = load.failure,
                    onRetry = onRetry,
                    retryLabel = stringResource(Res.string.kitTryAgain),
                )
            ReviewLoad.Ready -> {
                val diff = state.diff
                when {
                    state.missing ->
                        KitStateView(KitStateKind.Calm, stringResource(Res.string.reviewFileGone))
                    diff == null || state.noDiff ->
                        KitStateView(KitStateKind.Calm, stringResource(Res.string.reviewNoDiff))
                    else -> DiffBody(diff, fullContext, state, actions)
                }
            }
        }
    }
    CommentDialog(state, actions)
}

@Composable
private fun SendCommentsBlock(state: FileDiffUi, actions: FileDiffActions) {
    KitActionBlock(
        modifier = Modifier.padding(horizontal = Space.S4.dp, vertical = Space.S2.dp),
        primary =
            KitAction(
                pluralStringResource(Res.plurals.reviewSendComments, state.drafts, state.drafts),
                actions::sendComments,
                working = state.sending,
            ),
    )
}

/** The file's name with Copy patch, Copy updated file and the surrounding-lines switch, where each applies. */
@Composable
private fun FileTopBar(
    state: FileDiffUi,
    fullContext: Boolean,
    onToggleContext: () -> Unit,
    onBack: () -> Unit,
    onAddToPrompt: () -> Unit,
) {
    val current = state.diff
    // Copy is verbatim (a diff is the person's own content): the patch, and the updated file.
    val copyPatch =
        rememberKitCopyAction(
            stringResource(Res.string.reviewCopyPatch),
            stringResource(Res.string.s40iCopied),
            { current?.patch.orEmpty() },
        )
    val copyFile =
        rememberKitCopyAction(
            stringResource(Res.string.reviewCopyFile),
            stringResource(Res.string.s40iCopied),
            { current?.after.orEmpty() },
        )
    // Surrounding lines can be opened only where the whole file is known (before and after).
    val canExpand =
        current != null && !current.patch.isNullOrBlank() && current.before != null && current.after != null
    val expandLabel =
        stringResource(
            if (fullContext) Res.string.kitDiffHideUnchanged else Res.string.reviewExpandContext
        )
    val addToPromptLabel = stringResource(Res.string.reviewWorkspaceAddFileToPrompt, state.name)
    KitTopBar(
        title = state.name,
        exit = KitTopBarExit.Back,
        exitLabel = stringResource(Res.string.kitTopBarBack),
        onExit = onBack,
        actions =
            buildList {
                if (current != null && !current.patch.isNullOrBlank())
                    add(KitLineAction(copyPatch.label, { copyPatch.onClick?.invoke() }))
                if (current != null && !current.after.isNullOrEmpty())
                    add(KitLineAction(copyFile.label, { copyFile.onClick?.invoke() }))
                if (canExpand) add(KitLineAction(expandLabel, onToggleContext))
                if (state.canAddFile)
                    add(KitLineAction(addToPromptLabel, onAddToPrompt))
            },
    )
}

/** The short comment field, named after the lines it is about (B-156). */
@Composable
private fun CommentDialog(state: FileDiffUi, actions: FileDiffActions) {
    val anchor = state.commenting
    val emptyNote = stringResource(Res.string.reviewWorkspaceCommentEmpty)
    KitInputDialog(
        visible = anchor != null,
        title =
            if (anchor == null) ""
            else if (anchor.start == anchor.end)
                stringResource(Res.string.reviewCommentTitleLine, anchor.start.toString())
            else stringResource(Res.string.reviewCommentTitleLines, anchor.start.toString(), anchor.end.toString()),
        fieldLabel = stringResource(Res.string.reviewCommentField),
        confirmLabel = stringResource(Res.string.reviewCommentAdd),
        validate = { if (it.isBlank()) emptyNote else null },
        operationKey = anchor,
        onResult = actions::commentText,
    )
}

@Composable
private fun DiffBody(diff: FileDiff, fullContext: Boolean, state: FileDiffUi, actions: FileDiffActions) {
    val file = remember(diff, fullContext) { diffFile(diff, fullContext) }
    val redactor = LocalKitRedactor.current
    var unplaced by remember(diff) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = Space.S3.dp),
        verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
    ) {
        Column(Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)) {
            KitText(diff.file, role = TypeRole.Body, tone = TextTone.Secondary, maxLines = 2)
        }
        state.fileNotice?.let { notice ->
            when (notice) {
                FileNotice.Added ->
                    KitNotice(stringResource(Res.string.readerUiReferenceAdded, state.name), tone = StatusTone.Ok)
                FileNotice.AlreadyThere ->
                    KitNotice(stringResource(Res.string.readerUiReferenceDuplicate, state.name))
                is FileNotice.Failed ->
                    FailedNotice(stringResource(Res.string.filesMentionFailed), notice.failure)
            }
        }
        when {
            file.binary ->
                Column(Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)) {
                    KitText(stringResource(Res.string.kitDiffBinary))
                }
            file.segments.isEmpty() ->
                Column(Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)) {
                    KitText(stringResource(Res.string.kitDiffNoChanges))
                }
            else -> {
                val onComment: ((String) -> Unit)? =
                    if (!state.canComment) null
                    else { selected ->
                        val placed = CommentLines.anchor(file, selected) { redactor.redact(it) }
                        unplaced = placed == null
                        actions.comment(placed)
                    }
                Listing(file, onComment)
            }
        }
        if (state.canComment) Comments(state, actions, unplaced)
    }
}

/** The kit diff view of [file] (capped), with "Comment" on a selection where [onComment] is given. */
@Composable
private fun Listing(file: DiffFile, onComment: ((String) -> Unit)?) {
    val linesWord = stringResource(Res.string.kitDiffLines, "%1\$s", "%2\$s")
    val rows = remember(file) { DiffModel.displayRows(file, MAX_DIFF_LINES) }
    // The fold words for each hidden count the view will draw (its labels are asked
    // outside composition).
    val counts =
        remember(rows) {
            rows.rows
                .filterIsInstance<DiffRow.Gap>()
                .map { it.hidden }
                .distinct()
                .sorted()
        }
    val folds = counts.associateWith { n ->
        pluralStringResource(Res.plurals.kitDiffFoldBetween, n, n)
    }
    KitDiffView(
        file,
        maxLines = MAX_DIFF_LINES,
        hunkLabel = { oldStart, oldEnd, newStart, newEnd ->
            // The new side, or the old side when the hunk only removes lines
            // (kit_diff_rows.dart:67-76).
            val (from, to) =
                if (newEnd >= newStart) newStart to newEnd else oldStart to oldEnd
            linesWord.replace("%1\$s", from.toString()).replace("%2\$s", to.toString())
        },
        gapLabel = { hidden -> folds[hidden] ?: "… $hidden" },
        modifier = Modifier.fillMaxWidth().testTag("review-diff"),
        onComment = onComment,
    )
    if (rows.cut) {
        Column(Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)) {
            KitText(
                stringResource(
                    Res.string.kitDiffTooBig,
                    rows.shownLines.toString(),
                    rows.totalLines.toString(),
                ),
                tone = TextTone.Secondary,
            )
        }
    }
}

/** The file's comments under its diff: each line's note with where it stands (B-156). */
@Composable
private fun Comments(state: FileDiffUi, actions: FileDiffActions, unplaced: Boolean) {
    Column(
        Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp).testTag("review-comments"),
        verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
    ) {
        if (unplaced) KitNotice(stringResource(Res.string.reviewCommentNotPlaced))
        state.sendFailure?.let { FailedNotice(stringResource(Res.string.reviewCommentsSendFailed), it) }
        if (state.comments.isEmpty()) {
            KitText(stringResource(Res.string.reviewCommentHint), role = TypeRole.Body, tone = TextTone.Secondary)
        } else {
            val removeLabel = stringResource(Res.string.reviewCommentRemove)
            KitRowGroup(label = stringResource(Res.string.reviewCommentsLabel)) {
                state.comments.forEach { c ->
                    // A new comment, and each step to Fixed, arrives with the kit's entrance
                    // (none under reduced motion).
                    KitEntrance(key = c.status) { CommentRow(c, removeLabel) { actions.removeComment(c.id) } }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(comment: LineComment, removeLabel: String, onRemove: () -> Unit) {
    val a = comment.anchor
    val start = a.start.toString()
    val end = a.end.toString()
    val title =
        when {
            a.side == CommentSide.Old && a.start == a.end -> stringResource(Res.string.reviewCommentRemovedLine, start)
            a.side == CommentSide.Old -> stringResource(Res.string.reviewCommentRemovedLines, start, end)
            a.start == a.end -> stringResource(Res.string.reviewCommentLine, start)
            else -> stringResource(Res.string.reviewCommentLines, start, end)
        }
    val (word, tone) =
        when (comment.status) {
            CommentStatus.Draft -> stringResource(Res.string.reviewCommentDraft) to KitRowStateTone.Quiet
            CommentStatus.Sending -> stringResource(Res.string.reviewCommentSending) to KitRowStateTone.Running
            CommentStatus.Pending -> stringResource(Res.string.reviewCommentWaiting) to KitRowStateTone.Running
            CommentStatus.Resolved -> stringResource(Res.string.reviewCommentFixed) to KitRowStateTone.Quiet
        }
    val draft = comment.status == CommentStatus.Draft
    KitRow(
        title = title,
        supporting = comment.text,
        supportingMaxLines = 3,
        state = KitRowState(word, tone),
        trailing = if (draft) KitRowTrailing.Value(removeLabel, chevron = false) else KitRowTrailing.None,
        onClickLabel = if (draft) removeLabel else null,
        onClick = if (draft) onRemove else null,
        growKey = comment.status,
        modifier = Modifier.testTag("review-comment-${comment.id}"),
    )
}

private const val MAX_DIFF_LINES = 4000

private fun diffFile(diff: FileDiff, fullContext: Boolean = false): DiffFile {
    val status =
        when (diff.status) {
            "added" -> DiffFileStatus.Added
            "deleted" -> DiffFileStatus.Deleted
            else -> null
        }
    val patch = diff.patch
    return if (!patch.isNullOrBlank() && !(fullContext && diff.before != null && diff.after != null))
        DiffModel.parseFilePatch(diff.file, patch, status)
    else DiffModel.fromBeforeAfter(diff.file, diff.before, diff.after, status)
}

/** "Undo from this prompt?" (Flutter `showStageRevertSheet`), as its own page. */
@Composable
public fun UndoFromHereScreen(state: UndoFromHereUi, actions: UndoFromHereActions) {
    // The page and its question share one modal layer (the chat's arrangement).
    KitSheetHost(Modifier.fillMaxSize()) { UndoFromHereContent(state, actions) }
}

@Composable
private fun UndoFromHereContent(state: UndoFromHereUi, actions: UndoFromHereActions) {
    val reason =
        when (state.block) {
            UndoBlock.Busy -> stringResource(Res.string.revertBusy)
            UndoBlock.Stale -> stringResource(Res.string.revertReviewChanged)
            null -> null
        }
    val ready = state.load == ReviewLoad.Ready && state.available && !state.promptUnavailable
    KitScreen(
        topBar = { TopBar(stringResource(Res.string.reviewRevertSheetTitle), actions::back) },
        width = KitScreenWidth.Reading,
        loading = state.load == ReviewLoad.Loading,
        bottom =
            if (ready) {
                {
                    KitActionBlock(
                        modifier =
                            Modifier.padding(horizontal = Space.S4.dp, vertical = Space.S2.dp),
                        primary =
                            KitAction(
                                stringResource(
                                    if (state.staged) Res.string.reviewRevertSheetAction
                                    else Res.string.undoFromHereNowAction
                                ),
                                actions::confirm,
                                enabled =
                                    reason?.let { KitEnabled.Disabled(it) } ?: KitEnabled.Enabled,
                                destructive = !state.staged, // one step cannot be reviewed first
                                working = state.working,
                            ),
                    )
                }
            } else null,
        edges = KitScreenEdges.Bleed,
    ) {
        if (!state.available) {
            KitStateView(KitStateKind.Calm, stringResource(Res.string.reviewUndoUnavailable))
            return@KitScreen
        }
        if (state.load != ReviewLoad.Ready) return@KitScreen
        Page {
            val count = state.messagesAfter
            KitText(
                when {
                    state.staged -> stringResource(Res.string.reviewRevertSheetBody)
                    count == null -> stringResource(Res.string.undoFromHereBodyUnknown)
                    count == 0 -> stringResource(Res.string.undoFromHereBody__eq0)
                    else -> pluralStringResource(Res.plurals.undoFromHereBody, count, count)
                },
                role = TypeRole.Body,
                tone = TextTone.Secondary,
            )
            PromptGroup(state.prompt, state.promptUnavailable)
            if (state.staged) {
                KitRowGroup {
                    KitSwitchRow(
                        title = stringResource(Res.string.reviewRevertFilesToggle),
                        checked = state.applyFiles,
                        supporting = stringResource(Res.string.reviewRevertFilesToggleHint),
                        enabled =
                            if (state.working)
                                KitEnabled.Disabled(stringResource(Res.string.revertBusy))
                            else KitEnabled.Enabled,
                        modifier = Modifier.testTag("undo-files-back"),
                        onCheckedChange = actions::putFilesBack,
                    )
                }
            } else {
                KitRowGroup(label = stringResource(Res.string.undoFromHereFilesLabel)) {
                    if (state.edited.isEmpty()) {
                        KitRow(title = stringResource(Res.string.undoFromHereNoEdits))
                    } else {
                        state.edited.forEach { path ->
                            val cut = path.lastIndexOfAny(charArrayOf('/', '\\'))
                            KitRow(
                                title = if (cut < 0) path else path.substring(cut + 1),
                                supporting = if (cut <= 0) null else path.substring(0, cut),
                                titleIsFileName = true,
                            )
                        }
                    }
                }
            }
            state.failure?.let {
                FailedNotice(
                    stringResource(
                        if (state.staged) Res.string.reviewRevertStageFailed
                        else Res.string.reviewRevertFailed
                    ),
                    it,
                )
            }
        }
    }
}

@Composable
private fun PromptGroup(prompt: String?, unavailable: Boolean) {
    KitRowGroup(label = stringResource(Res.string.reviewRevertPromptLabel)) {
        KitRow(
            title =
                when {
                    unavailable -> stringResource(Res.string.revertPromptUnavailable)
                    prompt == null -> stringResource(Res.string.revertPromptLoading)
                    prompt.isBlank() -> stringResource(Res.string.revertAttachmentPrompt)
                    else -> prompt.trim()
                },
            supportingMaxLines = 4,
            modifier = Modifier.testTag("undo-prompt"),
        )
    }
}

/** "Review the undo" (Flutter `StagedRevertScreen`). */
@Composable
public fun StagedScreen(state: StagedUi, actions: StagedActions) {
    // The page and its question share one modal layer (the chat's arrangement).
    KitSheetHost(Modifier.fillMaxSize()) { StagedContent(state, actions) }
}

@Composable
private fun StagedContent(state: StagedUi, actions: StagedActions) {
    val busy =
        if (state.busy) KitEnabled.Disabled(stringResource(Res.string.revertBusy))
        else KitEnabled.Enabled
    // Keep and Put back wait for the live read: a kept page never decides anything.
    val decide = state.phase == StagedPhase.Review && !state.updating
    KitScreen(
        topBar = { TopBar(stringResource(Res.string.reviewRevertScreenTitle), actions::back) },
        width = KitScreenWidth.Reading,
        loading = state.phase == StagedPhase.Loading || state.working || state.updating,
        bottom =
            if (decide) {
                {
                    KitActionBlock(
                        modifier =
                            Modifier.padding(horizontal = Space.S4.dp, vertical = Space.S2.dp),
                        primary =
                            KitAction(
                                stringResource(Res.string.reviewRevertRestoreTitle),
                                actions::askPutBack,
                                enabled = busy,
                            ),
                        tertiary =
                            listOf(
                                KitAction(
                                    stringResource(Res.string.reviewRevertKeepTitle),
                                    actions::askKeep,
                                    enabled = busy,
                                    destructive = true,
                                )
                            ),
                    )
                }
            } else null,
        edges = KitScreenEdges.Bleed,
    ) {
        val back = KitAction(stringResource(Res.string.reviewRevertBackAction), actions::back)
        state.loadFailure?.let {
            KitStateView(
                KitStateKind.Error,
                stringResource(Res.string.reviewRevertFailed),
                failure = it,
                onRetry = actions::retry,
                retryLabel = stringResource(Res.string.kitTryAgain),
            )
            return@KitScreen
        }
        when (state.phase) {
            StagedPhase.Loading -> Unit
            StagedPhase.None ->
                KitStateView(
                    KitStateKind.Calm,
                    stringResource(Res.string.reviewRevertNoneTitle),
                    body = stringResource(Res.string.reviewRevertNoneBody),
                    primary = back,
                )
            StagedPhase.Kept ->
                KitStateView(
                    KitStateKind.Finished,
                    stringResource(Res.string.reviewRevertKeptTitle),
                    body = stringResource(Res.string.reviewRevertKeptBody),
                    primary = back,
                )
            StagedPhase.Restored ->
                KitStateView(
                    KitStateKind.Finished,
                    stringResource(Res.string.reviewRevertRestoredTitle),
                    body = stringResource(Res.string.reviewRevertRestoredBody),
                    primary = back,
                )
            StagedPhase.Stale ->
                Page {
                    KitStateView(
                        KitStateKind.Calm,
                        stringResource(Res.string.reviewRevertStaleTitle),
                        body = stringResource(Res.string.revertReviewChanged),
                        primary =
                            KitAction(
                                stringResource(Res.string.revertReviewLatest),
                                actions::reviewLatest,
                                enabled = busy,
                            ),
                        secondary = back,
                    )
                    state.failure?.let {
                        FailedNotice(stringResource(Res.string.reviewRevertFailed), it)
                    }
                }
            StagedPhase.Review ->
                Page {
                    val count = state.messagesAfter
                    KitText(
                        when (count) {
                            null -> stringResource(Res.string.reviewRevertScreenIntro)
                            0 -> stringResource(Res.string.reviewRevertScreenIntroCount__eq0)
                            else ->
                                pluralStringResource(
                                    Res.plurals.reviewRevertScreenIntroCount,
                                    count,
                                    count,
                                )
                        },
                        role = TypeRole.Body,
                        tone = TextTone.Secondary,
                    )
                    state.failure?.let {
                        FailedNotice(stringResource(Res.string.reviewRevertFailed), it)
                    }
                    PromptGroup(state.prompt, state.promptUnavailable)
                    val files = state.files
                    KitRowGroup(
                        label = stringResource(Res.string.reviewRevertFilesLabel),
                        modifier = Modifier.testTag("staged-files"),
                    ) {
                        when {
                            files == null ->
                                KitRow(title = stringResource(Res.string.revertPreviewUnavailable))
                            files.isEmpty() ->
                                KitRow(title = stringResource(Res.string.reviewRevertNoFiles))
                            else ->
                                files.forEach { file ->
                                    val cut = file.file.lastIndexOfAny(charArrayOf('/', '\\'))
                                    val counts =
                                        lines(file.additions, file.deletions)?.let { (a, r) ->
                                            stringResource(Res.string.reviewRevertFileLines, a, r)
                                        }
                                    KitRow(
                                        title =
                                            if (cut < 0) file.file
                                            else file.file.substring(cut + 1),
                                        supporting =
                                            listOfNotNull(
                                                    if (cut <= 0) null
                                                    else file.file.substring(0, cut),
                                                    counts,
                                                )
                                                .joinToString(" · ")
                                                .ifEmpty { null },
                                        titleIsFileName = true,
                                        trailing = KitRowTrailing.Chevron,
                                        modifier = Modifier.testTag("staged-file-${file.file}"),
                                        onClick = { actions.openFile(file.file) },
                                    )
                                }
                        }
                    }
                    KitText(
                        stringResource(Res.string.reviewOneFileUnavailable),
                        role = TypeRole.Body,
                        tone = TextTone.Secondary,
                    )
                }
        }
    }
    val title = state.title ?: stringResource(Res.string.commandUntitledChat)
    val count = state.messagesAfter
    val files = state.files
    KitConfirm(
        when (state.confirm) {
            StagedConfirm.Keep ->
                KitConfirmSpec(
                    title = stringResource(Res.string.reviewRevertKeepConfirmTitle, title),
                    confirmLabel = stringResource(Res.string.reviewRevertKeepConfirmAction),
                    kind = KitConfirmKind.Destructive,
                    body = stringResource(Res.string.reviewRevertKeepConfirmBody),
                    consequences =
                        listOf(
                            when (count) {
                                null ->
                                    stringResource(Res.string.reviewRevertKeepConsequenceMessages)
                                0 ->
                                    stringResource(Res.string.reviewRevertKeepConsequenceCount__eq0)
                                else ->
                                    pluralStringResource(
                                        Res.plurals.reviewRevertKeepConsequenceCount,
                                        count,
                                        count,
                                    )
                            },
                            stringResource(Res.string.reviewRevertKeepConsequenceFiles),
                        ),
                )
            StagedConfirm.PutBack ->
                KitConfirmSpec(
                    title = stringResource(Res.string.reviewRevertPutBackQuestion, title),
                    confirmLabel = stringResource(Res.string.reviewRevertRestoreTitle),
                    // Files go back to the saved snapshot: edits made since are replaced, so that
                    // is the error tone.
                    kind =
                        if (files == null || files.isNotEmpty()) KitConfirmKind.Destructive
                        else KitConfirmKind.Neutral,
                    body = stringResource(Res.string.reviewRevertRestoreConfirmBody),
                    consequences =
                        listOfNotNull(
                            stringResource(Res.string.reviewRevertRestoreConsequenceMessages),
                            when {
                                files == null ->
                                    stringResource(
                                        Res.string.reviewRevertRestoreConsequenceUnknownFiles
                                    )
                                files.isNotEmpty() ->
                                    pluralStringResource(
                                        Res.plurals.reviewRevertRestoreConsequenceFiles,
                                        files.size,
                                        files.size,
                                    )
                                else -> null
                            },
                        ),
                )
            null -> null
        },
        working = state.working,
        failed = state.confirm != null && state.failure != null,
        onConfirm = actions::confirm,
        onCancel = actions::dismissConfirm,
    )
}
