package io.github.eslamasabry.oc.ui.design.rb.o03

import io.github.eslamasabry.oc.ui.design.color.Graphite
import io.github.eslamasabry.oc.ui.design.style.Corners
import io.github.eslamasabry.oc.ui.design.style.Depth
import io.github.eslamasabry.oc.ui.design.style.Edges
import io.github.eslamasabry.oc.ui.design.style.Knobs
import io.github.eslamasabry.oc.ui.design.style.PaperSpec
import io.github.eslamasabry.oc.ui.design.style.Radii
import io.github.eslamasabry.oc.ui.design.style.StyleGlow
import io.github.eslamasabry.oc.ui.design.style.StyleType
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** lib/ui/kit/style/kit_style_paper.dart: constants, tokens, ink boxes, inverted chosen controls, no content shadows. */
class KitStylePaperParityTest {
    @Test fun constantsAndTokens() {
        assertEquals(.2f, PaperSpec.RULE_ALPHA)
        assertEquals(2f, PaperSpec.CHOSEN_RULE)
        assertEquals(.9f, PaperSpec.SECTION_ALPHA)
        assertEquals(.03f, PaperSpec.HOVER_ALPHA)
        assertEquals(.6f, PaperSpec.boxAlpha(Graphite.dark))
        assertEquals(.9f, PaperSpec.boxAlpha(Graphite.light))
        assertEquals(.10f, PaperSpec.pressStep(Graphite.dark))
        assertEquals(.07f, PaperSpec.pressStep(Graphite.light))
        val t = PaperSpec.tokens
        assertEquals(Knobs(corners = Corners.Sharp), t.defaults)
        assertEquals(0f, t.sharpRadius)
        assertTrue(t.keepsUnderHighContrast)
        assertTrue(t.live.offWhenChosen)
        assertEquals(StyleGlow(halo = false, lineWidthDp = 1f), t.glow)
        assertEquals(StyleType(displayWeight = 750, displayTracking = -.03f, largeTitleSize = 34, titleSize = 24, monoLabels = true, firmWeights = true), t.type)
    }

    @Test fun contentIsSetOnThePageWithNoShadow() {
        for (roles in bothModes) {
            val d = PaperSpec.resolve(req(roles, SurfaceRole.Content, Knobs(corners = Corners.Sharp)))!!
            assertEquals(0, d.fill!!.alpha)
            assertEquals(0f, d.edgeWidthDp)
            assertTrue(d.shadows.isEmpty())
            assertEquals(Radii.Zero, d.radii)
        }
    }

    @Test fun raisedStepIsABox() {
        val d = PaperSpec.resolve(req(Graphite.light, SurfaceRole.Content, Knobs(), level = SurfaceLevel.Surface2))!!
        assertEquals(Graphite.light.ground, d.fill)
        assertEquals(1f, d.edgeWidthDp)
    }

    @Test fun chosenControlIsInvertedInk() {
        for (roles in bothModes) {
            val r = req(roles, SurfaceRole.Control, selected = true)
            val d = PaperSpec.resolve(r)!!
            assertEquals(PaperSpec.invertedInk(roles), d.fill)
            assertEquals(d.fill, d.edgeStart)
            assertEquals(1f, d.edgeWidthDp)
            assertEquals(roles.ground.withAlpha(1f), PaperSpec.onColor(r))
        }
        assertNull(PaperSpec.onColor(req(Graphite.light, SurfaceRole.Control)))
    }

    @Test fun floatingGetsTheTightDropOnlyWhenRaised() {
        val raised = PaperSpec.resolve(req(Graphite.light, SurfaceRole.Floating, Knobs(depth = Depth.Raised)))!!
        val s = raised.shadows.single()
        assertEquals(6f, s.dyDp)
        assertEquals(16f, s.blurDp)
        assertEquals(-6f, s.spreadDp)
        assertTrue(PaperSpec.resolve(req(Graphite.light, SurfaceRole.Floating, Knobs(depth = Depth.Flat)))!!.shadows.isEmpty())
    }

    @Test fun fieldIsRuledAndChosenRuleIsTwo() {
        val d = PaperSpec.resolve(req(Graphite.light, SurfaceRole.Field, selected = true))!!
        assertEquals(2f, d.edgeWidthDp)
        assertEquals(0, d.fill!!.alpha)
        assertEquals(1f, PaperSpec.resolve(req(Graphite.light, SurfaceRole.Field))!!.edgeWidthDp)
    }

    @Test fun dividerRules() {
        val rule = PaperSpec.resolve(req(Graphite.light, SurfaceRole.Divider, Knobs(edges = Edges.Hairline)))!!
        assertEquals(51, rule.fill!!.alpha) // .2
        val section = PaperSpec.resolve(req(Graphite.light, SurfaceRole.Divider, Knobs(edges = Edges.Hairline), selected = true))!!
        assertEquals(230, section.fill!!.alpha) // .9
        assertEquals(0, PaperSpec.resolve(req(Graphite.light, SurfaceRole.Divider, Knobs(edges = Edges.None)))!!.fill!!.alpha)
    }

    @Test fun adjustedRolesPutTheRuleOnTheHairline() {
        for (roles in bothModes) {
            val a = PaperSpec.adjustRoles(roles)
            assertEquals(a.ground, a.surface1)
            assertEquals(51, a.hairline.alpha)
        }
    }
}
