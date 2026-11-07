@file:OptIn(ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.motion.kitItemMotion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Motion lead 2026-11-07: lists breathe (inserts and removals glide, never pop). Each test
 * drives the test clock by hand and checks a frame in the middle of the move, plus the reduced-motion path.
 */
class MotionLeadKitTest {
    private fun ComposeUiTest.top(text: String): Dp = onNodeWithText(text).getBoundsInRoot().top

    private fun ComposeUiTest.list(level: MotionLevel, rows: () -> List<String>) {
        setContent {
            KitTheme(KitAppearance(motion = level), FakeKitPlatform(), "en") {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(rows(), key = { it }) { row ->
                        Box(kitItemMotion().fillMaxWidth().height(56.dp)) { KitText(row) }
                    }
                }
            }
        }
    }

    @Test
    fun `MOT-LIST a row inserted above pushes the rows below on a spring, not in one frame`() =
        runDesktopComposeUiTest(400, 800) {
            var rows by mutableStateOf(listOf("B", "C"))
            list(MotionLevel.Full) { rows }
            waitForIdle()
            val before = top("C")
            mainClock.autoAdvance = false
            rows = listOf("A", "B", "C")
            repeat(2) { mainClock.advanceTimeByFrame() }
            mainClock.advanceTimeBy(48)
            val mid = top("C")
            mainClock.autoAdvance = true
            waitForIdle()
            val after = top("C")
            assertEquals(before + 56.dp, after)
            assertTrue(mid > before && mid < after, "mid-move C is between its places: $before < $mid < $after")
        }

    @Test
    fun `MOT-LIST when a row leaves, the rows below glide up into its place`() =
        runDesktopComposeUiTest(400, 800) {
            var rows by mutableStateOf(listOf("A", "B", "C"))
            list(MotionLevel.Full) { rows }
            waitForIdle()
            val before = top("C")
            mainClock.autoAdvance = false
            rows = listOf("A", "C")
            repeat(2) { mainClock.advanceTimeByFrame() }
            mainClock.advanceTimeBy(48)
            val mid = top("C")
            mainClock.autoAdvance = true
            waitForIdle()
            val after = top("C")
            assertEquals(before - 56.dp, after)
            assertTrue(mid < before && mid > after, "mid-move C is between its places: $after < $mid < $before")
        }

    @Test
    fun `MOT-LIST with Animations Off an insert lands on the next frame`() =
        runDesktopComposeUiTest(400, 800) {
            var rows by mutableStateOf(listOf("B", "C"))
            list(MotionLevel.Off) { rows }
            waitForIdle()
            val before = top("C")
            mainClock.autoAdvance = false
            rows = listOf("A", "B", "C")
            repeat(2) { mainClock.advanceTimeByFrame() }
            assertEquals(before + 56.dp, top("C"), "the first frame is final")
        }

    @Test
    fun `MOT-LIST Calm fades rows but nothing glides`() =
        runDesktopComposeUiTest(400, 800) {
            var rows by mutableStateOf(listOf("B", "C"))
            list(MotionLevel.Calm) { rows }
            waitForIdle()
            val before = top("C")
            mainClock.autoAdvance = false
            rows = listOf("A", "B", "C")
            repeat(2) { mainClock.advanceTimeByFrame() }
            assertEquals(before + 56.dp, top("C"))
        }
}
