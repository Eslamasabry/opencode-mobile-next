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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.modal.SheetDragRules
import kotlin.test.assertFalse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Motion lead 2026-11-07: lists breathe (inserts and removals glide, never pop) and sheets follow the finger. Each test
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

    @Test
    fun `MOT-SHEET a short slow pull springs the sheet back, a long pull dismisses it`() =
        runDesktopComposeUiTest(400, 800) {
            var open by mutableStateOf(true)
            setContent {
                KitTheme(KitAppearance(), FakeKitPlatform(), "en") {
                    KitSheetHost(Modifier.fillMaxSize()) {
                        KitSheet(visible = open, title = "Model", onDismissRequest = { open = false }) {
                            KitText("sheet body")
                        }
                    }
                }
            }
            waitForIdle()
            val rest = top("sheet body")
            // A short, slow pull from the handle: the sheet follows, then returns.
            onNodeWithTag("kit-sheet").performTouchInput {
                swipeDown(startY = top + 8f, endY = top + 40f, durationMillis = 600)
            }
            waitForIdle()
            assertTrue(open, "a short slow pull keeps the sheet")
            assertEquals(rest, top("sheet body"), "and it settles back home")
            onNodeWithTag("kit-sheet").performTouchInput {
                swipeDown(startY = top + 8f, endY = top + 8f + height * 0.6f, durationMillis = 400)
            }
            waitForIdle()
            assertFalse(open, "a pull past a quarter of its height dismisses it")
        }

    @Test
    fun `MOT-SHEET release rules - a quarter of the height or a downward flick dismisses, an upward flick keeps`() {
        val d = Density(2f)
        assertFalse(SheetDragRules.dismisses(offset = 100f, height = 800, velocity = 0f, density = d))
        assertTrue(SheetDragRules.dismisses(offset = 201f, height = 800, velocity = 0f, density = d))
        assertTrue(SheetDragRules.dismisses(offset = 20f, height = 800, velocity = 2500f, density = d), "a flick")
        assertFalse(SheetDragRules.dismisses(offset = 500f, height = 800, velocity = -2500f, density = d))
        assertEquals(0f, SheetDragRules.follow(10f, -30f), "never rises past rest")
        assertEquals(40f, SheetDragRules.follow(10f, 30f), "follows the finger 1:1")
    }
}
