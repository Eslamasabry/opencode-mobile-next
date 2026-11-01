package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.height
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Plan 08 §5.2 `ComposerKeysUiTest` (the motion half this unit owns): the status strip unfolds with the Stop when it was
 * empty and folds when it empties; with chips present the Stop fades in beside them without moving the strip; under Off
 * all of it is instant; the "Stop and send" pill shows its words, plays Send and swaps with the disc.
 */
@OptIn(ExperimentalTestApi::class)
class ComposerKeysUiTest {
    private val stopChip: @androidx.compose.runtime.Composable () -> Unit = {
        KitComposerStop("Stop", "Stop Claude Code", onStop = {})
    }

    @Test
    fun `an empty strip unfolds with the Stop over 250 ms and folds away when the turn ends`() =
        runComposeUiTest {
            mainClock.autoAdvance = false
            var running by mutableStateOf(false)
            setContent {
                MotionTheme(level = MotionLevel.Full) {
                    KitComposerStripReveal(stop = if (running) stopChip else null, modifier = Modifier.testTag("strip-host"))
                }
            }
            mainClock.advanceTimeByFrame()
            onNodeWithTag(STRIP_TAG).assertDoesNotExist()
            running = true
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeBy(100)
            val mid = onNodeWithTag("strip-host").getBoundsInRoot().height
            mainClock.advanceTimeBy(300)
            val full = onNodeWithTag("strip-host").getBoundsInRoot().height
            assertTrue(mid > 0.dp && mid < full, "unfolding: $mid of $full")
            onNodeWithTag(STOP_TAG).assertExists()
            running = false
            mainClock.advanceTimeBy(250)
            onNodeWithTag(STRIP_TAG).assertDoesNotExist()
        }

    @Test
    fun `with chips in the strip the Stop fades in at the trailing end and the strip keeps its height`() =
        runComposeUiTest {
            mainClock.autoAdvance = false
            var running by mutableStateOf(false)
            setContent {
                MotionTheme(level = MotionLevel.Full) {
                    Box(Modifier.width(360.dp)) {
                        KitComposerStripReveal(
                            stop = if (running) stopChip else null,
                            modifier = Modifier.testTag("strip-host"),
                            chips = { KitChip("Sonnet") },
                        )
                    }
                }
            }
            repeat(3) { mainClock.advanceTimeByFrame() }
            val before = onNodeWithTag("strip-host").getBoundsInRoot()
            running = true
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeBy(50)
            assertEquals(before.height, onNodeWithTag("strip-host").getBoundsInRoot().height, "no fold for the Stop alone")
            mainClock.advanceTimeBy(200)
            val stop = onNodeWithTag(STOP_TAG).getBoundsInRoot()
            assertEquals(before.right, stop.right, "the Stop sits at the trailing end")
            onNodeWithText("Sonnet").assertExists()
        }

    @Test
    fun `MOT-7 under Off the strip and the Stop appear and leave at once`() =
        runComposeUiTest {
            mainClock.autoAdvance = false
            var running by mutableStateOf(false)
            setContent {
                MotionTheme(level = MotionLevel.Off) {
                    KitComposerStripReveal(stop = if (running) stopChip else null, modifier = Modifier.testTag("strip-host"))
                }
            }
            mainClock.advanceTimeByFrame()
            running = true
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeByFrame()
            onNodeWithTag(STOP_TAG).assertExists()
            running = false
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeByFrame()
            onNodeWithTag(STRIP_TAG).assertDoesNotExist()
        }

    @Test
    fun `a non-steering agent with words in the field shows the Stop and send pill, which plays Send`() =
        runComposeUiTest {
            val platform = RecordingPlatform()
            var sent = 0
            var stopAndSend by mutableStateOf(false)
            setContent {
                MotionTheme(platform = platform) {
                    KitComposerSwap(stopAndSend) { pill ->
                        if (pill) KitStopAndSendPill("Stop and send", onClick = { sent++ })
                        else Box(Modifier.size(48.dp).testTag("send-disc"))
                    }
                }
            }
            onNodeWithTag("send-disc").assertExists()
            stopAndSend = true
            waitForIdle()
            onNodeWithTag(PILL_TAG).assertExists()
            onNodeWithText("Stop and send", useUnmergedTree = true).assertExists()
            onNodeWithTag(PILL_TAG).performClick()
            assertEquals(1, sent)
            assertEquals(listOf(KitHaptic.Send), platform.haptics)
            onNodeWithTag("send-disc").assertDoesNotExist()
        }

    @Test
    fun `the field button swap cross-fades over 150 ms under Full and is instant under Off`() =
        runComposeUiTest {
            mainClock.autoAdvance = false
            var level by mutableStateOf(MotionLevel.Full)
            var face by mutableStateOf("mic")
            setContent {
                MotionTheme(level = level) { KitComposerSwap(face) { Box(Modifier.size(48.dp).testTag(it)) } }
            }
            mainClock.advanceTimeByFrame()
            face = "send"
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeBy(64)
            onNodeWithTag("mic").assertExists() // both faces while they cross-fade
            onNodeWithTag("send").assertExists()
            mainClock.advanceTimeBy(150)
            onNodeWithTag("mic").assertDoesNotExist()
            level = MotionLevel.Off
            mainClock.advanceTimeByFrame()
            face = "pill"
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeByFrame()
            onNodeWithTag("send").assertDoesNotExist()
            onNodeWithTag("pill").assertExists()
        }
}
