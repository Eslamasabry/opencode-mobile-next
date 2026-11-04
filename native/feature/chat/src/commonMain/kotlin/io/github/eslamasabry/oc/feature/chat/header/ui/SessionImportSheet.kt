package io.github.eslamasabry.oc.feature.chat.header.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.engine.agents.sessionimport.ImportNotice
import io.github.eslamasabry.oc.engine.agents.sessionimport.SessionImportUi
import io.github.eslamasabry.oc.feature.chat.header.presentation.ChatHeaderActions
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** File → validate → explicit destination → review → one import → open, on the original server. */
@Composable
internal fun SessionImportBody(state: SessionImportUi, actions: ChatHeaderActions) {
    // Conversation content is memory only: never rememberSaveable, preferences or diagnostics.
    val json = remember { TextFieldState() }
    val directory = remember { TextFieldState(state.directory) }
    val workspace = remember { TextFieldState(state.workspace) }
    val busy = stringResource(Res.string.importSending)
    val consumed = stringResource(Res.string.importNativeAttemptDone)
    val editing =
        if (state.working || state.attempted)
            KitEnabled.Disabled(if (state.working) busy else consumed)
        else KitEnabled.Enabled
    Column(
        Modifier.fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .testTag("session-import-review")
    ) {
        KitNotice(message = stringResource(Res.string.importPreserves))
        if (!state.attempted) {
            if (state.filePickerAvailable) {
                KitButton(
                    label = stringResource(Res.string.importChoose),
                    onClick = actions::importChoose,
                    enabled = editing,
                    working = state.working,
                )
            }
            KitField(
                json,
                stringResource(Res.string.importNativePaste),
                kind = KitFieldKind.Multiline,
                enabled = editing,
            )
            KitButton(
                label = stringResource(Res.string.importNativeReview),
                onClick = { actions.importReview(json.text.toString()) },
                role = KitButtonRole.Secondary,
                enabled = editing,
            )
        }
        KitField(
            directory,
            stringResource(Res.string.importFolder),
            kind = KitFieldKind.Path,
            enabled = editing,
            onTextChanged = { actions.importDestination(it, workspace.text.toString()) },
        )
        if (state.workspaceAllowed) {
            KitField(
                workspace,
                stringResource(Res.string.importEnvironmentId),
                kind = KitFieldKind.Mono,
                helper = stringResource(Res.string.importNativeWorkspaceOptional),
                enabled = editing,
                onTextChanged = { actions.importDestination(directory.text.toString(), it) },
            )
        }
        state.document?.let { doc ->
            KitRowGroup {
                KitRow(
                    stringResource(Res.string.importPreviewLabel),
                    trailing =
                        KitRowTrailing.Value(
                            doc.title ?: stringResource(Res.string.importUntitled),
                            chevron = false,
                        ),
                )
                KitRow(
                    stringResource(Res.string.importConversationId),
                    trailing = KitRowTrailing.Value(doc.id, chevron = false),
                )
                KitRow(
                    pluralStringResource(
                        Res.plurals.importMessages,
                        doc.messageCount,
                        doc.messageCount,
                    )
                )
                doc.parentId?.let {
                    KitRow(
                        stringResource(Res.string.importParentId),
                        trailing = KitRowTrailing.Value(it, chevron = false),
                    )
                }
            }
            if (doc.hasRedactions) KitNotice(message = stringResource(Res.string.importRedacted))
            if (doc.parentId != null) KitNotice(message = stringResource(Res.string.importParent))
            if (doc.archived) KitNotice(message = stringResource(Res.string.importArchived))
            if (!state.attempted) {
                KitButton(
                    label = stringResource(Res.string.importAction),
                    onClick = actions::importSend,
                    enabled = editing,
                    working = state.working,
                )
            }
        }
        state.notice?.let { KitNotice(message = notice(it)) }
        if (state.imported != null) {
            KitNotice(message = stringResource(Res.string.importSucceeded))
            KitButton(label = stringResource(Res.string.importOpen), onClick = actions::importOpen)
        } else if (state.attempted && !state.working) {
            KitNotice(message = consumed)
        }
    }
}

@Composable
private fun notice(value: ImportNotice): String =
    stringResource(
        when (value) {
            ImportNotice.Invalid -> Res.string.importInvalidFile
            ImportNotice.TooLarge -> Res.string.importTooLarge
            ImportNotice.Changed -> Res.string.importChanged
            ImportNotice.Unsupported -> Res.string.importUnsupported
            ImportNotice.Conflict -> Res.string.importConflict
            ImportNotice.Authorization -> Res.string.importAuthorization
            ImportNotice.ParentMissing -> Res.string.importParentMissing
            ImportNotice.Rejected -> Res.string.importRejected
            ImportNotice.Unconfirmed -> Res.string.importUnconfirmed
        }
    )
