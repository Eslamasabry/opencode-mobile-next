package io.github.eslamasabry.oc.feature.chat.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds

/**
 * The edge dock inside a conversation, bound by the app (features may not depend on each other, and the back-gesture
 * exclusion is Android's). The chat places it over its transcript and under its own sheets, dialogs, menus and
 * viewers; [chrome] is the room the overlaid header and composer take (with the keyboard), which the dock keeps clear
 * of. [onBottomRoom] is the room (px) the dock's Undo bar takes while it shows, 0 when it goes: the transcript keeps
 * that much extra room so its newest content ends above the bar. Without a binding (desktop, tests) the chat draws no dock.
 */
public interface ChatEdgeDock {
    @Composable public fun Content(chrome: PaddingValues, onBottomRoom: (Int) -> Unit)
}

@ContributesTo(AppScope::class)
public interface ChatEdgeDockBindings {
    @Multibinds(allowEmpty = true) public val chatEdgeDocks: Set<ChatEdgeDock>
}
