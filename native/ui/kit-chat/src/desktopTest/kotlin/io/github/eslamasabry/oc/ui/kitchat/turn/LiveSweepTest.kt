package io.github.eslamasabry.oc.ui.kitchat.turn

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.layout
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.height
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.motion.MotionTokens
import io.github.eslamasabry.oc.ui.kit.motion.KitMotionState
import io.github.eslamasabry.oc.ui.kitchat.composer.MotionTheme
import io.github.eslamasabry.oc.ui.kitchat.composer.rememberLoopPhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Plan 08 §5.15 `KitTurnLive` sweep (Flutter `_LiveSweep`): a progress loop — 2200 ms a pass under Full, half speed
 * under Calm, still words under Off and while the agent waits; draw-only (the words never recompose or re-lay out).
 */
@OptIn(ExperimentalTestApi::class)
class LiveSweepTest {
    @Test
    fun `the light travels from before the words to past them, reversed in RTL`() {
        assertEquals(-0.3f, liveSweepCentre(0f, rtl = false), 1e-5f)
        assertEquals(1.3f, liveSweepCentre(1f, rtl = false), 1e-5f)
        assertEquals(0.5f, liveSweepCentre(0.5f, rtl = false), 1e-5f)
        assertEquals(1.3f, liveSweepCentre(0f, rtl = true), 1e-5f)
        assertEquals(-0.3f, liveSweepCentre(1f, rtl = true), 1e-5f)
    }

    @Test
    fun `M8-6 the sweep is a progress loop - full speed, half under Calm, still under Off`() {
        assertEquals(1f, liveSweepSpeed(KitMotionState(MotionLevel.Full)))
        assertEquals(0.5f, liveSweepSpeed(KitMotionState(MotionLevel.Calm)))
        assertEquals(0f, liveSweepSpeed(KitMotionState(MotionLevel.Off)))
    }

    @Test
    fun `a pass takes 2200 ms under Full and 4400 ms under Calm`() =
        runComposeUiTest {
            mainClock.autoAdvance = false
            var level by mutableStateOf(MotionLevel.Full)
            var seen = 0f
            setContent {
                MotionTheme(level = level) {
                    val speed = if (level == MotionLevel.Calm) 0.5f else 1f
                    val phase = rememberLoopPhase(true, MotionTokens.LIVE_SWEEP_LAP, speed, still = 0f)
                    Box(Modifier.size(10.dp).drawBehind { seen = phase.floatValue })
                }
            }
            mainClock.advanceTimeByFrame()
            val start = seen
            mainClock.advanceTimeBy(1104) // 69 frames of 16 ms ≈ half a pass
            assertEquals(0.5f, (seen - start + 1f) % 1f, 0.02f)
            level = MotionLevel.Calm
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeByFrame()
            val calmStart = seen
            mainClock.advanceTimeBy(1104)
            assertEquals(0.25f, (seen - calmStart + 1f) % 1f, 0.02f)
        }

    @Test
    fun `the words never recompose or re-lay out while the light sweeps`() =
        runComposeUiTest {
            mainClock.autoAdvance = false
            var compositions = 0
            var layouts = 0
            var draws = 0
            setContent {
                MotionTheme(level = MotionLevel.Full) {
                    KitLiveSweep(working = true) {
                        SideEffect { compositions++ }
                        Box(
                            Modifier.size(200.dp, 20.dp)
                                .layout { m, c -> layouts++; val p = m.measure(c); layout(p.width, p.height) { p.place(0, 0) } }
                                .drawBehind { draws++ },
                        )
                    }
                }
            }
            mainClock.advanceTimeByFrame()
            val (c0, l0, d0) = Triple(compositions, layouts, draws)
            repeat(120) { mainClock.advanceTimeByFrame() }
            assertEquals(c0, compositions)
            assertEquals(l0, layouts)
            assertTrue(draws - d0 >= 100, "the sweep draws each frame (${draws - d0})")
            onNodeWithTag(SWEEP_TAG).assertExists()
        }

    @Test
    fun `under Off, and while the agent waits for the person, the words are still and keep their state`() =
        runComposeUiTest {
            mainClock.autoAdvance = false
            var working by mutableStateOf(true)
            var level by mutableStateOf(MotionLevel.Off)
            var compositions = 0
            setContent {
                MotionTheme(level = level) {
                    KitLiveSweep(working = working) {
                        SideEffect { compositions++ }
                        Box(Modifier.size(200.dp, 20.dp))
                    }
                }
            }
            mainClock.advanceTimeByFrame()
            onNodeWithTag(SWEEP_TAG).assertDoesNotExist()
            level = MotionLevel.Full
            mainClock.advanceTimeByFrame()
            onNodeWithTag(SWEEP_TAG).assertExists()
            working = false
            mainClock.advanceTimeByFrame()
            onNodeWithTag(SWEEP_TAG).assertDoesNotExist()
            assertTrue(compositions <= 3, "the words stay in place across the switches ($compositions compositions)")
        }
}
