package io.github.eslamasabry.oc.feature.servers.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.servers.presentation.*
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.*
import io.github.eslamasabry.oc.ui.kit.choice.*
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.frame.*
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHeight
import io.github.eslamasabry.oc.ui.kit.row.*
import io.github.eslamasabry.oc.ui.kit.state.*
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Flutter's move sheet on the existing route. The queue port remains the only writer. */
@Composable
public fun ServerQueueScreen(state: ServerQueueUi, actions: ServerQueueActions) {
    val plan = state.plan
    val moved = state.moved
    val enabled = KitEnabled.of(!state.busy, stringResource(Res.string.serversWorking))
    // A formerly selected prompt may since have become blocked; only movable picks count.
    val selected =
        plan
            ?.items
            .orEmpty()
            .filter { it.blocked == null && it.id in state.selected }
            .map { it.id }
            .toSet()
    val primary =
        when {
            moved != null && state.undone == null ->
                KitAction(
                    stringResource(Res.string.queue40Open),
                    actions::openConversation,
                    enabled = enabled,
                )
            moved != null -> null
            plan != null ->
                KitAction(
                    if (selected.size == 1)
                        stringResource(
                            Res.string.queuedMoveAction__eq1,
                            selected.size,
                            plan.destinationName,
                        )
                    else
                        pluralStringResource(
                            Res.plurals.queuedMoveAction,
                            selected.size,
                            selected.size,
                            plan.destinationName,
                        ),
                    actions::move,
                    icon = KitIcons.forward,
                    working = state.busy,
                    enabled =
                        KitEnabled.of(
                            !state.busy && selected.isNotEmpty(),
                            stringResource(Res.string.queuedMoveChooseOne),
                        ),
                )
            else -> null
        }
    KitScreen(width = KitScreenWidth.Reading) {
        KitSheet(
            visible = true,
            onDismissRequest = actions::back,
            title = stringResource(Res.string.queuedMoveTitle),
            subtitle = plan?.let { stringResource(Res.string.queuedMoveSubtitle, it.sourceName) },
            icon = KitIcons.forward,
            height = KitSheetHeight.Full,
            dismissible = !state.busy,
            loading = state.loading,
            primary = primary,
            secondary =
                if (moved != null && state.undone == null)
                    KitAction(
                        stringResource(Res.string.queue40Undo),
                        actions::undo,
                        enabled = enabled,
                        working = state.busy,
                    )
                else
                    KitAction(
                        stringResource(Res.string.kitConfirmCancel),
                        actions::back,
                        enabled = enabled,
                    ),
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
            ) {
                state.failure?.let {
                    KitErrorNotice(
                        stringResource(
                            if (moved != null) Res.string.queue40UndoFailed
                            else Res.string.queue40Failed
                        ),
                        failure = it,
                        detailsLabel = stringResource(Res.string.kitDetails),
                        copyLabel = stringResource(Res.string.kitCopyDetails),
                    )
                }
                if (plan != null && moved == null) {
                    KitSectionLabel(
                        stringResource(Res.string.queuedMovePromptsLabel),
                        margin = 0.dp,
                    )
                    KitChoiceListMulti(
                        choices =
                            plan.items.map { item ->
                                KitChoice(
                                    value = item.id,
                                    title =
                                        item.preview.ifBlank {
                                            stringResource(Res.string.queue40Attachment)
                                        },
                                    enabled =
                                        if (item.blocked == null) enabled
                                        else
                                            KitEnabled.Disabled(
                                                stringResource(
                                                    when (item.blocked) {
                                                        QueueMoveBlock.Uncertain ->
                                                            Res.string.queuedMoveBlockedUncertain
                                                        QueueMoveBlock.ServerFile ->
                                                            Res.string.queuedMoveBlockedFile
                                                        QueueMoveBlock.SecretMentions ->
                                                            Res.string.queuedMoveBlockedMentions
                                                        else -> Res.string.queue40Sending
                                                    },
                                                    plan.sourceName,
                                                )
                                            ),
                                )
                            },
                        selected = selected,
                        showCount = false,
                        semanticsLabel = stringResource(Res.string.queuedMovePromptsLabel),
                        modifier = Modifier.testTag("queued-move-prompts"),
                        empty = {
                            KitStateView(
                                KitStateKind.Empty,
                                stringResource(Res.string.queuedMoveNoneLeft, plan.sourceName),
                            )
                        },
                        onChange = { next ->
                            (selected union next)
                                .filter { (it in selected) != (it in next) }
                                .forEach(actions::toggle)
                        },
                    )
                    Spacer(Modifier.height(Space.S3.dp))
                    KitSectionLabel(
                        stringResource(Res.string.queuedMoveConversationLabel),
                        margin = 0.dp,
                    )
                    // The non-null value is an index into this snapshot; null means no list
                    // selection,
                    // so the new-conversation choice itself must have a concrete value.
                    val conversations =
                        plan.conversations.take(
                            8
                        ) // F: lib/ui/widgets/queued_prompt_move_sheet.dart:276
                    KitChoiceList(
                        choices =
                            listOf(
                                KitChoice(
                                    0,
                                    stringResource(Res.string.queuedMoveNewConversation),
                                    enabled = enabled,
                                )
                            ) +
                                conversations.mapIndexed { index, conversation ->
                                    KitChoice(
                                        index + 1,
                                        conversation.title?.trim()?.takeIf { it.isNotEmpty() }
                                            ?: stringResource(Res.string.commandUntitledChat),
                                        enabled = enabled,
                                    )
                                },
                        selected =
                            if (state.conversation == null) 0
                            else
                                conversations
                                    .indexOfFirst { it.id == state.conversation }
                                    .takeIf { it >= 0 }
                                    ?.plus(1),
                        semanticsLabel = stringResource(Res.string.queuedMoveConversationLabel),
                        modifier = Modifier.testTag("queued-move-conversations"),
                        onSelect = {
                            actions.choose(if (it == 0) null else conversations[it - 1].id)
                        },
                    )
                }
                if (moved != null) {
                    KitNotice(
                        if (moved.skipped > 0 && plan != null)
                            stringResource(
                                Res.string.queuedMoveDonePartial,
                                moved.count,
                                moved.count + moved.skipped,
                                plan.destinationName,
                            )
                        else if (plan != null) {
                            if (moved.count == 1)
                                stringResource(
                                    Res.string.queuedMoveDone__eq1,
                                    moved.count,
                                    plan.destinationName,
                                )
                            else
                                pluralStringResource(
                                    Res.plurals.queuedMoveDone,
                                    moved.count,
                                    moved.count,
                                    plan.destinationName,
                                )
                        } else
                            stringResource(
                                Res.string.queue40Moved,
                                moved.count,
                                moved.skipped,
                                moved.reset,
                            )
                    )
                    state.undone?.let { KitNotice(stringResource(Res.string.queue40Undone, it)) }
                }
                if (!state.loading && (plan == null || state.failure != null))
                    KitButton(
                        stringResource(Res.string.queue40Refresh),
                        actions::refresh,
                        role = KitButtonRole.Tertiary,
                        enabled = enabled,
                    )
            }
        }
    }
}
