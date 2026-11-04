package io.github.eslamasabry.oc.feature.prompts.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.prompts.presentation.PromptEditor
import io.github.eslamasabry.oc.feature.prompts.presentation.SavedPromptItem
import io.github.eslamasabry.oc.feature.prompts.presentation.SavedPromptsActions
import io.github.eslamasabry.oc.feature.prompts.presentation.SavedPromptsIssue
import io.github.eslamasabry.oc.feature.prompts.presentation.SavedPromptsUi
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.modal.KitUndoBar
import io.github.eslamasabry.oc.ui.kit.modal.KitUndoOffer
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.chatUiEdit
import io.github.eslamasabry.oc.ui.words.res.chatUiSlashCommandsAndAgents
import io.github.eslamasabry.oc.ui.words.res.chatUiTodos
import io.github.eslamasabry.oc.ui.words.res.commonRetry
import io.github.eslamasabry.oc.ui.words.res.composerToolSaveForLater
import io.github.eslamasabry.oc.ui.words.res.fileSave
import io.github.eslamasabry.oc.ui.words.res.kitConfirmCancel
import io.github.eslamasabry.oc.ui.words.res.kitCopyDetails
import io.github.eslamasabry.oc.ui.words.res.kitDetails
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.promptEditorFieldLabel
import io.github.eslamasabry.oc.ui.words.res.promptFixFilesStay
import io.github.eslamasabry.oc.ui.words.res.promptFixWordsOnly
import io.github.eslamasabry.oc.ui.words.res.promptStashAttachments
import io.github.eslamasabry.oc.ui.words.res.promptStashBusy
import io.github.eslamasabry.oc.ui.words.res.promptStashContextOnly
import io.github.eslamasabry.oc.ui.words.res.promptStashDelete
import io.github.eslamasabry.oc.ui.words.res.promptStashDeleteFailed
import io.github.eslamasabry.oc.ui.words.res.promptStashDeleted
import io.github.eslamasabry.oc.ui.words.res.promptStashEmptyTitle
import io.github.eslamasabry.oc.ui.words.res.promptStashFull
import io.github.eslamasabry.oc.ui.words.res.promptStashIntro
import io.github.eslamasabry.oc.ui.words.res.promptStashReadFailed
import io.github.eslamasabry.oc.ui.words.res.promptStashRestoreFailed
import io.github.eslamasabry.oc.ui.words.res.promptStashRestoreToDraft
import io.github.eslamasabry.oc.ui.words.res.promptStashSaveFailed
import io.github.eslamasabry.oc.ui.words.res.promptStashSearch
import io.github.eslamasabry.oc.ui.words.res.promptStashTitle
import io.github.eslamasabry.oc.ui.words.res.promptStashed
import io.github.eslamasabry.oc.ui.words.res.statePromptFilesUnavailable
import io.github.eslamasabry.oc.ui.words.res.statePromptMigrationPending
import kotlinx.coroutines.flow.drop
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Saved prompts (Flutter `_PromptStashSheet`), as a page opened from the chat. Obvious actions are
 * visible rows and buttons: "Save prompt for later" writes a new one; tapping a prompt puts it into
 * the conversation's composer (the saved copy stays); Edit and Delete sit under each prompt; Delete
 * acts at once with Undo. The server's commands and the agent's tasks are two rows at the top.
 */
@Composable
public fun SavedPromptsScreen(state: SavedPromptsUi, actions: SavedPromptsActions) {
    val search = remember { TextFieldState(state.query) }
    LaunchedEffect(search) {
        snapshotFlow { search.text.toString() }.collect { actions.search(it) }
    }
    val busyReason = stringResource(Res.string.promptStashBusy)
    val enabled = if (state.busy) KitEnabled.Disabled(busyReason) else KitEnabled.Enabled
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.promptStashTitle),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        bottom = {
            KitUndoBar(
                offer =
                    state.undo?.let {
                        KitUndoOffer(
                            it.id,
                            stringResource(Res.string.promptStashDeleted),
                            working = state.busy,
                        )
                    },
                onUndo = actions::undo,
                onTimeout = actions::undoTimedOut,
            )
        },
        width = KitScreenWidth.Reading,
    ) {
        Column(
            Modifier.fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LocalKitTokens.current.gutter.dp, vertical = Space.S3.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            KitRow(
                title = stringResource(Res.string.chatUiSlashCommandsAndAgents),
                trailing = KitRowTrailing.Chevron,
                onClick = actions::openCommands,
                modifier = Modifier.testTag("prompts-open-commands"),
            )
            KitRow(
                title = stringResource(Res.string.chatUiTodos),
                trailing = KitRowTrailing.Chevron,
                onClick = actions::openTasks,
                modifier = Modifier.testTag("prompts-open-tasks"),
            )
            Issue(state, actions)
            if (state.migrationPending)
                KitNotice(
                    stringResource(Res.string.statePromptMigrationPending),
                    tone = StatusTone.Attention,
                    actions =
                        listOf(
                            KitLineAction(stringResource(Res.string.commonRetry), actions::retry)
                        ),
                )
            val editor = state.editor
            if (editor != null) {
                Editor(editor, state.busy, enabled, actions)
            } else {
                KitButton(
                    stringResource(Res.string.composerToolSaveForLater),
                    actions::newPrompt,
                    role = KitButtonRole.Secondary,
                    enabled = enabled,
                    modifier = Modifier.testTag("prompts-new"),
                )
            }
            if (state.note != null)
                KitNotice(stringResource(Res.string.promptStashed), tone = StatusTone.Ok)
            when {
                state.loading -> KitSkeletonRows(Modifier.testTag("prompts-loading"), count = 4)
                state.issue == SavedPromptsIssue.ReadFailed -> Unit
                state.prompts.isEmpty() ->
                    KitStateView(
                        kind = KitStateKind.Empty,
                        title = stringResource(Res.string.promptStashEmptyTitle),
                        body = stringResource(Res.string.promptStashIntro),
                    )
                else -> {
                    KitField(
                        search,
                        stringResource(Res.string.promptStashSearch),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    KitText(stringResource(Res.string.promptStashIntro))
                    for (prompt in state.visible) PromptRow(
                        prompt,
                        state.busy,
                        busyReason,
                        enabled,
                        actions,
                    )
                }
            }
        }
    }
}

@Composable
private fun Issue(state: SavedPromptsUi, actions: SavedPromptsActions) {
    val issue = state.issue ?: return
    val words =
        stringResource(
            when (issue) {
                SavedPromptsIssue.ReadFailed -> Res.string.promptStashReadFailed
                SavedPromptsIssue.SaveFailed -> Res.string.promptStashSaveFailed
                SavedPromptsIssue.DeleteFailed -> Res.string.promptStashDeleteFailed
                SavedPromptsIssue.Full -> Res.string.promptStashFull
                SavedPromptsIssue.UndoFailed -> Res.string.promptStashRestoreFailed
                SavedPromptsIssue.FilesStay -> Res.string.promptFixFilesStay
            }
        )
    if (issue == SavedPromptsIssue.Full) {
        KitNotice(words, tone = StatusTone.Attention)
        return
    }
    if (issue == SavedPromptsIssue.FilesStay) {
        val id = state.wordsOnly
        KitNotice(
            words,
            tone = StatusTone.Attention,
            actions =
                if (id == null) emptyList()
                else
                    listOf(
                        KitLineAction(
                            stringResource(Res.string.promptFixWordsOnly),
                            { actions.insertWordsOnly(id) },
                        )
                    ),
        )
        return
    }
    KitErrorNotice(
        words,
        failure = state.failure,
        detailsLabel = stringResource(Res.string.kitDetails),
        copyLabel = stringResource(Res.string.kitCopyDetails),
        retry =
            when (issue) {
                SavedPromptsIssue.ReadFailed ->
                    KitLineAction(stringResource(Res.string.commonRetry), actions::retry)
                // A failed Undo can be tried again while the deleted prompt is still held
                // (B-STATE-085).
                SavedPromptsIssue.UndoFailed ->
                    if (state.undo != null)
                        KitLineAction(stringResource(Res.string.commonRetry), actions::undo)
                    else null
                else -> null
            },
    )
}

@Composable
private fun Editor(
    editor: PromptEditor,
    busy: Boolean,
    enabled: KitEnabled,
    actions: SavedPromptsActions,
) {
    val field = remember(editor.generation) { TextFieldState(editor.initial) }
    // Typed words go to the view model, so a recreated page (rotation) opens with them.
    LaunchedEffect(field) {
        snapshotFlow { field.text.toString() }.drop(1).collect(actions::editorChanged)
    }
    KitField(
        field,
        stringResource(Res.string.promptEditorFieldLabel),
        kind = KitFieldKind.Multiline,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().testTag("prompts-editor"),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
        KitButton(
            stringResource(Res.string.fileSave),
            { actions.submitEdit(field.text.toString()) },
            enabled = enabled,
            working = busy,
            modifier = Modifier.testTag("prompts-editor-save"),
        )
        KitButton(
            stringResource(Res.string.kitConfirmCancel),
            actions::cancelEdit,
            role = KitButtonRole.Tertiary,
            enabled = enabled,
        )
    }
}

@Composable
private fun PromptRow(
    prompt: SavedPromptItem,
    busy: Boolean,
    busyReason: String,
    enabled: KitEnabled,
    actions: SavedPromptsActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
        if (prompt.unavailable.isNotEmpty())
            KitNotice(
                stringResource(
                    Res.string.statePromptFilesUnavailable,
                    prompt.unavailable.joinToString(", "),
                ),
                tone = StatusTone.Attention,
            )
        val title = prompt.text.ifBlank { stringResource(Res.string.promptStashContextOnly) }
        KitRow(
            title = title,
            supporting =
                if (prompt.attachments > 0)
                    pluralStringResource(
                        Res.plurals.promptStashAttachments,
                        prompt.attachments,
                        prompt.attachments,
                    )
                else null,
            supportingMaxLines = 2,
            enabled = !busy,
            disabledReason = if (busy) busyReason else null,
            onClickLabel = stringResource(Res.string.promptStashRestoreToDraft),
            onClick = if (prompt.text.isBlank()) null else ({ actions.insert(prompt.id) }),
            modifier = Modifier.testTag("prompt-row-${prompt.id}"),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
            KitButton(
                stringResource(Res.string.promptStashRestoreToDraft),
                { actions.insert(prompt.id) },
                role = KitButtonRole.Secondary,
                enabled = if (prompt.text.isBlank()) KitEnabled.Disabled(title) else enabled,
            )
            KitButton(
                stringResource(Res.string.chatUiEdit),
                { actions.editPrompt(prompt.id) },
                role = KitButtonRole.Tertiary,
                enabled = enabled,
            )
            KitButton(
                stringResource(Res.string.promptStashDelete),
                { actions.delete(prompt.id) },
                role = KitButtonRole.Tertiary,
                destructive = true,
                enabled = enabled,
                modifier = Modifier.testTag("prompt-delete-${prompt.id}"),
            )
        }
    }
}
