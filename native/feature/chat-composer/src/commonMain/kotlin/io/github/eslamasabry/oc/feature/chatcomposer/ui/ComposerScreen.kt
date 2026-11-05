package io.github.eslamasabry.oc.feature.chatcomposer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerStatusStrip
import io.github.eslamasabry.oc.ui.kitchat.composer.KitStopState
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerTextBox
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerMic
import io.github.eslamasabry.oc.ui.kitchat.composer.KitStopAndSendPill
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerSend
import io.github.eslamasabry.oc.ui.kitchat.composer.KitComposerStop
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import io.github.eslamasabry.oc.ui.words.res.kitComposerEditor
import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.approvalModeAskTitle
import io.github.eslamasabry.oc.ui.words.res.chatSendFailed
import io.github.eslamasabry.oc.ui.words.res.chatSendFailedKept
import io.github.eslamasabry.oc.ui.words.res.chatChipAutoApproveOn
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
    var editorOpen by remember { mutableStateOf(false) }

    LaunchedEffect(field) { snapshotFlow { field.text.toString() }.collect { currentActions.onTextChanged(it) } }
    LaunchedEffect(state.edit?.id) {
        val edit = state.edit ?: return@LaunchedEffect
        field.apply(edit.change)
        currentActions.onEditApplied(edit.id)
    }
    LaunchedEffect(state.sends) { if (state.sends > 0 && !keepFocusOnSend) focus.clearFocus() }

    Column(
        modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = Space.S4.dp, vertical = Space.S2.dp),
        verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
    ) {
        if (state.chips.isNotEmpty()) ChipRow(state, actions)
        KitComposerTextBox(
            text = field.text.toString(),
            running = state.glow,
            wide = LocalKitWindow.current.cls.isWide,
            allowStacking = state.fieldEnabled,
            above = {
                state.note?.let {
                    Box(Modifier.padding(start = Space.S3.dp, end = Space.S3.dp, top = Space.S2.dp)) {
                        NoteLine(it, actions)
                    }
                }
            },
            field = { multiline ->
                val canExpand = multiline && state.fieldEnabled
                Box {
                    KitField(
                        state = field,
                        label = stringResource(Res.string.composerFieldLabel),
                        modifier = Modifier.fillMaxWidth().padding(end = if (canExpand) LocalKitTokens.current.minTarget.dp else 0.dp).composerKeys(field, finePointer, state.button, actions),
                        kind = KitFieldKind.Multiline,
                        placeholder = stringResource(Res.string.kitComposerField),
                        enabled = if (state.fieldEnabled) KitEnabled.Enabled else KitEnabled.Disabled(readOnlyWords()),
                        labelShown = false,
                        framed = false,
                        multilineLines = if (LocalKitWindow.current.short) 1..3 else 1..8, // F: lib/ui/kit/chat/kit_composer.dart:590
                    )
                    if (canExpand) KitIconButton(
                        KitIcons.expand, stringResource(Res.string.kitComposerEditor), { editorOpen = true },
                        modifier = Modifier.align(Alignment.TopEnd).testTag("composer-open-editor"),
                    )
                }
            },
            trailing = { FieldButtonSlot(state.button, actions, mic, quiet = state.glow) },
        )

    }
    PromptEditorScreen(editorOpen, field, { editorOpen = false })
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
    val stop = state.chips.filterIsInstance<ComposerChip.Stop>().firstOrNull()
    val approvals = state.chips.filterIsInstance<ComposerChip.Approval>()
    val model = state.chips.filterIsInstance<ComposerChip.Model>().firstOrNull()
    KitComposerStatusStrip(
        hasChips = approvals.isNotEmpty(),
        model = if (model == null) null else ({
            KitChip(modelChipWords(model), kind = KitChipKind.Action, icon = KitIcons.model, opensMenu = true, onClick = actions::onModelChip)
        }),
        stop = if (stop == null) null else ({
            KitComposerStop(
                stringResource(Res.string.kitTurnLiveStopShort), stringResource(Res.string.kitTurnLiveStopShort), actions::onStop,
                state = if (stop.stuck) KitStopState.Stuck else KitStopState.Running,
                armKey = state.sends,
            )
        }),
    ) {
        approvals.forEach { chip ->
            KitChip(
                approvalWords(chip.mode), kind = KitChipKind.Action,
                icon = if (chip.mode == ApprovalMode.Paused) KitIcons.pause else KitIcons.shield,
                selected = if (chip.mode == ApprovalMode.AutoApprove) true else null,
                onClick = actions::onApprovalChip,
            )
        }
    }
}

@Composable
private fun approvalWords(mode: ApprovalMode): String =
    when (mode) {
        ApprovalMode.AsksFirst -> stringResource(Res.string.approvalModeAskTitle)
        ApprovalMode.AutoApprove -> stringResource(Res.string.chatChipAutoApproveOn)
        ApprovalMode.Paused -> stringResource(Res.string.chatStripAutoApprovePaused)
    }

@Composable
private fun FieldButtonSlot(button: FieldButton, actions: ComposerActions, mic: (@Composable () -> Unit)?, quiet: Boolean) {
    when (button) {
        FieldButton.None -> Unit
        FieldButton.Mic ->
            mic?.invoke() ?: KitComposerMic(stringResource(Res.string.kitComposerVoice), actions::onMic, quiet = quiet)
        FieldButton.Send -> KitComposerSend(stringResource(Res.string.kitComposerSend), actions::onSend)
        FieldButton.AddToTurn -> KitComposerSend(stringResource(Res.string.kitComposerAddToTurn), actions::onSend)
        FieldButton.StopAndSend ->
            KitStopAndSendPill(stringResource(Res.string.kitComposerStopAndSend), actions::onSend)
        FieldButton.SendWhenOnline ->
            KitComposerSend(stringResource(Res.string.kitComposerSendOffline), actions::onSend)
        FieldButton.CannotSendYet ->
            KitComposerSend(
                stringResource(Res.string.kitComposerSend),
                onClick = actions::onSend,
                enabled = KitEnabled.Disabled(stringResource(Res.string.kitComposerCannotSendYet)),
            )
        FieldButton.Sending ->
            KitComposerSend(
                stringResource(Res.string.kitComposerSending),
                onClick = actions::onSend,
                working = true,
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
                val canSend = when (button) {
                    FieldButton.Send, FieldButton.AddToTurn, FieldButton.StopAndSend, FieldButton.SendWhenOnline -> true
                    else -> false
                }
                if (enter == EnterKey.Send && canSend) {
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
