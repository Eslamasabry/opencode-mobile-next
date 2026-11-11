@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.dock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * e. Moving the dock keeps the grab: the point of the head under the finger stays under the finger. The column used to
 * re-centre on the finger, so the dock jumped when a move started and every move saved a shifted height. Driven through
 * the dock's own gesture path (as its pointer loop drives it).
 */
class KitEdgeDockMoveTest {
    private class Seen : KitDockIntents {
        val biases = mutableListOf<Float>()

        override fun onPlacementChange(side: KitDockSide, verticalBias: Float) {
            biases += verticalBias
        }
    }

    @Test
    fun `e - a move from the head keeps the grab offset and saves where the dock really stands`() =
        runDesktopComposeUiTest(412, 915) {
            val seen = Seen()
            val state = KitEdgeDockState()
            val rings = List(5) { KitDockRing("r$it", "ring $it", null, KitDockRingState.Idle) }
            setContent {
                KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                    Box(Modifier.fillMaxSize()) {
                        KitEdgeDock(KitDockModel(rings = rings), seen, TestDockWords, insets = KitDockInsets(top = 24.dp, bottom = 48.dp), state = state)
                    }
                }
            }
            waitForIdle()
            val x = runOnIdle { state.widthDp - DockDims.REST_W / 2f }
            val top0 = runOnIdle { state.top.value }
            // The finger on the head, at its centre: far above the column's centre.
            val y0 = top0 + DockDims.HEAD / 2f
            runOnIdle {
                state.computeFrame()
                assertEquals(DockZone.Grip, state.zoneAt(x, y0), "the finger is on the head")
                state.gesture.down(DockZone.Grip, x, y0, 0L, state.hits)
                state.onDown(DockZone.Grip)
            }
            // Past the slop (a move starts), then 1 dp more.
            for ((n, dy) in listOf(9f, 10f).withIndex()) {
                runOnIdle {
                    state.computeFrame()
                    state.gesture.move(x, y0 + dy, 40L + 20L * n, state.hits)
                    state.onMove(x, y0 + dy, x)
                }
                waitForIdle()
                assertEquals(DockMode.Move, runOnIdle { state.gesture.mode }, "the head's drag moves the dock")
                val moved = runOnIdle { state.top.value } - top0
                assertTrue(abs(moved - dy) < 0.01f, "after a $dy dp drag the dock moved $moved dp (it must follow the finger, not jump)")
            }
            // Letting go saves the height the dock stands at, not one shifted by the grab.
            val (column, safeTop, safeBottom) = runOnIdle { Triple(state.column.height, state.safeTop, state.safeBottom) }
            val standsAt = runOnIdle { state.top.value } + column / 2f
            runOnIdle { state.onOutcome(state.gesture.up(x, y0 + 10f, 0f, state.hits)) }
            waitForIdle()
            assertEquals(1, seen.biases.size, "one placement saved")
            assertEquals(biasFor(standsAt, column, safeTop, safeBottom), seen.biases.single(), 0.0001f, "the saved height is where the dock stands")
        }
}
