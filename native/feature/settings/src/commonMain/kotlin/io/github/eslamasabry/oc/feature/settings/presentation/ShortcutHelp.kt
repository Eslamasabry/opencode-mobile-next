package io.github.eslamasabry.oc.feature.settings.presentation

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** What a listed shortcut does; the screen gives each its words (F: shortcuts.dart `shortcutHelp`). */
public enum class ShortcutDoes {
    NewConversation,
    Destinations,
    Settings,
    CloseScreen,
    ThisList,
    CloseOverlay,
}

/** One row of About › Keyboard shortcuts. [keys] is a key name list ("Ctrl + N"), the same in every language. */
public data class ShortcutHelpEntry(val keys: String, val does: ShortcutDoes)

/**
 * The shortcuts this build answers, in Flutter's order (F: lib/ui/desktop/shortcuts.dart:146). Only keys the
 * shell really binds are listed (ShellShortcuts in :ui:shell): no shortcut is known only to the people who
 * guessed it, and none is listed that does nothing. The conversation's own keys (send, find next, recent
 * model) join this list when the conversation binds them.
 */
public object ShortcutHelp {
    public fun entries(modifier: String): ImmutableList<ShortcutHelpEntry> =
        persistentListOf(
            ShortcutHelpEntry("$modifier + N", ShortcutDoes.NewConversation),
            ShortcutHelpEntry("$modifier + 1 / 2", ShortcutDoes.Destinations),
            ShortcutHelpEntry("$modifier + ,", ShortcutDoes.Settings),
            ShortcutHelpEntry("$modifier + W", ShortcutDoes.CloseScreen),
            ShortcutHelpEntry("$modifier + /", ShortcutDoes.ThisList),
            ShortcutHelpEntry("Esc", ShortcutDoes.CloseOverlay),
        )
}
