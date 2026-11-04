package io.github.eslamasabry.oc.feature.chat.composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.domain.platform.PromptFilePicker
import io.github.eslamasabry.oc.feature.chat.composer.presentation.AttachKind
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerActions
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerUi
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerViewModel
import io.github.eslamasabry.oc.feature.chat.composer.presentation.ComposerQuery
import io.github.eslamasabry.oc.feature.chat.composer.presentation.ModelPicker
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitRow
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipKind
import io.github.eslamasabry.oc.ui.kit.chip.KitChipWrap
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceRow
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.input.KitSearchField
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerVoice
import io.github.eslamasabry.oc.ui.kitchat.composer.KitVoiceField
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.chatDraftAlreadySent
import io.github.eslamasabry.oc.ui.words.res.chatDraftRepeatAction
import io.github.eslamasabry.oc.ui.words.res.chatDraftRepeatBody
import io.github.eslamasabry.oc.ui.words.res.chatDraftRepeatTitle
import io.github.eslamasabry.oc.ui.words.res.chatDraftRestoring
import io.github.eslamasabry.oc.ui.words.res.chatModelsCurrent
import io.github.eslamasabry.oc.ui.words.res.chatModelsNoMatch
import io.github.eslamasabry.oc.ui.words.res.chatModelsRecent
import io.github.eslamasabry.oc.ui.words.res.chatModelsSearch
import io.github.eslamasabry.oc.ui.words.res.composerFullAddTitle
import io.github.eslamasabry.oc.ui.words.res.composerFullAgent
import io.github.eslamasabry.oc.ui.words.res.composerFullAttach
import io.github.eslamasabry.oc.ui.words.res.composerFullAttachmentsLocal
import io.github.eslamasabry.oc.ui.words.res.composerFullDraftFull
import io.github.eslamasabry.oc.ui.words.res.composerFullModelChangeFailed
import io.github.eslamasabry.oc.ui.words.res.composerFullPasteEmpty
import io.github.eslamasabry.oc.ui.words.res.composerFullPasteImage
import io.github.eslamasabry.oc.ui.words.res.composerFullPhotoDenied
import io.github.eslamasabry.oc.ui.words.res.composerFullPhotoLibrary
import io.github.eslamasabry.oc.ui.words.res.composerFullStale
import io.github.eslamasabry.oc.ui.words.res.composerFullSteerHint
import io.github.eslamasabry.oc.ui.words.res.composerFullTakePhoto
import io.github.eslamasabry.oc.ui.words.res.composerFullThinking
import io.github.eslamasabry.oc.ui.words.res.composerFullThinkingDefault
import io.github.eslamasabry.oc.ui.words.res.composerPreviewAttachFile
import io.github.eslamasabry.oc.ui.words.res.composerPreviewAttachModelWithoutFiles
import io.github.eslamasabry.oc.ui.words.res.composerPreviewAttachNoPicker
import io.github.eslamasabry.oc.ui.words.res.composerPreviewAttachmentDismiss
import io.github.eslamasabry.oc.ui.words.res.composerPreviewAttachmentFailed
import io.github.eslamasabry.oc.ui.words.res.composerPreviewAttachmentRemove
import io.github.eslamasabry.oc.ui.words.res.composerPreviewAttachmentRetry
import io.github.eslamasabry.oc.ui.words.res.composerPreviewAttachmentStorage
import io.github.eslamasabry.oc.ui.words.res.composerPreviewAttachmentTooLarge
import io.github.eslamasabry.oc.ui.words.res.composerPreviewAttachmentUnsupported
import io.github.eslamasabry.oc.ui.words.res.composerPreviewModelButton
import io.github.eslamasabry.oc.ui.words.res.composerPreviewModelDefault
import io.github.eslamasabry.oc.ui.words.res.composerPreviewModelsClose
import io.github.eslamasabry.oc.ui.words.res.composerPreviewModelsEmpty
import io.github.eslamasabry.oc.ui.words.res.composerPreviewModelsFailed
import io.github.eslamasabry.oc.ui.words.res.composerPreviewModelsLoading
import io.github.eslamasabry.oc.ui.words.res.composerPreviewModelsRetry
import io.github.eslamasabry.oc.ui.words.res.composerPreviewModelsTitle
import io.github.eslamasabry.oc.ui.words.res.draftRetrySave
import io.github.eslamasabry.oc.ui.words.res.draftSaveFailed
import io.github.eslamasabry.oc.ui.words.res.kitComposerAddToTurn
import io.github.eslamasabry.oc.ui.words.res.kitComposerField
import io.github.eslamasabry.oc.ui.words.res.kitComposerSend
import io.github.eslamasabry.oc.ui.words.res.kitComposerSendAfter
import io.github.eslamasabry.oc.ui.words.res.kitComposerSending
import io.github.eslamasabry.oc.ui.words.res.kitTurnLiveStopShort
import io.github.eslamasabry.oc.ui.words.res.phoneServerCardStopping
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * The slice-2 composer (plan 09 §6.3) extended by the full-composer slice (2026-11-02): the field,
 * Send and Stop in the same row while a reply runs, plus the visible model choice and file
 * attachments.
 */
@Inject
@ContributesBinding(AppScope::class)
public class BasicComposerHost(private val voices: Set<KitComposerVoice>) : ComposerHost {
    @Composable
    override fun Composer(target: ComposerTarget, chrome: ComposerChrome, modifier: Modifier) {
        val vm =
            assistedMetroViewModel<BasicComposerViewModel, BasicComposerViewModel.Factory>(
                key = "composer:${target.profileId}:${target.sessionId}"
            ) {
                create(target)
            }
        val state by vm.state.collectAsStateWithLifecycle()
        BasicComposer(state, vm, chrome, modifier, voice = voices.firstOrNull())
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
) {
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
    val insert = chrome.pendingInsert()
    LaunchedEffect(insert, editorReady, state.draft.ready) {
        val request = insert ?: return@LaunchedEffect
        if (!editorReady || !state.draft.ready) return@LaunchedEffect
        val current = field.text.toString()
        val next =
            if (request.replace || current.isBlank()) request.text
            else current.trimEnd() + "\n" + request.text
        field.edit { replace(0, length, next) }
        editor.userEditSerial += 1
        editor.lastReported = next
        repeatRevision = null
        actions.draftChanged(next)
        if (request.replace) focus.requestFocus()
        chrome.insertDone(request.id)
    }
    val busy = chrome.busy()
    val scope = rememberCoroutineScope()
    val send: () -> Unit = {
        val text = field.text.toString()
        scope.launch {
            if (actions.send(text)) {
                // A local admission is not a server acknowledgement. The draft owner publishes its
                // clear revision.
                chrome.onSent()
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
        )
    var modelsOpen by remember(state.targetKey) { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    var addOpen by remember(state.targetKey) { mutableStateOf(false) }
    val selectAndClose: (ComposerModel?) -> Unit = { choice ->
        scope.launch { if (actions.selectModel(choice)) modelsOpen = false }
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
        if (state.draft.failure != null || state.draft.sendBlocked) {
            KitErrorNotice(
                message =
                    stringResource(
                        if (state.draft.sendBlocked) Res.string.chatDraftAlreadySent
                        else Res.string.draftSaveFailed
                    ),
                retry =
                    when {
                        state.canRepeat ->
                            KitLineAction(
                                stringResource(Res.string.chatDraftRepeatAction),
                                { repeatRevision = state.draft.editRevision },
                            )
                        state.draft.sendBlocked -> null
                        else ->
                            KitLineAction(
                                stringResource(Res.string.draftRetrySave),
                                actions::retrySave,
                            )
                    },
            )
        }
        val attachmentFailure = state.attachmentFailure
        if (attachmentFailure != null) {
            KitNotice(
                message = attachmentWords(attachmentFailure),
                tone = StatusTone.Failure,
                actions =
                    listOf(
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
        Row(
            Modifier.fillMaxWidth().testTag("chat-composer-controls"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
        ) {
            KitButton(
                label =
                    modelLabel(
                        state.models.selected?.label
                            // QA wave 4 B13: "Server default" only once the server said so; before
                            // the session is
                            // read (after a restart) the chip claims nothing.
                            ?: stringResource(
                                if (state.models.usesDefault) Res.string.composerPreviewModelDefault
                                else Res.string.composerPreviewModelButton
                            ),
                        state.models.variant,
                        state.models.agent,
                    ),
                onClick = {
                    // B3: the keyboard never stays open over the picker.
                    focusManager.clearFocus()
                    modelsOpen = true
                },
                role = KitButtonRole.Secondary,
                semanticsLabel = stringResource(Res.string.composerPreviewModelButton),
                // QA wave 4 N2: a long model name shrinks first, so Attach and the approval chip
                // keep their room.
                modifier = Modifier.weight(1f, fill = false).testTag("chat-model-button"),
                maxLines = 1,
            )
            val attachReason =
                when {
                    !state.filesAvailable ->
                        stringResource(Res.string.composerPreviewAttachNoPicker)
                    state.models.selected?.supportsAttachments == false ->
                        stringResource(Res.string.composerPreviewAttachModelWithoutFiles)
                    else -> null
                }
            KitButton(
                label =
                    stringResource(
                        if (state.attachKinds.isEmpty()) Res.string.composerPreviewAttachFile
                        else Res.string.composerFullAttach
                    ),
                onClick = {
                    if (state.attachKinds.isEmpty()) actions.attachFile() else addOpen = true
                },
                role = KitButtonRole.Secondary,
                enabled =
                    if (attachReason != null) KitEnabled.Disabled(attachReason)
                    else KitEnabled.Enabled,
                working = state.pickingFile,
                modifier = Modifier.testTag("chat-attach-button"),
            )
            chrome.rowChips()
        }
        voice?.Panel(voiceField, Modifier.fillMaxWidth())
        if (state.draft.attachments.isNotEmpty()) {
            KitChipWrap(Modifier.fillMaxWidth().testTag("chat-attachments")) {
                state.draft.attachments.forEachIndexed { index, attachment ->
                    KitChip(
                        label = attachment.filename,
                        kind = KitChipKind.Removable,
                        removeLabel = stringResource(Res.string.composerPreviewAttachmentRemove),
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
        if (state.commandMatches.isNotEmpty() && query is ComposerQuery.Command) {
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
        // B8 (QA wave 1): while a reply runs, Stop and "Add to this turn" sit on their own row
        // under a full-width
        // field, so the message box never shrinks to a sliver.
        val stopShown = busy || chrome.requestWaiting()
        val fieldPart: @Composable (Modifier) -> Unit = { fieldModifier ->
            KitField(
                state = field,
                // While a reply runs on a steering server the words join it (B-PROTO-025).
                label =
                    stringResource(
                        if (busy && state.steers) Res.string.composerFullSteerHint
                        else Res.string.kitComposerField
                    ),
                kind = KitFieldKind.Multiline,
                // B6: one line when empty, so the conversation keeps its room above the keyboard.
                multilineLines = 1..6,
                // QA wave 4 B6: no "Message" row above the box; the words show inside it while it
                // is empty.
                labelShown = false,
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
                modifier = fieldModifier.testTag("chat-composer-field"),
            )
        }
        val stopPart: @Composable () -> Unit = {
            KitButton(
                label =
                    stringResource(
                        if (chrome.stopping()) Res.string.phoneServerCardStopping
                        else Res.string.kitTurnLiveStopShort
                    ),
                onClick = chrome.onStop,
                role = KitButtonRole.Secondary,
                working = chrome.stopping(),
                modifier = Modifier.testTag("chat-stop-button"),
            )
        }
        val sendPart: @Composable () -> Unit = {
            val label =
                when {
                    state.sending -> Res.string.kitComposerSending
                    busy && state.steers -> Res.string.kitComposerAddToTurn
                    busy -> Res.string.kitComposerSendAfter
                    else -> Res.string.kitComposerSend
                }
            if (!chrome.requestWaiting())
                KitButton(
                    label = stringResource(label),
                    onClick = send,
                    enabled =
                        if (
                            state.available &&
                                editorReady &&
                                state.draft.ready &&
                                state.draft.failure == null &&
                                !state.draft.sendBlocked &&
                                !state.pickingFile
                        )
                            KitEnabled.Enabled
                        else KitEnabled.Disabled(stringResource(Res.string.kitComposerField)),
                    working = state.sending,
                    haptic = KitHaptic.Send,
                    modifier = Modifier.testTag("chat-send-button"),
                )
        }
        if (stopShown) {
            if (!chrome.requestWaiting()) fieldPart(Modifier.fillMaxWidth())
            Row(
                Modifier.fillMaxWidth().testTag("chat-composer-turn-controls"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.S2.dp, Alignment.End),
            ) {
                if (!chrome.requestWaiting()) voice?.Mic(voiceField, Modifier) // P12-VOICE
                stopPart()
                sendPart()
            }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
            ) {
                fieldPart(Modifier.weight(1f))
                voice?.Mic(voiceField, Modifier) // P12-VOICE
                sendPart()
            }
        }
    }
    // The model list loads when the sheet opens empty; a retry after failure is the person's own
    // tap.
    LaunchedEffect(modelsOpen, state.targetKey) {
        if (modelsOpen && !state.models.loading && state.models.choices.isEmpty()) {
            actions.loadModels()
        }
    }
    KitSheet(
        visible = modelsOpen,
        onDismissRequest = { modelsOpen = false },
        title = stringResource(Res.string.composerPreviewModelsTitle),
        closeLabel = stringResource(Res.string.composerPreviewModelsClose),
    ) {
        val models = state.models
        when {
            models.loading && models.choices.isEmpty() ->
                KitNotice(
                    message = stringResource(Res.string.composerPreviewModelsLoading),
                    tone = StatusTone.Progress,
                )
            models.choices.isEmpty() && models.failure != null ->
                KitNotice(
                    message = stringResource(Res.string.composerPreviewModelsFailed),
                    tone = StatusTone.Failure,
                    actions =
                        listOf(
                            KitLineAction(
                                stringResource(Res.string.composerPreviewModelsRetry),
                                actions::loadModels,
                            )
                        ),
                )
            models.choices.isEmpty() ->
                KitNotice(message = stringResource(Res.string.composerPreviewModelsEmpty))
            else -> {
                if (models.failure != null)
                    KitNotice(
                        message = stringResource(Res.string.composerFullModelChangeFailed),
                        tone = StatusTone.Failure,
                    )
                // B3 (QA wave 1): a searchable list grouped by provider, recent picks first, the
                // current one marked.
                var modelQuery by remember(state.targetKey) { mutableStateOf("") }
                KitSearchField(
                    label = stringResource(Res.string.chatModelsSearch),
                    onSearch = { modelQuery = it },
                    modifier = Modifier.fillMaxWidth().testTag("chat-model-search"),
                )
                val currentTag = stringResource(Res.string.chatModelsCurrent)
                val sections =
                    remember(models, modelQuery) { ModelPicker.sections(models, modelQuery) }
                Column(Modifier.fillMaxWidth().testTag("chat-model-choices")) {
                    if (models.selected != null && modelQuery.isBlank()) {
                        KitChoiceRow(
                            title = stringResource(Res.string.composerPreviewModelDefault),
                            selected = models.usesDefault,
                            tag = if (models.usesDefault) currentTag else null,
                            onClick = { selectAndClose(null) },
                        )
                    }
                    if (sections.isEmpty()) KitText(stringResource(Res.string.chatModelsNoMatch))
                    sections.forEach { section ->
                        KitRowGroup(
                            label =
                                section.providerName?.let {
                                    providerRouteName(section.rows.first().model.providerId, it)
                                } ?: stringResource(Res.string.chatModelsRecent)
                        ) {
                            section.rows.forEach { pick ->
                                KitChoiceRow(
                                    title = pick.model.label,
                                    selected = pick.current,
                                    // In "Recent" the provider tells equal names apart.
                                    supporting =
                                        if (section.providerName == null)
                                            providerRouteName(
                                                pick.model.providerId,
                                                pick.model.providerName,
                                            )
                                        else null,
                                    tag = if (pick.current) currentTag else null,
                                    onClick = { selectAndClose(pick.model) },
                                    modifier = Modifier.testTag("chat-model-choice"),
                                )
                            }
                        }
                    }
                }
                val selected = models.selected
                if (selected != null && selected.variants.isNotEmpty()) {
                    KitSectionLabel(stringResource(Res.string.composerFullThinking))
                    KitChipWrap(Modifier.fillMaxWidth().testTag("chat-thinking-choices")) {
                        (listOf("") + selected.variants).forEach { level ->
                            KitChip(
                                label =
                                    if (level.isEmpty())
                                        stringResource(Res.string.composerFullThinkingDefault)
                                    else level,
                                kind = KitChipKind.Action,
                                selected = level == models.variant,
                                onClick = { scope.launch { actions.selectModel(selected, level) } },
                            )
                        }
                    }
                }
                if (models.agents.isNotEmpty()) {
                    KitSectionLabel(stringResource(Res.string.composerFullAgent))
                    KitChipWrap(Modifier.fillMaxWidth().testTag("chat-agent-choices")) {
                        models.agents.forEach { agent ->
                            KitChip(
                                label = agent.id,
                                kind = KitChipKind.Action,
                                selected = agent.id == models.agent,
                                onClick = { scope.launch { actions.selectAgent(agent.id) } },
                            )
                        }
                    }
                }
            }
        }
    }
    KitSheet(
        visible = addOpen,
        onDismissRequest = { addOpen = false },
        title = stringResource(Res.string.composerFullAddTitle),
        closeLabel = stringResource(Res.string.composerPreviewModelsClose),
    ) {
        Column(Modifier.fillMaxWidth().testTag("chat-add-sheet")) {
            if (state.filesAvailable)
                KitRow(
                    stringResource(Res.string.composerPreviewAttachFile),
                    null,
                    {
                        addOpen = false
                        actions.attachFile()
                    },
                )
            if (AttachKind.Photos in state.attachKinds)
                KitRow(
                    stringResource(Res.string.composerFullPhotoLibrary),
                    null,
                    {
                        addOpen = false
                        actions.attach(AttachKind.Photos)
                    },
                )
            if (AttachKind.Camera in state.attachKinds)
                KitRow(
                    stringResource(Res.string.composerFullTakePhoto),
                    null,
                    {
                        addOpen = false
                        actions.attach(AttachKind.Camera)
                    },
                )
            if (AttachKind.Clipboard in state.attachKinds)
                KitRow(
                    stringResource(Res.string.composerFullPasteImage),
                    null,
                    {
                        addOpen = false
                        actions.attach(AttachKind.Clipboard)
                    },
                )
        }
    }
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

/** "Model · level · agent": the level and agent only when chosen (Flutter composer.dart:795). */
private fun modelLabel(model: String, level: String, agent: String?): String =
    listOfNotNull(agent?.takeIf { it.isNotEmpty() }, model, level.takeIf { it.isNotEmpty() })
        .joinToString(" · ")

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

/**
 * Plain bookkeeping: synchronous input reporting does not mutate Compose state or the field
 * transaction.
 */
private class DraftEditorState {
    var restoreRevision: Long? = null
    var lastReported: String? = null
    var userEditSerial: Long = 0
}
