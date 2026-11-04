package io.github.eslamasabry.oc.feature.files.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.engine.files.FileBody
import io.github.eslamasabry.oc.feature.files.presentation.FileActions
import io.github.eslamasabry.oc.feature.files.presentation.FileNotice
import io.github.eslamasabry.oc.feature.files.presentation.FilePhase
import io.github.eslamasabry.oc.feature.files.presentation.FileUi
import io.github.eslamasabry.oc.feature.files.presentation.PdfPageUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitPdfPngPage
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitViewerAction
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitViewerBinaryBody
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitViewerCodeBody
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitViewerFrame
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitViewerImageBody
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitViewerSvgBody
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitViewerTableBody
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitViewerTextBody
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kitchat.header.KitClipboardEffect
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdown
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdownActions
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMarkdownSelection
import io.github.eslamasabry.oc.ui.kitchat.markdown.KitMdSource
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.fileLineOutsidePreview
import io.github.eslamasabry.oc.ui.words.res.filePdfEncrypted
import io.github.eslamasabry.oc.ui.words.res.filePdfFailed
import io.github.eslamasabry.oc.ui.words.res.filePdfLimit
import io.github.eslamasabry.oc.ui.words.res.filePdfUnavailable
import io.github.eslamasabry.oc.ui.words.res.fileReload
import io.github.eslamasabry.oc.ui.words.res.fileSave
import io.github.eslamasabry.oc.ui.words.res.fileShareAction
import io.github.eslamasabry.oc.ui.words.res.fileSource
import io.github.eslamasabry.oc.ui.words.res.filesAskAboutFile
import io.github.eslamasabry.oc.ui.words.res.filesAttachFailed
import io.github.eslamasabry.oc.ui.words.res.filesLinkRefused
import io.github.eslamasabry.oc.ui.words.res.filesMentionFailed
import io.github.eslamasabry.oc.ui.words.res.filesMoreActions
import io.github.eslamasabry.oc.ui.words.res.filesNotConnected
import io.github.eslamasabry.oc.ui.words.res.filesOpenFailed
import io.github.eslamasabry.oc.ui.words.res.filesOpenLink
import io.github.eslamasabry.oc.ui.words.res.filesOpenLinkBody
import io.github.eslamasabry.oc.ui.words.res.filesOpenLinkQuestion
import io.github.eslamasabry.oc.ui.words.res.filesOpenWith
import io.github.eslamasabry.oc.ui.words.res.filesOpenWithFailed
import io.github.eslamasabry.oc.ui.words.res.filesOpenWithNone
import io.github.eslamasabry.oc.ui.words.res.filesPreferencesFailed
import io.github.eslamasabry.oc.ui.words.res.filesSaveFailed
import io.github.eslamasabry.oc.ui.words.res.filesShowPreview
import io.github.eslamasabry.oc.ui.words.res.filesUnavailable
import io.github.eslamasabry.oc.ui.words.res.filesUnwrapCode
import io.github.eslamasabry.oc.ui.words.res.filesWrapCode
import io.github.eslamasabry.oc.ui.words.res.kitCodeCopyCode
import io.github.eslamasabry.oc.ui.words.res.kitTopBarClose
import io.github.eslamasabry.oc.ui.words.res.kitTryAgain
import io.github.eslamasabry.oc.ui.words.res.kitViewerPartial
import io.github.eslamasabry.oc.ui.words.res.readerUiAttachPrompt
import io.github.eslamasabry.oc.ui.words.res.readerUiAttached
import io.github.eslamasabry.oc.ui.words.res.readerUiCopyPath
import io.github.eslamasabry.oc.ui.words.res.readerUiOpenReview
import io.github.eslamasabry.oc.ui.words.res.readerUiPathLine
import io.github.eslamasabry.oc.ui.words.res.readerUiSavedDevice
import org.jetbrains.compose.resources.stringResource

/**
 * D03 (07 §5): the file viewer, a page. The kit's viewer frame (name, parent folder, Close) with
 * the body for the file's kind; at the thumb, Ask about this file (into this conversation's
 * message) and Share on Wi-Fi; Open with is a visible button under them. Nothing is long-press
 * only.
 */
@Composable
public fun FileScreen(state: FileUi, actions: FileActions) {
    var more by remember { mutableStateOf(false) }
    KitClipboardEffect(state.clipId, state.clipText, actions::clipDone)
    // One modal layer for the frame and the share sheet (the chat's KitSheetHost pattern).
    KitSheetHost(Modifier.fillMaxSize()) {
        KitScreen(modifier = Modifier.fillMaxSize(), page = true) {
            Column(Modifier.fillMaxSize()) {
                val ready = state.phase == FilePhase.Ready
                KitViewerFrame(
                    name =
                        if (state.line == null) state.name
                        else stringResource(Res.string.readerUiPathLine, state.name, state.line),
                    modifier = Modifier.weight(1f),
                    path = state.path,
                    primary =
                        if (ready && !state.asking)
                            KitViewerAction(
                                stringResource(Res.string.filesAskAboutFile),
                                actions::ask,
                                "project-file-ask",
                            )
                        else null,
                    secondary =
                        if (ready && state.canShare)
                            KitViewerAction(
                                stringResource(Res.string.fileShareAction),
                                actions::shareOnWifi,
                                "project-file-share-wifi",
                            )
                        else null,
                    onClose = actions::back,
                ) {
                    Body(state, actions)
                }
                state.notice?.let { notice ->
                    KitNotice(
                        noticeWords(notice, state.name),
                        Modifier.padding(horizontal = Space.S4.dp),
                        dismiss =
                            KitLineAction(
                                stringResource(Res.string.kitTopBarClose),
                                actions::dismissNotice,
                            ),
                    )
                }
                if (ready)
                    KitButton(
                        stringResource(Res.string.filesMoreActions),
                        { more = true },
                        Modifier.fillMaxWidth().padding(horizontal = Space.S4.dp),
                        role = KitButtonRole.Tertiary,
                    )
                if (ready && state.canOpenWith) {
                    KitButton(
                        stringResource(Res.string.filesOpenWith),
                        actions::openWith,
                        Modifier.fillMaxWidth()
                            .padding(horizontal = Space.S4.dp, vertical = Space.S2.dp),
                        role = KitButtonRole.Tertiary,
                        working = state.opening,
                        enabled = KitEnabled.Enabled,
                    )
                }
                if (ready && state.canSave) {
                    KitButton(
                        stringResource(Res.string.fileSave),
                        actions::save,
                        Modifier.fillMaxWidth()
                            .padding(horizontal = Space.S4.dp, vertical = Space.S2.dp),
                        role = KitButtonRole.Tertiary,
                        working = state.saving,
                        enabled = KitEnabled.Enabled,
                    )
                }
            }
        }
        KitConfirm(
            state.confirmLinkHost?.let { host ->
                KitConfirmSpec(
                    title = stringResource(Res.string.filesOpenLinkQuestion, host),
                    body = stringResource(Res.string.filesOpenLinkBody),
                    confirmLabel = stringResource(Res.string.filesOpenLink),
                )
            },
            actions::confirmOpenLink,
            actions::cancelOpenLink,
        )
        FileShareSheet(state.share, state.name, actions)
        KitSheet(
            visible = more,
            onDismissRequest = { more = false },
            title =
                if (state.name.isEmpty()) stringResource(Res.string.filesMoreActions)
                else state.name,
            closeLabel = stringResource(Res.string.kitTopBarClose),
        ) {
            Column {
                if (state.canReview)
                    KitRow(
                        stringResource(Res.string.readerUiOpenReview),
                        onClick = {
                            more = false
                            actions.review()
                        },
                    )
                if (state.canAttach)
                    KitRow(
                        stringResource(Res.string.readerUiAttachPrompt),
                        onClick = {
                            more = false
                            actions.attach()
                        },
                    )
                KitRow(
                    stringResource(Res.string.readerUiCopyPath),
                    onClick = {
                        more = false
                        actions.copyPath()
                    },
                )
                KitRow(
                    stringResource(Res.string.fileReload),
                    onClick = {
                        more = false
                        actions.retry()
                    },
                )
                if (state.canChooseWrap && (state.body is FileBody.Code || state.source))
                    KitRow(
                        stringResource(
                            if (state.wrapCode) Res.string.filesUnwrapCode
                            else Res.string.filesWrapCode
                        ),
                        onClick = {
                            more = false
                            actions.toggleWrap()
                        },
                    )
                if (
                    state.body is FileBody.Delimited ||
                        state.body is FileBody.Svg ||
                        state.body is FileBody.Markdown
                )
                    KitRow(
                        stringResource(
                            if (state.source) Res.string.filesShowPreview else Res.string.fileSource
                        ),
                        onClick = {
                            more = false
                            actions.toggleSource()
                        },
                    )
            }
        }
    }
}

@Composable
private fun Body(state: FileUi, actions: FileActions) {
    when (state.phase) {
        FilePhase.Loading -> KitSkeletonRows()
        FilePhase.NotConnected ->
            KitStateView(
                kind = KitStateKind.Error,
                title = stringResource(Res.string.filesNotConnected),
                onRetry = actions::retry,
                retryLabel = stringResource(Res.string.kitTryAgain),
            )
        FilePhase.Unavailable ->
            KitStateView(
                kind = KitStateKind.Calm,
                title = stringResource(Res.string.filesUnavailable),
            )
        FilePhase.Failed ->
            KitStateView(
                kind = KitStateKind.Error,
                title = stringResource(Res.string.filesOpenFailed),
                failure = state.failure,
                onRetry = actions::retry,
                retryLabel = stringResource(Res.string.kitTryAgain),
            )
        FilePhase.Ready ->
            when (val body = state.body) {
                null -> KitSkeletonRows()
                is FileBody.Text ->
                    Partial(body.truncated) { KitViewerTextBody(body.text, wrap = true) }
                is FileBody.Markdown ->
                    Partial(body.truncated) {
                        if (state.source) KitViewerCodeBody(body.text, wrap = state.wrapCode)
                        else {
                            val blocks =
                                remember(body.text) {
                                    KitMdSource.blocks("file", body.text, streaming = false)
                                }
                            KitMarkdownSelection(
                                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                            ) {
                                KitMarkdown(
                                    blocks,
                                    KitMarkdownActions(
                                        onLink = actions::openLink,
                                        onCopyCode = actions::copyText,
                                        copyCodeLabel = stringResource(Res.string.kitCodeCopyCode),
                                    ),
                                    Modifier.padding(Space.S4.dp),
                                )
                            }
                        }
                    }
                is FileBody.Delimited ->
                    Partial(body.truncated) {
                        if (state.source) KitViewerCodeBody(body.original, wrap = state.wrapCode)
                        else KitViewerTableBody(body.rows)
                    }
                is FileBody.Svg -> {
                    if (state.source) KitViewerCodeBody(body.original, wrap = state.wrapCode)
                    else
                        KitViewerSvgBody(body.source, state.name) {
                            KitViewerCodeBody(body.original, wrap = state.wrapCode)
                        }
                }
                is FileBody.Code ->
                    Partial(body.truncated) {
                        val count = remember(body.text) { body.text.lineSequence().count() }
                        val line = body.line
                        Column(Modifier.fillMaxSize()) {
                            if (line != null && line !in 1..count)
                                KitNotice(stringResource(Res.string.fileLineOutsidePreview, line))
                            Box(Modifier.weight(1f)) {
                                KitViewerCodeBody(
                                    body.text,
                                    initialLine = body.line,
                                    wrap = state.wrapCode,
                                )
                            }
                        }
                    }
                is FileBody.Image ->
                    KitViewerImageBody(body.bytes, state.name) {
                        Binary(state, actions, mime = null)
                    }
                is FileBody.Pdf -> Pdf(state, actions)
                is FileBody.Binary -> Binary(state, actions, body.mime)
            }
    }
}

/** A file longer than the viewer shows says so above its text (Flutter's partial-file notice). */
@Composable
private fun Partial(truncated: Boolean, body: @Composable () -> Unit) {
    if (!truncated) {
        body()
        return
    }
    Column(Modifier.fillMaxSize()) {
        KitNotice(
            stringResource(Res.string.kitViewerPartial),
            Modifier.padding(horizontal = Space.S4.dp),
        )
        Box(Modifier.weight(1f)) { body() }
    }
}

@Composable
private fun Binary(state: FileUi, actions: FileActions, mime: String?) {
    KitViewerBinaryBody(
        mimeType = mime,
        sizeBytes = state.sizeBytes,
        action =
            if (state.canOpenWith)
                KitViewerAction(stringResource(Res.string.filesOpenWith), actions::openWith)
            else null,
    )
}

@Composable
private fun Pdf(state: FileUi, actions: FileActions) {
    val failure = state.pdfFailure
    if (failure != null) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
            KitNotice(pdfWords(failure), Modifier.padding(horizontal = Space.S4.dp))
            Box(Modifier.weight(1f)) { Binary(state, actions, "application/pdf") }
        }
        return
    }
    if (state.pdfPageCount == 0) {
        KitSkeletonRows()
        return
    }
    val failedWords = stringResource(Res.string.filePdfFailed)
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
        items(state.pdfPageCount, key = { it }) { index ->
            LaunchedEffect(index) { actions.pdfPage(index) }
            val page = state.pdfPages[index]
            KitPdfPngPage(
                page = index + 1,
                pageCount = state.pdfPageCount,
                png = (page as? PdfPageUi.Ready)?.png,
                failed = page is PdfPageUi.Failed,
                failedReason = failedWords,
                onRetry = { actions.pdfPage(index) },
            )
        }
    }
}

@Composable
private fun pdfWords(failure: Failure): String =
    stringResource(
        when ((failure as? Failure.Media)?.problem) {
            MediaProblem.PdfTooLarge -> Res.string.filePdfLimit
            MediaProblem.PdfNeedsAndroid10 -> Res.string.filePdfUnavailable
            MediaProblem.PdfEncrypted -> Res.string.filePdfEncrypted
            else -> Res.string.filePdfFailed
        }
    )

@Composable
private fun noticeWords(notice: FileNotice, name: String): String =
    when (notice) {
        FileNotice.OpenWithNone -> stringResource(Res.string.filesOpenWithNone)
        FileNotice.OpenWithFailed -> stringResource(Res.string.filesOpenWithFailed)
        FileNotice.MentionFailed -> stringResource(Res.string.filesMentionFailed)
        FileNotice.Saved -> stringResource(Res.string.readerUiSavedDevice, name)
        FileNotice.SaveFailed -> stringResource(Res.string.filesSaveFailed)
        FileNotice.Attached -> stringResource(Res.string.readerUiAttached, name)
        FileNotice.AttachFailed -> stringResource(Res.string.filesAttachFailed)
        FileNotice.PreferencesFailed -> stringResource(Res.string.filesPreferencesFailed)
        FileNotice.LinkRefused -> stringResource(Res.string.filesLinkRefused)
    }
