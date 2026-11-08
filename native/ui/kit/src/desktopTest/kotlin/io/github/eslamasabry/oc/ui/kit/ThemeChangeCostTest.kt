package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.motion.ThemeSwitchTimings
import io.github.eslamasabry.oc.ui.kit.theme.KitThemeMetrics
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private var probeRuns = 0
private var readerRuns = 0

/** A part that reads no theme value: a theme change must not recompose it (P-116). */
@Composable
private fun Probe() {
    probeRuns++
    Box(Modifier.size(4.dp))
}

@Composable
private fun Reader() {
    readerRuns++
    val ground = LocalKitColors.current.ground
    Box(Modifier.size(4.dp).background(ground))
}

/**
 * Step 0 of the premium theme switch (docs/designs/theme-switch-premium.md, P-116): a theme change used to recompose
 * every part under the theme (static locals), which made the first frame in the new colours cost ~95 ms. These fail on
 * the code before the fix. The frame time itself is measured on the phone (JankStats / Perfetto) by the coordinator.
 */
@OptIn(ExperimentalTestApi::class)
class ThemeChangeCostTest {
    // ---- Step 0 ----

    @Test
    fun `P-116 a theme change recomposes only the parts that read the theme, and Material settles once idle`() =
        runComposeUiTest {
            var mode by mutableStateOf(AppearanceMode.Dark)
            var material = Color.Unspecified
            probeRuns = 0
            readerRuns = 0
            val platform = FakeKitPlatform()
            setContent {
                KitTheme(KitAppearance(mode = mode, motion = MotionLevel.Full), platform, "en") {
                    Column {
                        Probe()
                        Reader()
                        material = MaterialTheme.colorScheme.background
                    }
                }
            }
            waitForIdle()
            val probeBefore = probeRuns
            val readerBefore = readerRuns
            val darkGround = material
            mainClock.autoAdvance = false
            mode = AppearanceMode.Light
            repeat(4) { mainClock.advanceTimeByFrame() }
            assertEquals(probeBefore, probeRuns, "a part that reads no colour is not recomposed by the theme change")
            assertTrue(readerRuns > readerBefore, "a part that reads the colours is")
            assertEquals(darkGround, material, "Material's scheme waits for the motion to finish")
            mainClock.advanceTimeBy(ThemeSwitchTimings.IDLE_MS.toLong())
            mainClock.advanceTimeByFrame()
            assertTrue(material != darkGround, "then it settles to the new theme")
        }

    @Test
    fun `P-116 the theme-change frame time is recorded for the coordinator's device run`() = runComposeUiTest {
        var mode by mutableStateOf(AppearanceMode.Dark)
        val seen = mutableListOf<Float>()
        val platform = FakeKitPlatform()
        KitThemeMetrics.listener = { seen += it }
        try {
            setContent {
                KitTheme(KitAppearance(mode = mode, motion = MotionLevel.Full), platform, "en") {
                    Box(Modifier.size(64.dp))
                }
            }
            waitForIdle()
            mode = AppearanceMode.Light
            waitForIdle()
            assertEquals(1, seen.size)
            assertNotNull(KitThemeMetrics.lastChangeFrameMs)
        } finally {
            KitThemeMetrics.listener = null
        }
    }
}
