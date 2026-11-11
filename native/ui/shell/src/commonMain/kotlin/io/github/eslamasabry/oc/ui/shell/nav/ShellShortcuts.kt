package io.github.eslamasabry.oc.ui.shell.nav

import androidx.compose.ui.input.key.Key
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.routes.SearchRoute
import io.github.eslamasabry.oc.ui.routes.SettingsRoute

/** What a shell accelerator does (F: lib/ui/desktop/shortcuts.dart `appShortcutBindings`). */
public enum class ShellShortcut {
    NewConversation,
    Chats,
    Settings,
    CloseScreen,
    ShortcutList,

    /** Ctrl/⌘ + K away from a conversation: Search, whose Go to menu lists the app's places. */
    Launcher,
}

/**
 * The shell's keyboard shortcuts, the list About › Keyboard shortcuts shows (`ShortcutHelp` in :feature:settings):
 * Ctrl or ⌘ + N, K (away from a conversation), 1, 2, comma, W and slash. Every binding carries the modifier, so typing in a field never fires one,
 * and Alt or Shift with it is another key (left to the page). Escape stays the platform's own close.
 */
public object ShellShortcuts {
    public fun of(key: Key, ctrl: Boolean, meta: Boolean, alt: Boolean, shift: Boolean): ShellShortcut? {
        if (!(ctrl || meta) || alt || shift) return null
        return when (key) {
            Key.N -> ShellShortcut.NewConversation
            Key.One, Key.NumPad1 -> ShellShortcut.Chats
            Key.Two, Key.NumPad2 -> ShellShortcut.Settings
            Key.Comma -> ShellShortcut.Settings
            Key.W -> ShellShortcut.CloseScreen
            Key.Slash -> ShellShortcut.ShortcutList
            Key.K -> ShellShortcut.Launcher
            else -> null
        }
    }

    /**
     * Is [shortcut] the shell's to use on [top]? Ctrl+K inside a conversation belongs to the conversation (its own
     * commands launcher, as in Flutter's `onAppShortcut`); every other shortcut is the shell's everywhere.
     */
    public fun claims(shortcut: ShellShortcut, top: AppRoute?): Boolean =
        shortcut != ShellShortcut.Launcher || top !is ChatRoute

    /** Does [shortcut] on [navigator]; [top] is the page on screen (a destination is not reopened over itself). */
    public fun perform(shortcut: ShellShortcut, navigator: AppNavigator, top: AppRoute?) {
        when (shortcut) {
            ShellShortcut.NewConversation -> navigator.open(ChatsRoute.New(profile = null))
            ShellShortcut.Chats -> if (top != ChatsRoute.Home) navigator.land()
            ShellShortcut.Settings -> openSettings(navigator, top)
            ShellShortcut.CloseScreen -> navigator.back()
            ShellShortcut.ShortcutList -> navigator.open(SettingsRoute.KeyboardShortcuts)
            ShellShortcut.Launcher -> if (top !is SearchRoute.Conversations) navigator.open(SearchRoute.Conversations())
        }
    }

    /** Settings over Conversations, so Back returns to the list (F: a phone opens Settings as a page). */
    public fun openSettings(navigator: AppNavigator, top: AppRoute?) {
        if (top == SettingsRoute.Home) return
        if (top != ChatsRoute.Home) navigator.land()
        navigator.open(SettingsRoute.Home)
    }
}
