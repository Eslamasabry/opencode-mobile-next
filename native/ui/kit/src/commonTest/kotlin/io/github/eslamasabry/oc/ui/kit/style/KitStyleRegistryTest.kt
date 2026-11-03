package io.github.eslamasabry.oc.ui.kit.style

import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.lerp
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitCorners
import io.github.eslamasabry.oc.ui.design.effects.KitDepth
import io.github.eslamasabry.oc.ui.design.effects.KitEdges
import io.github.eslamasabry.oc.ui.design.effects.KitFineTune
import io.github.eslamasabry.oc.ui.design.effects.KitKnobs
import io.github.eslamasabry.oc.ui.design.effects.KitSurfaceStyle
import io.github.eslamasabry.oc.ui.design.tokens.Density
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class KitStyleRegistryTest {
    private fun resolve(
        roles: io.github.eslamasabry.oc.ui.design.color.Roles,
        style: KitSurfaceStyle,
        role: KitSurfaceRole = KitSurfaceRole.Content,
        knobs: KitKnobs? = null,
        selected: Boolean = false,
        pressed: Boolean = false,
        solid: KitSurfaceLook = solidPanel,
    ) = KitStyles.resolve(roles, style, role, solid, knobs, selected = selected, pressed = pressed)

    @Test
    fun `B-KIT-037 five styles ship, Glass and Crystal retired and Clay dropped`() {
        assertEquals(listOf("solid", "soft", "brutal", "paper", "signal"), KitSurfaceStyle.entries.map { it.storageName })
        for (gone in listOf("glass", "crystal", "clay", null, "")) assertEquals(KitSurfaceStyle.Solid, KitSurfaceStyle.fromStorage(gone))
        assertEquals(KitSurfaceStyle.Signal, KitSurfaceStyle.fromStorage("signal"))
    }

    @Test
    fun `B-KIT-037 each style has its contract defaults`() {
        fun d(s: KitSurfaceStyle) = KitStyles.spec(s).tokens.defaults
        assertEquals(KitKnobs(), d(KitSurfaceStyle.Solid))
        assertEquals(KitKnobs(depth = KitDepth.Raised, edges = KitEdges.None), d(KitSurfaceStyle.Soft))
        assertEquals(KitKnobs(corners = KitCorners.Soft, depth = KitDepth.Raised), d(KitSurfaceStyle.Brutal))
        assertEquals(KitKnobs(corners = KitCorners.Sharp), d(KitSurfaceStyle.Paper))
        assertEquals(0f, KitStyles.spec(KitSurfaceStyle.Paper).tokens.sharpRadius)
        assertEquals(KitKnobs(corners = KitCorners.Sharp), d(KitSurfaceStyle.Signal))
        assertEquals(4f, KitStyles.spec(KitSurfaceStyle.Signal).tokens.sharpRadius)
    }

    @Test
    fun `B-KIT-037 Solid at its defaults leaves every part's own look untouched`() {
        for (roles in graphite) {
            for (role in KitSurfaceRole.entries) {
                assertSame(solidPanel, resolve(roles, KitSurfaceStyle.Solid, role, selected = true), "$role")
                assertSame(solidPanel, resolve(roles, KitSurfaceStyle.Solid, role, pressed = true), "$role")
            }
            // Solid keeps the theme's own roles (hook H6): the same object.
            assertSame(roles, KitStyles.restyled(roles, KitSurfaceStyle.Solid))
        }
        assertTrue(KitStyleState.Solid.plain)
        assertEquals(KitStyleState.Solid, KitStyleState.of(KitAppearance.Default, highContrast = false))
    }

    @Test
    fun `B-KIT-037 Solid honours its knobs once they move`() {
        val raised = resolve(graphiteDark, KitSurfaceStyle.Solid, knobs = KitKnobs(depth = KitDepth.Raised))
        assertEquals(1, raised.shadows.size)
        val bordered = solidPanel.copy(edgeStart = graphiteDark.hairline, edgeEnd = graphiteDark.hairline, edgeWidth = 1f)
        val stripped = resolve(graphiteDark, KitSurfaceStyle.Solid, knobs = KitKnobs(edges = KitEdges.None), solid = bordered)
        assertEquals(0f, stripped.edgeWidth)
    }

    @Test
    fun `B-KIT-037 null choices take the style's defaults and Soft without depth keeps an edge`() {
        assertEquals(KitKnobs(corners = KitCorners.Soft, depth = KitDepth.Raised), KitStyles.knobsFor(KitSurfaceStyle.Brutal))
        assertEquals(KitCorners.Sharp, KitStyles.knobsFor(KitSurfaceStyle.Brutal, KitFineTune(corners = KitCorners.Sharp)).corners)
        assertEquals(KitEdges.Hairline, KitStyles.knobsFor(KitSurfaceStyle.Soft, KitFineTune(depth = KitDepth.Flat)).edges)
    }

    @Test
    fun `B-KIT-037 corners - Soft takes about 60 percent, Sharp the style's sharp radius`() {
        assertEquals(18f, KitStyles.corner(18f, KitCorners.Round))
        assertEquals(11f, KitStyles.corner(18f, KitCorners.Soft))
        assertEquals(2f, KitStyles.corner(18f, KitCorners.Sharp))
        assertEquals(0f, KitStyles.corner(18f, KitCorners.Sharp, sharp = 0f))
        assertEquals(9999f, KitStyles.corner(9999f, KitCorners.Soft))
    }

    @Test
    fun `B-KIT-037 Saver draws Solid with no Fine-tune choices`() {
        val tuned = KitAppearance(mode = AppearanceMode.Saver, style = KitSurfaceStyle.Brutal, corners = KitCorners.Round)
        assertEquals(KitSurfaceStyle.Solid, tuned.surfaceStyle)
        assertTrue(tuned.fineTune.isNone)
        assertEquals(KitStyleState.Solid, KitStyleState.of(tuned, highContrast = false))
        val chosen = KitAppearance(style = KitSurfaceStyle.Brutal, corners = KitCorners.Sharp, density = Density.Compact)
        assertEquals(KitFineTune(corners = KitCorners.Sharp, density = Density.Compact), chosen.fineTune)
    }

    @Test
    fun `B-KIT-039 high contrast turns Soft into Solid and keeps Brutal, Paper and Signal flat with edges`() {
        for (style in KitSurfaceStyle.entries) {
            val keeps = style == KitSurfaceStyle.Brutal || style == KitSurfaceStyle.Paper || style == KitSurfaceStyle.Signal
            assertEquals(if (keeps) style else KitSurfaceStyle.Solid, KitStyles.effectiveStyle(style, highContrast = true))
            val knobs = KitStyles.knobsFor(style, highContrast = true)
            assertEquals(KitDepth.Flat, knobs.depth, "$style")
            assertEquals(KitEdges.Hairline, knobs.edges, "$style")
        }
        // Paper keeps its edges on and depth flat whatever the person chose.
        val paper = KitStyles.knobsFor(KitSurfaceStyle.Paper, KitFineTune(depth = KitDepth.Raised, edges = KitEdges.None), true)
        assertEquals(KitDepth.Flat, paper.depth)
        assertEquals(KitEdges.Hairline, paper.edges)
        // Brutal under high contrast: no shadow, its ink edge still reads at 3:1 on the ground.
        val brutal = KitStyles.resolve(
            graphiteLight, KitSurfaceStyle.Brutal, KitSurfaceRole.Content, solidPanel, KitStyles.knobsFor(KitSurfaceStyle.Brutal, highContrast = true),
        )
        assertEquals(0f, brutal.hardOffset)
        assertTrue(ratio(brutal.edgeStart, graphiteLight.ground) >= 3)
        assertEquals(KitSurfaceStyle.Solid, KitStyleState.of(KitAppearance(style = KitSurfaceStyle.Soft), highContrast = true).style)
        assertEquals(KitSurfaceStyle.Paper, KitStyleState.of(KitAppearance(style = KitSurfaceStyle.Paper), highContrast = true).style)
    }

    @Test
    fun `B-KIT-017 Fine-tune shows a style's own knobs with the choices laid over`() {
        val r = KitStyles.fineTuneRead(KitSurfaceStyle.Brutal, KitFineTune(density = Density.Compact))
        assertEquals(KitSurfaceStyle.Brutal, r.style)
        assertEquals(KitCorners.Soft, r.defaults.corners)
        assertEquals(Density.Compact, r.knobs.density)
        assertTrue(r.differs)
        assertFalse(r.edgesHeldByDepth)
    }

    @Test
    fun `B-KIT-017 a choice equal to the style's own does not differ`() {
        assertFalse(KitStyles.fineTuneRead(KitSurfaceStyle.Paper, KitFineTune(corners = KitCorners.Sharp)).differs)
        assertFalse(KitStyles.fineTuneRead(KitSurfaceStyle.Solid, KitFineTune.None).differs)
    }

    @Test
    fun `B-KIT-017 flat Soft holds its edges and high contrast holds depth and edges`() {
        val soft = KitStyles.fineTuneRead(KitSurfaceStyle.Soft, KitFineTune(depth = KitDepth.Flat))
        assertTrue(soft.edgesHeldByDepth)
        assertEquals(KitEdges.Hairline, soft.knobs.edges)
        val contrast = KitStyles.fineTuneRead(KitSurfaceStyle.Solid, KitFineTune(depth = KitDepth.Raised), highContrast = true)
        assertTrue(contrast.heldByContrast)
        assertEquals(KitDepth.Flat, contrast.knobs.depth)
    }

    @Test
    fun `B-KIT-046 a style may move the page ground and Solid keeps the theme's own`() {
        for (roles in graphite) {
            assertSame(roles, KitStyles.restyled(roles, KitSurfaceStyle.Solid))
            for (style in KitSurfaceStyle.entries - KitSurfaceStyle.Solid) {
                val moved = KitStyles.restyled(roles, style)
                // Dark Brutal keeps the near-black ground and moves its cards instead.
                if (style == KitSurfaceStyle.Brutal && roles.dark) assertNotEquals(roles.surface1, moved.surface1)
                else assertNotEquals(roles.ground, moved.ground, "$style ${roles.dark}")
                // Words are the theme's own; only the ground and its steps move.
                assertEquals(roles.text1, moved.text1, "$style")
            }
        }
        // Dark Brutal keeps the near-black ground; cards take the second step.
        val darkBrutal = KitStyleBrutal.adjustRoles(graphiteDark)
        assertEquals(graphiteDark.ground, darkBrutal.ground)
        assertEquals(graphiteDark.surface2, darkBrutal.surface1)
    }

    @Test
    fun `B-KIT-046 a style change fades shadow, edge and fill smoothly to and from Solid`() {
        for (roles in graphite) {
            val solid = KitSurfaceLook(radius = KitRadii.all(12f), fill = roles.surface1)
            val brutal = KitStyleBrutal.resolve(
                KitSurfaceRequest(roles, KitSurfaceRole.Content, solid, KitStyleBrutal.tokens.defaults, radius = KitRadii.all(12f)),
            )
            assertEquals(brutal, KitSurfaceLook.lerp(solid, brutal, 1f))
            assertEquals(brutal, KitSurfaceLook.lerp(brutal, solid, 0f))
            assertEquals(solid, KitSurfaceLook.lerp(brutal, solid, 1f))
            // Halfway the shadow is still hard: no blur appears in between, and the fill is between the two.
            val half = KitSurfaceLook.lerp(brutal, solid, .5f)
            assertEquals(0f, half.blur)
            assertTrue(half.shadows.isEmpty())
            assertEquals(brutal.hardOffset, half.hardOffset, "the offset holds while its colour fades")
            assertEquals(lerp(brutal.fill, solid.fill, .5f), half.fill)
            assertTrue(half.edgeWidth > 0f && half.edgeWidth < KitStyleBrutal.EDGE)
        }
        // Paper's square box and Solid's 18 dp corners meet at 9 dp halfway.
        val paper = KitStyles.resolve(graphiteLight, KitSurfaceStyle.Paper, KitSurfaceRole.Control, solidPanel)
        val back = KitSurfaceLook.lerp(paper, solidPanel, 1f)
        assertEquals(solidPanel.radius, back.radius)
        assertEquals(KitRadii.all(9f), KitSurfaceLook.lerp(solidPanel, paper, .5f).radius)
        // Soft fades its fill from Solid's, both ways alike.
        val soft = KitStyles.resolve(graphiteDark, KitSurfaceStyle.Soft, KitSurfaceRole.Content, solidPanel)
        val mid = KitSurfaceLook.lerp(solidPanel, soft, .5f)
        assertEquals(lerp(solidPanel.fill, soft.fill, .5f), mid.fill)
        assertEquals(mid.fill, KitSurfaceLook.lerp(soft, solidPanel, .5f).fill)
    }

    @Test
    fun `B-KIT-046 rest to pressed fades the shadow out as the face sinks`() {
        fun look(pressed: Boolean) = KitStyleBrutal.resolve(
            KitSurfaceRequest(
                graphiteLight, KitSurfaceRole.Content, solidPanel, KitStyleBrutal.tokens.defaults,
                radius = KitRadii.all(12f), pressed = pressed,
            ),
        )
        val rest = look(false)
        val pressed = look(true)
        val half = KitSurfaceLook.lerp(rest, pressed, .5f)
        assertEquals(2f, half.faceShift, .001f)
        assertEquals(pressed, KitSurfaceLook.lerp(rest, pressed, 1f))
        assertEquals(rest, KitSurfaceLook.lerp(rest, pressed, 0f))
        assertTrue(half.hardShadow.alpha < rest.hardShadow.alpha)
    }

    @Test
    fun `B-KIT-048 needs you follows the glow by default and is decided apart from it`() {
        val look = KitNeedsYouLook()
        val loud = look.decide(halo = true)
        assertEquals(KitNeedsYouDecision(tint = true, line = true, ring = true, startEdge = false, pill = false), loud)
        val calm = look.decide(halo = false)
        assertEquals(listOf(false, false, false, true), listOf(calm.tint, calm.line, calm.ring, calm.startEdge))
        val question = look.decide(halo = true, question = true)
        assertEquals(listOf(false, true, false, true), listOf(question.tint, question.line, question.ring, question.startEdge))
        val solid = KitStyles.spec(KitSurfaceStyle.Solid).tokens.needsYou
        assertEquals(KitNeedsYouTag.Caption, solid.tag)
        assertNull(solid.ring)
        val s = KitNeedsYouLook(tint = false, ring = false).decide(halo = true)
        assertEquals(listOf(false, false, true), listOf(s.tint, s.ring, s.startEdge))
        val b = KitNeedsYouLook(tag = KitNeedsYouTag.Pill, startEdge = false).decide(halo = false)
        assertEquals(listOf(false, false, false, true), listOf(b.tint, b.ring, b.startEdge, b.pill))
    }

    @Test
    fun `B-KIT-048 dividers - the real specs answer within and between groups alike, but Brutal`() {
        for (style in KitSurfaceStyle.entries) {
            for (roles in graphite) {
                fun ask(within: Boolean) =
                    KitStyles.resolve(roles, style, KitSurfaceRole.Divider, KitSurfaceLook(fill = roles.hairline), withinGroup = within)
                if (style == KitSurfaceStyle.Brutal) {
                    assertNotEquals(ask(true), ask(false))
                    assertEquals(0, ask(true).fill.alpha)
                } else {
                    assertEquals(ask(true), ask(false), "$style")
                }
            }
        }
        fun line(style: KitSurfaceStyle) =
            KitStyles.divider(graphiteDark, style, KitStyles.spec(style).tokens.defaults, graphiteDark.hairline, .5f)
        val ink = graphiteDark.text1
        assertEquals(KitStyles.Line(graphiteDark.hairline, .5f), line(KitSurfaceStyle.Solid))
        assertEquals(0, line(KitSurfaceStyle.Soft).color.alpha)
        assertEquals(KitStyles.Line(ink.withAlpha(.9f), .5f), line(KitSurfaceStyle.Brutal))
        assertEquals(KitStyles.Line(ink.withAlpha(.2f), .5f), line(KitSurfaceStyle.Paper))
    }

    @Test
    fun `B-KIT-048 section rule, link underline, text halo source and field halo`() {
        fun rule(style: KitSurfaceStyle) = KitStyles.sectionRule(graphiteDark, style, KitStyles.spec(style).tokens.defaults, .5f)
        assertNull(rule(KitSurfaceStyle.Solid))
        assertNull(rule(KitSurfaceStyle.Soft))
        assertNull(rule(KitSurfaceStyle.Brutal))
        assertEquals(KitStyles.Line(graphiteDark.text1.withAlpha(.9f), 1f), rule(KitSurfaceStyle.Paper))
        fun underline(style: KitSurfaceStyle) = KitStyles.linkUnderline(graphiteDark, style, KitStyles.spec(style).tokens.defaults)
        assertNull(underline(KitSurfaceStyle.Solid))
        assertNull(underline(KitSurfaceStyle.Soft))
        assertEquals(2f, underline(KitSurfaceStyle.Brutal))
        assertEquals(1f, underline(KitSurfaceStyle.Paper))
        assertTrue(KitStyleTokens(defaults = KitKnobs()).fieldHaloShown)
        assertFalse(KitStyleTokens(defaults = KitKnobs(), glow = KitStyleGlow(halo = false)).fieldHaloShown)
        assertFalse(KitStyleTokens(defaults = KitKnobs(), fieldHalo = false).fieldHaloShown)
        assertTrue(KitStyleSoft.tokens.fieldHaloShown)
    }

    @Test
    fun `B-KIT-048 a highlight on an own fill keeps the part's layer by default, Brutal answers it`() {
        val own = KitSurfaceLook(radius = KitRadii.all(KitRadii.STADIUM), fill = Argb(0x29000000))
        for (style in KitSurfaceStyle.entries) {
            val look = KitStyles.resolve(graphiteDark, style, KitSurfaceRole.StateLayer, own, color = graphiteDark.accent, pressed = true)
            if (style == KitSurfaceStyle.Brutal) assertNotEquals(own, look) else assertSame(own, look, "$style")
        }
        // A disabled control tells the style: Brutal lies it flat, Soft drops its shadows.
        val off = KitStyles.resolve(graphiteLight, KitSurfaceStyle.Brutal, KitSurfaceRole.Control, solidPanel, disabled = true)
        assertEquals(0f, off.hardOffset)
        // The pressed state passed down sinks a floating action under Brutal.
        val fab = KitStyles.resolve(graphiteLight, KitSurfaceStyle.Brutal, KitSurfaceRole.Floating, solidPanel, pressed = true)
        assertEquals(KitStyleBrutal.offsetOf(KitSurfaceRole.Floating), fab.faceShift)
        // Words on a surface: Solid always keeps the part's own.
        assertEquals(
            graphiteLight.text2,
            KitStyles.wordsOn(graphiteLight, KitSurfaceStyle.Solid, KitKnobs(), graphiteLight.text2, selected = true, color = graphiteLight.accent),
        )
    }
}
