@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.choice

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.GlassLookId
import io.github.eslamasabry.oc.ui.design.style.GlassLookPreset
import io.github.eslamasabry.oc.ui.design.style.GlassLooks
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.effects.kitGlassLook
import io.github.eslamasabry.oc.ui.kit.effects.kitClearPanesOn
import io.github.eslamasabry.oc.ui.kit.effects.kitLiquidGlassOn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Glass look picker (docs/designs/glass-looks-2026-11-09.md §4-§5): each look applies to the whole kit, a preview's
 * look stays on its own pane, the field steps at most 60 times a second (45 on 90 Hz), rests when idle, stops when the
 * picker closes and holds still under reduced motion, and each preview is a radio. Extends B-KIT-018 (frost's holds)
 * and B-KIT-003 (glass looks).
 */
class KitGlassLookPickerTest {
    private val options =
        GlassLooks.offered.map { KitGlassLookOption(it, it.name, "${it.name} line") }

    // ---- the field's clock (no display needed)

    private fun stepsInOneSecond(hz: Double): Int {
        val clock = LookFieldClock()
        val frame = (1e9 / hz).toLong()
        var t = 1_000_000_000L
        // Warm up the clock's reading of the panel rate.
        repeat(30) { clock.frame(t); t += frame }
        var steps = 0
        repeat(hz.toInt()) {
            if (clock.frame(t)) steps++
            t += frame
        }
        return steps
    }

    @Test
    fun `B-KIT-018 the field steps at most 60 times a second, 45 on a 90 Hz panel`() {
        assertEquals(1, LookFieldPolicy.divisor(60f))
        assertEquals(2, LookFieldPolicy.divisor(90f))
        assertEquals(2, LookFieldPolicy.divisor(120f))
        assertEquals(3, LookFieldPolicy.divisor(144f))
        assertTrue(stepsInOneSecond(60.0) in 58..61, "60 Hz: ${stepsInOneSecond(60.0)}")
        assertTrue(stepsInOneSecond(120.0) in 58..61, "120 Hz: ${stepsInOneSecond(120.0)}")
        assertTrue(stepsInOneSecond(90.0) in 43..46, "90 Hz: ${stepsInOneSecond(90.0)}")
    }

    @Test
    fun `B-KIT-018 the field rests 30 seconds after the last touch and wakes on the next`() {
        val clock = LookFieldClock()
        val frame = 16_666_667L
        var t = 0L
        var stepped = 0
        while (t < LookFieldPolicy.IDLE_AFTER_NANOS + 1_000_000_000L) {
            if (clock.frame(t)) stepped++
            t += frame
        }
        assertTrue(clock.resting, "rests after 30 s without a touch")
        assertFalse(clock.frame(t), "no steps at rest")
        clock.touch()
        assertFalse(clock.resting)
        assertTrue(clock.frame(t + frame), "a touch wakes it")
        assertTrue(stepped in 1700..1900, "it ran until then: $stepped")
    }

    // ---- the picker

    @Test
    fun `B-KIT-018 the picker's field moves while it shows and stops when it closes`() = runComposeUiTest {
        var shown by mutableStateOf(true)
        val field = KitLookFieldState()
        mainClock.autoAdvance = false
        setContent {
            KitTheme(appearance = KitAppearance(style = Style.Glass), platform = FakeKitPlatform(frost = true), localeTag = "en") {
                Box(Modifier.size(412.dp, 600.dp)) {
                    if (shown) KitGlassLookPicker(options, GlassLookId.Glossy, {}, "Bright on purpose.", field = field)
                }
            }
        }
        mainClock.advanceTimeBy(500)
        assertTrue(field.moving, "the field moves while the picker shows")
        val version = field.version
        mainClock.advanceTimeBy(500)
        assertTrue(field.version > version, "and records new frames")
        shown = false
        mainClock.advanceTimeBy(100)
        assertFalse(field.moving, "closed: the field stops")
        val closed = field.version
        mainClock.advanceTimeBy(1_000)
        assertEquals(closed, field.version, "no frames once closed")
    }

    @Test
    fun `B-KIT-018 under reduced motion the field is one still picture`() = runComposeUiTest {
        val field = KitLookFieldState()
        mainClock.autoAdvance = false
        setContent {
            KitTheme(appearance = KitAppearance(style = Style.Glass, motion = MotionLevel.Off), platform = FakeKitPlatform(frost = true), localeTag = "en") {
                Box(Modifier.size(412.dp, 600.dp)) { KitGlassLookPicker(options, GlassLookId.Glossy, {}, "Bright on purpose.", field = field) }
            }
        }
        mainClock.advanceTimeBy(500)
        assertFalse(field.moving)
        val still = field.version
        mainClock.advanceTimeBy(2_000)
        assertEquals(still, field.version, "a still field records nothing more")
    }

    @Test
    fun `B-KIT-003 each preview is a radio, the chosen one selected, and a tap picks its look with a select tick`() = runComposeUiTest {
        val platform = FakeKitPlatform(frost = true)
        var picked: GlassLookId? = null
        mainClock.autoAdvance = false
        setContent {
            KitTheme(appearance = KitAppearance(style = Style.Glass), platform = platform, localeTag = "en", stillFrame = true) {
                Box(Modifier.size(412.dp, 600.dp)) {
                    KitGlassLookPicker(options, GlassLookId.Glossy, { picked = it }, "Bright on purpose.")
                }
            }
        }
        mainClock.advanceTimeBy(300)
        val radios = onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).fetchSemanticsNodes()
        assertEquals(GlassLooks.offered.size, radios.size)
        val selected = radios.filter { it.config.getOrElse(SemanticsProperties.Selected) { false } }
        assertEquals(1, selected.size)
        onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))[0].performClick()
        mainClock.advanceTimeBy(300)
        assertTrue(picked != null && picked != GlassLookId.Glossy, "a tap picks that look: $picked")
        assertTrue(KitHaptic.Select in platform.haptics, "the select tick")
    }

    // ---- each look applies to the kit

    @Test
    fun `B-KIT-003 each look applies to the whole kit, and Glossy is the default`() {
        for (id in GlassLookId.entries) {
            var seen: GlassLookPreset? = null
            var liquid = false
            var clear = false
            runComposeUiTest {
                setContent {
                    KitTheme(appearance = KitAppearance(style = Style.Glass, glassLook = id, mode = AppearanceMode.Dark), platform = FakeKitPlatform(liquidGlass = true), localeTag = "en", stillFrame = true) {
                        seen = kitGlassLook()
                        liquid = kitLiquidGlassOn()
                        clear = kitClearPanesOn()
                    }
                }
                waitForIdle()
            }
            assertEquals(GlassLooks.of(id), seen, "$id reaches the kit")
            assertEquals(GlassLooks.of(id).lens, liquid, "$id: liquid edges only with a lens")
            assertTrue(clear, "$id: clear panes in every look (Frosted's are its own lighter ones, D7)")
        }
        var default: GlassLookPreset? = null
        runComposeUiTest {
            setContent { KitTheme(appearance = KitAppearance(style = Style.Glass), platform = FakeKitPlatform(), localeTag = "en", stillFrame = true) { default = kitGlassLook() } }
            waitForIdle()
        }
        assertEquals(GlassLooks.Glossy, default)
    }
}
