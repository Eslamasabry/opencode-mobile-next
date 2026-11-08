package io.github.eslamasabry.oc.ui.shell.nav

import io.github.eslamasabry.oc.ui.kit.KitText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.KitWindowInfo
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.routes.SettingsRoute
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** s28-app story 8: the rail on a wider window; a phone keeps its layout. */
@OptIn(ExperimentalTestApi::class)
class ShellRailTest {
    private var before: Locale = Locale.getDefault()

    @BeforeTest
    fun english() {
        before = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun restore() = Locale.setDefault(before)

    private fun androidx.compose.ui.test.ComposeUiTest.frame(
        window: KitWindowInfo,
        top: AppRoute,
        picked: MutableList<ShellDestination>,
    ) = setContent {
        KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
            CompositionLocalProvider(LocalKitWindow provides window) {
                ShellRailFrame(top = top, onSelect = { picked += it }) { KitText("page") }
            }
        }
    }

    @Test
    fun `B-NAV-100 a tablet window shows Conversations and Settings in a rail`() = runComposeUiTest {
        val picked = mutableListOf<ShellDestination>()
        frame(KitWindowInfo(1000f, 800f), ChatsRoute.Home, picked)
        onNodeWithTag("shell-rail").assertExists()
        onNodeWithText("page").assertExists()
        onNodeWithText("Settings").performClick()
        assertEquals(listOf(ShellDestination.Settings), picked)
    }

    @Test
    fun `B-NAV-101 a phone window has no rail`() = runComposeUiTest {
        frame(KitWindowInfo.Phone, ChatsRoute.Home, mutableListOf())
        onNodeWithTag("shell-rail").assertDoesNotExist()
        onNodeWithText("page").assertExists()
    }

    @Test
    fun `B-NAV-102 a page over a destination covers the rail`() = runComposeUiTest {
        frame(KitWindowInfo(1000f, 800f), SettingsRoute.KeyboardShortcuts, mutableListOf())
        onNodeWithTag("shell-rail").assertDoesNotExist()
    }
}
