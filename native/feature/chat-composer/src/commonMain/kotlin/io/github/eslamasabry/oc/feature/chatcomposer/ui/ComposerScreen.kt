package io.github.eslamasabry.oc.feature.chatcomposer.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.ApprovalMode
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.ComposerActions
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.ComposerChip
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.ComposerKeys
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.ComposerNote
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.ComposerUi
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.EnterKey
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.FieldButton
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.FieldEdit
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.FieldValue
import io.github.eslamasabry.oc.feature.chatcomposer.presentation.ReadOnlyReason
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipKind
import io.github.eslamasabry.oc.ui.kit.chip.KitChipTone
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerGlow
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.approvalModeAskTitle
import io.github.eslamasabry.oc.ui.words.res.chatSendFailed
import io.github.eslamasabry.oc.ui.words.res.chatSendFailedKept
import io.github.eslamasabry.oc.ui.words.res.chatStripAutoApprove
import io.github.eslamasabry.oc.ui.words.res.chatStripAutoApprovePaused
import io.github.eslamasabry.oc.ui.words.res.chatSubagentReadOnly
import io.github.eslamasabry.oc.ui.words.res.chatUiNextTurnsModel
import io.github.eslamasabry.oc.ui.words.res.composerFieldLabel
import io.github.eslamasabry.oc.ui.words.res.kitComposerAddToTurn
import io.github.eslamasabry.oc.ui.words.res.kitComposerCannotSendYet
import io.github.eslamasabry.oc.ui.words.res.kitComposerField
import io.github.eslamasabry.oc.ui.words.res.kitComposerSend
import io.github.eslamasabry.oc.ui.words.res.kitComposerSendOffline
import io.github.eslamasabry.oc.ui.words.res.kitComposerSending
import io.github.eslamasabry.oc.ui.words.res.kitComposerStopAndSend
import io.github.eslamasabry.oc.ui.words.res.kitComposerVoice
import io.github.eslamasabry.oc.ui.words.res.kitTryAgain
import io.github.eslamasabry.oc.ui.words.res.kitTurnLiveStopShort
import org.jetbrains.compose.resources.stringResource

/**
 * The composer (plan 09 §10), arranged from kit parts: the one-line chip row (model, approval, then Stop at the
 * trailing end while a reply runs), the note line, the field, and the one field button (mic, Send or a steer form).
 * It follows the keyboard (`imePadding`) and the navigation bar. The field owns its text ([field]); words flow out
 * through [ComposerActions.onTextChanged] and edits flow in through [ComposerUi.edit].
 *
 * The real kit glow follows the acknowledged Appearance choice around the field; "Stop and send" remains a
 * secondary action and the field is a multiline `KitField`.
 *
 * @param finePointer a mouse or trackpad is the pointer: Enter sends, Shift+Enter is a newline.
 * @param keepFocusOnSend a wide window with a hardware keyboard ([ComposerKeys.keepFocusAfterSend]).
 * @param mic the voice slot (S5); null shows the plain "Talk instead of typing" button.
 */
@Composable
fun ComposerScreen(
    state: ComposerUi,
    actions: ComposerActions,
    field: TextFieldState,
    modifier: Modifier = Modifier,
    finePointer: Boolean = false,
    keepFocusOnSend: Boolean = false,
    mic: (@Composable () -> Unit)? = null,
) {
    val currentActions by rememberUpdatedState(actions)
    val focus = LocalFocusManager.current

    LaunchedEffect(field) { snapshotFlow { field.text.toString() }.collect { currentActions.onTextChanged(it) } }
    LaunchedEffect(state.edit?.id) {
        val edit = state.edit ?: return@LaunchedEffect
        field.apply(edit.change)
        currentActions.onEditApplied(edit.id)
    }
    LaunchedEffect(state.sends) { if (state.sends > 0 && !keepFocusOnSend) focus.clearFocus() }

    Column(
        modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = Space.S3.dp, vertical = Space.S2.dp),
        verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
    ) {
        state.note?.let { NoteLine(it, actions) }
        if (state.chips.isNotEmpty()) ChipRow(state, actions)
        KitComposerGlow(active = state.glow) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
            KitField(
                state = field,
                label = stringResource(Res.string.composerFieldLabel),
                modifier = Modifier.weight(1f).composerKeys(field, finePointer, state.button, actions),
                kind = KitFieldKind.Multiline,
                placeholder = stringResource(Res.string.kitComposerField),
                enabled = if (state.fieldEnabled) KitEnabled.Enabled else KitEnabled.Disabled(readOnlyWords()),
            )
            FieldButtonSlot(state.button, actions, mic)
        }
        }
    }
}

@Composable
private fun readOnlyWords(): String = stringResource(Res.string.chatSubagentReadOnly)

@Composable
private fun NoteLine(note: ComposerNote, actions: ComposerActions) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
        when (note) {
            ComposerNote.SendFailed -> {
                KitText(
                    stringResource(Res.string.chatSendFailed) + " · " + stringResource(Res.string.chatSendFailedKept),
                    Modifier.weight(1f),
                    role = TypeRole.Secondary,
                    tone = TextTone.Secondary,
                )
                KitButton(stringResource(Res.string.kitTryAgain), onClick = actions::onRetry, role = KitButtonRole.Tertiary)
            }
            is ComposerNote.NextTurnsModel ->
                KitText(stringResource(Res.string.chatUiNextTurnsModel, modelLabelWords(note.label)), role = TypeRole.Secondary, tone = TextTone.Secondary)
            is ComposerNote.ReadOnly ->
                KitText(
                    when (note.reason) {
                        ReadOnlyReason.SubAgent -> stringResource(Res.string.chatSubagentReadOnly)
                    },
                    role = TypeRole.Secondary,
                    tone = TextTone.Secondary,
                )
        }
    }
}

@Composable
private fun ChipRow(state: ComposerUi, actions: ComposerActions) {
    val stop = state.chips.lastOrNull() as? ComposerChip.Stop
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
            state.chips.forEach { chip ->
                when (chip) {
                    is ComposerChip.Model ->
                        KitChip(modelChipWords(chip), kind = KitChipKind.Action, onClick = actions::onModelChip)
                    is ComposerChip.Approval ->
                        KitChip(
                            approvalWords(chip.mode),
                            kind = KitChipKind.Action,
                            tone = if (chip.mode == ApprovalMode.AutoApprove) KitChipTone.Active else KitChipTone.Neutral,
                            onClick = actions::onApprovalChip,
                        )
                    is ComposerChip.Stop -> Unit // drawn at the trailing end, outside the scroller
                }
            }
        }
        // Stop never comes and goes mid-row: it is the row's trailing end for the whole running turn.
        stop?.let {
            KitChip(
                stringResource(Res.string.kitTurnLiveStopShort),
                kind = KitChipKind.Action,
                tone = if (it.stuck) KitChipTone.Attention else KitChipTone.Neutral,
                onClick = actions::onStop,
            )
        }
    }
}

@Composable
private fun approvalWords(mode: ApprovalMode): String =
    when (mode) {
        ApprovalMode.AsksFirst -> stringResource(Res.string.approvalModeAskTitle)
        ApprovalMode.AutoApprove -> stringResource(Res.string.chatStripAutoApprove)
        ApprovalMode.Paused -> stringResource(Res.string.chatStripAutoApprovePaused)
    }

@Composable
private fun FieldButtonSlot(button: FieldButton, actions: ComposerActions, mic: (@Composable () -> Unit)?) {
    when (button) {
        FieldButton.None -> Unit
        FieldButton.Mic ->
            mic?.invoke() ?: KitButton(stringResource(Res.string.kitComposerVoice), onClick = actions::onMic, role = KitButtonRole.Tertiary)
        FieldButton.Send -> KitButton(stringResource(Res.string.kitComposerSend), onClick = actions::onSend, maxLines = 1)
        FieldButton.AddToTurn -> KitButton(stringResource(Res.string.kitComposerAddToTurn), onClick = actions::onSend, maxLines = 1)
        FieldButton.StopAndSend ->
            KitButton(stringResource(Res.string.kitComposerStopAndSend), onClick = actions::onSend, role = KitButtonRole.Secondary, maxLines = 1)
        FieldButton.SendWhenOnline ->
            KitButton(stringResource(Res.string.kitComposerSendOffline), onClick = actions::onSend, role = KitButtonRole.Secondary, maxLines = 1)
        FieldButton.CannotSendYet ->
            KitButton(
                stringResource(Res.string.kitComposerSend),
                onClick = actions::onSend,
                maxLines = 1,
                enabled = KitEnabled.Disabled(stringResource(Res.string.kitComposerCannotSendYet)),
            )
        FieldButton.Sending ->
            KitButton(
                stringResource(Res.string.kitComposerSending),
                onClick = actions::onSend,
                maxLines = 1,
                working = true,
                enabled = KitEnabled.Disabled(stringResource(Res.string.kitComposerSending)),
            )
    }
}

/** Enter per [ComposerKeys.enter]; arrow up/down at the text's edges browse sent prompts (B-CHAT-089). */
private fun Modifier.composerKeys(
    field: TextFieldState,
    finePointer: Boolean,
    button: FieldButton,
    actions: ComposerActions,
): Modifier =
    onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
            Key.Enter, Key.NumPadEnter -> {
                val enter = ComposerKeys.enter(finePointer, event.isShiftPressed, event.isCtrlPressed || event.isMetaPressed)
                if (enter == EnterKey.Send && button != FieldButton.Mic && button != FieldButton.None) {
                    actions.onSend()
                    true
                } else {
                    enter == EnterKey.Send // nothing to send: swallow the key rather than add a newline
                }
            }
            Key.DirectionUp, Key.DirectionDown -> {
                val modifier = event.isShiftPressed || event.isCtrlPressed || event.isMetaPressed || event.isAltPressed
                if (modifier) return@onPreviewKeyEvent false
                actions.onHistoryKey(event.key == Key.DirectionUp, field.value(), suggestionsOpen = false)
            }
            else -> false
        }
    }

private fun TextFieldState.value(): FieldValue {
    val composing = composition
    return FieldValue(
        text = text.toString(),
        selectionStart = selection.start,
        selectionEnd = selection.end,
        composingStart = composing?.start ?: -1,
        composingEnd = composing?.end ?: -1,
    )
}

private fun TextFieldState.apply(change: FieldEdit.Change) {
    edit {
        when (change) {
            is FieldEdit.Change.Insert -> {
                val start = selection.min
                replace(start, selection.max, change.text)
                placeCursorBeforeCharAt(start + change.text.length)
            }
            is FieldEdit.Change.Replace -> {
                replace(0, length, change.text)
                placeCursorBeforeCharAt(change.caret.coerceIn(0, length))
            }
            FieldEdit.Change.Clear -> replace(0, length, "")
        }
    }
}
