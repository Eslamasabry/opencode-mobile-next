package io.github.eslamasabry.oc.feature.review.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.model.FileDiff
import io.github.eslamasabry.oc.feature.review.presentation.ChangeRowUi
import io.github.eslamasabry.oc.feature.review.presentation.ChangesActions
import io.github.eslamasabry.oc.feature.review.presentation.ChangesUi
import io.github.eslamasabry.oc.feature.review.presentation.FileDiffUi
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
import io.github.eslamasabry.oc.ui.kit.modal.KitInputDialog
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
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
import io.github.eslamasabry.oc.ui.words.res.kitCopyDetails
import io.github.eslamasabry.oc.ui.words.res.kitDetails
import io.github.eslamasabry.oc.ui.words.res.kitDiffBinary
import io.github.eslamasabry.oc.ui.words.res.kitDiffFoldBetween
import io.github.eslamasabry.oc.ui.words.res.kitDiffLines
import io.github.eslamasabry.oc.ui.words.res.kitDiffLoadFailed
import io.github.eslamasabry.oc.ui.words.res.kitDiffNoChanges
import io.github.eslamasabry.oc.ui.words.res.kitDiffTooBig
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.kitTryAgain
import io.github.eslamasabry.oc.ui.words.res.revertAttachmentPrompt
import io.github.eslamasabry.oc.ui.words.res.revertBusy
import io.github.eslamasabry.oc.ui.words.res.revertPreviewUnavailable
import io.github.eslamasabry.oc.ui.words.res.revertPromptLoading
import io.github.eslamasabry.oc.ui.words.res.revertPromptUnavailable
import io.github.eslamasabry.oc.ui.words.res.revertReviewChanged
import io.github.eslamasabry.oc.ui.words.res.revertReviewLatest
import io.github.eslamasabry.oc.ui.words.res.reviewFileGone
import io.github.eslamasabry.oc.ui.words.res.reviewIncomplete
import io.github.eslamasabry.oc.ui.words.res.defaultReviewScopeNotice
import io.github.eslamasabry.oc.ui.words.res.kitRequestSendBack
import io.github.eslamasabry.oc.ui.words.res.readerUiWorkingTree
import io.github.eslamasabry.oc.ui.words.res.reviewLooksGood
import io.github.eslamasabry.oc.ui.words.res.reviewSendBackFailed
import io.github.eslamasabry.oc.ui.words.res.reviewSendBackWithNote
import io.github.eslamasabry.oc.ui.words.res.reviewWorkspaceCommentEmpty
import io.github.eslamasabry.oc.ui.words.res.reviewWorkspaceCommentLabel
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
import io.github.eslamasabry.oc.ui.words.res.reviewRevertRestoreConfirmTitle
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
import io.github.eslamasabry.oc.ui.words.res.reviewSummary
import io.github.eslamasabry.oc.ui.words.res.reviewToolEvidence
import io.github.eslamasabry.oc.ui.words.res.reviewTurnTitle
import io.github.eslamasabry.oc.ui.words.res.reviewUndoAll
import io.github.eslamasabry.oc.ui.words.res.reviewUndoBody
import io.github.eslamasabry.oc.ui.words.res.reviewUndoTitle
import io.github.eslamasabry.oc.ui.words.res.reviewUndoUnavailable
import io.github.eslamasabry.oc.ui.words.res.reviewUndoUnknown
import io.github.eslamasabry.oc.ui.words.res.reviewWaiting
import io.github.eslamasabry.oc.ui.words.res.undoFromHereBody
import io.github.eslamasabry.oc.ui.words.res.undoFromHereBody__eq0
import io.github.eslamasabry.oc.ui.words.res.undoFromHereBodyUnknown
import io.github.eslamasabry.oc.ui.words.res.undoFromHereFilesLabel
import io.github.eslamasabry.oc.ui.words.res.undoFromHereNoEdits
import io.github.eslamasabry.oc.ui.words.res.undoFromHereNowAction
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

// The review pages arrange kit parts only (owner rule "kit only"). Every string is :ui:words; server text (paths,
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
    ) { content() }
}

@Composable
private fun FailedNotice(message: String, failure: io.github.eslamasabry.oc.core.common.Failure, retry: (() -> Unit)? = null) {
    KitErrorNotice(
        message,
        failure = failure,
        detailsLabel = stringResource(Res.string.kitDetails),
        copyLabel = stringResource(Res.string.kitCopyDetails),
        retry = retry?.let { KitLineAction(stringResource(Res.string.kitTryAgain), it) },
    )
}

private fun lines(added: Int?, removed: Int?): Pair<Int, Int>? = if (added != null && removed != null) added to removed else null

/** "Changes" of a turn or the conversation (Flutter `review_workspace.dart` question + `chat_files.dart` rows). */
@Composable
public fun ChangesScreen(state: ChangesUi, actions: ChangesActions) {
    // The page and its question share one modal layer (the chat's arrangement).
    KitSheetHost(Modifier.fillMaxSize()) { ChangesContent(state, actions) }
}

@Composable
private fun ChangesContent(state: ChangesUi, actions: ChangesActions) {
    val busyReason = stringResource(Res.string.revertBusy)
    KitScreen(
        topBar = { TopBar(stringResource(if (state.turn) Res.string.reviewTurnTitle else Res.string.chatUiChanges), actions::back) },
        width = KitScreenWidth.Reading,
        loading = state.load == ReviewLoad.Loading || state.working,
        bottom =
            if (state.load == ReviewLoad.Ready && state.files.isNotEmpty()) {
                {
                    KitActionBlock(
                        modifier = Modifier.padding(horizontal = Space.S4.dp, vertical = Space.S2.dp),
                        primary = KitAction(stringResource(Res.string.reviewLooksGood), actions::keepAll),
                        secondary =
                            when (state.undoAll) {
                                UndoAllUi.Offered ->
                                    KitAction(
                                        stringResource(Res.string.reviewUndoAll),
                                        actions::undoAll,
                                        enabled = if (state.busy) KitEnabled.Disabled(busyReason) else KitEnabled.Enabled,
                                        working = state.working,
                                    )
                                UndoAllUi.Waiting -> KitAction(stringResource(Res.string.reviewRevertScreenTitle), actions::openStaged)
                                else -> null
                            },
                        // Flutter: "Send back" is a note in the chat's box, offered beside Looks good / Undo all.
                        tertiary =
                            if (state.canSendBack) listOf(KitAction(stringResource(Res.string.reviewSendBackWithNote), actions::sendBack))
                            else emptyList(),
                    )
                }
            } else null,
    ) {
        when (val load = state.load) {
            ReviewLoad.Loading -> Unit
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
                    KitStateView(KitStateKind.Empty, stringResource(Res.string.kitDiffNoChanges), body = stringResource(Res.string.reviewNoChangesBody))
                } else {
                    Page {
                        val sum = lines(state.added, state.removed)
                        if (sum != null) {
                            KitText(
                                pluralStringResource(Res.plurals.reviewSummary, state.files.size, state.files.size, sum.first, sum.second),
                                Modifier.testTag("review-summary"),
                                role = TypeRole.Title,
                            )
                        }
                        if (state.workingTree) {
                            KitNotice(stringResource(Res.string.defaultReviewScopeNotice, stringResource(Res.string.readerUiWorkingTree)))
                        } else {
                            KitText(stringResource(Res.string.reviewToolEvidence), role = TypeRole.Body, tone = TextTone.Secondary)
                        }
                        if (state.undoAll == UndoAllUi.Waiting) KitNotice(stringResource(Res.string.reviewWaiting))
                        if (!state.complete) KitNotice(stringResource(Res.string.reviewIncomplete))
                        state.actionFailure?.let { FailedNotice(stringResource(Res.string.reviewRevertStageFailed), it) }
                        state.sendBackFailure?.let { FailedNotice(stringResource(Res.string.reviewSendBackFailed), it) }
                        KitRowGroup(Modifier.testTag("review-files")) {
                            state.files.forEach { row -> ChangeRow(row, actions) }
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
                title = pluralStringResource(Res.plurals.reviewUndoTitle, state.files.size, state.files.size),
                confirmLabel = stringResource(Res.string.reviewUndoAll),
                kind = KitConfirmKind.Destructive,
                body = stringResource(Res.string.reviewUndoBody),
            )
        } else null,
        onConfirm = actions::confirmUndoAll,
        onCancel = actions::dismissConfirm,
    )
}

@Composable
private fun ChangeRow(row: ChangeRowUi, actions: ChangesActions) {
    val counts = lines(row.added, row.removed)?.let { (a, r) -> stringResource(Res.string.reviewRevertFileLines, a, r) }
    KitRow(
        title = row.name,
        supporting = listOfNotNull(row.folder, counts).joinToString(" · ").ifEmpty { null },
        titleIsFileName = true,
        trailing = if (row.hasDiff) KitRowTrailing.Chevron else KitRowTrailing.None,
        modifier = Modifier.testTag("review-file-${row.path}"),
        onClick = if (row.hasDiff) ({ actions.openFile(row.path) }) else null,
    )
}

/** One file's diff, full width, in the kit diff view (left to right, sign column, number gutter). */
@Composable
public fun FileDiffScreen(state: FileDiffUi, onBack: () -> Unit, onRetry: () -> Unit) {
    KitScreen(
        topBar = { TopBar(state.name, onBack) },
        loading = state.load == ReviewLoad.Loading,
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
                    state.missing -> KitStateView(KitStateKind.Calm, stringResource(Res.string.reviewFileGone))
                    diff == null || state.noDiff -> KitStateView(KitStateKind.Calm, stringResource(Res.string.reviewNoDiff))
                    else -> DiffBody(diff)
                }
            }
        }
    }
}

@Composable
private fun DiffBody(diff: FileDiff) {
    val file = remember(diff) { diffFile(diff) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = Space.S3.dp),
        verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
    ) {
        Column(Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)) {
            KitText(diff.file, role = TypeRole.Body, tone = TextTone.Secondary, maxLines = 2)
        }
        when {
            file.binary -> Column(Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)) { KitText(stringResource(Res.string.kitDiffBinary)) }
            file.segments.isEmpty() -> Column(Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)) { KitText(stringResource(Res.string.kitDiffNoChanges)) }
            else -> {
                val linesWord = stringResource(Res.string.kitDiffLines, "%1\$s", "%2\$s")
                val rows = remember(file) { DiffModel.displayRows(file, MAX_DIFF_LINES) }
                // The fold words for each hidden count the view will draw (its labels are asked outside composition).
                val counts = remember(rows) { rows.rows.filterIsInstance<DiffRow.Gap>().map { it.hidden }.distinct().sorted() }
                val folds = counts.associateWith { n -> pluralStringResource(Res.plurals.kitDiffFoldBetween, n, n) }
                KitDiffView(
                    file,
                    maxLines = MAX_DIFF_LINES,
                    hunkLabel = { oldStart, oldEnd, newStart, newEnd ->
                        // The new side, or the old side when the hunk only removes lines (kit_diff_rows.dart:67-76).
                        val (from, to) = if (newEnd >= newStart) newStart to newEnd else oldStart to oldEnd
                        linesWord.replace("%1\$s", from.toString()).replace("%2\$s", to.toString())
                    },
                    gapLabel = { hidden -> folds[hidden] ?: "… $hidden" },
                    modifier = Modifier.fillMaxWidth().testTag("review-diff"),
                )
                if (rows.cut) {
                    Column(Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp)) {
                        KitText(stringResource(Res.string.kitDiffTooBig, rows.shownLines.toString(), rows.totalLines.toString()), tone = TextTone.Secondary)
                    }
                }
            }
        }
    }
}

private const val MAX_DIFF_LINES = 4000

private fun diffFile(diff: FileDiff): DiffFile {
    val status =
        when (diff.status) {
            "added" -> DiffFileStatus.Added
            "deleted" -> DiffFileStatus.Deleted
            else -> null
        }
    val patch = diff.patch
    return if (!patch.isNullOrBlank()) DiffModel.parseFilePatch(diff.file, patch, status)
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
                        modifier = Modifier.padding(horizontal = Space.S4.dp, vertical = Space.S2.dp),
                        primary =
                            KitAction(
                                stringResource(if (state.staged) Res.string.reviewRevertSheetAction else Res.string.undoFromHereNowAction),
                                actions::confirm,
                                enabled = reason?.let { KitEnabled.Disabled(it) } ?: KitEnabled.Enabled,
                                destructive = !state.staged, // one step cannot be reviewed first
                                working = state.working,
                            ),
                    )
                }
            } else null,
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
                        enabled = if (state.working) KitEnabled.Disabled(stringResource(Res.string.revertBusy)) else KitEnabled.Enabled,
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
            state.failure?.let { FailedNotice(stringResource(if (state.staged) Res.string.reviewRevertStageFailed else Res.string.reviewRevertFailed), it) }
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
    val busy = if (state.busy) KitEnabled.Disabled(stringResource(Res.string.revertBusy)) else KitEnabled.Enabled
    val decide = state.phase == StagedPhase.Review
    KitScreen(
        topBar = { TopBar(stringResource(Res.string.reviewRevertScreenTitle), actions::back) },
        width = KitScreenWidth.Reading,
        loading = state.phase == StagedPhase.Loading || state.working,
        bottom =
            if (decide) {
                {
                    KitActionBlock(
                        modifier = Modifier.padding(horizontal = Space.S4.dp, vertical = Space.S2.dp),
                        primary = KitAction(stringResource(Res.string.reviewRevertRestoreTitle), actions::askPutBack, enabled = busy),
                        tertiary = listOf(KitAction(stringResource(Res.string.reviewRevertKeepTitle), actions::askKeep, enabled = busy, destructive = true)),
                    )
                }
            } else null,
    ) {
        val back = KitAction(stringResource(Res.string.reviewRevertBackAction), actions::back)
        state.loadFailure?.let {
            KitStateView(KitStateKind.Error, stringResource(Res.string.reviewRevertFailed), failure = it, onRetry = actions::retry, retryLabel = stringResource(Res.string.kitTryAgain))
            return@KitScreen
        }
        when (state.phase) {
            StagedPhase.Loading -> Unit
            StagedPhase.None -> KitStateView(KitStateKind.Calm, stringResource(Res.string.reviewRevertNoneTitle), body = stringResource(Res.string.reviewRevertNoneBody), primary = back)
            StagedPhase.Kept -> KitStateView(KitStateKind.Finished, stringResource(Res.string.reviewRevertKeptTitle), body = stringResource(Res.string.reviewRevertKeptBody), primary = back)
            StagedPhase.Restored -> KitStateView(KitStateKind.Finished, stringResource(Res.string.reviewRevertRestoredTitle), body = stringResource(Res.string.reviewRevertRestoredBody), primary = back)
            StagedPhase.Stale ->
                Page {
                    KitStateView(
                        KitStateKind.Calm,
                        stringResource(Res.string.reviewRevertStaleTitle),
                        body = stringResource(Res.string.revertReviewChanged),
                        primary = KitAction(stringResource(Res.string.revertReviewLatest), actions::reviewLatest, enabled = busy),
                        secondary = back,
                    )
                    state.failure?.let { FailedNotice(stringResource(Res.string.reviewRevertFailed), it) }
                }
            StagedPhase.Review ->
                Page {
                    val count = state.messagesAfter
                    KitText(
                        when (count) {
                            null -> stringResource(Res.string.reviewRevertScreenIntro)
                            0 -> stringResource(Res.string.reviewRevertScreenIntroCount__eq0)
                            else -> pluralStringResource(Res.plurals.reviewRevertScreenIntroCount, count, count)
                        },
                        role = TypeRole.Body,
                        tone = TextTone.Secondary,
                    )
                    state.failure?.let { FailedNotice(stringResource(Res.string.reviewRevertFailed), it) }
                    PromptGroup(state.prompt, state.promptUnavailable)
                    val files = state.files
                    KitRowGroup(label = stringResource(Res.string.reviewRevertFilesLabel), modifier = Modifier.testTag("staged-files")) {
                        when {
                            files == null -> KitRow(title = stringResource(Res.string.revertPreviewUnavailable))
                            files.isEmpty() -> KitRow(title = stringResource(Res.string.reviewRevertNoFiles))
                            else ->
                                files.forEach { file ->
                                    val cut = file.file.lastIndexOfAny(charArrayOf('/', '\\'))
                                    val counts = lines(file.additions, file.deletions)?.let { (a, r) -> stringResource(Res.string.reviewRevertFileLines, a, r) }
                                    KitRow(
                                        title = if (cut < 0) file.file else file.file.substring(cut + 1),
                                        supporting = listOfNotNull(if (cut <= 0) null else file.file.substring(0, cut), counts).joinToString(" · ").ifEmpty { null },
                                        titleIsFileName = true,
                                        trailing = KitRowTrailing.Chevron,
                                        modifier = Modifier.testTag("staged-file-${file.file}"),
                                        onClick = { actions.openFile(file.file) },
                                    )
                                }
                        }
                    }
                    KitText(stringResource(Res.string.reviewOneFileUnavailable), role = TypeRole.Body, tone = TextTone.Secondary)
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
                                null -> stringResource(Res.string.reviewRevertKeepConsequenceMessages)
                                0 -> stringResource(Res.string.reviewRevertKeepConsequenceCount__eq0)
                                else -> pluralStringResource(Res.plurals.reviewRevertKeepConsequenceCount, count, count)
                            },
                            stringResource(Res.string.reviewRevertKeepConsequenceFiles),
                        ),
                )
            StagedConfirm.PutBack ->
                KitConfirmSpec(
                    title = stringResource(Res.string.reviewRevertRestoreConfirmTitle, title),
                    confirmLabel = stringResource(Res.string.reviewRevertRestoreTitle),
                    // Files go back to the saved snapshot: edits made since are replaced, so that is the error tone.
                    kind = if (files == null || files.isNotEmpty()) KitConfirmKind.Destructive else KitConfirmKind.Neutral,
                    body = stringResource(Res.string.reviewRevertRestoreConfirmBody),
                    consequences =
                        listOfNotNull(
                            stringResource(Res.string.reviewRevertRestoreConsequenceMessages),
                            when {
                                files == null -> stringResource(Res.string.reviewRevertRestoreConsequenceUnknownFiles)
                                files.isNotEmpty() -> pluralStringResource(Res.plurals.reviewRevertRestoreConsequenceFiles, files.size, files.size)
                                else -> null
                            },
                        ),
                )
            null -> null
        },
        onConfirm = actions::confirm,
        onCancel = actions::dismissConfirm,
    )
}
