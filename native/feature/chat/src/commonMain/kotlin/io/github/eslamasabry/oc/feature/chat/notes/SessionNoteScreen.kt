package io.github.eslamasabry.oc.feature.chat.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * Session note: a visible header action and a kit sheet, with Save and Remove as explicit
 * decisions.
 */
@Composable
public fun SessionNoteScreen(state: SessionNoteUi, actions: SessionNoteActions) {
    val working = stringResource(Res.string.stateNoteWorking)
    val locked = stringResource(Res.string.stateNoteReloadFirst)
    KitSheet(
        visible = state.visible,
        onDismissRequest = actions::closeNote,
        title = stringResource(Res.string.stateNoteTitle),
        closeLabel = stringResource(Res.string.kitSheetClose),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.S3.dp)) {
            KitNotice(stringResource(Res.string.stateNoteDescription))
            state.failure?.let { failure ->
                val conflict =
                    failure is Failure.Conflict ||
                        (failure is Failure.Refused && failure.reason == RefusalReason.StaleScope)
                KitErrorNotice(
                    message =
                        stringResource(
                            if (conflict) Res.string.stateNoteChanged
                            else Res.string.stateNoteFailed
                        ),
                    failure = failure,
                )
            }
            if (state.loading && !state.ready) KitSkeletonRows()
            if (state.ready) {
                key(state.editRevision) {
                    val field = rememberTextFieldState(state.draft)
                    KitField(
                        state = field,
                        label = stringResource(Res.string.stateNoteField),
                        kind = KitFieldKind.Multiline,
                        enabled =
                            if (state.loading) KitEnabled.Disabled(working) else KitEnabled.Enabled,
                        error =
                            if (state.tooLarge) stringResource(Res.string.stateNoteTooLarge)
                            else null,
                        onTextChanged = actions::changeNote,
                        multilineLines = 4..12,
                    )
                }
                state.savedReceipt?.let {
                    KitNotice(
                        stringResource(
                            if (it) Res.string.stateNoteSaved else Res.string.stateNoteRemoved
                        )
                    )
                }
            }
            KitButton(
                label = stringResource(Res.string.stateNoteSave),
                onClick = actions::saveNote,
                working = state.loading,
                enabled =
                    if (
                        state.ready &&
                            !state.loading &&
                            !state.tooLarge &&
                            state.draft.isNotBlank() &&
                            state.dirty
                    )
                        KitEnabled.Enabled
                    else KitEnabled.Disabled(if (state.loading) working else locked),
            )
            if (state.saved != null)
                KitButton(
                    label = stringResource(Res.string.stateNoteRemove),
                    onClick = actions::removeNote,
                    role = KitButtonRole.Secondary,
                    destructive = true,
                    enabled =
                        if (state.loading) KitEnabled.Disabled(working) else KitEnabled.Enabled,
                )
            KitButton(
                label = stringResource(Res.string.stateNoteReload),
                onClick = actions::reloadNote,
                role = KitButtonRole.Tertiary,
                enabled = if (state.loading) KitEnabled.Disabled(working) else KitEnabled.Enabled,
            )
        }
    }
    KitConfirm(
        spec =
            if (!state.discard) null
            else
                KitConfirmSpec(
                    title = stringResource(Res.string.stateNoteDiscardTitle),
                    confirmLabel = stringResource(Res.string.stateNoteDiscard),
                    body = stringResource(Res.string.stateNoteDiscardBody),
                    kind = KitConfirmKind.Destructive,
                ),
        onConfirm = actions::discardNote,
        onCancel = actions::keepEditing,
    )
}
