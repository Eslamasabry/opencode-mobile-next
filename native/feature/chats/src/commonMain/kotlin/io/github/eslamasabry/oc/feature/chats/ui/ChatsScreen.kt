package io.github.eslamasabry.oc.feature.chats.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.chats.presentation.ChatPreviewActions
import io.github.eslamasabry.oc.feature.chats.presentation.ChatPreviewUi
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsActions
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsAllActions
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsAllUi
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitRow
import io.github.eslamasabry.oc.ui.kit.KitScreen
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextRole
import io.github.eslamasabry.oc.ui.kit.motion.KitGrowPage
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.chats_all_filter
import io.github.eslamasabry.oc.ui.words.res.chats_all_none
import io.github.eslamasabry.oc.ui.words.res.chats_all_title
import io.github.eslamasabry.oc.ui.words.res.chats_back
import io.github.eslamasabry.oc.ui.words.res.chats_open_all
import io.github.eslamasabry.oc.ui.words.res.chats_placeholder
import io.github.eslamasabry.oc.ui.words.res.chats_title
import org.jetbrains.compose.resources.stringResource

/** Arranges kit parts only (Konsist K1). */
@Composable
public fun ChatsScreen(state: ChatsUi, actions: ChatsActions) {
    KitScreen(title = stringResource(Res.string.chats_title)) {
        KitText(stringResource(Res.string.chats_placeholder), KitTextRole.Supporting)
        KitButton(label = stringResource(Res.string.chats_open_all), onClick = actions::showAll)
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
        ) {
            items(state.rows, key = { it.ref.sessionId.raw }) { row ->
                // The row registers its grow key; the chat page grows out of it and shrinks back into it (P2).
                KitRow(
                    title = row.title,
                    supporting = row.preview,
                    onClick = { actions.openConversation(row.ref) },
                    growKey = row.ref,
                )
            }
        }
    }
}

/** P2 proof page for ChatRoute: the box a row grows into (plan 08 §5.4). */
@Composable
public fun ChatPreviewScreen(state: ChatPreviewUi, actions: ChatPreviewActions) {
    KitGrowPage(growKey = state.ref) {
        KitScreen(
            title = state.title,
            backLabel = stringResource(Res.string.chats_back),
            onBack = actions::back,
        ) {
            state.lines.forEach { KitText(it, KitTextRole.Body) }
        }
    }
}

@Composable
public fun ChatsAllScreen(state: ChatsAllUi, actions: ChatsAllActions) {
    KitScreen(
        title = stringResource(Res.string.chats_all_title),
        backLabel = stringResource(Res.string.chats_back),
        onBack = actions::back,
    ) {
        val filter = state.filter ?: stringResource(Res.string.chats_all_none)
        KitText(stringResource(Res.string.chats_all_filter, filter), KitTextRole.Supporting)
    }
}
