package io.github.eslamasabry.oc.ui.kit.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The finger-linked tabs: the strip and its pages move as one thing, by swipe, tap and keyboard. */
@OptIn(ExperimentalTestApi::class)
class KitSwipeTabsTest {
    private val six = listOf("Favorites", "Z.AI", "OpenCode", "OpenAI", "Anthropic", "Google")

    private class Scene {
        var index by mutableStateOf(0)
        val picked = mutableListOf<Int>()
        var state: KitTabsState? = null
        val lists = HashMap<Int, LazyListState>()
        val platform = FakeKitPlatform()
    }

    @Composable
    private fun Tabs(scene: Scene, level: MotionLevel, names: List<String> = six, rtl: Boolean = false) {
        CompositionLocalProvider(
            LocalDensity provides Density(1f, 1f),
            LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
        ) {
            KitTheme(KitAppearance(motion = level), scene.platform, if (rtl) "ar" else "en", stillFrame = true) {
                val state =
                    rememberKitTabsState(scene.index, names.size) {
                        scene.picked += it
                        scene.index = it
                    }
                scene.state = state
                Column(Modifier.size(400.dp, 600.dp)) {
                    KitTabStrip(names.map { KitTab(it) }, state, semanticsLabel = "Providers")
                    KitTabPages(state, Modifier.fillMaxSize().testTag("pages"), pageTitle = { names[it] }) { page ->
                        val list = rememberLazyListState()
                        scene.lists[page] = list
                        LazyColumn(Modifier.fillMaxSize().testTag("list-$page"), state = list) {
                            items(40) { row -> Box(Modifier.height(56.dp)) { KitText("${names[page]} row $row") } }
                        }
                    }
                }
            }
        }
    }

    private fun ComposeUiTest.settle() {
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
    }

    @Test
    fun `a swipe turns the page, moves the chosen tab and reports it once`() = runComposeUiTest {
        val scene = Scene()
        setContent { Tabs(scene, MotionLevel.Full) }
        onNodeWithTag("pages").performTouchInput { swipeLeft() }
        settle()
        assertEquals(listOf(1), scene.picked, "one report for one settled change")
        assertEquals(1f, scene.state!!.position)
        onNode(hasContentDescription("Z.AI")).assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        onNode(hasContentDescription("Favorites")).assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
        onNodeWithText("Z.AI row 0").assertExists()
        assertEquals(listOf(KitHaptic.Select), scene.platform.haptics, "one tick for the change, none per frame")
        onNodeWithTag("pages").performTouchInput { swipeRight() }
        settle()
        assertEquals(listOf(1, 0), scene.picked)
    }

    @Test
    fun `a swipe that does not leave the page reports nothing`() = runComposeUiTest {
        val scene = Scene()
        setContent { Tabs(scene, MotionLevel.Full) }
        onNodeWithTag("pages").performTouchInput { swipeRight() } // already on the first page
        settle()
        assertTrue(scene.picked.isEmpty())
        assertTrue(scene.platform.haptics.isEmpty())
        assertEquals(0f, scene.state!!.position)
    }

    @Test
    fun `mid-drag the pill is where the pages are and nothing in the strip is composed again`() = runComposeUiTest {
        val scene = Scene()
        setContent { Tabs(scene, MotionLevel.Full) }
        waitForIdle()
        mainClock.autoAdvance = false
        val before = TabStripProbe.compositions
        val places = mutableListOf<Float>()
        onNodeWithTag("pages").performTouchInput {
            down(Offset(350f, 300f))
            moveBy(Offset(-40f, 0f)) // past the touch slop
        }
        repeat(8) {
            onNodeWithTag("pages").performTouchInput { moveBy(Offset(-20f, 0f)) }
            mainClock.advanceTimeByFrame()
            places += scene.state!!.position
        }
        assertTrue(places.last() > 0.3f && places.last() < 0.6f, "the pill is part of the way to the next tab: ${places.last()}")
        assertTrue(places.zipWithNext().all { (a, b) -> b > a }, "and moves with every frame of the drag: $places")
        assertEquals(before, TabStripProbe.compositions, "the strip was not composed again while the finger moved")
        assertTrue(scene.picked.isEmpty(), "nothing is chosen until the pages come to rest")
        onNodeWithTag("pages").performTouchInput { up() }
        mainClock.autoAdvance = true
        settle()
    }

    @Test
    fun `a tap on a far tab ends on it, with one report and the pill there`() = runComposeUiTest {
        val scene = Scene()
        setContent { Tabs(scene, MotionLevel.Full) }
        // The sixth tab starts outside a 400 dp strip: bring it in as a finger would, then tap it.
        onNode(hasContentDescription("Google")).performScrollTo()
        onNode(hasContentDescription("Google")).performClick()
        settle()
        assertEquals(listOf(5), scene.picked)
        assertEquals(5f, scene.state!!.position)
        assertEquals(5, scene.state!!.settled)
        onNodeWithText("Google row 0").assertExists()
        onNode(hasContentDescription("Google")).assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        onNode(hasContentDescription("Google")).performClick()
        settle()
        assertEquals(listOf(5), scene.picked, "the chosen tab does nothing")
    }

    @Test
    fun `a far tap moves the pill across the tabs between while one page slides`() = runComposeUiTest {
        val scene = Scene()
        setContent { Tabs(scene, MotionLevel.Full) }
        waitForIdle()
        mainClock.autoAdvance = false
        onNode(hasContentDescription("Anthropic")).performClick()
        val places = mutableListOf<Float>()
        repeat(60) {
            mainClock.advanceTimeByFrame()
            places += scene.state!!.position
        }
        assertEquals(4f, places.last(), "it arrives")
        assertTrue(places.any { it > 0.5f && it < 1.5f } && places.any { it > 1.5f && it < 3.5f }, "it passed over the tabs between: $places")
        assertTrue(places.zipWithNext().all { (a, b) -> b >= a - 0.001f }, "without going back: $places")
        val arrived = places.indexOfFirst { abs(it - 4f) < 0.01f }
        assertTrue(arrived * 16 < 700, "a far tab takes no longer than a near one: ${arrived * 16} ms")
        mainClock.autoAdvance = true
    }

    @Test
    fun `with motion Off a tap shows the tab at once and a drag still turns the page`() = runComposeUiTest {
        val scene = Scene()
        setContent { Tabs(scene, MotionLevel.Off) }
        waitForIdle()
        mainClock.autoAdvance = false
        onNode(hasContentDescription("OpenAI")).performClick()
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        assertEquals(3f, scene.state!!.position, "no slide")
        mainClock.autoAdvance = true
        onNodeWithTag("pages").performTouchInput { swipeLeft() }
        settle()
        assertEquals(listOf(3, 4), scene.picked)
    }

    @Test
    fun `with motion Calm a tap still slides and the pill keeps the pages' own pace`() = runComposeUiTest {
        val scene = Scene()
        setContent { Tabs(scene, MotionLevel.Calm) }
        onNode(hasContentDescription("Z.AI")).performClick()
        settle()
        assertEquals(1f, scene.state!!.position)
        assertEquals(listOf(1), scene.picked)
    }

    @Test
    fun `a page keeps its place in its list after leaving and coming back`() = runComposeUiTest {
        val scene = Scene()
        setContent { Tabs(scene, MotionLevel.Full) }
        onNodeWithTag("list-0").performTouchInput { swipeUp() }
        settle()
        val kept = scene.lists.getValue(0).firstVisibleItemIndex
        assertTrue(kept > 0, "the list scrolled")
        onNode(hasContentDescription("OpenAI")).performClick()
        settle()
        onNodeWithTag("list-0").assertDoesNotExist()
        onNode(hasContentDescription("Favorites")).performClick()
        settle()
        assertEquals(kept, scene.lists.getValue(0).firstVisibleItemIndex, "it is where it was left")
    }

    @Test
    fun `an up and down drag inside a page scrolls its list and does not turn the page`() = runComposeUiTest {
        val scene = Scene()
        setContent { Tabs(scene, MotionLevel.Full) }
        onNodeWithTag("list-0").performTouchInput { swipeUp() }
        settle()
        assertTrue(scene.lists.getValue(0).firstVisibleItemIndex > 0)
        assertEquals(0f, scene.state!!.position)
        assertTrue(scene.picked.isEmpty())
    }

    @Test
    fun `arrows move focus along the strip and Enter chooses`() = runComposeUiTest {
        val scene = Scene()
        setContent { Tabs(scene, MotionLevel.Off) }
        onRoot().performKeyInput {
            pressKey(Key.Tab)
            pressKey(Key.DirectionRight)
            pressKey(Key.DirectionRight)
        }
        waitForIdle()
        assertTrue(scene.picked.isEmpty(), "arrows never choose")
        onRoot().performKeyInput { pressKey(Key.Enter) }
        settle()
        assertEquals(listOf(2), scene.picked)
        onNodeWithText("OpenCode row 0").assertExists()
    }

    @Test
    fun `right to left a swipe to the right turns to the next page and the pill moves left`() = runComposeUiTest {
        val scene = Scene()
        setContent { Tabs(scene, MotionLevel.Full, rtl = true) }
        waitForIdle()
        val first = onNode(hasContentDescription("Favorites")).fetchSemanticsNode().boundsInRoot
        val second = onNode(hasContentDescription("Z.AI")).fetchSemanticsNode().boundsInRoot
        assertTrue(second.right < first.left, "the second tab is to the left of the first")
        onNodeWithTag("pages").performTouchInput { swipeRight() }
        settle()
        assertEquals(listOf(1), scene.picked)
        assertEquals(1f, scene.state!!.position)
    }

    @Test
    fun `a strip wider than the window scrolls with the pill`() = runComposeUiTest {
        val scene = Scene()
        setContent { Tabs(scene, MotionLevel.Full) }
        waitForIdle()
        val before = onNode(hasContentDescription("Favorites")).fetchSemanticsNode().boundsInRoot.left
        onNode(hasContentDescription("OpenAI")).performClick()
        settle()
        val after = onNode(hasContentDescription("Favorites")).fetchSemanticsNode().boundsInRoot.left
        assertTrue(after < before, "the strip moved under the pill (first tab at $before, then $after)")
        val chosen = onNode(hasContentDescription("OpenAI")).fetchSemanticsNode().boundsInRoot
        assertTrue(chosen.left >= 0f && chosen.right <= 400f, "the chosen tab is in view: $chosen")
    }

    @Test
    fun `the plain strip without pages still chooses on tap and keeps its pill on the chosen tab`() = runComposeUiTest {
        var index by mutableStateOf(0)
        val picked = mutableListOf<Int>()
        setContent {
            KitTheme(KitAppearance(motion = MotionLevel.Full), FakeKitPlatform(), "en", stillFrame = true) {
                KitTabStrip(listOf(KitTab("One"), KitTab("Two"), KitTab("Three")), index, { picked += it; index = it })
            }
        }
        onNode(hasContentDescription("Three")).performClick()
        settle()
        assertEquals(listOf(2), picked)
        onNode(hasContentDescription("Three")).assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
    }
}
