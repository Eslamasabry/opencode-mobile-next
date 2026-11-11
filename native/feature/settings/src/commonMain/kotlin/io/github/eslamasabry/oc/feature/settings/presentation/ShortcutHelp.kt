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
    Launcher,
    Find,
    Terminal,

    /** Conversation only. */
    SendPrompt,
    RecentModel,
    BackgroundWork,
    FindMatch,
    PromptHistory,
}

/** Where a shortcut works: the page shows one group per value (F: `ShortcutHelpGroup`). */
public enum class ShortcutGroup { Anywhere, Conversation }

/** One row of About › Keyboard shortcuts. [keys] is a key name list ("Ctrl + N"), the same in every language. */
public data class ShortcutHelpEntry(
    val keys: String,
    val does: ShortcutDoes,
    val group: ShortcutGroup = ShortcutGroup.Anywhere,
)

/**
 * The shortcuts this build answers, in Flutter's order (F: lib/ui/desktop/shortcuts.dart:146). Only keys the
 * shell really binds are listed (ShellShortcuts in :ui:shell): no shortcut is known only to the people who
 * guessed it, and none is listed that does nothing. The conversation's own keys (send, find next, recent
 * model) are listed under their own group: ChatKeys in :feature:chat and the composer bind them.
 */
public object ShortcutHelp {
    public fun entries(modifier: String): ImmutableList<ShortcutHelpEntry> =
        persistentListOf(
            ShortcutHelpEntry("$modifier + K", ShortcutDoes.Launcher),
            ShortcutHelpEntry("$modifier + N", ShortcutDoes.NewConversation),
            ShortcutHelpEntry("$modifier + F", ShortcutDoes.Find),
            ShortcutHelpEntry("$modifier + 1 / 2", ShortcutDoes.Destinations),
            ShortcutHelpEntry("$modifier + ,", ShortcutDoes.Settings),
            ShortcutHelpEntry("$modifier + `", ShortcutDoes.Terminal),
            ShortcutHelpEntry("$modifier + W", ShortcutDoes.CloseScreen),
            ShortcutHelpEntry("$modifier + /", ShortcutDoes.ThisList),
            ShortcutHelpEntry("Esc", ShortcutDoes.CloseOverlay),
            ShortcutHelpEntry("$modifier + Enter", ShortcutDoes.SendPrompt, ShortcutGroup.Conversation),
            ShortcutHelpEntry("F2 / Shift + F2", ShortcutDoes.RecentModel, ShortcutGroup.Conversation),
            ShortcutHelpEntry("$modifier + B", ShortcutDoes.BackgroundWork, ShortcutGroup.Conversation),
            ShortcutHelpEntry("F3 / Shift + F3", ShortcutDoes.FindMatch, ShortcutGroup.Conversation),
            ShortcutHelpEntry("↑ / ↓", ShortcutDoes.PromptHistory, ShortcutGroup.Conversation),
        )
}
