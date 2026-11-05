package io.github.eslamasabry.oc.ui.design.rb.o03

import io.github.eslamasabry.oc.ui.design.color.Graphite
import io.github.eslamasabry.oc.ui.design.style.Corners
import io.github.eslamasabry.oc.ui.design.style.Depth
import io.github.eslamasabry.oc.ui.design.style.Edges
import io.github.eslamasabry.oc.ui.design.style.FineTuneChoice
import io.github.eslamasabry.oc.ui.design.style.LivePolicy
import io.github.eslamasabry.oc.ui.design.style.NeedsYouLook
import io.github.eslamasabry.oc.ui.design.style.NeedsYouTag
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.design.style.StyleResolver
import io.github.eslamasabry.oc.ui.design.style.StyleType
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.Density
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** lib/ui/kit/style/kit_style.dart at 8302de537 (+ the Glass hooks at 1bbdc3f9c): every constant and rule. */
class KitStyleParityTest {
    @Test fun styleOrderAndStorageNames() {
        assertEquals(listOf("solid", "glass", "soft", "brutal", "paper", "signal"), Style.entries.map { it.storageName })
    }

    @Test fun specsRegisteredForEveryStyle() {
        for (s in Style.entries) assertNotNull(StyleResolver.spec(s))
    }

    @Test fun tokenDefaults() {
        val t = StyleResolver.spec(Style.Solid).tokens
        assertEquals(2f, t.sharpRadius)
        assertFalse(t.keepsUnderHighContrast)
        assertFalse(t.dependsOnDepth)
        assertFalse(t.flattensNested)
        assertNull(t.dividerWidthDp)
        assertNull(t.linkUnderline)
        assertNull(t.fieldHalo)
        assertFalse(t.track)
        assertFalse(t.blursBehind)
        assertTrue(t.type.isNone)
        assertTrue(t.glow.halo)
        assertNull(t.glow.lineWidthDp)
        assertEquals(NeedsYouLook(), t.needsYou)
    }

    @Test fun fieldHaloFollowsGlowUnlessSet() {
        assertTrue(StyleResolver.spec(Style.Solid).tokens.fieldHaloShown)
        assertFalse(StyleResolver.spec(Style.Brutal).tokens.fieldHaloShown)
        assertTrue(StyleResolver.spec(Style.Soft).tokens.fieldHaloShown)
    }

    @Test fun needsYouDecide() {
        val d = NeedsYouLook().decide(halo = true)
        assertTrue(d.tint && d.line && d.ring && !d.startEdge && !d.pill)
        val calm = NeedsYouLook().decide(halo = false)
        assertTrue(!calm.tint && !calm.line && !calm.ring && calm.startEdge)
        val q = NeedsYouLook().decide(halo = true, question = true)
        assertTrue(!q.tint && q.line && !q.ring && q.startEdge)
        assertTrue(NeedsYouLook(tag = NeedsYouTag.Pill).decide(halo = true).pill)
    }

    @Test fun livePolicyCap() {
        assertEquals(1.6f, LivePolicy().cap(1.6f))
        assertEquals(.7f, LivePolicy(maxStrength = .7f).cap(1.6f))
        assertEquals(.5f, LivePolicy(maxStrength = .7f).cap(.5f))
        assertEquals(1.5f, LivePolicy(boost = 1.5f).cap(1f))
        assertEquals(.7f, LivePolicy(boost = 1.5f, maxStrength = .7f).cap(1f))
        assertEquals(.4f, LivePolicy(lightVivid = .4f, darkVivid = .2f).vividFor(dark = false))
        assertEquals(.2f, LivePolicy(lightVivid = .4f, darkVivid = .2f).vividFor(dark = true))
    }

    @Test fun styleTypeIsNone() {
        assertTrue(StyleType().isNone)
        assertFalse(StyleType(weightBoost = 50f).isNone)
        assertFalse(StyleType(firmWeights = true).isNone)
    }

    @Test fun effectiveStyleUnderHighContrastAndSaver() {
        for (s in Style.entries) {
            assertEquals(s, StyleResolver.effectiveStyle(s, highContrast = false))
            assertEquals(Style.Solid, StyleResolver.effectiveStyle(s, highContrast = false, saver = true))
        }
        val keeps = setOf(Style.Solid, Style.Brutal, Style.Paper, Style.Signal)
        for (s in Style.entries) {
            val e = StyleResolver.effectiveStyle(s, highContrast = true)
            assertEquals(if (s in keeps) s else Style.Solid, e)
        }
    }

    @Test fun knobsForEveryStyleAndKnob() {
        for (s in Style.entries) for (c in Corners.entries) for (d in Density.entries) {
            val k = StyleResolver.knobsFor(s, FineTuneChoice(c, d))
            assertEquals(c, k.corners)
            assertEquals(d, k.density)
            val defaults = StyleResolver.spec(s).tokens.defaults
            assertEquals(defaults.depth, k.depth)
            assertEquals(defaults.edges, k.edges)
        }
    }

    @Test fun knobsUnderHighContrastAndDependsOnDepth() {
        val hc = StyleResolver.knobsFor(Style.Brutal, highContrast = true)
        assertEquals(Depth.Flat, hc.depth)
        assertEquals(Edges.Hairline, hc.edges)
        assertEquals(Corners.Soft, hc.corners)
        // Soft is shaped by shadows: high contrast makes it Solid, whose defaults then rule.
        val soft = StyleResolver.knobsFor(Style.Soft, highContrast = true)
        assertEquals(Corners.Round, soft.corners)
        assertEquals(Edges.Hairline, soft.edges)
        val saver = StyleResolver.knobsFor(Style.Brutal, FineTuneChoice(Corners.Sharp, null), saver = true)
        assertEquals(Corners.Round, saver.corners)
    }

    @Test fun cornerRule() {
        assertEquals(12f, StyleResolver.corner(12f, Corners.Round))
        assertEquals(7f, StyleResolver.corner(12f, Corners.Soft))
        assertEquals(999f, StyleResolver.corner(999f, Corners.Soft))
        assertEquals(2f, StyleResolver.corner(12f, Corners.Sharp))
        assertEquals(0f, StyleResolver.corner(12f, Corners.Sharp, sharp = 0f))
        assertEquals(1f, StyleResolver.corner(1f, Corners.Sharp))
        assertEquals(0f, StyleResolver.corner(0f, Corners.Soft))
    }

    @Test fun offWhenChosenAndLookWhenChosen() {
        assertEquals(setOf(Style.Paper, Style.Signal), Style.entries.filter { StyleResolver.offWhenChosen(it) }.toSet())
        assertEquals("auroraImage", StyleResolver.liveLookAfterChoosing(Style.Solid, Style.Glass, "aurora", "aurora"))
        assertEquals("aurora", StyleResolver.liveLookAfterChoosing(Style.Glass, Style.Soft, "auroraImage", "aurora"))
        assertEquals("mesh", StyleResolver.liveLookAfterChoosing(Style.Glass, Style.Soft, "mesh", "aurora"))
        assertEquals("auroraImage", StyleResolver.liveLookAfterChoosing(Style.Glass, Style.Glass, "auroraImage", "aurora"))
    }

    @Test fun adjustedRolesChangeOnlyTheAllowedFields() {
        for (s in Style.entries) for (roles in bothModes) {
            val a = StyleResolver.adjust(roles, s)
            assertEquals(roles.text1, a.text1)
            assertEquals(roles.dark, a.dark)
        }
        assertEquals(Graphite.dark, StyleResolver.adjust(Graphite.dark, Style.Solid))
    }

    @Test fun groundIsAlwaysItsFillAlone() {
        for (s in Style.entries - Style.Solid) for (roles in bothModes) {
            val d = StyleResolver.spec(s).resolve(req(roles, SurfaceRole.Ground))
            assertNotNull(d)
            assertEquals(roles.ground, d.fill)
            assertEquals(0f, d.edgeWidthDp)
            assertTrue(d.shadows.isEmpty())
        }
    }
}
