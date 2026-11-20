package io.github.eslamasabry.oc.ui.shell.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.navigation3.runtime.NavKey
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitScreen
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import io.github.eslamasabry.oc.ui.routes.AgentsRoute
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.routes.LocalOpenBeside
import io.github.eslamasabry.oc.ui.routes.ServersRoute
import io.github.eslamasabry.oc.ui.routes.SettingsRoute
import io.github.eslamasabry.oc.ui.routes.TerminalRoute
import io.github.eslamasabry.oc.ui.shell.NavShell
import io.github.eslamasabry.oc.ui.shell.ShellDependencies
import io.github.eslamasabry.oc.ui.shell.ShellNavigator
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Big screens, the shared frame (BS-SHARED-FRAME): from 840 dp wide (and not short) every page reached from Settings
 * opens in the detail pane beside the Settings list; Back goes back inside the pane; under 840 dp the phone's push is
 * unchanged. Run on the real NavShell with a Settings hub and two pages made of kit parts.
 */
@OptIn(ExperimentalTestApi::class)
class ShellSettingsPaneTest {
    private var locale: Locale = Locale.getDefault()

    @BeforeTest
    fun english() {
        locale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest fun restore() = Locale.setDefault(locale)

    private class Settings {
        val navigator = ShellNavigator()
        val input = FakeBackInput()
        private val dispatcher = NavigationEventDispatcher().also { it.addInput(input) }
        private val owner =
            object : NavigationEventDispatcherOwner {
                override val navigationEventDispatcher: NavigationEventDispatcher = dispatcher
            }
        var hubWidth = -1f
        var pageWidth = -1f

        /** What the hub saw as the page open beside it, last composition. */
        var openSeen: NavKey? = null

        private val installer = EntryInstaller {
            entry<SettingsRoute.Home> {
                hubWidth = LocalKitWindow.current.widthDp
                openSeen = LocalOpenBeside.current
                KitScreen(title = "Settings") {
                    KitText(HUB)
                    KitButton("Open Servers", onClick = { navigator.open(ServersRoute.Home) }, modifier = Modifier.testTag("servers"))
                    KitButton("Open Agents", onClick = { navigator.open(AgentsRoute.Home) }, modifier = Modifier.testTag("agents"))
                    KitButton("Open Terminal", onClick = { navigator.open(TERMINAL_ROUTE) }, modifier = Modifier.testTag("terminal"))
                }
            }
            entry<ServersRoute.Home> {
                pageWidth = LocalKitWindow.current.widthDp
                KitScreen(title = "Servers", backLabel = "Back", onBack = navigator::back) { KitText(SERVERS) }
            }
            entry<AgentsRoute.Home> {
                KitScreen(title = "Agents", backLabel = "Back", onBack = navigator::back) { KitText(AGENTS) }
            }
            entry<TerminalRoute.Home> {
                KitScreen(title = "Terminal", backLabel = "Back", onBack = navigator::back) { KitText(TERMINAL) }
            }
        }
        val deps = ShellDependencies(navigator, setOf(installer), FakeSystemMotion(1f))

        @Composable
        fun Content() {
            val platform = remember { DesktopKitPlatform() }
            CompositionLocalProvider(
                LocalKitPlatform provides platform,
                LocalNavigationEventDispatcherOwner provides owner,
                LocalDensity provides Density(1f),
            ) {
                KitTheme(appearance = KitAppearance(mode = AppearanceMode.Light), platform = platform, localeTag = "en") {
                    NavShell(start = SettingsRoute.Home, deps = deps)
                }
            }
        }

        fun back() {
            input.start(NavigationEvent.EDGE_LEFT)
            input.complete()
        }
    }

    private fun at(w: Int, h: Int, block: ComposeUiTest.(Settings) -> Unit) =
        runDesktopComposeUiTest(w, h) {
            val p = Settings()
            setContent { p.Content() }
            waitForIdle()
            block(p)
        }

    private fun ComposeUiTest.tap(tag: String) {
        onNodeWithTag(tag).performClick()
        waitForIdle()
    }

    @Test
    fun `B-NAV-240 a page opened from Settings shows beside the Settings list at 840 and wider`() {
        for ((w, h) in listOf(840 to 800, 1024 to 768, 1280 to 800, 900 to 1200)) at(w, h) { p ->
            tap("servers")
            onNodeWithText(SERVERS).assertIsDisplayed()
            onNodeWithText(HUB).assertIsDisplayed()
            assertEquals(listOf(SettingsRoute.Home, ServersRoute.Home), p.navigator.snapshot(), "$w x $h")
            assertEquals(ServersRoute.Home, p.openSeen, "$w x $h: the list knows the open page")
            assertEquals(true, p.hubWidth < 600f, "$w x $h: the list is its own narrow window (${p.hubWidth})")
            assertEquals(true, p.pageWidth + p.hubWidth <= w + 1f, "$w x $h: the page has the rest")
        }
        for ((w, h) in listOf(360 to 780, 412 to 800, 600 to 800, 839 to 800, 1280 to 400)) at(w, h) { p ->
            tap("servers")
            onNodeWithText(SERVERS).assertIsDisplayed()
            onNodeWithText(HUB).assertDoesNotExist()
            assertEquals(listOf(SettingsRoute.Home, ServersRoute.Home), p.navigator.snapshot(), "$w x $h")
        }
    }

    @Test
    fun `B-NAV-241 Back inside the pane returns to the list, a second page takes the first one's place`() =
        at(1280, 800) { p ->
            tap("servers")
            tap("agents")
            onNodeWithText(AGENTS).assertIsDisplayed()
            onNodeWithText(SERVERS).assertDoesNotExist()
            assertEquals(listOf(SettingsRoute.Home, AgentsRoute.Home), p.navigator.snapshot(), "the stack does not grow per tap")
            runOnIdle { p.back() }
            waitForIdle()
            assertEquals(listOf(SettingsRoute.Home), p.navigator.snapshot())
            onNodeWithText(HUB).assertIsDisplayed()
            onNodeWithText(AGENTS).assertDoesNotExist()
        }

    @Test
    fun `B-NAV-242 the phone pushes over the list and Back walks the pages in order`() =
        at(412, 800) { p ->
            tap("servers")
            runOnIdle { p.navigator.open(AgentsRoute.Home) }
            waitForIdle()
            assertEquals(listOf(SettingsRoute.Home, ServersRoute.Home, AgentsRoute.Home), p.navigator.snapshot())
            runOnIdle { p.back() }
            waitForIdle()
            onNodeWithText(SERVERS).assertIsDisplayed()
            runOnIdle { p.back() }
            waitForIdle()
            onNodeWithText(HUB).assertIsDisplayed()
        }

    @Test
    fun `B-NAV-243 the terminal keeps the whole window`() =
        at(1280, 800) { p ->
            tap("terminal")
            onNodeWithText(TERMINAL).assertIsDisplayed()
            onNodeWithText(HUB).assertDoesNotExist()
            assertEquals(listOf(SettingsRoute.Home, TERMINAL_ROUTE), p.navigator.snapshot())
        }

    private companion object {
        val TERMINAL_ROUTE = TerminalRoute.Home(ProfileId("t"), "/")
        const val HUB = "Settings hub rows"
        const val SERVERS = "Servers page body"
        const val AGENTS = "Agents page body"
        const val TERMINAL = "Terminal page body"
    }
}
