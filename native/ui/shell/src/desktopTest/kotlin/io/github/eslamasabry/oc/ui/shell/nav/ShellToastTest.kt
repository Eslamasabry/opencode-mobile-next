package io.github.eslamasabry.oc.ui.shell.nav

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitScreen
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.LocalKitToasts
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.shell.NavShell
import io.github.eslamasabry.oc.ui.shell.ShellDependencies
import io.github.eslamasabry.oc.ui.shell.ShellNavigator
import kotlin.test.Test

/** A toast a page shows through the window's slot is on screen (no window provided one before, so none ever showed). */
@OptIn(ExperimentalTestApi::class)
class ShellToastTest {
    @Test
    fun `B-NAV-120 a toast shown from a page appears on screen`() = runComposeUiTest {
        val platform = DesktopKitPlatform()
        val installer = EntryInstaller {
            entry<ChatsRoute.Home> {
                val toasts = LocalKitToasts.current
                KitScreen(title = "Page") { KitButton("Copy it", onClick = { toasts?.show("Copied") }) }
            }
        }
        val deps = ShellDependencies(ShellNavigator(), setOf(installer), FakeSystemMotion())
        setContent {
            CompositionLocalProvider(LocalKitPlatform provides platform) {
                KitTheme(appearance = KitAppearance(mode = AppearanceMode.Light), platform = platform, localeTag = "en") {
                    NavShell(start = ChatsRoute.Home, deps = deps)
                }
            }
        }
        onNodeWithText("Copied").assertDoesNotExist()
        // The toast holds for a moment and leaves; look at it before the clock runs it out.
        mainClock.autoAdvance = false
        onNodeWithText("Copy it").performClick()
        mainClock.advanceTimeBy(300)
        onNodeWithText("Copied").assertExists()
    }
}
