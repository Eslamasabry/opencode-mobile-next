package io.github.eslamasabry.oc.feature.prompts.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.prompts.presentation.*
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.*
import io.github.eslamasabry.oc.ui.kit.foundation.*
import io.github.eslamasabry.oc.ui.kit.frame.*
import io.github.eslamasabry.oc.ui.kit.input.*
import io.github.eslamasabry.oc.ui.kit.modal.*
import io.github.eslamasabry.oc.ui.kit.row.*
import io.github.eslamasabry.oc.ui.kit.state.*
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Kept prompts remain usable even when their original server and all its local rows are gone. */
@Composable
public fun KeptPromptsScreen(state: KeptPromptsUi, actions: KeptPromptsActions) {
    val search = remember { TextFieldState(state.query) }
    LaunchedEffect(search) { snapshotFlow { search.text.toString() }.collect(actions::search) }
    val busy = stringResource(Res.string.promptStashBusy)
    val enabled = if (state.busy) KitEnabled.Disabled(busy) else KitEnabled.Enabled
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.keptPromptsTitle),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
                actions =
                    listOf(
                        KitLineAction(
                            stringResource(Res.string.commonRetry),
                            actions::retry,
                            enabled = !state.busy,
                            disabledReason = busy,
                        )
                    ),
            )
        },
        bottom = {
            KitUndoBar(
                offer =
                    state.undo?.let {
                        KitUndoOffer(
                            it,
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LocalKitTokens.current.gutter.dp, vertical = Space.S3.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            KitText(stringResource(Res.string.keptPromptsBody))
            if (state.failure != null)
                KitErrorNotice(
                    stringResource(Res.string.keptPromptsFailed),
                    failure = state.failure,
                    detailsLabel = stringResource(Res.string.kitDetails),
                    copyLabel = stringResource(Res.string.kitCopyDetails),
                    retry = KitLineAction(stringResource(Res.string.commonRetry), actions::retry),
                )
            when {
                state.loading -> KitSkeletonRows(count = 3)
                state.failure != null && state.prompts.isEmpty() -> Unit
                state.prompts.isEmpty() ->
                    KitStateView(KitStateKind.Empty, stringResource(Res.string.keptPromptsEmpty))
                else -> {
                    KitField(
                        search,
                        stringResource(Res.string.promptStashSearch),
                        Modifier.fillMaxWidth(),
                    )
                    for (prompt in state.visible) KitRowGroup {
                        KitRow(
                            title =
                                prompt.text.ifBlank {
                                    stringResource(Res.string.promptStashContextOnly)
                                },
                            supporting =
                                if (prompt.attachments > 0)
                                    pluralStringResource(
                                        Res.plurals.promptStashAttachments,
                                        prompt.attachments,
                                        prompt.attachments,
                                    )
                                else null,
                            supportingMaxLines = 2,
                            onClick = { actions.restore(prompt.id) },
                            enabled = !state.busy,
                            disabledReason = busy,
                        )
                        if (prompt.uncertain)
                            KitNotice(stringResource(Res.string.keptPromptsUncertain))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
                            KitButton(
                                stringResource(Res.string.promptStashRestoreToDraft),
                                { actions.restore(prompt.id) },
                                role = KitButtonRole.Secondary,
                                enabled = enabled,
                            )
                            if (prompt.attachments > 0 && prompt.text.isNotBlank())
                                KitButton(
                                    stringResource(Res.string.promptFixWordsOnly),
                                    { actions.restoreWordsOnly(prompt.id) },
                                    role = KitButtonRole.Tertiary,
                                    enabled = enabled,
                                )
                            KitButton(
                                stringResource(Res.string.promptStashDelete),
                                { actions.delete(prompt.id) },
                                role = KitButtonRole.Tertiary,
                                enabled = enabled,
                                destructive = true,
                            )
                        }
                    }
                }
            }
        }
        KitSheet(
            visible = state.choosing != null,
            onDismissRequest = actions::closePicker,
            title = stringResource(Res.string.keptPromptsChoose),
            closeLabel = stringResource(Res.string.kitConfirmCancel),
            height = KitSheetHeight.Half,
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
            ) {
                KitText(stringResource(Res.string.keptPromptsChooseBody))
                if (state.failure != null)
                    KitNotice(stringResource(Res.string.keptPromptsRestoreFailed))
                if (state.destinations.isEmpty())
                    KitStateView(
                        KitStateKind.Empty,
                        stringResource(Res.string.keptPromptsNoTargets),
                        body = stringResource(Res.string.keptPromptsNoTargetsBody),
                        primary =
                            KitAction(
                                stringResource(Res.string.keptPromptsConnect),
                                actions::openServers,
                            ),
                    )
                else
                    KitRowGroup {
                        for (destination in state.destinations) KitRow(
                            title = destination.title,
                            server = destination.server,
                            onClick = { actions.choose(destination.ref) },
                            enabled = !state.busy,
                            disabledReason = busy,
                            trailing = KitRowTrailing.Chevron,
                        )
                    }
            }
        }
    }
}
