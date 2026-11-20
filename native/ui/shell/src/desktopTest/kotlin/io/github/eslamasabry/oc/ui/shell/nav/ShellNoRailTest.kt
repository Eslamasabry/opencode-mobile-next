package io.github.eslamasabry.oc.ui.shell.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.KitWindowInfo
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.routes.SettingsRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** BS-RAIL-OFF: no rail at any window size, and the keyboard still reaches the places the rail used to name. */
@OptIn(ExperimentalTestApi::class)
class ShellNoRailTest {
    @Test
    fun `B-NAV-200 no rail at 600, 840 and 1280 dp`() = runComposeUiTest {
        var width by mutableStateOf(600f)
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                CompositionLocalProvider(LocalKitWindow provides KitWindowInfo(width, 800f)) {
                    Box(Modifier.fillMaxSize()) {
                        ShellRailFrame(top = ChatsRoute.Home, onSelect = {}) {
                            Box(Modifier.fillMaxSize().testTag("page"))
                        }
                    }
                }
            }
        }
        val root = onNodeWithTag("page").getUnclippedBoundsInRoot()
        for (w in listOf(600f, 840f, 1280f)) {
            width = w
            waitForIdle()
            onNodeWithTag("shell-rail").assertDoesNotExist()
            val b = onNodeWithTag("page").getUnclippedBoundsInRoot()
            assertEquals(0.dp, b.left, "page starts at the window's edge at $w")
            assertEquals(root.right - root.left, b.right - b.left, "page fills the window at $w")
        }
    }

    private class Recorder : AppNavigator {
        val calls = mutableListOf<String>()
        val opened = mutableListOf<AppRoute>()
        override val top: StateFlow<AppRoute?> = MutableStateFlow(null)
        override fun open(route: AppRoute) { opened += route; calls += "open" }
        override fun openChat(ref: ConversationRef, focusComposer: Boolean) { calls += "chat" }
        override fun land() { calls += "land" }
        override fun replaceRoot(root: AppRoute) { calls += "root" }
        override fun back() { calls += "back" }
    }

    private fun ctrl(key: Key) = ShellShortcuts.of(key, ctrl = true, meta = false, alt = false, shift = false)

    @Test
    fun `B-NAV-201 shortcuts work without the rail`() = runComposeUiTest {
        // At 1280 dp the rail is gone; the shortcuts alone are the way to the places it named.
        var shown = true
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                CompositionLocalProvider(LocalKitWindow provides KitWindowInfo(1280f, 800f)) {
                    shown = ShellRailRules.shows(LocalKitWindow.current.cls)
                }
            }
        }
        waitForIdle()
        assertEquals(false, shown)
        val nav = Recorder()
        ShellShortcuts.perform(checkNotNull(ctrl(Key.Two)), nav, ChatsRoute.Home)
        assertEquals(SettingsRoute.Home, nav.opened.last())
        nav.calls.clear()
        ShellShortcuts.perform(checkNotNull(ctrl(Key.Comma)), nav, ChatsRoute.All())
        assertEquals(listOf("land", "open"), nav.calls)
        assertEquals(SettingsRoute.Home, nav.opened.last())
        nav.calls.clear()
        ShellShortcuts.perform(checkNotNull(ctrl(Key.One)), nav, SettingsRoute.Home)
        assertEquals(listOf("land"), nav.calls)
    }
}
