package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kitchat.ChatTestTheme
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class HybridAtNewestTest {
    private fun runGesture(returnGapDp: Float?, leaveDp: Float = LEAVE_DP): Boolean {
        var result = false
        runComposeUiTest {
            lateinit var state: TranscriptListState
            mainClock.autoAdvance = false
            setContent {
                CompositionLocalProvider(LocalTranscriptAnchor provides TranscriptAnchor.Hybrid) {
                    ChatTestTheme(dark = true) {
                        state = rememberTranscriptListState()
                        KitTranscriptList(
                            state = state,
                            newestKey = "r0",
                            jumpLabel = "Jump to latest",
                            motion = KitMotionLevel.Off,
                            modifier = Modifier.width(VIEWPORT_W_DP.dp).height(VIEWPORT_H_DP.dp),
                            contentPadding = PaddingValues(bottom = BOTTOM_PAD_DP.dp),
                        ) {
                            item(key = "r0") { Box(Modifier.fillMaxWidth().height(NEWEST_ROW_DP.dp)) }
                            items(count = OLDER_ROW_COUNT, key = { "r${it + 1}" }) {
                                Box(Modifier.fillMaxWidth().height(OLDER_ROW_DP.dp))
                            }
                        }
                    }
                }
            }
            repeat(INITIAL_FRAMES) { mainClock.advanceTimeByFrame() }
            assertTrue(state.following, "expected following == true before drag")
            onNodeWithTag("transcript-list").performTouchInput {
                val slop = viewConfiguration.touchSlop
                val start = Offset(centerX, top + height * 0.2f)
                down(start)
                moveBy(Offset(0f, slop))
                moveBy(Offset(0f, leaveDp.dp.toPx()))
                if (returnGapDp != null) {
                    moveBy(Offset(0f, -(leaveDp - returnGapDp).dp.toPx()))
                }
                advanceEventTime(HOLD_MS)
                up()
            }
            repeat(SETTLE_FRAMES) { mainClock.advanceTimeByFrame() }
            result = state.following
        }
        return result
    }

    @Test
    fun `STEPS-SCROLL-009 hybrid newest row 40 dp under the line still counts as at newest`() {
        assertTrue(
            runGesture(returnGapDp = 40f),
            "expected following == true when returning to 40 dp under the line",
        )
    }

    @Test
    fun `STEPS-SCROLL-009b hybrid 56 dp under the line is away`() {
        assertFalse(
            runGesture(returnGapDp = 56f),
            "expected following == false when returning to 56 dp under the line",
        )
    }

    @Test
    fun `STEPS-SCROLL-009c hybrid 40 dp upward drag leaves following`() {
        assertFalse(
            runGesture(returnGapDp = null, leaveDp = 40f),
            "expected following == false after a 40 dp upward drag",
        )
    }

    private companion object {
        const val VIEWPORT_W_DP = 360
        const val VIEWPORT_H_DP = 800
        const val BOTTOM_PAD_DP = 120
        const val NEWEST_ROW_DP = 20
        const val OLDER_ROW_DP = 100
        const val OLDER_ROW_COUNT = 40
        const val LEAVE_DP = 200f
        const val INITIAL_FRAMES = 5
        const val SETTLE_FRAMES = 60
        const val HOLD_MS = 200L
    }
}
