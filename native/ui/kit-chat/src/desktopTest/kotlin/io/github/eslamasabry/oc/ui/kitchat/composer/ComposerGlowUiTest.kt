package io.github.eslamasabry.oc.ui.kitchat.composer

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
import io.github.eslamasabry.oc.ui.kit.platform.KitSystemSignals
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The glow in a real composition (plan 08 §5.3, §9.3): the choice and the levels, the 450 ms fade, and the frame
 * budget — the pill under the glow never recomposes or re-lays out (`RecompositionBudgetTest`), still glows request no
 * frames, and the clock parks once a faded glow is gone.
 */
@OptIn(ExperimentalTestApi::class)
class ComposerGlowUiTest {
    private class Counts {
        var compositions = 0
        var layouts = 0
        var draws = 0
    }

    private fun pill(c: Counts): Modifier =
        Modifier.size(320.dp, 56.dp)
            .layout { m, k -> c.layouts++; val p = m.measure(k); layout(p.width, p.height) { p.place(0, 0) } }
            .drawBehind { c.draws++ }

    @Test
    fun `B-CHAT-006 a running reply shows the glow by default and a glow switched off shows none`() =
        runComposeUiTest {
            var choice by mutableStateOf(KitGlowChoice.of())
            setContent {
                MotionTheme(stillFrame = true) {
                    KitComposerGlow(active = true, choice = choice) { Box(Modifier.size(320.dp, 56.dp)) }
                }
            }
            onNodeWithTag(GLOW_TAG).assertExists()
            choice = KitGlowChoice.Soft
            waitForIdle()
            onNodeWithTag(GLOW_TAG).assertExists()
            choice = KitGlowChoice.Off
            mainClock.advanceTimeBy(600)
            onNodeWithTag(GLOW_TAG).assertDoesNotExist()
        }

    @Test
    fun `MOT-7 under Off nothing glows, and Saver draws its one still hairline`() =
        runComposeUiTest {
            var saver by mutableStateOf(false)
            setContent {
                MotionTheme(level = MotionLevel.Off, saver = saver) {
                    KitComposerGlow(active = true) { Box(Modifier.size(320.dp, 56.dp)) }
                }
            }
            onNodeWithTag(GLOW_TAG).assertDoesNotExist()
            saver = true
            waitForIdle()
            onNodeWithTag(GLOW_TAG).assertExists()
        }

    @Test
    fun `RecompositionBudget the pill never recomposes or re-lays out during 3 s of a travelling glow`() =
        runComposeUiTest {
            mainClock.autoAdvance = false
            val c = Counts()
            setContent {
                MotionTheme(level = MotionLevel.Full) {
                    KitComposerGlow(active = true) {
                        SideEffect { c.compositions++ }
                        Box(pill(c))
                    }
                }
            }
            mainClock.advanceTimeByFrame()
            val (comp0, lay0, draw0) = Triple(c.compositions, c.layouts, c.draws)
            repeat(180) { mainClock.advanceTimeByFrame() } // 3 s at the test clock's 16 ms frames
            assertEquals(comp0, c.compositions, "the pill recomposed for the glow")
            assertEquals(lay0, c.layouts, "the pill re-laid out for the glow")
            assertTrue(c.draws - draw0 >= 150, "the glow redraws every frame while it travels (${c.draws - draw0})")
        }

    @Test
    fun `X6 the glow fades out over 450 ms, then leaves and requests no more frames`() =
        runComposeUiTest {
            mainClock.autoAdvance = false
            val c = Counts()
            var active by mutableStateOf(true)
            setContent {
                MotionTheme(level = MotionLevel.Full) { KitComposerGlow(active = active) { Box(pill(c)) } }
            }
            repeat(10) { mainClock.advanceTimeByFrame() }
            active = false
            mainClock.advanceTimeBy(300)
            onNodeWithTag(GLOW_TAG).assertExists() // still fading
            mainClock.advanceTimeBy(300)
            onNodeWithTag(GLOW_TAG).assertDoesNotExist()
            val parked = c.draws
            repeat(60) { mainClock.advanceTimeByFrame() }
            assertEquals(parked, c.draws, "no frames once the glow is gone")
        }

    @Test
    fun `M8-6 under Calm the glow fades in and then stands still - no frames while it is lit`() =
        runComposeUiTest {
            mainClock.autoAdvance = false
            val c = Counts()
            var active by mutableStateOf(false)
            setContent {
                MotionTheme(level = MotionLevel.Calm) { KitComposerGlow(active = active) { Box(pill(c)) } }
            }
            mainClock.advanceTimeByFrame()
            val before = c.draws
            active = true
            mainClock.advanceTimeBy(500)
            assertTrue(c.draws - before >= 10, "the fade draws frames (${c.draws - before})")
            val lit = c.draws
            repeat(120) { mainClock.advanceTimeByFrame() }
            assertEquals(lit, c.draws, "a still Calm glow requests no frames")
            onNodeWithTag(GLOW_TAG).assertExists()
        }

    @Test
    fun `MOT-13 battery saver turns the travelling glow into a still one`() =
        runComposeUiTest {
            mainClock.autoAdvance = false
            val c = Counts()
            val platform = RecordingPlatform(KitSystemSignals(powerSave = true))
            setContent {
                MotionTheme(level = MotionLevel.Full, platform = platform) { KitComposerGlow(active = true) { Box(pill(c)) } }
            }
            repeat(3) { mainClock.advanceTimeByFrame() }
            val settled = c.draws
            repeat(120) { mainClock.advanceTimeByFrame() }
            assertEquals(settled, c.draws, "battery saver: Calm, so nothing travels")
            onNodeWithTag(GLOW_TAG).assertExists()
        }

    @Test
    fun `R8 a reply already running when the chat opens is lit from the first frame with no fade`() =
        runComposeUiTest {
            mainClock.autoAdvance = false
            val c = Counts()
            setContent {
                MotionTheme(level = MotionLevel.Calm) { KitComposerGlow(active = true) { Box(pill(c)) } }
            }
            repeat(2) { mainClock.advanceTimeByFrame() }
            val first = c.draws
            repeat(40) { mainClock.advanceTimeByFrame() }
            assertEquals(first, c.draws, "no fade-in for a glow that was there when the screen opened")
        }
}
