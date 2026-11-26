@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import io.github.eslamasabry.oc.ui.kitchat.stream.KitPromptLiftHost
import io.github.eslamasabry.oc.ui.kitchat.stream.KitPromptLiftState
import io.github.eslamasabry.oc.ui.kitchat.stream.LocalKitPromptLift
import io.github.eslamasabry.oc.ui.kitchat.stream.kitPromptLift
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * B-530 kit-level verification:
 * - Motion Off settles cleanly on index 0 without animation.
 * - Motion Full/Calm anchors the prior newest row across a prepend and smoothly glides to index 0.
 * - `awayFromLatest` reflects the true viewport offset even while `following == true`.
 * - Owner decision #25: the person's own sent prompt with a pending `KitPromptLift` lands at
 *   index 0 on frame 1 so the bubble lifts out of the composer instead of gliding from off-screen.
 */
class SmoothFollowMotionTest {
    private fun ComposeUiTest.setup(
        rows: () -> List<String>,
        motion: KitMotionLevel,
        level: MotionLevel,
        holder: Array<TranscriptListState?>,
    ) {
        setContent {
            KitTheme(
                appearance = KitAppearance(motion = level),
                platform = DesktopKitPlatform(),
                localeTag = "en",
            ) {
                Box(Modifier.size(360.dp, 600.dp)) {
                    val state = rememberTranscriptListState()
                    holder[0] = state
                    KitTranscriptList(
                        state = state,
                        newestKey = rows().firstOrNull(),
                        jumpLabel = "Jump to latest",
                        motion = motion,
                    ) {
                        items(rows(), key = { it }) { id ->
                            Box(Modifier.fillMaxWidth().height(60.dp).testTag(id)) { KitText(id) }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `B-530 reduced motion settles on newest row immediately without animation`() = runComposeUiTest {
        var rows by mutableStateOf((0 until 30).map { "m$it" })
        val holder = arrayOfNulls<TranscriptListState>(1)
        setup({ rows }, KitMotionLevel.Off, MotionLevel.Off, holder)
        waitForIdle()

        val state = holder[0]!!
        mainClock.autoAdvance = false
        rows = listOf("n2", "n1", "n0") + rows
        mainClock.advanceTimeByFrame()
        assertEquals(0, state.lazy.firstVisibleItemIndex, "reduced motion lands on index 0 without animation")
        assertEquals(0, state.lazy.firstVisibleItemScrollOffset)
        mainClock.autoAdvance = true
        waitForIdle()
    }

    @Test
    fun `B-530 calm motion anchors prior newest row on arrival frame then glides to index 0`() = runComposeUiTest {
        var rows by mutableStateOf((0 until 30).map { "m$it" })
        val holder = arrayOfNulls<TranscriptListState>(1)
        setup({ rows }, KitMotionLevel.Calm, MotionLevel.Calm, holder)
        waitForIdle()

        val state = holder[0]!!
        mainClock.autoAdvance = false
        rows = listOf("n2", "n1", "n0") + rows
        mainClock.advanceTimeByFrame()
        assertEquals(3, state.lazy.firstVisibleItemIndex, "prior newest row stays anchored on arrival frame")
        assertTrue(
            state.awayFromLatest,
            "awayFromLatest reflects true viewport offset while index 0 is off-edge",
        )
        repeat(40) { mainClock.advanceTimeByFrame() }
        assertEquals(0, state.lazy.firstVisibleItemIndex, "glided smoothly back to index 0")
        assertEquals(0, state.lazy.firstVisibleItemScrollOffset)
        assertFalse(state.awayFromLatest, "awayFromLatest is false once settled at index 0")
        assertTrue(state.following)
        mainClock.autoAdvance = true
        waitForIdle()
    }

    @Test
    fun `B-530 owner decision 25 sent prompt lands at index 0 on frame 1 for prompt lift`() = runComposeUiTest {
        val sentText = "Ship the smooth follow fix"
        var rows by mutableStateOf((0 until 30).map { "m$it" })
        val holder = arrayOfNulls<TranscriptListState>(1)
        val liftHolder = arrayOfNulls<KitPromptLiftState>(1)
        setContent {
            KitTheme(
                appearance = KitAppearance(motion = MotionLevel.Full),
                platform = DesktopKitPlatform(),
                localeTag = "en",
            ) {
                KitPromptLiftHost {
                    liftHolder[0] = LocalKitPromptLift.current
                    Box(Modifier.size(360.dp, 600.dp)) {
                        val state = rememberTranscriptListState()
                        holder[0] = state
                        KitTranscriptList(
                            state = state,
                            newestKey = rows.firstOrNull(),
                            jumpLabel = "Jump to latest",
                            motion = KitMotionLevel.Full,
                        ) {
                            items(rows, key = { it }) { id ->
                                if (id == "pending:1") {
                                    KitPromptBubble(sentText, Modifier.kitPromptLift(1L, sentText))
                                } else {
                                    Box(Modifier.fillMaxWidth().height(60.dp).testTag(id)) { KitText(id) }
                                }
                            }
                        }
                    }
                }
            }
        }
        waitForIdle()

        val state = holder[0]!!
        val lift = liftHolder[0]!!
        lift.editorAt(Rect(0f, 540f, 360f, 600f))
        lift.offer(sentText)

        mainClock.autoAdvance = false
        rows = listOf("pending:1") + rows
        mainClock.advanceTimeByFrame()
        assertEquals(0, state.lazy.firstVisibleItemIndex, "sent prompt lands at index 0 immediately for lift")
        assertNull(lift.take(sentText), "prompt lift consumed the offered composer origin on frame 1")
        onNodeWithText(sentText).assertIsDisplayed()

        mainClock.autoAdvance = true
        waitForIdle()
    }
}
