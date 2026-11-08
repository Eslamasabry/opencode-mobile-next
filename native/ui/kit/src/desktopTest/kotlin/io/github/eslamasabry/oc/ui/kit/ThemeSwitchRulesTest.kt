package io.github.eslamasabry.oc.ui.kit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.color.ThemeResolver
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.style.StyleResolver
import io.github.eslamasabry.oc.ui.kit.icon.KitDotMorph
import io.github.eslamasabry.oc.ui.kit.icon.KitPixelIcons
import io.github.eslamasabry.oc.ui.kit.icon.MorphDot
import io.github.eslamasabry.oc.ui.kit.motion.ThemeRimKind
import io.github.eslamasabry.oc.ui.kit.motion.ThemeSwitchRules
import io.github.eslamasabry.oc.ui.kit.motion.ThemeSwitchTimings
import io.github.eslamasabry.oc.ui.kit.theme.KitHct
import io.github.eslamasabry.oc.ui.kit.theme.ThemeSwitchColorRules
import io.github.eslamasabry.oc.ui.kit.theme.toKitColors
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The pure rules of the premium theme switch (docs/designs/theme-switch-premium.md, Verification › Kit tests): colour
 * and contrast across every theme, the motion-level matrix, the per-style rim table, the debounce, the pixel icons and
 * the dot morph. Ids are TSW-NNN (theme switch); the catalog rows are proposed in the s33 report.
 */
class ThemeSwitchRulesTest {
    private fun near(a: Double, b: Double, tol: Double, what: String) = assertTrue(abs(a - b) <= tol, "$what: $a vs $b")

    private fun hueGap(a: Double, b: Double): Double = abs(((a - b + 540.0) % 360.0) - 180.0)

    // ---- §4 colour ----

    @Test
    fun `TSW-001 HCT matches Material's reference values and round-trips`() {
        val red = KitHct.of(Color(0xFFFF0000))
        near(red.hue, 27.41, 0.1, "red hue")
        near(red.chroma, 113.36, 0.2, "red chroma")
        near(red.tone, 53.23, 0.1, "red tone")
        val blue = KitHct.of(Color(0xFF0000FF))
        near(blue.hue, 282.79, 0.1, "blue hue")
        near(blue.tone, 32.30, 0.1, "blue tone")
        for (argb in listOf(0xFF3DDC8A, 0xFF1F6FEB, 0xFFAD2E99, 0xFF808080, 0xFFF1F2F4, 0xFF262B36)) {
            val c = Color(argb)
            val back = KitHct.of(c).toColor()
            for ((x, y) in listOf(c.red to back.red, c.green to back.green, c.blue to back.blue)) {
                assertTrue(abs(x - y) < 2.5f / 255f, "round trip of ${argb.toString(16)}: $c → $back")
            }
        }
    }

    @Test
    fun `TSW-002 the rim leans cool at night and warm by day, in the accent's family`() {
        val green = Color(0xFF3DDC8A)
        val g = KitHct.of(green)
        val night = KitHct.of(ThemeSwitchColorRules.rim(green, dark = true, grounds = listOf(Color(0xFF101114))))
        val day = KitHct.of(ThemeSwitchColorRules.rim(green, dark = false, grounds = listOf(Color.White)))
        val nightAnchor = ThemeSwitchColorRules.NIGHT_HUE
        val dayAnchor = ThemeSwitchColorRules.DAY_HUE
        assertTrue(hueGap(night.hue, nightAnchor) < hueGap(g.hue, nightAnchor), "night pulls cool")
        assertTrue(hueGap(day.hue, dayAnchor) < hueGap(g.hue, dayAnchor), "day pulls warm")
        assertTrue(hueGap(night.hue, g.hue) < 90.0 && hueGap(day.hue, g.hue) < 90.0, "still the accent's family")
        assertTrue(night.tone > day.tone, "bright on dark, deeper on light")
    }

    @Test
    fun `TSW-003 in every theme the rim reads on its ground and the chosen segment keeps 3 to 1`() {
        val failures = mutableListOf<String>()
        fun check(name: String, colors: io.github.eslamasabry.oc.ui.kit.theme.KitColors) {
            val step = if (colors.dark) colors.surface3 else colors.surface2
            val sw = ThemeSwitchColorRules.of(colors, colors.surface1, step)
            val ground = colors.ground.copy(alpha = 1f)
            val track = colors.surface1.compositeOver(ground)
            val stepOn = step.compositeOver(track)
            val rimTrack = ThemeSwitchColorRules.contrast(sw.rim, track)
            val rimGround = ThemeSwitchColorRules.contrast(sw.rim, ground)
            val ink = ThemeSwitchColorRules.contrast(sw.selectedInk, stepOn)
            if (rimTrack < 3.0 || rimGround < 3.0) {
                failures += "$name rim ${"%.2f".format(rimTrack)}/${"%.2f".format(rimGround)}"
            }
            if (ink < 3.0) failures += "$name chosen icon ${"%.2f".format(ink)}"
        }
        for (pack in PackId.entries) for (dark in listOf(false, true)) for (style in Style.entries) {
            val base = ThemeResolver.roles(pack, null, dark, saver = false, dynamic = null)
            val roles = StyleResolver.adjust(base, style)
            check("$pack/${if (dark) "dark" else "light"}/$style", roles.toKitColors())
        }
        for (pack in PackId.entries) {
            val saver = ThemeResolver.roles(pack, null, dark = true, saver = true, dynamic = null)
            check("$pack/saver", saver.toKitColors())
        }
        assertTrue(failures.isEmpty(), "below 3:1:\n" + failures.joinToString("\n"))
    }

    @Test
    fun `TSW-004 the selected icon keeps the accent where it already reads`() {
        val ink = Color(0xFF1F6FEB)
        assertEquals(ink, ThemeSwitchColorRules.readable(ink, dark = false, grounds = listOf(Color.White)))
    }

    // ---- §5 motion levels, §3 per-style rim ----

    @Test
    fun `TSW-005 Full plays everything, Calm slides and cross-fades, Off is instant`() {
        val full = ThemeSwitchRules.plan(MotionLevel.Full)
        assertTrue(full.slide && full.rim && full.dust && !full.crossfade)
        assertEquals(ThemeSwitchTimings.COMMIT_MS, full.commitDelayMs)
        val calm = ThemeSwitchRules.plan(MotionLevel.Calm)
        assertTrue(calm.slide && !calm.rim && !calm.dust && calm.crossfade)
        val off = ThemeSwitchRules.plan(MotionLevel.Off)
        assertFalse(off.slide || off.rim || off.dust || off.crossfade)
        assertEquals(0L, off.commitDelayMs)
    }

    @Test
    fun `TSW-006 each style has its own rim`() {
        assertEquals(ThemeRimKind.Glow, ThemeSwitchRules.rimFor(Style.Solid))
        assertEquals(ThemeRimKind.Glow, ThemeSwitchRules.rimFor(Style.Signal))
        assertEquals(ThemeRimKind.Glow, ThemeSwitchRules.rimFor(Style.Glass))
        assertEquals(ThemeRimKind.InnerGlow, ThemeSwitchRules.rimFor(Style.Soft))
        assertEquals(ThemeRimKind.HardSweep, ThemeSwitchRules.rimFor(Style.Brutal))
        assertEquals(ThemeRimKind.InkSweep, ThemeSwitchRules.rimFor(Style.Paper))
    }

    // ---- Accessibility: the debounce ----

    @Test
    fun `TSW-007 however fast the taps, the theme commits at most three times a second`() {
        val plan = ThemeSwitchRules.plan(MotionLevel.Full)
        for (period in listOf(50L, 130L, 200L, 260L, 400L)) {
            val taps = (0L until 3000L step period).toList()
            var last: Long? = null
            val commits = mutableListOf<Long>()
            taps.forEachIndexed { i, tap ->
                val at = ThemeSwitchRules.commitAt(tap, last, plan)
                // Each tap replaces the pending commit; it happens only when no newer tap comes first.
                val next = taps.getOrNull(i + 1) ?: Long.MAX_VALUE
                if (next > at) {
                    commits += at
                    last = at
                }
            }
            assertTrue(commits.isNotEmpty(), "period $period: the last choice always commits")
            commits.zipWithNext().forEach { (a, b) ->
                assertTrue(b - a >= ThemeSwitchTimings.MIN_COMMIT_GAP_MS, "period $period: $a → $b")
            }
            val worstSecond = commits.maxOf { start -> commits.count { it >= start && it < start + 1000 } }
            assertTrue(worstSecond <= 3, "period $period: $worstSecond commits in one second")
        }
        assertEquals(120L, ThemeSwitchRules.commitAt(0L, null, plan), "the first tap commits after the motion starts")
        assertEquals(0L, ThemeSwitchRules.commitAt(0L, null, ThemeSwitchRules.plan(MotionLevel.Off)), "Off: at once")
    }

    // ---- §2 pixel icons and the morph ----

    @Test
    fun `TSW-008 the four pixel icons are distinct 12 by 12 dot grids`() {
        val icons = listOf(KitPixelIcons.sun, KitPixelIcons.moon, KitPixelIcons.auto, KitPixelIcons.leaf)
        icons.forEach { i ->
            assertEquals(KitPixelIcons.GRID, i.size, i.name)
            assertTrue(i.dots.size in 30..90, "${i.name}: ${i.dots.size} dots")
            assertTrue(i.dots.all { it.x in 0f..12f && it.y in 0f..12f })
        }
        assertEquals(icons.size, icons.map { it.dots.toSet() }.toSet().size, "every icon is its own shape")
        // The sun is symmetric left to right; the moon is not.
        val sun = KitPixelIcons.sun.dots.toSet()
        assertTrue(sun.all { Offset(12f - it.x, it.y) in sun })
        assertFalse(KitPixelIcons.moon.dots.toSet().let { m -> m.all { Offset(12f - it.x, it.y) in m } })
    }

    @Test
    fun `TSW-009 the dots leave the old icon and land exactly on the new one`() {
        val cell = 2f
        val from = KitPixelIcons.sun.dots.map { MorphDot(it.x * cell, it.y * cell) }
        val to = KitPixelIcons.moon.dots.map { Offset(100f + it.x * cell, it.y * cell) }
        val morph = KitDotMorph(from, to, cell)
        val start = morph.sample(0f)
        val visibleStart = start.filter { it.alpha > 0.5f }
        assertEquals(from.size, visibleStart.size, "at the tap only the old icon shows")
        assertTrue(visibleStart.all { d -> from.any { abs(it.x - d.x) < 0.01f && abs(it.y - d.y) < 0.01f } })
        val end = morph.sample(KitDotMorph.MORPH_MS)
        val landed = end.filter { it.alpha > 0.99f }
        assertEquals(to.size, landed.size, "every slot of the new icon is filled")
        assertTrue(landed.all { d -> to.any { abs(it.x - d.x) < 0.01f && abs(it.y - d.y) < 0.01f } })
        assertTrue(end.filter { it.alpha <= 0.99f }.all { it.alpha < 0.01f }, "the extra dots have faded out")
        val mid = morph.sample(KitDotMorph.MORPH_MS / 2f)
        val inFlight = mid.any { d -> to.none { abs(it.x - d.x) < 0.5f && abs(it.y - d.y) < 0.5f } }
        assertTrue(inFlight, "mid-way the dots are in flight")
        assertEquals(mid, morph.sample(KitDotMorph.MORPH_MS / 2f), "the jitter is fixed, not random per frame")
    }

    @Test
    fun `TSW-010 the morph matches each slot once and staggers the dots`() {
        val cell = 2f
        val morph =
            KitDotMorph(
                KitPixelIcons.auto.dots.map { MorphDot(it.x * cell, it.y * cell) },
                KitPixelIcons.leaf.dots.map { Offset(60f + it.x * cell, it.y * cell) },
                cell,
            )
        val sourcesUsed = morph.sourceOf.filter { it >= 0 }
        assertEquals(sourcesUsed.size, sourcesUsed.toSet().size, "no dot is in two slots")
        assertEquals(minOf(KitPixelIcons.auto.dots.size, KitPixelIcons.leaf.dots.size), sourcesUsed.size)
        // Early on, some dots have left and some wait: the stagger.
        val early = morph.sample(KitDotMorph.STAGGER_MS / 2f).take(morph.slots)
        val start = morph.sample(0f).take(morph.slots)
        val moved = early.zip(start).count { (a, b) -> abs(a.x - b.x) > 0.5f }
        assertTrue(moved in 1 until morph.slots, "staggered: $moved of ${morph.slots} moving")
    }
}
