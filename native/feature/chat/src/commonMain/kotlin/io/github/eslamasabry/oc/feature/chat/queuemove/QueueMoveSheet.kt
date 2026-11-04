package io.github.eslamasabry.oc.feature.chat.queuemove

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipKind
import io.github.eslamasabry.oc.ui.kit.chip.KitChipWrap
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.chatQueueMoveChooseDestination
import io.github.eslamasabry.oc.ui.words.res.chatQueueMoveChoosePrompt
import io.github.eslamasabry.oc.ui.words.res.chatQueueMoveChooseServer
import io.github.eslamasabry.oc.ui.words.res.chatQueueMoveEmpty
import io.github.eslamasabry.oc.ui.words.res.chatQueueMoveFailure
import io.github.eslamasabry.oc.ui.words.res.chatQueueMoveMore
import io.github.eslamasabry.oc.ui.words.res.chatQueueMoveSelectionNote
import io.github.eslamasabry.oc.ui.words.res.chatQueueMoveTitle
import io.github.eslamasabry.oc.ui.words.res.commandUntitledChat
import io.github.eslamasabry.oc.ui.words.res.kitSheetClose
import org.jetbrains.compose.resources.stringResource

@Composable
public fun QueueMoveScreen(state: QueueMoveUi, actions: QueueMoveActions) {
    KitSheet(
        visible = state.open,
        onDismissRequest = actions::close,
        title = stringResource(Res.string.chatQueueMoveTitle),
        closeLabel = stringResource(Res.string.kitSheetClose),
    ) {
        Column {
            if (state.failure != null)
                KitErrorNotice(
                    message = stringResource(Res.string.chatQueueMoveFailure),
                    failure = state.failure,
                )
            if (state.prompts.isEmpty()) KitNotice(stringResource(Res.string.chatQueueMoveEmpty))
            else {
                KitRowGroup(label = stringResource(Res.string.chatQueueMoveChoosePrompt)) {
                    state.prompts.forEach { prompt ->
                        io.github.eslamasabry.oc.ui.kit.choice.KitChoiceRow(
                            title = prompt.text,
                            selected = prompt.id == state.prompt,
                            onClick = { actions.choosePrompt(prompt.id) },
                        )
                    }
                }
                if (state.prompt != null) {
                    KitNotice(stringResource(Res.string.chatQueueMoveChooseServer))
                    KitChipWrap {
                        state.servers.forEach { server ->
                            KitChip(
                                label = server.name,
                                kind = KitChipKind.Action,
                                selected = state.server == server.id,
                                onClick = { actions.chooseServer(server.id) },
                            )
                        }
                    }
                    if (state.server != null) {
                        KitNotice(stringResource(Res.string.chatQueueMoveSelectionNote))
                        KitRowGroup(
                            label = stringResource(Res.string.chatQueueMoveChooseDestination)
                        ) {
                            state.destinations.forEach { destination ->
                                KitRow(
                                    title =
                                        destination.title
                                            ?: stringResource(Res.string.commandUntitledChat),
                                    enabled = !state.working,
                                    onClick = { actions.move(destination.ref) },
                                )
                            }
                        }
                        if (state.more)
                            KitButton(
                                stringResource(Res.string.chatQueueMoveMore),
                                actions::loadMore,
                                role = KitButtonRole.Tertiary,
                                working = state.working,
                            )
                    }
                }
            }
        }
    }
}
