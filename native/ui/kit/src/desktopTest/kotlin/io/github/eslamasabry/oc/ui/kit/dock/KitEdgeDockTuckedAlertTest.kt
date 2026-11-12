@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.dock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
 * f. A new request in another conversation while the dock is tucked changes only that ring's dot: the nub neither leans
 * out nor pulses (it leaned about 7 dp for about 3 s). A dock the person pulled out still leans as before.
 */
class KitEdgeDockTuckedAlertTest {
    private fun alertLean(tucked: Boolean): List<Float> {
        val leans = mutableListOf<Float>()
        runDesktopComposeUiTest(412, 915) {
            mainClock.autoAdvance = false
            val state = KitEdgeDockState()
            var rings by mutableStateOf(List(3) { KitDockRing("r$it", "ring $it", null, KitDockRingState.Idle) })
            setContent {
                KitTheme(KitAppearance(motion = MotionLevel.Full), FakeKitPlatform(), "en", stillFrame = true) {
                    Box(Modifier.fillMaxSize()) {
                        KitEdgeDock(
                            KitDockModel(rings = rings, tucked = tucked),
                            object : KitDockIntents {},
                            TestDockWords,
                            insets = KitDockInsets(top = 24.dp, bottom = 48.dp),
                            state = state,
                        )
                    }
                }
            }
            mainClock.advanceTimeBy(1_500)
            val out0 = runOnUiThread { state.out.value }
            rings = rings.map { if (it.key == "r1") it.copy(state = KitDockRingState.NeedsYou, pulse = 1) else it }
            // Through the alert's window (the lean holds about 3 s), sampled every 250 ms.
            repeat(14) {
                mainClock.advanceTimeBy(250)
                leans += runOnUiThread { state.lean.value }
                if (tucked) assertEquals(out0, runOnUiThread { state.out.value }, "the tucked nub stays as it was")
            }
        }
        return leans
    }

    @Test
    fun `f - a tucked dock does not lean on another conversation's request`() {
        val leans = alertLean(tucked = true)
        assertTrue(leans.all { it == 0f }, "no lean while tucked: $leans")
    }

    @Test
    fun `f - a dock that is out still leans on another conversation's request`() {
        val leans = alertLean(tucked = false)
        assertTrue(leans.any { it > 5f }, "the out dock leans about 7 dp: $leans")
    }
}
