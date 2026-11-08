package io.github.eslamasabry.oc.ui.design.effects

import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.color.ThemeResolver
import io.github.eslamasabry.oc.ui.design.color.over
import io.github.eslamasabry.oc.ui.design.color.wcagContrast
import io.github.eslamasabry.oc.ui.design.style.Style
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Liquid glass (owner 2026-11-08) under the Glass style: when it is held (the rows of B-KIT-018: Saver, reduced
 * motion, high contrast; plus no runtime shaders and frost off), that only Glass ever draws it (B-KIT-037: every
 * other style is untouched), and that its words keep AA over any content (B-KIT-003's floors).
 * Catalog: these extend B-KIT-018/037/003; the test-writer lead assigns the liquid glass rows.
 */
class LiquidGlassTest {
    private val on = LiquidConditions(style = Style.Glass, runtimeShaders = true)

    @Test
    fun `B-KIT-018 Glass on Android 13 or later with nothing held is liquid glass`() {
        assertNull(LiquidGlass.holdOf(on))
        assertTrue(LiquidGlass.isLiquid(on))
    }

    @Test
    fun `B-KIT-018 below Android 13 the refraction cannot run and today's frost or solid face stays`() {
        assertEquals(LiquidHold.Platform, LiquidGlass.holdOf(on.copy(runtimeShaders = false)))
    }

    @Test
    fun `B-KIT-018 Saver, reduced motion and high contrast hold liquid glass as they hold frost`() {
        assertEquals(LiquidHold.Saver, LiquidGlass.holdOf(on.copy(saver = true)))
        assertEquals(LiquidHold.Motion, LiquidGlass.holdOf(on.copy(reducedMotion = true)))
        assertEquals(LiquidHold.Contrast, LiquidGlass.holdOf(on.copy(highContrast = true)))
        // Frost's own order: Saver first, then Motion, then Contrast.
        assertEquals(LiquidHold.Saver, LiquidGlass.holdOf(on.copy(saver = true, reducedMotion = true, highContrast = true)))
        assertEquals(LiquidHold.Motion, LiquidGlass.holdOf(on.copy(reducedMotion = true, highContrast = true)))
    }

    @Test
    fun `B-KIT-018 frost turned off keeps the glass off too`() {
        assertEquals(LiquidHold.Off, LiquidGlass.holdOf(on.copy(frostOff = true)))
    }

    @Test
    fun `B-KIT-018 liquid glass is held exactly where FrostPolicy holds frost`() {
        val flags = listOf(false, true)
        for (style in Style.entries) for (saver in flags) for (motion in flags) for (contrast in flags) for (shaders in flags) {
            val c = LiquidConditions(style, shaders, saver, motion, contrast)
            val frost = FrostPolicy.heldBy(FrostConditions(saver, motion, contrast, styleAllows = style == Style.Glass))
            val hold = LiquidGlass.holdOf(c)
            if (frost != null) assertEquals(frost.name, hold?.name, "$c")
            else assertEquals(if (shaders) null else LiquidHold.Platform, hold, "$c")
        }
    }

    @Test
    fun `B-KIT-037 only Glass is ever liquid glass`() {
        for (style in Style.entries.filter { it != Style.Glass }) {
            assertEquals(LiquidHold.Style, LiquidGlass.holdOf(LiquidConditions(style, runtimeShaders = true)), "$style")
        }
    }

    @Test
    fun `B-KIT-003 the backdrop filter is the library's colour controls matrix`() {
        val r = LiquidGlass.recipe(LiquidRole.Bar, dark = true)
        // White: contrast × 1 + (0.5 − contrast/2 + brightness); black: the shift alone, clamped at 0.
        val white = LiquidGlass.filtered(Argb(0xFFFFFFFF.toInt()), r)
        val expected = ((r.contrast + .5f - r.contrast / 2f + r.brightness).coerceIn(0f, 1f) * 255f)
        assertEquals(expected, white.red.toFloat(), 1f)
        assertEquals(white.red, white.green)
        val black = LiquidGlass.filtered(Argb(0xFF000000.toInt()), r)
        assertEquals(((.5f - r.contrast / 2f + r.brightness).coerceIn(0f, 1f) * 255f), black.red.toFloat(), 1f)
    }

    @Test
    fun `B-KIT-003 every pane's words read at 4_5 to 1 over any content, in every pack, light and dark`() {
        for (pack in PackId.entries) for (dark in listOf(true, false)) {
            val roles = ThemeResolver.roles(pack, null, dark, saver = false, dynamic = null)
            for (role in LiquidRole.entries) {
                val recipe = LiquidGlass.recipe(role, dark)
                val alpha = LiquidGlass.tintAlpha(roles, role, recipe)
                val face = LiquidGlass.tintOf(roles, role).withAlpha(alpha)
                // Every colour a page can show (the RGB cube's corners, and a few in between) through the filter.
                val spots = cube().map { LiquidGlass.filtered(it, recipe) }
                for ((ink, target) in LiquidGlass.inks(roles, role)) {
                    for (under in spots) {
                        val seen = face.over(under)
                        assertTrue(
                            wcagContrast(ink, seen) >= target - 1e-6,
                            "$pack dark=$dark $role: $ink on $seen is ${wcagContrast(ink, seen)} < $target (tint $alpha)",
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `B-KIT-003 the words on a pane are held to AA, never more than Solid itself reaches`() {
        val roles = ThemeResolver.roles(PackId.Opencode, null, dark = true, saver = false, dynamic = null)
        val inks = LiquidGlass.inks(roles, LiquidRole.Sheet)
        assertEquals(listOf(roles.text1, roles.text2), inks.map { it.first })
        inks.forEach { (_, target) -> assertTrue(target <= LiquidGlass.TEXT_FLOOR) }
        val action = LiquidGlass.inks(roles, LiquidRole.Action).single()
        assertEquals(roles.onAccent, action.first)
        assertTrue(action.second <= LiquidGlass.GLYPH_FLOOR)
    }

    @Test
    fun `B-KIT-003 Graphite's glass stays glass, not a solid sheet`() {
        for (dark in listOf(true, false)) {
            val roles = ThemeResolver.roles(PackId.Opencode, null, dark, saver = false, dynamic = null)
            for (role in listOf(LiquidRole.Bar, LiquidRole.Composer, LiquidRole.Sheet, LiquidRole.Menu, LiquidRole.Floating)) {
                val alpha = LiquidGlass.tintAlpha(roles, role)
                assertTrue(alpha in LiquidGlass.MIN_TINT..0.62f, "dark=$dark $role tint $alpha")
            }
        }
    }

    @Test
    fun `B-KIT-003 the worst contrast at the chosen tint meets the target`() {
        val roles = ThemeResolver.roles(PackId.Opencode, null, dark = false, saver = false, dynamic = null)
        val alpha = LiquidGlass.tintAlpha(roles, LiquidRole.Bar)
        val worst = LiquidGlass.worstContrast(roles, LiquidRole.Bar, alpha)
        assertTrue(worst >= LiquidGlass.inks(roles, LiquidRole.Bar).minOf { it.second } - 1e-6, "worst $worst")
    }

    private fun cube(): List<Argb> {
        val steps = listOf(0, 64, 128, 192, 255)
        return steps.flatMap { r -> steps.flatMap { g -> steps.map { b -> Argb((0xFF shl 24) or (r shl 16) or (g shl 8) or b) } } }
    }
}
