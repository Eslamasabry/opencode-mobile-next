@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.dock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Device QA 2026-11-11 (run 5, d): the list opened by an inward flick stood beyond the wrong screen edge. Driven
 * through the real pointer loop at the widths of a phone, on both edges.
 */
class KitEdgeDockListGestureTest {
    private val rings =
        List(7) { KitDockRing("r$it", "conversation number $it", null, KitDockRingState.Idle) }

    private class Run(val state: KitEdgeDockState, val sideNow: () -> KitDockSide, val placements: MutableList<KitDockSide>)

    private fun scene(widthDp: Int, side: KitDockSide, block: ComposeUiTest.(Run) -> Unit) =
        runDesktopComposeUiTest(widthDp, 800) {
            var now by mutableStateOf(side)
            val placements = mutableListOf<KitDockSide>()
            val state = KitEdgeDockState()
            setContent {
                KitTheme(KitAppearance(motion = MotionLevel.Off), FakeKitPlatform(), "en", stillFrame = true) {
                    Box(Modifier.fillMaxSize()) {
                        KitEdgeDock(
                            KitDockModel(rings = rings, placement = KitDockPlacement(now, 0.5f)),
                            object : KitDockIntents {
                                override fun onPlacementChange(side: KitDockSide, verticalBias: Float) {
                                    placements += side
                                    now = side
                                }
                            },
                            TestDockWords,
                            insets = KitDockInsets(top = 24.dp, bottom = 48.dp),
                            state = state,
                        )
                    }
                }
            }
            waitForIdle()
            block(Run(state, { now }, placements))
        }

    /** The physical x of the strip's middle and the y of ring [i]'s centre. */
    private fun ComposeUiTest.ringPoint(run: Run, i: Int): Offset =
        runOnIdle {
            val w = run.state.widthDp
            val x = if (run.state.onRight) w - DockDims.REST_W / 2f else DockDims.REST_W / 2f
            Offset(x, run.state.slotY(i))
        }

    private fun describe(run: Run): String =
        runOnIdle {
            val b = run.state.body()
            "right=${run.state.onRight} open=${run.state.panelOpen} second=${b.hasSecond} list=${b.secondIsList} " +
                "A=[${b.stripLeft},${b.stripRight}] B=[${b.secondLeft},${b.secondRight}] growth=${b.growth} placements=${run.placements}"
        }

    @Test
    fun `list flick - diagnostics`() {
        for (w in listOf(360, 320)) for (side in listOf(KitDockSide.End, KitDockSide.Start)) {
            scene(w, side) { run ->
                val p = ringPoint(run, 2)
                val inward = if (side == KitDockSide.End) -1f else 1f
                onRoot().performTouchInput { swipe(p, Offset(p.x + inward * 200f, p.y), durationMillis = 120) }
                waitForIdle()
                println("DIAG w=$w side=$side flick-from-ring: ${describe(run)}")
            }
            scene(w, side) { run ->
                val p = ringPoint(run, 2)
                val inward = if (side == KitDockSide.End) -1f else 1f
                val head = Offset(p.x, runOnIdle { run.state.columnTop + 12f })
                onRoot().performTouchInput { swipe(head, Offset(head.x + inward * 200f, head.y), durationMillis = 120) }
                waitForIdle()
                println("DIAG w=$w side=$side flick-from-head: ${describe(run)}")
            }
        }
    }
}
