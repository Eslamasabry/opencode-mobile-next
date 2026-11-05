package io.github.eslamasabry.oc.ui.design.rb.o03

import io.github.eslamasabry.oc.ui.design.color.Graphite
import io.github.eslamasabry.oc.ui.design.style.Corners
import io.github.eslamasabry.oc.ui.design.style.Depth
import io.github.eslamasabry.oc.ui.design.style.Edges
import io.github.eslamasabry.oc.ui.design.style.Knobs
import io.github.eslamasabry.oc.ui.design.style.NeedsYouLook
import io.github.eslamasabry.oc.ui.design.style.SoftSpec
import io.github.eslamasabry.oc.ui.design.style.StyleType
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** lib/ui/kit/style/kit_style_soft.dart: tokens, depth per role, paired shadows, wells, the sheet's drop. */
class KitStyleSoftParityTest {
    @Test fun tokens() {
        val t = SoftSpec.tokens
        assertEquals(Knobs(corners = Corners.Round, depth = Depth.Raised, edges = Edges.None), t.defaults)
        assertTrue(t.dependsOnDepth)
        assertEquals(.7f, t.live.maxStrength)
        assertEquals(NeedsYouLook(tint = false, ring = false, startEdge = true), t.needsYou)
        assertEquals(true, t.fieldHalo)
        assertEquals(StyleType(firmWeights = true), t.type)
        assertEquals(2f, t.sharpRadius)
    }

    private fun paired(role: SurfaceRole, roles: io.github.eslamasabry.oc.ui.design.color.Roles = Graphite.dark) =
        SoftSpec.resolve(req(roles, role, Knobs(depth = Depth.Raised, edges = Edges.None)))!!.paired

    @Test fun depthPerRole() {
        val want =
            mapOf(
                SurfaceRole.Content to (6f to 16f), SurfaceRole.Control to (3f to 8f), SurfaceRole.Track to (2.5f to 6f),
                SurfaceRole.Floating to (6f to 16f), SurfaceRole.Field to (2.5f to 6f), SurfaceRole.Toggle to (2.5f to 6f),
                SurfaceRole.Code to (2.5f to 6f),
            )
        for (roles in bothModes) for ((role, d) in want) {
            val p = paired(role, roles)
            assertNotNull(p, "$role")
            assertEquals(d.first, p.distanceDp, "$role distance")
            assertEquals(d.second, p.blurDp, "$role blur")
        }
    }

    @Test fun flatDepthHasNoDistance() {
        val d = SoftSpec.resolve(req(Graphite.light, SurfaceRole.Content, Knobs(depth = Depth.Flat, edges = Edges.Hairline)))!!
        assertEquals(0f, d.paired!!.distanceDp)
        assertEquals(0f, d.paired!!.blurDp)
        assertNotNull(d.edgeStart)
    }

    @Test fun wellsArePressedIn() {
        for (role in listOf(SurfaceRole.Track, SurfaceRole.Field, SurfaceRole.Toggle, SurfaceRole.Code)) for (roles in bothModes) {
            assertEquals(1f, paired(role, roles)!!.inset, "$role")
        }
        for (roles in bothModes) assertEquals(0f, paired(SurfaceRole.Content, roles)!!.inset)
        for (roles in bothModes) assertEquals(1f, SoftSpec.resolve(req(roles, SurfaceRole.Control, Knobs(), selected = true))!!.paired!!.inset)
    }

    @Test fun sheetIsGroundColourWithOneDrop() {
        for (roles in bothModes) {
            val d = SoftSpec.resolve(req(roles, SurfaceRole.Sheet))!!
            assertNull(d.paired)
            val s = d.shadows.single()
            assertEquals(0f, s.dxDp)
            assertEquals(-4f, s.dyDp)
            assertEquals(28f, s.blurDp)
        }
    }

    @Test fun dividerIsNoneUnlessEdgesOn() {
        val none = SoftSpec.resolve(req(Graphite.dark, SurfaceRole.Divider, Knobs(edges = Edges.None)))!!
        assertEquals(0, none.fill!!.alpha)
        val on = SoftSpec.resolve(req(Graphite.dark, SurfaceRole.Divider, Knobs(edges = Edges.Hairline)))!!
        assertEquals(Graphite.dark.hairline, on.fill)
    }

    @Test fun darkLightPairColours() {
        val p = paired(SurfaceRole.Content, Graphite.dark)!!
        assertEquals(217, p.dark.alpha) // .85 of 255, rounded as Flutter's 8-bit storage
        assertEquals(26, p.light.alpha) // .10
        val l = paired(SurfaceRole.Content, Graphite.light)!!
        assertEquals(179, l.light.alpha) // .70
        assertEquals(66, l.dark.alpha) // .26
    }

    @Test fun promptStepLiftsInDark() {
        val ground = Graphite.dark.ground
        val d = SoftSpec.resolve(req(Graphite.dark, SurfaceRole.Content, Knobs(), level = SurfaceLevel.Surface2))!!
        assertTrue(d.fill != ground)
    }

    @Test fun adjustedGroundMovesOffTheTheme() {
        for (roles in bothModes) {
            val a = SoftSpec.adjustRoles(roles)
            assertEquals(a.ground, a.surface1)
            assertTrue(a.ground != roles.ground)
        }
    }
}
