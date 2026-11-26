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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B-530 kit-level verification:
 * - Motion Off settles cleanly on index 0 without animation.
 * - Motion Full/Calm anchors the prior newest row across a prepend and smoothly glides to index 0.
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
        repeat(40) { mainClock.advanceTimeByFrame() }
        assertEquals(0, state.lazy.firstVisibleItemIndex, "glided smoothly back to index 0")
        assertEquals(0, state.lazy.firstVisibleItemScrollOffset)
        assertTrue(state.following)
        mainClock.autoAdvance = true
        waitForIdle()
    }
}
