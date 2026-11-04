package io.github.eslamasabry.oc.feature.servers.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.servers.presentation.*
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.*
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.frame.*
import io.github.eslamasabry.oc.ui.kit.row.*
import io.github.eslamasabry.oc.ui.kit.state.*
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

@Composable
public fun ServerQueueScreen(state: ServerQueueUi, actions: ServerQueueActions) {
    val enabled =
        if (state.busy) KitEnabled.Disabled(stringResource(Res.string.serversWorking))
        else KitEnabled.Enabled
    KitScreen(
        topBar = {
            KitTopBar(
                stringResource(Res.string.queue40Title),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        width = KitScreenWidth.Reading,
    ) {
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(LocalKitTokens.current.gutter.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            if (state.loading) KitNotice(stringResource(Res.string.queue40Loading))
            state.failure?.let {
                KitErrorNotice(
                    stringResource(
                        if (state.moved != null) Res.string.queue40UndoFailed
                        else Res.string.queue40Failed
                    ),
                    failure = it,
                    detailsLabel = stringResource(Res.string.kitDetails),
                    copyLabel = stringResource(Res.string.kitCopyDetails),
                )
            }
            val plan = state.plan
            val moved = state.moved
            if (plan != null && moved == null) {
                KitText(
                    stringResource(Res.string.queue40Intro, plan.sourceName, plan.destinationName)
                )
                if (plan.items.isEmpty()) KitNotice(stringResource(Res.string.queue40Empty))
                for (item in plan.items) KitSwitchRow(
                    title = item.preview.ifBlank { stringResource(Res.string.queue40Attachment) },
                    supporting =
                        item.blocked?.let {
                            stringResource(
                                when (it) {
                                    QueueMoveBlock.Uncertain -> Res.string.queue40Uncertain
                                    QueueMoveBlock.ServerFile -> Res.string.queue40ServerFile
                                    QueueMoveBlock.SecretMentions -> Res.string.queue40Mentions
                                    QueueMoveBlock.Sending -> Res.string.queue40Sending
                                }
                            )
                        },
                    checked = item.id in state.selected,
                    enabled =
                        if (item.blocked != null)
                            KitEnabled.Disabled(stringResource(Res.string.queue40CannotMove))
                        else enabled,
                    onCheckedChange = { actions.toggle(item.id) },
                )
                KitText(stringResource(Res.string.queue40Destination))
                KitRow(
                    title = stringResource(Res.string.queue40NewConversation),
                    selected = state.conversation == null,
                    onClick = { actions.choose(null) },
                )
                for (conversation in plan.conversations) KitRow(
                    title =
                        conversation.title?.takeIf { it.isNotBlank() }
                            ?: stringResource(Res.string.queue40Untitled),
                    selected = state.conversation == conversation.id,
                    onClick = { actions.choose(conversation.id) },
                )
                KitButton(
                    stringResource(
                        Res.string.queue40Move,
                        state.selected.size,
                        plan.destinationName,
                    ),
                    actions::move,
                    enabled =
                        if (state.selected.isEmpty())
                            KitEnabled.Disabled(stringResource(Res.string.queue40Select))
                        else enabled,
                    working = state.busy,
                )
            }
            if (moved != null) {
                KitNotice(
                    stringResource(Res.string.queue40Moved, moved.count, moved.skipped, moved.reset)
                )
                if (state.undone == null) {
                    KitButton(
                        stringResource(Res.string.queue40Undo),
                        actions::undo,
                        role = KitButtonRole.Secondary,
                        enabled = enabled,
                    )
                    KitButton(
                        stringResource(Res.string.queue40Open),
                        actions::openConversation,
                        enabled = enabled,
                    )
                } else KitNotice(stringResource(Res.string.queue40Undone, state.undone))
            }
            KitButton(
                stringResource(Res.string.queue40Refresh),
                actions::refresh,
                role = KitButtonRole.Tertiary,
                enabled = enabled,
            )
        }
    }
}
