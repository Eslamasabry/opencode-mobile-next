@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.dock

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitBackGuard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Device QA 2026-11-13: in a conversation one tap on the nub brought the strip out, and then Back left the page with the
 * strip still out, a tap outside and a tap on its handle did nothing, and only a swipe put it away. The strip the person
 * brings out is an open thing like a sheet: Back closes it first and does nothing else, a tap outside closes it and is
 * not passed to the page, its handle toggles it, and it never outlives its page.
 */
class KitEdgeDockBackTest {
    private val back =
        object : NavigationEventInput() {
            fun press() {
                dispatchOnBackStarted(NavigationEvent(NavigationEvent.EDGE_LEFT, 0f))
                dispatchOnBackCompleted()
            }
        }
    private var fellThrough = 0
    private val owner =
        object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher =
                NavigationEventDispatcher(onBackCompletedFallback = { fellThrough++ }).also { it.addInput(back) }
        }

    private class Page {
        var tucked by mutableStateOf(true)
        var shown by mutableStateOf(true)
        var backs = 0
        var rowTaps = 0
        val tuckCalls = mutableListOf<Boolean>()
    }

    private fun run(block: ComposeUiTest.(Page, KitEdgeDockState) -> Unit) {
        runDesktopComposeUiTest(412, 915) {
            val page = Page()
            val state = KitEdgeDockState()
            val rings = List(3) { KitDockRing("r$it", "ring $it", null, KitDockRingState.Idle) }
            setContent {
                CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                    KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                        Box(Modifier.fillMaxSize()) {
                            // The page: it owns Back and has a row under the dock's reach.
                            KitBackGuard(true) { page.backs++ }
                            Box(Modifier.padding(top = 300.dp).height(80.dp).width(200.dp).clickable { page.rowTaps++ })
                            if (page.shown) {
                                KitEdgeDock(
                                    KitDockModel(rings = rings, tucked = page.tucked),
                                    object : KitDockIntents {
                                        override fun onTuckChange(tucked: Boolean) {
                                            page.tuckCalls += tucked
                                            page.tucked = tucked
                                        }
                                    },
                                    TestDockWords,
                                    insets = KitDockInsets(top = 24.dp, bottom = 48.dp),
                                    state = state,
                                )
                            }
                        }
                    }
                }
            }
            waitForIdle()
            block(page, state)
        }
    }

    /** One tap on the nub, where the band stands. */
    private fun ComposeUiTest.tapNub(state: KitEdgeDockState) {
        val y = runOnIdle { state.columnTop + state.column.height / 2f }
        onNodeWithTag("kit-edge-dock").performTouchInput { click(Offset(width - 8f, y)) }
        waitForIdle()
    }

    private fun ComposeUiTest.tapAt(x: Float, y: Float) {
        onNodeWithTag("kit-edge-dock").performTouchInput { click(Offset(x, y)) }
        waitForIdle()
    }

    @Test
    fun `B-DOCK-BACK-1 Back closes the strip first and does nothing else`() =
        run { page, state ->
            tapNub(state)
            assertTrue(runOnIdle { state.stripOpen }, "one tap on the nub brought the strip out")
            assertEquals(listOf(false), page.tuckCalls)
            runOnIdle { back.press() }
            waitForIdle()
            assertFalse(runOnIdle { state.stripOpen }, "Back put the strip away")
            assertEquals(listOf(false, true), page.tuckCalls)
            assertEquals(0, page.backs, "the page did not navigate")
            assertEquals(0, fellThrough)
            runOnIdle { back.press() }
            assertEquals(1, page.backs, "the next Back is the page's")
        }

    @Test
    fun `B-DOCK-BACK-2 a tap outside closes the strip and is not passed to the page`() =
        run { page, state ->
            tapNub(state)
            assertTrue(runOnIdle { state.stripOpen })
            tapAt(100f, 340f)
            assertFalse(runOnIdle { state.stripOpen }, "the tap outside put the strip away")
            assertEquals(true, page.tuckCalls.last())
            assertEquals(0, page.rowTaps, "the row under the tap was not activated")
            tapAt(100f, 340f)
            assertEquals(1, page.rowTaps, "with the strip away the page takes its taps again")
        }

    @Test
    fun `B-DOCK-BACK-3 a tap on the handle puts the strip away`() =
        run { page, state ->
            tapNub(state)
            assertTrue(runOnIdle { state.stripOpen })
            val y = runOnIdle { state.columnTop + DockDims.HEAD / 2f }
            tapAt(412f - DockDims.REST_W / 2f, y)
            assertFalse(runOnIdle { state.stripOpen }, "the handle toggled it away")
            assertEquals(true, page.tuckCalls.last())
            assertEquals(0, page.rowTaps)
        }

    @Test
    fun `B-DOCK-BACK-4 an open strip does not survive leaving its page`() =
        run { page, state ->
            tapNub(state)
            assertTrue(runOnIdle { state.stripOpen })
            runOnIdle { page.shown = false }
            waitForIdle()
            assertEquals(true, page.tuckCalls.last(), "leaving the page put the strip away for the next page")
        }
}
