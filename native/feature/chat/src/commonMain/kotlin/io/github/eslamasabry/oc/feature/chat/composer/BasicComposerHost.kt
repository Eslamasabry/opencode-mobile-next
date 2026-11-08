package io.github.eslamasabry.oc.feature.chat.composer

import io.github.eslamasabry.oc.ui.kitchat.composer.KitStopAndSendPill
import io.github.eslamasabry.oc.ui.words.res.kitComposerStopAndSend
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow
import io.github.eslamasabry.oc.engine.settings.teaching.TeachingIds
import io.github.eslamasabry.oc.feature.chat.ui.ComposerTip
import io.github.eslamasabry.oc.feature.chat.ui.ComposerTipRules
import io.github.eslamasabry.oc.feature.chat.ui.OfferComposerTip
import io.github.eslamasabry.oc.feature.chat.ui.rememberTipUsed
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.onClick
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.platform.PromptFilePicker
import io.github.eslamasabry.oc.feature.chat.composer.history.ComposerHistorySheet
import io.github.eslamasabry.oc.feature.chat.composer.history.ComposerHistoryUi
import io.github.eslamasabry.oc.feature.chat.composer.presentation.AttachKind
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerActions
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerUi
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerViewModel
import io.github.eslamasabry.oc.feature.chat.composer.presentation.ComposerQuery
import io.github.eslamasabry.oc.feature.chat.composer.presentation.SendFieldRules
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.words.res.kitSuggestionsShowAll
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipKind
import io.github.eslamasabry.oc.ui.kit.chip.KitChipWrap
import io.github.eslamasabry.oc.ui.kit.chip.KitPhraseChips
import io.github.eslamasabry.oc.ui.kit.motion.KitReveal
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerAdd
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerFieldRow
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerStatusStrip
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerLimitStrip
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerBox
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerRowFit
import io.github.eslamasabry.oc.ui.kit.chip.KitComposerModelChip
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerPrimaryAction
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerSend
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerStopRing
import io.github.eslamasabry.oc.ui.kitchat.composer.KitInsertedPicture
import io.github.eslamasabry.oc.ui.kitchat.composer.KitPictureReceiver
import io.github.eslamasabry.oc.ui.words.res.chatStopAfterStep
import io.github.eslamasabry.oc.ui.words.res.tipSendLater
import io.github.eslamasabry.oc.ui.words.res.tipStopAfterStep
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.modal.KitMenu
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerTray
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerTrayAction
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerVoice
import io.github.eslamasabry.oc.ui.kitchat.composer.KitVoiceField
import io.github.eslamasabry.oc.ui.kitchat.stream.LocalKitPromptLift
import io.github.eslamasabry.oc.ui.kitchat.stream.kitPromptOrigin
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.agentErrorOpenSettings
import io.github.eslamasabry.oc.ui.words.res.composerLaterScheduled
import io.github.eslamasabry.oc.ui.words.res.chatDraftAlreadySent
import io.github.eslamasabry.oc.ui.words.res.chatDraftRepeatAction
import io.github.eslamasabry.oc.ui.words.res.chatDraftRepeatBody
import io.github.eslamasabry.oc.ui.words.res.chatDraftRepeatTitle
import io.github.eslamasabry.oc.ui.words.res.chatDraftRestoring
import io.github.eslamasabry.oc.ui.words.res.phraseChipsHide
import io.github.eslamasabry.oc.ui.words.res.phraseChipsRowLabel
import io.github.eslamasabry.oc.ui.words.res.phraseSuggestionsInsert
import io.github.eslamasabry.oc.ui.words.res.phraseSuggestionsOptions
import io.github.eslamasabry.oc.ui.words.res.chatSubagentReadOnly
import io.github.eslamasabry.oc.ui.words.res.chatUiOpenParentSession
import io.github.eslamasabry.oc.ui.words.res.composerDraftCleared
import io.github.eslamasabry.oc.ui.words.res.composerFullAttachmentsLocal
import io.github.eslamasabry.oc.ui.words.res.composerFullDraftFull
import io.github.eslamasabry.oc.ui.words.res.composerFullPasteEmpty
import io.github.eslamasabry.oc.ui.words.res.composerFullPhotoDenied
import io.github.eslamasabry.oc.ui.words.res.composerFullStale
import io.github.eslamasabry.oc.ui.words.res.composerFullSteerHint
import io.github.eslamasabry.oc.ui.words.res.composerKeptMissingFiles
import io.github.eslamasabry.oc.ui.words.res.composerKeptRestoreRefused
import io.github.eslamasabry.oc.ui.words.res.composerKeptRestoreTitle
import io.github.eslamasabry.oc.ui.words.res.composerPreviewAttachmentDismiss
import io.github.eslamasabry.oc.ui.words.res.composerPreviewAttachmentFailed
import io.github.eslamasabry.oc.ui.words.res.chatUiDelegateThisPrompt
import io.github.eslamasabry.oc.ui.words.res.composerPreviewAttachmentRetry
import io.github.eslamasabry.oc.ui.words.res.composerPreviewAttachmentStorage
import io.github.eslamasabry.oc.ui.words.res.composerPreviewAttachmentTooLarge
import io.github.eslamasabry.oc.ui.words.res.composerPreviewAttachmentUnsupported
import io.github.eslamasabry.oc.ui.words.res.composerPreviewModelButton
import io.github.eslamasabry.oc.ui.words.res.composerPreviewModelDefault
import io.github.eslamasabry.oc.ui.words.res.draftRetrySave
import io.github.eslamasabry.oc.ui.words.res.draftAttachmentsFailed
import io.github.eslamasabry.oc.ui.words.res.draftSaveFailed
import io.github.eslamasabry.oc.ui.words.res.kitComposerAddToTurn
import io.github.eslamasabry.oc.ui.words.res.kitComposerField
import io.github.eslamasabry.oc.ui.words.res.kitComposerSend
import io.github.eslamasabry.oc.ui.words.res.kitComposerSendAfter
import io.github.eslamasabry.oc.ui.words.res.kitComposerSending
import io.github.eslamasabry.oc.ui.words.res.kitComposerTrayClose
import io.github.eslamasabry.oc.ui.words.res.kitComposerTools
import io.github.eslamasabry.oc.ui.words.res.composerTrayCamera
import io.github.eslamasabry.oc.ui.words.res.composerTrayFile
import io.github.eslamasabry.oc.ui.words.res.composerTrayMore
import io.github.eslamasabry.oc.ui.words.res.composerTrayPhotos
import io.github.eslamasabry.oc.ui.words.res.chatUiAvailableWhenTheCurrentRunFinishes
import io.github.eslamasabry.oc.ui.words.res.kitTurnLiveStopShort
import io.github.eslamasabry.oc.ui.words.res.kitUndoAction
import io.github.eslamasabry.oc.ui.words.res.phoneServerCardStopping
import io.github.eslamasabry.oc.ui.words.res.promptHistorySaveFailed
import io.github.eslamasabry.oc.ui.words.res.tipSendLaterTitle
import io.github.eslamasabry.oc.ui.words.res.upgradeComposerAsk
import io.github.eslamasabry.oc.ui.words.res.upgradeComposerFree
import io.github.eslamasabry.oc.ui.words.res.workspaceDismissNotice
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * The slice-2 composer (plan 09 §6.3) extended by the full-composer slice (2026-11-02): the field,
 * Send and Stop in the same row while a reply runs, plus the visible model choice and file
 * attachments.
 */
@Inject
@ContributesBinding(AppScope::class)
public class BasicComposerHost(
    private val voices: Set<KitComposerVoice>,
    private val editors: Set<ComposerPromptEditor> = emptySet(),
    private val pictures: Set<ComposerInsertedPictures> = emptySet(),
    private val limits: Set<ComposerLimits> = emptySet(),
) : ComposerHost {
    override val hasPromptEditor: Boolean
        get() = editors.isNotEmpty()

    @Composable
    override fun Composer(target: ComposerTarget, chrome: ComposerChrome, modifier: Modifier) {
        val vm =
            assistedMetroViewModel<BasicComposerViewModel, BasicComposerViewModel.Factory>(
                key = "composer:${target.profileId}:${target.sessionId}"
            ) {
                create(target)
            }
        val state by vm.state.collectAsStateWithLifecycle()
        val history by vm.history.collectAsStateWithLifecycle()
        BasicComposer(
            state,
            vm,
            chrome,
            modifier,
            voice = voices.firstOrNull(),
            conversationRef =
                target.sessionId?.let {
                    ConversationRef(ProfileId(target.profileId), SessionId(it))
                },
            history = history,
            refreshHistory = vm::loadHistory,
            promptEditor = editors.firstOrNull(),
            insertedPictures = pictures.firstOrNull(),
            limits = limits.firstOrNull(),
        )
    }
}

/** Platform file pickers into the composer graph; desktop's empty set disables the action. */
@ContributesTo(AppScope::class)
public interface ComposerFilePickerBindings {
    @Multibinds(allowEmpty = true) public val promptFilePickers: Set<PromptFilePicker>

    @Multibinds(allowEmpty = true)
    public val composerAttachSources:
        Set<io.github.eslamasabry.oc.feature.chat.composer.presentation.ComposerAttachSource>
}

/** The field and its controls, from kit parts. Tags are the Flutter keys (plan 09 §15.5). */
@Composable
public fun BasicComposer(
    state: BasicComposerUi,
    actions: BasicComposerActions,
    chrome: ComposerChrome,
    modifier: Modifier = Modifier,
    voice: KitComposerVoice? = null,
    conversationRef: ConversationRef? = null,
    history: ComposerHistoryUi = ComposerHistoryUi(),
    refreshHistory: (() -> Unit)? = null,
    promptEditor: ComposerPromptEditor? = null,
    insertedPictures: ComposerInsertedPictures? = null,
    limits: ComposerLimits? = null,
) {
    if (state.readOnly != null) {
        Column(modifier) {
            KitNotice(
                stringResource(
                    io.github.eslamasabry.oc.ui.words.res.Res.string.chatSubagentReadOnly
                )
            )
            if (state.readOnly.parent != null)
                KitButton(
                    stringResource(
                        io.github.eslamasabry.oc.ui.words.res.Res.string.chatUiOpenParentSession
                    ),
                    actions::openParent,
                    role = KitButtonRole.Secondary,
                )
        }
        return
    }
    val field = remember(state.targetKey) { TextFieldState() }
    val focus = remember(state.targetKey) { FocusRequester() }
    var editorReady by remember(state.targetKey) { mutableStateOf(false) }
    val editor = remember(state.targetKey) { DraftEditorState() }
    var repeatRevision by remember(state.targetKey) { mutableStateOf<Long?>(null) }
    val editSerialAtComposition = editor.userEditSerial
    // Restore/clear only external revisions. Ordinary typing never assigns text or moves IME
    // selection.
    LaunchedEffect(field, state.draft.ready, state.draft.restoreRevision) {
        if (
            state.draft.ready &&
                (!editorReady || editor.restoreRevision != state.draft.restoreRevision)
        ) {
            val current = field.text.toString()
            editor.restoreRevision = state.draft.restoreRevision
            if (
                editorReady &&
                    (current != editor.lastReported ||
                        editor.userEditSerial != editSerialAtComposition)
            ) {
                editor.lastReported = current
                actions.draftChanged(current)
            } else {
                if (current != state.draft.text) field.edit { replace(0, length, state.draft.text) }
                editor.lastReported = state.draft.text
            }
            editorReady = true
        }
    }
    // N-55: a stray hardware key steers focus here (the chat asks; the field answers).
    DisposableEffect(focus, chrome.typing) {
        chrome.typing.focus = { focus.requestFocus() }
        onDispose { chrome.typing.focus = null }
    }
    // "Ready to type" (07 §2.5, B-NAV-005): focus once per arrival, then acknowledge it.
    val focusId = chrome.focusRequest()
    LaunchedEffect(focusId) {
        if (focusId != null) {
            focus.requestFocus()
            chrome.focusDone(focusId)
        }
    }
    // One composer insert at a time, after the draft is restored. Saved prompts and slash commands
    // (P12-PROMPTS): an
    // empty field takes the words whole, otherwise they go on a new line after the words. "Edit and
    // resend"
    // (P12-CHAT-FEEL, [InsertRequest.replace]): the prompt's words replace the field, focused,
    // ready to change and send.
    // Reported as the person's own edit so the draft owner keeps it.
    var insertRefused by remember(state.targetKey) { mutableStateOf(false) }
    var missingInsertFiles by
        remember(state.targetKey) { mutableStateOf<List<String>>(emptyList()) }
    val insert = chrome.pendingInsert()
    LaunchedEffect(insert, editorReady, state.draft.ready) {
        val request = insert ?: return@LaunchedEffect
        if (!editorReady || !state.draft.ready) return@LaunchedEffect
        insertRefused = false
        missingInsertFiles = emptyList()
        // A kept record is never consumed. A stale source or refused destination clears only this
        // transient offer,
        // so choosing Restore again is always an explicit action and cannot duplicate appended
        // words.
        if (
            !request.stillCurrent() ||
                (request.attachments.isNotEmpty() &&
                    !actions.restoreAttachments(request.attachments, request.stillCurrent))
        ) {
            insertRefused = true
            chrome.insertDone(request.id)
            return@LaunchedEffect
        }
        if (!request.stillCurrent()) {
            insertRefused = true
            chrome.insertDone(request.id)
            return@LaunchedEffect
        }
        val current = field.text.toString()
        if (request.onlyIfEmpty && (current.isNotBlank() || state.draft.attachments.isNotEmpty())) {
            chrome.insertDone(request.id)
            return@LaunchedEffect
        }
        val next =
            when {
                request.text.isEmpty() -> current
                request.replace || current.isBlank() -> request.text
                else -> current.trimEnd() + "\n" + request.text
            }
        field.edit { replace(0, length, next) }
        editor.userEditSerial += 1
        editor.lastReported = next
        repeatRevision = null
        actions.draftChanged(next)
        missingInsertFiles = request.missingAttachments
        if (request.replace || request.attachments.isNotEmpty()) focus.requestFocus()
        chrome.insertDone(request.id)
    }
    val busy = chrome.busy()
    val scope = rememberCoroutineScope()
    val lift = LocalKitPromptLift.current
    var followed by remember(state.targetKey) { mutableStateOf<FollowedSend?>(null) }
    val send: () -> Unit = {
        val text = field.text.toString()
        val liftId = lift?.offer(text)
        // P-003: the words leave the box at the tap (they are on their bubble now). The draft owner
        // keeps them until the server acknowledges the send, so this clear is not reported; its
        // clear revision empties the field for good. A send that does not go puts them back.
        val serial = editor.userEditSerial
        field.edit { replace(0, length, "") }
        editor.lastReported = ""
        scope.launch {
            if (actions.send(text)) {
                // A local admission is not a server acknowledgement. The draft owner publishes its
                // clear revision.
                followed = FollowedSend(text, serial)
                chrome.onSent()
            } else {
                if (liftId != null) lift?.cancel(liftId)
                if (SendFieldRules.restoreAfterFailedSend(field.text.toString(), serial, editor.userEditSerial)) {
                    field.edit { replace(0, length, text) }
                    editor.lastReported = text
                }
            }
        }
    }
    // P-003: an admitted send is followed until the draft owner clears it; one that later fails or
    // ends uncertain puts its words back (the uncertain one with its "may already have been sent"
    // line).
    LaunchedEffect(followed, state.draft) {
        val sent = followed ?: return@LaunchedEffect
        if (state.draft.sendInFlight) sent.seenInFlight = true
        val decision =
            SendFieldRules.afterSend(
                sent.text,
                sent.seenInFlight,
                state.draft,
                userEdited = editor.userEditSerial != sent.serial,
            )
        when (decision) {
            SendFieldRules.AfterSend.Wait -> Unit
            SendFieldRules.AfterSend.Done -> followed = null
            SendFieldRules.AfterSend.Restore -> {
                followed = null
                if (field.text.isEmpty()) {
                    field.edit { replace(0, length, state.draft.text) }
                    editor.lastReported = state.draft.text
                }
            }
        }
    }
    // P12-VOICE: the voice slot types into this field and sends through this composer's one send
    // path.
    val canSend =
        state.available &&
            editorReady &&
            state.draft.ready &&
            state.draft.failure == null &&
            !state.draft.sendBlocked &&
            !state.pickingFile &&
            !state.sending
    val voiceField =
        KitVoiceField(
            key = state.targetKey,
            text = { field.text.toString() },
            selection = { field.selection.let { it.min..it.max } },
            replace = { words ->
                field.edit { replace(0, length, words) }
                editor.userEditSerial += 1
                editor.lastReported = words
                actions.draftChanged(words)
            },
            send = send,
            canSend = { canSend },
            conversationRef = conversationRef,
        )
    var modelsOpen by remember(state.targetKey) { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    // Error card -> "Choose another model": open the list once, then acknowledge.
    val pickerId = chrome.modelPickerRequest()
    LaunchedEffect(pickerId) {
        if (pickerId != null) {
            focusManager.clearFocus()
            modelsOpen = true
            chrome.modelPickerDone(pickerId)
        }
    }
    // S24-CHAT story 4: the full-screen editor works on this same field; what it changed becomes the
    // draft when it closes (Cancel puts the words back first, so nothing changes).
    var promptEditorOpen by remember(state.targetKey) { mutableStateOf(false) }
    val editorId = chrome.editorRequest()
    LaunchedEffect(editorId) {
        if (editorId != null) {
            if (promptEditor != null) {
                focusManager.clearFocus()
                promptEditorOpen = true
            }
            chrome.editorDone(editorId)
        }
    }
    promptEditor?.Editor(promptEditorOpen, field) {
        promptEditorOpen = false
        val words = field.text.toString()
        if (words != editor.lastReported) {
            editor.userEditSerial += 1
            editor.lastReported = words
            actions.draftChanged(words)
        }
    }
    // S24-CHAT story 3: a picture the viewer sent joins the draft like a pasted one.
    val sentPicture = chrome.pendingPicture()
    LaunchedEffect(sentPicture?.id) {
        val picture = sentPicture ?: return@LaunchedEffect
        actions.attachInserted(pictureMime(picture.name), picture.bytes)
        chrome.pictureDone(picture.id)
    }
    var addOpen by remember(state.targetKey) { mutableStateOf(false) }
    var toolsOpen by remember(state.targetKey) { mutableStateOf(false) }
    // S28 story 12: the "File from the project" sheet.
    var projectFilesOpen by remember(state.targetKey) { mutableStateOf(false) }
    val trayItems =
        ComposerTrayRules.items(
            state.attachmentsSupported,
            state.imagesOnly,
            state.filesAvailable,
            state.attachKinds,
        )
    val attachBlockedReason =
        if (ComposerTrayRules.attachBlocked(state))
            stringResource(Res.string.chatUiAvailableWhenTheCurrentRunFinishes)
        else null
    var historyOpen by remember(state.targetKey) { mutableStateOf(false) }
    // Clear draft text keeps attachments and can be undone (Flutter composer_tools.dart).
    var clearedText by remember(state.targetKey) { mutableStateOf<String?>(null) }
    // N-44: a saved Send later schedule takes the words with it: the draft clears and one note says why.
    var laterNote by remember(state.targetKey) { mutableStateOf(false) }
    val laterId = chrome.laterScheduled()
    LaunchedEffect(laterId) {
        if (laterId != null) {
            field.edit { replace(0, length, "") }
            editor.userEditSerial += 1
            editor.lastReported = ""
            actions.draftChanged("")
            laterNote = true
            chrome.laterShown(laterId)
        }
    }
    // Picking from the strip edits the field like typing would, then reports it (one draft truth).
    val replaceTail: (Int, String) -> Unit = { start, words ->
        field.edit { replace(start.coerceIn(0, length), length, words) }
        val now = field.text.toString()
        editor.userEditSerial += 1
        editor.lastReported = now
        actions.draftChanged(now)
    }
    Column(modifier) {
        // A one-off note, as in Flutter: dismissing hides this failure until a new one arrives.
        var dismissedSave by remember { mutableStateOf<Failure?>(null) }
        if (history.saveFailure != null && history.saveFailure !== dismissedSave)
            KitNotice(
                message = stringResource(Res.string.promptHistorySaveFailed),
                tone = StatusTone.Failure,
                dismiss =
                    KitLineAction(
                        stringResource(Res.string.workspaceDismissNotice),
                        { dismissedSave = history.saveFailure },
                    ),
                modifier = Modifier.testTag("prompt-history-save-failed"),
            )
        if (laterNote)
            KitNotice(
                message = stringResource(Res.string.composerLaterScheduled),
                tone = StatusTone.Neutral,
                dismiss =
                    KitLineAction(
                        stringResource(Res.string.composerPreviewAttachmentDismiss),
                        { laterNote = false },
                    ),
                modifier = Modifier.testTag("chat-later-scheduled"),
            )
        clearedText?.let { previous ->
            KitNotice(
                message = stringResource(Res.string.composerDraftCleared),
                tone = StatusTone.Neutral,
                actions =
                    listOf(
                        KitLineAction(
                            stringResource(Res.string.kitUndoAction),
                            {
                                if (field.text.isEmpty()) {
                                    field.edit { replace(0, length, previous) }
                                    editor.userEditSerial += 1
                                    editor.lastReported = previous
                                    actions.draftChanged(previous)
                                }
                                clearedText = null
                            },
                            testTag = "chat-draft-clear-undo",
                        )
                    ),
                dismiss =
                    KitLineAction(
                        stringResource(Res.string.composerPreviewAttachmentDismiss),
                        { clearedText = null },
                    ),
                modifier = Modifier.testTag("chat-draft-cleared"),
            )
        }
        if (insertRefused) {
            KitNotice(
                title = stringResource(Res.string.composerKeptRestoreTitle),
                message = stringResource(Res.string.composerKeptRestoreRefused),
                tone = StatusTone.Attention,
            )
        }
        if (missingInsertFiles.isNotEmpty()) {
            KitNotice(
                title = stringResource(Res.string.composerKeptRestoreTitle),
                message =
                    stringResource(
                        Res.string.composerKeptMissingFiles,
                        missingInsertFiles.joinToString(", "),
                    ),
                tone = StatusTone.Attention,
            )
        }
        if (state.draft.failure != null || SendFieldRules.showsAlreadySent(state.draft)) {
            KitErrorNotice(
                message =
                    stringResource(
                        if (SendFieldRules.showsAlreadySent(state.draft)) Res.string.chatDraftAlreadySent
                        else Res.string.draftSaveFailed
                    ),
                retry =
                    when {
                        state.canRepeat ->
                            KitLineAction(
                                stringResource(Res.string.chatDraftRepeatAction),
                                { repeatRevision = state.draft.editRevision },
                            )
                        SendFieldRules.showsAlreadySent(state.draft) -> null
                        else ->
                            KitLineAction(
                                stringResource(Res.string.draftRetrySave),
                                actions::retrySave,
                            )
                    },
            )
        } else if (state.draft.heldBackAttachments.isNotEmpty()) {
            // B-144: the words are back; a saved file is kept in the draft but not shown yet.
            KitErrorNotice(
                message = stringResource(Res.string.draftAttachmentsFailed),
                retry =
                    KitLineAction(stringResource(Res.string.draftRetrySave), actions::retrySave),
                modifier = Modifier.testTag("chat-draft-files-held"),
            )
        }
        val attachmentFailure = state.attachmentFailure
        if (attachmentFailure != null) {
            KitNotice(
                message = attachmentWords(attachmentFailure),
                tone = StatusTone.Failure,
                actions =
                    listOf(
                        // E05-S01: a camera refusal is fixed in Android settings, not by picking a file.
                        if (attachmentFailure.isCameraRefusal())
                            KitLineAction(
                                stringResource(Res.string.agentErrorOpenSettings),
                                actions::openCameraSettings,
                            )
                        else
                            KitLineAction(
                                stringResource(Res.string.composerPreviewAttachmentRetry),
                                actions::attachFile,
                            )
                    ),
                dismiss =
                    KitLineAction(
                        stringResource(Res.string.composerPreviewAttachmentDismiss),
                        actions::dismissAttachmentFailure,
                    ),
                modifier = Modifier.testTag("chat-attachment-failure"),
            )
        }
        // E20-S08: the phrases only, on one line that scrolls (the kit's phrase row); hiding one is its
        // long-press menu, and Settings › Phrase suggestions is the visible way back. A "Hide <phrase>" chip
        // beside every phrase doubled the row and wrapped it to two lines.
        if (state.phrases.isNotEmpty() && !chrome.requestWaiting()) {
            val inserts = state.phrases.associateWith { stringResource(Res.string.phraseSuggestionsInsert, it) }
            val menus = state.phrases.associateWith { stringResource(Res.string.phraseSuggestionsOptions, it) }
            KitPhraseChips(
                phrases = state.phrases,
                label = stringResource(Res.string.phraseChipsRowLabel),
                insertLabel = { inserts[it] ?: it },
                menuLabel = { menus[it] ?: it },
                hideLabel = stringResource(Res.string.phraseChipsHide),
                onChoose = { phrase ->
                    if (field.text.isEmpty() && state.draft.attachments.isEmpty()) {
                        field.edit { replace(0, length, phrase) }
                        actions.draftChanged(phrase)
                    }
                },
                onHide = actions::hidePhrase,
                modifier = Modifier.testTag("chat-phrase-chips"),
            )
        }
        // E20-S20: the tray lives inside the message box (KitComposerBox `above`, Flutter concept C); its More, and
        // "+" on a server that takes no files, open the tools list (Flutter `_openTools`).
        val words = field.text.toString()
        val clearWords: () -> Unit = {
            clearedText = words
            field.edit { replace(0, length, "") }
            editor.userEditSerial += 1
            editor.lastReported = ""
            repeatRevision = null
            actions.draftChanged("")
        }
        // Flutter keeps "Voice conversation" under the More list's "More tools", never in the tray.
        val conversationRow: (@Composable (close: () -> Unit) -> Unit)? =
            if (voice == null) null
            else ({ close -> voice.Options(voiceField, Modifier.fillMaxWidth(), close) })
        ComposerToolsSheet(
            visible = toolsOpen,
            onDismiss = { toolsOpen = false },
            choices =
                ComposerToolsChoices(
                    state = state,
                    inTray = trayItems,
                    agentName = chrome.agentName(),
                    attachCount = state.draft.attachments.size,
                    blockedReason = attachBlockedReason,
                    attach = if (state.filesAvailable) actions::attachFile else null,
                    photos =
                        if (AttachKind.Photos in state.attachKinds) {
                            { actions.attach(AttachKind.Photos) }
                        } else null,
                    camera =
                        if (AttachKind.Camera in state.attachKinds) {
                            { actions.attach(AttachKind.Camera) }
                        } else null,
                    commands = chrome.openCommands,
                    promptEditor =
                        promptEditor?.let {
                            {
                                focusManager.clearFocus()
                                promptEditorOpen = true
                            }
                        },
                    reuse =
                        refreshHistory?.let { refresh ->
                            {
                                refresh()
                                historyOpen = true
                            }
                        },
                    saved = chrome.openSavedPrompts,
                    sendLater =
                        chrome.sendLater
                            ?.takeIf { words.isNotBlank() }
                            ?.let { later ->
                                {
                                    // N-44: the keyboard goes first, so the sheet's wheel is never under its button.
                                    focusManager.clearFocus()
                                    later(words)
                                }
                            },
                    clearText = clearWords.takeIf { words.isNotEmpty() },
                    conversation = conversationRow,
                    modelWords = modelWords(state, chrome),
                    model = {
                        focusManager.clearFocus()
                        modelsOpen = true
                    },
                    settings = chrome.promptSettings,
                    // F: composer_tools.dart:150-157 and chat_mentions.dart `_openProjectFiles`: "File from the
                    // project" opens the project's files; the picked one is written `@path ` and staged.
                    projectFile =
                        if (state.filesAvailable) {
                            {
                                focusManager.clearFocus()
                                projectFilesOpen = true
                            }
                        } else null,
                ),
        )
        ComposerProjectFileSheet(
            visible = projectFilesOpen,
            search = actions::searchProjectFiles,
            onPick = { path ->
                projectFilesOpen = false
                val text = field.text.toString()
                val lead = if (text.isEmpty() || text.endsWith(' ') || text.endsWith('\n')) "" else " "
                field.edit { append("$lead@$path ") }
                editor.userEditSerial += 1
                val typed = field.text.toString()
                editor.lastReported = typed
                actions.draftChanged(typed)
                actions.mentionFile(path)
                focus.requestFocus()
            },
            onDismiss = { projectFilesOpen = false },
        )
        voice?.Panel(voiceField, Modifier.fillMaxWidth())
        if (state.draft.attachments.isNotEmpty()) {
            KitChipWrap(Modifier.fillMaxWidth().testTag("chat-attachments")) {
                state.draft.attachments.forEachIndexed { index, attachment ->
                    KitChip(
                        label = attachment.filename,
                        kind = KitChipKind.Removable,
                        // The kit's "Remove <name>" (Flutter kit_composer_chips.dart:907 kitChipRemove), so
                        // TalkBack says which file goes.
                        onRemove = { actions.removeAttachment(index) },
                        modifier = Modifier.testTag("chat-attachment-$index"),
                    )
                }
            }
            KitText(
                stringResource(Res.string.composerFullAttachmentsLocal),
                role = io.github.eslamasabry.oc.ui.kit.KitTextRole.Supporting,
            )
        }
        val query = ComposerQuery.of(field.text.toString())
        // S30 story 2 (Flutter `composer.dart` `_suggestions`): a `/word` lists the app's own actions and the
        // server's commands together, best match first, five at most, then "Show all" opens the grouped,
        // searchable Commands sheet. A server command fills the box with `/name `; an app action leaves it and runs.
        val slashShown =
            if (query is ComposerQuery.Command) SlashMenu.rank(chrome.slashCommands(), query.query) else emptyList()
        if (slashShown.isNotEmpty()) {
            KitRowGroup(Modifier.fillMaxWidth().testTag("chat-slash-menu")) {
                slashShown.take(SlashMenu.VISIBLE).forEach { command ->
                    KitRow(
                        title = "/" + command.slash,
                        supporting = command.description ?: command.title.takeIf { it != "/" + command.slash },
                        modifier = Modifier.testTag("chat-slash-${command.key}"),
                        onClick = {
                            if (command.inserts) {
                                replaceTail(0, "/${command.slash} ")
                                focus.requestFocus()
                            } else {
                                if (field.text.toString().trimStart().startsWith("/")) replaceTail(0, "")
                                command.onRun()
                            }
                        },
                    )
                }
            }
            val showAll = chrome.openCommands
            if (slashShown.size > SlashMenu.VISIBLE && showAll != null)
                KitButton(
                    label = stringResource(Res.string.kitSuggestionsShowAll),
                    onClick = showAll,
                    role = KitButtonRole.Tertiary,
                    modifier = Modifier.testTag("chat-slash-show-all"),
                )
        } else if (state.commandMatches.isNotEmpty() && query is ComposerQuery.Command) {
            KitChipWrap(Modifier.fillMaxWidth().testTag("chat-command-suggestions")) {
                state.commandMatches.forEach { command ->
                    KitChip(
                        label = "/${command.name}",
                        kind = KitChipKind.Action,
                        onClick = { replaceTail(0, "/${command.name} ") },
                    )
                }
            }
        }
        // E04-S01: sub-agents lead the `@` strip (Flutter order); a pick writes `@id ` over the word being typed
        // and the send turns it into an agent part. TalkBack hears the agent and what it does.
        val agentsShown = state.agentMatches.isNotEmpty() && query is ComposerQuery.File
        KitReveal(agentsShown) {
            KitChipWrap(Modifier.fillMaxWidth().testTag("chat-agent-suggestions")) {
                val delegate = stringResource(Res.string.chatUiDelegateThisPrompt)
                state.agentMatches.forEach { agent ->
                    KitChip(
                        label = "@${agent.id}",
                        kind = KitChipKind.Action,
                        spoken = "@${agent.id}, ${agent.description?.takeIf { it.isNotBlank() } ?: delegate}",
                        onClick = {
                            val start = (ComposerQuery.of(field.text.toString()) as? ComposerQuery.File)?.start
                            if (start != null) replaceTail(start, "@${agent.id} ")
                        },
                        modifier = Modifier.testTag("chat-agent-${agent.id}"),
                    )
                }
            }
        }
        if (state.fileMatches.isNotEmpty() && query is ComposerQuery.File) {
            KitChipWrap(Modifier.fillMaxWidth().testTag("chat-file-suggestions")) {
                state.fileMatches.forEach { path ->
                    KitChip(
                        label = "@$path",
                        kind = KitChipKind.Action,
                        onClick = {
                            replaceTail(query.start, "@$path ")
                            actions.mentionFile(path)
                        },
                    )
                }
            }
        }
        // B8 (QA wave 1): while a reply runs with words typed, Stop and "Add to this turn" sit on their own row
        // under a full-width field, so the message box never shrinks to a sliver. Otherwise the box is Flutter's
        // one row: the plus, the words, then the mic, Send, or the ringed Stop while a reply runs. With words typed
        // during a run, Send stays at the end (they join the turn) and Stop moves beside the plus, away from the
        // thumb (Flutter `_stopLeads`).
        val waitingRequest = chrome.requestWaiting()
        val stopShown = busy || waitingRequest
        val typed = field.text.isNotBlank() || state.draft.attachments.isNotEmpty()
        val sendReady =
            state.available &&
                editorReady &&
                state.draft.ready &&
                state.draft.failure == null &&
                !state.draft.sendBlocked &&
                !state.pickingFile
        // Voice: while words are drafted or the agent works, a small mic stays in the field and the
        // big
        // round button is Send (or Stop). The mic stays where the hold began until voice ends, so
        // the
        // button never changes under a finger.
        val voiceActive = voice?.isActive(voiceField) == true
        val inlineAtStart = remember(voiceActive) { typed || stopShown }
        val inlineMic = if (voiceActive) inlineAtStart else typed || stopShown
        val primaryIsStop = inlineMic && stopShown && !(typed && !waitingRequest)
        // S28 story 14 (Flutter `_stopBeforeSend`): a hung reply on a server that does not steer is stopped first;
        // the words go once it has stopped, and nothing goes if it does not stop.
        val stopsFirst = SendFieldRules.stopsBeforeSend(busy, state.steers, chrome.stuck())
        val stopAndSend: () -> Unit = {
            scope.launch {
                chrome.onStop()
                val stopped =
                    withTimeoutOrNull(SendFieldRules.STOP_AND_SEND_WAIT_MS) {
                        snapshotFlow { chrome.busy() }.first { !it }
                    }
                if (stopped != null) send()
            }
        }
        val sendLabel =
            stringResource(
                when {
                    state.sending -> Res.string.kitComposerSending
                    busy && state.steers -> Res.string.kitComposerAddToTurn
                    busy -> Res.string.kitComposerSendAfter
                    else -> Res.string.kitComposerSend
                }
            )
        val stopLabel =
            stringResource(
                if (chrome.stopping()) Res.string.phoneServerCardStopping
                else Res.string.kitTurnLiveStopShort
            )
        // S24-CHAT story 6 (Flutter `_stopMenu`): holding Stop offers "Stop after this step" where the
        // server steers the running turn; a tap still stops at once.
        val afterStep = chrome.stopAfterStep()
        val afterStepLabel = stringResource(Res.string.chatStopAfterStep)
        // S28 story 15: hold Stop is taught once a reply has run for a while and the hold offers something.
        val stopTipUsed = rememberTipUsed(TeachingIds.StopAfterStep)
        OfferComposerTip(
            TeachingIds.StopAfterStep,
            due = ComposerTipRules.stopTipDue(busy, afterStep != null),
            blocked = chrome.requestWaiting(),
            afterMs = ComposerTipRules.STOP_TIP_AFTER_MS,
        )
        var stopMenu by remember { mutableStateOf(false) }
        if (afterStep == null && stopMenu) stopMenu = false
        val stopPart: @Composable () -> Unit = {
            KitMenu(
                expanded = stopMenu,
                items =
                    listOfNotNull(
                        afterStep?.let { run ->
                            KitMenuItem(afterStepLabel, onSelect = { stopMenu = false; stopTipUsed(); run() })
                        }
                    ),
                onDismissRequest = { stopMenu = false },
            ) {
                ComposerTip(TeachingIds.StopAfterStep, stringResource(Res.string.tipStopAfterStep)) {
                    KitComposerStopRing(
                        label = stopLabel,
                        onClick = chrome.onStop,
                        stopping = chrome.stopping(),
                        onLongClick = afterStep?.let { { stopMenu = true } },
                        onLongClickLabel = afterStepLabel,
                        modifier = Modifier.testTag("chat-stop-button"),
                    )
                }
            }
        }
        // F1 (scoreboard 2026-11-07), Flutter's layout (chat_composer_region.dart:44-77, kit_composer.dart:796-808):
        // the approval and model chips ride one strip ABOVE the message box (approval at the start, the model at
        // the end), and the box is one row (plus, the words, mic or Send) until the words outgrow it. Stop keeps
        // the native Send/Stop morph on the round target.
        val stopLeads = stopShown && typed && !waitingRequest
        // S28 story 15: hold Send is taught while words wait, no reply runs and Send offers Send later.
        val sendTipUsed = rememberTipUsed(TeachingIds.SendLater)
        OfferComposerTip(
            TeachingIds.SendLater,
            due = ComposerTipRules.sendTipDue(typed, busy, chrome.sendLater != null && sendReady),
            blocked = waitingRequest,
        )
        KitComposerStatusStrip(
            hasChips = true,
            modifier = Modifier.padding(bottom = COMPOSER_STRIP_GAP.dp).testTag("composer-status-strip"),
            model = {
                val chipWords = modelWords(state, chrome)
                val modelName = stringResource(Res.string.composerPreviewModelButton)
                val openModels = {
                    // B3: the keyboard never stays open over the picker.
                    focusManager.clearFocus()
                    modelsOpen = true
                }
                // Spoken as before: "Model", then the model's words as its state (09-10_3), one node around the kit's
                // chip; the chip's own label stays the visible words.
                Box(
                    Modifier.testTag("chat-model-button").clearAndSetSemantics {
                        contentDescription = modelName
                        stateDescription = chipWords
                        role = Role.Button
                        onClick { openModels(); true }
                    }
                ) {
                    KitComposerModelChip(label = chipWords, onClick = openModels)
                }
            },
        ) {
            chrome.rowChips()
        }
        // S24-CHAT story 5: a picture the keyboard inserts (a GIF, a sticker, the clipboard chip) joins
        // the draft like a picked photo; only where this agent takes pictures.
        val takePicture: ((KitInsertedPicture) -> Unit)? =
            insertedPictures
                ?.takeIf { state.attachmentsSupported }
                ?.let { reader ->
                    { picture ->
                        scope.launch {
                            when (val read = reader.read(picture)) {
                                is Outcome.Ok -> read.value.firstOrNull()?.let { actions.attachInserted(it.mime, it.bytes) }
                                is Outcome.Failed -> actions.insertFailed(read.failure)
                            }
                        }
                    }
                }
        KitPictureReceiver(takePicture, Modifier.fillMaxWidth()) {
        // B8: Stop beside the plus while words wait for the run: the words take the whole width above them.
        KitComposerRowFit(words, leadingTargets = if (stopLeads) 2 else 1, stack = stopLeads) { stacked ->
            KitComposerBox(
                running = busy && !waitingRequest,
                stacked = stacked,
                hasLeading = true,
                above = {
                    ComposerLimitArea(limits, state, conversationRef)
                    KitComposerTray(
                        open = addOpen,
                        actions =
                            trayItems.map { item ->
                                when (item) {
                                    TrayItem.Photos ->
                                        KitComposerTrayAction(
                                            stringResource(Res.string.composerTrayPhotos),
                                            KitIcons.images,
                                            {
                                                addOpen = false
                                                actions.attach(AttachKind.Photos)
                                            },
                                            attachBlockedReason,
                                            "composer-tool-gallery",
                                        )
                                    TrayItem.Camera ->
                                        KitComposerTrayAction(
                                            stringResource(Res.string.composerTrayCamera),
                                            KitIcons.camera,
                                            {
                                                addOpen = false
                                                actions.attach(AttachKind.Camera)
                                            },
                                            attachBlockedReason,
                                            "composer-tool-camera",
                                        )
                                    TrayItem.File ->
                                        KitComposerTrayAction(
                                            stringResource(Res.string.composerTrayFile),
                                            KitIcons.attach,
                                            {
                                                addOpen = false
                                                actions.attachFile()
                                            },
                                            attachBlockedReason,
                                            "composer-tool-attach",
                                        )
                                    TrayItem.More ->
                                        KitComposerTrayAction(
                                            stringResource(Res.string.composerTrayMore),
                                            KitIcons.more,
                                            {
                                                addOpen = false
                                                toolsOpen = true
                                            },
                                            testTag = "composer-tray-more",
                                        )
                                }
                            },
                    )
                },
                field = {
                    // Keep the editor mounted while an approval or form waits. Submission stays
                    // gated below; hiding it leaves an open conversation with only chips.
                    KitComposerFieldRow(
                        tail =
                            if (inlineMic && voice != null) {
                                { voice.InlineMic(voiceField, Modifier) }
                            } else null
                    ) {
                        KitField(
                            state = field,
                            // While a reply runs on a steering server the words join it
                            // (B-PROTO-025).
                            label =
                                if (busy && state.steers)
                                    stringResource(Res.string.composerFullSteerHint)
                                else
                                    stringResource(
                                        Res.string.upgradeComposerAsk,
                                        state.models.agent?.takeUnless {
                                            it.lowercase() in
                                                setOf(
                                                    "build",
                                                    "plan",
                                                    "compaction",
                                                    "title",
                                                    "summary",
                                                )
                                        } ?: chrome.agentName(),
                                    ),
                            kind = KitFieldKind.Multiline,
                            // B6: one line when empty, so the conversation keeps its room above the
                            // keyboard.
                            multilineLines = 1..5,
                            composer = true,
                            imeAction = ImeAction.Send,
                            onSubmit = {
                                if (
                                    sendReady &&
                                        typed &&
                                        !voiceActive &&
                                        !state.sending &&
                                        !waitingRequest
                                )
                                    send()
                            },
                            labelShown = false,
                            framed = false,
                            focusRequester = focus,
                            enabled =
                                if (editorReady && state.draft.ready) KitEnabled.Enabled
                                else KitEnabled.Disabled(stringResource(Res.string.chatDraftRestoring)),
                            onTextChanged = { accepted ->
                                editor.userEditSerial += 1
                                editor.lastReported = accepted
                                repeatRevision = null
                                actions.draftChanged(accepted)
                            },
                            modifier = Modifier.kitPromptOrigin().testTag("chat-composer-field"),
                        )
                    }
                },
                leading = {
                    // E20-S20: Flutter's "+" ("Attach and more", "Close" while open): it opens the tray, or the
                    // tools list straight away where the server takes no files. Never a dead control.
                    KitComposerAdd(
                        label =
                            stringResource(
                                if (addOpen && trayItems.isNotEmpty()) Res.string.kitComposerTrayClose
                                else Res.string.kitComposerTools
                            ),
                        onClick = {
                            if (trayItems.isEmpty()) toolsOpen = true else addOpen = !addOpen
                        },
                        open = addOpen && trayItems.isNotEmpty(),
                        modifier = Modifier.testTag("chat-attach-button"),
                    )
                    if (stopLeads) stopPart()
                },
                trailing = {
                    if (primaryIsStop) stopPart()
                    else
                        KitComposerPrimaryAction(
                            // Without voice the empty box shows a disabled Send, never an empty slot
                            // (Flutter kit_composer.dart:434 `_Trailing.sendDisabled`).
                            hasMessage = inlineMic || voice == null,
                            mic = { voice?.Mic(voiceField, Modifier) },
                            send = {
                                if (stopsFirst && typed && sendReady)
                                    KitStopAndSendPill(
                                        label = stringResource(Res.string.kitComposerStopAndSend),
                                        onClick = stopAndSend,
                                        modifier = Modifier.testTag("chat-stop-and-send"),
                                    )
                                else
                                ComposerTip(TeachingIds.SendLater, stringResource(Res.string.tipSendLater)) {
                                KitComposerSend(
                                    label = sendLabel,
                                    onClick = send,
                                    // F1: an empty box's Send is disabled (Flutter kit_composer.dart:434
                                    // `_Trailing.sendDisabled`): nothing to send yet.
                                    enabled =
                                        if (sendReady && typed && !voiceActive) KitEnabled.Enabled
                                        else
                                            KitEnabled.Disabled(
                                                stringResource(Res.string.kitComposerField)
                                            ),
                                    working = state.sending,
                                    onLongClick =
                                        chrome.sendLater
                                            ?.takeIf { typed && sendReady }
                                            ?.let { later ->
                                                {
                                                    // N-44: the keyboard goes first (the wheel stays in sight).
                                                    focusManager.clearFocus()
                                                    sendTipUsed()
                                                    later(field.text.toString())
                                                }
                                            },
                                    onLongClickLabel = stringResource(Res.string.tipSendLaterTitle),
                                    modifier = Modifier.testTag("chat-send-button"),
                                )
                                }
                            },
                        )
                },
            )
        }
        }
    }
    ModelSheet(
        visible = modelsOpen,
        targetKey = state.targetKey,
        models = state.models,
        actions = actions,
        onDismiss = { modelsOpen = false },
    )
    ComposerHistorySheet(
        visible = historyOpen,
        state = history,
        onDismiss = { historyOpen = false },
        onRetry = { refreshHistory?.invoke() },
        onChoose = { words ->
            val current = field.text.toString().trimEnd()
            val next = if (current.isEmpty()) words else "$current\n\n$words"
            field.edit { replace(0, length, next) }
            editor.userEditSerial += 1
            editor.lastReported = next
            repeatRevision = null
            actions.draftChanged(next)
            historyOpen = false
            focus.requestFocus()
        },
    )
    LaunchedEffect(state.targetKey, state.draft.editRevision, state.canRepeat) {
        if (!state.canRepeat || repeatRevision != state.draft.editRevision) repeatRevision = null
    }
    val repeating = repeatRevision
    // Closed confirmations are absent: this composer relies on ChatScreen's real modal host only
    // while open.
    if (repeating != null && state.canRepeat && repeating == state.draft.editRevision) {
        KitConfirm(
            spec =
                KitConfirmSpec(
                    title = stringResource(Res.string.chatDraftRepeatTitle),
                    body = stringResource(Res.string.chatDraftRepeatBody),
                    confirmLabel = stringResource(Res.string.chatDraftRepeatAction),
                    kind = KitConfirmKind.Neutral,
                ),
            onConfirm = {
                repeatRevision = null
                val text = field.text.toString()
                if (text == state.draft.text)
                    scope.launch {
                        if (actions.sendAgain(text, repeating)) chrome.onSent()
                    }
            },
            onCancel = { repeatRevision = null },
        )
    }
}

/**
 * The room between the chips strip and the message box (F: the strip's 48 dp line centres its 32 dp chips; Flutter's
 * golden `chat_approval_ask` has the chips' bottom 12 px above the box, 8 of them inside the line).
 */
private const val COMPOSER_STRIP_GAP = 4

/** "Model · level · agent": the level and agent only when chosen (Flutter composer.dart:795). */
/** The model's words, as the chip and the "+" sheet's Model row say them ("Server default", "Sonnet 4 · High"). */
@Composable
private fun modelWords(state: BasicComposerUi, chrome: ComposerChrome): String {
    val named =
        modelLabel(
            state.models.selected?.label
                // QA wave 4 B13: "Server default" only once the server said so.
                ?: stringResource(
                    if (state.models.usesDefault) Res.string.composerPreviewModelDefault
                    else Res.string.composerPreviewModelButton
                ),
            state.models.variant,
            state.models.agent,
        )
    // N-52: "DeepSeek V4.1 Flash Free · Free" said it twice.
    val free = stringResource(Res.string.upgradeComposerFree)
    val freeWord = free.trimStart(' ', '·').trim()
    return named + if (chrome.freeModel() && !named.contains(freeWord, ignoreCase = true)) free else ""
}

private fun modelLabel(model: String, level: String, agent: String?): String =
    listOfNotNull(agent?.takeIf { it.isNotEmpty() }, model, level.takeIf { it.isNotEmpty() })
        .joinToString(" · ")

internal fun Failure.isCameraRefusal(): Boolean =
    this is Failure.Media &&
        (problem == MediaProblem.CameraDenied || problem == MediaProblem.CameraDeniedForever)

/** Plain words per failure kind; raw details stay in the Failure's redacted fold. */
@Composable
private fun attachmentWords(failure: Failure): String =
    stringResource(
        when (failure) {
            is Failure.StorageRefused -> Res.string.composerPreviewAttachmentStorage
            is Failure.Refused -> Res.string.composerFullStale
            is Failure.Unavailable ->
                if (failure.feature.name == "clipboardImage") Res.string.composerFullPasteEmpty
                else Res.string.composerPreviewAttachmentFailed
            is Failure.Media ->
                when (failure.problem) {
                    MediaProblem.AttachmentTooLarge,
                    MediaProblem.AttachmentsTotalTooLarge ->
                        Res.string.composerPreviewAttachmentTooLarge
                    MediaProblem.DraftFull -> Res.string.composerFullDraftFull
                    MediaProblem.CameraDenied,
                    MediaProblem.CameraDeniedForever -> Res.string.composerFullPhotoDenied
                    MediaProblem.PhotoTooLarge,
                    MediaProblem.ClipboardImageTooLarge ->
                        Res.string.composerPreviewAttachmentTooLarge
                    MediaProblem.AttachmentUnsupported,
                    MediaProblem.OfficeUnreadable,
                    MediaProblem.OfficeEmpty -> Res.string.composerPreviewAttachmentUnsupported
                    else -> Res.string.composerPreviewAttachmentFailed
                }
            else -> Res.string.composerPreviewAttachmentFailed
        }
    )

/** P-003: words that left the field at the tap, followed until their send settles. */
private class FollowedSend(val text: String, val serial: Long) {
    var seenInFlight: Boolean = false
}

/**
 * Plain bookkeeping: synchronous input reporting does not mutate Compose state or the field
 * transaction.
 */
private class DraftEditorState {
    var restoreRevision: Long? = null
    var lastReported: String? = null
    var userEditSerial: Long = 0
}

/** The type a picture's name says (the intake checks the bytes again); PNG when it says none. */
internal fun pictureMime(name: String): String =
    when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        else -> "image/png"
    }

/**
 * B-181: the limit strip over the field, for the open conversation's provider and model. Nothing without a binding,
 * without a chosen model, or while the readings say nothing.
 */
@Composable
private fun ComposerLimitArea(limits: ComposerLimits?, state: BasicComposerUi, conversationRef: ConversationRef?) {
    if (limits == null) return
    val selected = state.models.selected
    val profileId = conversationRef?.profileId?.raw
    val target = composerLimitTarget(selected?.providerId, selected?.modelId, profileId)
    val targets = remember(limits) { kotlinx.coroutines.flow.MutableStateFlow(target) }
    androidx.compose.runtime.SideEffect { targets.value = target }
    val flow = remember(limits, targets) { limits.notice(targets) }
    val notice by flow.collectAsStateWithLifecycle(null)
    val shown = notice
    KitComposerLimitStrip(
        notice = shown?.toKit(),
        onViewUsage = { if (shown != null && profileId != null) limits.viewUsage(shown, profileId) },
        onDismiss = { if (shown != null) limits.dismiss(shown) },
    )
}
