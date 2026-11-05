package io.github.eslamasabry.oc.feature.chatcomposer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.frame.*
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.modal.*
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/** Local editor, sharing the composer's field (order C22 / ADR 9.2), with an explicit Use in draft decision. */
@Composable
internal fun PromptEditorScreen(visible: Boolean, field: TextFieldState, onClose: () -> Unit) {
    val original = remember(visible) { field.text.toString() }
    val selection = remember(visible) { field.selection }
    var discard by remember(visible) { mutableStateOf(false) }
    val focus = remember(visible) { FocusRequester() }
    val close = { if (field.text.toString() == original) onClose() else discard = true }
    val title = stringResource(Res.string.chatUiPromptEditor)
    KitPageOverlay(visible, title, close) {
        LaunchedEffect(Unit) { focus.requestFocus() }
        KitScreen(
            width = KitScreenWidth.Reading,
            topBar = { KitTopBar(title, exit = KitTopBarExit.Close, exitLabel = stringResource(Res.string.kitSheetClose), onExit = close) },
            bottom = { KitActionBlock(primary = KitAction(stringResource(Res.string.promptEditorDone), onClose, icon = KitIcons.check)) },
        ) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = Space.S2.dp, bottom = Space.S4.dp)) {
                KitField(
                    state = field,
                    label = stringResource(Res.string.promptEditorFieldLabel),
                    placeholder = stringResource(Res.string.chatUiWriteYourOpenCodePrompt),
                    kind = KitFieldKind.Multiline,
                    multilineLines = if (LocalKitWindow.current.short) 6..6 else 18..18, // F: lib/ui/screens/chat/prompt_editor.dart:187
                    focusRequester = focus,
                    modifier = Modifier.testTag("prompt-editor-field"),
                )
            }
        }
        KitConfirm(
            spec = if (!discard) null else KitConfirmSpec(
                title = stringResource(Res.string.chatUiDiscardPromptChanges),
                body = stringResource(Res.string.chatUiYourOriginalComposerDraftAndAttachmentsWill),
                confirmLabel = stringResource(Res.string.promptEditorDiscardChanges),
                cancelLabel = stringResource(Res.string.draftKeepEditing),
                kind = KitConfirmKind.Destructive,
            ),
            onConfirm = {
                field.edit { replace(0, length, original); this.selection = selection }
                discard = false
                onClose()
            },
            onCancel = { discard = false },
        )
    }
}
