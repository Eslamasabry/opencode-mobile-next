package io.github.eslamasabry.oc.ui.kit.style

import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.Roles
import io.github.eslamasabry.oc.ui.design.color.argb
import io.github.eslamasabry.oc.ui.design.color.relativeLuminance
import io.github.eslamasabry.oc.ui.design.effects.KitCorners
import io.github.eslamasabry.oc.ui.design.effects.KitDepth
import io.github.eslamasabry.oc.ui.design.effects.KitEdges
import io.github.eslamasabry.oc.ui.design.effects.KitKnobs
import io.github.eslamasabry.oc.ui.design.effects.KitSurfaceStyle
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KitStylePaperTest {
    private val spec = KitStylePaper
    private val solid = KitSurfaceLook(radius = KitRadii.all(18f), fill = argb(0xFF808080))

    private fun paper(
        roles: Roles,
        role: KitSurfaceRole = KitSurfaceRole.Content,
        level: SurfaceLevel = SurfaceLevel.Surface1,
        knobs: KitKnobs? = null,
        color: Argb? = null,
        circle: Boolean = false,
        selected: Boolean = false,
        pressed: Boolean = false,
    ) = KitStyles.resolve(roles, KitSurfaceStyle.Paper, role, solid, knobs, level, circle = circle, color = color, selected = selected, pressed = pressed)

    @Test
    fun `B-KIT-044 tokens - sharp at 0, flat, ruled, kept under high contrast`() {
        val t = spec.tokens
        assertEquals(KitKnobs(corners = KitCorners.Sharp), t.defaults)
        assertEquals(0f, t.sharpRadius)
        assertTrue(t.keepsUnderHighContrast)
        assertTrue(t.live.offWhenChosen)
        assertFalse(t.glow.halo)
        assertEquals(1f, t.glow.lineWidth)
        assertEquals(34f, t.type.largeTitleSize)
        assertEquals(750, t.type.displayWeight)
    }

    @Test
    fun `B-KIT-044 warm paper a shade under white in light and near-black in dark, words at AA`() {
        for (roles in graphite) {
            val p = spec.adjustRoles(roles)
            assertEquals(p.ground, p.surface1)
            assertEquals(roles.surface2, p.surface2)
            assertEquals(roles.text1, p.text1)
            assertEquals(roles.accent, p.accent)
            assertEquals(roles.scrim, p.scrim)
            assertEquals(roles.text1.withAlpha(.2f), p.hairline)
            for (fill in listOf(p.ground, p.surface2, p.surface3)) {
                for (ink in listOf(p.text1, p.text2, p.text3)) assertTrue(ratio(ink, fill) >= 4.5)
            }
            assertTrue(ratio(p.hairline.on(p.ground), p.ground) > 1.3)
        }
        val light = spec.adjustRoles(graphiteLight).ground
        assertTrue(light.red > light.blue, "warm")
        assertTrue(relativeLuminance(light) < 1 && relativeLuminance(light) > .85)
        assertTrue(relativeLuminance(spec.adjustRoles(graphiteDark).ground) < .01)
    }

    @Test
    fun `B-KIT-044 no shadows on any role and panels sit on the page with no box`() {
        for (roles in graphite) {
            for (role in KitSurfaceRole.entries) {
                for (selected in listOf(false, true)) {
                    val d = paper(roles, role, selected = selected)
                    assertTrue(d.shadows.isEmpty() && d.hardOffset == 0f && d.distance == 0f && d.faceShift == 0f, "$role")
                }
            }
            val panel = paper(roles)
            assertEquals(0, panel.fill.alpha)
            assertEquals(0f, panel.edgeWidth)
            val prompt = paper(roles, level = SurfaceLevel.Surface2)
            assertEquals(roles.ground, prompt.fill)
            assertEquals(1f, prompt.edgeWidth)
            assertTrue(ratio(prompt.edgeStart.on(prompt.fill), prompt.fill) >= 3)
        }
    }

    @Test
    fun `B-KIT-044 controls and floating pieces are opaque ink boxes at 3 to 1, the accent needs no box`() {
        for (roles in graphite) {
            for (role in listOf(KitSurfaceRole.Control, KitSurfaceRole.Track, KitSurfaceRole.Floating, KitSurfaceRole.Sheet)) {
                val d = paper(roles, role)
                assertEquals(1f, d.edgeWidth, "$role")
                assertEquals(roles.text1.withAlpha(if (roles.dark) .6f else .9f), d.edgeStart)
                assertEquals(255, d.fill.alpha)
                assertTrue(ratio(d.edgeStart.on(d.fill), d.fill) >= 3, "$role")
            }
            val accent = paper(roles, KitSurfaceRole.Control, color = roles.accent)
            assertEquals(roles.accent, accent.fill)
            assertEquals(0f, accent.edgeWidth)
        }
    }

    @Test
    fun `B-KIT-044 chosen controls invert to ink with paper words at AA and chosen cards are boxed`() {
        for (roles in graphite) {
            for (color in listOf(null, roles.accent)) {
                val d = paper(roles, KitSurfaceRole.Control, color = color, selected = true)
                val ink = KitStylePaper.invertedInk(roles)
                assertEquals(ink, d.fill)
                assertEquals(ink, d.edgeStart, "one block, no ring")
                val words = spec.onColor(KitSurfaceRequest(roles, KitSurfaceRole.Control, solid, spec.tokens.defaults, color = color, selected = true))
                assertEquals(roles.ground.withAlpha(1f), words)
                assertTrue(ratio(words!!, d.fill) >= 4.5)
            }
            val pressedRow = paper(roles, pressed = true)
            assertEquals(roles.surface3, pressedRow.fill)
            assertEquals(0f, pressedRow.edgeWidth)
        }
        assertEquals(KitStylePaper.CHOSEN_RULE, paper(graphiteLight, selected = true).edgeWidth)
        fun words(color: Argb? = null, selected: Boolean = false, role: KitSurfaceRole = KitSurfaceRole.Control) =
            spec.onColor(KitSurfaceRequest(graphiteLight, role, solid, spec.tokens.defaults, color = color, selected = selected))
        assertNull(words(color = graphiteLight.accent))
        assertNull(words())
        assertNull(words(selected = true, role = KitSurfaceRole.Content))
    }

    @Test
    fun `B-KIT-044 hover and press differ visibly and the press keeps every word at AA`() {
        for (roles in graphite) {
            val p = spec.adjustRoles(roles)
            val hover = paper(p, KitSurfaceRole.StateLayer).fill.on(p.ground)
            val held = paper(p, KitSurfaceRole.StateLayer, pressed = true).fill.on(p.ground)
            assertTrue(ratio(held, p.ground) > 1.15)
            assertTrue(ratio(held, p.ground) - 1 > (ratio(hover, p.ground) - 1) * 2)
            for (ink in listOf(p.text1, p.text2, p.text3)) assertTrue(ratio(ink, held) >= 4.5)
        }
    }

    @Test
    fun `B-KIT-044 square corners while circles stay circles, the thumb and main action squared`() {
        assertEquals(KitRadii.Zero, paper(graphiteLight, KitSurfaceRole.Control).radius)
        assertTrue(paper(graphiteLight, KitSurfaceRole.Control, circle = true).circle)
        for (roles in graphite) {
            val thumb = paper(roles, KitSurfaceRole.Control, SurfaceLevel.Surface2, color = roles.text2, circle = true)
            assertFalse(thumb.circle)
            assertEquals(KitRadii.Zero, thumb.radius)
            val track = paper(roles, KitSurfaceRole.Toggle)
            assertFalse(track.circle)
            assertEquals(KitRadii.Zero, track.radius)
            val send = paper(roles, KitSurfaceRole.Control, SurfaceLevel.Surface3, color = roles.accent, circle = true)
            assertFalse(send.circle)
            assertEquals(roles.accent, send.fill)
            assertTrue(paper(roles, KitSurfaceRole.Control, SurfaceLevel.Surface3, color = roles.text1, circle = true).circle, "stop stays round")
            assertTrue(paper(roles, KitSurfaceRole.Control, SurfaceLevel.Surface3, circle = true).circle)
            assertTrue(paper(roles, KitSurfaceRole.Control, SurfaceLevel.Surface3, KitKnobs(), roles.accent, circle = true).circle)
            assertTrue(paper(roles, KitSurfaceRole.Control, SurfaceLevel.Surface2, KitKnobs(), circle = true).circle)
        }
        for (corners in listOf(KitCorners.Round, KitCorners.Soft)) {
            assertEquals(KitRadii.all(18f), paper(graphiteLight, KitSurfaceRole.Control, knobs = KitKnobs(corners = corners)).radius)
        }
    }

    @Test
    fun `B-KIT-044 rules, code, fields and needs-you`() {
        for (roles in graphite) {
            assertEquals(roles.text1.withAlpha(KitStylePaper.RULE_ALPHA), paper(roles, KitSurfaceRole.Divider).fill)
            val section = paper(roles, KitSurfaceRole.Divider, selected = true)
            assertEquals(roles.text1.withAlpha(.9f), section.fill)
            assertEquals(1f, section.edgeWidth)
            val off = paper(roles, KitSurfaceRole.Divider, knobs = KitKnobs(KitCorners.Sharp, edges = KitEdges.None), selected = true)
            assertEquals(0, off.fill.alpha)
            val code = paper(roles, KitSurfaceRole.Code)
            assertEquals(1f, code.edgeWidth)
            assertEquals(roles.text1.withAlpha(KitStylePaper.boxAlpha(roles)), code.edgeStart)
            assertEquals(255, code.fill.alpha)
            for (focused in listOf(false, true)) {
                val d = paper(roles, KitSurfaceRole.Field, selected = focused)
                assertEquals(0, d.fill.alpha)
                assertEquals(if (focused) KitStylePaper.CHOSEN_RULE else 1f, d.edgeWidth)
                assertTrue(ratio(d.edgeStart.on(roles.ground), roles.ground) >= 3)
            }
        }
        val look = spec.tokens.needsYou.decide(halo = spec.tokens.glow.halo)
        assertEquals(KitNeedsYouDecision(tint = false, line = false, ring = false, startEdge = true, pill = false), look)
    }

    @Test
    fun `B-KIT-044 knobs - edges off, raised floating only, high contrast`() {
        val off = KitKnobs(KitCorners.Sharp, edges = KitEdges.None)
        for (roles in graphite) {
            assertEquals(0f, paper(roles, KitSurfaceRole.Sheet, knobs = off).edgeWidth)
            val prompt = paper(roles, level = SurfaceLevel.Surface2, knobs = off)
            assertEquals(0f, prompt.edgeWidth)
            assertEquals(roles.surface3, prompt.fill)
            for (role in listOf(KitSurfaceRole.Control, KitSurfaceRole.Track, KitSurfaceRole.Floating)) {
                assertEquals(1f, paper(roles, role, knobs = off).edgeWidth)
            }
        }
        val raised = KitKnobs(KitCorners.Sharp, KitDepth.Raised)
        for (role in KitSurfaceRole.entries) {
            assertEquals(if (role == KitSurfaceRole.Floating) 1 else 0, paper(graphiteLight, role, knobs = raised).shadows.size, "$role")
        }
    }

    @Test
    fun `B-KIT-044 nothing directional - the same rule at the start and the end`() {
        for (role in KitSurfaceRole.entries) {
            val d = paper(graphiteDark, role, selected = true)
            assertEquals(d.edgeStart, d.edgeEnd, "$role")
            assertEquals(d.fill, d.fillStart, "$role")
            assertEquals(0f, d.hardOffset)
        }
    }
}
