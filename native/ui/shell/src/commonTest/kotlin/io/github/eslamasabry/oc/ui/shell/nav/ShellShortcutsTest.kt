package io.github.eslamasabry.oc.ui.shell.nav

import androidx.compose.ui.input.key.Key
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.ui.design.layout.WindowClass
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.routes.SettingsRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** s28-app story 8: the shell's desktop shortcuts and the wide-window rail rules. */
class ShellShortcutsTest {
    private class Recorder : AppNavigator {
        val calls = mutableListOf<String>()
        val opened = mutableListOf<AppRoute>()
        override val top: StateFlow<AppRoute?> = MutableStateFlow(null)

        override fun open(route: AppRoute) {
            opened += route
            calls += "open"
        }

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) {
            calls += "chat"
        }

        override fun land() {
            calls += "land"
        }

        override fun replaceRoot(root: AppRoute) {
            calls += "root"
        }

        override fun back() {
            calls += "back"
        }
    }

    private fun ctrl(key: Key) = ShellShortcuts.of(key, ctrl = true, meta = false, alt = false, shift = false)

    @Test
    fun `B-NAV-097 Ctrl or Cmd with N, 1, 2, comma, W and slash are the shell's shortcuts`() {
        assertEquals(ShellShortcut.NewConversation, ctrl(Key.N))
        assertEquals(ShellShortcut.Chats, ctrl(Key.One))
        assertEquals(ShellShortcut.Settings, ctrl(Key.Two))
        assertEquals(ShellShortcut.Settings, ctrl(Key.Comma))
        assertEquals(ShellShortcut.CloseScreen, ctrl(Key.W))
        assertEquals(ShellShortcut.ShortcutList, ctrl(Key.Slash))
        assertEquals(ShellShortcut.NewConversation, ShellShortcuts.of(Key.N, ctrl = false, meta = true, alt = false, shift = false))
    }

    @Test
    fun `B-NAV-098 a plain letter, or Alt or Shift with the modifier, is never a shell shortcut`() {
        assertNull(ShellShortcuts.of(Key.N, ctrl = false, meta = false, alt = false, shift = false))
        assertNull(ShellShortcuts.of(Key.N, ctrl = true, meta = false, alt = true, shift = false))
        assertNull(ShellShortcuts.of(Key.N, ctrl = true, meta = false, alt = false, shift = true))
        assertNull(ctrl(Key.K))
    }

    @Test
    fun `B-NAV-099 the shortcuts open their places and Settings opens over Conversations`() {
        val nav = Recorder()
        ShellShortcuts.perform(ShellShortcut.NewConversation, nav, ChatsRoute.Home)
        assertIs<ChatsRoute.New>(nav.opened.last())
        ShellShortcuts.perform(ShellShortcut.ShortcutList, nav, ChatsRoute.Home)
        assertEquals(SettingsRoute.KeyboardShortcuts, nav.opened.last())
        nav.calls.clear()
        ShellShortcuts.perform(ShellShortcut.Settings, nav, ChatsRoute.All())
        assertEquals(listOf("land", "open"), nav.calls)
        assertEquals(SettingsRoute.Home, nav.opened.last())
        nav.calls.clear()
        ShellShortcuts.perform(ShellShortcut.Settings, nav, SettingsRoute.Home)
        ShellShortcuts.perform(ShellShortcut.Chats, nav, ChatsRoute.Home)
        assertEquals(emptyList(), nav.calls)
        ShellShortcuts.perform(ShellShortcut.CloseScreen, nav, SettingsRoute.Home)
        assertEquals(listOf("back"), nav.calls)
    }

    @Test
    fun `B-NAV-100 the rail exists from medium only, beside the two destinations`() {
        assertFalse(ShellRailRules.shows(WindowClass.Compact))
        assertTrue(ShellRailRules.shows(WindowClass.Medium))
        assertTrue(ShellRailRules.shows(WindowClass.Large))
        assertEquals(ShellDestination.Chats, ShellRailRules.destinationOf(ChatsRoute.Home))
        assertEquals(ShellDestination.Settings, ShellRailRules.destinationOf(SettingsRoute.Home))
        assertNull(ShellRailRules.destinationOf(ChatsRoute.All()))
    }
}
