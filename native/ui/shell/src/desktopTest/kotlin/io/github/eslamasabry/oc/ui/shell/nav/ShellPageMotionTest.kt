package io.github.eslamasabry.oc.ui.shell.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitScreen
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.shell.NavShell
import io.github.eslamasabry.oc.ui.shell.ShellDependencies
import io.github.eslamasabry.oc.ui.shell.ShellNavigator
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * M-001 / M-012 / M-013 on the real NavShell with the test clock: a pushed page (SharedAxisX, the default for every
 * route) moves like Flutter's fade-forwards (450 ms, a quarter of the width on the emphasized curve), Calm cross-fades
 * without travel, Off is instant, and the predictive preview scales only at Full.
 */
@OptIn(ExperimentalTestApi::class)
class ShellPageMotionTest {
    private class PageHarness(scale: Float = 1f, private val level: MotionLevel = MotionLevel.Full) {
        val navigator = ShellNavigator()
        val gate = RowTransformGate()
        val input = FakeBackInput()
        private val dispatcher = NavigationEventDispatcher().also { it.addInput(input) }
        private val owner = object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher: NavigationEventDispatcher = dispatcher
        }
        private val installer = EntryInstaller {
            entry<ChatsRoute.Home> {
                KitScreen(title = "Home") { KitButton(OPEN, onClick = { navigator.open(ChatsRoute.All()) }) }
            }
            entry<ChatsRoute.All> { KitScreen(title = "All") { KitText(PAGE) } }
        }
        val deps = ShellDependencies(navigator, setOf(installer), FakeSystemMotion(scale))

        @Composable
        fun Content() {
            val platform = DesktopKitPlatform()
            CompositionLocalProvider(
                LocalKitPlatform provides platform,
                LocalNavigationEventDispatcherOwner provides owner,
            ) {
                KitTheme(
                    appearance = KitAppearance(mode = AppearanceMode.Light, motion = level),
                    platform = platform,
                    localeTag = "en",
                ) { NavShell(start = ChatsRoute.Home, deps = deps, gate = gate) }
            }
        }
    }

    private fun ComposeUiTest.x(text: String): Float = onNodeWithText(text).fetchSemanticsNode().positionInRoot.x

    private fun ComposeUiTest.width(text: String): Float = onNodeWithText(text).fetchSemanticsNode().boundsInRoot.width

    private fun ComposeUiTest.shown(text: String): Boolean =
        onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    /** Opens the page, lets it settle, returns the page text's resting x; then goes back and settles again. */
    private fun ComposeUiTest.restingX(h: PageHarness): Float {
        onNodeWithText(OPEN).performClick()
        waitForIdle()
        val x = x(PAGE)
        runOnIdle { h.navigator.back() }
        waitForIdle()
        return x
    }

    @Test
    fun `M-012 a pushed page slides a quarter of the width over 450 ms like Flutter`() =
        runDesktopComposeUiTest(W, H) {
            val h = PageHarness()
            setContent { h.Content() }
            waitForIdle()
            val rest = restingX(h)
            val travel = ShellMotion.pageTravelPx(onRoot().fetchSemanticsNode().size.width)
            mainClock.autoAdvance = false
            onNodeWithText(OPEN).performClick()
            mainClock.advanceTimeBy(60)
            // ~t 0.1 on easeInOutCubicEmphasized (0.09): most of the quarter-width still to go. The old 30 dp shared
            // axis had under 15 px left here.
            val early = x(PAGE) - rest
            assertTrue(early > travel * 0.6f && early <= travel + 1f, "early offset $early of $travel")
            mainClock.advanceTimeBy(340) // 400 ms: still running (the old spec ended at 250 ms)
            assertTrue(shown(OPEN), "the covered page is still in its 450 ms transition at 400 ms")
            mainClock.advanceTimeBy(200) // the transition starts a few frames after the click; well past its end
            assertTrue(abs(x(PAGE) - rest) < 1f, "settled in place")
            assertTrue(!shown(OPEN), "the covered page is gone once the transition ends")
        }

    @Test
    fun `M-012 back slides the page out toward the end and the page underneath returns`() =
        runDesktopComposeUiTest(W, H) {
            val h = PageHarness()
            setContent { h.Content() }
            waitForIdle()
            onNodeWithText(OPEN).performClick()
            waitForIdle()
            val rest = x(PAGE)
            mainClock.autoAdvance = false
            runOnIdle { h.navigator.back() }
            mainClock.advanceTimeBy(150)
            assertTrue(shown(OPEN), "the page underneath is back at once (fading in)")
            // ~t 0.2 on the emphasized curve: well over half of the quarter width; the old spec moved 30 dp at most.
            val moved = x(PAGE) - rest
            assertTrue(moved > 40f, "the leaving page travels toward the end: $moved px")
            mainClock.advanceTimeBy(500)
            assertTrue(!shown(PAGE), "done after 450 ms")
        }

    @Test
    fun `M-013 Calm cross-fades pages, nothing travels, and the switch is not instant`() =
        runDesktopComposeUiTest(W, H) {
            val h = PageHarness(level = MotionLevel.Calm)
            setContent { h.Content() }
            waitForIdle()
            val rest = restingX(h)
            mainClock.autoAdvance = false
            onNodeWithText(OPEN).performClick()
            mainClock.advanceTimeBy(60)
            assertTrue(shown(OPEN), "Calm still fades: the covered page has not vanished in one frame")
            assertTrue(abs(x(PAGE) - rest) < 1f, "Calm: fade only, no travel")
            mainClock.advanceTimeBy(600)
            assertTrue(!shown(OPEN), "the fade ends with the 450 ms page transition")
        }

    @Test
    fun `M-013 Off is instant`() =
        runDesktopComposeUiTest(W, H) {
            val h = PageHarness(level = MotionLevel.Off)
            setContent { h.Content() }
            waitForIdle()
            mainClock.autoAdvance = false
            onNodeWithText(OPEN).performClick()
            mainClock.advanceTimeBy(64) // a few frames: far less than any fade (113 ms is the shortest)
            assertTrue(shown(PAGE))
            assertTrue(!shown(OPEN), "Animations: Off swaps pages with no transition")
        }

    @Test
    fun `M-013 system Remove animations is instant even when the app says Full`() =
        runDesktopComposeUiTest(W, H) {
            val h = PageHarness(scale = 0f)
            setContent { h.Content() }
            waitForIdle()
            mainClock.autoAdvance = false
            onNodeWithText(OPEN).performClick()
            mainClock.advanceTimeBy(64) // a few frames: far less than any fade (113 ms is the shortest)
            assertTrue(!shown(OPEN))
        }

    private fun previewScale(level: MotionLevel): Float {
        var ratio = 0f
        runDesktopComposeUiTest(W, H) {
            val h = PageHarness(level = level)
            setContent { h.Content() }
            onNodeWithText(OPEN).performClick()
            waitForIdle()
            val full = width(PAGE)
            runOnIdle {
                h.input.start(NavigationEvent.EDGE_LEFT)
                h.input.progress(NavigationEvent.EDGE_LEFT, 0.5f)
            }
            waitForIdle()
            ratio = width(PAGE) / full
            runOnIdle { h.input.cancel() }
            waitForIdle()
            assertTrue(shown(PAGE), "a cancelled swipe stays on the page")
        }
        return ratio
    }

    @Test
    fun `M-001 the preview follows the finger at Full and only fades at Calm`() {
        // 1 - 0.1 × PredictiveBack(0.5) = 1 - 0.1 × 0.895
        val expected = 1f - (1f - ShellMotion.PREDICTIVE_MIN_SCALE) * ShellMotion.PredictiveBack.transform(0.5f)
        val full = previewScale(MotionLevel.Full)
        assertTrue(abs(full - expected) < 0.02f, "Full preview scale $full, expected $expected")
        assertEquals(1f, previewScale(MotionLevel.Calm), 0.01f, "Calm: no scale")
    }

    private companion object {
        const val W = 412
        const val H = 860
        const val OPEN = "Open all"
        const val PAGE = "Everything in one list"
    }
}
