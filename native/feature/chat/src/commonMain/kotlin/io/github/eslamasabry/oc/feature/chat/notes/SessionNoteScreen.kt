package io.github.eslamasabry.oc.feature.chat.notes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.*
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.modal.*
import io.github.eslamasabry.oc.ui.kit.state.*
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/** Flutter's note editor: a full page, one field, and actions pinned below the scrolling words. */
@Composable
public fun SessionNoteScreen(state: SessionNoteUi, actions: SessionNoteActions) {
    val title = stringResource(Res.string.sessionNoteTitle)
    var savingHere by remember(state.visible) { mutableStateOf(false) }
    LaunchedEffect(state.loading, state.dirty, state.savedReceipt) {
        if (savingHere && !state.loading && !state.dirty && state.savedReceipt == true && state.failure == null) {
            savingHere = false
            actions.closeNote()
        }
    }
    KitPageOverlay(state.visible, title, actions::closeNote) {
        val wait = stringResource(Res.string.sessionNoteSaving)
        val locked = stringResource(Res.string.sessionNoteFieldLocked)
        val conflict = state.failure is Failure.Conflict ||
            (state.failure is Failure.Refused && state.failure.reason == RefusalReason.StaleScope)
        val enabled = if (state.loading) KitEnabled.Disabled(wait) else if (conflict) KitEnabled.Disabled(locked) else KitEnabled.Enabled
        KitScreen(
            topBar = { KitTopBar(title, exit = KitTopBarExit.Back, exitLabel = stringResource(Res.string.kitTopBarBack), onExit = actions::closeNote) },
            width = KitScreenWidth.List,
            loading = state.ready && state.loading,
            loadingLabel = wait,
            bottom = if (!state.ready || (!state.dirty && state.saved == null)) null else ({
                KitActionBlock(
                    primary = if (!state.dirty) null else KitAction(
                        stringResource(Res.string.sessionNoteSave),
                        { savingHere = true; actions.saveNote() },
                        enabled = when {
                            state.loading || conflict -> enabled
                            state.tooLarge -> KitEnabled.Disabled(stringResource(Res.string.sessionNoteTooLarge))
                            state.draft.isBlank() -> KitEnabled.Disabled(stringResource(if (state.saved == null) Res.string.sessionNoteWriteFirst else Res.string.sessionNoteEmptyUseDelete))
                            else -> KitEnabled.Enabled
                        },
                    ),
                    tertiary = if (state.saved == null) emptyList() else listOf(KitAction(
                        stringResource(Res.string.sessionNoteRemove),
                        actions::removeNote,
                        enabled = enabled,
                        destructive = true,
                    )),
                )
            }),
        ) {
            if (!state.ready) {
                if (state.failure != null) KitStateView(
                    kind = KitStateKind.Error,
                    title = stringResource(Res.string.sessionNoteLoadFailed),
                    body = stringResource(if (conflict) Res.string.sessionNoteChanged else Res.string.stateNoteFailed),
                    failure = state.failure,
                    onRetry = actions::reloadNote,
                ) else KitStateView(kind = KitStateKind.Loading, title = stringResource(Res.string.sessionNoteLoading))
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = Space.S3.dp),
                    verticalArrangement = Arrangement.spacedBy(Space.S4.dp),
                ) {
                    KitText(stringResource(Res.string.sessionNoteDescription), role = TypeRole.Secondary, tone = TextTone.Secondary)
                    state.failure?.let { failure ->
                        KitErrorNotice(
                            message = stringResource(if (conflict) Res.string.sessionNoteChanged else Res.string.sessionNoteSaveFailed),
                            failure = failure,
                            retry = KitLineAction(stringResource(Res.string.sessionNoteRefresh), actions::reloadNote),
                        )
                    }
                    key(state.editRevision) {
                        val field = rememberTextFieldState(state.draft)
                        KitField(
                            state = field,
                            label = stringResource(Res.string.sessionNoteFieldLabel),
                            placeholder = stringResource(Res.string.sessionNoteHint),
                            kind = KitFieldKind.Multiline,
                            enabled = enabled,
                            error = if (state.tooLarge) stringResource(Res.string.sessionNoteTooLarge) else null,
                            modifier = Modifier.testTag("session-note-editor"),
                            onTextChanged = actions::changeNote,
                        )
                    }
                    if (state.savedReceipt == false) KitNotice(stringResource(Res.string.sessionNoteRemoved))
                }
            }
        }
        KitConfirm(
            spec = if (!state.discard) null else KitConfirmSpec(
                title = stringResource(Res.string.sessionNoteDiscard),
                body = stringResource(Res.string.sessionNoteDiscardDetail),
                confirmLabel = stringResource(Res.string.sessionNoteDiscardAction),
                cancelLabel = stringResource(Res.string.sessionNoteKeepEditing),
                kind = KitConfirmKind.Destructive,
            ),
            onConfirm = actions::discardNote,
            onCancel = actions::keepEditing,
        )
    }
}
