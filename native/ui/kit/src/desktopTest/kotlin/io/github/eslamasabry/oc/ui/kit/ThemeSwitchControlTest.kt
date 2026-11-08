package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.choice.KitModeSegment
import io.github.eslamasabry.oc.ui.kit.choice.KitModeSegmented
import io.github.eslamasabry.oc.ui.kit.choice.LocalModeSwitchProbe
import io.github.eslamasabry.oc.ui.kit.choice.ModeSwitchMotion
import io.github.eslamasabry.oc.ui.kit.icon.KitPixelIcons
import io.github.eslamasabry.oc.ui.kit.motion.ThemeSwitchTimings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The premium theme switch in the composed kit (docs/designs/theme-switch-premium.md, Step 0 and Verification › Kit
 * tests). Each test drives the clock by hand.
 */
@OptIn(ExperimentalTestApi::class)
class ThemeSwitchControlTest {
    private val modes = listOf(AppearanceMode.Light, AppearanceMode.Dark, AppearanceMode.System, AppearanceMode.Saver)
    private val words =
        mapOf(
            AppearanceMode.Light to "Light",
            AppearanceMode.Dark to "Dark",
            AppearanceMode.System to "Auto",
            AppearanceMode.Saver to "Saver",
        )
    private val segments = modes.map { KitModeSegment(it, words.getValue(it), KitPixelIcons.of(it)) }

    // ---- the control ----

    private class Harness {
        var selected by mutableStateOf(AppearanceMode.Dark)
        val commits = mutableListOf<Pair<AppearanceMode, Long>>()
        var motion: ModeSwitchMotion? = null
        val platform = FakeKitPlatform()
    }

    private fun ComposeUiTest.control(
        h: Harness,
        level: MotionLevel = MotionLevel.Full,
        style: Style = Style.Solid,
        locale: String = "en",
        follow: Boolean = true,
    ) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                KitTheme(KitAppearance(style = style, motion = level), h.platform, locale) {
                    InWindow(412, 300) {
                        CompositionLocalProvider(LocalModeSwitchProbe provides { h.motion = it }) {
                            KitModeSegmented(segments, h.selected, "Mode") {
                                h.commits += it to mainClock.currentTime
                                if (follow) h.selected = it
                            }
                        }
                    }
                }
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.block(): Rect =
        onNodeWithTag("kit-mode-block", useUnmergedTree = true).getBoundsInRoot().let {
            Rect(it.left.value, it.top.value, it.right.value, it.bottom.value)
        }

    private fun ComposeUiTest.segment(label: String): Rect =
        onNodeWithText(label, useUnmergedTree = true).getBoundsInRoot().let {
            Rect(it.left.value, it.top.value, it.right.value, it.bottom.value)
        }

    private fun ComposeUiTest.selectedIs(label: String) {
        val chosen = SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)
        onNode(chosen.and(androidx.compose.ui.test.hasText(label)), useUnmergedTree = false).assertExists()
    }

    private fun under(block: Rect, words: Rect): Boolean = words.center.x in block.left..block.right

    @Test
    fun `TSW-011 at rest the chosen block sits under its mode, in every style, both ways round`() {
        for (style in Style.entries) for (locale in listOf("en", "ar")) {
            runComposeUiTest {
                val h = Harness()
                control(h, style = style, locale = locale, level = MotionLevel.Off)
                for (m in modes) {
                    h.selected = m
                    waitForIdle()
                    val label = words.getValue(m)
                    selectedIs(label)
                    assertTrue(under(block(), segment(label)), "$style/$locale: block under $label")
                }
                // Mirrored: in Arabic the first mode is on the right.
                val first = segment("Light").center.x
                val last = segment("Saver").center.x
                assertEquals(locale == "ar", first > last, "$style/$locale order")
            }
        }
    }

    @Test
    fun `TSW-012 a tap slides, plays the rim and dots, ticks once, commits once the motion runs`() = runComposeUiTest {
        val h = Harness()
        control(h)
        val start = block()
        mainClock.autoAdvance = false
        onNodeWithText("Light", useUnmergedTree = true).performClick()
        mainClock.advanceTimeByFrame()
        assertEquals(listOf(KitHaptic.Select), h.platform.haptics, "one haptic tick")
        val m = assertNotNull(h.motion)
        assertTrue(m.pos.isRunning, "the block slides")
        assertTrue(m.rim.isRunning, "the rim runs")
        assertNotNull(m.morph, "the dots are in flight")
        mainClock.advanceTimeBy(ThemeSwitchTimings.COMMIT_MS - 40)
        assertTrue(h.commits.isEmpty(), "the theme waits for the motion to be under way")
        val mid = block()
        assertTrue(mid.left != start.left, "mid-slide the block has moved")
        mainClock.advanceTimeBy(80)
        assertEquals(listOf(AppearanceMode.Light), h.commits.map { it.first })
        mainClock.advanceTimeBy(ThemeSwitchTimings.IDLE_MS.toLong())
        assertTrue(!m.switching, "idle: every clock has stopped")
        assertTrue(under(block(), segment("Light")))
    }

    @Test
    fun `TSW-013 a second tap mid-move retargets from where it is and commits only the last`() = runComposeUiTest {
        val h = Harness()
        control(h)
        mainClock.autoAdvance = false
        // An injected click itself moves the test clock (~64 ms, down to up): the second one lands before the first
        // commit at 120 ms after the first tap.
        onNodeWithText("Light", useUnmergedTree = true).performClick()
        mainClock.advanceTimeBy(10)
        val m = assertNotNull(h.motion)
        onNodeWithText("Saver", useUnmergedTree = true).performClick()
        mainClock.advanceTimeByFrame()
        // Dark is segment 1, Light 0, Saver 3: the block was on its way to Light and turns from there.
        val p = m.pos.value
        assertTrue(p < 1f && p > -0.3f, "the slide turns from where it was ($p), no jump or reset")
        assertEquals(3f, m.pos.targetValue, "now heading for Saver")
        assertTrue(m.rim.value < 40f, "the rim restarts on the new target")
        mainClock.advanceTimeBy(ThemeSwitchTimings.IDLE_MS.toLong())
        assertEquals(listOf(AppearanceMode.Saver), h.commits.map { it.first }, "one commit, the last: ${h.commits}")
        assertTrue(under(block(), segment("Saver")))
        assertEquals(2, m.started)
    }

    @Test
    fun `TSW-014 a change that is not a tap shows the end state at once, no motion, no haptic`() = runComposeUiTest {
        val h = Harness()
        control(h)
        mainClock.autoAdvance = false
        h.selected = AppearanceMode.System
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        val m = assertNotNull(h.motion)
        assertTrue(!m.switching, "no slide, rim or dots")
        assertEquals(0, m.started)
        assertTrue(h.platform.haptics.isEmpty())
        assertTrue(h.commits.isEmpty())
        assertTrue(under(block(), segment("Auto")))
    }

    @Test
    fun `TSW-015 rapid taps flip the theme at most three times a second`() = runComposeUiTest {
        val h = Harness()
        control(h)
        mainClock.autoAdvance = false
        val order = listOf("Light", "Dark", "Auto", "Saver", "Light", "Dark", "Auto", "Saver", "Light", "Dark")
        for (label in order) {
            onNodeWithText(label, useUnmergedTree = true).performClick()
            mainClock.advanceTimeBy(150)
        }
        mainClock.advanceTimeBy(1000)
        val times = h.commits.map { it.second }
        assertTrue(times.isNotEmpty())
        times.zipWithNext().forEach { (a, b) ->
            assertTrue(b - a >= ThemeSwitchTimings.MIN_COMMIT_GAP_MS, "commits at $times")
        }
        assertEquals(AppearanceMode.Dark, h.commits.last().first, "the last tap wins")
    }

    @Test
    fun `TSW-016 Calm slides with a plain cross-fade, Off changes at once`() {
        runComposeUiTest {
            val h = Harness()
            control(h, level = MotionLevel.Calm)
            mainClock.autoAdvance = false
            onNodeWithText("Light", useUnmergedTree = true).performClick()
            mainClock.advanceTimeByFrame()
            val m = assertNotNull(h.motion)
            assertTrue(m.pos.isRunning, "Calm: the slide")
            assertTrue(!m.rim.isRunning && m.morph == null, "Calm: no rim, no dust")
            assertTrue(m.morphMs.isRunning, "Calm: the icons cross-fade")
        }
        runComposeUiTest {
            val h = Harness()
            control(h, level = MotionLevel.Off)
            mainClock.autoAdvance = false
            onNodeWithText("Light", useUnmergedTree = true).performClick()
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeByFrame()
            val m = assertNotNull(h.motion)
            assertTrue(!m.switching, "Off: nothing moves")
            assertEquals(listOf(AppearanceMode.Light), h.commits.map { it.first }, "Off: the theme changes at once")
            assertTrue(under(block(), segment("Light")))
        }
    }

    @Test
    fun `TSW-017 a tap on the chosen mode or with haptics off plays no tick`() = runComposeUiTest {
        val h = Harness()
        setContent {
            KitTheme(KitAppearance(motion = MotionLevel.Full, haptics = false), h.platform, "en") {
                KitModeSegmented(segments, h.selected, "Mode") { h.selected = it }
            }
        }
        waitForIdle()
        onNodeWithText("Dark", useUnmergedTree = true).performClick()
        onNodeWithText("Light", useUnmergedTree = true).performClick()
        waitForIdle()
        assertTrue(h.platform.haptics.isEmpty())
        assertEquals(AppearanceMode.Light, h.selected)
    }
}
