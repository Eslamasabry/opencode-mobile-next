package io.github.eslamasabry.oc.feature.chat.sessiontransfer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.feature.chat.sessiontransfer.presentation.*
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHeight
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * Visible, non-gesture path to the complete server export, reviewed import and the mobile-owned
 * note.
 */
@Composable
public fun SessionTransferSheet(state: SessionTransferUi, actions: SessionTransferActions) {
    val wait = stringResource(Res.string.sessionTransferWait)
    val enabled = if (state.busy) KitEnabled.Disabled(wait) else KitEnabled.Enabled
    val note = rememberTextFieldState()
    val destination = rememberTextFieldState()
    val reference = rememberTextFieldState()
    LaunchedEffect(state.referencePath) {
        if (reference.text.toString() != state.referencePath)
            reference.edit { replace(0, length, state.referencePath) }
    }
    LaunchedEffect(state.noteRevision) { note.edit { replace(0, length, state.note) } }
    LaunchedEffect(state.destination) {
        if (destination.text.toString() != state.destination)
            destination.edit { replace(0, length, state.destination) }
    }
    KitSheet(
        visible = state.visible,
        onDismissRequest = actions::close,
        title = stringResource(Res.string.sessionTransferTitle),
        height = KitSheetHeight.Full,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            state.failure?.let { failure ->
                val resource =
                    when {
                        failure is Failure.Refused &&
                            failure.reason == RefusalReason.InvalidAddress ->
                            Res.string.sessionTransferReferenceInvalid
                        failure is Failure.SignIn -> Res.string.sessionTransferSignIn
                        failure is Failure.Conflict -> Res.string.sessionTransferConflict
                        failure is Failure.Unavailable -> Res.string.sessionTransferUnsupported
                        failure is Failure.Media &&
                            failure.problem == MediaProblem.AttachmentTooLarge ->
                            Res.string.sessionTransferLarge
                        else -> Res.string.sessionTransferFailed
                    }
                KitNotice(stringResource(resource))
            }
            state.notice?.let { notice ->
                KitNotice(
                    stringResource(
                        when (notice) {
                            TransferNotice.Saved -> Res.string.sessionTransferSaved
                            TransferNotice.Imported -> Res.string.sessionTransferImported
                            TransferNotice.NoteSaved -> Res.string.sessionTransferNoteSaved
                            TransferNotice.NoteRemoved -> Res.string.sessionTransferNoteRemoved
                            TransferNotice.InvalidFile -> Res.string.sessionTransferInvalid
                            TransferNotice.TooLarge -> Res.string.sessionTransferLarge
                            TransferNotice.Changed -> Res.string.sessionTransferChanged
                            TransferNotice.ReferenceAdded ->
                                Res.string.sessionTransferReferenceAdded
                        }
                    )
                )
            }
            if (state.busy) KitNotice(wait)
            if (state.referencesAvailable) {
                KitSectionLabel(stringResource(Res.string.sessionTransferReference))
                KitField(
                    state = reference,
                    label = stringResource(Res.string.sessionTransferReferencePath),
                    kind = KitFieldKind.Path,
                    helper = stringResource(Res.string.sessionTransferReferenceBody),
                    enabled = enabled,
                    onTextChanged = actions::referencePath,
                )
                KitButton(
                    label = stringResource(Res.string.sessionTransferReferenceAdd),
                    onClick = actions::addReference,
                    enabled =
                        if (state.referencePath.isBlank())
                            KitEnabled.Disabled(
                                stringResource(Res.string.sessionTransferReferenceRequired)
                            )
                        else enabled,
                    role = KitButtonRole.Secondary,
                )
            }
            if (state.transferAvailable) {
                KitSectionLabel(stringResource(Res.string.sessionTransferExport))
                KitSwitchRow(
                    title = stringResource(Res.string.sessionTransferRedact),
                    checked = state.sanitize,
                    supporting = stringResource(Res.string.sessionTransferRedactBody),
                    enabled = enabled,
                    onCheckedChange = actions::sanitize,
                )
                KitButton(
                    label = stringResource(Res.string.sessionTransferSave),
                    onClick = actions::export,
                    enabled = enabled,
                )
                KitSectionLabel(stringResource(Res.string.sessionTransferImport))
                KitButton(
                    label = stringResource(Res.string.sessionTransferChoose),
                    onClick = actions::choose,
                    enabled = enabled,
                    role = KitButtonRole.Secondary,
                )
                state.reviewCount?.let { count ->
                    state.reviewTitle?.let { KitText(it) }
                    KitText(stringResource(Res.string.sessionTransferRecords, count))
                    if (state.redacted)
                        KitNotice(stringResource(Res.string.sessionTransferRedacted))
                    KitField(
                        state = destination,
                        label = stringResource(Res.string.sessionTransferDestination),
                        kind = KitFieldKind.Path,
                        enabled = enabled,
                        onTextChanged = actions::destination,
                    )
                    if (!state.imported)
                        KitButton(
                            label = stringResource(Res.string.sessionTransferImportAction),
                            onClick = actions::importReviewed,
                            enabled =
                                if (state.destination.isBlank())
                                    KitEnabled.Disabled(
                                        stringResource(
                                            Res.string.sessionTransferDestinationRequired
                                        )
                                    )
                                else enabled,
                        )
                }
            } else KitNotice(stringResource(Res.string.sessionTransferUnsupported))
            if (state.notesAvailable) {
                KitSectionLabel(stringResource(Res.string.sessionTransferNote))
                KitButton(
                    label = stringResource(Res.string.sessionTransferLoadNote),
                    onClick = actions::loadNote,
                    enabled = enabled,
                    role = KitButtonRole.Secondary,
                )
                if (state.noteLoaded) {
                    KitField(
                        state = note,
                        label = stringResource(Res.string.sessionTransferNote),
                        kind = KitFieldKind.Multiline,
                        enabled = enabled,
                        onTextChanged = actions::note,
                        error =
                            state.noteLimit?.let {
                                stringResource(Res.string.sessionTransferNoteLimit, it)
                            },
                    )
                    KitButton(
                        label = stringResource(Res.string.sessionTransferSaveNote),
                        onClick = actions::saveNote,
                        enabled = enabled,
                    )
                    KitButton(
                        label = stringResource(Res.string.sessionTransferRemoveNote),
                        onClick = actions::removeNote,
                        enabled = enabled,
                        destructive = true,
                        role = KitButtonRole.Tertiary,
                    )
                }
            }
        }
    }
}
