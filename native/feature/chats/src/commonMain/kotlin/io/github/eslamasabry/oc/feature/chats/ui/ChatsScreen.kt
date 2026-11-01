package io.github.eslamasabry.oc.feature.chats.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsActions
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsAllActions
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsAllUi
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitScreen
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextRole
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
