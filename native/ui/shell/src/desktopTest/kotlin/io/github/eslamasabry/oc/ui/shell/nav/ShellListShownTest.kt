package io.github.eslamasabry.oc.ui.shell.nav

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitScreen
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.KitWindowInfo
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.routes.FilesRoute
import io.github.eslamasabry.oc.ui.shell.NavShell
import io.github.eslamasabry.oc.ui.shell.ShellDependencies
import io.github.eslamasabry.oc.ui.shell.ShellNavigator
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * B-NAV-217, the shell's half: the shell says while a list stands beside its page, so the edge light (the phone's way
 * between conversations, `EdgeDock*` in the app) stays hidden while the conversations are in view.
 */
@OptIn(ExperimentalTestApi::class)
class ShellListShownTest {
    @Test
    fun `B-NAV-217 the shell says when the list shows beside the conversation`() = runComposeUiTest {
        val navigator = ShellNavigator()
        val installer = EntryInstaller {
            entry<ChatsRoute.Home> { KitScreen(title = "Home") { KitText("list") } }
            entry<ChatRoute> { KitScreen(title = "Chat") { KitButton(
                "Files",
                onClick = { navigator.open(FilesRoute.Folder(it.ref)) },
            ) } }
            entry<FilesRoute.Folder> { KitScreen(title = "Files") { KitText("files") } }
        }
        val window = mutableStateOf(KitWindowInfo(1280f, 800f))
        val deps = ShellDependencies(navigator, setOf(installer), FakeSystemMotion(1f))
        val platform = DesktopKitPlatform()
        setContent {
            CompositionLocalProvider(LocalKitPlatform provides platform) {
                KitTheme(
                    appearance = KitAppearance(mode = AppearanceMode.Light),
                    platform = platform,
                    localeTag = "en",
                ) {
                    CompositionLocalProvider(LocalKitWindow provides window.value) {
                        Column { NavShell(start = ChatsRoute.Home, deps = deps, gate = RowTransformGate()) }
                    }
                }
            }
        }
        waitForIdle()
        assertEquals(true, navigator.listShown.value, "Home's list at 1280 dp")
        runOnIdle { navigator.openChat(ref(1)) }
        waitForIdle()
        assertEquals(true, navigator.listShown.value, "a conversation beside the list")
        runOnIdle { navigator.open(FilesRoute.Folder(ref(1))) }
        waitForIdle()
        assertEquals(false, navigator.listShown.value, "a conversation's Files page covers the window")
        runOnIdle { navigator.back() }
        waitForIdle()
        assertEquals(true, navigator.listShown.value)
        runOnIdle { window.value = KitWindowInfo(600f, 800f) }
        waitForIdle()
        assertEquals(false, navigator.listShown.value, "a split-screen window shows the conversation alone")
        runOnIdle { window.value = KitWindowInfo(1280f, 400f) }
        waitForIdle()
        assertEquals(false, navigator.listShown.value, "a short window shows the conversation alone")
    }
}
