package io.github.eslamasabry.oc.ui.shell.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.ui.design.layout.WindowClass
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.nav.KitNavDestination
import io.github.eslamasabry.oc.ui.kit.nav.KitNavRail
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.routes.SettingsRoute
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.librarySettingsTitle
import io.github.eslamasabry.oc.ui.words.res.shellTabChats
import org.jetbrains.compose.resources.stringResource

/** The shell's destinations on a wider window, in Flutter's order (F: home_screen.dart:390). */
public enum class ShellDestination {
    Chats,
    Settings,
}

/** Which rail destination a page is, or null for a page that covers the rail (a conversation, a form). */
public object ShellRailRules {
    /** F: the rail exists from medium (`KitNav`, 600 dp); a phone (compact) has no navigation bar at all. */
    public fun shows(window: WindowClass): Boolean = window >= WindowClass.Medium

    public fun destinationOf(top: AppRoute?): ShellDestination? =
        when (top) {
            ChatsRoute.Home -> ShellDestination.Chats
            SettingsRoute.Home -> ShellDestination.Settings
            else -> null
        }
}

/**
 * The rail beside the shell's two destinations (F: home_screen.dart `KitNav` from medium): Conversations and Settings.
 * Files has no destination here because native Files opens from a conversation (owner, see the s28 report). Pages
 * pushed over a destination (a conversation, a form) cover the whole window, as Flutter pushes them over the shell.
 * On a phone ([ShellRailRules.shows] false) the content is drawn alone, unchanged.
 */
@Composable
public fun ShellRailFrame(top: AppRoute?, onSelect: (ShellDestination) -> Unit, content: @Composable () -> Unit) {
    val current = ShellRailRules.destinationOf(top)
    val shown = ShellRailRules.shows(LocalKitWindow.current.cls) && current != null
    // One stable tree: the pages keep their place (and the navigation its state) when the rail comes and goes.
    Row(Modifier.fillMaxSize()) {
        if (shown && current != null) {
            val destinations =
                listOf(
                    KitNavDestination(
                        id = ShellDestination.Chats.name,
                        label = stringResource(Res.string.shellTabChats),
                        icon = KitIcons.chat,
                    ),
                    KitNavDestination(
                        id = ShellDestination.Settings.name,
                        label = stringResource(Res.string.librarySettingsTitle),
                        icon = KitIcons.settings,
                    ),
                )
            KitNavRail(
                destinations = destinations,
                selectedId = current.name,
                onSelect = { onSelect(ShellDestination.valueOf(it.id)) },
                modifier = Modifier.fillMaxHeight().testTag("shell-rail"),
            )
        }
        Box(Modifier.weight(1f).fillMaxHeight()) { content() }
    }
}
