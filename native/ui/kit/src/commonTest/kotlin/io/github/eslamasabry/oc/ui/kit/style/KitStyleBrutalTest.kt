package io.github.eslamasabry.oc.ui.kit.style

import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.Roles
import io.github.eslamasabry.oc.ui.design.color.withAccent
import io.github.eslamasabry.oc.ui.design.effects.KitCorners
import io.github.eslamasabry.oc.ui.design.effects.KitDepth
import io.github.eslamasabry.oc.ui.design.effects.KitEdges
import io.github.eslamasabry.oc.ui.design.effects.KitKnobs
import io.github.eslamasabry.oc.ui.design.effects.KitSurfaceStyle
import io.github.eslamasabry.oc.ui.design.tokens.Density
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class KitStyleBrutalTest {
    private val spec = KitStyleBrutal
    private val panel = KitRadii.all(12f)
    private val defaults = KitKnobs(corners = KitCorners.Soft, depth = KitDepth.Raised)

    private fun look(
        roles: Roles,
        role: KitSurfaceRole = KitSurfaceRole.Content,
        level: SurfaceLevel = SurfaceLevel.Surface1,
        knobs: KitKnobs = defaults,
        radius: KitRadii = panel,
        color: Argb? = null,
        selected: Boolean = false,
        pressed: Boolean = false,
        disabled: Boolean = false,
        withinGroup: Boolean = false,
    ) = spec.resolve(
        KitSurfaceRequest(
            roles, role, KitSurfaceLook(radius = radius, fill = roles.surface1), knobs, level, radius, color = color,
            selected = selected, pressed = pressed, disabled = disabled, withinGroup = withinGroup,
        ),
    )

    @Test
    fun `B-KIT-043 tokens - soft corners, raised, the ink edge on, kept under high contrast`() {
        val t = spec.tokens
        assertEquals(KitKnobs(KitCorners.Soft, KitDepth.Raised, Density.Comfortable, KitEdges.Hairline), t.defaults)
        assertTrue(t.keepsUnderHighContrast)
        assertTrue(t.flattensNested)
        assertEquals(.7f, t.live.cap(1f))
        assertFalse(t.glow.halo)
        assertEquals(2f, t.glow.lineWidth)
        assertEquals(800, t.type.displayWeight)
        assertTrue(t.type.monoLabels)
        assertSame(KitStyleBrutal, KitStyles.spec(KitSurfaceStyle.Brutal))
    }

    @Test
    fun `B-KIT-043 paper ground in light, near-black in dark, words unchanged`() {
        val light = spec.adjustRoles(graphiteLight)
        assertTrue(light.ground.red - light.ground.blue > 10, "warm")
        assertEquals(light.surface1, light.surface2)
        assertTrue(ratio(light.surface1, light.ground) > 1.0)
        assertEquals(graphiteLight.text1, light.text1)
        assertEquals(graphiteLight.accent, light.accent)
        val dark = spec.adjustRoles(graphiteDark)
        assertEquals(graphiteDark.ground, dark.ground)
        assertEquals(graphiteDark.surface2, dark.surface1)
        for ((name, base) in everyTheme()) {
            val roles = spec.adjustRoles(base).withAccent(base.accent)
            for (fill in listOf(roles.ground, roles.surface1, roles.surface2, roles.surface3)) {
                for (ink in listOf(roles.text1, roles.text2, roles.text3, roles.attention)) {
                    val before = listOf(base.ground, base.surface1, base.surface2, base.surface3).minOf { ratio(ink, it) }
                    assertTrue(ratio(ink, fill) >= if (before < 4.5) before - .05 else 4.5, name)
                }
            }
        }
    }

    @Test
    fun `B-KIT-043 the ink edge of 2 dp and the hard shadow per role`() {
        for (roles in graphite) {
            for (role in KitSurfaceRole.panes) {
                val d = look(roles, role)
                assertEquals(KitStyleBrutal.EDGE, d.edgeWidth, "$role")
                assertEquals(roles.text1, d.edgeStart, "$role")
                assertEquals(KitStyleBrutal.offsetOf(role), d.hardOffset, "$role")
                assertTrue(d.shadows.isEmpty() && d.blur == 0f)
            }
            assertEquals(listOf(4f, 3f, 6f, 0f), listOf(KitSurfaceRole.Content, KitSurfaceRole.Control, KitSurfaceRole.Floating, KitSurfaceRole.Track).map { look(roles, it).hardOffset })
            for (role in KitSurfaceRole.entries - KitSurfaceRole.panes.toSet()) {
                val d = look(roles, role)
                val boxed = role == KitSurfaceRole.Field || role == KitSurfaceRole.Toggle || role == KitSurfaceRole.Code
                assertEquals(if (boxed) KitStyleBrutal.EDGE else 0f, d.edgeWidth, "$role")
                assertFalse(d.casts, "$role")
            }
            // A bottom sheet sits on the scrim; a dialog casts.
            assertEquals(0f, look(roles, KitSurfaceRole.Sheet, radius = KitRadii.top(20f)).hardOffset)
            assertEquals(4f, look(roles, KitSurfaceRole.Sheet).hardOffset)
        }
        assertEquals(graphiteLight.text1, look(graphiteLight).hardShadow)
        val dark = look(graphiteDark).hardShadow
        assertEquals(255, dark.alpha)
        assertNotEquals(graphiteDark.text3, dark)
        assertTrue(ratio(dark, graphiteDark.ground) > 2.5)
        assertTrue(ratio(dark, graphiteDark.ground) < ratio(graphiteDark.text3, graphiteDark.ground))
        // The page at the ground step is the paper itself.
        val page = KitSurfaceLook(fill = graphiteLight.ground)
        assertSame(page, spec.resolve(KitSurfaceRequest(graphiteLight, KitSurfaceRole.Content, page, spec.tokens.defaults, SurfaceLevel.Ground)))
    }

    @Test
    fun `B-KIT-043 controls rest on paper, chosen sit down, pressed sink into the shadow`() {
        for (roles in graphite) {
            assertEquals(roles.surface2, look(roles, KitSurfaceRole.Control, SurfaceLevel.Surface3).fill)
            val chosen = look(roles, KitSurfaceRole.Control, SurfaceLevel.Surface3, selected = true)
            assertEquals(roles.surface3, chosen.fill)
            assertEquals(0f, chosen.hardOffset)
            assertEquals(0f, chosen.faceShift)
            val primary = look(roles, KitSurfaceRole.Control, color = roles.accent)
            assertEquals(KitStyleBrutal.mintOf(roles), primary.fill)
            assertEquals(3f, primary.hardOffset)
            assertEquals(roles.attentionFill, look(roles, KitSurfaceRole.Control, color = roles.attentionFill).fill)
        }
        for (role in listOf(KitSurfaceRole.Content, KitSurfaceRole.Control, KitSurfaceRole.Floating)) {
            val d = look(graphiteLight, role, pressed = true)
            assertEquals(KitStyleBrutal.offsetOf(role), d.faceShift, "$role")
            assertEquals(0f, d.hardOffset)
        }
    }

    @Test
    fun `B-KIT-043 dark chosen cards trade the edge for the accent at 3 to 1`() {
        for ((name, raw) in everyTheme()) {
            if (!raw.dark) continue
            val roles = spec.adjustRoles(raw)
            for (role in listOf(KitSurfaceRole.Content, KitSurfaceRole.Control)) {
                assertEquals(roles.text1, look(roles, role, SurfaceLevel.Surface2).edgeStart, name)
                val chosen = look(roles, role, SurfaceLevel.Surface2, selected = true)
                assertEquals(roles.accent, chosen.edgeStart, name)
                assertEquals(roles.accent, chosen.edgeEnd, name)
                for (under in listOf(chosen.fill, roles.ground)) assertTrue(ratio(roles.accent, under) >= 3, "$name $role")
                val off = look(roles, role, SurfaceLevel.Surface2, defaults.copy(edges = KitEdges.None), selected = true)
                assertEquals(roles.accent, off.edgeStart, name)
                assertEquals(KitStyleBrutal.EDGE, off.edgeWidth, name)
            }
            assertEquals(roles.text1, look(roles, KitSurfaceRole.Control, color = roles.accent, selected = true).edgeStart, name)
        }
        assertEquals(graphiteLight.text1, look(spec.adjustRoles(graphiteLight), KitSurfaceRole.Control, selected = true).edgeStart)
    }

    @Test
    fun `B-KIT-043 needs-you is an amber pill with no wash and dividers rule between groups`() {
        val d = spec.tokens.needsYou.decide(halo = false)
        assertEquals(KitNeedsYouDecision(tint = false, line = false, ring = false, startEdge = false, pill = true), d)
        val q = spec.tokens.needsYou.decide(halo = false, question = true)
        assertTrue(q.pill)
        assertFalse(q.startEdge)
        for (roles in graphite) {
            val pill = look(roles, KitSurfaceRole.Control, color = roles.attentionFill)
            assertEquals(roles.attentionFill, pill.fill)
            assertEquals(roles.text1, pill.edgeStart)
            assertEquals(roles.text1.withAlpha(.9f), look(roles, KitSurfaceRole.Divider).fill)
            assertEquals(0, look(roles, KitSurfaceRole.Divider, withinGroup = true).fill.alpha)
        }
    }

    @Test
    fun `B-KIT-043 a focused field rings its ink box in 3 dp of accent`() {
        for (roles in graphite) {
            assertTrue(look(roles, KitSurfaceRole.Field).shadows.isEmpty())
            val focused = look(roles, KitSurfaceRole.Field, selected = true)
            assertEquals(roles.text1, focused.edgeStart)
            assertEquals(KitStyleBrutal.EDGE, focused.edgeWidth)
            assertEquals(KitShadow(roles.accent, spread = KitStyleBrutal.FOCUS_RING), focused.shadows.single())
            assertEquals(0f, focused.hardOffset)
        }
    }

    @Test
    fun `B-KIT-043 the main action is bright mint with ink words at AA`() {
        val mint = KitStyleBrutal.mintOf(graphiteLight)
        assertNotEquals(graphiteLight.accent, mint)
        assertTrue(ratio(graphiteLight.text1, mint) >= 7)
        assertTrue(abs(Hsl.of(mint).hue - Hsl.of(graphiteLight.accent).hue) <= 2)
        assertTrue(abs(Hsl.of(mint).lightness - .55) <= .05)
        fun words(roles: Roles, color: Argb?, selected: Boolean = false) =
            spec.onColor(KitSurfaceRequest(roles, KitSurfaceRole.Control, KitSurfaceLook.Bare, spec.tokens.defaults, color = color, selected = selected))
        assertEquals(graphiteLight.text1, words(graphiteLight, graphiteLight.accent))
        assertNull(words(graphiteLight, null))
        assertEquals(graphiteLight.accent, look(graphiteLight, KitSurfaceRole.Control, color = graphiteLight.accent, selected = true).fill)
        assertNull(words(graphiteLight, graphiteLight.accent, selected = true))
        assertEquals(graphiteDark.accent, KitStyleBrutal.mintOf(graphiteDark))
        assertNull(words(graphiteDark, graphiteDark.accent))
        for ((name, raw) in everyTheme()) {
            val roles = spec.adjustRoles(raw)
            val face = KitStyleBrutal.mintOf(roles)
            assertTrue(ratio(words(roles, roles.accent) ?: roles.onAccent, face) >= 4.5, name)
        }
    }

    @Test
    fun `B-KIT-043 disabled controls lie flat in a muted edge and a held primary keeps its colour`() {
        for (roles in graphite) {
            for (pressed in listOf(false, true)) {
                val off = look(roles, KitSurfaceRole.Control, SurfaceLevel.Surface3, disabled = true, pressed = pressed)
                assertEquals(roles.surface3, off.fill)
                assertEquals(0f, off.hardOffset)
                assertEquals(0f, off.faceShift)
                assertEquals(roles.text3, off.edgeStart)
                assertTrue(ratio(roles.text3, off.fill) > 4.5)
            }
            val quiet = roles.accent.withAlpha(.14f)
            val hero = look(roles, KitSurfaceRole.Control, color = quiet, disabled = true)
            assertEquals(quiet, hero.fill)
            assertEquals(0f, hero.hardOffset)
        }
        fun layer(depth: KitDepth) = spec.resolveFilledLayer(
            KitSurfaceRequest(
                graphiteLight, KitSurfaceRole.StateLayer, KitSurfaceLook.Bare, KitKnobs(KitCorners.Soft, depth),
                color = graphiteLight.accent, pressed = true,
            ),
        )
        assertEquals(0, layer(KitDepth.Raised).fill.alpha)
        val flat = layer(KitDepth.Flat).fill
        assertTrue(abs(flat.alpha / 255.0 - .12) < .01)
        assertTrue(ratio(graphiteLight.text1, flat.on(KitStyleBrutal.mintOf(graphiteLight))) >= 4.5)
    }

    @Test
    fun `B-KIT-043 knobs - flat depth, edges off and compact adjust the shadows`() {
        val flat = KitKnobs(KitCorners.Soft, KitDepth.Flat)
        for (role in KitSurfaceRole.panes) {
            val rest = look(graphiteLight, role, knobs = flat)
            assertEquals(0f, rest.hardOffset, "$role")
            assertEquals(KitStyleBrutal.EDGE, rest.edgeWidth, "$role")
            val pressed = look(graphiteLight, role, knobs = flat, pressed = true)
            assertEquals(0f, pressed.faceShift)
            assertEquals(graphiteLight.surface3, pressed.fill)
        }
        val off = defaults.copy(edges = KitEdges.None)
        val panel = look(graphiteLight, knobs = off)
        assertEquals(0f, panel.edgeWidth)
        assertEquals(4f, panel.hardOffset)
        for (role in listOf(KitSurfaceRole.Control, KitSurfaceRole.Track)) {
            val d = look(graphiteLight, role, knobs = off)
            assertEquals(KitStyleBrutal.EDGE, d.edgeWidth)
            assertTrue(ratio(d.edgeStart, d.fill) >= 3)
        }
        val compact = defaults.copy(density = Density.Compact)
        for (role in KitSurfaceRole.entries) {
            val d = look(graphiteLight, role, knobs = compact)
            assertEquals(KitStyleBrutal.compactOffsetOf(role), d.hardOffset, "$role")
            assertTrue(d.hardOffset <= KitStyleBrutal.offsetOf(role))
        }
        assertEquals(3f, look(graphiteLight, knobs = compact).hardOffset)
    }
}
