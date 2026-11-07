package io.github.eslamasabry.oc.ui.shell.nav

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.navigationevent.NavigationEvent
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Motion lead 2026-11-07: home row → chat is one object. The row grows into the page (spring bounds), the title flies
 * from the row into the bar, and a predictive back gesture drives the same transform with the finger. Mid-animation
 * positions are read from the real shell, NavDisplay and kit grow parts on a phone-sized headless window.
 */
@OptIn(ExperimentalTestApi::class)
class GrowTransformMotionTest {
    private fun ComposeUiTest.top(text: String): Dp = onNodeWithText(text).getBoundsInRoot().top

    @Test
    fun `MOT-PB the chat shrinks toward its row under the finger and grows back when the gesture is cancelled`() =
        runDesktopComposeUiTest(W, H) {
            val h = Harness()
            setContent { h.Content() }
            waitForIdle()
            onNodeWithText("Row 3").performClick()
            waitForIdle()
            val rest = top("transcript line")
            mainClock.autoAdvance = false
            runOnIdle { h.input.start(NavigationEvent.EDGE_LEFT) }
            mainClock.advanceTimeByFrame()
            runOnIdle { h.input.progress(NavigationEvent.EDGE_LEFT, 0.3f) }
            repeat(3) { mainClock.advanceTimeByFrame() }
            val at30 = top("transcript line")
            runOnIdle { h.input.progress(NavigationEvent.EDGE_LEFT, 0.7f) }
            repeat(3) { mainClock.advanceTimeByFrame() }
            val at70 = top("transcript line")
            // Positions are read from the semantics tree, which follows the transform's placement: the page has left
            // its resting place at 30 % and is further from it at 70 %.
            val d30 = (at30 - rest).value.let { kotlin.math.abs(it) }
            val d70 = (at70 - rest).value.let { kotlin.math.abs(it) }
            assertTrue(d30 > 1f, "at 30 % the page has started to move ($rest → $at30)")
            assertTrue(d70 > d30, "the page follows the finger further ($at30 → $at70)")
            runOnIdle { h.input.cancel() }
            mainClock.autoAdvance = true
            waitForIdle()
            assertEquals(rest, top("transcript line"), "a cancel grows the page back to where it was")
            assertIs<ChatRoute>(h.navigator.top.value)
            assertEquals(GateState.Idle, h.gate.state)
        }

    @Test
    fun `MOT-PB a committed gesture finishes the flight into the row and the list is home again`() =
        runDesktopComposeUiTest(W, H) {
            val h = Harness()
            setContent { h.Content() }
            onNodeWithText("Row 3").performClick()
            waitForIdle()
            runOnIdle {
                h.input.start(NavigationEvent.EDGE_RIGHT)
                h.input.progress(NavigationEvent.EDGE_RIGHT, 0.5f)
            }
            waitForIdle()
            runOnIdle { h.input.complete() }
            waitForIdle()
            onNodeWithText("Chat s3").assertDoesNotExist()
            onNodeWithText("Row 3").assertExists()
            assertEquals<Any?>(ChatsRoute.Home, h.navigator.top.value)
            assertEquals(GateState.Idle, h.gate.state)
        }

    @Test
    fun `MOT-GROW the page grows out of the row on its spring`() =
        runDesktopComposeUiTest(W, H) {
            val h = Harness()
            setContent { h.Content() }
            waitForIdle()
            mainClock.autoAdvance = false
            onNodeWithText("Row 3").performClick()
            repeat(2) { mainClock.advanceTimeByFrame() }
            mainClock.advanceTimeBy(60)
            val midPage = top("transcript line")
            mainClock.autoAdvance = true
            waitForIdle()
            val restPage = top("transcript line")
            assertTrue(midPage != restPage, "mid-open the page is still on its way ($midPage vs $restPage)")
        }

    @Test
    fun `MOT-GROW reduced motion opens the chat on the next frame, nothing travels`() =
        runDesktopComposeUiTest(W, H) {
            val h = Harness(scale = 0f)
            setContent { h.Content() }
            waitForIdle()
            mainClock.autoAdvance = false
            onNodeWithText("Row 3").performClick()
            repeat(2) { mainClock.advanceTimeByFrame() }
            val first = top("Chat s3")
            mainClock.autoAdvance = true
            waitForIdle()
            assertEquals(first, top("Chat s3"), "the first frame is final")
        }

    private companion object {
        const val W = 412
        const val H = 860
    }
}
