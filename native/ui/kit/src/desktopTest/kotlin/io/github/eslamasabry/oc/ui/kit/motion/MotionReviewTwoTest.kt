package io.github.eslamasabry.oc.ui.kit.motion

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.test.onNodeWithText
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonItem
import io.github.eslamasabry.oc.ui.kit.row.KitRowPanel
import io.github.eslamasabry.oc.ui.kit.modal.KitFloatingPanel
import io.github.eslamasabry.oc.ui.kit.modal.KitPageOverlay
import io.github.eslamasabry.oc.ui.kit.nav.KitTab
import io.github.eslamasabry.oc.ui.kit.nav.KitTabStrip
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Motion review 2026-11-09, item 10: the page editor, the floating panel's exit and the tab pill, read part-way. */
@OptIn(ExperimentalTestApi::class)
class MotionReviewTwoTest {
    @Composable
    private fun Themed(level: MotionLevel = MotionLevel.Full, rtl: Boolean = false, content: @Composable () -> Unit) {
        KitTheme(KitAppearance(motion = level), FakeKitPlatform(), if (rtl) "ar" else "en") {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                Box(Modifier.fillMaxSize()) { content() }
            }
        }
    }

    @Test
    fun `MOT-PANEL-1 the floating panel fades out before its owner is told`() =
        runDesktopComposeUiTest(412, 860) {
            var dismissed = 0
            setContent { Themed { KitFloatingPanel(onDismiss = { dismissed++ }, dismissLabel = "Cancel") { KitText("Panel body") } } }
            waitForIdle()
            mainClock.autoAdvance = false
            onNodeWithTag("floating-panel-scrim").performClick()
            repeat(2) { mainClock.advanceTimeByFrame() }
            assertEquals(0, dismissed, "mid-exit the owner has not been told yet")
            mainClock.advanceTimeBy(600)
            assertEquals(1, dismissed, "once the exit is done the owner is told, once")
        }

    @Test
    fun `MOT-PANEL-2 reduced motion dismisses the floating panel at once`() =
        runDesktopComposeUiTest(412, 860) {
            var dismissed = 0
            setContent { Themed(MotionLevel.Off) { KitFloatingPanel(onDismiss = { dismissed++ }, dismissLabel = "Cancel") { KitText("Panel body") } } }
            waitForIdle()
            onNodeWithTag("floating-panel-scrim").performClick()
            assertEquals(1, dismissed)
        }

    @Test
    fun `MOT-PAGE-1 the page editor stays up while it fades out`() =
        runDesktopComposeUiTest(412, 860) {
            var open by mutableStateOf(true)
            setContent { Themed { KitPageOverlay(open, "Editor", {}) { KitText("Editor body") } } }
            waitForIdle()
            onNodeWithTag("kit-page-overlay").assertExists()
            mainClock.autoAdvance = false
            open = false
            repeat(2) { mainClock.advanceTimeByFrame() }
            onNodeWithTag("kit-page-overlay").assertExists()
            mainClock.advanceTimeBy(600)
            onNodeWithTag("kit-page-overlay").assertDoesNotExist()
        }

    private fun tabs() = listOf(KitTab("First"), KitTab("Second tab"), KitTab("Third"))

    @Test
    fun `MOT-TAB-1 the tab pill slides to the chosen tab, not jumps`() =
        runDesktopComposeUiTest(412, 860) {
            var sel by mutableStateOf(0)
            setContent { Themed { KitTabStrip(tabs(), sel, { sel = it }) } }
            waitForIdle()
            val from = onNodeWithTag("kit-tab-pill").getBoundsInRoot().left
            sel = 2
            waitForIdle()
            val to = onNodeWithTag("kit-tab-pill").getBoundsInRoot().left
            assertTrue(to > from)
            sel = 0
            waitForIdle()
            mainClock.autoAdvance = false
            sel = 2
            repeat(3) { mainClock.advanceTimeByFrame() }
            val mid = onNodeWithTag("kit-tab-pill").getBoundsInRoot().left
            assertTrue(mid > from && mid < to, "mid-slide the pill is between the tabs ($from < $mid < $to)")
        }

    @Test
    fun `MOT-TAB-2 in Arabic the pill slides toward the start side, which is the right`() =
        runDesktopComposeUiTest(412, 860) {
            var sel by mutableStateOf(0)
            setContent { Themed(rtl = true) { KitTabStrip(tabs(), sel, { sel = it }) } }
            waitForIdle()
            val from = onNodeWithTag("kit-tab-pill").getBoundsInRoot().left
            sel = 2
            waitForIdle()
            val to = onNodeWithTag("kit-tab-pill").getBoundsInRoot().left
            assertTrue(to < from, "the third tab is further to the left in Arabic")
            sel = 0
            waitForIdle()
            mainClock.autoAdvance = false
            sel = 2
            repeat(3) { mainClock.advanceTimeByFrame() }
            val mid = onNodeWithTag("kit-tab-pill").getBoundsInRoot().left
            assertTrue(mid < from && mid > to, "mid-slide the pill is between the tabs ($to < $mid < $from)")
        }

    @Test
    fun `MOT-TAB-3 under Calm the pill jumps`() =
        runDesktopComposeUiTest(412, 860) {
            var sel by mutableStateOf(0)
            setContent { Themed(MotionLevel.Calm) { KitTabStrip(tabs(), sel, { sel = it }) } }
            waitForIdle()
            sel = 2
            waitForIdle()
            val to = onNodeWithTag("kit-tab-pill").getBoundsInRoot().left
            sel = 0
            waitForIdle()
            mainClock.autoAdvance = false
            sel = 2
            repeat(3) { mainClock.advanceTimeByFrame() }
            assertEquals(to, onNodeWithTag("kit-tab-pill").getBoundsInRoot().left, "Calm: the pill is already there")
        }

    @Test
    fun `MOT-LOAD-1 placeholders cross-fade into the rows instead of vanishing`() =
        runDesktopComposeUiTest(412, 860) {
            var loaded by mutableStateOf(false)
            setContent {
                Themed {
                    LazyColumn {
                        if (!loaded) item(key = "opening") { KitSkeletonItem() }
                        else item(key = "rows") { Box(kitItemMotion()) { KitText("Real rows") } }
                    }
                }
            }
            waitForIdle()
            onNodeWithTag("kit-skeleton-rows").assertExists()
            mainClock.autoAdvance = false
            loaded = true
            repeat(2) { mainClock.advanceTimeByFrame() }
            onNodeWithTag("kit-skeleton-rows").assertExists()
            onNodeWithText("Real rows").assertExists()
            mainClock.advanceTimeBy(600)
            onNodeWithTag("kit-skeleton-rows").assertDoesNotExist()
        }

    @Test
    fun `MOT-LOAD-2 under reduced motion the placeholders go at once`() =
        runDesktopComposeUiTest(412, 860) {
            var loaded by mutableStateOf(false)
            setContent {
                Themed(MotionLevel.Off) {
                    LazyColumn {
                        if (!loaded) item(key = "opening") { KitSkeletonItem() }
                        else item(key = "rows") { Box(kitItemMotion()) { KitText("Real rows") } }
                    }
                }
            }
            waitForIdle()
            loaded = true
            waitForIdle()
            onNodeWithTag("kit-skeleton-rows").assertDoesNotExist()
        }

    /** The largest gap, over the first frames, between the bottom of the panel that eases and the top of the item below. */
    private fun worstGap(placement: Boolean): Float {
        var worst = 0f
        runDesktopComposeUiTest(412, 860) {
            var more by mutableStateOf(false)
            setContent {
                Themed {
                    LazyColumn {
                        item(key = "a") {
                            Box(kitItemMotion(placement = placement).testTag("a")) {
                                KitRowPanel {
                                    KitText("One")
                                    if (more) { KitText("Two"); KitText("Three") }
                                }
                            }
                        }
                        item(key = "b") { Box(kitItemMotion(placement = placement).testTag("b")) { KitText("Below") } }
                    }
                }
            }
            waitForIdle()
            mainClock.autoAdvance = false
            more = true
            repeat(6) {
                mainClock.advanceTimeByFrame()
                val a = onNodeWithTag("a").getBoundsInRoot()
                val b = onNodeWithTag("b").getBoundsInRoot()
                worst = maxOf(worst, kotlin.math.abs((b.top - a.bottom).value))
            }
        }
        return worst
    }

    @Test
    fun `MOT-ONE-3 a panel that eases its height is followed by the item below it with no second clock`() {
        assertEquals(0f, worstGap(placement = false), "the item below sits right under the easing panel on every frame")
        assertTrue(worstGap(placement = true) > 0f, "with placement on, the item below lags the panel: two clocks on one change")
    }
}
