package io.github.eslamasabry.oc.ui.shell.nav

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.navigationevent.NavigationEvent
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Plan 08 §5.4 `PredictiveBackPolicyTest`, run headless on the desktop JVM against the real NavShell, NavDisplay and
 * kit grow parts; the back gesture is a fake NavigationEventInput on the dispatcher NavDisplay listens to.
 */
@OptIn(ExperimentalTestApi::class)
class PredictiveBackShellTest {
    @Test
    fun `B-KIT-054 tap grows the chat, the gate settles, button back returns to the list`() = runComposeUiTest {
        val h = Harness()
        setContent { h.Content() }
        onNodeWithText("Row 2").performClick()
        waitForIdle()
        onNodeWithText("Chat s2").assertExists()
        assertEquals(GateState.Idle, h.gate.state)
        assertEquals(listOf(GateEvent.TapOpen, GateEvent.TransitionSettled), h.gate.trace)
        onNodeWithContentDescription("Back").performClick()
        waitForIdle()
        onNodeWithText("Chat s2").assertDoesNotExist()
        onNodeWithText("Row 2").assertExists()
        assertEquals(GateState.Idle, h.gate.state)
        assertEquals(GateEvent.ButtonBack, h.gate.trace[2])
    }

    @Test
    fun `B-KIT-054 P2-1 a committed gesture keeps shared bounds off until the pop settles`() = runComposeUiTest {
        val h = Harness()
        setContent { h.Content() }
        onNodeWithText("Row 3").performClick()
        waitForIdle()
        mainClock.autoAdvance = false
        runOnIdle { h.input.start(NavigationEvent.EDGE_LEFT) }
        mainClock.advanceTimeByFrame()
        assertEquals(GateState.Gesture, h.gate.state)
        assertFalse(sharedBoundsEnabled(h.gate.state, ShellMotionLevel.Full))
        runOnIdle { h.input.progress(NavigationEvent.EDGE_LEFT, 0.5f) }
        mainClock.advanceTimeByFrame()
        onNodeWithText("Row 3").assertExists() // the list is composed under the preview
        runOnIdle { h.input.complete() }
        mainClock.advanceTimeByFrame()
        assertEquals(GateState.GestureSettling, h.gate.state, "commit must not re-enable the transform mid-pop")
        mainClock.autoAdvance = true
        waitForIdle()
        assertEquals(GateState.Idle, h.gate.state)
        onNodeWithText("Chat s3").assertDoesNotExist()
        assertEquals<Any?>(ChatsRoute.Home, h.navigator.top.value)
        assertTrue(GateEvent.GestureCommitted in h.gate.trace)
        assertFalse(GateEvent.ButtonBack in h.gate.trace, "a gesture commit is never treated as a button back")
    }

    @Test
    fun `B-KIT-054 P2-2 a cancelled gesture returns to the chat and the next tap still transforms`() = runComposeUiTest {
        val h = Harness()
        setContent { h.Content() }
        onNodeWithText("Row 1").performClick()
        waitForIdle()
        runOnIdle {
            h.input.start(NavigationEvent.EDGE_RIGHT)
            h.input.progress(NavigationEvent.EDGE_RIGHT, 0.4f)
        }
        waitForIdle()
        assertEquals(GateState.Gesture, h.gate.state)
        runOnIdle { h.input.cancel() }
        waitForIdle()
        assertEquals(GateState.Idle, h.gate.state)
        onNodeWithText("Chat s1").assertExists()
        assertIs<ChatRoute>(h.navigator.top.value)
        onNodeWithContentDescription("Back").performClick()
        waitForIdle()
        onNodeWithText("Row 4").performClick()
        mainClock.autoAdvance = false
        mainClock.advanceTimeByFrame()
        assertTrue(sharedBoundsEnabled(h.gate.state, ShellMotionLevel.Full), "the gate was cleared by settle")
        mainClock.autoAdvance = true
        waitForIdle()
        onNodeWithText("Chat s4").assertExists()
    }

    @Test
    fun `B-KIT-054 a gesture during the opening transform turns it off and the pop still completes`() =
        runComposeUiTest {
            val h = Harness()
            setContent { h.Content() }
            mainClock.autoAdvance = false
            onNodeWithText("Row 5").performClick()
            mainClock.advanceTimeBy(120)
            runOnIdle { h.input.start(NavigationEvent.EDGE_LEFT) }
            mainClock.advanceTimeByFrame()
            assertEquals(GateState.Gesture, h.gate.state)
            runOnIdle { h.input.complete() }
            mainClock.autoAdvance = true
            waitForIdle()
            assertEquals(GateState.Idle, h.gate.state)
            onNodeWithText("Chat s5").assertDoesNotExist()
            onNodeWithText("Row 5").assertExists()
        }

    @Test
    fun `B-KIT-054 P2-6 reduced motion pops with no transform and the gate stays consistent`() = runComposeUiTest {
        val h = Harness(scale = 0f)
        setContent { h.Content() }
        onNodeWithText("Row 2").performClick()
        waitForIdle()
        onNodeWithText("Chat s2").assertExists()
        runOnIdle { h.input.start(NavigationEvent.EDGE_LEFT) }
        waitForIdle()
        runOnIdle { h.input.complete() }
        waitForIdle()
        onNodeWithText("Chat s2").assertDoesNotExist()
        assertEquals(GateState.Idle, h.gate.state)
    }

    @Test
    fun `B-KIT-054 the root never consumes back`() = runComposeUiTest {
        val h = Harness()
        setContent { h.Content() }
        waitForIdle()
        runOnIdle {
            h.input.start(NavigationEvent.EDGE_LEFT)
            h.input.complete()
        }
        waitForIdle()
        onNodeWithText("Row 0").assertExists()
        assertFalse(GateEvent.GestureCommitted in h.gate.trace, "no app handler is enabled at the root")
    }
}
