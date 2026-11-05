package io.github.eslamasabry.oc.ui.design.rb.o03

import io.github.eslamasabry.oc.ui.design.color.Graphite
import io.github.eslamasabry.oc.ui.design.style.BrutalSpec
import io.github.eslamasabry.oc.ui.design.style.Corners
import io.github.eslamasabry.oc.ui.design.style.Depth
import io.github.eslamasabry.oc.ui.design.style.Edges
import io.github.eslamasabry.oc.ui.design.style.Knobs
import io.github.eslamasabry.oc.ui.design.style.NeedsYouLook
import io.github.eslamasabry.oc.ui.design.style.NeedsYouTag
import io.github.eslamasabry.oc.ui.design.style.Radii
import io.github.eslamasabry.oc.ui.design.style.StyleType
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.Density
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** lib/ui/kit/style/kit_style_brutal.dart: tokens, hard offsets per role, the sink, ink edges. */
class KitStyleBrutalParityTest {
    @Test fun constantsAndTokens() {
        assertEquals(2f, BrutalSpec.EDGE)
        assertEquals(3f, BrutalSpec.FOCUS_RING)
        val t = BrutalSpec.tokens
        assertEquals(Knobs(corners = Corners.Soft, depth = Depth.Raised), t.defaults)
        assertTrue(t.keepsUnderHighContrast)
        assertEquals(.7f, t.live.maxStrength)
        assertEquals(false, t.glow.halo)
        assertEquals(2f, t.glow.lineWidthDp)
        assertTrue(t.flattensNested)
        assertEquals(2f, t.linkUnderline)
        assertEquals(NeedsYouLook(tag = NeedsYouTag.Pill, tint = false, ring = false, startEdge = false), t.needsYou)
        assertEquals(StyleType(displayWeight = 800, displayTracking = -.03f, bodyWeight = 500, monoLabels = true, firmWeights = true), t.type)
    }

    @Test fun offsets() {
        val full = mapOf(SurfaceRole.Content to 4f, SurfaceRole.Control to 3f, SurfaceRole.Track to 0f, SurfaceRole.Floating to 6f, SurfaceRole.Sheet to 4f, SurfaceRole.Field to 0f)
        val compact = mapOf(SurfaceRole.Content to 3f, SurfaceRole.Control to 2f, SurfaceRole.Track to 0f, SurfaceRole.Floating to 4f, SurfaceRole.Sheet to 3f, SurfaceRole.Field to 0f)
        for ((role, v) in full) assertEquals(v, BrutalSpec.offsetOf(role), "$role")
        for ((role, v) in compact) assertEquals(v, BrutalSpec.offsetOf(role, compact = true), "$role compact")
    }

    @Test fun resolvedHardShadowFollowsOffset() {
        val knobs = Knobs(corners = Corners.Soft, depth = Depth.Raised)
        for (roles in bothModes) {
            val c = BrutalSpec.resolve(req(roles, SurfaceRole.Content, knobs, level = SurfaceLevel.Surface1))!!
            assertEquals(4f, c.hardOffsetDp)
            assertEquals(BrutalSpec.shadowInk(roles), c.hardShadow)
            assertEquals(2f, c.edgeWidthDp)
            val compact = BrutalSpec.resolve(req(roles, SurfaceRole.Content, knobs.copy(density = Density.Compact)))!!
            assertEquals(3f, compact.hardOffsetDp)
        }
    }

    @Test fun pressedSinksByItsOffsetAndShowsNoShadow() {
        val knobs = Knobs(depth = Depth.Raised)
        val d = BrutalSpec.resolve(req(Graphite.light, SurfaceRole.Control, knobs, pressed = true))!!
        assertEquals(3f, d.faceShiftDp)
        assertEquals(0f, d.hardOffsetDp)
        assertNull(d.hardShadow)
    }

    @Test fun flatDepthDropsTheShadow() {
        val d = BrutalSpec.resolve(req(Graphite.light, SurfaceRole.Content, Knobs(depth = Depth.Flat)))!!
        assertEquals(0f, d.hardOffsetDp)
    }

    @Test fun fieldFocusRingIsThreeDpSpread() {
        val d = BrutalSpec.resolve(req(Graphite.light, SurfaceRole.Field, selected = true))!!
        assertEquals(3f, d.shadows.single().spreadDp)
        assertEquals(Graphite.light.accent, d.shadows.single().color)
    }

    @Test fun darkShadowInkAndSelectedRing() {
        val roles = Graphite.dark
        val d = BrutalSpec.resolve(req(roles, SurfaceRole.Content, selected = true))!!
        assertEquals(roles.accent, d.edgeStart)
    }

    @Test fun dividerUnderEdges() {
        val on = BrutalSpec.resolve(req(Graphite.light, SurfaceRole.Divider, Knobs(edges = Edges.Hairline)))!!
        assertEquals(230, on.fill!!.alpha) // .9
        val none = BrutalSpec.resolve(req(Graphite.light, SurfaceRole.Divider, Knobs(edges = Edges.None)))!!
        assertEquals(0, none.fill!!.alpha)
        assertEquals(0f, on.edgeWidthDp)
    }

    @Test fun stateLayer() {
        val r = Graphite.light
        assertEquals(r.surface3, BrutalSpec.resolve(req(r, SurfaceRole.StateLayer, pressed = true))!!.fill)
        assertEquals(10, BrutalSpec.resolve(req(r, SurfaceRole.StateLayer))!!.fill!!.alpha) // .04
    }

    @Test fun lightGroundWarmsDarkKeepsGround() {
        val d = Graphite.dark
        val a = BrutalSpec.adjustRoles(d)
        assertEquals(d.ground, a.ground)
        assertEquals(d.surface2, a.surface1)
        assertTrue(BrutalSpec.adjustRoles(Graphite.light).ground != Graphite.light.ground)
    }

    @Test fun evenSheetOnlyCasts() {
        val knobs = Knobs(depth = Depth.Raised)
        val top = Radii(16f, 16f, 0f, 0f)
        val bottomSheet = BrutalSpec.resolve(req(Graphite.light, SurfaceRole.Sheet, knobs, radii = top))!!
        assertEquals(0f, bottomSheet.hardOffsetDp)
        val dialog = BrutalSpec.resolve(req(Graphite.light, SurfaceRole.Sheet, knobs))!!
        assertEquals(4f, dialog.hardOffsetDp)
    }
}
