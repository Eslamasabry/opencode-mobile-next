package io.github.eslamasabry.oc.ui.kit

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.color.ThemeResolver
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.style.StyleResolver
import io.github.eslamasabry.oc.ui.kit.choice.IconParticles
import io.github.eslamasabry.oc.ui.kit.choice.ParticleMorph
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
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
 * and contrast across every theme, the motion-level matrix, the per-style rim table, the debounce, and the particle
 * morph sampled from the regular icons. Ids are TSW-NNN (theme switch); the catalog rows are proposed in the s33
 * report.
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
        assertEquals(0L, ThemeSwitchRules.commitAt(0L, null, plan), "the first tap commits at once; the motion waits for the new theme")
        assertEquals(0L, ThemeSwitchRules.commitAt(0L, null, ThemeSwitchRules.plan(MotionLevel.Off)), "Off: at once")
    }

    // ---- §2 the particle morph of the regular icons ----

    private val modeIcons = listOf(KitIcons.lightMode, KitIcons.darkMode, KitIcons.systemTheme, KitIcons.leaf)

    @Test
    fun `TSW-008 the particles are sampled from each regular icon's own rendered shape, finely and off-grid`() {
        val size = IconParticles.MASK_PX
        for (icon in modeIcons) {
            val mask = IconParticles.mask(icon, size)
            val pts = IconParticles.of(icon)
            assertEquals(IconParticles.COUNT * 2, pts.size, icon.name)
            assertTrue(IconParticles.COUNT >= 100, "many small particles, not a dozen-square grid")
            for (k in 0 until IconParticles.COUNT) {
                val x = (pts[2 * k] * size).toInt().coerceIn(0, size - 1)
                val y = (pts[2 * k + 1] * size).toInt().coerceIn(0, size - 1)
                // Every particle lies on the icon's ink (its own pixel or a neighbour, for the sub-pixel jitter).
                val near = (-1..1).flatMap { dy -> (-1..1).map { dx -> dx to dy } }.any { (dx, dy) ->
                    val px = (x + dx).coerceIn(0, size - 1)
                    val py = (y + dy).coerceIn(0, size - 1)
                    mask[py * size + px] > 0
                }
                assertTrue(near, "${icon.name}: particle $k at $x,$y is off the icon")
            }
            // Off-grid: the points do not sit on a coarse lattice (the old 12 x 12 pixel icons did).
            val lattice = (0 until IconParticles.COUNT).count { k ->
                val gx = pts[2 * k] * 12f
                abs(gx - gx.toInt() - 0.5f) < 0.02f
            }
            assertTrue(lattice < IconParticles.COUNT / 4, "${icon.name}: $lattice of the points on a 12-grid")
            assertTrue(IconParticles.of(icon).contentEquals(pts), "the sampling is fixed, not random per run")
        }
        val shapes = modeIcons.map { IconParticles.of(it).toList() }.toSet()
        assertEquals(modeIcons.size, shapes.size, "every icon gives its own cloud")
    }

    /** Whether point [i] of [a] (stride [sa]) and point [j] of [b] (stride [sb]) are within [tol] on both axes. */
    @Suppress("LongParameterList") // Two strided arrays, two indices and a tolerance.
    private fun at(a: FloatArray, sa: Int, i: Int, b: FloatArray, sb: Int, j: Int, tol: Float): Boolean =
        abs(a[i * sa] - b[j * sb]) < tol && abs(a[i * sa + 1] - b[j * sb + 1]) < tol

    private fun placed(icon: androidx.compose.ui.graphics.vector.ImageVector, x: Float, px: Float, alpha: Boolean) =
        IconParticles.of(icon).let { u ->
            val n = u.size / 2
            val stride = if (alpha) ParticleMorph.FROM_FLOATS else 2
            FloatArray(n * stride) { k ->
                val p = k / stride
                when (k % stride) {
                    0 -> x + u[2 * p] * px
                    1 -> u[2 * p + 1] * px
                    else -> 1f
                }
            }
        }

    @Test
    fun `TSW-009 the particles leave the old icon, drift, and land exactly on the new one`() {
        val px = 48f
        val from = placed(KitIcons.lightMode, 0f, px, alpha = true)
        val to = placed(KitIcons.darkMode, 120f, px, alpha = false)
        val morph = ParticleMorph(from, to, px)
        assertEquals(IconParticles.COUNT, morph.count)
        val start = morph.sample(0f)
        for (n in 0 until morph.count) {
            val onOld = (0 until morph.count).any { at(from, 3, it, start, 3, n, 0.01f) }
            assertTrue(onOld, "at the tap particle $n sits on the old icon")
        }
        val end = morph.sample(ParticleMorph.MORPH_MS * ParticleMorph.FADE_OUT_START)
        for (n in 0 until morph.count) {
            val onNew = (0 until morph.count).any { at(to, 2, it, end, 3, n, 0.01f) }
            assertTrue(onNew, "re-formed: particle $n is on the new icon")
        }
        val gone = morph.sample(ParticleMorph.MORPH_MS)
        assertTrue((0 until morph.count).all { gone[it * 3 + 2] < 0.01f }, "at the end the crisp icon takes over")
        // Mid-way the cloud has swelled out of either shape and dimmed a little (the soft fade).
        val mid = morph.sample(ParticleMorph.MORPH_MS / 2f)
        val off = (0 until morph.count).count { n -> (0 until morph.count).none { at(to, 2, it, mid, 3, n, 1f) } }
        assertTrue(off > morph.count / 2, "mid-way most particles are in flight ($off)")
        assertTrue((0 until morph.count).any { mid[it * 3 + 2] < 0.95f }, "in flight they fade a little")
        val again = morph.sample(ParticleMorph.MORPH_MS / 2f)
        assertTrue(mid.contentEquals(again), "the drift is fixed, not random per frame")
    }

    @Test
    fun `TSW-010 particles leave staggered, and an interruption carries on from where they are`() {
        val px = 48f
        val morph =
            ParticleMorph(
                placed(KitIcons.systemTheme, 0f, px, alpha = true),
                placed(KitIcons.leaf, 90f, px, alpha = false),
                px,
            )
        val start = morph.sample(0f)
        val early = morph.sample(ParticleMorph.MORPH_MS * 0.08f)
        val moved = (0 until morph.count).count { abs(early[it * 3] - start[it * 3]) > 1.5f }
        assertTrue(moved in 1 until morph.count, "staggered: $moved of ${morph.count} on their way")
        // A second tap mid-way: the new morph's first frame is exactly the old one's frame then.
        val t = ParticleMorph.MORPH_MS * 0.4f
        val now = morph.sample(t)
        val next = ParticleMorph(now, placed(KitIcons.lightMode, 0f, px, alpha = false), px).sample(0f)
        for (n in 0 until morph.count) {
            assertTrue((0 until morph.count).any { at(now, 3, it, next, 3, n, 0.01f) }, "no jump")
        }
    }
}
