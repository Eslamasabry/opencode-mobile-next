package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.height
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Plan 08 §5.2 `StopGuardTest` and the Stop chip's keys (`ComposerKeysUiTest`, the Stop part): taps inside 800 ms of a
 * send are ignored (fix `49d067f2d`, `chat_stop_guard_test.dart`) while the chip stays enabled and pressable; a confirmed
 * stop plays Commit; Working disables it with a reason; the hold menu has one item, "Stop after this step".
 */
@OptIn(ExperimentalTestApi::class)
class StopGuardTest {
    @Test
    fun `the guard ignores taps for 800 ms after it is armed and lets later taps through`() {
        val guard = StopGuard()
        assertFalse(guard.allows(5_000_000L), "not armed yet (the chip's first frame arms it): taps wait")
        guard.arm(1_000_000_000L)
        assertFalse(guard.allows(1_000_000_000L))
        assertFalse(guard.allows(1_799_000_000L))
        assertTrue(guard.allows(1_800_000_000L))
        guard.arm(2_000_000_000L)
        assertFalse(guard.allows(2_500_000_000L), "re-armed by the next send")
    }

    @Test
    fun `B-CHAT-012 taps on Stop within 800 ms of a send are ignored, later taps stop the turn`() =
        runComposeUiTest {
            mainClock.autoAdvance = false
            val platform = RecordingPlatform()
            var stops = 0
            var sends by mutableIntStateOf(0)
            setContent {
                MotionTheme(level = MotionLevel.Full, platform = platform) {
                    KitComposerStop("Stop", "Stop Claude Code", onStop = { stops++ }, armKey = sends)
                }
            }
            fun tap() {
                onNodeWithTag(STOP_TAG).assertIsEnabled().performClick() // still pressable: the guard hides nothing
                mainClock.advanceTimeByFrame()
            }
            mainClock.advanceTimeByFrame()
            tap()
            mainClock.advanceTimeBy(300)
            tap()
            mainClock.advanceTimeBy(150) // each tap also moves the test clock (~60 ms): this one lands near 700 ms
            tap()
            assertEquals(0, stops, "a double tap on Send never stops the turn")
            assertTrue(KitHaptic.Commit !in platform.haptics, "an ignored tap plays nothing")
            mainClock.advanceTimeBy(250) // past 800 ms
            tap()
            assertEquals(1, stops)
            assertEquals(listOf(KitHaptic.Commit), platform.haptics, "a confirmed stop plays Commit")
            // The next send re-arms the guard.
            sends++
            mainClock.advanceTimeByFrame()
            tap()
            assertEquals(1, stops)
            mainClock.advanceTimeBy(850)
            tap()
            assertEquals(2, stops)
        }

    @Test
    fun `the chip names its target and a stop on its way disables it with a reason`() =
        runComposeUiTest {
            var state by mutableStateOf(KitStopState.Running)
            setContent {
                MotionTheme {
                    KitComposerStop(
                        "Stop", "Stop Claude Code", onStop = {}, state = state, workingReason = "Stopping",
                        stopAfterStep = KitStopAfterStep("Stop after this step") {},
                    )
                }
            }
            onNodeWithTag(STOP_TAG).assertContentDescriptionEquals("Stop Claude Code").assertIsEnabled()
            state = KitStopState.Stuck
            waitForIdle()
            onNodeWithTag(STOP_TAG).assertIsEnabled()
            state = KitStopState.Working
            waitForIdle()
            onNodeWithTag(STOP_TAG).assertIsNotEnabled()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Stopping"))
            val node = onNodeWithTag(STOP_TAG).fetchSemanticsNode()
            assertNull(node.config.getOrNull(SemanticsActions.CustomActions), "Working: the hold menu is empty")
        }

    @Test
    fun `holding Stop opens one item, Stop after this step, with the long-press haptic, also as a screen-reader action`() =
        runComposeUiTest {
            val platform = RecordingPlatform()
            var afterStep = 0
            setContent {
                MotionTheme(platform = platform) {
                    KitComposerStop(
                        "Stop", "Stop Claude Code", onStop = {},
                        stopAfterStep = KitStopAfterStep("Stop after this step") { afterStep++ },
                    )
                }
            }
            val actions = onNodeWithTag(STOP_TAG).fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions)
            assertNotNull(actions)
            assertEquals(listOf("Stop after this step"), actions.map { it.label })
            onNodeWithTag(STOP_TAG).performTouchInput { longClick() }
            waitForIdle()
            assertEquals(listOf(KitHaptic.LongPress), platform.haptics)
            onNodeWithText("Stop after this step").performClick()
            waitForIdle()
            assertEquals(1, afterStep)
            onNodeWithText("Stop after this step").assertDoesNotExist()
            onNodeWithTag(STOP_TAG).performSemanticsAction(SemanticsActions.OnLongClick)
            waitForIdle()
            onNodeWithText("Stop after this step").assertExists()
        }

    @Test
    fun `an agent that cannot stop after a step has no hold menu`() =
        runComposeUiTest {
            setContent { MotionTheme { KitComposerStop("Stop", "Stop Codex", onStop = {}) } }
            val config = onNodeWithTag(STOP_TAG).fetchSemanticsNode().config
            assertNull(config.getOrNull(SemanticsActions.OnLongClick))
            assertNull(config.getOrNull(SemanticsActions.CustomActions))
        }
}
