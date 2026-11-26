package io.github.eslamasabry.oc.ui.shell.nav

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitRow
import io.github.eslamasabry.oc.ui.kit.KitScreen
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.shell.NavShell
import io.github.eslamasabry.oc.ui.shell.ShellDependencies
import io.github.eslamasabry.oc.ui.shell.ShellNavigator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-379: closing New conversation and tapping Home's "+" while New is still sliding away must deliver the tap to "+"
 * beneath it rather than letting the leaving page swallow it or dropping it onto a row.
 */
@OptIn(ExperimentalTestApi::class)
class ShellFastPlusAfterCloseTest {
    private val rowTaps = mutableListOf<String>()
    private val leavingTaps = mutableListOf<String>()

    private class H {
        val navigator = ShellNavigator()
        val gate = RowTransformGate()
        private val dispatcher = NavigationEventDispatcher().also { it.addInput(FakeBackInput()) }
        private val owner =
            object : NavigationEventDispatcherOwner {
                override val navigationEventDispatcher: NavigationEventDispatcher = dispatcher
            }
        lateinit var installer: EntryInstaller
        val deps
            get() = ShellDependencies(navigator, setOf(installer), FakeSystemMotion(1f))

        @Composable
        fun Content() {
            val platform = DesktopKitPlatform()
            CompositionLocalProvider(
                LocalKitPlatform provides platform,
                LocalNavigationEventDispatcherOwner provides owner,
            ) {
                KitTheme(
                    appearance = KitAppearance(mode = AppearanceMode.Light, motion = MotionLevel.Full),
                    platform = platform,
                    localeTag = "en",
                ) {
                    NavShell(start = ChatsRoute.Home, deps = deps, gate = gate)
                }
            }
        }
    }

    private fun installerFor(h: H): EntryInstaller = EntryInstaller {
        entry<ChatsRoute.Home> {
            KitScreen(title = "Home") {
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items((0 until 30).toList(), key = { it }) { i ->
                            KitRow("Row $i", "preview", onClick = { rowTaps += "Row $i" })
                        }
                    }
                    KitButton(
                        "+",
                        onClick = { h.navigator.open(ChatsRoute.New(profile = null)) },
                        modifier = Modifier.align(Alignment.BottomEnd),
                    )
                }
            }
        }
        entry<ChatsRoute.New> {
            KitScreen(title = "New") {
                KitButton("Close", onClick = { h.navigator.back() })
                Box(
                    Modifier.fillMaxWidth()
                        .weight(1f)
                        .testTag("new-body")
                        .clickable { leavingTaps += "body" },
                )
            }
        }
    }

    private fun after(delayMs: Long, expectReopen: Boolean = true) =
        runDesktopComposeUiTest(360, 780) {
            rowTaps.clear()
            leavingTaps.clear()
            val h = H().also { it.installer = installerFor(it) }
            setContent { h.Content() }
            waitForIdle()
            val spot = onNodeWithText("+").fetchSemanticsNode().boundsInRoot.center
            onNodeWithText("+").performClick()
            mainClock.advanceTimeBy(1_500)
            waitForIdle()
            assertTrue(
                onNodeWithTag("new-body").fetchSemanticsNode().boundsInRoot.contains(spot),
                "precondition: + spot $spot must lie inside new-body",
            )
            rowTaps.clear()
            leavingTaps.clear()
            onNodeWithText("Close").performClick()
            mainClock.autoAdvance = false
            mainClock.advanceTimeBy(delayMs)
            onRoot().performTouchInput { click(spot) }
            mainClock.advanceTimeBy(1_500)
            mainClock.autoAdvance = true
            waitForIdle()
            assertEquals(emptyList(), rowTaps, "a row took the tap meant for +, $delayMs ms after Close")
            assertEquals(
                emptyList(),
                leavingTaps,
                "the leaving New page took the tap meant for +, $delayMs ms after Close",
            )
            if (expectReopen) {
                assertEquals(2, h.navigator.snapshot().size, "back stack did not reopen New, $delayMs ms after Close")
                assertTrue(
                    h.navigator.snapshot().last() is ChatsRoute.New,
                    "top route is not New, $delayMs ms after Close",
                )
            }
        }

    @Test
    fun `B-379 a tap on plus 16 ms after closing New conversation is not taken by the leaving page`() =
        after(16, expectReopen = false)

    @Test
    fun `B-379 a tap on plus 34 ms after closing New conversation opens it again`() = after(34)

    @Test
    fun `B-379 a tap on plus 50 ms after closing New conversation opens it again`() = after(50)

    @Test
    fun `B-379 a tap on plus 100 ms after closing New conversation opens it again`() = after(100)
}
